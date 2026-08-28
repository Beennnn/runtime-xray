package lab.xray;

import com.sun.net.httpserver.HttpServer;
import lab.xray.json.Json;
import lab.xray.report.Annotations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server writes to disk and listens on the network: two reasons not to settle for
 * launching it by hand once. These checks bear on what would really break — a write that
 * overwrites someone else's work, a path that leaves the served directory, an unknown run
 * taken at face value.
 */
class LocalServerTest {

    private HttpServer server;
    private final AtomicInteger reconstructions = new AtomicInteger();
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String base(Path dir) throws IOException {
        // Port 0: the system chooses, so two tests never fight over one.
        server = LocalServer.start(dir, "127.0.0.1", 0, () -> {
            reconstructions.incrementAndGet();
            return null;
        });
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Path run(Path dir, String name, String uuid) throws IOException {
        Path run = dir.resolve("runs").resolve(name);
        Files.createDirectories(run);
        Files.writeString(run.resolve("run-context.json"),
                Json.write(Map.of("uuid", uuid)), StandardCharsets.UTF_8);
        return run;
    }

    private HttpResponse<String> get(String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String url, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> json(HttpResponse<String> r) {
        return (Map<String, Object>) Json.read(r.body());
    }

    @Test
    @DisplayName("The page learns it may write, and reads the existing annotations")
    void pingAndRead(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        Files.writeString(run.resolve(Annotations.IN_THE_RUN),
                Json.write(Map.of("nom", "Recette")), StandardCharsets.UTF_8);
        String base = base(dir);

        assertEquals(Boolean.TRUE, json(get(base + "/__xray/ping")).get("peutEcrire"));

        Map<String, Object> names = json(get(base + "/__xray/noms"));
        Map<?, ?> annotations = (Map<?, ?>) names.get("annotations");
        assertEquals("Recette", ((Map<?, ?>) annotations.get("UUID-1")).get("nom"));
        assertTrue(((Map<?, ?>) names.get("empreintes")).containsKey("UUID-1"),
                "one fingerprint per run, otherwise the first write has nothing to compare against");
    }

    @Test
    @DisplayName("A run never annotated still has a fingerprint")
    void everyRunHasAFingerprint(@TempDir Path dir) throws Exception {
        run(dir, "vierge", "UUID-V");
        Map<String, Object> names = json(get(base(dir) + "/__xray/noms"));
        assertTrue(((Map<?, ?>) names.get("empreintes")).containsKey("UUID-V"));
        assertFalse(((Map<?, ?>) names.get("annotations")).containsKey("UUID-V"),
                "no annotation invented for all that");
    }

    @Test
    @DisplayName("The write lands in the run's directory, and regenerates the page")
    void writeLandsInTheRunDirectory(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        String base = base(dir);
        String fingerprint = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));

        HttpResponse<String> r = post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", fingerprint,
                        "valeur", Map.of("nom", "Recette du soir"))));
        assertEquals(200, r.statusCode());

        Map<?, ?> written = (Map<?, ?>) Annotations.readFile(run.resolve(Annotations.IN_THE_RUN));
        assertEquals("Recette du soir", written.get("nom"));
        assertNotEquals(fingerprint, json(r).get("empreinte"), "the fingerprint follows the content");

        // The regeneration is launched in the background: give it time to arrive.
        for (int i = 0; i < 50 && reconstructions.get() == 0; i++) Thread.sleep(20);
        assertEquals(1, reconstructions.get(),
                "without regeneration, the served page would show the previous annotation");
    }

    @Test
    @DisplayName("A write starting from a stale version is refused, not applied")
    void staleWriteIsRefused(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        String base = base(dir);
        String start = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));

        // The first one goes through.
        assertEquals(200, post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", start, "valeur", Map.of("nom", "posé par Alice"))))
                .statusCode());

        // The second started from the same version: it is told so, and nothing is overwritten.
        HttpResponse<String> refusal = post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", start, "valeur", Map.of("nom", "posé par Bob"))));
        assertEquals(409, refusal.statusCode());
        assertEquals("posé par Alice", ((Map<?, ?>) json(refusal).get("valeur")).get("nom"),
                "the refusal must show what is recorded, otherwise one cannot decide");

        Map<?, ?> annotations = (Map<?, ?>) json(get(base + "/__xray/noms")).get("annotations");
        assertEquals("posé par Alice", ((Map<?, ?>) annotations.get("UUID-1")).get("nom"));
    }

    @Test
    @DisplayName("Two different runs never get in each other's way")
    void twoRunsNeverCollide(@TempDir Path dir) throws Exception {
        run(dir, "un", "UUID-A");
        run(dir, "deux", "UUID-B");
        String base = base(dir);
        Map<?, ?> fingerprints = (Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes");

        assertEquals(200, post(base + "/__xray/noms/UUID-A", Json.write(Map.of(
                "base", fingerprints.get("UUID-A"), "valeur", Map.of("nom", "A")))).statusCode());
        assertEquals(200, post(base + "/__xray/noms/UUID-B", Json.write(Map.of(
                "base", fingerprints.get("UUID-B"), "valeur", Map.of("nom", "B")))).statusCode(),
                "the write bears on one run: the neighbour's has not moved");
    }

    @Test
    @DisplayName("An emptied annotation removes its file")
    void emptyAnnotationRemovesTheFile(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        String base = base(dir);
        Map<?, ?> fingerprints = (Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes");
        post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", fingerprints.get("UUID-1"), "valeur", Map.of("nom", "x"))));
        assertTrue(Files.exists(run.resolve(Annotations.IN_THE_RUN)));

        String after = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));
        assertEquals(200, post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", after, "valeur", Map.of()))).statusCode());
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)));
    }

    @Test
    @DisplayName("An identifier that names no run writes nowhere")
    void unknownRunIsRefused(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        String base = base(dir);
        assertEquals(404, post(base + "/__xray/noms/UUID-INCONNU",
                Json.write(Map.of("valeur", Map.of("nom", "x")))).statusCode());
        try (var listing = Files.walk(dir)) {
            assertTrue(listing.noneMatch(p -> p.getFileName().toString().equals("config.json")));
        }
    }

    @Test
    @DisplayName("A body that is not a JSON object is refused")
    void garbageBodyIsRefused(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        assertEquals(400, post(base(dir) + "/__xray/noms/UUID-1", "ceci n'est pas du JSON")
                .statusCode());
    }

    @Test
    @DisplayName("File serving never leaves the served directory")
    void staticServingStaysInside(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html>la vue</html>", StandardCharsets.UTF_8);
        Files.writeString(dir.getParent().resolve("secret.txt"), "hors du répertoire",
                StandardCharsets.UTF_8);
        String base = base(dir);

        assertEquals("<html>la vue</html>", get(base + "/").body(), "the root serves the index");

        for (String path : List.of("/../secret.txt", "/%2e%2e/secret.txt",
                                     "/runs/../../secret.txt")) {
            HttpResponse<String> r = get(base + path);
            assertFalse(r.body().contains("hors du répertoire"),
                    "tree traversal accepted on " + path);
        }
        // And the check itself bears on the resolved path, not on what was written.
        assertEquals(null, LocalServer.resolve(dir.toAbsolutePath().normalize(),
                "/../secret.txt"));
    }

    @Test
    @DisplayName("A run dropped while the server is running is taken into account")
    void droppedRunIsPickedUp(@TempDir Path dir) throws Exception {
        run(dir, "premiere", "UUID-1");
        String base = base(dir);
        String before = String.valueOf(json(get(base + "/__xray/noms")).get("revision"));
        assertEquals(1.0, json(get(base + "/__xray/noms")).get("executions"));

        // The shared-server scenario: someone drops results alongside.
        run(dir, "deposee", "UUID-2");

        Map<String, Object> after = json(get(base + "/__xray/noms"));
        assertNotEquals(before, after.get("revision"),
                "without a revision, the page would never know there is something new");
        assertEquals(2.0, after.get("executions"));

        // The watch polls every ten seconds: we do not wait for it here, we only check
        // that the revision itself did follow the disk.
        assertNotEquals(LocalServer.revision(dir.toAbsolutePath().normalize()), before);
    }

    @Test
    @DisplayName("The revision does not move when nothing moves")
    void revisionIsStableWhenNothingChanges(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        Path root = dir.toAbsolutePath().normalize();
        assertEquals(LocalServer.revision(root), LocalServer.revision(root),
                "a re-read must not pass for a drop");
    }

    @Test
    @DisplayName("The fingerprint changes only if the content changes")
    void fingerprintFollowsContent() {
        String a = LocalServer.fingerprint(Map.of("nom", "x"));
        assertEquals(a, LocalServer.fingerprint(Map.of("nom", "x")));
        assertNotEquals(a, LocalServer.fingerprint(Map.of("nom", "y")));
        assertNotEquals(a, LocalServer.fingerprint(null));
    }
}
