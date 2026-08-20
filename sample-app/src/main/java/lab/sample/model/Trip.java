package lab.sample.model;

import java.util.List;

/**
 * Le trajet dont on veut connaître la durée — <b>le paramètre dont on veut voir la
 * valeur capturée</b>.
 *
 * <p>Chaque champ envoie l'exécution dans une branche différente. C'est ce qui rend la
 * question « quelles valeurs ont été passées ? » décisive : deux appels de même
 * signature peuvent parcourir des sous-arbres complètement disjoints, et un outil qui
 * n'affiche que {@code Trip@3f2a1b} ne permet pas de savoir lequel des deux on regarde.
 */
public record Trip(
        String id,
        Mode mode,
        Weather weather,
        TimeOfDay timeOfDay,
        boolean withLuggage,   // ne pèse qu'à vélo et à pied
        List<Leg> legs) {

    public double totalDistanceKm() {
        double km = 0;
        for (Leg leg : legs) {
            km += leg.distanceKm();
        }
        return km;
    }
}
