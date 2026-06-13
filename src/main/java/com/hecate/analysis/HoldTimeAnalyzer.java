package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Surfaces locks held for abnormally long, and the individual critical sections
 * responsible. Long holds block every other contender, so an outlier hold is a prime
 * suspect for I/O or heavy work performed inside a {@code synchronized} region.
 *
 * Per lock it builds the hold-time distribution, then flags any acquisition whose hold
 * exceeds {@code mean + OUTLIER_SIGMA * stddev} (only when there is a real distribution
 * to compare against: at least {@code MIN_SAMPLES} holds with non-zero variance).
 */
public class HoldTimeAnalyzer implements Analyzer {

    /** How many standard deviations above the mean counts as an outlier hold. */
    private static final double OUTLIER_SIGMA = 2.0;
    /** Minimum acquisitions before outlier detection is meaningful. */
    private static final int MIN_SAMPLES = 3;

    @Override
    public String name() {
        return "Hold-Time Analyzer";
    }

    /** Per-lock hold-time distribution, ranked by total time held (most first). */
    public List<HoldTimeStats> computeStats(LockStateModel model) {
        Map<String, List<LockAcquisition>> byLock = new LinkedHashMap<>();
        for (LockAcquisition acq : model.getAcquisitions()) {
            byLock.computeIfAbsent(acq.getLockKey(), k -> new ArrayList<>()).add(acq);
        }

        List<HoldTimeStats> stats = new ArrayList<>();
        for (Map.Entry<String, List<LockAcquisition>> e : byLock.entrySet()) {
            stats.add(toStats(e.getKey(), e.getValue()));
        }
        stats.sort(Comparator.comparingLong(HoldTimeStats::getTotalHoldNs).reversed());
        return stats;
    }

    @Override
    public List<Finding> analyze(LockStateModel model) {
        Map<String, List<LockAcquisition>> byLock = new LinkedHashMap<>();
        for (LockAcquisition acq : model.getAcquisitions()) {
            byLock.computeIfAbsent(acq.getLockKey(), k -> new ArrayList<>()).add(acq);
        }

        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<String, List<LockAcquisition>> e : byLock.entrySet()) {
            List<LockAcquisition> acqs = e.getValue();
            HoldTimeStats s = toStats(e.getKey(), acqs);

            if (acqs.size() < MIN_SAMPLES || s.getStdDevHoldNs() <= 0.0) {
                continue;
            }
            double threshold = s.getMeanHoldNs() + OUTLIER_SIGMA * s.getStdDevHoldNs();

            List<Map<String, Object>> outliers = new ArrayList<>();
            for (LockAcquisition acq : acqs) {
                if (acq.getHoldDuration() > threshold) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("threadId", acq.getThreadId());
                    o.put("threadName", acq.getThreadName());
                    o.put("holdNs", acq.getHoldDuration());
                    o.put("acquireTs", acq.getAcquireTs());
                    outliers.add(o);
                }
            }
            if (outliers.isEmpty()) {
                continue;
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lockKey", s.getLockKey());
            details.put("lockClass", s.getLockClass());
            details.put("meanHoldNs", s.getMeanHoldNs());
            details.put("medianHoldNs", s.getMedianHoldNs());
            details.put("stdDevHoldNs", s.getStdDevHoldNs());
            details.put("outlierThresholdNs", threshold);
            details.put("maxHoldNs", s.getMaxHoldNs());
            details.put("outliers", outliers);

            String summary = String.format(
                    "Lock %s (%s) — %d critical section(s) held far longer than typical (max %dns vs mean %.0fns, threshold %.0fns)",
                    s.getLockKey(), s.getLockClass(), outliers.size(), s.getMaxHoldNs(),
                    s.getMeanHoldNs(), threshold);

            findings.add(new Finding(Finding.Severity.WARNING, "HOLD_TIME", summary, details));
        }
        return findings;
    }

    private HoldTimeStats toStats(String lockKey, List<LockAcquisition> acqs) {
        String lockClass = acqs.get(0).getLockClass();
        int n = acqs.size();

        List<Long> holds = new ArrayList<>(n);
        long total = 0;
        long max = 0;
        for (LockAcquisition acq : acqs) {
            long h = acq.getHoldDuration();
            holds.add(h);
            total += h;
            max = Math.max(max, h);
        }
        Collections.sort(holds);

        double mean = (double) total / n;
        double variance = 0.0;
        for (long h : holds) {
            double d = h - mean;
            variance += d * d;
        }
        variance /= n; // population variance
        double stdDev = Math.sqrt(variance);

        double median = percentileInterpolatedMedian(holds);
        long p95 = percentileNearestRank(holds, 0.95);

        return new HoldTimeStats(lockKey, lockClass, n, total, mean, median, p95, max, stdDev);
    }

    private static double percentileInterpolatedMedian(List<Long> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return 0.0;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static long percentileNearestRank(List<Long> sorted, double p) {
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(p * n);
        int idx = Math.min(Math.max(rank - 1, 0), n - 1);
        return sorted.get(idx);
    }
}
