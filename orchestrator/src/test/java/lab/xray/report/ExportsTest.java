package lab.xray.report;

import lab.xray.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un export n'a de valeur que si l'outil d'en face l'ouvre. Ces contrôles portent donc sur
 * ce que ces formats exigent — l'ordre des frames, la cohérence des renvois, les totaux —
 * et non sur le nombre d'octets écrits.
 */
class ExportsTest {

    private Path run(Path dir) throws IOException {
        Path run = dir.resolve("runs/essai");
        Files.createDirectories(run.resolve("async-profiler"));
        Files.createDirectories(run.resolve("jacoco/html"));
        Files.writeString(run.resolve("async-profiler/profil.collapsed"), """
                app/Main.main;app/Moteur.calculer 3
                app/Main.main;app/Moteur.ecrire 1
                """, StandardCharsets.UTF_8);
        Files.writeString(run.resolve("jacoco/html/jacoco.xml"), """
                <report name="t">
                  <package name="app">
                    <class name="app/Moteur" sourcefilename="Moteur.java">
                      <method name="calculer" desc="(I)I" line="3">
                        <counter type="INSTRUCTION" missed="0" covered="9"/>
                      </method>
                      <method name="mort" desc="()V" line="7">
                        <counter type="INSTRUCTION" missed="4" covered="0"/>
                      </method>
                      <counter type="INSTRUCTION" missed="4" covered="9"/>
                    </class>
                    <sourcefile name="Moteur.java">
                      <line nr="3" mi="0" ci="9" mb="1" cb="1"/>
                      <line nr="7" mi="4" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                </report>
                """, StandardCharsets.UTF_8);
        return run;
    }

    @Test
    @DisplayName("perf script : un bloc par relevé, l'appelé avant son appelant")
    void perfIsLeafFirst(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.PERF), 1, 8);
        List<String> lines = Files.readAllLines(run.resolve("exports/profil.perf.txt"));

        long entetes = lines.stream().filter(l -> l.contains("cpu-clock:")).count();
        assertEquals(4, entetes, "un relevé pesant 3 donne trois échantillons");

        int first = lines.indexOf(lines.stream().filter(l -> l.contains("cpu-clock:")).findFirst().orElseThrow());
        assertTrue(lines.get(first + 1).contains("app/Moteur.calculer"), "l'appelé d'abord");
        assertTrue(lines.get(first + 2).contains("app/Main.main"), "puis son appelant");
        assertTrue(lines.get(first + 1).startsWith("\t"), "les frames sont indentées");
    }

    @Test
    @DisplayName("cpuprofile : les renvois pointent tous sur un nœud qui existe")
    @SuppressWarnings("unchecked")
    void cpuprofileIsConsistent(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.CPUPROFILE), 1, 8);
        Map<String, Object> profile = (Map<String, Object>) Json.read(
                Files.readString(run.resolve("exports/profil.cpuprofile"), StandardCharsets.UTF_8));

        List<Object> nodes = (List<Object>) profile.get("nodes");
        Set<Double> ids = new HashSet<>();
        for (Object o : nodes) ids.add((Double) ((Map<String, Object>) o).get("id"));

        for (Object o : nodes) {
            Map<String, Object> node = (Map<String, Object>) o;
            for (Object child : (List<Object>) node.getOrDefault("children", List.of())) {
                assertTrue(ids.contains((Double) child),
                        "un nœud renvoie vers un enfant absent : le lecteur ne l'ouvrira pas");
            }
        }
        List<Object> samples = (List<Object>) profile.get("samples");
        assertEquals(4, samples.size());
        samples.forEach(s -> assertTrue(ids.contains((Double) s), "échantillon sans nœud"));
        assertEquals(samples.size(), ((List<Object>) profile.get("timeDeltas")).size(),
                "un intervalle par échantillon, sinon la durée totale est fausse");
    }

    @Test
    @DisplayName("LCOV : les totaux découlent des lignes déclarées juste au-dessus")
    void lcovTotalsAddUp(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.LCOV), 1, 8);
        String lcov = Files.readString(run.resolve("exports/couverture.lcov"), StandardCharsets.UTF_8);

        assertTrue(lcov.contains("SF:app/Moteur.java"), "le fichier source est nommé");
        assertTrue(lcov.contains("DA:3,1"), "la ligne 3 a tourné");
        assertTrue(lcov.contains("DA:7,0"), "la ligne 7 n'a jamais tourné");
        assertTrue(lcov.contains("LF:2"), "deux lignes connues");
        assertTrue(lcov.contains("LH:1"), "une seule atteinte");
        assertTrue(lcov.contains("FNDA:1,calculer"));
        assertTrue(lcov.contains("FNDA:0,mort"));
        assertTrue(lcov.contains("BRDA:3,0,0,1"), "une branche prise sur les deux");
        assertTrue(lcov.contains("BRDA:3,0,1,0"), "l'autre ne l'a pas été");
        assertTrue(lcov.endsWith("end_of_record\n"));
    }

    @Test
    @DisplayName("Sans mesure à réécrire, aucun fichier n'est créé")
    void nothingToExportWritesNothing(@TempDir Path dir) throws Exception {
        Path run = dir.resolve("runs/vide");
        Files.createDirectories(run);
        assertTrue(Exports.write(run, Set.of(Exports.Format.values()), 1, 8).isEmpty());
        assertFalse(Files.exists(run.resolve("exports")),
                "un répertoire vide laisserait croire à un export raté");
    }

    @Test
    @DisplayName("Le nom des formats est lu tel qu'on l'écrit, et « tout » les prend tous")
    void formatsAreParsed() {
        assertEquals(Set.of(Exports.Format.PERF, Exports.Format.LCOV),
                Exports.Format.parse("perf, lcov"));
        assertEquals(Set.of(Exports.Format.values()), Exports.Format.parse("tout"));
        assertEquals(Set.of(Exports.Format.values()), Exports.Format.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Exports.Format.parse("pprof"));
    }
}
