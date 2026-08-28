package lab.sample;

import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.TimeOfDay;
import lab.sample.model.Trip;
import lab.sample.model.Weather;

import java.util.ArrayList;
import java.util.List;

/**
 * The trips replayed identically by every tool.
 *
 * <p>The draw is <b>deterministic</b>: no {@code Random}, no clock. Two runs produce exactly
 * the same trace — otherwise comparing two reports would amount to comparing two different
 * runs, and the gap observed would no longer say anything about the tool.
 *
 * <p>{@code Mode.PLANE} and {@code Weather.SNOW} are deliberately absent from the draw.
 */
final class Scenarios {

    private Scenarios() {}

    private static final Mode[] MODES = {Mode.CAR, Mode.TRAIN, Mode.BIKE, Mode.WALK};
    private static final Weather[] WEATHERS = {Weather.SUNNY, Weather.RAIN};
    private static final TimeOfDay[] TIMES = {TimeOfDay.QUIET, TimeOfDay.RUSH_HOUR, TimeOfDay.NIGHT};
    private static final String[] PLACES = {"Toulouse", "Albi", "Montauban", "Castres", "Auch", "Foix"};

    static Trip at(int i) {
        Mode mode = MODES[i % MODES.length];
        Weather weather = WEATHERS[(i / 4) % WEATHERS.length];
        TimeOfDay time = TIMES[(i / 8) % TIMES.length];
        boolean luggage = (i % 5) == 0;

        int legCount = 1 + (i % 4);
        List<Leg> legs = new ArrayList<>(legCount);
        for (int l = 0; l < legCount; l++) {
            String from = PLACES[(i + l) % PLACES.length];
            String to = PLACES[(i + l + 1) % PLACES.length];
            double km = 2 + ((i + l * 7) % 60);
            legs.add(new Leg(from, to, km));
        }
        // Plain concatenation rather than "TRIP-%05d".formatted(i): the formatting alone
        // cost ~40 % of the CPU time and crushed the computation in the flame graph. The
        // profile must show the logic being evaluated, not the making of the test set.
        return new Trip("TRIP-" + i, mode, weather, time, luggage, List.copyOf(legs));
    }
}
