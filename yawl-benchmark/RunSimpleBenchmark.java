import java.lang.management.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple YAWL performance test to verify setup
 */
public class RunSimpleBenchmark {
    
    public static void main(String[] args) {
        System.out.println("YAWL Simple Performance Test");
        System.out.println("============================");
        
        // Memory baseline
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        System.out.println("Initial memory:");
        System.out.println("  Max memory: " + runtime.maxMemory() / 1024 / 1024 + " MB");
        System.out.println("  Total memory: " + runtime.totalMemory() / 1024 / 1024 + " MB");
        System.out.println("  Free memory: " + runtime.freeMemory() / 1024 / 1024 + " MB");
        System.out.println("  Used memory: " + (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + " MB");
        
        // Thread baseline
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        System.out.println("\nInitial threads: " + threadBean.getThreadCount());
        
        // Test virtual thread performance
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger counter = new AtomicInteger(0);
        int taskCount = 1000;
        
        System.out.println("\nTesting virtual thread throughput...");
        long startTime = System.nanoTime();
        
        // Simulate work item processing
        CountDownLatch latch = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            virtualExecutor.submit(() -> {
                try {
                    // Simulate YAWL work item processing
                    Thread.sleep(5); // 5ms simulated I/O
                    counter.incrementAndGet();
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        try {
            latch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        double throughput = taskCount / (durationMs / 1000.0);
        
        System.out.println("  Tasks completed: " + taskCount);
        System.out.println("  Duration: " + String.format("%.2f ms", durationMs));
        System.out.println("  Throughput: " + String.format("%.0f ops/sec", throughput));
        
        // Final metrics
        System.out.println("\nFinal metrics:");
        System.out.println("  Threads: " + threadBean.getThreadCount());
        System.out.println("  CPU time: " + threadBean.getThreadCpuTime(threadBean.getAllThreadIds()) / 1_000_000 + " ms");
        System.out.println("  Memory used: " + 
            (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + " MB");
        
        // GC metrics
        GarbageCollectorMXBean gcBean = ManagementFactory.getGarbageCollectorMXBeans().get(0);
        if (gcBean != null) {
            System.out.println("  GC collection count: " + gcBean.getCollectionCount());
            System.out.println("  GC collection time: " + gcBean.getCollectionTime() + " ms");
        }
        
        virtualExecutor.shutdown();
        
        System.out.println("\nTest completed successfully!");
    }
}
