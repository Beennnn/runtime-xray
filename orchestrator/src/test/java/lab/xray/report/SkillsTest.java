package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two skills in {@code skills/} are read by an assistant, usually on the observed
 * machine — so with no network, no repository, and nobody to fix anything.
 *
 * <p>An option renamed here and left written over there fails nowhere: it sends someone
 * into a wall, with "Unknown option" for the whole explanation, on the machine where they
 * can check nothing. Hence these tests — they do not guard prose, they guard the agreement
 * between what the code accepts and what the skill promises.
 */
class SkillsTest {

    private static Path root() {
        // Surefire runs with the module's directory as base; the repository is one above.
        return Path.of("").toAbsolutePath().getParent();
    }

    private static String read(String skill) throws Exception {
        Path f = root().resolve("skills").resolve(skill).resolve("SKILL.md");
        assertTrue(Files.exists(f), "skill not found: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    /** The options the code really accepts, read from Main's {@code switch}. */
    private static Set<String> optionsInCode() throws Exception {
        String source = Files.readString(root().resolve("orchestrator").resolve("src")
                .resolve("main").resolve("java").resolve("lab").resolve("xray")
                .resolve("Main.java"), StandardCharsets.UTF_8);
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("case \"(--[a-z-]+)\"").matcher(source);
        while (m.find()) out.add(m.group(1));
        // The switch writes them grouped: {@code case "-h", "--help"}.
        Matcher m2 = Pattern.compile(", \"(--[a-z-]+)\"").matcher(source);
        while (m2.find()) out.add(m2.group(1));
        assertTrue(out.size() > 15, "the extraction failed: " + out);
        return out;
    }

    private static Set<String> quotedOptions(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = Pattern.compile("`?(--[a-z][a-z-]+)").matcher(text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    @Test
    @DisplayName("Every option quoted by a skill really exists in the tool")
    void everyOptionQuotedByASkillReallyExists() throws Exception {
        Set<String> real = optionsInCode();
        for (String skill : new String[]{"runtime-xray", "runtime-xray-read"}) {
            for (String quoted : quotedOptions(read(skill))) {
                assertTrue(real.contains(quoted), "skill " + skill
                        + " quotes " + quoted + ", which the tool refuses. On the observed machine,"
                        + " the reader has only \"Unknown option\" to get out of it. Known: "
                        + real);
            }
        }
    }

    @Test
    @DisplayName("The fact vocabulary promised to the reader is the one the tool writes")
    void thePromisedVocabularyIsTheOneWritten() throws Exception {
        String read = read("runtime-xray-read");
        for (String family : Facts.VOCABULARY.keySet()) {
            assertTrue(read.contains(family), "the reading skill says nothing about \""
                    + family + "\": the reader will meet a line they cannot read");
        }
        // The converse counts as much: a family announced and never written gets searched
        // for a long time in a file that does not contain it. We look only at the vocabulary
        // table — the document's other tables speak of files, not of families.
        int start = read.indexOf("## The vocabulary");
        assertTrue(start > 0, "the vocabulary table must exist under this heading");
        String table = read.substring(start, read.indexOf("\n## ", start + 4));
        Matcher m = Pattern.compile("\\| `([a-z][a-z._]+)` \\|").matcher(table);
        while (m.find()) {
            assertTrue(Facts.VOCABULARY.containsKey(m.group(1)),
                    "the skill announces family \"" + m.group(1)
                    + "\", which the tool never writes");
        }
    }

    @Test
    @DisplayName("The four restrictions are said to be distinct, where they get confused")
    void theFourRestrictionsAreSaidToBeDistinct() throws Exception {
        // This is the tool's costliest confusion: one widens the filter that had nothing
        // to do with it, re-runs a whole campaign, and gets the same report. Both skills
        // must defuse it, each from its own side of the problem.
        for (String skill : new String[]{"runtime-xray", "runtime-xray-read"}) {
            String text = read(skill);
            for (String option : new String[]{"--sources", "--root", "--filter", "--cover"}) {
                assertTrue(text.contains(option),
                        skill + " does not name " + option + " among the restrictions");
            }
        }
    }

    @Test
    @DisplayName("A zero that was not measured is announced as such, not as a finding")
    void aZeroThatWasNotMeasuredIsAnnouncedAsSuch() throws Exception {
        // Without that sentence, a zero time under Windows reads as "never called", and the
        // conclusion falls the wrong way, confidently.
        String read = read("runtime-xray-read");
        assertTrue(read.contains("indisponibilite"));
        assertTrue(read.contains("Windows"), "the concrete case is worth more than the rule alone");
        assertTrue(read("runtime-xray").contains("Windows"));
    }

    @Test
    @DisplayName("Each skill carries the header that makes it triggerable")
    void eachSkillCarriesTheHeaderThatMakesItTriggerable() throws Exception {
        for (String skill : new String[]{"runtime-xray", "runtime-xray-read"}) {
            String text = read(skill);
            assertTrue(text.startsWith("---\n"), skill + ": no header");
            String header = text.substring(0, text.indexOf("\n---", 4));
            assertTrue(header.contains("name: " + skill),
                    skill + ": the declared name must be the directory's");
            // A description without the vocabulary of the question never triggers: the
            // skill exists, and nobody sees it.
            assertTrue(header.contains("description:"));
            assertTrue(header.length() > 200, skill + ": description too thin to be "
                    + "chosen on anything but its name");
            assertFalse(text.contains("runtime-xray-cli"),
                    skill + ": the published jar's name changes from version to version, "
                    + "writing it here makes it stale");
        }
    }
}
