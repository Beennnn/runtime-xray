package lab.xray;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Fetches and caches the three analysis components.
 *
 * <p>Everything goes through a Maven repository — the publisher's by default, or the
 * company's internal mirror. That is a choice: on a closed network, the Maven mirror is
 * very often the only open channel, and it is already how these three tools are
 * distributed.
 *
 * <p>The cache ({@code ~/.runtime-xray}) means that on the second launch nothing goes out
 * on the network at all. On a genuinely isolated machine, carrying that directory is
 * enough.
 */
public final class Toolbox {

    private static final String JACOCO_VERSION = "0.8.13";
    private static final String ARTHAS_VERSION = "4.3.4";
    private static final String ASYNC_VERSION = "4.1";

    private final Path cache;
    private final String repo;
    /** Directories searched flat, in order, before going out on the network. */
    private final List<Path> flat;
    /** The local Maven repository, searched in group/artifact/version layout. */
    private final Path localRepo;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Toolbox(String repo) {
        this(repo, "");
    }

    /**
     * @param componentsDir directory named by the user ({@code --components}), or empty.
     */
    public Toolbox(String repo, String components) {
        this(repo,
             Path.of(System.getProperty("user.home"), ".runtime-xray"),
             locations(components),
             localMavenRepo());
    }

    Toolbox(String repo, Path cache, List<Path> flat, Path localRepo) {
        this.cache = cache;
        this.flat = flat;
        this.localRepo = localRepo;
        this.repo = repo.endsWith("/") ? repo.substring(0, repo.length() - 1) : repo;
    }

    /**
     * The places where a hand-placed component can be, from the most explicit to the least
     * likely: what the user named, then the jar's neighbourhood — that is the natural thing
     * to do when carrying the tool on a stick.
     */
    private static List<Path> locations(String components) {
        List<Path> dirs = new ArrayList<>();
        if (components != null && !components.isBlank()) dirs.add(Path.of(components.trim()));
        String env = System.getenv("RUNTIME_XRAY_COMPOSANTS");
        if (env != null && !env.isBlank()) dirs.add(Path.of(env.trim()));
        Path jar = jarDir();
        if (jar != null) {
            dirs.add(jar);
            dirs.add(jar.resolve("composants"));
        }
        return dirs;
    }

    /** {@code null} outside a jar — under test, the code lives in a class directory. */
    private static Path jarDir() {
        try {
            java.net.URL src = Toolbox.class.getProtectionDomain().getCodeSource().getLocation();
            Path p = Path.of(src.toURI());
            return Files.isDirectory(p) ? null : p.getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private static Path localMavenRepo() {
        String explicit = System.getenv("MAVEN_REPO_LOCAL");
        if (explicit != null && !explicit.isBlank()) return Path.of(explicit.trim());
        return Path.of(System.getProperty("user.home"), ".m2", "repository");
    }

    public Path jacocoAgent() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.agent", JACOCO_VERSION, "runtime", "jar",
                "jacocoagent.jar");
    }

    public Path jacocoCli() throws IOException, InterruptedException {
        return artifact("org.jacoco", "org.jacoco.cli", JACOCO_VERSION, "nodeps", "jar",
                "jacococli.jar");
    }

    /**
     * async-profiler's official converter: it turns the folded stacks into the page the
     * tool produces itself.
     *
     * <p>Why produce it when this page already has its own tree: because they are not the
     * same thing. Ours is a <b>summary</b>, with its choices (folding the JDK, folding the
     * hidden packages, aggregating). This one is the tool's <b>raw</b> output, without any
     * of our processing — it is the one that settles the matter if the summary is wrong,
     * and the one that stays readable if the summary no longer opens.
     */
    public Path asyncProfilerConverter() throws IOException, InterruptedException {
        return artifact("tools.profiler", "jfr-converter", ASYNC_VERSION, null, "jar",
                "jfr-converter.jar");
    }

