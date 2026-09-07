package lab.xray.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * The contract between what a run <b>records</b> and what the tool knows how to <b>read
 * back</b>.
 *
 * <p>An acceptance campaign is expensive: launch the application, play ten scenarios, wait.
 * What it produces — JaCoCo's {@code .exec} files, async-profiler's stacks, Arthas's
 * readings, the launch context — has no reason to age along with the tool that reads them
 * back. Replaying a campaign because the page changed its layout would be absurd.
 *
 * <p>Hence this number. The capture carries its own; the tool declares the <b>minimum
 * version</b> it can read. As long as the capture is above it, updating the tool merely
 * reassembles the view — {@code --report-only} — without running anything again. Below it,
 * the tool says precisely what is missing, and whether a new measurement is really needed.
 *
 * <h2>What the number commits to</h2>
 *
 * <p>It covers the <b>shape of what is written to disk</b>, not what the page makes of it.
 * Concretely: the paths ({@code jacoco/jacoco.exec}, {@code jacoco/html/jacoco.xml},
 * {@code async-profiler/profil.collapsed}, {@code arthas/watch-params.txt},
 * {@code arthas/trace-calltree.txt}), and the fields of {@code run-context.json}.
 *
 * <ul>
 *   <li><b>Major</b> — a field or a file <i>disappears</i>, or changes meaning. Earlier
 *       captures no longer read back; they must be measured again.</li>
 *   <li><b>Minor</b> — a field or a file <i>is added</i>. Earlier captures still read back,
 *       with less in them: the page says what is missing rather than pretending.</li>
 * </ul>
 *
 * <p>A capture without a number is taken to be {@link #ORIGIN}: that is the shape from
 * before this contract existed, and it stays readable. Yesterday's silence must not cost a
 * campaign.
 */
public final class Capture {

    /** The field where the version lives, in {@code run-context.json}. */
    public static final String FIELD = "formatCapture";

    /** The shape from before the contract: anything that announces nothing falls under it. */
    public static final String ORIGIN = "1.0";

    /** What a run started today writes into its context. */
    public static final String CURRENT = "1.2";

    /**
     * The oldest one this tool can read.
     *
     * <p>It only goes up when an old capture becomes genuinely unreadable — never for the
     * convenience of the code. Raising it forces every user to measure again.
     */
    public static final String MINIMUM = "1.0";

    /**
     * Where a run's folded stacks are, whichever tool produced them.
     *
     * <p>Two tools can measure the time and they do not write to the same place —
     * async-profiler folds the stacks itself, Flight Recorder records and the converter
     * folds afterwards. Everything downstream reads <b>one</b> file, so the choice is made
     * here rather than at each of the four readers: the tree, the two exports, the raw
     * flame graphs and the page's links would otherwise each have their own idea of where
     * the profile is, and a run measured the other way would look like a run without one.
     *
     * <p>The order is not arbitrary. A directory named after a tool that did not produce
     * the file would be a quiet lie, so each writes under its own name; and an older
     * capture, which knows only {@code async-profiler/}, still resolves — that is what
     * makes this a minor version and not a major one.
     */
    public static Path profile(Path runDir) {
        Path fromJfr = runDir.resolve(JFR_DIR + "/profil.collapsed");
        return Files.isRegularFile(fromJfr)
                ? fromJfr : runDir.resolve(ASYNC_DIR + "/profil.collapsed");
    }

    /** The directory of that file, relative to the run — what the page's links need. */
    public static String profileDir(Path runDir) {
        return Files.isRegularFile(runDir.resolve(JFR_DIR + "/profil.collapsed"))
                ? JFR_DIR : ASYNC_DIR;
    }

    public static final String ASYNC_DIR = "async-profiler";
    public static final String JFR_DIR = "jfr";

    private Capture() {}

    /** The version a capture announces, or {@link #ORIGIN} when it announces nothing. */
    public static String versionOf(Map<String, Object> context) {
        if (context == null) return ORIGIN;
        Object v = context.get(FIELD);
        return v == null || String.valueOf(v).isBlank() ? ORIGIN : String.valueOf(v);
    }

    /** True when this tool can read that capture without anything being run again. */
    public static boolean readable(String version) {
        return compare(version, MINIMUM) >= 0;
    }

    /**
     * True when the capture is newer than the tool.
     *
     * <p>This is not an error: a capture made by a more advanced version carries at worst
     * some fields we ignore. We say so and carry on — refusing to read would lose a
     * campaign over a difference that does not get in the way.
     */
    public static boolean newerThanTheTool(String version) {
        return compare(version, CURRENT) > 0;
    }

    /** What is shown when a capture does not read back — and what to do about it. */
    public static String whyUnreadable(String version) {
        return "capture in format " + version + ", whereas this tool only reads from "
                + MINIMUM + " on. This run must be measured again; the others, not.";
    }

    /** Numeric comparison, segment by segment: "1.10" comes after "1.9". */
    static int compare(String a, String b) {
        String[] ga = a.split("\\."), gb = b.split("\\.");
        for (int i = 0; i < Math.max(ga.length, gb.length); i++) {
            int va = i < ga.length ? toInt(ga[i]) : 0;
            int vb = i < gb.length ? toInt(gb[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
