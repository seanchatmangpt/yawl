import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;

public class SimpleBenchmark {
    
    public static void main(String[] args) {
        System.out.println("YAWL Performance Benchmark Suite");
        System.out.println("================================");
        
        // Run different benchmark types
        runVirtualThreadBenchmark();
        runMemoryBenchmark();
        runLockContentionBenchmark();
        
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
        
        AtomicLong totalTime = new AtomicLong();
        CountDownLatch latch = new CountDownLatch(cases);
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < cases; i++) {
            final int caseId = i;
            executor.submit(() -> {
                try {
                    // Simulate workflow execution with I/O
                    simulateWorkflow(ioLatencyMs);
                    totalTime.addAndGet(3 * ioLatencyMs);
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
                    // Simulate workflow execution with I/O
                    simulateWorkflow(ioLatencyMs);
                    totalTime.addAndGet(3 * ioLatencyMs);
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
    
    private static void simulateWorkflow(int ioLatencyMs) throws InterruptedException {
        for (int task = 0; task < 3; task++) {
            // Simulate I/O wait (Hibernate query, network call)
            Thread.sleep(ioLatencyMs);
        }
    }
    
    private static void runMemoryBenchmark() {
        System.out.println("\n--- Memory Usage Benchmark ---");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Measure memory usage
        int iterations = 10000;
        
        // ArrayList memory usage
        long startMemory = runtime.totalMemory() - runtime.freeMemory();
        List<String> list = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            list.add("work-item-" + i);
        }
        long endMemory = runtime.totalMemory() - runtime.freeMemory();
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
        
        // Object creation test
        startMemory = runtime.totalMemory() - runtime.freeMemory();
        List<Object> objects = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            objects.add(new Object());
        }
        endMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.printf("Plain objects (%d): %,d bytes%n", 
            iterations, endMemory - startMemory);
    }
    
    private static void runLockContentionBenchmark() {
        System.out.println("\n--- Lock Contention Benchmark ---");
        
        int threads = 10;
        int iterations = 10000;
        
        // Test synchronized vs ReentrantLock
        long syncTime = testSynchronized(threads, iterations);
        long reentrantTime = testReentrantLock(threads, iterations);
        
        System.out.printf("synchronized: %,dms | ReentrantLock: %,dms | Ratio: %.2f%n",
            syncTime, reentrantTime, (double) syncTime / reentrantTime);
    }
    
    private static long testSynchronized(int threads, int iterations) {
        Counter counter = new SynchronizedCounter();
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        
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
    
    private static long testReentrantLock(int threads, int iterations) {
        Counter counter = new ReentrantLockCounter();
        
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        long start = System.currentTimeMillis();
        
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
    
    interface Counter {
        void increment();
    }
    
    static class SynchronizedCounter implements Counter {
        private int count = 0;
        
        @Override
        public synchronized void increment() {
            count++;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    static class ReentrantLockCounter implements Counter {
        private int count = 0;
        private final ReentrantLock lock = new ReentrantLock();
        
        @Override
        public void increment() {
            lock.lock();
            try {
                count++;
            } finally {
                lock.unlock();
            }
        }
        
        public int getCount() {
            return count;
        }
    }
}
