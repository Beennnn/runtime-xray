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
        r.put("nom", "essai");
        r.put("chemin", "runs/" + uuid + "/");
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

        Path bloc = dir.resolve("vue/sources/app.js");
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
        // Les blocs d'une exécution vivent SOUS elle : copier son répertoire emporte tout
        // ce qui la concerne, et en retirer une ne laisse pas d'orphelin ailleurs.
        assertEquals(List.of("runs/UUID-2/vue/couverture.js", "runs/UUID-2/vue/arbre.js",
                             "runs/UUID-2/vue/valeurs.js", "runs/UUID-2/vue/traces.js"),
                leger.get("blocs"), "et le reste s'annonce par ses blocs, sous l'exécution");

        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            assertFalse(leger.containsKey(lourd),
                    lourd + " ne s'affiche qu'après un geste : il n'a pas à être là avant");
        }
        for (String lourd : List.of("coverage", "calltree", "values", "trace")) {
            String nom = Map.of("coverage", "couverture.js", "calltree", "arbre.js",
                    "values", "valeurs.js", "trace", "traces.js").get(lourd);
            String bloc = Files.readString(dir.resolve("runs/UUID-2/vue/" + nom),
                    StandardCharsets.UTF_8);
            assertTrue(bloc.contains("\"UUID-2/" + lourd + "\""),
                    lourd + " doit être dans son bloc, sans quoi il serait perdu");
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
        Path sources = dir.resolve("vue/sources");
        try (var f = Files.list(sources)) {
            assertTrue(f.allMatch(p -> p.getParent().equals(sources)),
                    "tout doit rester sous vue/sources/");
        }
    }

    @Test
    @DisplayName("Une exécution sans profil garde un arbre vide, et non pas rien")
    void aRunWithoutAProfileStillCarriesAnEmptyTree() {
        // Sous Windows, async-profiler ne publie rien : ces exécutions n'ont pas d'arbre
        // d'appel, et Arthas peut n'avoir rien capturé non plus. Le découpage en blocs a
        // fait de l'absence un troisième état — ni « chargé », ni « pas encore chargé » —
        // que trente lecteurs de la page ne connaissaient pas. Trois d'entre eux ont
        // échoué en cascade, et l'onglet « Exécutions » ne s'affichait plus du tout.
        Map<String, Object> sansProfil = new LinkedHashMap<>(run("UUID-3"));
        sansProfil.remove("calltree");
        sansProfil.remove("values");

        Map<String, Object> leger = Blocs.alleger(sansProfil, "runs/UUID-3/vue/");

        assertEquals(Map.of("name", "tout", "total", 0, "children", List.of()),
                leger.get("calltree"), "l'arbre absent doit se lire comme un arbre vide");
        assertEquals(Map.of(), leger.get("values"));
        assertEquals(0L, leger.get("mesures"));
        assertFalse(String.valueOf(leger.get("blocs")).contains("arbre.js"),
                "pas de bloc pour un champ qui n'existe pas : on l'attendrait pour rien");
    }

    @Test
    @DisplayName("Le sommaire porte le nombre de mesures, que l'arbre soit chargé ou non")
    void theSummaryCarriesTheSampleCount() {
        // L'onglet « Exécutions » affiche ce nombre sur CHAQUE racine, donc sur des
        // exécutions dont l'arbre n'est pas chargé et ne le sera pas. Aller le chercher
        // rendrait au premier affichage tout ce que le chargement tardif économise.
        assertEquals(40L, Blocs.alleger(run("UUID-4"), "runs/UUID-4/vue/").get("mesures"));
    }
}
