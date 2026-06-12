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
