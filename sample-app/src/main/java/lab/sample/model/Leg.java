package lab.sample.model;

/** One leg of the trip: a stretch covered in one go. */
public record Leg(String from, String to, double distanceKm) {}
