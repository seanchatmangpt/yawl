# Concurrency Test Framework Implementation Guide
## Critical Section & Semaphore Pattern Validation (WCP-39 to WCP-43)

**Version:** 1.0
**Date:** February 20, 2026
**Target:** 85%+ branch coverage for critical section patterns

---

## Overview

This guide provides detailed specifications for implementing the concurrency test framework required for Phase 2 validation of workflow patterns WCP-39 through WCP-43, with particular focus on semaphore-based mutual exclusion (WCP-42, WCP-43).

---

## Part 1: Test Infrastructure Components

### 1.1 Concurrency Test Base Class

**Purpose:** Provide common utilities for all concurrency tests

```java
package org.yawlfoundation.yawl.patterns.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;

/**
 * Base class for concurrency tests providing thread coordination,
 * event collection, and synchronization utilities.
 */
public abstract class ConcurrencyTestBase {
    
    protected ExecutorService executor;
    protected volatile List<ThreadEvent> events;
    protected AtomicInteger errorCount;
    protected AtomicReference<Throwable> firstError;
    
    protected static final long TIMEOUT_SEC = 30;
    protected static final int MAX_THREADS = 100;
    
    protected void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        events = Collections.synchronizedList(new ArrayList<>());
        errorCount = new AtomicInteger(0);
        firstError = new AtomicReference<>(null);
    }
    
    protected void tearDown() throws Exception {
        executor.shutdown();
        if (!executor.awaitTermination(TIMEOUT_SEC, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            throw new TimeoutException("Executor did not terminate within " + TIMEOUT_SEC + "s");
        }
    }
    
    /**
     * Thread synchronization barrier - all threads must reach before any proceed.
     */
    protected CyclicBarrier createBarrier(int parties) {
        return new CyclicBarrier(parties);
    }
    
    /**
     * One-time event latch - waits for count to reach 0.
     */
    protected CountDownLatch createLatch(int count) {
        return new CountDownLatch(count);
    }
    
    /**
     * Record thread event for later analysis.
     */
    protected void recordEvent(String threadId, String event, long timestampNs) {
        events.add(new ThreadEvent(threadId, event, timestampNs));
    }
    
    /**
     * Record error occurred in thread.
     */
    protected void recordError(Throwable e) {
        errorCount.incrementAndGet();
        firstError.compareAndSet(null, e);
    }
    
    /**
     * Assert no errors occurred across all threads.
     */
    protected void assertNoErrors() throws Throwable {
        if (errorCount.get() > 0) {
            throw new AssertionError("Errors occurred in " + errorCount.get() + " threads", 
                                   firstError.get());
        }
    }
    
    /**
     * Assert all threads completed within timeout.
     */
    protected void assertAllCompleted(List<Thread> threads) throws InterruptedException {
        for (Thread t : threads) {
            if (!t.join(TIMEOUT_SEC * 1000)) {
                throw new TimeoutException("Thread did not complete: " + t.getName());
            }
        }
    }
    
    record ThreadEvent(String threadId, String event, long timestampNs) {}
}
```

### 1.2 Deadlock Detector

**Purpose:** Detect deadlock conditions during test execution

```java
package org.yawlfoundation.yawl.patterns.concurrency.detector;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

/**
 * Detects deadlock conditions by monitoring thread states.
 */
public class DeadlockDetector {
    
    private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
    private final long checkIntervalMs = 100;
    private volatile boolean running = false;
    
    /**
     * Start monitoring for deadlocks. Check every 100ms.
     */
    public void startMonitoring() {
        running = true;
        new Thread(() -> {
            while (running) {
                try {
                    long[] deadlockedThreadIds = threadMxBean.findDeadlockedThreads();
                    if (deadlockedThreadIds != null && deadlockedThreadIds.length > 0) {
                        throw new DeadlockDetectedException(
                            "Deadlock detected with " + deadlockedThreadIds.length + " threads"
                        );
                    }
                    Thread.sleep(checkIntervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "DeadlockDetector").start();
    }
    
    /**
     * Stop monitoring.
     */
    public void stopMonitoring() {
        running = false;
    }
    
    public static class DeadlockDetectedException extends RuntimeException {
        public DeadlockDetectedException(String message) {
            super(message);
        }
    }
}
```

### 1.3 Starvation Detector

**Purpose:** Detect threads that never acquire critical resource

