package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lab.xray.json.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trail exists for one precise case: the tool launched at the bottom of nested scripts,
 * its output going into a pipe, and where <b>nobody sees anything any more</b>. These tests
 * guard what makes that case playable — the file always written, at a path one knows, each
 * of whose lines stands on its own, and whose last one says one can stop watching.
 */
class FollowTest {

    private static List<Map<String, Object>> read(Path dir) throws Exception {
        Path f = dir.resolve(Follow.FILE);
        assertTrue(Files.exists(f), "the trail must be written without being asked for: " + f);
        List<Map<String, Object>> out = new ArrayList<>();
        for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            assertFalse(line.contains("\n"), "one line, one reading: \"tail -f\" depends on it");
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) Json.read(line);
            out.add(m);
        }
        return out;
    }

    @Test
    @DisplayName("The trail is written without a server: the terminal-less case is what justifies it")
    void theTrailIsWrittenWithoutAnyServer(@TempDir Path dir) throws Exception {
        try (Follow follow = Follow.open(dir, dir.resolve("runs/essai"), "essai", "java -jar app.jar", 0)) {
            follow.tick(Duration.ofSeconds(1), Duration.ofMillis(900), 120, 0.9);
            follow.tick(Duration.ofSeconds(2), Duration.ofMillis(1800), 340, 0.9);
            follow.end("ended", 2);
        }
        List<Map<String, Object>> lines = read(dir);
        assertEquals(List.of("start", "progress", "progress", "end"),
                lines.stream().map(l -> l.get("event")).toList());
    }

    @Test
    @DisplayName("Each line stands on its own, and the last says one can stop")
    void eachLineStandsAloneAndTheLastOneSaysStop(@TempDir Path dir) throws Exception {
        try (Follow follow = Follow.open(dir, dir.resolve("runs/essai"), "recette 1", "java -jar app.jar", 0)) {
            follow.tick(Duration.ofSeconds(7), Duration.ofSeconds(12), 4096, 1.7);
            follow.end("ended", 7);
        }
        List<Map<String, Object>> lines = read(dir);
        for (Map<String, Object> l : lines) {
            // Without the run's name on every line, a trail that accumulates several runs
            // — which it does — becomes impossible to read back.
            assertEquals("recette 1", l.get("run"));
            assertTrue(l.containsKey("date"), "a line with no timestamp cannot be cross-checked");
        }
        assertEquals("java -jar app.jar", lines.get(0).get("command"),
                "without the command, a run that is moving does not say which one is");
        Map<String, Object> tick = lines.get(1);
        assertEquals(7L, ((Number) tick.get("seconds")).longValue());
        assertEquals(1.7, ((Number) tick.get("cores")).doubleValue(), 0.001);
        assertEquals(4, ((Number) tick.get("level")).intValue(),
                "1.7 cores is the densest tier — the same threshold as the band");
        assertEquals("end", lines.get(lines.size() - 1).get("event"),
                "a reader following the file must know when to stop");
    }

    @Test
    @DisplayName("The tier written is the band's, never a second calculation")
    void theWrittenLevelIsTheOneFromTheBand(@TempDir Path dir) throws Exception {
        // Two load calculations would end up diverging, and the file would contradict the
        // terminal without anything saying so. The tier therefore comes from Progress.
        try (Follow follow = Follow.open(dir, dir.resolve("runs/x"), "x", "", 0)) {
            for (double cores : new double[]{0.0, 0.05, 0.3, 1.0, 3.0}) {
                follow.tick(Duration.ofSeconds(1), Duration.ZERO, 0, cores);
            }
        }
        List<Integer> tiers = read(dir).stream()
                .filter(l -> "progress".equals(l.get("event")))
                .map(l -> ((Number) l.get("level")).intValue()).toList();
        assertEquals(List.of(0, 1, 2, 3, 4), tiers);
    }

    @Test
    @DisplayName("A failed write never takes down the measurement, which is what one came for")
    void aFailedWriteNeverTakesTheMeasurementDown(@TempDir Path dir) throws Exception {
        // An impossible path: a file where a directory should be.
        Path bar = dir.resolve("barre");
        Files.writeString(bar, "not a directory", StandardCharsets.UTF_8);
        try (Follow follow = Follow.open(bar.resolve("dedans"), dir, "essai", "", 0)) {
            follow.tick(Duration.ofSeconds(1), Duration.ZERO, 0, 0.5);
            follow.end("ended", 1);
        }
        // Nothing was written, and above all: nothing was thrown.
        assertFalse(Files.exists(bar.resolve("dedans").resolve(Follow.FILE)));
    }

    @Test
    @DisplayName("The log tail is bounded: the display is not paid for out of the measurement")
    void theLogTailIsBounded(@TempDir Path dir) throws Exception {
        StringBuilder big = new StringBuilder();
        while (big.length() < 300_000) big.append("une ligne de journal bien ordinaire\n");
        Path log = dir.resolve("execution.log");
        Files.writeString(log, big.toString(), StandardCharsets.UTF_8);

        byte[] tail = Follow.tail(log);
        assertTrue(tail.length <= 64 * 1024,
                "a chatty application writes tens of megabytes: copying them at every "
                + "refresh would make the machine that measures pay for the display");
        assertTrue(tail.length > 60_000, "and there must still be enough to read the end");
        assertEquals(0, Follow.tail(dir.resolve("jamais-ecrit.log")).length,
                "before the application writes there is nothing, and that is not an error");
    }

    @Test
    @DisplayName("The page travels inside the jar: it must serve on a machine without a network")
    void thePageTravelsInsideTheJar() throws Exception {
        String page = new String(Follow.page(), StandardCharsets.UTF_8);
        assertTrue(page.contains("progression.jsonl"), "it re-reads the trail, it does not create one");
        assertFalse(page.contains("http://") && page.contains("cdn"),
                "no dependency served from outside: the target machine has no network");
        assertFalse(page.contains("<script src="),
                "nothing to go and fetch: the page must show whole or not at all");
        assertTrue(page.contains("busy cores"),
                "the count is in cores, not in a share of the machine — and the page must say so, "
                + "otherwise one reads saturation where there is one core out of thirty-two");
    }

    @Test
    @DisplayName("A log that is not UTF-8 is read all the same, and the fallback is stated")
    void aLogThatIsNotUtf8IsStillReadAndTheFallbackIsStated() {
        // The tool writes UTF-8; the observed application writes in whatever its JVM gave
        // it, and on the target estate that is often CP1252 or CP850. Served as UTF-8 as it
        // is, "démarrage terminé" becomes gibberish on half of the French logs.
        byte[] latin1 = "démarrage terminé".getBytes(StandardCharsets.ISO_8859_1);
        String rendered = new String(Follow.asUtf8(latin1), StandardCharsets.UTF_8);
        assertTrue(rendered.contains("démarrage terminé"), "the accents must come back");
        assertTrue(rendered.contains("ISO-8859-1"),
                "guessing is acceptable here, keeping quiet about it is not: a reader who sees a "
                + "doubtful character must know it is an interpretation");

        // Valid UTF-8 is never touched — no fallback, no warning.
        byte[] utf8 = "démarrage terminé".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(utf8, Follow.asUtf8(utf8));
        assertEquals(0, Follow.asUtf8(new byte[0]).length);
    }
}
