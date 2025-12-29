package com.hecate.agent;

import com.hecate.events.*;
import net.bytebuddy.asm.Advice;

public class SynchronizedMethodInterceptor {

    @Advice.OnMethodEnter
    public static long onEnter (@Advice.This(optional = true) Object lockObject) {
        try {
            long timestamp = System.nanoTime();
            Thread currentThread = Thread.currentThread();
            String lockId = generateLockId(lockObject);
            String lockClass;

            if (lockObject != null) {
                lockClass = lockObject.getClass().getName();
            }
            else {
                lockClass = "static-lock";
            }

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
            String lockId = generateLockId(lockObject);
            String lockClass;

            if (lockObject != null) {
                lockClass = lockObject.getClass().getName();
            }
            else {
                lockClass = "static-lock";
            }

            long holdDuration = timestamp - enterTimestamp;
            EventCollector.getInstance().recordEvent(new LockReleaseEvent(timestamp, currentThread.getId(), currentThread.getName(), lockId, lockClass, holdDuration));
        } catch (Throwable t) {
            System.err.println("[Hecate] Error in onExit: " + t.getMessage());
        }
    }

    private static String generateLockId(Object lockObject) {
        if (lockObject == null) {
            return "static-lock";
        }
        else {
            return "lock@" + Integer.toHexString(System.identityHashCode(lockObject));
        }
    }


}
