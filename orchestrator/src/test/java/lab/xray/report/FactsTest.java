package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab.xray.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The facts file has only one reader in mind: a program. Three promises make it usable, and
 * it loses all its interest as soon as one is missing — hence one test per promise. It is
 * added to the report without taking anything from it: that is the fourth.
 */
class FactsTest {

    private static Map<String, Object> run(String uuid, boolean withProfile) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uuid", uuid);
        r.put("nom", "scénario " + uuid);
        r.put("chemin", "runs/" + uuid + "/");
        r.put("packages", Map.of("app", List.of(
                Map.of("name", "app/Moteur", "simple", "Moteur",
                        "source", "app/Moteur.java", "covered", 9, "missed", 1),
                Map.of("name", "app/Jamais", "simple", "Jamais",
                        "source", "app/Jamais.java", "covered", 0, "missed", 12))));
        r.put("methods", Map.of());
        r.put("coverage", Map.of("app/Moteur.java", Map.of()));
        if (withProfile) {
            r.put("calltree", Map.of("name", "tout", "total", 100, "children",
                    List.of(Map.of("name", "app/Moteur.calculer", "total", 60,
                            "children", List.of()))));
            r.put("values", Map.of("app/Moteur.calculer", List.of("un appel")));
        }
        return r;
    }

    private static List<Map<String, Object>> read(Path file) throws Exception {
        List<Map<String, Object>> facts = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            assertFalse(line.contains("\n"), "un fait doit tenir sur UNE ligne");
            @SuppressWarnings("unchecked")
            Map<String, Object> f = (Map<String, Object>) Json.read(line);
            facts.add(f);
        }
        return facts;
    }

    private static List<Map<String, Object>> de(List<Map<String, Object>> facts, String name) {
        return facts.stream().filter(f -> name.equals(f.get("fact"))).toList();
    }

    private static Path write(Path dir, List<Object> runs) throws Exception {
        return Facts.write(dir, runs, Sources.load(List.of()),
                Map.of("outil", "runtime-xray", "version", "essai", "date", "2026-08-27"));
    }

    @Test
    @DisplayName("One line, one fact, complete: ten lines taken from the middle make sense")
    void eachLineStandsAlone(@TempDir Path dir) throws Exception {
        Path f = write(dir, List.of(run("A", true), run("B", false)));
        List<Map<String, Object>> facts = read(f);

        assertTrue(facts.size() > 5, "we expect at least the header, the runs and the classes");
        for (Map<String, Object> fact : facts) {
            assertTrue(fact.containsKey("fact"), "chaque ligne dit ce qu'elle est : " + fact);
        }
        // A measurement without its unit forces the reader to guess, and they will.
        for (Map<String, Object> fact : de(facts, "class")) {
            assertEquals("jacoco.instructions", fact.get("measure"));
        }
        for (Map<String, Object> fact : de(facts, "method.hot")) {
            assertEquals("async-profiler.echantillons", fact.get("measure"));
        }
    }

    @Test
    @DisplayName("What was NOT measured is written, otherwise a zero reads as a fact")
    void whatWasNotMeasuredIsStated(@TempDir Path dir) throws Exception {
        // Run B has neither a profile nor values — the Windows case, where async-profiler
        // publishes nothing. Without these lines its "0 samples" reads exactly like "this
        // code never ran", and a reader will decide the wrong way.
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true), run("B", false))));

        List<Map<String, Object>> unavailabilities = de(facts, "unavailable");
        assertEquals(2, unavailabilities.size(), "time and values are both missing, and both must be said");
        for (Map<String, Object> a : unavailabilities) {
            assertEquals("B", a.get("run"), "only B is concerned: A measured everything");
            assertTrue(String.valueOf(a.get("why")).length() > 20);
            assertTrue(a.containsKey("consequence"), "dire ce qu'on ne peut PAS en conclure");
            assertTrue(a.containsKey("remedy"));
        }
    }

    @Test
    @DisplayName("A class never reached carries its own fact name, so it can be filtered")
    void aClassNeverReachedIsItsOwnFact(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true), run("B", true))));

        List<Map<String, Object>> never = de(facts, "class.never_executed");
        assertEquals(1, never.size());
        assertEquals("app.Jamais", never.get(0).get("class"));
        assertEquals(List.of(), never.get(0).get("runsCovering"));
        // Sorted: two campaigns over the same runs must give the same lines, otherwise
        // comparing two reports becomes impossible.
        assertEquals(List.of("A", "B"), never.get(0).get("runsAnalysed"));

        List<Map<String, Object>> covered = de(facts, "class");
        assertEquals(1, covered.size());
        assertEquals(List.of("A", "B"), covered.get(0).get("runsCovering"));
    }

    @Test
    @DisplayName("The file describes itself: its vocabulary is in its first line")
    void theFileExplainsItsOwnVocabulary(@TempDir Path dir) throws Exception {
        // Documentation placed beside gets lost — in a zip, in an attachment, in a copied
        // folder. This one travels with the data.
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true))));
        Map<String, Object> head = facts.get(0);
        assertEquals("campaign", head.get("fact"));

        @SuppressWarnings("unchecked")
        Map<String, Object> vocabulary = (Map<String, Object>) head.get("vocabulary");
        for (Map<String, Object> f : facts) {
            assertTrue(vocabulary.containsKey(String.valueOf(f.get("fact"))),
                    "un fait que le vocabulaire n'explique pas est muet : " + f.get("fact"));
        }
        assertTrue(head.get("alsoSee") instanceof Map, "where to find the page and the diagnostic");
    }

    @Test
    @DisplayName("Neither source code nor captured values: this file carries facts, not a copy")
    void itCarriesFactsNotAReplica(@TempDir Path dir) throws Exception {
        Map<String, Object> withSecret = run("A", true);
        withSecret.put("values", Map.of("app.Moteur.calculer",
                List.of("motDePasse=hunter2")));
        String text = Files.readString(write(dir, List.of(withSecret)), StandardCharsets.UTF_8);

        assertFalse(text.contains("hunter2"),
                "the captured values stay in their block: they are bulky, "
                + "et elles portent parfois ce qu'on ne veut pas voir voyager");
    }
}
