package com.champsworld.algo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author agrsachin81
 */
class FixedResourceAllocatorTest {

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