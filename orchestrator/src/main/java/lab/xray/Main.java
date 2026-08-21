package lab.xray;

import lab.xray.json.Json;
import lab.xray.report.Coverage;
import lab.xray.report.Dashboard;
import lab.xray.report.Exports;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Runtime X-Ray — point d'entrée.
 *
 * <p>Un seul fichier à déposer, lancé par le {@code java} qui est déjà là. C'est tout
 * l'intérêt d'un orchestrateur écrit en Java : la machine qui exécute l'application
 * analysée possède forcément un JDK, mais pas nécessairement bash ni Python.
 */
public final class Main {

    private static final String DEFAULT_CONFIG = "runtime-xray.conf";

    public static void main(String[] args) {
        parlerUtf8();
        try {
            System.exit(run(args));
        } catch (Exception e) {
            System.err.println();
            System.err.println("Échec : " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Écrit sur la console en UTF-8, quelle que soit l'encodage de la machine.
     *
     * <p>Java choisit l'encodage de {@code System.out} d'après celui du système. Sur une
     * machine réglée en C/POSIX — un conteneur, un serveur d'intégration, une session
     * distante — cela vaut ASCII, et tous les messages de cet outil, qui sont en français,
     * y perdent leurs accents : « Classes analys?es », « pas d'?chantillonnage ». Ce n'est
     * pas cosmétique : ce sont des messages qu'on lit pour comprendre ce qui s'est passé,
     * et qu'on cherche parfois dans un journal.
     */
    private static void parlerUtf8() {
        System.setOut(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(
                new java.io.FileOutputStream(java.io.FileDescriptor.err), true,
                StandardCharsets.UTF_8));
    }

    private static int run(String[] args) throws Exception {
        Config config = new Config();
        Path configFile = null;
        boolean printOptionsOnly = false;
        boolean reportOnly = false;
        boolean serve = false;
        int servePort = 8787;
        String serveHost = "127.0.0.1";

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "-h", "--help" -> { usage(); return 0; }
                case "--config" -> configFile = Path.of(args[++i]);
                case "--java" -> config.javaCommand = args[++i];
                case "--root" -> config.rootMethod = args[++i];
                case "--classes" -> config.classesDir = args[++i];
                case "--sources" -> config.sourceDirs = args[++i];
                case "--filter" -> config.classFilter = args[++i];
                case "--hide" -> config.hiddenPackages = args[++i];
                case "--out" -> config.outDir = args[++i];
                case "--name" -> config.runName = args[++i];
                case "--repo" -> config.mavenRepo = args[++i];
                case "--attach-after" -> config.attachAfterSeconds = Integer.parseInt(args[++i]);
                case "--max-seconds" -> config.maxSeconds = Integer.parseInt(args[++i]);
                case "--no-values" -> config.captureValues = false;
                case "--print-options" -> printOptionsOnly = true;
                case "--report-only" -> reportOnly = true;
                case "--export" -> config.exportFormats = args[++i];
                case "--niveau" -> config.level = args[++i];
                case "--cover" -> config.coverIncludes = args[++i];
                case "--interval" -> config.sampleIntervalMs = Integer.parseInt(args[++i]);
                case "--serve-host" -> serveHost = args[++i];
                case "--serve" -> {
                    serve = true;
                    // Le port se colle à l'option quand on le donne, et se tait sinon :
                    // « --serve 9000 » et « --serve --report-only » doivent marcher tous les deux.
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        servePort = Integer.parseInt(args[++i]);
                    }
                }
                default -> {
                    System.err.println("Option inconnue : " + a);
                    usage();
                    return 2;
                }
            }
        }

        // Le fichier de configuration est GÉNÉRÉ s'il manque : personne ne devrait avoir à
        // deviner un format. On s'arrête là pour laisser l'utilisateur le renseigner.
        if (configFile != null) {
            if (!Files.isRegularFile(configFile)) {
                Config.writeTemplate(configFile);
                announceTemplate(configFile, "--config " + configFile);
                return 0;
            }
            Config fromFile = Config.load(configFile);
            merge(fromFile, config);
            config = fromFile;
        } else if (!reportOnly && config.javaCommand.isBlank() && config.classesDir.isBlank()) {
            // --report-only ne relance rien : il réassemble la vue depuis des exécutions
            // déjà sur le disque. Lui réclamer une configuration serait absurde, et écrire
            // un gabarit à sa place l'était encore plus.
            Path def = Path.of(DEFAULT_CONFIG);
            if (Files.isRegularFile(def)) {
                System.out.println("▶ Configuration lue : " + DEFAULT_CONFIG);
                config = Config.load(def);
            } else {
                Config.writeTemplate(def);
                announceTemplate(def, "");
                return 0;
            }
        }

        Toolbox tools = new Toolbox(config.mavenRepo);

        // Mode « je veux juste les options à coller » : l'outil doit pouvoir s'ajouter à une
        // ligne de commande Java quelconque, y compris une qu'on ne contrôle pas — un
        // service géré par systemd, un conteneur, un serveur d'application.
        if (printOptionsOnly) {
            printAgentOptions(config, tools);
            return 0;
        }

        require(!config.javaCommand.isBlank() || reportOnly, "--java est obligatoire");
        require(List.of("couverture", "arbre", "complet").contains(config.level.trim()),
                "--niveau attend couverture, arbre ou complet (reçu : " + config.level + ")");
        // Les classes servent à MESURER. Réassembler une vue depuis des mesures existantes
        // n'en a aucun besoin.
        // --classes n'est plus exigé ici : le bytecode se déduit de la JVM observée, une
        // fois qu'elle a tourné. Voir ClassSources pour l'ordre des sources consultées.
        for (Path entry : config.classesPaths()) {
            require(Files.isDirectory(entry) || Files.isRegularFile(entry),
                    "classes introuvables : " + entry + " (ni répertoire, ni jar)");
        }

        Path outDir = Path.of(config.outDir);
        Files.createDirectories(outDir);

        if (!reportOnly) {
            collect(config, tools, outDir);
        }

        if (!config.exportFormats.isBlank()) {
            exportRuns(config, outDir);
        }

        System.out.println("▶ Assemblage de la vue");
        Path page = Dashboard.build(outDir, sourceRoots(config), config.watchCount, config.hidden());
        System.out.println();
        System.out.println("Terminé — ouvrir : " + page);

        if (serve) {
            Config servi = config;
            LocalServer.serve(outDir, serveHost, servePort, () -> {
                // Après une écriture, la page est reconstruite : l'annotation devient celle
                // du rapport, et pas seulement celle de ce navigateur.
                Dashboard.build(outDir, sourceRoots(servi), servi.watchCount, servi.hidden());
                return null;
            });
        }
        return 0;
    }

