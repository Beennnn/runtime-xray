package lab.xray;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Une exécution sous observation : on lance l'application, on s'y attache pendant qu'elle
 * travaille, on attend qu'elle finisse.
 *
 * <p>Les agents sont injectés par {@code JAVA_TOOL_OPTIONS}. C'est ce qui permet de ne
 * rien imposer sur la façon de lancer Java : la variable est lue par toute JVM à son
 * démarrage, que la commande soit un {@code java -jar}, un {@code mvn exec:java} ou un
 * script maison.
 */
public final class RunSession {

    /** Le rythme de redessin : assez lent pour ne rien coûter, assez vif pour vivre. */
    private static final long INTERVAL_MS = 1000L;

    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final Config config;
    private final Toolbox tools;
    private final Path runDir;

    public String startedAt = "";
    public String endedAt = "";
    public long durationSeconds;
    public String status = "";

    public RunSession(Config config, Toolbox tools, Path runDir) {
        this.config = config;
        this.tools = tools;
        this.runDir = runDir;
    }

    /** Dernier temps processeur relevé pour chaque processus de l'application observée. */
    private final java.util.Map<Long, Duration> cpuPerProcess = new java.util.HashMap<>();

    /** Les arguments réels de la JVM observée, relevés pendant qu'elle tournait. */
    public final List<String> jvmArguments = new ArrayList<>();

    public void execute() throws IOException, InterruptedException {
        Files.createDirectories(runDir.resolve("jacoco"));
        Files.createDirectories(runDir.resolve("async-profiler"));
        Files.createDirectories(runDir.resolve("arthas"));

        String agentOptions = agentOptions();

        ProcessBuilder pb = new ProcessBuilder(CommandLine.toProcessArgs(config.javaCommand));
        pb.environment().put("JAVA_TOOL_OPTIONS", agentOptions);
        pb.redirectErrorStream(true);
        pb.redirectOutput(runDir.resolve("execution.log").toFile());

        LocalDateTime start = LocalDateTime.now();
        startedAt = HUMAN.format(start);
        System.out.println("▶ Running the application");
        System.out.println("   " + config.javaCommand);
        Process process = pb.start();

        try {
            // Relevé pendant que la JVM vit : après, ses arguments ne sont plus lisibles.
            // C'est ce qui permet de ne pas réclamer --classes — voir ClassSources.
            observeJvmArguments(process);
            if (config.valuesWanted() && !config.rootMethod.isBlank()) {
                inspectValues(process);
            }
            System.out.println("▶ Waiting for the run to finish");
            boolean finished = await(process);
            status = finished
                    ? "ended normally (code " + process.exitValue() + ")"
                    : "stopped after " + config.maxSeconds + " s (safety limit)";
            if (!finished) process.destroy();
        } finally {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
        }
        // Les agents écrivent leurs fichiers à l'arrêt de la JVM : on leur laisse le temps.
        Thread.sleep(1500);
        endedAt = HUMAN.format(LocalDateTime.now());
        durationSeconds = Duration.between(start, LocalDateTime.now()).toSeconds();
    }

