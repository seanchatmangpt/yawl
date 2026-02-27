import java.util.*;
import java.util.concurrent.*;

public class StructuredConcurrencyBenchmark {
    
    public static void main(String[] args) {
        System.out.println("Structured Concurrency Benchmark");
        System.out.println("===============================");
        
        runBasicStructuredBenchmark();
        runExceptionHandlingBenchmark();
        runParallelProcessingBenchmark();
        
        System.out.println("\nStructured concurrency benchmark completed!");
    }
    
    private static void runBasicStructuredBenchmark() {
        System.out.println("\n--- Basic Structured Concurrency ---");
        
        int tasks = 10;
        
        // Structured concurrency (simulated)
        long structuredTime = testStructuredExecution(tasks);
        
        // Traditional parallel execution
        long traditionalTime = testTraditionalExecution(tasks);
        
        System.out.printf("Structured: %dms | Traditional: %dms | Ratio: %.2f%n",
            structuredTime, traditionalTime, (double) structuredTime / traditionalTime);
    }
    
    private static long testStructuredExecution(int tasks) {
        long start = System.currentTimeMillis();
        
        // Use virtual thread per task with structured approach
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        // Fork all tasks
        for (int i = 0; i < tasks; i++) {
            final int taskId = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate work
                    Thread.sleep(10);
                    return "task-" + taskId;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static long testTraditionalExecution(int tasks) {
        long start = System.currentTimeMillis();
        
        // Use platform thread pool
        ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        // Fork all tasks
        for (int i = 0; i < tasks; i++) {
            final int taskId = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate work
                    Thread.sleep(10);
                    return "task-" + taskId;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static void runExceptionHandlingBenchmark() {
        System.out.println("\n--- Exception Handling Benchmark ---");
        
        int tasks = 5;
        
        // Test exception propagation
        long exceptionTime = testExceptionPropagation(tasks);
        
        // Test normal execution
        long normalTime = testNormalExecution(tasks);
        
        System.out.printf("With exceptions: %dms | Normal: %dms | Overhead: %.2f%n",
            exceptionTime, normalTime, (double) exceptionTime / normalTime);
    }
    
    private static long testExceptionPropagation(int tasks) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            
            for (int i = 0; i < tasks; i++) {
                final int taskId = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    if (taskId == 2) {
                        throw new RuntimeException("Task " + taskId + " failed");
                    }
                    try {
                        Thread.sleep(10);
                        return "task-" + taskId;
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }, executor));
            }
            
            // This will throw exception immediately when any task fails
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
        } catch (Exception e) {
            // Exception expected
        }
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static long testNormalExecution(int tasks) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (int i = 0; i < tasks; i++) {
            final int taskId = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(10);
                    return "task-" + taskId;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, executor));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static void runParallelProcessingBenchmark() {
        System.out.println("\n--- Parallel Processing Benchmark ---");
        
        int dataSets = 100;
        int itemsPerSet = 1000;
        
        // Process data sets in parallel
        long parallelTime = testParallelProcessing(dataSets, itemsPerSet);
        
        // Process data sets sequentially
        long sequentialTime = testSequentialProcessing(dataSets, itemsPerSet);
        
        System.out.printf("Parallel: %dms | Sequential: %dms | Speedup: %.2fx%n",
            parallelTime, sequentialTime, (double) sequentialTime / parallelTime);
    }
    
    private static long testParallelProcessing(int dataSets, int itemsPerSet) {
        long start = System.currentTimeMillis();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < dataSets; i++) {
            final int setId = i;
            futures.add(CompletableFuture.runAsync(() -> {
                // Process data set
                for (int j = 0; j < itemsPerSet; j++) {
                    // Simulate processing
                    Math.sqrt(j + setId);
                }
            }, executor));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        executor.shutdown();
        return System.currentTimeMillis() - start;
    }
    
    private static long testSequentialProcessing(int dataSets, int itemsPerSet) {
        long start = System.currentTimeMillis();
        
        for (int i = 0; i < dataSets; i++) {
            for (int j = 0; j < itemsPerSet; j++) {
                // Simulate processing
                Math.sqrt(j + i);
            }
        }
        
        return System.currentTimeMillis() - start;
    }
}
