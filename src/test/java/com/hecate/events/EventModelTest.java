package com.hecate.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class EventModelTest {

    @BeforeEach
    void setUp() {
        EventCollector.getInstance().clear();
    }

    @Test
    void testThreadStartEvent() {
        long now = System.nanoTime();
        ThreadStartEvent event = new ThreadStartEvent(now, "main", 1);
        assertEquals(now, event.getTimestamp());
        assertEquals(1, event.getThreadId());
        assertEquals("main", event.getThreadName());
        assertEquals(EventType.THREAD_START, event.getEventType());
        System.out.println("ThreadStartEvent:  " + event);
    }

    @Test
    void testLockAcquireEvent() {
        long now = System.nanoTime();
        LockAcquireEvent event = new LockAcquireEvent(now, 42, "worker-1", "lock@12345", "java.lang.Object");
        assertEquals("lock@12345", event.getLockId());
        assertEquals("java.lang.Object", event.getLockClass());
        assertEquals(EventType.LOCK_ACQUIRE, event.getEventType());
        System.out.println("LockAcquireEvent: " + event);
    }

    @Test
    void testLockReleaseEvent() {
        long now = System.nanoTime();
        LockReleaseEvent event = new LockReleaseEvent(now, 42, "worker-1", "lock@12345", "java.lang.Object", 500000);
        assertEquals(500000, event.getHoldDuration());
        System.out.println("LockReleaseEvent: " + event);
    }

    @Test
    void testEventCollector() {
        EventCollector collector = EventCollector. getInstance();
        assertEquals(0, collector.getEventCount());
        collector.startCollecting();
        long now = System.nanoTime();
        collector.recordEvent(new ThreadStartEvent(now, "test-thread", 1));
        collector.recordEvent(new LockWaitEvent(now + 1000, 1, "test-thread", "lock@1", "Object"));
        collector.recordEvent(new LockAcquireEvent(now + 2000, 1, "test-thread", "lock@1", "Object"));
        collector.recordEvent(new ThreadEndEvent(now + 3000, "test-thread", 1));
        assertEquals(4, collector.getEventCount());
        collector.stopCollecting();
        var events = collector.getEvents();
        assertEquals(4, events.size());
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).getTimestamp() >= events.get(i-1).getTimestamp());
        }
        System.out.println("Collected events:");
        for (Event e : events) {
            System.out.println("  " + e);
        }
    }

    @Test
    void testEventCollectorSingleton() {
        EventCollector c1 = EventCollector.getInstance();
        EventCollector c2 = EventCollector.getInstance();
        assertSame(c1, c2);
        System.out.println("Singleton pattern verified!");
    }
}