package com.hecate.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hecate.events.Event;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class EventExporter {

    private static final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static String exportToFile(List<Event> events, String filename) {
        try {
            Path outputDir = Paths.get("hecate-output");
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            Path filePath = outputDir.resolve(filename);
            File outputFile = filePath.toFile();
            mapper.writeValue(outputFile, events);
            System.out.println("[Hecate] Events exported to: " + filePath.toAbsolutePath());
            System.out.println("[Hecate] Total events: " + events.size());
            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.println("[Hecate] Error exporting events: " + e.getMessage());
            return null;
        }
    }

    public static String exportToTimestampedFile(List <Event> events) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"));
        String filename = "hecate-events-" + timestamp + ".json";
        return exportToFile(events, filename);
    }

    public static String exportToDefaultFile(List<Event> events) {
        return exportToFile(events, "hecate-events.json");
    }

}

/*
 * Notes
 * - Writes a list of events to hecate-output/<filename> as indented JSON via Jackson, creating
 *   the directory if needed. The agent calls exportToFile(events, "hecate-events.json") from
 *   its shutdown hook.
 * - The polymorphic type info on Event is what lets EventLoader read the concrete subtypes back.
 */

