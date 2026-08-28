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
 * Ce que l'outil produit aujourd'hui, épinglé tel quel — avant de le déplacer.
 *
 * <h2>Pourquoi ces tests-là, et pourquoi maintenant</h2>
 *
 * <p>Les autres tests de ce dépôt gardent des <b>décisions</b> : pourquoi une racine de
 * sources ne se devine pas, pourquoi le compte est en cœurs. Ceux-ci ne gardent aucune
 * décision. Ils gardent un <b>constat</b> : à entrées égales, voilà exactement les fichiers
 * produits, les faits écrits et les chiffres calculés.
 *
 * <p>Ils existent pour une réécriture annoncée — le format qui passe à l'anglais, puis le
 * code lui-même. Ces deux chantiers renomment beaucoup et ne devraient rien changer d'autre.
 * Or « ne rien changer d'autre » est précisément ce qu'aucun test ne vérifiait : on aurait
 * perdu un fait, décalé un pourcentage ou cessé d'écrire un fichier sans qu'une seule
 * assertion bronche.
 *
 * <p><b>Ils sont faits pour être modifiés</b>, et c'est ce qui les distingue du reste. Quand
 * le format renommera {@code classe.jamais_executee} en {@code class.never_executed}, la
 * valeur attendue ici changera — c'est normal, c'est le but du chantier. Ce qui ne doit pas
 * changer, et que le test attrapera, c'est le <b>nombre</b> de faits, la <b>liste</b> des
 * classes mortes et les <b>chiffres</b> de couverture. Un renommage qui touche à cela n'est
 * plus un renommage.
 */
class CaracterisationTest {

