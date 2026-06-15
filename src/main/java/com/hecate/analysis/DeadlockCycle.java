package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class DeadlockCycle {

    private final List<LockDependency> chain;

    public DeadlockCycle(List<LockDependency> chain) {
        this.chain = Collections.unmodifiableList(new ArrayList<>(chain));
    }

    public List<LockDependency> getChain() {
        return chain;
    }

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

/*
 * Notes
 * - A potential-deadlock cycle: an ordered chain of LockDependency tuples forming a closed
 *   wait-for loop, where each thread holds the lock acquired by the previous one and the last
 *   closes back to the first.
 * - signature() is a rotation-invariant identity (the sorted set of thread:lock pairs), so the
 *   same cycle found from different starting points is reported only once.
 */
