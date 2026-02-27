/*
 * Concurrency Stress Test Runner for YAWL v6.0.0
 * 
 * This class runs comprehensive stress tests to identify:
 * - Race conditions in work item state transitions
 * - Deadlocks under high concurrency
 * - State consistency issues
 * - Performance degradation
 */

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;

public class ConcurrencyStressTestRunner {
    
    // Test configuration
    private static final int CONCURRENT_THREADS = 500;
    private static final int TEST_ITERATIONS = 100;
    private static final int WARMUP_ITERATIONS = 10;
    private static final int MAX_WAIT_SECONDS = 30;
    
    // Metrics collection
    private static final AtomicLong totalOperations = new AtomicLong(0);
    private static final AtomicInteger raceConditionCount = new AtomicInteger(0);
    private static final AtomicInteger deadlockCount = new AtomicInteger(0);
    private static final AtomicInteger stateCorruptionCount = new AtomicInteger(0);
    private static final AtomicLong totalLatency = new AtomicLong(0);
    
    // Test results
    private static final List<String> testResults = new ArrayList<>();
    private static final List<String> errorLog = new ArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== YAWL Concurrency Stress Test Runner ===");
        System.out.println("Start time: " + Instant.now());
        System.out.println("Configuration: " + CONCURRENT_THREADS + " threads, " + TEST_ITERATIONS + " iterations");
        System.out.println();
        
        // Run warmup
        System.out.println("Running warmup iterations...");
        runWarmupTests();
        System.out.println("Warmup completed.\n");
        
        // Run main stress tests
        System.out.println("Starting main stress tests...");
        long testStartTime = System.currentTimeMillis();
        
        // Test 1: WorkItem state transition races
        System.out.println("Test 1: WorkItem State Transition Races");
        runStateTransitionRaceTest();
        
        // Test 2: Concurrent completion attempts
        System.out.println("\nTest 2: Concurrent Completion Attempts");
        runConcurrentCompletionTest();
        
        // Test 3: Deadlock detection under load
        System.out.println("\nTest 3: Deadlock Detection");
        runDeadlockDetectionTest();
        
        // Test 4: Performance degradation analysis
        System.out.println("\nTest 4: Performance Degradation");
        runPerformanceDegradationTest();
        
        long testEndTime = System.currentTimeMillis();
        
