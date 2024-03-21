package com.champsworld.algo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * @author agrsachin81
 */
public class TokenBucket {
    private static final long NONS_PER_SECOND = 1000L * 1000 * 1000;
    private static final ThreadLocal<int[]> reusableStampHolder = ThreadLocal.withInitial(()-> new int[1]);
    private final AtomicInteger stampGenerator = new AtomicInteger(0);
    private final long nanosPerPermit;
    private final int maxTokens;

    private final AtomicStampedReference<BucketState> currentState ;
    private final ThreadLocal<BucketState> swapReference;

    public TokenBucket(final int maxTokens, int seconds){
        this.maxTokens = maxTokens;
        this.nanosPerPermit = (NONS_PER_SECOND * seconds) /(long)maxTokens;
        this.currentState = new AtomicStampedReference<>(new BucketState(maxTokens, nanosPerPermit), stampGenerator.getAndIncrement());
        this.swapReference = ThreadLocal.withInitial( ()-> new BucketState(maxTokens, nanosPerPermit));
    }

    public long getNanosPerPermit() {
        return nanosPerPermit;
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

    public boolean consume(int tokens) {
        return acquire(tokens);
    }

    public void refill() {
        final int[] stampHolder = reusableStampHolder.get();
        final BucketState newState = this.swapReference.get();
        while(true){
            final BucketState state = this.currentState.get(stampHolder);
            newState.refillToMax();
            final int newStamp = stampGenerator.getAndIncrement();
            if(this.currentState.compareAndSet(state, newState, stampHolder[0], newStamp)){
                this.swapReference.set(state);
                return;
            }
        }
    }

    public int getCapacity(){
        return maxTokens;
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
        }

        public void init(BucketState state) {
            this.tokens = state.tokens;
            this.ref_Stamp = state.ref_Stamp;
        }

        public void refillToMax() {
            this.tokens = this.maxTokens;
            this.ref_Stamp = System.currentTimeMillis();
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




}
