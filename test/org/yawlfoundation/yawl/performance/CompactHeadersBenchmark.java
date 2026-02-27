import java.lang.management.*;
import java.util.*;

public class CompactHeadersBenchmark {
    
    public static void main(String[] args) {
        System.out.println("Compact Object Headers Benchmark");
        System.out.println("==============================");
        
        runMemoryBenchmark();
        runThroughputBenchmark();
        runCacheBenchmark();
        
        System.out.println("\nCompact headers benchmark completed!");
    }
    
    private static void runMemoryBenchmark() {
        System.out.println("\n--- Memory Usage with Compact Headers ---");
        
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Test object creation
        int iterations = 10000;
        
        // Small objects (strings)
        long startMemory = memoryBean.getHeapMemoryUsage().getUsed();
        List<String> strings = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            strings.add("item-" + i);
        }
        long endMemory = memoryBean.getHeapMemoryUsage().getUsed();
        
        System.out.printf("Small objects (%d): %,d bytes%n", 
            iterations, endMemory - startMemory);
        
        // Medium objects (HashMap entries)
        Map<String, String> map = new HashMap<>(iterations);
        for (int i = 0; i < iterations; i++) {
            map.put("key-" + i, "value-" + i);
        }
        long mapMemory = memoryBean.getHeapMemoryUsage().getUsed() - endMemory;
        System.out.printf("HashMap entries (%d): %,d bytes%n", 
            iterations, mapMemory);
        
        // Large objects (custom objects)
        List<WorkItem> workItems = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            workItems.add(new WorkItem("case-" + i, "task-" + i));
        }
        long workItemMemory = memoryBean.getHeapMemoryUsage().getUsed() - 
                            (memoryBean.getHeapMemoryUsage().getUsed() - mapMemory);
        System.out.printf("WorkItem objects (%d): %,d bytes%n", 
            iterations, workItemMemory);
    }
    
    private static void runThroughputBenchmark() {
        System.out.println("\n--- Throughput Benchmark ---");
        
        // Test object creation throughput
        int iterations = 100000;
        
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            String str = "work-item-" + i;
            Map<String, String> map = new HashMap<>();
            map.put("id", str);
            map.put("status", "active");
        }
        
        long end = System.currentTimeMillis();
        double opsPerSec = iterations / ((end - start) / 1000.0);
        
        System.out.printf("Object creation throughput: %,.0f ops/sec%n", opsPerSec);
    }
    
    private static void runCacheBenchmark() {
        System.out.println("\n--- Cache Efficiency Benchmark ---");
        
        // Test cache performance with small vs large objects
        int iterations = 5000;
        
        // Small objects cache
        long start = System.currentTimeMillis();
        List<String> smallCache = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            smallCache.add("small-" + i);
            String item = smallCache.get(i % 1000); // Access cached items
        }
        long smallCacheTime = System.currentTimeMillis() - start;
        
        // Large objects cache
        start = System.currentTimeMillis();
        List<WorkItem> largeCache = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            largeCache.add(new WorkItem("case-" + i, "task-" + i));
            WorkItem item = largeCache.get(i % 1000); // Access cached items
        }
        long largeCacheTime = System.currentTimeMillis() - start;
        
        System.out.printf("Small objects cache: %dms%n", smallCacheTime);
        System.out.printf("Large objects cache: %dms%n", largeCacheTime);
        System.out.printf("Cache efficiency ratio: %.2f%n", 
            (double) smallCacheTime / largeCacheTime);
    }
    
    static class WorkItem {
        String caseId;
        String taskId;
        
        public WorkItem(String caseId, String taskId) {
            this.caseId = caseId;
            this.taskId = taskId;
        }
    }
}
