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
 * Le fichier de faits n'a qu'un lecteur en tête : un programme. Trois promesses le rendent
 * utilisable, et il perd tout son intérêt dès qu'il en manque une — d'où un test par
 * promesse. Il s'ajoute au rapport sans rien lui retirer : c'est la quatrième.
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
    @DisplayName("Une ligne, un fait, complet : dix lignes prises au milieu se comprennent")
    void eachLineStandsAlone(@TempDir Path dir) throws Exception {
        Path f = write(dir, List.of(run("A", true), run("B", false)));
        List<Map<String, Object>> facts = read(f);

        assertTrue(facts.size() > 5, "on attend au moins l'en-tête, les exécutions et les classes");
        for (Map<String, Object> fact : facts) {
            assertTrue(fact.containsKey("fact"), "chaque ligne dit ce qu'elle est : " + fact);
        }
        // Une mesure sans son unité oblige le lecteur à deviner, et il devinera.
        for (Map<String, Object> fact : de(facts, "class")) {
            assertEquals("jacoco.instructions", fact.get("measure"));
        }
        for (Map<String, Object> fact : de(facts, "method.hot")) {
            assertEquals("async-profiler.echantillons", fact.get("measure"));
        }
    }

    @Test
    @DisplayName("Ce qui N'A PAS été mesuré est écrit, sinon un zéro se lit comme un fait")
    void whatWasNotMeasuredIsStated(@TempDir Path dir) throws Exception {
        // L'exécution B n'a ni profil ni valeurs — le cas Windows, où async-profiler ne
        // publie rien. Sans ces lignes, son « 0 relevé » se lit exactement comme « ce code
        // n'a jamais tourné », et un lecteur tranchera dans le mauvais sens.
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true), run("B", false))));

        List<Map<String, Object>> unavailabilities = de(facts, "unavailable");
        assertEquals(2, unavailabilities.size(), "le temps et les valeurs manquent, tous deux à dire");
        for (Map<String, Object> a : unavailabilities) {
            assertEquals("B", a.get("run"), "seule B est concernée : A a tout mesuré");
            assertTrue(String.valueOf(a.get("why")).length() > 20);
            assertTrue(a.containsKey("consequence"), "dire ce qu'on ne peut PAS en conclure");
            assertTrue(a.containsKey("remedy"));
        }
    }

    @Test
    @DisplayName("Une classe jamais atteinte porte son propre nom de fait, pour être filtrée")
    void aClassNeverReachedIsItsOwnFact(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true), run("B", true))));

        List<Map<String, Object>> never = de(facts, "class.never_executed");
        assertEquals(1, never.size());
        assertEquals("app.Jamais", never.get(0).get("class"));
        assertEquals(List.of(), never.get(0).get("runsCovering"));
        // Triées : deux campagnes des mêmes exécutions doivent donner les mêmes lignes,
        // sinon comparer deux rapports devient impossible.
        assertEquals(List.of("A", "B"), never.get(0).get("runsAnalysed"));

        List<Map<String, Object>> covered = de(facts, "class");
        assertEquals(1, covered.size());
        assertEquals(List.of("A", "B"), covered.get(0).get("runsCovering"));
    }

    @Test
    @DisplayName("Le fichier se décrit lui-même : son vocabulaire est dans sa première ligne")
    void theFileExplainsItsOwnVocabulary(@TempDir Path dir) throws Exception {
        // Une documentation posée à côté se perd — dans un zip, dans une pièce jointe, dans
        // un dossier recopié. Celle-ci voyage avec la donnée.
        List<Map<String, Object>> facts = read(write(dir, List.of(run("A", true))));
        Map<String, Object> head = facts.get(0);
        assertEquals("campaign", head.get("fact"));

        @SuppressWarnings("unchecked")
        Map<String, Object> vocabulary = (Map<String, Object>) head.get("vocabulary");
        for (Map<String, Object> f : facts) {
            assertTrue(vocabulary.containsKey(String.valueOf(f.get("fact"))),
                    "un fait que le vocabulaire n'explique pas est muet : " + f.get("fact"));
        }
        assertTrue(head.get("alsoSee") instanceof Map, "où trouver la page et le diagnostic");
    }

    @Test
    @DisplayName("Ni code source ni valeurs capturées : ce fichier porte des faits, pas une copie")
    void itCarriesFactsNotAReplica(@TempDir Path dir) throws Exception {
        Map<String, Object> withSecret = run("A", true);
        withSecret.put("values", Map.of("app.Moteur.calculer",
                List.of("motDePasse=hunter2")));
        String text = Files.readString(write(dir, List.of(withSecret)), StandardCharsets.UTF_8);

        assertFalse(text.contains("hunter2"),
                "les valeurs capturées restent dans leur bloc : elles sont volumineuses, "
                + "et elles portent parfois ce qu'on ne veut pas voir voyager");
    }
}
