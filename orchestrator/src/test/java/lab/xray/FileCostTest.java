package lab.xray;

import lab.xray.report.Coverage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the tool refuses to write, and what it says about the rest.
 *
 * <p>Two decisions meet here, and they pull in opposite directions. One says that a report
 * nobody asked to lose must never go missing — so the only thing the tool drops on its own
 * is an exact copy. The other says that a machine where a filter inspects every file open
 * has to be given a way out, and that a way out which takes something away must say so
 * where it is offered, not in a paragraph somebody may never reach.
 */
class FileCostTest {

    /** Two classes, both entered: the focused site would list exactly the complete one's. */
    private static final String ALL_RAN = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <report name="test">
              <package name="app/engine">
                <class name="app/engine/Compute" sourcefilename="Compute.java">
                  <counter type="INSTRUCTION" missed="4" covered="12"/>
                </class>
                <class name="app/engine/Route" sourcefilename="Route.java">
                  <counter type="INSTRUCTION" missed="0" covered="9"/>
                </class>
              </package>
            </report>
            """;

    /** The same, with one class the run never entered. */
    private static final String ONE_NEVER_RAN = ALL_RAN.replace(
            "<counter type=\"INSTRUCTION\" missed=\"0\" covered=\"9\"/>",
            "<counter type=\"INSTRUCTION\" missed=\"9\" covered=\"0\"/>");

    private static Coverage coverage(Path dir, String xml) throws Exception {
        Path file = dir.resolve("jacoco.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return Coverage.parse(file);
    }

    private static String main() throws IOException {
        return Files.readString(Path.of("").toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("When every analysed class ran, the focused report is a copy")
    void everyClassRanMakesItACopy(@TempDir Path dir) throws Exception {
        // Two files per class, written then walked then archived, to frame nothing. This is
        // the only case where the tool drops a report without being asked, and it is the
        // only case where dropping it takes nothing away.
        assertTrue(Main.focusedWouldRepeatTheComplete(new Config(), coverage(dir, ALL_RAN)));
    }

    @Test
    @DisplayName("One class that never ran, and the focused report has something to say")
    void oneClassThatNeverRanIsEnough(@TempDir Path dir) throws Exception {
        // The threshold is one class, deliberately. A "nearly identical" report is exactly
        // the one a reader opens to find what is missing from it.
        assertFalse(Main.focusedWouldRepeatTheComplete(
                new Config(), coverage(dir, ONE_NEVER_RAN)));
    }

    @Test
    @DisplayName("A hidden package puts the two reports back out of step")
    void hidingAPackageDisablesTheShortcut(@TempDir Path dir) throws Exception {
        // Hiding happens on OUR side, on reading. The CLI knows nothing of it and puts the
        // hidden classes in the complete site — so the two sites differ, and the coverage
        // read here can no longer see it: everything it holds ran.
        Config config = new Config();
        config.hiddenPackages = "org.slf4j";
        assertFalse(Main.focusedWouldRepeatTheComplete(config, coverage(dir, ALL_RAN)));
    }

    @Test
    @DisplayName("A measurement with no class at all is not a copy of anything")
    void anEmptyCoverageIsNotADuplicate(@TempDir Path dir) throws Exception {
        // "Every class ran" is vacuously true of no class. Answering yes would have made an
        // empty measurement — the failure this tool exists to explain — silently produce
        // one report fewer.
        assertFalse(Main.focusedWouldRepeatTheComplete(new Config(),
                coverage(dir, "<report name=\"test\"></report>")));
    }

    @Test
    @DisplayName("The help has a section for the filtered machine, and it names the exclusion")
    void theHelpNamesTheExclusion() throws IOException {
        String help = section();
        assertTrue(help.contains("runs"),
                "the directory to exclude is the first thing to say: it is the only measure "
                + "that removes the cost instead of trimming it");
        assertTrue(help.contains("reproducible"),
                "and why it is safe to exclude, because the reader has to convince somebody");
    }

    @Test
    @DisplayName("Every setting that takes something away says so where it is offered")
    void everyRestrictingSettingCarriesItsPrice() throws IOException {
        String help = section();
        // The rule this guards: nothing restricts by default, and what restricts is written
        // down beside the thing it restricts. A value added later and left out of here is a
        // capability nobody weighs before turning it on.
        for (String value : Config.JACOCO_REPORTS) {
            if (Config.FULL.equals(value)) continue;
            assertTrue(help.contains("--jacoco-reports " + value),
                    "\"" + value + "\" reduces what is written and is not in the section "
                    + "that lists what each value costs");
        }
        assertTrue(help.contains("--archive " + Config.REPLACE),
                "the value that removes the measurements must be named with its price");
        assertFalse(help.contains("--archive " + Config.KEEP + " "),
                "\"keep\" takes nothing away: listing it among the trade-offs would suggest "
                + "it does");
    }

    @Test
    @DisplayName("No option quoted in that section is an option that does not exist")
    void theSectionQuotesOnlyRealOptions() throws IOException {
        String main = main();
        int start = main.indexOf("switch (a) {");
        int end = main.indexOf("unknown option", start);
        String options = main.substring(start, end);
        Matcher m = Pattern.compile("(--[a-z-]{3,})").matcher(section());
        while (m.find()) {
            String option = m.group(1);
            assertTrue(options.contains('"' + option + '"'),
                    "the help sends the reader to " + option + ", which Main does not accept "
                    + "— on the machine where this section gets read, there is nothing to "
                    + "check it against");
        }
    }

    /** The help's section on filtered machines, cut out of the source rather than run. */
    private static String section() throws IOException {
        String main = main();
        int start = main.indexOf("LIVING WITH A SECURITY FILTER");
        int end = main.indexOf("EXIT STATUS", start);
        assertTrue(start > 0 && end > start, "the section must stay locatable in the help");
        return main.substring(start, end);
    }
}
