package com.champsworld.concurrent;

import com.champsworld.algo.FixedResourceAllocator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
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
    public static final int MAX_CONCURRENCY_ID_ALLOWED_AT_A_TIME = (Integer.MAX_VALUE-1) /2;

    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean shutdownNow = new AtomicBoolean(false);
    private final int execArrayLength;
    private final AtomicReferenceArray<ExecutorService> singleThreadPoolExecutor;
    private final FixedResourceAllocator threadPoolIndexCalculator;

    /**
     * The OrderedTaskExecutor keeps the decision of last Thread Pool used for each concurrencyId/orderingId; in memory.
     * maxCapacity is used to delete least recently used concurrencyId/orderingId's (half the max of Integer.MAX_VALUE)
     * @param maxCapacity, the maximum number of unique OrderingIds present at a point in time, for which tasks are executing
     */
    public OrderedTaskExecutor(int maxCapacity) {
        if(maxCapacity >= MAX_CONCURRENCY_ID_ALLOWED_AT_A_TIME)
            throw new IllegalArgumentException("MAX CONCURRENCY ALLOWED IS < "+MAX_CONCURRENCY_ID_ALLOWED_AT_A_TIME);
        this.execArrayLength = Math.min(Runtime.getRuntime().availableProcessors(), MAX_SINGLE_THREAD_POOL_COUNT);
        this.singleThreadPoolExecutor = new AtomicReferenceArray<>(this.execArrayLength);
        threadPoolIndexCalculator = new FixedResourceAllocator(this.execArrayLength, maxCapacity);
        //TODO: log with Info level
        //"OrderedTaskExecutor CREATED " + System.identityHashCode(this));
    }

    public <T> CompletableFuture<T> submit(OrderedCallable<T> task, int genNextUpdateId) {
        if (task == null) throw new NullPointerException("Unable to execute null-" +genNextUpdateId);
        return CompletableFuture.supplyAsync(()->{
            try {
                return task.call();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }, getExecutorService(task.orderingId(), genNextUpdateId));
    }

    public <T> CompletableFuture<T> submit(OrderedTask<T> task, int genNextUpdateId) {
        if (task == null) throw new NullPointerException("Unable to execute null-" +genNextUpdateId);
        return CompletableFuture.supplyAsync(task, getExecutorService(task.orderingId(), genNextUpdateId));
    }

    private ExecutorService getExecutorService(int taskOrderingId, final int genNextUpdateId) {
        if (shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Executor is already shutdown; rejected "+taskOrderingId +" ,"+genNextUpdateId);
        final int executorIndexForOrderingId = threadPoolIndexCalculator.getResourceIndex(taskOrderingId);
        if (singleThreadPoolExecutor.get(executorIndexForOrderingId) == null) {
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            if (!singleThreadPoolExecutor.compareAndSet(executorIndexForOrderingId, null, executor)) {
                // in case an executor already set for this index we must destroy it
                // must catch throwable so that we complete the return of exeIndex
                //TODO: VERBOSE LEVEL LOG
                //"EXEC INDEX " + executorIndexForOrderingId + " already USED "+taskOrderingId +" ,"+genNextUpdateId);
                try {
                    executor.shutdownNow();
                } catch (Throwable ignored) {
                }
            } else {
                //TODO: VERBOSE LEVEL LOG
                //"EXEC INDEX " + executorIndexForOrderingId + " SUCCESSFULLY USED "+taskOrderingId +" ,"+genNextUpdateId);
            }
        }

        if (shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Already shutdown" +taskOrderingId +" ,"+genNextUpdateId);
        return this.singleThreadPoolExecutor.get(executorIndexForOrderingId);
    }

    public int getNextUpdateId(int orderingId){
        return threadPoolIndexCalculator.getNextUpdatedId(orderingId);
    }

    public <T> CompletableFuture<T> submit(OrderedTask<T> task) {
        return submit(task, getNextUpdateId(task.orderingId()));
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
            //"OrderedTaskExecutor Shutdown " + System.identityHashCode(this));
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
            //"OrderedTaskExecutor ShutdownNow " + System.identityHashCode(this));
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
}
