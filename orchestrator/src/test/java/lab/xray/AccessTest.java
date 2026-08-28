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
 * Une porte ne vaut que par ce qu'elle refuse. Ces contrôles portent donc d'abord sur les
 * refus — et surtout sur celui qui compte : personne n'écrit dans les fichiers d'annotation
 * sans le secret. Le reste vérifie qu'on peut malgré tout entrer, et que les deux modes qui
 * n'ont jamais demandé de secret n'en demandent toujours pas.
 */
class AccessTest {

    private static final String SECRET = "phrase de passe partagee";

    private HttpServer server;
    // Redirect.NEVER par défaut : c'est ce qu'on veut, la redirection est justement ce
    // qu'on mesure. Les cookies aussi sont posés à la main, pour les voir passer.
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
    @DisplayName("Sans secret, rien n'est demandé : les deux premiers modes ne bougent pas")
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
    @DisplayName("Avec un secret, on n'écrit pas d'annotation sans l'avoir donné")
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
                "le refus doit précéder l'écriture, sinon la porte ne sert à rien");
    }

    @Test
    @DisplayName("Un navigateur est envoyé au formulaire, un fetch reçoit un 401")
    void browsersAreRedirectedAndScriptsAreNot(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> page = get(base + "/", null);
        assertEquals(302, page.statusCode());
        assertTrue(page.headers().firstValue("Location").orElse("").startsWith("/__xray/entrer"),
                "quelqu'un à qui on a donné l'adresse doit tomber sur la porte, pas sur une erreur");

        // La page, elle, appelle __xray en fetch : lui répondre 302 vers du HTML lui ferait
        // analyser un formulaire comme si c'étaient ses données.
        assertEquals(401, get(base + "/__xray/noms", null).statusCode());
        assertEquals(401, get(base + "/__xray/ping", null).statusCode());
    }

    @Test
    @DisplayName("Le bon secret ouvre une session, et la session ouvre le rapport")
    void theRightSecretOpensASession(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        assertEquals(200, get(base + "/__xray/entrer", null).statusCode(),
                "le formulaire lui-même n'est pas gardé, sinon on ne pourrait jamais entrer");

        HttpResponse<String> entry = form(base,
                "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8)
                        + "&vers=%2F");
        assertEquals(302, entry.statusCode());
        String cookie = cookie(entry);
        assertTrue(cookie != null && cookie.startsWith("xray_session="), "une session est posée");
        String raw = entry.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(raw.contains("HttpOnly"), "le script de la page n'a aucune raison de le lire");
        assertTrue(raw.contains("SameSite=Strict"), "et un autre site aucune de s'en servir");
        assertFalse(raw.contains(SECRET), "la session ne transporte pas le secret lui-même");

        assertEquals(200, get(base + "/", cookie).statusCode());
        assertEquals(200, get(base + "/__xray/noms", cookie).statusCode());
    }

    @Test
    @DisplayName("Un mauvais secret n'ouvre rien")
    void theWrongSecretOpensNothing(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        HttpResponse<String> refusal = form(base, "jeton=au-hasard&vers=%2F");
        assertEquals(401, refusal.statusCode());
        assertEquals(null, cookie(refusal));
        assertFalse(refusal.body().contains(SECRET), "une page d'erreur ne souffle pas la réponse");
    }

    @Test
    @DisplayName("Un script passe par l'en-tête, sans formulaire")
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
    @DisplayName("Après quelques essais ratés, on cesse de répondre à cette adresse")
    void guessingIsThrottled(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        for (int i = 0; i < 5; i++) {
            assertEquals(401, form(base, "jeton=essai-" + i).statusCode());
        }
        // Le sixième n'est plus examiné : un secret partagé se devine par force brute, et
        // rien d'autre ici ne ralentit un script qui essaie mille mots.
        assertEquals(429, form(base, "jeton=essai-6").statusCode());
        assertEquals(429, form(base,
                        "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8))
                .statusCode(), "y compris avec le bon : la mise à l'écart ne se contourne pas");
    }

    @Test
    @DisplayName("Le retour après entrée ne quitte jamais ce serveur")
    void theReturnStaysHere(@TempDir Path dir) throws Exception {
        run(dir, "u-1");
        String base = guards(dir, Access.withSecret(SECRET));

        // « vers » vient de l'URL : sans garde-fou, une adresse préparée ferait entrer
        // quelqu'un ici puis le déposerait ailleurs, avec la confiance qu'il vient d'accorder.
        HttpResponse<String> entry = form(base,
                "jeton=" + java.net.URLEncoder.encode(SECRET, StandardCharsets.UTF_8)
                        + "&vers=" + java.net.URLEncoder.encode("//ailleurs.example/piege",
                                StandardCharsets.UTF_8));
        assertEquals(302, entry.statusCode());
        assertEquals("/", entry.headers().firstValue("Location").orElseThrow());
    }

    @Test
    @DisplayName("Deux secrets tirés au sort ne se ressemblent pas, et un secret vide est refusé")
    void drawnSecretsAreUsable() {
        String a = Access.randomSecret();
        assertNotEquals(a, Access.randomSecret());
        assertTrue(a.length() >= 20, "trop court pour résister à quoi que ce soit : " + a);
        assertThrows(IllegalArgumentException.class, () -> Access.withSecret("  "));
        // Un secret accentué se perdrait dans l'en-tête Authorization : mieux vaut le dire
        // au démarrage qu'au premier 401 sur un serveur déjà déployé.
        assertThrows(IllegalArgumentException.class, () -> Access.withSecret("clé-secrète"));
    }

    @Test
    @DisplayName("Le secret vient de la ligne de commande, sinon de l'environnement")
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
