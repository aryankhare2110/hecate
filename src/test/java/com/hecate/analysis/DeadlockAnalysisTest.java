package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Known-answer tests for {@link DeadlockAnalyzer} (iGoodLock).
 *
 * The traces all run to completion with no actual deadlock — the detector's job is to
 * predict the latent one from lock ordering, and to NOT cry wolf when a gate lock or a
 * single thread makes the cycle benign.
 */
class DeadlockAnalysisTest {

    /** Helper: thread tid acquires `locks` in order (nested), then releases them in reverse. */
    private static void nest(List<Event> out, long baseTs, long tid, String tname, String... locks) {
        long t = baseTs;
        for (String lock : locks) {
            out.add(new LockAcquireEvent(t++, tid, tname, lock, lock));
        }
        for (int i = locks.length - 1; i >= 0; i--) {
            out.add(new LockReleaseEvent(t++, tid, tname, locks[i], locks[i], 1));
        }
    }

    @Test
    void detectsOppositeOrderDeadlock() {
        // T1: A then B. T2: B then A. Classic AB-BA deadlock potential.
        List<Event> events = new ArrayList<>();
        nest(events, 100, 1, "T1", "lock@A", "lock@B");
        nest(events, 200, 2, "T2", "lock@B", "lock@A");

        LockStateModel model = new LockStateModel(events);
        List<DeadlockCycle> cycles = new DeadlockAnalyzer().computeCycles(model);

        assertEquals(1, cycles.size(), "exactly one AB-BA cycle expected");
        DeadlockCycle c = cycles.get(0);
        assertEquals(2, c.getThreadIds().size());
        assertTrue(c.getLockCycle().contains("lock@A"));
        assertTrue(c.getLockCycle().contains("lock@B"));

        List<Finding> findings = new DeadlockAnalyzer().analyze(model);
        assertEquals(1, findings.size());
        assertEquals(Finding.Severity.CRITICAL, findings.get(0).getSeverity());
        assertEquals("DEADLOCK", findings.get(0).getCategory());
    }

    @Test
    void gateLockSuppressesFalsePositive() {
        // Both threads hold outer gate G across the AB / BA nesting -> G serializes them,
        // so there is NO real deadlock and nothing should be reported.
        List<Event> events = new ArrayList<>();
        nest(events, 100, 1, "T1", "lock@G", "lock@A", "lock@B");
        nest(events, 200, 2, "T2", "lock@G", "lock@B", "lock@A");

        LockStateModel model = new LockStateModel(events);
        assertTrue(new DeadlockAnalyzer().computeCycles(model).isEmpty(),
                "shared gate lock must suppress the cycle");
    }

    @Test
    void singleThreadOrderingIsNotADeadlock() {
        // One thread takes A->B and later B->A. A lock-order cycle exists, but a single
        // thread can't deadlock against itself, so it must not be reported.
        List<Event> events = new ArrayList<>();
        nest(events, 100, 1, "T1", "lock@A", "lock@B");
        nest(events, 200, 1, "T1", "lock@B", "lock@A");

        LockStateModel model = new LockStateModel(events);
        assertTrue(new DeadlockAnalyzer().computeCycles(model).isEmpty(),
                "single-thread lock order must not be flagged");
    }
}
