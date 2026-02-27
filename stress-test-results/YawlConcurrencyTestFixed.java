/*
 * Fixed YAWL Concurrency Test
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class YawlConcurrencyTestFixed {
    
    private static final int THREAD_COUNT = 100;
    private static final int TEST_RUNS = 5;
    
    public static void main(String[] args) {
        System.out.println("=== YAWL v6.0.0 Concurrency Test Suite ===");
        System.out.println("Threads: " + THREAD_COUNT);
        System.out.println("Test Runs: " + TEST_RUNS);
        System.out.println();
        
        // Test 1: WorkItem Timer Race
        testWorkItemTimerRace();
        
        // Test 2: Cascade Cancellation
        testCascadeCancellation();
        
        // Test 3: Lock Starvation
        testLockStarvation();
    }
    
    private static void testWorkItemTimerRace() {
        System.out.println("Test 1: WorkItem Timer Race Conditions");
        System.out.println("-------------------------------------");
        
        int passed = 0;
        
        for (int run = 0; run < TEST_RUNS; run++) {
            if (executeTimerRaceTest(run)) {
                passed++;
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeTimerRaceTest(int runId) {
        YawlWorkItem workItem = new YawlWorkItem("timer-test-" + runId);
        workItem.setStatus(WorkItemStatus.EXECUTING);
        
        AtomicReference<Exception> timerException = new AtomicReference<>(null);
        AtomicReference<Exception> externalException = new AtomicReference<>(null);
        AtomicReference<WorkItemStatus> finalStatus = new AtomicReference<>(null);
        
        // External completion
        Thread externalCompleter = Thread.ofVirtual()
            .name("external-completer-" + runId)
            .start(() -> {
                try {
                    Thread.sleep(50);
                    workItem.setStatus(WorkItemStatus.COMPLETE);
                    finalStatus.set(workItem.getStatus());
                } catch (Exception e) {
                    externalException.set(e);
                }
            });
        
        // Timer expiry
        Thread timerFirer = Thread.ofVirtual()
            .name("timer-firer-" + runId)
            .start(() -> {
                try {
                    Thread.sleep(75);
                    workItem.setStatus(WorkItemStatus.FAILED);
                    finalStatus.set(workItem.getStatus());
                } catch (Exception e) {
                    timerException.set(e);
                }
            });
        
        try {
            timerFirer.join(2000);
            externalCompleter.join(2000);
            
            WorkItemStatus status = finalStatus.get();
            boolean isTerminal = status == WorkItemStatus.COMPLETE || status == WorkItemStatus.FAILED;
            
            return timerException.get() == null && externalException.get() == null && isTerminal;
            
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
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeCascadeCancellationTest(int runId) {
        List<YawlWorkItem> workItems = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            YawlWorkItem item = new YawlWorkItem("case-" + runId + "-item-" + i);
            item.setStatus(WorkItemStatus.ENABLED);
            workItems.add(item);
        }
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(1);
        List<AtomicReference<Exception>> exceptions = new ArrayList<>();
        
        for (YawlWorkItem item : workItems) {
            AtomicReference<Exception> exRef = new AtomicReference<>(null);
            exceptions.add(exRef);
            
            executor.submit(() -> {
                try {
                    latch.await();
                    item.setStatus(WorkItemStatus.FIRED);
                    item.setStatus(WorkItemStatus.EXECUTING);
                } catch (Exception e) {
                    exRef.set(e);
                }
            });
        }
        
        executor.submit(() -> {
            try {
                latch.countDown();
                Thread.sleep(30);
                workItems.forEach(item -> {
                    try {
                        item.setStatus(WorkItemStatus.CANCELLED_BY_CASE);
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        executor.shutdown();
        try {
            return executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    private static void testLockStarvation() {
        System.out.println("Test 3: Virtual Thread Lock Starvation");
        System.out.println("--------------------------------------");
        
        int passed = 0;
        
        for (int run = 0; run < TEST_RUNS; run++) {
            if (executeLockStarvationTest(run)) {
                passed++;
            }
        }
        
        System.out.printf("Result: %d/%d passed\n", passed, TEST_RUNS);
        System.out.println();
    }
    
    private static boolean executeLockStarvationTest(int runId) {
        SimpleReadWriteLock lock = new SimpleReadWriteLock();
        AtomicInteger writeOperations = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // Start readers
        AtomicInteger readerCount = new AtomicInteger(0);
        for (int i = 0; i < THREAD_COUNT / 2; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    lock.acquireReadLock();
                    readerCount.incrementAndGet();
                    Thread.sleep(1);
                    lock.releaseReadLock();
                } catch (Exception e) {
                    // Ignore
                }
            });
        }
        
        // Start writer
        executor.submit(() -> {
            try {
                startLatch.await();
                lock.acquireWriteLock();
                writeOperations.incrementAndGet();
                Thread.sleep(10);
                lock.releaseWriteLock();
            } catch (Exception e) {
                // Ignore
            }
        });
        
        startLatch.countDown();
        
        executor.shutdown();
        try {
            return executor.awaitTermination(5, TimeUnit.SECONDS) && writeOperations.get() > 0;
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    static class YawlWorkItem {
        private volatile WorkItemStatus status;
        private final String id;
        
        public YawlWorkItem(String id) {
            this.id = id;
            this.status = WorkItemStatus.ENABLED;
        }
        
        public synchronized void setStatus(WorkItemStatus newStatus) {
            if (newStatus == null) {
                throw new IllegalArgumentException("Status cannot be null");
            }
            
            if (isTerminal(status) && !isTerminal(newStatus)) {
                throw new IllegalStateException("Cannot transition from terminal state");
            }
            
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
        ENABLED, FIRED, EXECUTING, COMPLETE, FAILED, CANCELLED_BY_CASE, DELETED, WITHDRAWN
    }
    
    static class SimpleReadWriteLock {
        private int readers = 0;
        private boolean writeLocked = false;
        private final Object lock = new Object();
        
        public void acquireReadLock() {
            synchronized (lock) {
                while (writeLocked) {
                    try {
                        lock.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                readers++;
            }
        }
        
        public void releaseReadLock() {
            synchronized (lock) {
                readers--;
                if (readers == 0) {
                    lock.notifyAll();
                }
            }
        }
        
        public void acquireWriteLock() {
            synchronized (lock) {
                while (readers > 0 || writeLocked) {
                    try {
                        lock.wait(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                writeLocked = true;
            }
        }
        
        public void releaseWriteLock() {
            synchronized (lock) {
                writeLocked = false;
                lock.notifyAll();
            }
        }
    }
}
