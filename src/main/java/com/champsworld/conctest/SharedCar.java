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

        private void release() {
            this.democrat.set(0);
            this.republic.set(0);
        }
    }

    private final AtomicInteger stamper = new AtomicInteger(1);
    private final AtomicStampedReference<BookingState> currBookingState = new AtomicStampedReference<>(new BookingState(), stamper.getAndIncrement());

    private final ThreadLocal<BookingState> bookingStateThreadLocal =  ThreadLocal.withInitial(BookingState::new);

    private final ThreadLocal<int[]> stampHolderThreadLocal = ThreadLocal.withInitial(()-> new int[1]);

    boolean isReady() {
        return this.currBookingState.getReference().isReady();
    }

    public boolean releaseCar() {
        if(isReady()) {
            this.currBookingState.getReference().release();
            return true;
        }
        return false;
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


    //++++++++++++++ TEST AREA STARTS
    /**
     *  TESTING
     * @param args
     */
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
            } catch (Exception ignored) {}

            if(democrat.get() ==3 || republic.get()==3 || (republic.get() + democrat.get()) !=4 ) {
                System.out.println("ERROR------------------IDX " +errorCount + i + "DEM " + democrat.get() + " REP " + republic.get());
                errorCount ++;
            } else {
                System.out.println("SUCCESS "+i + " ERROR COUNT "+errorCount);
            }
            boolean released = car.releaseCar();
            if(!released) errorCount ++;
            resetResultCounters();
        }
        executorService.shutdown();
    }

    static final AtomicInteger democrat = new AtomicInteger();
    static final AtomicInteger republic = new AtomicInteger();

    /**
     * used only by tester
     */
    private static void resetResultCounters() {
        democrat.set(0);
        republic.set(0);
    }

    private static void reserveDemocrat(SharedCar car) {
        Random random = new Random();
        while(!car.isReady()) {
            boolean res = car.reserveDemocrat();
            if(res) democrat.getAndIncrement();
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
            if(res) republic.getAndIncrement();
            if(random.nextInt(10)% 2==0) {
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    //++++++++++++++ TEST AREA ENDS
}
