package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
 * Ce format est celui que la forge affiche réellement — donc celui que quelqu'un lira sans
 * jamais ouvrir la page. Deux façons de le rater : produire un tableau cassé (une valeur
 * capturée peut contenir n'importe quoi, y compris un {@code |}), et annoncer un taux de
 * couverture sans dire de quoi il est le taux.
 */
class MarkdownTest {

    private static Map<String, Object> classe(String nom, int couvert, int manque) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nom);
        m.put("simple", nom.substring(nom.lastIndexOf('/') + 1));
        m.put("covered", couvert);
        m.put("missed", manque);
        return m;
    }

    private static Map<String, Object> noeud(String nom, long total, Object... enfants) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nom);
        m.put("total", total);
        m.put("children", List.of(enfants));
        return m;
    }

    private static Map<String, Object> run() {
        Map<String, Object> packages = new LinkedHashMap<>();
        packages.put("app/moteur", List.of(classe("app/moteur/Calcul", 90, 10)));
        packages.put("app/mort", List.of(classe("app/mort/JamaisAppele", 0, 40)));

        Map<String, Object> appel = new LinkedHashMap<>();
        appel.put("params", List.of(Map.of("type", "Trip", "value", "Trip[id=A|B, mode=CAR]")));
        appel.put("retour", Map.of("type", "Double", "value", "42.0"));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("nom", "Recette");
        run.put("context", new LinkedHashMap<>(Map.of("commande", "java -jar app.jar",
                "java", "Temurin 25")));
        run.put("packages", packages);
        run.put("calltree", noeud("tout", 100, noeud("app/moteur/Calcul.calculer", 80)));
        run.put("values", Map.of("app/moteur/Calcul.calculer", List.of(appel)));
        return run;
    }

    private static String write(Path dir) throws IOException {
        Markdown.write(dir, List.of(run()));
        return Files.readString(dir.resolve("rapport.md"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("Les trois observations ont chacune leur section")
    void hasOneSectionPerObservation(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("## Recette"), "l'exécution est nommée");
        assertTrue(md.contains("### Par où le code est passé"));
        assertTrue(md.contains("### Où le temps est passé"));
        assertTrue(md.contains("### Avec quelles valeurs"));
    }

    @Test
    @DisplayName("Le taux de couverture dit de quoi il est le taux")
    void qualifiesTheCoverageRatio(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("64 % des instructions analysées"),
                "90 couvertes sur 140 analysées : " + md.lines().filter(l -> l.contains("%"))
                        .findFirst().orElse("aucune ligne avec un %"));
        assertTrue(md.contains("dénominateur"),
                "sans quoi le taux se lit comme une note, alors qu'il dépend de ce qu'on "
                + "a donné à analyser");
    }

    @Test
    @DisplayName("Le code jamais exécuté est nommé, pas seulement compté")
    void namesDeadCode(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("app.mort.JamaisAppele"),
                "c'est précisément ce que la lecture du code ne révèle pas");
    }

    @Test
    @DisplayName("Une valeur contenant un « | » ne casse pas le tableau")
    void escapesPipesInValues(@TempDir Path dir) throws IOException {
        String md = write(dir);
        String ligne = md.lines().filter(l -> l.contains("Trip[id=")).findFirst().orElseThrow();
        // 4 séparateurs pour 3 colonnes : | # | paramètres | retour |
        assertEquals(4, ligne.chars().filter(c -> c == '|').count() - countEscaped(ligne),
                "colonne cassée par un séparateur non échappé : " + ligne);
        assertTrue(ligne.contains("\\|"), "le séparateur de la valeur doit être échappé");
    }

    private static long countEscaped(String s) {
        return s.split("\\\\\\|", -1).length - 1;
    }

    @Test
    @DisplayName("Le contexte de l'exécution accompagne le rapport")
    void carriesTheRunContext(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("java -jar app.jar"), "la commande lancée");
        assertTrue(md.contains("Temurin 25"), "la plateforme");
    }

    @Test
    @DisplayName("Sans donnée, une section est absente plutôt que vide")
    void skipsEmptySections(@TempDir Path dir) throws IOException {
        Map<String, Object> vide = new LinkedHashMap<>();
        vide.put("nom", "Rien");
        vide.put("calltree", noeud("tout", 0));
        Markdown.write(dir, List.of(vide));
        String md = Files.readString(dir.resolve("rapport.md"), StandardCharsets.UTF_8);
        assertFalse(md.contains("### Où le temps est passé"),
                "une section vide fait croire à une panne de l'outil");
        assertFalse(md.contains("### Avec quelles valeurs"));
    }
}
