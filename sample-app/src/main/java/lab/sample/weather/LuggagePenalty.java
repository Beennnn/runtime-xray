package lab.sample.weather;

/** Slowdown caused by luggage. A double condition: there must be luggage <b>and</b> a mode
 *  where it gets in the way. Two nested conditions = a branch reached in only a minority of
 *  scenarios. */
public final class LuggagePenalty {

    private LuggagePenalty() {}

    public static double slowdownFactor(boolean withLuggage) {
        return withLuggage ? 1.10 : 1.00;
    }
}
