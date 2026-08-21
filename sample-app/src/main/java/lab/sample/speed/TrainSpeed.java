package lab.sample.speed;

import lab.sample.model.Leg;

/** En train : vitesse quasi constante, indépendante de la longueur de l'étape. */
public final class TrainSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 100 ? 220 : 90;
    }
}
