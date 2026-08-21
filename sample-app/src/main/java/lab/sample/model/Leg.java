package lab.sample.model;

/** Une étape du trajet : une portion parcourue d'un seul tenant. */
public record Leg(String from, String to, double distanceKm) {}
