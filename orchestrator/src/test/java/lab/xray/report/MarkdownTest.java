package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This format is the one the forge actually displays — so the one somebody will read
 * without ever opening the page. Two ways to get it wrong: producing a broken table (a
 * captured value can contain anything at all, including a {@code |}), and announcing a
 * coverage rate without saying what it is a rate of.
 */
class MarkdownTest {

    private static Map<String, Object> clazz(String name, int covered, int missing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("simple", name.substring(name.lastIndexOf('/') + 1));
        m.put("covered", covered);
        m.put("missed", missing);
        return m;
    }

    private static Map<String, Object> node(String name, long total, Object... children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("total", total);
        m.put("children", List.of(children));
        return m;
    }

    private static Map<String, Object> run() {
        Map<String, Object> packages = new LinkedHashMap<>();
        packages.put("app/moteur", List.of(clazz("app/moteur/Calcul", 90, 10)));
        packages.put("app/mort", List.of(clazz("app/mort/JamaisAppele", 0, 40)));

        Map<String, Object> call = new LinkedHashMap<>();
        call.put("params", List.of(Map.of("type", "Trip", "value", "Trip[id=A|B, mode=CAR]")));
        call.put("retour", Map.of("type", "Double", "value", "42.0"));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("nom", "Recette");
        run.put("context", new LinkedHashMap<>(Map.of("commande", "java -jar app.jar",
                "java", "Temurin 25")));
        run.put("packages", packages);
        run.put("calltree", node("tout", 100, node("app/moteur/Calcul.calculer", 80)));
        run.put("values", Map.of("app/moteur/Calcul.calculer", List.of(call)));
        return run;
    }

    private static String write(Path dir) throws IOException {
        Markdown.write(dir, List.of(run()));
        return Files.readString(dir.resolve("rapport.md"), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("The three observations each have their section")
    void hasOneSectionPerObservation(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("## Recette"), "the run is named");
        assertTrue(md.contains("### Where the code went"));
        assertTrue(md.contains("### Where the time went"));
        assertTrue(md.contains("### With which values"));
    }

    @Test
    @DisplayName("The coverage rate says what it is a rate of")
    void qualifiesTheCoverageRatio(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("64% of the analysed instructions"),
                "90 covered out of 140 analysed: " + md.lines().filter(l -> l.contains("%"))
                        .findFirst().orElse("aucune ligne avec un %"));
        assertTrue(md.contains("denominator"),
                "without which the rate reads as a mark, when it depends on what was "
                + "given to be analysed");
    }

    @Test
    @DisplayName("Never-executed code is named, not merely counted")
    void namesDeadCode(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("app.mort.JamaisAppele"),
                "that is precisely what reading the code does not reveal");
    }

    @Test
    @DisplayName("A value containing a \"|\" does not break the table")
    void escapesPipesInValues(@TempDir Path dir) throws IOException {
        String md = write(dir);
        String line = md.lines().filter(l -> l.contains("Trip[id=")).findFirst().orElseThrow();
        // 4 separators for 3 columns: | # | arguments | return |
        assertEquals(4, line.chars().filter(c -> c == '|').count() - countEscaped(line),
                "column broken by an unescaped separator: " + line);
        assertTrue(line.contains("\\|"), "the value's separator must be escaped");
    }

    private static long countEscaped(String s) {
        return s.split("\\\\\\|", -1).length - 1;
    }

    @Test
    @DisplayName("The run's context comes with the report")
    void carriesTheRunContext(@TempDir Path dir) throws IOException {
        String md = write(dir);
        assertTrue(md.contains("java -jar app.jar"), "the command launched");
        assertTrue(md.contains("Temurin 25"), "la plateforme");
    }

    @Test
    @DisplayName("Without data, a section is absent rather than empty")
    void skipsEmptySections(@TempDir Path dir) throws IOException {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("nom", "Rien");
        empty.put("calltree", node("tout", 0));
        Markdown.write(dir, List.of(empty));
        String md = Files.readString(dir.resolve("rapport.md"), StandardCharsets.UTF_8);
        assertFalse(md.contains("### Where the time went"),
                "an empty section suggests the tool broke down");
        assertFalse(md.contains("### With which values"));
    }
}
