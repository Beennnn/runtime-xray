package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The activity band is a comfort, and a comfort must cost nothing to what it accompanies:
 * neither the measurement, nor a log's readability, nor the capture.
 */
class ProgressTest {

    private static final Duration NOTHING = Duration.ZERO;

    @Test
    @DisplayName("Without a terminal, nothing is written at all")
    void silentWithoutTerminal() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, false, false);
        assertFalse(p.active());
        for (int i = 1; i <= 20; i++) {
            p.tick(Duration.ofSeconds(i), Duration.ofSeconds(i), 1024L * i);
        }
        p.end();
        assertEquals("", out.toString(),
                "a line that rewrites itself every second would drown an integration log");
    }

    @Test
    @DisplayName("The band scrolls and never exceeds its width")
    void bandScrolls() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        for (int i = 1; i <= Progress.WIDTH + 15; i++) {
            p.tick(Duration.ofSeconds(i), Duration.ofSeconds(i), 0);
        }
        String last = out.toString().substring(out.lastIndexOf("\r"));
        assertEquals(Progress.WIDTH, countSquares(last),
                "beyond that the line would wrap and stop rewriting itself");
    }

    @Test
    @DisplayName("A saturated core shows, even on a machine that has thirty-two")
    void densityCountsCoresNotMachineShare() {
        assertEquals(0, Progress.tier(0.0));
        assertTrue(Progress.tier(1.0) >= 3,
                "scaled to the size of the machine, a full core would pass for rest");
        assertEquals(4, Progress.tier(4.0));
        assertTrue(Progress.tier(0.3) > Progress.tier(0.05),
                "the tiers must increase, otherwise the band means nothing");
    }

    @Test
    @DisplayName("Density carries the information, colour merely doubles it")
    void colourIsNeverTheOnlyCarrier() {
        StringBuilder withoutColour = new StringBuilder();
        StringBuilder withColour = new StringBuilder();
        Progress plain = new Progress(withoutColour, true, false);
        Progress tint = new Progress(withColour, true, true);
        for (int i = 1; i <= 6; i++) {
            plain.tick(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
            tint.tick(Duration.ofSeconds(i), Duration.ofMillis(i * 400L), 0);
        }
        assertFalse(withoutColour.toString().contains("\u001b"),
                "NO_COLOR, un tuyau, un terminal ancien : la ligne doit rester lisible");
        assertEquals(countSquares(withoutColour.toString()), countSquares(withColour.toString()),
                "the same loads give the same squares, coloured or not");
    }

    @Test
    @DisplayName("A shorter line entirely erases the one it replaces")
    void erasesTheLongerLineBefore() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        p.tick(Duration.ofSeconds(1), Duration.ofMillis(1_500), 5L * 1024 * 1024);
        int longLine = out.length();
        out.setLength(0);
        p.tick(Duration.ofSeconds(2), Duration.ofMillis(1_500), 10);
        assertTrue(out.length() >= longLine,
                "without padding, \"5.0 MB\" would leave its tail behind \"10 B\"");
    }

    @Test
    @DisplayName("Without published processor time, the application's output stands witness")
    void fallsBackToOutputGrowth() {
        StringBuilder out = new StringBuilder();
        Progress p = new Progress(out, true, false);
        p.tick(Duration.ofSeconds(1), NOTHING, 100);
        p.tick(Duration.ofSeconds(2), NOTHING, 100);
        p.tick(Duration.ofSeconds(3), NOTHING, 4096);
        String last = out.toString().substring(out.lastIndexOf("\r"));
        assertTrue(last.contains("running"),
                "announcing \"cpu 0.0 s\" would suggest a frozen program");
        assertTrue(last.contains("4.0 KB"));
    }

    @Test
    @DisplayName("The first processor reading fixes the origin, it does not become a rate")
    void theFirstCpuReadingIsNotARate() {
        // What the observed JVM has already burnt when the band opens — start-up, class
        // loading, the JIT compiler's threads, several cores at once — is not the load of
        // the first interval. Divided by it, a real run announced 26.71 busy cores on a
        // four-core machine, and that impossible figure set the scale of the --follow curve.
        Progress p = new Progress(new StringBuilder(), true, false);
        double first = p.tick(Duration.ofSeconds(1), Duration.ofSeconds(28), 100);
        assertTrue(first <= 1.0,
                "28 s of processor time consumed before the band existed is not 28 busy cores");

        double steady = p.tick(Duration.ofSeconds(2), Duration.ofSeconds(29), 100);
        assertEquals(1.0, steady, 0.01,
                "from the second reading on, the rate is the one really observed");
    }

    @Test
    @DisplayName("The origin is fixed by the first reading that carries a figure")
    void theOriginWaitsForTheFirstFigure() {
        // A platform may publish nothing for a while and then start. The reading that
        // arrives is still a first one: it carries everything since launch, not one interval.
        Progress p = new Progress(new StringBuilder(), true, false);
        p.tick(Duration.ofSeconds(1), NOTHING, 100);
        double appears = p.tick(Duration.ofSeconds(2), Duration.ofSeconds(30), 100);
        assertTrue(appears <= 1.0, "a late first reading is an origin like any other");
        assertEquals(2.0, p.tick(Duration.ofSeconds(3), Duration.ofSeconds(32), 100), 0.01);
    }

    @Test
    @DisplayName("The band's characters exist in CP850 as well")
    void glyphsSurviveAWindowsTerminal() {
        // A Windows machine in cp850 is the default, not the exception: see CLAUDE.md.
        for (char c : Progress.TIERS) {
            assertTrue(java.nio.charset.Charset.forName("IBM850").newEncoder().canEncode(c),
                    "the square \"" + c + "\" would become a question mark");
        }
    }

    private static int countSquares(String line) {
        int n = 0;
        for (char c : line.toCharArray()) {
            for (char tier : Progress.TIERS) {
                if (c == tier) n++;
            }
        }
        return n;
    }

    @Test
    @DisplayName("A nested shell can ask for the band, and can also silence it")
    void anestedShellCanAskForTheBandAndAlsoSilenceIt() {
        // A shell that launches another often puts a pipe in the middle: the JVM no longer
        // sees a terminal while an operator is indeed watching a screen at the far end. The
        // default stays silence; whoever knows they are watching must be able to say so.
        assertFalse(Progress.requested(null), "with nothing, the default does not change");
        assertFalse(Progress.requested(""));
        assertFalse(Progress.requested("0"), "0 imposes silence, it does not lift it");
        assertFalse(Progress.requested("false"));
        assertTrue(Progress.requested("1"));
        assertTrue(Progress.requested("oui"),
                "toute autre valeur vaut demande : on ne fait pas deviner une syntaxe");
    }
}
