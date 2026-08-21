package lab.sample.terrain;

import lab.sample.model.Leg;
import lab.sample.model.Mode;

/**
 * Ralentissement dû au relief, moyenné sur les sous-segments d'une étape.
 *
 * <p>Deux raisons d'exister :
 * <ul>
 *   <li>c'est le <b>cœur de calcul</b> du programme — sans lui, l'application passe son
 *       temps dans les itérateurs du JDK et un profiler ne montre rien du métier ;</li>
 *   <li>il n'est appelé que pour les modes sensibles au terrain : le train suit une voie
 *       ferrée dont la pente est négligeable, il saute donc tout ce sous-arbre.</li>
 * </ul>
 */
public final class Terrain {

    /** Nombre de sous-segments analysés par étape. */
    private static final int STEPS = 24;

    private Terrain() {}

    public static boolean applies(Mode mode) {
        return mode != Mode.TRAIN;
    }

    public static double slowdownFactor(Leg leg, Mode mode) {
        double sum = 0;
        for (int step = 0; step < STEPS; step++) {
            double grade = Elevation.gradePercentAt(leg, step);
            sum += penaltyFor(grade, mode);
        }
        return sum / STEPS;
    }

    /** Une montée coûte plus qu'une descente ne rapporte, et d'autant plus qu'on est
     *  à la force du mollet. */
    private static double penaltyFor(double gradePercent, Mode mode) {
        double sensitivity = switch (mode) {
            case BIKE -> 0.09;
            case WALK -> 0.05;
            case CAR -> 0.01;
            case TRAIN -> 0.00;
            case PLANE -> 0.00;
        };
        if (gradePercent > 0) {
            return 1.0 + gradePercent * sensitivity;
        }
        return 1.0 + gradePercent * sensitivity * 0.3; // la descente ne compense qu'en partie
    }
}
