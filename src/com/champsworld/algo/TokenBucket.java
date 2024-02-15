package com.champsworld.algo;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

public class TokenBucket {
    private static final long NONS_PER_SECOND = 1000L * 1000 * 1000;
    private static final ThreadLocal<int[]> reusableStampHolder = ThreadLocal.withInitial(()-> new int[1]);
    private final AtomicInteger stampGenerator = new AtomicInteger(0);
    private final long nanos_per_permit;
    private final int maxTokens;

    private final AtomicStampedReference<BucketState> currentState ;
    private final ThreadLocal<BucketState> swapReference;

    public TokenBucket(int seconds, final int maxTokens){
        this.maxTokens = maxTokens;
        this.nanos_per_permit = (NONS_PER_SECOND * seconds) /(long)maxTokens;
        this.currentState = new AtomicStampedReference<>(new BucketState(maxTokens,  nanos_per_permit), stampGenerator.getAndIncrement());
        this.swapReference = ThreadLocal.withInitial( ()-> new BucketState(maxTokens,  nanos_per_permit));
    }

    public boolean acquire(final int no_tokens){
        if(no_tokens <=0 || no_tokens> maxTokens) return false;
        final int[] stampHolder = reusableStampHolder.get();
        final BucketState newState = this.swapReference.get();
        while(true){
            final BucketState state = this.currentState.get(stampHolder);
            newState.init(state);
            final boolean canAllot = newState.allot(no_tokens);
            final int newStamp = stampGenerator.getAndIncrement();
            if(this.currentState.compareAndSet(state, newState, stampHolder[0], newStamp)){
                this.swapReference.set(state);
                if(canAllot) return true;
            }
            if(!canAllot) return false;
        }
    }

    public int available(){
        return this.currentState.getReference().tokens;
    }

    private static class BucketState {
        private int tokens;
        private long ref_Stamp;

        private final long nanos_per_permit;
        private final int maxTokens;

        private BucketState(int max, long nanos_per_permit){
            this.tokens = max;
            this.ref_Stamp = 0;
            this.nanos_per_permit= nanos_per_permit;
            this.maxTokens= max;

            System.out.println("CREATED STATE OBJECT "+Thread.currentThread().getName());
        }

        public void init(BucketState state) {
            this.tokens = state.tokens;
            this.ref_Stamp = state.ref_Stamp;
        }

        private void refill(){
            if(this.ref_Stamp ==0) return;
            final long current = System.nanoTime();
            final long diff = current - this.ref_Stamp;
            final int earned = (int) (diff / nanos_per_permit);
            if(earned > 0) {
                this.tokens = Math.min(this.maxTokens, this.tokens + earned);
                this.ref_Stamp = current;
            }
        }

        boolean allot(final int tokens){
            refill();
            if(tokens <= this.tokens){
                this.tokens -=tokens;
                this.ref_Stamp = System.nanoTime();
                return true;
            }
            return false;
        }
    }

    public static void main(String[] args) {
        final int max = 10000;
        final Random random = new Random();
        final TokenBucket bucket = new TokenBucket(10, max);

        AllottingThread thread = new AllottingThread(random, bucket, max);
        AllottingThread thread2 = new AllottingThread(random, bucket, max);
        AllottingThread thread3 = new AllottingThread(random, bucket, max);
        Set<AllottingThread> threads = new HashSet<>();
        thread.start(); threads.add(thread);
        thread2.start();threads.add(thread2);
        thread3.start(); threads.add(thread3);
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
        for(AllottingThread allottingThread: threads){
            if(allottingThread.start_time < startTime) startTime = allottingThread.start_time;
            if(allottingThread.end_time > endTime) endTime = allottingThread.end_time;
            count +=allottingThread.total_allotted;
        }
        final int max_Allotted =  (int) ((endTime - startTime)/ bucket.nanos_per_permit);
        System.out.println("TOTAL ALLOTTED "+ count +" total refilled "+ (max_Allotted + max) +" remaining "+bucket.available());
    }

    static class AllottingThread extends Thread{
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

        public void run(){
            final String name= Thread.currentThread().getName();
            start_time = System.nanoTime();
            System.out.println(">>>>>>>>"+name+" starting at "+start_time);
            final int num_iteration = 100;
            for(int i=0; i< num_iteration;i ++) {
                final int asked = random.nextInt(1001);
                end_time = System.nanoTime();
                final boolean success =  bucket.acquire(asked);
                final int available = bucket.available();
                if(success)  {
                    total_allotted +=asked;
                    System.out.println("GOT "+asked +" -- "+available +" "+ name +" "+end_time);
                }
                else System.out.println("DENIED "+asked +" -- "+available +" "+ name +" "+end_time);
                try {
                    if (asked <max)
                        Thread.sleep(random.nextInt(300));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            System.out.println(">>>>>>>>"+name+" ending at "+ end_time +"  TOTAL ALLOTTED "+ total_allotted);
        }
    }
}
