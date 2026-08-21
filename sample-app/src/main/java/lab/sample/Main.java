package lab.sample;

import lab.sample.model.Trip;

import java.util.ArrayList;
import java.util.List;

/**
 * Lanceur de l'application-cible.
 *
 * <p>Deux options n'existent que pour les besoins de l'analyse :
 * <ul>
 *   <li>{@code --iterations} — un profiler par échantillonnage n'a rien à montrer sur
 *       une exécution de 5 ms ; il faut du temps CPU pour que des frames soient prélevées.</li>
 *   <li>{@code --hold-seconds} — les outils qui s'<em>attachent</em> à un process vivant
 *       (VisualVM, JMC, JProfiler, YourKit) ont besoin que la JVM soit encore là quand
 *       l'opérateur clique. Sans cette pause, la cible est morte avant d'avoir été
 *       sélectionnée dans la liste.</li>
 * </ul>
 */
public final class Main {

    /** Nombre de trajets distincts fabriqués avant la mesure. Assez pour couvrir toutes
     *  les combinaisons mode × météo × horaire × bagages, assez peu pour que leur
     *  fabrication ne pèse rien face à la boucle de calcul. */
    private static final int TRIP_VARIANTS = 240;

    /** Calibré pour que le calcul dure ~10 s sur un Mac Apple Silicon. C'est le minimum
     *  confortable pour qu'un outil qui s'attache à un process vivant (VisualVM, JMC,
     *  JProfiler) ait le temps d'être lancé, de sélectionner la JVM et d'enregistrer
     *  quelque chose. Une exécution d'une seconde ne laisse aucune prise. */
    private static final int DEFAULT_ITERATIONS = 16_000_000;

    public static void main(String[] args) throws InterruptedException {
        int iterations = intArg(args, "--iterations", DEFAULT_ITERATIONS);
        int holdSeconds = intArg(args, "--hold-seconds", 0);

        System.out.printf("sample-app : %d trajets évalués, pause finale %d s%n", iterations, holdSeconds);
        Metrics metrics = new Metrics();
        metrics.stage("JVM démarrée");

        // Les trajets sont fabriqués UNE FOIS, hors de la boucle mesurée.
        // Sans cette séparation, la génération du jeu de test (concaténations de chaînes,
        // allocations de listes) capte ~70 % des échantillons et l'arbre d'appel montre
        // java.lang.StringConcatHelper au lieu du calcul d'itinéraire. Mesuré, pas supposé.
        List<Trip> trips = new ArrayList<>(TRIP_VARIANTS);
        for (int i = 0; i < TRIP_VARIANTS; i++) {
            trips.add(Scenarios.at(i));
        }

        metrics.stage("jeu de trajets fabriqué");

        double total = 0;
        for (int i = 0; i < iterations; i++) {
            total += RoutePlanner.travelTimeMinutes(trips.get(i % TRIP_VARIANTS));
        }
        // Imprimé pour que le calcul ne puisse pas être éliminé, et pour vérifier d'un
        // coup d'œil que deux exécutions comparées ont bien fait le même travail.
        metrics.stage("calcul terminé");
        System.out.printf("durée cumulée = %.0f minutes%n", total);

        if (holdSeconds > 0) {
            System.out.printf("JVM maintenue en vie %d s — attache ton profiler (pid %d)%n",
                    holdSeconds, ProcessHandle.current().pid());
            Thread.sleep(holdSeconds * 1000L);
        }
    }

    private static int intArg(String[] args, String name, int fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return fallback;
    }
}
