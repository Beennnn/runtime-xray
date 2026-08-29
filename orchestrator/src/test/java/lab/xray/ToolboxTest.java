package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The target machine has no network: that is the use case that motivated the tool. What
 * these tests exercise is therefore not the download, but everything that makes it possible
 * to do without — and the quality of the message when one really cannot.
 *
 * <p>The repository is pointed at a dead address everywhere: a test that passed by going
 * out on the network would prove nothing.
 */
class ToolboxTest {

    private static final String DEAD = "http://127.0.0.1:1/aucun-depot";

    private Path file(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path f = dir.resolve(name);
        Files.writeString(f, "composant", StandardCharsets.UTF_8);
        return f;
    }

    private Toolbox tools(Path cache, List<Path> flat, Path localRepo) {
        return new Toolbox(DEAD, cache, flat, localRepo);
    }

    @Test
    @DisplayName("A component placed beside the jar is taken as it is, without the network")
    void takesComponentPlacedNextToTheJar(@TempDir Path dir) throws Exception {
        Path brings = file(dir.resolve("voisinage"), "org.jacoco.agent-0.8.13-runtime.jar");

        Toolbox t = tools(dir.resolve("cache"), List.of(dir.resolve("voisinage")),
                dir.resolve("m2"));

        assertEquals(brings, t.jacocoAgent());
    }

    @Test
    @DisplayName("It is also recognised under the name its publisher gives it")
    void acceptsTheNameFromTheOfficialDistribution(@TempDir Path dir) throws Exception {
        // Whoever has "the JaCoCo file" to hand took it from JaCoCo's distribution, not
        // from Maven: it is called jacocoagent.jar, and nothing else.
        Path brings = file(dir.resolve("voisinage"), "jacocoagent.jar");

        Toolbox t = tools(dir.resolve("cache"), List.of(dir.resolve("voisinage")),
                dir.resolve("m2"));

        assertEquals(brings, t.jacocoAgent());
    }

    @Test
    @DisplayName("Failing that, the machine's local Maven repository will do")
    void fallsBackToTheLocalMavenRepository(@TempDir Path dir) throws Exception {
        Path m2 = dir.resolve("m2");
        Path brings = file(m2.resolve("org/jacoco/org.jacoco.cli/0.8.13"),
                "org.jacoco.cli-0.8.13-nodeps.jar");

        Toolbox t = tools(dir.resolve("cache"), List.of(), m2);

        assertEquals(brings, t.jacocoCli());
    }

    @Test
    @DisplayName("The tool's own cache keeps priority over everything else")
    void theToolsOwnCacheComesFirst(@TempDir Path dir) throws Exception {
        Path cache = dir.resolve("cache");
        Path expected = file(cache, "jfr-converter-4.1.jar");
        file(dir.resolve("voisinage"), "jfr-converter-4.1.jar");
        file(dir.resolve("m2/tools/profiler/jfr-converter/4.1"), "jfr-converter-4.1.jar");

        Toolbox t = tools(cache, List.of(dir.resolve("voisinage")), dir.resolve("m2"));

        assertEquals(expected, t.asyncProfilerConverter());
    }

    @Test
    @DisplayName("An already unpacked Arthas is recognised, under both its directory names")
    void findsAnAlreadyUnpackedArthas(@TempDir Path dir) throws Exception {
        Path neighbourhood = dir.resolve("voisinage");
        file(neighbourhood.resolve("arthas"), "arthas-boot.jar");

        Toolbox t = tools(dir.resolve("cache"), List.of(neighbourhood), dir.resolve("m2"));

        assertEquals(neighbourhood.resolve("arthas"), t.arthasHome());
    }

    @Test
    @DisplayName("The directories are searched in the announced order: the first one wins")
    void searchesInTheAnnouncedOrder(@TempDir Path dir) throws Exception {
        Path designates = dir.resolve("designe");
        Path neighbourhood = dir.resolve("voisinage");
        Path expected = file(designates, "jacocoagent.jar");
        file(neighbourhood, "jacocoagent.jar");

        Toolbox t = tools(dir.resolve("cache"), List.of(designates, neighbourhood), dir.resolve("m2"));

        assertEquals(expected, t.jacocoAgent());
    }

