package lab.sample.model;

/** Time of day. Only {@code RUSH_HOUR} triggers the traffic-jam computation, and only by
 *  car: it is the simplest example of a function called or not depending on context. */
public enum TimeOfDay { QUIET, RUSH_HOUR, NIGHT }
