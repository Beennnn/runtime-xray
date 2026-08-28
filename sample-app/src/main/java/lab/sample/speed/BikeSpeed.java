package lab.sample.speed;

import lab.sample.model.Leg;

/** By bike: one slows down over long distances (fatigue). */
public final class BikeSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 20 ? 15 : 18;
    }
}
