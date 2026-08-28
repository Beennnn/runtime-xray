package lab.xray;

import lab.xray.json.Json;
import lab.xray.report.Coverage;
import lab.xray.report.Dashboard;
import lab.xray.report.Exports;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
 * Runtime X-Ray — entry point.
 *
 * <p>One single file to drop, launched by the {@code java} that is already there. That is
 * the whole point of an orchestrator written in Java: the machine that runs the analysed
 * application necessarily has a JDK, but not necessarily bash or Python.
 */
public final class Main {

    private static final String DEFAULT_CONFIG = "runtime-xray.conf";

    public static void main(String[] args) {
        speakUtf8();
        try {
            System.exit(run(args));
        } catch (Exception e) {
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Writes to the console in UTF-8, whatever the machine's encoding.
     *
     * <p>Java picks {@code System.out}'s encoding from the system's. On a machine set to
     * C/POSIX — a container, an integration server, a remote session — that means ASCII,
     * and any message carrying a non-ASCII character loses it there: "Classes analys?ed",
     * "no ?ampling". That is not cosmetic: these are messages one reads to understand what
     * happened, and sometimes searches for in a log.
     */
    private static void speakUtf8() {
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
        String contextQuestion = null;
        java.util.List<String> contextFamilies = java.util.List.of();
        boolean reportOnly = false;
        boolean serve = false;
        int servePort = 8787;
        String serveHost = "127.0.0.1";
        String serveToken = null;
        boolean randomToken = false;

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
                case "--components", "--composants" -> config.componentsDir = args[++i];
                case "--attach-after" -> config.attachAfterSeconds = Integer.parseInt(args[++i]);
                case "--max-seconds" -> config.maxSeconds = Integer.parseInt(args[++i]);
                case "--no-values" -> config.captureValues = false;
                case "--print-options" -> printOptionsOnly = true;
                // The context pack: what one needs in order to answer a question, and
                // nothing more. No network, no provider — see Context.
                case "--context", "--contexte" -> contextQuestion =
                        i + 1 < args.length && !args[i + 1].startsWith("--") ? args[++i] : "";
                // The path scripts take: naming the families rather than having a
                // sentence interpreted, whose result is only reproducible as long as the
                // keywords are left alone.
                case "--families", "--familles" -> contextFamilies =
                        java.util.List.of(args[++i].split("\\s*,\\s*"));
                case "--report-only" -> reportOnly = true;
                case "--export" -> config.exportFormats = args[++i];
                case "--level", "--niveau" -> config.level = args[++i];
                case "--cover" -> config.coverIncludes = args[++i];
                case "--jacoco-reports" -> config.jacocoReports = args[++i];
                case "--interval" -> config.sampleIntervalMs = Integer.parseInt(args[++i]);
                // The port attaches to the option, as for --serve: "--suivi" alone takes
                // the default port, and "--suivi 9100" the one given to it.
                case "--follow", "--suivi" -> {
                    config.followPort = Follow.PORT;
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        config.followPort = Integer.parseInt(args[++i]);
                    }
                }
                case "--serve-host" -> serveHost = args[++i];
                case "--serve-token" -> {
                    // The secret attaches to the option, or stays silent: "--serve-token"
                    // alone draws one at random and shows it. That is the most frequent
                    // case — one wants to close the door, not invent a phrase.
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        serveToken = args[++i];
                    } else {
                        serveToken = Access.randomSecret();
                        randomToken = true;
                    }
                }
                case "--serve" -> {
                    serve = true;
                    // The port attaches to the option when given, and stays silent
                    // otherwise: "--serve 9000" and "--serve --report-only" must both work.
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        servePort = Integer.parseInt(args[++i]);
                    }
                }
                default -> {
                    // The short message, and the pointer — not the whole help. Dumping a
                    // hundred lines on standard output pushes the one line that explains
                    // the problem off the screen, and the user sees only the end of the
                    // help, that is, what does not concern them.
                    System.err.println("runtime-xray: unknown option: " + a);
                    System.err.println("Try \"runtime-xray --help\" for the list of options.");
                    return 2;
                }
            }
        }

        // Reading an already assembled report launches nothing and writes nothing: no
        // configuration to fill in, no --java to supply. Hence this exit before everything
        // else — demanding one for a read would be the same nonsense as generating a
        // template in front of a --report-only.
        if (contextQuestion != null) {
            var pkg = lab.xray.report.Context.of(Path.of(config.outDir),
                    contextQuestion, contextFamilies, lab.xray.report.Context.BUDGET);
            // What was understood goes to standard error, and the pack to standard
            // output: the operator sees the one without the other being polluted when it
            // is redirected — which it almost always is.
            System.err.println(pkg.announcement());
            System.out.print(pkg.text());
            return 0;
        }

        // The configuration file is GENERATED when missing: nobody should have to guess a
        // format. We stop there to let the user fill it in.
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
            // --report-only launches nothing: it reassembles the view from runs already
            // on disk. Demanding a configuration of it would be absurd, and writing a
            // template in its place was more absurd still.
            Path def = Path.of(DEFAULT_CONFIG);
            if (Files.isRegularFile(def)) {
                System.out.println("▶ Configuration read from: " + DEFAULT_CONFIG);
                config = Config.load(def);
            } else {
                Config.writeTemplate(def);
                announceTemplate(def, "");
                return 0;
            }
        }

        Toolbox tools = new Toolbox(config.mavenRepo, config.componentsDir);

        // The "I just want the options to paste" mode: the tool must be able to add
        // itself to any Java command line at all, including one you do not control — a
        // service managed by systemd, a container, an application server.
        if (printOptionsOnly) {
            printAgentOptions(config, tools);
            return 0;
        }

        require(!config.javaCommand.isBlank() || reportOnly, "--java is required");
        // An unknown value stops here rather than falling back on the default. This one
        // comes from a script or a configuration file, never from a sentence typed by
        // someone searching: a script that has it wrong has a defect, and keeping quiet
        // about it would write a report silently different from the one it expects.
        config.jacocoReports = config.jacocoReports.trim().toLowerCase(Locale.ROOT);
        require(Config.JACOCO_REPORTS.contains(config.jacocoReports),
                "unknown value for --jacoco-reports: " + config.jacocoReports
                + " (known: " + String.join(", ", Config.JACOCO_REPORTS) + ")");
        require(Config.LEVELS.contains(Config.level(config.level)),
                "--level expects coverage, tree or full (got: " + config.level + ")");
        // The classes serve to MEASURE. Reassembling a view from existing measurements
        // needs none of them.
        // --classes is no longer required here: the bytecode is deduced from the observed
        // JVM, once it has run. See ClassSources for the order of the sources consulted.
        for (Path entry : config.classesPaths()) {
            require(Files.isDirectory(entry) || Files.isRegularFile(entry),
                    "classes not found: " + entry + " (neither a directory nor a jar)");
        }

        Path outDir = Path.of(config.outDir);
        Files.createDirectories(outDir);
        adoptRecordedSourceRoots(config, outDir);

        if (!reportOnly) {
            collect(config, tools, outDir);
        }

        if (!config.exportFormats.isBlank()) {
            exportRuns(config, outDir);
        }

        mergeCoverage(config, tools, outDir);

        System.out.println("▶ Building the report");
        Path page = Dashboard.build(outDir, sourceRoots(config), config.watchCount,
                config.hidden(), launch(config, tools, sourceRoots(config)));
        sayWhatWasFound(outDir);
        sayTheWeight(page);
        System.out.println();
        System.out.println("Done — open: " + page);

        if (serve) {
            Config served = config;
            String secret = Access.secretRequested(serveToken, System.getenv());
            Access access = secret == null ? Access.open() : Access.withSecret(secret);
            if (randomToken) {
                // Shown once, here and nowhere else: it is written to no file, and the
                // server will not show it again.
                System.out.println();
                System.out.println("▶ Shared secret drawn at random: " + secret);
                System.out.println("   Pass it to whoever needs access to the "
                        + "report.");
            }
            LocalServer.serve(outDir, serveHost, servePort, () -> {
                // After a write the page is rebuilt: the annotation becomes the report's,
                // and not merely this browser's.
                Dashboard.build(outDir, sourceRoots(served), served.watchCount, served.hidden(),
                        launch(served, tools, sourceRoots(served)));
                return null;
            }, access);
        }
        return 0;
    }

    // ------------------------------------------------------------------ collecte

    private static void collect(Config config, Toolbox tools, Path outDir) throws Exception {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        String uuid = UUID.randomUUID().toString();
        String name = config.runName.isBlank()
                ? "run of " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                        .format(LocalDateTime.now())
                : config.runName;
        Path runDir = outDir.resolve("runs").resolve(stamp + slug(config.runName));
        Files.createDirectories(runDir);

        System.out.println("▶ Run \"" + name + "\" — id " + uuid);

        RunSession session = new RunSession(config, tools, runDir);
        session.execute();

        resolveClasses(config, session);

        System.out.println("▶ Rendering coverage");
        renderCoverage(config, tools, runDir);

        System.out.println("▶ Rendering the profile with its own tool");
        renderProfileViews(tools, runDir);

        writeContext(config, runDir, uuid, name, session);

        System.out.println();
        System.out.println("   To name, describe or tag this run later, add to "
                + outDir.resolve("noms.json") + ":");
        System.out.println("     { \"" + uuid + "\": \"a more telling name\" }");
        System.out.println("   or, to fill in everything:");
        System.out.println("     { \"" + uuid + "\": { \"nom\": \"…\", \"description\": \"…\","
                + " \"etiquettes\": { \"ticket\": \"ABC-123\", \"recette\": \"\" } } }");
        System.out.println("   The report can also capture them and write this file.");
    }

    /**
     * Works out the bytecode to analyse by questioning the run rather than the operator.
     *
     * <p>An explicit path always wins: it expresses an intent nothing must override —
     * analysing one more dependency, or restricting to one module.
     */
    private static void resolveClasses(Config config, RunSession session) {
        if (!config.classesDir.isBlank()) {
            return;
        }
        List<Path> found = ClassSources.discover(session.jvmArguments, config.javaCommand,
                Path.of("").toAbsolutePath());
        if (found.isEmpty()) {
            System.out.println("   ⚠️ cannot determine where the classes are — "
                    + "coverage will be empty. Give --classes (a directory, a jar, "
                    + "or a list separated by ':')");
            return;
        }
        config.classesDir = found.stream().map(Path::toString)
                .collect(java.util.stream.Collectors.joining(":"));
        System.out.println("▶ Classes analysed: " + config.classesDir
                + "  (inferred from the run — --classes to add to or change them)");
    }

    /**
     * Produces the pages <b>async-profiler renders itself</b> from the folded stacks.
     *
     * <p>They duplicate this page's tree, and deliberately: ours is a summary, with its
     * choices (folding the JDK and the hidden packages, aggregating per method). Theirs is
     * the raw output, without processing. Two uses: checking the summary when it surprises,
     * and keeping a usable view if it breaks.
     *
     * <p>A conversion failure interrupts nothing — it is a comfort view, not a measurement;
     * the measurement is already on disk as {@code .collapsed}.
     */
    private static void renderProfileViews(Toolbox tools, Path runDir) {
        Path collapsed = runDir.resolve("async-profiler/profil.collapsed");
        if (!Files.isRegularFile(collapsed)) {
            return;
        }
        try {
            Path converter = tools.asyncProfilerConverter();
            // The classic graph, then its reverse: the first answers "where does the time
            // go?", the second "who calls this costly method?". They are two different
            // questions, and the reverse is the harder one to obtain otherwise.
            convert(converter, collapsed, runDir.resolve("async-profiler/flamegraph.html"),
                    List.of("--title", "Raw profile — async-profiler"));
            convert(converter, collapsed, runDir.resolve("async-profiler/flamegraph-inverse.html"),
                    List.of("--reverse", "--title", "Reversed profile — who calls what"));
        } catch (Exception e) {
            System.out.println("   ⚠️ native rendering unavailable (" + e.getMessage()
                    + ") — the raw stacks remain in profil.collapsed");
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
     * The coverage of all the runs together, as JaCoCo computes it.
     *
     * <p>JaCoCo can merge: {@code merge} adds several {@code .exec} files into one, and the
     * report drawn from it is the whole campaign's — a line is covered there as soon as one
     * run covered it. That is exactly the question one asks after an acceptance campaign of
     * ten scenarios, and the one ten separate reports do not answer: the union would have
     * to be made in one's head, class by class.
     *
     * <p>The page makes the same union on its side, over the ticked runs, and can say
     * <i>which</i> covered what — which the JaCoCo merge can no longer say once the
     * measurements are added up. The two complement each other: the page to understand, the
     * JaCoCo report for the authoritative figure one passes on.
     *
     * <p>Without at least two runs there is nothing to merge and nothing is produced: a
     * "merged report" identical to the plain one would suggest an operation took place.
     */
    private static void mergeCoverage(Config config, Toolbox tools, Path outDir) {
        try {
            List<Path> samples = new ArrayList<>();
            for (Path run : runDirectories(outDir)) {
                Path exec = run.resolve("jacoco/jacoco.exec");
                if (Files.isRegularFile(exec)) samples.add(exec);
            }
            if (samples.size() < 2) return;
            List<Path> classes = config.classesPaths();
            if (classes.isEmpty()) {
                System.out.println("▶ Merged coverage: skipped — the bytecode is not "
                        + "known in this mode (give --classes to get it)");
                return;
            }

            System.out.println("▶ Merged coverage over " + samples.size() + " runs");
            Path cli = tools.jacocoCli();
            Path dir = outDir.resolve("jacoco-fusion");
            Files.createDirectories(dir);
            Path merged = dir.resolve("fusion.exec");

            List<String> merge = new ArrayList<>(List.of(
                    RunSession.javaExecutable(), "-jar", cli.toString(), "merge"));
            for (Path m : samples) merge.add(m.toString());
            merge.add("--destfile");
            merge.add(merged.toString());
            merge.add("--quiet");
            exec(merge);

            Path html = dir.resolve("html");
            Files.createDirectories(html);
            List<String> report = new ArrayList<>(List.of(
                    RunSession.javaExecutable(), "-jar", cli.toString(), "report",
                    merge.toString(),
                    "--html", html.toString(),
                    "--xml", html.resolve("jacoco.xml").toString(),
                    "--csv", html.resolve("jacoco.csv").toString(),
                    "--name", "Couverture cumulée — " + samples.size() + " exécutions",
                    "--quiet"));
            for (Path entry : classes) {
                report.add("--classfiles");
                report.add(entry.toString());
            }
            for (Path src : sourceRoots(config)) {
                report.add("--sourcefiles");
                report.add(src.toString());
            }
            exec(report);
        } catch (Exception e) {
            // The merge is a bonus: the view can already accumulate on the page side. Its
            // failure must not take the report down, it must be said.
            System.out.println("   ⚠️ merged coverage not produced: " + e.getMessage());
        }
    }

    /** The run directories under the common output, in the sense the view means them. */
    private static List<Path> runDirectories(Path outDir) throws IOException {
        List<Path> found = new ArrayList<>();
        if (Files.isRegularFile(outDir.resolve("jacoco/jacoco.exec"))) found.add(outDir);
        Path runs = outDir.resolve("runs");
        for (Path dir : List.of(outDir, runs)) {
            if (!Files.isDirectory(dir)) continue;
            try (java.util.stream.Stream<Path> children = Files.list(dir)) {
                children.filter(Files::isDirectory)
                       .filter(d -> Files.isRegularFile(d.resolve("jacoco/jacoco.exec")))
                       .forEach(found::add);
            }
        }
        return found.stream().distinct().toList();
    }

    /**
     * Two renderings from the same measurement: the complete report, and a <b>focused</b>
     * report restricted to the classes that actually ran. On a real project the second is
     * often the only readable one — the first lists thousands of irrelevant classes.
     */
    private static void renderCoverage(Config config, Toolbox tools, Path runDir) throws Exception {
        Path exec = runDir.resolve("jacoco/jacoco.exec");
        if (!Files.isRegularFile(exec)) {
            System.out.println("   ⚠️ no coverage data — did the application start?");
            return;
        }
        Path cli = tools.jacocoCli();
        // The XML and the CSV keep their path inside jacoco/html/ even when no site is
        // written there: it is the path the page, the exports and every already assembled
        // report read. A produced file never moves.
        Path html = runDir.resolve("jacoco/html");
        Files.createDirectories(html);

        List<String> cmd = new ArrayList<>(List.of(
                RunSession.javaExecutable(), "-jar", cli.toString(), "report", exec.toString(),
                "--xml", html.resolve("jacoco.xml").toString(),
                "--csv", html.resolve("jacoco.csv").toString(),
                "--name", "Runtime X-Ray", "--quiet"));
        if (config.jacocoHtmlWanted()) {
            cmd.add("--html");
            cmd.add(html.toString());
        }
        // One --classfiles entry per directory or jar: the option is repeatable, and that
        // is the tool's intended mechanism for analysing several bytecode sources.
        for (Path entry : config.classesPaths()) {
            cmd.add("--classfiles");
            cmd.add(entry.toString());
        }
        for (Path src : sourceRoots(config)) {
            cmd.add("--sourcefiles");
            cmd.add(src.toString());
        }
        exec(cmd);

        if (!config.focusedReportWanted()) {
            System.out.println("   JACOCO_REPORTS=" + config.jacocoReports
                    + " — coverage read from jacoco.xml, "
                    + (config.jacocoHtmlWanted() ? "focused report not written"
                                                 : "no HTML site written for this run"));
            return;
        }

        // Focused report: only the classes with at least one covered instruction are
        // presented to the CLI. That is the tool's native mechanism, not a home-made
        // filter.
        Coverage coverage = Coverage.parse(html.resolve("jacoco.xml"), config.hidden());
        Path staging = runDir.resolve("classes-executees.jar");
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
                "--name", "Code actually executed", "--quiet"));
        for (Path src : sourceRoots(config)) {
            focusedCmd.add("--sourcefiles");
            focusedCmd.add(src.toString());
        }
        exec(focusedCmd);
        System.out.println("   " + kept + " executed classes kept for the focused report");
    }

    /**
     * Gathers the executed classes into <b>one jar</b>, to be handed to the focused report.
     *
     * <p>A directory would have held one file per executed class, and those files are a
     * pure intermediate — nothing reads them once the report is built. On a machine where
     * every file open crosses a stack of security filters, writing several thousand of
     * them, then walking them again, then zipping them up with the report, is paid three
     * times for nothing. {@code jacococli} accepts a jar exactly as it accepts a directory,
     * and produces a byte-identical report from it.
     *
     * <p>The copy goes straight into the archive through the zip file system, so the
     * intermediate files never exist at all — writing them only to delete them would have
     * cost the very thing this avoids.
     */
    @SuppressWarnings("unchecked")
    private static int stageExecutedClasses(Coverage coverage, List<Path> classesPaths, Path staging)
            throws IOException {
        Files.deleteIfExists(staging);
        // A directory left by an earlier version, which used to stage one file per class.
        Path legacy = staging.resolveSibling("classes-executees");
        if (Files.isDirectory(legacy)) {
            deleteRecursively(legacy);
        }
        Files.createDirectories(staging.getParent());
        int kept = 0;
        try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(
                URI.create("jar:" + staging.toUri()), Map.of("create", "true"))) {
            for (Map.Entry<String, Object> entry : coverage.packages.entrySet()) {
                for (Object o : (List<Object>) entry.getValue()) {
                    Map<String, Object> cls = (Map<String, Object>) o;
                    if (((Number) cls.get("covered")).intValue() == 0) {
                        continue;
                    }
                    String name = cls.get("name") + ".class";
                    if (copyClassBytes(classesPaths, name, jar.getPath("/" + name))) {
                        kept++;
                    }
                }
            }
        }
        if (kept == 0) {
            Files.deleteIfExists(staging);
        }
        return kept;
    }

    /**
     * Copies a class from the first entry that contains it, directory or jar.
     *
     * <p>The order of the entries is authoritative, as on a classpath: if the same class
     * exists in two places, the first one wins — that is the one the JVM would have
     * loaded.
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
     * Looks for a class inside an archive, <b>including inside the archives it contains</b>.
     *
     * <p>A modern application jar is not a bag of classes: Spring Boot files the code under
     * {@code BOOT-INF/classes/} and its dependencies as jars under {@code BOOT-INF/lib/}.
     * Looking only at the first level would amount to finding nothing in the most common
     * case. The coverage tool goes down — the focused report must do the same, otherwise
     * the two reports are not talking about the same code.
     *
     * <p>One level down only: beyond that, we know of no real format that requires it, and
     * unbounded recursion over archives is a fine way never to stop.
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
            // An unreadable archive must not bring the analysis down: the other entries
            // are tried, and the class will simply be missing from the focused report.
        }
        return false;
    }

    /** The level below: the class is looked for there, without going down any further. */
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
        // The name is only recorded when it was GIVEN. Writing the fallback label here —
        // "run of 2026-08-21 00:41" — would amount to passing a display convenience off as
        // the operator's intent, and the view could no longer say which of the two it is
        // showing.
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
        // The capture announces its shape: that is what lets a later version of the tool
        // read it back without replaying the campaign. See Capture.
        ctx.put(lab.xray.report.Capture.FIELD, lab.xray.report.Capture.CURRENT);
        Files.writeString(runDir.resolve("run-context.json"), Json.write(ctx), StandardCharsets.UTF_8);
    }

    /**
     * Rewrites each run in the requested formats, so that another tool can read it.
     *
     * <p>The export covers <b>every</b> run present, including yesterday's: it is the same
     * gesture as assembling the view, and nothing would justify serving a format to one run
     * and not to its neighbour.
     */
    private static void exportRuns(Config config, Path outDir) throws Exception {
        Set<Exports.Format> formats = Exports.Format.parse(config.exportFormats);
        System.out.println("▶ Exporting to " + formats.stream().map(f -> f.affiche).sorted()
                .collect(java.util.stream.Collectors.joining(", ")));
        Path runs = outDir.resolve("runs");
        if (!Files.isDirectory(runs)) return;
        try (var dirs = Files.list(runs)) {
            for (Path run : dirs.filter(Files::isDirectory).sorted().toList()) {
                for (Path written : Exports.write(run, formats, 1, config.watchCount)) {
                    System.out.println("   " + written + " ("
                            + Files.size(written) / 1024 + " KB)");
                }
            }
        }
    }

    // ------------------------------------------------------------------- divers

    private static void printAgentOptions(Config config, Toolbox tools) throws Exception {
        require(!config.classesDir.isBlank() || !config.outDir.isBlank(),
                "--out (or --classes) is needed to place the measurement files");
        Path runDir = Path.of(config.outDir, "runs", "manuel");
        Files.createDirectories(runDir.resolve("jacoco"));
        Files.createDirectories(runDir.resolve("async-profiler"));
        String options = new RunSession(config, tools, runDir).agentOptions();
        System.out.println();
        System.out.println("Options to add to ANY Java command line:");
        System.out.println();
        System.out.println("  " + options);
        System.out.println();
        System.out.println("Or, without touching the command line, through the environment:");
        System.out.println();
        System.out.println("  export JAVA_TOOL_OPTIONS=\"" + options + "\"");
        System.out.println();
        System.out.println("Then, once the application has stopped, build the report:");
        System.out.println();
        System.out.println("  java -jar runtime-xray.jar --report-only --out " + config.outDir
                + " --classes <classes directory> --sources <sources>");
    }

    private static void announceTemplate(Path file, String relaunch) {
        System.out.println("Configuration file generated: " + file);
        System.out.println();
        System.out.println("   It holds the defaults, comments and examples.");
        System.out.println("   Fill in JAVA_CMD at least, then run again:");
        System.out.println();
        System.out.println("     java -jar runtime-xray.jar " + relaunch);
    }

    /** The command line's options win over the file. */
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

    /**
     * What only the launch knows, and the diagnostic must carry.
     *
     * <p>The view reassembles from the output directory alone: by construction it does not
     * know with which command, which options and which components the measurement was made.
     * Yet that is often where the cause lies. So we give it to it.
     *
     * <p><b>The shared server's token is not there</b>: this file is made to be passed on,
     * and a secret passed on is no longer one.
     */
    private static Map<String, Object> launch(Config config, Toolbox tools,
                                                 List<Path> sourceRoots) {
        Map<String, Object> m = new LinkedHashMap<>(config.describe());
        List<Object> roots = new ArrayList<>();
        for (Path r : sourceRoots) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("demandee", r.toString());
            e.put("absolue", r.toAbsolutePath().normalize().toString());
            roots.add(e);
        }
        m.put("racinesSources", roots);
        List<Object> classes = new ArrayList<>();
        for (Path c : config.classesPaths()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("chemin", c.toString());
            e.put("absolu", c.toAbsolutePath().normalize().toString());
            e.put("existe", Files.exists(c));
            classes.add(e);
        }
        m.put("racinesClasses", classes);
        m.put("mesureDuTemps", tools.asyncProfilerAvailable());
        return m;
    }

    /**
     * The finding, on the console, at the moment it can still be of use.
     *
     * <p>The diagnostic file is complete, but one only goes looking for it knowing that it
     * exists. Two lines here are worth more than a discovery later.
     */
    private static void sayWhatWasFound(Path outDir) {
        Path file = outDir.resolve("diagnostic.json");
        try {
            Object read = lab.xray.json.Json.read(Files.readString(file, StandardCharsets.UTF_8));
            if (read instanceof Map<?, ?> d && d.get("rapprochement") instanceof Map<?, ?> r) {
                Object sans = r.get("fichiersSansSource");
                // The JSON reader returns Doubles: "0.0/27.0" reads as a defect of the
                // tool before it reads as a count.
                System.out.println("   sources: " + toInt(r.get("fichiersAvecSource"))
                        + "/" + toInt(r.get("fichiersMesures"))
                        + " measured class(es) have their source");
                if (sans instanceof Number n && n.intValue() > 0) {
                    System.out.println("   " + r.get("conclusion"));
                    proposeRoots(r.get("pistes"));
                }
            }
        } catch (Exception e) {
            // The diagnostic is a comfort: its absence must prevent nothing.
        }
        System.out.println("   diagnostic: " + file);
    }

    /**
     * The page's weight, said at the moment it is decided.
     *
     * <p>A page that grows announces itself nowhere: it is written, the tool says "Done",
     * and the defect only appears the day the browser gives up on displaying it — on
     * somebody else's machine, with nothing to explain it. It happened at 217 MB. Two lines
     * here are worth the discovery.
     */
    private static void sayTheWeight(Path page) {
        try {
            long bytes = Files.size(page);
            String size = bytes >= 1 << 20 ? (bytes >> 20) + " MB" : (bytes >> 10) + " KB";
            System.out.println("   page: " + size);
            if (bytes > PAGE_THRESHOLD) {
                System.out.println("   ⚠️ beyond " + (PAGE_THRESHOLD >> 20) + " MB a browser "
                        + "may give up rendering it. The two causes, in order:");
                System.out.println("      • a --sources root that is too wide — only measured classes "
                        + "are embedded, but reading them all still costs;");
                System.out.println("      • the number of runs accumulated under " 
                        + "<out>/runs/: all of them are embedded.");
                System.out.println("      See sources.racines and executions in diagnostic.json.");
            }
        } catch (IOException e) {
            // The weight is a comfort: its absence must prevent nothing.
        }
    }

    /** Beyond that a browser starts to struggle — and it must be said before it gives up. */
    private static final long PAGE_THRESHOLD = 64L << 20;

    /**
     * The roots found, with what they would resolve.
     *
     * <p>A diagnostic that stops at "sources are missing" leaves the reader with the same
     * question as before. This one proposes a path and justifies it with a count: the line
     * is copied out, and the count says whether to believe it.
     */
    private static void proposeRoots(Object leads) {
        if (!(leads instanceof List<?> list) || list.isEmpty()) {
            System.out.println("   no matching source found around the project — "
                    + "the .java files are not on this machine, or not here.");
            return;
        }
        System.out.println("   roots found, to add to SOURCE_DIRS:");
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> p)) continue;
            System.out.println("     " + p.get("racine")
                    + "   (resolves " + toInt(p.get("resout")) + "/" + toInt(p.get("surTotal"))
                    + " of the classes without source)");
        }
    }

    private static String toInt(Object o) {
        return o instanceof Number n ? String.valueOf(n.longValue()) : String.valueOf(o);
    }

    /**
     * Takes back the source roots the runs recorded, when none is given.
     *
     * <p>{@code --report-only} used to reassemble without any annotated code and announce
     * "no source directory was given". That was false, and the falsehood was the expensive
     * part: one had been given when the measurement was taken, each run had written it into
     * its own context, and the message sent the reader looking for a setting they had
     * already made.
     *
     * <p>What is given on the command line always wins: it is the gesture of someone who is
     * here now, in front of this machine, and it may well correct a path that has moved.
     *
     * <p>A root that no longer exists is <b>not</b> adopted, and not passed over in silence
     * either — a report travels, and the machine that reads it is rarely the one that
     * produced it. Saying which paths were recorded is what turns "no source" into
     * something one can act on.
     */
    private static void adoptRecordedSourceRoots(Config config, Path outDir) throws IOException {
        if (!Config.paths(config.sourceDirs).isEmpty()) {
            return;
        }
        List<Path> recorded = Dashboard.recordedSourceRoots(outDir);
        if (recorded.isEmpty()) {
            return;
        }
        // Joined with the platform's own separator: Config reads both, and ':' would cut a
        // Windows path on its drive letter — the defect this project already met once.
        config.sourceDirs = recorded.stream().map(Path::toString)
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));

        // A root that no longer exists is adopted all the same, and that is deliberate: the
        // diagnostic already shows a root as absent rather than making it disappear, and
        // its conclusion then names the path to correct. Filtering here would have put the
        // reader back in front of "no source directory was given" — the very sentence that
        // sent them looking for a setting they had already made.
        List<Path> here = recorded.stream().filter(Files::isDirectory).toList();
        System.out.println("   sources: none given — taking back what the run(s) recorded:");
        recorded.forEach(p -> System.out.println("      " + p
                + (Files.isDirectory(p) ? "" : "   (absent from this machine)")));
        if (here.isEmpty()) {
            System.out.println("   → none of them exist here: pass --sources with the path "
                    + "they have on this machine.");
        }
    }

    private static List<Path> sourceRoots(Config config) {
        // The splitting lives in Config: sources and classes are written the same way, and
        // used to go wrong the same way on an absolute Windows path.
        return Config.paths(config.sourceDirs);
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
            throw new IOException("failed (exit " + p.exitValue() + ") : " + String.join(" ", cmd));
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // A leftover does not justify interrupting the analysis.
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
                Runtime X-Ray \u2014 see what a Java run actually did.

                SYNOPSIS
                  runtime-xray --java <command> [options]          measure a run
                  runtime-xray --print-options [options]           prepare a JVM you don't launch
                  runtime-xray --report-only [options]             rebuild the report, run nothing
                  runtime-xray --context [<question>] [options]    extract a bounded excerpt
                  runtime-xray --serve [<port>] [options]          serve the report, allow notes

                EXAMPLES
                  java -jar runtime-xray.jar --config my-project.conf
                  java -jar runtime-xray.jar --java "java -jar app.jar" --classes target/classes \\
                                             --root "com.example.Engine::compute" --sources src/main/java

                MEASURING
                  --config <file>      Read settings from a file. GENERATES a commented
                                       template when the file does not exist.
                  --java "<command>"   The command that launches the application, verbatim.
                  --classes <paths>    Directories of .class files and/or jars, separated by
                                       ':' (or ';' on Windows, where "C:" is not split).
                  --root "<C::m>"      Root method: the one whose argument values are captured.
                  --sources <dirs>     Source roots, separated by ':' (or ';' on Windows).
                  --filter "<glob>"    Restrict timing measurements, e.g. "com/example/*".
                  --hide "<packages>"  Packages to fold away like the JDK, e.g. "org.slf4j".
                  --out <dir>          Output directory (default: runtime-xray-out).
                  --name "<text>"      Name for this run; runs accumulate and the report lets
                                       you switch between them.
                  --level <level>      How deep to observe: coverage (JaCoCo only), tree
                                       (+ stack sampling), full (+ values). Default:
                                       full. French names still work. The first knob to turn down on a large codebase.
                  --cover "<globs>"    Classes JaCoCo instruments, e.g. "com.example.*".
                                       Without it every class the JVM loads is instrumented.
                  --jacoco-reports <v> How much of JaCoCo's own rendering to write per run:
                                       full (both sites, the default), detailed (the complete
                                       site alone), data (jacoco.xml and .csv only). The page
                                       reads the XML, so it shows the same coverage either
                                       way; this only decides how many FILES land on disk.
                  --interval <ms>      Stack sampling interval (default: 1).
                  --attach-after <s>   Delay before inspecting values (default: 8).
                  --max-seconds <s>    Guard rail on the run duration (default: 600).
                  --no-values          Do not inspect values: timings become exact.
                  --export <formats>   Rewrite measurements for other tools: perf, cpuprofile,
                                       lcov, values \u2014 or "all". Files go to <run>/exports/.

                WATCHING A RUN
                  --follow [port]      Serve a page showing the run in progress (default:
                                       8788, loopback only). progression.jsonl is written
                                       either way: "tail -f <out>/progression.jsonl" follows
                                       the run with no browser and no open port.

                READING A REPORT \u2014 these run nothing
                  --report-only        Rebuild the report from measurements already on disk.
                  --context ["q"]      Write a bounded excerpt of the report to standard
                                       output, ready to hand to a reader. The question SELECTS
                                       the facts \u2014 by plain keywords, not by understanding
                                       \u2014 and travels inside the excerpt. The families kept
                                       are announced on standard error. Keywords, by family:
                                         class.never_executed    never, dead, unused, uncovered,
                                                                 not covered
                                         coverage.run + class    cover, coverage, percent
                                         method.hot              time, slow, hot, cost, perf,
                                                                 fast, profil
                                         source.missing          source, missing, root
                                         + source.hint
                                         run                     run, campaign, when, machine,
                                                                 command
                                       A keyword matches the START of a word, so "screenshot"
                                       does not match "hot". French words are recognised too.
                                       No keyword matched: the overview, and it says so.
                  --families a,b       Name the fact families to include instead of deriving
                                       them from the question. The scripting path: the result
                                       no longer depends on the words used. An unknown family
                                       stops, listing the ones that exist. The format 1.0
                                       names (classe.jamais_executee, methode.chaude, …)
                                       are still accepted.

                SERVING
                  --serve [port]       Serve the report (default: 8787) and let the page write
                                       its annotations next to the runs, then rebuild it.
                                       Several people can annotate at once.
                  --serve-host <host>  Listening interface (default: 127.0.0.1). Use 0.0.0.0
                                       for a shared server.
                  --serve-token [s]    Guard the served report with a shared secret, asked once
                                       then remembered for 12 h. With no value, a secret is
                                       drawn at random and printed. XRAY_SERVE_TOKEN does the
                                       same without exposing it in "ps". Without this option
                                       nothing is asked: keep it to loopback or an already
                                       filtered network.

                FINDING THE ANALYSIS COMPONENTS
                  --components <dir>   Components already present on the machine, taken as is.
                                       Otherwise: next to the jar, then the local Maven
                                       repository. The network is the last resort.
                  --repo <url>         Maven repository to fetch components from (internal
                                       mirror). Used only when everything else failed.

                  --print-options      Run nothing: print the JVM options to add to any command
                                       line, then assemble with --report-only.

                EXIT STATUS
                  0  success, or --help
                  2  bad usage: unknown option, unknown fact family
                """);
    }
}
