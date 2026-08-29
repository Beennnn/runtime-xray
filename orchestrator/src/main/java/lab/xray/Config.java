package lab.xray;

import lab.xray.report.PackageFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings of one analysis.
 *
 * <p>The configuration file is a plain {@code key="value"} per line — no YAML, no TOML:
 * the format must read and write by hand without documentation, and read back here
 * without a library.
 *
 * <p>When the expected file does not exist, a {@linkplain #writeTemplate commented
 * template} is written rather than an error shown: nobody should have to guess key names.
 */
public final class Config {

    public String javaCommand = "";
    public String rootMethod = "";
    /**
     * Where to find the analysed bytecode: class directories <b>and/or</b> jar archives,
     * separated by {@code :} or {@code ,}.
     *
     * <p>Jars count as much as directories: the code one is trying to understand is often
     * an internal dependency delivered in that form — a shared module, a home-grown library
     * — and it is precisely the one nobody has a mental model of. The coverage tool can
     * read both; there was no reason to accept only one.
     */
    public String classesDir = "";
    /**
     * Packages to keep quiet about, on the same footing as the JDK —
     * {@code org.slf4j, io.netty}…
     *
     * <p>See {@link PackageFilter} for what "keep quiet about" means exactly (the time is
     * carried over to the caller, it is not lost).
     */
    public String hiddenPackages = "";
    public String sourceDirs = "";
    public String classFilter = "";
    public String outDir = "runtime-xray-out";
    /**
     * What to do with {@code runs/} once the report is assembled: {@code ""} nothing,
     * {@code "keep"} also write {@code runs.zip}, {@code "replace"} write it and remove the
     * tree.
     *
     * <p>A campaign is a great many small files, and on a machine where a filter inspects
     * every open, they are paid at every later walk — a backup, a search, the archive one
     * makes to send it. Gathering them into one file pays that count once and stops paying
     * it. {@code replace} is the only value that actually reduces anything, and it is the
     * only one that takes something away: see {@link #ARCHIVE}.
     */
    public String archive = "";
    public String runName = "";
    public int attachAfterSeconds = 8;
    public int maxSeconds = 600;
    public int watchCount = 10;
    /**
     * What one accepts to pay in order to observe, in order of priority of the information.
     *
     * <p>On enterprise code, measuring everything at once is not always tenable: coverage
     * instruments every class loaded, sampling wakes the JVM a thousand times a second, and
     * value capture intercepts every entry into a method. The three pieces of information
     * are not worth the same, though: knowing <b>what ran</b> comes before knowing <b>who
     * calls whom</b>, which comes before <b>with which values</b>. The level chosen says
     * how far one goes.
     *
     * <ul>
     *   <li>{@code coverage} — JaCoCo alone;</li>
     *   <li>{@code tree} — plus stack sampling;</li>
     *   <li>{@code full} — plus value capture (the default).</li>
     * </ul>
     */
    public String level = "complet";

    /** The three levels, under their internal name. */
    public static final java.util.List<String> LEVELS =
            java.util.List.of("couverture", "arbre", "complet");

    /**
     * The level brought back to its internal name.
     *
     * <p>The tool speaks English: {@code coverage}, {@code tree}, {@code full}. The French
     * names stay accepted, for life and undocumented — a script deployed before the switch
     * must never stop working over a question of language.
     */
    public static String level(String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (v) {
            case "coverage" -> "couverture";
            case "tree" -> "arbre";
            case "full" -> "complet";
            default -> v;
        };
    }
    /**
     * Classes JaCoCo instruments, in its agent's format — {@code com.example.*}, several
     * patterns separated by {@code :}. Empty: everything the JVM loads, third-party
     * libraries included, which is the most expensive and rarely the most useful.
     */
    public String coverIncludes = "";
    /**
     * The stack sampling interval, in milliseconds. Raising it is the most direct lever on
     * the profile's cost: at 10 ms, ten times fewer samples than at 1 ms.
     */
    public int sampleIntervalMs = 1;

