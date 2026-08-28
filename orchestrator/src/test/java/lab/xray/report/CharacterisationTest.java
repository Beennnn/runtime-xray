package lab.xray.report;

import lab.xray.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the tool produces today, pinned as it is — before moving it.
 *
 * <h2>Why these tests, and why now</h2>
 *
 * <p>The repository's other tests guard <b>decisions</b>: why a source root is not guessed,
 * why the count is in cores. These guard no decision at all. They guard a <b>reading</b>:
 * given equal inputs, here are exactly the files produced, the facts written and the
 * numbers computed.
 *
 * <p>They exist for an announced rewrite — the format switching to English, then the code
 * itself. Both undertakings rename a great deal and should change nothing else. And
 * "changing nothing else" is precisely what no test was checking: one could have lost a
 * fact, shifted a percentage or stopped writing a file without a single assertion flinching.
 *
 * <p><b>They are made to be modified</b>, and that is what sets them apart from the rest.
 * When the format renames {@code classe.jamais_executee} to {@code class.never_executed},
 * the value expected here will change — that is normal, that is the point of the work. What
 * must not change, and what the test will catch, is the <b>number</b> of facts, the
 * <b>list</b> of dead classes and the coverage <b>figures</b>. A rename that touches those
 * is no longer a rename.
 */
class CharacterisationTest {

    /**
     * The files a two-run report contains, today.
     *
     * <p>Nothing here is pretty or final: it is a survey. It catches the file one would stop
     * writing, the one one would write twice, and the unintended rename.
     */
    private static final List<String> EXPECTED_FILES = List.of(
            "diagnostic.json",
            "faits.jsonl",
            "index.html",
            "rapport.md",
            "runs/run-a/async-profiler/profil.collapsed",
            "runs/run-a/jacoco/html/index.html",
            "runs/run-a/jacoco/html/jacoco.xml",
            "runs/run-a/run-context.json",
            "runs/run-a/vue/arbre.js",
            "runs/run-a/vue/couverture.js",
            "runs/run-a/vue/traces.js",
            "runs/run-a/vue/valeurs.js",
            "runs/run-b/async-profiler/profil.collapsed",
            "runs/run-b/jacoco/html/index.html",
            "runs/run-b/jacoco/html/jacoco.xml",
            "runs/run-b/run-context.json",
            "runs/run-b/vue/arbre.js",
            "runs/run-b/vue/couverture.js",
            "runs/run-b/vue/traces.js",
            "runs/run-b/vue/valeurs.js",
            "synthese.svg",
            "vue/cumul.js",
            "vue/manifeste.json",
            "vue/poids.js",
            "vue/presence.js",
            "vue/sources/app.js");

    /** The fact count per family, today. */
    private static final Map<String, Integer> EXPECTED_FACTS = new TreeMap<>(Map.of(
            "campaign", 1,
            "class", 1,
            "class.never_executed", 1,
            "coverage.run", 2,
            "run", 2,
            "unavailable", 2,
            "method.hot", 4,
            "source.missing", 1));

    /** The options the command line accepts, today. */
    private static final List<String> EXPECTED_OPTIONS = List.of(
            "--attach-after", "--classes", "--components", "--composants", "--config",
            "--context", "--contexte", "--cover", "--export", "--families", "--familles",
            "--filter", "--follow", "--help", "--hide", "--interval", "--java", "--level",
            "--max-seconds", "--name", "--niveau", "--no-values", "--out", "--print-options",
            "--repo", "--report-only", "--root", "--serve", "--serve-host", "--serve-token",
            "--sources", "--suivi");

    @Test
    @DisplayName("Two runs produce exactly these twenty-six files")
    void twoRunsProduceExactlyTheseFiles(@TempDir Path dir) throws Exception {
        Path out = assemble(dir);
        assertEquals(EXPECTED_FILES, files(out),
                "one file more or less is never a detail: the report is a folder, and "
                + "the page as much as the skills count on its shape");
    }

    @Test
    @DisplayName("The same runs always yield the same facts, in the same number")
    void theSameRunsAlwaysYieldTheSameFacts(@TempDir Path dir) throws Exception {
        Path out = assemble(dir);
        List<Map<String, Object>> facts = facts(out);

        Map<String, Integer> counts = new TreeMap<>();
        for (Map<String, Object> f : facts) {
            counts.merge(String.valueOf(f.get("fact")), 1, Integer::sum);
        }
        assertEquals(EXPECTED_FACTS, counts,
                "renaming a family is expected; losing one, duplicating one, or ceasing "
                + "to write one is not");

        assertEquals(List.of("app.Never"), values(facts, "class.never_executed", "class"),
                "the dead class is THE fact the tool exists to give");
        assertEquals(List.of("app.Engine"), values(facts, "class", "class"));
    }

