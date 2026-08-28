package lab.xray;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lab.xray.json.Json;
import lab.xray.report.Annotations;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The served report, so that annotations stop being prisoners of one browser.
 *
 * <p>A page opened as a file cannot write anything to disk — that is a browser rule, and it
 * is also what makes the page sendable as it is. The names, descriptions, tags and prunings
 * typed into it therefore live in each person's browser. That is the simplest mode, and it
 * is often enough.
 *
 * <p>This server opens the other two:
 * <ul>
 *   <li><b>on one's own machine</b> — {@code --serve}: the page writes its annotations
 *       beside the runs — see {@link Annotations} for the possible locations — and the
 *       report is regenerated, so that the annotation is acquired even away from the
 *       server;</li>
 *   <li><b>deployed somewhere</b> — {@code --serve --serve-host 0.0.0.0}: results are
 *       dropped there, everyone reaches them through a browser, and <b>several people
 *       annotate in parallel</b>. Runs dropped while it runs are taken into account on
 *       their own: see the watcher, below.</li>
 * </ul>
 *
 * <p>Concurrency is the only real difficulty, and it is dealt with where it arises: <b>a
 * write targets one run</b>, not the whole file. Two people annotating two runs therefore
 * never see each other. Two people on the <b>same</b> run are settled by the fingerprint of
 * what they had in front of them: the second gets a refusal and the current version, rather
 * than silently overwriting the first one's work.
 *
 * <p>It only listens on the loopback by default, and the only write it accepts is the
 * annotation of a run, into a file whose name it chooses itself. Deployed beyond that,
 * {@code --serve-token} gives it a shared secret — see {@link Access} for what that secret
 * is and is not worth. Without a secret, the warning is printed at start-up: it then sits
 * behind whatever already filters the company's access.
 */
public final class LocalServer {

    private static final int MAX_BODY = 4 * 1024 * 1024;

    /** Writes are serialised: read, merge, write must not interleave. */
    private static final ReentrantLock LOCK = new ReentrantLock();

    private LocalServer() {}

