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
 * An export is only worth anything if the tool at the other end opens it. These checks are
 * therefore about what those formats demand — the order of the frames, the consistency of
 * the references, the totals — and not about the number of bytes written.
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
    @DisplayName("perf script: one block per sample, the callee before its caller")
    void perfIsLeafFirst(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.PERF), 1, 8);
        List<String> lines = Files.readAllLines(run.resolve("exports/profil.perf.txt"));

        long headers = lines.stream().filter(l -> l.contains("cpu-clock:")).count();
        assertEquals(4, headers, "a sample weighing 3 gives three samples");

        int first = lines.indexOf(lines.stream().filter(l -> l.contains("cpu-clock:")).findFirst().orElseThrow());
        assertTrue(lines.get(first + 1).contains("app/Moteur.calculer"), "the callee first");
        assertTrue(lines.get(first + 2).contains("app/Main.main"), "puis son appelant");
        assertTrue(lines.get(first + 1).startsWith("\t"), "the frames are indented");
    }

    @Test
    @DisplayName("cpuprofile: the references all point at a node that exists")
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
        samples.forEach(s -> assertTrue(ids.contains((Double) s), "sample without a node"));
        assertEquals(samples.size(), ((List<Object>) profile.get("timeDeltas")).size(),
                "one interval per sample, otherwise the total duration is wrong");
    }

    @Test
    @DisplayName("LCOV: the totals follow from the lines declared just above")
    void lcovTotalsAddUp(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.LCOV), 1, 8);
        String lcov = Files.readString(run.resolve("exports/couverture.lcov"), StandardCharsets.UTF_8);

        assertTrue(lcov.contains("SF:app/Moteur.java"), "the source file is named");
        assertTrue(lcov.contains("DA:3,1"), "line 3 ran");
        assertTrue(lcov.contains("DA:7,0"), "line 7 never ran");
        assertTrue(lcov.contains("LF:2"), "deux lignes connues");
        assertTrue(lcov.contains("LH:1"), "une seule atteinte");
        assertTrue(lcov.contains("FNDA:1,calculer"));
        assertTrue(lcov.contains("FNDA:0,mort"));
        assertTrue(lcov.contains("BRDA:3,0,0,1"), "une branche prise sur les deux");
        assertTrue(lcov.contains("BRDA:3,0,1,0"), "the other one was not");
        assertTrue(lcov.endsWith("end_of_record\n"));
    }

    @Test
    @DisplayName("A profile that is too big is thinned proportionally, not truncated")
    void hugeProfileIsThinnedNotCut(@TempDir Path dir) throws Exception {
        // Two very unequal branches, and a total weight far above the cap: what matters is
        // that the SECOND survives, and in its proportion. Truncating would make it vanish
        // entirely — the profile would then say the program never went there.
        Path run = dir.resolve("runs/gros");
        Files.createDirectories(run.resolve("async-profiler"));
        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Chaud.boucle 9000000\n"
              + "app/Main.main;app/Froid.rare 1000000\n", StandardCharsets.UTF_8);

        Exports.write(run, Set.of(Exports.Format.PERF), 1, 8);
        List<String> lines = Files.readAllLines(run.resolve("exports/profil.perf.txt"));

        long chaud = lines.stream().filter(l -> l.contains("app/Chaud.boucle")).count();
        long cold = lines.stream().filter(l -> l.contains("app/Froid.rare")).count();
        long total = chaud + cold;

        // The cap is on bytes: it is the file we stop from becoming unusable, not a number
        // of samples that says nothing about its size.
        long bytes = Files.size(run.resolve("exports/profil.perf.txt"));
        assertTrue(bytes < 40L * 1024 * 1024,
                "the file clearly exceeds the cap: " + bytes / (1024 * 1024) + " Mo");
        assertTrue(bytes > 16L * 1024 * 1024,
                "thinned far more than necessary: " + bytes / (1024 * 1024) + " Mo");

        assertTrue(cold > 0, "the light branch has vanished — that is what truncation used to do");
        double share = (double) cold / total;
        assertTrue(share > 0.08 && share < 0.12,
                "the measured proportion (10%) must be kept, yet it is " + share);
    }

    @Test
    @DisplayName("A profile that fits is left untouched")
    void smallProfileIsKeptWhole(@TempDir Path dir) throws Exception {
        Path run = run(dir);
        Exports.write(run, Set.of(Exports.Format.PERF), 1, 8);
        long headers = Files.readAllLines(run.resolve("exports/profil.perf.txt")).stream()
                .filter(l -> l.contains("cpu-clock:")).count();
        assertEquals(4, headers, "3 + 1 samples, all present");
    }

    @Test
    @DisplayName("The cpuprofile carries every sample, even when perf is thinned")
    @SuppressWarnings("unchecked")
    void cpuprofileKeepsEverything(@TempDir Path dir) throws Exception {
        Path run = dir.resolve("runs/gros");
        Files.createDirectories(run.resolve("async-profiler"));
        Files.writeString(run.resolve("async-profiler/profil.collapsed"),
                "app/Main.main;app/Chaud.boucle 2000000\n", StandardCharsets.UTF_8);

        Exports.write(run, Set.of(Exports.Format.CPUPROFILE), 1, 8);
        Map<String, Object> profile = (Map<String, Object>) Json.read(
                Files.readString(run.resolve("exports/profil.cpuprofile"), StandardCharsets.UTF_8));
        assertEquals(2_000_000, ((List<Object>) profile.get("samples")).size(),
                "compact by construction: it has no reason to be thinned");
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
