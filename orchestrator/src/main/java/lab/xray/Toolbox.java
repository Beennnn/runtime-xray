package lab.xray;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Récupère et met en cache les trois composants d'analyse.
 *
 * <p>Tout passe par un dépôt Maven — celui de l'éditeur par défaut, ou le miroir interne
 * de l'entreprise. C'est un choix : dans un réseau fermé, le miroir Maven est très souvent
 * le seul canal ouvert, et c'est déjà par lui que ces trois outils sont distribués.
 *
 * <p>Le cache ({@code ~/.runtime-xray}) fait qu'au deuxième lancement plus rien ne sort sur
 * le réseau. Sur une machine réellement isolée, il suffit de transporter ce répertoire.
 */
public final class Toolbox {

    private static final String JACOCO_VERSION = "0.8.13";
    private static final String ARTHAS_VERSION = "4.3.4";
    private static final String ASYNC_VERSION = "4.1";

    private final Path cache;
    private final String repo;
    /** Répertoires fouillés à plat, dans l'ordre, avant de sortir sur le réseau. */
    private final List<Path> plats;
    /** Dépôt Maven local, fouillé selon la disposition groupe/artefact/version. */
    private final Path depotLocal;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Toolbox(String repo) {
        this(repo, "");
    }

    /**
     * @param composants répertoire indiqué par l'utilisateur ({@code --composants}), ou vide.
     */
    public Toolbox(String repo, String composants) {
        this(repo,
             Path.of(System.getProperty("user.home"), ".runtime-xray"),
             emplacements(composants),
             depotMavenLocal());
    }

    Toolbox(String repo, Path cache, List<Path> plats, Path depotLocal) {
        this.cache = cache;
        this.plats = plats;
        this.depotLocal = depotLocal;
        this.repo = repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
    }

    /**
     * Les endroits où un composant déposé à la main peut se trouver, du plus explicite au
     * moins probable : ce que l'utilisateur a désigné, puis le voisinage du jar — c'est le
     * geste naturel quand on transporte l'outil sur une clé.
     */
    private static List<Path> emplacements(String composants) {
        List<Path> dirs = new ArrayList<>();
        if (composants != null && !composants.isBlank()) dirs.add(Path.of(composants.trim()));
        String env = System.getenv("RUNTIME_XRAY_COMPOSANTS");
        if (env != null && !env.isBlank()) dirs.add(Path.of(env.trim()));
        Path jar = repertoireDuJar();
        if (jar != null) {
            dirs.add(jar);
            dirs.add(jar.resolve("composants"));
        }
        return dirs;
    }