```java
package org.yawlfoundation.yawl.patterns.concurrency.detector;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Detects starvation where threads fail to acquire shared resource
 * within reasonable time or iteration count.
 */
public class StarvationDetector {
    
    private final Map<String, AtomicInteger> acquisitionCounts = new ConcurrentHashMap<>();
    private final long timeoutMs = 60000;  // 1 minute
    private final int minAcquisitions = 1;
    
    /**
     * Register that thread attempted to acquire resource.
     */
    public void recordAttempt(String threadId) {
        acquisitionCounts.computeIfAbsent(threadId, k -> new AtomicInteger(0))
            .incrementAndGet();
    }
    
    /**
     * Check for starvation: threads that never acquired resource.
     */
    public void verifyFairness(long elapsedMs) throws StarvationException {
        for (Map.Entry<String, AtomicInteger> entry : acquisitionCounts.entrySet()) {
            String threadId = entry.getKey();
            int acquisitions = entry.getValue().get();
            
            if (acquisitions < minAcquisitions) {
                throw new StarvationException(
                    "Thread " + threadId + " never acquired resource after " + 
                    elapsedMs + "ms and " + acquisitions + " attempts"
                );
            }
        }
    }
    
    public static class StarvationException extends RuntimeException {
        public StarvationException(String message) {
            super(message);
        }
    }
}
```

### 1.4 Race Condition Detector

**Purpose:** Detect race conditions during concurrent execution

```java
package org.yawlfoundation.yawl.patterns.concurrency.detector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects race conditions by tracking concurrent access patterns.
 */
public class RaceConditionDetector {
    
    private final Map<String, AccessRecord> lastAccess = new ConcurrentHashMap<>();
    
    /**
     * Record access to shared resource.
     * @throws RaceConditionException if concurrent access detected
     */
    public void recordAccess(String resourceId, String threadId, long timestampNs) 
            throws RaceConditionException {
        
        AccessRecord newRecord = new AccessRecord(threadId, timestampNs);
        AccessRecord prevRecord = lastAccess.put(resourceId, newRecord);
        
        if (prevRecord != null) {
            long timeSinceLast = timestampNs - prevRecord.timestampNs;
            
            // Detect overlapping access (within 1ms = likely concurrent)
            if (timeSinceLast < 1_000_000) {  // 1ms in nanoseconds
                throw new RaceConditionException(
                    "Concurrent access detected on " + resourceId + ": " +
                    prevRecord.threadId + " → " + threadId + 
                    " with gap " + (timeSinceLast / 1_000_000) + "ms"
                );
            }
        }
    }
    
    record AccessRecord(String threadId, long timestampNs) {}
    
    public static class RaceConditionException extends RuntimeException {
        public RaceConditionException(String message) {
            super(message);
        }
    }
}
```

### 1.5 Lock State Verifier

**Purpose:** Verify lock invariants during execution

```java
package org.yawlfoundation.yawl.patterns.concurrency.verifier;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Verifies semaphore/lock invariants during concurrent execution.
 */
public class LockStateVerifier {
    
    private final AtomicInteger semaphoreValue;
    private final int initialValue;
    private final ReentrantLock stateLock = new ReentrantLock();
    
    public LockStateVerifier(int initialValue) {
        this.initialValue = initialValue;
        this.semaphoreValue = new AtomicInteger(initialValue);
    }
    
    /**
     * Verify acquire operation: value decrements from positive to 0.
     */
    public void verifyAcquire(String threadId) throws InvariantViolation {
        stateLock.lock();
        try {
            int before = semaphoreValue.get();
            
            if (before <= 0) {
                throw new InvariantViolation(
                    "Cannot acquire: semaphore already 0 or negative (" + before + ")"
                );
            }
            
            int after = semaphoreValue.decrementAndGet();
            if (after < 0) {
                throw new InvariantViolation(
                    "Acquire caused negative semaphore: " + after
                );
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Verify release operation: value increments.
     */
    public void verifyRelease(String threadId) throws InvariantViolation {
        stateLock.lock();
        try {
            int before = semaphoreValue.get();
            
            if (before >= initialValue) {
                throw new InvariantViolation(
                    "Cannot release: semaphore already at max (" + before + ")"
                );
            }
            
            int after = semaphoreValue.incrementAndGet();
            if (after > initialValue) {
                throw new InvariantViolation(
                    "Release exceeded maximum: " + after + " > " + initialValue
                );
            }
        } finally {
            stateLock.unlock();
        }
    }
    
    /**
     * Final invariant check: semaphore returned to initial value.
     */
    public void verifyFinalState() throws InvariantViolation {
        if (semaphoreValue.get() != initialValue) {
            throw new InvariantViolation(
                "Final semaphore state incorrect: " + semaphoreValue.get() + 
                " != " + initialValue + " (leaked locks?)"
            );
        }
    }
    
    public static class InvariantViolation extends RuntimeException {
        public InvariantViolation(String message) {
            super(message);
        }
    }
}
```

