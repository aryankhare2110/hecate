package com.hecate.analysis;

/**
 * Aggregated hold-time distribution for a single lock, computed by {@link HoldTimeAnalyzer}.
 * All durations are nanoseconds.
 */
public final class HoldTimeStats {

    private final String lockKey;
    private final String lockClass;
    private final long count;
    private final long totalHoldNs;
    private final double meanHoldNs;
    private final double medianHoldNs;
    private final long p95HoldNs;
    private final long maxHoldNs;
    private final double stdDevHoldNs;

    public HoldTimeStats(String lockKey, String lockClass, long count, long totalHoldNs,
                         double meanHoldNs, double medianHoldNs, long p95HoldNs, long maxHoldNs,
                         double stdDevHoldNs) {
        this.lockKey = lockKey;
        this.lockClass = lockClass;
        this.count = count;
        this.totalHoldNs = totalHoldNs;
        this.meanHoldNs = meanHoldNs;
        this.medianHoldNs = medianHoldNs;
        this.p95HoldNs = p95HoldNs;
        this.maxHoldNs = maxHoldNs;
        this.stdDevHoldNs = stdDevHoldNs;
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockClass() {
        return lockClass;
    }

    public long getCount() {
        return count;
    }

    public long getTotalHoldNs() {
        return totalHoldNs;
    }

    public double getMeanHoldNs() {
        return meanHoldNs;
    }

    public double getMedianHoldNs() {
        return medianHoldNs;
    }

    public long getP95HoldNs() {
        return p95HoldNs;
    }

    public long getMaxHoldNs() {
        return maxHoldNs;
    }

    public double getStdDevHoldNs() {
        return stdDevHoldNs;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): n=%d mean=%.1fns median=%.1fns p95=%dns max=%dns stddev=%.1fns",
                lockKey, lockClass, count, meanHoldNs, medianHoldNs, p95HoldNs, maxHoldNs, stdDevHoldNs);
    }
}
