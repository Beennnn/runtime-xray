package lab.sample.traffic;

import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.TimeOfDay;

import java.util.List;

/**
 * Delay caused by traffic.
 *
 * <p>An entirely <b>conditional</b> sub-tree: it exists only for the car, and only at rush
 * hour. On a train trip at 10 pm, none of these lines runs — and that is exactly what this
 * repository is trying to make visible.
 */
public final class Traffic {

    private Traffic() {}

    public static boolean applies(Mode mode, TimeOfDay timeOfDay) {
        return mode == Mode.CAR && timeOfDay == TimeOfDay.RUSH_HOUR;
    }

    public static double delayMinutes(List<Leg> legs) {
        double total = 0;
        for (Leg leg : legs) {
            total += RushHourDelay.minutesFor(leg);
        }
        return total;
    }
}
