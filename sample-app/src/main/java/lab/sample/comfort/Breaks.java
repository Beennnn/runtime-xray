package lab.sample.comfort;

import org.apache.commons.lang3.Range;

/**
 * Pauses obligatoires sur les longs trajets.
 *
 * <p>Cette classe est aussi la seule à appeler une <b>bibliothèque externe</b>
 * ({@code commons-lang3}). C'est délibéré : le code d'une dépendance n'arrive pas sous
 * forme de répertoire de classes mais de jar, et l'outil doit savoir l'analyser dans cette
 * forme-là — sans quoi tout un pan du code réellement exécuté reste invisible.
 *
 * <p>Particularité volontaire : la décision se prend sur une <b>valeur calculée</b>
 * (la durée obtenue jusque-là) et non sur un argument reçu tel quel. Un outil qui ne
 * capture que les arguments d'entrée de la fonction analysée ne saura pas expliquer
 * pourquoi cette branche a été prise — alors qu'elle change le résultat.
 */
public final class Breaks {

    /** Au-delà de 2 h de conduite ou de marche, on s'arrête. */
    public static final double THRESHOLD_MINUTES = 120;

    /** Au-delà, la pause n'est plus une pause : c'est une étape, et elle se planifie. */
    private static final Range<Integer> REASONABLE = Range.of(0, 6);

    private Breaks() {}

    public static int count(double travelMinutes) {
        if (travelMinutes < THRESHOLD_MINUTES) {
            return 0;
        }
        return REASONABLE.fit((int) (travelMinutes / THRESHOLD_MINUTES));
    }

    public static double totalMinutes(double travelMinutes) {
        return count(travelMinutes) * 15.0;
    }
}
