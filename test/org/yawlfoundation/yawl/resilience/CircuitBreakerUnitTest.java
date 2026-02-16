package org.yawlfoundation.yawl.resilience;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker.CircuitBreakerOpenException;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker.State;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for CircuitBreaker resilience pattern.
 * Tests state transitions, failure thresholds, recovery timing, and thread safety.
 *
 * Chicago TDD: Real CircuitBreaker instances, no mocks.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class CircuitBreakerUnitTest extends TestCase {

    private CircuitBreaker breaker;

    public CircuitBreakerUnitTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        breaker = new CircuitBreaker("test-breaker", 3, 100);
    }

    @Override
    protected void tearDown() throws Exception {
        breaker = null;
        super.tearDown();
    }

    public void testCircuitBreakerInitialStateClosed() {
        assertEquals("Initial state should be CLOSED", State.CLOSED, breaker.getState());
        assertEquals("Initial failures should be 0", 0, breaker.getConsecutiveFailures());
    }

    public void testCircuitBreakerConstructorValidation() {
        try {
            new CircuitBreaker(null);
            fail("Should reject null name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should mention name", e.getMessage().contains("name"));
        }

        try {
            new CircuitBreaker("", 5, 1000);
            fail("Should reject empty name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should mention name", e.getMessage().contains("name"));
        }

        try {
            new CircuitBreaker("test", 0, 1000);
            fail("Should reject zero threshold");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should mention threshold", e.getMessage().contains("failureThreshold"));
        }

        try {
            new CircuitBreaker("test", 5, -100);
            fail("Should reject negative duration");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should mention duration", e.getMessage().contains("openDurationMs"));
        }
    }

    public void testSuccessfulOperationKeepsCircuitClosed() throws Exception {
        Callable<String> successOp = () -> "success";

        String result = breaker.execute(successOp);
        assertEquals("Should return operation result", "success", result);
        assertEquals("State should remain CLOSED", State.CLOSED, breaker.getState());
        assertEquals("Failures should be 0", 0, breaker.getConsecutiveFailures());
    }

    public void testCircuitOpensAfterThresholdFailures() {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
                fail("Operation should throw exception");
            } catch (Exception e) {
                assertEquals("Failure count should increment", i + 1, breaker.getConsecutiveFailures());
            }
        }

        assertEquals("Circuit should be OPEN after threshold", State.OPEN, breaker.getState());
    }

    public void testOpenCircuitFailsFast() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
            } catch (Exception e) {
            }
        }

        assertEquals("Circuit should be OPEN", State.OPEN, breaker.getState());

        Callable<String> successOp = () -> "success";

        try {
            breaker.execute(successOp);
            fail("Should throw CircuitBreakerOpenException");
        } catch (CircuitBreakerOpenException e) {
            assertTrue("Exception message should mention circuit is open",
                    e.getMessage().contains("OPEN"));
        }
    }

    public void testCircuitTransitionsToHalfOpenAfterTimeout() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
            } catch (Exception e) {
            }
        }

        assertEquals("Circuit should be OPEN", State.OPEN, breaker.getState());

        Thread.sleep(150);

        State state = breaker.getState();
        assertEquals("Circuit should transition to HALF_OPEN after timeout",
                State.HALF_OPEN, state);
    }

    public void testHalfOpenSuccessClosesCircuit() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
            } catch (Exception e) {
            }
        }

        Thread.sleep(150);
        assertEquals("Circuit should be HALF_OPEN", State.HALF_OPEN, breaker.getState());

        Callable<String> successOp = () -> "success";
        String result = breaker.execute(successOp);

        assertEquals("Should return success", "success", result);
        assertEquals("Circuit should close after successful test", State.CLOSED, breaker.getState());
        assertEquals("Failures should be reset", 0, breaker.getConsecutiveFailures());
    }

    public void testHalfOpenFailureReopensCircuit() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
            } catch (Exception e) {
            }
        }

        Thread.sleep(150);
        assertEquals("Circuit should be HALF_OPEN", State.HALF_OPEN, breaker.getState());

        try {
            breaker.execute(failingOp);
            fail("Should throw exception");
        } catch (RuntimeException e) {
            assertEquals("Circuit should reopen after failed test", State.OPEN, breaker.getState());
        }
    }

    public void testSuccessAfterPartialFailuresResetsCounter() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        try {
            breaker.execute(failingOp);
        } catch (Exception e) {
        }
        try {
            breaker.execute(failingOp);
        } catch (Exception e) {
        }

        assertEquals("Should have 2 failures", 2, breaker.getConsecutiveFailures());
        assertEquals("Circuit should still be CLOSED", State.CLOSED, breaker.getState());

        Callable<String> successOp = () -> "success";
        breaker.execute(successOp);

        assertEquals("Failures should be reset after success", 0, breaker.getConsecutiveFailures());
        assertEquals("Circuit should remain CLOSED", State.CLOSED, breaker.getState());
    }

    public void testManualReset() throws Exception {
        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < 3; i++) {
            try {
                breaker.execute(failingOp);
            } catch (Exception e) {
            }
        }

        assertEquals("Circuit should be OPEN", State.OPEN, breaker.getState());

        breaker.reset();

        assertEquals("Circuit should be CLOSED after reset", State.CLOSED, breaker.getState());
        assertEquals("Failures should be 0 after reset", 0, breaker.getConsecutiveFailures());
    }

    public void testConcurrentOperations() throws Exception {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Callable<String> successOp = () -> "success";

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    breaker.execute(successOp);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("All operations should complete", completed);
        assertEquals("All operations should succeed", threadCount, successCount.get());
        assertEquals("No operations should fail", 0, failureCount.get());
        assertEquals("Circuit should remain CLOSED", State.CLOSED, breaker.getState());

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    public void testConcurrentFailuresThreadSafety() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Callable<String> failingOp = () -> {
            throw new RuntimeException("Operation failed");
        };

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    breaker.execute(failingOp);
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("All operations should complete", completed);
        assertEquals("Circuit should be OPEN", State.OPEN, breaker.getState());
        assertTrue("Failure count should be at least threshold",
                breaker.getConsecutiveFailures() >= 3);

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    public void testNullOperationThrowsException() {
        try {
            breaker.execute(null);
            fail("Should throw IllegalArgumentException for null operation");
        } catch (IllegalArgumentException e) {
            assertTrue("Error message should mention operation",
                    e.getMessage().contains("operation"));
        } catch (Exception e) {
            fail("Should throw IllegalArgumentException, not " + e.getClass().getName());
        }
    }

    public void testGetters() {
        assertEquals("Name should match", "test-breaker", breaker.getName());
        assertEquals("Failure threshold should match", 3, breaker.getFailureThreshold());
        assertEquals("Open duration should match", 100L, breaker.getOpenDurationMs());
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("CircuitBreaker Unit Tests");
        suite.addTestSuite(CircuitBreakerUnitTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
