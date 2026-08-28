package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The code one is trying to understand does not always arrive as a directory of classes: an
 * internal dependency is delivered compiled, as a jar. If the tool can only read
 * directories, that code is missing from the report with nothing to say so.
 */
class ClassSourcesTest {

    /** A fake .class: these methods copy bytes, they do not interpret them. */
    private static final byte[] BYTES = "compiled class".getBytes(StandardCharsets.UTF_8);

    private static Path jarWith(Path dir, String name, String entry) throws IOException {
        Path jar = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(BYTES);
            zip.closeEntry();
        }
        return jar;
    }

    private static Path dirWith(Path dir, String name, String entry) throws IOException {
        Path root = dir.resolve(name);
        Path file = root.resolve(entry);
        Files.createDirectories(file.getParent());
        Files.write(file, BYTES);
        return root;
    }

    @Test
    @DisplayName("A class is found in a jar as well as in a directory")
    void findsClassesInJarsAndDirectories(@TempDir Path dir) throws IOException {
        Path fromJar = dir.resolve("depuis-jar.class");
        assertTrue(Main.copyClassBytes(
                List.of(jarWith(dir, "lib.jar", "com/example/Noyau.class")),
                "com/example/Noyau.class", fromJar));
        assertEquals(BYTES.length, Files.size(fromJar));

        Path fromDir = dir.resolve("depuis-repertoire.class");
        assertTrue(Main.copyClassBytes(
                List.of(dirWith(dir, "classes", "app/Calcul.class")),
                "app/Calcul.class", fromDir));
        assertEquals(BYTES.length, Files.size(fromDir));
    }

    @Test
    @DisplayName("Directories and jars mix in the same analysis")
    void mixesBothKinds(@TempDir Path dir) throws IOException {
        List<Path> sources = List.of(
                dirWith(dir, "classes", "app/Calcul.class"),
                jarWith(dir, "lib.jar", "com/example/Noyau.class"));
        assertTrue(Main.copyClassBytes(sources, "app/Calcul.class", dir.resolve("a.class")));
        assertTrue(Main.copyClassBytes(sources, "com/example/Noyau.class", dir.resolve("b.class")));
    }

    @Test
    @DisplayName("The first entry wins, as on a classpath")
    void firstEntryWins(@TempDir Path dir) throws IOException {
        Path first = dirWith(dir, "premier", "app/C.class");
        Files.write(first.resolve("app/C.class"), "le bon".getBytes(StandardCharsets.UTF_8));
        Path second = jarWith(dir, "second.jar", "app/C.class");

        Path out = dir.resolve("resolue.class");
        assertTrue(Main.copyClassBytes(List.of(first, second), "app/C.class", out));
        assertEquals("le bon", Files.readString(out, StandardCharsets.UTF_8),
                "the JVM would have loaded the first: the report must show the same");
    }

    @Test
    @DisplayName("A class is found in an application jar laid out the Spring Boot way")
    void findsClassesUnderBootInfClasses(@TempDir Path dir) throws IOException {
        Path app = jarWith(dir, "appli.jar", "BOOT-INF/classes/com/example/Moteur.class");
        assertTrue(Main.copyClassBytes(List.of(app), "com/example/Moteur.class",
                dir.resolve("o.class")), "the BOOT-INF/classes prefix must be traversed");
    }

    @Test
    @DisplayName("A class is found in a jar CONTAINED in the application jar")
    void findsClassesInNestedJars(@TempDir Path dir) throws IOException {
        // The common fat-jar case: the dependencies are jars filed under BOOT-INF/lib.
        // Without going down, all the dependencies' code would be missing from the focused
        // report, while the coverage tool does see it — the two reports would say different
        // things about the same code.
        Path inner = jarWith(dir, "interne.jar", "com/example/Noyau.class");
        Path outer = dir.resolve("appli.jar");
        try (OutputStream os = Files.newOutputStream(outer); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("BOOT-INF/lib/interne.jar"));
            zip.write(Files.readAllBytes(inner));
            zip.closeEntry();
        }
        assertTrue(Main.copyClassBytes(List.of(outer), "com/example/Noyau.class",
                dir.resolve("o.class")), "the nested jar must be opened");
    }

    @Test
    @DisplayName("An unreadable archive does not bring the analysis down")
    void brokenArchiveIsSkipped(@TempDir Path dir) throws IOException {
        Path casse = dir.resolve("casse.jar");
        Files.writeString(casse, "ceci n'est pas une archive", StandardCharsets.UTF_8);
        Path bon = jarWith(dir, "bon.jar", "app/C.class");
        assertTrue(Main.copyClassBytes(List.of(casse, bon), "app/C.class", dir.resolve("o.class")),
                "the next entry must be tried");
    }

    @Test
    @DisplayName("A class absent everywhere is reported, not invented")
    void missingClassReturnsFalse(@TempDir Path dir) throws IOException {
        assertFalse(Main.copyClassBytes(
                List.of(jarWith(dir, "lib.jar", "com/example/Autre.class")),
                "com/example/Absent.class", dir.resolve("o.class")));
    }

    @Test
    @DisplayName("The configuration accepts several entries, directories and jars mixed")
    void configSplitsEntries() {
        Config c = new Config();
        c.classesDir = "target/classes:libs/noyau-1.4.jar, build/classes/java/main";
        List<Path> paths = c.classesPaths();
        assertEquals(3, paths.size());
        assertEquals(Path.of("target/classes"), paths.get(0));
        assertEquals(Path.of("libs/noyau-1.4.jar"), paths.get(1));
        assertEquals(Path.of("build/classes/java/main"), paths.get(2));
    }

    @Test
    @DisplayName("The launched jar is kept, read off the JVM's real arguments")
    void discoversTheLaunchedJar(@TempDir Path dir) throws IOException {
        Path jar = jarWith(dir, "appli.jar", "com/example/Main.class");
        List<Path> found = ClassSources.discover(
                List.of("-Xmx2g", "-jar", jar.toString(), "--profil", "recette"), "", dir);
        assertEquals(List.of(jar), found);
    }

    @Test
    @DisplayName("From a classpath, only the DIRECTORIES are kept")
    void keepsOnlyDirectoriesFromClasspath(@TempDir Path dir) throws IOException {
        // A real classpath holds dozens of dependency jars. Analysing them all would make
        // a report where the project's code is one percent of the total.
        Path classes = dirWith(dir, "classes", "app/Calcul.class");
        Path other = dirWith(dir, "generated", "app/Genere.class");
        Path dep = jarWith(dir, "commons.jar", "org/apache/Truc.class");
        String cp = String.join(File.pathSeparator,
                classes.toString(), dep.toString(), other.toString());
        assertEquals(List.of(classes, other),
                ClassSources.discover(List.of("-cp", cp, "com.example.Main"), "", dir));
    }

    @Test
    @DisplayName("The -jar wins over the classpath: it is what carries the code")
    void jarWinsOverClasspath(@TempDir Path dir) throws IOException {
        Path jar = jarWith(dir, "appli.jar", "com/example/Main.class");
        Path classes = dirWith(dir, "classes", "app/Autre.class");
        List<Path> found = ClassSources.discover(
                List.of("-cp", classes.toString(), "-jar", jar.toString()), "", dir);
        assertEquals(List.of(jar), found);
    }

    @Test
    @DisplayName("Without readable arguments, the configured command serves as a fallback")
    void fallsBackToTheConfiguredCommand(@TempDir Path dir) throws IOException {
        Path jar = jarWith(dir, "appli.jar", "com/example/Main.class");
        assertEquals(List.of(jar),
                ClassSources.discover(List.of(), "java -jar " + jar, dir));
    }

    @Test
    @DisplayName("With nothing else, the project convention is tried — and checked")
    void fallsBackToProjectConventions(@TempDir Path dir) throws IOException {
        // The `mvn exec:java` case: the application runs inside Maven's JVM and its
        // classpath appears on no command line. The convention is then the only recourse —
        // but it is only kept when the directory really exists.
        assertEquals(List.of(), ClassSources.discover(List.of(), "mvn exec:java", dir),
                "no conventional directory: we do not guess");

        Path target = dirWith(dir, "target/classes", "app/Calcul.class");
        assertEquals(List.of(target), ClassSources.discover(List.of(), "mvn exec:java", dir));
    }

    @Test
    @DisplayName("A jar announced but missing is not kept")
    void refusesPathsThatDoNotExist(@TempDir Path dir) {
        assertEquals(List.of(), ClassSources.discover(
                List.of("-jar", dir.resolve("never-built.jar").toString()), "", dir));
    }

    @Test
    @DisplayName("A single entry stays a single entry")
    void configKeepsSingleEntry() {
        Config c = new Config();
        c.classesDir = "target/classes";
        assertEquals(List.of(Path.of("target/classes")), c.classesPaths());
    }
}
