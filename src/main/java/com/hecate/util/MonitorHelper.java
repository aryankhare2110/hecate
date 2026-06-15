package com.hecate.util;

import com.hecate.events.EventCollector;
import com.hecate.events.LockAcquireEvent;
import com.hecate.events.LockReleaseEvent;
import com.hecate.events.LockWaitEvent;

import java.util.*;
import java.util.concurrent.*;

public class MonitorHelper {

    private static final Map<String, Long> lockAcquisitionTimes = new ConcurrentHashMap<>();

    public static void beforeMonitorEnter(Object lockObject) {
        try {
            if (lockObject ==null){
                return;
            }
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = generateLockId(lockObject);
            String lockClass = lockObject.getClass().getName();
            EventCollector.getInstance().recordEvent(new LockWaitEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass));
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in beforeMonitorEnter: " + t.getMessage());
        }
    }

    public static void afterMonitorEnter(Object lockObject) {
        try {
            if (lockObject == null) {
                return;
            }
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = generateLockId(lockObject);
            String lockClass = lockObject.getClass().getName();
            String key = currentThread.getId()+":"+lockId;
            lockAcquisitionTimes.put(key, timestamp);
            EventCollector.getInstance().recordEvent(new LockAcquireEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass));
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in afterMonitorExit: " + t.getMessage());
        }
    }

    public static void beforeMonitorExit(Object lockObject) {
        try {
            if (lockObject == null) {
                return;
            }
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = generateLockId(lockObject);
            String lockClass = lockObject.getClass().getName();
            String key = currentThread.getId()+":"+lockId;
            Long acquisitionTime = lockAcquisitionTimes.remove(key);
            long holdDuration;
            if (acquisitionTime != null) {
                holdDuration = timestamp - acquisitionTime;
            }
            else {
                holdDuration = 0;
            }
            EventCollector.getInstance().recordEvent(new LockReleaseEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass, holdDuration));
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in beforeMonitorExit: " + t.getMessage());
        }
    }

    public static boolean afterTryLock(Object lockObject, boolean acquired) {
        if (acquired) {
            afterMonitorEnter(lockObject);
        }
        return acquired;
    }

    private static String generateLockId(Object lockObject) {
        if (lockObject == null) {
            return "null-lock";
        }
        else {
            return "lock@" + Integer.toHexString(System.identityHashCode(lockObject));
        }
    }

}

/*
 * Notes
 * - The callbacks injected at every instrumented lock site. They run on the host program's own
 *   threads, so the state is concurrent and every method swallows Throwable to never break the
 *   host.
 * - beforeMonitorEnter records WAIT; afterMonitorEnter records ACQUIRE and remembers the acquire
 *   time keyed by "threadId:lockId"; beforeMonitorExit records RELEASE and computes the hold
 *   duration from that remembered time.
 * - The same three methods serve both synchronized blocks and j.u.c lock()/unlock().
 *   afterTryLock records an ACQUIRE only when tryLock() returned true, passing the boolean
 *   result straight through.
 * - lockId is identityHashCode-based, the project's single (collision-prone) lock identity.
 * - Known wrinkle: the acquire-time map keys on lock id only, so a reentrant re-acquire
 *   overwrites the outer acquire time, and the outer release then reports a short or zero hold.
 */
