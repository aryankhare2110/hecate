package com.hecate.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-lock fairness summary computed by {@link FairnessAnalyzer}.
 *
 * {@code jainIndex} is Jain's fairness index over each thread's total wait time for the
 * lock: it ranges from {@code 1/n} (one thread absorbs all the waiting — maximal
 * starvation) up to {@code 1.0} (every thread waits equally). Lower means some thread is
 * being starved relative to its peers.
 */
public final class FairnessStats {

    private final String lockKey;
    private final String lockClass;
    private final int distinctThreads;
    private final double jainIndex;
    private final long totalWaitNs;
    private final long maxThreadWaitNs;
    private final long minThreadWaitNs;
    private final Map<Long, Long> perThreadWaitNs;

    public FairnessStats(String lockKey, String lockClass, int distinctThreads, double jainIndex,
                         long totalWaitNs, long maxThreadWaitNs, long minThreadWaitNs,
                         Map<Long, Long> perThreadWaitNs) {
        this.lockKey = lockKey;
        this.lockClass = lockClass;
        this.distinctThreads = distinctThreads;
        this.jainIndex = jainIndex;
        this.totalWaitNs = totalWaitNs;
        this.maxThreadWaitNs = maxThreadWaitNs;
        this.minThreadWaitNs = minThreadWaitNs;
        this.perThreadWaitNs = Collections.unmodifiableMap(new LinkedHashMap<>(perThreadWaitNs));
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockClass() {
        return lockClass;
    }

    public int getDistinctThreads() {
        return distinctThreads;
    }

    public double getJainIndex() {
        return jainIndex;
    }

    public long getTotalWaitNs() {
        return totalWaitNs;
    }

    public long getMaxThreadWaitNs() {
        return maxThreadWaitNs;
    }

    public long getMinThreadWaitNs() {
        return minThreadWaitNs;
    }

    /** Total wait time accumulated by each thread on this lock. */
    public Map<Long, Long> getPerThreadWaitNs() {
        return perThreadWaitNs;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): jain=%.3f threads=%d wait[min=%d max=%d total=%d]",
                lockKey, lockClass, jainIndex, distinctThreads, minThreadWaitNs, maxThreadWaitNs, totalWaitNs);
    }
}
