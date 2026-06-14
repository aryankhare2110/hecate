package com.hecate.testapps;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Exercises {@code java.util.concurrent} locking so the explicit-lock instrumentation can
 * be validated end-to-end.
 *
 * Each account guards its balance with a {@link ReentrantLock}. A transfer locks the
 * "from" account then the "to" account, so opposite-direction transfers form an AB-BA
 * ordering — the same latent deadlock as the monitor demo, but via {@code lock()}/
 * {@code unlock()} instead of {@code synchronized}. The transfers run sequentially so the
 * JVM exits cleanly and the trace is written. A {@code tryLock()} call exercises that path.
 */
public class ReentrantLockDemo {

    static class Account {
        final String name;
        final ReentrantLock lock = new ReentrantLock();
        int balance;

        Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    static void transfer(Account from, Account to, int amount) {
        from.lock.lock();
        try {
            sleepQuietly(20);
            to.lock.lock();
            try {
                from.balance -= amount;
                to.balance += amount;
            } finally {
                to.lock.unlock();
            }
        } finally {
            from.lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account a = new Account("A", 1000);
        Account b = new Account("B", 1000);

        Thread t1 = new Thread(() -> transfer(a, b, 100), "Thread-1");
        t1.start();
        t1.join();

        Thread t2 = new Thread(() -> transfer(b, a, 50), "Thread-2");
        t2.start();
        t2.join();

        // Exercise the tryLock() instrumentation path.
        if (a.lock.tryLock()) {
            try {
                a.balance += 0;
            } finally {
                a.lock.unlock();
            }
        }

        System.out.println();
        System.out.println("ReentrantLock transfers complete (no actual hang).");
        System.out.println("A balance: " + a.balance + ", B balance: " + b.balance);
        System.out.println("Opposite j.u.c lock orderings (A->B and B->A) were exercised.");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
