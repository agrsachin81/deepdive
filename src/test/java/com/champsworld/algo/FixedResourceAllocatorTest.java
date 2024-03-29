package com.champsworld.algo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author agrsachin81
 */
class FixedResourceAllocatorTest {

    public static final int RESOURCE_SIZE = 3;
    private FixedResourceAllocator allocator;

    @BeforeEach
    public void setUp() {
        allocator = new FixedResourceAllocator(RESOURCE_SIZE, 10); // 3 resources, 10 max unique resource users
    }

    @Test
    public void testGetResourceIndex() {
        assertEquals(0, allocator.getResourceIndex(1));
        assertEquals(1, allocator.getResourceIndex(2));
        assertEquals(2, allocator.getResourceIndex(3));
        assertEquals(0, allocator.getResourceIndex(4));
        assertEquals(1, allocator.getResourceIndex(5));
        assertEquals(2, allocator.getResourceIndex(6));
    }

    @Test
    public void testGetNextUpdatedId() {
        assertEquals(1, allocator.getNextUpdatedId(1));
        assertEquals(2, allocator.getNextUpdatedId(1));
        assertEquals(1, allocator.getNextUpdatedId(2));
        assertEquals(1, allocator.getNextUpdatedId(3));
    }

    @Test
    public void testGetResourceIndex_MaxUniqueResourceUsersExceeded() {
        int index = allocator.getResourceIndex(0);
        for (int i = 1; i < 2 * 10; i++) {
            allocator.getResourceIndex(i); // allocate all resources to unique users
        }
        System.out.println(allocator.getCountMap());

        assertTrue(allocator.getResourceIndex(20) < RESOURCE_SIZE,  "Still return valid index");
        assertEquals(2*10, allocator.getResourceUserCount(), "after max user count the users remain same");
        assertFalse(allocator.getResourceMapping().containsKey(0), "the eldest entry should have been deleted");
    }

    @Test
    public void testGetResourceIndex_Cleared() {
        assertEquals(0, allocator.getResourceIndex(1));
        assertEquals(1, allocator.getResourceIndex(2));
        allocator.clear();
        // now it will restart from zero
        assertEquals(0, allocator.getResourceIndex(2));
        assertEquals(1, allocator.getResourceIndex(1));
    }

    @Test
    public void testGetResourceIndex_MultipleThreads() throws InterruptedException {
        allocator.clear();
        Map<Integer, Integer> indexResourceMap = new HashMap<>();
        Thread t1 = new Thread(() -> {
            indexResourceMap.put(allocator.getResourceIndex(1), 1);
        });

        Thread t2 = new Thread(() -> {
            indexResourceMap.put(allocator.getResourceIndex(2), 2);
        });

        Thread t3 = new Thread(() -> {
            indexResourceMap.put(allocator.getResourceIndex(3), 3);
        });

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();
        assertEquals(indexResourceMap.size(), RESOURCE_SIZE);
    }

    @Test
    void testResourceIndexPurging() {
        final int resourceSize = 5;
        final int maxUniqueResourceUsers = 100;
        FixedResourceAllocator strategy =new FixedResourceAllocator(resourceSize, maxUniqueResourceUsers);

        for(int i=0; i< 10_000; i++){
           int resourceIndex =  strategy.getResourceIndex(i);
           assertTrue(resourceIndex < resourceSize, "CANNOT BE MORE THE MAX");

           assertTrue(strategy.getResourceUserCount() <= 2 * maxUniqueResourceUsers, "Must clear old not used resource ids");
        }

        assertTrue(strategy.getResourceUserCount() <= 2 * maxUniqueResourceUsers, "Must clear old not used resource ids");

        System.out.println(strategy.getCountMap());
    }

    @Test
    void testResourceIndexPurgingCount() {
        final int resourceSize = 5;
        final int maxUniqueResourceUsers = 100;
        FixedResourceAllocator strategy =new FixedResourceAllocator(resourceSize, maxUniqueResourceUsers);
        final int eldestIndex = strategy.getResourceIndex(0);
        int deleteAfterUniqueResources = 2 * maxUniqueResourceUsers;
        for(int i = 1; i< deleteAfterUniqueResources; i++){
            int resourceIndex =  strategy.getResourceIndex(i);
            assertTrue(resourceIndex < resourceSize, "CANNOT BE MORE THEN THE MAX");
        }
        final int lastIndex = strategy.getResourceIndex(deleteAfterUniqueResources);
        assertEquals(lastIndex, eldestIndex);
        assertEquals(deleteAfterUniqueResources, strategy.getResourceUserCount(), "Eldest entry was deleted, hence the count must remain same");
        assertTrue(strategy.getResourceUserCount() <= deleteAfterUniqueResources, "Must clear old not used resource ids");
        assertFalse(strategy.getResourceMapping().containsKey(0), "the oldest entry must have been deleted");
        assertEquals(deleteAfterUniqueResources/5, strategy.getCountMap().get(eldestIndex), "All indexes must have same number of count");
    }

    @Test
    public void testRepeatIndexMappingForResource(){
        final int resourceSize = 5;
        final int maxUniqueResourceUsers = 100;
        final FixedResourceAllocator strategy =new FixedResourceAllocator(resourceSize, maxUniqueResourceUsers);
        final int[] index = new int[maxUniqueResourceUsers];
        for(int i=0; i< maxUniqueResourceUsers; i++){
            index[i] =  strategy.getResourceIndex(i);
        }
        int count = strategy.getResourceUserCount();
        // test repeat call will return same values
        for(int i=0; i< maxUniqueResourceUsers;i++){
            assertEquals( index[i] , strategy.getResourceIndex(i), "already asked hence same index should have been returned");
        }
        assertEquals(count, strategy.getResourceUserCount(), "resource user count must remain same as no new user was added");
    }
}