package org.yawlfoundation.yawl.graalpy.load;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Tests concurrent execution performance and scalability
 */
public class ConcurrencyTester {
    
    private final ExecutorService executor;
    private final MetricsCollector metrics;
    private final int threadCount;
    
    public ConcurrencyTester(int threadCount, MetricsCollector metrics) {
        this.threadCount = threadCount;
        this.metrics = metrics;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    /**
     * Tests concurrent execution with the given workload
     */
    public ConcurrencyResult testConcurrentExecution(Supplier<Long> workload, 
                                                   int iterations, 
                                                   int warmupIterations) {
        
        // Warmup phase
        for (int i = 0; i < warmupIterations; i++) {
            try {
                workload.get();
            } catch (Exception e) {
                // Log warmup errors but continue
                System.err.println("Warmup error: " + e.getMessage());
            }
        }
        
        // Reset metrics for measurement
        metrics.reset();
        
        // Test phase
        long startTime = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(iterations);
        AtomicInteger activeThreads = new AtomicInteger(0);
        
        for (int i = 0; i < iterations; i++) {
            final int taskId = i;
            
            executor.submit(() -> {
                try {
                    activeThreads.incrementAndGet();
                    long taskStart = System.currentTimeMillis();
                    
                    // Execute workload
                    long result = workload.get();
                    long duration = System.currentTimeMillis() - taskStart;
                    
                    // Record metrics
                    metrics.recordExecutionTime(duration);
                    metrics.recordSuccess();
                    
                    // Simulate some work
                    Thread.sleep(1);
                    
                } catch (Exception e) {
                    metrics.recordFailure();
                    System.err.println("Task " + taskId + " failed: " + e.getMessage());
                } finally {
                    activeThreads.decrementAndGet();
                    latch.countDown();
                }
            });
        }
        
        // Wait for all tasks to complete
        try {
            latch.await(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Calculate performance metrics
        double throughput = metrics.calculateThroughput(duration);
        double speedup = calculateSpeedup(iterations, duration);
        boolean meetsTargets = evaluatePerformance(throughput, speedup);
        
        return new ConcurrencyResult(
            iterations,
            duration,
            throughput,
            speedup,
            metrics.calculateAverageLatency(),
            metrics.calculateP50Latency(),
            metrics.calculateP95Latency(),
            metrics.calculateP99Latency(),
            metrics.getSuccessfulOperations(),
            metrics.getFailedOperations(),
            meetsTargets
        );
    }
    
    /**
     * Tests linear scalability by increasing thread count
     */
    public ScalabilityResult testLinearScalability(Supplier<Long> workload, 
                                                  int maxThreads, 
                                                  int iterationsPerThread) {
        
        int[] threadCounts = {1, 2, 4, 8, 16, 32, 64, 128};
        double[] speedups = new double[threadCounts.length];
        double[] throughputs = new double[threadCounts.length];
        
        // Run baseline with 1 thread
        ConcurrencyResult baseline = testConcurrentExecution(
            workload, iterationsPerThread, 5);
        
        // Test different thread counts
        for (int i = 0; i < threadCounts.length && threadCounts[i] <= maxThreads; i++) {
            try {
                ConcurrencyResult result = testConcurrentExecution(
                    workload, 
                    iterationsPerThread,
                    5);
                
                speedups[i] = result.speedup();
                throughputs[i] = result.throughput();
                
            } catch (Exception e) {
                speedups[i] = 0;
                throughputs[i] = 0;
                System.err.println("Failed to test " + threadCounts[i] + " threads: " + e.getMessage());
            }
        }
        
        return new ScalabilityResult(
            threadCounts,
            speedups,
            throughputs,
            baseline.throughput()
        );
    }
    
    /**
     * Tests resource contention scenarios
     */
    public ContentionResult testResourceContention(Supplier<Long> workload, 
                                                  int threadCount, 
                                                  long testDurationMs) {
        
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(1000);
        ThreadPoolExecutor contentedExecutor = new ThreadPoolExecutor(
            threadCount, threadCount, 60, TimeUnit.SECONDS, workQueue);
        
        metrics.reset();
        long startTime = System.currentTimeMillis();
        int operations = 0;
        
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            try {
                contentedExecutor.submit(() -> {
                    try {
                        long taskStart = System.currentTimeMillis();
                        workload.get();
                        long duration = System.currentTimeMillis() - taskStart;
                        
                        metrics.recordExecutionTime(duration);
                        metrics.recordSuccess();
                        
                    } catch (Exception e) {
                        metrics.recordFailure();
                    }
                });
                
                operations++;
                Thread.sleep(1);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Shutdown executor
        contentedExecutor.shutdown();
        try {
            contentedExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        
        return new ContentionResult(
            threadCount,
            operations,
            endTime - startTime,
            metrics.calculateAverageLatency(),
            metrics.calculateP95Latency(),
            workQueue.size(),
            !contentedExecutor.isTerminated()
        );
    }
    
    /**
     * Calculates speedup relative to single-threaded performance
     */
    private double calculateSpeedup(int operations, long duration) {
        if (duration <= 0) return 0;
        
        double singleThreadedOpsPerSec = (double) operations / (duration / 1000.0);
        double theoreticalMax = threadCount * singleThreadedOpsPerSec;
        
        return theoreticalMax > 0 ? (singleThreadedOpsPerSec * threadCount) / theoreticalMax : 0;
    }
    
    /**
     * Evaluates if performance meets targets
     */
    private boolean evaluatePerformance(double throughput, double speedup) {
        boolean throughputOk = throughput >= PerformanceTargets.MIN_THROUGHPUT_OPS_PER_SEC;
        boolean speedupOk = PerformanceTargets.isParallelSpeedupAcceptable(speedup);
        
        return throughputOk && speedupOk;
    }
    
    /**
     * Shuts down the executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Result for concurrency test
     */
    public record ConcurrencyResult(
        int totalOperations,
        long durationMs,
        double throughput,
        double speedup,
        double avgLatency,
        long p50Latency,
        long p95Latency,
        long p99Latency,
        int successfulOperations,
        int failedOperations,
        boolean meetsTargets
    ) {}
    
    /**
     * Result for scalability test
     */
    public record ScalabilityResult(
        int[] threadCounts,
        double[] speedups,
        double[] throughputs,
        double baselineThroughput
    ) {}
    
    /**
     * Result for contention test
     */
    public record ContentionResult(
        int threadCount,
        int operationsCompleted,
        long durationMs,
        double avgLatency,
        long p95Latency,
        int queueSize,
        boolean executorTimedOut
    ) {}
}
