package lab.xray;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code --attach-after} names an instant in the observed application's life, not a pause
 * added to whatever the tool did before.
 *
 * <p>The difference is invisible where the preparation costs nothing and decides the run
 * where it does not. Reading the arguments of the observed JVM costs one turn on a system
 * that publishes them and the full five seconds on a system that publishes none: the same
 * command, with the same {@code --attach-after 2}, then attached at 2.01 s on one and at
 * 7.15 s on the other — past the end of an application that lived six seconds, which
 * announced "values not captured" on a run that had gone perfectly well, and sent the
 * operator to raise a workload that was never the problem.
 */
class AttachTimingTest {

    @Test
    @DisplayName("The wait is counted from the launch, so what came before is deducted")
    void countedFromTheLaunch() {
        assertEquals(2000, RunSession.remainingBeforeAttach(2, 0),
                "nothing done yet: the whole delay is still to wait");
        assertEquals(1800, RunSession.remainingBeforeAttach(2, 200),
                "a preparation of 200 ms leaves 1.8 s, not 2 s");
        assertEquals(0, RunSession.remainingBeforeAttach(2, 5150),
                "a preparation of 5.15 s has already gone past the instant asked for");
    }

    @Test
    @DisplayName("An instant already gone gives no wait, never a negative one")
    void neverNegative() {
        assertEquals(0, RunSession.remainingBeforeAttach(2, 60000),
                "being late is not a reason to be later still");
    }
}
