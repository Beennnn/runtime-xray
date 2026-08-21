package lab.sample.traffic;

import lab.sample.model.Leg;

/** Retard dû aux bouchons de l'heure de pointe. */
public final class RushHourDelay {

    private RushHourDelay() {}

    static double minutesFor(Leg leg) {
        // Les bouchons pénalisent surtout les entrées et sorties d'agglomération,
        // donc les étapes courtes : d'où un retard qui ne suit pas la distance.
        if (leg.distanceKm() < 5) {
            return 8;
        }
        return leg.distanceKm() < 40 ? 15 : 20;
    }
}
