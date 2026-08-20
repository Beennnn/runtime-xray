package lab.sample;

import lab.sample.comfort.Breaks;
import lab.sample.model.Leg;
import lab.sample.model.Mode;
import lab.sample.model.Trip;
import lab.sample.speed.SpeedModel;
import lab.sample.speed.Speeds;
import lab.sample.terrain.Terrain;
import lab.sample.traffic.Traffic;
import lab.sample.transfer.Connections;
import lab.sample.weather.LuggagePenalty;
import lab.sample.weather.WeatherPenalty;

/**
 * <b>La fonction sous analyse.</b> « Combien de temps pour aller de A à B ? »
 *
 * <p>C'est le point d'entrée que chaque outil doit savoir cibler : à l'exécution de
 * cette fonction, donne-moi les lignes exécutées, l'arbre d'appel et les valeurs des
 * paramètres.
 *
 * <p>Elle appelle quatre familles de calcul <b>selon le contexte</b>, jamais toutes en
 * même temps :
 * <ul>
 *   <li>la vitesse du mode choisi — toujours, mais via une implémentation différente ;</li>
 *   <li>les bouchons — voiture à l'heure de pointe uniquement ;</li>
 *   <li>la météo et les bagages — vélo et marche uniquement ;</li>
 *   <li>les correspondances — train uniquement ;</li>
 *   <li>les pauses — au-delà d'une durée <em>calculée</em>, quel que soit le mode.</li>
 * </ul>
 * Deux trajets différents produisent donc deux arbres d'appel différents, à partir du
 * même appel de la même méthode. C'est précisément ce qu'on demande aux outils de montrer.
 */
public final class RoutePlanner {

    private RoutePlanner() {}

    /**
     * @param trip le trajet à évaluer
     * @return la durée totale estimée, en minutes
     */
    public static double travelTimeMinutes(Trip trip) {
        SpeedModel speed = Speeds.forMode(trip.mode());

        double minutes = 0;
        // Boucle indexée volontairement : la boucle for-each sur une List immuable
        // allouait un itérateur par étape, qui représentait à lui seul la moitié des
        // échantillons. Constaté au profiler, corrigé, re-mesuré.
        for (int i = 0; i < trip.legs().size(); i++) {
            minutes += legMinutes(trip.legs().get(i), speed, trip.mode());
        }

        if (Traffic.applies(trip.mode(), trip.timeOfDay())) {
            minutes += Traffic.delayMinutes(trip.legs());
        }

        if (WeatherPenalty.applies(trip.mode())) {
            minutes *= WeatherPenalty.slowdownFactor(trip.weather());
            minutes *= LuggagePenalty.slowdownFactor(trip.withLuggage());
        }

        if (trip.mode() == Mode.TRAIN) {
            minutes += Connections.waitMinutes(trip.legs(), 0);
        }

        // Décidé sur la durée obtenue, pas sur un argument reçu : voir Breaks.
        minutes += Breaks.totalMinutes(minutes);

        return minutes;
    }

    private static double legMinutes(Leg leg, SpeedModel speed, Mode mode) {
        double minutes = leg.distanceKm() / speed.kmPerHour(leg) * 60.0;
        if (Terrain.applies(mode)) {
            minutes *= Terrain.slowdownFactor(leg, mode);
        }
        return minutes;
    }
}
