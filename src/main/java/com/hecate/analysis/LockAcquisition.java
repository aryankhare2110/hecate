package com.hecate.analysis;

import java.util.Collections;
import java.util.List;

public final class LockAcquisition {

    private final String lockKey;
    private final String lockClass;
    private final long threadId;
    private final String threadName;
    private final long waitStartTs;
    private final long acquireTs;
    private final long releaseTs;
    private final long holdDuration;
    private final List<String> heldWhenAcquired;

    public LockAcquisition(String lockKey, String lockClass, long threadId, String threadName,
                           long waitStartTs, long acquireTs, long releaseTs, long holdDuration,
                           List<String> heldWhenAcquired) {
        this.lockKey = lockKey;
        this.lockClass = lockClass;
        this.threadId = threadId;
        this.threadName = threadName;
        this.waitStartTs = waitStartTs;
        this.acquireTs = acquireTs;
        this.releaseTs = releaseTs;
        this.holdDuration = holdDuration;
        this.heldWhenAcquired = Collections.unmodifiableList(heldWhenAcquired);
    }

    public String getLockKey() {
        return lockKey;
    }

    public String getLockClass() {
        return lockClass;
    }

    public long getThreadId() {
        return threadId;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getWaitStartTs() {
        return waitStartTs;
    }

    public long getAcquireTs() {
        return acquireTs;
    }

    public long getReleaseTs() {
        return releaseTs;
    }

    public long getHoldDuration() {
        return holdDuration;
    }

    public List<String> getHeldWhenAcquired() {
        return heldWhenAcquired;
    }

    public long getWaitDuration() {
        return waitStartTs >= 0 ? acquireTs - waitStartTs : 0;
    }

    public boolean isContended() {
        return getWaitDuration() > 0;
    }

    public boolean isReleased() {
        return releaseTs >= 0;
    }

    @Override
    public String toString() {
        return String.format("Acq[%s by %s/%d wait=%dns hold=%dns]",
                lockKey, threadName, threadId, getWaitDuration(), holdDuration);
    }
}

/*
 * Notes
 * - One reconstructed lock-hold interval: a single ACQUIRE paired with its matching WAIT (if
 *   any) and RELEASE (if observed). The atomic unit every analyzer consumes, produced by
 *   LockStateModel.
 * - waitStartTs is -1 when no matching WAIT was seen; releaseTs is -1 when the lock was still
 *   held at end of trace, in which case holdDuration is a lower-bound estimate.
 * - heldWhenAcquired lists the locks this thread already held at the moment of acquisition
 *   (outermost first), which the deadlock analyzer turns into lock-order dependencies.
 * - Derived views: getWaitDuration(), isContended() (waited a non-zero amount), isReleased().
 */
