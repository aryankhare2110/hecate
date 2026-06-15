package com.hecate.analysis;

public final class LockContentionStats {

    private final String lockKey;
    private final String lockClass;
    private final long acquisitions;
    private final long contendedAcquisitions;
    private final int distinctThreads;
    private final long totalWaitNs;
    private final long totalHoldNs;
    private final long maxWaitNs;
    private final double avgWaitNs;
    private final double contentionFactor;

    public LockContentionStats(String lockKey, String lockClass, long acquisitions,
                               long contendedAcquisitions, int distinctThreads, long totalWaitNs,
                               long totalHoldNs, long maxWaitNs, double avgWaitNs, double contentionFactor) {
        this.lockKey = lockKey;
        this.lockClass = lockClass;
        this.acquisitions = acquisitions;
        this.contendedAcquisitions = contendedAcquisitions;
        this.distinctThreads = distinctThreads;
        this.totalWaitNs = totalWaitNs;
        this.totalHoldNs = totalHoldNs;
        this.maxWaitNs = maxWaitNs;
        this.avgWaitNs = avgWaitNs;
        this.contentionFactor = contentionFactor;
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockClass() {
        return lockClass;
    }

    public long getAcquisitions() {
        return acquisitions;
    }

    public long getContendedAcquisitions() {
        return contendedAcquisitions;
    }

    public int getDistinctThreads() {
        return distinctThreads;
    }

    public long getTotalWaitNs() {
        return totalWaitNs;
    }

    public long getTotalHoldNs() {
        return totalHoldNs;
    }

    public long getMaxWaitNs() {
        return maxWaitNs;
    }

    public double getAvgWaitNs() {
        return avgWaitNs;
    }

    public double getContentionFactor() {
        return contentionFactor;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): factor=%.3f wait=%dns hold=%dns acq=%d/%d threads=%d",
                lockKey, lockClass, contentionFactor, totalWaitNs, totalHoldNs,
                contendedAcquisitions, acquisitions, distinctThreads);
    }
}

/*
 * Notes
 * - Aggregated contention metrics for one lock, computed by ContentionAnalyzer. A value object
 *   so callers and tests can read exact numbers without parsing a Finding.
 * - contentionFactor = totalWaitNs / totalHoldNs: 0 is uncontended; values near or above 1.0
 *   mean threads queue for the lock about as long as it is actually used, a serialization
 *   bottleneck.
 */
