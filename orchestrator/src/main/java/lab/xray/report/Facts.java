package lab.xray.report;

import lab.xray.json.Json;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The same report, written to be <b>filtered</b> rather than browsed.
 *
 * <p>The page arranges the measurements as a tree — packages, classes, methods — because
 * that is how a human descends towards what they are looking for. A program does not
 * descend: it <b>selects</b>. "Which classes never ran?" is answered here with a
 * {@code grep}, there with a full traversal of the tree. Over 450 classes the difference is
 * not one of comfort: it is an order of magnitude in what has to be read to answer.
 *
 * <p>Hence this file: <b>one fact per line</b>, flat, each line standing on its own. No
 * context to reconstruct, no order to respect, no companion file to open. One can read ten
 * lines from the middle and understand them.
 *
 * <pre>
 * grep '"class.never_executed"' faits.jsonl | head -50
 * jq -r 'select(.fact=="unavailable") | .why' faits.jsonl
 * </pre>
 *
 * <h2>What it is not</h2>
 *
 * <p>It <b>takes nothing away</b>. The page, its blocks, {@code diagnostic.json} and
 * {@code rapport.md} are written as before, to the bit: this file is added beside them.
 * There are therefore not two formats to maintain in parallel, there is a second output of
 * the same computation — and nothing that reads the old one has to change.
 *
 * <p>Nor is it a replica: it carries <b>neither the source code nor the captured
 * values</b>, which are bulky and only of interest once opened. It carries the facts, and
 * for each of them what is needed to go and fetch the rest.
 *
 * <h2>What matters most</h2>
 *
 * <p>The {@code unavailable} and {@code caveat} lines. A "0 % of time" does not say whether
 * it means "this code did not run" or "no profile could be taken on this platform". A
 * reader who cannot tell will decide anyway — and will be confidently wrong. Those lines
 * exist so that the question never arises.
 */
public final class Facts {

    /** The vocabulary of the facts. It only moves by adding, never by renaming. */
    public static final String FORMAT = "2.0";

    /** The file, at the root of the output: it is the first thing a tool opens. */
    public static final String FILE = "faits.jsonl";

    /** Bounds: a facts file must stay readable in one go, or it becomes a heap again. */
    static final int MAX_MISSING_SOURCES = 500;
    static final int MAX_HOT_METHODS = 25;

    /**
     * What each fact name means.
     *
     * <p>Written in the file itself, not beside it: that is what lets a program read an
     * output it has never seen. Every addition to the vocabulary is added here, without
     * which it would be mute.
     */
    static final Map<String, String> VOCABULARY = new LinkedHashMap<>();
    static {
        VOCABULARY.put("campaign", "the header: tool, version, date, and where to find the rest");
        VOCABULARY.put("run", "one observed run: identity, command, machine");
        VOCABULARY.put("unavailable",
                "a measurement that was NOT taken, and why — read BEFORE any zero, which "
                + "otherwise reads exactly like \"never ran\"");
        VOCABULARY.put("caveat", "a measurement that was taken, but whose reach the tool limits");
        VOCABULARY.put("coverage.run",
                "instructions covered out of the total, for one run (JaCoCo measurement)");
        VOCABULARY.put("class",
                "a class and its best coverage over the campaign, with the runs that covered it");
        VOCABULARY.put("class.never_executed",
                "an analysed class no run ever reached — the fact most often looked for");
        VOCABULARY.put("method.hot",
                "a method among the most costly in time, counted in stack samples");
        VOCABULARY.put("source.missing",
                "a file whose coverage is known but not its code: source root not configured, "
                + "never \"the code does not exist\"");
        VOCABULARY.put("source.hint",
                "a root to add to SOURCE_DIRS, with the number of files it would resolve — "
                + "the only directly actionable item of the lot");
    }

    private Facts() {}

