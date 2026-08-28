package lab.sample.export;

import lab.sample.model.Leg;
import lab.sample.model.Trip;

/**
 * Formatting a trip for printing.
 *
 * <p><b>Deliberately never called.</b> This whole package exists to show what dynamic
 * analysis reveals and no reading of the code gives: code that compiles, that looks useful,
 * that has perhaps been maintained for years — and that execution never reaches.
 *
 * <p>It is the most interesting case when taking over code: a coverage report turns a whole
 * package red, and the question "who calls this?" becomes a question one finally asks.
 */
public final class ItineraryExporter {

    private ItineraryExporter() {}

    public static String toPrintable(Trip trip, double minutes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trip ").append(trip.id()).append(" — ").append(trip.mode()).append('\n');
        for (Leg leg : trip.legs()) {
            sb.append("  ").append(leg.from()).append(" → ").append(leg.to())
              .append(" (").append(leg.distanceKm()).append(" km)").append('\n');
        }
        sb.append("Estimated duration: ").append(Math.round(minutes)).append(" minutes");
        if (trip.withLuggage()) {
            sb.append(" (with luggage)");
        }
        return sb.toString();
    }

    public static String summary(Trip trip) {
        return trip.legs().size() + " leg(s), " + Math.round(trip.totalDistanceKm()) + " km";
    }
}
