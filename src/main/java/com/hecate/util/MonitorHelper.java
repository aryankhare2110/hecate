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

    private static String generateLockId(Object lockObject) {
        if (lockObject == null) {
            return "null-lock";
        }
        else {
            return "lock@" + Integer.toHexString(System.identityHashCode(lockObject));
        }
    }

}
