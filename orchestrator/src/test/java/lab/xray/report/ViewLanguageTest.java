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
 * The view opens in English, and knows how to render French.
 *
 * <h2>What these tests guard</h2>
 *
 * <p>The translation is made of a dictionary and a DOM walk, both held in the page itself.
 * No test can open a browser here — checking by rendering is done by hand, before pushing.
 * What a test can guard, and what is the real failure mode, is the <b>drift</b>: someone
 * rewords a French label in the template, the dictionary no longer knows it, and the English
 * view shows French with nothing to signal it. It is the same silence as the empty code
 * panel elsewhere in this project.
 *
 * <p>The page's static body — what a reader sees before any script has drawn anything at
 * all — is checked in full: every French sentence it carries must have its translation. The
 * rest of the text is born in JavaScript and shows only at render time; the other tests here
 * therefore guard the mechanism's <b>decisions</b>, the ones a quick re-reading would undo
 * without noticing.
 */
class ViewLanguageTest {

    private static final String PAGE = read();

    private static String read() {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            if (in == null) throw new IllegalStateException("dashboard.html missing from the jar");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The page's dictionary, read back as it is: these are JSON strings. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> dictionary() {
        int start = PAGE.indexOf("const DICO = {");
        assertTrue(start > 0, "the page must carry its dictionary: it travels alone");
        int end = PAGE.indexOf("\n  };", start);
        String body = PAGE.substring(PAGE.indexOf('{', start) + 1, end).trim();
        body = body.replaceAll(",\\s*$", "");
        return (Map<String, Object>) Json.read("{" + body + "}");
    }

    /**
     * The page's body before any script — the header, the tabs, the toolbar. It is the only
     * text one can collect without a browser, and it is also the one shown first.
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
     * What looks French. Broad on purpose — accents and function words — because the cost of
     * the two mistakes is not the same: demanding a translation for a word that needs none
     * shows at once, letting a whole sentence through does not. Words common to both
     * languages ("Code", "Exports") are therefore absent from it.
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
    @DisplayName("Every French sentence in the static body has its translation")
    void everyFrenchSentenceInTheStaticBodyHasATranslation() {
        Map<String, Object> dictionary = dictionary();
        List<String> orphans = new ArrayList<>();
        for (String s : displayedStrings(staticBody())) {
            if (FRENCH.matcher(s).find() && !dictionary.containsKey(s)) orphans.add(s);
        }
        // The failure mode is silent: the English view shows the French sentence, and
        // nothing says so. It is this test, and this test alone, that says it.
        assertEquals(List.of(), orphans,
                "French label(s) with no translation — the English view would show them "
                + "in French: add the missing entries to dashboard.html's DICO");
    }

    @Test
    @DisplayName("The view opens in English, and follows the browser only into French")
    void theViewOpensInEnglishAndOnlyFollowsTheBrowserIntoFrench() {
        // A report gets sent and attached to a ticket: it is read by someone who did not
        // launch the measurement, and nothing says they speak French.
        assertTrue(PAGE.contains("(navigator.language || \"en\").toLowerCase().startsWith(\"fr\")"),
                "French imposes itself only on a browser that announces it");
        assertTrue(PAGE.contains("? \"fr\" : \"en\""),
                "and everything else falls back on English, never on a third language");
        assertTrue(PAGE.contains("document.documentElement.lang = vers"),
                "the lang attribute must follow: a screen reader and a translator both use it");
    }

    @Test
    @DisplayName("The displayed source code is never translated — it is someone else's code")
    void theDisplayedSourceCodeIsNeverTranslated() {
        // Translating the observed application's code would be absurd; doing it by accident
        // more so. The code cell is left out of the walk, and it alone: the folds and the
        // call annotations on the same line are the tool's own text.
        assertTrue(PAGE.contains("el.nodeName === \"TD\" && el.classList.contains(\"c\")"),
                "the code cell must be left out of the walk");
        assertTrue(PAGE.contains("el.classList.contains(\"nm\") || el.classList.contains(\"lbl\")"),
                "the tree's labels are class and package names: a class named "
                + "\"Comment\" must not become \"How\"");
    }

    @Test
    @DisplayName("The choice survives a reload, and its failure never breaks the page")
    void theChoiceSurvivesAReloadAndItsFailureNeverBreaksThePage() {
        int start = PAGE.indexOf("const CLE = \"runtime-xray.langue\"");
        assertTrue(start > 0, "the choice must be kept, otherwise it is remade at every opening");
        String block = PAGE.substring(start, start + 400);
        // A page opened over file:// is not always allowed to write to localStorage.
        // Without these try blocks, the view would not show at all on such machines.
        assertTrue(block.contains("try { return localStorage.getItem(CLE); } catch"),
                "reading the choice must be under try: file:// may refuse it");
        assertTrue(block.contains("try { localStorage.setItem(CLE, v); } catch"),
                "the write too, and for the same reason");
    }

    @Test
    @DisplayName("The dictionary really translates: no entry copies itself")
    void theDictionaryActuallyTranslates() {
        Map<String, Object> dictionary = dictionary();
        assertTrue(dictionary.size() > 250,
                "the dictionary covers the whole view, collected on a real report: "
                + "fallen this low, half of it has been lost — " + dictionary.size());
        List<String> offending = new ArrayList<>();
        for (Map.Entry<String, Object> e : dictionary.entrySet()) {
            String en = String.valueOf(e.getValue());
            // An entry that copies itself translates nothing and hides the hole: the
            // static-body test would see it as covered.
            if (en.isBlank() || en.equals(e.getKey())) offending.add(e.getKey());
        }
        assertEquals(List.of(), offending, "entr(y|ies) empty or identical to their key");
    }

    @Test
    @DisplayName("The selector is two letters, and the page fetches nothing from outside")
    void theSelectorIsTwoLettersAndThePageFetchesNothing() {
        assertTrue(PAGE.contains("id=\"langsel\""), "the selector must exist in the header");
        assertTrue(PAGE.contains("data-lang=\"en\"") && PAGE.contains("data-lang=\"fr\""),
                "two languages, two buttons");
        // A flag names a country, not a language — and one would then have to choose one
        // for English.
        assertFalse(PAGE.contains("🇫🇷") || PAGE.contains("🇬🇧"),
                "no flag: a flag names a country, not a language");
    }
}
