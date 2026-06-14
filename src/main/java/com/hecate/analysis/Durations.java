package com.hecate.analysis;

/**
 * Formats nanosecond durations into compact, human-readable strings so reports read in
 * sensible units (ns / µs / ms / s) instead of giant raw nanosecond counts.
 */
public final class Durations {

    private Durations() {
    }

    public static String human(long ns) {
        if (ns < 1_000L) {
            return ns + " ns";
        }
        if (ns < 1_000_000L) {
            return String.format("%.1f µs", ns / 1_000.0);
        }
        if (ns < 1_000_000_000L) {
            return String.format("%.1f ms", ns / 1_000_000.0);
        }
        return String.format("%.2f s", ns / 1_000_000_000.0);
    }
}
