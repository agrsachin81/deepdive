package com.champsworld.concurrent;

import com.google.common.collect.TreeMultimap;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * A ThreadPool executor that ensures ordering of task, where ordering of task is needed based on an orderingId/ConcurrencyId
 * It uses singleThreadExecutor to execute all the task submitted with the same orderingId
 * so that the same orderingId will always be submitted in the same order they arrive
 * by default multiple singleThreadExecutors are created (number of processors)
 * It uses a lru approach for memory cleanup, (for Tasks/OrderingId which are no longer in use)
 * @author agrsachin81
 */
public class OrderedTaskExecutor {
    public static final int MAX_SINGLE_THREAD_POOL_COUNT = 100;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean shutdownNow = new AtomicBoolean(false);
    private final int execArrayLength;
    private final AtomicReferenceArray<ExecutorService> singleThreadPoolExecutor;
    private final NextPoolIndexCalculator threadPoolIndexCalculator;

    /**
     * The OrderedTaskExecutor keeps the decision of last Thread Pool used for each concurrencyId/orderingId; in memory.
     * maxCapacity is used to delete least recently used concurrencyId/orderingId's
     * @param maxCapacity, the maximum unique OrderingIds at a point in time
     */
    public OrderedTaskExecutor(int maxCapacity) {
        this.execArrayLength = Math.min(Runtime.getRuntime().availableProcessors(), MAX_SINGLE_THREAD_POOL_COUNT);
        this.singleThreadPoolExecutor = new AtomicReferenceArray<>(this.execArrayLength);
        threadPoolIndexCalculator = new NextPoolIndexCalculator(this.execArrayLength, maxCapacity);
        //TODO: log with Info level
        System.out.println("OrderedTaskExecutor CREATED " + System.identityHashCode(this));
    }

