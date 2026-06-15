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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LockStateModel {

    private final List<Event> events;
    private final List<LockAcquisition> acquisitions = new ArrayList<>();
    private final Set<String> lockKeys = new LinkedHashSet<>();
    private final Set<Long> threadIds = new LinkedHashSet<>();
    private final Map<Long, String> threadNames = new LinkedHashMap<>();
    private final Map<Long, BlockedThread> blockedThreads = new LinkedHashMap<>();
    private final Map<String, Long> lockOwnersAtEnd = new LinkedHashMap<>();
    private final LockKeyFn keyFn;

    public LockStateModel(List<Event> events) {
        this(events, LockKeyFn.BY_ID);
    }

    public LockStateModel(List<Event> events, LockKeyFn keyFn) {
        this.keyFn = keyFn;
        this.events = new ArrayList<>(events);
        this.events.sort(Comparator.comparingLong(Event::getTimestamp));
        reconstruct();
    }

    private void reconstruct() {
        Map<Long, PendingWait> pendingWaits = new HashMap<>();
        Map<Long, Deque<OpenAcquisition>> heldByThread = new HashMap<>();

        for (Event e : events) {
            threadIds.add(e.getThreadId());
            threadNames.putIfAbsent(e.getThreadId(), e.getThreadName());

            switch (e.getEventType()) {
                case LOCK_WAIT: {
                    LockWaitEvent w = (LockWaitEvent) e;
                    String key = keyFn.key(w.getLockId(), w.getLockClass());
                    lockKeys.add(key);
                    pendingWaits.put(e.getThreadId(), new PendingWait(key, w.getLockClass(), e.getTimestamp()));
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
                    Collections.reverse(heldSnapshot);

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
                        for (Iterator<OpenAcquisition> it = held.iterator(); it.hasNext(); ) {
                            OpenAcquisition open = it.next();
                            if (open.lockKey.equals(key)) {
                                it.remove();
                                acquisitions.add(open.complete(e.getTimestamp(), r.getHoldDuration()));
                                break;
                            }
                        }
                    }
                    break;
                }

                default:
                    break;
            }
        }

        long endTs = events.isEmpty() ? 0 : events.get(events.size() - 1).getTimestamp();
        for (Map.Entry<Long, Deque<OpenAcquisition>> entry : heldByThread.entrySet()) {
            for (OpenAcquisition open : entry.getValue()) {
                acquisitions.add(open.finalizeUnreleased(endTs));
                lockOwnersAtEnd.putIfAbsent(open.lockKey, entry.getKey());
            }
        }

        for (Map.Entry<Long, PendingWait> entry : pendingWaits.entrySet()) {
            long tid = entry.getKey();
            PendingWait pw = entry.getValue();
            blockedThreads.put(tid, new BlockedThread(tid, getThreadName(tid), pw.key, pw.lockClass, pw.timestamp));
        }

        acquisitions.sort(Comparator.comparingLong(LockAcquisition::getAcquireTs));
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public List<LockAcquisition> getAcquisitions() {
        return Collections.unmodifiableList(acquisitions);
    }

    public List<LockAcquisition> getAcquisitionsForLock(String lockKey) {
        List<LockAcquisition> result = new ArrayList<>();
        for (LockAcquisition a : acquisitions) {
            if (a.getLockKey().equals(lockKey)) {
                result.add(a);
            }
        }
        return result;
    }

    public Set<String> getLockKeys() {
        return Collections.unmodifiableSet(lockKeys);
    }

    public Set<Long> getThreadIds() {
        return Collections.unmodifiableSet(threadIds);
    }

    public String getThreadName(long threadId) {
        return threadNames.getOrDefault(threadId, "thread-" + threadId);
    }

    public Map<Long, BlockedThread> getBlockedThreads() {
        return Collections.unmodifiableMap(blockedThreads);
    }

    public Map<String, Long> getLockOwnersAtEnd() {
        return Collections.unmodifiableMap(lockOwnersAtEnd);
    }

    public static final class BlockedThread {
        private final long threadId;
        private final String threadName;
        private final String lockKey;
        private final String lockClass;
        private final long sinceTs;

        public BlockedThread(long threadId, String threadName, String lockKey, String lockClass, long sinceTs) {
            this.threadId = threadId;
            this.threadName = threadName;
            this.lockKey = lockKey;
            this.lockClass = lockClass;
            this.sinceTs = sinceTs;
        }

        public long getThreadId() {
            return threadId;
        }

        public String getThreadName() {
            return threadName;
        }

        public String getLockKey() {
            return lockKey;
        }

        public String getLockClass() {
            return lockClass;
        }

        public long getSinceTs() {
            return sinceTs;
        }
    }

    private static final class PendingWait {
        final String key;
        final String lockClass;
        final long timestamp;

        PendingWait(String key, String lockClass, long timestamp) {
            this.key = key;
            this.lockClass = lockClass;
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

        LockAcquisition finalizeUnreleased(long endTs) {
            long holdLowerBound = Math.max(0, endTs - acquireTs);
            return new LockAcquisition(lockKey, lockClass, threadId, threadName,
                    waitStartTs, acquireTs, -1, holdLowerBound, heldWhenAcquired);
        }
    }
}

/*
 * Notes
 * - Pass 0 of the engine: replays a timestamp-sorted event stream once and reconstructs the
 *   lock-hold timeline as immutable LockAcquisition records. This is the shared substrate every
 *   analyzer reads; the event stream is parsed exactly once here.
 * - The sort by timestamp is stable, so equal-timestamp events keep their captured order and a
 *   WAIT stays ahead of the ACQUIRE it precedes.
 * - Reconstruction tracks per thread: pendingWaits (the WAIT a thread is currently blocked on)
 *   and heldByThread (a stack of held locks, most-recent on top). An ACQUIRE pairs with a
 *   matching pending WAIT for the wait time and snapshots the currently-held locks for the
 *   lock-order graph; a RELEASE matches the innermost open acquisition of that lock, which
 *   handles nesting and reentrancy.
 * - End-of-trace snapshot, used for live-deadlock detection: locks still held become unreleased
 *   acquisitions and populate lockOwnersAtEnd, and threads with an unmatched WAIT become
 *   blockedThreads.
 * - An unreleased acquisition's hold time is unknown, so it is estimated as acquire-to-end (a
 *   lower bound) rather than zero, which keeps a deadlocked, held-forever lock from looking like
 *   infinite contention.
 * - Lock identity is pluggable via LockKeyFn, so the model can be rebuilt under a coarser key.
 * - PendingWait and OpenAcquisition are private mutable scratch types; BlockedThread is the
 *   public end-of-trace view.
 */
