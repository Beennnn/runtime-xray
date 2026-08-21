package lab.sample.speed;

import lab.sample.model.Leg;

/** À pied : 5 km/h, la vitesse de marche de tout le monde. */
public final class WalkSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return 5;
    }
}