    /**
     * Les fichiers qu'un rapport de deux exécutions contient, aujourd'hui.
     *
     * <p>Rien ici n'est joli ni définitif : c'est un relevé. Il attrape le fichier qu'on
     * cesserait d'écrire, celui qu'on écrirait en double, et le renommage involontaire.
     */
    private static final List<String> FICHIERS_ATTENDUS = List.of(
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

    /** Le compte de faits par famille, aujourd'hui. */
    private static final Map<String, Integer> FAITS_ATTENDUS = new TreeMap<>(Map.of(
            "campaign", 1,
            "class", 1,
            "class.never_executed", 1,
            "coverage.run", 2,
            "run", 2,
            "unavailable", 2,
            "method.hot", 4,
            "source.missing", 1));

    /** Les options que la ligne de commande accepte, aujourd'hui. */
    private static final List<String> OPTIONS_ATTENDUES = List.of(
            "--attach-after", "--classes", "--components", "--composants", "--config",
            "--context", "--contexte", "--cover", "--export", "--families", "--familles",
            "--filter", "--follow", "--help", "--hide", "--interval", "--java", "--level",
            "--max-seconds", "--name", "--niveau", "--no-values", "--out", "--print-options",
            "--repo", "--report-only", "--root", "--serve", "--serve-host", "--serve-token",
            "--sources", "--suivi");

    @Test
    @DisplayName("Deux exécutions produisent exactement ces vingt-six fichiers")
    void twoRunsProduceExactlyTheseFiles(@TempDir Path dir) throws Exception {
        Path out = assembler(dir);
        assertEquals(FICHIERS_ATTENDUS, fichiers(out),
                "un fichier en plus ou en moins n'est jamais un détail : le rapport est un "
                + "dossier, et la page comme les compétences comptent sur sa forme");
    }

    @Test
    @DisplayName("Les mêmes exécutions donnent toujours les mêmes faits, en même nombre")
    void theSameRunsAlwaysYieldTheSameFacts(@TempDir Path dir) throws Exception {
        Path out = assembler(dir);
        List<Map<String, Object>> faits = faits(out);

        Map<String, Integer> comptes = new TreeMap<>();
        for (Map<String, Object> f : faits) {
            comptes.merge(String.valueOf(f.get("fact")), 1, Integer::sum);
        }
        assertEquals(FAITS_ATTENDUS, comptes,
                "renommer une famille est prévu ; en perdre une, en dupliquer une, ou cesser "
                + "d'en écrire une ne l'est pas");

        assertEquals(List.of("app.Jamais"), valeurs(faits, "class.never_executed", "class"),
                "la classe morte est LE fait que l'outil existe pour donner");
        assertEquals(List.of("app.Moteur"), valeurs(faits, "class", "class"));
    }

    @Test
    @DisplayName("Les chiffres de couverture ne bougent pas d'une virgule")
    void theCoverageNumbersDoNotMoveOneDecimal(@TempDir Path dir) throws Exception {
        List<Map<String, Object>> faits = faits(assembler(dir));
        List<Map<String, Object>> couvertures = new ArrayList<>();
        for (Map<String, Object> f : faits) {
            if ("coverage.run".equals(f.get("fact"))) couvertures.add(f);
        }
        assertEquals(2, couvertures.size());
        for (Map<String, Object> c : couvertures) {
            assertEquals(9L, nombre(c.get("instructionsCovered")));
            assertEquals(22L, nombre(c.get("instructionsTotal")));
            assertEquals(40.9, ((Number) c.get("pct")).doubleValue(), 0.001,
                    "un pourcentage qui glisse à la faveur d'un renommage est le pire des "
                    + "défauts : personne ne le remarque, et il fait conclure");
        }
    }

    @Test
    @DisplayName("Réassembler deux fois donne les mêmes blocs, à l'octet près")
    void rebuildingTwiceYieldsTheSameBlocks(@TempDir Path dir) throws Exception {
        Path a = assembler(dir.resolve("un"));
        Path b = assembler(dir.resolve("deux"));

        // Les blocs sont de pures dérivations des mesures : aucune horloge, aucun chemin
        // absolu. Une différence entre deux assemblages y serait un non-déterminisme, et le
        // rapport cesserait d'être comparable à lui-même d'une semaine sur l'autre.
        for (String bloc : FICHIERS_ATTENDUS) {
            if (!bloc.endsWith(".js")) continue;
            assertArrayEquals(Files.readAllBytes(a.resolve(bloc)),
                    Files.readAllBytes(b.resolve(bloc)), bloc + " diffère d'un assemblage à "
                    + "l'autre alors qu'il ne dérive que des mesures");
        }
    }

    @Test
    @DisplayName("Aucune option de la ligne de commande ne disparaît sans qu'on le voie")
    void noCommandLineOptionQuietlyDisappears() throws Exception {
        // Les alias français doivent survivre à la bascule, et une option retirée par
        // inadvertance ne se remarque que le jour où un script de recette s'arrête.
        String main = Files.readString(Path.of("").toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                StandardCharsets.UTF_8);
        List<String> vues = new ArrayList<>();
        // Borné au seul switch des options : plus loin, Main compose les lignes de commande
        // de JaCoCo, qui ont leurs propres « --xml » et « --classfiles ».
        int debut = main.indexOf("switch (a) {");
        int fin = main.indexOf("unknown option", debut);
        assertTrue(debut > 0 && fin > debut, "le switch des options doit rester repérable");
        Matcher m = Pattern.compile("\"(--[a-z-]+)\"").matcher(main.substring(debut, fin));
        while (m.find()) if (!vues.contains(m.group(1))) vues.add(m.group(1));
        Collections.sort(vues);
        assertEquals(OPTIONS_ATTENDUES, vues);
    }

    // ------------------------------------------------------------------ fixtures

    private static Path assembler(Path dir) throws Exception {
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
        Files.writeString(src.resolve("app/Moteur.java"), """
                package app;
                class Moteur { int calculer(int x) { return x * x; } }
                """, StandardCharsets.UTF_8);
        return src;
    }

    /** Une exécution telle que l'orchestrateur en produit : une classe couverte, une morte. */
    private static void run(Path parent, String nom, String uuid) throws IOException {
        Path run = parent.resolve(nom);
        Files.createDirectories(run.resolve("jacoco/html"));
        Files.createDirectories(run.resolve("async-profiler"));
        Files.writeString(run.resolve("jacoco/html/jacoco.xml"), """
                <report name="t"><package name="app">
                <class name="app/Moteur" sourcefilename="Moteur.java">
                  <method name="calculer" desc="(I)I" line="3">
                    <counter type="INSTRUCTION" missed="0" covered="9"/></method>
                  <counter type="INSTRUCTION" missed="1" covered="9"/></class>
                <class name="app/Jamais" sourcefilename="Jamais.java">
                  <counter type="INSTRUCTION" missed="12" covered="0"/></class>
                <sourcefile name="Moteur.java"><line nr="3" mi="0" ci="9" mb="0" cb="0"/></sourcefile>
                </package></report>
                """, StandardCharsets.UTF_8);
        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Moteur.calculer 40\n", StandardCharsets.UTF_8);
        Files.writeString(run.resolve("jacoco/html/index.html"), "<html>c</html>",
                StandardCharsets.UTF_8);
        Files.writeString(run.resolve("run-context.json"), Json.write(new LinkedHashMap<>(Map.of(
                "uuid", uuid, "nomOrigine", nom, "commande", "java -jar app.jar",
                "methodeRacine", "app.Moteur::calculer", "machine", "poste"))),
                StandardCharsets.UTF_8);
    }

    private static List<String> fichiers(Path out) throws IOException {
        List<String> noms = new ArrayList<>();
        try (Stream<Path> s = Files.walk(out)) {
            s.filter(Files::isRegularFile)
             .forEach(p -> noms.add(out.relativize(p).toString().replace('\\', '/')));
        }
        Collections.sort(noms);
        return noms;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> faits(Path out) throws IOException {
        List<Map<String, Object>> faits = new ArrayList<>();
        for (String l : Files.readAllLines(out.resolve("faits.jsonl"), StandardCharsets.UTF_8)) {
            if (!l.isBlank()) faits.add((Map<String, Object>) Json.read(l));
        }
        assertTrue(faits.size() > 5);
        return faits;
    }

    private static List<String> valeurs(List<Map<String, Object>> faits, String famille,
                                        String champ) {
        List<String> out = new ArrayList<>();
        for (Map<String, Object> f : faits) {
            if (famille.equals(f.get("fact"))) out.add(String.valueOf(f.get(champ)));
        }
        Collections.sort(out);
        return out;
    }

    private static long nombre(Object o) {
        return ((Number) o).longValue();
    }
}
