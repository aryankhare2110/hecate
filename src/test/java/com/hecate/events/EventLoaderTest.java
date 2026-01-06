package com.hecate.events;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventLoaderTest {

    @Test
    void testExportAndLoad() throws Exception {
        List<Event> originalEvents = List.of(
                new ThreadStartEvent(1000, "test-thread", 1),
                new LockWaitEvent(2000, 1, "test-thread", "lock@abc", "Object"),
                new LockAcquireEvent(3000, 1, "test-thread", "lock@abc", "Object"),
                new LockReleaseEvent(4000, 1, "test-thread", "lock@abc", "Object", 1000),
                new ThreadEndEvent(5000, "test-thread", 1)
        );

        String filename = "test-events.json";
        String filePath = com.hecate.events.EventExporter.exportToFile(originalEvents, filename);
        assertNotNull(filePath);

        List<Event> loadedEvents = EventLoader.loadFromFile(filename);

        assertEquals(originalEvents.size(), loadedEvents.size());

        System.out. println("\n===== Loaded Events =====");
        for (int i = 0; i < loadedEvents.size(); i++) {
            Event e = loadedEvents.get(i);
            System.out.println("Index " + i + ":  " + e.getClass().getSimpleName() +
                    " - " + e.getEventType() + " at " + e.getTimestamp());
        }
        System.out.println("=========================\n");

        Event first = loadedEvents.get(0);
        assertEquals(EventType.THREAD_START, first.getEventType());
        assertTrue(first instanceof ThreadStartEvent, "Index 0 should be ThreadStartEvent");
        assertEquals(1000, first.getTimestamp());
        assertEquals(1, first.getThreadId());
        assertEquals("test-thread", first. getThreadName());

        Event second = loadedEvents.get(1);
        assertEquals(EventType. LOCK_WAIT, second. getEventType());
        assertTrue(second instanceof LockWaitEvent, "Index 1 should be LockWaitEvent");
        LockWaitEvent waitEvent = (LockWaitEvent) second;
        assertEquals(2000, waitEvent.getTimestamp());
        assertEquals("lock@abc", waitEvent.getLockId());
        assertEquals("Object", waitEvent.getLockClass());

        Event third = loadedEvents.get(2);
        assertEquals(EventType.LOCK_ACQUIRE, third.getEventType());
        assertTrue(third instanceof LockAcquireEvent, "Index 2 should be LockAcquireEvent");
        LockAcquireEvent acquireEvent = (LockAcquireEvent) third;
        assertEquals(3000, acquireEvent.getTimestamp());
        assertEquals("lock@abc", acquireEvent.getLockId());

        Event fourth = loadedEvents.get(3);
        assertEquals(EventType.LOCK_RELEASE, fourth.getEventType());
        assertTrue(fourth instanceof LockReleaseEvent, "Index 3 should be LockReleaseEvent");
        LockReleaseEvent releaseEvent = (LockReleaseEvent) fourth;
        assertEquals(4000, releaseEvent.getTimestamp());
        assertEquals(1000, releaseEvent.getHoldDuration());
        Event last = loadedEvents.get(4);
        assertEquals(EventType. THREAD_END, last.getEventType());
        assertTrue(last instanceof ThreadEndEvent, "Index 4 should be ThreadEndEvent");
        assertEquals(5000, last.getTimestamp());

        System.out.println("✓ Export/Load round-trip successful!");
    }

    @Test
    void testLoadMultipleThreads() throws Exception {
        List<Event> originalEvents = List.of(
                new ThreadStartEvent(1000, "Thread-1", 1),
                new ThreadStartEvent(1100, "Thread-2",2),
                new LockWaitEvent(2000, 1, "Thread-1", "lock@xyz", "BankAccount"),
                new LockAcquireEvent(2100, 1, "Thread-1", "lock@xyz", "BankAccount"),
                new LockWaitEvent(2500, 2, "Thread-2", "lock@xyz", "BankAccount"),
                new LockReleaseEvent(3000, 1, "Thread-1", "lock@xyz", "BankAccount", 900),
                new LockAcquireEvent(3100, 2, "Thread-2", "lock@xyz", "BankAccount"),
                new LockReleaseEvent(4000, 2, "Thread-2", "lock@xyz", "BankAccount", 900),
                new ThreadEndEvent(5000, "Thread-1", 1),
                new ThreadEndEvent(5100, "Thread-2", 2)
        );

        String filename = "test-events-multithread.json";
        com.hecate.events.EventExporter.exportToFile(originalEvents, filename);

        List<Event> loadedEvents = EventLoader.loadAndSummarize(filename);

        assertEquals(10, loadedEvents.size());

        long thread1Count = loadedEvents.stream()
                .filter(e -> e.getThreadId() == 1)
                .count();
        long thread2Count = loadedEvents.stream()
                .filter(e -> e.getThreadId() == 2)
                .count();

        assertEquals(5, thread1Count, "Should have 5 events for Thread-1");
        assertEquals(5, thread2Count, "Should have 5 events for Thread-2");

        System.out.println("✓ Multi-thread export/load successful!");
    }

    @Test
    void testFileNotFound() {
        assertThrows(java.io.IOException.class, () -> {
            EventLoader.loadFromFile("non-existent-file.json");
        }, "Should throw IOException for missing file");

        System.out.println("✓ File not found handling works!");
    }
}