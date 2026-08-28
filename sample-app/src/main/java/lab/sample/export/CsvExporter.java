package lab.sample.export;

import lab.sample.model.Trip;

/**
 * Exporting trips to CSV.
 *
 * <p><b>Deliberately never called</b>, like the rest of this package — see
 * {@link ItineraryExporter}. Two classes rather than one: a whole package in red gets
 * noticed, where a lone class gets lost in a list.
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
