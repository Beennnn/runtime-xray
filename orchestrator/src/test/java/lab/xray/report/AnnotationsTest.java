package lab.xray.report;

import lab.xray.json.Json;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three possible locations for an annotation, and an order. Getting the order wrong, or
 * writing somewhere other than where it already lives, would leave two truths, of which the
 * one that takes priority would not be the one just typed in.
 */
class AnnotationsTest {

    private Path run(Path dir, String name) throws IOException {
        Path run = dir.resolve("runs").resolve(name);
        Files.createDirectories(run);
        Files.writeString(run.resolve("run-context.json"),
                Json.write(Map.of("uuid", "UUID-" + name)), StandardCharsets.UTF_8);
        return run;
    }

    @Test
    @DisplayName("The file placed INSIDE the run wins over the one beside it")
    void insideWinsOverSibling(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        Files.writeString(run.resolve(Annotations.IN_THE_RUN),
                Json.write(Map.of("nom", "dedans")), StandardCharsets.UTF_8);
        Files.writeString(run.resolveSibling("essai" + Annotations.SUFFIX),
                Json.write(Map.of("nom", "à côté")), StandardCharsets.UTF_8);

        Object read = Annotations.forRun(run, "UUID-essai", Map.of("UUID-essai", "central"));
        assertEquals("dedans", ((Map<?, ?>) read).get("nom"));
    }

    @Test
    @DisplayName("Failing that, the one beside it wins over the common file")
    void siblingWinsOverCentral(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        Files.writeString(run.resolveSibling("essai" + Annotations.SUFFIX),
                Json.write(Map.of("nom", "à côté")), StandardCharsets.UTF_8);

        Object read = Annotations.forRun(run, "UUID-essai", Map.of("UUID-essai", "central"));
        assertEquals("à côté", ((Map<?, ?>) read).get("nom"));
    }

    @Test
    @DisplayName("Without a file of its own, the run takes what the common file says")
    void centralIsTheFallback(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        assertEquals("central",
                Annotations.forRun(run, "UUID-essai", Map.of("UUID-essai", "central")));
        assertNull(Annotations.forRun(run, "UUID-essai", Map.of()));
    }

    @Test
    @DisplayName("We write where the annotation already lives, and in the run otherwise")
    void writesWhereItAlreadyLives(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        // Nothing yet: the annotation goes into the directory, to follow the run.
        assertEquals(run.resolve(Annotations.IN_THE_RUN), Annotations.fileFor(run));

        // It lives beside: we are not going to create a second one, taking priority, in
        // the directory — the input would be hidden by the file just written.
        Path beside = run.resolveSibling("essai" + Annotations.SUFFIX);
        Files.writeString(beside, "{}", StandardCharsets.UTF_8);
        assertEquals(beside, Annotations.fileFor(run));

        Annotations.write(run, Map.of("nom", "reprise"));
        assertEquals("reprise",
                ((Map<?, ?>) Annotations.readFile(beside)).get("nom"));
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)),
                "no second file must appear");
    }

    @Test
    @DisplayName("An emptied annotation removes its file rather than leaving an empty one")
    void emptyAnnotationRemovesTheFile(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        Annotations.write(run, Map.of("nom", "posé"));
        assertTrue(Files.exists(run.resolve(Annotations.IN_THE_RUN)));

        Annotations.write(run, Map.of());
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)));
    }

    @Test
    @DisplayName("Runs are recognised by their context, and found again by id")
    void runsAreFoundByUuid(@TempDir Path dir) throws IOException {
        run(dir, "un");
        run(dir, "deux");
        Files.createDirectories(dir.resolve("runs/pas-une-execution"));

        Map<String, Path> byUuid = Annotations.runsByUuid(dir);
        assertEquals(2, byUuid.size(), "a directory without a context is not a run");
        assertEquals(dir.resolve("runs/un"), byUuid.get("UUID-un"));
    }
}
