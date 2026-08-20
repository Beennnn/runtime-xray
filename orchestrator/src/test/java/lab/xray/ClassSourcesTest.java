package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * Le code que l'on cherche à comprendre n'arrive pas toujours sous forme de répertoire de
 * classes : une dépendance interne est livrée compilée, en jar. Si l'outil ne sait lire que
 * des répertoires, ce code-là manque au rapport sans que rien ne le signale.
 */
class ClassSourcesTest {

    /** Un faux .class : ces méthodes recopient des octets, elles ne les interprètent pas. */
    private static final byte[] BYTES = "classe compilée".getBytes(StandardCharsets.UTF_8);

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
    @DisplayName("Une classe est retrouvée dans un jar aussi bien que dans un répertoire")
    void findsClassesInJarsAndDirectories(@TempDir Path dir) throws IOException {
        Path fromJar = dir.resolve("depuis-jar.class");
        assertTrue(Main.copyClassBytes(
                List.of(jarWith(dir, "lib.jar", "org/exemple/Noyau.class")),
                "org/exemple/Noyau.class", fromJar));
        assertEquals(BYTES.length, Files.size(fromJar));

        Path fromDir = dir.resolve("depuis-repertoire.class");
        assertTrue(Main.copyClassBytes(
                List.of(dirWith(dir, "classes", "app/Calcul.class")),
                "app/Calcul.class", fromDir));
        assertEquals(BYTES.length, Files.size(fromDir));
    }

    @Test
    @DisplayName("Répertoires et jar se mélangent dans la même analyse")
    void mixesBothKinds(@TempDir Path dir) throws IOException {
        List<Path> sources = List.of(
                dirWith(dir, "classes", "app/Calcul.class"),
                jarWith(dir, "lib.jar", "org/exemple/Noyau.class"));
        assertTrue(Main.copyClassBytes(sources, "app/Calcul.class", dir.resolve("a.class")));
        assertTrue(Main.copyClassBytes(sources, "org/exemple/Noyau.class", dir.resolve("b.class")));
    }

    @Test
    @DisplayName("La première entrée gagne, comme sur un chemin de classe")
    void firstEntryWins(@TempDir Path dir) throws IOException {
        Path premier = dirWith(dir, "premier", "app/C.class");
        Files.write(premier.resolve("app/C.class"), "le bon".getBytes(StandardCharsets.UTF_8));
        Path second = jarWith(dir, "second.jar", "app/C.class");

        Path out = dir.resolve("resolue.class");
        assertTrue(Main.copyClassBytes(List.of(premier, second), "app/C.class", out));
        assertEquals("le bon", Files.readString(out, StandardCharsets.UTF_8),
                "la JVM aurait chargé la première : le rapport doit montrer la même");
    }

    @Test
    @DisplayName("Une classe est trouvée dans un jar applicatif rangé à la Spring Boot")
    void findsClassesUnderBootInfClasses(@TempDir Path dir) throws IOException {
        Path app = jarWith(dir, "appli.jar", "BOOT-INF/classes/com/exemple/Moteur.class");
        assertTrue(Main.copyClassBytes(List.of(app), "com/exemple/Moteur.class",
                dir.resolve("o.class")), "le préfixe BOOT-INF/classes doit être traversé");
    }

    @Test
    @DisplayName("Une classe est trouvée dans un jar CONTENU dans le jar applicatif")
    void findsClassesInNestedJars(@TempDir Path dir) throws IOException {
        // Le cas courant du jar gras : les dépendances sont des jar rangés dans BOOT-INF/lib.
        // Sans descente, tout le code des dépendances manquerait au rapport ciblé, alors que
        // l'outil de couverture, lui, le voit — les deux rapports diraient des choses
        // différentes du même code.
        Path inner = jarWith(dir, "interne.jar", "org/exemple/Noyau.class");
        Path outer = dir.resolve("appli.jar");
        try (OutputStream os = Files.newOutputStream(outer); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("BOOT-INF/lib/interne.jar"));
            zip.write(Files.readAllBytes(inner));
            zip.closeEntry();
        }
        assertTrue(Main.copyClassBytes(List.of(outer), "org/exemple/Noyau.class",
                dir.resolve("o.class")), "le jar imbriqué doit être ouvert");
    }

    @Test
    @DisplayName("Une archive illisible ne fait pas tomber l'analyse")
    void brokenArchiveIsSkipped(@TempDir Path dir) throws IOException {
        Path casse = dir.resolve("casse.jar");
        Files.writeString(casse, "ceci n'est pas une archive", StandardCharsets.UTF_8);
        Path bon = jarWith(dir, "bon.jar", "app/C.class");
        assertTrue(Main.copyClassBytes(List.of(casse, bon), "app/C.class", dir.resolve("o.class")),
                "l'entrée suivante doit être essayée");
    }

    @Test
    @DisplayName("Une classe absente partout est signalée, pas inventée")
    void missingClassReturnsFalse(@TempDir Path dir) throws IOException {
        assertFalse(Main.copyClassBytes(
                List.of(jarWith(dir, "lib.jar", "org/exemple/Autre.class")),
                "org/exemple/Absent.class", dir.resolve("o.class")));
    }

    @Test
    @DisplayName("La configuration accepte plusieurs entrées, répertoires et jar mêlés")
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
    @DisplayName("Une seule entrée reste une seule entrée")
    void configKeepsSingleEntry() {
        Config c = new Config();
        c.classesDir = "target/classes";
        assertEquals(List.of(Path.of("target/classes")), c.classesPaths());
    }
}
