package com.hecate.analysis;

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

/*
 * Notes
 * - Formats nanosecond durations into compact strings (ns, µs, ms, s) so reports read in
 *   sensible units instead of giant raw nanosecond counts. Used by every analyzer's summary.
 */
