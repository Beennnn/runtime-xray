package lab.xray.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The whole campaign on one page, in SVG.
 *
 * <p>The interactive view answers the questions put to it; it does not travel. Yet what is
 * shown in a meeting, pasted into a slide, attached to a write-up, is an image — and until
 * now it had to be built by hand out of screenshots, so redone for every campaign, so not
 * done at all.
 *
 * <p>This file is generated at every assembly, beside the page. It contains only
 * <b>measured</b> figures: none is rounded to the advantage of whoever presents it, and the
 * never-executed classes appear there on the same footing as the coverage. A summary
 * diagram showing only what goes well would lose its credibility to the first person who
 * checks.
 *
 * <p><b>The colours are written out literally</b>, not inherited from a theme: the file is
 * made to be dropped into a presentation, and PowerPoint does not interpret
 * {@code currentColor}. For the same reason there is no script and no external font.
 */
public final class Diagram {

    private static final String INK = "#14181e", MUTED = "#5a6472", RULE = "#c9d1d9";
    private static final String GREEN = "#1a7f37", RED = "#cf222e", AMBER = "#9a6700";
    private static final String BLUE = "#0969da", BAR_BACKGROUND = "#e4e9ef";
    private static final String FONT = "Segoe UI, system-ui, sans-serif";

    /** Beyond that the page becomes a wall: we show the most telling and say the rest. */
    private static final int MAX_PACKAGES = 9, MAX_METHODS = 6, MAX_DEAD = 16;

    private Diagram() {}

    /**
     * Writes {@code synthese.svg} beside the page.
     *
     * @param runs the runs as the view receives them
     * @return the file written, or {@code null} when there was nothing to draw
     */
    public static Path write(Path commonDir, List<Object> runs) throws IOException {
        if (runs == null || runs.isEmpty()) return null;
        Map<String, long[]> pkgs = coveragePerPackage(runs);
        if (pkgs.isEmpty()) return null;

        long cov = 0, put = 0;
        for (long[] v : pkgs.values()) { cov += v[0]; put += v[1]; }
        Path out = commonDir.resolve("synthese.svg");
        Files.writeString(out, svg(commonDir, runs, pkgs, cov, put), StandardCharsets.UTF_8);
        return out;
    }

    /**
     * Where the coverage figure shown in large type comes from, and what it is worth.
     *
     * <p>Bringing runs together is not adding up their percentages. The only exact
     * cumulative figure is the one JaCoCo computes by merging the measurements; that is the
     * one shown as soon as it exists, with its source stated.
     *
     * <p>Failing that, we keep per class the <b>best coverage reached</b> — an
     * approximation from above, honest as long as it is said. A diagram put on a screen
     * cannot afford a figure nobody knows the origin of: it would be contradicted by the
     * first person to open the JaCoCo report.
     */
    private record Figure(double pct, long covered, long total, String source) {}

    private static Figure coverage(Path commonDir, long cov, long put) {
        Path merge = commonDir.resolve("jacoco-fusion/html/jacoco.xml");
        if (Files.isRegularFile(merge)) {
            try {
                Coverage c = Coverage.parse(merge, PackageFilter.NONE);
                long covered = 0, missing = 0;
                for (Object classes : c.packages.values()) {
                    if (!(classes instanceof Iterable<?> list)) continue;
                    for (Object o : list) {
                        if (!(o instanceof Map<?, ?> cls)) continue;
                        covered += number(cls.get("covered"));
                        missing += number(cls.get("missed"));
                    }
                }
                if (covered + missing > 0) {
                    return new Figure(100.0 * covered / (covered + missing), covered,
                            covered + missing, "JaCoCo merge of " );
                }
            } catch (Exception e) {
                // The merge is a bonus: its being unreadable must not cost the diagram.
            }
        }
        long total = cov + put;
        return new Figure(total == 0 ? 0 : 100.0 * cov / total, cov, total, null);
    }

