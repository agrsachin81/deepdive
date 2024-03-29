package com.champsworld.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author agrsachin81
 */
public class MultiSubmittedOrderedTask implements OrderedTask<String>{

    private final String prefix;
    private final int newOrderingId;

    private final AtomicInteger resultCounter = new AtomicInteger(1);

    public MultiSubmittedOrderedTask(String prefix) {
        this.prefix = prefix;
        this.newOrderingId = OrderedTask.super.orderingId();
    }

    private final Map<Long, Integer> execTimeStamps = new HashMap<>();

    @Override
    public int orderingId() {
        return this.newOrderingId;
    }

    public MultiSubmittedOrderedTask(String prefix, int orderingId) {
        this.prefix = prefix;
        this.newOrderingId = orderingId;
    }

    private final BlockingDeque<Integer> submissionIds = new LinkedBlockingDeque<>();

    public CompletableFuture<String> addSubmission(int subId, OrderedTaskExecutor executor){
        synchronized (this) {
            submissionIds.offer(subId);
            return executor.submit(this);
        }
    }

    public String call() {
        synchronized (this) {
            ts = System.nanoTime();
            try {
                counter = submissionIds.take();
            } catch (InterruptedException ignore) {
                counter = -100;
            }
            execTimeStamps.put(ts, counter);
            return prefix + " Completed " + counter + " ConID -->[" + orderingId() + "] " + Thread.currentThread().getName() + " " + ts;
        }
    }

    @Override
    public String get() {
        return call();
    }

    public TreeMap<Long, Integer> getExecTimeStamps() {
        return new TreeMap<>(execTimeStamps);
    }

    private volatile  int counter ;
    private volatile long ts;
    @Override
    public String toString() {
        return "MultiSubmittedOrderedTask{" +
                "prefix='" + prefix + '\'' +
                ", newOrderingId=" + newOrderingId +
                '}';
    }
}
