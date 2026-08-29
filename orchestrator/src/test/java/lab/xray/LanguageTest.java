package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the user reads is in English.
 *
 * <h2>Why a test, and not a re-reading</h2>
 *
 * <p>The switch to English was made in steps, and each one left behind a repository where
 * <b>both languages legitimately coexist</b>: the code, the comments and the
 * {@code @DisplayName}s stay French until their step comes. A French sentence added inside
 * a {@code println} therefore does not stand out on re-reading — it looks like everything
 * around it. It shows only at run time, on the machine of someone who does not read French.
 *
 * <h2>What is looked for, and what is not</h2>
 *
 * <p>A <b>sentence</b>: at least four words, and one French function word. This is
 * deliberately narrow. Identifiers stay out of reach — and that is necessary, because many
 * are French on purpose and will stay so: the measurements' JSON keys
 * ({@code methodeRacine}, {@code dureeSecondes}), the levels' internal names
 * ({@code couverture}, {@code arbre}, {@code complet}) and the French option names, which
 * are accepted for life so as to break no already-deployed script.
 *
 * <p>This test therefore does not prove that everything is translated; it catches the
 * regression one really commits — a whole sentence written in the repository's language
 * rather than in the tool's.
 */
class LanguageTest {

    /** The function words that give a French sentence away. Without "on", which is English. */
    private static final Pattern FRENCH = Pattern.compile(
            "(^|[^\\w])(le|la|les|des|une|du|de|qui|que|pour|dans|est|sont|pas|sur|avec"
            + "|cette|aux|par|leur|elle|aucun|aucune|été|nous|vous|ils)([^\\w]|$)",
            Pattern.CASE_INSENSITIVE);

    /**
     * The literals, <b>text blocks included</b>.
     *
     * <p>Forgetting them let the configuration template through: a hundred and thirty lines
     * of French written inside a {@code \"\"\"…\"\"\"}, and dropped as they are onto the disk
     * of whoever runs the tool without a configuration file. A test that looks only at
     * ordinary strings reassures about what it does not check.
     */
    private static final Pattern LITERAL = Pattern.compile(
            "\"\"\"(.*?)\"\"\"|\"((?:[^\"\\\\\\n]|\\\\.)*)\"", Pattern.DOTALL);

    @Test
    @DisplayName("No French sentence is left in what the tool writes")
    void noFrenchSentenceIsLeftInWhatTheToolWrites() throws IOException {
        Path root = Path.of("src/main/java");
        assertTrue(Files.isDirectory(root), "the sources must be there: " + root);

        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = LITERAL.matcher(source);
                while (m.find()) {
                    String text = m.group(1) != null ? m.group(1) : m.group(2);
                    // Four words at least: below that it is an identifier, a JSON key or a
                    // format fragment — and many are French for good reasons, starting with
                    // the option names that will never break.
                    if (text.chars().filter(c -> c == ' ').count() >= 3
                            && FRENCH.matcher(text).find()) {
                        found.add(root.relativize(f) + " : " + text);
                    }
                }
            }
        }
        assertEquals(List.of(), found,
                "French sentence(s) in a literal: the tool speaks English, and a sentence "
                + "added in the repository's language shows only at run time, on the "
                + "machine of someone who does not read French");
    }
}
