package lab.xray;

import lab.xray.json.Json;
import lab.xray.report.Coverage;
import lab.xray.report.Dashboard;

import java.io.IOException;
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
        try {
            System.exit(run(args));
        } catch (Exception e) {
            System.err.println();
            System.err.println("Échec : " + e.getMessage());
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        Config config = new Config();
        Path configFile = null;
        boolean printOptionsOnly = false;
        boolean reportOnly = false;

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
                case "--out" -> config.outDir = args[++i];
                case "--name" -> config.runName = args[++i];
                case "--repo" -> config.mavenRepo = args[++i];
                case "--attach-after" -> config.attachAfterSeconds = Integer.parseInt(args[++i]);
                case "--max-seconds" -> config.maxSeconds = Integer.parseInt(args[++i]);
                case "--no-values" -> config.captureValues = false;
                case "--print-options" -> printOptionsOnly = true;
                case "--report-only" -> reportOnly = true;
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
        } else if (config.javaCommand.isBlank() && config.classesDir.isBlank()) {
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
        require(!config.classesDir.isBlank(), "--classes est obligatoire");
        require(Files.isDirectory(Path.of(config.classesDir)),
                "répertoire de classes introuvable : " + config.classesDir);

        Path outDir = Path.of(config.outDir);
        Files.createDirectories(outDir);

        if (!reportOnly) {
            collect(config, tools, outDir);
        }

        System.out.println("▶ Assemblage de la vue");
        Path page = Dashboard.build(outDir, sourceRoots(config), config.watchCount);
        System.out.println();
        System.out.println("Terminé — ouvrir : " + page);
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

        System.out.println("▶ Rendu de la couverture");
        renderCoverage(config, tools, runDir);

        writeContext(config, runDir, uuid, name, session);

        System.out.println();
        System.out.println("   Pour renommer cette exécution plus tard, ajouter dans "
                + outDir.resolve("noms.json") + " :");
        System.out.println("     { \"" + uuid + "\": \"un nom plus parlant\" }");
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
                "--classfiles", config.classesDir,
                "--html", html.toString(),
                "--xml", html.resolve("jacoco.xml").toString(),
                "--csv", html.resolve("jacoco.csv").toString(),
                "--name", "Runtime X-Ray", "--quiet"));
        for (Path src : sourceRoots(config)) {
            cmd.add("--sourcefiles");
            cmd.add(src.toString());
        }
        exec(cmd);

        // Rapport ciblé : on ne présente à la CLI que les classes ayant au moins une
        // instruction couverte. C'est le mécanisme natif de l'outil, pas un filtre maison.
        Coverage coverage = Coverage.parse(html.resolve("jacoco.xml"));
        Path staging = runDir.resolve("classes-executees");
        int kept = stageExecutedClasses(coverage, Path.of(config.classesDir), staging);
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
    private static int stageExecutedClasses(Coverage coverage, Path classesDir, Path staging)
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
                Path source = classesDir.resolve(cls.get("name") + ".class");
                if (!Files.isRegularFile(source)) {
                    continue;
                }
                Path target = staging.resolve(cls.get("name") + ".class");
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                kept++;
            }
        }
        return kept;
    }

    private static void writeContext(Config config, Path runDir, String uuid, String name,
                                     RunSession session) throws IOException {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("uuid", uuid);
        ctx.put("nomOrigine", name);
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
        System.out.println("   Renseigner au minimum JAVA_CMD et CLASSES_DIR, puis relancer :");
        System.out.println();
        System.out.println("     java -jar runtime-xray.jar " + relaunch);
    }

    /** Les options de la ligne de commande l'emportent sur le fichier. */
    private static void merge(Config base, Config overrides) {
        if (!overrides.javaCommand.isBlank()) base.javaCommand = overrides.javaCommand;
        if (!overrides.rootMethod.isBlank()) base.rootMethod = overrides.rootMethod;
        if (!overrides.classesDir.isBlank()) base.classesDir = overrides.classesDir;
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
                  --classes <dir>      Répertoire des .class compilés. Obligatoire.
                  --root "<C::m>"      Méthode racine : celle dont on capture les valeurs.
                  --sources <dirs>     Répertoires de sources, séparés par ':'.
                  --filter "<motif>"   Restreint les mesures de temps, ex. "com/exemple/*".
                  --out <dir>          Répertoire de sortie (défaut : runtime-xray-out).
                  --name "<texte>"     Nom de cette exécution ; elles s'accumulent et la vue
                                       permet de passer de l'une à l'autre.
                  --attach-after <s>   Délai avant l'inspection des valeurs (défaut : 8).
                  --max-seconds <s>    Garde-fou sur la durée (défaut : 600).
                  --no-values          N'inspecte pas les valeurs : mesures de temps exactes.
                  --print-options      N'exécute rien : affiche les options JVM à ajouter à une
                                       ligne de commande quelconque, puis sortez par --report-only.
                  --repo <url>         Dépôt Maven d'où tirer les composants (miroir interne).
                  --report-only        Assemble la vue depuis des mesures déjà collectées.
                """);
    }
}
