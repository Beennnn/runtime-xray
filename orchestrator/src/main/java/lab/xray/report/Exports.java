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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Rewriting the measurements into formats other tools can read.
 *
 * <p>The page this tool produces is <b>one</b> way of reading a run, not the only one. A
 * profile examines very well in Firefox Profiler, a coverage shows in an editor, a call
 * tree compares in speedscope. Refusing those uses would amount to locking an open
 * measurement into a single rendering — exactly what the study holds against closed tools.
 *
 * <p>The principle held here: <b>only what was measured is exported</b>. None of the
 * page's folding, hiding or aggregating applies to these files; they reproduce the tools'
 * output in another grammar. What a third party makes of it then depends only on their own
 * tool.
 *
 * <p>The formats kept are public and documented:
 * <ul>
 *   <li><b>perf script</b> — the text output of {@code perf script} on Linux. It is the
 *       import format of Firefox Profiler and of several converters.</li>
 *   <li><b>cpuprofile</b> — Chrome DevTools' sampled profile: compact, read by speedscope
 *       and by the browsers' development tools.</li>
 *   <li><b>LCOV</b> — the most widely accepted coverage format: editors, forges, tracking
 *       services.</li>
 *   <li><b>JSON</b> for the captured values, whose structure is described in the
 *       documentation: there is no established exchange format for that information.</li>
 * </ul>
 */
public final class Exports {

    /**
     * What a {@code perf} export weighs at most.
     *
     * <p>The cap is on <b>bytes</b>, not on the number of samples: this format rewrites the
     * whole stack at every sample, so its size depends first on the <b>depth</b> of the
     * stacks. Eighty thousand samples forty frames deep weigh more than eight hundred
     * thousand samples two deep — counting samples would be counting the wrong thing.
     *
     * <p>Beyond it, we <b>thin out proportionally</b> rather than stopping half way.
     * Stopping cut off the end of the run: everything that happened after the cap vanished
     * without a trace, and the profile lied about the shape of the program. Thinning keeps
     * that shape — it is exactly what a sampling profiler does when its samples are spaced
     * out, and the same caveat applies: what weighs very little can disappear. The factor
     * used is announced on standard output.
     */
    private static final long MAX_BYTES = 32L * 1024 * 1024;

    /** Available formats. The name is the one written on the command line. */
    public enum Format {
        PERF("perf", "perf"), CPUPROFILE("cpuprofile", "cpuprofile"),
        LCOV("lcov", "lcov"), VALUES("valeurs", "values");

        public final String option;

        /**
         * The name that is displayed. It differs from {@link #option} for {@code valeurs},
         * and that is the whole point of the switch: the French name stays accepted for
         * life because scripts write it, but it is no longer the one shown.
         */
        public final String affiche;

        Format(String option, String affiche) { this.option = option; this.affiche = affiche; }

        /** The English names, canonical. The French ones stay accepted, for life. */
        private static final java.util.Map<String, String> ALIAS =
                java.util.Map.of("values", "valeurs");

        public static Format of(String name) {
            String n = name.trim().toLowerCase(java.util.Locale.ROOT);
            n = ALIAS.getOrDefault(n, n);
            for (Format f : values()) {
                if (f.option.equalsIgnoreCase(n)) return f;
            }
            throw new IllegalArgumentException("unknown export format: " + name
                    + " (expected: perf, cpuprofile, lcov, values, or all)");
        }

        public static Set<Format> parse(String list) {
            String t = list.trim().toLowerCase(java.util.Locale.ROOT);
            if (list.isBlank() || t.equals("all") || t.equals("tout")) {
                return Set.of(values());
            }
            Set<Format> out = new java.util.LinkedHashSet<>();
            for (String piece : list.split("[,\\s]+")) {
                if (!piece.isBlank()) out.add(of(piece));
            }
            return out;
        }
    }

    private Exports() {}

    /**
     * Writes the requested formats into {@code <run>/exports/} and returns the files
     * produced. A format whose source is missing is simply skipped: a run launched without
     * value capture has nothing to export on that side.
     */
    public static List<Path> write(Path runDir, Set<Format> formats, int intervalMs,
                                   int valuesPerMethod) throws Exception {
        Path dir = runDir.resolve("exports");
        Path collapsed = runDir.resolve("async-profiler/profil.collapsed");
        Path jacoco = runDir.resolve("jacoco/html/jacoco.xml");
        List<Path> written = new ArrayList<>();

        if (formats.contains(Format.PERF) && Files.isRegularFile(collapsed)) {
            Files.createDirectories(dir);
            written.add(perf(collapsed, dir.resolve("profil.perf.txt"), intervalMs));
        }
        if (formats.contains(Format.CPUPROFILE) && Files.isRegularFile(collapsed)) {
            Files.createDirectories(dir);
            written.add(cpuprofile(collapsed, dir.resolve("profil.cpuprofile"), intervalMs));
        }
        if (formats.contains(Format.LCOV) && Files.isRegularFile(jacoco)) {
            Files.createDirectories(dir);
            written.add(lcov(Coverage.parse(jacoco), dir.resolve("couverture.lcov")));
        }
        if (formats.contains(Format.VALUES)) {
            Inspection inspection = Inspection.read(
                    runDir.resolve("arthas/watch-params.txt"),
                    runDir.resolve("arthas/trace-calltree.txt"),
                    valuesPerMethod);
            if (!inspection.values.isEmpty()) {
                Files.createDirectories(dir);
                written.add(values(inspection, dir.resolve("valeurs.json")));
            }
        }
        return written;
    }