    /** {@code null} hors d'un jar — en test, le code vit dans un répertoire de classes. */
    private static Path repertoireDuJar() {
        try {
            java.net.URL src = Toolbox.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(src.toURI());
            return Files.isDirectory(p) ? null : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path depotMavenLocal() {
        String explicite = System.getenv("MAVEN_REPO_LOCAL");
        if (explicite != null && !explicite.isBlank()) return Path.of(explicite.trim());
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    public Path jacocoAgent() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.agent", JACOCO_VERSION, "runtime", "jar",
                "jacocoagent.jar");
    }

    public Path jacocoCli() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.cli", JACOCO_VERSION, "nodeps", "jar",
                "jacococli.jar");
    }

    /**
     * Le convertisseur officiel d'async-profiler : il transforme les piles repliées en la
     * page que l'outil produit lui-même.
     *
     * <p>Pourquoi la produire alors que cette page a déjà son propre arbre : parce que ce
     * n'est pas la même chose. La nôtre est une <b>synthèse</b>, avec ses choix (repli du
     * JDK, repli des paquets masqués, agrégation). Celle-ci est la sortie <b>brute</b> de
     * l'outil, sans aucun de nos traitements — c'est elle qui fait foi si la synthèse se
     * trompe, et c'est elle qui reste lisible si la synthèse ne s'ouvre plus.
     */
    public Path asyncProfilerConverter() throws IOException, InterruptedException {
        return artifact("tools.profiler", "jfr-converter", ASYNC_VERSION, null, "jar",
                "jfr-converter.jar");
    }

    /**
     * La bibliothèque native d'async-profiler, extraite du jar publié sur Maven Central.
     * Le jar embarque les binaires de plusieurs plateformes ; on ne sort que la bonne.
     */
    public Path asyncProfilerLibrary() throws IOException, InterruptedException {
        String explicit = System.getenv("ASYNC_PROFILER_LIB");
        if (explicit != null && Files.isRegularFile(Path.of(explicit))) {
            return Path.of(explicit);
        }
        String entry = nativeEntryName();
        Path target = cache.resolve("libasyncProfiler-" + ASYNC_VERSION + suffix(entry));
        if (Files.isRegularFile(target)) return target;

        Path jar = artifact("tools.profiler", "async-profiler", ASYNC_VERSION, null, "jar",
                "async-profiler.jar");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) {
                throw new IOException("async-profiler " + ASYNC_VERSION
                        + " does not contain " + entry + " — platform not supported");
            }
            Files.createDirectories(cache);
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    /** Le paquet complet d'Arthas, décompressé : son lanceur ne sortira pas sur le réseau. */
    public Path arthasHome() throws IOException, InterruptedException {
        Path home = cache.resolve("arthas-" + ARTHAS_VERSION);
        if (Files.isRegularFile(home.resolve("arthas-boot.jar"))) return home;

        // Arthas se distribue aussi décompressé : celui qui l'a déjà sous la main l'a le
        // plus souvent sous cette forme, et le redemander en archive n'aurait aucun sens.
        for (Path dir : plats) {
            for (Path candidat : List.of(dir.resolve("arthas-" + ARTHAS_VERSION),
                                         dir.resolve("arthas"), dir)) {
                if (Files.isRegularFile(candidat.resolve("arthas-boot.jar"))) {
                    System.out.println("   local component: " + candidat);
                    return candidat;
                }
            }
        }

        Path zip = artifact("com.taobao.arthas", "arthas-packaging", ARTHAS_VERSION, "bin", "zip",
                "arthas-bin.zip");
        Files.createDirectories(home);
        unzip(zip, home);
        return home;
    }

    // ------------------------------------------------------------------ interne

    /**
     * Trouve un composant, sans réseau si possible.
     *
     * <p>Le réseau est le dernier recours, jamais le premier réflexe : sur la machine qui
     * nous intéresse, il n'y en a pas. On regarde donc d'abord le cache, puis ce que
     * l'utilisateur a déposé à la main, puis le dépôt Maven local — celui que le moindre
     * {@code mvn} d'entreprise a déjà rempli depuis le miroir interne.
     *
     * @param usuel nom du même composant dans la distribution de son éditeur. C'est sous ce
     *              nom-là qu'on l'a quand on ne l'a pas pris sur Maven : {@code jacocoagent.jar},
     *              pas {@code org.jacoco.agent-0.8.13-runtime.jar}. On l'accepte, en disant
     *              que la version n'est alors pas vérifiée.
     */
    private Path artifact(String group, String name, String version, String classifier, String ext,
                          String usuel) throws IOException, InterruptedException {
        String file = name + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext;
        List<String> vus = new ArrayList<>();

        Path local = cache.resolve(file);
        vus.add(local.toString());
        if (Files.isRegularFile(local)) return local;

        for (Path dir : plats) {
            Path exact = dir.resolve(file);
            vus.add(exact.toString());
            if (Files.isRegularFile(exact)) {
                System.out.println("   local component: " + exact);
                return exact;
            }
            if (usuel != null) {
                Path autre = dir.resolve(usuel);
                vus.add(autre.toString());
                if (Files.isRegularFile(autre)) {
                    System.out.println("   local component: " + autre + mention(autre, version));
                    return autre;
                }
            }
        }

        Path m2 = depotLocal.resolve(group.replace('.', '/')).resolve(name).resolve(version)
                .resolve(file);
        vus.add(m2.toString());
        if (Files.isRegularFile(m2)) {
            System.out.println("   local component: " + m2);
            return m2;
        }

        Path embarque = extraitDuJar(file);
        if (embarque != null) return embarque;
        vus.add("(bundled in the jar: complete edition only)");

        String url = repo + "/" + group.replace('.', '/') + "/" + name + "/" + version + "/" + file;
        Files.createDirectories(cache);
        Path tmp = Files.createTempFile(cache, "dl-", ".part");
        System.out.println("   downloading: " + file);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<Path> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        } catch (IOException echec) {
            Files.deleteIfExists(tmp);
            throw new IOException(indisponible(file, url, vus) + "\n   (" + echec + ")");
        }
        if (res.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException(indisponible(file, url + " → HTTP " + res.statusCode(), vus));
        }
        Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING);
        return local;
    }

    /**
     * Ce qu'on ajoute derrière un composant trouvé sous son nom usuel.
     *
     * <p>Ce nom-là ne porte pas la version : {@code jacocoagent.jar} peut être n'importe
     * lequel. Plutôt que d'avertir à tout hasard — ce qui inquiète celui qui a apporté le
     * bon fichier et n'apprend rien à celui qui s'est trompé — on lit la version que le
     * fichier déclare lui-même. Silence si elle correspond, avertissement précis sinon.
     *
     * @return la mention à afficher, vide quand il n'y a rien à signaler.
     */
    static String mention(Path fichier, String attendue) {
        String declaree = versionDeclaree(fichier);
        if (declaree == null) {
            return "  (version not verified, " + attendue + " expected)";
        }
        if (declaree.equals(attendue)) {
            return "";
        }
        return "  (version " + declaree + " found, " + attendue + " expected:"
                + " components of the same tool must agree)";
    }

    /**
     * La version que le fichier déclare dans son manifeste, ou {@code null} s'il n'en
     * déclare aucune — les archives d'async-profiler et d'Arthas sont dans ce cas, et un
     * fichier illisible aussi. On ne conclut alors rien : ne pas savoir n'est pas un défaut.
     */
    private static String versionDeclaree(Path fichier) {
        try (JarFile jar = new JarFile(fichier.toFile())) {
            Manifest manifeste = jar.getManifest();
            if (manifeste == null) return null;
            return manifeste.getMainAttributes().getValue("Implementation-Version");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * L'édition complète du jar porte les composants en ressources. On les dépose alors
     * dans le cache — un {@code -javaagent} veut un fichier sur le disque, pas une entrée
     * d'archive, et le CLI de JaCoCo comme Arthas sont lancés en processus séparés.
     *
     * @return {@code null} pour le jar ordinaire, qui n'embarque rien.
     */
    private Path extraitDuJar(String file) throws IOException {
        try (InputStream in = Toolbox.class.getResourceAsStream("/lab/xray/composants/" + file)) {
            if (in == null) return null;
            Files.createDirectories(cache);
            Path tmp = Files.createTempFile(cache, "ex-", ".part");
            System.out.println("   bundled component: " + file);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Path cible = cache.resolve(file);
            Files.move(tmp, cible, StandardCopyOption.REPLACE_EXISTING);
            return cible;
        }
    }

    /** Un échec ici arrête tout : le message dit où on a cherché, et comment s'en sortir. */
    private static String indisponible(String file, String url, List<String> vus) {
        return "component not found: " + file
                + "\n   looked for on the network: " + url
                + "\n   looked for on disk:\n      " + String.join("\n      ", vus)
                + "\n   On a closed network: drop the file into one of these directories,"
                + "\n   point at it with --composants <directory>, or name the internal"
                + "\n   Maven mirror with --repo <url> (or MAVEN_REPO).";
    }

    /** Chemin de la bibliothèque native dans le jar, selon le système et l'architecture. */
    private static String nativeEntryName() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return "macos/libasyncProfiler.so";  // binaire universel
        if (os.contains("linux")) {
            boolean arm = arch.contains("aarch64") || arch.contains("arm");
            return arm ? "linux-arm64/libasyncProfiler.so" : "linux-x64/libasyncProfiler.so";
        }
        throw new IOException("Timing measurements are not available on " + os
                + ": async-profiler only publishes macOS and Linux."
                + "\n   The analysis still works without them (coverage and values).");
    }

    private static String suffix(String entry) {
        return entry.startsWith("macos/") ? ".dylib" : ".so";
    }

    private static void unzip(Path zip, Path target) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path out = target.resolve(e.getName()).normalize();
                // Un zip peut contenir des chemins qui remontent hors de la cible.
                if (!out.startsWith(target)) {
                    throw new IOException("suspicious archive entry: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = file.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    public boolean asyncProfilerAvailable() {
        try {
            nativeEntryName();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
