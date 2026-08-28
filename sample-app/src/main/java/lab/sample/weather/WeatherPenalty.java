package lab.sample.weather;

import lab.sample.model.Mode;
import lab.sample.model.Weather;

/**
 * Slowdown caused by the weather.
 *
 * <p>Concerns only the exposed modes — bike and walking. The {@code SNOW} branch is never
 * taken: it is a coverage hole <em>inside</em> a method otherwise executed thousands of
 * times. A report showing only a percentage per class hides it; a report that colours the
 * lines shows it.
 */
public final class WeatherPenalty {

    private WeatherPenalty() {}

    public static boolean applies(Mode mode) {
        return mode == Mode.BIKE || mode == Mode.WALK;
    }

    public static double slowdownFactor(Weather weather) {
        return switch (weather) {
            case SUNNY -> 1.00;
            case RAIN -> 1.15;
            case SNOW -> 1.60;
        };
    }
}
