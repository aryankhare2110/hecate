package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

public class DeadlockAnalyzer implements Analyzer {

    @Override
    public String name() {
        return "Deadlock Analyzer";
    }

    public Set<LockDependency> buildDependencies(LockStateModel model) {
        Set<LockDependency> deps = new LinkedHashSet<>();
        for (LockAcquisition acq : model.getAcquisitions()) {
            if (acq.getHeldWhenAcquired().isEmpty()) {
                continue;
            }
            Set<String> held = new LinkedHashSet<>(acq.getHeldWhenAcquired());
            held.remove(acq.getLockKey());
            if (held.isEmpty()) {
                continue;
            }
            deps.add(new LockDependency(acq.getThreadId(), acq.getThreadName(),
                    acq.getLockKey(), acq.getLockClass(), held));
        }
        return deps;
    }

    public List<DeadlockCycle> computeCycles(LockStateModel model) {
        List<LockDependency> deps = new ArrayList<>(buildDependencies(model));
        List<DeadlockCycle> cycles = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (LockDependency start : deps) {
            List<LockDependency> chain = new ArrayList<>();
            chain.add(start);
            Set<Long> threadsUsed = new HashSet<>();
            threadsUsed.add(start.getThreadId());
            Set<String> locksUsed = new HashSet<>();
            locksUsed.add(start.getLockKey());
            Set<String> heldUnion = new HashSet<>(start.getHeldLocks());

            search(chain, threadsUsed, locksUsed, heldUnion, deps, seen, cycles);
        }
        return cycles;
    }

    private void search(List<LockDependency> chain, Set<Long> threadsUsed, Set<String> locksUsed,
                        Set<String> heldUnion, List<LockDependency> all, Set<String> seen,
                        List<DeadlockCycle> out) {
        LockDependency last = chain.get(chain.size() - 1);

        if (chain.size() >= 2 && chain.get(0).getHeldLocks().contains(last.getLockKey())) {
            DeadlockCycle cycle = new DeadlockCycle(chain);
            if (seen.add(cycle.signature())) {
                out.add(cycle);
            }
        }

        for (LockDependency d : all) {
            if (threadsUsed.contains(d.getThreadId())) {
                continue;
            }
            if (locksUsed.contains(d.getLockKey())) {
                continue;
            }
            if (!d.getHeldLocks().contains(last.getLockKey())) {
                continue;
            }
            if (!disjoint(heldUnion, d.getHeldLocks())) {
                continue;
            }

            chain.add(d);
            threadsUsed.add(d.getThreadId());
            locksUsed.add(d.getLockKey());
            heldUnion.addAll(d.getHeldLocks());

            search(chain, threadsUsed, locksUsed, heldUnion, all, seen, out);

            chain.remove(chain.size() - 1);
            threadsUsed.remove(d.getThreadId());
            locksUsed.remove(d.getLockKey());
            heldUnion.removeAll(d.getHeldLocks());
        }
    }

    private static boolean disjoint(Set<String> a, Set<String> b) {
        Set<String> smaller = a.size() <= b.size() ? a : b;
        Set<String> larger = smaller == a ? b : a;
        for (String s : smaller) {
            if (larger.contains(s)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<Finding> analyze(LockStateModel model) {
        List<Finding> findings = new ArrayList<>();
        for (DeadlockCycle cycle : computeCycles(model)) {
            List<LockDependency> chain = cycle.getChain();
            int n = chain.size();

            List<Map<String, Object>> steps = new ArrayList<>();
            List<String> readable = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LockDependency d = chain.get(i);
                String holdsLock = chain.get((i - 1 + n) % n).getLockKey();
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("threadId", d.getThreadId());
                step.put("threadName", d.getThreadName());
                step.put("holdsLock", holdsLock);
                step.put("acquiresLock", d.getLockKey());
                steps.add(step);
                readable.add(String.format("%s holds %s, wants %s", d.getThreadName(), holdsLock, d.getLockKey()));
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lockCycle", cycle.getLockCycle());
            details.put("threadIds", new ArrayList<>(cycle.getThreadIds()));
            details.put("steps", steps);
            details.put("lines", readable);

            String summary = String.format(
                    "Potential deadlock: a circular lock order across %d threads could deadlock under a different schedule.",
                    n);
            findings.add(new Finding(Finding.Severity.CRITICAL, "DEADLOCK", summary, details));
        }
        return Collections.unmodifiableList(findings);
    }
}

/*
 * Notes
 * - Predicts deadlocks a clean run never hit (the iGoodLock algorithm). Every nested
 *   acquisition becomes a LockDependency: (thread, lock, locks-already-held).
 * - buildDependencies drops self-dependencies (reentrancy): the acquired lock is removed from
 *   its own held set, so re-locking an already-held lock never forms an edge.
 * - computeCycles runs a backtracking search for a chain of dependencies that closes into a
 *   cycle, deduped by cycle.signature(). A chain closes when the first dependency holds the lock
 *   the last one acquired.
 * - Three filters applied while extending the chain remove the textbook false positives:
 *     - distinct threads: each dependency must come from a new thread,
 *     - connectivity: the next thread must already hold the lock just acquired,
 *     - gate locks: held sets along the chain stay pairwise disjoint (disjoint() vs heldUnion),
 *       so a shared outer lock that serializes the threads makes the cycle benign.
 * - analyze() turns each cycle into a CRITICAL Finding, with per-thread "lines" for the report.
 */
