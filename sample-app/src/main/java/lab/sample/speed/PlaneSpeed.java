package lab.sample.speed;

import lab.sample.model.Leg;

/** En avion. <b>Jamais instanciée par les scénarios</b> : cette classe entière doit
 *  apparaître à 0 % dans un rapport de couverture. C'est le test le plus visuel du
 *  critère « sait-on par où le code n'est PAS passé ? » — un profiler par
 *  échantillonnage, lui, ne dira jamais rien de ce code. */
public final class PlaneSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 1000 ? 850 : 600;
    }
}
