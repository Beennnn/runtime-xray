package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le gabarit généré est la première chose que voit un nouvel utilisateur. S'il ne se relit
 * pas lui-même, l'outil envoie quelqu'un dans le mur dès le premier lancement.
 */
class ConfigTest {

    @Test
    @DisplayName("Le gabarit généré est relisible et donne des valeurs exploitables")
    void generatedTemplateReloads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("runtime-xray.conf");
        Config.writeTemplate(file);
        assertTrue(Files.isRegularFile(file));

        Config c = Config.load(file);
        assertFalse(c.javaCommand.isBlank(), "JAVA_CMD doit être renseigné dans le gabarit");
        // CLASSES_DIR ne doit PAS être actif : le bytecode est déterminé automatiquement, et
        // un chemin pré-rempli au hasard ferait échouer la première exécution en donnant
        // l'impression que l'outil réclame un réglage dont il n'a pas besoin.
        assertTrue(c.classesDir.isBlank(),
                "CLASSES_DIR doit rester commenté : il est facultatif");
        assertEquals(8, c.attachAfterSeconds);
        assertEquals(600, c.maxSeconds);
        assertEquals(10, c.watchCount);
        assertEquals("runtime-xray-out", c.outDir);
    }

    @Test
    @DisplayName("Le gabarit contient des exemples commentés pour chaque clé qui en mérite")
    void templateCarriesExamples() throws IOException {
        Path file = Files.createTempFile("conf", ".conf");
        Config.writeTemplate(file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // Ce sont ces exemples qui évitent d'avoir à lire la documentation.
        assertTrue(text.contains("#JAVA_CMD=\"mvn"), "un exemple Maven est attendu");
        assertTrue(text.contains("#JAVA_CMD=\"./gradlew"), "un exemple Gradle est attendu");
        // Facultatif, donc présenté par ses cas d'usage réels plutôt que par un défaut.
        assertTrue(text.contains("#CLASSES_DIR="), "les cas où le préciser sont attendus");
        assertTrue(text.contains("facultatif"), "CLASSES_DIR doit être annoncé facultatif");
        assertTrue(text.contains("#RUN_NAME="), "un exemple de nom d'exécution est attendu");
        assertTrue(text.contains("MAVEN_REPO"), "le miroir interne doit être documenté");
        Files.deleteIfExists(file);
    }

    @Test
    @DisplayName("Les commentaires de fin de ligne ne polluent pas les valeurs")
    void trailingCommentsAreStripped(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, """
                # un commentaire entier
                JAVA_CMD="java -jar app.jar"   # un commentaire en fin de ligne
                ATTACH_AFTER=4  # un autre
                CLASSES_DIR=target/classes # sans guillemets
                """, StandardCharsets.UTF_8);

        Config c = Config.load(file);
        assertEquals("java -jar app.jar", c.javaCommand);
        assertEquals(4, c.attachAfterSeconds);
        assertEquals("target/classes", c.classesDir);
    }

    @Test
    @DisplayName("Une clé inconnue est ignorée sans faire échouer la lecture")
    void unknownKeysAreIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "CLEF_INCONNUE=\"x\"\nJAVA_CMD=\"java -jar a.jar\"\n",
                StandardCharsets.UTF_8);
        assertEquals("java -jar a.jar", Config.load(file).javaCommand);
    }

    @Test
    @DisplayName("Une valeur numérique invalide retombe sur le défaut plutôt que de planter")
    void invalidNumberFallsBack(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "ATTACH_AFTER=beaucoup\n", StandardCharsets.UTF_8);
        assertEquals(8, Config.load(file).attachAfterSeconds);
    }

    @Test
    @DisplayName("Le filtre de profil se déduit du paquet de la méthode racine")
    void filterIsDerivedFromRootMethod() {
        Config c = new Config();
        c.rootMethod = "com.exemple.moteur.Calculateur::calculer";
        assertEquals("com/exemple/moteur/*", c.effectiveFilter());
        assertEquals("com.exemple.moteur.Calculateur", c.rootClass());
    }

    @Test
    @DisplayName("Un filtre explicite l'emporte sur la déduction")
    void explicitFilterWins() {
        Config c = new Config();
        c.rootMethod = "com.exemple.moteur.Calculateur::calculer";
        c.classFilter = "com/exemple/*";
        assertEquals("com/exemple/*", c.effectiveFilter());
    }

    @Test
    @DisplayName("Sans méthode racine, aucun filtre n'est inventé")
    void noRootMeansNoFilter() {
        Config c = new Config();
        assertTrue(c.effectiveFilter().isBlank());
        assertTrue(c.rootClass().isBlank());
    }

    @Test
    @DisplayName("Le contexte décrit reflète les réglages effectifs")
    void describeReflectsSettings() {
        Config c = new Config();
        c.javaCommand = "java -jar a.jar";
        c.rootMethod = "a.b.C::m";
        c.classesDir = "target/classes";
        var described = c.describe();
        assertEquals("java -jar a.jar", described.get("commande"));
        assertEquals("a.b.C::m", described.get("methodeRacine"));
        assertEquals("a/b/*", described.get("filtreClasses"));
        assertEquals(Boolean.TRUE, described.get("valeursInspectees"));
    }
}