    /**
     * The follow page's port, or 0 to serve nothing.
     *
     * <p>Zero by default, and deliberately so: {@code progression.jsonl} is written anyway,
     * and opening a port without being asked to is a decision one does not take on the
     * operator's behalf.
     */
    public int followPort = 0;
    /**
     * Rewrite formats requested — {@code perf}, {@code cpuprofile}, {@code lcov},
     * {@code values}, or {@code all}. Empty: no export, and nothing written extra.
     */
    public String exportFormats = "";
    /**
     * How many invocations have their call tree traced.
     *
     * <p>Since each invocation takes different branches, this number decides how many lines
     * of code can be annotated: with only one, one sees the path of that call alone. The
     * cost is low — one traced invocation makes a few dozen lines of output — so the
     * default is generous.
     */
    public int traceCount = 10;
    public boolean captureValues = true;
    /** The Maven repository to fetch the components from. An internal mirror is enough. */
    public String mavenRepo = "https://repo1.maven.org/maven2";
    /**
     * Directory to look in for components already present on the machine, before any
     * network access. What it holds takes priority over what might be downloaded — it is
     * the gesture of whoever brought them along.
     */
    public String componentsDir = "";

    /**
     * How much of JaCoCo's own rendering is written to disk, per run.
     *
     * <p>This governs neither the measurement nor the display: the coverage the page shows
     * is read from {@code jacoco.xml}, which is written whatever the value. What it governs
     * is the number of <b>files</b> produced, and that is its whole point — JaCoCo's HTML
     * site holds two files per class, the tool asks for two such sites per run, and the
     * count therefore grows with the analysed code rather than with the measurement.
     *
     * <p>On a machine where every file open crosses a stack of security filters, that count
     * is what a campaign pays for, several times over: at write time, at every later walk,
     * and when the report is zipped up to be passed on.
     *
     * <ul>
     *   <li>{@code full} — both sites, the default: nothing changes for anyone;</li>
     *   <li>{@code detailed} — the complete site alone. The focused report holds no datum
     *       the complete one lacks: it is the same coverage, restricted to the classes that
     *       ran. Dropping it costs a framing, never a measurement;</li>
     *   <li>{@code data} — {@code jacoco.xml}, {@code jacoco.csv} and the {@code .exec},
     *       and no site per run. The campaign's merged report stays, so JaCoCo's own
     *       rendering remains reachable for whoever cannot open the page.</li>
     * </ul>
     *
     * <p>An absent report is never silent: the page keeps naming it, greyed out, with the
     * command that produces it.
     */
    public String jacocoReports = FULL;

    public static final String FULL = "full";
    public static final String DETAILED = "detailed";
    public static final String DATA = "data";
    /**
     * Neither site per run, <b>nor the campaign's merged rendering</b>.
     *
     * <p>Apart from the other three, and it must stay apart. They give up a convenience per
     * run while the campaign's figure — the one that is handed on and that makes authority —
     * stays rendered; this one gives that up too. It is the last reading JaCoCo produces by
     * itself, so nothing here should ever make it a default, and the documentation states
     * what it costs before saying how to turn it on. The merged XML and CSV are still
     * written: the figure survives, its rendering does not.
     */
    public static final String MINIMAL = "minimal";

    /** The four values, in decreasing order of what is written. */
    public static final java.util.List<String> JACOCO_REPORTS =
            java.util.List.of(FULL, DETAILED, DATA, MINIMAL);

    /** Whether an HTML site is written for each run at all. */
    public boolean jacocoHtmlWanted() {
        return !DATA.equals(jacocoReports) && !MINIMAL.equals(jacocoReports);
    }

    /** Whether the focused report — and the class staging it needs — is produced. */
    public boolean focusedReportWanted() {
        return FULL.equals(jacocoReports);
    }

