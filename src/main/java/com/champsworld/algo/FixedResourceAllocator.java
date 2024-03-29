package com.champsworld.algo;

import com.google.common.collect.TreeMultimap;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple usage count based load balancer strategy implementation, when number of common resources are fixed
 * it uses lru mapping to keep purging the oldest used resourceUserId
 */
public class FixedResourceAllocator {

    /**
     * resourceUserId vs resource Index; a lru cache implementation ensuring least used resourceUserId is removed from cache
     * to prevent memory leak
     */
    private final LinkedHashMap<Integer, Integer> lruMap;

    /**
     * used to ensure O(1) time complexity, while allotting index for a resourceUserId
     * count vs list of indexes to keep the index on count with a tree map, we know the least allotted index
     * so fairness is ensured by keeping count of each index, then we update the reverse map indexCount array
     */
    private final TreeMultimap<Integer, Integer> countVsIndexMap;

    /**
     * used to ensure O(1) time complexity, while removing resourceUserId from lruCache
     * reverse map to quickly decrease the count for an index
     * after this we update the reverse mapping
     */
    private final int[] indexCount;
    private final int size;
    private final int eldestSize;
    private final ConcurrentHashMap<Integer, AtomicInteger> resourceUserSeqIdGenerators = new ConcurrentHashMap<>();

    public FixedResourceAllocator(final int resourceSize, final int maxUniqueResourceUsers) {
        this.size = resourceSize;
        this.indexCount = new int[resourceSize];
        // since we are removing the eldest entry,
        // we are assuming twice the size is safe to remove no task is posted
        // for very long for the eldest entry
        this.eldestSize = 2 * maxUniqueResourceUsers;
        this.countVsIndexMap = TreeMultimap.create();
        fillDefaultCountWithAllIndexes(resourceSize);
        lruMap = new LinkedHashMap<Integer, Integer>(Math.min(maxUniqueResourceUsers, 1000), 0.85f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
                // this method is called when we put something inside map
                // which already have lock on lruMap and countvsIndexMap
                // decrease count for the index of this ordering Id
                boolean remove = size() > eldestSize;
                if (remove) {
                    // when we call this we must have a lock on lruMap, which is done through synchronized
                    // lruMap is modified after lock is released
                    decreaseCountForIndex(eldest.getValue());
                    resourceUserSeqIdGenerators.remove(eldest.getKey());
                }
                return remove;
            }
        };
    }

    private void fillDefaultCountWithAllIndexes(int size) {
        countVsIndexMap.clear();
        for (int i = 0; i < size; i++) {
            //initially all counts are zero
            countVsIndexMap.put(0, i);
            indexCount[i] = 0;
        }
    }

    private void decreaseCountForIndex(Integer index) {
        // this is called from remove the eldest entry, which in turn called from put or put all,
        // which is called from getThreadPoolExecIndex
        // hence we always lock lruMap first
        synchronized (countVsIndexMap) {
            final int count = indexCount[index];
            if (countVsIndexMap.containsKey(count))
                countVsIndexMap.remove(count, index);
            int newCount = count - 1;
            countVsIndexMap.put(newCount, index);
            indexCount[index] = newCount;
        }
    }


    /**
     * Helper method; It keeps the number of times user has used the resourceUserId
     * It is reset when a resourceUserId is deleted from the mapping (resourceUserId --> resourceIndex)
     *
     * @param resourceUserId, the unique resourceUserId on which seq id is generated
     * @return the total next update seq of the specified resourceUserId
     */
    public Integer getNextUpdatedId(Integer resourceUserId) {
        AtomicInteger generator = resourceUserSeqIdGenerators.computeIfAbsent(resourceUserId, id -> new AtomicInteger(1));
        return generator.getAndIncrement();
    }

    /**
     * returns a zero based reource id mapped to resourceUserId
     *
     * @param resourceUserId, the user of resources
     * @return index of the resource mapped to specified resourceUserId
     */
    public Integer getResourceIndex(final Integer resourceUserId) {
        synchronized (lruMap) {
            if (lruMap.containsKey(resourceUserId)) {
                return lruMap.get(resourceUserId);
            } else {
                //generate index for a new Ordering ID
                synchronized (countVsIndexMap) {
                    // least used index is allotted
                    if (countVsIndexMap.isEmpty()) {
                        fillDefaultCountWithAllIndexes(this.size);
                    }
                    final int lowestCount = countVsIndexMap.keySet().first();
                    final NavigableSet<Integer> indexes = countVsIndexMap.get(lowestCount);
                    // this removes the index from the value set
                    Integer index = indexes.pollFirst();
                    if (index == null) index = 0;
                    // we put the index back with a different count
                    final int newCount = lowestCount + 1;
                    //double check deletion of index from multiMap, it will ret false if already removed
                    boolean removed = countVsIndexMap.remove(lowestCount, index);
                    if (removed)
                        //TODO: log
                        System.out.println("REMOVED IN second attempt");
                    countVsIndexMap.put(newCount, index);
                    indexCount[index] = newCount;
                    lruMap.put(resourceUserId, index);
                    return index;
                }
            }
        }
    }

    public void clear() {
        synchronized (lruMap) {
            synchronized (countVsIndexMap) {
                lruMap.clear();
                countVsIndexMap.clear();
                Arrays.fill(indexCount, 0);
            }
        }
    }

    /**
     * current state of resource usage
     *
     * @return resUserCount for each index of resource index
     */
    public List<Integer> getCountMap(){
        return Collections.unmodifiableList(Arrays.asList(Arrays.stream(indexCount).boxed().toArray(Integer[]::new)));
    }

    /**
     *
     * @return map of resourceUser id to resource index
     */
    public Map<Integer, Integer> getResourceMapping(){
        return Collections.unmodifiableMap(lruMap);
    }

    public int getResourceUserCount(){
        return lruMap.size();
    }
}