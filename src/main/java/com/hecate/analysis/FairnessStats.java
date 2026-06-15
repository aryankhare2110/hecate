package com.hecate.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public Map<Long, Long> getPerThreadWaitNs() {
        return perThreadWaitNs;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): jain=%.3f threads=%d wait[min=%d max=%d total=%d]",
                lockKey, lockClass, jainIndex, distinctThreads, minThreadWaitNs, maxThreadWaitNs, totalWaitNs);
    }
}

/*
 * Notes
 * - Per-lock fairness summary computed by FairnessAnalyzer.
 * - jainIndex is Jain's fairness index over each thread's total wait time for the lock: it runs
 *   from 1/n (one thread absorbs all the waiting, maximal starvation) up to 1.0 (every thread
 *   waits equally). Lower means some thread is starved relative to its peers.
 * - perThreadWaitNs is the total wait each thread accumulated on this lock.
 */
