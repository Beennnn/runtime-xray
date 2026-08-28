package lab.sample.speed;

import lab.sample.model.Leg;

/** By plane. <b>Never instantiated by the scenarios</b>: this whole class must appear at
 *  0 % in a coverage report. It is the most visual test of the criterion "do we know where
 *  the code did NOT go?" — a sampling profiler will never say anything about this code. */
public final class PlaneSpeed implements SpeedModel {

    @Override
    public double kmPerHour(Leg leg) {
        return leg.distanceKm() > 1000 ? 850 : 600;
    }
}
