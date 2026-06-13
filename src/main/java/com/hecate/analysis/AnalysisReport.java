package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The combined output of an {@link AnalysisEngine} run: a trace-level summary plus every
 * {@link Finding} the registered analyzers produced. Serializes cleanly to JSON (via its
 * getters) and renders to a console-friendly report.
 */
public final class AnalysisReport {

    private final Map<String, Object> summary;
    private final List<Finding> findings;

    public AnalysisReport(LockStateModel model, List<Finding> findings) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("events", model.getEvents().size());
        s.put("locks", model.getLockKeys().size());
        s.put("threads", model.getThreadIds().size());
        s.put("acquisitions", model.getAcquisitions().size());
        s.put("findings", findings.size());
        this.summary = Collections.unmodifiableMap(s);

        List<Finding> sorted = new ArrayList<>(findings);
        // Most severe first; analyzers contribute in registration order within a severity.
        sorted.sort(Comparator.comparingInt((Finding f) -> severityRank(f.getSeverity())));
        this.findings = Collections.unmodifiableList(sorted);
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public long countBySeverity(Finding.Severity severity) {
        return findings.stream().filter(f -> f.getSeverity() == severity).count();
    }

    private static int severityRank(Finding.Severity s) {
        switch (s) {
            case CRITICAL: return 0;
            case WARNING:  return 1;
            default:       return 2;
        }
    }

    /** Human-readable report for console output. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== Hecate Analysis Report ==========\n");
        sb.append(String.format("Events: %s   Locks: %s   Threads: %s   Acquisitions: %s%n",
                summary.get("events"), summary.get("locks"),
                summary.get("threads"), summary.get("acquisitions")));
        sb.append(String.format("Findings: %d  (CRITICAL %d, WARNING %d, INFO %d)%n",
                findings.size(),
                countBySeverity(Finding.Severity.CRITICAL),
                countBySeverity(Finding.Severity.WARNING),
                countBySeverity(Finding.Severity.INFO)));
        sb.append("--------------------------------------------\n");

        if (findings.isEmpty()) {
            sb.append("No concurrency issues detected.\n");
        } else {
            for (Finding f : findings) {
                sb.append(String.format("[%-8s] %-10s %s%n",
                        f.getSeverity(), f.getCategory(), f.getSummary()));
            }
        }
        sb.append("============================================\n");
        return sb.toString();
    }
}
