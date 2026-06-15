package com.hecate.analysis;

public interface LockKeyFn {

    String key(String lockId, String lockClass);

    LockKeyFn BY_ID = (id, cls) -> id;

    LockKeyFn BY_CLASS = (id, cls) -> cls;
}

/*
 * Notes
 * - Strategy for deriving a lock identity from a captured event. BY_ID uses the agent's
 *   identityHashCode-based id (precise but collision- and GC-prone); BY_CLASS groups every
 *   instance of a lock class together.
 * - Keeping identity pluggable lets the analysis layer swap in a sturdier key (e.g. allocation
 *   site) later without touching the model or analyzers.
 */
