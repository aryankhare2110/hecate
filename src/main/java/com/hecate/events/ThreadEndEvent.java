package com.hecate.events;

public class ThreadEndEvent extends Event{

    public ThreadEndEvent(long timestamp, String threadName, long threadId) {
        super(timestamp, EventType.THREAD_END, threadName, threadId);
    }

    @Override
    public String toString() {
        return super.toString() + " – Thread ended";
    }

}
