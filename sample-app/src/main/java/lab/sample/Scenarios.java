package lab.sample;

import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.TimeOfDay;
import lab.sample.model.Trip;
import lab.sample.model.Weather;

import java.util.ArrayList;
import java.util.List;

/**
 * Les trajets rejoués à l'identique par tous les outils.
 *
 * <p>Le tirage est <b>déterministe</b> : ni {@code Random}, ni horloge. Deux exécutions
 * produisent exactement la même trace — sans quoi comparer deux rapports reviendrait à
 * comparer deux exécutions différentes, et l'écart observé ne dirait plus rien de l'outil.
 *
 * <p>{@code Mode.PLANE} et {@code Weather.SNOW} sont volontairement absents du tirage.
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
        // Concaténation simple et non "TRIP-%05d".formatted(i) : le formatage coûtait à lui
        // seul ~40 % du temps CPU et écrasait le calcul dans le flame graph. Le profil doit
        // montrer la logique évaluée, pas la fabrication du jeu de test.
        return new Trip("TRIP-" + i, mode, weather, time, luggage, List.copyOf(legs));
    }
}
