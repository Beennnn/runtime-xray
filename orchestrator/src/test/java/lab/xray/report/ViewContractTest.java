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

    @Test
    @DisplayName("A data file that cannot be read is said in the page, not only in the console")
    void aMissingBlockIsNeverSilent() {
        String view = template();
        // The page travels as a single file; the tree, the code and the captured values are
        // read beside it, under runs/, as they are opened. Deployed alone — a very easy
        // thing to do, since the page opens perfectly — the overview works and everything
        // one clicks stays shut. That used to leave a console error and nothing else: the
        // node simply did not open, and the report read like a report with nothing in it.
        assertTrue(view.contains("XR.absent(blockName);"),
                "the loader must record a block it could not read");
        assertTrue(view.contains("sayBlocksMissing()"),
                "and something must say it where the reader is looking");
        assertTrue(view.contains("This report's data files were not found."),
                "with the sentence that names the situation");
        assertTrue(view.contains("Deploy the whole output directory"),
                "and the one that says what to do about it — a warning with no way out is "
                + "a complaint");
        // Said in both languages: a band that appears only in English inside a French view
        // is the drift the dictionary exists to prevent.
        assertTrue(view.contains("Les fichiers de données de ce rapport n'ont pas été trouvés."),
                "the band must have its translation, like every sentence the page shows");
    }

    @Test
    @DisplayName("A tree with nothing under it says why, and not the same thing every time")
    void anEmptyTreeSaysWhy() {
        String view = template();
        // A run root that opens onto nothing reads exactly like a run that executed
        // nothing. It almost never is that — and until this, the page said nothing at all:
        // the twisty turned, no row appeared, and the console held no error either,
        // because nothing had failed. A whole week was spent looking for a deployment
        // problem that did not exist, on a machine where the profiler simply does not run.
        assertTrue(view.contains("holder.appendChild(whyEmpty(run, measured))"),
                "an empty holder must be filled with its reason, wherever it is discovered");
        // The five causes are fixed in five different ways, so one sentence for all of
        // them would send four readers out of five to the wrong place.
        for (String said : List.of(
                "No frame of this run matches the search.",
                "No execution profile for this run.",
                "Every frame of this run is in a hidden package.",
                "The profile is full, and no source was found for the methods it names.",
                "Nothing in this profile has a source to open.")) {
            assertTrue(view.contains("say(\"" + said + "\", true)"),
                    "the tree lost the case it named: " + said);
            assertTrue(view.contains("\"" + said + "\":"),
                    "and its translation — a band that shows in English inside a French "
                    + "view is the drift the dictionary exists to prevent: " + said);
        }
        // The platform reason is READ, never guessed: it is the run's own context that
        // says which system measured it, and Windows is the one with no profiler at all.
        assertTrue(view.contains("/^windows/i.test(c.systeme"),
                "the system comes from the run's launch context");
        assertTrue(view.contains("async-profiler, which publishes no binary for Windows"),
                "and the sentence must name the tool and the platform: on the machine "
                + "where this is read, there is nothing else to check it against");
    }

    @Test
    @DisplayName("The page keeps its own trace, bounded, and hands it over in one gesture")
    void thePageKeepsAnActivityLog() {
        String view = template();
        // Everything that settles "it does not work on my machine" used to live in the
        // browser's console: nobody opens it, it keeps nothing once the tab is closed, and
        // one cannot ask for it over the telephone. That is what cost a week on a call
        // tree that opened onto nothing.
        assertTrue(view.contains("function note(what, detail, repeats)"),
                "the page must have one way of writing down what it does");
        assertTrue(view.contains("MAX: 400"),
                "and a ceiling: a page left open all afternoon must not grow all afternoon");
        assertTrue(view.contains("id=\"journallink\""),
                "reachable without a console — otherwise it is the console over again");
        assertTrue(view.contains("Activity log"), "under a name that says what it is");
        assertTrue(view.contains("Journal d'activité"), "in both languages, like the rest");
        // An uncaught error is the one thing that left no trace at all. It is written down
        // before any drawing has had the chance to throw.
        assertTrue(view.contains("window.addEventListener(\"error\", e =>"),
                "an uncaught error goes into the log");
        assertTrue(view.contains("window.addEventListener(\"unhandledrejection\", e =>"),
                "and so does a rejected promise — the block loader works with those");
        // The same rule as everywhere else in this tool: an observed argument never
        // travels. Class and method names do — the reader already has the whole report.
        assertTrue(view.contains("No captured value goes into it."),
                "the entry says so where it is offered, because the log is meant to be sent");
        Matcher m = Pattern.compile("note\\(\"[^\"]+\", ([^;]*)\\)[;,]").matcher(view);
        while (m.find()) {
            String arg = m.group(1);
            assertTrue(!arg.contains("params") && !arg.contains(".value"),
                    "a note that carries a captured value: " + arg);
        }
    }

    @Test
    @DisplayName("One level of the tree lays down a bounded number of calls, and says so")
    void oneLevelIsBounded() {
        String view = template();
        // The tree is never built in advance — one pays only for what one opens — but ONE
        // level was built whole, however wide it was. And a level's width is bounded by
        // nothing: in place of a frame without source, the display pulls up all of its
        // readable descendants, so a run whose top frames are virtual-machine cogs shows
        // tens of thousands of children at the first click. Measured: 4 000 children take
        // 0.36 s to lay down, 60 000 take 10.6 s — and a fold-then-reopen pays it again.
        assertTrue(view.contains("const LEVEL_BUDGET = 400;"),
                "a level has a ceiling, or a wide profile freezes the click that opens it");
        // Truncating in silence would be this project's own failure mode: a level that
        // stops without saying so reads as a level that holds nothing more.
        assertTrue(view.contains("calls shown at this level, the heaviest first."),
                "the row that says how many were held back");
        assertTrue(view.contains("appels sur \" + m[2] + \" affichés à ce niveau"),
                "and its translation: the sentence carries a figure, so it travels as a "
                + "pattern rather than as a key");
        assertTrue(view.contains("btn.textContent = \"show \" +"),
                "and the click that gives the rest back — a ceiling with no way past it "
                + "would hide measurements rather than defer them");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }
}