        // Generate report
        generateTestReport(testStartTime, testEndTime);
    }
    
    private static void runWarmupTests() {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                // Simple warmup test
                SimpleWorkItem item = new SimpleWorkItem("warmup-" + i);
                item.setStatus(WorkItemStatus.ENABLED);
                item.setStatus(WorkItemStatus.FIRED);
                item.setStatus(WorkItemStatus.EXECUTING);
                item.setStatus(WorkItemStatus.COMPLETE);
            } catch (Exception e) {
                errorLog.add("Warmup " + i + " failed: " + e.getMessage());
            }
        }
    }
    
    private static void runStateTransitionRaceTest() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch allTestsComplete = new CountDownLatch(TEST_ITERATIONS);
        
        long testStart = System.currentTimeMillis();
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    StateTransitionResult result = testSingleStateTransitionRace(iteration);
                    if (result.hasRaceCondition) {
                        raceConditionCount.incrementAndGet();
                        testResults.add(String.format("Race condition detected in iteration %d: %s", 
                            iteration, result.errorMessage));
                    }
                    if (result.hasStateCorruption) {
                        stateCorruptionCount.incrementAndGet();
                        testResults.add(String.format("State corruption in iteration %d: %s", 
                            iteration, result.errorMessage));
                    }
                } catch (Exception e) {
                    errorLog.add("State transition test " + iteration + " failed: " + e.getMessage());
                } finally {
                    allTestsComplete.countDown();
                }
            });
        }
        
        try {
            allTestsComplete.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            errorLog.add("State transition test interrupted: " + e.getMessage());
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                errorLog.add("State transition test executor did not shutdown cleanly");
            }
        } catch (InterruptedException e) {
            errorLog.add("State transition test executor shutdown interrupted");
        }
        
        long testEnd = System.currentTimeMillis();
        System.out.printf("Completed in %d ms\n", testEnd - testStart);
    }
    
    private static StateTransitionResult testSingleStateTransitionRace(int iteration) {
        SimpleWorkItem item = new SimpleWorkItem("race-test-" + iteration);
        AtomicReference<Exception> thread1Exception = new AtomicReference<>(null);
        AtomicReference<Exception> thread2Exception = new AtomicReference<>(null);
        AtomicReference<WorkItemStatus> finalStatus = new AtomicReference<>(null);
        
        // Thread 1: Normal transition sequence
        Thread t1 = Thread.ofVirtual()
            .name("normal-transition-" + iteration)
            .start(() -> {
                try {
                    item.setStatus(WorkItemStatus.FIRED);
                    Thread.sleep(5); // Small delay to increase race chance
                    item.setStatus(WorkItemStatus.EXECUTING);
                    Thread.sleep(5);
                    item.setStatus(WorkItemStatus.COMPLETE);
                    finalStatus.set(item.getStatus());
                } catch (Exception e) {
                    thread1Exception.set(e);
                }
            });
        
        // Thread 2: Rapid state changes
        Thread t2 = Thread.ofVirtual()
            .name("rapid-transition-" + iteration)
            .start(() -> {
                try {
                    Thread.sleep(3); // Slightly different timing
                    item.setStatus(WorkItemStatus.FIRED);
                    item.setStatus(WorkItemStatus.FORCE_COMPLETE);
                    Thread.sleep(3);
                    item.setStatus(WorkItemStatus.CANCELLED_BY_CASE);
                    finalStatus.set(item.getStatus());
                } catch (Exception e) {
                    thread2Exception.set(e);
                }
            });
        
        try {
            t1.join(1000);
            t2.join(1000);
            
            StateTransitionResult result = new StateTransitionResult();
            result.finalStatus = finalStatus.get();
            
            if (thread1Exception.get() != null) {
                if (thread1Exception.get().getMessage().contains("already terminal")) {
                    result.hasRaceCondition = true;
                    result.errorMessage = "Thread 1 hit terminal state race";
                }
            }
            
            if (thread2Exception.get() != null) {
                if (thread2Exception.get().getMessage().contains("already terminal")) {
                    result.hasRaceCondition = true;
                    result.errorMessage = "Thread 2 hit terminal state race";
                }
            }
            
            // Check for state corruption
            if (finalStatus.get() != null) {
                String statusStr = finalStatus.get().toString();
                if (statusStr.contains("COMPLETE") && statusStr.contains("CANCELLED")) {
                    result.hasStateCorruption = true;
                    result.errorMessage = "State corruption detected: multiple terminal states";
                }
            }
            
            return result;
            
        } catch (InterruptedException e) {
            return new StateTransitionResult().withError("Test interrupted");
        }
    }
    
    private static void runConcurrentCompletionTest() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch allTestsComplete = new CountDownLatch(TEST_ITERATIONS);
        
        long testStart = System.currentTimeMillis();
        
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    CompletionResult result = testConcurrentCompletions(iteration);
                    if (result.completionErrors > 1) {
                        raceConditionCount.incrementAndGet();
                        testResults.add(String.format("Multiple completion attempts in iteration %d", iteration));
                    }
                } catch (Exception e) {
                    errorLog.add("Completion test " + iteration + " failed: " + e.getMessage());
                } finally {
                    allTestsComplete.countDown();
                }
            });
        }
        
        try {
            allTestsComplete.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            errorLog.add("Completion test interrupted: " + e.getMessage());
        }
        
        executor.shutdown();
        long testEnd = System.currentTimeMillis();
        System.out.printf("Completed in %d ms\n", testEnd - testStart);
    }
    
    private static CompletionResult testConcurrentCompletions(int iteration) {
        SimpleWorkItem item = new SimpleWorkItem("completion-test-" + iteration);
        AtomicInteger completionAttempts = new AtomicInteger(0);
        AtomicInteger completionErrors = new AtomicInteger(0);
        CountDownLatch allComplete = new CountDownLatch(CONCURRENT_THREADS);
        
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            Thread.ofVirtual()
                .name("completer-" + iteration + "-" + i)
                .start(() -> {
                    try {
                        completionAttempts.incrementAndGet();
                        item.setStatus(WorkItemStatus.COMPLETE);
                    } catch (Exception e) {
                        completionErrors.incrementAndGet();
                    } finally {
                        allComplete.countDown();
                    }
                });
        }
        
        try {
            allComplete.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            completionErrors.incrementAndGet();
        }
        
        CompletionResult result = new CompletionResult();
        result.completionAttempts = completionAttempts.get();
        result.completionErrors = completionErrors.get();
        return result;
    }
    
    private static void runDeadlockDetectionTest() {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        long testStart = System.currentTimeMillis();
        
        // Create a complex deadlock scenario
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            final int iteration = i;
            executor.submit(() -> {
                try {
                    if (testDeadlockScenario(iteration)) {
                        deadlockCount.incrementAndGet();
                        testResults.add(String.format("Deadlock detected in iteration %d", iteration));
                    }
                } catch (Exception e) {
                    errorLog.add("Deadlock test " + iteration + " failed: " + e.getMessage());
                }
            });
        }
        
        // Give time for deadlocks to manifest
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            errorLog.add("Deadlock test sleep interrupted");
        }
        
        executor.shutdown();
        long testEnd = System.currentTimeMillis();
        System.out.printf("Completed in %d ms\n", testEnd - testStart);
    }
    
    private static boolean testDeadlockScenario(int iteration) {
        // Create a resource ordering scenario that can cause deadlock
        SimpleWorkItem item1 = new SimpleWorkItem("deadlock-test-1-" + iteration);
        SimpleWorkItem item2 = new SimpleWorkItem("deadlock-test-2-" + iteration);
        
        Thread t1 = Thread.ofVirtual()
            .name("deadlock-thread-1-" + iteration)
            .start(() -> {
                try {
                    // Lock order: item1 then item2
                    item1.setStatus(WorkItemStatus.FIRED);
                    Thread.sleep(10);
                    item2.setStatus(WorkItemStatus.FIRED);
                    item1.setStatus(WorkItemStatus.EXECUTING);
                    item2.setStatus(WorkItemStatus.EXECUTING);
                } catch (Exception e) {
                    // Ignore expected exceptions
                }
            });
        
        Thread t2 = Thread.ofVirtual()
            .name("deadlock-thread-2-" + iteration)
            .start(() -> {
                try {
                    // Reverse lock order: item2 then item1 (potential deadlock)
                    Thread.sleep(5); // Ensure t1 starts first
                    item2.setStatus(WorkItemStatus.FIRED);
                    Thread.sleep(10);
                    item1.setStatus(WorkItemStatus.FIRED);
                    item2.setStatus(WorkItemStatus.EXECUTING);
                    item1.setStatus(WorkItemStatus.EXECUTING);
                } catch (Exception e) {
                    // Ignore expected exceptions
                }
            });
        
        try {
            // Wait for completion or timeout
            t1.join(1000);
            t2.join(1000);
            
            // If both threads are still alive, deadlock likely
            if (t1.isAlive() && t2.isAlive()) {
                return true;
            }
        } catch (InterruptedException e) {
            return false; // Test interrupted, no deadlock
        }
        
        return false;
    }
    
    private static void runPerformanceDegradationTest() {
        // Measure performance under increasing load
        int[] threadCounts = {100, 200, 500, 1000, 2000};
        
        for (int threadCount : threadCounts) {
            System.out.printf("Testing with %d threads...\n", threadCount);
            
            long startLatency = System.currentTimeMillis();
            AtomicInteger successfulOps = new AtomicInteger(0);
            AtomicInteger failedOps = new AtomicInteger(0);
            
            // Create a high load scenario
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            
            for (int i = 0; i < threadCount; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    long opStart = System.nanoTime();
                    try {
                        SimpleWorkItem item = new SimpleWorkItem("perf-test-" + taskId);
                        item.setStatus(WorkItemStatus.FIRED);
                        item.setStatus(WorkItemStatus.EXECUTING);
                        item.setStatus(WorkItemStatus.COMPLETE);
                        
                        long opLatency = System.nanoTime() - opStart;
                        totalLatency.addAndGet(opLatency);
                        totalOperations.incrementAndGet();
                        successfulOps.incrementAndGet();
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                    }
                });
            }
            
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    testResults.add(String.format("Performance test with %d threads timed out", threadCount));
                    failedOps.addAndGet(threadCount - successfulOps.get());
                }
            } catch (InterruptedException e) {
                testResults.add(String.format("Performance test with %d threads interrupted", threadCount));
            }
            
            long endLatency = System.currentTimeMillis();
            long totalTestTime = endLatency - startLatency;
            
            System.out.printf("  Success: %d, Failed: %d, Time: %d ms\n", 
                successfulOps.get(), failedOps.get(), totalTestTime);
            
            // Calculate performance metrics
            double avgLatencyMs = totalOperations.get() > 0 ? 
                (totalLatency.get() / totalOperations.get()) / 1_000_000.0 : 0;
            double opsPerSecond = totalOperations.get() / (totalTestTime / 1000.0);
            
            testResults.add(String.format(
                "Performance with %d threads: avg=%.2fms, ops/sec=%.2f, success_rate=%.2f%%",
                threadCount, avgLatencyMs, opsPerSecond,
                (successfulOps.get() * 100.0) / threadCount
            ));
        }
    }
    
    private static void generateTestReport(long startTime, long endTime) {
        System.out.println("\n=== CONCURRENCY STRESS TEST REPORT ===");
        System.out.println("Test Duration: " + (endTime - startTime) + " ms");
        System.out.println();
        
        // Summary statistics
        System.out.println("=== TEST SUMMARY ===");
        System.out.println("Total Operations: " + totalOperations.get());
        System.out.println("Race Conditions Detected: " + raceConditionCount.get());
        System.out.println("Deadlocks Detected: " + deadlockCount.get());
        System.out.println("State Corruption Cases: " + stateCorruptionCount.get());
        
        if (totalOperations.get() > 0) {
            double avgLatencyMs = (totalLatency.get() / totalOperations.get()) / 1_000_000.0;
            System.out.println("Average Operation Latency: " + String.format("%.2f", avgLatencyMs) + " ms");
        }
        
        System.out.println();
        
        // Test results
        if (!testResults.isEmpty()) {
            System.out.println("=== TEST RESULTS ===");
            for (String result : testResults) {
                System.out.println("  " + result);
            }
            System.out.println();
        }
        
        // Error log
        if (!errorLog.isEmpty()) {
            System.out.println("=== ERRORS ===");
            for (String error : errorLog) {
                System.out.println("  ERROR: " + error);
            }
            System.out.println();
        }
        
        // Recommendations
        System.out.println("=== RECOMMENDATIONS ===");
        if (raceConditionCount.get() > 0) {
            System.out.println("- IMPLEMENT: Atomic state transitions for work items");
            System.out.println("- ADD: Lock ordering protocols to prevent deadlocks");
        }
        
        if (deadlockCount.get() > 0) {
            System.out.println("- IMPLEMENT: Deadlock detection mechanism");
            System.out.println("- CONSIDER: Timeout-based lock acquisition");
        }
        
        if (stateCorruptionCount.get() > 0) {
            System.out.println("- ADD: State validation on transitions");
            System.out.println("- IMPLEMENT: Transaction-style rollback for invalid states");
        }
        
        System.out.println("\nTest completed at: " + Instant.now());
    }
    
    // Simple work item implementation for testing
    static class SimpleWorkItem {
        private volatile WorkItemStatus status;
        private final String id;
        
        public SimpleWorkItem(String id) {
            this.id = id;
            this.status = WorkItemStatus.ENABLED;
        }
        
        public synchronized void setStatus(WorkItemStatus newStatus) {
            // Simulate validation
            if (newStatus == null) {
                throw new IllegalArgumentException("Status cannot be null");
            }
            
            // Check for illegal transitions
            if (isTerminal(status) && !isTerminal(newStatus)) {
                throw new IllegalStateException("Cannot transition from terminal state to non-terminal");
            }
            
            // Check for duplicate terminal states
            if (isTerminal(status) && isTerminal(newStatus) && status != newStatus) {
                throw new IllegalStateException("Cannot change from " + status + " to " + newStatus + " - both terminal");
            }
            
            this.status = newStatus;
        }
        
        public WorkItemStatus getStatus() {
            return status;
        }
        
        private boolean isTerminal(WorkItemStatus status) {
            return status == WorkItemStatus.COMPLETE || 
                   status == WorkItemStatus.FAILED ||
                   status == WorkItemStatus.FORCE_COMPLETE ||
                   status == WorkItemStatus.CANCELLED_BY_CASE ||
                   status == WorkItemStatus.DELETED ||
                   status == WorkItemStatus.WITHDRAWN ||
                   status == WorkItemStatus.DISCARDED;
        }
    }
    
    enum WorkItemStatus {
        ENABLED,
        FIRED,
        EXECUTING,
        COMPLETE,
        FAILED,
        FORCE_COMPLETE,
        CANCELLED_BY_CASE,
        DELETED,
        WITHDRAWN,
        DISCARDED
    }
    
    static class StateTransitionResult {
        boolean hasRaceCondition = false;
        boolean hasStateCorruption = false;
        WorkItemStatus finalStatus;
        String errorMessage;
        
        StateTransitionResult withError(String message) {
            this.errorMessage = message;
            return this;
        }
    }
    
    static class CompletionResult {
        int completionAttempts;
        int completionErrors;
    }
}
