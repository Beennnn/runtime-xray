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
class ContexteTest {

    private static Path fichierDeFaits(Path dir, int classesMortes) throws Exception {
        List<String> lignes = new ArrayList<>();
        lignes.add(Json.write(Map.of("fact", "campaign", "outil", "runtime-xray",
                "runs", 2, "vocabulary", Faits.VOCABULAIRE)));
        lignes.add(Json.write(Map.of("fact", "unavailable", "run", "B",
                "what", "time", "why", "aucun relevé de pile n'a été pris",
                "consequence", "aucun pourcentage de temps n'est calculable",
                "remedy", "relancer sous Linux")));
        lignes.add(Json.write(Map.of("fact", "caveat", "run", "A", "what", "time",
                "caveat", "une partie des relevés a été rattachée à l'appelant")));
        for (int i = 0; i < classesMortes; i++) {
            lignes.add(Json.write(Map.of("fact", "class.never_executed",
                    "classe", "com.example.app.Classe" + i,
                    "runsAnalysed", List.of("A", "B"))));
        }
        Path f = dir.resolve(Faits.FICHIER);
        Files.write(f, lignes, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("Ce qui n'a pas été mesuré vient AVANT les chiffres, jamais après")
    void whatWasNotMeasuredComesFirst(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 3);
        String paquet = Contexte.pour(dir, "which classes never ran?",
                Contexte.BUDGET);

        int absences = paquet.indexOf("NOT measured");
        int faits = paquet.indexOf("```jsonl");
        assertTrue(absences > 0, "la section doit exister");
        assertTrue(absences < faits,
                "un lecteur qui voit les chiffres d'abord les a déjà interprétés quand il "
                + "arrive aux réserves : l'ordre EST l'information");
        assertTrue(paquet.contains("aucun relevé de pile n'a été pris"));
        assertTrue(paquet.contains("une partie des relevés a été rattachée à l'appelant"),
                "une réserve porte sur une mesure qui existe : elle compte aussi");
    }

    @Test
    @DisplayName("Le budget serré n'écarte jamais une indisponibilité, seulement des mesures")
    void theBudgetNeverDropsAnUnavailability(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 400);
        // Un budget qui ne laisse passer que l'en-tête : c'est le cas limite qui décide.
        String paquet = Contexte.pour(dir, "classes jamais exécutées", 3_000);

        assertTrue(paquet.contains("aucun relevé de pile n'a été pris"),
                "l'absence de mesure est ce qui empêche une conclusion fausse : elle reste, "
                + "même quand tout le reste est coupé");
        assertTrue(paquet.contains("left out"),
                "une troncature muette fait conclure sur un échantillon en croyant voir tout");
        assertTrue(paquet.contains("INCOMPLETE"));
        assertTrue(paquet.length() < 3_000 + 2_000,
                "le budget doit être tenu à l'avertissement près, sinon il ne sert à rien");
    }

    @Test
    @DisplayName("Le vocabulaire et les pièges de lecture voyagent avec les faits")
    void theLegendTravelsWithTheData(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 2);
        String paquet = Contexte.pour(dir, "", Contexte.BUDGET);