    // ------------------------------------------------------------------ collecte

    private static void collect(Config config, Toolbox tools, Path outDir) throws Exception {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String uuid = UUID.randomUUID().toString();
        String name = config.runName.isBlank()
                ? "exécution du " + DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm", Locale.FRENCH)
                        .format(LocalDateTime.now())
                : config.runName;
        Path runDir = outDir.resolve("runs").resolve(stamp + slug(config.runName));
        Files.createDirectories(runDir);

        System.out.println("▶ Exécution « " + name + " » — identifiant " + uuid);

        RunSession session = new RunSession(config, tools, runDir);
        session.execute();

        resolveClasses(config, session);

        System.out.println("▶ Rendu de la couverture");
        renderCoverage(config, tools, runDir);

        System.out.println("▶ Rendu du profil par son propre outil");
        renderProfileViews(tools, runDir);

        writeContext(config, runDir, uuid, name, session);

        System.out.println();
        System.out.println("   Pour nommer, décrire ou étiqueter cette exécution plus tard, "
                + "ajouter dans " + outDir.resolve("noms.json") + " :");
        System.out.println("     { \"" + uuid + "\": \"un nom plus parlant\" }");
        System.out.println("   ou, pour tout renseigner :");
        System.out.println("     { \"" + uuid + "\": { \"nom\": \"…\", \"description\": \"…\","
                + " \"etiquettes\": { \"ticket\": \"ABC-123\", \"recette\": \"\" } } }");
        System.out.println("   La vue sait aussi les saisir et rendre ce fichier.");
    }

