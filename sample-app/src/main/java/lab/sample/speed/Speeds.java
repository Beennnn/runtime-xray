package lab.sample.speed;

import lab.sample.model.Mode;

/** Factory: maps a transport mode to its speed model. */
public final class Speeds {

    // Shared instances: the speed models are stateless. Allocating them on every call
    // captured ~25 % of the samples — allocation noise that hid the computation.
    private static final SpeedModel CAR = new CarSpeed();
    private static final SpeedModel TRAIN = new TrainSpeed();
    private static final SpeedModel BIKE = new BikeSpeed();
    private static final SpeedModel WALK = new WalkSpeed();

    private Speeds() {}

    public static SpeedModel forMode(Mode mode) {
        return switch (mode) {
            case CAR -> CAR;
            case TRAIN -> TRAIN;
            case BIKE -> BIKE;
            case WALK -> WALK;
            // Instantiated here rather than shared like the others: this way PlaneSpeed is
            // NEVER loaded nor executed, and stays at 0 % in the coverage report. A static
            // final field would have had it built when Speeds was loaded.
            case PLANE -> new PlaneSpeed();
        };
    }
}
