package lab.sample.weather;

/** Ralentissement dû aux bagages. Double condition : il faut des bagages <b>et</b> un
 *  mode où ils gênent. Deux conditions imbriquées = une branche qu'on n'atteint que
 *  dans une minorité de scénarios. */
public final class LuggagePenalty {

    private LuggagePenalty() {}

    public static double slowdownFactor(boolean withLuggage) {
        return withLuggage ? 1.10 : 1.00;
    }
}
