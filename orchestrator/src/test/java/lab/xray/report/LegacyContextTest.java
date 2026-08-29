package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A campaign measured before the switch to English, reassembled today.
 *
 * <p>{@code --report-only} rewrites the page, the facts and the diagnostic from the
 * measurements in {@code runs/} — but it <b>reads</b> {@code run-context.json} rather than
 * rewriting it: the run recorded what it did, and reassembling deduces nothing. So the
 * fields an older tool wrote in French are still there, and they reach the page as they are.
 *
 * <p>Only one of them was ever composed by the tool. The command, the machine, the name
 * given at launch belong to whoever measured — a run called "Scénario 1" must stay so, and
 * translating it would be a lie about what was typed. The <b>status</b> is the tool's own
 * sentence, and it went to English in August 2026.
 *
 * <p>While the view's dictionary went from French to English, an old status was translated
 * on its way to the screen and nobody noticed. Turning the view round removed that, for the
 * right reason: asking for English now costs no walk at all. The compatibility therefore
 * moves to the reader — which is where a legacy format belongs.
 */
class LegacyContextTest {

    private static Map<String, Object> context(String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commande", "java -jar app.jar");
        m.put("nomOrigine", "Scénario complet");
        m.put("statut", status);
        return m;
    }

    @Test
    @DisplayName("A status written in French by an older tool is read back in English")
    void aFrenchStatusIsReadBackInEnglish() {
        assertEquals("ended normally (code 0)",
                Dashboard.englishStatus(context("terminée normalement (code 0)")).get("statut"));
        assertEquals("ended normally (code 137)",
                Dashboard.englishStatus(context("terminée normalement (code 137)")).get("statut"));
        // The other shape the tool ever wrote: the safety limit cut the run short.
        assertEquals("stopped after 900 s (safety limit)",
                Dashboard.englishStatus(context("interrompue après 900 s (garde-fou)")).get("statut"));
    }

    @Test
    @DisplayName("A status already in English is left alone")
    void anEnglishStatusIsLeftAlone() {
        assertEquals("ended normally (code 0)",
                Dashboard.englishStatus(context("ended normally (code 0)")).get("statut"));
        assertEquals("stopped after 60 s (safety limit)",
                Dashboard.englishStatus(context("stopped after 60 s (safety limit)")).get("statut"));
    }

    @Test
    @DisplayName("What the operator typed is never touched — a French run name stays French")
    void whatTheOperatorTypedIsNeverTouched() {
        // The name given at launch is a fact about the campaign, not a label of the tool.
        // Translating it would make the report disagree with the command that produced it.
        Map<String, Object> read = Dashboard.englishStatus(context("terminée normalement (code 0)"));
        assertEquals("Scénario complet", read.get("nomOrigine"));
        assertEquals("java -jar app.jar", read.get("commande"));
    }

    @Test
    @DisplayName("A context with no status, or an unknown one, goes through untouched")
    void anUnknownStatusGoesThroughUntouched() {
        Map<String, Object> without = new LinkedHashMap<>();
        without.put("commande", "java -jar app.jar");
        assertEquals(null, Dashboard.englishStatus(without).get("statut"));
        // Never invent: a sentence we do not recognise is shown as it was written.
        assertEquals("écrasée par un tiers",
                Dashboard.englishStatus(context("écrasée par un tiers")).get("statut"));
    }
}
