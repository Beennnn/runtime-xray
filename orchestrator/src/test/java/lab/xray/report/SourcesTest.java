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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The source index decides everything that shows beside the coverage. Its key must be
 * JaCoCo's — {@code package/File.java} — and it must be so <b>whatever directory was
 * named</b>. That is the decision these tests guard: on 26 August 2026, a root one notch too
 * high was enough to display "Source unavailable" on all 447 classes of an analysis, with
 * nothing to say why.
 */
class SourcesTest {

    private static Path source(Path dir, String path, String pkg) throws IOException {
        Path f = dir.resolve(path);
        Files.createDirectories(f.getParent());
        String name = f.getFileName().toString().replace(".java", "");
        Files.writeString(f, (pkg == null ? "" : "package " + pkg + ";\n\n")
                + "class " + name + " {\n    int x() { return 1; }\n}\n", StandardCharsets.UTF_8);
        return f;
    }

    @Test
    @DisplayName("The key comes from the declared package, not from the level of the root passed")
    void keysFollowTheDeclaredPackage(@TempDir Path dir) throws Exception {
        source(dir, "projet/src/main/java/com/example/app/Application.java", "com.example.app");

        // Three roots, from the right level to the most unlikely: the key does not move.
        for (String root : List.of("projet/src/main/java", "projet/src", "projet")) {
            Sources.Index index = Sources.load(List.of(dir.resolve(root)));
            assertTrue(index.byKey().containsKey("com/example/app/Application.java"),
                    "root \"" + root + "\": the key must stay JaCoCo's, "
                    + "or on a " + index.byKey().keySet());
        }
    }

    @Test
    @DisplayName("A root BELOW the package still lands on the right key")
    void keysSurviveARootBelowThePackage(@TempDir Path dir) throws Exception {
        source(dir, "src/com/example/app/Application.java", "com.example.app");

        Sources.Index index = Sources.load(List.of(dir.resolve("src/com/example")));
        assertTrue(index.byKey().containsKey("com/example/app/Application.java"),
                "the relative path would have given \"mod/Application.java\", which matches nothing");
    }

