package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What wins when the command line and the configuration file disagree.
 *
 * <p>The answer has always been "the command line", and for a long time it was true of nine
 * settings out of twenty-two. The others — the observation level, what JaCoCo writes, the
 * instrumented classes, the exports, the sampling interval — were <b>dropped without a
 * word</b> the moment a {@code --config} was on the line beside them. A run asked for at
 * {@code --level coverage} measured everything; one asked for at
 * {@code --jacoco-reports data} wrote a hundred and eighty files instead of nine. Nothing
 * failed: the tool simply did something other than what it had been told, and the only way
 * to find out was to count afterwards.
 *
 * <p>The rule is now applied rather than listed — a setting that differs from a fresh
 * {@link Config} is one the command line set — and this test holds the list that a list
 * would have needed.
 */
class SettingsPrecedenceTest {

    /** The file's values: everything set, and all of them different from the defaults. */
    private static Config fromFile() {
        Config file = new Config();
        file.javaCommand = "java -jar from-the-file.jar";
        file.level = "coverage";
        file.jacocoReports = Config.DATA;
        file.coverIncludes = "file.pkg.*";
        file.exportFormats = "lcov";
        file.sampleIntervalMs = 50;
        file.serveHost = "10.0.0.1";
        file.archive = Config.KEEP;
        file.outDir = "from-the-file";
        return file;
    }

    @Test
    @DisplayName("Every setting given on the command line survives a --config beside it")
    void theCommandLineWinsOnEverySetting() {
        Config file = fromFile();
        Config line = new Config();
        line.level = "tree";
        line.jacocoReports = Config.MINIMAL;
        line.coverIncludes = "line.pkg.*";
        line.exportFormats = "perf";
        line.sampleIntervalMs = 7;
        line.serveHost = "0.0.0.0";
        line.archive = Config.REPLACE;
        line.outDir = "from-the-line";

        Main.merge(file, line);

        assertEquals("tree", file.level);
        assertEquals(Config.MINIMAL, file.jacocoReports);
        assertEquals("line.pkg.*", file.coverIncludes);
        assertEquals("perf", file.exportFormats);
        assertEquals(7, file.sampleIntervalMs);
        assertEquals("0.0.0.0", file.serveHost);
        assertEquals(Config.REPLACE, file.archive);
        assertEquals("from-the-line", file.outDir);
        // And what the line said nothing about stays what the file said.
        assertEquals("java -jar from-the-file.jar", file.javaCommand);
    }

    @Test
    @DisplayName("A setting the command line did not touch never overwrites the file")
    void anUntouchedSettingLeavesTheFileAlone() {
        // This is the other half, and the reason the rule is "differs from a fresh Config"
        // rather than "is not empty": a default is not an instruction.
        Config file = fromFile();
        Main.merge(file, new Config());
        assertEquals("coverage", file.level);
        assertEquals(Config.DATA, file.jacocoReports);
        assertEquals(50, file.sampleIntervalMs);
        assertEquals("10.0.0.1", file.serveHost);
        assertEquals("from-the-file", file.outDir);
    }

    @Test
    @DisplayName("A setting added later is carried over without anyone remembering to")
    void everySettingIsCoveredWithoutAList() throws Exception {
        // The defect was a hand-written list that stopped being complete. The guard is not
        // a longer list: it is that every field of Config, including the one added next
        // week, goes through. A field left out here fails the build rather than a campaign.
        List<String> dropped = new ArrayList<>();
        for (Field f : Config.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            Config file = new Config();
            Config line = new Config();
            Object other = different(f.get(line));
            if (other == null) continue;
            f.set(line, other);
            Main.merge(file, line);
            if (!other.equals(f.get(file))) dropped.add(f.getName());
        }
        assertTrue(dropped.isEmpty(),
                "these settings are lost when a --config accompanies them: " + dropped);
    }

    /** Any value that is not the one given, whatever the field's type. */
    private static Object different(Object value) {
        if (value instanceof String s) return s + "-changed";
        if (value instanceof Integer i) return i + 1;
        if (value instanceof Boolean b) return !b;
        return null;
    }

    @Test
    @DisplayName("SERVE_HOST says where to listen, and never that one should")
    void theFileCannotOpenAPort() throws Exception {
        // A configuration file travels: into a repository, a ticket, another machine. One
        // that could put a port into listening by being copied would be unreadable safely.
        // So the key sets an interface, and "--serve" alone decides to serve.
        Config file = Config.load(java.nio.file.Files.writeString(
                java.nio.file.Files.createTempDirectory("xray").resolve("x.conf"),
                "SERVE_HOST=\"0.0.0.0\"\n"));
        assertEquals("0.0.0.0", file.serveHost);

        String main = java.nio.file.Files.readString(java.nio.file.Path.of("")
                .toAbsolutePath().getParent()
                .resolve("orchestrator/src/main/java/lab/xray/Main.java").normalize(),
                java.nio.charset.StandardCharsets.UTF_8);
        int start = main.indexOf("switch (a) {");
        int end = main.indexOf("unknown option", start);
        assertTrue(start > 0 && end > start, "the options switch must stay locatable");
        String options = main.substring(start, end);
        assertTrue(options.contains("\"--serve\""), "the region really is the options switch");
        assertEquals(1, count(options, "serve = true"),
                "exactly one thing puts the tool into listening, and it is --serve");
        assertFalse(main.contains("config.serveHost.isBlank()"),
                "the interface never decides whether to serve");
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) n++;
        return n;
    }
}
