package com.hecate.events;

import java.util.*;
import java.util.concurrent.*;

public class EventCollector {
    private static final EventCollector INSTANCE = new EventCollector();
    private final ConcurrentLinkedQueue<Event> events;
    private volatile boolean collecting;

    private EventCollector() {
        this.events = new ConcurrentLinkedQueue<>();
        this.collecting = false;
    }

    public static EventCollector getInstance() {
        return INSTANCE;
    }

    public void startCollecting() {
        collecting = true;
        System.out.println("[HECATE] - Collecting events...");
    }

    public void stopCollecting() {
        collecting = false;
        System.out.println("[HECATE] - Stopped collecting events.");
    }

    public void recordEvent(Event event) {
        if (collecting) {
            events.add(event);
        }
    }

    public List<Event> getEvents() {
        List <Event> eventList = new ArrayList<>(events);
        Collections.sort(eventList, (e1, e2) -> Long.compare(e1.getTimestamp(), e2.getTimestamp()));
        return eventList;
    }

    public void clear() {
        events.clear();
    }

    public long getEventCount() {
        return events.size();
    }

}

/*
 * Notes
 * - Process-wide singleton sink for events. Instrumented application threads call recordEvent
 *   concurrently, so the backing store is a lock-free ConcurrentLinkedQueue.
 * - collecting is a volatile on/off flag toggled by the agent's premain and shutdown hook;
 *   events recorded while it is false are dropped.
 * - getEvents() returns a timestamp-sorted copy, a stable snapshot for export and analysis.
 * - Limitation: the queue is unbounded and held entirely in memory until shutdown, so a very
 *   long or extremely lock-heavy run can make it large.
 */