    /**
     * Serves {@code outDir} and blocks until interrupted.
     *
     * @param host  the listening interface — {@code 127.0.0.1} by default
     * @param rebuild regenerates the page after a write, in the background: without it, a
     *                page reopened as a file would show the previous annotation
     */
    public static void serve(Path outDir, String host, int port, Callable<Void> rebuild,
                             Access access) throws IOException {
        HttpServer server = start(outDir, host, port, rebuild, access);
        announcement(outDir.toAbsolutePath().normalize(), host, port, access);

        CountDownLatch stop = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            stop.countDown();
        }));
        try {
            stop.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds and starts the server, without blocking.
     *
     * <p>Separate from {@link #serve} so that the tests can launch it on a port chosen by
     * the system, query it, then stop it — a server that never hands control back cannot be
     * tested.
     */
    static HttpServer start(Path outDir, String host, int port, Callable<Void> rebuild)
            throws IOException {
        return start(outDir, host, port, rebuild, Access.open());
    }

    static HttpServer start(Path outDir, String host, int port, Callable<Void> rebuild,
                            Access access) throws IOException {
        Path root = outDir.toAbsolutePath().normalize();
        InetAddress address = "0.0.0.0".equals(host) || "*".equals(host)
                ? new InetSocketAddress(port).getAddress()
                : InetAddress.getByName(host);
        HttpServer server = HttpServer.create(new InetSocketAddress(address, port), 0);
        Rebuilder rebuilder = new Rebuilder(rebuild);

        // The page queries this path on load: that is what tells it that it can offer
        // "Save" rather than a file export alone.
        server.createContext("/__xray/ping", ex -> {
            noCache(ex);
            if (bar(ex, access)) return;
            json(ex, 200, Map.of("peutEcrire", true, "fichier", Annotations.IN_THE_RUN,
                    "garde", access.guards()));
        });

        // The door, when there is a secret: a form, and the cookie that follows.
        if (access.guards()) server.createContext("/__xray/entrer", ex -> {
            try {
                entrer(ex, access);
            } catch (Exception e) {
                // A handler that lets an exception through sends NOTHING: the browser
                // sees a cut connection, and nobody knows why they cannot get in. A named
                // error is better.
                System.err.println("   entry page failed: " + e);
                text(ex, 500, String.valueOf(e.getMessage()));
            }
        });

        // The shared annotations, each with its fingerprint: that is what makes it
        // possible to detect that a run moved while it was being annotated.
        server.createContext("/__xray/noms", ex -> {
            noCache(ex);
            if (bar(ex, access)) return;
            try {
                String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
                String rest = ex.getRequestURI().getPath().substring("/__xray/noms".length());
                String uuid = rest.startsWith("/") ? rest.substring(1) : "";

                if (method.equals("GET")) {
                    Map<String, Object> all = effectiveAnnotations(root);
                    // The revision tells the page whether the report changed under its
                    // feet — a run dropped, another removed. It re-reads it at regular
                    // intervals anyway: it may as well learn it there.
                    String revision = revision(root);
                    // One fingerprint for EVERY run, including those that have no
                    // annotation yet: without it the first write would have nothing to
                    // compare against, and two people creating the same annotation at the
                    // same time would not see each other.
                    Map<String, Object> fingerprints = new LinkedHashMap<>();
                    Annotations.runsByUuid(root).forEach(
                            (runUuid, dir) -> fingerprints.put(runUuid, fingerprint(all.get(runUuid))));
                    json(ex, 200, Map.of("annotations", all, "empreintes", fingerprints,
                            "revision", revision, "executions", fingerprints.size()));
                    return;
                }
                if (!method.equals("POST") && !method.equals("PUT")) {
                    text(ex, 405, "method not allowed");
                    return;
                }
                if (uuid.isBlank()) {
                    text(ex, 400, "a write targets one run: /__xray/noms/<uuid>");
                    return;
                }
                // An unreadable body is the caller's fault, not the server breaking down:
                // answering 500 would send people looking on the wrong side.
                Object read;
                try {
                    read = Json.read(read(ex.getRequestBody()));
                } catch (Exception malformed) {
                    text(ex, 400, "corps illisible : " + malformed.getMessage());
                    return;
                }
                if (!(read instanceof Map<?, ?> body)) {
                    text(ex, 400, "the expected body is a JSON object");
                    return;
                }
                write(root, uuid, body, ex, rebuilder);
            } catch (Exception e) {
                System.err.println("   write refused: " + e.getMessage());
                text(ex, 500, String.valueOf(e.getMessage()));
            }
        });

        server.createContext("/", ex -> {
            try {
                if (bar(ex, access)) return;
                Path file = resolve(root, ex.getRequestURI().getPath());
                if (file == null || !Files.isRegularFile(file)) {
                    text(ex, 404, "introuvable");
                    return;
                }
                noCache(ex);
                send(ex, 200, contentType(file), Files.readAllBytes(file));
            } catch (Exception e) {
                text(ex, 500, String.valueOf(e.getMessage()));
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();
        watch(root, rebuilder);
        return server;
    }

    /**
     * Watches for runs dropped in while the server is running.
     *
     * <p>That is the shared server's very scenario: results are dropped there, and everyone
     * reads them. Without this watcher the server would have to be restarted at every drop
     * — which is to say nobody would, and the directory and the page would end up saying
     * two different things.
     *
     * <p>By polling, and not by watching the file system: results often arrive over a
     * network share, where change notifications are irregular at best. Ten seconds is
     * enough — one does not drop a run ten times a minute.
     */
    private static void watch(Path root, Rebuilder rebuilder) {
        Thread.ofPlatform().daemon().name("runtime-xray-veille").start(() -> {
            String known = revision(root);
            while (true) {
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                String now = revision(root);
                if (!now.equals(known)) {
                    known = now;
                    System.out.println("   runs changed on disk — the page is "
                            + "rebuilt");
                    rebuilder.request();
                }
            }
        });
    }

    /**
     * What tells one state of the directory from another: the runs present and the date of
     * their context. Two successive drops give two different revisions; a mere re-read does
     * not.
     */
    static String revision(Path root) {
        StringBuilder sb = new StringBuilder();
        for (Path run : Annotations.runDirs(root)) {
            sb.append(run.getFileName()).append(':');
            try {
                sb.append(Files.getLastModifiedTime(run.resolve("run-context.json")).toMillis());
            } catch (IOException e) {
                sb.append('?');
            }
            sb.append(';');
        }
        return fingerprint(sb.toString());
    }

    /**
     * Writes a run's annotation, where it already lives or into its directory.
     *
     * <p>The body carries {@code base}, the fingerprint of what the author had in front of
     * them. If it no longer matches, somebody else came through in the meantime: we refuse,
     * and hand back the current version so they can decide — overwriting would lose a third
     * party's work without anyone noticing.
     */
    @SuppressWarnings("unchecked")
    private static void write(Path root, String uuid, Map<?, ?> body, HttpExchange ex,
                               Rebuilder rebuilder) throws IOException {
        Object value = body.get("valeur");
        String base = body.get("base") == null ? null : String.valueOf(body.get("base"));

        LOCK.lock();
        try {
            Path runDir = Annotations.runsByUuid(root).get(uuid);
            if (runDir == null) {
                text(ex, 404, "no run carries the id " + uuid);
                return;
            }
            Object known = Annotations.forRun(runDir, uuid, Annotations.readCentral(root));
            String current = fingerprint(known);
            if (base != null && !base.equals(current)) {
                json(ex, 409, Map.of("conflit", true,
                        "valeur", known == null ? Map.of() : known,
                        "empreinte", current));
                return;
            }
            Map<String, Object> annotation = value instanceof Map<?, ?> m
                    ? new LinkedHashMap<>((Map<String, Object>) m)
                    : new LinkedHashMap<>();
            Path file = Annotations.write(runDir, annotation);
            System.out.println("   annotation saved: " + file);
            json(ex, 200, Map.of("ok", true, "fichier", file.toString(),
                    "empreinte", fingerprint(annotation.isEmpty() ? null : annotation)));
        } finally {
            LOCK.unlock();
        }
        // After the response: regenerating the page takes a second, and nobody has any
        // reason to wait on it to carry on annotating.
        rebuilder.request();
    }

    /**
     * The annotations as the page must see them: for each run, the one that wins among the
     * three possible locations — see {@link Annotations}.
     */
    private static Map<String, Object> effectiveAnnotations(Path root) {
        Map<String, Object> central = Annotations.readCentral(root);
        Map<String, Object> out = new LinkedHashMap<>();
        Annotations.runsByUuid(root).forEach((uuid, runDir) -> {
            Object value = Annotations.forRun(runDir, uuid, central);
            if (value != null) out.put(uuid, value);
        });
        return out;
    }

    /** An annotation's fingerprint: it changes as soon as its content does, and not before. */
    static String fingerprint(Object value) {
        String text = value == null ? "" : Json.write(value);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    /**
     * Regenerating the page, one at a time and never in bursts.
     *
     * <p>Ten people saving at the same time must not trigger ten concurrent assemblies: a
     * request made while an assembly is running merely asks for one more, at the end.
     */
    private static final class Rebuilder {
        private final Callable<Void> action;
        private boolean running;
        private boolean askedAgain;

        Rebuilder(Callable<Void> action) { this.action = action; }

        synchronized void request() {
            if (running) { askedAgain = true; return; }
            running = true;
            Thread.ofPlatform().daemon().start(this::loop);
        }

        private void loop() {
            while (true) {
                try {
                    action.call();
                } catch (Exception e) {
                    System.err.println("   page could not be rebuilt: " + e.getMessage());
                }
                synchronized (this) {
                    if (!askedAgain) { running = false; return; }
                    askedAgain = false;
                }
            }
        }
    }

    /**
     * Stops a request that has not shown its credentials, and says {@code true} when it
     * has been dealt with.
     *
     * <p>Two different answers, because two different callers: a browser asking for a page
     * is sent to the form, where it will know what to do; a {@code fetch} from the page, or
     * a script, gets a 401 — sending it to HTML would have it parse a form as if it were
     * its data.
     */
    private static boolean bar(HttpExchange ex, Access access) throws IOException {
        if (access.allows(ex)) return false;
        String path = ex.getRequestURI().getPath();
        if (path.startsWith("/__xray/")) {
            text(ex, 401, "shared secret required: open " + path.replaceFirst("/__xray/.*",
                    "/") + " in a browser, or send an Authorization: Bearer header");
            return true;
        }
        String target = ex.getRequestURI().getRawQuery() == null ? path
                : path + "?" + ex.getRequestURI().getRawQuery();
        ex.getResponseHeaders().add("Location", Access.toEntryPage(target));
        send(ex, 302, "text/plain; charset=utf-8", new byte[0]);
        return true;
    }

    /** The entry form, then the cookie and the return to the page asked for. */
    private static void entrer(HttpExchange ex, Access access) throws IOException {
        noCache(ex);
        String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
        if (method.equals("GET")) {
            String target = param(ex.getRequestURI().getRawQuery(), "vers");
            html(ex, 200, Access.entryPage(target, null));
            return;
        }
        if (!method.equals("POST")) {
            text(ex, 405, "method not allowed");
            return;
        }
        Map<String, String> fields = Access.fields(read(ex.getRequestBody()));
        String origin = Access.origin(ex);
        if (access.throttled(origin)) {
            html(ex, 429, Access.entryPage(fields.get("vers"),
                    "Too many attempts from this address. Try again in about thirty seconds."));
            return;
        }
        String session = access.openSession(fields.get("jeton"), origin);
        if (session == null) {
            System.err.println("   access refused from " + origin);
            html(ex, 401, Access.entryPage(fields.get("vers"), "That is not the right secret."));
            return;
        }
        String target = fields.getOrDefault("vers", "/");
        // An open redirect would send somebody who just trusted the address they were
        // given somewhere else: we only go back to a page of this server.
        if (!target.startsWith("/") || target.startsWith("//")) target = "/";
        ex.getResponseHeaders().add("Set-Cookie", access.cookieHeader(session));
        ex.getResponseHeaders().add("Location", target);
        send(ex, 302, "text/plain; charset=utf-8", new byte[0]);
    }

    private static String param(String request, String name) {
        if (request == null) return null;
        for (String chunk : request.split("&")) {
            String[] pair = chunk.split("=", 2);
            if (pair.length == 2 && pair[0].equals(name)) {
                return java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void announcement(Path root, String host, int port, Access access) {
        boolean local = host.startsWith("127.") || host.equals("localhost");
        System.out.println();
        System.out.println("▶ Report served at http://" + (local ? "localhost" : host)
                + ":" + port + "/");
        System.out.println("   Annotations typed in the page are written to the");
        System.out.println("   directory of each run (" + Annotations.IN_THE_RUN
                + "), and the page is rebuilt:");
        System.out.println("   so they hold for everyone, and travel with the run "
                + "if it is moved.");
        access.announce(local);
        System.out.println("   Ctrl-C to stop.");
    }

    /**
     * The file asked for, or {@code null} when it lies outside the served directory.
     *
     * <p>A request path can contain anything, including enough to climb the tree: we
     * normalise, then check that the result is indeed under the root — the check is on the
     * resolved path, never on what was written.
     */
    static Path resolve(Path root, String requestPath) {
        String decoded = java.net.URLDecoder.decode(requestPath, StandardCharsets.UTF_8);
        if (decoded.isBlank() || decoded.equals("/")) decoded = "/index.html";
        Path candidate = root.resolve(decoded.substring(1)).normalize();
        if (!candidate.startsWith(root)) return null;
        return Files.isDirectory(candidate) ? candidate.resolve("index.html") : candidate;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".json") || name.endsWith(".cpuprofile")) return "application/json";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".xml")) return "application/xml";
        return "text/plain; charset=utf-8";
    }

    private static void noCache(HttpExchange ex) {
        // The page is regenerated under the browser's feet: a cached version would show
        // the previous annotation, which would look like a lost write.
        ex.getResponseHeaders().add("Cache-Control", "no-store");
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readNBytes(MAX_BODY), StandardCharsets.UTF_8);
    }

    private static void json(HttpExchange ex, int code, Map<String, Object> body)
            throws IOException {
        send(ex, code, "application/json", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void html(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "text/html; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void text(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        // A length of zero does NOT mean "no body" here: it asks for chunked encoding, and
        // the client then waits for bytes that will never come. A redirect without a body
        // must pass -1.
        ex.sendResponseHeaders(code, body.length == 0 ? -1 : body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
