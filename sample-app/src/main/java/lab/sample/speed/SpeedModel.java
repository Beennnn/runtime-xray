package lab.sample.speed;

import lab.sample.model.Leg;

/** Average speed of a transport mode over a leg.
 *
 *  <p>The program's <b>virtual dispatch</b> point: the call always starts from the
 *  interface, but it is the concrete implementation that does the work. A good call tree
 *  must name the implementation actually executed; some tools stop at the interface, and
 *  that is a difference one sees at once. */
public interface SpeedModel {
    double kmPerHour(Leg leg);
}