    // ------------------------------------------------------------------ perf script

    /**
     * Translates the folded stacks into {@code perf script} output.
     *
     * <p>One block per sample, callee first and caller after — that is {@code perf}'s
     * order, the reverse of the folded file. The addresses are zero: they mean nothing for
     * just-in-time compiled code, and no reader uses them once the symbol is there.
     */
    private static Path perf(Path collapsed, Path out, int intervalMs) throws IOException {
        List<String> lines = Files.readAllLines(collapsed, StandardCharsets.UTF_8);
        Thinning thinning = Thinning.of(lines);
        long emitted = 0;
        double remains = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                Sample sample = Sample.of(line);
                if (sample == null) continue;
                // The remainder carries from one stack to the next: without it everything
                // weighing less than the factor would vanish, and the total would collapse.
                double du = sample.count * thinning.factor + remains;
                long howMany = (long) Math.floor(du);
                remains = du - howMany;
                for (long i = 0; i < howMany; i++, emitted++) {
                    double when = emitted * (intervalMs / 1000.0) / thinning.factor;
                    w.write(String.format(Locale.ROOT, "java 1/1 %.6f: 1 cpu-clock:%n", when));
                    for (int f = sample.frames.length - 1; f >= 0; f--) {
                        w.write("\t0 " + sample.frames[f] + " ([jit])\n");
                    }
                    w.write("\n");
                }
            }
        }
        if (thinning.thinned()) {
            System.out.println("   perf export thinned out: " + emitted + " samples out of "
                    + thinning.total + " (" + Math.round(thinning.factor * 100)
                    + "%) — this format rewrites the whole stack at every sample, and would\n"
                    + "      otherwise weigh " + thinning.bytes / (1024 * 1024) + " MB.");
            System.out.println("      The shape of the profile is kept; very small shares "
                    + "may vanish. The cpuprofile, itself, carries everything.");
        }
        return out;
    }

    /**
     * By how much to thin out, and what to say about it.
     *
     * <p>The factor is 1 as long as the file stays under the cap: the vast majority of
     * measurements are not concerned, and an export that changes nothing must announce
     * nothing.
     */
    private record Thinning(long total, long bytes, double factor) {

        /** What one sample line writes, header included, measured on the data. */
        static Thinning of(List<String> lines) {
            long total = 0, bytes = 0;
            for (String line : lines) {
                Sample sample = Sample.of(line);
                if (sample == null) continue;
                total += sample.count;
                long perSample = HEADER;
                for (String frame : sample.frames) {
                    perSample += frame.length() + DECOR;
                }
                bytes += sample.count * perSample;
            }
            return new Thinning(total, bytes,
                    bytes <= MAX_BYTES ? 1.0 : (double) MAX_BYTES / bytes);
        }

        boolean thinned() { return factor < 1.0; }

        /** "java 1/1 12.345678: 1 cpu-clock:" plus the blank line at the end. */
        private static final int HEADER = 34;
        /** The tab, the address, the space, "([jit])" and the newline. */
        private static final int DECOR = 13;
    }

    // ------------------------------------------------------------------ cpuprofile

    /**
     * Translates the folded stacks into Chrome DevTools' sampled profile.
     *
     * <p>Far more compact than {@code perf script}: the stacks are shared in a tree there,
     * and a sample is only a reference to a node. A one-minute profile fits in a few
     * hundred kilobytes where the text takes tens of megabytes. It therefore needs no
     * thinning: it carries every sample.
     */
    private static Path cpuprofile(Path collapsed, Path out, int intervalMs) throws IOException {
        Node root = new Node(1, "(root)");
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        List<Object> samples = new ArrayList<>();

        for (String line : Files.readAllLines(collapsed, StandardCharsets.UTF_8)) {
            Sample sample = Sample.of(line);
            if (sample == null) continue;
            Node node = root;
            for (String frame : sample.frames) node = node.child(frame, nodes);
            for (long i = 0; i < sample.count; i++) {
                samples.add(node.id);
            }
        }

        long step = Math.max(1L, intervalMs) * 1000L;                 // microsecondes
        List<Object> deltas = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) deltas.add(step);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("nodes", nodes.stream().map(Node::toMap).map(Object.class::cast).toList());
        profile.put("startTime", 0L);
        profile.put("endTime", step * samples.size());
        profile.put("samples", samples);
        profile.put("timeDeltas", deltas);
        Files.writeString(out, Json.write(profile), StandardCharsets.UTF_8);
        return out;
    }

    /** A node of the stack tree, in the shape the cpuprofile format expects. */
    private static final class Node {
        final int id;
        final String frame;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(int id, String frame) { this.id = id; this.frame = frame; }

        Node child(String name, List<Node> all) {
            Node existing = children.get(name);
            if (existing != null) return existing;
            Node created = new Node(all.size() + 1, name);
            children.put(name, created);
            all.add(created);
            return created;
        }

        Map<String, Object> toMap() {
            // async-profiler writes "package/Class.method": the class makes an acceptable
            // "url", and the reader finds their usual bearings.
            int cut = frame.lastIndexOf('.');
            String owner = cut > 0 ? frame.substring(0, cut).replace('/', '.') : "";
            String method = cut > 0 ? frame.substring(cut + 1) : frame;

            Map<String, Object> call = new LinkedHashMap<>();
            call.put("functionName", method);
            call.put("scriptId", "0");
            call.put("url", owner);
            call.put("lineNumber", -1);
            call.put("columnNumber", -1);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("callFrame", call);
            if (!children.isEmpty()) {
                m.put("children", children.values().stream()
                        .map(n -> (Object) n.id).toList());
            }
            return m;
        }
    }

    /** One folded stack line: the frames, from root to callee, and its weight. */
    private record Sample(String[] frames, long count) {

        static Sample of(String line) {
            int space = line.lastIndexOf(' ');
            if (space < 0) return null;
            long count;
            try {
                count = Long.parseLong(line.substring(space + 1).trim());
            } catch (NumberFormatException e) {
                return null;
            }
            String stack = line.substring(0, space);
            return stack.isBlank() ? null : new Sample(stack.split(";"), count);
        }
    }

    // ------------------------------------------------------------------ LCOV

    /**
     * Translates the JaCoCo coverage into LCOV.
     *
     * <p>An honest caveat: JaCoCo says <b>whether</b> a line ran, never <b>how many
     * times</b>. The LCOV counter is therefore 1 or 0 — that is all the information there
     * is, and presenting it otherwise would be inventing passes.
     */
    private static Path lcov(Coverage coverage, Path out) throws IOException {
        StringBuilder sb = new StringBuilder();
        Map<String, List<Object>> methodsBySource = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : coverage.methods.entrySet()) {
            Map<?, ?> byClass = (Map<?, ?>) e.getValue();
            methodsBySource
                    .computeIfAbsent(String.valueOf(byClass.get("source")), k -> new ArrayList<>())
                    .addAll((List<Object>) byClass.get("methods"));
        }

        for (Map.Entry<String, Map<String, Object>> file : coverage.lines.entrySet()) {
            sb.append("TN:\n");
            sb.append("SF:").append(file.getKey()).append('\n');

            List<Object> methods = methodsBySource.getOrDefault(file.getKey(), List.of());
            for (Object o : methods) {
                Map<?, ?> m = (Map<?, ?>) o;
                sb.append("FN:").append(m.get("line")).append(',').append(m.get("name")).append('\n');
            }
            long hitMethods = 0;
            for (Object o : methods) {
                Map<?, ?> m = (Map<?, ?>) o;
                int covered = ((Number) m.get("covered")).intValue();
                if (covered > 0) hitMethods++;
                sb.append("FNDA:").append(covered > 0 ? 1 : 0).append(',')
                  .append(m.get("name")).append('\n');
            }
            if (!methods.isEmpty()) {
                sb.append("FNF:").append(methods.size()).append('\n');
                sb.append("FNH:").append(hitMethods).append('\n');
            }

            long found = 0, hit = 0, branchesFound = 0, branchesHit = 0;
            for (Map.Entry<String, Object> line : file.getValue().entrySet()) {
                Map<?, ?> info = (Map<?, ?>) line.getValue();
                boolean executed = !"miss".equals(info.get("s"));
                sb.append("DA:").append(line.getKey()).append(',')
                  .append(executed ? 1 : 0).append('\n');
                found++;
                if (executed) hit++;

                if (info.get("b") instanceof List<?> b && b.size() == 2) {
                    int taken = ((Number) b.get(0)).intValue();
                    int total = ((Number) b.get(1)).intValue();
                    for (int i = 0; i < total; i++) {
                        sb.append("BRDA:").append(line.getKey()).append(",0,").append(i)
                          .append(',').append(i < taken ? "1" : "0").append('\n');
                    }
                    branchesFound += total;
                    branchesHit += taken;
                }
            }
            if (branchesFound > 0) {
                sb.append("BRF:").append(branchesFound).append('\n');
                sb.append("BRH:").append(branchesHit).append('\n');
            }
            sb.append("LF:").append(found).append('\n');
            sb.append("LH:").append(hit).append('\n');
            sb.append("end_of_record\n");
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        return out;
    }

    // ------------------------------------------------------------------ valeurs

    /** The captured values, as the page reads them, in a JSON a script can read back. */
    private static Path values(Inspection inspection, Path out) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("valeurs", inspection.values);
        doc.put("trace", inspection.trace);
        Files.writeString(out, Json.write(doc), StandardCharsets.UTF_8);
        return out;
    }
}
