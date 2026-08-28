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
 * The shared secret that guards the served report, when one is set.
 *
 * <p>Three uses coexist in this tool, and they do not have the same need. On one's own
 * machine — {@code --serve} alone — the server only listens on the loopback: asking
 * somebody for a password to reach their own measurements would add nothing. Deployed for
 * a team — {@code --serve-host 0.0.0.0} — the question changes: whoever reaches the port
 * reads the reports and annotates them.
 *
 * <p>Hence this <b>optional</b> guard: without a secret it lets everything through, and
 * the first two modes do not change by an inch. With {@code --serve-token}, it demands the
 * same phrase from everyone. It is a shared secret, not accounts: the tool does not know
 * who annotates, it never did, and pretending otherwise would be lying about what is
 * written in the annotation files.
 *
 * <p>What it is worth, said plainly:
 * <ul>
 *   <li>it stops a passer-by on the internal network, not a determined attacker;</li>
 *   <li><b>over plain HTTP the secret travels in the clear</b> — for it to protect
 *       anything, TLS must sit in front, terminated by a reverse proxy;</li>
 *   <li>a secret given on the command line is readable in {@code ps} by the machine's
 *       other accounts: {@code XRAY_SERVE_TOKEN} exists for that.</li>
 * </ul>
 * In other words it complements network filtering, it does not replace it.
 */
final class Access {

    /** Name of the session cookie. Short, and unrelated to the secret's content. */
    private static final String COOKIE = "xray_session";

    /** An open session is worth one working day, no more. */
    private static final long DURATION_MS = 12 * 60 * 60 * 1000L;

    /** Beyond that the oldest are forgotten: memory is not a log. */
    private static final int MAX_SESSIONS = 512;

    /** How much one must get wrong to be set aside, and for how long. */
    private static final int TOLERATED_FAILURES = 5;
    private static final long THROTTLE_MS = 30_000L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private Access(String secret) {
        this.secret = secret;
    }

    /** A guard that lets everything through: the normal case, on one's own machine. */
    static Access open() {
        return new Access(null);
    }

    /** A guard that demands {@code secret}. */
    static Access withSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("an empty secret guards nothing");
        }
        String clean = secret.trim();
        // An accent in the secret is lost on the way: the "Authorization" header only
        // carries ASCII, and an environment variable crosses a shell whose encoding is
        // unknown. Refusing it here, with the reason, is better than an unexplainable 401
        // once the server is deployed.
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

    /** A secret drawn at random, for those who would rather not invent one. */
    static String randomSecret() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    boolean guards() {
        return secret != null;
    }

    /**
     * Is this request allowed to go any further?
     *
     * <p>Two ways to prove it: the cookie set after the entry page, for a browser; the
     * {@code Authorization: Bearer} header for everything else — a {@code curl} check, an
     * integration script.
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
     * Checks the secret offered and opens a session; returns {@code null} on failure.
     *
     * <p>A shared secret is guessed by trying: after a few misses from the same place, we
     * stop answering for a while. This is not a defence against a patient attacker — it is
     * one against a script trying a thousand words.
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

    /** The header to set so that the browser keeps the session open. */
    String cookieHeader(String session) {
        // No "Secure": it would make the cookie inoperative over plain HTTP, and that is
        // how this server is launched. TLS, if any, is terminated in front.
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

    /** Constant-time comparison: how long a refusal takes must say nothing about the secret. */
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

    /** The address the request comes from, as counted for the misses. */
    static String origin(HttpExchange ex) {
        return ex.getRemoteAddress() == null ? "?"
                : String.valueOf(ex.getRemoteAddress().getAddress());
    }

    /**
     * The entry page: one field, one button, and the reason for being there.
     *
     * <p>Written here rather than in a separate file because it must stay readable even
     * when nothing else is served yet — it is the first thing someone given an address
     * sees.
     */
    static String entryPage(String requested, String message) {
        String target = requested == null || requested.isBlank() ? "/" : requested;
        String alert = message == null ? ""
                : "<p class=ko role=alert>" + escape(message) + "</p>";
        // Literal replacement, not "formatted": the stylesheet contains "%" signs
        // (width:100%), which the formatter would take for conversions.
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

    /** The path to the entry page, remembering the page that was asked for. */
    static String toEntryPage(String request) {
        return "/__xray/entrer?vers="
                + URLEncoder.encode(request == null ? "/" : request, StandardCharsets.UTF_8);
    }

    /** The form sends back {@code application/x-www-form-urlencoded}: nothing more. */
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

    /** What is shown at start-up: never the secret, only that there is one. */
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

    /** The secret kept: the one from the command line, otherwise the one from the environment. */
    static String secretRequested(String onTheLine, Map<String, String> environment) {
        if (onTheLine != null) return onTheLine;
        String env = environment.get("XRAY_SERVE_TOKEN");
        return env == null || env.isBlank() ? null : env.trim();
    }
}
