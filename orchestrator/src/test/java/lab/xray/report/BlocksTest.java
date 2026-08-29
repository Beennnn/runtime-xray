package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blocks must keep three promises at once, and it is their conjunction that decides the
 * format: readable at the command line, loadable from {@code file://}, and releasable. One
 * test per promise — losing any one of them would make the format useless.
 */
class BlocksTest {

    private static Map<String, Object> run(String uuid) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uuid", uuid);
        r.put("nom", "essai");
        r.put("chemin", "runs/" + uuid + "/");
        r.put("nom", "scenario " + uuid);
        r.put("packages", Map.of("app", List.of(Map.of("name", "app/Moteur"))));
        r.put("coverage", Map.of("app/Moteur.java", Map.of("3", Map.of("s", "full"))));
        r.put("calltree", Map.of("name", "racine", "total", 40));
        r.put("values", Map.of("app/Moteur.calculer", List.of("un appel")));
        r.put("trace", Map.of("3", List.of("un appel")));
        return r;
    }

    @Test
    @DisplayName("One line per record: the blocks read at the command line")
    void oneRecordPerLineSoTheBlocksStayGreppable(@TempDir Path dir) throws Exception {
        Blocks.write(dir, List.of(run("UUID-1")),
                Map.of("app/Moteur.java", List.of("package app;", "class Moteur {}")));

        Path block = dir.resolve("vue/sources/app.js");
        List<String> lines = Files.readAllLines(block, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "une source, une ligne");
        assertTrue(lines.get(0).startsWith("XR.bloc(\"src\",\"app/Moteur.java\","),
                "the key must come first, so that a grep finds it: " + lines.get(0));
        assertTrue(lines.get(0).endsWith(");"), "and the line must close itself again");

        // Stripping the wrapper must give pure JSON — that is what gives jq for free.
        String json = lines.get(0).replaceFirst("^XR\\.bloc\\(", "[").replaceFirst("\\);$", "]");
        assertEquals(List.of("src", "app/Moteur.java", List.of("package app;", "class Moteur {}")),
                lab.xray.json.Json.read(json));
    }

    @Test
    @DisplayName("The summary keeps what shows at once, and sends the rest to the block")
    @SuppressWarnings("unchecked")
    void theSummaryKeepsOnlyWhatTheFirstPaintNeeds(@TempDir Path dir) throws Exception {
        Map<String, Object> summary = Blocks.write(dir, List.of(run("UUID-2")), Map.of());

        Map<String, Object> light = (Map<String, Object>) ((List<Object>) summary.get("runs")).get(0);
        assertTrue(light.containsKey("nom"), "the run's identity stays: it is displayed");
        assertTrue(light.containsKey("packages"), "the class tree too");
        // A run's blocks live UNDER it: copying its directory takes with it everything
        // that concerns it, and removing one leaves no orphan elsewhere.
        assertEquals(List.of("runs/UUID-2/vue/couverture.js", "runs/UUID-2/vue/arbre.js",
                             "runs/UUID-2/vue/valeurs.js", "runs/UUID-2/vue/traces.js"),
                light.get("blocs"), "and the rest announces itself through its blocks, under the run");

        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            assertFalse(light.containsKey(lourd),
                    lourd + " only shows after a gesture: it does not have to be there before");
        }
        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            String name = Map.of("coverage", "couverture.js", "calltree", "arbre.js",
                    "values", "valeurs.js", "trace", "traces.js").get(lourd);
            String block = Files.readString(dir.resolve("runs/UUID-2/vue/" + name),
                    StandardCharsets.UTF_8);
            assertTrue(block.contains("\"UUID-2/" + lourd + "\""),
                    lourd + " must be in its block, without which it would be lost");
        }
    }

    @Test
    @DisplayName("A package name cannot write outside the blocks directory")
    void aPackageNameCannotEscapeTheBlockDirectory(@TempDir Path dir) throws Exception {
        // Packages are well behaved, but an unexpected key must not be able to name
        // "../..": we filter, we do not substitute.
        assertEquals("a-b-c", Blocks.fileName("a/../b\\c"));
        assertEquals("divers", Blocks.fileName("../.."));
        assertEquals("com-example-app", Blocks.fileName("com/example/app"));

        Blocks.write(dir, List.of(), Map.of("../evade.java", List.of("x")));
        Path sources = dir.resolve("vue/sources");
        try (var f = Files.list(sources)) {
            assertTrue(f.allMatch(p -> p.getParent().equals(sources)),
                    "everything must stay under vue/sources/");
        }
    }

    @Test
    @DisplayName("A run without a profile keeps an empty tree, and not nothing")
    void aRunWithoutAProfileStillCarriesAnEmptyTree() {
        // On Windows async-profiler publishes nothing: those runs have no call tree, and
        // Arthas may have captured nothing either. Splitting into blocks turned absence
        // into a third state — neither "loaded" nor "not loaded yet" — which thirty readers
        // of the page did not know about. Three of them failed in cascade, and the "Runs"
        // tab stopped showing at all.
        Map<String, Object> withoutProfile = new LinkedHashMap<>(run("UUID-3"));
        withoutProfile.remove("calltree");
        withoutProfile.remove("values");

        Map<String, Object> light = Blocks.thin(withoutProfile, "runs/UUID-3/vue/");

        assertEquals(Map.of("name", "tout", "total", 0, "children", List.of()),
                light.get("calltree"), "an absent tree must read as an empty tree");
        assertEquals(Map.of(), light.get("values"));
        assertEquals(0L, light.get("mesures"));
        assertFalse(String.valueOf(light.get("blocs")).contains("arbre.js"),
                "no block for a field that does not exist: it would be awaited for nothing");
    }

    @Test
    @DisplayName("The summary carries the sample count, whether the tree is loaded or not")
    void theSummaryCarriesTheSampleCount() {
        // The "Runs" tab shows that number on EVERY root, so on runs whose tree is not
        // loaded and will not be. Going to fetch it would give back to the first paint
        // everything the lazy loading saves.
        assertEquals(40L, Blocks.thin(run("UUID-4"), "runs/UUID-4/vue/").get("mesures"));
    }
}
