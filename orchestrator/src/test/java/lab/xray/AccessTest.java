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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A door is only worth what it refuses. These checks are therefore first about the refusals
 * — and above all about the one that matters: nobody writes into the annotation files
 * without the secret. The rest checks that one can nonetheless get in, and that the two
 * modes which never asked for a secret still do not.
 */
class AccessTest {

    private static final String SECRET = "phrase de passe partagee";

    private HttpServer server;
    // Redirect.NEVER by default: that is what we want, the redirect being precisely what
    // is measured. The cookies too are set by hand, so as to see them go past.
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private String guards(Path dir, Access access) throws IOException {
        server = LocalServer.start(dir, "127.0.0.1", 0, () -> null, access);
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Path run(Path dir, String uuid) throws IOException {
        Path run = dir.resolve("runs").resolve("essai");
        Files.createDirectories(run);
        Files.writeString(run.resolve("run-context.json"),
                Json.write(Map.of("uuid", uuid)), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("index.html"), "<html>rapport</html>",
                StandardCharsets.UTF_8);
        return run;
    }

    private HttpResponse<String> get(String url, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url));
        if (cookie != null) b.header("Cookie", cookie);
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> form(String base, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + "/__xray/entrer"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Le cookie de session tel qu'on le renverra ensuite, ou {@code null} s'il n'y en a pas. */
    private String cookie(HttpResponse<String> r) {
        return r.headers().firstValue("Set-Cookie")
                .map(header -> header.split(";")[0]).orElse(null);
    }

    @Test
    @DisplayName("Without a secret nothing is asked: the first two modes do not move")
    void withoutSecretNothingIsAsked(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.open());

        assertEquals(200, get(base + "/", null).statusCode(), "la page s'ouvre");
        HttpResponse<String> ping = get(base + "/__xray/ping", null);
        assertEquals(200, ping.statusCode());
        assertTrue(ping.body().contains("\"garde\":false"),
                "la page doit savoir qu'il n'y a pas de porte, pour ne rien annoncer de faux");
    }

    @Test
    @DisplayName("With a secret, no annotation is written without having given it")
    void writingIsRefusedWithoutTheSecret(@TempDir Path dir) throws Exception {
        Path run = run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> refusal = client.send(
                HttpRequest.newBuilder(URI.create(base + "/__xray/noms/u-1"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"valeur\":{\"nom\":\"intrus\"}}", StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, refusal.statusCode());
        assertFalse(Files.exists(run.resolve(Annotations.IN_THE_RUN)),
                "the refusal must come before the write, otherwise the door is useless");
    }

    @Test
    @DisplayName("A browser is sent to the form, a fetch gets a 401")
    void browsersAreRedirectedAndScriptsAreNot(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> page = get(base + "/", null);
        assertEquals(302, page.statusCode());
        assertTrue(page.headers().firstValue("Location").orElse("").startsWith("/__xray/entrer"),
                "somebody given the address must land on the door, not on an error");

        // The page, for its part, calls __xray with a fetch: answering it 302 towards HTML
        // would have it parse a form as if it were its data.
        assertEquals(401, get(base + "/__xray/noms", null).statusCode());
        assertEquals(401, get(base + "/__xray/ping", null).statusCode());
    }

    @Test
    @DisplayName("The right secret opens a session, and the session opens the report")
    void theRightSecretOpensASession(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        assertEquals(200, get(base + "/__xray/entrer", null).statusCode(),
                "the form itself is not guarded, otherwise one could never get in");

        HttpResponse<String> entry = form(base,
                "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8)
                        + "&vers=%2F");
        assertEquals(302, entry.statusCode());
        String cookie = cookie(entry);
        assertTrue(cookie != null && cookie.startsWith("xray_session="), "a session is set");
        String raw = entry.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(raw.contains("HttpOnly"), "le script de la page n'a aucune raison de le lire");
        assertTrue(raw.contains("SameSite=Strict"), "et un autre site aucune de s'en servir");
        assertFalse(raw.contains(SECRET), "the session does not carry the secret itself");

        assertEquals(200, get(base + "/", cookie).statusCode());
        assertEquals(200, get(base + "/__xray/noms", cookie).statusCode());
    }

    @Test
    @DisplayName("A wrong secret opens nothing")
    void theWrongSecretOpensNothing(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> refusal = form(base, "jeton=au-hasard&vers=%2F");
        assertEquals(401, refusal.statusCode());
        assertEquals(null, cookie(refusal));
        assertFalse(refusal.body().contains(SECRET), "an error page does not whisper the answer");
    }

    @Test
    @DisplayName("A script goes through the header, without a form")
    void scriptsUseTheHeader(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> ok = client.send(
                HttpRequest.newBuilder(URI.create(base + "/__xray/ping"))
                        .header("Authorization", "Bearer " + SECRET).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"garde\":true"));

        HttpResponse<String> ko = client.send(
                HttpRequest.newBuilder(URI.create(base + "/__xray/ping"))
                        .header("Authorization", "Bearer pas-le-bon").build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, ko.statusCode());
    }

    @Test
    @DisplayName("After a few failed attempts, we stop answering that address")
    void guessingIsThrottled(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        for (int i = 0; i < 5; i++) {
            assertEquals(401, form(base, "jeton=essai-" + i).statusCode());
        }
        // The sixth is no longer examined: a shared secret is guessed by brute force, and
        // nothing else here slows down a script trying a thousand words.
        assertEquals(429, form(base, "jeton=essai-6").statusCode());
        assertEquals(429, form(base,
                        "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8))
                .statusCode(), "including with the right one: the throttling is not worked around");
    }

    @Test
    @DisplayName("The return after entering never leaves this server")
    void theReturnStaysHere(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        // "vers" comes from the URL: without a safeguard, a prepared address would let
        // somebody in here and then drop them elsewhere, carrying the trust they just gave.
        HttpResponse<String> entry = form(base,
                "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8)
                        + "&vers=" + java.net.URLEncoder.encode("//ailleurs.example/piege",
                                StandardCharsets.UTF_8));
        assertEquals(302, entry.statusCode());
        assertEquals("/", entry.headers().firstValue("Location").orElseThrow());
    }

    @Test
    @DisplayName("Two secrets drawn at random do not resemble each other, and an empty one is refused")
    void drawnSecretsAreUsable() {
        String a = Access.randomSecret();
        assertNotEquals(a, Access.randomSecret());
        assertTrue(a.length() >= 20, "too short to resist anything at all: " + a);
        assertThrows(IllegalArgumentException.class, () -> Access.withSecret("  "));
        // An accented secret would be lost in the Authorization header: better to say so
        // at start-up than at the first 401 on an already deployed server.
        assertThrows(IllegalArgumentException.class, () -> Access.withSecret("clé-secrète"));
    }

    @Test
    @DisplayName("The secret comes from the command line, otherwise from the environment")
    void theSecretComesFromEitherPlace() {
        assertEquals("depuis-la-ligne", Access.secretRequested("depuis-la-ligne",
                Map.of("XRAY_SERVE_TOKEN", "depuis-l-environnement")));
        assertEquals("depuis-l-environnement", Access.secretRequested(null,
                Map.of("XRAY_SERVE_TOKEN", "depuis-l-environnement")));
        assertEquals(null, Access.secretRequested(null, Map.of()));
        assertEquals(null, Access.secretRequested(null, Map.of("XRAY_SERVE_TOKEN", "   ")),
                "une variable vide vaut pas de secret, pas un secret vide");
    }
}
