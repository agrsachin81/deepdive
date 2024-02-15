package com.champsworld.learn;

import java.lang.ref.WeakReference;
import java.util.*;

public class WeakReferenceTests {



    public static void main(String[] args) {
        //WeakReference<finalizableTest> abc = new WeakReference<>(new finalizableTest(null));

        //testWithSet();

        //testWithMap();

        testWithMapThreadAsKey();
    }

    private static void testWithSet() {
        Set<WeakReference<finalizableTest>> weakSet = new HashSet<>();
        Set<finalizableTest> strongSet = new HashSet<>();

        System.out.println("------START ADDING WEAK SET");
        weakSet.add(new WeakReference<>(new finalizableTest()));
        weakSet.add(new WeakReference<>(new finalizableTest()));
        System.out.println("------END ADDING WEAK SET");

        System.out.println("------START ADDING STRONG SET");
        strongSet.add(new finalizableTest());
        strongSet.add(new finalizableTest());
        System.out.println("------END ADDING STRONG SET");
        Runtime.getRuntime().gc();

        for (WeakReference<finalizableTest> obj: weakSet) {
            System.out.println("WEAKSET value found "+obj.get());
        }
        System.out.println("SIZE OF WEAK SET "+ weakSet.size());
        System.out.println("SIZE OF STRONG SET "+ strongSet.size());

        for (finalizableTest obj: strongSet) {
            System.out.println("STRONG SET value found "+obj.toString());
        }
    }

    private static void testWithMap() {

        WeakHashMap<finalizableTest, Integer> weakHashMap= new WeakHashMap<>();
        Map<finalizableTest, Integer> strongMap = new HashMap<>();

        System.out.println("------START ADDING Weak MAP");
        weakHashMap.put(new finalizableTest(weakHashMap), 1);
        weakHashMap.put(new finalizableTest(weakHashMap), 2);
        System.out.println("------END ADDING Weak MAP");

        System.out.println("------START ADDING STRONG MAP");
        strongMap.put(new finalizableTest(strongMap), 3);
        strongMap.put(new finalizableTest(strongMap), 4);
        System.out.println("------END ADDING STRONG MAP");
        Runtime.getRuntime().gc();

        for (finalizableTest obj: weakHashMap.keySet()) {
            System.out.println("WEAKHASHAMP value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP "+ strongMap.size());

        for (finalizableTest obj: strongMap.keySet()) {
            System.out.println("STRONG MAP value found "+obj.toString());
        }
    }

    private static void testWithMapThreadAsKey() {

        WeakHashMap<Thread, finalizableTest> weakHashMap= new WeakHashMap<>();
        Map<Thread, finalizableTest> strongMap = new HashMap<>();
        
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

        for (finalizableTest obj: weakHashMap.values()) {
            System.out.println("WEAKHASHAMP THREAD value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP THREAD "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP THREAD "+ strongMap.size());

        for (finalizableTest obj: strongMap.values()) {
            System.out.println("STRONG MAP value found THREAD "+obj.toString());
        }

        Runtime.getRuntime().gc();
        try {
            Thread.sleep(20_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static Thread createThreadWithFinalizeTest(Map<Thread, finalizableTest> testMap) {
        finalizableTest value1 = new finalizableTest(testMap);
        Thread key = new Thread(() -> System.out.println("RUNNING THREAD " + Thread.currentThread().getName() + "  value " + value1.toString()));
        testMap.put(key, value1);
        return key;
    }

}
