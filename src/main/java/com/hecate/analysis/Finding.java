package com.hecate.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Finding {

    public enum Severity {
        INFO, WARNING, CRITICAL
    }

    private final Severity severity;
    private final String category;
    private final String summary;
    private final Map<String, Object> details;

    public Finding(Severity severity, String category, String summary, Map<String, Object> details) {
        this.severity = severity;
        this.category = category;
        this.summary = summary;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", severity, category, summary);
    }
}

/*
 * Notes
 * - A single result from an Analyzer: a Severity, a category tag, a human-readable summary, and
 *   a structured details map.
 * - The details map feeds the JSON report and may carry a "lines" list, which the text report
 *   renders as indented sub-bullets (used for deadlock cycle steps).
 */
