package com.hecate.testapps;

/**
 * Measures the runtime overhead Hecate's instrumentation adds to lock-heavy code.
 *
 * Several threads hammer a single {@code synchronized} block with a near-empty critical
 * section, which is the pessimistic case: the lock bookkeeping dominates, so any
 * per-acquire instrumentation cost shows up at its largest. Only the work loop is timed —
 * the agent's end-of-run JSON export happens afterwards and is reported separately by the
 * agent itself, so it is excluded from this number.
 *
 * Run it twice and compare the printed "work elapsed" line:
 * <pre>
 *   java -cp ...:target/test-classes com.hecate.testapps.OverheadBenchmark
 *   java -javaagent:target/hecate-1.0-SNAPSHOT.jar -cp ...:target/test-classes com.hecate.testapps.OverheadBenchmark
 * </pre>
 *
 * Optional args: {@code [threads] [iterationsPerThread]} (defaults 4 and 20000).
 */
public class OverheadBenchmark {

    private static final Object lock = new Object();
    private static long counter = 0;

    public static void main(String[] args) throws InterruptedException {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 20_000;

        Runnable work = () -> {
            for (int i = 0; i < iterations; i++) {
                synchronized (lock) {
                    counter++;
                }
            }
        };

        Thread[] pool = new Thread[threads];
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            pool[t] = new Thread(work, "Bench-" + t);
            pool[t].start();
        }
        for (Thread t : pool) {
            t.join();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        long totalAcquisitions = (long) threads * iterations;
        System.out.println();
        System.out.println("Threads: " + threads + "  Iterations/thread: " + iterations);
        System.out.println("Lock acquisitions: " + totalAcquisitions + "  (final counter: " + counter + ")");
        System.out.println("work elapsed: " + elapsedMs + " ms");
        if (elapsedMs > 0) {
            System.out.println("throughput: " + (totalAcquisitions / elapsedMs) + " acquisitions/ms");
        }
    }
}
