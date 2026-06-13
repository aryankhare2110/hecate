package com.hecate.analysis;

import com.hecate.events.Event;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import com.hecate.events.LockWaitEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pass 0 of the analysis engine: replays a timestamp-ordered event stream once and
 * reconstructs the lock-hold timeline as a list of {@link LockAcquisition} records.
 *
 * This is the shared substrate every analyzer reads from — the event stream is parsed
 * exactly once here, never by individual analyzers. Lock identity is pluggable via
 * {@link LockKeyFn} so the same model can be rebuilt under a coarser/robuster key.
 */
public class LockStateModel {

    private final List<Event> events;
    private final List<LockAcquisition> acquisitions = new ArrayList<>();
    private final Set<String> lockKeys = new LinkedHashSet<>();
    private final Set<Long> threadIds = new LinkedHashSet<>();
    private final LockKeyFn keyFn;

    public LockStateModel(List<Event> events) {
        this(events, LockKeyFn.BY_ID);
    }

    public LockStateModel(List<Event> events, LockKeyFn keyFn) {
        this.keyFn = keyFn;
        this.events = new ArrayList<>(events);
        // Stable sort by timestamp: equal-timestamp events keep their captured order
        // (so WAIT stays ahead of the ACQUIRE it precedes).
        this.events.sort(Comparator.comparingLong(Event::getTimestamp));
        reconstruct();
    }

    private void reconstruct() {
        // Per-thread: the WAIT a thread is currently blocked on (a thread blocks on at most one lock).
        Map<Long, PendingWait> pendingWaits = new HashMap<>();
        // Per-thread: stack of locks currently held (most-recent on top), to pair RELEASE with ACQUIRE
        // and to snapshot the held-set for the lock-order graph. Handles nesting and reentrancy.
        Map<Long, Deque<OpenAcquisition>> heldByThread = new HashMap<>();

        for (Event e : events) {
            threadIds.add(e.getThreadId());

            switch (e.getEventType()) {
                case LOCK_WAIT: {
                    LockWaitEvent w = (LockWaitEvent) e;
                    String key = keyFn.key(w.getLockId(), w.getLockClass());
                    lockKeys.add(key);
                    pendingWaits.put(e.getThreadId(), new PendingWait(key, e.getTimestamp()));
                    break;
                }

                case LOCK_ACQUIRE: {
                    LockAcquireEvent a = (LockAcquireEvent) e;
                    String key = keyFn.key(a.getLockId(), a.getLockClass());
                    lockKeys.add(key);
                    long tid = e.getThreadId();

                    long waitStart = -1;
                    PendingWait pw = pendingWaits.get(tid);
                    if (pw != null && pw.key.equals(key)) {
                        waitStart = pw.timestamp;
                        pendingWaits.remove(tid);
                    }

                    Deque<OpenAcquisition> held = heldByThread.computeIfAbsent(tid, k -> new ArrayDeque<>());
                    List<String> heldSnapshot = new ArrayList<>();
                    for (OpenAcquisition open : held) {
                        heldSnapshot.add(open.lockKey);
                    }
                    Collections.reverse(heldSnapshot); // outermost-held first

                    OpenAcquisition acq = new OpenAcquisition();
                    acq.lockKey = key;
                    acq.lockClass = a.getLockClass();
                    acq.threadId = tid;
                    acq.threadName = a.getThreadName();
                    acq.waitStartTs = waitStart;
                    acq.acquireTs = e.getTimestamp();
                    acq.heldWhenAcquired = heldSnapshot;
                    held.push(acq);
                    break;
                }

                case LOCK_RELEASE: {
                    LockReleaseEvent r = (LockReleaseEvent) e;
                    String key = keyFn.key(r.getLockId(), r.getLockClass());
                    long tid = e.getThreadId();

                    Deque<OpenAcquisition> held = heldByThread.get(tid);
                    if (held != null) {
                        // Match the most-recent open acquisition of this lock (innermost for nesting).
                        for (Iterator<OpenAcquisition> it = held.iterator(); it.hasNext(); ) {
                            OpenAcquisition open = it.next();
                            if (open.lockKey.equals(key)) {
                                it.remove();
                                acquisitions.add(open.complete(e.getTimestamp(), r.getHoldDuration()));
                                break;
                            }
                        }
                    }
                    // A RELEASE with no matching ACQUIRE means a truncated trace — safely ignored.
                    break;
                }

                default:
                    // THREAD_START / THREAD_END carry no lock state; not needed in this pass.
                    break;
            }
        }

        // Locks still held when the trace ended: record them as unreleased acquisitions.
        for (Deque<OpenAcquisition> held : heldByThread.values()) {
            for (OpenAcquisition open : held) {
                acquisitions.add(open.finalizeUnreleased());
            }
        }

        acquisitions.sort(Comparator.comparingLong(LockAcquisition::getAcquireTs));
    }

    /** All events that built this model, timestamp-sorted. */
    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /** Every reconstructed lock-hold interval, ordered by acquisition time. */
    public List<LockAcquisition> getAcquisitions() {
        return Collections.unmodifiableList(acquisitions);
    }

    /** Acquisitions for a single lock key, in acquisition order. */
    public List<LockAcquisition> getAcquisitionsForLock(String lockKey) {
        List<LockAcquisition> result = new ArrayList<>();
        for (LockAcquisition a : acquisitions) {
            if (a.getLockKey().equals(lockKey)) {
                result.add(a);
            }
        }
        return result;
    }

    /** Distinct lock keys observed (insertion order). */
    public Set<String> getLockKeys() {
        return Collections.unmodifiableSet(lockKeys);
    }

    /** Distinct thread ids observed (insertion order). */
    public Set<Long> getThreadIds() {
        return Collections.unmodifiableSet(threadIds);
    }

    // ---- internal mutable scratch types ----

    private static final class PendingWait {
        final String key;
        final long timestamp;

        PendingWait(String key, long timestamp) {
            this.key = key;
            this.timestamp = timestamp;
        }
    }

    private static final class OpenAcquisition {
        String lockKey;
        String lockClass;
        long threadId;
        String threadName;
        long waitStartTs;
        long acquireTs;
        List<String> heldWhenAcquired;

        LockAcquisition complete(long releaseTs, long holdDuration) {
            return new LockAcquisition(lockKey, lockClass, threadId, threadName,
                    waitStartTs, acquireTs, releaseTs, holdDuration, heldWhenAcquired);
        }

        LockAcquisition finalizeUnreleased() {
            return new LockAcquisition(lockKey, lockClass, threadId, threadName,
                    waitStartTs, acquireTs, -1, 0, heldWhenAcquired);
        }
    }
}