---

## Part 2: Test Class Specifications

### 2.1 WCP42CriticalSectionConcurrencyTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP42CriticalSectionConcurrencyTest.java`

**Test Methods (8 total):**

1. **testDualThreadMutualExclusion** - Verify only one thread in critical section at time
2. **testLockAcquisitionOrdering** - Verify FIFO ordering of lock acquisition
3. **testFairSchedulingUnderContention** - Verify all threads get equal lock time
4. **testStarvationDetection100Threads** - Verify no starvation with 100 threads
5. **testConcurrentAccessPattern** - Verify interleaving of acquire/critical/release
6. **testLongHoldTimes** - Verify behavior with prolonged critical section
7. **testRapidAcquisitionReleases** - Verify rapid lock cycling works
8. **testMixedWorkload** - Verify heterogeneous thread behavior

### 2.2 WCP42SemaphoreInvariantsTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP42SemaphoreInvariantsTest.java`

**Test Methods (6 total):**

1. **testSemaphoreValueNeverNegative** - Verify semaphore ≥ 0 always
2. **testAcquireReleaseBalance** - Verify #acquires == #releases at end
3. **testNoMultipleReleases** - Detect if same thread releases twice
4. **testBoundsEnforcement** - Verify semaphore ≤ initial value
5. **testAtomicOperations** - Verify acquire/release are atomic
6. **testMemoryVisibility** - Verify changes visible across threads

### 2.3 WCP42ExceptionHandlingTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP42ExceptionHandlingTest.java`

**Test Methods (5 total):**

1. **testLockReleasedAfterException** - Lock released even if exception thrown
2. **testSecondThreadCanAcquireAfterException** - Other threads unblocked after exception
3. **testCascadingExceptions** - Multiple exceptions handled correctly
4. **testExceptionInWaitQueue** - Exception while thread waiting for lock
5. **testRecoveryAfterFailure** - Normal operation resumes after exception

### 2.4 WCP42DeadlockDetectionTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP42DeadlockDetectionTest.java`

**Test Methods (6 total):**

1. **testCoffmanConditionsNotSatisfied** - Verify no circular wait
2. **testNoDeadlockWith1000Threads** - 1000 threads, no deadlock
3. **testProlongedContentionDetection** - Monitor for deadlock indicators
4. **testTimeoutBasedDetection** - Timeout detects potential deadlock
5. **testDeadlockRecoveryProtocol** - System recovers from near-deadlock
6. **testLivenessProperyVerification** - System always makes progress

### 2.5 WCP43CancellationSafetyTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP43CancellationSafetyTest.java`

**Test Methods (7 total):**

1. **testCancellationDuringLockAcquisition** - Cancel while waiting for lock
2. **testCancellationDuringCriticalSection** - Cancel while in critical section
3. **testCancellationDuringLockRelease** - Cancel during release phase
4. **testLockReleasedOnCancellation** - Lock always released when cancelled
5. **testSecondThreadCanProceededAfterCancel** - Other threads unblocked after cancel
6. **testMultipleCancelSignals** - Multiple cancellations handled correctly
7. **testCancelPriority** - Cancel takes priority over normal flow

### 2.6 WCP43PartialExecutionTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP43PartialExecutionTest.java`

**Test Methods (5 total):**

1. **testTaskACompleteTaskBCancelled** - Partial execution with Task B cancelled
2. **testTaskACancelledTaskBStarts** - Task A cancelled, Task B should not start
3. **testBothTasksCancelledSimultaneously** - Simultaneous cancellation
4. **testStateConsistencyAfterPartialExecution** - No data corruption
5. **testRollbackSemantics** - State reverted to pre-critical-section

