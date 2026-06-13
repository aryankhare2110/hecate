package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * Predicts deadlocks the run never actually hit, using the iGoodLock algorithm.
 *
 * <p>Every nested acquisition becomes a {@link LockDependency} {@code (thread, lock, held)}.
 * The analyzer then searches for a chain of dependencies that closes into a cycle — e.g.
 * one thread takes A then B while another takes B then A. Such opposite orderings can
 * deadlock under the right interleaving even if this particular execution serialized
 * cleanly, which is exactly what makes the detector valuable as a testing tool.
 *
 * <p>Three filters keep the result free of the classic false positives:
 * <ul>
 *   <li><b>Reentrancy</b> — a lock is removed from its own held set, so re-locking an
 *       already-held monitor never forms an edge.</li>
 *   <li><b>Distinct threads</b> — every dependency in a cycle must come from a different
 *       thread; a single thread's lock order can't deadlock against itself.</li>
 *   <li><b>Gate locks</b> — the held sets along the cycle must be pairwise disjoint. If
 *       two threads share an outer "gate" lock, that lock serializes them and the cycle
 *       is benign.</li>
 * </ul>
 */
public class DeadlockAnalyzer implements Analyzer {

    @Override
    public String name() {
        return "Deadlock Analyzer";
    }

    /** Builds the deduplicated lock-dependency set from the model. */
    public Set<LockDependency> buildDependencies(LockStateModel model) {
        Set<LockDependency> deps = new LinkedHashSet<>();
        for (LockAcquisition acq : model.getAcquisitions()) {
            if (acq.getHeldWhenAcquired().isEmpty()) {
                continue;
            }
            // Reentrancy filter: a lock can't depend on itself.
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

    /** All distinct potential-deadlock cycles in the trace. */
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

        // Closing condition: the first dependency holds the lock the last one acquired,
        // completing the wait-for loop. Distinctness and gate-disjointness are already
        // guaranteed incrementally, so any closed chain of length >= 2 is a real cycle.
        if (chain.size() >= 2 && chain.get(0).getHeldLocks().contains(last.getLockKey())) {
            DeadlockCycle cycle = new DeadlockCycle(chain);
            if (seen.add(cycle.signature())) {
                out.add(cycle);
            }
        }

        for (LockDependency d : all) {
            if (threadsUsed.contains(d.getThreadId())) {
                continue; // distinct-thread filter
            }
            if (locksUsed.contains(d.getLockKey())) {
                continue; // keep cycles simple
            }
            if (!d.getHeldLocks().contains(last.getLockKey())) {
                continue; // connectivity: the next thread must hold the lock we just acquired
            }
            if (!disjoint(heldUnion, d.getHeldLocks())) {
                continue; // gate-lock filter: a shared held lock serializes the threads
            }

            chain.add(d);
            threadsUsed.add(d.getThreadId());
            locksUsed.add(d.getLockKey());
            // Held sets along the chain are pairwise disjoint, so these elements are new.
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
                readable.add(String.format("%s holds %s wants %s", d.getThreadName(), holdsLock, d.getLockKey()));
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("lockCycle", cycle.getLockCycle());
            details.put("threadIds", new ArrayList<>(cycle.getThreadIds()));
            details.put("steps", steps);

            String summary = "Potential deadlock — circular lock order: " + String.join("; ", readable);
            findings.add(new Finding(Finding.Severity.CRITICAL, "DEADLOCK", summary, details));
        }
        return Collections.unmodifiableList(findings);
    }
}
