package com.champsworld.learn;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

/**
 * TO TEST WHETHER WEAK HASH MAP removes THread as key when they die
 * Short lived Threads were put as keys in hashMap and WeakHashMap to see the difference
 */
public class WeakReferenceTests {



    public static void main(String[] args) {
        //WeakReference<finalizableTest> abc = new WeakReference<>(new finalizableTest(null));

        // testWithSet();

        //testWithMap();

        //testWithMapThreadAsKey();

        testWithReferenceQueue();
    }

    private static void testWithSet() {
        Set<WeakReference<FinalizeTest>> set = new HashSet<>();
        Set<FinalizeTest> strongSet = new HashSet<>();

        System.out.println("------START ADDING WEAK SET");
        set.add(new WeakReference<>(new FinalizeTest()));
        set.add(new WeakReference<>(new FinalizeTest()));
        System.out.println("------END ADDING WEAK SET");

        System.out.println("------START ADDING STRONG SET");
        strongSet.add(new FinalizeTest());
        strongSet.add(new FinalizeTest());
        System.out.println("------END ADDING STRONG SET");
        Runtime.getRuntime().gc();

        for (WeakReference<FinalizeTest> obj: set) {
            System.out.println("WEAKSET value found "+obj.get());
        }
        System.out.println("SIZE OF WEAK SET "+ set.size());
        System.out.println("SIZE OF STRONG SET "+ strongSet.size());

        for (FinalizeTest obj: strongSet) {
            System.out.println("STRONG SET value found "+obj.toString());
        }
    }

    private static void testWithMap() {

        WeakHashMap<FinalizeTest, Integer> weakHashMap= new WeakHashMap<>();
        Map<FinalizeTest, Integer> strongMap = new HashMap<>();

        System.out.println("------START ADDING Weak MAP");
        weakHashMap.put(new FinalizeTest(weakHashMap), 1);
        weakHashMap.put(new FinalizeTest(weakHashMap), 2);
        System.out.println("------END ADDING Weak MAP");

        System.out.println("------START ADDING STRONG MAP");
        strongMap.put(new FinalizeTest(strongMap), 3);
        strongMap.put(new FinalizeTest(strongMap), 4);
        System.out.println("------END ADDING STRONG MAP");
        Runtime.getRuntime().gc();

        for (FinalizeTest obj: weakHashMap.keySet()) {
            System.out.println("WEAKHASHAMP value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP "+ strongMap.size());

        for (FinalizeTest obj: strongMap.keySet()) {
            System.out.println("STRONG MAP value found "+obj.toString());
        }
    }

    private static void testWithMapThreadAsKey() {

        WeakHashMap<Thread, FinalizeTest> weakHashMap= new WeakHashMap<>();
        Map<Thread, FinalizeTest> strongMap = new HashMap<>();
        
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

        for (FinalizeTest obj: weakHashMap.values()) {
            System.out.println("WEAKHASHAMP THREAD value found "+obj);
        }
        System.out.println("SIZE OF WEAK MAP THREAD "+ weakHashMap.size());
        System.out.println("SIZE OF STRONG MAP THREAD "+ strongMap.size());

        for (FinalizeTest obj: strongMap.values()) {
            System.out.println("STRONG MAP value found THREAD "+obj.toString());
        }

        Runtime.getRuntime().gc();
        try {
            Thread.sleep(20_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ReferenceQueue with WeakReferenceExample
     * It demonstrates that added to reference queue can be used as a notification when an object is gcd
     */
    private static void testWithReferenceQueue(){

        final ReferenceQueue<FinalizeTest> que = new ReferenceQueue<>();
        System.out.println("------START ADDING SET");
        FinalizeTest finalizableTest = new FinalizeTest();
        WeakReference<FinalizeTest> ref1 = new InterWeakReference<>(0, finalizableTest, que);
        FinalizeTest finalizableTest1 = new FinalizeTest();
        WeakReference<FinalizeTest> ref2 = new InterWeakReference<>(1, finalizableTest1, que);

        System.out.println("------END ADDING TO SET ");
        final int count = 2;
        Thread t = new Thread( ()->{
            int sum =0;
            int iter =0;
            while(sum < count && iter < 1000){
                try {
                    InterWeakReference<? extends FinalizeTest> item = (InterWeakReference<? extends FinalizeTest>) que.remove(500);
                    if(item!=null) {
                        FinalizeTest test = item.get();
                        //since item .get is always null we have to extend WeakReference in order to be able to use the notification that too using
                        // a separate thread
                        System.out.println("FROM REF QUEUE " + test +" item id "+item + " hash "+item.hashCode +" order "+item.orderingId);
                        sum++;
                    } else {
                        System.out.println("FOUND null item from queue "+iter);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                iter++;
            }
        });


        finalizableTest = null;
        finalizableTest1 = null;
        System.out.println("BEFORE GC WeakRef1 "+ref1.get() +" ref ="+ref1);
        System.out.println("BEFORE GC WeakRef2 "+ref2.get() +" ref ="+ref2);
        // clearing of wekRef is not required
        //weakRef.clear();
        //ref2.clear();
        t.start();
        // it is important to run gc to see impact whether it is added to reference queue
        Runtime.getRuntime().gc();
        System.out.println("AFTER GC WeakRef1 "+ref1.get() +" ref ="+ref1);
        System.out.println("AFTER GC WeakRef2 "+ref2.get() +" ref ="+ref2);
        // now weak ref are not reachable also
    }

    private static Thread createThreadWithFinalizeTest(Map<Thread, FinalizeTest> testMap) {
        FinalizeTest value1 = new FinalizeTest(testMap);
        Thread key = new Thread(() -> System.out.println("RUNNING THREAD " + Thread.currentThread().getName() + "  value " + value1.toString()));
        testMap.put(key, value1);
        return key;
    }


    /**
     * Sample WeakReference extension needed to pass data to ReferenceQueue about object
     * @param <S>
     */
    private static class InterWeakReference<S> extends WeakReference<S>{

        private final int hashCode ;
        private final int orderingId;
        public InterWeakReference(int orderingId, S referent) {
            super(referent);
            this.orderingId = orderingId;
            this.hashCode = System.identityHashCode(referent);
        }

        public InterWeakReference(int orderingId, S referent, ReferenceQueue<S> queue) {
            super(referent, queue);
            this.orderingId = orderingId;
            this.hashCode = System.identityHashCode(referent);
        }
    }
}
