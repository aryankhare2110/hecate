package com.hecate.analysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects deadlocks that were <em>actually happening</em> when the trace ended, by building
 * a wait-for graph from the end-of-trace state.
 *
 * <p>Where {@link DeadlockAnalyzer} (iGoodLock) <em>predicts</em> deadlocks from a run that
 * completed, this analyzer catches a run that hung: take a trace of a deadlocked program
 * (interrupt it so the shutdown hook still writes the trace) and the threads frozen
 * mid-acquisition form a cycle here.
 *
 * <p>Each blocked thread waits on exactly one lock, so the graph has out-degree at most one:
 * thread T (waiting on lock L held by thread U) yields edge {@code T -> U}. Any cycle in
 * that graph is a live deadlock.
 */
public class WaitForGraphAnalyzer implements Analyzer {

    @Override
    public String name() {
        return "Live Deadlock Analyzer";
    }

    /** Each detected live-deadlock cycle, as an ordered list of thread ids. */
    public List<List<Long>> computeCycles(LockStateModel model) {
        Map<Long, LockStateModel.BlockedThread> blocked = model.getBlockedThreads();
        Map<String, Long> owners = model.getLockOwnersAtEnd();

        // Build the wait-for edges: blocked thread -> thread holding the lock it wants.
        Map<Long, Long> waitsFor = new LinkedHashMap<>();
        for (LockStateModel.BlockedThread b : blocked.values()) {
            Long owner = owners.get(b.getLockKey());
            if (owner != null && owner != b.getThreadId()) {
                waitsFor.put(b.getThreadId(), owner);
            }
        }

        List<List<Long>> cycles = new ArrayList<>();
        Set<Long> settled = new HashSet<>(); // threads already assigned to a cycle or proven acyclic

        for (Long start : waitsFor.keySet()) {
            if (settled.contains(start)) {
                continue;
            }
            List<Long> path = new ArrayList<>();
            Set<Long> onPath = new HashSet<>();
            Long node = start;
            while (node != null && !settled.contains(node)) {
                if (onPath.contains(node)) {
                    // Found a cycle: the suffix of the path from `node` back to itself.
                    cycles.add(new ArrayList<>(path.subList(path.indexOf(node), path.size())));
                    break;
                }
                path.add(node);
                onPath.add(node);
                node = waitsFor.get(node);
            }
            settled.addAll(path);
        }
        return cycles;
    }

    @Override
    public List<Finding> analyze(LockStateModel model) {
        Map<Long, LockStateModel.BlockedThread> blocked = model.getBlockedThreads();
        List<Finding> findings = new ArrayList<>();

        for (List<Long> cycle : computeCycles(model)) {
            List<String> lines = new ArrayList<>();
            List<Map<String, Object>> steps = new ArrayList<>();
            for (int i = 0; i < cycle.size(); i++) {
                long tid = cycle.get(i);
                long ownerTid = cycle.get((i + 1) % cycle.size());
                LockStateModel.BlockedThread b = blocked.get(tid);
                String waitedLock = b.getLockKey();

                lines.add(String.format("%s is blocked on %s, held by %s",
                        b.getThreadName(), waitedLock, model.getThreadName(ownerTid)));

                Map<String, Object> step = new LinkedHashMap<>();
                step.put("threadId", tid);
                step.put("threadName", b.getThreadName());
                step.put("waitsForLock", waitedLock);
                step.put("lockClass", b.getLockClass());
                step.put("heldByThreadId", ownerTid);
                step.put("heldByThreadName", model.getThreadName(ownerTid));
                steps.add(step);
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("threadIds", new ArrayList<>(cycle));
            details.put("steps", steps);
            details.put("lines", lines);

            String summary = String.format("Live deadlock: %d threads are blocked in a cycle, each waiting on a lock another holds.",
                    cycle.size());
            findings.add(new Finding(Finding.Severity.CRITICAL, "DEADLOCK (LIVE)", summary, details));
        }
        return findings;
    }
}
