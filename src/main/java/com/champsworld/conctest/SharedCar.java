package com.champsworld.conctest;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static class BookingState {
        final AtomicInteger democrat = new AtomicInteger(0);
        final AtomicInteger republic =  new AtomicInteger(0);

        void init(BookingState state){
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
    private final AtomicStampedReference<BookingState> currBookingState = new AtomicStampedReference<>(new BookingState(), stamper.getAndIncrement());

    private final ThreadLocal<BookingState> bookingStateThreadLocal =  ThreadLocal.withInitial(BookingState::new);

    private final ThreadLocal<int[]> stampHolderThreadLocal = ThreadLocal.withInitial(()-> new int[1]);

    boolean isReady() {
        return this.currBookingState.getReference().isReady();
    }

    public boolean reserveDemocrat(){
        if(isReady()) return false;
        return bookSlot(false);
    }

    private boolean bookSlot(boolean republic) {
        if(isReady()) return false;
        final BookingState newState = bookingStateThreadLocal.get();
        final int stamp = stamper.getAndIncrement();
        final int[] stampHolders = stampHolderThreadLocal.get();
        stampHolders[0] = -2;
        while(true){
            BookingState state = currBookingState.get(stampHolders);
            newState.init(state);
            if(newState.isReady()) return false;
            if(newState.bookSlot(republic)){
                if(currBookingState.compareAndSet(state, newState, stampHolders[0], stamp)){
                    bookingStateThreadLocal.set(state);
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
        int errorCount = 0;
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        final Runnable democratBooker = () -> reserveDemocrat(car);
        final Runnable republicanBooker = () -> reserveRepublic(car);
        for(int i=0; i<10000; i++){
            CompletableFuture<Void> demoFuture = CompletableFuture.runAsync(democratBooker, executorService);
            CompletableFuture<Void> repuFuture = CompletableFuture.runAsync(republicanBooker, executorService);

            try {
                Void ret = CompletableFuture.allOf(demoFuture, repuFuture).get();
            } catch (Exception e) {}

            if(demo.get() ==3 || repu.get()==3 || (repu.get() + demo.get()) !=4 ) {
                System.out.println("ERROR------------------IDX " +errorCount + i + "DEM " + demo.get() + " REP " + repu.get());
                errorCount ++;
            } else {
                System.out.println("SUCCESS "+i + " ERROR COUNT "+errorCount);
            }
            car.releaseCar();
            resetResultCounters();
        }
    }

    static final AtomicInteger demo = new AtomicInteger();
    static final AtomicInteger repu = new AtomicInteger();

    private void releaseCar() {
        this.currBookingState.getReference().democrat.set(0);
        this.currBookingState.getReference().republic.set(0);
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