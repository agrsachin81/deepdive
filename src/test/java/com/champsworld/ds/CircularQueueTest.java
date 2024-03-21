package com.champsworld.ds;

import org.junit.jupiter.api.Test;
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
}