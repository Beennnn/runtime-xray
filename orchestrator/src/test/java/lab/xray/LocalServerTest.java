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
 * Le serveur écrit sur le disque et écoute le réseau : deux raisons de ne pas se contenter
 * de le lancer à la main une fois. Ces contrôles portent sur ce qui casserait vraiment —
 * une écriture qui écrase le travail d'un autre, un chemin qui sort du répertoire servi,
 * une exécution inconnue prise pour argent comptant.
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
        // Port 0 : c'est le système qui choisit, deux tests ne se disputent donc rien.
        server = LocalServer.start(dir, "127.0.0.1", 0, () -> {
            reconstructions.incrementAndGet();
            return null;
        });
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private Path run(Path dir, String nom, String uuid) throws IOException {
        Path run = dir.resolve("runs").resolve(nom);
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
    @DisplayName("La page apprend qu'elle peut écrire, et lit les annotations existantes")
    void pingAndRead(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        Files.writeString(run.resolve(Annotations.DANS_LE_RUN),
                Json.write(Map.of("nom", "Recette")), StandardCharsets.UTF_8);
        String base = base(dir);

        assertEquals(Boolean.TRUE, json(get(base + "/__xray/ping")).get("peutEcrire"));

        Map<String, Object> noms = json(get(base + "/__xray/noms"));
        Map<?, ?> annotations = (Map<?, ?>) noms.get("annotations");
        assertEquals("Recette", ((Map<?, ?>) annotations.get("UUID-1")).get("nom"));
        assertTrue(((Map<?, ?>) noms.get("empreintes")).containsKey("UUID-1"),
                "une empreinte par exécution, sinon la première écriture n'a rien à comparer");
    }

    @Test
    @DisplayName("Une exécution jamais annotée a quand même une empreinte")
    void everyRunHasAFingerprint(@TempDir Path dir) throws Exception {
        run(dir, "vierge", "UUID-V");
        Map<String, Object> noms = json(get(base(dir) + "/__xray/noms"));
        assertTrue(((Map<?, ?>) noms.get("empreintes")).containsKey("UUID-V"));
        assertFalse(((Map<?, ?>) noms.get("annotations")).containsKey("UUID-V"),
                "pas d'annotation inventée pour autant");
    }

    @Test
    @DisplayName("L'écriture atterrit dans le répertoire de l'exécution, et régénère la page")
    void writeLandsInTheRunDirectory(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        String base = base(dir);
        String empreinte = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));

        HttpResponse<String> r = post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", empreinte,
                        "valeur", Map.of("nom", "Recette du soir"))));
        assertEquals(200, r.statusCode());

        Map<?, ?> ecrit = (Map<?, ?>) Annotations.readFile(run.resolve(Annotations.DANS_LE_RUN));
        assertEquals("Recette du soir", ecrit.get("nom"));
        assertNotEquals(empreinte, json(r).get("empreinte"), "l'empreinte suit le contenu");

        // La régénération est lancée en arrière-plan : on lui laisse le temps d'arriver.
        for (int i = 0; i < 50 && reconstructions.get() == 0; i++) Thread.sleep(20);
        assertEquals(1, reconstructions.get(),
                "sans régénération, la page servie afficherait l'annotation d'avant");
    }

    @Test
    @DisplayName("Une écriture partie d'une version périmée est refusée, pas appliquée")
    void staleWriteIsRefused(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        String base = base(dir);
        String depart = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));

        // Le premier passe.
        assertEquals(200, post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", depart, "valeur", Map.of("nom", "posé par Alice"))))
                .statusCode());

        // Le second partait de la même version : il est prévenu, et rien n'est écrasé.
        HttpResponse<String> refus = post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", depart, "valeur", Map.of("nom", "posé par Bob"))));
        assertEquals(409, refus.statusCode());
        assertEquals("posé par Alice", ((Map<?, ?>) json(refus).get("valeur")).get("nom"),
                "le refus doit montrer ce qui est enregistré, sinon on ne peut pas trancher");

        Map<?, ?> annotations = (Map<?, ?>) json(get(base + "/__xray/noms")).get("annotations");
        assertEquals("posé par Alice", ((Map<?, ?>) annotations.get("UUID-1")).get("nom"));
    }

    @Test
    @DisplayName("Deux exécutions différentes ne se gênent pas")
    void twoRunsNeverCollide(@TempDir Path dir) throws Exception {
        run(dir, "un", "UUID-A");
        run(dir, "deux", "UUID-B");
        String base = base(dir);
        Map<?, ?> empreintes = (Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes");

        assertEquals(200, post(base + "/__xray/noms/UUID-A", Json.write(Map.of(
                "base", empreintes.get("UUID-A"), "valeur", Map.of("nom", "A")))).statusCode());
        assertEquals(200, post(base + "/__xray/noms/UUID-B", Json.write(Map.of(
                "base", empreintes.get("UUID-B"), "valeur", Map.of("nom", "B")))).statusCode(),
                "l'écriture porte sur une exécution : celle du voisin n'a pas bougé");
    }

    @Test
    @DisplayName("Une annotation vidée retire son fichier")
    void emptyAnnotationRemovesTheFile(@TempDir Path dir) throws Exception {
        Path run = run(dir, "essai", "UUID-1");
        String base = base(dir);
        Map<?, ?> empreintes = (Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes");
        post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", empreintes.get("UUID-1"), "valeur", Map.of("nom", "x"))));
        assertTrue(Files.exists(run.resolve(Annotations.DANS_LE_RUN)));

        String apres = String.valueOf(
                ((Map<?, ?>) json(get(base + "/__xray/noms")).get("empreintes")).get("UUID-1"));
        assertEquals(200, post(base + "/__xray/noms/UUID-1",
                Json.write(Map.of("base", apres, "valeur", Map.of()))).statusCode());
        assertFalse(Files.exists(run.resolve(Annotations.DANS_LE_RUN)));
    }

    @Test
    @DisplayName("Un identifiant qui ne désigne aucune exécution n'écrit nulle part")
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
    @DisplayName("Un corps qui n'est pas un objet JSON est refusé")
    void garbageBodyIsRefused(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        assertEquals(400, post(base(dir) + "/__xray/noms/UUID-1", "ceci n'est pas du JSON")
                .statusCode());
    }

    @Test
    @DisplayName("Le service de fichiers ne sort jamais du répertoire servi")
    void staticServingStaysInside(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("index.html"), "<html>la vue</html>", StandardCharsets.UTF_8);
        Files.writeString(dir.getParent().resolve("secret.txt"), "hors du répertoire",
                StandardCharsets.UTF_8);
        String base = base(dir);

        assertEquals("<html>la vue</html>", get(base + "/").body(), "la racine sert l'index");

        for (String chemin : List.of("/../secret.txt", "/%2e%2e/secret.txt",
                                     "/runs/../../secret.txt")) {
            HttpResponse<String> r = get(base + chemin);
            assertFalse(r.body().contains("hors du répertoire"),
                    "remontée d'arborescence acceptée sur " + chemin);
        }
        // Et la vérification elle-même porte sur le chemin résolu, pas sur ce qui a été écrit.
        assertEquals(null, LocalServer.resolve(dir.toAbsolutePath().normalize(),
                "/../secret.txt"));
    }

    @Test
    @DisplayName("Une exécution déposée pendant que le serveur tourne est prise en compte")
    void droppedRunIsPickedUp(@TempDir Path dir) throws Exception {
        run(dir, "premiere", "UUID-1");
        String base = base(dir);
        String avant = String.valueOf(json(get(base + "/__xray/noms")).get("revision"));
        assertEquals(1.0, json(get(base + "/__xray/noms")).get("executions"));

        // Le scénario du serveur partagé : quelqu'un dépose des résultats à côté.
        run(dir, "deposee", "UUID-2");

        Map<String, Object> apres = json(get(base + "/__xray/noms"));
        assertNotEquals(avant, apres.get("revision"),
                "sans révision, la page ne saurait jamais qu'il y a du nouveau");
        assertEquals(2.0, apres.get("executions"));

        // La veille sonde toutes les dix secondes : on ne l'attend pas ici, on vérifie
        // seulement que la révision, elle, a bien suivi le disque.
        assertNotEquals(LocalServer.revision(dir.toAbsolutePath().normalize()), avant);
    }

    @Test
    @DisplayName("La révision ne bouge pas quand rien ne bouge")
    void revisionIsStableWhenNothingChanges(@TempDir Path dir) throws Exception {
        run(dir, "essai", "UUID-1");
        Path racine = dir.toAbsolutePath().normalize();
        assertEquals(LocalServer.revision(racine), LocalServer.revision(racine),
                "une relecture ne doit pas passer pour un dépôt");
    }

    @Test
    @DisplayName("L'empreinte ne change que si le contenu change")
    void fingerprintFollowsContent() {
        String a = LocalServer.fingerprint(Map.of("nom", "x"));
        assertEquals(a, LocalServer.fingerprint(Map.of("nom", "x")));
        assertNotEquals(a, LocalServer.fingerprint(Map.of("nom", "y")));
        assertNotEquals(a, LocalServer.fingerprint(null));
    }
}
