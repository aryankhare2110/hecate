package com.hecate.analysis;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A single observed lock-ordering fact: thread {@code threadId} acquired {@code lockKey}
 * while already holding the locks in {@code heldLocks}. This is the unit the iGoodLock
 * deadlock search chains together — equivalent to the dependency tuple {@code (t, l, L)}
 * in the literature.
 *
 * Two dependencies are equal when thread, acquired lock, and held set all match, so
 * repeated identical nestings collapse to one.
 */
public final class LockDependency {

    private final long threadId;
    private final String threadName;
    private final String lockKey;
    private final String lockClass;
    private final Set<String> heldLocks;

    public LockDependency(long threadId, String threadName, String lockKey, String lockClass,
                          Set<String> heldLocks) {
        this.threadId = threadId;
        this.threadName = threadName;
        this.lockKey = lockKey;
        this.lockClass = lockClass;
        this.heldLocks = Collections.unmodifiableSet(new LinkedHashSet<>(heldLocks));
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockClass() {
        return lockClass;
    }

    /** Locks this thread already held when it acquired {@link #getLockKey()}. */
    public Set<String> getHeldLocks() {
        return heldLocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LockDependency)) {
            return false;
        }
        LockDependency that = (LockDependency) o;
        return threadId == that.threadId
                && lockKey.equals(that.lockKey)
                && heldLocks.equals(that.heldLocks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threadId, lockKey, heldLocks);
    }

    @Override
    public String toString() {
        return String.format("(%s/%d acquires %s holding %s)", threadName, threadId, lockKey, heldLocks);
    }
}
