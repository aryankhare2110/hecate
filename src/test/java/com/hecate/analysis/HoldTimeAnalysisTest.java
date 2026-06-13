package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Known-answer test for {@link HoldTimeAnalyzer}.
 *
 * Lock {@code lock@H} acquired 10 times by one thread, holds = nine of 100ns and one of
 * 1000ns. Chosen so the statistics are exact and round:
 *   total=1900, mean=190, median=100, p95=1000, max=1000,
 *   population stddev = sqrt(72900) = 270.
 * Outlier threshold = mean + 2*stddev = 190 + 540 = 730, so only the 1000ns hold is flagged.
 */
class HoldTimeAnalysisTest {

    private static List<Event> scenario() {
        long[] holds = {100, 100, 100, 100, 100, 100, 100, 100, 100, 1000};
        List<Event> events = new ArrayList<>();
        long t = 0;
        for (long hold : holds) {
            events.add(new LockAcquireEvent(t, 1, "T1", "lock@H", "Cache"));
            events.add(new LockReleaseEvent(t + hold, 1, "T1", "lock@H", "Cache", hold));
            t += 2000; // keep acquisitions well separated
        }
        return events;
    }

    @Test
    void computesExactDistribution() {
        LockStateModel model = new LockStateModel(scenario());
        List<HoldTimeStats> stats = new HoldTimeAnalyzer().computeStats(model);

        assertEquals(1, stats.size());
        HoldTimeStats s = stats.get(0);

        assertEquals("lock@H", s.getLockKey());
        assertEquals(10, s.getCount());
        assertEquals(1900, s.getTotalHoldNs());
        assertEquals(190.0, s.getMeanHoldNs(), 1e-9);
        assertEquals(100.0, s.getMedianHoldNs(), 1e-9);
        assertEquals(1000, s.getP95HoldNs());
        assertEquals(1000, s.getMaxHoldNs());
        assertEquals(270.0, s.getStdDevHoldNs(), 1e-6);
    }

    @Test
    void flagsTheSingleOutlierCriticalSection() {
        LockStateModel model = new LockStateModel(scenario());
        List<Finding> findings = new HoldTimeAnalyzer().analyze(model);

        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals("HOLD_TIME", f.getCategory());
        assertEquals(Finding.Severity.WARNING, f.getSeverity());

        assertEquals(730.0, (double) f.getDetails().get("outlierThresholdNs"), 1e-9);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outliers = (List<Map<String, Object>>) f.getDetails().get("outliers");
        assertEquals(1, outliers.size(), "only the 1000ns hold should be flagged");
        assertEquals(1000L, outliers.get(0).get("holdNs"));
    }

    @Test
    void uniformHoldsProduceNoOutliers() {
        // All holds equal -> stddev 0 -> nothing flagged (no false positives).
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            long t = i * 1000L;
            events.add(new LockAcquireEvent(t, 1, "T1", "lock@U", "Cache"));
            events.add(new LockReleaseEvent(t + 50, 1, "T1", "lock@U", "Cache", 50));
        }
        LockStateModel model = new LockStateModel(events);
        assertTrue(new HoldTimeAnalyzer().analyze(model).isEmpty());
    }
}
