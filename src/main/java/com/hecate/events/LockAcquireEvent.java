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
        return super.toString() + String.format(" –  Acquired lock %s (%s)", lockId, lockClass);
    }

}
