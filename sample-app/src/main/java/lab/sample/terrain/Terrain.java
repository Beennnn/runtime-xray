package lab.sample.terrain;

import lab.sample.model.Leg;
import lab.sample.model.Mode;

/**
 * Slowdown caused by the terrain, averaged over a leg's sub-segments.
 *
 * <p>Two reasons to exist:
 * <ul>
 *   <li>it is the program's <b>computational core</b> — without it, the application spends
 *       its time in the JDK's iterators and a profiler shows nothing of the business;</li>
 *   <li>it is called only for the modes sensitive to terrain: the train follows a track
 *       whose gradient is negligible, so it skips this whole sub-tree.</li>
 * </ul>
 */
public final class Terrain {

    /** Number of sub-segments analysed per leg. */
    private static final int STEPS = 24;

    private Terrain() {}

    public static boolean applies(Mode mode) {
        return mode != Mode.TRAIN;
    }

    public static double slowdownFactor(Leg leg, Mode mode) {
        double sum = 0;
        for (int step = 0; step < STEPS; step++) {
            double grade = Elevation.gradePercentAt(leg, step);
            sum += penaltyFor(grade, mode);
        }
        return sum / STEPS;
    }

    /** A climb costs more than a descent gives back, all the more so under one's own
     *  muscle power. */
    private static double penaltyFor(double gradePercent, Mode mode) {
        double sensitivity = switch (mode) {
            case BIKE -> 0.09;
            case WALK -> 0.05;
            case CAR -> 0.01;
            case TRAIN -> 0.00;
            case PLANE -> 0.00;
        };
        if (gradePercent > 0) {
            return 1.0 + gradePercent * sensitivity;
        }
        return 1.0 + gradePercent * sensitivity * 0.3; // going down only partly makes up for it
    }
}
