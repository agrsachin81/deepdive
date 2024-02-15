package com.champsworld.learn;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class finalizableTest {

        private static final AtomicInteger idGen = new AtomicInteger(0);
        private final int id;
        private final Map<?,?> map;
        public finalizableTest(Map<?, ?> testMap){
            id = idGen.getAndIncrement();
            System.out.println("CREATED finalize TEST "+ id +" "+System.identityHashCode(this) + " --map = "+System.identityHashCode(testMap));
            this.map = testMap;
        }

        public finalizableTest(){
            this(null);
        }

        @Override
        protected void finalize() throws Throwable {
            System.out.println("FINALIAZED with id " + id +" "+System.identityHashCode(this) +" map "+System.identityHashCode(map));
            super.finalize();
        }

    @Override
    public String toString() {
        return "finalizableTest{" +
                "id=" + id +
                "} " + super.toString() +" -- map ="+System.identityHashCode(map);
    }
}