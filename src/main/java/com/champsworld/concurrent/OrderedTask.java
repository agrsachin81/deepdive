package com.champsworld.concurrent;

import java.util.function.Supplier;

/**
 * get is called asynchronously with in ordering id bound
 * @author agrsachin81
 */
public interface OrderedTask<V> extends OrderedTaskSpec<V>, Supplier<V> {

}
