package lab.sample.export;

import lab.sample.model.Leg;
import lab.sample.model.Trip;

/**
 * Mise en forme d'un trajet pour impression.
 *
 * <p><b>Volontairement jamais appelée.</b> Ce paquet entier existe pour montrer ce que
 * l'analyse dynamique révèle et qu'aucune lecture du code ne donne : du code qui compile,
 * qui a l'air utile, qui est peut-être maintenu depuis des années — et que l'exécution
 * n'atteint jamais.
 *
 * <p>C'est le cas le plus intéressant pour une reprise de code : un rapport de couverture
 * fait apparaître un paquet complet en rouge, et la question « qui appelle ça ? » devient
 * une question qu'on se pose enfin.
 */
public final class ItineraryExporter {

    private ItineraryExporter() {}

    public static String toPrintable(Trip trip, double minutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trajet ").append(trip.id()).append(" — ").append(trip.mode()).append('\n');
        for (Leg leg : trip.legs()) {
            sb.append("  ").append(leg.from()).append(" → ").append(leg.to())
              .append(" (").append(leg.distanceKm()).append(" km)").append('\n');
        }
        sb.append("Durée estimée : ").append(Math.round(minutes)).append(" minutes");
        if (trip.withLuggage()) {
            sb.append(" (avec bagages)");
        }
        return sb.toString();
    }

    public static String summary(Trip trip) {
        return trip.legs().size() + " étape(s), " + Math.round(trip.totalDistanceKm()) + " km";
    }
}
