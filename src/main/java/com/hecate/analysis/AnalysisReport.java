package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public String render() {
        String bar = repeat('=', 64);
        String rule = repeat('-', 64);
        StringBuilder sb = new StringBuilder();

        sb.append('\n').append(bar).append('\n');
        sb.append("  Hecate Concurrency Analysis").append('\n');
        sb.append(bar).append('\n');
        sb.append(String.format("  Trace      %s events, %s locks, %s threads, %s acquisitions%n",
                summary.get("events"), summary.get("locks"),
                summary.get("threads"), summary.get("acquisitions")));
        sb.append(String.format("  Findings   %d   (%d critical, %d warning, %d info)%n",
                findings.size(),
                countBySeverity(Finding.Severity.CRITICAL),
                countBySeverity(Finding.Severity.WARNING),
                countBySeverity(Finding.Severity.INFO)));

        if (findings.isEmpty()) {
            sb.append(rule).append('\n');
            sb.append("  No concurrency issues detected.").append('\n');
            sb.append(bar).append('\n');
            return sb.toString();
        }

        int index = 1;
        for (Finding f : findings) {
            sb.append('\n');
            sb.append(String.format("  [%d]  %-8s  %s%n", index++, f.getSeverity(), f.getCategory()));
            sb.append("       ").append(f.getSummary()).append('\n');

            Object lines = f.getDetails().get("lines");
            if (lines instanceof List) {
                for (Object line : (List<?>) lines) {
                    sb.append("         - ").append(line).append('\n');
                }
            }
        }

        sb.append(bar).append('\n');
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }
}

/*
 * Notes
 * - The combined output of an AnalysisEngine run: a trace-level summary plus every Finding,
 *   sorted most-severe-first. Serializes to JSON via its getters and renders a console report.
 * - render() prints a header, the trace and findings counts, then each finding numbered with its
 *   severity and category; a finding's "lines" detail (if present) is shown as indented
 *   sub-bullets.
 * - repeat() builds the separator bars.
 */
