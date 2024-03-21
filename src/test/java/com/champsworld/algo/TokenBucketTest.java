package com.champsworld.algo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author agrsachin81
 */
class TokenBucketTest {

    @Test
    public void testTokenBucketConsume() {
        TokenBucket tokenBucket = new TokenBucket(10, 1); // Create a TokenBucket with capacity 10 and 1 token added per second
        assertTrue(tokenBucket.consume(5)); // Consume 5 tokens, should return true
        assertFalse(tokenBucket.consume(10)); // Try to consume 10 tokens, should return false as there are not enough tokens
    }

    @Test
    public void testTokenBucketRefill() {
        TokenBucket tokenBucket = new TokenBucket(10, 1); // Create a TokenBucket with capacity 10 and 1 token added per second
        tokenBucket.consume(5); // Consume 5 tokens
        tokenBucket.refill(); // Refill the tokens
        assertTrue(tokenBucket.consume(10)); // Consume 10 tokens, should return true as tokens are refilled
    }


    @Test
    public void testTokenBucketCapacity() {
        TokenBucket tokenBucket = new TokenBucket(10, 1); // Create a TokenBucket with capacity 10 and 1 token added per second
        assertEquals(10, tokenBucket.getCapacity()); // Check initial capacity
    }

    @Test
    public void testTokenBucketMultipleConsumes() {
        TokenBucket tokenBucket = new TokenBucket(10, 1); // Create a TokenBucket with capacity 10 and 1 token added per second
        assertTrue(tokenBucket.consume(5)); // Consume 5 tokens
        assertTrue(tokenBucket.consume(2));
        assertFalse(tokenBucket.consume(6)); // Try to consume 6 tokens, should return false as there are not enough tokens
    }

    @Test
    public void testTokenBucketRefillRate() {
        TokenBucket tokenBucket = new TokenBucket(10, 2); // Create a TokenBucket with capacity 10 and 2 tokens added per second
        tokenBucket.consume(5); // Consume 5 tokens
        try {
            Thread.sleep(3000); // Wait for 3 seconds for tokens to refill
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertTrue(tokenBucket.consume(6)); // Consume 6 tokens, should return true as tokens are refilled
    }

    @Test
    public void test() {
        final int max = 10000;
        final Random random = new Random();
        final TokenBucket bucket = new TokenBucket(max, 10);

        AllottingThread thread = new AllottingThread(random, bucket, max);
        AllottingThread thread2 = new AllottingThread(random, bucket, max);
        AllottingThread thread3 = new AllottingThread(random, bucket, max);
        Set<AllottingThread> threads = new HashSet<>();
        thread.start();
        threads.add(thread);
        thread2.start();
        threads.add(thread2);
        thread3.start();
        threads.add(thread3);
        try {
            thread.join();
            thread2.join();
            thread3.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        int count = 0;

        long startTime = Long.MAX_VALUE;
        long endTime = Long.MIN_VALUE;
        for (AllottingThread allottingThread : threads) {
            if (allottingThread.start_time < startTime) startTime = allottingThread.start_time;
            if (allottingThread.end_time > endTime) endTime = allottingThread.end_time;
            count += allottingThread.total_allotted;
        }
        final int max_Allotted = (int) ((endTime - startTime) / bucket.getNanosPerPermit());
        System.out.println("TOTAL ALLOTTED " + count + " total refilled " + (max_Allotted + max) + " remaining " + bucket.available());
    }


    static class AllottingThread extends Thread {
        private final Random random;
        private final TokenBucket bucket;
        private final int max;
        private int total_allotted = 0;
        private long start_time;
        private long end_time;

        AllottingThread(Random random, TokenBucket bucket, int max) {
            this.random = random;
            this.bucket = bucket;
            this.max = max;
        }

        public void run() {
            final String name = Thread.currentThread().getName();
            start_time = System.nanoTime();
            System.out.println(">>>>>>>>" + name + " starting at " + start_time);
            final int num_iteration = 100;
            for (int i = 0; i < num_iteration; i++) {
                final int asked = random.nextInt(1001);
                end_time = System.nanoTime();
                final boolean success = bucket.acquire(asked);
                final int available = bucket.available();
                if (success) {
                    total_allotted += asked;
                    System.out.println("GOT " + asked + " -- " + available + " " + name + " " + end_time);
                } else System.out.println("DENIED " + asked + " -- " + available + " " + name + " " + end_time);
                try {
                    if (asked < max)
                        Thread.sleep(random.nextInt(300));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(">>>>>>>>" + name + " ending at " + end_time + "  TOTAL ALLOTTED " + total_allotted);
        }
    }
}