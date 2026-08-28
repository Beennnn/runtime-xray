package lab.xray.report;

import lab.xray.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assembled page is the deliverable: it is sent as it is, opened offline, sometimes
 * months later. Two defects would be invisible at generation time and fatal at reading time
 * — a link that leads nowhere, and badly escaped data that stops the page from showing. That
 * is what these tests check.
 */
class DashboardTest {

    private static final Pattern HREF = Pattern.compile("(?:href|src)=\"([^\"]+)\"");
    /** The markup alone: the embedded script holds string fragments that look like links
     *  without being any. */
    private static final Pattern SCRIPT =
            Pattern.compile("<script\\b.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    /** The line ending is the template's, as git checked it out: \R accepts them all, so
     *  that reading does not depend on the machine. */
    private static final Pattern DATA =
            Pattern.compile("const D = (\\{.*?\\});\\R", Pattern.DOTALL);

    // ------------------------------------------------------------------ fixtures

    /** A complete run, such as the orchestrator produces. */
    private Path fixtureRun(Path parent, String name, String uuid, boolean withReports)
            throws IOException {
        Path run = parent.resolve(name);
        Files.createDirectories(run.resolve("jacoco/html"));
        Files.createDirectories(run.resolve("async-profiler"));
        Files.createDirectories(run.resolve("arthas"));

        Files.writeString(run.resolve("jacoco/html/jacoco.xml"), """
                <report name="t">
                  <package name="app">
                    <class name="app/Engine" sourcefilename="Engine.java">
                      <method name="compute" desc="(I)I" line="3">
                        <counter type="INSTRUCTION" missed="0" covered="9"/>
                      </method>
                      <counter type="INSTRUCTION" missed="1" covered="9"/>
                    </class>
                    <sourcefile name="Engine.java">
                      <line nr="3" mi="0" ci="9" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                </report>
                """, StandardCharsets.UTF_8);

        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Engine.compute 40\n", StandardCharsets.UTF_8);

        Files.writeString(run.resolve("arthas/watch-params.txt"), """
                method=app.Engine.compute location=AtExit
                ts=2026-08-20 21:08:16.440; [cost=0.1ms] result=@ArrayList[
                    @Object[][
                        @Integer[7],
                    ],
                    @Integer[49],
                ]
                """, StandardCharsets.UTF_8);

        Files.writeString(run.resolve("run-context.json"), Json.write(Map.of(
                "uuid", uuid,
                "nomOrigine", name,
                "commande", "java -jar app.jar",
                "methodeRacine", "app.Engine::compute",
                "machine", "test-host")), StandardCharsets.UTF_8);

        if (withReports) {
            Files.writeString(run.resolve("jacoco/html/index.html"), "<html>coverage</html>",
                    StandardCharsets.UTF_8);
            Files.createDirectories(run.resolve("jacoco-focused/html"));
            Files.writeString(run.resolve("jacoco-focused/html/index.html"), "<html>focused</html>",
                    StandardCharsets.UTF_8);
        }
        return run;
    }

    private Path sources(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("app"));
        Files.writeString(src.resolve("app/Engine.java"), """
                package app;
                class Engine {
                    int compute(int x) { return x * x; }
                }
                """, StandardCharsets.UTF_8);
        return src;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Path page) throws IOException {
        String html = Files.readString(page, StandardCharsets.UTF_8);
        Matcher m = DATA.matcher(html);
        assertTrue(m.find(), "the data must be embedded in the page");
        return (Map<String, Object>) Json.read(m.group(1));
    }

    // ------------------------------------------------------------------- tests

    @Test
    @DisplayName("One run produces a complete page, with no template marker left over")
    void buildsCompletePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-1", true);

        Path page = Dashboard.build(out, List.of(sources(dir)), 8);
        String html = Files.readString(page, StandardCharsets.UTF_8);

