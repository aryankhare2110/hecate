package com.hecate.events;

public class LockAcquireEvent extends Event {

    private final String lockId;
    private final String lockClass;

    public LockAcquireEvent(long timestamp, long threadId, String threadName, String lockId, String lockClass) {
        super(timestamp, EventType.LOCK_ACQUIRE, threadName, threadId);
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
        return super.toString() + String.format(" - Acquired lock %s (%s)", lockId, lockClass);
    }

}

/*
 * Notes
 * - Emitted the moment a thread actually holds the lock (after MONITORENTER, or after a
 *   successful Lock.lock() / tryLock()).
 * - Paired with the preceding WAIT (to measure blocking time) and the following RELEASE (to
 *   measure hold time) when LockStateModel reconstructs the timeline.
 */
