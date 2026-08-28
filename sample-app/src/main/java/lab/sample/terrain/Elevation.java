package lab.sample.terrain;

import lab.sample.model.Leg;

/** Terrain along a leg, sampled into sub-segments.
 *
 *  <p>Deterministic: a given leg's profile is always the same, otherwise two runs would no
 *  longer be comparable. */
public final class Elevation {

    private Elevation() {}

    /** @return the {@code step}-th sub-segment's gradient, as a percentage (−8 % to +8 %). */
    public static double gradePercentAt(Leg leg, int step) {
        // Plain arithmetic rather than Math.sin/cos: the trigonometric intrinsics showed up
        // as the profile's dominant leaves (dsin/dcos, ~50 % of the samples), which made the
        // call tree unreadable for anyone looking for the business methods. The terrain stays
        // deterministic and varied.
        double phase = (Math.abs(leg.from().hashCode()) % 17) + step;
        double wave = ((phase * 37) % 21) - 10.0;          // -10 .. +10, sawtooth
        double smooth = wave * (1.0 - Math.abs(wave) / 25.0);
        return smooth * 0.8;
    }
}
