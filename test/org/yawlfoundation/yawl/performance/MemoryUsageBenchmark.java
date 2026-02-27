import java.lang.management.*;
import java.util.*;

public class MemoryUsageBenchmark {
    
    public static void main(String[] args) {
        System.out.println("Memory Usage Benchmark");
        System.out.println("=====================");
        
        runGarbageCollectionBenchmark();
        runObjectAllocationBenchmark();
        runMemoryPressureBenchmark();
        runCacheEfficiencyBenchmark();
        
        System.out.println("\nMemory usage benchmark completed!");
    }
    
    private static void runGarbageCollectionBenchmark() {
        System.out.println("\n--- Garbage Collection Benchmark ---");
        
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        
        // Force GC to clean state
        runtime.gc();
        
        // Allocate objects
        List<String> strings = new ArrayList<>(100000);
        for (int i = 0; i < 100000; i++) {
            strings.add("string-" + i);
        }
        
        // Measure GC time after allocation
        MemoryUsage beforeGC = memoryBean.getHeapMemoryUsage();
        long startTime = System.currentTimeMillis();
        
        runtime.gc();
        
        long gcTime = System.currentTimeMillis() - startTime;
        MemoryUsage afterGC = memoryBean.getHeapMemoryUsage();
        
        System.out.printf("GC time: %dms%n", gcTime);
        System.out.printf("Memory reclaimed: %,d bytes%n", 
            afterGC.getUsed() - beforeGC.getUsed());
    }
    
    private static void runObjectAllocationBenchmark() {
        System.out.println("\n--- Object Allocation Benchmark ---");
        
        int[] allocationSizes = {1000, 10000, 100000};
        
        for (int size : allocationSizes) {
            long allocationTime = testObjectAllocation(size);
            double allocPerSec = size / (allocationTime / 1000.0);
            
            System.out.printf("Objects: %,d | Time: %,dms | Rate: %,.0f objects/sec%n",
                size, allocationTime, allocPerSec);
        }
    }
    
    private static long testObjectAllocation(int size) {
        long start = System.currentTimeMillis();
        
        List<Object> objects = new ArrayList<>(size);
        
        for (int i = 0; i < size; i++) {
            objects.add(new TestObject("obj-" + i));
        }
        
        return System.currentTimeMillis() - start;
    }
    
    private static void runMemoryPressureBenchmark() {
        System.out.println("\n--- Memory Pressure Benchmark ---");
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Fill memory gradually
        List<byte[]> memoryBatches = new ArrayList<>();
        int batchSize = 1024 * 1024; // 1MB batches
        
        while (usedMemory < maxMemory * 0.9) { // Stop at 90% usage
            byte[] batch = new byte[batchSize];
            memoryBatches.add(batch);
            usedMemory = runtime.totalMemory() - runtime.freeMemory();
            
            if (memoryBatches.size() % 100 == 0) {
                System.out.printf("Memory usage: %,d MB / %,d MB (%.1f%%)%n",
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024),
                    (double) usedMemory / maxMemory * 100);
            }
        }
    }
    
    private static void runCacheEfficiencyBenchmark() {
        System.out.println("\n--- Cache Efficiency Benchmark ---");
        
        int iterations = 100000;
        
        // Test in-cache access
        long cacheTime = testCacheAccess(iterations, true);
        
        // Test out-of-cache access
        long outCacheTime = testCacheAccess(iterations, false);
        
        System.out.printf("In-cache access: %,dms%n", cacheTime);
        System.out.printf("Out-of-cache access: %,dms%n", outCacheTime);
        System.out.printf("Cache efficiency: %.2fx%n", 
            (double) outCacheTime / cacheTime);
    }
    
    private static long testCacheAccess(int iterations, boolean inCache) {
        long start = System.currentTimeMillis();
        
        List<Integer> data;
        
        if (inCache) {
            // Small list that fits in CPU cache
            data = new ArrayList<>(1000);
            for (int i = 0; i < 1000; i++) {
                data.add(i);
            }
            
            // Repeatedly access the same data
            for (int i = 0; i < iterations; i++) {
                int value = data.get(i % 1000);
                // Use value to prevent optimization
                if (value < 0) System.out.println();
            }
        } else {
            // Large list that doesn't fit in cache
            data = new ArrayList<>(1000000);
            for (int i = 0; i < 1000000; i++) {
                data.add(i);
            }
            
            // Access random elements to cause cache misses
            Random random = new Random();
            for (int i = 0; i < iterations; i++) {
                int index = random.nextInt(1000000);
                int value = data.get(index);
                // Use value to prevent optimization
                if (value < 0) System.out.println();
            }
        }
        
        return System.currentTimeMillis() - start;
    }
    
    static class TestObject {
        String id;
        long timestamp;
        
        public TestObject(String id) {
            this.id = id;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
