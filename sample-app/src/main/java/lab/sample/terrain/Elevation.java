package lab.sample.terrain;

import lab.sample.model.Leg;

/** Relief le long d'une étape, échantillonné en sous-segments.
 *
 *  <p>Déterministe : le profil d'une étape donnée est toujours le même, sinon deux
 *  exécutions ne seraient plus comparables. */
public final class Elevation {

    private Elevation() {}

    /** @return la pente du {@code step}-ième sous-segment, en pourcentage (−8 % à +8 %). */
    public static double gradePercentAt(Leg leg, int step) {
        // Arithmétique simple plutôt que Math.sin/cos : les intrinsèques trigonométriques
        // apparaissaient comme feuilles dominantes du profil (dsin/dcos, ~50 % des
        // échantillons), ce qui rendait l'arbre d'appel illisible pour qui cherche les
        // méthodes métier. Le relief reste déterministe et varié.
        double phase = (Math.abs(leg.from().hashCode()) % 17) + step;
        double wave = ((phase * 37) % 21) - 10.0;          // -10 .. +10, en dents de scie
        double smooth = wave * (1.0 - Math.abs(wave) / 25.0);
        return smooth * 0.8;
    }
}
