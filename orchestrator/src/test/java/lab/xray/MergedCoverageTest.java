package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The campaign's merged JaCoCo report — the command that draws it.
 *
 * <p>This is the shape of defect that costs a whole feature and says almost nothing. The
 * report was asked for by naming the merge <b>command</b> where the merged <b>file</b> was
 * meant; a {@code List<String>} has a {@code toString()}, so it compiled, and JaCoCo then
 * looked for a file called {@code [java, -jar, …]}. The merge being a bonus, the failure
 * was caught and reduced to a single warning line — the report was produced anyway, nobody
 * read the line, and {@code jacoco-fusion/} was missing from every campaign ever run.
 *
 * <p>Found on 29 August 2026 by regenerating the demo, not by reading the code.
 */
class MergedCoverageTest {

    private static final Path CLI = Path.of("/tools/jacococli.jar");
    private static final Path MERGED = Path.of("/out/jacoco-fusion/fusion.exec");
    private static final Path HTML = Path.of("/out/jacoco-fusion/html");
    // Never a literal with separators: under Windows the same path renders "\app\classes",
    // and the test would fail on a command that is exactly right.
    private static final Path CLASSES = Path.of("/app/classes");
    private static final Path SOURCES = Path.of("/app/src");

    private static List<String> command() {
        return Main.mergedReportCommand(CLI, MERGED, HTML, 3, List.of(CLASSES), List.of(SOURCES));
    }

    @Test
    @DisplayName("The report is drawn from the merged file, never from the merge command")
    void theReportIsDrawnFromTheMergedFile() {
        List<String> command = command();
        int report = command.indexOf("report");
        assertTrue(report > 0, "the CLI must be asked for a report: " + command);
        // JaCoCo takes the .exec files to read as positional arguments, right after the
        // sub-command. It is that position, and it alone, that the defect got wrong.
        assertEquals(MERGED.toString(), command.get(report + 1),
                "what follows \"report\" is the merged .exec, not anything else");
    }

    @Test
    @DisplayName("No argument is a Java collection printed by accident")
    void noArgumentIsACollectionPrintedByAccident() {
        // The signature of the defect, and what makes it survive a re-reading: the argument
        // looks like a path until one notices it starts with a bracket.
        for (String argument : command()) {
            assertFalse(argument.startsWith("[") && argument.endsWith("]"),
                    "an argument printed from a List instead of a Path: " + argument);
        }
    }

    @Test
    @DisplayName("The bytecode and the sources are passed, otherwise the report is empty")
    void theBytecodeAndTheSourcesArePassed() {
        List<String> command = command();
        // Without --classfiles JaCoCo produces a report with no class at all; without
        // --sourcefiles it produces one with no annotated code. Both are silent failures.
        assertTrue(command.contains("--classfiles") && command.contains(CLASSES.toString()),
                "the analysed bytecode must be named: " + command);
        assertTrue(command.contains("--sourcefiles") && command.contains(SOURCES.toString()),
                "and the sources, otherwise the merged report shows no code: " + command);
        assertTrue(command.contains("Cumulative coverage — 3 runs"),
                "the report says how many runs it unites: " + command);
    }
}
