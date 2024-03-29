package com.champsworld.concurrent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.mapping;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Ordered Tester, WIP
 * @author agrsachin81
 */
class OrderedTaskExecutorTest {
    @Test
    public void testExecutionOrderMultipleSubmittedTask() {
        final OrderedTaskExecutor executor = new OrderedTaskExecutor(2000);
        final MultiSubmittedOrderedTask task = new MultiSubmittedOrderedTask("Sample");
        final List<CompletableFuture<String>> multiTaskResults = new ArrayList<>(30000);

        for(int i=0;i < 100_000; i++){
            multiTaskResults.add(task.addSubmission(i, executor));
        }

        System.out.println("GOT FUTURE COUNT "+multiTaskResults.size());
        CompletableFuture<Void> allFuture = CompletableFuture.allOf(multiTaskResults.toArray(new CompletableFuture[0]));

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        allFuture.whenComplete((a, b)-> {
            assertNull(b, "Exception thrown when executing same task. Same task can be submitted multiple times. Mandatory Requirement");
            final TreeMap<Long, Integer> execOrder = task.getExecTimeStamps();
            int previousValue = Integer.MIN_VALUE;
            for (Map.Entry<Long, Integer> entry : execOrder.entrySet()) {
                int currentValue = entry.getValue();
                System.out.println(currentValue +" "+entry.getKey());
                if (currentValue < previousValue) {
                    // Values are not in ascending order, fail here
                    fail("Not executed in same order as it was submitted" + currentValue +" prev "+previousValue);
                }
                previousValue = currentValue;
            }
        });

        allFuture.join();
        System.out.println("FINISHED ");
        executor.shutdown();
    }

    @Test
    void testExecutionOrder() {
        final OrderedTaskExecutor executor = new OrderedTaskExecutor(2000);
        final List<CompletableFuture<String>> results = new ArrayList<>(30000);
        final List<SampleOrderedTask> orderedTasks = new ArrayList<>(400000);
        final int MAX = 10_000;
        //first prepare and then submit in the same order
        for (int i=0; i< MAX; i++){
            final int orderingId = i % 200;
            final SampleOrderedTask testTask = new SampleOrderedTask("Sam" + i, orderingId);
            orderedTasks.add(testTask);
        }
        //first prepare and then submit in the same order
        for(int i=0;i < MAX; i++){
            results.add(executor.submit(orderedTasks.get(i)));
        }
        CompletableFuture<Void> allOfFuture = CompletableFuture.allOf(results.toArray(new CompletableFuture[0]));
        // Add a callback to the 'allOfFuture' to execute code when all tasks are complete
        allOfFuture.thenRun(() -> {
            System.out.println("All tasks completed!");
            // Your code to execute when all tasks are complete
            final Map<Integer, List<Object[]>> map = orderedTasks.stream().collect(Collectors.groupingBy(
                    SampleOrderedTask::orderingId,
                    mapping(
                            SampleOrderedTask::getLastExec,
                            Collectors.toList()
                    )
            ));
            System.out.println("SIZE IS "+map.size() + map.keySet());
            int failCount = 0;
            StringBuilder message = new StringBuilder();
            for(Map.Entry<Integer, List<Object[]>> list: map.entrySet()){
                List<Object[]> retLists = list.getValue();
                System.out.println(" "+list.getKey() +" "+ retLists.size() );
                try {
                    // zeroth index has the submission order
                    retLists.sort(Comparator.comparing(o -> Integer.parseInt(((String) o[0]).substring(3))));
                    for (int i = 0; i < retLists.size() - 1; i++) {
                        Object[] current = retLists.get(i);
                        Object[] next = retLists.get(i + 1);
                        // index (1) has static sequence generator; it can be skipped because it wil be always be in creasing order,
                        // index (1) it acts as a verifier that test itself is correct
                        // index (2) has timestamp; it must be in increasing order
                        String suf = current[0]+" "+current [1]+" "+current[2] +" NEXT "+next[0]+" "+next[1] +" "+next[2];
                        if ((int) current[1] > (int) next[1] || (long) current[2] > (long) next[2]) {
                            fail("Single Task results are not in order for orderingId " + list.getKey() + " "+suf);
                        }
                    }
                } catch (Throwable e){
                    message.append(e.getMessage()).append(",");
                    failCount++;
                }
            }

            if(failCount >0){
                fail(message.toString());
            }
        });
        List<Throwable> value = executor.shutdown();
        for (Throwable throwable:value) {
            System.out.println(throwable.getMessage());
        }
        System.out.println(executor.isShutdown());
        final Object[] array = executor.shutdownNow();
        if(array[0] instanceof Collection<?>) {
            for (Object val : (Collection<?>) array[0]) {
                System.out.println(" Runnable returned " + val.toString());
            }
        }
        if(array[0] instanceof Collection<?>) {
            for (Object val : (Collection<?>) array[1]) {
                if (val instanceof Throwable)
                    System.out.println(" Throw returned " + ((Throwable) val).getMessage());
            }
        }
    }

    @Test
    public void testFailedTask() {
        final OrderedTaskExecutor executor = new OrderedTaskExecutor(20);
        CompletableFuture<Integer> fut = null;
        final Class<? extends Throwable> toThrow = NullPointerException.class;
        try {
            fut = executor.submit(() -> {
                String name = Thread.currentThread().getName();
                if (true) {
                    throw new NullPointerException("Unable to perform task for xyz reasons " +name);
                }
                else
                    return 1;
            });
        } catch(Throwable ignore){}
        assertNotNull(fut, "future can not be null");
        Throwable t= null;
        try {
           Object value =  fut.get();
           assertNull(value, "Not null return values must been null");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            t = e;
        }
        System.out.println(t.getCause().getMessage() +"  "+t.getCause().getClass().getName());
        assertNotNull(t, "exception should have been throw" );
        assertEquals(toThrow, t.getCause().getClass()," exception type did not match");
        executor.shutdown();
    }
}

