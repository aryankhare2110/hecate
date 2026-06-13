package com.hecate.analysis;

/**
 * Strategy for deriving a stable lock identity from a captured event.
 *
 * The agent currently emits {@code lockId = "lock@" + identityHashCode}, which can
 * collide and be reused after GC. Keeping identity pluggable lets the analysis layer
 * swap in a more robust key (e.g. allocation site) later without touching the model.
 */
public interface LockKeyFn {

    String key(String lockId, String lockClass);

    /** Identity-hash based key as captured by the agent. Precise but GC-fragile. */
    LockKeyFn BY_ID = (id, cls) -> id;

    /** Coarse key grouping every instance of a lock class together. */
    LockKeyFn BY_CLASS = (id, cls) -> cls;
}
