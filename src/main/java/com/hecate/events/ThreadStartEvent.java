package com.hecate.events;

public class ThreadStartEvent extends Event{

    public ThreadStartEvent(long timestamp, String threadName, long threadId) {
        super(timestamp, EventType.THREAD_START, threadName, threadId);
    }

    @Override
    public String toString() {
        return super.toString() + " - Thread started";
    }

}

/*
 * Notes
 * - Marks a thread starting. Part of the event model and round-trips through JSON, but the
 *   agent does not emit it yet, so it does not appear in captured traces today.
 */