    private static String svg(Path commonDir, List<Object> runs, Map<String, long[]> pkgs,
                             long cov, long put) {
        StringBuilder s = new StringBuilder();
        s.append("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1280 720" width="1280" \
                height="720" role="img" aria-label="Summary of the campaign: cumulative \
                coverage, coverage per package, time spent per method and never-executed \
                classes.">
                <rect width="1280" height="720" fill="#ffffff"/>
                <g font-family="%s">
                """.formatted(FONT));

        Map<String, Object> ctx = context(runs);
        Figure coverage = coverage(commonDir, cov, put);
        List<String> never = neverExecutedClasses(runs);
        long classes = classCount(runs), dead = never.size();

        // ── header ─────────────────────────────────────────────────────────────────
        text(s, 40, 48, 22, 700, INK, "What the campaign measured");
        text(s, 40, 72, 13, 400, MUTED, shorten(String.valueOf(ctx.getOrDefault("commande", "")), 95));
        text(s, 40, 92, 12.5, 400, MUTED,
                join(" · ", String.valueOf(ctx.getOrDefault("machine", "")),
                        String.valueOf(ctx.getOrDefault("debut", "")),
                        runs.size() + (runs.size() > 1 ? " runs" : " run")));

        // ── four figures ───────────────────────────────────────────────────────────
        tile(s, 40, 118, "CUMULATIVE COVERAGE", rounded(coverage.pct()) + "%",
                coverage.source() != null
                        ? coverage.covered() + " of " + coverage.total() + " — JaCoCo merge"
                        : coverage.covered() + " of " + coverage.total()
                          + " — best per class",
                GREEN);
        tile(s, 350, 118, "CLASSES MEASURED", String.valueOf(classes),
                dead + " never ran", BLUE);
        tile(s, 660, 118, "RUNS GATHERED", String.valueOf(runs.size()),
                "each brings its share", AMBER);
        tile(s, 970, 118, "TIME OBSERVED", totalDuration(runs),
                "wall-clock time of the scenarios", MUTED);

        // ── per package ────────────────────────────────────────────────────────────
        title(s, 40, 268, "WHERE THE CODE IS COVERED, PACKAGE BY PACKAGE", 600);
        List<Map.Entry<String, long[]>> ordered = new ArrayList<>(pkgs.entrySet());
        // The least covered first: that is where something gets decided.
        ordered.sort(Comparator.comparingDouble(e -> share(e.getValue())));
        int y = 296;
        for (int i = 0; i < Math.min(MAX_PACKAGES, ordered.size()); i++) {
            Map.Entry<String, long[]> e = ordered.get(i);
            bar(s, 40, y, 600, e.getKey().replace('/', '.'), share(e.getValue()),
                  e.getValue()[2] + (e.getValue()[2] > 1 ? " classes" : " classe"), 236);
            y += 30;
        }
        if (ordered.size() > MAX_PACKAGES) {
            text(s, 40, y + 6, 12, 400, MUTED,
                    "and " + (ordered.size() - MAX_PACKAGES) + " more packages — the page shows them all");
        }

        // ── where the time went ────────────────────────────────────────────────────
        title(s, 700, 268, "WHERE THE TIME WENT", 540);
        List<Map.Entry<String, Long>> chaud = hottestMethods(runs);
        if (chaud.isEmpty()) {
            text(s, 700, 300, 12.5, 400, MUTED, "No time measurement in this campaign.");
        } else {
            long max = chaud.get(0).getValue();
            int yc = 296;
            for (Map.Entry<String, Long> e : chaud) {
                // A qualified method name is long: we give it the room, and cut on the
                // LEFT — it is the end of the name that identifies, not the package.
                bar(s, 700, yc, 540, shorten(e.getKey().replace('/', '.'), 40),
                      max == 0 ? 0 : 100.0 * e.getValue() / max, "", 300);
                yc += 30;
            }
            text(s, 700, yc + 6, 12, 400, MUTED,
                    "Share of the measured time, calls included — relative to the costliest.");
        }

        // ── never executed ─────────────────────────────────────────────────────────
        // Per run: what each scenario brings. That is the question asked in front of a
        // campaign — "was the second scenario of any use?".
        title(s, 700, 500, "WHAT EACH RUN COVERED", 540);
        // All on the SAME denominator as the cumulative figure, without which a run would
        // show a percentage above the total — each on its own perimeter, which reads as a
        // contradiction when it is only a change of base.
        long base = coverage.total();
        int ye = 528;
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run)) continue;
            long[] v = coverageOf(run);
            double p = base == 0 ? 0 : 100.0 * v[0] / base;
            bar(s, 700, ye, 540, shorten(String.valueOf(run.get("nom")), 34), p, duration(run), 300);
            ye += 30;
            if (ye > 604) break;
        }
        text(s, 700, ye + 4, 11.5, 400, MUTED,
                "Share of the same total as the cumulative figure — what each scenario alone would give.");

        title(s, 40, 620, "WHAT NEVER RAN", 1200);
        if (never.isEmpty()) {
            text(s, 40, 652, 12.5, 400, GREEN,
                    "None: every measured class was reached.");
        } else {
            int x = 40, yj = 650;
            for (int i = 0; i < Math.min(MAX_DEAD, never.size()); i++) {
                String name = never.get(i);
                int w = 9 * name.length() + 18;
                if (x + w > 1240) { x = 40; yj += 28; }
                s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"22\" rx=\"4\" fill=\"#fff5f5\" stroke=\"%s\" stroke-width=\"1\"/>\n"
                        .formatted(x, yj - 16, w, RED));
                text(s, x + 9, yj, 11.5, 500, RED, name);
                x += w + 8;
            }
            if (never.size() > MAX_DEAD) {
                text(s, x + 4, yj, 12, 400, MUTED,
                        "and " + (never.size() - MAX_DEAD) + " more.");
            }
        }

        text(s, 40, 706, 11.5, 400, MUTED,
                "Runtime X-Ray — measured figures, none estimated. The full report comes with this diagram.");
        s.append("</g></svg>\n");
        return s.toString();
    }

    // ------------------------------------------------------------------ dessin

    private static void text(StringBuilder s, double x, double y, double size, int bold,
                              String colour, String content) {
        s.append("<text x=\"%s\" y=\"%s\" font-size=\"%s\" font-weight=\"%d\" fill=\"%s\">%s</text>\n"
                .formatted(number(x), number(y), number(size), bold, colour, escape(content)));
    }

    private static void title(StringBuilder s, int x, int y, String t, int width) {
        text(s, x, y, 12, 700, MUTED, t);
        s.append("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\"/>\n"
                .formatted(x, y + 10, x + width, y + 10, RULE));
    }

    private static void tile(StringBuilder s, int x, int y, String tag, String value,
                              String detail, String colour) {
        s.append("<rect x=\"%d\" y=\"%d\" width=\"270\" height=\"118\" rx=\"8\" fill=\"#f6f8fa\"/>\n"
                .formatted(x, y));
        s.append("<rect x=\"%d\" y=\"%d\" width=\"4\" height=\"118\" rx=\"2\" fill=\"%s\"/>\n"
                .formatted(x, y, colour));
        text(s, x + 20, y + 26, 11, 700, MUTED, tag);
        text(s, x + 20, y + 70, 32, 700, colour, value);
        text(s, x + 20, y + 96, 12, 400, MUTED, detail);
    }

    /** A bar, its label and its percentage — the shape that reads from a distance. */
    private static void bar(StringBuilder s, int x, int y, int width, String label,
                              double pct, String right, int placeLabel) {
        int barX = x + placeLabel, barL = width - placeLabel - 62;
        text(s, x, y + 4, 12.5, 400, INK, label);
        s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"10\" rx=\"5\" fill=\"%s\"/>\n"
                .formatted(barX, y - 5, barL, BAR_BACKGROUND));
        int full = (int) Math.round(barL * Math.max(0, Math.min(100, pct)) / 100.0);
        String colour = pct >= 70 ? GREEN : pct >= 35 ? AMBER : RED;
        if (full > 0) {
            s.append("<rect x=\"%d\" y=\"%d\" width=\"%d\" height=\"10\" rx=\"5\" fill=\"%s\"/>\n"
                    .formatted(barX, y - 5, full, colour));
        }
        text(s, barX + barL + 10, y + 4, 12, 600, colour, rounded(pct) + " %");
        if (!right.isEmpty()) text(s, x + placeLabel - 84, y + 4, 11.5, 400, MUTED, right);
    }

    // ------------------------------------------------------------------ mesures

    /** Per package: instructions covered, missed, and the number of classes. */
    static Map<String, long[]> coveragePerPackage(List<Object> runs) {
        Map<String, long[]> out = new TreeMap<>();
        Map<String, Map<String, long[]>> byClass = new TreeMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Map.Entry<?, ?> p : pkgs.entrySet()) {
                if (!(p.getValue() instanceof Iterable<?> classes)) continue;
                for (Object c : classes) {
                    if (!(c instanceof Map<?, ?> cls)) continue;
                    // The best coverage reached by any run is authoritative: it is the
                    // union that describes the campaign, not the last run launched.
                    long[] v = byClass.computeIfAbsent(String.valueOf(p.getKey()),
                            k -> new TreeMap<>()).computeIfAbsent(String.valueOf(cls.get("name")),
                            k -> new long[2]);
                    long covered = number(cls.get("covered")), missing = number(cls.get("missed"));
                    if (covered > v[0]) { v[0] = covered; v[1] = missing; }
                }
            }
        }
        for (Map.Entry<String, Map<String, long[]>> p : byClass.entrySet()) {
            long[] total = new long[3];
            for (long[] v : p.getValue().values()) { total[0] += v[0]; total[1] += v[1]; total[2]++; }
            out.put(p.getKey(), total);
        }
        return out;
    }

    static List<String> neverExecutedClasses(List<Object> runs) {
        Map<String, Long> best = new TreeMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Object classes : pkgs.values()) {
                if (!(classes instanceof Iterable<?> list)) continue;
                for (Object c : list) {
                    if (!(c instanceof Map<?, ?> cls)) continue;
                    String name = String.valueOf(cls.get("simple"));
                    best.merge(name, number(cls.get("covered")), Math::max);
                }
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : best.entrySet()) if (e.getValue() == 0) out.add(e.getKey());
        return out;
    }

    static long classCount(List<Object> runs) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("packages") instanceof Map<?, ?> pkgs)) continue;
            for (Object classes : pkgs.values()) {
                if (!(classes instanceof Iterable<?> list)) continue;
                for (Object c : list) if (c instanceof Map<?, ?> cls) names.add(String.valueOf(cls.get("name")));
            }
        }
        return names.size();
    }

    /** The methods beneath which most time was spent, all runs taken together. */
    static List<Map.Entry<String, Long>> hottestMethods(List<Object> runs) {
        Map<String, Long> weight = new LinkedHashMap<>();
        for (Object r : runs) {
            if (!(r instanceof Map<?, ?> run) || !(run.get("calltree") instanceof Map<?, ?> tree)) continue;
            descend(tree, weight);
        }
        List<Map.Entry<String, Long>> ordered = new ArrayList<>(weight.entrySet());
        ordered.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return ordered.subList(0, Math.min(MAX_METHODS, ordered.size()));
    }

    private static void descend(Map<?, ?> node, Map<String, Long> weight) {
        if (!(node.get("children") instanceof Iterable<?> children)) return;
        for (Object e : children) {
            if (!(e instanceof Map<?, ?> child)) continue;
            Object name = child.get("name");
            if (name != null) weight.merge(String.valueOf(name), number(child.get("total")), Long::sum);
            descend(child, weight);
        }
    }

    private static Map<String, Object> context(List<Object> runs) {
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("context") instanceof Map<?, ?> c) {
                Map<String, Object> out = new LinkedHashMap<>();
                c.forEach((k, v) -> out.put(String.valueOf(k), v));
                return out;
            }
        }
        return Map.of();
    }

    private static String totalDuration(List<Object> runs) {
        double total = 0;
        for (Object r : runs) {
            if (r instanceof Map<?, ?> run && run.get("context") instanceof Map<?, ?> c
                    && c.get("dureeSecondes") instanceof Number n) total += n.doubleValue();
        }
        if (total <= 0) return "—";
        return total < 90 ? rounded(total) + " s" : Math.round(total / 60) + " min";
    }

    // ------------------------------------------------------------------ menues

    /** What one run covered, on its own. */
    static long[] coverageOf(Map<?, ?> run) {
        long[] out = new long[2];
        if (!(run.get("packages") instanceof Map<?, ?> pkgs)) return out;
        for (Object classes : pkgs.values()) {
            if (!(classes instanceof Iterable<?> list)) continue;
            for (Object c : list) {
                if (!(c instanceof Map<?, ?> cls)) continue;
                out[0] += number(cls.get("covered"));
                out[1] += number(cls.get("missed"));
            }
        }
        return out;
    }

    private static String duration(Map<?, ?> run) {
        if (run.get("context") instanceof Map<?, ?> c && c.get("dureeSecondes") instanceof Number n) {
            return rounded(n.doubleValue()) + " s";
        }
        return "";
    }

    private static double share(long[] v) {
        return v[0] + v[1] == 0 ? 0 : 100.0 * v[0] / (v[0] + v[1]);
    }

    private static long number(Object o) { return o instanceof Number n ? n.longValue() : 0; }

    private static String rounded(double d) {
        return String.valueOf(Math.round(d * 10) / 10.0).replace(".0", "");
    }

    private static String number(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : "…" + s.substring(s.length() - max + 1);
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isBlank() || "null".equals(p)) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(p);
        }
        return sb.toString();
    }

    /** A class name can carry anything: the SVG must not end up broken by it. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
