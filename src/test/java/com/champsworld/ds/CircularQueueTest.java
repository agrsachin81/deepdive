package com.champsworld.ds;

import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author agrsachin81
 */
public class CircularQueueTest {

    @Test
    public void testAddElement() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        queue.add(1);
        assertEquals(1, queue.size());
        assertEquals(1, queue.peek());
    }

    @Test
    public void testAddMultipleElements() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertEquals(3, queue.size());
        assertEquals(1, queue.peek());
    }

    @Test
    public void testRemoveElement() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        queue.add(1);
        int removed = queue.remove();
        assertEquals(0, queue.size());
        assertEquals(1, removed);
    }

    @Test
    public void testCircularBehavior() {
        CircularQueue<Integer> queue = new CircularQueue<>(3);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertEquals(3, queue.size());
        assertEquals(1, queue.remove());
        queue.add(4);
        assertEquals(3, queue.size());
        assertEquals(2, queue.remove());
        assertEquals(3, queue.remove());
        assertEquals(4, queue.remove());
        assertEquals(0, queue.size());
    }

    @Test
    public void testPeekEmptyQueue() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        assertThrows(IllegalStateException.class, () -> queue.peek());
    }

    @Test
    public void testRemoveEmptyQueue() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        assertThrows(IllegalStateException.class, () -> queue.remove());
    }

    @Test
    public void testCapacityExceeded() {
        CircularQueue<Integer> queue = new CircularQueue<>(1, 3);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        assertThrows(IllegalStateException.class, () -> queue.add(4));
    }

    @Test
    public void testClearQueue() {
        CircularQueue<Integer> queue = new CircularQueue<>(5);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.clear();
        assertEquals(0, queue.size());
        assertThrows(IllegalStateException.class, () -> queue.peek());
    }

    @Test
    public void testCircularBehaviorAfterClear() {
        CircularQueue<Integer> queue = new CircularQueue<>(3);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.clear();
        queue.add(4);
        assertEquals(1, queue.size());
        assertEquals(4, queue.peek());
    }

    @Test
    public void testResizeQueue() {
        CircularQueue<Integer> queue = new CircularQueue<>(3);
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4); // Resize the queue
        assertEquals(4, queue.size());
        assertEquals(1, queue.peek());
    }

    @Test
    public void testMultiThreadAddAndRemove() {
        int capacity = 50;
        Random rand = new Random();
        CircularQueue<String> queue = new CircularQueue<>(capacity, 20000);
        System.out.println(queue.isFull() + " " + queue.size() + " " + queue.isEmpty() +" "+queue.getMaxCapacity());
        LinkedList<String> referenceImplList = new LinkedList<>();
        for (long l=0; l< 10000; l++){

            final int toAdd = rand.nextInt(capacity/10);
            for(int i=0; i<toAdd; i++) {
                boolean res = queue.add(l+"");
                if (res) referenceImplList.offer(l+"");
                l++;
            }
            System.out.println(" size " + queue.size() + " "+queue.getCurrentCapacity() +" added till-> ["+(l-1) +"] total dded ="+toAdd);

            if(queue.size() > 10000 && capacity > (CircularQueue.INT_BOUNDARY_CHECK -2)){
                final int toRemove = rand.nextInt(capacity/10);
                for(int i=0; i<toRemove; i++) {

                    if(referenceImplList.isEmpty() && queue.isEmpty()) {
                        //System.out.println(i + " CAN NOT REMOVE FURTHER both queue Empty "+queue.qState.getReference());
                        break;
                    }
                    assertEquals(referenceImplList.isEmpty(),queue.isEmpty() , " ERROR "+queue.getCurrentCapacity() + " TEST Q SIZE "+referenceImplList.size());
                    String value = queue.remove();
                    String testVal = referenceImplList.remove();
                    assertEquals(testVal, value, i + " ERROR removed actual " + value + " expected " + testVal);
                }
                System.out.println(" size " + queue.size() + " "+queue.getCurrentCapacity() +" total Removed "+toRemove);
            }

            capacity = queue.getCurrentCapacity();
        }
    }
}