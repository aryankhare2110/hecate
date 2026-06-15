package com.hecate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hecate.analysis.AnalysisEngine;
import com.hecate.analysis.AnalysisReport;
import com.hecate.analysis.LockStateModel;
import com.hecate.events.Event;
import com.hecate.util.EventLoader;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

public class Hecate {

    private static final String DEFAULT_TRACE = "hecate-events.json";

    public static void main(String[] args) {
        String traceArg = DEFAULT_TRACE;
        String jsonOut = null;

        for (int i = 0; i < args.length; i++) {
            if ("--json".equals(args[i]) && i + 1 < args.length) {
                jsonOut = args[++i];
            } else if (!args[i].startsWith("--")) {
                traceArg = args[i];
            }
        }

        List<Event> events;
        try {
            File direct = new File(traceArg);
            events = direct.isFile() ? EventLoader.loadFromFile(direct) : EventLoader.loadFromFile(traceArg);
        } catch (Exception e) {
            System.err.println("[Hecate] Could not load trace '" + traceArg + "': " + e.getMessage());
            System.err.println("[Hecate] Pass a trace file, or run the agent first to produce one.");
            return;
        }

        if (events.isEmpty()) {
            System.out.println("[Hecate] Trace contains no events, nothing to analyze.");
            return;
        }

        LockStateModel model = new LockStateModel(events);
        AnalysisReport report = AnalysisEngine.withDefaults().run(model);
        System.out.println(report.render());

        if (jsonOut != null) {
            writeJson(report, jsonOut);
        }
    }

    private static void writeJson(AnalysisReport report, String filename) {
        try {
            File out = filename.contains(File.separator)
                    ? new File(filename)
                    : Paths.get("hecate-output").resolve(filename).toFile();
            if (out.getParentFile() != null) {
                out.getParentFile().mkdirs();
            }
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(out, report);
            System.out.println("[Hecate] Report written to: " + out.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[Hecate] Failed to write JSON report: " + e.getMessage());
        }
    }
}

/*
 * Notes
 * - Command-line entry point for the offline analyzer:
 *     java -cp hecate.jar com.hecate.Hecate [traceFile] [--json <outFile>]
 * - traceFile is a direct path or a name resolved under hecate-output/ (default
 *   hecate-events.json, which the agent writes on shutdown). --json also writes the structured
 *   report.
 * - Builds a LockStateModel from the loaded events and runs AnalysisEngine.withDefaults(),
 *   printing the rendered report.
 */
