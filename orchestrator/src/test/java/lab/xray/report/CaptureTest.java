package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An acceptance campaign costs a morning. This number exists so that updating the tool does
 * not make it be replayed: that is the decision these tests guard, and it is lost at the
 * first "never mind, we will measure again".
 */
class CaptureTest {

    @Test
    @DisplayName("A capture that announces nothing stays readable")
    void aCaptureWithoutAVersionIsStillReadable() {
        // Yesterday's silence must not cost a campaign: runs made before this contract
        // existed fall under the original shape, and read back.
        assertEquals(Capture.ORIGIN, Capture.versionOf(null));
        assertEquals(Capture.ORIGIN, Capture.versionOf(new HashMap<>()));
        assertEquals(Capture.ORIGIN, Capture.versionOf(Map.of(Capture.FIELD, "  ")));
        assertTrue(Capture.readable(Capture.versionOf(null)));
    }

    @Test
    @DisplayName("A capture newer than the tool is still read, and says so")
    void aCaptureNewerThanTheToolIsStillRead() {
        // Refusing to read would lose a campaign over fields we ignore.
        assertTrue(Capture.newerThanTheTool("9.0"));
        assertTrue(Capture.readable("9.0"));
        assertFalse(Capture.newerThanTheTool(Capture.CURRENT));
    }

    @Test
    @DisplayName("Versions compare as numbers, not as text")
    void versionsCompareNumerically() {
        // "1.10" comes after "1.9"; compared as text it would come before, and the tool
        // would demand a new measurement for no reason.
        assertTrue(Capture.compare("1.10", "1.9") > 0);
        assertTrue(Capture.compare("2.0", "1.99") > 0);
        assertEquals(0, Capture.compare("1.1", "1.1"));
        assertEquals(0, Capture.compare("1", "1.0"), "a missing segment counts as zero");
    }

    @Test
    @DisplayName("The minimum version never exceeds the current one")
    void theMinimumNeverExceedsWhatWeWrite() {
        // Otherwise the tool would refuse the captures it has just produced itself.
        assertTrue(Capture.compare(Capture.MINIMUM, Capture.CURRENT) <= 0);
        assertTrue(Capture.readable(Capture.CURRENT));
        assertTrue(Capture.readable(Capture.ORIGIN),
                "raising the minimum forces everyone to measure again: this test makes that visible");
    }
}
