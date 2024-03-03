package com.champsworld.concurrent;

/**
 * @author agrsachin81
 */
public interface OrderedTaskSpec<T> {
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
