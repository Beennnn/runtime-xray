package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab.xray.json.Json;

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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le paquet de contexte est ce qu'un modèle lira <b>à la place</b> du rapport. Ce qu'on en
 * retire, il ne l'apprendra pas ; ce qu'on y met sans le dire, il le prendra pour la vérité
 * entière. D'où ces tests : ils gardent moins un format qu'une honnêteté.
 */
class ContextTest {

    private static Path factsFile(Path dir, int deadClasses) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(Json.write(Map.of("fact", "campaign", "outil", "runtime-xray",
                "runs", 2, "vocabulary", Facts.VOCABULARY)));
        lines.add(Json.write(Map.of("fact", "unavailable", "run", "B",
                "what", "time", "why", "aucun relevé de pile n'a été pris",
                "consequence", "aucun pourcentage de temps n'est calculable",
                "remedy", "relancer sous Linux")));
        lines.add(Json.write(Map.of("fact", "caveat", "run", "A", "what", "time",
                "caveat", "une partie des relevés a été rattachée à l'appelant")));
        for (int i = 0; i < deadClasses; i++) {
            lines.add(Json.write(Map.of("fact", "class.never_executed",
                    "classe", "com.example.app.Classe" + i,
                    "runsAnalysed", List.of("A", "B"))));
        }
        Path f = dir.resolve(Facts.FILE);
        Files.write(f, lines, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("Ce qui n'a pas été mesuré vient AVANT les chiffres, jamais après")
    void whatWasNotMeasuredComesFirst(@TempDir Path dir) throws Exception {
        factsFile(dir, 3);
        String pkg = Context.of(dir, "which classes never ran?",
                Context.BUDGET);

        int unavailabilities = pkg.indexOf("NOT measured");
        int facts = pkg.indexOf("```jsonl");
        assertTrue(unavailabilities > 0, "la section doit exister");
        assertTrue(unavailabilities < facts,
                "un lecteur qui voit les chiffres d'abord les a déjà interprétés quand il "
                + "arrive aux réserves : l'ordre EST l'information");
        assertTrue(pkg.contains("aucun relevé de pile n'a été pris"));
        assertTrue(pkg.contains("une partie des relevés a été rattachée à l'appelant"),
                "une réserve porte sur une mesure qui existe : elle compte aussi");
    }

    @Test
    @DisplayName("Le budget serré n'écarte jamais une indisponibilité, seulement des mesures")
    void theBudgetNeverDropsAnUnavailability(@TempDir Path dir) throws Exception {
        factsFile(dir, 400);
        // Un budget qui ne laisse passer que l'en-tête : c'est le cas limite qui décide.
        String pkg = Context.of(dir, "classes jamais exécutées", 3_000);

        assertTrue(pkg.contains("aucun relevé de pile n'a été pris"),
                "l'absence de mesure est ce qui empêche une conclusion fausse : elle reste, "
                + "même quand tout le reste est coupé");
        assertTrue(pkg.contains("left out"),
                "une troncature muette fait conclure sur un échantillon en croyant voir tout");
        assertTrue(pkg.contains("INCOMPLETE"));
        assertTrue(pkg.length() < 3_000 + 2_000,
                "le budget doit être tenu à l'avertissement près, sinon il ne sert à rien");
    }

    @Test
    @DisplayName("Le vocabulaire et les pièges de lecture voyagent avec les faits")
    void theLegendTravelsWithTheData(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        String pkg = Context.of(dir, "", Context.BUDGET);

        // Un modèle qui n'a jamais vu ce format doit pouvoir le lire : on joint la légende,
        // pas un lien vers elle.
        assertTrue(pkg.contains("class.never_executed"));
        assertTrue(pkg.contains("Three reading traps"));
        assertTrue(pkg.contains("does not mean \"never ran\""));
        assertTrue(pkg.contains("says nothing about whether it is correct"),
                "un taux de couverture pris pour une note de qualité est l'erreur la plus "
                + "banale, et un modèle la commet aussi");
    }

    @Test
    @DisplayName("Une question qu'on ne sait pas classer donne la vue d'ensemble, pas le vide")
    void anUnrecognisedQuestionStillGetsAnAnswerablePack(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        String pkg = Context.of(dir, "est-ce que ce truc marche bien ?", Context.BUDGET);
        assertTrue(pkg.contains("class.never_executed"),
                "faute de mieux on donne de quoi répondre, plutôt qu'un paquet vide");
    }

    @Test
    @DisplayName("Sans rapport assemblé, on dit quoi faire — on n'invente pas un contexte")
    void withoutAReportItSaysWhatToDo(@TempDir Path dir) {
        Exception e = assertThrows(Exception.class,
                () -> Context.of(dir, "une question", Context.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("--report-only"),
                "le message doit porter le geste qui débloque");
    }

    @Test
    @DisplayName("Un bloc de faits vide s'explique, sinon il se lit comme une extraction ratée")
    void anEmptyFenceExplainsItself(@TempDir Path dir) throws Exception {
        // La campagne contient des faits, mais aucun de la famille demandée. Sans un mot, le
        // lecteur ne peut pas trancher entre « il n'y en a pas » et « l'outil a échoué » :
        // deux conclusions opposées à partir du même vide.
        factsFile(dir, 0);
        String pkg = Context.of(dir, "which classes never ran?",
                Context.BUDGET);

        assertTrue(pkg.contains("No fact of these families"));
        assertTrue(pkg.contains("Families present in the file"),
                "dire ce qu'il Y A permet de conclure que le reste manque vraiment");
        assertTrue(pkg.contains("unavailable"),
                "la liste des familles présentes doit être celle du fichier, pas une devinette");
    }

    @Test
    @DisplayName("Un compte entier s'écrit 29, jamais 29.0 — dans l'en-tête comme dans les faits")
    void anIntegerCountIsWrittenAsAnInteger(@TempDir Path dir) throws Exception {
        // Le JSON relu rend tout nombre en double : un aller-retour transforme 41 en 41.0.
        // Ce n'est pas faux, mais c'est le détail qui fait douter du reste — chez un lecteur
        // humain comme chez un modèle.
        Path f = factsFile(dir, 0);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + "{\"fact\":\"method.hot\",\"method\":\"com.example.app.Repository.load\","
                + "\"samples\":41,\"pct\":100.0}\n", StandardCharsets.UTF_8);

        String pkg = Context.of(dir, "where does the time go?", Context.BUDGET);
        assertTrue(pkg.contains("runs : 2"));
        assertFalse(pkg.contains("runs : 2.0"));
        assertTrue(pkg.contains("\"samples\":41"), "la ligne d'origine est recopiée telle quelle");
        assertFalse(pkg.contains("41.0"));
        assertTrue(pkg.contains("\"pct\":100.0"),
                "et un flottant reste un flottant : on recopie, on ne reformate pas");
    }

    @Test
    @DisplayName("Aucune valeur capturée ne part vers un service tiers")
    void noCapturedValueEverLeaves(@TempDir Path dir) throws Exception {
        // Les faits n'en portent pas — un test de Facts le garde. Celui-ci garde le maillon
        // suivant : ce qui SORT de la machine vers un modèle, parfois hébergé ailleurs.
        Path f = factsFile(dir, 1);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + Json.write(Map.of("fact", "classe", "classe", "com.example.app.A",
                        "pct", 12)) + "\n", StandardCharsets.UTF_8);

        String pkg = Context.of(dir, "couverture", Context.BUDGET);
        assertFalse(pkg.contains("motDePasse"));
        assertFalse(pkg.contains("params"),
                "les valeurs de paramètres restent dans leur bloc, sur le disque");
    }

