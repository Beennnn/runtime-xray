package lab.sample.transfer;

import lab.sample.model.Leg;

import java.util.List;

/**
 * Temps d'attente aux correspondances — propre au train.
 *
 * <p>Le calcul est <b>récursif</b> sur la liste des étapes : il ajoute un foyer de
 * récursion distinct de celui de {@code comfort.Breaks}, dans une branche que seuls
 * certains scénarios empruntent. Utile pour vérifier qu'un outil ne confond pas deux
 * récursions différentes dans le même arbre.
 */
public final class Connections {

    private Connections() {}

    public static double waitMinutes(List<Leg> legs, int index) {
        if (index >= legs.size() - 1) {
            return 0; // dernière étape : plus de correspondance après
        }
        double here = transferMinutes(legs.get(index), legs.get(index + 1));
        return here + waitMinutes(legs, index + 1);
    }

    private static double transferMinutes(Leg arriving, Leg departing) {
        // Une correspondance dans la même gare est plus rapide qu'un changement de gare,
        // et l'attente dépend de la fréquence de passage — donc d'une consultation
        // d'horaires, seule opération bloquante du programme.
        double walk = arriving.to().equals(departing.from()) ? 6 : 18;
        return walk + Timetable.frequencyMinutes(departing) / 2.0;
    }
}
