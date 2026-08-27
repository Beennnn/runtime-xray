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
class FaitsTest {

    private static Map<String, Object> run(String uuid, boolean avecProfil) {
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
        if (avecProfil) {
            r.put("calltree", Map.of("name", "tout", "total", 100, "children",
                    List.of(Map.of("name", "app/Moteur.calculer", "total", 60,
                            "children", List.of()))));
            r.put("values", Map.of("app/Moteur.calculer", List.of("un appel")));
        }
        return r;
    }

    private static List<Map<String, Object>> lire(Path fichier) throws Exception {
        List<Map<String, Object>> faits = new ArrayList<>();
        for (String ligne : Files.readAllLines(fichier, StandardCharsets.UTF_8)) {
            if (ligne.isBlank()) continue;
            assertFalse(ligne.contains("\n"), "un fait doit tenir sur UNE ligne");
            @SuppressWarnings("unchecked")
            Map<String, Object> f = (Map<String, Object>) Json.read(ligne);
            faits.add(f);
        }
        return faits;
    }

    private static List<Map<String, Object>> de(List<Map<String, Object>> faits, String nom) {
        return faits.stream().filter(f -> nom.equals(f.get("fait"))).toList();
    }

    private static Path ecrire(Path dir, List<Object> runs) throws Exception {
        return Faits.ecrire(dir, runs, Sources.load(List.of()),
                Map.of("outil", "runtime-xray", "version", "essai", "date", "2026-08-27"));
    }

    @Test
    @DisplayName("Une ligne, un fait, complet : dix lignes prises au milieu se comprennent")
    void eachLineStandsAlone(@TempDir Path dir) throws Exception {
        Path f = ecrire(dir, List.of(run("A", true), run("B", false)));
        List<Map<String, Object>> faits = lire(f);

        assertTrue(faits.size() > 5, "on attend au moins l'en-tête, les exécutions et les classes");
        for (Map<String, Object> fait : faits) {
            assertTrue(fait.containsKey("fait"), "chaque ligne dit ce qu'elle est : " + fait);
        }
        // Une mesure sans son unité oblige le lecteur à deviner, et il devinera.
        for (Map<String, Object> fait : de(faits, "classe")) {
            assertEquals("jacoco.instructions", fait.get("mesure"));
        }
        for (Map<String, Object> fait : de(faits, "methode.chaude")) {
            assertEquals("async-profiler.echantillons", fait.get("mesure"));
        }
    }

    @Test
    @DisplayName("Ce qui N'A PAS été mesuré est écrit, sinon un zéro se lit comme un fait")
    void whatWasNotMeasuredIsStated(@TempDir Path dir) throws Exception {
        // L'exécution B n'a ni profil ni valeurs — le cas Windows, où async-profiler ne
        // publie rien. Sans ces lignes, son « 0 relevé » se lit exactement comme « ce code
        // n'a jamais tourné », et un lecteur tranchera dans le mauvais sens.
        List<Map<String, Object>> faits = lire(ecrire(dir, List.of(run("A", true), run("B", false))));

        List<Map<String, Object>> absences = de(faits, "indisponibilite");
        assertEquals(2, absences.size(), "le temps et les valeurs manquent, tous deux à dire");
        for (Map<String, Object> a : absences) {
            assertEquals("B", a.get("execution"), "seule B est concernée : A a tout mesuré");
            assertTrue(String.valueOf(a.get("pourquoi")).length() > 20);
            assertTrue(a.containsKey("consequence"), "dire ce qu'on ne peut PAS en conclure");
            assertTrue(a.containsKey("remede"));
        }
    }

    @Test
    @DisplayName("Une classe jamais atteinte porte son propre nom de fait, pour être filtrée")
    void aClassNeverReachedIsItsOwnFact(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> faits = lire(ecrire(dir, List.of(run("A", true), run("B", true))));

        List<Map<String, Object>> jamais = de(faits, "classe.jamais_executee");
        assertEquals(1, jamais.size());
        assertEquals("app.Jamais", jamais.get(0).get("classe"));
        assertEquals(List.of(), jamais.get(0).get("executionsCouvrantes"));
        // Triées : deux campagnes des mêmes exécutions doivent donner les mêmes lignes,
        // sinon comparer deux rapports devient impossible.
        assertEquals(List.of("A", "B"), jamais.get(0).get("executionsAnalysee"));

        List<Map<String, Object>> couvertes = de(faits, "classe");
        assertEquals(1, couvertes.size());
        assertEquals(List.of("A", "B"), couvertes.get(0).get("executionsCouvrantes"));
    }

    @Test
    @DisplayName("Le fichier se décrit lui-même : son vocabulaire est dans sa première ligne")
    void theFileExplainsItsOwnVocabulary(@TempDir Path dir) throws Exception {
        // Une documentation posée à côté se perd — dans un zip, dans une pièce jointe, dans
        // un dossier recopié. Celle-ci voyage avec la donnée.
        List<Map<String, Object>> faits = lire(ecrire(dir, List.of(run("A", true))));
        Map<String, Object> tete = faits.get(0);
        assertEquals("campagne", tete.get("fait"));

        @SuppressWarnings("unchecked")
        Map<String, Object> vocabulaire = (Map<String, Object>) tete.get("vocabulaire");
        for (Map<String, Object> f : faits) {
            assertTrue(vocabulaire.containsKey(String.valueOf(f.get("fait"))),
                    "un fait que le vocabulaire n'explique pas est muet : " + f.get("fait"));
        }
        assertTrue(tete.get("aussi") instanceof Map, "où trouver la page et le diagnostic");
    }

    @Test
    @DisplayName("Ni code source ni valeurs capturées : ce fichier porte des faits, pas une copie")
    void itCarriesFactsNotAReplica(@TempDir Path dir) throws Exception {
        Map<String, Object> avecSecret = run("A", true);
        avecSecret.put("values", Map.of("app.Moteur.calculer",
                List.of("motDePasse=hunter2")));
        String texte = Files.readString(ecrire(dir, List.of(avecSecret)), StandardCharsets.UTF_8);

        assertFalse(texte.contains("hunter2"),
                "les valeurs capturées restent dans leur bloc : elles sont volumineuses, "
                + "et elles portent parfois ce qu'on ne veut pas voir voyager");
    }
}
