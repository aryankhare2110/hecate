package com.hecate.testapps;

/**
 * Classic AB-BA lock-ordering hazard: a bank transfer that locks the "from" account
 * then the "to" account. When two transfers run in opposite directions, one thread
 * takes A&rarr;B while the other takes B&rarr;A — a latent deadlock.
 *
 * The two transfers are run SEQUENTIALLY here so the JVM never actually hangs (a real
 * hang would prevent the shutdown hook from writing the trace). The run completes
 * cleanly, yet both lock orderings are recorded — exactly the situation Hecate's
 * deadlock detector is meant to flag from a successful execution.
 */
public class DeadlockDemo {

    static class Account {
        final String name;
        int balance;

        Account(String name, int balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    /** Locks `from` then `to` — the ordering is whatever order the caller passes them. */
    static void transfer(Account from, Account to, int amount) {
        synchronized (from) {
            sleepQuietly(20);
            synchronized (to) {
                from.balance -= amount;
                to.balance += amount;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account a = new Account("A", 1000);
        Account b = new Account("B", 1000);

        // Thread-1 transfers A -> B (locks A then B)
        Thread t1 = new Thread(() -> transfer(a, b, 100), "Thread-1");
        t1.start();
        t1.join();

        // Thread-2 transfers B -> A (locks B then A) — opposite order
        Thread t2 = new Thread(() -> transfer(b, a, 50), "Thread-2");
        t2.start();
        t2.join();

        System.out.println();
        System.out.println("Transfers complete (no actual hang).");
        System.out.println("A balance: " + a.balance + ", B balance: " + b.balance);
        System.out.println("Two opposite lock orderings (A->B and B->A) were exercised.");
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
