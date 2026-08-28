package lab.sample.speed;

import lab.sample.model.Leg;

/** By car: the longer the leg, the faster one drives (town, then dual carriageway). */
public final class CarSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        if (leg.distanceKm() < 5) {
            return 25;   // trajet urbain
        }
        return leg.distanceKm() < 40 ? 70 : 110;
    }
}
