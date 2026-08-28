package lab.sample;

import lab.sample.comfort.Breaks;
import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.Trip;
import lab.sample.speed.SpeedModel;
import lab.sample.speed.Speeds;
import lab.sample.terrain.Terrain;
import lab.sample.traffic.Traffic;
import lab.sample.transfer.Connections;
import lab.sample.weather.LuggagePenalty;
import lab.sample.weather.WeatherPenalty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The function under analysis.</b> "How long does it take to get from A to B?"
 *
 * <p>This is the entry point every tool must be able to target: while this function runs,
 * give me the lines executed, the call tree and the parameter values.
 *
 * <p>It calls four families of computation <b>depending on the context</b>, never all at
 * once:
 * <ul>
 *   <li>the chosen mode's speed — always, but through a different implementation;</li>
 *   <li>traffic jams — car at rush hour only;</li>
 *   <li>weather and luggage — bike and walking only;</li>
 *   <li>connections — train only;</li>
 *   <li>breaks — beyond a <em>computed</em> duration, whatever the mode.</li>
 * </ul>
 * Two different trips therefore produce two different call trees, from the same call to the
 * same method. That is precisely what tools are asked to show.
 *
 * <p>The logging is there for a reason that is not functional: {@code org.slf4j} is not in
 * the JDK, so nothing folds it away automatically, and it shows up in the call tree in the
 * middle of the route computation. It is the archetypal library one wants to hide by
 * configuration — see {@code HIDDEN_PACKAGES}.
 */
public final class RoutePlanner {

    private static final Logger log = LoggerFactory.getLogger(RoutePlanner.class);

    private RoutePlanner() {}

    /**
     * @param trip the trip to evaluate
     * @return the total estimated duration, in minutes
     */
    public static double travelTimeMinutes(Trip trip) {
        // A call deliberately left in the hot loop: that is what makes slf4j visible in the
        // profile, hence hideable, hence demonstrable.
        log.debug("evaluating trip {}", trip.id());
        SpeedModel speed = Speeds.forMode(trip.mode());

        double minutes = 0;
        // An indexed loop on purpose: the for-each loop over an immutable List allocated one
        // iterator per leg, which on its own accounted for half the samples. Seen in the
        // profiler, fixed, re-measured.
        for (int i = 0; i < trip.legs().size(); i++) {
            minutes += legMinutes(trip.legs().get(i), speed, trip.mode());
        }

        if (Traffic.applies(trip.mode(), trip.timeOfDay())) {
            minutes += Traffic.delayMinutes(trip.legs());
        }

        if (WeatherPenalty.applies(trip.mode())) {
            minutes *= WeatherPenalty.slowdownFactor(trip.weather());
            minutes *= LuggagePenalty.slowdownFactor(trip.withLuggage());
        }

        if (trip.mode() == Mode.TRAIN) {
            minutes += Connections.waitMinutes(trip.legs(), 0);
        }

        // Decided on the duration obtained, not on an argument received: see Breaks.
        minutes += Breaks.totalMinutes(minutes);

        return minutes;
    }

    private static double legMinutes(Leg leg, SpeedModel speed, Mode mode) {
        double minutes = leg.distanceKm() / speed.kmPerHour(leg) * 60.0;
        if (Terrain.applies(mode)) {
            minutes *= Terrain.slowdownFactor(leg, mode);
        }
        return minutes;
    }
}
