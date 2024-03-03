package com.champsworld.conctest;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * UBER RIDE PROBLEM GENERIC SOLUTION
 * Imagine at the end of a political conference, republicans and democrats are trying to leave the venue.
 * and ordering Uber rides at the same time. However, to make sure no fight breaks out in an Uber ride,
 * the software developers at Uber come up with an algorithm whereby either an Uber ride can have all democrats or republicans or
 * two Democrats and two Republicans. All other combinations can result in a fist-fight.
 * @author agrsachin81
 */
public class SharedCar {

    private static class CarState {
        final AtomicInteger democrat = new AtomicInteger(0);
        final AtomicInteger republic =  new AtomicInteger(0);

        void init(CarState state){
            this.democrat.set(state.democrat.get());
            this.republic.set(state.republic.get());
        }

        boolean isReady() {
            return this.republic.get() + this.democrat.get() ==4;
        }

        boolean bookSlot(boolean republic){
            if(isReady()) return false;
            if(republic) {
                if ((this.republic.get() ==2 && this.democrat.get() == 1) || this.democrat.get()==3) return false;
                this.republic.getAndIncrement();
            } else {
                if ((this.republic.get() == 1 && this.democrat.get() == 2) || this.republic.get() ==3) return false;
                this.democrat.getAndIncrement();
            }
            return true;
        }
    }

    private final AtomicInteger stamper = new AtomicInteger(1);
    private final AtomicStampedReference<CarState> currState = new AtomicStampedReference<>(new CarState(), stamper.getAndIncrement());

    private final ThreadLocal<CarState> carStateThreadLocal=  ThreadLocal.withInitial(CarState::new);

    private final ThreadLocal<int[]> stampHolderThreadLocal = ThreadLocal.withInitial(()-> new int[1]);

    boolean isReady() {
        return this.currState.getReference().isReady();
    }

    public boolean reserveDemocrat(){
        if(isReady()) return false;
        return bookSlot(false);
    }

    private boolean bookSlot(boolean republic) {
        if(isReady()) return false;
        final CarState newState = carStateThreadLocal.get();
        final int stamp = stamper.getAndIncrement();
        final int[] stampHolders = stampHolderThreadLocal.get();
        stampHolders[0] = -2;
        while(true){
            CarState state = currState.get(stampHolders);
            newState.init(state);
            if(newState.isReady()) return false;
            if(newState.bookSlot(republic)){
                if(currState.compareAndSet(state, newState, stampHolders[0], stamp)){
                    carStateThreadLocal.set(state);
                    return true;
                }
            } else {
                return false;
            }
        }
    }

    public boolean reserveRepublic(){
        if(isReady()) return false;
        return bookSlot(true);
    }

    public static void main(String[] args) {
        SharedCar car = new SharedCar();
        final Runnable runnable = () -> reserveDemocrat(car);
        final Runnable runnable1 = () -> reserveRepublic(car);
        for(int i=0; i<10000; i++){
            Thread demTHread = new Thread(runnable);
            Thread repThread = new Thread(runnable1);
            repThread.start();
            demTHread.start();
            try {
                repThread.join();
                demTHread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            if(demo.get() ==3 || repu.get()==3) {
                System.out.println("ERROR------------------IDX " + i + "DEM " + demo.get() + " REP " + repu.get());
            } else {
                System.out.println("SUCCESS "+i);
            }
            car.releaseCar();
            resetResultCounters();
        }
    }

    static final AtomicInteger demo = new AtomicInteger();
    static final AtomicInteger repu = new AtomicInteger();

    private void releaseCar() {
        this.currState.getReference().democrat.set(0);
        this.currState.getReference().republic.set(0);
    }

    /**
     * used only by tester
     */
    private static void resetResultCounters() {
        demo.set(0);
        repu.set(0);
    }

    private static void reserveDemocrat(SharedCar car) {
        Random random = new Random();
        while(!car.isReady()) {
            boolean res = car.reserveDemocrat();
            if(res) demo.getAndIncrement();
            if(random.nextInt(40)% 2==0) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private static void reserveRepublic(SharedCar car) {
        Random random = new Random();
        while(!car.isReady()) {
            boolean res = car.reserveRepublic();
            if(res) repu.getAndIncrement();
            if(random.nextInt(10)% 2==0) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
