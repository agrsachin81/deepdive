package com.champsworld.concurrent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * It uses a brute force approach for memory cleanup, (for Tasks/OrderingId which are no longer in use)
 * A ThreadPool executor that ensures ordering of task, where ordering of task is needed based on an ordering Id
 * It uses singleThreadExecutor to execute all the task submitted with the same orderingId
 * so that the same orderingId will always be submitted in the same order they arrive
 * by default multiple singleThreadExecutors are created (number of processors)
 * The memory leak is cleaned using WeakReference , ReferenceQueue based notifications
 * @author agrsachin81
 */
public class OrderedTaskExecutorRaw {
    private final AtomicReferenceArray<ExecutorService> singleThreadPoolExecutor;
    private final ConcurrentHashMap<Integer, Integer> orderingIdPoolIndexMap;

    /**
     * for avoiding memory leak
     * to manage orderingId task count. so that when count reaches zero, it can be removed from orderingIdPoolIndexMap and itself
     */
    private final ConcurrentHashMap<Integer, AtomicInteger> orderingIdCount;

    /**
     * for avoiding memory leak
     * It is used to decrease the orderingId count when item is added to reference queue
     * idea is to create a single entry in this map per OrderedTask object NOT per ordering Id,
     * the key are based on System.identityHashcode of the Task Object.
     * Because it may happen that implementer has overridden hashCode method
    */
    private final HashMap<Integer, InterWeakReference> taskWeakReferenceMap;
    private final AtomicInteger execIdGenerator = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean shutdownNow = new AtomicBoolean(false);

    private final AtomicBoolean reaperThreadStarted = new AtomicBoolean(false);
    private final AtomicReference<Thread> reaperThread = new AtomicReference<>(null);
    private final int execArrayLength ;
    /**
     * for avoiding memory leak
     * this acts as a notification queue when gc add it to reference queue to gc
     * can not be shared outside at all
     */
    private final ReferenceQueue<OrderedTask<?>> gcEdTaskNotificationQueue = new ReferenceQueue<>();

    public OrderedTaskExecutorRaw(){
        this.execArrayLength = Runtime.getRuntime().availableProcessors();
        this.singleThreadPoolExecutor = new AtomicReferenceArray<>(this.execArrayLength);
        orderingIdPoolIndexMap = new ConcurrentHashMap<>();
        taskWeakReferenceMap = new HashMap<>();
        orderingIdCount = new ConcurrentHashMap<>();

        //TODO: log with Info level
        System.out.println("OrderedTaskExecutor CREATED "+System.identityHashCode(this));
    }

