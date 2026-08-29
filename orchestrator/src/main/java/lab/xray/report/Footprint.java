package lab.xray.report;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a report weighs <b>in number of files</b>.
 *
 * <p>That is the figure which decides how long a campaign takes on a machine where every
 * file open crosses a stack of security filters — antivirus, EDR, DLP. The cost model was
 * measured, not assumed: injecting a fixed latency on every {@code open} under the output
 * directory gives a total that tracks <b>one open per file written</b>. So the time paid is
 * the file count multiplied by the filter's latency, and nothing else — not the volume, not
 * the number of runs by itself.
 *
 * <p>The count therefore had to stop being invisible. Before this, the only way to know
 * what a campaign had cost was to go and count by hand, after the fact, on the machine
 * where it hurt — which is to say: never. A setting that reduces the count existed and was
 * documented, and nothing in the tool's output ever suggested reaching for it.
 *
 * <p><b>Counting is far cheaper than reading, and it is not free.</b> A walk opens each
 * <i>directory</i> and reads the entries' attributes; it never opens a file. The cost is
 * therefore proportional to the tree's directories and not to its files — measured at
 * 0.63 s for one walk of a run's output under a 20 ms filter, against some four seconds for
 * a single pass over the files it holds. That ratio is what makes the figure affordable at
 * every assembly; it is also why the count is taken once at the end and summed from the
 * runs in the diagnostic, rather than walked twice for the same number.
 */
public final class Footprint {

    /**
     * Above this many files, the tool says so on its own.
     *
     * <p>Ten thousand is a convention, not a measurement: at the latency of a filtered
     * machine — of the order of a millisecond per open — it is where a single walk of the
     * output starts to be counted in tens of seconds, hence where the settings begin to be
     * worth knowing about. Set lower, the message would fire on runs nobody waits for; set
     * higher, it would arrive after the campaign that hurt.
     */
    public static final long NOTABLE = 10_000;

    /** Where the advice sends the reader, and what it names. */
    public static final String EXCLUDE = "runs";

    public final Path out;
    public final long files;

    private Footprint(Path out, long files) {
        this.out = out;
        this.files = files;
    }

    public static Footprint of(Path out) throws IOException {
        return new Footprint(out, count(out));
    }

    /** Regular files under {@code dir}, or 0 when it does not exist. */
    public static long count(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Files.isRegularFile(dir) ? 1 : 0;
        }
        long[] n = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) n[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                // A file that cannot be read still exists, and still gets scanned.
                n[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return n[0];
    }

    public boolean notable() {
        return files >= NOTABLE;
    }

    /**
     * What the console says once the report is assembled — empty below the threshold.
     *
     * <p>It says the figure, then the one measure that removes the cost instead of reducing
     * it, then where to read the rest. It does <b>not</b> list the settings: they restrict
     * what the report holds, and a line printed at the end of a campaign is the wrong place
     * to weigh that up. The help section is written for it.
     */
    public List<String> console() {
        return message(out, files);
    }

    /**
     * Built apart from any directory so a test can read it.
     *
     * <p>Checking the message otherwise would mean creating ten thousand files — that is,
     * paying in the test exactly the cost the message exists to report.
     */
    static List<String> message(Path out, long files) {
        List<String> lines = new ArrayList<>();
        if (files < NOTABLE) return lines;
        lines.add("   📁 " + grouped(files) + " files written under "
                + out.toAbsolutePath().normalize());
        lines.add("      On a machine where a security filter inspects every file open, that");
        lines.add("      count is what a campaign pays — at every walk, and when archiving it.");
        lines.add("      " + exclusionAdvice(out));
        lines.add("      \"runtime-xray --help\" explains what can be reduced, and at what price.");
        return lines;
    }

    /**
     * The sentence that names the directory to exclude, and says why it is safe to.
     *
     * <p>It lives here, in one place, because it is said in three: the console at the end of
     * a campaign, {@code diagnostic.json}, and the help. Three copies would have drifted,
     * and the one a reader gets is decided by where they happened to look.
     */
    public static String exclusionAdvice(Path out) {
        return "Exclude " + out.toAbsolutePath().normalize().resolve(EXCLUDE)
                + " from the scan: it holds only generated artefacts, nothing there is"
                + " executed, and everything in it is reproducible.";
    }

    /** English grouping, and the tool's locale rather than the machine's. */
    public static String grouped(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
