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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree is what the user browses first. Two mistakes would be serious there: losing
 * measured time while folding frames, or letting noise rise that drowns the application
 * code.
 */
class CallTreeTest {

    private Path write(Path dir, String content) throws IOException {
        Path file = dir.resolve("profil.collapsed");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> node, String name) {
        for (Object o : (List<Object>) node.get("children")) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (name.equals(m.get("name"))) return m;
        }
        return null;
    }

    @Test
    @DisplayName("A simple stack produces a branch, and the counts add up")
    void buildsBranchesAndSums(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter 10
                app/Main.main;app/Service.traiter;app/Calcul.faire 5
                app/Main.main;app/Autre.faire 3
                """));
        assertEquals(18L, t.root.get("total"));
        Map<String, Object> main = child(t.root, "app/Main.main");
        assertNotNull(main);
        assertEquals(18L, main.get("total"));
        assertEquals(15L, child(main, "app/Service.traiter").get("total"));
        assertEquals(5L, child(child(main, "app/Service.traiter"), "app/Calcul.faire").get("total"));
    }

    @Test
    @DisplayName("A stack the profiler could not walk back does not become a second entry point")
    void brokenStackNeverBecomesARoot(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter 99
                [unknown_Java];app/Speeds.forMode 1
                """));
        // The sample stays in the total: the time was indeed spent somewhere.
        assertEquals(100L, t.root.get("total"));
        // But it does not show beside main, where it would read as a second root.
        assertNull(child(t.root, "app/Speeds.forMode"));
        assertEquals(1, ((List<Object>) t.root.get("children")).size());
        assertNotNull(t.stacksNote);
        assertTrue(t.stacksNote.contains("1 sample out of 100"), t.stacksNote);
    }

    @Test
    @DisplayName("A bracketed root that names a thread stays a real root")
    void threadRootIsKept(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                [main tid=123];app/Main.main 7
                """));
        assertNull(t.stacksNote);
        assertNotNull(child(t.root, "app/Main.main"));
    }

    @Test
    @DisplayName("The branches are sorted from the costliest to the least")
    void sortsChildrenByWeight(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Petit.faire 1
                app/Main.main;app/Gros.faire 50
                app/Main.main;app/Moyen.faire 10
                """));
        @SuppressWarnings("unchecked")
        List<Object> kids = (List<Object>) child(t.root, "app/Main.main").get("children");
        assertEquals("app/Gros.faire", ((Map<?, ?>) kids.get(0)).get("name"));
        assertEquals("app/Moyen.faire", ((Map<?, ?>) kids.get(1)).get("name"));
        assertEquals("app/Petit.faire", ((Map<?, ?>) kids.get(2)).get("name"));
    }

    @Test
    @DisplayName("The JDK frames disappear, but their time is attributed to the caller")
    void foldsPlatformFramesWithoutLosingTime(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter;java/util/ArrayList.iterator 7
                app/Main.main;app/Service.traiter;jdk/internal/misc/Unsafe.park 3
                """));
        assertEquals(10L, t.root.get("total"), "no time must be lost");
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(10L, service.get("total"));
        assertTrue(((List<?>) service.get("children")).isEmpty(),
                "no JDK frame must appear under the application method");
    }

    @Test
    @DisplayName("The virtual machine's frames and the trampolines are folded too")
    void foldsVirtualMachineFrames(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;CompileBroker::compile_method 4
                app/Main.main;stub:fmod 2
                app/Main.main;itable 1
                """));
        Map<String, Object> main = child(t.root, "app/Main.main");
        assertEquals(7L, main.get("total"));
        assertTrue(((List<?>) main.get("children")).isEmpty());
    }

    @Test
    @DisplayName("The inspector's frames are folded AND reported by a caveat")
    void foldsInstrumentationAndWarns(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter 20
                app/Main.main;app/Service.traiter;java/arthas/SpyAPI.atEnter 80
                """));
        assertEquals(100L, t.root.get("total"));
        assertNotNull(t.note, "a caveat must be stated when the inspector weighs on the profile");
        assertTrue(t.note.contains("80%"), "the share concerned must be given as a figure: " + t.note);
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(100L, service.get("total"));
    }

    @Test
    @DisplayName("Without an inspector, no caveat is invented")
    void noNoteWhenNoInstrumentation(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "app/Main.main;app/Service.traiter 5\n"));
        assertNull(t.note);
    }

    @Test
    @DisplayName("A hidden package is folded like the JDK: its time goes back to the caller")
    void foldsHiddenPackages(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter;org/slf4j/Logger.debug 30
                app/Main.main;app/Service.traiter 10
                """), PackageFilter.of("org.slf4j"));
        assertEquals(40L, t.root.get("total"), "hiding must not lose measured time");
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(40L, service.get("total"), "the hidden package's time goes back to the caller");
        assertTrue(((List<?>) service.get("children")).isEmpty());
    }

    @Test
    @DisplayName("The VM's native symbols are folded: nobody can open them")
    void foldsNativeSymbols(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;Java_java_lang_ClassLoader_defineClass1 3
                app/Main.main;MHN_resolve_Mem 2
                app/Main.main;eventHandlerClassFileLoadHook 4
                app/Main.main;Runtime1::counter_overflow 5
                app/Main.main;[unknown_Java] 6
                """));
        Map<String, Object> main = child(t.root, "app/Main.main");
        assertEquals(20L, main.get("total"), "aucun temps perdu");
        assertTrue(((List<?>) main.get("children")).isEmpty(),
                "no native symbol must appear: " + main.get("children"));
    }

    @Test
    @DisplayName("The coverage agent is an instrument, not application code")
    void foldsCoverageAgent(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir,
                "app/Main.main;org/jacoco/agent/rt/internal_0e205/Offline.getProbes 8\n"));
        Map<String, Object> main = child(t.root, "app/Main.main");
        assertTrue(((List<?>) main.get("children")).isEmpty());
        assertEquals(8L, main.get("total"));
    }

    @Test
    @DisplayName("A class in the default package stays visible all the same")
    void keepsDefaultPackageClasses(@TempDir Path dir) throws IOException {
        // Folding the native symbols rests on the absence of both '/' AND '.'. A class
        // without a package keeps its dot — it must not be taken with them.
        CallTree t = CallTree.parse(write(dir, "Main.main;Calcul.faire 4\n"));
        assertNotNull(child(child(t.root, "Main.main"), "Calcul.faire"));
    }

    @Test
    @DisplayName("Without a hiding instruction, the library stays visible")
    void keepsLibraryFramesWhenNotHidden(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "app/Main.main;org/slf4j/Logger.debug 5\n"));
        assertNotNull(child(child(t.root, "app/Main.main"), "org/slf4j/Logger.debug"),
                "nothing outside the JDK must disappear without being asked for");
    }

    @Test
    @DisplayName("A missing file gives an empty tree, not an error")
    void missingFileGivesEmptyTree(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(dir.resolve("inexistant.collapsed"));
        assertEquals(0L, t.root.get("total"));
        assertTrue(((List<?>) t.root.get("children")).isEmpty());
    }

    @Test
    @DisplayName("A malformed line is ignored without bringing the rest down")
    void skipsMalformedLines(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                ceci n'est pas une pile
                app/Main.main;app/Service.traiter 4

                app/Main.main pasunnombre
                """));
        assertEquals(4L, t.root.get("total"));
    }

    @Test
    @DisplayName("A stack made entirely of folded frames creates no phantom branch")
    void fullyFoldedStackCreatesNoBranch(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "java/lang/Thread.run;java/util/Timer.mainLoop 9\n"));
        assertEquals(9L, t.root.get("total"), "the time stays counted in the total");
        assertTrue(((List<?>) t.root.get("children")).isEmpty(),
                "but no JDK branch must appear");
        assertFalse(String.valueOf(t.note).contains("null") && t.note != null);
    }
}
