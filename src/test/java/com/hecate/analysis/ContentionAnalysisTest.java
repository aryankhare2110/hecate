package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import com.hecate.events.LockWaitEvent;
import com.hecate.util.EventExporter;
import com.hecate.util.EventLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Foundation test for the analysis engine: a hand-built trace with known answers.
 *
 * Scenario — one lock {@code lock@A}, two threads:
 *   t=100  T1 ACQUIRE A          (no preceding WAIT -> uncontended)
 *   t=150  T2 WAIT A             (T2 starts blocking)
 *   t=200  T1 RELEASE A hold=100 (T1 held 100ns)
 *   t=210  T2 ACQUIRE A          (waited 210-150 = 60ns -> contended)
 *   t=270  T2 RELEASE A hold=60  (T2 held 60ns)
 *
 * Expected for lock@A:
 *   acquisitions=2, contended=1, threads=2,
 *   totalWait=60, totalHold=160, maxWait=60, avgWait=30,
 *   contentionFactor = 60/160 = 0.375
 */
class ContentionAnalysisTest {

    private static List<Event> scenario() {
        return List.of(
                new LockAcquireEvent(100, 1, "T1", "lock@A", "Account"),
                new LockWaitEvent(150, 2, "T2", "lock@A", "Account"),
                new LockReleaseEvent(200, 1, "T1", "lock@A", "Account", 100),
                new LockAcquireEvent(210, 2, "T2", "lock@A", "Account"),
                new LockReleaseEvent(270, 2, "T2", "lock@A", "Account", 60)
        );
    }

    @Test
    void reconstructsAcquisitionsWithCorrectWaitAndHold() {
        LockStateModel model = new LockStateModel(scenario());

        List<LockAcquisition> acqs = model.getAcquisitions();
        assertEquals(2, acqs.size(), "two acquire/release pairs expected");

        LockAcquisition first = acqs.get(0); // T1, acquired earliest
        assertEquals(1, first.getThreadId());
        assertFalse(first.isContended(), "T1 had no WAIT, so it is uncontended");
        assertEquals(0, first.getWaitDuration());
        assertEquals(100, first.getHoldDuration());
        assertTrue(first.getHeldWhenAcquired().isEmpty(), "T1 held nothing else");

        LockAcquisition second = acqs.get(1); // T2
        assertEquals(2, second.getThreadId());
        assertTrue(second.isContended(), "T2 blocked before acquiring");
        assertEquals(60, second.getWaitDuration());
        assertEquals(60, second.getHoldDuration());
    }

    @Test
    void computesExactContentionStats() {
        LockStateModel model = new LockStateModel(scenario());
        List<LockContentionStats> stats = new ContentionAnalyzer().computeStats(model);

        assertEquals(1, stats.size(), "single lock in this trace");
        LockContentionStats a = stats.get(0);

        assertEquals("lock@A", a.getLockKey());
        assertEquals("Account", a.getLockClass());
        assertEquals(2, a.getAcquisitions());
        assertEquals(1, a.getContendedAcquisitions());
        assertEquals(2, a.getDistinctThreads());
        assertEquals(60, a.getTotalWaitNs());
        assertEquals(160, a.getTotalHoldNs());
        assertEquals(60, a.getMaxWaitNs());
        assertEquals(30.0, a.getAvgWaitNs(), 1e-9);
        assertEquals(0.375, a.getContentionFactor(), 1e-9);
    }

    @Test
    void analyzeProducesWarningFinding() {
        LockStateModel model = new LockStateModel(scenario());
        List<Finding> findings = new ContentionAnalyzer().analyze(model);

        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals("CONTENTION", f.getCategory());
        // factor 0.375 is >= WARNING (0.25) and < CRITICAL (1.0)
        assertEquals(Finding.Severity.WARNING, f.getSeverity());
        assertEquals(0.375, (double) f.getDetails().get("contentionFactor"), 1e-9);
    }

    @Test
    void roundTripsThroughJsonExportAndLoad() throws Exception {
        // Proves the real pipeline: captured events -> JSON file -> loaded -> analyzed.
        String filename = "analysis-contention-fixture.json";
        EventExporter.exportToFile(scenario(), filename);

        List<Event> loaded = EventLoader.loadFromFile(filename);
        LockStateModel model = new LockStateModel(loaded);
        List<LockContentionStats> stats = new ContentionAnalyzer().computeStats(model);

        assertEquals(1, stats.size());
        assertEquals(0.375, stats.get(0).getContentionFactor(), 1e-9);
        assertEquals(60, stats.get(0).getTotalWaitNs());
    }
}
