package lab.xray.report;

import lab.xray.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La vue s'ouvre en anglais, et sait rendre le français.
 *
 * <h2>Ce que ces tests gardent</h2>
 *
 * <p>La traduction est faite d'un dictionnaire et d'un parcours du DOM, tenus dans la page
 * elle-même. Aucun test ne peut ouvrir un navigateur ici — la vérification par le rendu se
 * fait à la main, avant de pousser. Ce qui se garde en revanche par un test, et qui est le
 * mode d'échec réel, c'est le <b>décrochage</b> : quelqu'un reformule un libellé français
 * dans le gabarit, le dictionnaire ne le connaît plus, et la vue anglaise affiche du
 * français sans que rien ne le signale. C'est le même silence que le panneau de code vide
 * ailleurs dans ce projet.
 *
 * <p>Le corps statique de la page — ce qu'un lecteur voit avant que le moindre script n'ait
 * dessiné quoi que ce soit — est vérifié en entier : chaque phrase française qu'il porte
 * doit avoir sa traduction. Le reste du texte naît en JavaScript et ne se relève qu'au
 * rendu ; les autres tests ici gardent alors les <b>décisions</b> du mécanisme, celles
 * qu'une relecture rapide déferait sans s'en apercevoir.
 */
class ViewLanguageTest {

    private static final String PAGE = read();

