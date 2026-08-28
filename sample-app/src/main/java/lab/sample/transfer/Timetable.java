package lab.sample.transfer;

import lab.sample.model.Leg;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Timetable lookup — the program's only <b>blocking</b> operation.
 *
 * <p>It simulates a call to an external service with a {@code Thread.sleep}. Its presence is
 * not decorative: it creates <em>waiting time</em> alongside computation time, and that is an
 * excellent discriminator between tools. A profiler that samples the CPU will hardly see it;
 * a wall-clock profiler will see it dominate. Two tools can therefore give two opposite
 * answers to "where does the time go?" on exactly the same run — and one has to know which
 * one is being looked at.
 *
 * <p>The result is cached: a timetable is asked for only once per link.
 */
public final class Timetable {

    private static final Map<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static final int LOOKUP_MILLIS = 3;

    private Timetable() {}

    /** @return the service frequency, in minutes, for this link. */
    public static int frequencyMinutes(Leg leg) {
        return CACHE.computeIfAbsent(leg.from() + ">" + leg.to(), key -> {
            try {
                Thread.sleep(LOOKUP_MILLIS); // simulated network call
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 10 + (Math.abs(key.hashCode()) % 50);
        });
    }
}
