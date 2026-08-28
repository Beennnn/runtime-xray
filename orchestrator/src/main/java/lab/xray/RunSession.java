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
 * One run under observation: the application is launched, attached to while it works, and
 * waited for.
 *
 * <p>The agents are injected through {@code JAVA_TOOL_OPTIONS}. That is what makes it
 * possible to impose nothing on the way Java is launched: the variable is read by every
 * JVM at start-up, whether the command is a {@code java -jar}, an {@code mvn exec:java} or
 * a home-made script.
 */
public final class RunSession {

    /** The redraw rate: slow enough to cost nothing, brisk enough to look alive. */
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

    /** Last processor time read for each process of the observed application. */
    private final java.util.Map<Long, Duration> cpuPerProcess = new java.util.HashMap<>();

    /** The real arguments of the observed JVM, read while it was running. */
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
            // Read while the JVM is alive: afterwards its arguments are no longer
            // readable. That is what makes --classes unnecessary — see ClassSources.
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
        // The agents write their files when the JVM stops: we give them the time.
        Thread.sleep(1500);
        endedAt = HUMAN.format(LocalDateTime.now());
        durationSeconds = Duration.between(start, LocalDateTime.now()).toSeconds();
    }

    /**
     * Waits for the application to finish, while showing that it is alive.
     *
     * <p>The wait itself is unchanged — same safety limit, same verdict; only the silence
     * changes.
     *
     * <p>The regular polling first served to redraw the terminal band, and without a
     * terminal we fell back to a blocking wait. It now also serves to write
     * {@code progression.jsonl}, which is written <b>always</b>: it is precisely when there
     * is no terminal — a pipe, an integration log, nested scripts — that nobody sees
     * anything any more, and that the file is the only answer. The polling costs no more
     * than reading counters the system keeps anyway.
     *
     * <p>The time limit is counted on the clock and not on the number of turns: a turn can
     * last longer than its interval when the machine is loaded, and {@code MAX_SECONDS} is
     * a promise made to the operator.
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
                // One single load calculation, the band's: the file carries its result
                // rather than redoing it.
                double cores = progress.tick(Duration.ofMillis(elapsed), cpu, bytes);
                follow.tick(Duration.ofMillis(elapsed), cpu, bytes, cores);
            }
        }
    }

    /**
     * The processor time consumed by the observed application, descendants included.
     *
     * <p>It is already counted by the system for each process: reading it measures nothing
     * more and does not go near the observed JVM. Descendants count because the command
     * launched is often a launcher — {@code mvn}, a script, a wrapper — whose real work
     * happens in a child.
     *
     * <p>We keep the last reading of each process, including the ones that are gone.
     * Without that the total would <b>go backwards</b> as each child dies, and a very busy
     * phase would read as a dead one: exactly the opposite of what the band must say. A
     * reused process number would be undercounted; over the length of one observed run,
     * the confusion is theoretical and the consequence is one paler square.
     */
    private Duration cpuTime(Process process) {
        reading(process.toHandle());
        process.descendants().forEach(this::reading);
        Duration total = Duration.ZERO;
        for (Duration each : cpuPerProcess.values()) total = total.plus(each);
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
     * Notes where the observed JVM loads its code from, while it is still alive.
     *
     * <p>We question the system rather than the written command: a start-up script, a
     * Gradle wrapper or a home-made launcher hide the real command line, whereas the
     * process carries it. The read is bounded in time — an application that has not started
     * within a few seconds will not start any better for waiting — and a missing reading is
     * not a breakdown: it simply falls back on the explicit setting.
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

    /** The JVM options to add to a command line. Public: the tool must be able to invite
     *  itself into a launch one does not control (a service, a container, an application
     *  server), without going through this program. */
    public String agentOptions() throws IOException, InterruptedException {
        StringBuilder sb = new StringBuilder();
        sb.append("-javaagent:").append(tools.jacocoAgent().toAbsolutePath())
          .append("=destfile=").append(runDir.resolve("jacoco/jacoco.exec").toAbsolutePath());
        // Restricting the instrumentation is the first lever on a large codebase: without
        // it, JaCoCo instruments every class loaded, dependencies included.
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
            // Without these two options, the samples are attributed to the wrong line number.
            sb.append(" -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints");
        } else {
            System.out.println("   ⚠️ timing measurements unavailable on this platform — "
                    + "coverage and values remain available");
        }
        return sb.toString();
    }

    /**
     * Attaches to the JVM while it works and records the values received by the methods of
     * the root class.
     */
    private void inspectValues(Process process) throws IOException, InterruptedException {
        Thread.sleep(config.attachAfterSeconds * 1000L);
        Optional<ProcessHandle> jvm = findJvm(process);
        if (jvm.isEmpty()) {
            // Two very different causes behind the same absence, and the operator should
            // not have to guess which: either the application finished before we attached —
            // by far the most frequent case, and it is fixed with one option — or the
            // command starts no JVM that can be followed. Saying "JVM not found" without
            // telling the two apart sent people hunting for an installation problem where
            // there was only a program that was too short.
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

        // The command files accept NO comment at all: a line starting with # would be
        // sent as a command. And a `stop` after a `trace` would close the session before
        // the calls are collected.
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
        // Without a valid PID the launcher waits for interactive input forever: we bound it.
        if (!p.waitFor(limitSeconds, TimeUnit.SECONDS)) {
            p.destroy();
            p.waitFor(5, TimeUnit.SECONDS);
        }
    }

    /**
     * The JVM to observe. A {@code java -jar} command gives the right process directly; an
     * {@code mvn} or a {@code gradlew} starts the JVM further down.
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