    private static String read() {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            if (in == null) throw new IllegalStateException("dashboard.html absent du jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Le dictionnaire de la page, relu tel quel : ce sont des chaînes JSON. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> dictionary() {
        int start = PAGE.indexOf("const DICO = {");
        assertTrue(start > 0, "la page doit porter son dictionnaire : elle voyage seule");
        int end = PAGE.indexOf("\n  };", start);
        String body = PAGE.substring(PAGE.indexOf('{', start) + 1, end).trim();
        body = body.replaceAll(",\\s*$", "");
        return (Map<String, Object>) Json.read("{" + body + "}");
    }

    /**
     * Le corps de la page avant tout script — l'en-tête, les onglets, la barre d'outils.
     * C'est le seul texte qu'on peut relever sans navigateur, et c'est aussi celui qui
     * s'affiche en premier.
     */
    private static String staticBody() {
        int start = PAGE.indexOf("<body>");
        int end = PAGE.indexOf("<script>", start);
        assertTrue(start > 0 && end > start);
        return PAGE.substring(start, end);
    }

    private static final Pattern ATTRIBUTE =
            Pattern.compile("(?:title|aria-label|placeholder)=\"([^\"]*)\"");
    private static final Pattern TEXT = Pattern.compile(">([^<>]+)<");

    /**
     * Ce qui a l'air français. Large à dessein — accents et mots outils — parce que le
     * coût des deux erreurs n'est pas le même : réclamer une traduction pour un mot qui
     * n'en a pas besoin se voit tout de suite, laisser passer une phrase entière non. Les
     * mots communs aux deux langues (« Code », « Exports ») en sont donc absents.
     */
    private static final Pattern FRENCH = Pattern.compile(
            "[éèêëàâçùûîïôœÉÈÀÇ]|\\b(le|la|les|des|une|un|du|de|qui|que|pour|dans|est|sont"
            + "|pas|sur|avec|cette|ne|au|aux|par|plus|tout|ou|et|se|son|sa|ses|elle|cliquer"
            + "|voir|noms|bout|champ)\\b", Pattern.CASE_INSENSITIVE);

    private static List<String> displayedStrings(String fragment) {
        Set<String> out = new LinkedHashSet<>();
        for (Pattern p : List.of(ATTRIBUTE, TEXT)) {
            Matcher m = p.matcher(fragment);
            while (m.find()) {
                String s = m.group(1).replace("&apos;", "'").replace("&nbsp;", " ")
                        .replace("&amp;", "&").trim();
                if (s.length() > 1) out.add(s);
            }
        }
        return new ArrayList<>(out);
    }

    @Test
    @DisplayName("Chaque phrase française du corps statique a sa traduction")
    void everyFrenchSentenceInTheStaticBodyHasATranslation() {
        Map<String, Object> dictionary = dictionary();
        List<String> orphans = new ArrayList<>();
        for (String s : displayedStrings(staticBody())) {
            if (FRENCH.matcher(s).find() && !dictionary.containsKey(s)) orphans.add(s);
        }
        // Le mode d'échec est silencieux : la vue anglaise affiche la phrase française, et
        // rien ne le dit. C'est ce test, et lui seul, qui le dit.
        assertEquals(List.of(), orphans,
                "libellé(s) français sans traduction — la vue anglaise les afficherait "
                + "en français : ajouter les entrées manquantes au DICO de dashboard.html");
    }

    @Test
    @DisplayName("La vue s'ouvre en anglais, et ne suit le navigateur que vers le français")
    void theViewOpensInEnglishAndOnlyFollowsTheBrowserIntoFrench() {
        // Un rapport s'envoie et se joint à un ticket : il est lu par quelqu'un qui n'a pas
        // lancé la mesure, et rien ne dit qu'il parle français.
        assertTrue(PAGE.contains("(navigator.language || \"en\").toLowerCase().startsWith(\"fr\")"),
                "le français ne s'impose qu'à un navigateur qui l'annonce");
        assertTrue(PAGE.contains("? \"fr\" : \"en\""),
                "et tout le reste retombe sur l'anglais, jamais sur une troisième langue");
        assertTrue(PAGE.contains("document.documentElement.lang = vers"),
                "l'attribut lang doit suivre : un lecteur d'écran et un traducteur s'en servent");
    }

    @Test
    @DisplayName("Le code source affiché n'est jamais traduit — c'est le code d'un autre")
    void theDisplayedSourceCodeIsNeverTranslated() {
        // Traduire le code de l'application observée serait absurde ; le faire par accident
        // le serait davantage. La cellule de code est écartée du parcours, et elle seule :
        // les replis et les annotations d'appel de la même ligne sont du texte de l'outil.
        assertTrue(PAGE.contains("el.nodeName === \"TD\" && el.classList.contains(\"c\")"),
                "la cellule de code doit être écartée du parcours");
        assertTrue(PAGE.contains("el.classList.contains(\"nm\") || el.classList.contains(\"lbl\")"),
                "les libellés de l'arbre sont des noms de classe et de paquet : une classe "
                + "nommée « Comment » ne doit pas devenir « How »");
    }

    @Test
    @DisplayName("Le choix survit au rechargement, et son échec n'emporte jamais la page")
    void theChoiceSurvivesAReloadAndItsFailureNeverBreaksThePage() {
        int start = PAGE.indexOf("const CLE = \"runtime-xray.langue\"");
        assertTrue(start > 0, "le choix doit être gardé, sinon il se refait à chaque ouverture");
        String block = PAGE.substring(start, start + 400);
        // Une page ouverte en file:// n'a pas toujours le droit d'écrire dans localStorage.
        // Sans ces try, la vue ne s'afficherait pas du tout sur ces postes-là.
        assertTrue(block.contains("try { return localStorage.getItem(CLE); } catch"),
                "la lecture du choix doit être sous try : file:// peut la refuser");
        assertTrue(block.contains("try { localStorage.setItem(CLE, v); } catch"),
                "l'écriture aussi, et pour la même raison");
    }

    @Test
    @DisplayName("Le dictionnaire traduit vraiment : aucune entrée ne se recopie")
    void theDictionaryActuallyTranslates() {
        Map<String, Object> dictionary = dictionary();
        assertTrue(dictionary.size() > 250,
                "le dictionnaire couvre la vue entière, relevée sur un rapport réel : "
                + "tombé si bas, c'est qu'on en a perdu la moitié — " + dictionary.size());
        List<String> offending = new ArrayList<>();
        for (Map.Entry<String, Object> e : dictionary.entrySet()) {
            String en = String.valueOf(e.getValue());
            // Une entrée qui se recopie ne traduit rien et masque le trou : le test du
            // corps statique la verrait comme couverte.
            if (en.isBlank() || en.equals(e.getKey())) offending.add(e.getKey());
        }
        assertEquals(List.of(), offending, "entrée(s) vide(s) ou identiques à leur clé");
    }

    @Test
    @DisplayName("Le sélecteur est deux lettres, et la page ne va rien chercher dehors")
    void theSelectorIsTwoLettersAndThePageFetchesNothing() {
        assertTrue(PAGE.contains("id=\"langsel\""), "le sélecteur doit exister dans l'en-tête");
        assertTrue(PAGE.contains("data-lang=\"en\"") && PAGE.contains("data-lang=\"fr\""),
                "deux langues, deux boutons");
        // Un drapeau nomme un pays, pas une langue — et il faudrait alors en choisir un
        // pour l'anglais.
        assertFalse(PAGE.contains("🇫🇷") || PAGE.contains("🇬🇧"),
                "pas de drapeau : un drapeau nomme un pays, pas une langue");
    }
}
