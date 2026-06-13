package com.hecate.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A single result produced by an {@link Analyzer}: a severity-tagged observation with
 * a human-readable summary and a structured details map (the latter feeds JSON reports).
 */
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
