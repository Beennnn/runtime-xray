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
 * The generated template is the first thing a new user sees. If it does not read itself
 * back, the tool sends someone into a wall on the very first run.
 */
class ConfigTest {

    @Test
    @DisplayName("The generated template reads back and yields usable values")
    void generatedTemplateReloads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("runtime-xray.conf");
        Config.writeTemplate(file);
        assertTrue(Files.isRegularFile(file));

        Config c = Config.load(file);
        assertFalse(c.javaCommand.isBlank(), "JAVA_CMD must be filled in in the template");
        // CLASSES_DIR must NOT be active: the bytecode is determined automatically, and a
        // path pre-filled at random would make the first run fail, giving the impression
        // that the tool demands a setting it does not need.
        assertTrue(c.classesDir.isBlank(),
                "CLASSES_DIR must stay commented out: it is optional");
        assertEquals(8, c.attachAfterSeconds);
        assertEquals(600, c.maxSeconds);
        assertEquals(10, c.watchCount);
        assertEquals("runtime-xray-out", c.outDir);
    }

    @Test
    @DisplayName("The template carries commented examples for each key that deserves one")
    void templateCarriesExamples() throws IOException {
        Path file = Files.createTempFile("conf", ".conf");
        Config.writeTemplate(file);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        // These examples are what saves one from having to read the documentation.
        assertTrue(text.contains("#JAVA_CMD=\"mvn"), "a Maven example is expected");
        assertTrue(text.contains("#JAVA_CMD=\"./gradlew"), "a Gradle example is expected");
        // Optional, hence presented through its real use cases rather than through a default.
        assertTrue(text.contains("#CLASSES_DIR="), "the cases where it is worth setting are expected");
        assertTrue(text.contains("optional"), "CLASSES_DIR must be announced as optional");
        assertTrue(text.contains("#RUN_NAME="), "a run-name example is expected");
        assertTrue(text.contains("MAVEN_REPO"), "the internal mirror must be documented");
        Files.deleteIfExists(file);
    }

    @Test
    @DisplayName("End-of-line comments do not pollute the values")
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
    @DisplayName("An unknown key is ignored without failing the read")
    void unknownKeysAreIgnored(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "UNKNOWN_KEY=\"x\"\nJAVA_CMD=\"java -jar a.jar\"\n",
                StandardCharsets.UTF_8);
        assertEquals("java -jar a.jar", Config.load(file).javaCommand);
    }

    @Test
    @DisplayName("An invalid numeric value falls back on the default rather than crashing")
    void invalidNumberFallsBack(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "ATTACH_AFTER=plenty\n", StandardCharsets.UTF_8);
        assertEquals(8, Config.load(file).attachAfterSeconds);
    }

    @Test
    @DisplayName("The profile filter is derived from the root method's package")
    void filterIsDerivedFromRootMethod() {
        Config c = new Config();
        c.rootMethod = "com.example.engine.Calculator::compute";
        assertEquals("com/example/engine/*", c.effectiveFilter());
        assertEquals("com.example.engine.Calculator", c.rootClass());
    }

    @Test
    @DisplayName("An explicit filter wins over the derivation")
    void explicitFilterWins() {
        Config c = new Config();
        c.rootMethod = "com.example.engine.Calculator::compute";
        c.classFilter = "com/example/*";
        assertEquals("com/example/*", c.effectiveFilter());
    }

    @Test
    @DisplayName("Without a root method, no filter is invented")
    void noRootMeansNoFilter() {
        Config c = new Config();
        assertTrue(c.effectiveFilter().isBlank());
        assertTrue(c.rootClass().isBlank());
    }

    @Test
    @DisplayName("The observation level decides what one pays")
    void levelDecidesWhatIsMeasured() {
        Config c = new Config();
        assertTrue(c.profileWanted(), "by default, one goes all the way to the values");
        assertTrue(c.valuesWanted());

        c.level = "arbre";
        assertTrue(c.profileWanted(), "the tree implies the sampling");
        assertFalse(c.valuesWanted(), "but not the value capture");

        c.level = "couverture";
        assertFalse(c.profileWanted());
        assertFalse(c.valuesWanted());

        // --no-values stays a veto, whatever the level asked for.
        c.level = "complet";
        c.captureValues = false;
        assertFalse(c.valuesWanted());
    }

    @Test
    @DisplayName("What JaCoCo renders is a setting of its own, distinct from the level")
    void jacocoReportsIsItsOwnAxis() {
        Config c = new Config();
        assertEquals(Config.FULL, c.jacocoReports, "the default writes what it always wrote");
        assertTrue(c.jacocoHtmlWanted());
        assertTrue(c.focusedReportWanted());

        c.jacocoReports = Config.DETAILED;
        assertTrue(c.jacocoHtmlWanted(), "the complete site stays: it is the fallback");
        assertFalse(c.focusedReportWanted(),
                "the focused report holds no datum the complete one lacks");

        c.jacocoReports = Config.DATA;
        assertFalse(c.jacocoHtmlWanted());
        assertFalse(c.focusedReportWanted());

        // The level says what is MEASURED, this says what is WRITTEN. Confusing the two is
        // how one lowers the wrong knob, re-runs a whole campaign and gets the same report.
        c.level = "couverture";
        c.jacocoReports = Config.FULL;
        assertTrue(c.focusedReportWanted(),
                "lowering the level must not silently drop the rendering, nor the reverse");
    }

    @Test
    @DisplayName("A sparser run says so in its context, so it does not read as a failure")
    void aSparserRunIsRecordedAsAChoice() {
        Config c = new Config();
        c.jacocoReports = Config.DATA;
        // Without this, a report with no JaCoCo site reads as a measurement that went
        // wrong rather than as a setting someone chose — the same reason the level travels.
        assertEquals("data", c.describe().get("rapportsJacoco"));
    }

    @Test
    @DisplayName("JACOCO_REPORTS is read from the configuration file like any other key")
    void jacocoReportsIsReadFromTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "JAVA_CMD=\"java -jar a.jar\"\nJACOCO_REPORTS=\"data\"\n",
                StandardCharsets.UTF_8);
        assertEquals("data", Config.load(file).jacocoReports);
    }

    @Test
    @DisplayName("SERVE_HOST is read from the file, and its example carries the warning")
    void serveHostIsReadAndItsPriceIsStated(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("c.conf");
        Files.writeString(file, "JAVA_CMD=\"java -jar a.jar\"\nSERVE_HOST=\"0.0.0.0\"\n",
                StandardCharsets.UTF_8);
        assertEquals("0.0.0.0", Config.load(file).serveHost);
        assertEquals("127.0.0.1", new Config().serveHost, "the loopback stays the default");

        Path template = dir.resolve("t.conf");
        Config.writeTemplate(template);
        String text = Files.readString(template, StandardCharsets.UTF_8);
        assertTrue(text.contains("#SERVE_HOST="), "the key must be shown, commented out");
        // Whoever uncomments it must read what it costs on the same screen, not in a
        // paragraph somewhere else: past the loopback, the captured values of a real
        // application become readable — and the annotations writable — by whoever reaches
        // the port.
        assertTrue(text.contains("XRAY_SERVE_TOKEN"), "and how to guard it: " + text);
        assertTrue(text.contains("TLS"), "and that this speaks plain HTTP: " + text);
    }

    @Test
    @DisplayName("The level and its settings travel with the run")
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
    @DisplayName("The described context reflects the effective settings")
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

    // ------------------------------------------ several paths, Windows included

    @Test
    @DisplayName("An absolute Windows path is not cut on its drive letter")
    void aWindowsDriveLetterIsNotASeparator() {
        // The documented separator is ':', which goes without saying on Unix and not at
        // all on Windows: split naively, C:\project\src gave 'C' and '\project\src', two
        // paths that do not exist. The tool then announced "not found" on a perfectly valid
        // path the user had right in front of them.
        assertEquals(java.util.List.of("C:\\Users\\me\\project\\src\\main\\java"),
                Config.split("C:\\Users\\me\\project\\src\\main\\java"));

        assertEquals(java.util.List.of("C:/Users/me/project", "D:/other/module"),
                Config.split("C:/Users/me/project;D:/other/module"),
                "the semicolon is Windows' native separator: we accept it");
    }

    @Test
    @DisplayName("On Unix, ':' stays the separator between several roots")
    void aColonStillSeparatesRootsOnUnix() {
        assertEquals(java.util.List.of("src/main/java", "src/generated/java", "/opt/other/src"),
                Config.split("src/main/java:src/generated/java:/opt/other/src"));
        assertEquals(java.util.List.of("a", "b", "c"), Config.split("a,b:c"));
    }

    @Test
    @DisplayName("Only a letter ALONE before a path separator makes a drive")
    void onlyASingleLetterBeforeAPathSeparatorCountsAsADrive() {
        // Without which "src:other" or "lib:target" would be glued into a single path.
        assertEquals(java.util.List.of("src", "other"), Config.split("src:other"));
        assertEquals(java.util.List.of("ab", "/c"), Config.split("ab:/c"),
                "two letters are not a drive");
        assertEquals(java.util.List.of("C", "no-slash"), Config.split("C:no-slash"),
                "with no path separator behind it, that ':' really does separate two entries");
    }

    @Test
    @DisplayName("Classes and sources are split the same way")
    void classesAndSourcesAreSplitTheSameWay() {
        // Both settings are written the same way, and got it wrong the same way: the fix
        // must hold for both, or the next reader will think one of the two is broken.
        Config c = new Config();
        c.classesDir = "C:\\project\\target\\classes;C:\\project\\libs\\internal.jar";
        assertEquals(2, c.classesPaths().size(), "a drive does not cut a path in two");
        assertEquals(Path.of("C:\\project\\target\\classes"), c.classesPaths().get(0));

        assertEquals(java.util.List.of(Path.of("C:\\project\\src\\main\\java")),
                Config.paths("C:\\project\\src\\main\\java"));
    }

    @Test
    @DisplayName("An empty or blank value produces no path")
    void blankValuesProduceNoPaths() {
        assertTrue(Config.paths("").isEmpty());
        assertTrue(Config.paths("  ,  : ").isEmpty(),
                "separators with no content name nothing");
        assertTrue(Config.paths(null).isEmpty());
    }

    @Test
    @DisplayName("The English names are canonical, the French ones still work")
    void theEnglishNamesAreCanonicalAndTheFrenchOnesStillWork() {
        // A script deployed before the switch must never stop working over a question of
        // language. The aliases are not documented: they work, that is all.
        assertEquals("couverture", Config.level("coverage"));
        assertEquals("arbre", Config.level("tree"));
        assertEquals("complet", Config.level("full"));
        assertEquals("couverture", Config.level("couverture"));
        assertEquals("arbre", Config.level("  Arbre "));
        assertEquals("complet", Config.level("COMPLET"));

        Config c = new Config();
        c.level = "coverage";
        assertFalse(c.profileWanted(), "\"coverage\" must mean \"couverture\"");
        c.level = "full";
        assertTrue(c.valuesWanted());

        // Environment variables too.
        Config e = new Config();
        e.set("COMPONENTS", "/here");
        assertEquals("/here", e.componentsDir);
        Config fr = new Config();
        fr.set("COMPOSANTS", "/la");
        assertEquals("/la", fr.componentsDir);
    }
}
