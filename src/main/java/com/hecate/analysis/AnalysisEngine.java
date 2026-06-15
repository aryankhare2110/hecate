package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class AnalysisEngine {

    private final List<Analyzer> analyzers;

    public AnalysisEngine(List<Analyzer> analyzers) {
        this.analyzers = Collections.unmodifiableList(new ArrayList<>(analyzers));
    }

    public static AnalysisEngine withDefaults() {
        return new AnalysisEngine(Arrays.asList(
                new WaitForGraphAnalyzer(),
                new DeadlockAnalyzer(),
                new ContentionAnalyzer(),
                new HoldTimeAnalyzer(),
                new FairnessAnalyzer()));
    }

    public List<Analyzer> getAnalyzers() {
        return analyzers;
    }

    public AnalysisReport run(LockStateModel model) {
        List<Finding> all = new ArrayList<>();
        for (Analyzer analyzer : analyzers) {
            all.addAll(analyzer.analyze(model));
        }
        return new AnalysisReport(model, all);
    }
}

/*
 * Notes
 * - Runs a set of Analyzers over one LockStateModel and collects their findings into an
 *   AnalysisReport.
 * - withDefaults() is the standard set, ordered so the report reads most-to-least severe:
 *   live deadlock, potential deadlock, contention, hold-time, fairness.
 * - Analyzers are independent, so registering a new one here is the only wiring it needs.
 */
