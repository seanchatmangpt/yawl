package org.yawlfoundation.yawl.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.resilience.config.ResilienceConfig;
import org.yawlfoundation.yawl.resilience.config.YawlResilienceProperties;
import org.yawlfoundation.yawl.resilience.provider.YawlResilienceProvider;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for YAWL Resilience Provider.
 *
 * Validates circuit breaker, retry, rate limiting, and bulkhead patterns
 * using real Resilience4j integrations (Chicago TDD style).
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResilienceProviderTest {

    private YawlResilienceProvider provider;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        YawlResilienceProperties properties = new YawlResilienceProperties();
        meterRegistry = new SimpleMeterRegistry();
        ResilienceConfig config = new ResilienceConfig(properties, meterRegistry);
        provider = YawlResilienceProvider.initialize(properties, meterRegistry);
    }

    @Test
    public void testEngineCallWithCircuitBreaker() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = provider.executeEngineCall(() -> {
            callCount.incrementAndGet();
            return "success";
        });

        assertEquals("success", result);
        assertEquals(1, callCount.get());
    }

    @Test
    public void testEngineCallWithRetry() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);

        String result = provider.executeEngineCall(() -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 3) {
                throw new IOException("Transient failure");
            }
            return "success after retry";
        });

        assertEquals("success after retry", result);
        assertEquals(3, attemptCount.get());
    }

    @Test
    public void testExternalCallWithCircuitBreaker() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = provider.executeExternalCall(() -> {
            callCount.incrementAndGet();
            return "external response";
        });

        assertEquals("external response", result);
        assertEquals(1, callCount.get());
    }

    @Test
    public void testCircuitBreakerOpensOnFailures() {
        CircuitBreaker circuitBreaker = provider.getResilienceConfig()
            .getEngineServiceCircuitBreaker();

        int failureCount = 0;
        for (int i = 0; i < 100; i++) {
            try {
                provider.executeEngineCall(() -> {
                    throw new IOException("Service unavailable");
                });
            } catch (Exception e) {
                failureCount++;
            }
        }

        assertTrue("Circuit breaker should have opened", failureCount > 0);
        CircuitBreaker.State state = circuitBreaker.getState();
        assertTrue("Circuit should be OPEN or HALF_OPEN",
            state == CircuitBreaker.State.OPEN ||
            state == CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    public void testMultiAgentFanoutWithBulkhead() {
        AtomicInteger executionCount = new AtomicInteger(0);

        CompletableFuture<String> result = provider.executeMultiAgentFanout(() -> {
            executionCount.incrementAndGet();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "agent response";
        });

        String response = result.join();
        assertEquals("agent response", response);
        assertEquals(1, executionCount.get());
    }

    @Test
    public void testRateLimiterThrottlesRequests() {
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger throttleCount = new AtomicInteger(0);

        for (int i = 0; i < 150; i++) {
            try {
                provider.executeWithRateLimit(() -> {
                    successCount.incrementAndGet();
                    return "success";
                });
            } catch (Exception e) {
                throttleCount.incrementAndGet();
            }
        }

        assertTrue("Some requests should succeed", successCount.get() > 0);
        assertTrue("Rate limiter should throttle some requests",
            successCount.get() < 150);
    }

    @Test
    public void testBulkheadIsolation() throws Exception {
        AtomicInteger concurrentCalls = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        for (int i = 0; i < 30; i++) {
            new Thread(() -> {
                try {
                    provider.executeWithBulkhead(() -> {
                        int current = concurrentCalls.incrementAndGet();
                        maxConcurrent.updateAndGet(max -> Math.max(max, current));

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        concurrentCalls.decrementAndGet();
                        return "success";
                    });
                } catch (Exception e) {
                    // Bulkhead rejection expected
                }
            }).start();
        }

        Thread.sleep(500);

        assertTrue("Max concurrent calls should be limited by bulkhead",
            maxConcurrent.get() <= 25);
    }

    @Test
    public void testRetryWithExponentialBackoff() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);
        long[] attemptTimes = new long[3];

        try {
            provider.executeWithRetry(() -> {
                int attempt = attemptCount.getAndIncrement();
                attemptTimes[attempt] = System.currentTimeMillis();

                if (attempt < 2) {
                    throw new IOException("Transient error");
                }
                return "success";
            });
        } catch (Exception e) {
            // Expected
        }

        if (attemptCount.get() >= 2) {
            long firstDelay = attemptTimes[1] - attemptTimes[0];
            assertTrue("First retry delay should be at least 200ms",
                firstDelay >= 200);
        }
    }

    @Test
    public void testMcpIntegrationCircuitBreaker() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = provider.executeMcpCall(() -> {
            callCount.incrementAndGet();
            return "mcp response";
        });

        assertEquals("mcp response", result);
        assertEquals(1, callCount.get());
    }

    @Test
    public void testA2aIntegrationCircuitBreaker() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        String result = provider.executeA2aCall(() -> {
            callCount.incrementAndGet();
            return "a2a response";
        });

        assertEquals("a2a response", result);
        assertEquals(1, callCount.get());
    }

    @Test
    public void testCustomCircuitBreaker() throws Exception {
        String result = provider.executeWithCustomCircuitBreaker(
            "engineService",
            () -> "custom result"
        );

        assertEquals("custom result", result);
    }

    @Test
    public void testCircuitBreakerRecovery() throws Exception {
        CircuitBreaker circuitBreaker = provider.getResilienceConfig()
            .getEngineServiceCircuitBreaker();

        for (int i = 0; i < 20; i++) {
            try {
                provider.executeEngineCall(() -> {
                    throw new IOException("Force open");
                });
            } catch (Exception e) {
                // Expected
            }
        }

        CircuitBreaker.State stateAfterFailures = circuitBreaker.getState();
        assertTrue("Circuit should be OPEN after failures",
            stateAfterFailures == CircuitBreaker.State.OPEN ||
            stateAfterFailures == CircuitBreaker.State.HALF_OPEN);

        Thread.sleep(25000);

        CircuitBreaker.State stateAfterWait = circuitBreaker.getState();
        assertTrue("Circuit should transition to HALF_OPEN",
            stateAfterWait == CircuitBreaker.State.HALF_OPEN ||
            stateAfterWait == CircuitBreaker.State.CLOSED);
    }

    @Test
    public void testMetricsRegistration() {
        assertNotNull("Circuit breaker metrics should be registered",
            meterRegistry.find("resilience4j.circuitbreaker.calls").meter());
    }

    @Test
    public void testGetResilienceComponents() {
        assertNotNull("Should get circuit breaker",
            provider.getCircuitBreaker("engineService"));
        assertNotNull("Should get retry",
            provider.getRetry("default"));
        assertNotNull("Should get rate limiter",
            provider.getRateLimiter("default"));
        assertNotNull("Should get bulkhead",
            provider.getBulkhead("default"));
    }

    @Test
    public void testSingletonInstance() {
        YawlResilienceProvider instance1 = YawlResilienceProvider.getInstance();
        YawlResilienceProvider instance2 = YawlResilienceProvider.getInstance();

        assertSame("Should return same singleton instance", instance1, instance2);
    }

    @Test
    public void testConcurrentExecution() throws Exception {
        AtomicInteger successCount = new AtomicInteger(0);
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    provider.executeEngineCall(() -> {
                        successCount.incrementAndGet();
                        return "success";
                    });
                } catch (Exception e) {
                    // Expected for some threads
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue("At least some threads should succeed",
            successCount.get() > 0);
    }

    @Test
    public void testExceptionPropagation() {
        try {
            provider.executeEngineCall(() -> {
                throw new IllegalArgumentException("Invalid argument");
            });
            fail("Exception should be propagated");
        } catch (Exception e) {
            assertTrue("Exception should be wrapped",
                e.getCause() instanceof IllegalArgumentException);
        }
    }
}