    /**
     * Attend la fin de l'application en montrant qu'elle vit.
     *
     * <p>L'attente reste la même — même garde-fou, même verdict ; seul le silence change.
     *
     * <p>Le sondage régulier servait d'abord à redessiner la bande du terminal, et on
     * retombait sans terminal sur l'attente bloquante. Il sert désormais aussi à écrire
     * {@code progression.jsonl}, qui, lui, s'écrit <b>toujours</b> : c'est justement quand
     * il n'y a pas de terminal — un tuyau, un journal d'intégration, des scripts imbriqués
     * — que personne ne voit plus rien, et que le fichier est la seule réponse. Le sondage
     * ne coûte que la lecture de compteurs que le système tient de toute façon.
     *
     * <p>Le temps limite se compte sur l'horloge et non sur le nombre de tours : un tour
     * peut durer plus que son intervalle si la machine est chargée, et {@code MAX_SECONDS}
     * est une promesse faite à l'opérateur.
     */
    private boolean await(Process process) throws InterruptedException {
        Progress progress = Progress.of(System.out);
        Path log = runDir.resolve("execution.log");
        long limit = config.maxSeconds * 1000L;
        long start = System.nanoTime();
        try (Follow follow = Follow.open(Path.of(config.outDir), runDir,
                runDir.getFileName().toString(), config.javaCommand, config.followPort)) {
            while (true) {
                if (process.waitFor(INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                    progress.end();
                    follow.end("ended, code " + process.exitValue(),
                            (System.nanoTime() - start) / 1_000_000_000L);
                    return true;
                }
                long elapsed = (System.nanoTime() - start) / 1_000_000L;
                if (elapsed >= limit) {
                    progress.end();
                    follow.end("stopped by the safety limit", elapsed / 1000L);
                    return false;
                }
                Duration cpu = cpuTime(process);
                long bytes = sizeOf(log);
                // Un seul calcul de charge, celui de la bande : le fichier en porte le
                // résultat plutôt que de le refaire.
                double cores = progress.tick(Duration.ofMillis(elapsed), cpu, bytes);
                follow.tick(Duration.ofMillis(elapsed), cpu, bytes, cores);
            }
        }
    }

    /**
     * Le temps processeur consommé par l'application observée, descendants compris.
     *
     * <p>Il est déjà compté par le système pour chaque processus : le lire ne mesure rien
     * de plus et n'approche pas la JVM observée. Les descendants comptent parce que la
     * commande lancée est souvent un lanceur — {@code mvn}, un script, un wrapper — dont le
     * travail réel se passe dans un fils.
     *
     * <p>On garde le dernier relevé de chaque processus, y compris disparu. Sans cela le
     * cumul <b>reculerait</b> à la mort de chaque fils, et une phase très active se
     * lirait comme une phase morte : c'est exactement l'inverse de ce que la bande doit
     * dire. Un numéro de processus réattribué serait sous-compté ; sur la durée d'une
     * exécution observée, la confusion est théorique et la conséquence, un carré plus pâle.
     */
    private Duration cpuTime(Process process) {
        reading(process.toHandle());
        process.descendants().forEach(this::reading);
        Duration total = Duration.ZERO;
        for (Duration share : cpuPerProcess.values()) total = total.plus(share);
        return total;
    }

    private void reading(ProcessHandle processes) {
        processes.info().totalCpuDuration().ifPresent(cpu -> cpuPerProcess.merge(
                processes.pid(), cpu, (previous, current) ->
                        current.compareTo(previous) > 0 ? current : previous));
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException absent) {
            return 0;
        }
    }

    /**
     * Note d'où la JVM observée charge son code, tant qu'elle est en vie.
     *
     * <p>On interroge le système plutôt que la commande écrite : un script de démarrage, un
     * wrapper Gradle ou un lanceur maison cachent la vraie ligne de commande, alors que le
     * processus, lui, la porte. La lecture est bornée dans le temps — une application qui
     * n'a pas démarré en quelques secondes ne démarrera pas mieux si l'on attend, et
     * l'absence de relevé n'est pas une panne : elle renvoie simplement au réglage explicite.
     */
    private void observeJvmArguments(Process process) throws InterruptedException {
        for (int i = 0; i < 20 && jvmArguments.isEmpty(); i++) {
            Optional<ProcessHandle> jvm = findJvm(process);
            if (jvm.isPresent()) {
                jvm.get().info().arguments()
                        .ifPresent(a -> jvmArguments.addAll(List.of(a)));
                if (!jvmArguments.isEmpty()) return;
            }
            if (!process.isAlive()) return;
            Thread.sleep(250);
        }
    }

    /** Les options JVM à ajouter à une ligne de commande. Publiques : l'outil doit pouvoir
     *  s'inviter dans un lancement qu'on ne contrôle pas (service, conteneur, serveur
     *  d'application), sans passer par ce programme. */
    public String agentOptions() throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("-javaagent:").append(tools.jacocoAgent().toAbsolutePath())
          .append("=destfile=").append(runDir.resolve("jacoco/jacoco.exec").toAbsolutePath());
        // Restreindre l'instrumentation est le premier levier sur un gros code : sans cela,
        // JaCoCo instrumente chaque classe chargée, dépendances comprises.
        if (!config.coverIncludes.isBlank()) {
            sb.append(",includes=").append(config.coverIncludes.trim());
        }

