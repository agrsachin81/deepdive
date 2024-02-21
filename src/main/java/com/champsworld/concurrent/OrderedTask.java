package com.champsworld.concurrent;

import java.util.concurrent.Callable;

/**
 * The Ordered Task with having same Id will be executed in order of submitting.
 * Ideally the orderingId should be object hashcode as it is the unique for each object
 * @param <V>
 */
public abstract class OrderedTask<V> implements Callable<V> {

    private final int sysIdentityHashCode;

    public OrderedTask(){
        this.sysIdentityHashCode = System.identityHashCode(this);
    }

    /**
     * the concurrencyId, Ideally the orderingId is System hashcode as it is the unique for each object in java
     * by default every task has a unique OrderingId ensuring maximum concurrent behavior
     * Should be overridden by individual subtasks with the value the System.identityHashCode of parent Task (of all subtasks)
     * to ensure no reordering happens between subtasks of the parent tasks
     * @return the unique id identifying the unique ordering for the same group of Tasks
     */
    public int orderingId(){
        return System.identityHashCode(this);
    }

    /*
     * finalized for memory leak
     * equals and hashcode are made final with System.identityHashCode so that we know when exactly they have been garbage collected
     * because they are put in a HashMap as a Key to be garbage collected enqueued WeakReference
     */
    final public boolean equals(Object obj) {
        return this == obj ;
    }

    /**
     * finalized for memory leak
     * @return always returns System.identityHashCode
     */
    final public int hashCode() {
        return sysIdentityHashCode;
    }
}
