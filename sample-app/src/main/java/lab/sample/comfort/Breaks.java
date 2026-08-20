package lab.sample.comfort;

/**
 * Pauses obligatoires sur les longs trajets.
 *
 * <p>Particularité volontaire : la décision se prend sur une <b>valeur calculée</b>
 * (la durée obtenue jusque-là) et non sur un argument reçu tel quel. Un outil qui ne
 * capture que les arguments d'entrée de la fonction analysée ne saura pas expliquer
 * pourquoi cette branche a été prise — alors qu'elle change le résultat.
 */
public final class Breaks {

    /** Au-delà de 2 h de conduite ou de marche, on s'arrête. */
    public static final double THRESHOLD_MINUTES = 120;

    private Breaks() {}

    public static int count(double travelMinutes) {
        if (travelMinutes < THRESHOLD_MINUTES) {
            return 0;
        }
        return (int) (travelMinutes / THRESHOLD_MINUTES);
    }

    public static double totalMinutes(double travelMinutes) {
        return count(travelMinutes) * 15.0;
    }
}