### 2.7 WCP43RaceConditionTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP43RaceConditionTest.java`

**Test Methods (6 total):**

1. **testCheckAndAcquireRace** - Race between check and acquire
2. **testCancelSignalVsTaskExecutionRace** - Cancel vs task execution ordering
3. **testReleaseVsCancelRace** - Release vs cancel operation ordering
4. **testSignalOrderingEnforcement** - Events ordered deterministically
5. **testTimingWindowDetection** - Timing windows where races could occur
6. **testAtomicCheckAndAcquire** - Verify atomic semantics

### 2.8 WCP41BlockedSplitSynchronizationTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP41BlockedSplitSynchronizationTest.java`

**Test Methods (5 total):**

1. **testAllBranchesReadyVerification** - All branches must signal ready
2. **testBlockedSplitMutualExclusion** - Split doesn't proceed until ready
3. **testMultiPointSynchronization** - Multiple sync points work correctly
4. **testLoopTerminationGuarantees** - Loop eventually terminates
5. **testReadinessVariableTracking** - allReady variable state correctness

### 2.9 WCP39WCP40TriggerEventTest

**File:** `/home/user/yawl/yawl-mcp-a2a-app/src/test/java/org/yawlfoundation/yawl/mcp/a2a/patterns/WCP39WCP40TriggerEventTest.java`

**Test Methods (4 total):**

1. **testResetTriggerEventDelivery** - Reset signal delivered to trigger
2. **testCancelRegionCancellationSemantics** - Cancel region cancels all tasks
3. **testEventOrderingDuringResets** - Events ordered correctly during reset
4. **testDuplicateEventHandling** - Multiple events handled correctly

### 2.10-2.18 Performance & Stress Tests (9 additional test classes)

1. **WCP42SemaphoreContentionBenchmark** - Measure lock contention overhead
2. **WCP42CriticalSectionThroughputTest** - Throughput under load
3. **VirtualThreadScalabilityTest** - 10K+ virtual threads
4. **MemoryPressureStressTest** - GC impact on lock behavior
5. **LongRunningStabilityTest** - 48+ hour stability test
6. **CacheCoherencyTest** - Memory visibility verification
7. **ContextSwitchBenchmark** - Thread scheduling impact
8. **CPUAffinityTest** - Virtual thread scheduling patterns
9. **ResourceCleanupTest** - No memory/lock leaks

---

## Part 3: Test Execution & Validation

### 3.1 Test Suite Execution

```bash
# Run all concurrency tests
mvn test -Dtest=WCP4*

# Run with detailed output
mvn test -Dtest=WCP42* -X

# Run with deadlock detection enabled
mvn test -Dtest=WCP42DeadlockDetectionTest -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints

# Run stress test (1+ hour)
mvn test -Dtest=LongRunningStabilityTest -Ptiming-intensive

# Run with code coverage
mvn clean test -Dtest=WCP4* -P jacoco verify
```

### 3.2 Coverage Targets

**Branch Coverage by Test Class:**

- WCP42CriticalSectionConcurrencyTest: 75%
- WCP42SemaphoreInvariantsTest: 85%
- WCP42ExceptionHandlingTest: 90%
- WCP42DeadlockDetectionTest: 70%
- WCP43CancellationSafetyTest: 85%
- WCP43PartialExecutionTest: 80%
- WCP43RaceConditionTest: 75%
- WCP41BlockedSplitSynchronizationTest: 80%
- WCP39WCP40TriggerEventTest: 70%
- **Combined: 85%+ branch coverage**

---

## Part 4: Known Issues & Edge Cases

### 4.1 Java Memory Model Considerations

- All semaphore operations must use volatile variables
- Happens-before relationships must be documented
- Acquire must come before release (ordering)

### 4.2 Virtual Thread Considerations

- Virtual threads don't pin on ReentrantLock (good)
- Virtual threads pin on synchronized (avoid)
- Use structured concurrency (StructuredTaskScope)
- Scoped values replace ThreadLocal

### 4.3 Test Flakiness Mitigation

- Use high timeout values (30 seconds)
- Repeat flaky tests 3 times
- Collect full thread dumps on timeout
- Log all synchronization events

---

## Conclusion

This framework provides comprehensive infrastructure for Phase 2 validation of critical section patterns, targeting 85%+ branch coverage and zero deadlock/starvation guarantees for production deployment.

