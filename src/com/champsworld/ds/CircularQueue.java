package com.champsworld.ds;

import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;


/**
 * Array based Queue implementation (circular queue) FIFO
 * Non blocking implementation
 * make it grow by amortized way
 **/
public final class CircularQueue<T> {

    // the below variable must be of type int only
    private static final int INT_BOUNDARY_CHECK =  Integer.MAX_VALUE -1;

    //use ThreadLocal to recycle object, all the items inside must be volatile then
    //increase size data array is automatically shared with both objects, since with add and remove we dont change data reference at all
    private static class QState<V> {
        volatile V[] data;

        //since max_size is of Integer.MAX_VALUE the idx must be of long
        // else when max size is INT BOUNDARY CHECK then it will reindex at each addition
        final AtomicLong consumeIdx;
        //since max_size is of Integer.MAX_VALUE the idx must be of long
        //else when max size is INT BOUNDARY CHECK then it will reindex at each addition
        final AtomicLong produceIdx;

        //caching threshold so calculating only when max_size is changed, else we recalculate on each addition to queue
        final AtomicLong indexThreshold;
        volatile int max_size;

        @SuppressWarnings("unchecked")
        private QState(int max) {
            this.data = (V[]) new Object[max];
            this.produceIdx = new AtomicLong(0);
            this.consumeIdx = new AtomicLong(0);
            this.indexThreshold = new AtomicLong(calcIndexThreshold(max));
            this.max_size = max;
        }

        static long calcIndexThreshold(int max_size){
            // this cheque make sure that casting to int after modulo never fails for cases where dataIdx is created and
            // since max_size is Integer.MAX_VALUE because of java max array size limit
            // multiply by 2 is necessary unless we shall be adjusting indexes on each addition when max size is equal to INT_BOUNDARY_CHECK
            // and resetting the indexes with max int is necessary since we modulo it and want to cast it to int while accessing array
            // because for small max_size; if cons/prod idx grows too big then after dividing it with max_size it may overflow int
            System.out.println("CHANGED INDEX THRESHOLD ====");
            return max_size < INT_BOUNDARY_CHECK ? INT_BOUNDARY_CHECK : ((INT_BOUNDARY_CHECK * 2L) -1);
        }

        // for expanded, create a zero index consumed QState
        private QState(V[] data, int max, int prod) {
            this.data = data;
            this.produceIdx = new AtomicLong(prod);
            this.consumeIdx = new AtomicLong(0);
            this.indexThreshold = new AtomicLong(calcIndexThreshold(max));
            this.max_size = max;
        }

        void addValue(V data) {
            final int dataIdx = (int) (produceIdx.getAndIncrement() % max_size);
            this.data[dataIdx] = data;
        }

        V remove() {
            final int dataIdx = (int) (consumeIdx.getAndIncrement() % max_size);
            final V value = data[dataIdx];
            data[dataIdx] = null;
            return value;
        }


        @SuppressWarnings("unchecked")
        void reset(QState<V> state) {
            // case arrives when old cached copy has lesser size data array but new has grown
            if (max_size < state.max_size)
                this.data = (V[]) new Object[state.max_size];

            System.arraycopy(state.data, 0, this.data, 0, state.max_size);

            this.produceIdx.set(state.produceIdx.get());
            this.consumeIdx.set(state.consumeIdx.get());
            if(state.max_size != this.max_size) {
                this.max_size = state.max_size;
                this.indexThreshold.set(state.indexThreshold.get());
            }
        }

        void reset(V[] data, int prod, int max) {
            //used while reset is used on a newly created object in a loop
            this.data = data;
            this.produceIdx.set(prod);
            this.consumeIdx.set(0);
            if(max != this.max_size) {
                this.max_size = max;
                this.indexThreshold.set(calcIndexThreshold(max));
            }
        }

        private boolean isFull() {
            return (produceIdx.get() - consumeIdx.get()) == max_size;
        }

        private boolean isEmpty() {
            return produceIdx.get() == consumeIdx.get();
        }

