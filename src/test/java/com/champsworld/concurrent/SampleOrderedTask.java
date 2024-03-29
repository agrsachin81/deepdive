package com.champsworld.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author agrsachin81
 */
public class SampleOrderedTask implements OrderedTask<String> {

    private final String prefix;
    private final int newOrderingId;

    private final AtomicInteger resultCounter = new AtomicInteger(1);


    public SampleOrderedTask(String prefix) {
        this.prefix = prefix;
        this.newOrderingId = OrderedTask.super.orderingId();
    }

    @Override
    public int orderingId() {
        return this.newOrderingId;
    }

    public SampleOrderedTask(String prefix, int orderingId) {
        this.prefix = prefix;
        this.newOrderingId = orderingId;
    }

    public String call() {
        return prefix+ " Completed "+ resultCounter.getAndIncrement() +" ConID -->["+orderingId() +"] "+Thread.currentThread().getName();
    }

    @Override
    public String get() {
        return call();
    }
}