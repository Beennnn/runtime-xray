package lab.sample.model;

import java.util.List;

/**
 * The trip whose duration one wants to know — <b>the parameter whose captured value one
 * wants to see</b>.
 *
 * <p>Each field sends the execution down a different branch. That is what makes the question
 * "which values were passed?" decisive: two calls with the same signature may walk completely
 * disjoint sub-trees, and a tool that only shows {@code Trip@3f2a1b} gives no way of knowing
 * which of the two one is looking at.
 */
public record Trip(
        String id,
        Mode mode,
        Weather weather,
        TimeOfDay timeOfDay,
        boolean withLuggage,   // only weighs by bike and on foot
        List<Leg> legs) {

    public double totalDistanceKm() {
        double km = 0;
        for (Leg leg : legs) {
            km += leg.distanceKm();
        }
        return km;
    }
}
