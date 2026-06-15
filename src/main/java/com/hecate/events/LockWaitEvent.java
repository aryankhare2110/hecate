package com.hecate.events;

public class LockWaitEvent extends Event {

    private final String lockId;
    private final String lockClass;

    public LockWaitEvent(long timestamp, long threadId, String threadName, String lockId, String lockClass) {
        super(timestamp, EventType.LOCK_WAIT, threadName, threadId);
        this.lockId = lockId;
        this.lockClass = lockClass;
    }

    public String getLockId() {
        return lockId;
    }

    public String getLockClass() {
        return lockClass;
    }

    public String toString() {
        return super.toString() + String.format(" - Waiting for lock %s (%s)", lockId, lockClass);
    }

}

/*
 * Notes
 * - Emitted just before a thread attempts to take a lock (before MONITORENTER / Lock.lock()).
 * - lockId is identityHashCode-based ("lock@<hex>"); lockClass is the lock object's class.
 * - The gap between this WAIT and the matching ACQUIRE is the thread's blocking time, which the
 *   contention and fairness analyzers measure. A WAIT with no later ACQUIRE means the thread
 *   was still blocked at the end of the trace (used for live-deadlock detection).
 */