    /**
     * async-profiler's native library, extracted from the jar published on Maven Central.
     * The jar bundles binaries for several platforms; only the right one is taken out.
     */
    public Path asyncProfilerLibrary() throws IOException, InterruptedException {
        String explicit = System.getenv("ASYNC_PROFILER_LIB");
        if (explicit != null && Files.isRegularFile(Path.of(explicit))) {
            return Path.of(explicit);
        }
        String entry = nativeEntryName();
        Path target = cache.resolve("libasyncProfiler-" + ASYNC_VERSION + suffix(entry));
        if (Files.isRegularFile(target)) return target;

        Path jar = artifact("tools.profiler", "async-profiler", ASYNC_VERSION, null, "jar",
                "async-profiler.jar");
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry e = zip.getEntry(entry);
            if (e == null) {
                throw new IOException("async-profiler " + ASYNC_VERSION
                        + " does not contain " + entry + " — platform not supported");
            }
            Files.createDirectories(cache);
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    /** Arthas's complete package, unpacked: its launcher will not go out on the network. */
    public Path arthasHome() throws IOException, InterruptedException {
        Path home = cache.resolve("arthas-" + ARTHAS_VERSION);
        if (Files.isRegularFile(home.resolve("arthas-boot.jar"))) return home;

        // Arthas is also distributed unpacked: whoever already has it to hand most often
        // has it in that form, and asking for the archive again would make no sense.
        for (Path dir : flat) {
            for (Path candidate : List.of(dir.resolve("arthas-" + ARTHAS_VERSION),
                                         dir.resolve("arthas"), dir)) {
                if (Files.isRegularFile(candidate.resolve("arthas-boot.jar"))) {
                    System.out.println("   local component: " + candidate);
                    return candidate;
                }
            }
        }

        Path zip = artifact("com.taobao.arthas", "arthas-packaging", ARTHAS_VERSION, "bin", "zip",
                "arthas-bin.zip");
        Files.createDirectories(home);
        unzip(zip, home);
        return home;
    }

    // ------------------------------------------------------------------ interne

    /**
     * Finds a component, without the network where possible.
     *
     * <p>The network is the last resort, never the first reflex: on the machine we care
     * about, there is none. So we look first at the cache, then at what the user placed by
     * hand, then at the local Maven repository — the one that any company {@code mvn} has
     * already filled from the internal mirror.
     *
     * @param usual the name of the same component in its publisher's distribution. That is
     *              the name it has when it did not come from Maven:
     *              {@code jacocoagent.jar}, not
     *              {@code org.jacoco.agent-0.8.13-runtime.jar}. We accept it, while saying
     *              that the version is then not verified.
     */
    private Path artifact(String group, String name, String version, String classifier, String ext,
                          String usual) throws IOException, InterruptedException {
        String file = name + "-" + version + (classifier == null ? "" : "-" + classifier) + "." + ext;
        List<String> seen = new ArrayList<>();

        Path local = cache.resolve(file);
        seen.add(local.toString());
        if (Files.isRegularFile(local)) return local;

        for (Path dir : flat) {
            Path exact = dir.resolve(file);
            seen.add(exact.toString());
            if (Files.isRegularFile(exact)) {
                System.out.println("   local component: " + exact);
                return exact;
            }
            if (usual != null) {
                Path other = dir.resolve(usual);
                seen.add(other.toString());
                if (Files.isRegularFile(other)) {
                    System.out.println("   local component: " + other + note(other, version));
                    return other;
                }
            }
        }

        Path m2 = localRepo.resolve(group.replace('.', '/')).resolve(name).resolve(version)
                .resolve(file);
        seen.add(m2.toString());
        if (Files.isRegularFile(m2)) {
            System.out.println("   local component: " + m2);
            return m2;
        }

        Path bundled = extractFromJar(file);
        if (bundled != null) return bundled;
        seen.add("(bundled in the jar: complete edition only)");

        String url = repo + "/" + group.replace('.', '/') + "/" + name + "/" + version + "/" + file;
        Files.createDirectories(cache);
        Path tmp = Files.createTempFile(cache, "dl-", ".part");
        System.out.println("   downloading: " + file);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).build();
        HttpResponse<Path> res;
        try {
            res = http.send(req, HttpResponse.BodyHandlers.ofFile(tmp));
        } catch (IOException failure) {
            Files.deleteIfExists(tmp);
            throw new IOException(unavailable(file, url, seen) + "\n   (" + failure + ")");
        }
        if (res.statusCode() != 200) {
            Files.deleteIfExists(tmp);
            throw new IOException(unavailable(file, url + " → HTTP " + res.statusCode(), seen));
        }
        Files.move(tmp, local, StandardCopyOption.REPLACE_EXISTING);
        return local;
    }

