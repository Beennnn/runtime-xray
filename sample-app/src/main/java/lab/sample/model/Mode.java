package lab.sample.model;

/** How one travels. This is the calculation's main <b>switchman</b>: each mode activates a
 *  different family of computations (traffic jams for the car, connections for the train,
 *  weather for the bike and for walking).
 *
 *  <p>{@code PLANE} is never chosen by the scenarios: the matching speed class must stay at
 *  0 % in a coverage report. */
public enum Mode { CAR, TRAIN, BIKE, WALK, PLANE }