        public int size() {
            return (int) (produceIdx.get() - consumeIdx.get());
        }

        // produce idx is supposed to be always larger than consumeIdx
        // hence overfill will be called by add only
        private void checkOverfill() {
            if (produceIdx.get() >=  this.indexThreshold.get()) {
                final long newCondIdx = (consumeIdx.get() % max_size);
                //since isEmpty, isFull, size depends on the notion that produceIdx is always bigger or equal then the consumeIdx
                final long currIdx =  (produceIdx.get() % max_size);
                // since after adding amx_size the produceIdx can go beyond Int range
                // hence produceIdx and consumeIdx is of type Long
                produceIdx.set(currIdx < newCondIdx ? (currIdx + max_size) : currIdx);
                consumeIdx.set(newCondIdx);
                System.out.println("RESET INDEXES consume "+consumeIdx.longValue() +" produce "+produceIdx.longValue());
            }
        }

        @Override
        public String toString() {
            return "QState{" +
                    "data=" + System.identityHashCode(data) +
                    ", consumeIdx=" + consumeIdx +
                    ", produceIdx=" + produceIdx +
                    ", max_size=" + max_size +
                    '}';
        }
    }

    // we have to use AtomicStampedReference, so for each increment we create new object as compareAndSet uses '==' and NOT equals
    // stamped reference is needed for ABA problem since reference objects are recycled, it is used for unique write signature
    private final AtomicStampedReference<QState<T>> qState;
    private final ThreadLocal<QState<T>> cachedSwapState;

    //if we use AtomicInteger we have to use getAndIncrement which will reset to zero when reach Integer_MAX_VALUE, our counter will be ok
    //since we are using only for stamps
    private final AtomicInteger stampGenerator = new AtomicInteger(1);

    public CircularQueue(int initial_capacity) {
        if (initial_capacity > INT_BOUNDARY_CHECK)
            throw new IllegalStateException("Invalid initial Size can not be larger then " + INT_BOUNDARY_CHECK);
        this.qState = new AtomicStampedReference<>(new QState<>(initial_capacity), stampGenerator.getAndIncrement());
        cachedSwapState = ThreadLocal.withInitial(() -> new QState<>(this.qState.getReference().max_size));
    }

    // queues item at the end of the queue
    public boolean add(T value) {
        if (value == null) throw new IllegalArgumentException(" NUll value not allowed");
        final int[] stampHolder = new int[1];
        QState<T> reference;
        // the value of newStamp is only known to currentThread, each thread will have their own different value
        final int newStamp = stampGenerator.getAndIncrement();
        QState<T> newReference = cachedSwapState.get();
        while (true) {
            reference = this.qState.get(stampHolder);
            if (reference.isFull()) {
                boolean result = increaseSize();
                // reached max array Limit
                if (!result) return false;
                newReference = cachedSwapState.get();
                reference = this.qState.get(stampHolder);
            }
            newReference.reset(reference);
            newReference.checkOverfill();
            newReference.addValue(value);

            if (this.qState.compareAndSet(reference, newReference, stampHolder[0], newStamp)) {
                cachedSwapState.set(reference);
                return true;
            }
        }

    }


