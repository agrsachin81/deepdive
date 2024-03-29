import com.champsworld.concurrent.OrderedTask;
import com.champsworld.concurrent.OrderedTaskExecutor;
import com.champsworld.concurrent.SampleOrderedTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Ordered Tester, WIP
 * @author agrsachin81
 */
class OrderedTaskExecutorTest {

    @Test
    void submit() {

        OrderedTaskExecutor executor = new OrderedTaskExecutor(2000);
        OrderedTask<String> task = new SampleOrderedTask("Sample");
        List<CompletableFuture<String>> results = new ArrayList<>();
        for (int i=0; i< 20; i++){
            results.add(executor.submit(task));
            results.add(executor.submit(new SampleOrderedTask("Sam" +i, i %5)));
            //System.out.println(retVal.get());
        }
        results.add(executor.submit( ()-> {
            throw new RuntimeException("Unable to perform task for xyz reasons");
        }));
        for (CompletableFuture<String> fut: results) {
            fut.whenComplete((s, t )-> {
                if(s!=null) {
                    System.out.println(s);
                } else {
                    System.out.println(t.getMessage());
                }
            });
        }

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
}