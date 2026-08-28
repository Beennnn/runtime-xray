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
import java.util.regex.Pattern;

/**
 * The call tree, rebuilt from the folded stacks produced by the time measurement.
 *
 * <p>Each line of the file is one complete stack followed by a count:
 * {@code Main.main;Service.process;Compute.run 42}.
 */
public final class CallTree {

    /**
     * Frames of the value-inspection tool. When it works during the measurement it
     * intercepts every entry into and exit from the observed method: its frames then catch
     * most of the samples and swamp the profile.
     *
     * <p>They are <b>folded onto their caller</b> rather than shown: the samples stay
     * counted, but attributed to the application method that triggered them, which restores
     * the shape of the tree.
     */
    private static final Pattern INSTRUMENTATION = Pattern.compile(
            "arthas|SpyAPI|taobao|jacoco", Pattern.CASE_INSENSITIVE);

    /**
     * Frames of the JDK and of the virtual machine. They are folded too: nobody opens
     * {@code java.util.ArrayList.iterator} to understand their application. The time they
     * stand for is not lost — it is attributed to the application method that called them,
     * which is precisely the useful information.
     */
    private static final Pattern PLATFORM = Pattern.compile(
            "^(java|javax|jdk|sun|com/sun|kotlin|scala)/"        // standard library
            + "|^[A-Za-z_][A-Za-z0-9_]*::"                       // native VM frames
            + "|^(stub:|itable|vtable|call_stub)"                // trampolines
            + "|^(C1|C2|Interpreter|Compile|CompileBroker)"
            // A Java method is always written "package/Class.method", or at the very least
            // "Class.method": it therefore contains a '/' or a '.'. Anything with neither is
            // a native symbol of the virtual machine — Java_java_lang_…, MHN_resolve_Mem,
            // eventHandlerClassFileLoadHook, [unknown_Java] — that is, code no reader can
            // open or correct.
            + "|^[^/.]*$");
    /**
     * Stacks the profiler could not walk back.
     *
     * <p>When the sample lands in a fragment of code where the stack walk fails — a
     * compiler trampoline, a native transition — async-profiler writes a marker in square
     * brackets as the root: {@code [unknown_Java];lab/sample/Speeds.forMode}. The marker is
     * code that cannot be opened, so it is folded like the rest of the virtual machine —
     * but then the next frame rises to the root and shows up beside {@code main}, as if the
     * program had two entry points. It does not: this is a truncated stack. We count it, we
     * report it, and we do not graft it.
     *
     * <p>The pattern only targets failure markers. A root in square brackets can also be a
     * thread name ({@code [main tid=123]}): that one is a real root.
     */
    private static final Pattern BROKEN_ROOT = Pattern.compile(
            "^\\[(unknown|not_walkable|broken|deopt|failed)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_DEPTH = 40;

    public Map<String, Object> root;
    public String note;
    /** Caveat about the truncated stacks, or {@code null} when there were none. */
    public String stacksNote;

    public static CallTree parse(Path collapsed) throws IOException {
        return parse(collapsed, PackageFilter.NONE);
    }

    /**
     * @param hidden packages to fold on the same footing as the JDK — see
     *               {@link PackageFilter}
     */
    public static CallTree parse(Path collapsed, PackageFilter hidden) throws IOException {
        CallTree t = new CallTree();
        Node root = new Node("tout");
        long folded = 0;
        long brokenStacks = 0;

        if (Files.isRegularFile(collapsed)) {
            for (String line : Files.readAllLines(collapsed, StandardCharsets.UTF_8)) {
                int space = line.lastIndexOf(' ');
                if (space < 0) continue;
                long count;
                try {
                    count = Long.parseLong(line.substring(space + 1).trim());
                } catch (NumberFormatException e) {
                    continue;
                }
                String[] frames = line.substring(0, space).split(";");
                if (frames.length > 0 && BROKEN_ROOT.matcher(frames[0]).find()) {
                    // Counted in the total — the time was indeed spent — but not grafted.
                    root.total += count;
                    brokenStacks += count;
                    continue;
                }
                List<String> kept = new ArrayList<>(frames.length);
                boolean foldedInstrumentation = false;
                for (String f : frames) {
                    if (INSTRUMENTATION.matcher(f).find()) {
                        foldedInstrumentation = true;
                    } else if (!PLATFORM.matcher(f).find() && !hidden.hidden(f)) {
                        kept.add(f);
                    }
                }
                if (foldedInstrumentation) folded += count;
                root.total += count;
                Node node = root;
                for (int i = 0; i < kept.size() && i < MAX_DEPTH; i++) {
                    node = node.child(kept.get(i));
                    node.total += count;
                }
            }
        }

        t.root = root.toMap();
        if (folded > 0 && root.total > 0) {
            long share = Math.round(100.0 * folded / root.total);
            t.note = "The time samples were taken while value inspection was active: "
                    + share + "% of them fell inside its instrumentation. They were attributed "
                    + "to the application method that triggered them, which restores the shape "
                    + "of the tree. Caveat: the cost shown for the observed method remains "
                    + "overestimated.";
        }
        if (brokenStacks > 0 && root.total > 0) {
            double share = 100.0 * brokenStacks / root.total;
            t.stacksNote = brokenStacks + " sample" + (brokenStacks > 1 ? "s" : "")
                    + " out of " + root.total + " (" + format(share) + "%) have a stack the "
                    + "profiler could not trace back to its entry point. They are counted in "
                    + "the total, but do not appear in the tree: attaching them there would "
                    + "have shown a second entry point that does not exist.";
        }
        return t;
    }

    /** Two decimals at most, and no "0.00%" for a sample that does exist. */
    private static String format(double share) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", share);
        return s.equals("0.00") ? "< 0.01" : s;
    }

    private static final class Node {
        final String name;
        long total;
        final Map<String, Node> children = new LinkedHashMap<>();

        Node(String name) { this.name = name; }

        Node child(String n) { return children.computeIfAbsent(n, Node::new); }

        Map<String, Object> toMap() {
            List<Object> kids = new ArrayList<>(children.values()).stream()
                    .sorted(Comparator.comparingLong((Node n) -> n.total).reversed())
                    .map(Node::toMap)
                    .map(Object.class::cast)
                    .toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("total", total);
            m.put("children", kids);
            return m;
        }
    }
}
