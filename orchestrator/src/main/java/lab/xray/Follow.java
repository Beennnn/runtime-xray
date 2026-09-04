package lab.xray;

import lab.xray.json.Json;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What happens during the run, written to a file — and served to a browser on request.
 *
 * <h2>Why a file, and not just the console</h2>
 *
 * <p>{@link Progress} draws a band in the terminal, and falls silent as soon as there is
 * none. That is the right behaviour for a comfort display — one line per second in an
 * integration log tells nobody anything — but it leaves the most common case in the field
 * unanswered: <b>the tool is launched at the bottom of nested scripts</b>, its output goes
 * into a pipe or a file, and nobody sees anything.
 *
 * <p>Hence this file. It is written <b>always</b>, without being asked for, like
 * {@code diagnostic.json}: one JSON line per second, in {@code progression.jsonl}, at the
 * root of the output directory — so at a path one knows without knowing the run's name.
 * {@code tail -f} is enough to follow it, from any other terminal, including on a machine
 * where that is all one has.
 *
 * <h2>Why a page, and not just the file</h2>
 *
 * <p>Because a sequence of JSON lines answers badly the question one actually asks: <i>is
 * this moving?</i> One has to compare the current line with the previous ones, and the eye
 * does not do that on scrolling text. The page, on the other hand, shows the shape: the
 * activity band, the curve of busy cores, the output growing or no longer growing. It
 * re-reads the same file, it produces no other.
 *
 * <p>It is <b>optional</b> ({@code --suivi}), and the file is not: what must work
 * everywhere must not depend on an open port.
 *
 * <h2>What this object does not do</h2>
 *
 * <p>It measures nothing — the same rule as {@link Progress}, and for the same reason. The
 * processor time comes from what the system counts anyway, the output size from a file
 * already written. Nothing is added to the observed JVM, and the report is the same
 * whether the run is followed or not. The server, when it runs, serves two files from disk
 * on a thread of its own; it never goes near the observed process.
 */
public final class Follow implements AutoCloseable {

    /** The file to follow. At the root of the output: one knows it without looking. */
    public static final String FILE = "progression.jsonl";

    /** The page's default port. Next to the served report's, without colliding with it. */
    public static final int PORT = 8788;

    private final Path file;
    private final String run;
    private final HttpServer server;

    private Follow(Path file, String run, HttpServer server) {
        this.file = file;
        this.run = run;
        this.server = server;
    }

    /**
     * Opens the trail of one run.
     *
     * @param port the page's port, or 0 to write the file only
     */
    public static Follow open(Path outDir, Path runDir, String run, String command,
                               int port) {
        Path file = outDir.resolve(FILE);
        HttpServer server = null;
        if (port > 0) {
            try {
                server = serve(file, runDir, port);
            } catch (IOException e) {
                // A taken port must not cost the run: the observation is worth more than
                // its display, and the file is written anyway.
                System.out.println("   follow page not served on port " + port + " ("
                        + e.getMessage() + ") — " + FILE + " is written anyway");
            }
        }
        Follow follow = new Follow(file, run, server);
        // The observed command on the opening line: without it, whoever opens the page or
        // the file sees a run moving without knowing which one. It does not leave the
        // machine — the page only listens on the loopback.
        follow.write("start", Json.ordered("command", command == null ? "" : command,
                "output", outDir.toAbsolutePath().toString()));
        return follow;
    }