    /** Whether the campaign's merged coverage gets its HTML site. */
    public boolean mergedHtmlWanted() {
        return !MINIMAL.equals(jacocoReports);
    }

    public static final String KEEP = "keep";
    public static final String REPLACE = "replace";

    /**
     * The accepted values of {@code ARCHIVE}, and the only restricting one is the second.
     *
     * <p>{@code keep} adds {@code runs.zip} beside the tree: one object to hand on, to
     * back up, to have scanned once instead of file by file. It takes nothing away, and it
     * reduces nothing either — the tree is still there.
     *
     * <p>{@code replace} removes the tree once the archive is verified, and <b>that one
     * restricts</b>: the report's links to the JaCoCo sites stop resolving, and neither
     * {@code --report-only} nor {@code --serve} can rebuild anything from this output any
     * more. The page, the diagnostic, the facts and the Markdown stay where they are and
     * still read. It is off by default and says what it costs where it is documented.
     */
    public static final java.util.List<String> ARCHIVE = java.util.List.of(KEEP, REPLACE);

    public boolean archiveWanted() {
        return !archive.isBlank();
    }

    /** Whether {@code runs/} is removed once the archive has been checked. */
    public boolean archiveReplaces() {
        return REPLACE.equals(archive);
    }

    public static Config load(Path file) throws IOException {
        Config c = new Config();
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());
            c.set(key, value);
        }
        return c;
    }

    private static String unquote(String v) {
        // A trailing comment is cut off, but only outside quotes.
        if (v.startsWith("\"")) {
            int end = v.indexOf('"', 1);
            return end > 0 ? v.substring(1, end) : v.substring(1);
        }
        int hash = v.indexOf('#');
        return (hash >= 0 ? v.substring(0, hash) : v).trim();
    }

    void set(String key, String value) {
        switch (key) {
            case "JAVA_CMD" -> javaCommand = value;
            case "ROOT_METHOD" -> rootMethod = value;
            case "CLASSES_DIR" -> classesDir = value;
            case "HIDDEN_PACKAGES" -> hiddenPackages = value;
            case "SOURCE_DIRS" -> sourceDirs = value;
            case "CLASS_FILTER" -> classFilter = value;
            case "OUT_DIR" -> outDir = value;
            case "RUN_NAME" -> runName = value;
            case "MAVEN_REPO" -> mavenRepo = value;
            case "COMPONENTS", "COMPOSANTS", "COMPONENTS_DIR" -> componentsDir = value;
            case "ATTACH_AFTER" -> attachAfterSeconds = parse(value, attachAfterSeconds);
            case "MAX_SECONDS" -> maxSeconds = parse(value, maxSeconds);
            case "WATCH_COUNT" -> watchCount = parse(value, watchCount);
            case "EXPORT" -> exportFormats = value;
            case "LEVEL", "NIVEAU" -> level = value;
            case "COVER_INCLUDES" -> coverIncludes = value;
            case "JACOCO_REPORTS" -> jacocoReports = value;
            case "ARCHIVE" -> archive = value;
            case "SAMPLE_INTERVAL_MS" -> sampleIntervalMs = parse(value, sampleIntervalMs);
            case "FOLLOW_PORT", "SUIVI_PORT" -> followPort = parse(value, followPort);
            case "TRACE_COUNT" -> traceCount = parse(value, traceCount);
            default -> { /* an unknown key is not an error: the file can serve other purposes */ }
        }
    }

    private static int parse(String v, int fallback) {
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The profile filter is deduced from the root method's package, failing an instruction. */
    public String effectiveFilter() {
        if (!classFilter.isBlank()) return classFilter;
        if (rootMethod.isBlank()) return "";
        String cls = rootMethod.split("::")[0];
        int dot = cls.lastIndexOf('.');
        return dot < 0 ? "" : cls.substring(0, dot).replace('.', '/') + "/*";
    }

    /** The entries of {@link #classesDir}, directories or jars, in the order given. */
    public List<Path> classesPaths() {
        return paths(classesDir);
    }

    /**
     * A list of paths as it is written in the configuration — <b>Windows drive letters
     * included</b>.
     *
     * <p>The documented separator is {@code :}, which goes without saying on Unix and not
     * at all on Windows: {@code C:\project\src} starts there with a {@code :} that is not a
     * separator. Split naively, that path gave two entries — {@code C} and
     * {@code \project\src} — neither of which exists, and the tool concluded "directory not
     * found" about a perfectly valid path the user had in front of them.
     *
     * <p>A {@code :} that follows a single letter at the start of a segment and precedes a
     * path separator is therefore given back to the path. {@code ;} — Windows's native
     * separator — and {@code ,} are accepted as well, because that is what a Windows user
     * will write spontaneously.
     */
    public static List<Path> paths(String value) {
        List<Path> paths = new ArrayList<>();
        for (String piece : split(value)) {
            if (!piece.isBlank()) paths.add(Path.of(piece.trim()));
        }
        return paths;
    }

    /** The splitting alone, without interpretation: it is what the tests exercise. */
    static List<String> split(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean separates = (c == ',' || c == ';')
                    || (c == ':' && !driveLetter(value, i));
            if (separates) {
                out.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        out.add(current.toString());
        return out;
    }

    /**
     * True when this {@code :} belongs to a Windows drive, and is not a separator.
     *
     * <p>Three conditions, and all three are needed: a letter just before, that letter at
     * the start of a segment, and a path separator just after. {@code C:\x} is a drive;
     * {@code src:other} is not, nor is {@code ab:c}.
     */
    private static boolean driveLetter(String v, int i) {
        if (i < 1 || !Character.isLetter(v.charAt(i - 1))) return false;
        if (i >= 2) {
            char before = v.charAt(i - 2);
            boolean segmentStart = before == ',' || before == ';' || before == ':'
                    || Character.isWhitespace(before);
            if (!segmentStart) return false;
        }
        return i + 1 < v.length() && (v.charAt(i + 1) == '\\' || v.charAt(i + 1) == '/');
    }

    /** True when the level requested goes as far as stack sampling. */
    public boolean profileWanted() {
        return !"couverture".equals(level(level));
    }

    /** True when the level requested goes as far as value capture. */
    public boolean valuesWanted() {
        String l = level.trim();
        return captureValues && ("complet".equals(level(l)) || l.isBlank());
    }

    public PackageFilter hidden() {
        return PackageFilter.of(hiddenPackages);
    }

    public String rootClass() {
        return rootMethod.isBlank() ? "" : rootMethod.split("::")[0];
    }

    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commande", javaCommand);
        m.put("methodeRacine", rootMethod.isBlank() ? null : rootMethod);
        m.put("filtreClasses", effectiveFilter().isBlank() ? null : effectiveFilter());
        m.put("repertoireClasses", classesDir);
        m.put("paquetsMasques", hiddenPackages.isBlank() ? null : hiddenPackages);
        m.put("repertoiresSources", sourceDirs.isBlank() ? null : sourceDirs);
        m.put("valeursInspectees", valuesWanted() && !rootMethod.isBlank());
        // The level and its settings travel with the run: without them, a report less
        // full than another reads as a failed measurement rather than as a choice.
        m.put("niveau", level);
        m.put("classesInstrumentees", coverIncludes.isBlank() ? null : coverIncludes);
        m.put("intervalleMs", sampleIntervalMs);
        m.put("rapportsJacoco", jacocoReports);
        return m;
    }

    // ------------------------------------------------------------------- gabarit

    public static void writeTemplate(Path file) throws IOException {
        Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
    }

    private static final String TEMPLATE = """
            # ---------------------------------------------------------------------------
            # Runtime X-Ray — configuration
            #
            #   java -jar runtime-xray.jar --config this-file.conf
            #
            # Only JAVA_CMD is required. Everything else has sensible defaults, and the
            # commented lines show other uses.
            # ---------------------------------------------------------------------------

            # ── How to launch the application ──────────────────────────────────── REQUIRED
            # No constraint: the command is executed as it is. The analysis agents are
            # injected through JAVA_TOOL_OPTIONS, which every JVM reads at start-up.
            JAVA_CMD="java -jar target/my-app.jar"
            #JAVA_CMD="java -Xmx2g -jar target/my-app.jar --profile acceptance --set 42"
            #JAVA_CMD="mvn -q exec:java -Dexec.mainClass=com.example.Main"
            #JAVA_CMD="./gradlew run --args='--profile acceptance'"
            #JAVA_CMD="./scripts/start-acceptance.sh"

            # ── The compiled classes ──────────────────────────────────────────── optional
            # Normally UNNECESSARY: the analysed bytecode is read off the observed JVM's real
            # arguments, and failing that deduced from the project's layout (target/classes,
            # build/classes/java/main…). Dependency jars are left out on purpose.
            #
            # Only write it to analyse SOMETHING ELSE: an internal dependency delivered
            # compiled, a "fat" jar, or one precise module.
            # Directories AND jar archives are accepted, separated by ':' (or ';' on
            # Windows, where a path starts with "C:"). Adding an internal dependency's jar
            # brings it into the analysis on the same footing as the project's code — it is
            # often the one you are trying to understand.
            #CLASSES_DIR="target/classes:libs/shared-module-3.2.jar"  # + a dependency
            #CLASSES_DIR="target/my-app-boot.jar"                      # a "fat" jar
            #CLASSES_DIR="modules/billing/target/classes"              # one precise module

            # ── The packages not to see ───────────────────────────────────────── optional
            # The JDK is always hidden: nobody opens java.util.ArrayList to understand their
            # application. The same holds for libraries that are not part of it but that one
            # treats as infrastructure — a logger, an HTTP client, an injection framework.
            #
            # Where that boundary lies depends on what the team owns and what it merely puts
            # up with: it is a choice, not a rule, hence this setting.
            #
            # The time of the hidden packages is not lost: it is attributed to the
            # application method that called them, exactly as for the JDK.
            #HIDDEN_PACKAGES="org.slf4j, ch.qos.logback"
            #HIDDEN_PACKAGES="org.slf4j, io.netty, org.springframework, com.fasterxml"

            # ── The root method ───────────────────────────────────────────── recommended
            # The function whose argument values and single-call tree you want to see.
            # Format package.Class::method. Pick a BUSINESS entry point: a processing step,
            # a computation, a command — not a main, not an accessor.
            # Leave it empty to get coverage and times only.
            ROOT_METHOD="com.example.engine.Calculator::compute"
            #ROOT_METHOD="com.example.api.OrderService::validate"
            #ROOT_METHOD=""

            # ── The sources ───────────────────────────────────────────────── recommended
            # To show the annotated code. Several roots: separated by ':' — or by ';' on
            # Windows, where an absolute path itself starts with "C:".
            SOURCE_DIRS="src/main/java"
            #SOURCE_DIRS="src/main/java:src/generated/java"

            # ── The name of this run ──────────────────────────────────────────── optional
            # Runs accumulate under <OUT_DIR>/runs/ and the view lets you move from one to
            # the next. A telling name is worth more than a timestamp.
            # It stays changeable afterwards, without running again: see OUT_DIR/noms.json.
            #RUN_NAME="acceptance v2"
            #RUN_NAME="incident 4712"

            # ── The profile filter ────────────────────────────────────────────── optional
            # Restricts the time measurements to the application's code. Without it, most of
            # the samples concern the JVM's own compiler: exact, but unreadable.
            # Deduced from ROOT_METHOD's package when absent.
            #CLASS_FILTER="com/example/*"

            # ── Output and safety limits ──────────────────────────────────────── optional
            OUT_DIR="runtime-xray-out"

            # Delay before inspecting the values. The application must have started AND
            # still be working. If it is too fast, increase its load rather than shortening
            # this delay: a reading over two seconds says nothing.
            ATTACH_AFTER=8

            # Beyond this the run is stopped, and the reports are produced all the same.
            MAX_SECONDS=600

            # How many calls have their values captured, across the root class's methods.
            WATCH_COUNT=10

            # How many invocations have their call tree traced. Each invocation takes
            # different branches: the more there are, the more lines of code carry the
            # "calls …" annotation. The cost is low.
            TRACE_COUNT=10

            # ── The footprint on the observed application ─────────────────────── optional
            # Three pieces of information, not the same value nor the same cost: what ran
            # comes before who calls whom, which comes before with which values. LEVEL says
            # how far one goes, and it is the first setting to lower when the measurement
            # becomes too expensive.
            #
            #   coverage  JaCoCo alone — the cheapest, and the most reliable information
            #   tree      + stack sampling
            #   full      + value capture (the default)
            #NIVEAU="tree"

            # How much of JaCoCo's own rendering to write, per run. This governs neither
            # the measurement nor what the page shows — the coverage displayed is read from
            # jacoco.xml, always written. It governs the number of FILES.
            #
            # JaCoCo's HTML site holds two files per class, and the tool asks for two such
            # sites per run: the count grows with the analysed code, not with the
            # measurement. Where every file open crosses a stack of security filters, that
            # is what a campaign pays for.
            #
            #   full      both sites (the default)
            #   detailed  the complete site alone — the focused report holds no datum it lacks
            #   data      jacoco.xml and .csv only; the campaign's merged report still stands
            #   minimal   and not the merged site either; its XML and CSV are still written
            #
            # Every value but the default GIVES A RENDERING UP. None of them changes the
            # coverage the page shows — it is read from jacoco.xml, written in every case —
            # and an absent report stays named in the page, with the command that produces
            # it. "runtime-xray --help" weighs them up one by one.
            #JACOCO_REPORTS="detailed"

            # Gathering runs/ into a single runs.zip once the report is built, for a machine
            # where every file open crosses a security filter and the count is paid again at
            # each walk.
            #
            #   keep      the archive beside the tree — takes nothing away, reduces nothing
            #   replace   the archive INSTEAD of the tree, once it has been verified
            #
            # "replace" gives something up: the page's links to the JaCoCo sites stop
            # resolving, and --report-only and --serve have nothing left to rebuild from.
            # The page, the diagnostic, the facts and the Markdown still read.
            #ARCHIVE="keep"

            # Classes JaCoCo instruments, in its agent's format (patterns separated by ':').
            # Without this setting, EVERY class loaded is instrumented, dependencies
            # included: it is the main cost centre on an enterprise application.
            #COVER_INCLUDES="com.example.*:com.example.shared.*"

            # The stack sampling interval, in milliseconds. Multiplying it by ten divides
            # the number of samples — and the cost that goes with it — by ten.
            #SAMPLE_INTERVAL_MS=10

            # Rewriting the measurements for other tools: perf, cpuprofile, lcov, values,
            # or "all". The files go into <run>/exports/.
            #EXPORT="cpuprofile,lcov"

            # Serving the report is not set here: it is a way of launching, not a property
            # of the project. "--serve" serves the output directory and lets the page write
            # its annotations beside the runs; "--serve-host 0.0.0.0" makes it a shared
            # server, where several people annotate in parallel, which "--serve-token"
            # closes with a secret (XRAY_SERVE_TOKEN so as not to expose it in "ps"). A
            # secret does not belong in a file under version control.

            # The repository to fetch the analysis components from, once. On a closed
            # network, name the internal mirror: it is the only setting that matters for
            # working without access to the Internet.
            #MAVEN_REPO="https://nexus.internal.example.com/repository/maven-public"
            """;
}