    /**
     * Détermine le bytecode à analyser, en interrogeant l'exécution plutôt que l'opérateur.
     *
     * <p>Un chemin explicite l'emporte toujours : il exprime une intention que rien ne doit
     * écraser — analyser une dépendance en plus, ou restreindre à un module.
     */
    private static void resolveClasses(Config config, RunSession session) {
        if (!config.classesDir.isBlank()) {
            return;
        }
        List<Path> found = ClassSources.discover(session.jvmArguments, config.javaCommand,
                Path.of("").toAbsolutePath());
        if (found.isEmpty()) {
            System.out.println("   ⚠️ impossible de déterminer où sont les classes — "
                    + "la couverture sera vide. Préciser --classes (un répertoire, un jar, "
                    + "ou une liste séparée par ':')");
            return;
        }
        config.classesDir = found.stream().map(Path::toString)
                .collect(java.util.stream.Collectors.joining(":"));
        System.out.println("▶ Classes analysées : " + config.classesDir
                + "  (déduit de l'exécution — --classes pour en ajouter ou en changer)");
    }

    /**
     * Produit les pages que <b>async-profiler rend lui-même</b> depuis les piles repliées.
     *
     * <p>Elles font doublon avec l'arbre de cette page, et c'est voulu : celui-ci est une
     * synthèse, avec ses partis pris (repli du JDK et des paquets masqués, agrégation par
     * méthode). Celles-là sont la sortie brute, sans traitement. Deux usages : vérifier la
     * synthèse quand elle surprend, et garder une vue exploitable si elle se casse.
     *
     * <p>Un échec de conversion n'interrompt rien — c'est une vue de confort, pas une
     * mesure ; la mesure, elle, est déjà sur le disque en {@code .collapsed}.
     */
    private static void renderProfileViews(Toolbox tools, Path runDir) {
        Path collapsed = runDir.resolve("async-profiler/profil.collapsed");
        if (!Files.isRegularFile(collapsed)) {
            return;
        }
        try {
            Path converter = tools.asyncProfilerConverter();
            // Le graphe classique, puis son inverse : le premier répond « où passe le
            // temps ? », le second « qui appelle cette méthode coûteuse ? ». Ce sont deux
            // questions différentes, et l'inverse est la plus difficile à obtenir autrement.
            convert(converter, collapsed, runDir.resolve("async-profiler/flamegraph.html"),
                    List.of("--title", "Profil brut — async-profiler"));
            convert(converter, collapsed, runDir.resolve("async-profiler/flamegraph-inverse.html"),
                    List.of("--reverse", "--title", "Profil inversé — qui appelle quoi"));
        } catch (Exception e) {
            System.out.println("   ⚠️ rendu natif indisponible (" + e.getMessage()
                    + ") — les piles brutes restent dans profil.collapsed");
        }
    }

    private static void convert(Path converter, Path collapsed, Path out, List<String> options)
            throws Exception {
        List<String> cmd = new ArrayList<>(List.of(
                RunSession.javaExecutable(), "-jar", converter.toString(), "-o", "html"));
        cmd.addAll(options);
        cmd.add(collapsed.toString());
        cmd.add(out.toString());
        exec(cmd);
    }

    /**
     * Deux rendus depuis la même mesure : le rapport complet, et un rapport <b>ciblé</b>
     * restreint aux classes qui ont réellement tourné. Sur un vrai projet, le second est
     * souvent le seul lisible — le premier liste des milliers de classes hors sujet.
     */
    private static void renderCoverage(Config config, Toolbox tools, Path runDir) throws Exception {
        Path exec = runDir.resolve("jacoco/jacoco.exec");
        if (!Files.isRegularFile(exec)) {
            System.out.println("   ⚠️ aucune donnée de couverture — l'application a-t-elle démarré ?");
            return;
        }
        Path cli = tools.jacocoCli();
        Path html = runDir.resolve("jacoco/html");
        Files.createDirectories(html);

        List<String> cmd = new ArrayList<>(List.of(
                RunSession.javaExecutable(), "-jar", cli.toString(), "report", exec.toString(),
                "--html", html.toString(),
                "--xml", html.resolve("jacoco.xml").toString(),
                "--csv", html.resolve("jacoco.csv").toString(),
                "--name", "Runtime X-Ray", "--quiet"));
        // Une entrée --classfiles par répertoire ou jar : l'option est répétable, c'est le
        // mécanisme prévu par l'outil pour analyser plusieurs sources de bytecode.
        for (Path entry : config.classesPaths()) {
            cmd.add("--classfiles");
            cmd.add(entry.toString());
        }
        for (Path src : sourceRoots(config)) {
            cmd.add("--sourcefiles");
            cmd.add(src.toString());
        }
        exec(cmd);

        // Rapport ciblé : on ne présente à la CLI que les classes ayant au moins une
        // instruction couverte. C'est le mécanisme natif de l'outil, pas un filtre maison.
        Coverage coverage = Coverage.parse(html.resolve("jacoco.xml"), config.hidden());
        Path staging = runDir.resolve("classes-executees");
        int kept = stageExecutedClasses(coverage, config.classesPaths(), staging);
        if (kept == 0) {
            return;
        }
        Path focused = runDir.resolve("jacoco-focused/html");
        Files.createDirectories(focused);
        List<String> focusedCmd = new ArrayList<>(List.of(
                RunSession.javaExecutable(), "-jar", cli.toString(), "report", exec.toString(),
                "--classfiles", staging.toString(),
                "--html", focused.toString(),
                "--name", "Code réellement exécuté", "--quiet"));
        for (Path src : sourceRoots(config)) {
            focusedCmd.add("--sourcefiles");
            focusedCmd.add(src.toString());
        }
        exec(focusedCmd);
        System.out.println("   " + kept + " classes exécutées retenues pour le rapport ciblé");
    }

