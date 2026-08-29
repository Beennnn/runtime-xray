package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These records are the only place in the report where <b>values</b> appear. A reading
 * mistake is particularly expensive there: it does not show — a wrong value is simply
 * displayed, with a measurement's authority. Two real bugs are covered here: nested values
 * being truncated, and the return value taken for an argument.
 */
class InspectionTest {

    private static final String ESC = "\u001B";

    /** A reading conforming to what the inspector produces, indentation included. */
    private static final String SAMPLE = """
            method=lab.sample.RoutePlanner.legMinutes location=AtExit
            ts=2026-08-20 21:08:16.440; [cost=1.240042ms] result=@ArrayList[
                @Object[][
                    @Leg[Leg[from=Auch, to=Foix, distanceKm=54.0]],
                    @CarSpeed[lab.sample.speed.CarSpeed@299a06ac],
                    @Mode[CAR],
                ],
                @Double[29.720971636363636],
            ]
            method=lab.sample.RoutePlanner.travelTimeMinutes location=AtExit
            ts=2026-08-20 21:08:16.466; [cost=63.983125ms] result=@ArrayList[
                @Object[][
                    @Trip[Trip[id=TRIP-112, mode=CAR]],
                ],
                @Double[64.0],
            ]
            """;

    private Path write(Path dir, String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content, StandardCharsets.UTF_8);
        return f;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstCall(Inspection in, String frame) {
        List<Object> calls = (List<Object>) in.values.get(frame);
        assertNotNull(calls, "no value for " + frame + " — keys: " + in.values.keySet());
        return (Map<String, Object>) calls.get(0);
    }

    @Test
    @DisplayName("The values are attached to EACH method, not to the class")
    void groupsValuesByMethod(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        assertEquals(2, in.values.size(), "two distinct methods expected");
        assertTrue(in.values.containsKey("lab/sample/RoutePlanner.legMinutes"));
        assertTrue(in.values.containsKey("lab/sample/RoutePlanner.travelTimeMinutes"));
    }

