import java.util.*;
import java.util.concurrent.*;

public class WorkingBenchmark {
    
    public static void main(String[] args) {
        System.out.println("YAWL Performance Benchmark Suite");
        System.out.println("================================");
        
        // Run different benchmark types
        runVirtualThreadBenchmark();
        runMemoryBenchmark();
        runConcurrencyBenchmark();
        
        System.out.println("\nAll benchmarks completed!");
    }
    
    private static void runVirtualThreadBenchmark() {
        System.out.println("\n--- Virtual Thread Benchmark ---");
        
        int[] concurrentCases = {10, 100, 500};
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
        
        CountDownLatch latch = new CountDownLatch(cases);
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate workflow execution with I/O
                    Thread.sleep(ioLatencyMs * 3); // 3 tasks * ioLatencyMs
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
        
        CountDownLatch latch = new CountDownLatch(cases);
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate workflow execution with I/O
                    Thread.sleep(ioLatencyMs * 3); // 3 tasks * ioLatencyMs
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
        long startMemory, endMemory;
        
        // Measure memory usage
        int iterations = 10000;
        
        // ArrayList memory usage
        startMemory = runtime.totalMemory() - runtime.freeMemory();
        List<String> list = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            list.add("work-item-" + i);
        }
        endMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("ArrayList (%d items): %,d bytes%n", 
            iterations, endMemory - startMemory);
        
        // HashMap memory usage
        startMemory = runtime.totalMemory() - runtime.freeMemory();
        Map<String, String> map = new HashMap<>(iterations);
        for (int i = 0; i < iterations; i++) {
            map.put("key-" + i, "value-" + i);
        }
        endMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("HashMap (%d items): %,d bytes%n", 
            iterations, endMemory - startMemory);
    }
    
    private static void runConcurrencyBenchmark() {
        System.out.println("\n--- Concurrency Benchmark ---");
        
        int threads = 10;
        int iterations = 10000;
        
        long syncTime = testSynchronized(threads, iterations);
        long futureTime = testCompletableFuture(threads, iterations);
        
        System.out.printf("Synchronized: %,dms | CompletableFuture: %,dms | Ratio: %.2f%n",
            syncTime, futureTime, (double) syncTime / futureTime);
    }
    
    private static long testSynchronized(int threads, int iterations) {
        long start = System.currentTimeMillis();
        Counter counter = new SynchronizedCounter();
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                for (int j = 0; j < iterations; j++) {
                    counter.increment();
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return System.currentTimeMillis() - start;
    }
    
    private static long testCompletableFuture(int threads, int iterations) {
        long start = System.currentTimeMillis();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int j = 0; j < iterations; j++) {
                    // Just simulate work without actual counter
                }
            }));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return System.currentTimeMillis() - start;
    }
    
    static class SynchronizedCounter {
        private int count = 0;
        
        public synchronized void increment() {
            count++;
        }
    }
}
