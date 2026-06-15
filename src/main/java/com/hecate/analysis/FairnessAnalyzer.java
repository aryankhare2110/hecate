package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FairnessAnalyzer implements Analyzer {

    private static final double WARNING_THRESHOLD = 0.8;
    private static final double CRITICAL_THRESHOLD = 0.5;

    @Override
    public String name() {
        return "Fairness Analyzer";
    }

    public List<FairnessStats> computeStats(LockStateModel model) {
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
                continue;
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
                continue;
            }
            int n = perThread.size();
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
                continue;
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
                    "Lock %s (%s): uneven wait distribution (fairness index %.2f across %d threads; per-thread waits %s to %s).",
                    s.getLockKey(), s.getLockClass(), s.getJainIndex(), s.getDistinctThreads(),
                    Durations.human(s.getMinThreadWaitNs()), Durations.human(s.getMaxThreadWaitNs()));

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

/*
 * Notes
 * - Detects thread starvation: locks where the waiting burden falls on some threads while
 *   others sail through.
 * - For each lock it sums each thread's total wait time, then computes Jain's fairness index
 *   over those per-thread totals: (sum)^2 / (n * sumOfSquares). The index runs from 1/n (one
 *   thread absorbs all the waiting) up to 1.0 (perfectly even).
 * - Only locks contended by at least two threads with non-zero total wait are scored; others
 *   are trivially fair and skipped.
 * - Severity: index < 0.5 CRITICAL, < 0.8 WARNING, otherwise INFO. analyze() drops INFO so a
 *   fair lock does not clutter the report.
 */
