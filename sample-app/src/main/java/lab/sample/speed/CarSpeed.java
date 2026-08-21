package lab.sample.speed;

import lab.sample.model.Leg;

/** En voiture : plus l'étape est longue, plus on roule vite (ville puis voie rapide). */
public final class CarSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        if (leg.distanceKm() < 5) {
            return 25;   // trajet urbain
        }
        return leg.distanceKm() < 40 ? 70 : 110;
    }
}