        // Un modèle qui n'a jamais vu ce format doit pouvoir le lire : on joint la légende,
        // pas un lien vers elle.
        assertTrue(paquet.contains("class.never_executed"));
        assertTrue(paquet.contains("Three reading traps"));
        assertTrue(paquet.contains("does not mean \"never ran\""));
        assertTrue(paquet.contains("says nothing about whether it is correct"),
                "un taux de couverture pris pour une note de qualité est l'erreur la plus "
                + "banale, et un modèle la commet aussi");
    }

    @Test
    @DisplayName("Une question qu'on ne sait pas classer donne la vue d'ensemble, pas le vide")
    void anUnrecognisedQuestionStillGetsAnAnswerablePack(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 2);
        String paquet = Contexte.pour(dir, "est-ce que ce truc marche bien ?", Contexte.BUDGET);
        assertTrue(paquet.contains("class.never_executed"),
                "faute de mieux on donne de quoi répondre, plutôt qu'un paquet vide");
    }

    @Test
    @DisplayName("Sans rapport assemblé, on dit quoi faire — on n'invente pas un contexte")
    void withoutAReportItSaysWhatToDo(@TempDir Path dir) {
        Exception e = assertThrows(Exception.class,
                () -> Contexte.pour(dir, "une question", Contexte.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("--report-only"),
                "le message doit porter le geste qui débloque");
    }

    @Test
    @DisplayName("Un bloc de faits vide s'explique, sinon il se lit comme une extraction ratée")
    void anEmptyFenceExplainsItself(@TempDir Path dir) throws Exception {
        // La campagne contient des faits, mais aucun de la famille demandée. Sans un mot, le
        // lecteur ne peut pas trancher entre « il n'y en a pas » et « l'outil a échoué » :
        // deux conclusions opposées à partir du même vide.
        fichierDeFaits(dir, 0);
        String paquet = Contexte.pour(dir, "which classes never ran?",
                Contexte.BUDGET);

        assertTrue(paquet.contains("No fact of these families"));
        assertTrue(paquet.contains("Families present in the file"),
                "dire ce qu'il Y A permet de conclure que le reste manque vraiment");
        assertTrue(paquet.contains("unavailable"),
                "la liste des familles présentes doit être celle du fichier, pas une devinette");
    }

    @Test
    @DisplayName("Un compte entier s'écrit 29, jamais 29.0 — dans l'en-tête comme dans les faits")
    void anIntegerCountIsWrittenAsAnInteger(@TempDir Path dir) throws Exception {
        // Le JSON relu rend tout nombre en double : un aller-retour transforme 41 en 41.0.
        // Ce n'est pas faux, mais c'est le détail qui fait douter du reste — chez un lecteur
        // humain comme chez un modèle.
        Path f = fichierDeFaits(dir, 0);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + "{\"fact\":\"method.hot\",\"method\":\"com.example.app.Repository.load\","
                + "\"samples\":41,\"pct\":100.0}\n", StandardCharsets.UTF_8);

        String paquet = Contexte.pour(dir, "where does the time go?", Contexte.BUDGET);
        assertTrue(paquet.contains("runs : 2"));
        assertFalse(paquet.contains("runs : 2.0"));
        assertTrue(paquet.contains("\"samples\":41"), "la ligne d'origine est recopiée telle quelle");
        assertFalse(paquet.contains("41.0"));
        assertTrue(paquet.contains("\"pct\":100.0"),
                "et un flottant reste un flottant : on recopie, on ne reformate pas");
    }

    @Test
    @DisplayName("Aucune valeur capturée ne part vers un service tiers")
    void noCapturedValueEverLeaves(@TempDir Path dir) throws Exception {
        // Les faits n'en portent pas — un test de Faits le garde. Celui-ci garde le maillon
        // suivant : ce qui SORT de la machine vers un modèle, parfois hébergé ailleurs.
        Path f = fichierDeFaits(dir, 1);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + Json.write(Map.of("fact", "classe", "classe", "com.example.app.A",
                        "pct", 12)) + "\n", StandardCharsets.UTF_8);

        String paquet = Contexte.pour(dir, "couverture", Contexte.BUDGET);
        assertFalse(paquet.contains("motDePasse"));
        assertFalse(paquet.contains("params"),
                "les valeurs de paramètres restent dans leur bloc, sur le disque");
    }

    @Test
    @DisplayName("Nommer les familles prime sur la question, et le résultat ne dépend plus des mots")
    void namingTheFamiliesWinsOverTheQuestion(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 2);
        // Une question dont les mots-clés désignent le temps, mais un script qui demande
        // les classes mortes : c'est la demande explicite qui doit gagner, sans quoi le
        // script produirait autre chose que ce qu'il croit lire.
        Contexte.Paquet p = Contexte.pour(dir, "where does the time go?",
                List.of("class.never_executed"), Contexte.BUDGET);

        assertEquals(Contexte.Origine.DEMANDEES, p.origine());
        assertEquals(List.of("class.never_executed"), p.familles());
        assertTrue(p.texte().contains("class.never_executed"));
        assertFalse(p.texte().contains("methode.chaude\""),
                "la question ne doit plus rien choisir quand on a nommé les familles");
        // Elle continue en revanche de voyager : c'est son autre rôle.
        assertTrue(p.texte().contains("where does the time go?"));
    }

    @Test
    @DisplayName("Une famille inconnue s'arrête net, avec la liste de celles qui existent")
    void anUnknownFamilyStopsAndListsTheRealOnes(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 1);
        // L'inverse du texte libre, et délibérément : une phrase vient d'un humain qui
        // cherche, une famille vient d'un script. Un script qui se trompe a un défaut, et
        // le lui taire produirait un paquet silencieusement différent de son attente.
        Exception e = assertThrows(Exception.class, () -> Contexte.pour(dir, "",
                List.of("classes.mortes"), Contexte.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("classes.mortes"));
        assertTrue(String.valueOf(e.getMessage()).contains("class.never_executed"),
                "dire ce qui existe, pas seulement que ce qu'on a donné n'existe pas");
    }

    @Test
    @DisplayName("L'opérateur sait ce qui a été compris de sa question, ou qu'elle ne l'a pas été")
    void theOperatorLearnsWhatWasUnderstood(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 1);
        // Le cœur du problème : une question en toutes lettres A L'AIR d'être comprise.
        // Sans ce retour, personne ne sait que « quelles classes n'ont jamais tourné ? »
        // se réduit au seul mot « jamais ».
        Contexte.Paquet reconnue = Contexte.pour(dir, "which classes never ran?",
                List.of(), Contexte.BUDGET);
        assertEquals(Contexte.Origine.MOTS_CLES, reconnue.origine());
        assertTrue(reconnue.annonce().contains("from the question"));

        Contexte.Paquet muette = Contexte.pour(dir, "welche Klassen liefen nie",
                List.of(), Contexte.BUDGET);
        assertEquals(Contexte.Origine.VUE_ENSEMBLE, muette.origine());
        assertTrue(muette.annonce().contains("no keyword recognised"),
                "un repli qui passe pour une lecture réussie est pire que pas de repli");
        assertFalse(muette.familles().isEmpty(), "on répond quand même");
    }

    @Test
    @DisplayName("Tout mot-clé anglais est écrit dans l'aide : sinon il est introuvable")
    void everyEnglishKeywordIsWrittenInTheHelp() throws Exception {
        // Le reproche fait à cette option est qu'elle n'est pas découvrable : « --help » ne
        // peut pas énumérer ce qui marche. Il le peut, à condition que la table ne pourrisse
        // pas — et une table de documentation ne casse aucun build.
        String aide = aide();
        for (Map.Entry<String, String[]> e : Contexte.MOTS.entrySet()) {
            for (String mot : e.getValue()) {
                assertTrue(aide.contains(mot), "le mot-clé « " + mot + " » déclenche « "
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
        assertEquals(Contexte.MOTS.keySet(), Contexte.MOTS_FR.keySet(),
                "chaque famille documentée doit avoir ses mots français, et l'inverse");
        assertEquals(List.of("class.never_executed"),
                Contexte.famillesReconnues("quelles classes n'ont jamais tourné ?"));
        assertEquals(List.of("method.hot"), Contexte.famillesReconnues("quel est le coût ?"),
                "les accents ne doivent pas empêcher la reconnaissance");
        assertTrue(aide().contains("French words are recognised too"),
                "leur existence doit être dite, même sans la table");
    }

    @Test
    @DisplayName("Un mot-clé ouvre un mot : « screenshot » ne déclenche pas « hot »")
    void aKeywordOpensAWordItIsNotFoundAnywhere() {
        // Chercher n'importe où dans la chaîne paraissait plus généreux et se retournait
        // contre nous. Le passage à l'anglais aggravait le défaut : « hot » vit dans
        // « screenshot », « rate » dans « generate », « cout » dans « écouter ».
        assertTrue(Contexte.famillesReconnues("show me a screenshot").isEmpty());
        assertTrue(Contexte.famillesReconnues("generate a report").isEmpty());
        assertTrue(Contexte.famillesReconnues("je veux écouter les journaux").isEmpty());
        // Les flexions, elles, restent attrapées : c'est tout l'intérêt d'un début de mot.
        assertEquals(List.of("source.missing", "source.hint"),
                Contexte.famillesReconnues("il manque des sources"));
        assertEquals(List.of("class.never_executed"),
                Contexte.famillesReconnues("des classes inutilisées"));
    }

    @Test
    @DisplayName("Un rapport 1.0 renvoie à --report-only, faute d'un migrateur qui n'existe pas")
    void aFormatOneReportPointsAtReportOnly(@TempDir Path dir) throws Exception {
        // Il n'y a pas d'outil de migration, et c'est un constat : faits.jsonl est dérivé
        // des mesures, donc --report-only le réécrit entièrement dans le format courant,
        // avec au passage tous les correctifs accumulés. Un migrateur n'aurait renommé que
        // des clés dans un fichier resté périmé par ailleurs.
        Files.writeString(dir.resolve(Faits.FICHIER),
                "{\"fait\":\"campagne\",\"outil\":\"runtime-xray\"}\n"
                + "{\"fait\":\"classe.jamais_executee\",\"classe\":\"a.B\"}\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("runs"));

        Exception avecMesures = assertThrows(Exception.class,
                () -> Contexte.pour(dir, "", Contexte.BUDGET));
        assertTrue(String.valueOf(avecMesures.getMessage()).contains("--report-only"),
                "les mesures sont là : le geste qui débloque est de régénérer");

        // Amputée de runs/, rien ne peut être régénéré — et le dire vaut mieux que
        // proposer une commande qui échouera.
        Files.delete(dir.resolve("runs"));
        Exception sansMesures = assertThrows(Exception.class,
                () -> Contexte.pour(dir, "", Contexte.BUDGET));
        assertFalse(String.valueOf(sansMesures.getMessage()).contains("--report-only"));
        assertTrue(String.valueOf(sansMesures.getMessage()).contains("runs/"));
    }

    @Test
    @DisplayName("Les noms de familles de la 1.0 restent acceptés par --families")
    void theFormatOneFamilyNamesAreStillAccepted(@TempDir Path dir) throws Exception {
        // Un script de recette écrit contre la 1.0 ne doit pas s'arrêter parce que le
        // format a changé de langue.
        fichierDeFaits(dir, 2);
        Contexte.Paquet p = Contexte.pour(dir, "", List.of("classe.jamais_executee"),
                Contexte.BUDGET);
        assertEquals(List.of("class.never_executed"), p.familles());
    }

    private static String aide() throws Exception {
        return Files.readString(Path.of("").toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                StandardCharsets.UTF_8);
    }
}
