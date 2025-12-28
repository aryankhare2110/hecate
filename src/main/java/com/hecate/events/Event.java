package com.hecate.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ThreadStartEvent.class, name = "THREAD_START"),
        @JsonSubTypes.Type(value = ThreadEndEvent.class, name = "THREAD_END"),
        @JsonSubTypes.Type(value = LockAcquireEvent.class, name = "LOCK_ACQUIRE"),
        @JsonSubTypes.Type(value = LockReleaseEvent.class, name = "LOCK_RELEASE"),
        @JsonSubTypes.Type(value = LockWaitEvent.class, name = "LOCK_WAIT")
})

public class Event {
    private final long timestamp;
    private final EventType eventType;
    private final String threadName;
    private final long threadId;

    protected Event(long timestamp, EventType eventType, String threadName, long threadId) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.threadName = threadName;
        this.threadId = threadId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getThreadId() {
        return threadId;
    }

    public String toString() {
        return String.format("[%d] %s (Thread: %s/%d)", timestamp, eventType, threadName, threadId);
    }
}
