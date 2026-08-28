package lab.sample.traffic;

import lab.sample.model.Leg;

/** Delay caused by rush-hour traffic jams. */
public final class RushHourDelay {

    private RushHourDelay() {}

    static double minutesFor(Leg leg) {
        // Traffic jams mostly penalise entering and leaving built-up areas, hence short
        // legs: which is why the delay does not follow the distance.
        if (leg.distanceKm() < 5) {
            return 8;
        }
        return leg.distanceKm() < 40 ? 15 : 20;
    }
}
