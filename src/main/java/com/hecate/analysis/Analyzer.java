package com.hecate.analysis;

import java.util.List;

/**
 * A pluggable analysis pass over a reconstructed {@link LockStateModel}.
 *
 * Each analyzer is independent and stateless with respect to others: it reads the
 * shared model and returns its findings. Adding a new analysis is one new class —
 * no edits to the model or existing analyzers.
 */
public interface Analyzer {

    /** Short human-readable name, e.g. "Contention Analyzer". */
    String name();

    List<Finding> analyze(LockStateModel model);
}
