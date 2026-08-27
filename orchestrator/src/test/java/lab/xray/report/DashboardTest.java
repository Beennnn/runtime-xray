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
    /** La fin de ligne est celle du gabarit tel qu'il a été extrait par git : \R les accepte
     *  toutes, pour que la lecture ne dépende pas du poste. */
    private static final Pattern DATA =
            Pattern.compile("const D = (\\{.*?\\});\\R", Pattern.DOTALL);

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
    @DisplayName("L'élagage enregistré voyage jusqu'à la page")
    void pruningTravelsToThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-E", true);
        Files.writeString(out.resolve("noms.json"), Json.write(Map.of(
                "UUID-E", Map.of("elagage", Map.of(
                        "racine", "app/Main.main",
                        "coupes", List.of("app/Main.main;app/Moteur.ecrire"))))),
                StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(sources(dir)), 8));
        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        Map<?, ?> elagage = (Map<?, ?>) run.get("elagage");
        assertNotNull(elagage, "sans lui, la page rouvrirait l'arbre entier à chaque lecture");
        assertEquals("app/Main.main", elagage.get("racine"));
        assertEquals(List.of("app/Main.main;app/Moteur.ecrire"), elagage.get("coupes"));

        // L'élagage cadre la lecture : la mesure, elle, reste entière. L'arbre vit sous
        // l'exécution, dans son bloc — la page ne le porte plus.
        String arbre = Files.readString(out.resolve(String.valueOf(run.get("chemin")))
                .resolve("vue/arbre.js"), StandardCharsets.UTF_8);
        assertTrue(arbre.contains("\"total\":40"), "le total mesuré ne bouge pas : " + arbre);
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

        // Le rapport est un dossier : la page porte le sommaire, les blocs portent le reste.
        // Ce qui compte est que RIEN ne manque — pas que tout soit au même endroit.
        assertTrue(((Map<?, ?>) data.get("sourcesDisponibles")).containsKey("app/Moteur.java"),
                "la page doit savoir que ce code existe, et où le prendre");
        assertTrue(Files.readString(out.resolve("vue/sources/app.js"), StandardCharsets.UTF_8)
                        .contains("class Moteur"),
                "et le code doit être dans le bloc, sinon le dossier est illisible");

        @SuppressWarnings("unchecked")
        Map<String, Object> run = (Map<String, Object>) ((List<Object>) data.get("runs")).get(0);
        assertNotNull(run.get("methods"), "l'arbre des classes s'affiche d'emblée : il reste");
        assertNotNull(run.get("context"));
        Path vue = out.resolve(String.valueOf(run.get("chemin"))).resolve("vue");
        for (String bloc : List.of("couverture.js", "arbre.js", "valeurs.js")) {
            assertTrue(Files.isRegularFile(vue.resolve(bloc)), bloc + " doit être sous l'exécution");
        }
        assertEquals("app/Moteur", run.get("tracedClass"),
                "la classe inspectée vient du contexte, pas d'une valeur codée en dur");
        assertTrue(Files.readString(vue.resolve("valeurs.js"), StandardCharsets.UTF_8)
                        .contains("app/Moteur.calculer"),
                "les valeurs relevées vivent dans le bloc de leur exécution");
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

    @Test
    @DisplayName("Les fichiers de l'exécution sont décrits à un seul endroit")
    void theFileTableIsNotDuplicated(@TempDir Path dir) throws Exception {
        // Le menu du haut et le bloc « sorties brutes » de la vue d'ensemble montrent la même
        // liste. Tant qu'ils la portaient chacun, ajouter un export à l'un l'oubliait dans
        // l'autre — c'est arrivé, perf a manqué au menu pendant que le bloc l'annonçait.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-F", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        for (String chemin : List.of("exports/profil.perf.txt", "exports/profil.cpuprofile",
                "exports/couverture.lcov", "exports/valeurs.json",
                "async-profiler/profil.collapsed", "execution.log")) {
            assertEquals(1, page.split(java.util.regex.Pattern.quote(chemin), -1).length - 1,
                    "« " + chemin + " » est écrit plus d'une fois : les deux listes vont diverger");
        }
        assertTrue(page.contains("id=\"dlmenu\""), "le menu des exports doit être là");
    }

    @Test
    @DisplayName("La page ne se donne jamais un ascenseur d'ensemble en plus de celui du rapport")
    void thePageNeverScrollsAsAWhole(@TempDir Path dir) throws Exception {
        // La mise en page retirait au corps une hauteur d'en-tête écrite en dur — 47 px —
        // alors que cet en-tête grandit avec ses liens, avec le zoom du navigateur et avec la
        // police du système : il en mesure 49,75 dans Chromium. Trois pixels suffisaient à
        // donner à la fenêtre entière un ascenseur qui venait se coller contre celui du
        // rapport, et c'est celui du rapport — le seul qui serve — qui s'en trouvait masqué.
        // Ce que ce test garde n'est pas la règle CSS : c'est le refus de deviner une hauteur
        // qu'on peut mesurer.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-H", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);
        String style = page.substring(page.indexOf("<style>"), page.indexOf("</style>"));

        assertFalse(style.contains("height:calc(100% - "),
                "une hauteur d'en-tête devinée revient : c'est ce qui faisait le second ascenseur");
        assertTrue(style.contains("body{display:flex;flex-direction:column;height:100%;overflow:hidden}"),
                "le corps occupe la fenêtre et ne défile pas : ce sont ses panneaux qui défilent");
        assertTrue(style.contains("flex:1 1 auto;min-height:0}"),
                "la zone de travail prend ce que l'en-tête laisse, quelle que soit sa hauteur");
    }

    @Test
    @DisplayName("La liste « Jamais exécuté » se replie, sans emporter le compte avec elle")
    void theNeverExecutedListCanBeFolded(@TempDir Path dir) throws Exception {
        // Sur un vrai projet cette liste tient des centaines de classes et pousse hors de
        // l'écran tout ce qui la suit — les méthodes coûteuses, les valeurs capturées, la
        // réserve — c'est-à-dire ce qu'on est venu chercher. Repliable, elle cesse de coûter
        // la page entière. Deux choses la rendent utilisable une fois repliée, et ce sont
        // elles que ce test garde : le compte reste affiché, et le choix est gardé.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-P", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("sectionPliable(\"dead\", \"Jamais exécuté\""),
                "la section « Jamais exécuté » doit être celle qu'on replie");
        assertTrue(page.contains("class=\"plicnt\""),
                "le compte doit rester lisible repliée, sinon replier fait perdre ce qui décide d'ouvrir");
        assertTrue(page.contains("aria-expanded=") && page.contains("aria-controls=\"pli-"),
                "un titre qui replie doit s'annoncer comme tel, y compris hors de la souris");
        assertTrue(page.contains("runtime-xray.plis"),
                "le choix de repli est un réglage de lecture : il est gardé dans le navigateur");
    }

    @Test
    @DisplayName("L'arbre ne prend pas Entrée ni Espace aux commandes de la page")
    void theTreeDoesNotStealActivationKeys(@TempDir Path dir) throws Exception {
        // Le parcours de l'arbre au clavier écoutait le document entier et retenait Entrée et
        // Espace partout hors des champs de saisie. Résultat : aucun bouton de la page ne
        // s'activait au clavier — ni l'aide, ni les exports, ni le repli des panneaux. Les
        // flèches, elles, doivent rester à l'arbre : on veut le parcourir sans y revenir à
        // la souris. C'est ce partage-là que le test garde.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-K", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("t.closest(\"button, a[href], select, summary\")"),
                "une commande qui a le focus doit garder Entrée et Espace pour elle");
        assertTrue(page.contains("\"ArrowDown\", \"ArrowUp\", \"ArrowRight\", \"ArrowLeft\""),
                "les flèches restent au parcours de l'arbre");
    }

    // ------------------------------------------------- ce qu'on sait quand ça n'a pas marché

    @Test
    @DisplayName("Une racine de sources d'un cran trop haute donne quand même le code")
    @SuppressWarnings("unchecked")
    void findsSourcesEvenWhenTheRootIsTooHigh(@TempDir Path dir) throws Exception {
        // Le défaut d'origine : la couverture indexe « app/Moteur.java », l'index indexait
        // « src/app/Moteur.java » dès que la racine passée était le projet et non le
        // répertoire de sources. Aucune correspondance, donc « Source indisponible »
        // partout — et rien pour le comprendre.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-S", true);
        sources(dir);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(dir), 8));

        Map<String, Object> src = (Map<String, Object>) data.get("sourcesDisponibles");
        assertTrue(src.containsKey("app/Moteur.java"),
                "la clé doit être celle de JaCoCo, pas le chemin depuis la racine : " + src.keySet());
    }

    @Test
    @DisplayName("Le diagnostic écrit à côté de la page dit ce qui a été cherché et trouvé")
    @SuppressWarnings("unchecked")
    void writesADiagnosticFileNextToThePage(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-D", true);

        // Volontairement une racine qui ne contient pas la source de la classe mesurée.
        Dashboard.build(out, List.of(dir.resolve("ailleurs")), 8);

        Path fichier = out.resolve("diagnostic.json");
        assertTrue(Files.isRegularFile(fichier), "le diagnostic doit exister sans qu'on le demande");
        Map<String, Object> d = (Map<String, Object>) Json.read(
                Files.readString(fichier, StandardCharsets.UTF_8));

        assertNotNull(d.get("machine"), "l'environnement explique la moitié des symptômes");
        assertNotNull(d.get("executions"), "les exécutions et leurs rapports sont dans le fichier");
        Map<String, Object> rap = (Map<String, Object>) d.get("rapprochement");
        assertEquals(1L, ((Number) rap.get("fichiersMesures")).longValue());
        assertEquals(1L, ((Number) rap.get("fichiersSansSource")).longValue());
        assertNotNull(rap.get("conclusion"), "une phrase qu'on lit en premier");
        List<Object> manquants = (List<Object>) rap.get("exemplesManquants");
        assertEquals("app/Moteur.java", ((Map<String, Object>) manquants.get(0)).get("cherche"),
                "le fichier doit nommer la clé exacte que la couverture réclamait");

        Map<String, Object> sources = (Map<String, Object>) d.get("sources");
        List<Object> racines = (List<Object>) sources.get("racines");
        assertEquals(Boolean.FALSE, ((Map<String, Object>) racines.get(0)).get("existe"),
                "une racine inexistante doit se voir, pas disparaître");
    }

    @Test
    @DisplayName("Le diagnostic ne contient aucun secret de serveur")
    void theDiagnosticCarriesNoSharedSecret(@TempDir Path dir) throws Exception {
        // Ce fichier est fait pour être transmis. Un jeton qui s'y glisserait circulerait
        // avec lui, et rien dans la page ne le dirait.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-T", true);
        Dashboard.build(out, List.of(sources(dir)), 8);

        String texte = Files.readString(out.resolve("diagnostic.json"), StandardCharsets.UTF_8);
        assertFalse(texte.contains("serve-token") || texte.contains("secret"),
                "aucun secret ne doit voyager avec le diagnostic");
    }

    @Test
    @DisplayName("L'explorateur dit où est chaque classe, et ce que chaque réglage commande")
    @SuppressWarnings("unchecked")
    void explainsWhereEachClassWasFound(@TempDir Path dir) throws Exception {
        // Trois choses peuvent manquer — le bytecode, la source, la classe — et un rapport
        // vide leur donne le même visage. L'explorateur les sépare ; le tableau des réglages
        // dit lequel élargir, parce qu'on soupçonne presque toujours le mauvais.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-X", true);
        Path classes = dir.resolve("classes/app");
        Files.createDirectories(classes);
        Files.writeString(classes.resolve("Moteur.class"), "faux bytecode", StandardCharsets.UTF_8);

        Map<String, Object> lancement = new java.util.LinkedHashMap<>();
        lancement.put("commande", "java -jar app.jar");
        lancement.put("methodeRacine", "app.Moteur");
        lancement.put("racinesClasses", List.of(Map.of(
                "chemin", "classes", "absolu", dir.resolve("classes").toString(), "existe", true)));

        Path page = Dashboard.build(out, List.of(sources(dir)), 8, PackageFilter.NONE, lancement);
        Map<String, Object> data = dataOf(page);
        Map<String, Object> d = (Map<String, Object>) data.get("diagnostic");

        List<Object> bytecode = (List<Object>) d.get("bytecode");
        assertEquals(1, bytecode.size(), "la racine de bytecode doit être recensée");
        Map<String, Object> racine = (Map<String, Object>) bytecode.get(0);
        assertEquals(List.of("app/Moteur"), racine.get("classes"),
                "les classes trouvées sont ce qui permet de répondre « où est-elle ? »");

        String html = Files.readString(page, StandardCharsets.UTF_8);
        assertTrue(html.contains("function verdictRecherche("),
                "la recherche doit dire si une classe a été trouvée, et où");
        assertTrue(html.contains("function arbreSources("),
                "l'arbre doit exister, coloré par ce qui manque à chaque classe");
        assertTrue(html.contains("function piegesConfig("),
                "le tableau des réglages est ce qui évite d'élargir le mauvais filtre");
        assertTrue(html.contains("ne restreint NI la couverture, NI les classes affichées"),
                "--root ne doit plus pouvoir être soupçonné d'un rapport sans sources");
    }

    @Test
    @DisplayName("Le diagnostic se lit sous « sources », et la page le lit au bon niveau")
    void thePageReadsTheDiagnosticAtTheRightDepth(@TempDir Path dir) throws Exception {
        // Lu un cran trop haut, le diagnostic annonçait « 0 racine » alors que tout avait
        // été trouvé — et accusait la configuration de l'utilisateur.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-N", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("function diagSources(){ return (D.diagnostic || {}).sources || {}; }"),
                "un seul accès nommé, pour que les trois inventaires ne se confondent pas");
        assertFalse(page.contains("(D.diagnostic || {}).racines"),
                "les racines ne vivent pas à la racine du diagnostic");
    }

    @Test
    @DisplayName("Seules les sources affichables voyagent, mais le diagnostic les connaît toutes")
    @SuppressWarnings("unchecked")
    void carriesOnlyTheSourcesThePageCanShow(@TempDir Path dir) throws Exception {
        // Une source qu'aucune classe mesurée ne réclame n'a aucun chemin d'affichage : rien
        // dans la page n'y mène. L'embarquer, c'est recopier l'arborescence du projet — un
        // rapport de 217 Mo que Firefox a renoncé à ouvrir, le 26 août 2026.
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "essai", "UUID-P", true);
        Path src = sources(dir);
        Files.writeString(src.resolve("app/JamaisMesuree.java"), """
                package app;
                class JamaisMesuree { int x() { return 1; } }
                """, StandardCharsets.UTF_8);

        Map<String, Object> data = dataOf(Dashboard.build(out, List.of(src), 8));

        Map<String, Object> embarquees = (Map<String, Object>) data.get("sourcesDisponibles");
        assertTrue(embarquees.containsKey("app/Moteur.java"), "la classe mesurée garde son code");
        assertFalse(embarquees.containsKey("app/JamaisMesuree.java"),
                "une source sans classe mesurée ne peut pas s'afficher, donc ne voyage pas");
        assertFalse(Files.readString(out.resolve("vue/sources/app.js"), StandardCharsets.UTF_8)
                        .contains("JamaisMesuree"),
                "et elle ne doit pas non plus peser dans le bloc");

        // Mais rien de fonctionnel ne part : le diagnostic sait toujours qu'elle a été lue,
        // et l'arbre « Où est chaque classe » continue de la situer.
        Map<String, Object> d = (Map<String, Object>) data.get("diagnostic");
        Map<String, Object> parNom =
                (Map<String, Object>) ((Map<String, Object>) d.get("sources")).get("parNom");
        assertTrue(parNom.containsKey("JamaisMesuree.java"),
                "le diagnostic doit continuer de dire où chaque fichier a été lu");
        assertEquals(1L, ((Number) ((Map<String, Object>) d.get("rapprochement"))
                .get("sourcesSansClasse")).longValue(),
                "et compter celles qui ne correspondent à aucune classe mesurée");
    }

    @Test
    @DisplayName("La page sait cumuler la couverture des exécutions cochées, et dire laquelle")
    void thePageCanUnifyCoverageAcrossRuns(@TempDir Path dir) throws Exception {
        Path out = dir.resolve("out");
        Files.createDirectories(out);
        fixtureRun(out.resolve("runs"), "un", "UUID-C1", true);
        fixtureRun(out.resolve("runs"), "deux", "UUID-C2", true);
        String page = Files.readString(Dashboard.build(out, List.of(sources(dir)), 8),
                StandardCharsets.UTF_8);

        assertTrue(page.contains("function covPourVue("),
                "la vue de code doit pouvoir lire une couverture réunie");
        assertTrue(page.contains("function pastillesRuns("),
                "réunir sans dire QUI a couvert ferait perdre l'information que la fusion "
                + "JaCoCo perd déjà");
        assertTrue(page.contains("runtime-xray.cumul"),
                "le choix de cumuler est un réglage de lecture : il est gardé");
    }

    @Test
    @DisplayName("La colonne du code a le droit d'être plus étroite que la ligne la plus longue")
    void theCodeColumnMayBeNarrowerThanItsLongestLine() throws Exception {
        // Le minimum implicite d'une colonne de grille est le min-content de son contenu.
        // Avec « 1fr », une ligne de 450 caractères — il en existe dans du code d'entreprise —
        // élargissait la colonne à 3680 px dans une fenêtre de 1280 : le code ne défilait pas
        // horizontalement, et son ascenseur vertical partait à 4016 px, hors de l'écran. On
        // ne pouvait ni descendre dans le fichier, ni voir la fin de la ligne.
        String gabarit = new String(Objects.requireNonNull(
                Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")).readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(gabarit.contains("minmax(0, 1fr)"),
                "sans le zéro, la colonne du code s'élargit au lieu de défiler");
        assertFalse(gabarit.contains("grid-template-columns:var(--navw,330px) 6px 1fr"),
                "c'est exactement la forme qui avait poussé l'ascenseur hors de l'écran");
    }
}
