package lab.sample.weather;

import lab.sample.model.Mode;
import lab.sample.model.Weather;

/**
 * Ralentissement dû à la météo.
 *
 * <p>Ne concerne que les modes exposés — vélo et marche. La branche {@code SNOW} n'est
 * jamais empruntée : c'est un trou de couverture <em>à l'intérieur</em> d'une méthode
 * par ailleurs exécutée des milliers de fois. Un rapport qui n'affiche qu'un
 * pourcentage par classe le masque ; un rapport qui colorie les lignes le montre.
 */
public final class WeatherPenalty {

    private WeatherPenalty() {}

    public static boolean applies(Mode mode) {
        return mode == Mode.BIKE || mode == Mode.WALK;
    }

    public static double slowdownFactor(Weather weather) {
        return switch (weather) {
            case SUNNY -> 1.00;
            case RAIN -> 1.15;
            case SNOW -> 1.60;
        };
    }
}
