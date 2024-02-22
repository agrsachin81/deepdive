package com.champsworld.learn;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class acts a mock test object for many WeakReference and other tests
 * @author agrsachin81
 */
public class FinalizeTest {

    private static final AtomicInteger idGen = new AtomicInteger(0);
    private final int id;
    private final Map<?, ?> map;

    public FinalizeTest(Map<?, ?> testMap) {
        id = idGen.getAndIncrement();
        System.out.println("CREATED finalize TEST " + id + " " + System.identityHashCode(this) + " --map = " + System.identityHashCode(testMap));
        this.map = testMap;
    }

    public FinalizeTest() {
        this(null);
    }

    @Override
    protected void finalize() throws Throwable {
        System.out.println("finalizeTest, Finalized with id =" + id + " " + System.identityHashCode(this) + "-- map=" + System.identityHashCode(map));
        super.finalize();
    }

    @Override
    public String toString() {
        return "finalizeTest{" +
                "id=" + id +
                "} " + System.identityHashCode(this) + "-- map =" + System.identityHashCode(map);
    }
}