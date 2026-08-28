package lab.sample;

/**
 * Stage measurements: elapsed time and memory in use.
 *
 * <p>Deliberately founded on the JDK alone ({@code System.nanoTime}, {@code Runtime}): these
 * figures serve as an <b>independent reference</b>. When an analysis tool announces a time or
 * a footprint, one wants to be able to confront it with a measurement that does not come from
 * it — and note in passing how much the tool itself slows the run down.
 *
 * <p>Memory is read after a call to {@code System.gc()}: without it, one mostly measures the
 * garbage not yet collected, which means nothing. The call remains a <em>suggestion</em> made
 * to the JVM, never a guarantee — hence the order of magnitude announced rather than an exact
 * value.
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

        System.out.printf("  [%-28s] stage %8.0f ms | total %8.0f ms | memory ~%d MB / %d MB max%n",
                label, sinceLastMs, totalMs, usedMb, maxMb);
    }
}