    @Test
    @DisplayName("Nommer les familles prime sur la question, et le résultat ne dépend plus des mots")
    void namingTheFamiliesWinsOverTheQuestion(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        // Une question dont les mots-clés désignent le temps, mais un script qui demande
        // les classes mortes : c'est la demande explicite qui doit gagner, sans quoi le
        // script produirait autre chose que ce qu'il croit lire.
        Context.Pack p = Context.of(dir, "where does the time go?",
                List.of("class.never_executed"), Context.BUDGET);

        assertEquals(Context.Origin.REQUESTED, p.origin());
        assertEquals(List.of("class.never_executed"), p.families());
        assertTrue(p.text().contains("class.never_executed"));
        assertFalse(p.text().contains("methode.chaude\""),
                "la question ne doit plus rien choisir quand on a nommé les familles");
        // Elle continue en revanche de voyager : c'est son autre rôle.
        assertTrue(p.text().contains("where does the time go?"));
    }

    @Test
    @DisplayName("Une famille inconnue s'arrête net, avec la liste de celles qui existent")
    void anUnknownFamilyStopsAndListsTheRealOnes(@TempDir Path dir) throws Exception {
        factsFile(dir, 1);
        // L'inverse du texte libre, et délibérément : une phrase vient d'un humain qui
        // cherche, une famille vient d'un script. Un script qui se trompe a un défaut, et
        // le lui taire produirait un paquet silencieusement différent de son attente.
        Exception e = assertThrows(Exception.class, () -> Context.of(dir, "",
                List.of("classes.mortes"), Context.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("classes.mortes"));
        assertTrue(String.valueOf(e.getMessage()).contains("class.never_executed"),
                "dire ce qui existe, pas seulement que ce qu'on a donné n'existe pas");
    }

    @Test
    @DisplayName("L'opérateur sait ce qui a été compris de sa question, ou qu'elle ne l'a pas été")
    void theOperatorLearnsWhatWasUnderstood(@TempDir Path dir) throws Exception {
        factsFile(dir, 1);
        // Le cœur du problème : une question en toutes lettres A L'AIR d'être comprise.
        // Sans ce retour, personne ne sait que « quelles classes n'ont jamais tourné ? »
        // se réduit au seul mot « jamais ».
        Context.Pack recognised = Context.of(dir, "which classes never ran?",
                List.of(), Context.BUDGET);
        assertEquals(Context.Origin.KEYWORDS, recognised.origin());
        assertTrue(recognised.announcement().contains("from the question"));

        Context.Pack silent = Context.of(dir, "welche Klassen liefen nie",
                List.of(), Context.BUDGET);
        assertEquals(Context.Origin.OVERVIEW, silent.origin());
        assertTrue(silent.announcement().contains("no keyword recognised"),
                "un repli qui passe pour une lecture réussie est pire que pas de repli");
        assertFalse(silent.families().isEmpty(), "on répond quand même");
    }

    @Test
    @DisplayName("Tout mot-clé anglais est écrit dans l'aide : sinon il est introuvable")
    void everyEnglishKeywordIsWrittenInTheHelp() throws Exception {
        // Le reproche fait à cette option est qu'elle n'est pas découvrable : « --help » ne
        // peut pas énumérer ce qui marche. Il le peut, à condition que la table ne pourrisse
        // pas — et une table de documentation ne casse aucun build.
        String help = help();
        for (Map.Entry<String, String[]> e : Context.WORDS.entrySet()) {
            for (String word : e.getValue()) {
                assertTrue(help.contains(word), "le mot-clé « " + word + " » déclenche « "
                        + e.getKey() + " » mais n'est écrit nulle part dans l'aide : "
                        + "personne ne peut le deviner");
            }
        }
    }

    @Test
    @DisplayName("Les mots français marchent sans être documentés, et l'aide le dit")
    void theFrenchWordsWorkWithoutBeingDocumented() throws Exception {
        // L'outil parle anglais : une seconde table dans l'aide la rendrait illisible pour
        // ceux à qui elle s'adresse. Mais taire complètement leur existence ferait croire à
        // un francophone que sa question n'a pas été lue.
        assertEquals(Context.WORDS.keySet(), Context.WORDS_FR.keySet(),
                "chaque famille documentée doit avoir ses mots français, et l'inverse");
        assertEquals(List.of("class.never_executed"),
                Context.recognisedFamilies("quelles classes n'ont jamais tourné ?"));
        assertEquals(List.of("method.hot"), Context.recognisedFamilies("quel est le coût ?"),
                "les accents ne doivent pas empêcher la reconnaissance");
        assertTrue(help().contains("French words are recognised too"),
                "leur existence doit être dite, même sans la table");
    }

    @Test
    @DisplayName("Un mot-clé ouvre un mot : « screenshot » ne déclenche pas « hot »")
    void aKeywordOpensAWordItIsNotFoundAnywhere() {
        // Chercher n'importe où dans la chaîne paraissait plus généreux et se retournait
        // contre nous. Le passage à l'anglais aggravait le défaut : « hot » vit dans
        // « screenshot », « rate » dans « generate », « cout » dans « écouter ».
        assertTrue(Context.recognisedFamilies("show me a screenshot").isEmpty());
        assertTrue(Context.recognisedFamilies("generate a report").isEmpty());
        assertTrue(Context.recognisedFamilies("je veux écouter les journaux").isEmpty());
        // Les flexions, elles, restent attrapées : c'est tout l'intérêt d'un début de mot.
        assertEquals(List.of("source.missing", "source.hint"),
                Context.recognisedFamilies("il manque des sources"));
        assertEquals(List.of("class.never_executed"),
                Context.recognisedFamilies("des classes inutilisées"));
    }

    @Test
    @DisplayName("Un rapport 1.0 renvoie à --report-only, faute d'un migrateur qui n'existe pas")
    void aFormatOneReportPointsAtReportOnly(@TempDir Path dir) throws Exception {
        // Il n'y a pas d'outil de migration, et c'est un constat : faits.jsonl est dérivé
        // des mesures, donc --report-only le réécrit entièrement dans le format courant,
        // avec au passage tous les correctifs accumulés. Un migrateur n'aurait renommé que
        // des clés dans un fichier resté périmé par ailleurs.
        Files.writeString(dir.resolve(Facts.FILE),
                "{\"fait\":\"campagne\",\"outil\":\"runtime-xray\"}\n"
                + "{\"fait\":\"classe.jamais_executee\",\"classe\":\"a.B\"}\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("runs"));

        Exception withSamples = assertThrows(Exception.class,
                () -> Context.of(dir, "", Context.BUDGET));
        assertTrue(String.valueOf(withSamples.getMessage()).contains("--report-only"),
                "les mesures sont là : le geste qui débloque est de régénérer");

        // Amputée de runs/, rien ne peut être régénéré — et le dire vaut mieux que
        // proposer une commande qui échouera.
        Files.delete(dir.resolve("runs"));
        Exception withoutSamples = assertThrows(Exception.class,
                () -> Context.of(dir, "", Context.BUDGET));
        assertFalse(String.valueOf(withoutSamples.getMessage()).contains("--report-only"));
        assertTrue(String.valueOf(withoutSamples.getMessage()).contains("runs/"));
    }

    @Test
    @DisplayName("Les noms de familles de la 1.0 restent acceptés par --families")
    void theFormatOneFamilyNamesAreStillAccepted(@TempDir Path dir) throws Exception {
        // Un script de recette écrit contre la 1.0 ne doit pas s'arrêter parce que le
        // format a changé de langue.
        factsFile(dir, 2);
        Context.Pack p = Context.of(dir, "", List.of("classe.jamais_executee"),
                Context.BUDGET);
        assertEquals(List.of("class.never_executed"), p.families());
    }

    private static String help() throws Exception {
        return Files.readString(Path.of("").toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                StandardCharsets.UTF_8);
    }
}
