package lab.sample;

/**
 * Mesures d'étape : temps écoulé et mémoire occupée.
 *
 * <p>Volontairement fondé sur le seul JDK ({@code System.nanoTime}, {@code Runtime}) :
 * ces chiffres servent de <b>référence indépendante</b>. Quand un outil d'analyse annonce
 * un temps ou une empreinte, on veut pouvoir le confronter à une mesure qui ne vient pas
 * de lui — et constater au passage combien l'outil lui-même ralentit l'exécution.
 *
 * <p>La mémoire est lue après un appel à {@code System.gc()} : sans lui, on mesure surtout
 * les déchets pas encore ramassés, ce qui ne veut rien dire. L'appel reste une
 * <em>suggestion</em> faite à la JVM, jamais une garantie — d'où l'ordre de grandeur
 * annoncé plutôt qu'une valeur exacte.
 */
public final class Metrics {

    private final long startNanos = System.nanoTime();
    private long lastNanos = startNanos;

    void stage(String label) {
        long now = System.nanoTime();
        double sinceLastMs = (now - lastNanos) / 1_000_000.0;
        double totalMs = (now - startNanos) / 1_000_000.0;
        lastNanos = now;

        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        System.out.printf("  [%-28s] étape %8.0f ms | cumul %8.0f ms | mémoire ~%d Mo / %d Mo max%n",
                label, sinceLastMs, totalMs, usedMb, maxMb);
    }
}