    public Future<?> submit(OrderedTask<?> task) {
        if (task == null) throw new NullPointerException("Unable to execute null");
        if (shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Already shutdown");
        final int executorIndexForOrderingId = threadPoolIndexCalculator.getThreadPoolExecIndex(task.orderingId());

        if (singleThreadPoolExecutor.get(executorIndexForOrderingId) == null) {
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            if (!singleThreadPoolExecutor.compareAndSet(executorIndexForOrderingId, null, executor)) {
                // in case an executor already set for this index we must destroy it
                // must catch throwable so that we complete the return of exeIndex
                //TODO: VERBOSE LEVEL LOG
                System.out.println("EXEC INDEX " + executorIndexForOrderingId + " already USED ");
                try {
                    executor.shutdownNow();
                } catch (Throwable ignored) {
                }
            } else {
                //TODO: VERBOSE LEVEL LOG
                System.out.println("EXEC INDEX " + executorIndexForOrderingId + " SUCCESSFULLY USED ");
            }
        }

        if (shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Already shutdown");
        return this.singleThreadPoolExecutor.get(executorIndexForOrderingId).submit(task);
    }

    /**
     * calls shutdown on all underlying executors and collects Exception/Errors from them
     * does nothing if this method or shutdownNow is already called
     *
     * @return a list of PermissionException and SecurityException if returned from any else returns empty
     */
    public List<Throwable> shutdown() {
        List<Throwable> exc = Collections.emptyList();
        if (this.shutdown.compareAndSet(false, true)) {
            //TODO: INFO LEVEL LOG
            System.out.println("OrderedTaskExecutor Shutdown " + System.identityHashCode(this));
            exc = new ArrayList<>();
            //TODO: INFO LEVEL LOG
            for (int i = 0; i < execArrayLength; i++) {
                final ExecutorService executor = this.singleThreadPoolExecutor.get(i);
                if (executor != null) {
                    try {
                        if (!executor.isShutdown() && !executor.isTerminated()) executor.shutdown();
                    } catch (Throwable e) {
                        exc.add(e);
                    }
                }
            }
            threadPoolIndexCalculator.clear();
        }
        return exc;
    }

    public boolean isShutdown(){
        return this.shutdown.get() || this.shutdownNow.get();
    }

    /**
     * calls shutdownNow on all underlying executors and collects runnable and Exceptions/Errors from them
     * does nothing if this method is already called
     *
     * @return object array of size 2 first is list of Runnable , second is List of Throwable
     */
    public Object[] shutdownNow() {
        List<Runnable> shutList = Collections.emptyList();
        List<Throwable> exc = Collections.emptyList();
        if (this.shutdownNow.compareAndSet(false, true)) {
            this.shutdown.set(true);
            //TODO: INFO LEVEL LOG
            System.out.println("OrderedTaskExecutor ShutdownNow " + System.identityHashCode(this));
            shutList = new ArrayList<>();
            exc = new ArrayList<>();
            // no harm in clearing again
            threadPoolIndexCalculator.clear();
            for (int i = 0; i < execArrayLength; i++) {
                final ExecutorService executor = this.singleThreadPoolExecutor.get(i);
                if (executor != null) {
                    // following is done to avoid calling ths shutdownNow repeatedly on same object
                    if (this.singleThreadPoolExecutor.compareAndSet(i, executor, null)) {
                        try {
                            if (!executor.isTerminated()) shutList.addAll(executor.shutdownNow());
                        } catch (Throwable e) {
                            exc.add(e);
                        }
                    }
                }
            }
        }
        return new Object[]{shutList, exc};
    }

    private static class NextPoolIndexCalculator {

        /**
         * Ordering id vs Index; a lru cache implementation ensuring least used orderingId is removed from cache
         * to prevent memory leak
         */
        private final LinkedHashMap<Integer, Integer> lruMap;

        /**
         * used to ensure O(1) time complexity, while allotting index for a orderingId
         * count vs list of indexes to keep the index on count with a tree map, we know the least allotted index
         * so fairness is ensured by keeping count of each index, then we update the reverse map indexCount array
         */
        private final TreeMultimap<Integer, Integer> countVsIndexMap = TreeMultimap.create();

        /**
         * used to ensure O(1) time complexity, while removing ordering id from lruCache
         * reverse map to quickly decrease the count for an index
         * after this we update the reverse mapping
         */
        private final int[] indexCount;
        private final int size;

        NextPoolIndexCalculator(final int size, final int maxCapacity) {
            this.size = size;
            indexCount = new int[size];
            fillDefaultCountWithAllIndexes(size);
            lruMap = new LinkedHashMap<Integer, Integer>(101, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
                    // TODO: decrease count for the index of this ordering Id
                    boolean remove = size() > maxCapacity;
                    if (remove) decreaseCountForIndex(eldest.getValue());
                    return remove;
                }
            };
        }

        private void fillDefaultCountWithAllIndexes(int size) {
            for (int i = 0; i < size; i++) {
                //initially all counts are zero
                countVsIndexMap.put(0, i);
                indexCount[i] = 0;
            }
        }

        void decreaseCountForIndex(Integer index) {
            // this is called from remove eldest entry, which in turn called from put or put all,
            // which is called from getThreadPoolExecIndex
            // hence we always lock lruMap first
            synchronized (countVsIndexMap) {
                final int count = indexCount[index];
                if (countVsIndexMap.containsKey(count))
                    countVsIndexMap.remove(count, index);
                countVsIndexMap.put(count - 1, index);
            }
        }

        Integer getThreadPoolExecIndex(Integer orderingId) {
            synchronized (lruMap) {
                if (lruMap.containsKey(orderingId)) {
                    return lruMap.get(orderingId);
                } else {
                    synchronized (countVsIndexMap) {
                        // allocation of index is done is done which is least used
                        int lowestCount = -1;
                        NavigableSet<Integer> indexes = null;
                        Integer index = null;
                        while (indexes == null || index == null) {
                            if (indexes != null)
                                countVsIndexMap.removeAll(lowestCount);
                            if (countVsIndexMap.isEmpty()) {
                                fillDefaultCountWithAllIndexes(this.size);
                            }
                            lowestCount = countVsIndexMap.keySet().first();
                            indexes = countVsIndexMap.get(lowestCount);
                            // this removes the index from the value set
                            index = indexes.pollFirst();
                        }
                        // we put it back with a different count
                        final int newCount = lowestCount + 1;
                        countVsIndexMap.put(newCount, index);
                        indexCount[index] = newCount;
                        lruMap.put(orderingId, index);
                        return index;
                    }
                }
            }
        }

        public void clear() {
            synchronized (lruMap) {
                synchronized (countVsIndexMap) {
                    lruMap.clear();
                    countVsIndexMap.clear();
                    Arrays.fill(indexCount, 0);
                }
            }
        }
    }

    public static void main(String[] args) {
        OrderedTaskExecutor executor = new OrderedTaskExecutor(2000);
        OrderedTask<String> task = new SampleOrderedTask("Sample");
        List<Future<?>> results = new ArrayList<>();
        for (int i=0; i< 20; i++){
            results.add(executor.submit(task));
            results.add(executor.submit(new SampleOrderedTask("Sam" +i, i %5)));
            //System.out.println(retVal.get());
        }
        for (Future<?> fut: results) {
            try {
                System.out.println(fut.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        List<Throwable> value = executor.shutdown();
        for (Throwable throwable:value) {
            System.out.println(throwable.getMessage());
        }
        System.out.println(executor.isShutdown());
        Object[] array = executor.shutdownNow();
        for (Runnable val: (List<Runnable>)array[0]) {
            System.out.println(" Runnable returned "+val.toString());
        }

        for (Throwable val: (List<Throwable>)array[1]) {
            System.out.println(" Throw returned "+val.getMessage());
        }
    }
}
