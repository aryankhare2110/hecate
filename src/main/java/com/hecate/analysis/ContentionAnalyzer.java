package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ranks locks by how much they serialize the program.
 *
 * For each lock it sums the time threads spent blocked waiting for it (from the
 * WAIT&rarr;ACQUIRE gap reconstructed in {@link LockStateModel}) against the time the
 * lock was actually held, yielding a {@code contentionFactor = totalWait / totalHold}.
 * Pure O(n) aggregation over the acquisition list.
 */
public class ContentionAnalyzer implements Analyzer {

    /** Contention factor at/above which a lock is flagged WARNING. */
    private static final double WARNING_THRESHOLD = 0.25;
    /** Contention factor at/above which a lock is flagged CRITICAL. */
    private static final double CRITICAL_THRESHOLD = 1.0;

    @Override
    public String name() {
        return "Contention Analyzer";
    }

    /**
     * Computes per-lock contention stats, ranked most-contended first.
     * Exposed separately from {@link #analyze} so callers can read exact metrics.
     */
    public List<LockContentionStats> computeStats(LockStateModel model) {
        Map<String, Accumulator> byLock = new LinkedHashMap<>();

        for (LockAcquisition acq : model.getAcquisitions()) {
            Accumulator acc = byLock.computeIfAbsent(acq.getLockKey(), k -> new Accumulator(acq.getLockClass()));
            acc.add(acq);
        }

        List<LockContentionStats> stats = new ArrayList<>();
        for (Map.Entry<String, Accumulator> e : byLock.entrySet()) {
            stats.add(e.getValue().toStats(e.getKey()));
        }
        stats.sort(Comparator.comparingDouble(LockContentionStats::getContentionFactor).reversed()
                .thenComparing(Comparator.comparingLong(LockContentionStats::getTotalWaitNs).reversed()));
        return stats;
    }

    @Override
    public List<Finding> analyze(LockStateModel model) {
        List<Finding> findings = new ArrayList<>();
        for (LockContentionStats s : computeStats(model)) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lockKey", s.getLockKey());
            details.put("lockClass", s.getLockClass());
            details.put("contentionFactor", s.getContentionFactor());
            details.put("totalWaitNs", s.getTotalWaitNs());
            details.put("totalHoldNs", s.getTotalHoldNs());
            details.put("maxWaitNs", s.getMaxWaitNs());
            details.put("avgWaitNs", s.getAvgWaitNs());
            details.put("acquisitions", s.getAcquisitions());
            details.put("contendedAcquisitions", s.getContendedAcquisitions());
            details.put("distinctThreads", s.getDistinctThreads());

            String summary = String.format(
                    "Lock %s (%s), contention factor %.3f: %d/%d acquisitions contended across %d threads, %dns total wait vs %dns held",
                    s.getLockKey(), s.getLockClass(), s.getContentionFactor(),
                    s.getContendedAcquisitions(), s.getAcquisitions(), s.getDistinctThreads(),
                    s.getTotalWaitNs(), s.getTotalHoldNs());

            findings.add(new Finding(severityFor(s), "CONTENTION", summary, details));
        }
        return findings;
    }

    private Finding.Severity severityFor(LockContentionStats s) {
        if (s.getContentionFactor() >= CRITICAL_THRESHOLD) {
            return Finding.Severity.CRITICAL;
        }
        if (s.getContentionFactor() >= WARNING_THRESHOLD) {
            return Finding.Severity.WARNING;
        }
        return Finding.Severity.INFO;
    }

    private static final class Accumulator {
        final String lockClass;
        long acquisitions;
        long contended;
        long totalWaitNs;
        long totalHoldNs;
        long maxWaitNs;
        final Set<Long> threads = new TreeSet<>();

        Accumulator(String lockClass) {
            this.lockClass = lockClass;
        }

        void add(LockAcquisition acq) {
            acquisitions++;
            long wait = acq.getWaitDuration();
            if (acq.isContended()) {
                contended++;
            }
            totalWaitNs += wait;
            totalHoldNs += acq.getHoldDuration();
            maxWaitNs = Math.max(maxWaitNs, wait);
            threads.add(acq.getThreadId());
        }

        LockContentionStats toStats(String lockKey) {
            double avgWait = acquisitions == 0 ? 0.0 : (double) totalWaitNs / acquisitions;
            double factor;
            if (totalHoldNs > 0) {
                factor = (double) totalWaitNs / totalHoldNs;
            } else {
                factor = totalWaitNs > 0 ? Double.POSITIVE_INFINITY : 0.0;
            }
            return new LockContentionStats(lockKey, lockClass, acquisitions, contended,
                    threads.size(), totalWaitNs, totalHoldNs, maxWaitNs, avgWait, factor);
        }
    }
}