    @Test
    @DisplayName("The complete edition carries its components: they are dropped into the cache")
    void extractsAComponentEmbeddedInTheJar(@TempDir Path dir) throws Exception {
        // The test classpath carries /lab/xray/composants/jfr-converter-4.1.jar, as the jar
        // built with -Pcomplet would. Nothing on disk, repository unreachable.
        Path cache = dir.resolve("cache");

        Path obtained = tools(cache, List.of(), dir.resolve("m2")).asyncProfilerConverter();

        assertEquals(cache.resolve("jfr-converter-4.1.jar"), obtained,
                "a -javaagent wants a file: the component must land on the disk");
        assertTrue(Files.isRegularFile(obtained));
        assertTrue(Files.list(cache).noneMatch(f -> f.getFileName().toString().startsWith("ex-")),
                "no working file must be left behind");
    }

    @Test
    @DisplayName("A component declaring the right version raises no warning")
    void staysSilentWhenTheBroughtComponentDeclaresTheRightVersion(@TempDir Path dir)
            throws Exception {
        Path right = jarWithVersion(dir.resolve("jacocoagent.jar"), "0.8.13");

        assertEquals("", Toolbox.note(right, "0.8.13"),
                "warning when all is well teaches one to stop reading warnings");
    }

    @Test
    @DisplayName("A differing version is named, not merely suspected")
    void namesTheVersionWhenItDiffers(@TempDir Path dir) throws Exception {
        Path previous = jarWithVersion(dir.resolve("jacocoagent.jar"), "0.8.11");

        String note = Toolbox.note(previous, "0.8.13");

        assertTrue(note.contains("0.8.11") && note.contains("0.8.13"),
                "both versions must appear: " + note);
    }

    @Test
    @DisplayName("A file that declares nothing stays \"not verified\", without failing")
    void keepsTheOldCautionWhenNothingIsDeclared(@TempDir Path dir) throws Exception {
        // The async-profiler and Arthas archives are in this case, so is a truncated file.
        Path silent = jarWithVersion(dir.resolve("no-version.jar"), null);
        Path notAJar = file(dir, "text.jar");

        assertTrue(Toolbox.note(silent, "4.1").contains("not verified"));
        assertTrue(Toolbox.note(notAJar, "4.1").contains("not verified"),
                "not knowing is not a fault: above all, do not raise an exception");
    }

    /** @param version {@code null} for a jar whose manifest declares none. */
    private Path jarWithVersion(Path target, String version) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (version != null) {
            manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        }
        Files.createDirectories(target.getParent());
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target), manifest)) {
            out.putNextEntry(new java.util.zip.ZipEntry("nothing.txt"));
            out.write("nothing".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return target;
    }

    @Test
    @DisplayName("The pom versions do not diverge from the code's constants")
    void thePomVersionsMatchTheCode() throws Exception {
        // The "complet" profile bundles what the pom says; at run time, Toolbox looks for
        // what its constants say. Two sources that diverged would give a jar that looks
        // complete and would re-download everything.
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        String code = Files.readString(
                Path.of("src/main/java/lab/xray/Toolbox.java"), StandardCharsets.UTF_8);

        for (String[] pair : new String[][]{{"jacoco.version", "JACOCO_VERSION"},
                                             {"arthas.version", "ARTHAS_VERSION"},
                                             {"async.version", "ASYNC_VERSION"}}) {
            String fromPom = between(pom, "<" + pair[0] + ">", "</" + pair[0] + ">");
            String fromCode = between(code, pair[1] + " = \"", "\"");
            assertEquals(fromCode, fromPom, pair[0] + " and " + pair[1] + " must coincide");
        }
    }

    private static String between(String text, String start, String end) {
        int i = text.indexOf(start);
        assertTrue(i >= 0, "marker not found: " + start);
        i += start.length();
        return text.substring(i, text.indexOf(end, i));
    }

    @Test
    @DisplayName("When nothing is found, the message says where it looked and how to get out")
    void tellsWhereItLookedWhenNothingIsFound(@TempDir Path dir) {
        Path cache = dir.resolve("cache");
        Path neighbourhood = dir.resolve("voisinage");

        Toolbox t = tools(cache, List.of(neighbourhood), dir.resolve("m2"));

        IOException failure = assertThrows(IOException.class, t::jacocoAgent);
        String message = failure.getMessage();
        assertTrue(message.contains("org.jacoco.agent-0.8.13-runtime.jar"),
                "the missing component must be named: " + message);
        assertTrue(message.contains(cache.toString()) && message.contains(neighbourhood.toString()),
                "the directories searched must be listed: " + message);
        assertTrue(message.contains("--composants") && message.contains("--repo"),
                "both ways out must be recalled: " + message);
        assertTrue(message.contains("bundled"), "the complete edition must be mentioned: " + message);
    }
}
