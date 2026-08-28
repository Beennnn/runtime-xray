package lab.sample.speed;

import lab.sample.model.Leg;

/** By train: near-constant speed, independent of the leg's length. */
public final class TrainSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 100 ? 220 : 90;
    }
}
