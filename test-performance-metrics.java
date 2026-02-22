// YAWL Engine Performance Test Metrics Collection
// This demonstrates how to collect and measure performance metrics

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.time.*;
import java.util.*;

/**
 * Performance Metrics Collection for YAWL Engine
 * Provides comprehensive measurement of key performance indicators
 */
public class YAWLPerformanceMetrics {
    
    // Core performance counters
    private final AtomicInteger _totalCasesCreated = new AtomicInteger();
    private final AtomicInteger _totalWorkItemsProcessed = new AtomicInteger();
    private final LongAdder _totalTaskCompletionTime = new LongAdder();
    private final AtomicInteger _activeConcurrentCases = new AtomicInteger();
    
    // Lock contention metrics
    private final LongAdder _lockWaitTimeNanos = new LongAdder();
    private final AtomicInteger _lockContentionCount = new AtomicInteger();
    
    // Database performance metrics
    private final LongAdder _queryTimeNanos = new LongAdder();
    private final AtomicInteger _queryCount = new AtomicInteger();
    
    // Memory metrics
    private final LongAdder _memoryAllocatedBytes = new LongAdder();
    private final AtomicInteger _gcCount = new AtomicInteger();
    private final LongAdder _gcTimeNanos = new LongAdder();
    
    // Error metrics
    private final AtomicInteger _errorCount = new AtomicInteger();
    private final LongAdder _errorRecoveryTimeNanos = new LongAdder();
    
    // Latency percentiles (using reservoir sampling)
    private final PercentileSampler _caseCreationLatency = new PercentileSampler();
    private final PercentileSampler _workItemProcessLatency = new PercentileSampler();
    private final PercentileSampler _taskCompletionLatency = new PercentileSampler();
    
    // Performance thresholds
    private static final long CASE_CREATION_P95_THRESHOLD_MS = 500;
    private static final long WORK_ITEM_P95_THRESHOLD_MS = 200;
    private static final long TASK_COMPLETION_P95_THRESHOLD_MS = 100;
    private static final long LOCK_CONTENTION_THRESHOLD_MS = 100;
    private static final long GC_PAUSE_THRESHOLD_MS = 200;
    
    /**
     * Case creation with performance measurement
     */
    public YIdentifier createCase(YSpecificationID specID) throws Exception {
        long startNanos = System.nanoTime();
        YIdentifier caseID = null;
        
        try {
            // Simulate case creation
            caseID = YEngine.getInstance().startCase(
                specID, null, null, null, 
                new YLogDataItemList(), null, false
            );
            
            _totalCasesCreated.incrementAndGet();
            _activeConcurrentCases.incrementAndGet();
            
            // Record latency
            long latencyNanos = System.nanoTime() - startNanos;
            _caseCreationLatency.addSample(latencyNanos / 1_000_000.0); // Convert to ms
            
        } catch (Exception e) {
            _errorCount.incrementAndGet();
            throw e;
        } finally {
            _activeConcurrentCases.decrementAndGet();
        }
        
        return caseID;
    }
    
    /**
     * Work item processing with performance measurement
     */
    public void processWorkItem(YWorkItem item) throws Exception {
        long startNanos = System.nanoTime();
        
        try {
            // Simulate work item processing
            long taskStart = System.nanoTime();
            
            // Simulate database query
            simulateDatabaseQuery();
            
            // Simulate task execution
            Thread.sleep(1); // Simulate work
            
            long taskEnd = System.nanoTime();
            _totalTaskCompletionTime.add(taskEnd - taskStart);
            
            // Record metrics
            _totalWorkItemsProcessed.incrementAndGet();
            
            long totalTimeNanos = System.nanoTime() - startNanos;
            _workItemProcessLatency.addSample(totalTimeNanos / 1_000_000.0);
            
        } catch (Exception e) {
            _errorCount.incrementAndGet();
            throw e;
        }
    }
    
    /**
     * Lock contention measurement
     */
    public void measureLockContention(Runnable operation) {
        long lockWaitStart = System.nanoTime();
        
        try {
            // Simulate lock acquisition
            Thread.sleep((long) (Math.random() * 10)); // Random wait time
            operation.run();
        } finally {
            long lockWaitNanos = System.nanoTime() - lockWaitStart;
            
            if (lockWaitNanos > LOCK_CONTENTION_THRESHOLD_MS * 1_000_000) {
                _lockContentionCount.incrementAndGet();
            }
            
            _lockWaitTimeNanos.add(lockWaitNanos);
        }
    }
    
    /**
     * Database query performance measurement
     */
    private void simulateDatabaseQuery() throws InterruptedException {
        long queryStart = System.nanoTime();
        
        // Simulate database query time
        long queryTimeMs = (long) (10 + Math.random() * 90); // 10-100ms
        Thread.sleep(queryTimeMs);
        
        long queryEnd = System.nanoTime();
        _queryTimeNanos.add(queryEnd - queryStart);
        _queryCount.incrementAndGet();
    }
    
