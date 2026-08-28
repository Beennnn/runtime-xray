package lab.sample.speed;

import lab.sample.model.Leg;

/** On foot: 5 km/h, everyone's walking speed. */
public final class WalkSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return 5;
    }
}