    @Test
    @DisplayName("A nested value is not truncated at its first closing bracket")
    void keepsNestedBracketsIntact(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) firstCall(in, "lab/sample/RoutePlanner.legMinutes").get("params");
        assertEquals("Leg[from=Auch, to=Foix, distanceKm=54.0]",
                ((Map<?, ?>) params.get(0)).get("value"),
                "the value's closing bracket must be kept");
    }

    @Test
    @DisplayName("The return value really is the return value, not the last argument")
    void separatesReturnFromParameters(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(write(dir, "v.txt", SAMPLE), dir.resolve("absent.txt"), 8);
        Map<String, Object> call = firstCall(in, "lab/sample/RoutePlanner.legMinutes");
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) call.get("params");
        assertEquals(3, params.size(), "three arguments expected");
        assertEquals("CAR", ((Map<?, ?>) params.get(2)).get("value"));
        assertEquals("29.720971636363636", ((Map<?, ?>) call.get("retour")).get("value"));
    }

    @Test
    @DisplayName("The terminal colour codes are removed")
    void stripsTerminalColours(@TempDir Path dir) throws IOException {
        String coloured = SAMPLE.replace("@Mode[CAR]", ESC + "[1;31m@Mode[CAR]" + ESC + "[0m");
        Inspection in = Inspection.read(write(dir, "v.txt", coloured), dir.resolve("a.txt"), 8);
        @SuppressWarnings("unchecked")
        List<Object> params = (List<Object>) firstCall(in, "lab/sample/RoutePlanner.legMinutes").get("params");
        assertEquals("CAR", ((Map<?, ?>) params.get(2)).get("value"));
    }

    @Test
    @DisplayName("Synthetic methods are left out: no human wrote them")
    void skipsSyntheticMethods(@TempDir Path dir) throws IOException {
        String withSynthetic = """
                method=lab.sample.RoutePlanner.$jacocoInit location=AtExit
                ts=2026-08-20 21:08:16.440; [cost=0.1ms] result=@ArrayList[
                    @Object[][
                        @boolean[][true, true],
                    ],
                    @boolean[][true],
                ]
                """ + SAMPLE;
        Inspection in = Inspection.read(write(dir, "v.txt", withSynthetic), dir.resolve("a.txt"), 8);
        assertFalse(in.values.containsKey("lab/sample/RoutePlanner.$jacocoInit"),
                "the coverage probe must not appear as an observed method");
        assertEquals(2, in.values.size());
    }

    @Test
    @DisplayName("The number of calls kept per method is bounded")
    void limitsCallsPerMethod(@TempDir Path dir) throws IOException {
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            many.append(SAMPLE);
        }
        Inspection in = Inspection.read(write(dir, "v.txt", many.toString()), dir.resolve("a.txt"), 5);
        @SuppressWarnings("unchecked")
        List<Object> calls = (List<Object>) in.values.get("lab/sample/RoutePlanner.legMinutes");
        assertEquals(5, calls.size());
    }

    @Test
    @DisplayName("The trace ties each call to its line of code and to its duration")
    void readsTraceLinesAndDurations(@TempDir Path dir) throws IOException {
        String trace = """
                `---ts=2026-08-20 20:10:59.049;thread_name=main;id=1
                    `---[0.416459ms] lab.sample.RoutePlanner:travelTimeMinutes()
                        +---[4,08% 0.017ms ] lab.sample.model.Trip:mode() #43
                        +---[13,35% 0.055583ms ] lab.sample.RoutePlanner:legMinutes() #50
                        `---[2,06% 0.008583ms ] lab.sample.comfort.Breaks:totalMinutes() #67
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        assertEquals(3, in.trace.size());
        @SuppressWarnings("unchecked")
        List<Object> line50 = (List<Object>) in.trace.get("50");
        Map<?, ?> call = (Map<?, ?>) line50.get(0);
        assertEquals("RoutePlanner.legMinutes()", call.get("callee"));
        assertEquals(1, call.get("n"), "a single observation here");
        assertEquals(0.055583, (Double) call.get("minMs"), 1e-9);
        assertEquals(0.055583, (Double) call.get("maxMs"), 1e-9);
        assertEquals("lab/sample/RoutePlanner.legMinutes", call.get("frame"),
                "the frame must follow the package/Class.method convention to be clickable");
    }

    @Test
    @DisplayName("Several invocations of one line are aggregated, not piled up")
    void aggregatesRepeatedObservations(@TempDir Path dir) throws IOException {
        // Tracing several invocations is what makes it possible to annotate more lines —
        // but the same line then comes back as many times. Piling up the duplicates would
        // make the annotation unreadable; we aggregate, keeping the range of durations
        // observed.
        String trace = """
                    +---[1,0% 0.010ms ] a.b.C:calculer() #12
                    +---[1,0% 0.030ms ] a.b.C:calculer() #12
                    +---[1,0% 0.020ms ] a.b.C:calculer() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        @SuppressWarnings("unchecked")
        List<Object> line = (List<Object>) in.trace.get("12");
        assertEquals(1, line.size(), "one single entry for the same callee");
        Map<?, ?> call = (Map<?, ?>) line.get(0);
        assertEquals(3, call.get("n"), "the three observations are counted");
        assertEquals(0.010, (Double) call.get("minMs"), 1e-9);
        assertEquals(0.030, (Double) call.get("maxMs"), 1e-9);
    }

    @Test
    @DisplayName("Each pass of a loop is kept, in order")
    void keepsEachIterationInOrder(@TempDir Path dir) throws IOException {
        // The aggregate would say "3 times, from 0.010 to 0.300 ms" and would hide that it
        // is the FIRST iteration that cost everything — often the very information sought.
        String trace = """
                    +---[1,0% 0.300ms ] a.b.C:calculer() #12
                    +---[1,0% 0.012ms ] a.b.C:calculer() #12
                    +---[1,0% 0.010ms ] a.b.C:calculer() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        @SuppressWarnings("unchecked")
        Map<String, Object> call = (Map<String, Object>) ((List<Object>) in.trace.get("12")).get(0);
        @SuppressWarnings("unchecked")
        List<Object> iterations = (List<Object>) call.get("passages");
        assertEquals(List.of(0.300, 0.012, 0.010), iterations,
                "the order of the iterations must be the order they ran in");
        assertEquals(3, call.get("n"), "the aggregate stays available alongside");
    }

    @Test
    @DisplayName("A very long loop is bounded rather than made to swell the page")
    void boundsVeryLongLoops(@TempDir Path dir) throws IOException {
        StringBuilder trace = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            trace.append("    +---[1,0% 0.010ms ] a.b.C:calculer() #12\n");
        }
        Inspection in = Inspection.read(dir.resolve("a.txt"), write(dir, "t.txt", trace.toString()), 8);
        @SuppressWarnings("unchecked")
        Map<String, Object> call = (Map<String, Object>) ((List<Object>) in.trace.get("12")).get(0);
        assertEquals(500, call.get("n"), "le compte total reste exact");
        assertTrue(((List<?>) call.get("passages")).size() <= 60,
                "but the detailed list is bounded");
    }

    @Test
    @DisplayName("Two different callees on the same line stay distinct")
    void keepsDistinctCalleesOnOneLine(@TempDir Path dir) throws IOException {
        String trace = """
                    +---[1,0% 0.01ms ] a.b.C:un() #12
                    +---[2,0% 0.02ms ] a.b.D:deux() #12
                """;
        Inspection in = Inspection.read(dir.resolve("absent.txt"), write(dir, "t.txt", trace), 8);
        assertEquals(2, ((List<?>) in.trace.get("12")).size());
    }

    @Test
    @DisplayName("Missing files give empty records, not an error")
    void missingFilesGiveEmptyResults(@TempDir Path dir) throws IOException {
        Inspection in = Inspection.read(dir.resolve("a.txt"), dir.resolve("b.txt"), 8);
        assertTrue(in.values.isEmpty());
        assertTrue(in.trace.isEmpty());
    }
}
