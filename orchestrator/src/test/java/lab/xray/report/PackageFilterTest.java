package lab.xray.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hiding a package removes code from the view. A rule that is too broad takes business code
 * with it without saying so — that is the failure mode to cover first, because it is
 * silent: one does not notice the absence of what one was not looking for.
 */
class PackageFilterTest {

    @Test
    @DisplayName("Without an instruction, nothing is hidden")
    void emptyByDefault() {
        assertTrue(PackageFilter.of("").isEmpty());
        assertTrue(PackageFilter.of(null).isEmpty());
        assertFalse(PackageFilter.of("   ").hidden("org/slf4j/Logger"));
    }

    @Test
    @DisplayName("Both spellings of a package are accepted, with or without a star")
    void acceptsBothNotations() {
        for (String spec : new String[]{"org.slf4j", "org/slf4j", "org.slf4j.*", "org/slf4j/*"}) {
            assertTrue(PackageFilter.of(spec).hidden("org/slf4j/Logger.debug"),
                    "spelling refused: " + spec);
        }
    }

    @Test
    @DisplayName("A sub-package is hidden along with its parent")
    void hidesSubPackages() {
        PackageFilter f = PackageFilter.of("org.slf4j");
        assertTrue(f.hidden("org/slf4j/helpers/Util.report"));
    }

    @Test
    @DisplayName("A package with a neighbouring name is NOT taken with it")
    void doesNotCatchNeighbours() {
        // This is the silent mistake: "org.slf4j" must not hide "org.slf4jext".
        PackageFilter f = PackageFilter.of("org.slf4j");
        assertFalse(f.hidden("org/slf4jext/Bridge.log"));
        assertFalse(f.hidden("org/slf4j"), "the package itself, without a class, is not a member");
    }

    @Test
    @DisplayName("Several packages, separated by comma, semicolon or space")
    void readsSeveralEntries() {
        PackageFilter f = PackageFilter.of("org.slf4j, io.netty; ch.qos.logback");
        assertTrue(f.hidden("org/slf4j/Logger"));
        assertTrue(f.hidden("io/netty/channel/Pipeline"));
        assertTrue(f.hidden("ch/qos/logback/core/Appender"));
        assertFalse(f.hidden("lab/sample/RoutePlanner.travelTimeMinutes"));
    }

    @Test
    @DisplayName("Business code is never hidden by a rule that does not name it")
    void neverHidesUnrelatedCode() {
        PackageFilter f = PackageFilter.of("org.slf4j, org.apache.commons");
        assertFalse(f.hidden("lab/sample/comfort/Breaks.count"));
        assertFalse(f.hidden("com/example/engine/Compute.run"));
    }

    @Test
    @DisplayName("The original list is kept as it is, to be shown to the reader")
    void keepsOriginalSpec() {
        assertTrue(PackageFilter.of("org.slf4j, io.netty").spec().contains("io.netty"));
    }
}
