package lab.sample.speed;

import lab.sample.model.Leg;

/** À vélo : on ralentit sur les longues distances (fatigue). */
public final class BikeSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 20 ? 15 : 18;
    }
}