    /**
     * Writes the campaign's facts.
     *
     * @param diagnostic what {@link Diagnostic} has just produced — nothing is recomputed,
     *                   we take the source leads from it, which are the only directly
     *                   actionable piece of the lot.
     */
    public static Path write(Path commonDir, List<Object> runs, Sources.Index index,
                              Map<String, Object> diagnostic) throws IOException {
        Path file = commonDir.resolve(FILE);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            line(w, campaign(commonDir, runs, diagnostic));
            for (Object o : runs) {
                if (!(o instanceof Map<?, ?> run)) continue;
                line(w, run(run));
                for (Map<String, Object> f : unavailabilities(run)) line(w, f);
                for (Map<String, Object> f : caveats(run)) line(w, f);
                line(w, coverageOf(run));
                for (Map<String, Object> f : hotMethods(run)) line(w, f);
            }
            for (Map<String, Object> f : classes(runs, index)) line(w, f);
            for (Map<String, Object> f : missingSources(runs, index)) line(w, f);
            for (Map<String, Object> f : leads(diagnostic)) line(w, f);
        }
        return file;
    }

    private static void line(BufferedWriter w, Map<String, Object> fact) throws IOException {
        w.write(Json.write(fact));
        w.write("\n");
    }

    // ------------------------------------------------------------------ the campaign

    private static Map<String, Object> campaign(Path commonDir, List<Object> runs,
                                                Map<String, Object> diagnostic) {
        Map<String, Object> f = fact("campaign");
        f.put("factsFormat", FORMAT);
        f.put("reportFormat", Blocks.FORMAT);
        f.put("tool", diagnostic.get("outil"));
        f.put("version", diagnostic.get("version"));
        f.put("date", diagnostic.get("date"));
        f.put("output", commonDir.toAbsolutePath().normalize().toString());
        f.put("runs", runs.size());
        // Where to go when a fact is not enough. A reader arriving through this file has
        // no reason to guess that the page and the diagnostic exist.
        f.put("alsoSee", Map.of(
                "page", "index.html",
                "diagnostic", "diagnostic.json",
                "markdown", "rapport.md",
                "manifeste", Blocks.GLOBAL + "/manifeste.json"));
        // The file describes itself, from its very first line. A reader who has only this
        // file — that is the case of a zipped folder, or of a program that has read only
        // the head — must be able to understand the rest without outside documentation.
        // Separate documentation gets lost; this one travels with the data.
        f.put("vocabulary", VOCABULARY);
        return f;
    }

    private static Map<String, Object> run(Map<?, ?> run) {
        Map<String, Object> f = fact("run");
        f.put("run", run.get("uuid"));
        f.put("name", run.get("nom"));
        f.put("path", run.get("chemin"));
        f.put("measurements", samples(run));
        f.put("intervalMs", run.get("intervalMs"));
        if (run.get("context") instanceof Map<?, ?> ctx) {
            for (String key : List.of("commande", "machine", "systeme", "java", "debut",
                    "fin", "duree", "statut", "methodeRacine")) {
                if (ctx.get(key) != null) f.put(key, ctx.get(key));
            }
        }
        return f;
    }

    // ------------------------------------------------ what was NOT measured, and why

    /**
     * Absence, declared.
     *
     * <p>This is the file's reason for existing. An observer that did not run leaves
     * exactly the same trace as code that was never reached: zero. The difference follows
     * from no figure at all — it has to be written down.
     */
    static List<Map<String, Object>> unavailabilities(Map<?, ?> run) {
        List<Map<String, Object>> out = new ArrayList<>();
        String uuid = String.valueOf(run.get("uuid"));

        if (samples(run) == 0) {
            Map<String, Object> f = fact("unavailable");
            f.put("run", uuid);
            f.put("what", "time");
            f.put("why", "no stack sample was taken: async-profiler only publishes Linux "
                    + "and macOS binaries, and the \"coverage\" level does not enable it");
            f.put("consequence", "no time percentage can be computed for this run; a zero "
                    + "time here does not mean \"never called\"");
            f.put("remedy", "run again on Linux or macOS, at level \"tree\" or \"full\"");
            out.add(f);
        }
        if (empty(run.get("values"))) {
            Map<String, Object> f = fact("unavailable");
            f.put("run", uuid);
            f.put("what", "values");
            f.put("why", "Arthas captured no call: either --root was not given, or the "
                    + "application finished before attachment");
            f.put("consequence", "no argument value is available; this says nothing about what "
                    + "was executed");
            f.put("remedy", "run again with --root package.Class::method, or lower "
                    + "--attach-after");
            out.add(f);
        }
        if (empty(run.get("coverage"))) {
            Map<String, Object> f = fact("unavailable");
            f.put("run", uuid);
            f.put("what", "coverage");
            f.put("why", "no JaCoCo data for this run");
            f.put("consequence", "neither coverage nor a list of executed classes");
            f.put("remedy", "check that the agent was actually loaded — see diagnostic.json");
            out.add(f);
        }
        return out;
    }

    /** The caveats the tool itself places on a measurement that does exist. */
    static List<Map<String, Object>> caveats(Map<?, ?> run) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : List.of("profileNote", "stacksNote")) {
            Object note = run.get(key);
            if (note == null || String.valueOf(note).isBlank()) continue;
            Map<String, Object> f = fact("caveat");
            f.put("run", run.get("uuid"));
            f.put("what", "time");
            f.put("caveat", note);
            out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ the measurements

    private static Map<String, Object> coverageOf(Map<?, ?> run) {
        long covered = 0, missed = 0;
        for (Object classes : pkgs(run).values()) {
            for (Map<?, ?> c : classesOf(classes)) {
                covered += toInt(c.get("covered"));
                missed += toInt(c.get("missed"));
            }
        }
        Map<String, Object> f = fact("coverage.run");
        f.put("run", run.get("uuid"));
        f.put("instructionsCovered", covered);
        f.put("instructionsTotal", covered + missed);
        f.put("pct", percent(covered, covered + missed));
        f.put("measure", "jacoco.instructions");
        return f;
    }

    /**
     * One line per class, valid for the whole campaign.
     *
     * <p>A class appears in several runs with different coverages. The question asked in
     * front of an acceptance report is not "how much in this one?" but "did
     * <b>something</b> cover it, and which?". So we keep the best figure, and the list of
     * those that contributed to it.
     */
    static List<Map<String, Object>> classes(List<Object> runs, Sources.Index index) {
        Map<String, Map<String, Object>> byClass = new TreeMap<>();
        Map<String, Set<String>> covering = new LinkedHashMap<>();
        Map<String, Set<String>> analysed = new LinkedHashMap<>();

        for (Object o : runs) {
            if (!(o instanceof Map<?, ?> run)) continue;
            String uuid = String.valueOf(run.get("uuid"));
            for (Object classes : pkgs(run).values()) {
                for (Map<?, ?> c : classesOf(classes)) {
                    String name = String.valueOf(c.get("name"));
                    long couv = toInt(c.get("covered"));
                    long total = couv + toInt(c.get("missed"));
                    analysed.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(uuid);
                    if (couv > 0) {
                        covering.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(uuid);
                    }
                    Map<String, Object> f = byClass.get(name);
                    if (f == null || toInt(f.get("instructionsCouvertes")) < couv) {
                        f = f == null ? new LinkedHashMap<>() : f;
                        f.put("class", name.replace('/', '.'));
                        f.put("file", c.get("source"));
                        f.put("instructionsCovered", couv);
                        f.put("instructionsTotal", total);
                        f.put("pct", percent(couv, total));
                        byClass.put(name, f);
                    }
                }
            }
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : byClass.entrySet()) {
            Set<String> coveredBy = covering.getOrDefault(e.getKey(), Set.of());
            Map<String, Object> f = fact(coveredBy.isEmpty() ? "class.never_executed" : "class");
            f.putAll(e.getValue());
            // Sorted: without this, two campaigns over the same runs give different lines
            // depending on the order the directories were read in, and nothing compares any
            // more — yet comparing two reports is exactly what one wants to be able to do.
            f.put("runsCovering", sortedList(coveredBy));
            f.put("runsAnalysed", sortedList(analysed.getOrDefault(e.getKey(), Set.of())));
            Object key = e.getValue().get("fichier");
            f.put("sourceAvailable", key != null && index.byKey().containsKey(String.valueOf(key)));
            f.put("measure", "jacoco.instructions");
            out.add(f);
        }
        return out;
    }

    /** A run's costliest methods, when it has a profile. */
    static List<Map<String, Object>> hotMethods(Map<?, ?> run) {
        if (!(run.get("calltree") instanceof Map<?, ?> tree)) return List.of();
        long total = toInt(tree.get("total"));
        if (total <= 0) return List.of();

        Map<String, Long> weight = new LinkedHashMap<>();
        cumulate(tree, weight);
        List<Map<String, Object>> out = new ArrayList<>();
        weight.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_HOT_METHODS)
                .forEach(e -> {
                    Map<String, Object> f = fact("method.hot");
                    f.put("run", run.get("uuid"));
                    f.put("method", e.getKey().replace('/', '.'));
                    f.put("samples", e.getValue());
                    f.put("pct", percent(e.getValue(), total));
                    f.put("measure", "async-profiler.echantillons");
                    out.add(f);
                });
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void cumulate(Map<?, ?> node, Map<String, Long> weight) {
        Object children = node.get("children");
        if (!(children instanceof List<?> list)) return;
        for (Object k : list) {
            if (!(k instanceof Map<?, ?> kid)) continue;
            weight.merge(String.valueOf(kid.get("name")), toInt(kid.get("total")), Long::sum);
            cumulate(kid, weight);
        }
    }

    // -------------------------------------------------------------- what could not be read

    /** Measured files whose code we do not have: cause number one of a disappointing report. */
    static List<Map<String, Object>> missingSources(List<Object> runs, Sources.Index index) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String key : Diagnostic.samples(runs)) {
            if (index.byKey().containsKey(key)) continue;
            if (out.size() >= MAX_MISSING_SOURCES) break;
            Map<String, Object> f = fact("source.missing");
            f.put("key", key);
            f.put("consequence", "the coverage of this file is known, its code is not: the code "
                    + "view has nothing to show");
            f.put("remedy", "add the root holding this package to SOURCE_DIRS — see the "
                    + "\"source.hint\" facts");
            out.add(f);
        }
        return out;
    }

    /** The proposed roots, with their evidence: the only directly actionable information. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> leads(Map<String, Object> diagnostic) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(diagnostic.get("rapprochement") instanceof Map<?, ?> r)) return out;
        if (!(r.get("pistes") instanceof List<?> list)) return out;
        for (Object p : list) {
            if (!(p instanceof Map<?, ?> lead)) continue;
            Map<String, Object> f = fact("source.hint");
            f.putAll((Map<String, Object>) lead);
            out.add(f);
        }
        return out;
    }

    // ------------------------------------------------------------------ menue monnaie

    private static List<String> sortedList(Set<String> s) {
        List<String> l = new ArrayList<>(s);
        l.sort(Comparator.naturalOrder());
        return l;
    }

    private static Map<String, Object> fact(String name) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("fact", name);
        return f;
    }

    private static Map<?, ?> pkgs(Map<?, ?> run) {
        return run.get("packages") instanceof Map<?, ?> p ? p : Map.of();
    }

    private static List<Map<?, ?>> classesOf(Object classes) {
        List<Map<?, ?>> out = new ArrayList<>();
        if (classes instanceof List<?> list) {
            for (Object c : list) if (c instanceof Map<?, ?> m) out.add(m);
        }
        return out;
    }

    private static long samples(Map<?, ?> run) {
        if (run.get("calltree") instanceof Map<?, ?> tree) return toInt(tree.get("total"));
        return toInt(run.get("mesures"));
    }

    private static boolean empty(Object o) {
        return o == null || (o instanceof Map<?, ?> m && m.isEmpty());
    }

    private static long toInt(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static double percent(long share, long total) {
        if (total <= 0) return 0;
        return Math.round(1000.0 * share / total) / 10.0;
    }

    /** The order of the facts, for whoever sorts: the most general first. */
    static final Comparator<Map<String, Object>> ORDER =
            Comparator.comparing(f -> String.valueOf(f.get("fait")));
}