    @SuppressWarnings("unchecked")
    private static int stageExecutedClasses(Coverage coverage, List<Path> classesPaths, Path staging)
            throws IOException {
        if (Files.exists(staging)) {
            deleteRecursively(staging);
        }
        int kept = 0;
        for (Map.Entry<String, Object> entry : coverage.packages.entrySet()) {
            for (Object o : (List<Object>) entry.getValue()) {
                Map<String, Object> cls = (Map<String, Object>) o;
                if (((Number) cls.get("covered")).intValue() == 0) {
                    continue;
                }
                String name = cls.get("name") + ".class";
                Path target = staging.resolve(name);
                if (copyClassBytes(classesPaths, name, target)) {
                    kept++;
                }
            }
        }
        return kept;
    }

    /**
     * Recopie une classe depuis la première entrée qui la contient, répertoire ou jar.
     *
     * <p>L'ordre des entrées fait foi, comme un chemin de classe : si la même classe existe
     * à deux endroits, c'est la première qui gagne — la JVM aurait chargé celle-là.
     */
    static boolean copyClassBytes(List<Path> classesPaths, String name, Path target)
            throws IOException {
        for (Path entry : classesPaths) {
            if (Files.isDirectory(entry)) {
                Path source = entry.resolve(name);
                if (Files.isRegularFile(source)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
            } else if (Files.isRegularFile(entry) && copyFromArchive(entry, name, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cherche une classe dans une archive, <b>y compris dans les archives qu'elle contient</b>.
     *
     * <p>Un jar applicatif moderne n'est pas un sac de classes : Spring Boot range le code
     * sous {@code BOOT-INF/classes/} et ses dépendances en jar sous {@code BOOT-INF/lib/}.
     * Ne regarder qu'au premier niveau reviendrait à ne rien trouver dans le cas le plus
     * courant. L'outil de couverture, lui, descend — le rapport ciblé doit faire pareil,
     * sinon les deux rapports ne parlent pas du même code.
     *
     * <p>Une seule descente : au-delà, on ne connaît pas de format réel qui l'exige, et une
     * récursion sans borne sur des archives est un bon moyen de ne jamais s'arrêter.
     */
    private static boolean copyFromArchive(Path archive, String name, Path target) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String candidate : List.of(name, "BOOT-INF/classes/" + name,
                    "WEB-INF/classes/" + name, "classes/" + name)) {
                ZipEntry ze = zip.getEntry(candidate);
                if (ze != null) {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = zip.getInputStream(ze)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return true;
                }
            }
            for (ZipEntry nested : zip.stream().filter(z -> z.getName().endsWith(".jar")).toList()) {
                Path tmp = Files.createTempFile("rx-nested-", ".jar");
                try {
                    try (InputStream in = zip.getInputStream(nested)) {
                        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (copyFromArchiveFlat(tmp, name, target)) {
                        return true;
                    }
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
        } catch (IOException e) {
            // Une archive illisible ne doit pas faire tomber l'analyse : les autres entrées
            // sont essayées, et la classe manquera simplement au rapport ciblé.
        }
        return false;
    }

    /** Le niveau du dessous : on y cherche la classe, sans redescendre plus loin. */
    private static boolean copyFromArchiveFlat(Path archive, String name, Path target) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry ze = zip.getEntry(name);
            if (ze == null) return false;
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(ze)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeContext(Config config, Path runDir, String uuid, String name,
                                     RunSession session) throws IOException {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("uuid", uuid);
        // Le nom n'est enregistré que s'il a été DONNÉ. Écrire ici le libellé de repli
        // — « exécution du 21/08 à 00:41 » — reviendrait à faire passer une commodité
        // d'affichage pour une intention de l'opérateur, et la vue ne pourrait plus dire
        // laquelle des deux elle montre.
        ctx.put("nomOrigine", config.runName.isBlank() ? null : config.runName);
        ctx.putAll(config.describe());
        ctx.put("debut", session.startedAt);
        ctx.put("fin", session.endedAt);
        ctx.put("dureeSecondes", session.durationSeconds);
        ctx.put("statut", session.status);
        ctx.put("machine", hostname());
        ctx.put("utilisateur", System.getProperty("user.name", ""));
        ctx.put("systeme", System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " (" + System.getProperty("os.arch") + ")");
        ctx.put("processeurs", Runtime.getRuntime().availableProcessors());
        ctx.put("java", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));
        ctx.put("javaHome", System.getProperty("java.home", ""));
        ctx.put("repertoireTravail", Path.of("").toAbsolutePath().toString());
        Files.writeString(runDir.resolve("run-context.json"), Json.write(ctx), StandardCharsets.UTF_8);
    }

    /**
     * Réécrit chaque exécution dans les formats demandés, pour qu'un autre outil la lise.
     *
     * <p>L'export porte sur <b>toutes</b> les exécutions présentes, y compris celles d'hier :
     * c'est le même geste que l'assemblage de la vue, et rien ne justifierait de servir un
     * format à une exécution et pas à sa voisine.
     */
    private static void exportRuns(Config config, Path outDir) throws Exception {
        Set<Exports.Format> formats = Exports.Format.parse(config.exportFormats);
        System.out.println("▶ Export vers " + formats.stream().map(f -> f.option).sorted()
                .collect(java.util.stream.Collectors.joining(", ")));
        Path runs = outDir.resolve("runs");
        if (!Files.isDirectory(runs)) return;
        try (var dirs = Files.list(runs)) {
            for (Path run : dirs.filter(Files::isDirectory).sorted().toList()) {
                for (Path written : Exports.write(run, formats, 1, config.watchCount)) {
                    System.out.println("   " + written + " ("
                            + Files.size(written) / 1024 + " Ko)");
                }
            }
        }
    }

    // ------------------------------------------------------------------- divers

    private static void printAgentOptions(Config config, Toolbox tools) throws Exception {
        require(!config.classesDir.isBlank() || !config.outDir.isBlank(),
                "--out (ou --classes) est nécessaire pour placer les fichiers de mesure");
        Path runDir = Path.of(config.outDir, "runs", "manuel");
        Files.createDirectories(runDir.resolve("jacoco"));
        Files.createDirectories(runDir.resolve("async-profiler"));
        String options = new RunSession(config, tools, runDir).agentOptions();
        System.out.println();
        System.out.println("Options à ajouter à N'IMPORTE QUELLE ligne de commande Java :");
        System.out.println();
        System.out.println("  " + options);
        System.out.println();
        System.out.println("Ou, sans toucher à la ligne de commande, par l'environnement :");
        System.out.println();
        System.out.println("  export JAVA_TOOL_OPTIONS=\"" + options + "\"");
        System.out.println();
        System.out.println("Puis, une fois l'application arrêtée, assembler la vue :");
        System.out.println();
        System.out.println("  java -jar runtime-xray.jar --report-only --out " + config.outDir
                + " --classes <répertoire de classes> --sources <sources>");
    }

    private static void announceTemplate(Path file, String relaunch) {
        System.out.println("Fichier de configuration généré : " + file);
        System.out.println();
        System.out.println("   Il contient les valeurs par défaut, des commentaires et des exemples.");
        System.out.println("   Renseigner au minimum JAVA_CMD, puis relancer :");
        System.out.println();
        System.out.println("     java -jar runtime-xray.jar " + relaunch);
    }

    /** Les options de la ligne de commande l'emportent sur le fichier. */
    private static void merge(Config base, Config overrides) {
        if (!overrides.javaCommand.isBlank()) base.javaCommand = overrides.javaCommand;
        if (!overrides.rootMethod.isBlank()) base.rootMethod = overrides.rootMethod;
        if (!overrides.classesDir.isBlank()) base.classesDir = overrides.classesDir;
        if (!overrides.hiddenPackages.isBlank()) base.hiddenPackages = overrides.hiddenPackages;
        if (!overrides.sourceDirs.isBlank()) base.sourceDirs = overrides.sourceDirs;
        if (!overrides.classFilter.isBlank()) base.classFilter = overrides.classFilter;
        if (!overrides.runName.isBlank()) base.runName = overrides.runName;
        if (!"runtime-xray-out".equals(overrides.outDir)) base.outDir = overrides.outDir;
        if (!overrides.captureValues) base.captureValues = false;
    }

    private static List<Path> sourceRoots(Config config) {
        List<Path> roots = new ArrayList<>();
        for (String s : config.sourceDirs.split("[:,]")) {
            if (!s.isBlank()) roots.add(Path.of(s.trim()));
        }
        return roots;
    }

    private static String slug(String name) {
        if (name.isBlank()) return "";
        String s = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "" : "-" + (s.length() > 60 ? s.substring(0, 60) : s);
    }

    private static String hostname() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv().getOrDefault("HOSTNAME", "inconnue");
        }
    }

    private static void exec(List<String> cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        if (!p.waitFor(10, TimeUnit.MINUTES)) {
            p.destroy();
            throw new IOException("commande interrompue : " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IOException("échec (code " + p.exitValue() + ") : " + String.join(" ", cmd));
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // Un reliquat ne justifie pas d'interrompre l'analyse.
                }
            });
        }
    }

