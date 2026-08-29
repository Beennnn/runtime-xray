package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names the view writes to disk, and which a rename must never touch.
 *
 * <p>The page's own identifiers are free — they are read by nobody but itself, and the
 * rendered snapshot proves a rename changed nothing. Its <b>keys</b> are not: they go into
 * {@code config.json} beside the runs, into the exported annotations file, and into the
 * body of a save request. A key renamed here is not a compile error and not a rendering
 * difference; it is a value written under a name nothing reads.
 *
 * <p>That is not a hypothesis. Translating the view's identifiers to English on
 * 29 August 2026 turned the string {@code "nom"} into {@code "name"} in the two calls that
 * record a run's name — while every reader, the exporter and the server payload went on
 * asking for {@code nom}. A name typed in the page was written into a field nobody read,
 * and nothing anywhere said so: the page showed the new name, because it re-read the value
 * it had just put in the input.
 */
class ViewContractTest {

    /**
     * The annotation fields, frozen: they are what {@code config.json} carries beside a run
     * and what the exported file hands back.
     */
    private static final List<String> FIELDS =
            List.of("nom", "description", "etiquettes", "elagage");

    private static String template() {
        try (InputStream in = Dashboard.class.getResourceAsStream("/lab/xray/dashboard.html")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("The view only ever patches an annotation field that exists")
    void everyPatchedFieldIsAFrozenOne() {
        Matcher m = Pattern.compile("annotationPatch\\([^,]+,\\s*\"([^\"]+)\"").matcher(template());
        Set<String> patched = new LinkedHashSet<>();
        while (m.find()) patched.add(m.group(1));
        assertTrue(patched.size() >= 3, "the calls must stay findable: " + patched);
        for (String field : patched) {
            assertTrue(FIELDS.contains(field),
                    "the view writes an annotation field \"" + field + "\" that nothing reads. "
                    + "These keys travel to disk and back: they are frozen, whatever the "
                    + "language of the code around them. Known fields: " + FIELDS);
        }
    }

    @Test
    @DisplayName("A run's name is still read under the key it is written with")
    void theNameIsReadWhereItIsWritten() {
        String view = template();
        assertTrue(view.contains("annotationPatch(R, \"nom\""),
                "the identity card records the name under \"nom\"");
        assertTrue(view.contains("a.nom"),
                "and something reads it back — a writer with no reader is the whole defect");
    }

    @Test
    @DisplayName("What a save sends keeps the names the server expects")
    void theSavePayloadKeepsItsKeys() {
        String view = template();
        // The body is {base: <fingerprint>, valeur: <annotation>}; the answer carries
        // "empreinte". Three keys shared with Java, and none of them is English.
        for (String key : List.of("valeur:", "base:", "empreinte")) {
            assertTrue(view.contains(key),
                    "the save exchange lost the key \"" + key + "\": the page and the "
                    + "server would then agree on nothing");
        }
    }

    @Test
    @DisplayName("The block loader's only published name is the one the blocks call")
    void theBlockFilesOnlyKnowOneName() {
        String view = template();
        // Every vue/*.js file on disk is a list of XR.bloc(...) lines — the format is even
        // documented as being readable with sed. The loader's other members are the page's
        // own business and were renamed to English; this one cannot be.
        assertTrue(view.contains("bloc(type, srcKey, val)"),
                "XR.bloc is the block files' entry point, and those files are already written");
        assertEquals(0, count(view, "XR.charger"),
                "the loader's internals are English now, and no block file names them");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }
}
