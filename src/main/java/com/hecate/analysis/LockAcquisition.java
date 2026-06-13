package com.hecate.analysis;

import java.util.Collections;
import java.util.List;

/**
 * One reconstructed lock-hold interval: a single ACQUIRE paired with its matching
 * WAIT (if any) and RELEASE (if observed). This is the atomic unit every analyzer
 * consumes — produced once by {@link LockStateModel}.
 */
public final class LockAcquisition {

    private final String lockKey;
    private final String lockClass;
    private final long threadId;
    private final String threadName;
    private final long waitStartTs;   // -1 if no matching WAIT was observed
    private final long acquireTs;
    private final long releaseTs;     // -1 if the lock was still held at end of trace
    private final long holdDuration;  // nanoseconds, from the RELEASE event; 0 if unreleased
    private final List<String> heldWhenAcquired; // lock keys this thread already held (for lock-order graph)

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

    /** Lock keys already held by this thread at the moment of acquisition (outermost first). */
    public List<String> getHeldWhenAcquired() {
        return heldWhenAcquired;
    }

    /** Time spent blocked before acquiring, or 0 if no WAIT was observed. */
    public long getWaitDuration() {
        return waitStartTs >= 0 ? acquireTs - waitStartTs : 0;
    }

    /** True if the thread actually blocked (waited a non-zero amount) before acquiring. */
    public boolean isContended() {
        return getWaitDuration() > 0;
    }

    /** True if a matching RELEASE was observed within the trace. */
    public boolean isReleased() {
        return releaseTs >= 0;
    }

    @Override
    public String toString() {
        return String.format("Acq[%s by %s/%d wait=%dns hold=%dns]",
                lockKey, threadName, threadId, getWaitDuration(), holdDuration);
    }
}
