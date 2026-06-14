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
 * End-to-end test for {@link AnalysisEngine}: the default analyzer set run over an
 * AB-BA trace must surface a CRITICAL deadlock finding and render a report.
 */
class AnalysisEngineTest {

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
    void defaultEngineSurfacesDeadlock() {
        List<Event> events = new ArrayList<>();
        nest(events, 100, 1, "T1", "lock@A", "lock@B");
        nest(events, 200, 2, "T2", "lock@B", "lock@A");

        LockStateModel model = new LockStateModel(events);
        AnalysisReport report = AnalysisEngine.withDefaults().run(model);

        assertTrue(report.countBySeverity(Finding.Severity.CRITICAL) >= 1,
                "AB-BA trace must produce a CRITICAL deadlock finding");

        boolean hasDeadlock = report.getFindings().stream()
                .anyMatch(f -> "DEADLOCK".equals(f.getCategory())
                        && f.getSeverity() == Finding.Severity.CRITICAL);
        assertTrue(hasDeadlock);

        // Most-severe-first ordering: the first finding is the CRITICAL deadlock.
        assertEquals(Finding.Severity.CRITICAL, report.getFindings().get(0).getSeverity());

        String text = report.render();
        assertTrue(text.contains("Hecate Concurrency Analysis"));
        assertTrue(text.contains("DEADLOCK"));
    }
}
