package com.champsworld.concurrent;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author agrsachin81
 */
public class SampleOrderedTask implements OrderedTask<String> {

    private final String prefix;
    private final int newOrderingId;

    private final static AtomicInteger resultCounter = new AtomicInteger(1);

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
        ts = System.nanoTime();
        counter = resultCounter.getAndIncrement();
        return prefix+ " Completed "+ counter +" ConID -->["+orderingId() +"] "+Thread.currentThread().getName() +" "+ts;
    }

    @Override
    public String get() {
        return call();
    }

    private volatile  int counter ;
    private volatile long ts;

    public Object[] getLastExec(){
        return new Object[]{prefix, counter, ts};
    }

    @Override
    public String toString() {
        return "SampleOrderedTask{" +
                "prefix='" + prefix + '\'' +
                ", newOrderingId=" + newOrderingId +
                '}';
    }
}