    @Test
    @DisplayName("A file without a package keeps its relative path as its key")
    void keepsTheRelativePathWhenNoPackageIsDeclared(@TempDir Path dir) throws Exception {
        source(dir, "src/Ancien.java", null);

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));
        assertTrue(index.byKey().containsKey("Ancien.java"),
                "without a declaration there is nothing better than the path: " + index.byKey().keySet());
    }

    @Test
    @DisplayName("A \"package\" written after the class starts is not a declaration")
    void ignoresAPackageWordThatComesTooLate() {
        assertEquals("app.vrai", Sources.declaredPackage(List.of(
                "package app.vrai;", "class A {", "  String s = \"package autre.chose;\";", "}")));
        assertNull(Sources.declaredPackage(List.of(
                "class A {", "  String s = \"package autre.chose;\";", "}")));
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    @DisplayName("A non-existent root is kept in the diagnostic, not ignored in silence")
    void reportsARootThatDoesNotExist(@TempDir Path dir) {
        Sources.Index index = Sources.load(List.of(dir.resolve("nulle-part")));

        assertEquals(0, index.files(), "nothing to read in a missing directory");
        assertEquals(1, index.roots().size(), "the root asked for must stay visible");
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) index.roots().get(0);
        assertEquals(Boolean.FALSE, root.get("existe"));
        assertNotNull(root.get("absolue"), "the absolute path is what allows understanding");
        assertNotNull(root.get("motif"), "and the reason is what allows fixing");
    }

    @Test
    @DisplayName("The diagnostic says where each file name was found")
    void tellsWhereEachFileNameWasFound(@TempDir Path dir) throws Exception {
        source(dir, "src/autre/paquet/Application.java", "autre.paquet");

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));

        // The real case: the coverage looks for com/example/app/Application.java, the
        // index has only a namesake. That list is what lets the page say so.
        @SuppressWarnings("unchecked")
        List<Object> places = (List<Object>) index.byName().get("Application.java");
        assertNotNull(places, "a file that was found must be findable again by its name");
        @SuppressWarnings("unchecked")
        Map<String, Object> where = (Map<String, Object>) places.get(0);
        assertEquals("autre.paquet", where.get("paquet"));
        assertEquals("autre/paquet/Application.java", where.get("cle"));
        assertTrue(String.valueOf(where.get("chemin")).endsWith("Application.java"),
                "the absolute path is what one copies to fix the configuration");
    }

    @Test
    @DisplayName("A source that is not UTF-8 is read all the same")
    void readsSourcesThatAreNotUtf8(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("src/app/Accent.java");
        Files.createDirectories(f.getParent());
        Files.write(f, ("package app;\n// data already measured\n"
                + "class Accent {}\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        Sources.Index index = Sources.load(List.of(dir.resolve("src")));

        assertTrue(index.byKey().containsKey("app/Accent.java"),
                "an accent in a comment must not cost the file its display");
        assertFalse(((List<?>) index.byKey().get("app/Accent.java")).isEmpty());
    }

    // ------------------------------------------- proposing a root, without guessing it

    @Test
    @DisplayName("The root proposed is the one that explains the most missing classes")
    @SuppressWarnings("unchecked")
    void proposesTheRootThatExplainsTheMostClasses(@TempDir Path dir) throws Exception {
        source(dir, "projet/src/main/java/com/example/app/Application.java", "com.example.app");
        source(dir, "projet/src/main/java/com/example/data/Repository.java", "com.example.data");
        source(dir, "autre/src/com/example/app/Application.java", "com.example.app");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java",
                                                      "com/example/data/Repository.java")),
                List.of(dir));

        assertFalse(leads.isEmpty(), "both candidate roots must be found");
        Map<String, Object> best = (Map<String, Object>) leads.get(0);
        // Compare on a constructed path, never on a literal with separators: on Windows
        // the root ends with "project\src\main\java", and the test failed while the search
        // had found exactly the right thing.
        assertEquals(dir.resolve("projet/src/main/java").toAbsolutePath().normalize().toString(),
                best.get("racine"),
                "the one that resolves two classes comes before the one that resolves only one");
        assertEquals(2, ((Number) best.get("resout")).intValue());
        assertEquals(2, ((Number) best.get("surTotal")).intValue(),
                "the count must stay readable: \"2 of 2\", not \"2\"");
    }

    @Test
    @DisplayName("A file with the right name but the wrong package does not count")
    void doesNotCreditANamesakeFromAnotherProject(@TempDir Path dir) throws Exception {
        // THIS test is what separates a proposal from a guess. An Application.java from
        // another project would show, beside the coverage, code that never ran — more
        // expensive than an empty panel, because it would be believed.
        source(dir, "un-autre-projet/src/util/Application.java", "un.autre.projet");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir));

        assertTrue(leads.isEmpty(),
                "the name matches, the package does not: nothing must be proposed, yet " + leads);
    }

    @Test
    @DisplayName("Nothing to propose is said, rather than filled in")
    void proposesNothingWhenNothingMatches(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("vide"));

        assertTrue(Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir)).isEmpty());
        assertTrue(Sources.searchRoots(java.util.Set.of(), List.of(dir)).isEmpty(),
                "without a missing class there is nothing to look for");
    }

    @Test
    @DisplayName("Searching under a base and under its parent does not walk twice")
    void searchesAParentOnlyOnce(@TempDir Path dir) throws Exception {
        Path child = dir.resolve("projet/src/main/java");
        Files.createDirectories(child);

        List<Path> kept = Sources.deduplicate(List.of(child, dir, dir.resolve("projet")));

        assertEquals(1, kept.size(), "the parent already covers its descendants: " + kept);
        assertEquals(dir.toAbsolutePath().normalize(), kept.get(0));
    }

    @Test
    @DisplayName("The root proposed is the one that should have been written, package removed")
    @SuppressWarnings("unchecked")
    void proposesTheRootWithThePackagePathRemoved(@TempDir Path dir) throws Exception {
        source(dir, "depot/module/src/main/java/com/example/app/Application.java", "com.example.app");

        List<Object> leads = Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir));

        Map<String, Object> lead = (Map<String, Object>) leads.get(0);
        assertEquals(dir.resolve("depot/module/src/main/java").toAbsolutePath().normalize()
                        .toString(),
                lead.get("racine"),
                "we return the source directory, not the file's");
        assertEquals(List.of("com/example/app/Application.java"), lead.get("exemples"),
                "la preuve accompagne le chiffre");
    }

    @Test
    @DisplayName("Bytecode and metadata are not traversed")
    void doesNotWalkThroughBuildOutputOrVersionControl(@TempDir Path dir) throws Exception {
        source(dir, ".git/sauvegarde/com/example/app/Application.java", "com.example.app");
        source(dir, "classes/com/example/app/Application.java", "com.example.app");
        source(dir, "node_modules/paquet/com/example/app/Application.java", "com.example.app");

        assertTrue(Sources.searchRoots(
                new java.util.LinkedHashSet<>(List.of("com/example/app/Application.java")),
                List.of(dir)).isEmpty(),
                "these directories never hold a project's sources, and they are "
                + "eux qui font exploser le parcours");
    }
}
