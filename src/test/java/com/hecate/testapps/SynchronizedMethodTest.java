package com.hecate.testapps;

public class SynchronizedMethodTest {

    private int counter = 0;

    public synchronized void increment() {
        counter++;
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized int getCounter() {
        return counter;
    }

    public synchronized void add(int value) {
        counter += value;

        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread. currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        SynchronizedMethodTest test = new SynchronizedMethodTest();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                test.increment();
                if (i % 3 == 0) {
                    System.out.println("Thread-1: counter = " + test.getCounter());
                }
            }
        }, "Thread-1");

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                test.increment();
                if (i % 3 == 0) {
                    System.out.println("Thread-2: counter = " + test.getCounter());
                }
            }
        }, "Thread-2");

        Thread t3 = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                test. add(2);
                System.out.println("Thread-3: added 2, counter = " + test.getCounter());
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

        System.out.println();
        System.out.println("Results");
        System.out.println("Final counter: " + test.getCounter());
        System.out.println("Expected: 30 (10+10+5×2)");
        System.out.println("Execution time: " + (endTime - startTime) + "ms");
        System.out.println();

        if (test.getCounter() == 30) {
            System.out.println("✓ Test PASSED - Counter is correct!");
        } else {
            System.out.println("✗ Test FAILED - Counter is incorrect!");
        }
    }
}