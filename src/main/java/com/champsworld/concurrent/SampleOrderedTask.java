package com.champsworld.concurrent;

import java.util.concurrent.atomic.AtomicInteger;

public class SampleOrderedTask implements OrderedTask<String>{

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

    @Override
    public String call() throws Exception {
        return prefix+ " Completed "+ resultCounter.getAndIncrement() +" ConID -->["+orderingId() +"] "+Thread.currentThread().getName();
    }
}
