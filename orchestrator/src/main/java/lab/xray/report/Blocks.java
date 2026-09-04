package lab.xray.report;

import lab.xray.json.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The report's data, taken out of the page and filed where it belongs.
 *
 * <p>A page that carries everything stops opening past a certain size: 217 MB seen on
 * 26 August 2026, which Firefox gave up on displaying. Yet one never looks at everything —
 * one opens a class, then another, and ticks two runs out of ten. What has to be there at
 * the first paint fits in a few tens of kilobytes; the rest waits to be asked for.
 *
 * <h2>What goes where</h2>
 *
 * <pre>
 * runtime-xray-out/
 *   index.html                      the view
 *   diagnostic.json                 what explains a disappointing report
 *   runs/&lt;run&gt;/                  THE CAPTURE, unchanged — jacoco/, arthas/, run-context.json
 *   runs/&lt;run&gt;/vue/             what follows from THAT run, and from it alone
 *       couverture.js  arbre.js  valeurs.js  traces.js
 *   vue/                            what CROSSES runs, and so belongs to none of them
 *       sources/&lt;package&gt;.js     the code, grouped by package
 *       cumul.js                    per line, which runs covered it
 *       poids.js                    per method, its time in each run
 *       manifeste.json              what this directory holds, in plain sight
 * </pre>
 *
 * <p>The rule is simple and it decides everything: <b>what follows from one run lives under
 * it</b>, what crosses several lives above. Copying a run's directory therefore takes with
 * it everything that concerns it; removing a run leaves no orphan elsewhere, except the
 * crossing indexes — which a reassembly rebuilds in seconds.
 *
 * <h2>The format, and why that one</h2>
 *
 * <p>Each block is a {@code .js} file in which <b>every line is one record</b>:
 *
 * <pre>XR.bloc("src","com/example/app/Application.java",["package com.example.app;","…"]);</pre>
 *
 * <p>Three properties, and all three are needed:
 * <ul>
 *   <li><b>readable in ASCII, at the command line</b> — one line per record, the key first,
 *       so {@code grep}, {@code wc -l} and {@code head} work on it directly. Stripping the
 *       wrapper gives pure JSON for {@code jq}:
 *       {@code sed 's/^XR.bloc(//; s/);$//'};</li>
 *   <li><b>loadable from {@code file://}</b> — it is JavaScript, so a {@code <script src>}
 *       accepts it. A {@code fetch} on a neighbouring {@code .json} does not: the browser
 *       refuses it on origin grounds, and the report would no longer open by
 *       double-clicking it;</li>
 *   <li><b>releasable</b> — a loaded block can be forgotten: the page removes its script
 *       and erases its entry, which a literal embedded in the page never allows.</li>
 * </ul>
 *
 * <p>These files are a <b>projection for display</b>: generated, disposable, rebuildable
 * with a {@code --report-only}. Never one more source of truth — that stays the capture,
 * under {@code runs/}.
 */
public final class Blocks {

    /** The directory of the indexes that cross runs, beside the page. */
    public static final String GLOBAL = "vue";

    /** The index sub-directory, under each run. */
    public static final String PER_RUN = "vue";

    /**
     * The format of the generated report — the page and its blocks.
     *
     * <p>Distinct from the {@link Capture capture format}: this one covers what the tool
     * <b>produces</b>, that one what a run <b>recorded</b>. The first is thrown away and
     * rebuilt; the second would cost a campaign.
     *
     * <p>The page demands it and refuses what it does not know. That is deliberate: the
     * view does not have to carry yesterday's shapes, since rebuilding them costs nothing.
     * It is the capture format that protects what is expensive, and it alone.
     */
    public static final String FORMAT = "2.0";

    /** What only appears after a gesture, and so does not have to be there before. */
    private static final List<String> HEAVY =
            List.of("coverage", "calltree", "values", "trace");

    /** The file name of each heavy piece of data, under {@code <run>/vue/}. */
    private static final Map<String, String> FILES = Map.of(
            "coverage", "couverture.js",
            "calltree", "arbre.js",
            "values", "valeurs.js",
            "trace", "traces.js");

    private Blocks() {}

    /**
     * Writes the blocks, and returns the summary — what has to be present at the first
     * paint.
     *
     * @param commonDir the output directory
     * @param runs      the runs as the view receives them
     * @param sources   the showable sources, by {@code package/File.java} key
     */
    public static Map<String, Object> write(Path commonDir, List<Object> runs,
                                             Map<String, Object> sources) throws IOException {
        Path global = commonDir.resolve(GLOBAL);
        Files.createDirectories(global);

        List<Object> summary = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            String subDir = String.valueOf(run.get("chemin")) + PER_RUN + "/";
            Path dir = commonDir.resolve(subDir);
            Files.createDirectories(dir);
            summary.add(thin(run, subDir));
            writeRun(dir, run);
        }

        Map<String, String> blockByKey = writeSources(global.resolve("sources"), sources);
        writeCumulative(global.resolve("cumul.js"), runs);
        writeWeights(global.resolve("poids.js"), runs);
        writePresence(global.resolve("presence.js"), runs);
        writeManifest(global.resolve("manifeste.json"), runs, sources.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("format", FORMAT);
        out.put("runs", summary);
        // The source KEYS stay in the page, never their content: the tree must be able to
        // say "this one has its code" without loading the code to find out, otherwise the
        // first paint would ask back for everything we just took out of it.
        out.put("sourcesDisponibles", blockByKey);
        out.put("global", GLOBAL + "/");
        return out;
    }

    /**
     * What a run keeps in the page, and what it sends under its own directory.
     *
     * <p>The boundary is not arbitrary: what the <b>first paint</b> shows stays — the run's
     * identity, its reports, and the tree of classes with their counters. What only appears
     * once a class is opened or a run ticked goes.
     */
    static Map<String, Object> thin(Map<?, ?> run, String subDir) {
        Map<String, Object> light = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : run.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (!HEAVY.contains(key)) light.put(key, e.getValue());
        }
        List<Object> blocks = new ArrayList<>();
        for (String key : HEAVY) {
            if (run.get(key) != null) blocks.add(subDir + FILES.get(key));
            // A field no run will ever carry is not a field that is pending. Without a
            // profile — on Windows, where async-profiler publishes nothing — there will
            // never be a call tree, and without Arthas never any values. The page told
            // "loaded" from "not loaded yet"; it had no reason to know a third state, and
            // splitting into blocks had just invented one. So the empty shape is written
            // into the summary: readers have nothing more to know, and "not there yet"
            // becomes the only special case again.
            else light.put(key, EMPTY.get(key));
        }
        light.put("blocs", blocks);
        // A run's sample count fits in one number, and the "Runs" tab shows it before
        // anything is opened — so for runs whose tree has not been loaded, and will not
        // be: showing them all would cost exactly what the lazy loading has just saved.
        // It therefore lives in the summary.
        light.put("mesures", samplesOf(run));
        return light;
    }

    /** The empty shape of each heavy field: what a reader must find failing anything better. */
    private static final Map<String, Object> EMPTY = Json.ordered(
            "coverage", Json.ordered(),
            "calltree", Json.ordered("name", "tout", "total", 0, "children", List.of()),
            "values", Json.ordered(),
            "trace", Json.ordered());

    private static long samplesOf(Map<?, ?> run) {
        return run.get("calltree") instanceof Map<?, ?> tree
                && tree.get("total") instanceof Number n ? n.longValue() : 0L;
    }

    private static void writeRun(Path dir, Map<?, ?> run) throws IOException {
        String uuid = String.valueOf(run.get("uuid"));
        for (String key : HEAVY) {
            Object value = run.get(key);
            if (value == null) continue;
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(FILES.get(key)),
                    StandardCharsets.UTF_8)) {
                line(w, "run", uuid + "/" + key, value);
            }
        }
    }

    /**
     * The sources, grouped by package.
     *
     * <p>One file per source would make thousands of files — tiresome to copy, slow on a
     * network share. One file per package keeps blocks of a few tens of kilobytes, which is
     * the granularity one navigates at: one rarely opens a class without looking at its
     * neighbours.
     */
    private static Map<String, String> writeSources(Path dir, Map<String, Object> sources)
            throws IOException {
        Files.createDirectories(dir);
        Map<String, Map<String, Object>> byPackage = new TreeMap<>();
        for (Map.Entry<String, Object> e : sources.entrySet()) {
            String key = e.getKey();
            int i = key.lastIndexOf('/');
            String pkg = i < 0 ? "(defaut)" : key.substring(0, i);
            byPackage.computeIfAbsent(pkg, k -> new LinkedHashMap<>()).put(key, e.getValue());
        }
        Map<String, String> blockByKey = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> e : byPackage.entrySet()) {
            String name = fileName(e.getKey()) + ".js";
            try (BufferedWriter w = Files.newBufferedWriter(dir.resolve(name),
                    StandardCharsets.UTF_8)) {
                for (Map.Entry<String, Object> f : e.getValue().entrySet()) {
                    line(w, "src", f.getKey(), f.getValue());
                    blockByKey.put(f.getKey(), GLOBAL + "/sources/" + name);
                }
            }
        }
        return blockByKey;
    }

    /**
     * The cumulative index: for each line, <b>which runs</b> covered it.
     *
     * <p>Without it, bringing the ticked runs together requires, for every line displayed,
     * consulting each one's coverage — ten runs, ten lookups per line, redone at every
     * change of a checkbox. It is a {@code lines × runs} traversal, and it would force
     * loading every run in order to display one.
     *
     * <p>So it is computed once, as <b>bit masks</b>: bit <i>i</i> says that run <i>i</i>
     * covered this line. Bringing a subset together becomes a binary AND — one operation
     * per line, whatever the number of runs. Two masks, because "covered" and "partly
     * covered" are not to be confused.
     */
    private static void writeCumulative(Path file, List<Object> runs) throws IOException {
        Map<String, Map<Integer, int[]>> index = new TreeMap<>();
        int bit = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            if (bit > 30) break;   // beyond that the mask overflows: we stop rather than lie
            int flag = 1 << bit++;
            if (!(run.get("coverage") instanceof Map<?, ?> coverage)) continue;
            for (Map.Entry<?, ?> f : coverage.entrySet()) {
                if (!(f.getValue() instanceof Map<?, ?> lines)) continue;
                Map<Integer, int[]> byLine =
                        index.computeIfAbsent(String.valueOf(f.getKey()), k -> new TreeMap<>());
                for (Map.Entry<?, ?> l : lines.entrySet()) {
                    if (!(l.getValue() instanceof Map<?, ?> state)) continue;
                    String s = String.valueOf(state.get("s"));
                    if ("miss".equals(s)) continue;
                    int[] hidden = byLine.computeIfAbsent(toInt(l.getKey()), k -> new int[2]);
                    hidden["full".equals(s) ? 0 : 1] |= flag;
                }
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, Map<Integer, int[]>> f : index.entrySet()) {
                List<Object> flat = new ArrayList<>();
                for (Map.Entry<Integer, int[]> l : f.getValue().entrySet()) {
                    flat.add(List.of(l.getKey(), l.getValue()[0], l.getValue()[1]));
                }
                line(w, "cumul", f.getKey(), flat);
            }
        }
    }

    /**
     * The weights index: for each method, its time in each run.
     *
     * <p>The overview ranks the costliest methods <b>across all ticked runs</b>. Computing
     * that on display requires walking each one's call tree — so loading them all, for a
     * list of six lines. It is the crossing traversal that costs the most, and the only one
     * that stood in the way of loading only what is being looked at.
     *
     * <p>One line per method, one number per run, in the summary's order.
     */
    private static void writeWeights(Path file, List<Object> runs) throws IOException {
        Map<String, long[]> weight = new TreeMap<>();
        int n = runs.size(), i = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) { i++; continue; }
            final int rank = i++;
            if (run.get("calltree") instanceof Map<?, ?> tree) {
                walk(tree, (name, total) ->
                        weight.computeIfAbsent(name, k -> new long[n])[rank] += total);
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, long[]> e : weight.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (long v : e.getValue()) values.add(v);
                line(w, "poids", e.getKey(), values);
            }
        }
    }

    /** Walks down a call tree, announcing each node and its total. */
    private static void walk(Map<?, ?> node, java.util.function.BiConsumer<String, Long> seen) {
        if (!(node.get("children") instanceof Iterable<?> children)) return;
        for (Object e : children) {
            if (!(e instanceof Map<?, ?> child)) continue;
            Object name = child.get("name");
            Object total = child.get("total");
            if (name != null && total instanceof Number t) seen.accept(String.valueOf(name), t.longValue());
            walk(child, seen);
        }
    }

    /**
     * The presence index: for each method, in how many places each run calls it.
     *
     * <p>It is what the view shows under "also called in": for an open method it must know
     * which <b>other</b> runs go through it, and how many times. Counting that on display
     * requires walking down each one's call tree — so loading every run in order to look at
     * one.
     *
     * <p>The count is enough for the display; the path itself is only read on a click, and
     * then the run in question is loaded for real. That separates what is always shown from
     * what is rarely asked for.
     */
    private static void writePresence(Path file, List<Object> runs) throws IOException {
        Map<String, int[]> presence = new TreeMap<>();
        int n = runs.size(), i = 0;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) { i++; continue; }
            final int rank = i++;
            if (run.get("calltree") instanceof Map<?, ?> tree) {
                walk(tree, (name, total) ->
                        presence.computeIfAbsent(name, k -> new int[n])[rank]++);
            }
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, int[]> e : presence.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (int v : e.getValue()) values.add(v);
                line(w, "presence", e.getKey(), values);
            }
        }
    }

    /**
     * What the directory holds, in plain sight.
     *
     * <p>A report folder gets copied from one machine to another, unzipped six months
     * later, found missing a directory. A readable manifest then says what ought to be
     * there — without it, one has to open the page to discover what is missing.
     */
    private static void writeManifest(Path file, List<Object> runs, int sources)
            throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("formatCaptureMinimal", Capture.MINIMUM);
        List<Object> entries = new ArrayList<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("nom", run.get("nom"));
            e.put("uuid", run.get("uuid"));
            e.put("chemin", run.get("chemin"));
            e.put("vue", String.valueOf(run.get("chemin")) + PER_RUN + "/");
            entries.add(e);
        }
        m.put("executions", entries);
        m.put("sourcesAffichables", sources);
        m.put("indexGlobaux", List.of("cumul.js", "poids.js", "presence.js", "sources/"));
        // A program that discovers this folder will not guess there is an output made for
        // it. The manifest is where we tell it — and "../" because the facts speak of the
        // whole campaign, not of this view.
        m.put("faits", "../" + Facts.FILE);
        m.put("faitsFormat", Facts.FORMAT);
        Files.writeString(file, Json.write(m), StandardCharsets.UTF_8);
    }

    /** One record, one line — that rule is what makes the file greppable. */
    private static void line(BufferedWriter w, String type, String key, Object value)
            throws IOException {
        w.write("XR.bloc(");
        w.write(Json.write(type));
        w.write(",");
        w.write(Json.write(key));
        w.write(",");
        w.write(Json.write(value));
        w.write(");\n");
    }

    /**
     * A package name turned into a safe file name.
     *
     * <p>Packages contain only letters, digits and dots, but an unexpected key must not be
     * able to write anywhere other than the intended directory — hence the filter, and not
     * a plain substitution.
     */
    static String fileName(String pkg) {
        StringBuilder sb = new StringBuilder();
        for (char c : pkg.toCharArray()) {
            sb.append(Character.isLetterOrDigit(c) ? c : '-');
        }
        String s = sb.toString().replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "divers" : s;
    }

    private static int toInt(Object o) {
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
