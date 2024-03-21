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
}