    @SuppressWarnings("unchecked")
    // returns reference based on which it taken a decision not Full always return not full reference
    private boolean increaseSize() {
        QState<T> reference;
        final int[] stampHolder = new int[1];
        final int newStamp = stampGenerator.getAndIncrement();
        QState<T> newReference = null;
        T[] data_swap = null;
        int prevNewSize = 0;
        do {
            reference = this.qState.get(stampHolder);
            QState<T> temp = cachedSwapState.get();
            temp.reset(reference);

            // if 2 threads are doing increaseSize concurrently then the other needs to exit quietly
            if (!temp.isFull()) return true;
            final int currSize = temp.max_size;
            final long doubleCurrSize = currSize * (long) 2;
            int newSize;
            if (doubleCurrSize > INT_BOUNDARY_CHECK) newSize = INT_BOUNDARY_CHECK;
            else newSize = (int) doubleCurrSize;
            if (newSize <= currSize) return false;

            if (data_swap == null || newSize != prevNewSize) data_swap = (T[]) new Object[newSize];
            final int consIdx = (int) (temp.consumeIdx.get() % currSize);
            if (consIdx != 0) {
                // separately needs to copy both portions to the new array because it is full, so making it linear in new array
                System.arraycopy(temp.data, consIdx, data_swap, 0, currSize - consIdx);
                System.arraycopy(temp.data, 0, data_swap, currSize - consIdx, consIdx);
            } else {
                System.arraycopy(temp.data, 0, data_swap, 0, currSize);
            }
            if (newReference == null)
                newReference = new QState<>(data_swap, newSize, currSize);
            else newReference.reset(data_swap, currSize, newSize);
            prevNewSize = newSize;
        } while (!this.qState.compareAndSet(reference, newReference, stampHolder[0], newStamp));
        System.out.println("SUCCESSFULLY INCREASED CAPACITY "+newReference);
        return true;
    }

    // removes the head of queue and returns it, null if queue is empty
    public T remove() {
        QState<T> reference;
        final QState<T> newReference = cachedSwapState.get();
        final int newStamp = stampGenerator.getAndIncrement();
        final int[] stampHolder = new int[1];
        T value;
        do {
            reference = this.qState.get(stampHolder);
            newReference.reset(reference);
            if (newReference.isEmpty()) return null;
            value = newReference.remove();
        } while (!this.qState.compareAndSet(reference, newReference, stampHolder[0], newStamp));
        cachedSwapState.set(reference);
        return value;
    }

    //number of items currently in the queue
    public int size() {
        return this.qState.getReference().size();
    }

    public boolean isEmpty() {
        return this.qState.getReference().isEmpty();
    }

    public boolean isFull() {
        return this.qState.getReference().isFull();
    }

    public static void main(String[] args) {
        int capacity = 50;
        Random rand = new Random();
        CircularQueue<Long> queue = new CircularQueue<>(capacity);
        System.out.println(queue.isFull() + " " + queue.size() + " " + queue.isEmpty());
        LinkedList<Long> testList = new LinkedList<>();
        for (long l=0; l< (Long.MAX_VALUE -2); l++){

            final int toAdd = rand.nextInt(capacity/10);
            for(int i=0; i<toAdd; i++) {
                boolean res = queue.add(l);
                if (res) testList.offer(l);
                l++;
            }
            System.out.println(" size " + queue.size() + " "+queue.qState.getReference().toString() +" added till-> ["+(l-1) +"] total dded ="+toAdd);

            if(queue.size() > 10000 && capacity > (INT_BOUNDARY_CHECK -2)){
                final int toRemove = rand.nextInt(capacity/10);
                for(int i=0; i<toRemove; i++) {

                    if(testList.isEmpty() && queue.isEmpty()) {
                        System.out.println(i + " CAN NOT REMOVE FURTHER both queue Empty "+queue.qState.getReference());
                        break;
                    }
                    if(queue.isEmpty() && !testList.isEmpty()){
                        System.out.println(i + " ERROR queue Empty while test list not empty "+queue.qState.getReference() + " TEST Q SIZE "+testList.size());
                        System.exit(1);
                    }
                    if(!queue.isEmpty() && testList.isEmpty()){
                        System.out.println(i + " ERROR queue NOT Empty while test list IS empty "+queue.qState.getReference() + " Q SIZE "+queue.size());
                        System.exit(1);
                    }
                    Long value = queue.remove();
                    Long testVal = testList.remove();
                    if (!testVal.equals(value)) {
                        System.out.println(i + " ERROR removed actual " + value + " expected " + testVal);
                        System.exit(1);
                    }
                }
                System.out.println(" size " + queue.size() + " "+queue.qState.getReference().toString() +" total Removed "+toRemove);
            }

            capacity = queue.qState.getReference().max_size;
        }
    }
}