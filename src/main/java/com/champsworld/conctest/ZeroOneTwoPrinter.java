package com.champsworld.conctest;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author agrsachin81
 */
public class ZeroOneTwoPrinter extends Thread{

    private volatile static int num_static =1;
    private final int printWhenResult;
    private final int divisor;
    private final String prefix;

    private final AtomicInteger number;

    public ZeroOneTwoPrinter(int printWhenResult, int divisor, String prefix, AtomicInteger number) {
        this.printWhenResult = printWhenResult;
        this.divisor = divisor;
        this.prefix = prefix;
        this.number = number;
    }


    public void run_Atomic() {
        final String name = Thread.currentThread().getName();
        while(number.get() <100) {
            while(number.get() % this.divisor!= this.printWhenResult){
            }
            //we need synchronized because of system.out lock
            synchronized (System.out) {
                System.out.println(this.prefix + "  " + number.getAndIncrement() + " " + name);
            }
        }
    }

    public void run() {
        final String name = Thread.currentThread().getName();
        while(num_static <100) {
            while(num_static % this.divisor!= this.printWhenResult){
            }
            synchronized (System.out) {
                System.out.println(this.prefix + "  " + (num_static++) + " " + name);
            }
        }
    }

    public static void main(String[] args) {
        AtomicInteger number = new AtomicInteger(1);
        int divisor = 3;
        ZeroOneTwoPrinter zero = new ZeroOneTwoPrinter(0, divisor, "ZERO PRINTER ", number);
        ZeroOneTwoPrinter one = new ZeroOneTwoPrinter(1, divisor, "ONE PRINTER ", number);
        ZeroOneTwoPrinter two = new ZeroOneTwoPrinter(2, divisor, "TWO PRINTER ", number);

        zero.start();
        one.start();
        two.start();

        try {
            zero.join();
            one.join();
            two.join();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
