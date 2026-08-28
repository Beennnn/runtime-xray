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
 * Trois emplacements possibles pour une annotation, et un ordre. Se tromper d'ordre, ou
 * écrire ailleurs que là où elle vit déjà, laisserait deux vérités dont l'une, prioritaire,
 * ne serait pas celle qu'on vient de saisir.
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
    @DisplayName("Le fichier posé DANS l'exécution l'emporte sur celui d'à côté")
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
    @DisplayName("À défaut, celui d'à côté l'emporte sur le fichier commun")
    void siblingWinsOverCentral(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        Files.writeString(run.resolveSibling("essai" + Annotations.SUFFIX),
                Json.write(Map.of("nom", "à côté")), StandardCharsets.UTF_8);

        Object read = Annotations.forRun(run, "UUID-essai", Map.of("UUID-essai", "central"));
        assertEquals("à côté", ((Map<?, ?>) read).get("nom"));
    }

    @Test
    @DisplayName("Sans fichier propre, l'exécution prend ce que dit le fichier commun")
    void centralIsTheFallback(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        assertEquals("central",
                Annotations.forRun(run, "UUID-essai", Map.of("UUID-essai", "central")));
        assertNull(Annotations.forRun(run, "UUID-essai", Map.of()));
    }

    @Test
    @DisplayName("On écrit là où l'annotation vit déjà, et dans l'exécution sinon")
    void writesWhereItAlreadyLives(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        // Rien encore : l'annotation va dans le répertoire, pour suivre l'exécution.
        assertEquals(run.resolve(Annotations.IN_THE_RUN), Annotations.fileFor(run));

        // Elle vit à côté : on ne va pas en créer une seconde, prioritaire, dans le
        // répertoire — la saisie serait masquée par le fichier qu'on vient d'écrire.
        Path beside = run.resolveSibling("essai" + Annotations.SUFFIX);
        Files.writeString(beside, "{}", StandardCharsets.UTF_8);
        assertEquals(beside, Annotations.fileFor(run));

        Annotations.write(run, Map.of("nom", "reprise"));
        assertEquals("reprise",
                ((Map<?, ?>) Annotations.readFile(beside)).get("nom"));
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)),
                "aucun second fichier ne doit apparaître");
    }

    @Test
    @DisplayName("Une annotation vidée retire son fichier plutôt que d'en laisser un vide")
    void emptyAnnotationRemovesTheFile(@TempDir Path dir) throws IOException {
        Path run = run(dir, "essai");
        Annotations.write(run, Map.of("nom", "posé"));
        assertTrue(Files.exists(run.resolve(Annotations.IN_THE_RUN)));

        Annotations.write(run, Map.of());
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)));
    }

    @Test
    @DisplayName("Les exécutions se reconnaissent à leur contexte, et se retrouvent par identifiant")
    void runsAreFoundByUuid(@TempDir Path dir) throws IOException {
        run(dir, "un");
        run(dir, "deux");
        Files.createDirectories(dir.resolve("runs/pas-une-execution"));

        Map<String, Path> byUuid = Annotations.runsByUuid(dir);
        assertEquals(2, byUuid.size(), "un répertoire sans contexte n'est pas une exécution");
        assertEquals(dir.resolve("runs/un"), byUuid.get("UUID-un"));
    }
}
