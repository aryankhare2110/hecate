package com.hecate.analysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs a set of {@link Analyzer}s over a single {@link LockStateModel} and collects their
 * findings into an {@link AnalysisReport}. Analyzers are independent, so registering a new
 * one here is the only wiring a new analysis needs.
 */
public final class AnalysisEngine {

    private final List<Analyzer> analyzers;

    public AnalysisEngine(List<Analyzer> analyzers) {
        this.analyzers = Collections.unmodifiableList(new ArrayList<>(analyzers));
    }

    /** The standard analyzer set, ordered most-to-least severe for readable reports. */
    public static AnalysisEngine withDefaults() {
        return new AnalysisEngine(Arrays.asList(
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
