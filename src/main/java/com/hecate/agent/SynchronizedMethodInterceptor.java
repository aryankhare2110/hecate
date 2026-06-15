package com.hecate.agent;

import com.hecate.events.*;
import net.bytebuddy.asm.Advice;

public class SynchronizedMethodInterceptor {

    @Advice.OnMethodEnter
    public static long onEnter(@Advice.This(optional = true) Object lockObject) {
        try {
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = lockObject == null ? "static-lock" : "lock@" + Integer.toHexString(System.identityHashCode(lockObject));
            String lockClass = lockObject == null ? "static-lock" : lockObject.getClass().getName();
            EventCollector.getInstance().recordEvent(new LockAcquireEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass));
            return timestamp;
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in onEnter: " + t.getMessage());
            return System.nanoTime();
        }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.This(optional = true) Object lockObject, @Advice.Enter long enterTimestamp) {
        try {
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = lockObject == null ? "static-lock" : "lock@" + Integer.toHexString(System.identityHashCode(lockObject));
            String lockClass = lockObject == null ? "static-lock" : lockObject.getClass().getName();
            long holdDuration = timestamp - enterTimestamp;
            EventCollector.getInstance().recordEvent(new LockReleaseEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass, holdDuration));
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in onExit: " + t.getMessage());
        }
    }

}

/*
 * Notes
 * - ByteBuddy @Advice woven around synchronized methods (matched by isSynchronized in
 *   HecateAgent). Advice bodies are inlined into the target class, so this code stays
 *   self-contained and references no shared private helpers.
 * - onEnter records a LOCK_ACQUIRE and returns the enter timestamp; onExit (also on a thrown
 *   Throwable) records a LOCK_RELEASE with the hold duration computed from that timestamp.
 * - lockObject is the monitor: the instance for an instance method, or null for a static
 *   synchronized method (reported as "static-lock"). lockId uses identityHashCode, matching
 *   MonitorHelper's scheme so block and method locks share identities.
 * - All work is wrapped in try/catch so instrumentation never breaks the host program.
 */

