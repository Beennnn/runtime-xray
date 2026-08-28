package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coverage is the authoritative source on "what ran". If it is read wrongly, the whole
 * list of executed code becomes wrong.
 */
class CoverageTest {

    /** A minimal report, with the external type declaration the real tool produces. */
    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="test">
              <package name="app/moteur">
                <class name="app/moteur/Calcul" sourcefilename="Calcul.java">
                  <method name="calculer" desc="(I)I" line="10">
                    <counter type="INSTRUCTION" missed="0" covered="12"/>
                  </method>
                  <method name="jamaisAppelee" desc="()V" line="20">
                    <counter type="INSTRUCTION" missed="4" covered="0"/>
                  </method>
                  <counter type="INSTRUCTION" missed="4" covered="12"/>
                </class>
                <class name="app/moteur/Morte" sourcefilename="Morte.java">
                  <method name="rien" desc="()V" line="5">
                    <counter type="INSTRUCTION" missed="3" covered="0"/>
                  </method>
                  <counter type="INSTRUCTION" missed="3" covered="0"/>
                </class>
                <sourcefile name="Calcul.java">
                  <line nr="10" mi="0" ci="4" mb="0" cb="0"/>
                  <line nr="11" mi="0" ci="4" mb="1" cb="1"/>
                  <line nr="20" mi="4" ci="0" mb="0" cb="0"/>
                </sourcefile>
              </package>
            </report>
            """;

    private Path xml(Path dir) throws IOException {
        Path f = dir.resolve("jacoco.xml");
        Files.writeString(f, XML, StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("The external type declaration is not followed: no network access")
    void doesNotResolveExternalDtd(@TempDir Path dir) throws Exception {
        // The DTD referenced does not exist beside the file: if the reader tried to load
        // it, the analysis would fail — yet it must succeed.
        assertNotNull(Coverage.parse(xml(dir)));
    }

    @Test
    @DisplayName("Each line gets its state: executed, partial, never reached")
    void classifiesLines(@TempDir Path dir) throws Exception {
        Coverage c = Coverage.parse(xml(dir));
        Map<String, Object> lines = c.lines.get("app/moteur/Calcul.java");
        assertNotNull(lines, "the source file must be indexed under package/File.java");
        assertEquals("full", ((Map<?, ?>) lines.get("10")).get("s"));
        assertEquals("part", ((Map<?, ?>) lines.get("11")).get("s"));
        assertEquals("miss", ((Map<?, ?>) lines.get("20")).get("s"));
    }

    @Test
    @DisplayName("The branch count comes with the partly covered lines")
    void reportsBranchCounts(@TempDir Path dir) throws Exception {
        Coverage c = Coverage.parse(xml(dir));
        Map<String, Object> lines = c.lines.get("app/moteur/Calcul.java");
        assertEquals(List.of(1, 2), ((Map<?, ?>) lines.get("11")).get("b"));
        assertNull(((Map<?, ?>) lines.get("10")).get("b"), "no branch, no count");
    }

    @Test
    @DisplayName("Methods are attached to their class, sorted by line, with their coverage")
    void listsMethodsPerClass(@TempDir Path dir) throws Exception {
        Coverage c = Coverage.parse(xml(dir));
        @SuppressWarnings("unchecked")
        Map<String, Object> cls = (Map<String, Object>) c.methods.get("app/moteur/Calcul");
        assertEquals("app/moteur/Calcul.java", cls.get("source"));
        @SuppressWarnings("unchecked")
        List<Object> methods = (List<Object>) cls.get("methods");
        assertEquals(2, methods.size());
        assertEquals("calculer", ((Map<?, ?>) methods.get(0)).get("name"));
        assertEquals(10, ((Map<?, ?>) methods.get(0)).get("line"));
        assertEquals(12, ((Map<?, ?>) methods.get(0)).get("covered"));
        assertEquals(0, ((Map<?, ?>) methods.get(1)).get("covered"),
                "a method that was never called must be visible with 0");
    }

    @Test
    @DisplayName("A never-executed class stays present, with zero instructions covered")
    void keepsNeverExecutedClasses(@TempDir Path dir) throws Exception {
        Coverage c = Coverage.parse(xml(dir));
        @SuppressWarnings("unchecked")
        List<Object> classes = (List<Object>) c.packages.get("app/moteur");
        assertEquals(2, classes.size());
        Map<?, ?> dead = classes.stream()
                .map(Map.class::cast)
                .filter(m -> "Morte".equals(m.get("simple")))
                .findFirst().orElseThrow();
        assertEquals(0, dead.get("covered"));
        assertEquals(3, dead.get("missed"));
    }

    @Test
    @DisplayName("A package's classes are sorted by name, for a stable list")
    void sortsClassesByName(@TempDir Path dir) throws Exception {
        Coverage c = Coverage.parse(xml(dir));
        @SuppressWarnings("unchecked")
        List<Object> classes = (List<Object>) c.packages.get("app/moteur");
        assertEquals("Calcul", ((Map<?, ?>) classes.get(0)).get("simple"));
        assertEquals("Morte", ((Map<?, ?>) classes.get(1)).get("simple"));
    }

    @Test
    @DisplayName("A hidden package appears neither among the packages nor among the classes")
    void hidesConfiguredPackages(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("j.xml");
        Files.writeString(f, """
                <report name="t">
                  <package name="app/moteur">
                    <class name="app/moteur/Calcul" sourcefilename="Calcul.java">
                      <counter type="INSTRUCTION" missed="0" covered="9"/>
                    </class>
                  </package>
                  <package name="org/slf4j/helpers">
                    <class name="org/slf4j/helpers/Util" sourcefilename="Util.java">
                      <counter type="INSTRUCTION" missed="0" covered="40"/>
                    </class>
                  </package>
                </report>
                """, StandardCharsets.UTF_8);
        Coverage c = Coverage.parse(f, PackageFilter.of("org.slf4j"));
        assertTrue(c.packages.containsKey("app/moteur"), "the business code stays");
        assertNull(c.packages.get("org/slf4j/helpers"), "the hidden package disappears");
        assertNull(c.methods.get("org/slf4j/helpers/Util"),
                "and its classes too, otherwise the focused report would copy them out");
    }

    @Test
    @DisplayName("A method without a line number is ignored rather than placed at random")
    void skipsMethodsWithoutLine(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("j.xml");
        Files.writeString(f, """
                <report name="t"><package name="a">
                  <class name="a/C" sourcefilename="C.java">
                    <method name="sansLigne" desc="()V"/>
                    <counter type="INSTRUCTION" missed="0" covered="1"/>
                  </class>
                </package></report>
                """, StandardCharsets.UTF_8);
        Coverage c = Coverage.parse(f);
        @SuppressWarnings("unchecked")
        Map<String, Object> cls = (Map<String, Object>) c.methods.get("a/C");
        assertTrue(((List<?>) cls.get("methods")).isEmpty());
    }
}
