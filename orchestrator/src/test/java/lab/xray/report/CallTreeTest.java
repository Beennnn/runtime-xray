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
 * L'arbre est ce que l'utilisateur parcourt en premier. Deux erreurs y seraient graves :
 * perdre du temps mesuré en repliant des frames, ou laisser remonter du bruit qui noie le
 * code applicatif.
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
    @DisplayName("Une pile simple produit une branche, et les comptes s'additionnent")
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
    @DisplayName("Les branches sont triées du plus coûteux au moins coûteux")
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
    @DisplayName("Les frames du JDK disparaissent, mais leur temps est attribué à l'appelant")
    void foldsPlatformFramesWithoutLosingTime(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter;java/util/ArrayList.iterator 7
                app/Main.main;app/Service.traiter;jdk/internal/misc/Unsafe.park 3
                """));
        assertEquals(10L, t.root.get("total"), "aucun temps ne doit être perdu");
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(10L, service.get("total"));
        assertTrue(((List<?>) service.get("children")).isEmpty(),
                "aucune frame du JDK ne doit apparaître sous la méthode applicative");
    }

    @Test
    @DisplayName("Les frames de la machine virtuelle et les trampolines sont repliés aussi")
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
    @DisplayName("Les frames de l'inspecteur sont repliées ET signalées par une réserve")
    void foldsInstrumentationAndWarns(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter 20
                app/Main.main;app/Service.traiter;java/arthas/SpyAPI.atEnter 80
                """));
        assertEquals(100L, t.root.get("total"));
        assertNotNull(t.note, "une réserve doit être formulée quand l'inspecteur pèse sur le profil");
        assertTrue(t.note.contains("80 %"), "la part concernée doit être chiffrée : " + t.note);
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(100L, service.get("total"));
    }

    @Test
    @DisplayName("Sans inspecteur, aucune réserve n'est inventée")
    void noNoteWhenNoInstrumentation(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "app/Main.main;app/Service.traiter 5\n"));
        assertNull(t.note);
    }

    @Test
    @DisplayName("Un paquet masqué est replié comme le JDK : son temps revient à l'appelant")
    void foldsHiddenPackages(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                app/Main.main;app/Service.traiter;org/slf4j/Logger.debug 30
                app/Main.main;app/Service.traiter 10
                """), PackageFilter.of("org.slf4j"));
        assertEquals(40L, t.root.get("total"), "masquer ne doit pas perdre de temps mesuré");
        Map<String, Object> service = child(child(t.root, "app/Main.main"), "app/Service.traiter");
        assertEquals(40L, service.get("total"), "le temps du paquet masqué revient à l'appelant");
        assertTrue(((List<?>) service.get("children")).isEmpty());
    }

    @Test
    @DisplayName("Les symboles natifs de la VM sont repliés : personne ne peut les ouvrir")
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
                "aucun symbole natif ne doit apparaître : " + main.get("children"));
    }

    @Test
    @DisplayName("L'agent de couverture est un instrument, pas du code applicatif")
    void foldsCoverageAgent(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir,
                "app/Main.main;org/jacoco/agent/rt/internal_0e205/Offline.getProbes 8\n"));
        Map<String, Object> main = child(t.root, "app/Main.main");
        assertTrue(((List<?>) main.get("children")).isEmpty());
        assertEquals(8L, main.get("total"));
    }

    @Test
    @DisplayName("Une classe du paquet par défaut reste visible malgré tout")
    void keepsDefaultPackageClasses(@TempDir Path dir) throws IOException {
        // Le repli des symboles natifs s'appuie sur l'absence de '/' ET de '.'. Une classe
        // sans paquet garde son point — elle ne doit pas être emportée.
        CallTree t = CallTree.parse(write(dir, "Main.main;Calcul.faire 4\n"));
        assertNotNull(child(child(t.root, "Main.main"), "Calcul.faire"));
    }

    @Test
    @DisplayName("Sans consigne de masquage, la bibliothèque reste visible")
    void keepsLibraryFramesWhenNotHidden(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "app/Main.main;org/slf4j/Logger.debug 5\n"));
        assertNotNull(child(child(t.root, "app/Main.main"), "org/slf4j/Logger.debug"),
                "rien hors du JDK ne doit disparaître sans qu'on l'ait demandé");
    }

    @Test
    @DisplayName("Un fichier absent donne un arbre vide, pas une erreur")
    void missingFileGivesEmptyTree(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(dir.resolve("inexistant.collapsed"));
        assertEquals(0L, t.root.get("total"));
        assertTrue(((List<?>) t.root.get("children")).isEmpty());
    }

    @Test
    @DisplayName("Une ligne mal formée est ignorée sans faire tomber le reste")
    void skipsMalformedLines(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, """
                ceci n'est pas une pile
                app/Main.main;app/Service.traiter 4

                app/Main.main pasunnombre
                """));
        assertEquals(4L, t.root.get("total"));
    }

    @Test
    @DisplayName("Une pile entièrement composée de frames repliées ne crée pas de branche fantôme")
    void fullyFoldedStackCreatesNoBranch(@TempDir Path dir) throws IOException {
        CallTree t = CallTree.parse(write(dir, "java/lang/Thread.run;java/util/Timer.mainLoop 9\n"));
        assertEquals(9L, t.root.get("total"), "le temps reste compté au total");
        assertTrue(((List<?>) t.root.get("children")).isEmpty(),
                "mais aucune branche du JDK ne doit apparaître");
        assertFalse(String.valueOf(t.note).contains("null") && t.note != null);
    }
}
