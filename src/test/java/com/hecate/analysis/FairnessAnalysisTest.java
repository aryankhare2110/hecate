package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import com.hecate.events.LockWaitEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Known-answer test for {@link FairnessAnalyzer}.
 *
 * Lock {@code lock@F}, three rounds: T1 always acquires with no wait, T2 always waits
 * 300ns. Per-thread totals are T1=0, T2=900, so Jain's index over {0, 900} is
 * (900)^2 / (2 * (0 + 900^2)) = 0.5 — the worst possible for two threads.
 *
 * A second lock {@code lock@S} is touched by only one thread and must be excluded
 * (fairness needs at least two contenders).
 */
class FairnessAnalysisTest {

    private static List<Event> scenario() {
        List<Event> events = new ArrayList<>();
        long[] starts = {100, 500, 900};
        for (long base : starts) {
            // T1: uncontended acquire (no WAIT) -> wait 0
            events.add(new LockAcquireEvent(base, 1, "T1", "lock@F", "Queue"));
            events.add(new LockReleaseEvent(base + 10, 1, "T1", "lock@F", "Queue", 10));
            // T2: waits 300ns before acquiring
            events.add(new LockWaitEvent(base, 2, "T2", "lock@F", "Queue"));
            events.add(new LockAcquireEvent(base + 300, 2, "T2", "lock@F", "Queue"));
            events.add(new LockReleaseEvent(base + 310, 2, "T2", "lock@F", "Queue", 10));
        }
        // Single-thread lock: must not appear in fairness stats.
        events.add(new LockAcquireEvent(50, 1, "T1", "lock@S", "Single"));
        events.add(new LockReleaseEvent(60, 1, "T1", "lock@S", "Single", 10));
        return events;
    }

    @Test
    void computesJainIndexAndExcludesSingleThreadLocks() {
        LockStateModel model = new LockStateModel(scenario());
        List<FairnessStats> stats = new FairnessAnalyzer().computeStats(model);

        assertEquals(1, stats.size(), "only the two-thread lock is scored");
        FairnessStats s = stats.get(0);

        assertEquals("lock@F", s.getLockKey());
        assertEquals(2, s.getDistinctThreads());
        assertEquals(0.5, s.getJainIndex(), 1e-9);
        assertEquals(900, s.getTotalWaitNs());
        assertEquals(900, s.getMaxThreadWaitNs());
        assertEquals(0, s.getMinThreadWaitNs());
        assertEquals(0L, s.getPerThreadWaitNs().get(1L));
        assertEquals(900L, s.getPerThreadWaitNs().get(2L));
    }

    @Test
    void flagsStarvationAsWarning() {
        LockStateModel model = new LockStateModel(scenario());
        List<Finding> findings = new FairnessAnalyzer().analyze(model);

        assertEquals(1, findings.size());
        Finding f = findings.get(0);
        assertEquals("FAIRNESS", f.getCategory());
        // index 0.5 is < WARNING (0.8) but not < CRITICAL (0.5)
        assertEquals(Finding.Severity.WARNING, f.getSeverity());
        assertEquals(0.5, (double) f.getDetails().get("jainIndex"), 1e-9);
    }
}