        assertEquals(out.resolve("index.html"), page, "the page lives at the directory's root");
        assertFalse(html.contains("/*__DATA__*/"), "the template marker must be replaced");
        assertTrue(html.contains("<title>"), "the page must be complete");
    }

    @Test
    @DisplayName("NO link in the page may be broken")
    void producesNoBrokenLinks(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        // Deliberately WITHOUT the detailed reports: the matching links must then not be
        // written at all, rather than point into the void.
        fixtureRun(out.resolve("runs"), "no-reports", "UUID-2", false);

        Path page = Dashboard.build(out, List.of(sources(dir)), 8);
        String markup = SCRIPT.matcher(Files.readString(page, StandardCharsets.UTF_8))
                .replaceAll("");

        List<String> broken = new ArrayList<>();
        Matcher m = HREF.matcher(markup);
        while (m.find()) {
            String href = m.group(1);
            if (href.startsWith("http") || href.startsWith("#") || href.startsWith("data:")
                    || href.isBlank()) {
                continue;
            }
            String clean = href.split("[#?]")[0];
            if (!Files.exists(out.resolve(clean))) {
                broken.add(href);
            }
        }
        assertTrue(broken.isEmpty(), "broken links in the produced page: " + broken);
    }

    @Test
    @DisplayName("Links to the reports that are there are written, and do resolve")
    void writesLinksForExistingReports(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "with-reports", "UUID-3", true);

        Path page = Dashboard.build(out, List.of(sources(dir)), 8);
        Map<String, Object> data = dataOf(page);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> reports = (Map<String, Object>) first.get("rapports");

        assertEquals(Boolean.TRUE, reports.get("couverture"));
        assertEquals(Boolean.TRUE, reports.get("ciblee"));
        assertEquals(Boolean.FALSE, reports.get("flamegraph"), "this report does not exist here");

        String base = String.valueOf(first.get("chemin"));
        assertTrue(Files.exists(out.resolve(base + "jacoco/html/index.html")),
                "the announced path must lead to the report: " + base);
        assertTrue(run.toString().endsWith("with-reports"));
    }

    @Test
    @DisplayName("Each run's path is relative to the page, never absolute")
    void runPathsAreRelative(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "relative", "UUID-4", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        String path = String.valueOf(((Map<String, Object>)
                ((List<Object>) data.get("runs")).get(0)).get("chemin"));
        assertFalse(path.startsWith("/"), "an absolute path would break the page once moved");
        assertTrue(path.endsWith("/"), "the path must be a prefix usable as it is");
        assertEquals("runs/relative/", path);
    }

    @Test
    @DisplayName("Several runs are gathered into a single page")
    void gathersSeveralRuns(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "first", "UUID-A", true);
        fixtureRun(out.resolve("runs"), "second", "UUID-B", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        assertEquals(2, ((List<?>) data.get("runs")).size());
    }

    @Test
    @DisplayName("Runs collected from several directories are all found")
    void findsRunsAcrossDirectories(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("commun");
        Files.createDirectories(out);
        fixtureRun(out.resolve("machine-a/runs"), "run-a", "UUID-A", true);
        fixtureRun(out.resolve("machine-b/runs"), "run-b", "UUID-B", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        assertEquals(2, ((List<?>) data.get("runs")).size(),
                "gathering output directories must be enough");
    }

    @Test
    @DisplayName("Renaming afterwards replaces the name without erasing the origin")
    void lateRenamingKeepsOrigin(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "technical-name", "UUID-R", true);
        Files.writeString(out.resolve("noms.json"),
                Json.write(Map.of("UUID-R", "Acceptance — nominal case")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("Acceptance — nominal case", run.get("nom"));
        assertEquals("technical-name", run.get("nomOrigine"), "the origin is what allows going back");
        assertEquals(Boolean.TRUE, run.get("renomme"));
    }

    @Test
    @DisplayName("A complete annotation carries the name, the description and the labels")
    void richAnnotationIsRead(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "technical-name", "UUID-A", true);
        Files.writeString(out.resolve("noms.json"), Json.write(Map.of(
                "UUID-A", Map.of(
                        "nom", "Acceptance of 21/08",
                        "description", "Check the weather branch",
                        "etiquettes", Map.of("ticket", "RX-142", "to-replay", "")))),
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("Acceptance of 21/08", run.get("nom"));
        assertEquals("technical-name", run.get("nomOrigine"), "the origin stays consultable");
        assertEquals("Check the weather branch", run.get("description"));
        assertEquals("RX-142", ((Map<?, ?>) run.get("etiquettes")).get("ticket"));
        assertEquals("", ((Map<?, ?>) run.get("etiquettes")).get("to-replay"),
                "a label may be no more than a key");
    }

    @Test
    @DisplayName("The recorded pruning travels all the way to the page")
    void pruningTravelsToThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-E", true);
        Files.writeString(out.resolve("noms.json"), Json.write(Map.of(
                "UUID-E", Map.of("elagage", Map.of(
                        "racine", "app/Main.main",
                        "coupes", List.of("app/Main.main;app/Engine.write"))))),
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        Map<?, ?> pruning = (Map<?, ?>) run.get("elagage");
        assertNotNull(pruning, "without it, the page would reopen the whole tree at every reading");
        assertEquals("app/Main.main", pruning.get("racine"));
        assertEquals(List.of("app/Main.main;app/Engine.write"), pruning.get("coupes"));

        // Pruning frames the reading: the measurement itself stays whole. The tree lives
        // under the run, in its block — the page no longer carries it.
        String tree = Files.readString(out.resolve(String.valueOf(run.get("chemin")))
                .resolve("vue/arbre.js"), StandardCharsets.UTF_8);
        assertTrue(tree.contains("\"total\":40"), "the measured total does not move: " + tree);
    }

    @Test
    @DisplayName("With no name at launch, the identifier names the run")
    void identifierStandsInForAMissingName(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "20260821-004121", "0123456789abcdef", true);
        // A run launched without --name: the context carries no name.
        Files.writeString(run.resolve("run-context.json"), Json.write(Map.of(
                "uuid", "0123456789abcdef",
                "commande", "java -jar app.jar")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertNull(first.get("nomOrigine"), "no name was given: nothing is invented");
        assertEquals("01234567", first.get("nom"), "the shortened identifier stands in for a name");
    }

    @Test
    @DisplayName("An unreadable rename file does not stop the page from being produced")
    void brokenRenameFileIsTolerated(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-X", true);
        Files.writeString(out.resolve("noms.json"), "{ this is not JSON",
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("trial", run.get("nom"), "we fall back on the original name");
    }

    @Test
    @DisplayName("The page carries everything: sources, coverage, tree, values, context")
    void embedsEverythingNeeded(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "full", "UUID-C", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));

        // The report is a folder: the page carries the table of contents, the blocks carry
        // the rest. What matters is that NOTHING is missing — not that everything sits in the
        // same place.
        assertTrue(((Map<?, ?>) data.get("sourcesDisponibles")).containsKey("app/Engine.java"),
                "the page must know this code exists, and where to take it from");
        assertTrue(Files.readString(out.resolve("vue/sources/app.js"), StandardCharsets.UTF_8)
                        .contains("class Engine"),
                "and the code must be in the block, otherwise the folder is unreadable");

        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertNotNull(run.get("methods"), "the class tree shows straight away: it stays");
        assertNotNull(run.get("context"));
        Path view = out.resolve(String.valueOf(run.get("chemin"))).resolve("vue");
        for (String block : List.of("couverture.js", "arbre.js", "valeurs.js")) {
            assertTrue(Files.isRegularFile(view.resolve(block)), block + " must live under the run");
        }
        assertEquals("app/Engine", run.get("tracedClass"),
                "the inspected class comes from the context, not from a hard-coded value");
        assertTrue(Files.readString(view.resolve("valeurs.js"), StandardCharsets.UTF_8)
                        .contains("app/Engine.compute"),
                "the captured values live in their run's block");
    }

    @Test
    @DisplayName("Without a context, the inspected class is derived from the captured values")
    void tracedClassFallsBackToCapturedValues(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "no-context", "UUID-D", true);
        Files.delete(run.resolve("run-context.json"));

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("app/Engine", first.get("tracedClass"));
        assertEquals("no-context", first.get("nom"), "failing that, the directory's name");
    }

    @Test
    @DisplayName("A directory with no run at all gives an explicit error")
    void emptyDirectoryFailsClearly(@TempDir Path dir) {
        Path out = dir.resolve("empty");
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> Dashboard.build(out, List.of(), 8));
        assertTrue(e.getMessage().contains("no run found"), e.getMessage());
    }

    @Test
    @DisplayName("A run name containing quotes does not break the page")
    void quotesInRunNameDoNotBreakThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "trap", "UUID-Q", true);
        Files.writeString(run.resolve("run-context.json"), Json.write(Map.of(
                "uuid", "UUID-Q",
                "nomOrigine", "trial \"quotes\" and </script> and \\ backslash",
                "methodeRacine", "app.Engine::compute")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("trial \"quotes\" and </script> and \\ backslash", first.get("nom"));
    }

    @Test
    @DisplayName("The run's files are described in one single place")
    void theFileTableIsNotDuplicated(@TempDir Path dir) throws Exception {
        // The top menu and the overview's "raw outputs" block show the same list. As long as
        // each carried its own, adding an export to one forgot it in the other — it happened:
        // perf was missing from the menu while the block announced it.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-F", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        for (String path : List.of("exports/profil.perf.txt", "exports/profil.cpuprofile",
                "exports/couverture.lcov", "exports/valeurs.json",
                "async-profiler/profil.collapsed", "execution.log")) {
            assertEquals(1, page.split(java.util.regex.Pattern.quote(path), -1).length - 1,
                    "\"" + path + "\" is written more than once: the two lists will diverge");
        }
        assertTrue(page.contains("id=\"dlmenu\""), "the exports menu must be there");
    }

    @Test
    @DisplayName("The page never gives itself a whole-window scrollbar on top of the report's")
    void thePageNeverScrollsAsAWhole(@TempDir Path dir) throws Exception {
        // The layout subtracted a hard-coded header height from the body — 47 px — whereas
        // that header grows with its links, with the browser's zoom and with the system font:
        // it measures 49.75 in Chromium. Three pixels were enough to give the whole window a
        // scrollbar that came and stuck against the report's, and it was the report's — the
        // only useful one — that got hidden by it. What this test guards is not the CSS rule:
        // it is the refusal to guess a height one can measure.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-H", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);
        String style = page.substring(page.indexOf("<style>"), page.indexOf("</style>"));

        assertFalse(style.contains("height:calc(100% - "),
                "a guessed header height is back: that is what made the second scrollbar");
        assertTrue(style.contains("body{display:flex;flex-direction:column;height:100%;overflow:hidden}"),
                "the body fills the window and does not scroll: its panels are what scroll");
        assertTrue(style.contains("flex:1 1 auto;min-height:0}"),
                "the working area takes what the header leaves, whatever its height");
    }

    @Test
    @DisplayName("The \"never executed\" list folds, without taking the count with it")
    void theNeverExecutedListCanBeFolded(@TempDir Path dir) throws Exception {
        // On a real project this list holds hundreds of classes and pushes off screen
        // everything that follows it — the costly methods, the captured values, the caveat —
        // that is to say what one came for. Foldable, it stops costing the whole page. Two
        // things make it usable once folded, and those are what this test guards: the count
        // stays shown, and the choice is kept.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-P", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("sectionPliable(\"dead\", \"Jamais exécuté\""),
                "the \"never executed\" section must be the one that folds");
        assertTrue(page.contains("class=\"plicnt\""),
                "the count must stay readable when folded, otherwise folding loses what decides to open");
        assertTrue(page.contains("aria-expanded=") && page.contains("aria-controls=\"pli-"),
                "a heading that folds must announce itself as such, mouse aside");
        assertTrue(page.contains("runtime-xray.plis"),
                "the folding choice is a reading setting: it is kept in the browser");
    }

    @Test
    @DisplayName("The tree does not steal Enter or Space from the page's controls")
    void theTreeDoesNotStealActivationKeys(@TempDir Path dir) throws Exception {
        // Keyboard navigation of the tree listened to the whole document and swallowed Enter
        // and Space everywhere outside input fields. As a result, no button on the page
        // activated from the keyboard — not the help, not the exports, not the panel folding.
        // The arrows, on the other hand, must stay with the tree: one wants to walk it without
        // going back to the mouse. It is that sharing the test guards.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-K", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("t.closest(\"button, a[href], select, summary\")"),
                "a control that has the focus must keep Enter and Space for itself");
        assertTrue(page.contains("\"ArrowDown\", \"ArrowUp\", \"ArrowRight\", \"ArrowLeft\""),
                "the arrows stay with the tree navigation");
    }

    @Test
    @DisplayName("Without a JaCoCo site, the coverage is still rendered — it comes from the XML")
    void coverageSurvivesWithoutTheJacocoSite(@TempDir Path dir) throws Exception {
        // This is the shape JACOCO_REPORTS=data produces: the XML, no HTML site. What the
        // page shows must not move, because it never read the HTML in the first place.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "no-site", "UUID-DATA", false);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> reports = (Map<String, Object>) run.get("rapports");

        assertEquals(Boolean.FALSE, reports.get("couverture"), "no site was written");
        assertEquals(Boolean.TRUE, reports.get("jacocoXml"), "but the data is there");
        assertTrue(Files.readString(out.resolve(String.valueOf(run.get("chemin")))
                        .resolve("vue/couverture.js"), StandardCharsets.UTF_8)
                        .contains("app/Engine"),
                "the coverage block is built from the XML, so it does not depend on the site");
    }

    @Test
    @DisplayName("A JaCoCo report that was not produced stays named, with the command for it")
    void anAbsentJacocoReportIsStillNamed(@TempDir Path dir) throws Exception {
        // The whole point of making the rendering optional: an entry that vanishes reads
        // exactly like an entry nobody could fill. The page keeps naming it instead.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "no-site", "UUID-N", false);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("{mark: \"jacoco\", nom: \"JaCoCo\", toujours: true"),
                "the JaCoCo group must survive the absence of its files, like open formats do");
        assertTrue(page.contains("--jacoco-reports full"),
                "and say the command that produces them, on the machine where one is stuck");
        assertTrue(page.contains("data-cmd=") && page.contains("a.dataset.cmd"),
                "each group explains how to get back what IT carries: one single command for "
                + "both would have sent one of the two into a wall");
    }

    @Test
    @DisplayName("The source roots a run recorded are readable again, absent ones included")
    void theRecordedSourceRootsCanBeReadBack(@TempDir Path dir) throws Exception {
        // Reassembling without --sources used to lose the annotated code and announce "no
        // source directory was given" — false, and expensive: it sent the reader looking
        // for a setting they had already made when the measurement was taken.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path gone = dir.resolve("gone/src/main/java");
        Path here = sources(dir);

        Path a = fixtureRun(out.resolve("runs"), "one", "UUID-1", true);
        Path b = fixtureRun(out.resolve("runs"), "two", "UUID-2", true);
        record(a, here + java.io.File.pathSeparator + gone);
        record(b, here.toString());

        List<Path> roots = Dashboard.recordedSourceRoots(out);

        assertEquals(List.of(here, gone), roots,
                "the runs' order is kept, and a root recorded twice is not returned twice");
        assertTrue(roots.contains(gone),
                "a root that no longer exists is returned all the same: it is what lets the "
                + "caller name the path to correct, instead of claiming none was ever given");
    }

    @Test
    @DisplayName("Runs that recorded nothing yield nothing — no root is invented")
    void nothingRecordedYieldsNothing(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "plain", "UUID-0", true);
        // The fixture's context carries no source root: reading one back must not turn into
        // deducing one. Guessing a root from a convention is what Sources.searchRoots
        // refuses to do, and for the same reason.
        assertEquals(List.of(), Dashboard.recordedSourceRoots(out));
    }

    /** Records a source root in a run's context, as a measurement does. */
    private void record(Path run, String roots) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) Json.read(
                Files.readString(run.resolve("run-context.json"), StandardCharsets.UTF_8));
        context.put("repertoiresSources", roots);
        Files.writeString(run.resolve("run-context.json"), Json.write(context),
                StandardCharsets.UTF_8);
    }

    // ------------------------------------------ what one knows when it did not work

    @Test
    @DisplayName("A source root one notch too high still gives the code")
    @SuppressWarnings("unchecked")
    void findsSourcesEvenWhenTheRootIsTooHigh(@TempDir Path dir) throws Exception {
        // The original defect: coverage indexes "app/Engine.java", while the index indexed
        // "src/app/Engine.java" as soon as the root passed was the project and not the source
        // directory. No match, hence "Source unavailable" everywhere — and nothing to
        // understand it by.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-S", true);
        sources(dir);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(dir), 8));

        Map<String, Object> src = (Map<String, Object>) data.get("sourcesDisponibles");
        assertTrue(src.containsKey("app/Engine.java"),
                "the key must be JaCoCo's, not the path from the root: " + src.keySet());
    }

    @Test
    @DisplayName("The diagnostic written beside the page says what was looked for and found")
    @SuppressWarnings("unchecked")
    void writesADiagnosticFileNextToThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-D", true);

        // Deliberately a root that does not contain the measured class's source.
        Dashboard.build(out, List.of(dir.resolve("elsewhere")), 8);

        Path file = out.resolve("diagnostic.json");
        assertTrue(Files.isRegularFile(file), "the diagnostic must exist without being asked for");
        Map<String, Object> d = (Map<String, Object>) Json.read(
                Files.readString(file, StandardCharsets.UTF_8));

        assertNotNull(d.get("machine"), "the environment explains half of the symptoms");
        assertNotNull(d.get("executions"), "the runs and their reports are in the file");
        Map<String, Object> rap = (Map<String, Object>) d.get("rapprochement");
        assertEquals(1L, ((Number) rap.get("fichiersMesures")).longValue());
        assertEquals(1L, ((Number) rap.get("fichiersSansSource")).longValue());
        assertNotNull(rap.get("conclusion"), "a sentence one reads first");
        List<Object> missing = (List<Object>) rap.get("exemplesManquants");
        assertEquals("app/Engine.java", ((Map<String, Object>) missing.get(0)).get("cherche"),
                "the file must name the exact key the coverage was asking for");

        Map<String, Object> sources = (Map<String, Object>) d.get("sources");
        List<Object> roots = (List<Object>) sources.get("racines");
        assertEquals(Boolean.FALSE, ((Map<String, Object>) roots.get(0)).get("existe"),
                "a non-existent root must show, not vanish");
    }

    @Test
    @DisplayName("The diagnostic contains no server secret")
    void theDiagnosticCarriesNoSharedSecret(@TempDir Path dir) throws Exception {
        // This file is made to be passed on. A token slipping into it would travel with it,
        // and nothing in the page would say so.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-T", true);
        Dashboard.build(out, List.of(sources(dir)), 8);

        String text = Files.readString(out.resolve("diagnostic.json"), StandardCharsets.UTF_8);
        assertFalse(text.contains("serve-token") || text.contains("secret"),
                "no secret may travel with the diagnostic");
    }

    @Test
    @DisplayName("The explorer says where each class is, and what each setting commands")
    @SuppressWarnings("unchecked")
    void explainsWhereEachClassWasFound(@TempDir Path dir) throws Exception {
        // Three things can be missing — the bytecode, the source, the class — and an empty
        // report gives them the same face. The explorer separates them; the settings table
        // says which one to widen, because one nearly always suspects the wrong one.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-X", true);
        Path classes = dir.resolve("classes/app");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("Engine.class"), "fake bytecode", StandardCharsets.UTF_8);

        Map<String, Object> launch = new java.util.LinkedHashMap<>();
        launch.put("commande", "java -jar app.jar");
        launch.put("methodeRacine", "app.Engine");
        launch.put("racinesClasses", List.of(Map.of(
                "chemin", "classes", "absolu", dir.resolve("classes").toString(), "existe", true)));

        Path page = Dashboard.build(out, List.of(sources(dir)), 8, PackageFilter.NONE, launch);
        Map<String, Object> data = dataOf(page);
        Map<String, Object> d = (Map<String, Object>) data.get("diagnostic");

        List<Object> bytecode = (List<Object>) d.get("bytecode");
        assertEquals(1, bytecode.size(), "the bytecode root must be listed");
        Map<String, Object> root = (Map<String, Object>) bytecode.get(0);
        assertEquals(List.of("app/Engine"), root.get("classes"),
                "the classes found are what allows answering \"where is it?\"");

        String html = Files.readString(page, StandardCharsets.UTF_8);
        assertTrue(html.contains("function verdictRecherche("),
                "the search must say whether a class was found, and where");
        assertTrue(html.contains("function arbreSources("),
                "the tree must exist, coloured by what each class is missing");
        assertTrue(html.contains("function piegesConfig("),
                "the settings table is what avoids widening the wrong filter");
        assertTrue(html.contains("ne restreint NI la couverture, NI les classes affichées"),
                "--root must no longer be suspectable of a report without sources");
    }

    @Test
    @DisplayName("The diagnostic reads under \"sources\", and the page reads it at the right depth")
    void thePageReadsTheDiagnosticAtTheRightDepth(@TempDir Path dir) throws Exception {
        // Read one notch too high, the diagnostic announced "0 roots" although everything
        // had been found — and blamed the user's configuration.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-N", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("function diagSources(){ return (D.diagnostic || {}).sources || {}; }"),
                "a single named accessor, so the three inventories do not get confused");
        assertFalse(page.contains("(D.diagnostic || {}).racines"),
                "the roots do not live at the diagnostic's root");
    }

    @Test
    @DisplayName("Only the displayable sources travel, but the diagnostic knows them all")
    @SuppressWarnings("unchecked")
    void carriesOnlyTheSourcesThePageCanShow(@TempDir Path dir) throws Exception {
        // A source no measured class asks for has no display path: nothing in the page leads
        // to it. Embedding it means copying the project's whole tree — a 217 MB report that
        // Firefox gave up on opening, on 26 August 2026.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "trial", "UUID-P", true);
        Path src = sources(dir);
        Files.writeString(src.resolve("app/NeverMeasured.java"), """
                package app;
                class NeverMeasured { int x() { return 1; } }
                """, StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(src), 8));

        Map<String, Object> bundled = (Map<String, Object>) data.get("sourcesDisponibles");
        assertTrue(bundled.containsKey("app/Engine.java"), "the measured class keeps its code");
        assertFalse(bundled.containsKey("app/NeverMeasured.java"),
                "a source with no measured class cannot be shown, so it does not travel");
        assertFalse(Files.readString(out.resolve("vue/sources/app.js"), StandardCharsets.UTF_8)
                        .contains("NeverMeasured"),
                "and it must not weigh in the block either");

        // But nothing functional goes away: the diagnostic still knows it was read, and the
        // "where is each class" tree still places it.
        Map<String, Object> d = (Map<String, Object>) data.get("diagnostic");
        Map<String, Object> byName =
                (Map<String, Object>) ((Map<String, Object>) d.get("sources")).get("parNom");
        assertTrue(byName.containsKey("NeverMeasured.java"),
                "the diagnostic must keep saying where each file was read");
        assertEquals(1L, ((Number) ((Map<String, Object>) d.get("rapprochement"))
                .get("sourcesSansClasse")).longValue(),
                "and count those matching no measured class");
    }

    @Test
    @DisplayName("The page can unify the ticked runs' coverage, and say which one covered")
    void thePageCanUnifyCoverageAcrossRuns(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "one", "UUID-C1", true);
        fixtureRun(out.resolve("runs"), "two", "UUID-C2", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("function covPourVue("),
                "the code view must be able to read a unified coverage");
        assertTrue(page.contains("function pastillesRuns("),
                "unifying without saying WHO covered would lose the information the JaCoCo "
                + "merge already loses");
        assertTrue(page.contains("runtime-xray.cumul"),
                "the choice to accumulate is a reading setting: it is kept");
    }

    @Test
    @DisplayName("The code column may be narrower than its longest line")
    void theCodeColumnMayBeNarrowerThanItsLongestLine() throws Exception {
        // The implicit minimum of a grid column is its content's min-content. With "1fr", a
        // 450-character line — such lines do exist in enterprise code — widened the column to
        // 3680 px in a 1280 window: the code did not scroll horizontally, and its vertical
        // scrollbar sat at 4016 px, off screen. One could neither go down the file nor see the
        // end of the line.
        String template = new String(Objects.requireNonNull(
                Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(template.contains("minmax(0, 1fr)"),
                "without the zero, the code column widens instead of scrolling");
        assertFalse(template.contains("grid-template-columns:var(--navw,330px) 6px 1fr"),
                "this is exactly the shape that pushed the scrollbar off screen");
    }
}
