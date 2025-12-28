package com.hecate.events;

public class LockReleaseEvent extends Event{

    private final String lockId;
    private final String lockClass;
    private final long holdDuration;

    public LockReleaseEvent(long timestamp, long threadId, String threadName, String lockId, String lockClass, long holdDuration) {
        super(timestamp, EventType.LOCK_WAIT, threadName, threadId);
        this.lockId = lockId;
        this.lockClass = lockClass;
        this.holdDuration = holdDuration;
    }

    public String getLockId() {
        return lockId;
    }

    public String getLockClass() {
        return lockClass;
    }

    public long getHoldDuration() {
        return holdDuration;
    }

    public String toString() {
        return super.toString() + String.format(" –  Released lock %s (%s) held for %d ns", lockId, lockClass, holdDuration);
    }
}
