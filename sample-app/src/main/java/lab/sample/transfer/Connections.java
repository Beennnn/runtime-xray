package lab.sample.transfer;

import lab.sample.model.Leg;

import java.util.List;

/**
 * Waiting time at connections — specific to the train.
 *
 * <p>The computation is <b>recursive</b> over the list of legs: it adds a recursion site
 * distinct from {@code comfort.Breaks}', in a branch only some scenarios take. Useful to
 * check that a tool does not confuse two different recursions in the same tree.
 */
public final class Connections {

    private Connections() {}

    public static double waitMinutes(List<Leg> legs, int index) {
        if (index >= legs.size() - 1) {
            return 0; // last leg: no connection after it
        }
        double here = transferMinutes(legs.get(index), legs.get(index + 1));
        return here + waitMinutes(legs, index + 1);
    }

    private static double transferMinutes(Leg arriving, Leg departing) {
        // A connection within the same station is faster than a change of station, and the
        // wait depends on the service frequency — hence on a timetable lookup, the program's
        // only blocking operation.
        double walk = arriving.to().equals(departing.from()) ? 6 : 18;
        return walk + Timetable.frequencyMinutes(departing) / 2.0;
    }
}
