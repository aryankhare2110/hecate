package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A potential-deadlock cycle: an ordered chain of {@link LockDependency} tuples that
 * forms a closed wait-for loop. With dependencies d0..d(n-1), each thread holds the
 * lock acquired by the previous dependency and tries to acquire its own — the last
 * closing back to the first.
 */
public final class DeadlockCycle {

    private final List<LockDependency> chain;

    public DeadlockCycle(List<LockDependency> chain) {
        this.chain = Collections.unmodifiableList(new ArrayList<>(chain));
    }

    public List<LockDependency> getChain() {
        return chain;
    }

    /** The locks involved, in cycle order (each thread's acquired lock). */
    public List<String> getLockCycle() {
        List<String> locks = new ArrayList<>(chain.size());
        for (LockDependency d : chain) {
            locks.add(d.getLockKey());
        }
        return locks;
    }

    public Set<Long> getThreadIds() {
        Set<Long> threads = new LinkedHashSet<>();
        for (LockDependency d : chain) {
            threads.add(d.getThreadId());
        }
        return threads;
    }

    /**
     * Rotation-invariant identity, so the same cycle discovered from different starting
     * points is reported once. Built from the unordered set of (thread, lock) pairs.
     */
    public String signature() {
        Set<String> pairs = new TreeSet<>();
        for (LockDependency d : chain) {
            pairs.add(d.getThreadId() + ":" + d.getLockKey());
        }
        return String.join(",", pairs);
    }

    @Override
    public String toString() {
        return "DeadlockCycle" + chain;
    }
}