    private static void require(boolean condition, String message) throws IOException {
        if (!condition) {
            throw new IOException(message);
        }
    }

    private static void usage() {
        System.out.println("""
                Runtime X-Ray — voir ce qu'une exécution Java a réellement fait.

                  java -jar runtime-xray.jar --config mon-projet.conf
                  java -jar runtime-xray.jar --java "java -jar app.jar" --classes target/classes \\
                                             --root "com.exemple.Moteur::calculer" --sources src/main/java

                Options
                  --config <fichier>   Lit les réglages depuis un fichier. GÉNÈRE un gabarit
                                       commenté si le fichier n'existe pas.
                  --java "<commande>"  La commande qui lance l'application, telle quelle.
                  --classes <chemins>  Répertoires de .class et/ou jar, séparés par ':'.
                                       Obligatoire.
                  --hide "<paquets>"   Paquets à taire comme le JDK, ex. "org.slf4j".
                  --root "<C::m>"      Méthode racine : celle dont on capture les valeurs.
                  --sources <dirs>     Répertoires de sources, séparés par ':'.
                  --filter "<motif>"   Restreint les mesures de temps, ex. "com/exemple/*".
                  --out <dir>          Répertoire de sortie (défaut : runtime-xray-out).
                  --name "<texte>"     Nom de cette exécution ; elles s'accumulent et la vue
                                       permet de passer de l'une à l'autre.
                  --attach-after <s>   Délai avant l'inspection des valeurs (défaut : 8).
                  --max-seconds <s>    Garde-fou sur la durée (défaut : 600).
                  --no-values          N'inspecte pas les valeurs : mesures de temps exactes.
                  --niveau <n>         Jusqu'où observer : couverture (JaCoCo seul), arbre
                                       (+ échantillonnage), complet (+ valeurs). Défaut :
                                       complet. Le levier à baisser sur un gros code.
                  --cover "<motifs>"   Classes que JaCoCo instrumente, ex. "com.exemple.*".
                                       Sans lui, tout ce que la JVM charge est instrumenté.
                  --interval <ms>      Intervalle d'échantillonnage des piles (défaut : 1).
                  --print-options      N'exécute rien : affiche les options JVM à ajouter à une
                                       ligne de commande quelconque, puis sortez par --report-only.
                  --repo <url>         Dépôt Maven d'où tirer les composants (miroir interne).
                  --report-only        Assemble la vue depuis des mesures déjà collectées.
                  --serve [port]       Sert le rapport (défaut : 8787) et laisse la page
                                       écrire ses annotations à côté des exécutions, puis la
                                       régénère. Plusieurs personnes peuvent annoter à la fois.
                  --serve-host <hôte>  Interface d'écoute (défaut : 127.0.0.1). Mettre
                                       0.0.0.0 pour un serveur partagé — sans
                                       authentification : à placer derrière un filtrage.
                  --export <formats>   Réécrit les mesures pour d'autres outils : perf,
                                       cpuprofile, lcov, valeurs — ou « tout ». Les fichiers
                                       vont dans <exécution>/exports/.
                """);
    }
}
