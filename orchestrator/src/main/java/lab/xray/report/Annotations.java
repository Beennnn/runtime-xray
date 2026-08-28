package lab.xray.report;

import lab.xray.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What is added to a run after it has been measured: a name, a description, tags, a
 * pruning of its tree.
 *
 * <p>These annotations can live in three places, and the choice is not cosmetic — it
 * decides <b>what travels with what</b>:
 *
 * <table>
 *   <caption>Where an annotation can live</caption>
 *   <tr><th>Location</th><th>What it implies</th></tr>
 *   <tr><td>{@code runs/<run>/config.json}</td>
 *       <td><b>Takes priority.</b> The annotation is inside the run's directory: it follows
 *       the run everywhere — a copy, an archive, a colleague's inbox</td></tr>
 *   <tr><td>{@code runs/<run>-config.json}</td>
 *       <td>Beside the directory, same name with a suffix. The run stays untouched, which
 *       helps when it is read-only or signed</td></tr>
 *   <tr><td>{@code noms.json}</td>
 *       <td>One single file for the whole report, indexed by id. This is the exchange
 *       format: the one exported from the page and handed to someone else</td></tr>
 * </table>
 *
 * <p>The order is the table's: <b>the closest to the run wins</b>. An annotation placed in
 * the directory beats the one beside it, which beats the central file — because whoever
 * took the trouble to file it with the measurement expressed a more precise intent than
 * whoever filled in the common file.
 *
 * <p>No field-by-field merge between the three: the closest source is taken <b>whole</b>.
 * Mixing a name from one file and a description from another would give an annotation
 * nobody wrote, and that nobody would know how to correct.
 */
public final class Annotations {

    /** The common file, indexed by run id. */
    public static final String CENTRAL = "noms.json";
    /** The file placed INSIDE the run's directory. */
    public static final String IN_THE_RUN = "config.json";
    /** The suffix of the file placed BESIDE the directory. */
    public static final String SUFFIX = "-config.json";

    private Annotations() {}

    /**
     * The annotation kept for a run, closest source first.
     *
     * @param central the contents of {@code noms.json}, already read — it serves every run
     * @return the value as it was written (string or object), or {@code null}
     */
    public static Object forRun(Path runDir, String uuid, Map<String, Object> central) {
        Object inside = readFile(runDir.resolve(IN_THE_RUN));
        if (inside != null) return inside;
        Object beside = readFile(runDir.resolveSibling(runDir.getFileName() + SUFFIX));
        if (beside != null) return beside;
        return uuid == null || uuid.isBlank() ? null : central.get(uuid);
    }

    /**
     * Where to write a run's annotation.
     *
     * <p>Where it already lives, if it lives anywhere: rewriting elsewhere would leave two
     * versions, one of which — the one that takes priority — would not be the one just
     * typed in. Otherwise, in the run's directory: that is the location that makes it
     * travel with the measurement, and the one wanted by default.
     */
    public static Path fileFor(Path runDir) {
        Path inside = runDir.resolve(IN_THE_RUN);
        if (Files.isRegularFile(inside)) return inside;
        Path beside = runDir.resolveSibling(runDir.getFileName() + SUFFIX);
        if (Files.isRegularFile(beside)) return beside;
        return inside;
    }

    /** Writes a run's annotation, or removes the file when it is empty. */
    public static Path write(Path runDir, Map<String, Object> annotation) throws IOException {
        Path file = fileFor(runDir);
        if (annotation == null || annotation.isEmpty()) {
            Files.deleteIfExists(file);
            return file;
        }
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.write(annotation), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        return file;
    }

    /** The runs present under {@code commonDir}, by id. */
    public static Map<String, Path> runsByUuid(Path commonDir) {
        Map<String, Path> out = new LinkedHashMap<>();
        for (Path run : runDirs(commonDir)) {
            String uuid = uuidOf(run);
            if (uuid != null) out.put(uuid, run);
        }
        return out;
    }

    /** The run directories: those that carry a {@code run-context.json}. */
    public static List<Path> runDirs(Path commonDir) {
        List<Path> out = new ArrayList<>();
        Path runs = commonDir.resolve("runs");
        if (!Files.isDirectory(runs)) return out;
        try (var listing = Files.list(runs)) {
            listing.filter(Files::isDirectory)
                   .filter(d -> Files.isRegularFile(d.resolve("run-context.json")))
                   .sorted()
                   .forEach(out::add);
        } catch (IOException e) {
            System.err.println("   runs unreadable under " + runs + " : " + e.getMessage());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static String uuidOf(Path runDir) {
        Object read = readFile(runDir.resolve("run-context.json"));
        if (read instanceof Map<?, ?> m) {
            Object uuid = ((Map<String, Object>) m).get("uuid");
            return uuid == null || String.valueOf(uuid).isBlank() ? null : String.valueOf(uuid);
        }
        return null;
    }

    /** Reads a JSON file, or {@code null} when it is absent — or unreadable, and we say so. */
    public static Object readFile(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return Json.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("   " + file + " unreadable — skipped (" + e.getMessage() + ")");
            return null;
        }
    }

    /** The common file, or an empty map when it does not exist. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readCentral(Path commonDir) {
        Object read = readFile(commonDir.resolve(CENTRAL));
        return read instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
    }
}