        if (!config.profileWanted()) {
            System.out.println("   level \"couverture\": no stack sampling");
        } else if (tools.asyncProfilerAvailable()) {
            StringBuilder async = new StringBuilder("start,event=itimer,interval=")
                    .append(Math.max(1, config.sampleIntervalMs))
                    .append("ms,collapsed,file=")
                    .append(runDir.resolve("async-profiler/profil.collapsed").toAbsolutePath());
            if (!config.effectiveFilter().isBlank()) {
                async.append(",include=").append(config.effectiveFilter());
            }
            sb.append(" -agentpath:").append(tools.asyncProfilerLibrary().toAbsolutePath())
              .append('=').append(async);
            // Sans ces deux options, les relevés sont attribués au mauvais numéro de ligne.
            sb.append(" -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints");
        } else {
            System.out.println("   ⚠️ timing measurements unavailable on this platform — "
                    + "coverage and values remain available");
        }
        return sb.toString();
    }

    /**
     * S'attache à la JVM pendant qu'elle travaille et relève les valeurs reçues par les
     * méthodes de la classe racine.
     */
    private void inspectValues(Process process) throws IOException, InterruptedException {
        Thread.sleep(config.attachAfterSeconds * 1000L);
        Optional<ProcessHandle> jvm = findJvm(process);
        if (jvm.isEmpty()) {
            // Deux causes très différentes derrière la même absence, et l'opérateur n'a pas
            // à deviner laquelle : ou l'application a fini avant qu'on ne s'attache — de
            // loin le cas le plus fréquent, et il se corrige en une option — ou la commande
            // ne démarre pas de JVM qu'on puisse suivre. Dire « JVM introuvable » sans
            // distinguer les deux envoyait chercher un problème d'installation là où il n'y
            // avait qu'un programme trop court.
            if (!process.isAlive()) {
                System.out.println("   ⚠️ the application finished before attachment ("
                        + config.attachAfterSeconds + " s): values not captured.");
                System.out.println("      Increase the workload, or lower "
                        + "--attach-after. Coverage and timings, themselves, are complete.");
            } else {
                System.out.println("   ⚠️ no JVM to follow among the launched processes: "
                        + "values not captured.");
                System.out.println("      The command may not start one "
                        + "directly — see --print-options to attach to your own.");
            }
            return;
        }
        long pid = jvm.get().pid();
        String rootClass = config.rootClass();
        System.out.println("▶ Inspecting values on " + config.rootMethod + " (pid " + pid + ")");

        // Les fichiers de commandes n'acceptent AUCUN commentaire : une ligne commençant
        // par # serait envoyée comme commande. Et un `stop` après un `trace` fermerait la
        // session avant que les appels soient collectés.
        Path watch = runDir.resolve("arthas/watch.cmd");
        Files.writeString(watch, "watch " + rootClass + " * '{params, returnObj}' -n "
                + config.watchCount + " -x 2\nstop\n", StandardCharsets.UTF_8);
        Path trace = runDir.resolve("arthas/trace.cmd");
        Files.writeString(trace, "trace " + config.rootMethod.replace("::", " ")
                + " -n " + config.traceCount + "\n", StandardCharsets.UTF_8);

        runAttached(pid, watch, runDir.resolve("arthas/watch-params.txt"), 20);
        runAttached(pid, trace, runDir.resolve("arthas/trace-calltree.txt"), 20);
    }

    private void runAttached(long pid, Path batch, Path output, int limitSeconds)
            throws IOException, InterruptedException {
        Path home = tools.arthasHome();
        List<String> cmd = List.of(
                javaExecutable(), "-jar", home.resolve("arthas-boot.jar").toString(),
                String.valueOf(pid), "--arthas-home", home.toString(),
                "-f", batch.toAbsolutePath().toString());
        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        // Sans PID valide, le lanceur attend une saisie interactive indéfiniment : on borne.
        if (!p.waitFor(limitSeconds, TimeUnit.SECONDS)) {
            p.destroy();
            p.waitFor(5, TimeUnit.SECONDS);
        }
    }

    /**
     * La JVM à observer. Une commande {@code java -jar} donne directement le bon processus ;
     * un {@code mvn} ou un {@code gradlew} lance la JVM en descendant.
     */
    private static Optional<ProcessHandle> findJvm(Process process) {
        if (isJava(process.toHandle())) return Optional.of(process.toHandle());
        return process.descendants().filter(RunSession::isJava).findFirst();
    }

    private static boolean isJava(ProcessHandle handle) {
        return handle.info().command()
                .map(c -> c.endsWith("java") || c.endsWith("java.exe"))
                .orElse(false);
    }

    static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
