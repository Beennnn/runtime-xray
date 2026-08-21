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
 * La page assemblée est le livrable : elle est envoyée telle quelle, ouverte hors ligne,
 * parfois des mois plus tard. Deux défauts y seraient invisibles à la génération et fatals
 * à la lecture — un lien qui ne mène nulle part, et des données mal échappées qui empêchent
 * la page de s'afficher. C'est ce que vérifient ces tests.
 */
class DashboardTest {

    private static final Pattern HREF = Pattern.compile("(?:href|src)=\"([^\"]+)\"");
    /** Le balisage seul : le script embarqué contient des fragments de chaînes qui
     *  ressemblent à des liens sans en être. */
    private static final Pattern SCRIPT =
            Pattern.compile("<script\\b.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA = Pattern.compile("const D = (\\{.*?\\});\\n", Pattern.DOTALL);

    // ------------------------------------------------------------------ fixtures

    /** Une exécution complète, telle que l'orchestrateur en produit une. */
    private Path fixtureRun(Path parent, String name, String uuid, boolean withReports)
            throws IOException {
        Path run = parent.resolve(name);
        Files.createDirectories(run.resolve("jacoco/html"));
        Files.createDirectories(run.resolve("async-profiler"));
        Files.createDirectories(run.resolve("arthas"));

        Files.writeString(run.resolve("jacoco/html/jacoco.xml"), """
                <report name="t">
                  <package name="app">
                    <class name="app/Moteur" sourcefilename="Moteur.java">
                      <method name="calculer" desc="(I)I" line="3">
                        <counter type="INSTRUCTION" missed="0" covered="9"/>
                      </method>
                      <counter type="INSTRUCTION" missed="1" covered="9"/>
                    </class>
                    <sourcefile name="Moteur.java">
                      <line nr="3" mi="0" ci="9" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                </report>
                """, StandardCharsets.UTF_8);

        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Moteur.calculer 40\n", StandardCharsets.UTF_8);

        Files.writeString(run.resolve("arthas/watch-params.txt"), """
                method=app.Moteur.calculer location=AtExit
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
                "methodeRacine", "app.Moteur::calculer",
                "machine", "poste-de-test")), StandardCharsets.UTF_8);

        if (withReports) {
            Files.writeString(run.resolve("jacoco/html/index.html"), "<html>couverture</html>",
                    StandardCharsets.UTF_8);
            Files.createDirectories(run.resolve("jacoco-focused/html"));
            Files.writeString(run.resolve("jacoco-focused/html/index.html"), "<html>ciblee</html>",
                    StandardCharsets.UTF_8);
        }
        return run;
    }

    private Path sources(Path dir) throws IOException {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("app"));
        Files.writeString(src.resolve("app/Moteur.java"), """
                package app;
                class Moteur {
                    int calculer(int x) { return x * x; }
                }
                """, StandardCharsets.UTF_8);
        return src;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataOf(Path page) throws IOException {
        String html = Files.readString(page, StandardCharsets.UTF_8);
        Matcher m = DATA.matcher(html);
        assertTrue(m.find(), "les données doivent être embarquées dans la page");
        return (Map<String, Object>) Json.read(m.group(1));
    }

    // ------------------------------------------------------------------- tests

    @Test
    @DisplayName("Une exécution produit une page complète, sans marqueur de gabarit résiduel")
    void buildsCompletePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-1", true);

        Path page = Dashboard.build(out, List.of(sources(dir)), 8);
        String html = Files.readString(page, StandardCharsets.UTF_8);

        assertEquals(out.resolve("index.html"), page, "la page vit à la racine du répertoire");
        assertFalse(html.contains("/*__DATA__*/"), "le marqueur du gabarit doit être remplacé");
        assertTrue(html.contains("<title>"), "la page doit être complète");
    }

    @Test
    @DisplayName("AUCUN lien de la page ne doit être cassé")
    void producesNoBrokenLinks(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        // Volontairement SANS les rapports détaillés : les liens correspondants ne doivent
        // alors pas être écrits du tout, plutôt que de pointer dans le vide.
        fixtureRun(out.resolve("runs"), "sans-rapports", "UUID-2", false);

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
        assertTrue(broken.isEmpty(), "liens cassés dans la page produite : " + broken);
    }

    @Test
    @DisplayName("Les liens vers les rapports présents, eux, sont bien écrits et résolvent")
    void writesLinksForExistingReports(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "avec-rapports", "UUID-3", true);

        Path page = Dashboard.build(out, List.of(sources(dir)), 8);
        Map<String, Object> data = dataOf(page);
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> reports = (Map<String, Object>) first.get("rapports");

        assertEquals(Boolean.TRUE, reports.get("couverture"));
        assertEquals(Boolean.TRUE, reports.get("ciblee"));
        assertEquals(Boolean.FALSE, reports.get("flamegraph"), "ce rapport n'existe pas ici");

        String base = String.valueOf(first.get("chemin"));
        assertTrue(Files.exists(out.resolve(base + "jacoco/html/index.html")),
                "le chemin annoncé doit mener au rapport : " + base);
        assertTrue(run.toString().endsWith("avec-rapports"));
    }

    @Test
    @DisplayName("Le chemin de chaque exécution est relatif à la page, jamais absolu")
    void runPathsAreRelative(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "relatif", "UUID-4", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        String chemin = String.valueOf(((Map<String, Object>)
                ((List<Object>) data.get("runs")).get(0)).get("chemin"));
        assertFalse(chemin.startsWith("/"), "un chemin absolu casserait la page une fois déplacée");
        assertTrue(chemin.endsWith("/"), "le chemin doit être un préfixe utilisable tel quel");
        assertEquals("runs/relatif/", chemin);
    }

    @Test
    @DisplayName("Plusieurs exécutions sont réunies dans une seule page")
    void gathersSeveralRuns(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "premiere", "UUID-A", true);
        fixtureRun(out.resolve("runs"), "seconde", "UUID-B", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        assertEquals(2, ((List<?>) data.get("runs")).size());
    }

    @Test
    @DisplayName("Des exécutions rassemblées depuis plusieurs répertoires sont toutes trouvées")
    void findsRunsAcrossDirectories(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("commun");
        Files.createDirectories(out);
        fixtureRun(out.resolve("machine-a/runs"), "run-a", "UUID-A", true);
        fixtureRun(out.resolve("machine-b/runs"), "run-b", "UUID-B", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        assertEquals(2, ((List<?>) data.get("runs")).size(),
                "rassembler des répertoires de sortie doit suffire");
    }

    @Test
    @DisplayName("Le renommage après coup remplace le nom sans effacer l'origine")
    void lateRenamingKeepsOrigin(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "nom-technique", "UUID-R", true);
        Files.writeString(out.resolve("noms.json"),
                Json.write(Map.of("UUID-R", "Recette — cas nominal")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("Recette — cas nominal", run.get("nom"));
        assertEquals("nom-technique", run.get("nomOrigine"), "l'origine permet le retour arrière");
        assertEquals(Boolean.TRUE, run.get("renomme"));
    }

    @Test
    @DisplayName("Une annotation complète porte le nom, la description et les étiquettes")
    void richAnnotationIsRead(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "nom-technique", "UUID-A", true);
        Files.writeString(out.resolve("noms.json"), Json.write(Map.of(
                "UUID-A", Map.of(
                        "nom", "Recette du 21/08",
                        "description", "Vérifier la branche météo",
                        "etiquettes", Map.of("ticket", "RX-142", "à-rejouer", "")))),
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("Recette du 21/08", run.get("nom"));
        assertEquals("nom-technique", run.get("nomOrigine"), "l'origine reste consultable");
        assertEquals("Vérifier la branche météo", run.get("description"));
        assertEquals("RX-142", ((Map<?, ?>) run.get("etiquettes")).get("ticket"));
        assertEquals("", ((Map<?, ?>) run.get("etiquettes")).get("à-rejouer"),
                "une étiquette peut n'être qu'une clé");
    }

    @Test
    @DisplayName("Sans nom au lancement, c'est l'identifiant qui désigne l'exécution")
    void identifierStandsInForAMissingName(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "20260821-004121", "0123456789abcdef", true);
        // Une exécution lancée sans --name : le contexte ne porte aucun nom.
        Files.writeString(run.resolve("run-context.json"), Json.write(Map.of(
                "uuid", "0123456789abcdef",
                "commande", "java -jar app.jar")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertNull(first.get("nomOrigine"), "aucun nom n'a été donné : rien n'est inventé");
        assertEquals("01234567", first.get("nom"), "l'identifiant abrégé tient lieu de nom");
    }

    @Test
    @DisplayName("Un fichier de renommage illisible n'empêche pas de produire la page")
    void brokenRenameFileIsTolerated(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-X", true);
        Files.writeString(out.resolve("noms.json"), "{ ceci n'est pas du JSON",
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("essai", run.get("nom"), "on retombe sur le nom d'origine");
    }

    @Test
    @DisplayName("La page embarque tout : sources, couverture, arbre, valeurs, contexte")
    void embedsEverythingNeeded(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "complet", "UUID-C", true);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        assertTrue(((Map<?, ?>) data.get("sources")).containsKey("app/Moteur.java"),
                "le code doit voyager avec la page, sinon elle est illisible hors du projet");

        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertNotNull(run.get("coverage"));
        assertNotNull(run.get("methods"));
        assertNotNull(run.get("calltree"));
        assertNotNull(run.get("context"));
        assertEquals("app/Moteur", run.get("tracedClass"),
                "la classe inspectée vient du contexte, pas d'une valeur codée en dur");
        assertTrue(((Map<?, ?>) run.get("values")).containsKey("app/Moteur.calculer"));
    }

    @Test
    @DisplayName("Sans contexte, la classe inspectée se déduit des valeurs capturées")
    void tracedClassFallsBackToCapturedValues(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "sans-contexte", "UUID-D", true);
        Files.delete(run.resolve("run-context.json"));

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("app/Moteur", first.get("tracedClass"));
        assertEquals("sans-contexte", first.get("nom"), "à défaut, le nom du répertoire");
    }

    @Test
    @DisplayName("Un répertoire sans aucune exécution donne une erreur explicite")
    void emptyDirectoryFailsClearly(@TempDir Path dir) {
        Path out = dir.resolve("vide");
        Exception e = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> Dashboard.build(out, List.of(), 8));
        assertTrue(e.getMessage().contains("aucune exécution"), e.getMessage());
    }

    @Test
    @DisplayName("Un nom d'exécution contenant des guillemets ne casse pas la page")
    void quotesInRunNameDoNotBreakThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        Path run = fixtureRun(out.resolve("runs"), "piege", "UUID-Q", true);
        Files.writeString(run.resolve("run-context.json"), Json.write(Map.of(
                "uuid", "UUID-Q",
                "nomOrigine", "essai \"guillemets\" et </script> et \\ antislash",
                "methodeRacine", "app.Moteur::calculer")), StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertEquals("essai \"guillemets\" et </script> et \\ antislash", first.get("nom"));
    }
}
