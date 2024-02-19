package com.champsworld.ds;

import java.util.concurrent.atomic.*;


/**
 * Array based Queue implementation (circular queue) FIFO
 * Non blocking implementation
 * make it grow by amortized way
 **/
public final class CircularQueue<T> {

    private static final int INT_BOUNDARY_CHECK = Integer.MAX_VALUE - 1;

    //use ThreadLocal to recycle object, all the items inside must be volatile then
    //increase size data array is automatically shared with both objects, since with add and remove we dont change data reference at all
    private static class QState<V> {
        volatile V[] data;
        AtomicInteger consumeIdx ;
        AtomicInteger produceIdx;
        volatile int max_size;

        private QState(int max) {
            this.data = (V[]) new Object[max];
            this.produceIdx = new AtomicInteger(0);
            this.consumeIdx = new AtomicInteger(0);
            this.max_size = max;
        }

        // for expanded
        private QState(V[] data, int max, int prod, int cons) {
            this.data = data;
            this.produceIdx = new AtomicInteger(prod);
            this.consumeIdx = new AtomicInteger(cons);
            this.max_size = max;
        }

        void addValue(V data){
            final int dataIdx = produceIdx.getAndIncrement() % max_size;
            this.data[dataIdx] = data;
        }

        V remove(){
            int dataIdx = consumeIdx.getAndIncrement() % max_size;
            final V value = data[dataIdx];
            data[dataIdx] = null;
            return value;
        }


        void reset(QState<V> state) {
            // case arrives when old cached copy has lesser size data array but new has grown
            if (max_size < state.max_size)
                this.data = (V[]) new Object[state.max_size];

            System.arraycopy(state.data, 0, this.data, 0, state.max_size);

            this.produceIdx.set(state.produceIdx.get());
            this.consumeIdx.set(state.consumeIdx.get());
            this.max_size = state.max_size;
        }

        void reset(V[] data, int prod, int cons, int max) {
            //used while reset is used on a newly created object in a loop
            this.data = data;
            this.produceIdx.set(prod);
            this.consumeIdx.set(cons);
            this.max_size = max;
        }

        private boolean isFull() {
            return (produceIdx.get() - consumeIdx.get()) == max_size;
        }

        private boolean isEmpty() {
            return produceIdx.get() == consumeIdx.get();
        }

        public int size() {
            return produceIdx.get() - consumeIdx.get();
        }

        // produce idx is supposed to be always larger than consumeIdx
        // hence overfill will be managed by add only
        private void checkOverfill() {
            if (produceIdx.get() == INT_BOUNDARY_CHECK) {
                final int newCondIdx = consumeIdx.get() % max_size;
                //since isEmpty, isFull, size depends on the notion that produceIdx is always bigger or equal then the consumeIdx
                final int currIdx = produceIdx.get() % max_size;
                produceIdx.set(currIdx < newCondIdx ? (currIdx +max_size) : currIdx);
                consumeIdx.set(newCondIdx);
            }
        }
    }

    //we have to use AtomicReference, so for each increment we create new object as compareAndSet uses '==' and NOT equals  
    //if we use AtomicInteger we have to use getAndIncrement which will produce error when queue is full, our counter is overshot
    // if we use AtomicInteger and we use get for checking full, then we shall create a race condition, values will be overridden


    // stamped reference is needed for ABA problem since reference objects are recycled, it is also used for unique write signature 
    private final AtomicStampedReference<QState<T>> qState;
    private final ThreadLocal<QState<T>> cachedSwapState;
    private final AtomicInteger stampGenerator = new AtomicInteger(1);

    public CircularQueue(int initial_capacity) {
        if(initial_capacity > INT_BOUNDARY_CHECK) throw new IllegalStateException("Invalid initial Size can not be larger then "+INT_BOUNDARY_CHECK);
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
            // if 2 threads are doing increaseSize concurrently then the other needs to exit
            if (!reference.isFull()) return true;
            final int currSize = reference.max_size;
            final long doubleCurrSize = currSize * (long) 2;
            int newSize ;
            if (doubleCurrSize > INT_BOUNDARY_CHECK) newSize = INT_BOUNDARY_CHECK;
            else newSize = (int) doubleCurrSize;
            if (newSize <= currSize) return false;
            if (data_swap == null || newSize != prevNewSize) data_swap = (T[]) new Object[newSize];
            final int consIdx = reference.consumeIdx.get() % currSize;
            if (consIdx != 0) {
                // separately needs to copy both portions to the new array because it is full, so making it linear in new array
                System.arraycopy(reference.data, consIdx, data_swap, 0, currSize - consIdx);
                System.arraycopy(reference.data, 0, data_swap, currSize - consIdx, consIdx);
            } else {
                System.arraycopy(reference.data, 0, data_swap, 0, currSize);
            }
            if (newReference == null)
                newReference = new QState<>(data_swap, newSize, currSize, 0);
            else newReference.reset(data_swap, currSize, 0, newSize);
            prevNewSize = newSize;
        } while (!this.qState.compareAndSet(reference, newReference, stampHolder[0], newStamp));
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
            value = reference.remove();
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
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        boolean res = queue.add(1);
        Integer value = queue.remove();
        System.out.println(value + " addRe "+res);
        System.out.println(queue.isFull() + " "+queue.size() +" "+queue.isEmpty());
    }
}