package com.hecate.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.hecate.events.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EventLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<Event> loadFromFile(String filename) throws IOException {
        Path filePath = Paths.get("hecate-output").resolve(filename);
        File inputFile = filePath.toFile();
        if (!inputFile.exists()) {
            throw new IOException("File not found:  " + filePath.toAbsolutePath());
        }
        return loadFromFile(inputFile);
    }

    public static List<Event> loadFromFile(File file) throws IOException {
        JsonNode rootNode = mapper.readTree(file);
        if (!rootNode.isArray()) {
            throw new IOException("Expected JSON array of events, got: " + rootNode.getNodeType());
        }
        List<Event> events = new ArrayList<>();
        int index = 0;
        for (JsonNode eventNode : rootNode) {
            Event event = deserializeEvent(eventNode, index);
            if (event != null) {
                events.add(event);
            }
            index++;
        }
        System.out.println("[Hecate] Loaded " + events.size() + " events from: " + file.getAbsolutePath());
        return events;
    }

    private static Event deserializeEvent(JsonNode node, int index) {
        try {
            JsonNode eventTypeNode = node.get("eventType");
            if (eventTypeNode == null) {
                throw new IllegalArgumentException("Missing required field: eventType");
            }
            EventType eventType = EventType.valueOf(eventTypeNode. asText());

            JsonNode timestampNode = node.get("timestamp");
            if (timestampNode == null) {
                throw new IllegalArgumentException("Missing required field: timestamp");
            }
            long timestamp = timestampNode.asLong();

            JsonNode threadIdNode = node.get("threadId");
            if (threadIdNode == null) {
                throw new IllegalArgumentException("Missing required field: threadId");
            }
            long threadId = threadIdNode.asLong();

            JsonNode threadNameNode = node.get("threadName");
            if (threadNameNode == null) {
                throw new IllegalArgumentException("Missing required field: threadName");
            }
            String threadName = threadNameNode.asText();

            switch (eventType) {
                case THREAD_START:
                    return new ThreadStartEvent(timestamp, threadName, threadId);

                case THREAD_END:
                    return new ThreadEndEvent(timestamp, threadName, threadId);

                case LOCK_WAIT:
                    String waitLockId = getRequiredField(node, "lockId").asText();
                    String waitLockClass = getRequiredField(node, "lockClass").asText();
                    return new LockWaitEvent(timestamp, threadId, threadName, waitLockId, waitLockClass);

                case LOCK_ACQUIRE:
                    String acquireLockId = getRequiredField(node, "lockId").asText();
                    String acquireLockClass = getRequiredField(node, "lockClass").asText();
                    return new LockAcquireEvent(timestamp, threadId, threadName, acquireLockId, acquireLockClass);

                case LOCK_RELEASE:
                    String releaseLockId = getRequiredField(node, "lockId").asText();
                    String releaseLockClass = getRequiredField(node, "lockClass").asText();
                    long holdDuration = getRequiredField(node, "holdDuration").asLong();
                    return new LockReleaseEvent(timestamp, threadId, threadName, releaseLockId, releaseLockClass, holdDuration);

                default:
                    System.err.println("[Hecate] Unknown event type at index " + index + ": " + eventType);
                    return null;
            }

        } catch (Exception e) {
            System. err.println("[Hecate] Error deserializing event at index " + index + ": " + e.getMessage());
            System. err.println("[Hecate] Problematic JSON:  " + node.toString());
            return null;
        }
    }

    private static JsonNode getRequiredField(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
        return field;
    }

    public static List<Event> loadAndSummarize(String filename) throws IOException {
        List<Event> events = loadFromFile(filename);

        long threadStartCount = events.stream().filter(e -> e.getEventType() == EventType.THREAD_START).count();
        long threadEndCount = events.stream().filter(e -> e.getEventType() == EventType.THREAD_END).count();
        long lockWaitCount = events.stream().filter(e -> e.getEventType() == EventType.LOCK_WAIT).count();
        long lockAcquireCount = events.stream().filter(e -> e.getEventType() == EventType.LOCK_ACQUIRE).count();
        long lockReleaseCount = events.stream().filter(e -> e.getEventType() == EventType.LOCK_RELEASE).count();

        System.out.println("\n========== Event Summary ==========");
        System.out.println("Total Events: " + events.size());
        System.out.println("  THREAD_START:   " + threadStartCount);
        System.out.println("  THREAD_END:     " + threadEndCount);
        System.out.println("  LOCK_WAIT:      " + lockWaitCount);
        System.out.println("  LOCK_ACQUIRE:   " + lockAcquireCount);
        System.out.println("  LOCK_RELEASE:   " + lockReleaseCount);
        System.out.println("===================================\n");

        return events;
    }
}

/*
 * Notes
 * - Reads a JSON event array back into Event objects. loadFromFile(String) resolves the name
 *   under hecate-output/; loadFromFile(File) reads any path.
 * - deserializeEvent is a hand-rolled, lenient parser: it keys off the "eventType" field,
 *   checks the required fields per type, and skips (logs and returns null for) any malformed
 *   entry rather than failing the whole load.
 * - loadAndSummarize also prints per-type counts for a quick overview of a trace.
 */
