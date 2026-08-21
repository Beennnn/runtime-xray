package lab.sample.traffic;

import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.TimeOfDay;

import java.util.List;

/**
 * Retard lié à la circulation.
 *
 * <p>Sous-arbre entièrement <b>conditionnel</b> : il n'existe que pour la voiture, et
 * seulement à l'heure de pointe. Sur un trajet en train à 22 h, aucune de ces lignes
 * ne s'exécute — et c'est exactement ce que le dépôt cherche à rendre visible.
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
