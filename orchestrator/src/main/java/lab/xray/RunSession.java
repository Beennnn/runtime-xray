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

    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.FRENCH);

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
        System.out.println("▶ Exécution de l'application");
        System.out.println("   " + config.javaCommand);
        Process process = pb.start();

        try {
            // Relevé pendant que la JVM vit : après, ses arguments ne sont plus lisibles.
            // C'est ce qui permet de ne pas réclamer --classes — voir ClassSources.
            observeJvmArguments(process);
            if (config.valuesWanted() && !config.rootMethod.isBlank()) {
                inspectValues(process);
            }
            System.out.println("▶ Attente de la fin de l'exécution");
            boolean finished = process.waitFor(config.maxSeconds, TimeUnit.SECONDS);
            status = finished
                    ? "terminée normalement (code " + process.exitValue() + ")"
                    : "interrompue après " + config.maxSeconds + " s (garde-fou)";
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
            System.out.println("   niveau « couverture » : pas d'échantillonnage des piles");
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
            System.out.println("   ⚠️ mesures de temps indisponibles sur cette plateforme — "
                    + "couverture et valeurs restent disponibles");
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
                System.out.println("   ⚠️ l'application s'est terminée avant l'attachement ("
                        + config.attachAfterSeconds + " s) : valeurs non capturées.");
                System.out.println("      Augmenter la charge de travail, ou baisser "
                        + "--attach-after. La couverture et les temps, eux, sont complets.");
            } else {
                System.out.println("   ⚠️ aucune JVM à suivre parmi les processus lancés : "
                        + "valeurs non capturées.");
                System.out.println("      La commande n'en démarre peut-être pas une "
                        + "directement — voir --print-options pour s'attacher à la vôtre.");
            }
            return;
        }
        long pid = jvm.get().pid();
        String rootClass = config.rootClass();
        System.out.println("▶ Inspection des valeurs sur " + config.rootMethod + " (pid " + pid + ")");

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
