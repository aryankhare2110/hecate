package com.hecate.events;

public class ThreadStartEvent extends Event{

    public ThreadStartEvent(long timestamp, String threadName, long threadId) {
        super(timestamp, EventType.THREAD_START, threadName, threadId);
    }

    @Override
    public String toString() {
        return super.toString() + " – Thread started";
    }

}