    /**
     * What is added after a component found under its usual name.
     *
     * <p>That name does not carry the version: {@code jacocoagent.jar} can be any of them.
     * Rather than warning just in case — which worries whoever brought the right file and
     * teaches nothing to whoever brought the wrong one — we read the version the file
     * declares itself. Silence when it matches, a precise warning otherwise.
     *
     * @return the note to display, empty when there is nothing to report.
     */
    static String note(Path file, String expected) {
        String declared = declaredVersion(file);
        if (declared == null) {
            return "  (version not verified, " + expected + " expected)";
        }
        if (declared.equals(expected)) {
            return "";
        }
        return "  (version " + declared + " found, " + expected + " expected:"
                + " components of the same tool must agree)";
    }

    /**
     * The version the file declares in its manifest, or {@code null} when it declares none
     * — async-profiler's and Arthas's archives are in that case, and so is an unreadable
     * file. We then conclude nothing: not knowing is not a fault.
     */
    private static String declaredVersion(Path file) {
        try (JarFile jar = new JarFile(file.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return null;
            return manifest.getMainAttributes().getValue("Implementation-Version");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The complete edition of the jar carries the components as resources. They are then
     * dropped into the cache — a {@code -javaagent} wants a file on disk, not an archive
     * entry, and JaCoCo's CLI as well as Arthas are launched as separate processes.
     *
     * @return {@code null} for the ordinary jar, which bundles nothing.
     */
    private Path extractFromJar(String file) throws IOException {
        try (InputStream in = Toolbox.class.getResourceAsStream("/lab/xray/composants/" + file)) {
            if (in == null) return null;
            Files.createDirectories(cache);
            Path tmp = Files.createTempFile(cache, "ex-", ".part");
            System.out.println("   bundled component: " + file);
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Path target = cache.resolve(file);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }

    /** A failure here stops everything: the message says where we looked, and the way out. */
    private static String unavailable(String file, String url, List<String> seen) {
        return "component not found: " + file
                + "\n   looked for on the network: " + url
                + "\n   looked for on disk:\n      " + String.join("\n      ", seen)
                + "\n   On a closed network: drop the file into one of these directories,"
                + "\n   point at it with --composants <directory>, or name the internal"
                + "\n   Maven mirror with --repo <url> (or MAVEN_REPO).";
    }

    /** Path of the native library inside the jar, by operating system and architecture. */
    private static String nativeEntryName() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) return "macos/libasyncProfiler.so";  // binaire universel
        if (os.contains("linux")) {
            boolean arm = arch.contains("aarch64") || arch.contains("arm");
            return arm ? "linux-arm64/libasyncProfiler.so" : "linux-x64/libasyncProfiler.so";
        }
        throw new IOException("Timing measurements are not available on " + os
                + ": async-profiler only publishes macOS and Linux."
                + "\n   The analysis still works without them (coverage and values).");
    }

    private static String suffix(String entry) {
        return entry.startsWith("macos/") ? ".dylib" : ".so";
    }

    private static void unzip(Path zip, Path target) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                Path out = target.resolve(e.getName()).normalize();
                // A zip can contain paths that climb out of the target.
                if (!out.startsWith(target)) {
                    throw new IOException("suspicious archive entry: " + e.getName());
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream in = file.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    public boolean asyncProfilerAvailable() {
        try {
            nativeEntryName();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
