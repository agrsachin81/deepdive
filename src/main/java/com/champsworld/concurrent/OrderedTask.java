package com.champsworld.concurrent;

import java.util.concurrent.Callable;

/**
 * The Ordered Task with having same orderingId/concurrencyId will be executed in order of submitting.
 * Ideally the orderingId should be object System hashcode as it is the unique for each object
 * @param <V>
 *
 * @author agrsachin81
 *
 */
public interface OrderedTask<V> extends Callable<V> {

    /**
     * the concurrencyId, Ideally the orderingId is System hashcode as it is the unique for each object in java
     * by default every task has a unique OrderingId ensuring maximum concurrent behavior
     * Should be overridden by individual subtasks with the value the System.identityHashCode of parent Task (of all subtasks)
     * to ensure no reordering happens between subtasks of the parent tasks
     * @return the unique id identifying the unique ordering for the same group of Tasks
     */
    default public int orderingId(){
        return System.identityHashCode(this);
    }
}