    /**
     * Memory usage monitoring
     */
    public void monitorMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        _memoryAllocatedBytes.add(usedMemory);
        
        // Simulate GC monitoring
        if (Math.random() < 0.01) { // 1% chance of GC event
            long gcStart = System.nanoTime();
            System.gc();
            long gcEnd = System.nanoTime();
            _gcCount.incrementAndGet();
            _gcTimeNanos.add(gcEnd - gcStart);
        }
    }
    
    /**
     * Generate performance report
     */
    public PerformanceReport generateReport() {
        double caseCreationP95 = _caseCreationLatency.getPercentile(95);
        double workItemP95 = _workItemProcessLatency.getPercentile(95);
        double taskCompletionP95 = _taskCompletionLatency.getPercentile(95);
        
        double avgQueryTime = _queryCount.get() > 0 ? 
            (_queryTimeNanos.sum() / _queryCount.get()) / 1_000_000.0 : 0;
        
        double avgLockWaitTime = _lockContentionCount.get() > 0 ?
            (_lockWaitTimeNanos.sum() / _lockContentionCount.get()) / 1_000_000.0 : 0;
        
        return new PerformanceReport(
            Instant.now(),
            _totalCasesCreated.get(),
            _totalWorkItemsProcessed.get(),
            _activeConcurrentCases.get(),
            caseCreationP95,
            workItemP95,
            taskCompletionP95,
            avgQueryTime,
            avgLockWaitTime,
            _errorCount.get(),
            checkThresholds(caseCreationP95, workItemP95, taskCompletionP95, avgLockWaitTime)
        );
    }
    
    /**
     * Check if performance thresholds are met
     */
    private ThresholdStatus checkThresholds(
        double caseCreationP95, 
        double workItemP95, 
        double taskCompletionP95,
        double avgLockWaitTime) {
        
        boolean withinTargets = true;
        List<String> violations = new ArrayList<>();
        
        if (caseCreationP95 > CASE_CREATION_P95_THRESHOLD_MS) {
            violations.add("Case creation P95 (" + caseCreationP95 + "ms) exceeds threshold");
            withinTargets = false;
        }
        
        if (workItemP95 > WORK_ITEM_P95_THRESHOLD_MS) {
            violations.add("Work item processing P95 (" + workItemP95 + "ms) exceeds threshold");
            withinTargets = false;
        }
        
        if (taskCompletionP95 > TASK_COMPLETION_P95_THRESHOLD_MS) {
            violations.add("Task completion P95 (" + taskCompletionP95 + "ms) exceeds threshold");
            withinTargets = false;
        }
        
        if (avgLockWaitTime > LOCK_CONTENTION_THRESHOLD_MS) {
            violations.add("Average lock wait time (" + avgLockWaitTime + "ms) exceeds threshold");
            withinTargets = false;
        }
        
        return new ThresholdStatus(withinTargets, violations);
    }
    
    /**
     * Test suite for performance validation
     */
    public void runPerformanceTests() {
        System.out.println("=== YAWL Performance Test Suite ===");
        
        // Test 1: Single-threaded throughput
        testSingleThreadedThroughput();
        
        // Test 2: Multi-threaded scalability
        testMultiThreadedScalability();
        
        // Test 3: Memory usage under load
        testMemoryUsage();
        
        // Test 4: Error handling under load
        testErrorHandling();
        
        // Test 5: Lock contention measurement
        testLockContention();
        
        // Generate final report
        PerformanceReport report = generateReport();
        System.out.println("\n" + report);
    }
    
    private void testSingleThreadedThroughput() {
        System.out.println("\n--- Test 1: Single-threaded Throughput ---");
        long startTime = System.nanoTime();
        int caseCount = 100;
        
        for (int i = 0; i < caseCount; i++) {
            try {
                createCase(new YSpecificationID("test-spec", "1.0"));
                processWorkItem(new YWorkItem("case-" + i, "task-1"));
            } catch (Exception e) {
                System.err.println("Error in test: " + e.getMessage());
            }
        }
        
        long duration = System.nanoTime() - startTime;
        double throughput = (caseCount * 1000.0 * 1_000_000) / duration;
        
        System.out.printf("Throughput: %.2f cases/sec\n", throughput);
        System.out.printf("Latency P95: %.2f ms\n", _caseCreationLatency.getPercentile(95));
    }
    
    private void testMultiThreadedScalability() {
        System.out.println("\n--- Test 2: Multi-threaded Scalability ---");
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        int threadCount = 10;
        int casesPerThread = 50;
        
        long startTime = System.nanoTime();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < casesPerThread; i++) {
                    try {
                        createCase(new YSpecificationID("test-spec", "1.0"));
                        processWorkItem(new WorkItem("case-" + threadId + "-" + i, "task-1"));
                    } catch (Exception e) {
                        System.err.println("Thread " + threadId + " error: " + e.getMessage());
                    }
                }
            }));
        }
        
        // Wait for all threads to complete
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Future error: " + e.getMessage());
            }
        }
        
        long duration = System.nanoTime() - startTime;
        double totalCases = threadCount * casesPerThread;
        double throughput = (totalCases * 1000.0 * 1_000_000) / duration;
        
        System.out.printf("Concurrent throughput: %.2f cases/sec\n", throughput);
        System.out.printf("Active cases: %d\n", _activeConcurrentCases.get());
        System.out.printf("Lock wait time avg: %.2f ms\n", 
            (_lockWaitTimeNanos.sum() / 1_000_000.0) / threadCount);
    }
    
    private void testMemoryUsage() {
        System.out.println("\n--- Test 3: Memory Usage ---");
        Runtime runtime = Runtime.getRuntime();
        
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        int objectCount = 10000;
        
        // Create many objects to test GC behavior
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < objectCount; i++) {
            objects.add(new Object());
            if (i % 1000 == 0) {
                monitorMemoryUsage();
            }
        }
        
        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryPerObject = (peakMemory - initialMemory) / objectCount;
        
        System.out.printf("Memory per object: %d bytes\n", memoryPerObject);
        System.out.printf("GC count: %d\n", _gcCount.get());
        System.out.printf("Total GC time: %.2f ms\n", _gcTimeNanos.sum() / 1_000_000.0);
        
        objects.clear();
        System.gc();
    }
    
    private void testErrorHandling() {
        System.out.println("\n--- Test 4: Error Handling ---");
        int errorCount = 0;
        int totalAttempts = 100;
        
        for (int i = 0; i < totalAttempts; i++) {
            try {
                // Simulate occasional errors
                if (Math.random() < 0.05) { // 5% error rate
                    throw new RuntimeException("Simulated error");
                }
                
                createCase(new YSpecificationID("test-spec", "1.0"));
            } catch (Exception e) {
                errorCount++;
                // Log error recovery time
                long recoveryStart = System.nanoTime();
                Thread.sleep(10); // Simulate recovery
                long recoveryEnd = System.nanoTime();
                _errorRecoveryTimeNanos.add(recoveryEnd - recoveryStart);
            }
        }
        
        System.out.printf("Error rate: %.2f%%\n", (errorCount * 100.0) / totalAttempts);
        System.out.printf("Error recovery time avg: %.2f ms\n", 
            (_errorRecoveryTimeNanos.sum() / 1_000_000.0) / errorCount);
    }
    
    private void testLockContention() {
        System.out.println("\n--- Test 5: Lock Contention ---");
        int contentionCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<?>> futures = new ArrayList<>();
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < contentionCount; i++) {
            final int taskId = i;
            futures.add(executor.submit(() -> {
                measureLockContention(() -> {
                    // Simulate work under lock
                    try {
                        Thread.sleep((long) (Math.random() * 20));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }));
        }
        
        for (Future<?> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("Contention test error: " + e.getMessage());
            }
        }
        
        long duration = System.nanoTime() - startTime;
        double avgLockWait = (_lockWaitTimeNanos.sum() / 1_000_000.0) / contentionCount;
        
        System.out.printf("Average lock wait time: %.2f ms\n", avgLockWait);
        System.out.printf("Lock contention events: %d\n", _lockContentionCount.get());
    }
    
    // Data classes
    public record PerformanceReport(
        Instant timestamp,
        int totalCasesCreated,
        int totalWorkItemsProcessed,
        int activeConcurrentCases,
        double caseCreationP95ms,
        double workItemP95ms,
        double taskCompletionP95ms,
        double avgQueryTimeMs,
        double avgLockWaitTimeMs,
        int errorCount,
        ThresholdStatus thresholdStatus
    ) {}
    
    public record ThresholdStatus(
        boolean withinTargets,
        List<String> violations
    ) {}
    
    public record YSpecificationID(String id, String version) {}
    
    // Helper classes
    static class PercentileSampler {
        private final List<Double> samples = new CopyOnWriteArrayList<>();
        private final int maxSamples = 1000;
        
        public synchronized void addSample(double value) {
            samples.add(value);
            if (samples.size() > maxSamples) {
                samples.remove(0);
            }
        }
        
        public double getPercentile(double percentile) {
            if (samples.isEmpty()) return 0;
            
            List<Double> sorted = new ArrayList<>(samples);
            Collections.sort(sorted);
            
            int index = (int) (percentile / 100.0 * (sorted.size() - 1));
            return sorted.get(index);
        }
    }
    
    static class WorkItem {
        private String caseID;
        private String taskID;
        
        public WorkItem(String caseID, String taskID) {
            this.caseID = caseID;
            this.taskID = taskID;
        }
    }
}