    /** One progress line. The same figures as the terminal band. */
    public void tick(Duration elapsed, Duration cpu, long bytes, double cores) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("seconds", elapsed.toSeconds());
        f.put("cores", Math.round(cores * 100) / 100.0);
        f.put("level", Progress.tier(cores));
        f.put("cpuSeconds", cpu.toSeconds());
        f.put("outputBytes", bytes);
        write("progress", f);
    }

    /** The last line: what lets a reader know they can stop. */
    public void end(String status, long seconds) {
        write("end", Json.ordered("status", status, "seconds", seconds));
    }

    private void write(String event, Map<String, Object> fields) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("event", event);
        line.put("run", run);
        line.put("date", Instant.now().toString());
        line.putAll(fields);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.write(line) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Following is a comfort. Its failing must never take down the measurement,
            // which is what one came for.
        }
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
    }

    // ------------------------------------------------------------------ the page

    private static HttpServer serve(Path file, Path runDir, int port) throws IOException {
        // Loopback only: this page shows a command and an application's output, which
        // have no business being readable from the network.
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);

        server.createContext("/", ex -> {
            String path = ex.getRequestURI().getPath();
            try {
                switch (path) {
                    case "/", "/index.html" -> send(ex, "text/html; charset=utf-8", page());
                    case "/progression.jsonl" -> send(ex, "application/x-ndjson; charset=utf-8",
                            readOrEmpty(file));
                    case "/execution.log" -> {
                        byte[] raw = tail(runDir.resolve("execution.log"));
                        send(ex, "text/plain; charset=utf-8", asUtf8(raw));
                    }
                    default -> send(ex, "text/plain; charset=utf-8",
                            "nothing here".getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                send(ex, "text/plain; charset=utf-8",
                        String.valueOf(e.getMessage()).getBytes(StandardCharsets.UTF_8));
            }
        });
        server.setExecutor(null);
        server.start();
        System.out.println("   live follow: http://127.0.0.1:" + port
                + "   (or: tail -f " + file + ")");
        return server;
    }

    static byte[] page() throws IOException {
        try (InputStream in = Follow.class.getResourceAsStream("suivi.html")) {
            if (in == null) throw new IOException("follow page missing from the jar");
            return in.readAllBytes();
        }
    }

    private static byte[] readOrEmpty(Path f) throws IOException {
        return Files.exists(f) ? Files.readAllBytes(f) : new byte[0];
    }

    /**
     * The tail of the application's log.
     *
     * <p>Bounded: a chatty application writes tens of megabytes, and copying them at every
     * refresh would make the machine that measures pay for the display. What matters while
     * waiting is the end of it anyway.
     */
    static byte[] tail(Path log) throws IOException {
        if (!Files.exists(log)) return new byte[0];
        long size = Files.size(log);
        long since = Math.max(0, size - 64 * 1024);
        try (var channel = Files.newByteChannel(log)) {
            channel.position(since);
            java.nio.ByteBuffer buffer =
                    java.nio.ByteBuffer.allocate((int) Math.min(size - since, 64 * 1024));
            channel.read(buffer);
            return buffer.array();
        }
    }

    /**
     * The application's log, made readable without pretending to know which character set
     * it was written in.
     *
     * <p>The tool writes UTF-8; the observed application does not — it writes in whatever
     * its JVM gave it, and on the estate we target that is often CP1252 or CP850. Serving
     * those bytes as UTF-8 gives gibberish instead of accents, on half of the French logs.
     *
     * <p>So we guess only once, and only when certainty has failed: if the bytes form
     * valid UTF-8, they are UTF-8; otherwise they are read as ISO-8859-1, which rejects no
     * byte and renders the accents of the western sets. That is not exact in every case,
     * and the fallback is <b>announced in the page</b> rather than merely suffered: a
     * reader who sees a doubtful character must know it is an interpretation.
     *
     * <p>The opposite — guessing in silence — is what the tool refuses elsewhere, for
     * source roots. The difference lies in what the mistake costs: wrong code shown beside
     * a coverage figure gets believed, a crooked accent gets seen.
     */
    static byte[] asUtf8(byte[] raw) {
        if (raw.length == 0) return raw;
        var strict = StandardCharsets.UTF_8.newDecoder();
        try {
            strict.decode(java.nio.ByteBuffer.wrap(raw));
            return raw;
        } catch (java.nio.charset.CharacterCodingException notUtf8) {
            String text = new String(raw, StandardCharsets.ISO_8859_1);
            return ("[runtime-xray: this log is not UTF-8; read as ISO-8859-1]\n"
                    + text).getBytes(StandardCharsets.UTF_8);
        }
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, String type, byte[] body)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        // Without this a browser serves the first response for the whole run: the page
        // would look frozen while the file grows.
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        } else {
            ex.close();
        }
    }
}
