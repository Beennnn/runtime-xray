package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the single page out of the tools' outputs.
 *
 * <p>Nothing is measured here: we re-read what was produced and put it side by side. The
 * page is <b>self-contained</b> — data embedded, no external resource — which makes it
 * readable offline and sendable as an attachment.
 */
public final class Dashboard {

    private Dashboard() {}

    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod)
            throws Exception {
        return build(commonDir, sourceRoots, valuesPerMethod, PackageFilter.NONE);
    }

    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod,
                             PackageFilter hidden) throws Exception {
        return build(commonDir, sourceRoots, valuesPerMethod, hidden, null);
    }

    /**
     * @param hidden packages hidden by the current configuration. A run that recorded its
     *               own list keeps that one: it is the list it was analysed under, and the
     *               view must say what was done, not what one would do today.
     */
    public static Path build(Path commonDir, List<Path> sourceRoots, int valuesPerMethod,
                             PackageFilter hidden, Map<String, Object> launch)
            throws Exception {
        Map<String, Object> overrides = Annotations.readCentral(commonDir);
        List<Path> bases = findRuns(commonDir, 0, 3);
        if (bases.isEmpty()) {
            throw new IOException("no run found under " + commonDir
                    + " (a run is a directory containing run-context.json)");
        }
        // The most recent first: that is the one just produced.
        bases.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());

        List<Object> runs = new ArrayList<>();
        for (Path base : bases) {
            runs.add(readRun(base, commonDir, overrides, valuesPerMethod, hidden));
        }

        Sources.Index index = Sources.load(sourceRoots);

        // The bulk goes into blocks, loaded on demand and releasable. What stays here is
        // what the FIRST PAINT shows: the identity of the runs, the tree of classes, and
        // the source keys — never their content.
        Map<String, Object> data =
                new LinkedHashMap<>(Blocks.write(commonDir, runs, showableSources(runs, index)));
        // The diagnostic travels WITH the page, not merely beside it: it is what lets the
        // code panel say what it looked for when it has nothing to show. A page sent as an
        // attachment then stays explainable without its original directory.
        Map<String, Object> diagnostic = Diagnostic.write(commonDir, runs, index, launch);
        data.put("diagnostic", withoutCode(diagnostic));
        // The same content, but arranged to be filtered rather than browsed — see Facts.
        // Nothing above changes: this file is added, it replaces nothing.
        Facts.write(commonDir, runs, index, diagnostic);
        data.put("fusion", mergePresent(commonDir, runs.size()));

        String template = loadTemplate();
        String page = template.replace("/*__DATA__*/", Json.write(data));
        Path out = commonDir.resolve("index.html");
        Files.writeString(out, page, StandardCharsets.UTF_8);

        // The campaign on one page, in SVG: the interactive view answers the questions put
        // to it, it does not travel. This diagram pastes into a slide.
        Diagram.write(commonDir, runs);

        // The same content in Markdown, at no extra collection cost: it is the only format
        // a forge shows as a page and not as source code.
        Markdown.write(commonDir, runs);
        return out;
    }

    /**
     * The diagnostic, relieved of what the page already carries.
     *
     * <p>Each run's context and the list of its reports are in {@code runs}: embedding them
     * a second time would double that data in a page that gets sent as an attachment. The
     * {@code diagnostic.json} file keeps them — it is read on its own.
     */
    /**
     * The sources the page can actually show — and nothing else.
     *
     * <p>We used to embed the whole index, that is, every {@code .java} met under the
     * roots. But the code view only opens a file through a measured class: a source absent
     * from the coverage has no path leading to it, ever. It weighed without being able to
     * show.
     *
     * <p>The cost stayed invisible as long as one named the exact source directory. It blew
     * up when the index started accepting any root at all: pointing at the whole project
     * becomes convenient, and then embeds the tests, the generated sources, the unpacked
     * dependencies. A 217 MB report that Firefox gives up on displaying, seen on
     * 26 August 2026; on the sample project, <b>93 % of the sources' weight</b> could not
     * be displayed.
     *
     * <p>Nothing functional goes with it: the complete index stays in the diagnostic, which
     * still says what was read and where. Only what had no display path stops travelling.
     */
    static Map<String, Object> showableSources(List<Object> runs, Sources.Index index) {
        Map<String, Object> kept = new LinkedHashMap<>();
        for (String key : Diagnostic.samples(runs)) {
            Object lines = index.byKey().get(key);
            if (lines != null) kept.put(key, lines);
        }
        return kept;
    }

    /**
     * The JaCoCo report of all the runs together, if it was produced.
     *
     * <p>We report its presence rather than deducing it from the number of runs: it can be
     * missing for good reasons — bytecode unknown on reassembly, JaCoCo component not found
     * — and a dead link is worth less than no link.
     */
    private static Map<String, Object> mergePresent(Path commonDir, int runs) {
        Map<String, Object> m = new LinkedHashMap<>();
        Path html = commonDir.resolve("jacoco-fusion/html");
        m.put("couverture", Files.isRegularFile(html.resolve("index.html")));
        m.put("xml", Files.isRegularFile(html.resolve("jacoco.xml")));
        m.put("csv", Files.isRegularFile(html.resolve("jacoco.csv")));
        m.put("executions", runs);
        return m;
    }

    private static Map<String, Object> withoutCode(Map<String, Object> diagnostic) {
        Map<String, Object> thinned = new LinkedHashMap<>(diagnostic);
        thinned.remove("executions");
        return thinned;
    }

    private static Map<String, Object> readRun(Path base, Path commonDir,
                                               Map<String, Object> overrides, int valuesPerMethod,
                                               PackageFilter fallback)
            throws Exception {
        Map<String, Object> context = readContext(base);
        Object recorded = context.get("paquetsMasques");
        PackageFilter hidden = recorded == null
                ? fallback
                : PackageFilter.of(String.valueOf(recorded));

        Coverage coverage = Coverage.parse(base.resolve("jacoco/html/jacoco.xml"), hidden);
        CallTree tree = CallTree.parse(base.resolve("async-profiler/profil.collapsed"), hidden);
        Inspection inspection = Inspection.read(
                base.resolve("arthas/watch-params.txt"),
                base.resolve("arthas/trace-calltree.txt"),
                valuesPerMethod);

        String uuid = String.valueOf(context.getOrDefault("uuid", ""));
        Object recordedName = context.get("nomOrigine");
        String origin = recordedName == null || String.valueOf(recordedName).isBlank()
                ? null
                : String.valueOf(recordedName);
        // Three possible locations, the closest to the run wins — see Annotations for
        // what each one implies.
        Annotation annotation = Annotation.of(Annotations.forRun(base, uuid, overrides));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("uuid", uuid);
        run.put("nomOrigine", origin);
        run.put("nomOutil", annotation.name);
        run.put("description", annotation.description);
        run.put("etiquettes", annotation.tags);
        run.put("elagage", annotation.pruning);
        run.put("renomme", annotation.name != null);
        // Three sources, in this order: the name set in the tool, the one given at launch,
        // and failing both the id. No invented label: without a name, what is displayed
        // still designates the run, and only that one.
        run.put("nom", annotation.name != null ? annotation.name
                : origin != null ? origin
                : shortId(uuid, base));
        run.put("chemin", relative(commonDir, base));
        run.put("rapports", reportsPresent(base));
        // The view must be able to say what was set aside BEFORE the measurement: that
        // code is not "absent", it was silenced, and silencing the difference would be
        // lying by omission.
        run.put("paquetsMasques", recorded);
        run.put("coverage", coverage.lines);
        run.put("methods", coverage.methods);
        run.put("packages", coverage.packages);
        run.put("calltree", tree.root);
        // One sample every millisecond: that is what makes it possible to turn a number of
        // samples into an estimated duration in the view.
        run.put("intervalMs", 1);
        run.put("profileNote", tree.note);
        run.put("stacksNote", tree.stacksNote);
        run.put("trace", inspection.trace);
        run.put("values", inspection.values);
        run.put("context", context.isEmpty() ? null : context);
        run.put("tracedClass", tracedClass(context, inspection));
        return run;
    }

    /**
     * The inspected class is deduced, in order: from the run's context, otherwise from the
     * values actually captured. Nothing is hard-coded — the report must hold for any
     * application at all.
     */
    private static String tracedClass(Map<String, Object> context, Inspection inspection) {
        Object root = context.get("methodeRacine");
        if (root instanceof String s && !s.isBlank()) {
            return s.split("::")[0].replace('.', '/');
        }
        for (String frame : inspection.values.keySet()) {
            return frame.substring(0, frame.lastIndexOf('.'));
        }
        return "";
    }

    /**
     * What each tool actually wrote to disk, raw files included.
     *
     * <p>Everything is listed, not just the presentable pages: this page is a summary, and
     * a summary can be wrong or stop opening. The original outputs must stay one click
     * away — that is what separates a report one can check from a report one has to take on
     * trust.
     */
    private static Map<String, Object> reportsPresent(Path base) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("couverture", Files.isRegularFile(base.resolve("jacoco/html/index.html")));
        m.put("ciblee", Files.isRegularFile(base.resolve("jacoco-focused/html/index.html")));
        m.put("jacocoXml", Files.isRegularFile(base.resolve("jacoco/html/jacoco.xml")));
        m.put("jacocoCsv", Files.isRegularFile(base.resolve("jacoco/html/jacoco.csv")));
        m.put("flamegraph", Files.isRegularFile(base.resolve("async-profiler/flamegraph.html")));
        m.put("flamegraphInverse",
                Files.isRegularFile(base.resolve("async-profiler/flamegraph-inverse.html")));
        m.put("collapsed", Files.isRegularFile(base.resolve("async-profiler/profil.collapsed")));
        m.put("valeurs", Files.isRegularFile(base.resolve("arthas/watch-params.txt")));
        m.put("traceBrute", Files.isRegularFile(base.resolve("arthas/trace-calltree.txt")));
        m.put("exportPerf", Files.isRegularFile(base.resolve("exports/profil.perf.txt")));
        m.put("exportCpuprofile", Files.isRegularFile(base.resolve("exports/profil.cpuprofile")));
        m.put("exportLcov", Files.isRegularFile(base.resolve("exports/couverture.lcov")));
        m.put("exportValeurs", Files.isRegularFile(base.resolve("exports/valeurs.json")));
        m.put("journal", Files.isRegularFile(base.resolve("execution.log")));
        m.put("contexte", Files.isRegularFile(base.resolve("run-context.json")));
        return m;
    }

    private static String relative(Path commonDir, Path base) {
        Path rel = commonDir.toAbsolutePath().normalize()
                .relativize(base.toAbsolutePath().normalize());
        String s = rel.toString().replace('\\', '/');
        return s.isEmpty() ? "" : s + "/";
    }

    /** A run is a directory holding its context, or failing that its coverage. */
    private static List<Path> findRuns(Path dir, int level, int maxDepth) throws IOException {
        List<Path> found = new ArrayList<>();
        if (level > maxDepth || !Files.isDirectory(dir)) {
            return found;
        }
        if (Files.isRegularFile(dir.resolve("run-context.json"))
                || Files.isRegularFile(dir.resolve("jacoco/html/jacoco.xml"))) {
            found.add(dir);
            return found;
        }
        try (var entries = Files.list(dir)) {
            for (Path sub : entries.sorted().toList()) {
                if (Files.isDirectory(sub) && !sub.getFileName().toString().startsWith(".")) {
                    found.addAll(findRuns(sub, level + 1, maxDepth));
                }
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readContext(Path base) {
        Path file = base.resolve("run-context.json");
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            return (Map<String, Object>) Json.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("   run context unreadable: " + file + " (" + e.getMessage() + ")");
            return new LinkedHashMap<>();
        }
    }

    /** Failing a name, the shortened id: short enough to fit, long enough to sort by. */
    private static String shortId(String uuid, Path base) {
        if (uuid == null || uuid.isBlank()) return base.getFileName().toString();
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    /** Name, description and tags placed on a run after it was measured. */
    private record Annotation(String name, String description, Map<String, Object> tags,
                              Map<String, Object> pruning) {

        static final Annotation EMPTY = new Annotation(null, null, Map.of(), null);

        @SuppressWarnings("unchecked")
        static Annotation of(Object recorded) {
            if (recorded == null) return EMPTY;
            if (recorded instanceof Map<?, ?> map) {
                Map<String, Object> m = (Map<String, Object>) map;
                Object tags = m.get("etiquettes");
                Object pruning = m.get("elagage");
                return new Annotation(
                        text(m.get("nom")),
                        text(m.get("description")),
                        tags instanceof Map<?, ?> t ? (Map<String, Object>) t : Map.of(),
                        pruning instanceof Map<?, ?> p ? (Map<String, Object>) p : null);
            }
            return new Annotation(text(recorded), null, Map.of(), null);
        }

        private static String text(Object value) {
            if (value == null) return null;
            String s = String.valueOf(value).trim();
            return s.isEmpty() ? null : s;
        }
    }

    private static String loadTemplate() throws IOException {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            if (in == null) {
                throw new IOException("page template missing from the jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
