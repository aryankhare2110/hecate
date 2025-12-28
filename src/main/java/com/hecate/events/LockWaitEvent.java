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
        return super.toString() + String.format(" – Waiting for lock %s (%s)", lockId, lockClass);
    }

}
