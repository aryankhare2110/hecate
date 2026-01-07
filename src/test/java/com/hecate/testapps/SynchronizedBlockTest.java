package com.hecate.testapps;

public class SynchronizedBlockTest {

    private int balance = 0;
    private final Object lock = new Object();

    public void deposit(int amount) {
        synchronized(lock) {
            balance += amount;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread. currentThread().interrupt();
            }
        }
    }

    public int getBalance() {
        synchronized(lock) {
            return balance;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println();

        SynchronizedBlockTest test = new SynchronizedBlockTest();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                test. deposit(10);
                System.out.println("Thread-1 deposited 10, balance:  " + test.getBalance());
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                test.deposit(20);
                System.out.println("Thread-2 deposited 20, balance: " + test. getBalance());
            }
        }, "Thread-2");

        Thread t3 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                test.deposit(30);
                System.out.println("Thread-3 deposited 30, balance: " + test.getBalance());
            }
        }, "Thread-3");

        long startTime = System.currentTimeMillis();

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        long endTime = System.currentTimeMillis();

        System.out. println();
        System.out. println("Results");
        System.out.println("Final balance: " + test.getBalance());
        System.out.println("Expected: 300 (10×5 + 20×5 + 30×5)");
        System.out.println("Execution time: " + (endTime - startTime) + " ms");
        System.out. println();

        if (test.getBalance() == 300) {
            System.out.println("✓ Test PASSED - Balance is correct!");
        } else {
            System.out.println("✗ Test FAILED - Balance is incorrect!");
        }
    }
}