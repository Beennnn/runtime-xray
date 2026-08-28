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
 * The context pack is what a model will read <b>instead of</b> the report. What is taken out
 * of it, the model will not learn; what is put in without saying so, it will take for the
 * whole truth. Hence these tests: they guard less a format than an honesty.
 */
class ContextTest {

    private static Path factsFile(Path dir, int deadClasses) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add(Json.write(Map.of("fact", "campaign", "outil", "runtime-xray",
                "runs", 2, "vocabulary", Facts.VOCABULARY)));
        lines.add(Json.write(Map.of("fact", "unavailable", "run", "B",
                "what", "time", "why", "no stack sample was taken",
                "consequence", "no time percentage can be computed",
                "remedy", "re-run on Linux")));
        lines.add(Json.write(Map.of("fact", "caveat", "run", "A", "what", "time",
                "caveat", "some of the samples were attributed to the caller")));
        for (int i = 0; i < deadClasses; i++) {
            lines.add(Json.write(Map.of("fact", "class.never_executed",
                    "classe", "com.example.app.Class" + i,
                    "runsAnalysed", List.of("A", "B"))));
        }
        Path f = dir.resolve(Facts.FILE);
        Files.write(f, lines, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("What was NOT measured comes BEFORE the figures, never after")
    void whatWasNotMeasuredComesFirst(@TempDir Path dir) throws Exception {
        factsFile(dir, 3);
        String pkg = Context.of(dir, "which classes never ran?",
                Context.BUDGET);

        int unavailabilities = pkg.indexOf("NOT measured");
        int facts = pkg.indexOf("```jsonl");
        assertTrue(unavailabilities > 0, "the section must exist");
        assertTrue(unavailabilities < facts,
                "a reader who sees the figures first has already interpreted them by the "
                + "time they reach the caveats: the order IS the information");
        assertTrue(pkg.contains("no stack sample was taken"));
        assertTrue(pkg.contains("some of the samples were attributed to the caller"),
                "a caveat bears on a measurement that exists: it counts too");
    }

    @Test
    @DisplayName("A tight budget never drops an unavailability, only measurements")
    void theBudgetNeverDropsAnUnavailability(@TempDir Path dir) throws Exception {
        factsFile(dir, 400);
        // A budget that lets only the header through: this is the edge case that decides.
        String pkg = Context.of(dir, "classes jamais exécutées", 3_000);

        assertTrue(pkg.contains("no stack sample was taken"),
                "a missing measurement is what prevents a false conclusion: it stays, "
                + "even when everything else is cut");
        assertTrue(pkg.contains("left out"),
                "a silent truncation makes one conclude on a sample believing one sees everything");
        assertTrue(pkg.contains("INCOMPLETE"));
        assertTrue(pkg.length() < 3_000 + 2_000,
                "the budget must be held to within the warning, otherwise it serves no purpose");
    }

    @Test
    @DisplayName("The vocabulary and the reading traps travel with the facts")
    void theLegendTravelsWithTheData(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        String pkg = Context.of(dir, "", Context.BUDGET);

        // A model that has never seen this format must be able to read it: we attach the
        // legend, not a link to it.
        assertTrue(pkg.contains("class.never_executed"));
        assertTrue(pkg.contains("Three reading traps"));
        assertTrue(pkg.contains("does not mean \"never ran\""));
        assertTrue(pkg.contains("says nothing about whether it is correct"),
                "a coverage rate taken for a quality grade is the most ordinary mistake, "
                + "and a model makes it too");
    }

    @Test
    @DisplayName("A question one cannot classify gives the overview, not emptiness")
    void anUnrecognisedQuestionStillGetsAnAnswerablePack(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        String pkg = Context.of(dir, "est-ce que ce truc marche bien ?", Context.BUDGET);
        assertTrue(pkg.contains("class.never_executed"),
                "for want of better, we give something to answer with, rather than an empty pack");
    }

    @Test
    @DisplayName("Without an assembled report, we say what to do — we invent no context")
    void withoutAReportItSaysWhatToDo(@TempDir Path dir) {
        Exception e = assertThrows(Exception.class,
                () -> Context.of(dir, "une question", Context.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("--report-only"),
                "the message must carry the gesture that unblocks");
    }

    @Test
    @DisplayName("An empty fact fence explains itself, otherwise it reads as a failed extraction")
    void anEmptyFenceExplainsItself(@TempDir Path dir) throws Exception {
        // The campaign holds facts, but none of the family asked for. Without a word, the
        // reader cannot decide between "there are none" and "the tool failed": two opposite
        // conclusions from the same emptiness.
        factsFile(dir, 0);
        String pkg = Context.of(dir, "which classes never ran?",
                Context.BUDGET);

        assertTrue(pkg.contains("No fact of these families"));
        assertTrue(pkg.contains("Families present in the file"),
                "saying what IS there is what allows concluding that the rest really is missing");
        assertTrue(pkg.contains("unavailable"),
                "the list of families present must be the file's, not a guess");
    }

    @Test
    @DisplayName("An integer count is written 29, never 29.0 — in the header as in the facts")
    void anIntegerCountIsWrittenAsAnInteger(@TempDir Path dir) throws Exception {
        // JSON read back renders every number as a double: a round trip turns 41 into 41.0.
        // It is not wrong, but it is the detail that casts doubt on the rest — for a human
        // reader as much as for a model.
        Path f = factsFile(dir, 0);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + "{\"fact\":\"method.hot\",\"method\":\"com.example.app.Repository.load\","
                + "\"samples\":41,\"pct\":100.0}\n", StandardCharsets.UTF_8);

        String pkg = Context.of(dir, "where does the time go?", Context.BUDGET);
        assertTrue(pkg.contains("runs : 2"));
        assertFalse(pkg.contains("runs : 2.0"));
        assertTrue(pkg.contains("\"samples\":41"), "the original line is copied as it is");
        assertFalse(pkg.contains("41.0"));
        assertTrue(pkg.contains("\"pct\":100.0"),
                "and a float stays a float: we copy, we do not reformat");
    }

    @Test
    @DisplayName("No captured value ever leaves for a third-party service")
    void noCapturedValueEverLeaves(@TempDir Path dir) throws Exception {
        // The facts carry none — a Facts test guards that. This one guards the next link:
        // what LEAVES the machine for a model, sometimes hosted elsewhere.
        Path f = factsFile(dir, 1);
        Files.writeString(f, Files.readString(f, StandardCharsets.UTF_8)
                + Json.write(Map.of("fact", "classe", "classe", "com.example.app.A",
                        "pct", 12)) + "\n", StandardCharsets.UTF_8);

        String pkg = Context.of(dir, "couverture", Context.BUDGET);
        assertFalse(pkg.contains("motDePasse"));
        assertFalse(pkg.contains("params"),
                "parameter values stay in their block, on disk");
    }

    @Test
    @DisplayName("Naming the families wins over the question, and the result no longer depends on words")
    void namingTheFamiliesWinsOverTheQuestion(@TempDir Path dir) throws Exception {
        factsFile(dir, 2);
        // A question whose keywords point at time, but a script asking for the dead
        // classes: the explicit request must win, otherwise the script would produce
        // something other than what it believes it is reading.
        Context.Pack p = Context.of(dir, "where does the time go?",
                List.of("class.never_executed"), Context.BUDGET);

        assertEquals(Context.Origin.REQUESTED, p.origin());
        assertEquals(List.of("class.never_executed"), p.families());
        assertTrue(p.text().contains("class.never_executed"));
        assertFalse(p.text().contains("methode.chaude\""),
                "the question must choose nothing once the families are named");
        // It does keep travelling, though: that is its other role.
        assertTrue(p.text().contains("where does the time go?"));
    }

    @Test
    @DisplayName("An unknown family stops dead, with the list of the ones that exist")
    void anUnknownFamilyStopsAndListsTheRealOnes(@TempDir Path dir) throws Exception {
        factsFile(dir, 1);
        // The opposite of free text, and deliberately so: a sentence comes from a human
        // searching, a family comes from a script. A script that gets it wrong has a defect,
        // and keeping quiet about it would produce a pack silently different from what it
        // expects.
        Exception e = assertThrows(Exception.class, () -> Context.of(dir, "",
                List.of("classes.mortes"), Context.BUDGET));
        assertTrue(String.valueOf(e.getMessage()).contains("classes.mortes"));
        assertTrue(String.valueOf(e.getMessage()).contains("class.never_executed"),
                "say what exists, not only that what was given does not");
    }

    @Test
    @DisplayName("The operator learns what was understood of their question, or that it was not")
    void theOperatorLearnsWhatWasUnderstood(@TempDir Path dir) throws Exception {
        factsFile(dir, 1);
        // The heart of the problem: a question written out in full LOOKS understood.
        // Without this feedback, nobody knows that "which classes never ran?" boils down to
        // the single word "never".
        Context.Pack recognised = Context.of(dir, "which classes never ran?",
                List.of(), Context.BUDGET);
        assertEquals(Context.Origin.KEYWORDS, recognised.origin());
        assertTrue(recognised.announcement().contains("from the question"));

        Context.Pack silent = Context.of(dir, "welche Klassen liefen nie",
                List.of(), Context.BUDGET);
        assertEquals(Context.Origin.OVERVIEW, silent.origin());
        assertTrue(silent.announcement().contains("no keyword recognised"),
                "a fallback that passes for a successful reading is worse than no fallback");
        assertFalse(silent.families().isEmpty(), "we answer all the same");
    }

    @Test
    @DisplayName("Every English keyword is written in the help: otherwise it cannot be found")
    void everyEnglishKeywordIsWrittenInTheHelp() throws Exception {
        // The reproach made to this option is that it is not discoverable: "--help" cannot
        // enumerate what works. It can, provided the table does not rot — and a documentation
        // table breaks no build.
        String help = help();
        for (Map.Entry<String, String[]> e : Context.WORDS.entrySet()) {
            for (String word : e.getValue()) {
                assertTrue(help.contains(word), "keyword \"" + word + "\" triggers \""
                        + e.getKey() + "\" but is written nowhere in the help: "
                        + "nobody can guess it");
            }
        }
    }

    @Test
    @DisplayName("The French words work without being documented, and the help says so")
    void theFrenchWordsWorkWithoutBeingDocumented() throws Exception {
        // The tool speaks English: a second table in the help would make it unreadable for
        // those it addresses. But keeping their existence entirely quiet would make a French
        // speaker believe their question was not read.
        assertEquals(Context.WORDS.keySet(), Context.WORDS_FR.keySet(),
                "every documented family must have its French words, and the converse");
        assertEquals(List.of("class.never_executed"),
                Context.recognisedFamilies("quelles classes n'ont jamais tourné ?"));
        assertEquals(List.of("method.hot"), Context.recognisedFamilies("quel est le coût ?"),
                "accents must not prevent recognition");
        assertTrue(help().contains("French words are recognised too"),
                "their existence must be stated, even without the table");
    }

    @Test
    @DisplayName("A keyword opens a word: \"screenshot\" does not trigger \"hot\"")
    void aKeywordOpensAWordItIsNotFoundAnywhere() {
        // Searching anywhere in the string looked more generous and turned against us. The
        // switch to English made the defect worse: "hot" lives inside "screenshot", "rate"
        // inside "generate", "cout" inside "écouter".
        assertTrue(Context.recognisedFamilies("show me a screenshot").isEmpty());
        assertTrue(Context.recognisedFamilies("generate a report").isEmpty());
        assertTrue(Context.recognisedFamilies("je veux écouter les journaux").isEmpty());
        // Inflections, on the other hand, are still caught: that is the whole point of a word start.
        assertEquals(List.of("source.missing", "source.hint"),
                Context.recognisedFamilies("il manque des sources"));
        assertEquals(List.of("class.never_executed"),
                Context.recognisedFamilies("des classes inutilisées"));
    }

    @Test
    @DisplayName("A 1.0 report points at --report-only, for want of a migrator that does not exist")
    void aFormatOneReportPointsAtReportOnly(@TempDir Path dir) throws Exception {
        // There is no migration tool, and that is a finding: faits.jsonl is derived from
        // the measurements, so --report-only rewrites it entirely in the current format,
        // picking up every accumulated fix along the way. A migrator would only have renamed
        // keys in a file left stale in every other respect.
        Files.writeString(dir.resolve(Facts.FILE),
                "{\"fait\":\"campagne\",\"outil\":\"runtime-xray\"}\n"
                + "{\"fait\":\"classe.jamais_executee\",\"classe\":\"a.B\"}\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(dir.resolve("runs"));

        Exception withSamples = assertThrows(Exception.class,
                () -> Context.of(dir, "", Context.BUDGET));
        assertTrue(String.valueOf(withSamples.getMessage()).contains("--report-only"),
                "the measurements are there: the gesture that unblocks is to regenerate");

        // With runs/ gone, nothing can be regenerated — and saying so is worth more than
        // offering a command that will fail.
        Files.delete(dir.resolve("runs"));
        Exception withoutSamples = assertThrows(Exception.class,
                () -> Context.of(dir, "", Context.BUDGET));
        assertFalse(String.valueOf(withoutSamples.getMessage()).contains("--report-only"));
        assertTrue(String.valueOf(withoutSamples.getMessage()).contains("runs/"));
    }

    @Test
    @DisplayName("The 1.0 family names are still accepted by --families")
    void theFormatOneFamilyNamesAreStillAccepted(@TempDir Path dir) throws Exception {
        // An acceptance script written against 1.0 must not stop because the format changed
        // language.
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
