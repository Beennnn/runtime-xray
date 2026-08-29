package lab.xray;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * What happens while you wait, said in one line that rewrites itself.
 *
 * <p>An analysis lasts as long as the observed application does — ten seconds or twenty
 * minutes — and nothing on screen used to say which: between "Waiting for the run to
 * finish" and the report, the tool fell silent. A silent wait reads as a hang, and the
 * only way to find out was to kill the process, which loses the capture.
 *
 * <p>Each square stands for one interval of time, and its density for the activity of the
 * observed JVM during that interval. The band scrolls: one glance says whether the program
 * is still working, waiting for input, or asleep on a socket.
 *
 * <p><b>What this display does not do</b>, and that is what makes it acceptable: it
 * measures nothing. The load comes from the processor time the system accounts for anyway
 * on the observed process ({@link ProcessHandle.Info#totalCpuDuration()}), and the growth
 * of the output from the size of a file already written. No option is added to the
 * observed JVM, no agent is called on, nothing extra is sampled — so nothing the report
 * shows changes according to whether this band is displayed or not.
 *
 * <p>It only shows in front of a terminal. In a pipe, an integration log or a redirection,
 * a line that rewrites itself every second gives thousands of unreadable lines: there the
 * object is silent, and does not even pay for reading the counters.
 *
 * <p>The characters chosen — {@code · ░ ▒ ▓ █} — exist in CP850 as well as in UTF-8: a
 * badly configured Windows terminal still shows them (see CLAUDE.md, "Terminal Windows").
 * Colour only doubles the density, it never carries it alone; {@code NO_COLOR} removes it.
 */
public final class Progress {

    /**
     * From the quietest to the busiest. Density carries the information, not colour.
     *
     * <p>The idle dot also serves as a separator elsewhere in the tool: the line therefore
     * uses a different one, without which counting the squares — which a test does — would
     * find one too many.
     */
    static final char[] TIERS = {'·', '░', '▒', '▓', '█'};

    private static final String[] TINTS = {
            "\u001b[90m", "\u001b[36m", "\u001b[32m", "\u001b[33m", "\u001b[31m"};
    private static final String RESET = "\u001b[0m";

    /** How many intervals stay visible. Beyond that, the band scrolls. */
    static final int WIDTH = 28;

    private final Appendable out;
    private final boolean active;
    private final boolean colour;

    private final Deque<Integer> band = new ArrayDeque<>();
    private Duration previousElapsed = Duration.ZERO;
    private Duration previousCpu = Duration.ZERO;
    private long previousBytes = -1;
    private boolean cpuKnown;
    private int previousLength;

    Progress(Appendable out, boolean active, boolean colour) {
        this.out = out;
        this.active = active;
        this.colour = colour;
    }

    /**
     * The display in front of a terminal, or silence.
     *
     * <p>{@code System.console()} answers for the real terminal, not for the stream:
     * {@code Main} replaces {@code System.out} with a UTF-8 stream, which does not change
     * the answer.
     */
    public static Progress of(Appendable out) {
        boolean terminal = System.console() != null || forced();
        return new Progress(out, terminal, terminal && System.getenv("NO_COLOR") == null);
    }

    /**
     * A way to ask for the band where the JVM sees no terminal.
     *
     * <p>A shell that starts another shell, which starts the tool, often puts a pipe in
     * the middle: {@code System.console()} then answers {@code null} while an operator is
     * indeed watching a screen at the far end. The default stays silence — and it is the
     * right default, one line per second in an integration log tells nobody anything — but
     * whoever knows they are watching can say so.
     *
     * <p>{@code RUNTIME_XRAY_PROGRESSION=0} does the opposite and imposes silence.
     */
    private static boolean forced() {
        return requested(System.getenv("RUNTIME_XRAY_PROGRESSION"));
    }

    static boolean requested(String value) {
        return value != null && !value.isBlank()
                && !value.equals("0") && !value.equalsIgnoreCase("false");
    }

    /** An object that never writes anything — for callers that have no terminal. */
    public static Progress silent() {
        return new Progress(new StringBuilder(), false, false);
    }

    /** True when something is displayed: no point reading counters otherwise. */
    public boolean active() {
        return active;
    }

    /**
     * One more interval.
     *
     * <p>Returns the load of the interval, in busy cores — <b>even without a terminal</b>.
     * The calculation lives here and nowhere else: {@link Follow} writes the same figure
     * into {@code progression.jsonl}, and two separate calculations would end up
     * diverging, giving a file that contradicts the band without anything saying so.
     *
     * @param elapsed   since the observed application was launched
     * @param cpuTotal  processor time consumed by it and its descendants, cumulative
     * @param bytes     size of its standard output as written to disk
     */
    public double tick(Duration elapsed, Duration cpuTotal, long bytes) {
        double cores = load(elapsed, cpuTotal, bytes);
        previousElapsed = elapsed;
        previousCpu = cpuTotal;
        previousBytes = bytes;
        if (active) {
            band.addLast(tier(cores));
            while (band.size() > WIDTH) band.removeFirst();
            write(line(elapsed, cpuTotal, bytes));
        }
        return cores;
    }

    /** Hands the line back to what follows: the band must not stay half written. */
    public void end() {
        if (!active || previousLength == 0) return;
        write("");
        try {
            out.append(System.lineSeparator());
        } catch (IOException ignored) {
            // A comfort display does not make a successful analysis fail.
        }
    }

    /**
     * How many cores the observed JVM occupied on average during the interval.
     *
     * <p>The count is in <b>cores</b>, not in percent of the machine: on a thirty-two-core
     * server, an application that saturates one is working flat out, and bringing it back
     * to 3 % would make it look asleep. The question asked in front of a wait is "is this
     * moving?", not "is the machine full?".
     *
     * <p>When the system does not publish the processor time — which happens depending on
     * the platform and the permissions — the application's output stands in as the
     * witness: it does not say <i>how much</i> the program is working, but it says that it
     * is still alive, which is precisely the question.
     *
     * <p><b>The first reading of the processor time fixes the origin, it is not a rate.</b>
     * A rate needs two readings, and the first one carries everything consumed before the
     * band existed — the JVM starting, the classes loading, the JIT compiler's own threads,
     * all of it on several cores at once. Divided by one interval, that gave <i>26.71 busy
     * cores on a four-core machine</i>: an impossible figure, which set the scale of the
     * {@code --follow} curve and flattened every honest reading after it. Until the second
     * reading, the output stands witness, exactly as on a platform that publishes nothing.
     */
    private double load(Duration elapsed, Duration cpuTotal, long bytes) {
        boolean origin = !cpuKnown && !cpuTotal.isZero();
        if (!cpuTotal.isZero()) cpuKnown = true;
        long interval = elapsed.toMillis() - previousElapsed.toMillis();
        if (interval <= 0) return 0;
        if (cpuKnown && !origin) {
            long cpu = Math.max(0, cpuTotal.toMillis() - previousCpu.toMillis());
            return cpu / (double) interval;
        }
        if (previousBytes < 0) return 0;
        return bytes > previousBytes ? 0.3 : 0;
    }

    /**
     * The square that goes with a load, in busy cores. The thresholds are visual, not
     * normative: they separate "nothing", "a little", "one core", "several".
     */
    static int tier(double cores) {
        if (cores <= 0.01) return 0;
        if (cores < 0.15) return 1;
        if (cores < 0.50) return 2;
        if (cores < 1.50) return 3;
        return 4;
    }

    String line(Duration elapsed, Duration cpuTotal, long bytes) {
        StringBuilder sb = new StringBuilder("   ").append(clock(elapsed)).append("  ");
        for (int level : band) {
            if (colour) sb.append(TINTS[level]);
            sb.append(TIERS[level]);
        }
        if (colour) sb.append(RESET);
        for (int i = band.size(); i < WIDTH; i++) sb.append(' ');
        sb.append("  ");
        if (cpuKnown) {
            sb.append("cpu ")
              .append(String.format(Locale.ROOT, "%.1f", cpuTotal.toMillis() / 1000.0))
              .append(" s");
        } else {
            sb.append("running");
        }
        return sb.append(" | output ").append(size(bytes)).toString();
    }

    static String clock(Duration elapsed) {
        long s = Math.max(0, elapsed.toSeconds());
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }

    /**
     * Rewrites the line in place.
     *
     * <p>It is padded with spaces up to the previous length: a line shorter than the one
     * it replaces would leave that one's tail on screen — "12.4 s" erasing "112.4 s" would
     * give "12.4 ss".
     */
    private void write(String line) {
        int visible = colour ? lengthWithoutCodes(line) : line.length();
        StringBuilder sb = new StringBuilder("\r").append(line);
        for (int i = visible; i < previousLength; i++) sb.append(' ');
        previousLength = visible;
        try {
            out.append(sb);
            if (out instanceof java.io.Flushable f) f.flush();
        } catch (IOException ignored) {
            // Likewise: losing the band has no consequence on the capture.
        }
    }

    private static int lengthWithoutCodes(String line) {
        return line.replaceAll("\u001b\\[[0-9;]*m", "").length();
    }
}
