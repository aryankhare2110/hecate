package com.hecate.analysis;

import java.util.List;

public interface Analyzer {

    String name();

    List<Finding> analyze(LockStateModel model);
}

/*
 * Notes
 * - A pluggable analysis pass over a reconstructed LockStateModel. Each analyzer is independent
 *   and stateless with respect to the others: it reads the shared model and returns findings.
 * - Adding a new analysis is one new class implementing this interface, plus one line in
 *   AnalysisEngine.withDefaults().
 */
