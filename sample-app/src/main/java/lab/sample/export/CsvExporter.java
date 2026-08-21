package lab.sample.export;

import lab.sample.model.Trip;

/**
 * Export des trajets au format CSV.
 *
 * <p><b>Volontairement jamais appelée</b>, comme le reste de ce paquet — voir
 * {@link ItineraryExporter}. Deux classes plutôt qu'une : un paquet entier en rouge se
 * remarque, là où une classe isolée se perd dans une liste.
 */
public final class CsvExporter {

    private static final char SEPARATOR = ';';

    private CsvExporter() {}

    public static String header() {
        return String.join(String.valueOf(SEPARATOR),
                "id", "mode", "meteo", "moment", "bagages", "distance_km", "duree_min");
    }

    public static String toRow(Trip trip, double minutes) {
        return trip.id() + SEPARATOR
                + trip.mode() + SEPARATOR
                + trip.weather() + SEPARATOR
                + trip.timeOfDay() + SEPARATOR
                + (trip.withLuggage() ? "oui" : "non") + SEPARATOR
                + Math.round(trip.totalDistanceKm()) + SEPARATOR
                + Math.round(minutes);
    }
}