    @Test
    @DisplayName("The coverage figures do not move by one decimal")
    void theCoverageNumbersDoNotMoveOneDecimal(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> facts = facts(assemble(dir));
        List<Map<String, Object>> coverages = new ArrayList<>();
        for (Map<String, Object> f : facts) {
            if ("coverage.run".equals(f.get("fact"))) coverages.add(f);
        }
        assertEquals(2, coverages.size());
        for (Map<String, Object> c : coverages) {
            assertEquals(9L, number(c.get("instructionsCovered")));
            assertEquals(22L, number(c.get("instructionsTotal")));
            assertEquals(40.9, ((Number) c.get("pct")).doubleValue(), 0.001,
                    "a percentage that slips under cover of a rename is the worst kind of "
                    + "defect: nobody notices it, and it makes one conclude");
        }
    }

    @Test
    @DisplayName("Assembling twice yields the same blocks, byte for byte")
    void rebuildingTwiceYieldsTheSameBlocks(@TempDir Path dir) throws Exception {
        Path a = assemble(dir.resolve("one"));
        Path b = assemble(dir.resolve("two"));

        // The blocks are pure derivations of the measurements: no clock, no absolute path.
        // A difference between two assemblies would be non-determinism there, and the report
        // would stop being comparable with itself from one week to the next.
        for (String block : EXPECTED_FILES) {
            if (!block.endsWith(".js")) continue;
            assertArrayEquals(Files.readAllBytes(a.resolve(block)),
                    Files.readAllBytes(b.resolve(block)), block + " differs from one assembly to "
                    + "the next although it derives only from the measurements");
        }
    }

    @Test
    @DisplayName("No command-line option disappears without being seen")
    void noCommandLineOptionQuietlyDisappears() throws Exception {
        // The French aliases must survive the switch, and an option removed inadvertently
        // is noticed only the day an acceptance script stops.
        String main = Files.readString(Path.of("").toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                StandardCharsets.UTF_8);
        List<String> views = new ArrayList<>();
        // Bounded to the options switch alone: further on, Main composes JaCoCo's command
        // lines, which have their own "--xml" and "--classfiles".
        int start = main.indexOf("switch (a) {");
        int end = main.indexOf("unknown option", start);
        assertTrue(start > 0 && end > start, "the options switch must stay locatable");
        Matcher m = Pattern.compile("\"(--[a-z-]+)\"").matcher(main.substring(start, end));
        while (m.find()) if (!views.contains(m.group(1))) views.add(m.group(1));
        Collections.sort(views);
        assertEquals(EXPECTED_OPTIONS, views);
    }

    // ------------------------------------------------------------------ fixtures

    private static Path assemble(Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        run(out.resolve("runs"), "run-a", "UUID-A");
        run(out.resolve("runs"), "run-b", "UUID-B");
        Dashboard.build(out, List.of(sources(dir)), 8);
        return out;
    }

    private static Path sources(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("app"));
        Files.writeString(src.resolve("app/Engine.java"), """
                package app;
                class Engine { int compute(int x) { return x * x; } }
                """, StandardCharsets.UTF_8);
        return src;
    }

    /** A run such as the orchestrator produces: one covered class, one dead. */
    private static void run(Path parent, String name, String uuid) throws IOException {
        Path run = parent.resolve(name);
        Files.createDirectories(run.resolve("jacoco/html"));
        Files.createDirectories(run.resolve("async-profiler"));
        Files.writeString(run.resolve("jacoco/html/jacoco.xml"), """
                <report name="t"><package name="app">
                <class name="app/Engine" sourcefilename="Engine.java">
                  <method name="compute" desc="(I)I" line="3">
                    <counter type="INSTRUCTION" missed="0" covered="9"/></method>
                  <counter type="INSTRUCTION" missed="1" covered="9"/></class>
                <class name="app/Never" sourcefilename="Never.java">
                  <counter type="INSTRUCTION" missed="12" covered="0"/></class>
                <sourcefile name="Engine.java"><line nr="3" mi="0" ci="9" mb="0" cb="0"/></sourcefile>
                </package></report>
                """, StandardCharsets.UTF_8);
        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Engine.compute 40\n", StandardCharsets.UTF_8);
        Files.writeString(run.resolve("jacoco/html/index.html"), "<html>c</html>",
                StandardCharsets.UTF_8);
        Files.writeString(run.resolve("run-context.json"), Json.write(new LinkedHashMap<>(Map.of(
                "uuid", uuid, "nomOrigine", name, "commande", "java -jar app.jar",
                "methodeRacine", "app.Engine::compute", "machine", "host"))),
                StandardCharsets.UTF_8);
    }

    private static List<String> files(Path out) throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> s = Files.walk(out)) {
            s.filter(Files::isRegularFile)
             .forEach(p -> names.add(out.relativize(p).toString().replace('\\', '/')));
        }
        Collections.sort(names);
        return names;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> facts(Path out) throws IOException {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (String l : Files.readAllLines(out.resolve("faits.jsonl"), StandardCharsets.UTF_8)) {
            if (!l.isBlank()) facts.add((Map<String, Object>) Json.read(l));
        }
        assertTrue(facts.size() > 5);
        return facts;
    }

    private static List<String> values(List<Map<String, Object>> facts, String family,
                                        String field) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> f : facts) {
            if (family.equals(f.get("fact"))) out.add(String.valueOf(f.get(field)));
        }
        Collections.sort(out);
        return out;
    }

    private static long number(Object o) {
        return ((Number) o).longValue();
    }
}
