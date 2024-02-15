package com.champsworld.learn;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * TO TEST WHETHER WEAK HASH MAP removes THread as key when they die
 * Short lived Threads were put as keys in hashMap and WeakHashMap to see the difference
 */
public class WeakReferenceTests {



    public static void main(String[] args) {
        //WeakReference<finalizableTest> abc = new WeakReference<>(new finalizableTest(null));

        //testWithSet();

        //testWithMap();

        testWithMapThreadAsKey();
    }

    private static void testWithSet() {
        Set<WeakReference<FinalizableTest>> weakSet = new HashSet<>();
        Set<FinalizableTest> strongSet = new HashSet<>();

        System.out.println("------START ADDING WEAK SET");
        weakSet.add(new WeakReference<>(new FinalizableTest()));
        weakSet.add(new WeakReference<>(new FinalizableTest()));
        System.out.println("------END ADDING WEAK SET");

        System.out.println("------START ADDING STRONG SET");
        strongSet.add(new FinalizableTest());
        strongSet.add(new FinalizableTest());
        System.out.println("------END ADDING STRONG SET");
        Runtime.getRuntime().gc();

        for (WeakReference<FinalizableTest> obj: weakSet) {
            System.out.println("WEAKSET value found "+obj.get());
        }
        System.out.println("SIZE OF WEAK SET "+ weakSet.size());
        System.out.println("SIZE OF STRONG SET "+ strongSet.size());

        for (FinalizableTest obj: strongSet) {
            System.out.println("STRONG SET value found "+obj.toString());
        }
    }

    private static void testWithMap() {

        WeakHashMap<FinalizableTest, Integer> weakHashMap= new WeakHashMap<>();
        Map<FinalizableTest, Integer> strongMap = new HashMap<>();

        System.out.println("------START ADDING Weak MAP");
        weakHashMap.put(new FinalizableTest(weakHashMap), 1);
        weakHashMap.put(new FinalizableTest(weakHashMap), 2);
        System.out.println("------END ADDING Weak MAP");

        System.out.println("------START ADDING STRONG MAP");
        strongMap.put(new FinalizableTest(strongMap), 3);
        strongMap.put(new FinalizableTest(strongMap), 4);
        System.out.println("------END ADDING STRONG MAP");
        Runtime.getRuntime().gc();

        for (FinalizableTest obj: weakHashMap.keySet()) {
            System.out.println("WEAKHASHAMP value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP "+ strongMap.size());

        for (FinalizableTest obj: strongMap.keySet()) {
            System.out.println("STRONG MAP value found "+obj.toString());
        }
    }

    private static void testWithMapThreadAsKey() {

        WeakHashMap<Thread, FinalizableTest> weakHashMap= new WeakHashMap<>();
        Map<Thread, FinalizableTest> strongMap = new HashMap<>();
        
        System.out.println("------START ADDING Weak MAP THREAD");
        Set<Thread> threads = new HashSet<>();
        threads.add(createThreadWithFinalizeTest(weakHashMap));
        threads.add(createThreadWithFinalizeTest(weakHashMap));
        System.out.println("------END ADDING Weak MAP THREAD");

        System.out.println("------START ADDING STRONG MAP THREAD");
        threads.add(createThreadWithFinalizeTest(strongMap));
        threads.add(createThreadWithFinalizeTest(strongMap));
        System.out.println("------END ADDING STRONG MAP THREAD");

        threads.forEach(Thread::start);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        threads.clear();
        Runtime.getRuntime().gc();

        for (FinalizableTest obj: weakHashMap.values()) {
            System.out.println("WEAKHASHAMP THREAD value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP THREAD "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP THREAD "+ strongMap.size());

        for (FinalizableTest obj: strongMap.values()) {
            System.out.println("STRONG MAP value found THREAD "+obj.toString());
        }

        Runtime.getRuntime().gc();
        try {
            Thread.sleep(20_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Thread createThreadWithFinalizeTest(Map<Thread, FinalizableTest> testMap) {
        FinalizableTest value1 = new FinalizableTest(testMap);
        Thread key = new Thread(() -> System.out.println("RUNNING THREAD " + Thread.currentThread().getName() + "  value " + value1.toString()));
        testMap.put(key, value1);
        return key;
    }

}
