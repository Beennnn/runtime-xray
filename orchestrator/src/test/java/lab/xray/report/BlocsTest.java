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
 * Les blocs doivent tenir trois promesses à la fois, et c'est leur conjonction qui décide du
 * format : lisibles à la ligne de commande, chargeables depuis {@code file://}, et libérables.
 * Un test par promesse — perdre l'une d'elles rendrait le format inutile.
 */
class BlocsTest {

    private static Map<String, Object> run(String uuid) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("uuid", uuid);
        r.put("nom", "scénario " + uuid);
        r.put("packages", Map.of("app", List.of(Map.of("name", "app/Moteur"))));
        r.put("coverage", Map.of("app/Moteur.java", Map.of("3", Map.of("s", "full"))));
        r.put("calltree", Map.of("name", "racine", "total", 40));
        r.put("values", Map.of("app/Moteur.calculer", List.of("un appel")));
        r.put("trace", Map.of("3", List.of("un appel")));
        return r;
    }

    @Test
    @DisplayName("Une ligne par enregistrement : les blocs se lisent à la ligne de commande")
    void oneRecordPerLineSoTheBlocksStayGreppable(@TempDir Path dir) throws Exception {
        Blocs.ecrire(dir, List.of(run("UUID-1")),
                Map.of("app/Moteur.java", List.of("package app;", "class Moteur {}")));

        Path bloc = dir.resolve("donnees/src-app.js");
        List<String> lignes = Files.readAllLines(bloc, StandardCharsets.UTF_8);
        assertEquals(1, lignes.size(), "une source, une ligne");
        assertTrue(lignes.get(0).startsWith("XR.bloc(\"src\",\"app/Moteur.java\","),
                "la clé doit être en tête, pour qu'un grep la trouve : " + lignes.get(0));
        assertTrue(lignes.get(0).endsWith(");"), "et la ligne doit se refermer");

        // Retirer l'enrobage doit rendre du JSON pur — c'est ce qui donne jq gratuitement.
        String json = lignes.get(0).replaceFirst("^XR\\.bloc\\(", "[").replaceFirst("\\);$", "]");
        assertEquals(List.of("src", "app/Moteur.java", List.of("package app;", "class Moteur {}")),
                lab.xray.json.Json.read(json));
    }

    @Test
    @DisplayName("Le sommaire garde ce qui s'affiche d'emblée, et renvoie le reste au bloc")
    @SuppressWarnings("unchecked")
    void theSummaryKeepsOnlyWhatTheFirstPaintNeeds(@TempDir Path dir) throws Exception {
        Map<String, Object> sommaire = Blocs.ecrire(dir, List.of(run("UUID-2")), Map.of());

        Map<String, Object> leger = (Map<String, Object>) ((List<Object>) sommaire.get("runs")).get(0);
        assertTrue(leger.containsKey("nom"), "l'identité de l'exécution reste : elle s'affiche");
        assertTrue(leger.containsKey("packages"), "l'arbre des classes aussi");
        assertEquals("donnees/run-1.js", leger.get("bloc"), "et le reste s'annonce par son bloc");

        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            assertFalse(leger.containsKey(lourd),
                    lourd + " ne s'affiche qu'après un geste : il n'a pas à être là avant");
        }
        String bloc = Files.readString(dir.resolve("donnees/run-1.js"), StandardCharsets.UTF_8);
        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            assertTrue(bloc.contains("\"UUID-2/" + lourd + "\""),
                    lourd + " doit être dans le bloc, sans quoi il serait perdu");
        }
    }

    @Test
    @DisplayName("Un nom de paquet ne peut pas écrire hors du répertoire des blocs")
    void aPackageNameCannotEscapeTheBlockDirectory(@TempDir Path dir) throws Exception {
        // Les paquets sont sages, mais une clé inattendue ne doit pas pouvoir désigner
        // « ../.. » : on filtre, on ne substitue pas.
        assertEquals("a-b-c", Blocs.nomDeFichier("a/../b\\c"));
        assertEquals("divers", Blocs.nomDeFichier("../.."));
        assertEquals("coo-jocc-mod", Blocs.nomDeFichier("coo/jocc/mod"));

        Blocs.ecrire(dir, List.of(), Map.of("../evade.java", List.of("x")));
        try (var f = Files.list(dir.resolve("donnees"))) {
            assertTrue(f.allMatch(p -> p.getParent().equals(dir.resolve("donnees"))),
                    "tout doit rester sous donnees/");
        }
    }
}
