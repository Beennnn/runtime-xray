package lab.sample;

import lab.sample.model.Trip;

import java.util.ArrayList;
import java.util.List;

/**
 * Launcher for the target application.
 *
 * <p>Two options exist only for the needs of the analysis:
 * <ul>
 *   <li>{@code --iterations} — a sampling profiler has nothing to show on a 5 ms run; CPU
 *       time is needed for frames to be collected.</li>
 *   <li>{@code --hold-seconds} — tools that <em>attach</em> to a live process (VisualVM, JMC,
 *       JProfiler, YourKit) need the JVM to still be there when the operator clicks. Without
 *       this pause, the target is dead before it has been selected from the list.</li>
 * </ul>
 */
public final class Main {

    /** Number of distinct trips built before the measurement. Enough to cover every
     *  mode × weather × time × luggage combination, few enough that building them weighs
     *  nothing against the computation loop. */
    private static final int TRIP_VARIANTS = 240;

    /** Calibrated so the computation lasts ~10 s on an Apple Silicon Mac. That is the
     *  comfortable minimum for a tool attaching to a live process (VisualVM, JMC, JProfiler)
     *  to have time to be launched, to select the JVM and to record something. A one-second
     *  run offers no grip at all. */
    private static final int DEFAULT_ITERATIONS = 16_000_000;

    public static void main(String[] args) throws InterruptedException {
        int iterations = intArg(args, "--iterations", DEFAULT_ITERATIONS);
        int holdSeconds = intArg(args, "--hold-seconds", 0);

        System.out.printf("sample-app: %d trips evaluated, final pause %d s%n", iterations, holdSeconds);
        Metrics metrics = new Metrics();
        metrics.stage("JVM started");

        // The trips are built ONCE, outside the measured loop. Without that separation,
        // generating the test set (string concatenations, list allocations) captures ~70 % of
        // the samples and the call tree shows java.lang.StringConcatHelper instead of the
        // route computation. Measured, not assumed.
        List<Trip> trips = new ArrayList<>(TRIP_VARIANTS);
        for (int i = 0; i < TRIP_VARIANTS; i++) {
            trips.add(Scenarios.at(i));
        }

        metrics.stage("trip set built");

        double total = 0;
        for (int i = 0; i < iterations; i++) {
            total += RoutePlanner.travelTimeMinutes(trips.get(i % TRIP_VARIANTS));
        }
        // Printed so the computation cannot be eliminated, and to check at a glance that two
        // runs being compared really did the same work.
        metrics.stage("computation finished");
        System.out.printf("total duration = %.0f minutes%n", total);

        if (holdSeconds > 0) {
            System.out.printf("JVM kept alive %d s — attach your profiler (pid %d)%n",
                    holdSeconds, ProcessHandle.current().pid());
            Thread.sleep(holdSeconds * 1000L);
        }
    }

    private static int intArg(String[] args, String name, int fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return fallback;
    }
}
