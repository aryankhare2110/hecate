package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import com.hecate.events.LockWaitEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Known-answer tests for {@link WaitForGraphAnalyzer} (live deadlock detection).
 *
 * These traces are "hung": each thread acquires its first lock and then issues a WAIT for a
 * second lock that never completes (no RELEASE events), exactly what an interrupted
 * deadlocked program produces.
 */
class WaitForGraphAnalysisTest {

    /** Thread tid acquires `held` (uncontended), then blocks waiting for `wanted`. */
    private static void hold(List<Event> out, long ts, long tid, String tname, String held, String wanted) {
        out.add(new LockWaitEvent(ts, tid, tname, held, held));
        out.add(new LockAcquireEvent(ts + 1, tid, tname, held, held));
        out.add(new LockWaitEvent(ts + 2, tid, tname, wanted, wanted)); // blocks here, never acquires
    }

    @Test
    void detectsThreeThreadLiveDeadlock() {
        // T1 holds A waits B, T2 holds B waits C, T3 holds C waits A.
        List<Event> events = new ArrayList<>();
        hold(events, 100, 1, "T1", "lock@A", "lock@B");
        hold(events, 110, 2, "T2", "lock@B", "lock@C");
        hold(events, 120, 3, "T3", "lock@C", "lock@A");

        LockStateModel model = new LockStateModel(events);
        List<List<Long>> cycles = new WaitForGraphAnalyzer().computeCycles(model);

        assertEquals(1, cycles.size());
        assertEquals(3, cycles.get(0).size());

        List<Finding> findings = new WaitForGraphAnalyzer().analyze(model);
        assertEquals(1, findings.size());
        assertEquals(Finding.Severity.CRITICAL, findings.get(0).getSeverity());
        assertEquals("DEADLOCK (LIVE)", findings.get(0).getCategory());
    }

    @Test
    void detectsTwoIndependentCycles() {
        // {T1<->? } cycle A/B/C plus a separate D/E cycle, like the 5-thread demo.
        List<Event> events = new ArrayList<>();
        hold(events, 100, 1, "T1", "lock@A", "lock@B");
        hold(events, 110, 2, "T2", "lock@B", "lock@C");
        hold(events, 120, 3, "T3", "lock@C", "lock@A");
        hold(events, 130, 4, "T4", "lock@D", "lock@E");
        hold(events, 140, 5, "T5", "lock@E", "lock@D");

        LockStateModel model = new LockStateModel(events);
        List<List<Long>> cycles = new WaitForGraphAnalyzer().computeCycles(model);

        assertEquals(2, cycles.size(), "two independent deadlock cycles expected");
        assertEquals(2, new WaitForGraphAnalyzer().analyze(model).size());
    }

    @Test
    void completedRunHasNoLiveDeadlock() {
        // Every lock acquired and released: nothing is blocked at the end.
        List<Event> events = new ArrayList<>();
        events.add(new LockAcquireEvent(100, 1, "T1", "lock@A", "lock@A"));
        events.add(new LockReleaseEvent(110, 1, "T1", "lock@A", "lock@A", 10));
        events.add(new LockAcquireEvent(120, 2, "T2", "lock@B", "lock@B"));
        events.add(new LockReleaseEvent(130, 2, "T2", "lock@B", "lock@B", 10));

        LockStateModel model = new LockStateModel(events);
        assertTrue(model.getBlockedThreads().isEmpty());
        assertTrue(new WaitForGraphAnalyzer().computeCycles(model).isEmpty());
    }
}
