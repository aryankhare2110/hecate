package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects thread starvation: locks where the waiting burden falls disproportionately on
 * some threads while others sail through.
 *
 * For each lock it sums every thread's total wait time, then computes Jain's fairness
 * index over those per-thread totals. An index near {@code 1/n} means one thread is
 * absorbing nearly all the contention — a starvation signal that raw averages hide.
 *
 * Only locks contended by at least two threads, with non-zero total wait, are scored.
 */
public class FairnessAnalyzer implements Analyzer {

    /** Below this index a lock is flagged WARNING (some imbalance). */
    private static final double WARNING_THRESHOLD = 0.8;
    /** Below this index a lock is flagged CRITICAL (severe starvation). */
    private static final double CRITICAL_THRESHOLD = 0.5;

    @Override
    public String name() {
        return "Fairness Analyzer";
    }

    /** Per-lock fairness stats for every lock with multi-thread contention, least-fair first. */
    public List<FairnessStats> computeStats(LockStateModel model) {
        // lockKey -> (threadId -> total wait)
        Map<String, Map<Long, Long>> waitByLockThread = new LinkedHashMap<>();
        Map<String, String> lockClasses = new LinkedHashMap<>();

        for (LockAcquisition acq : model.getAcquisitions()) {
            lockClasses.putIfAbsent(acq.getLockKey(), acq.getLockClass());
            Map<Long, Long> perThread = waitByLockThread.computeIfAbsent(acq.getLockKey(), k -> new LinkedHashMap<>());
            perThread.merge(acq.getThreadId(), acq.getWaitDuration(), Long::sum);
        }

        List<FairnessStats> stats = new ArrayList<>();
        for (Map.Entry<String, Map<Long, Long>> e : waitByLockThread.entrySet()) {
            Map<Long, Long> perThread = e.getValue();
            if (perThread.size() < 2) {
                continue; // fairness needs at least two contenders
            }
            long total = 0;
            long max = Long.MIN_VALUE;
            long min = Long.MAX_VALUE;
            double sumSq = 0.0;
            for (long w : perThread.values()) {
                total += w;
                max = Math.max(max, w);
                min = Math.min(min, w);
                sumSq += (double) w * w;
            }
            if (total == 0) {
                continue; // no contention on this lock -> trivially fair, nothing to report
            }
            int n = perThread.size();
            // Jain's fairness index: (sum)^2 / (n * sumOfSquares)
            double jain = ((double) total * total) / (n * sumSq);

            stats.add(new FairnessStats(e.getKey(), lockClasses.get(e.getKey()), n, jain,
                    total, max, min, perThread));
        }
        stats.sort(Comparator.comparingDouble(FairnessStats::getJainIndex));
        return stats;
    }

    @Override
    public List<Finding> analyze(LockStateModel model) {
        List<Finding> findings = new ArrayList<>();
        for (FairnessStats s : computeStats(model)) {
            Finding.Severity severity = severityFor(s.getJainIndex());
            if (severity == Finding.Severity.INFO) {
                continue; // fair enough; don't clutter the report
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lockKey", s.getLockKey());
            details.put("lockClass", s.getLockClass());
            details.put("jainIndex", s.getJainIndex());
            details.put("distinctThreads", s.getDistinctThreads());
            details.put("totalWaitNs", s.getTotalWaitNs());
            details.put("maxThreadWaitNs", s.getMaxThreadWaitNs());
            details.put("minThreadWaitNs", s.getMinThreadWaitNs());
            details.put("perThreadWaitNs", s.getPerThreadWaitNs());

            String summary = String.format(
                    "Lock %s (%s), fairness index %.3f across %d threads: waiting is unevenly distributed (max %dns vs min %dns per thread)",
                    s.getLockKey(), s.getLockClass(), s.getJainIndex(), s.getDistinctThreads(),
                    s.getMaxThreadWaitNs(), s.getMinThreadWaitNs());

            findings.add(new Finding(severity, "FAIRNESS", summary, details));
        }
        return findings;
    }

    private Finding.Severity severityFor(double jainIndex) {
        if (jainIndex < CRITICAL_THRESHOLD) {
            return Finding.Severity.CRITICAL;
        }
        if (jainIndex < WARNING_THRESHOLD) {
            return Finding.Severity.WARNING;
        }
        return Finding.Severity.INFO;
    }
}
