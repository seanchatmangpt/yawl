import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/*
 * Manual benchmark runner that doesn't require JMH annotations
 */

public class ManualBenchmarkRunner {
    
    // JVM configuration for optimal virtual thread performance
    private static final String JVM_CONFIG = 
        "-XX:+UseCompactObjectHeaders " +  // Enable compact object headers
        "-XX:+UseZGC " +                  // Use Z garbage collector
        "-Xms2g -Xmx4g";                  // Heap size

    public static void main(String[] args) {
        System.out.println("YAWL Performance Benchmark Suite (Manual Mode)");
        System.out.println("JVM Configuration: " + JVM_CONFIG);
        System.out.println("==============================================");
        
        // Run different benchmark types
        runVirtualThreadBenchmark();
        runMemoryBenchmark();
        runStructuredConcurrencyBenchmark();
        
        System.out.println("\nAll benchmarks completed!");
    }
    
    private static void runVirtualThreadBenchmark() {
        System.out.println("\n--- Virtual Thread Benchmark ---");
        
        int[] concurrentCases = {10, 100, 500, 1000};
        int ioLatencyMs = 5;
        
        for (int cases : concurrentCases) {
            // Platform threads
            long platformTime = runPlatformThreadBenchmark(cases, ioLatencyMs);
            
            // Virtual threads  
            long virtualTime = runVirtualThreadBenchmark(cases, ioLatencyMs);
            
            double speedup = (double) platformTime / virtualTime;
            
            System.out.printf("Concurrent cases: %4d | Platform: %6dms | Virtual: %6dms | Speedup: %.2fx%n",
                cases, platformTime, virtualTime, speedup);
        }
    }
    
    private static long runPlatformThreadBenchmark(int cases, int ioLatencyMs) {
        ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
        
        AtomicLong totalTime = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(cases);
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate workflow execution
                    for (int task = 0; task < 3; task++) {
                        Thread.sleep(ioLatencyMs); // Simulate I/O
                    }
                    totalTime.addAndGet(3 * ioLatencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static long runVirtualThreadBenchmark(int cases, int ioLatencyMs) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        AtomicLong totalTime = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(cases);
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate workflow execution
                    for (int task = 0; task < 3; task++) {
                        Thread.sleep(ioLatencyMs); // Simulate I/O
                    }
                    totalTime.addAndGet(3 * ioLatencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static void runMemoryBenchmark() {
        System.out.println("\n--- Memory Usage Benchmark ---");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Measure memory usage for different collections
        int iterations = 10000;
        
        // ArrayList test
        long startMemory = runtime.totalMemory() - runtime.freeMemory();
        List<String> list = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            list.add("work-item-" + i);
        }
        long endMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("ArrayList (%d items): %,d bytes%n", 
            iterations, endMemory - startMemory);
        
        // Virtual thread allocation test
        startMemory = runtime.totalMemory() - runtime.freeMemory();
        for (int i = 0; i < 1000; i++) {
            Thread.ofVirtual().name("vt-" + i).start(() -> {
                // Do nothing, just create virtual thread
            });
        }
        // Give virtual threads time to start and then stop
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        endMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Virtual threads (1000): %,d bytes%n", 
            endMemory - startMemory);
    }
    
    private static void runStructuredConcurrencyBenchmark() {
        System.out.println("\n--- Structured Concurrency Benchmark ---");
        
        // Test structured concurrency vs CompletableFuture
        int parallelTasks = 10;
        
        // Structured concurrency (Java 25 preview feature)
        long structuredTime = testStructuredConcurrency(parallelTasks);
        
        // CompletableFuture
        long futureTime = testCompletableFuture(parallelTasks);
        
        System.out.printf("Structured: %dms | CompletableFuture: %dms | Ratio: %.2f%n",
            structuredTime, futureTime, (double) futureTime / structuredTime);
    }
    
    private static long testStructuredConcurrency(int tasks) {
        long start = System.currentTimeMillis();
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < tasks; i++) {
                scope.fork(() -> {
                    Thread.sleep(10); // Simulate work
                    return "task-" + i;
                });
            }
            scope.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return System.currentTimeMillis() - start;
    }
    
    private static long testCompletableFuture(int tasks) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < tasks; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(10);
                    return "task-" + i;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return System.currentTimeMillis() - start;
    }
}
