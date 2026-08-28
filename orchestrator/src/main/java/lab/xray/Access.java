package lab.xray;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Le secret partagé qui garde le rapport servi, quand on en met un.
 *
 * <p>Trois usages coexistent dans cet outil, et ils n'ont pas le même besoin. Sur son
 * poste — {@code --serve} seul — le serveur n'écoute que la boucle locale : demander un
 * mot de passe à quelqu'un pour accéder à ses propres mesures n'ajouterait rien. Déployé
 * pour une équipe — {@code --serve-host 0.0.0.0} — la question change : quiconque atteint
 * le port lit les rapports et annote.
 *
 * <p>D'où ce garde <b>facultatif</b> : sans secret il laisse tout passer, et les deux
 * premiers modes ne changent pas d'un pouce. Avec {@code --serve-token}, il exige la même
 * phrase de tout le monde. C'est un secret partagé, pas des comptes : l'outil ne sait pas
 * qui annote, il ne l'a jamais su, et le prétendre serait mentir sur ce qui est écrit dans
 * les fichiers d'annotation.
 *
 * <p>Ce qu'il vaut, dit franchement :
 * <ul>
 *   <li>il arrête un passant sur le réseau interne, pas un attaquant décidé ;</li>
 *   <li><b>en HTTP simple, le secret circule en clair</b> — pour qu'il protège vraiment,
 *       il faut du TLS devant, terminé par un reverse proxy ;</li>
 *   <li>un secret donné sur la ligne de commande se lit dans {@code ps} par les autres
 *       comptes de la machine : {@code XRAY_SERVE_TOKEN} existe pour cela.</li>
 * </ul>
 * Autrement dit il complète un filtrage réseau, il ne le remplace pas.
 */
final class Access {

    /** Nom du cookie de session. Court, et sans rapport avec le contenu du secret. */
    private static final String COOKIE = "xray_session";

    /** Une session ouverte vaut une journée de travail, pas davantage. */
    private static final long DURATION_MS = 12 * 60 * 60 * 1000L;

    /** Au-delà, les plus anciennes sont oubliées : la mémoire n'est pas un journal. */
    private static final int MAX_SESSIONS = 512;

    /** Ce qu'il faut rater pour être mis de côté, et pour combien de temps. */
    private static final int TOLERATED_FAILURES = 5;
    private static final long THROTTLE_MS = 30_000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private Access(String secret) {
        this.secret = secret;
    }

    /** Un garde qui laisse tout passer : le cas normal, sur son poste. */
    static Access open() {
        return new Access(null);
    }

