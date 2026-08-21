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
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
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
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Toolbox(String repo) {
        this.cache = Path.of(System.getProperty("user.home"), ".runtime-xray");
        this.repo = repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
    }

    public Path jacocoAgent() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.agent", JACOCO_VERSION, "runtime", "jar");
    }

    public Path jacocoCli() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.cli", JACOCO_VERSION, "nodeps", "jar");
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
        return artifact("tools.profiler", "jfr-converter", ASYNC_VERSION, null, "jar");
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

        Path jar = artifact("tools.profiler", "async-profiler", ASYNC_VERSION, null, "jar");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) {
                throw new IOException("async-profiler " + ASYNC_VERSION
                        + " ne contient pas " + entry + " — plateforme non prise en charge");
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

        Path zip = artifact("com.taobao.arthas", "arthas-packaging", ARTHAS_VERSION, "bin", "zip");
        Files.createDirectories(home);
        unzip(zip, home);
        return home;
    }

    // ------------------------------------------------------------------ interne

    private Path artifact(String group, String name, String version, String classifier, String ext)
            throws IOException, InterruptedException {
        String file = name + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext;
        Path local = cache.resolve(file);
        if (Files.isRegularFile(local)) return local;

        String url = repo + "/" + group.replace('.', '/') + "/" + name + "/" + version + "/" + file;
        Files.createDirectories(cache);
        Path tmp = Files.createTempFile(cache, "dl-", ".part");
        System.out.println("   téléchargement : " + file);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        if (res.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException("téléchargement impossible (" + res.statusCode() + ") : " + url
                    + "\n   Sur un réseau fermé, indiquer le miroir interne avec MAVEN_REPO.");
        }
        Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING);
        return local;
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
        throw new IOException("Les mesures de temps ne sont pas disponibles sur " + os
                + " : async-profiler ne publie que macOS et Linux."
                + "\n   L'analyse reste possible sans elles (couverture et valeurs).");
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
                    throw new IOException("entrée d'archive suspecte : " + e.getName());
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
