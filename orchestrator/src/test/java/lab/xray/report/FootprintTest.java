package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a report costs in <b>number of files</b>, and the moment the tool says so.
 *
 * <p>The figure existed before, in the sense that anybody could go and count. Nobody did:
 * the machines where the count hurts are the ones where running a {@code find} over the
 * output is itself slow. A setting that reduces it was documented, and nothing in the run
 * ever pointed at it. These tests guard the three decisions that fix that — count without
 * paying, say it only when it matters, and always name the one measure that costs nothing.
 */
class FootprintTest {

    private static void files(Path dir, int n) throws IOException {
        Files.createDirectories(dir);
        for (int i = 0; i < n; i++) {
            Files.writeString(dir.resolve("f" + i + ".html"), "x", StandardCharsets.UTF_8);
        }
    }

    @Test
    @DisplayName("Files are counted, and the directories holding them are not")
    void countsFilesAndNotDirectories(@TempDir Path dir) throws IOException {
        files(dir.resolve("runs/one/jacoco/html"), 3);
        files(dir.resolve("runs/two/jacoco/html"), 4);
        // Seven files under five directories: a count that included the directories would
        // read as twelve, and the figure is meant to be checkable with "find -type f".
        assertEquals(7, Footprint.count(dir));
        assertEquals(3, Footprint.count(dir.resolve("runs/one")));
    }

    @Test
    @DisplayName("A directory that does not exist counts zero, it is not an error")
    void missingDirectoryCountsZero(@TempDir Path dir) throws IOException {
        // A run stopped before writing anything, or an output already archived away: the
        // figure is 0, and the campaign carries on. Counting is a comfort.
        assertEquals(0, Footprint.count(dir.resolve("never-written")));
    }

    @Test
    @DisplayName("Below the threshold the tool says nothing")
    void staysQuietBelowTheThreshold(@TempDir Path dir) throws IOException {
        files(dir.resolve("runs/one"), 5);
        Footprint small = Footprint.of(dir);
        assertFalse(small.notable());
        assertTrue(small.console().isEmpty(),
                "five files is every ordinary run: a warning there would teach nothing and "
                + "would be ignored the day it matters");
    }

    @Test
    @DisplayName("Past the threshold it gives the figure and names the directory to exclude")
    void pastTheThresholdItNamesTheExclusion(@TempDir Path dir) {
        // Built rather than written: creating ten thousand real files to check a message
        // would make the test pay exactly the cost the message is about.
        String said = String.join("\n", Footprint.message(dir, 186_400));
        assertTrue(said.contains("186,400"), "the figure itself, grouped: " + said);
        assertTrue(said.contains(Footprint.EXCLUDE),
                "and the directory to exclude — a warning with no way out is a complaint: "
                + said);
        assertTrue(said.contains("--help"),
                "the rest is a matter of trade-offs, and belongs where they are written out");
        // Exactly at the threshold it speaks: a boundary read as "strictly above" would
        // make the round number in the documentation a lie by one file.
        assertFalse(Footprint.message(dir, Footprint.NOTABLE).isEmpty());
        assertTrue(Footprint.message(dir, Footprint.NOTABLE - 1).isEmpty());
    }

    @Test
    @DisplayName("The advice names a path, and says why removing it from the scan is safe")
    void theAdviceSaysWhyItIsSafe(@TempDir Path dir) throws IOException {
        String advice = Footprint.exclusionAdvice(dir);
        assertTrue(advice.contains(dir.toAbsolutePath().normalize()
                        .resolve(Footprint.EXCLUDE).toString()),
                "an exclusion advice that does not give the path to exclude is a slogan: "
                + advice);
        // Whoever reads this has to convince somebody else, and "trust me" does not carry.
        // The three properties are the argument: generated, not executed, reproducible.
        assertTrue(advice.contains("generated"), advice);
        assertTrue(advice.contains("executed"), advice);
        assertTrue(advice.contains("reproducible"), advice);
    }

    @Test
    @DisplayName("The figure is grouped, and in the tool's locale rather than the machine's")
    void theFigureIsGroupedInTheToolsLocale() {
        // The page and the console read as English wherever they run — a machine set to a
        // French locale must not render 186 400 with a space and look like two numbers.
        assertEquals("186,400", Footprint.grouped(186_400));
        assertEquals("9", Footprint.grouped(9));
    }

    @Test
    @DisplayName("The per-run count travels in the diagnostic, run by run")
    void theDiagnosticCarriesThePerRunCount(@TempDir Path dir) throws Exception {
        files(dir.resolve("runs/one/jacoco/html"), 6);
        Sources.Index index = Sources.load(List.of());
        Diagnostic.write(dir, List.of(java.util.Map.of(
                "nom", "one", "uuid", "u-1", "chemin", "runs/one/")), index, null);
        String written = Files.readString(dir.resolve("diagnostic.json"), StandardCharsets.UTF_8);
        assertTrue(written.contains("\"fichiersEcrits\":6"),
                "each run says what it left on disk: " + written);
        assertTrue(written.contains("\"fichiersDansRuns\":6"),
                "and the directory the advice names carries its own figure: " + written);
        assertTrue(written.contains("\"seuilAlerte\":" + Footprint.NOTABLE),
                "with the threshold, so a reader knows why nothing was said: " + written);
    }
}
