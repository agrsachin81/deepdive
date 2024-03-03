package com.champsworld.concurrent;

import java.util.function.Supplier;

/**
 * The Ordered Task with having same orderingId/concurrencyId will be executed in order of submitting.
 * Ideally the orderingId should be object System hashcode as it is the unique for each object
 * @param <V>
 * @author agrsachin81
 */
public interface OrderedTask<V> extends OrderedTaskSpec<V>, Supplier<V> {
    
}
