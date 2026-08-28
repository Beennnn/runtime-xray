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
        lignes.add(Json.write(Map.of("fait", "campagne", "outil", "runtime-xray",
                "executions", 2, "vocabulaire", Faits.VOCABULAIRE)));
        lignes.add(Json.write(Map.of("fait", "indisponibilite", "execution", "B",
                "quoi", "temps", "pourquoi", "aucun relevé de pile n'a été pris",
                "consequence", "aucun pourcentage de temps n'est calculable",
                "remede", "relancer sous Linux")));
        lignes.add(Json.write(Map.of("fait", "reserve", "execution", "A", "quoi", "temps",
                "reserve", "une partie des relevés a été rattachée à l'appelant")));
        for (int i = 0; i < classesMortes; i++) {
            lignes.add(Json.write(Map.of("fait", "classe.jamais_executee",
                    "classe", "org.exemple.module.Classe" + i,
                    "executionsAnalysee", List.of("A", "B"))));
        }
        Path f = dir.resolve(Faits.FICHIER);
        Files.write(f, lignes, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("Ce qui n'a pas été mesuré vient AVANT les chiffres, jamais après")
    void whatWasNotMeasuredComesFirst(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 3);
        String paquet = Contexte.pour(dir, "quelles classes n'ont jamais tourné ?",
                Contexte.BUDGET);

        int absences = paquet.indexOf("N'A PAS été mesuré");
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
        assertTrue(paquet.contains("écarté(s)"),
                "une troncature muette fait conclure sur un échantillon en croyant voir tout");
        assertTrue(paquet.contains("INCOMPLÈTE"));
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
        assertTrue(paquet.contains("classe.jamais_executee"));
        assertTrue(paquet.contains("Trois pièges de lecture"));
        assertTrue(paquet.contains("ne veut pas dire « n'a pas tourné »"));
        assertTrue(paquet.contains("Il ne dit rien de sa justesse"),
                "un taux de couverture pris pour une note de qualité est l'erreur la plus "
                + "banale, et un modèle la commet aussi");
    }

    @Test
    @DisplayName("Une question qu'on ne sait pas classer donne la vue d'ensemble, pas le vide")
    void anUnrecognisedQuestionStillGetsAnAnswerablePack(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 2);
        String paquet = Contexte.pour(dir, "est-ce que ce truc marche bien ?", Contexte.BUDGET);
        assertTrue(paquet.contains("classe.jamais_executee"),
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
        String paquet = Contexte.pour(dir, "quelles classes n'ont jamais tourné ?",
                Contexte.BUDGET);

        assertTrue(paquet.contains("Aucun fait de ces familles"));
        assertTrue(paquet.contains("Familles présentes dans le fichier"),
                "dire ce qu'il Y A permet de conclure que le reste manque vraiment");
        assertTrue(paquet.contains("indisponibilite"),
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
                + "{\"fait\":\"methode.chaude\",\"methode\":\"org.exemple.module.Depot.charger\","
                + "\"releves\":41,\"pct\":100.0}\n", StandardCharsets.UTF_8);

        String paquet = Contexte.pour(dir, "où passe le temps ?", Contexte.BUDGET);
        assertTrue(paquet.contains("executions : 2"));
        assertFalse(paquet.contains("executions : 2.0"));
        assertTrue(paquet.contains("\"releves\":41"), "la ligne d'origine est recopiée telle quelle");
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
                + Json.write(Map.of("fait", "classe", "classe", "org.exemple.module.A",
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
        Contexte.Paquet p = Contexte.pour(dir, "où passe le temps ?",
                List.of("classe.jamais_executee"), Contexte.BUDGET);

        assertEquals(Contexte.Origine.DEMANDEES, p.origine());
        assertEquals(List.of("classe.jamais_executee"), p.familles());
        assertTrue(p.texte().contains("classe.jamais_executee"));
        assertFalse(p.texte().contains("methode.chaude\""),
                "la question ne doit plus rien choisir quand on a nommé les familles");
        // Elle continue en revanche de voyager : c'est son autre rôle.
        assertTrue(p.texte().contains("où passe le temps ?"));
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
        assertTrue(String.valueOf(e.getMessage()).contains("classe.jamais_executee"),
                "dire ce qui existe, pas seulement que ce qu'on a donné n'existe pas");
    }

    @Test
    @DisplayName("L'opérateur sait ce qui a été compris de sa question, ou qu'elle ne l'a pas été")
    void theOperatorLearnsWhatWasUnderstood(@TempDir Path dir) throws Exception {
        fichierDeFaits(dir, 1);
        // Le cœur du problème : une question en toutes lettres A L'AIR d'être comprise.
        // Sans ce retour, personne ne sait que « quelles classes n'ont jamais tourné ? »
        // se réduit au seul mot « jamais ».
        Contexte.Paquet reconnue = Contexte.pour(dir, "quelles classes n'ont jamais tourné ?",
                List.of(), Contexte.BUDGET);
        assertEquals(Contexte.Origine.MOTS_CLES, reconnue.origine());
        assertTrue(reconnue.annonce().contains("d'après la question"));

        Contexte.Paquet muette = Contexte.pour(dir, "welche Klassen liefen nie",
                List.of(), Contexte.BUDGET);
        assertEquals(Contexte.Origine.VUE_ENSEMBLE, muette.origine());
        assertTrue(muette.annonce().contains("aucun mot-clé reconnu"),
                "un repli qui passe pour une lecture réussie est pire que pas de repli");
        assertFalse(muette.familles().isEmpty(), "on répond quand même");
    }

    @Test
    @DisplayName("Tout mot-clé du code est écrit dans l'aide : sinon il est introuvable")
    void everyKeywordInTheCodeIsWrittenInTheHelp() throws Exception {
        // Le reproche fait à cette option est qu'elle n'est pas découvrable : « --help » ne
        // peut pas énumérer ce qui marche. Il le peut, à condition que la table ne pourrisse
        // pas — et une table de documentation ne casse aucun build.
        Path racine = Path.of("").toAbsolutePath().getParent();
        String contexte = Files.readString(racine.resolve("orchestrator/src/main/java/lab/xray"
                + "/report/Contexte.java").normalize(), StandardCharsets.UTF_8);
        String main = Files.readString(racine.resolve("orchestrator/src/main/java/lab/xray"
                + "/Main.java").normalize(), StandardCharsets.UTF_8);

        int debut = contexte.indexOf("static List<String> famillesReconnues");
        int fin = contexte.indexOf("\n    }", debut);
        assertTrue(debut > 0 && fin > debut);
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(contexte.substring(debut, fin));
        int comptes = 0;
        while (m.find()) {
            String mot = m.group(1);
            if (mot.contains(".")) continue;             // un nom de famille, pas un mot-clé
            comptes++;
            assertTrue(main.contains(mot), "le mot-clé « " + mot + " » déclenche une famille "
                    + "mais n'est écrit nulle part dans l'aide : personne ne peut le deviner");
        }
        assertTrue(comptes > 25, "l'extraction a raté : " + comptes + " mots trouvés");
    }
}