    public Future<?> submit(OrderedTask<?> task){
        if(task ==null) throw new NullPointerException("Unable to execute null");
        if(shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Already shutdown");
        final int executorIndexForOrderingId = orderingIdPoolIndexMap.computeIfAbsent(task.orderingId(), (orderingId)->{
            final int id = execIdGenerator.getAndIncrement();
            final int exeIndex = id % execArrayLength;
            //access to this is protected by computeIfAbsent
            if (singleThreadPoolExecutor.get(exeIndex) == null) {
                final ExecutorService executor = Executors.newSingleThreadExecutor();
                if (!singleThreadPoolExecutor.compareAndSet(exeIndex, null, executor)) {
                    // in case an executor already set for this index we must destroy it
                    // must catch throwable so that we complete the return of exeIndex
                    //TODO: VERBOSE LEVEL LOG
                    System.out.println("EXEC INDEX " + exeIndex + " already USED for ID " + id);
                    try {
                        executor.shutdownNow();
                    } catch (Throwable ignored) {
                    }
                } else {
                    //TODO: VERBOSE LEVEL LOG
                    System.out.println("EXEC INDEX " + exeIndex + " SUCCESSFULLY USED for ID " + id);
                }
            }
            return exeIndex;
        });
        if(shutdownNow.get() || shutdown.get()) throw new RejectedExecutionException("Already shutdown");
        try {
            Future<?> result = this.singleThreadPoolExecutor.get(executorIndexForOrderingId).submit(task);
            //following is done to avoid memory leak
            getOrderingIdCount(task, true);
            // we need to explicitly manage threadSafety a single WeakReference for a single Task object
            // ordered Task may or may not be immutable, so we have to use their System.identifyHashCode as their hashcode and in equals
            synchronized (taskWeakReferenceMap) {
                // we need to ensure foe one OrderedTask instance one is created
                // so that the counter can be decremented and task itself is cleared
                final int hc = System.identityHashCode(task);
                if (!taskWeakReferenceMap.containsKey(hc)) {
                    // further we need to have a kind of notification when the task is scheduled for garbage collected
                    // inside InterWeakReference also uses System.identityCode
                    taskWeakReferenceMap.put(hc, new InterWeakReference(task, gcEdTaskNotificationQueue));
                }
            }
            startReaperThread();
            return result;
        } catch (Throwable t){
            //if prev count is zero then remove it from main map, as it will never be notified from gcEdTaskNotificationQueue
            if(getOrderingIdCount(task, false) ==0){
                orderingIdPoolIndexMap.remove(task.orderingId());
            }
            throw t;
        }
    }

    private int getOrderingIdCount(OrderedTask<?> task, boolean increase) {
        final AtomicInteger count ;
        if(increase) {
            count = orderingIdCount.compute(task.orderingId(), (orderingId, currentCount) -> {
                if (currentCount == null) {
                    return new AtomicInteger(1);
                } else {
                    currentCount.getAndIncrement();
                    return currentCount;
                }
            });
        } else {
            count = orderingIdCount.get(task.orderingId());
        }
        final int res = (count ==null) ? 0 : count.get();
        // TODO: verbose log
        System.out.println("CURRENT COUNT for ID ["+ task.orderingId() +"] is ["+ res+"]");
        return res;
    }

    /**
     * calls shutdown on all underlying executors and collects Exception/Errors from them
     * does nothing if this method or shutdownNow is already called
     *
     * @return a list of PermissionException and SecurityException if returned from any else returns empty
     */
    public List<Throwable> shutdown(){
        List<Throwable> exc = Collections.emptyList();
        if(this.shutdown.compareAndSet(false, true)) {
            //TODO: INFO LEVEL LOG
            System.out.println("OrderedTaskExecutor Shutdown "+System.identityHashCode(this));
            exc = new ArrayList<>();
            //TODO: INFO LEVEL LOG
            for (int i = 0; i < execArrayLength; i++) {
                final ExecutorService executor = this.singleThreadPoolExecutor.get(i);
                if (executor != null) {
                    try {
                        if(!executor.isShutdown() && !executor.isTerminated()) executor.shutdown();
                    } catch (Throwable e){
                        exc.add(e);
                    }
                }
            }
            orderingIdPoolIndexMap.clear();
            clearWeakReferenceMap();
            orderingIdCount.clear();
            //not calling interrupt on reaper thread; the thread will see shutdown flag and exit
        }
        return exc;
    }

    /**
     * calls shutdownNow on all underlying executors and collects runnable and Exceptions/Errors from them
     * does nothing if this method is already called
     * @return object array of size 2 first is list of Runnable , second is List of Throwable
     */
    public Object[] shutdownNow() {
        List<Runnable> shutList = Collections.emptyList();
        List<Throwable> exc = Collections.emptyList();
        if(this.shutdownNow.compareAndSet(false, true)) {
            this.shutdown.set(true);
            //TODO: INFO LEVEL LOG
            System.out.println("OrderedTaskExecutor ShutdownNow "+System.identityHashCode(this));
            shutList = new ArrayList<>();
            exc = new ArrayList<>();
            // no harm in clearing again
            orderingIdPoolIndexMap.clear();
            clearWeakReferenceMap();
            orderingIdCount.clear();
            try {
                if(reaperThread.get()!=null && reaperThread.get().isAlive()) reaperThread.get().interrupt();
                // catching to making sure the next part executes
            } catch (SecurityException ignore) {
                //TODO: can not ignore must log this with ERROR level
            }
            for (int i = 0; i < execArrayLength; i++) {
                final ExecutorService executor = this.singleThreadPoolExecutor.get(i);
                if (executor != null) {
                    // following is done to avoid calling ths shutdownNow repeatedly on same object
                    if(this.singleThreadPoolExecutor.compareAndSet(i, executor, null)) {
                        try {
                            if (!executor.isTerminated()) shutList.addAll(executor.shutdownNow());
                        } catch (Throwable e) {
                            exc.add(e);
                        }
                    }
                }
            }
        }
        return new Object[] {shutList, exc};
    }

    /**
     * The reference of the instance of ths class must never escape outside the class
     * The reaper thread polls the ReferenceQueue for any WeakReference enqueued marking a particular task object
     * not submitted to this Executor any longer
     */
    private static class ReaperThread extends Thread {
        private volatile OrderedTaskExecutorRaw outside;
        public ReaperThread(OrderedTaskExecutorRaw outsideReference) {
            this.outside = outsideReference;
        }

        @Override
        public void run() {
            if(this.outside ==null) return;
            //TODO: log with info level logging
            System.out.println("OrderedTaskExecutor REAPER THREAD STARTED " + System.identityHashCode(outside));
            try {
                while (!outside.shutdown.get() && !outside.shutdownNow.get()) {
                    outside.checkReferenceQueue();
                    if (isInterrupted()) break;
                }
            } catch (InterruptedException e) {
                // thread is interrupted means exit
                //todo log info level thread exited due to Interrupt
            }
            //TODO: log with info level logging
            System.out.println("OrderedTaskExecutor REAPER THREAD EXITED " + System.identityHashCode(outside));
            this.outside = null;
        }
    }

    private void startReaperThread(){
        if(reaperThreadStarted.compareAndSet(false, true)){
            Thread clearInternalCacheThread = new ReaperThread(this);
            reaperThread.set(clearInternalCacheThread);
            try {
                clearInternalCacheThread.setDaemon(true);
            } catch (Throwable ignore){}
            clearInternalCacheThread.start();
        }
    }

    /*
    Must throw Exception to let know thread it has been interrupted
     */
    private void checkReferenceQueue() throws InterruptedException {
        // waits for 500 millis only if not present
        Reference<? extends OrderedTask<?>> refItem = gcEdTaskNotificationQueue.remove(500);
        if(refItem!=null) {
            InterWeakReference taskReference = (InterWeakReference) refItem;
            OrderedTask<?> task = taskReference.get();
            // task is always null testing using WeakReferenceTests
            // TODO: info level log if task is null or not
            final int orderingId = taskReference.orderingId;
            AtomicInteger count = orderingIdCount.computeIfPresent(orderingId, (Id, currentCount) -> {
                currentCount.decrementAndGet();
                return currentCount;
            });
            //after shutdown count will be null
            if (count == null || count.get() <= 0) {
                orderingIdCount.remove(orderingId);
                orderingIdPoolIndexMap.remove(orderingId);
            }
            //we must clear then the task map in either case as the taskWeakReferenceMap will have leak
            taskWeakReferenceMap.remove(taskReference.hashCode);
        }
    }

    private void clearWeakReferenceMap(){
        //clearing the taskWeakReference map will stop adding reference to queue when they are actually collected
        // since the weakReference itself is not reachable hence it will be gced
        try {
            for (Map.Entry<Integer, InterWeakReference> orderedTaskWeakReferenceEntry : taskWeakReferenceMap.entrySet()) {
                orderedTaskWeakReferenceEntry.getValue().clear();
            }
        } catch (Throwable ignore){}
        taskWeakReferenceMap.clear();
    }

    /**
     * It is necessary to cache hashCod and OrderingId both because ReferenceQueue returns only WekReference inside item is already cleared
     */
    private static class InterWeakReference extends WeakReference<OrderedTask<?>>{

        private final int hashCode ;
        private final int orderingId;
        public InterWeakReference(OrderedTask<?> referent) {
            super(referent);
            this.orderingId = referent.orderingId();
            // mandatory use of System.identityCode else key of map will not match
            this.hashCode = System.identityHashCode(referent);
        }

        public InterWeakReference(OrderedTask<?> referent, ReferenceQueue<OrderedTask<?>> queue) {
            super(referent, queue);
            this.orderingId = referent.orderingId();
            // mandatory use of System.identityCode else key of map will not match
            this.hashCode = System.identityHashCode(referent);
        }
    }
}
