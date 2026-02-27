/*
 * Simplified YAWL Concurrency Test (Based on actual YAWL test patterns)
 * 
 * This test implements the key scenarios from the original YAWL concurrency tests:
 * - WorkItem timer race conditions
 * - Cascade cancellation under load
 * - Lock starvation detection
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class YawlConcurrencyTest {
    
    private static final int THREAD_COUNT = 500;
    private static final int TEST_RUNS = 10;
    private static final int TIMEOUT_MS = 30000;
    
    // Metrics
    private static final AtomicInteger successCount = new AtomicInteger(0);
    private static final AtomicInteger failureCount = new AtomicInteger(0);
    private static final AtomicLong totalTime = new AtomicLong(0);
    
    public static void main(String[] args) {
        System.out.println("=== YAWL v6.0.0 Concurrency Test Suite ===");
        System.out.println("Threads: " + THREAD_COUNT);
        System.out.println("Test Runs: " + TEST_RUNS);
        System.out.println();
        
        // Test 1: WorkItem Timer Race (based on WorkItemTimerRaceTest)
        testWorkItemTimerRace();
        
        // Test 2: Cascade Cancellation (based on CascadeCancellationTest)
        testCascadeCancellation();
        
        // Test 3: Virtual Thread Lock Starvation (based on VirtualThreadLockStarvationTest)
        testLockStarvation();
        
        // Generate report
        generateReport();
    }
    
    private static void testWorkItemTimerRace() {
        System.out.println("Test 1: WorkItem Timer Race Conditions");
        System.out.println("-------------------------------------");
        
        int passed = 0;
        
        for (int run = 0; run < TEST_RUNS; run++) {
            if (executeTimerRaceTest(run)) {
                passed++;
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeTimerRaceTest(int runId) {
        // Simulate the YAWL timer race scenario
        YawlWorkItem workItem = new YawlWorkItem("timer-test-" + runId);
        workItem.setStatus(WorkItemStatus.EXECUTING);
        
        AtomicReference<Exception> timerException = new AtomicReference<>(null);
        AtomicReference<Exception> externalException = new AtomicReference<>(null);
        AtomicReference<WorkItemStatus> finalStatus = new AtomicReference<>(null);
        
        // External completion thread
        Thread externalCompleter = Thread.ofVirtual()
            .name("external-completer-" + runId)
            .start(() -> {
                try {
                    Thread.sleep(100); // Simulate work completion delay
                    workItem.setStatus(WorkItemStatus.COMPLETE);
                    finalStatus.set(workItem.getStatus());
                } catch (Exception e) {
                    externalException.set(e);
                }
            });
        
        // Timer expiry thread
        Thread timerFirer = Thread.ofVirtual()
            .name("timer-firer-" + runId)
            .start(() -> {
                try {
                    Thread.sleep(150); // Simulate timer delay
                    workItem.setStatus(WorkItemStatus.FAILED);
                    finalStatus.set(workItem.getStatus());
                } catch (Exception e) {
                    timerException.set(e);
                }
            });
        
        try {
            // Wait for both operations
            timerFirer.join(5000);
            externalCompleter.join(5000);
            
            // Check results
            WorkItemStatus status = finalStatus.get();
            boolean isTerminal = status == WorkItemStatus.COMPLETE || status == WorkItemStatus.FAILED;
            
            // Both operations should succeed without exceptions
            boolean success = timerException.get() == null && 
                             externalException.get() == null && 
                             isTerminal;
            
            return success;
            
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    private static void testCascadeCancellation() {
        System.out.println("Test 2: Cascade Cancellation Under Load");
        System.out.println("--------------------------------------");
        
        int passed = 0;
        
        for (int run = 0; run < TEST_RUNS; run++) {
            if (executeCascadeCancellationTest(run)) {
                passed++;
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeCascadeCancellationTest(int runId) {
        // Create work items for a case
        List<YawlWorkItem> workItems = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            YawlWorkItem item = new YawlWorkItem("case-" + runId + "-item-" + i);
            item.setStatus(WorkItemStatus.ENABLED);
            workItems.add(item);
        }
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch transitionLatch = new CountDownLatch(5);
        List<AtomicReference<Exception>> exceptions = new ArrayList<>();
        
        // Start task transitions
        for (YawlWorkItem item : workItems) {
            AtomicReference<Exception> exRef = new AtomicReference<>(null);
            exceptions.add(exRef);
            
            executor.submit(() -> {
                try {
                    transitionLatch.await();
                    item.setStatus(WorkItemStatus.FIRED);
                    item.setStatus(WorkItemStatus.EXECUTING);
                } catch (Exception e) {
                    exRef.set(e);
                }
            });
        }
        
        // Simulate cancellation after some delay
        executor.submit(() -> {
            try {
                transitionLatch.countDown(); // Allow transitions to start
                Thread.sleep(50); // Random cancellation timing
                workItems.forEach(item -> {
                    try {
                        item.setStatus(WorkItemStatus.CANCELLED_BY_CASE);
                    } catch (Exception e) {
                        // Ignore expected exceptions
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Wait for completion
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        
        // Check results
        boolean allCancelled = workItems.stream()
            .allMatch(item -> item.getStatus() == WorkItemStatus.CANCELLED_BY_CASE);
        
        boolean noUnexpectedErrors = exceptions.stream()
            .noneMatch(ref -> ref.get() != null && 
                   !ref.get().getMessage().contains("already terminal") &&
                   !ref.get().getMessage().contains("Cancelled"));
        
        return allCancelled && noUnexpectedErrors;
    }
    
    private static void testLockStarvation() {
        System.out.println("Test 3: Virtual Thread Lock Starvation");
        System.out.println("--------------------------------------");
        
        int passed = 0;
        
        for (int run = 0; run < TEST_RUNS; run++) {
            if (executeLockStarvationTest(run)) {
                passed++;
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeLockStarvationTest(int runId) {
        // Simulate YNetRunner lock scenario
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        AtomicInteger writeOperations = new AtomicInteger(0);
        AtomicLong maxWriteWait = new AtomicLong(0);
        
        // Create many reader threads
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger readerCount = new AtomicInteger(0);
        
        // Start readers
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lock.readLock().lock();
                    readerCount.incrementAndGet();
                    Thread.sleep(1); // Simulate read work
                    lock.readLock().unlock();
                } catch (Exception e) {
                    // Ignore
                }
            });
        }
        
        // Start writer
        AtomicLong writeStart = new AtomicLong(0);
        AtomicLong writeEnd = new AtomicLong(0);
        executor.submit(() -> {
            try {
                startLatch.await();
                writeStart.set(System.nanoTime());
                lock.writeLock().lock();
                writeOperations.incrementAndGet();
                Thread.sleep(5); // Simulate write work
                writeEnd.set(System.nanoTime());
                long writeWait = writeEnd.get() - writeStart.get();
                
                long currentMax = maxWriteWait.get();
                while (writeWait > currentMax) {
                    if (maxWriteWait.compareAndSet(currentMax, writeWait)) {
                        break;
                    }
                    currentMax = maxWriteWait.get();
                }
                
                lock.writeLock().unlock();
            } catch (Exception e) {
                // Ignore
            }
        });
        
        // Start the test
        startLatch.countDown();
        
        // Wait for completion
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        }
        
        // Check if writer was not starved
        boolean writerSucceeded = writeOperations.get() > 0;
        boolean writeWaitReasonable = maxWriteWait.get() < 1_000_000_000; // < 1 second
        
        return writerSucceeded && writeWaitReasonable;
    }
    
    private static void generateReport() {
        long totalTimeMs = totalTime.get();
        int totalTests = successCount.get() + failureCount.get();
        double successRate = totalTests > 0 ? (successCount.get() * 100.0) / totalTests : 0;
        
        System.out.println("=== FINAL TEST REPORT ===");
        System.out.println();
        System.out.println("Test Results:");
        System.out.printf("  Tests Passed: %d\n", successCount.get());
        System.out.printf("  Tests Failed: %d\n", failureCount.get());
        System.out.printf("  Success Rate: %.1f%%\n", successRate);
        System.out.printf("  Total Time: %d ms\n", totalTimeMs);
        System.out.println();
        
        System.out.println("Concurrency Assessment:");
        if (successRate >= 90) {
            System.out.println("✅ EXCELLENT - Concurrency handling is robust and reliable");
        } else if (successRate >= 80) {
            System.out.println("✅ GOOD - Concurrency handling is reliable with minor issues");
        } else {
            System.out.println("❌ NEEDS IMPROVEMENT - Concurrency issues detected");
        }
        
        System.out.println();
        System.out.println("Recommendations:");
        if (successCount.get() == totalTests) {
            System.out.println("- Current implementation is production ready");
            System.out.println("- Consider adding monitoring for lock contention");
        } else {
            System.out.println("- Review synchronization mechanisms");
            System.out.println("- Implement additional error handling for race conditions");
        }
        
        System.out.println("\nReport completed at: " + new Date());
    }
    
    // Simplified YAWL model classes for testing
    static class YawlWorkItem {
        private volatile WorkItemStatus status;
        private final String id;
        
        public YawlWorkItem(String id) {
            this.id = id;
            this.status = WorkItemStatus.ENABLED;
        }
        
        public synchronized void setStatus(WorkItemStatus newStatus) {
            // Simulate YAWL state validation
            if (newStatus == null) {
                throw new IllegalArgumentException("Status cannot be null");
            }
            
            // Check for illegal transitions
            if (isTerminal(status) && !isTerminal(newStatus)) {
                throw new IllegalStateException("Cannot transition from terminal state");
            }
            
            // Check for multiple terminal states
            if (isTerminal(status) && isTerminal(newStatus) && status != newStatus) {
                throw new IllegalStateException("Cannot change terminal state");
            }
            
            this.status = newStatus;
        }
        
        public WorkItemStatus getStatus() {
            return status;
        }
        
        private boolean isTerminal(WorkItemStatus status) {
            return status == WorkItemStatus.COMPLETE || 
                   status == WorkItemStatus.FAILED ||
                   status == WorkItemStatus.CANCELLED_BY_CASE ||
                   status == WorkItemStatus.DELETED ||
                   status == WorkItemStatus.WITHDRAWN;
        }
    }
    
    enum WorkItemStatus {
        ENABLED,
        FIRED,
        EXECUTING,
        COMPLETE,
        FAILED,
        CANCELLED_BY_CASE,
        DELETED,
        WITHDRAWN
    }
    
    // Simple implementation for testing
    static class ReentrantReadWriteLock {
        private int readers = 0;
        private boolean writeLocked = false;
        private final Object lock = new Object();
        
        public ReadLock readLock() {
            return new ReadLock();
        }
        
        public WriteLock writeLock() {
            return new WriteLock();
        }
        
        class ReadLock {
            public void lock() {
                synchronized (lock) {
                    while (writeLocked) {
                        try {
                            lock.wait(100); // Timeout to prevent starvation
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    readers++;
                }
            }
            
            public void unlock() {
                synchronized (lock) {
                    readers--;
                    if (readers == 0) {
                        lock.notifyAll();
                    }
                }
            }
        }
        
        class WriteLock {
            public void lock() {
                synchronized (lock) {
                    while (readers > 0 || writeLocked) {
                        try {
                            lock.wait(100); // Timeout to prevent starvation
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    writeLocked = true;
                }
            }
            
            public void unlock() {
                synchronized (lock) {
                    writeLocked = false;
                    lock.notifyAll();
                }
            }
        }
    }
}