    /** Un garde qui exige {@code secret}. */
    static Access withSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("an empty secret guards nothing");
        }
        String clean = secret.trim();
        // Un accent dans le secret se perd en route : l'en-tête « Authorization » ne
        // transporte que de l'ASCII, et une variable d'environnement traverse un shell dont
        // on ne connaît pas l'encodage. Le refuser ici avec sa raison vaut mieux qu'un 401
        // inexplicable une fois le serveur déployé.
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c < 0x20 || c > 0x7E) {
                throw new IllegalArgumentException("the shared secret must be ASCII "
                        + "printable (\"" + c + "\" survives neither the Authorization header "
                        + "nor some environment variables)");
            }
        }
        return new Access(clean);
    }

    /** Un secret tiré au sort, pour qui n'a pas envie d'en inventer un. */
    static String randomSecret() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    boolean guards() {
        return secret != null;
    }

    /**
     * Cette requête a-t-elle le droit d'aller plus loin ?
     *
     * <p>Deux façons de le prouver : le cookie posé après la page d'entrée, pour un
     * navigateur ; l'en-tête {@code Authorization: Bearer} pour tout le reste — un
     * {@code curl} de vérification, un script d'intégration.
     */
    boolean allows(HttpExchange ex) {
        if (!guards()) return true;

        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                && sameSecret(header.substring(7).trim())) {
            return true;
        }
        String session = cookie(ex, COOKIE);
        if (session == null) return false;
        Long end = sessions.get(session);
        if (end == null) return false;
        if (end < System.currentTimeMillis()) {
            sessions.remove(session);
            return false;
        }
        return true;
    }

    /**
     * Vérifie le secret proposé et ouvre une session ; rend {@code null} en cas d'échec.
     *
     * <p>Un secret partagé se devine par essais successifs : après quelques ratés venant du
     * même endroit, on cesse de répondre pendant un moment. Ce n'est pas une défense contre
     * un attaquant patient — c'en est une contre un script qui essaie mille mots.
     */
    String openSession(String propose, String origin) {
        if (throttled(origin)) return null;
        if (propose == null || !sameSecret(propose.trim())) {
            recordFailure(origin);
            return null;
        }
        failures.remove(origin);
        purge();
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(id, System.currentTimeMillis() + DURATION_MS);
        return id;
    }

    boolean throttled(String origin) {
        Failures e = failures.get(origin);
        return e != null && e.count >= TOLERATED_FAILURES
                && System.currentTimeMillis() - e.last < THROTTLE_MS;
    }

    /** L'en-tête à poser pour que le navigateur garde la session ouverte. */
    String cookieHeader(String session) {
        // Pas de « Secure » : il rendrait le cookie inopérant en HTTP simple, et c'est
        // ainsi que ce serveur est lancé. Le TLS, s'il y en a, est terminé devant.
        return COOKIE + "=" + session + "; Path=/; HttpOnly; SameSite=Strict; Max-Age="
                + (DURATION_MS / 1000);
    }

    private void recordFailure(String origin) {
        failures.compute(origin, (k, e) -> {
            Failures rest = e == null || System.currentTimeMillis() - e.last > THROTTLE_MS
                    ? new Failures() : e;
            rest.count++;
            rest.last = System.currentTimeMillis();
            return rest;
        });
    }

    /** Comparaison à durée constante : la durée d'un refus ne doit rien dire du secret. */
    private boolean sameSecret(String propose) {
        return MessageDigest.isEqual(propose.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }

    private void purge() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(e -> e.getValue() < now);
        if (sessions.size() >= MAX_SESSIONS) sessions.clear();
    }

    private static String cookie(HttpExchange ex, String name) {
        List<String> headers = ex.getRequestHeaders().get("Cookie");
        if (headers == null) return null;
        for (String header : headers) {
            for (String chunk : header.split(";")) {
                String[] pair = chunk.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals(name)) return pair[1].trim();
            }
        }
        return null;
    }

    /** L'adresse d'où vient la requête, telle qu'on la compte pour les ratés. */
    static String origin(HttpExchange ex) {
        return ex.getRemoteAddress() == null ? "?"
                : String.valueOf(ex.getRemoteAddress().getAddress());
    }

    /**
     * La page d'entrée : un champ, un bouton, et la raison d'être là.
     *
     * <p>Écrite ici plutôt que dans un fichier à part parce qu'elle doit rester lisible
     * même quand rien d'autre n'est encore servi — c'est la première chose que voit
     * quelqu'un à qui on a donné une adresse.
     */
    static String entryPage(String requested, String message) {
        String target = requested == null || requested.isBlank() ? "/" : requested;
        String alert = message == null ? ""
                : "<p class=ko role=alert>" + escape(message) + "</p>";
        // Remplacement littéral, et non « formatted » : la feuille de style contient des
        // « % » (width:100%), que le formateur prendrait pour des conversions.
        return """
                <!doctype html><html lang=en><meta charset=utf-8>
                <meta name=viewport content="width=device-width,initial-scale=1">
                <title>Runtime X-Ray — access</title>
                <style>
                :root{color-scheme:dark}
                body{margin:0;min-height:100vh;display:flex;align-items:center;
                     justify-content:center;background:#0d1117;color:#e6edf3;
                     font:15px/1.5 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
                form{width:min(92vw,26rem);background:#161b22;border:1px solid #30363d;
                     border-radius:10px;padding:1.6rem}
                h1{font-size:1.1rem;margin:0 0 .3rem}
                p{margin:.4rem 0 1rem;color:#9daab7;font-size:.87rem}
                label{display:block;font-size:.82rem;color:#9daab7;margin-bottom:.35rem}
                input{width:100%;box-sizing:border-box;padding:.6rem .7rem;border-radius:6px;
                      border:1px solid #30363d;background:#0d1117;color:#e6edf3;font-size:1rem}
                input:focus-visible,button:focus-visible{outline:2px solid #58a6ff;outline-offset:2px}
                button{margin-top:1rem;width:100%;padding:.6rem;border:0;border-radius:6px;
                       background:#238636;color:#fff;font-size:.95rem;cursor:pointer}
                button:hover{background:#2ea043}
                .ko{color:#ff9f9f;background:#3c1a1a;border:1px solid #6d2c2c;
                    border-radius:6px;padding:.5rem .7rem;margin:0 0 1rem;font-size:.85rem}
                </style>
                <form method=post action="/__xray/entrer">
                  <h1>Runtime X-Ray report</h1>
                  <p>This report is guarded by a shared secret, given by whoever started
                     the server.</p>
                  {{alerte}}
                  <label for=jeton>Secret</label>
                  <input id=jeton name=jeton type=password autofocus autocomplete=current-password>
                  <input type=hidden name=vers value="{{cible}}">
                  <button type=submit>Enter</button>
                </form>
                """.replace("{{alerte}}", alert).replace("{{cible}}", escape(target));
    }

    /** Le chemin vers la page d'entrée, avec la page demandée en mémoire. */
    static String toEntryPage(String request) {
        return "/__xray/entrer?vers="
                + URLEncoder.encode(request == null ? "/" : request, StandardCharsets.UTF_8);
    }

    /** Le formulaire renvoie du {@code application/x-www-form-urlencoded} : rien de plus. */
    static Map<String, String> fields(String body) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        for (String chunk : body.split("&")) {
            if (chunk.isBlank()) continue;
            String[] pair = chunk.split("=", 2);
            out.put(decoder(pair[0]), pair.length == 2 ? decoder(pair[1]) : "");
        }
        return out;
    }

    private static String decoder(String text) {
        return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Ce qu'on affiche au démarrage : jamais le secret, seulement son existence. */
    void announce(boolean local) {
        if (guards()) {
            System.out.println();
            System.out.println("   🔒 Access guarded by a shared secret.");
            System.out.println("      Visitors type it once, the session lasts 12 h.");
            if (!local) {
                System.out.println("      Over plain HTTP it travels in the clear: put TLS in front "
                        + "if the network is not trusted.");
            }
        } else if (!local) {
            System.out.println();
            System.out.println("   ⚠️ Listening beyond loopback, WITHOUT authentication:");
            System.out.println("      anyone reaching this port can read the reports and annotate.");
            System.out.println("      Put it behind something that already filters access, or guard it");
            System.out.println("      with --serve-token.");
        }
    }

    private static final class Failures {
        int count;
        long last;
    }

    /** Le secret retenu : celui de la ligne de commande, sinon celui de l'environnement. */
    static String secretRequested(String onTheLine, Map<String, String> environment) {
        if (onTheLine != null) return onTheLine;
        String env = environment.get("XRAY_SERVE_TOKEN");
        return env == null || env.isBlank() ? null : env.trim();
    }
}
