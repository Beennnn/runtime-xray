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
        c.rootMethod = "com.example.moteur.Calculateur::calculer";
        assertEquals("com/example/moteur/*", c.effectiveFilter());
        assertEquals("com.example.moteur.Calculateur", c.rootClass());
    }

    @Test
    @DisplayName("Un filtre explicite l'emporte sur la déduction")
    void explicitFilterWins() {
        Config c = new Config();
        c.rootMethod = "com.example.moteur.Calculateur::calculer";
        c.classFilter = "com/example/*";
        assertEquals("com/example/*", c.effectiveFilter());
    }

    @Test
    @DisplayName("Sans méthode racine, aucun filtre n'est inventé")
    void noRootMeansNoFilter() {
        Config c = new Config();
        assertTrue(c.effectiveFilter().isBlank());
        assertTrue(c.rootClass().isBlank());
    }

    @Test
    @DisplayName("Le niveau d'observation décide de ce qu'on paie")
    void levelDecidesWhatIsMeasured() {
        Config c = new Config();
        assertTrue(c.profileWanted(), "par défaut, on va jusqu'aux valeurs");
        assertTrue(c.valuesWanted());

        c.level = "arbre";
        assertTrue(c.profileWanted(), "l'arbre suppose l'échantillonnage");
        assertFalse(c.valuesWanted(), "mais pas la capture des valeurs");

        c.level = "couverture";
        assertFalse(c.profileWanted());
        assertFalse(c.valuesWanted());

        // --no-values reste un veto, quel que soit le niveau demandé.
        c.level = "complet";
        c.captureValues = false;
        assertFalse(c.valuesWanted());
    }

    @Test
    @DisplayName("Le niveau et ses réglages voyagent avec l'exécution")
    void levelIsRecorded() {
        Config c = new Config();
        c.level = "arbre";
        c.coverIncludes = "com.example.*";
        c.sampleIntervalMs = 10;
        var described = c.describe();
        assertEquals("arbre", described.get("niveau"));
        assertEquals("com.example.*", described.get("classesInstrumentees"));
        assertEquals(10, described.get("intervalleMs"));
        assertEquals(Boolean.FALSE, described.get("valeursInspectees"));
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

    // ---------------------------------------- plusieurs chemins, y compris sous Windows

    @Test
    @DisplayName("Un chemin Windows absolu n'est pas coupé sur la lettre de son lecteur")
    void aWindowsDriveLetterIsNotASeparator() {
        // Le séparateur documenté est « : », ce qui va de soi sur Unix et pas du tout sur
        // Windows : découpé bêtement, C:\projet\src donnait « C » et « \projet\src », deux
        // chemins qui n'existent pas. L'outil annonçait alors « introuvable » sur un chemin
        // parfaitement valide que l'utilisateur avait sous les yeux.
        assertEquals(java.util.List.of("C:\\Users\\moi\\projet\\src\\main\\java"),
                Config.decouper("C:\\Users\\moi\\projet\\src\\main\\java"));

        assertEquals(java.util.List.of("C:/Users/moi/projet", "D:/autre/module"),
                Config.decouper("C:/Users/moi/projet;D:/autre/module"),
                "le point-virgule est le séparateur natif de Windows : on l'accepte");
    }

    @Test
    @DisplayName("Sur Unix, « : » reste le séparateur de plusieurs racines")
    void aColonStillSeparatesRootsOnUnix() {
        assertEquals(java.util.List.of("src/main/java", "src/generated/java", "/opt/autre/src"),
                Config.decouper("src/main/java:src/generated/java:/opt/autre/src"));
        assertEquals(java.util.List.of("a", "b", "c"), Config.decouper("a,b:c"));
    }

    @Test
    @DisplayName("Seule une lettre SEULE devant un séparateur de chemin fait un lecteur")
    void onlyASingleLetterBeforeAPathSeparatorCountsAsADrive() {
        // Sans quoi « src:autre » ou « lib:cible » seraient recollés en un seul chemin.
        assertEquals(java.util.List.of("src", "autre"), Config.decouper("src:autre"));
        assertEquals(java.util.List.of("ab", "/c"), Config.decouper("ab:/c"),
                "deux lettres ne sont pas un lecteur");
        assertEquals(java.util.List.of("C", "sans-slash"), Config.decouper("C:sans-slash"),
                "sans séparateur de chemin derrière, ce « : » sépare bien deux entrées");
    }

    @Test
    @DisplayName("Les classes et les sources se découpent de la même façon")
    void classesAndSourcesAreSplitTheSameWay() {
        // Les deux réglages s'écrivent pareil, et se trompaient pareil : la correction doit
        // valoir pour les deux, ou le prochain lecteur croira que l'un des deux est cassé.
        Config c = new Config();
        c.classesDir = "C:\\projet\\target\\classes;C:\\projet\\libs\\interne.jar";
        assertEquals(2, c.classesPaths().size(), "un lecteur ne coupe pas un chemin en deux");
        assertEquals(Path.of("C:\\projet\\target\\classes"), c.classesPaths().get(0));

        assertEquals(java.util.List.of(Path.of("C:\\projet\\src\\main\\java")),
                Config.chemins("C:\\projet\\src\\main\\java"));
    }

    @Test
    @DisplayName("Une valeur vide ou en blancs ne produit aucun chemin")
    void blankValuesProduceNoPaths() {
        assertTrue(Config.chemins("").isEmpty());
        assertTrue(Config.chemins("  ,  : ").isEmpty(),
                "des séparateurs sans contenu ne désignent rien");
        assertTrue(Config.chemins(null).isEmpty());
    }
}
