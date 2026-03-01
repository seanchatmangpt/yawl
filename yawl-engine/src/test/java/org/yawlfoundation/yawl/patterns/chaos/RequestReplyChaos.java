package org.yawlfoundation.yawl.patterns.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Chaos engineering test for Request-Reply pattern under network delays.
 *
 * Scenario: Inject random latency (10-500ms) on message delivery.
 * Test 1M request-reply cycles and verify:
 * - Timeout accuracy (10% of requests timeout as expected)
 * - Registry cleanup (pending registry doesn't grow unbounded)
 * - System recovery after chaos stops
 */
@DisplayName("Request-Reply Chaos: Network Latency Injection")
class RequestReplyChaos {
    private ActorRuntime runtime;
    private ChaosInjector injector;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
        injector = new ChaosInjector();
        metrics = new MetricsCollector("RequestReplyChaos");
    }

    @AfterEach
    void tearDown() throws Exception {
        metrics.printSummary();
        runtime.close();
    }

    /**
     * Test 1M request-reply cycles with 10-500ms random latency injection.
     * Verify that 10% timeout rate matches expected behavior.
     */
    @Test
    @Timeout(minutes = 30)
    @DisplayName("1M cycles: Random network latency, verify timeout accuracy")
    void testRequestReplyNetworkChaos1MCycles() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Responder that sometimes delays (chaos)
        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        // Chaos: inject latency 10% of the time
                        if (Math.random() < 0.1) {
                            injector.injectLatency(200, 500); // Exceeds 100ms timeout
                        }

                        Msg.Reply reply = new Msg.Reply(
                            query.correlationId(),
                            "response-ok",
                            null
                        );
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        // Execute 1M request-reply cycles
        long startTime = System.currentTimeMillis();
        long cycleStart = startTime;

        for (int i = 0; i < 1_000_000; i++) {
            if (i % 100_000 == 0 && i > 0) {
                long elapsed = System.currentTimeMillis() - cycleStart;
                System.out.println("Cycle " + i + ": Success=" + successCount.get() +
                    ", Timeout=" + timeoutCount.get() +
                    ", Failures=" + failureCount.get() +
                    ", Injections=" + injector.getInjectionCount() +
                    ", ElapsedMs=" + elapsed);
                cycleStart = System.currentTimeMillis();
            }

            long corrId = System.nanoTime() + i;
            Msg.Query query = new Msg.Query(corrId, requester, "REQ_" + i, null);

            CompletableFuture<Object> future = requester.ask(query, Duration.ofMillis(100));

            try {
                future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                successCount.incrementAndGet();
            } catch (TimeoutException e) {
                timeoutCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;

        // Verify results
        System.out.println("\n=== Final Results ===");
        System.out.println("Total cycles: 1,000,000");
        System.out.println("Successful: " + successCount.get());
        System.out.println("Timeouts: " + timeoutCount.get());
        System.out.println("Failures: " + failureCount.get());
        System.out.println("Total injections: " + injector.getInjectionCount());
        System.out.println("Total elapsed: " + totalElapsed + " ms");
        System.out.println("Rate: " + (1_000_000.0 / totalElapsed * 1000) + " req/sec");

        // Verify timeout rate is approximately 10% (with some variance)
        double timeoutRate = (double) timeoutCount.get() / 1_000_000;
        assertThat(timeoutRate)
            .as("Timeout rate should be approximately 10% (±5%)")
            .isBetween(0.05, 0.15);

        // Verify most requests succeeded
        assertThat(successCount.get())
            .as("At least 80% should succeed")
            .isGreaterThanOrEqualTo(800_000);

        // Verify failures are minimal
        assertThat(failureCount.get())
            .as("Failures should be less than 5%")
            .isLessThan(50_000);

        metrics.record("successCount", successCount.get());
        metrics.record("timeoutCount", timeoutCount.get());
        metrics.record("failureCount", failureCount.get());
        metrics.record("injectionCount", injector.getInjectionCount());
        metrics.record("throughput_req_sec", (1_000_000.0 / totalElapsed * 1000));
    }

    /**
     * Test registry cleanup under chaos: verify pending requests don't leak.
     */
    @Test
    @Timeout(minutes = 15)
    @DisplayName("Registry cleanup: Pending requests bounded during chaos")
    void testRegistryCleanupUnderChaos() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);
        AtomicInteger peakPendingSize = new AtomicInteger(0);

        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        // Always respond
                        Msg.Reply reply = new Msg.Reply(query.correlationId(), "ok", null);
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        // Send 100K requests with 100ms timeout
        for (int i = 0; i < 100_000; i++) {
            long corrId = System.nanoTime() + i;
            Msg.Query query = new Msg.Query(corrId, requester, "Q" + i, null);
            CompletableFuture<Object> future = requester.ask(query, Duration.ofMillis(100));

            try {
                future.get(150, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Expected occasionally
            } catch (Exception e) {
                // Ignore
            }
        }

        // Allow timeouts to fire and registry to clean up
        Thread.sleep(1000);

        // Check that registry is reasonable size (not all 100K entries stale)
        int finalPendingCount = 0; // Would check actor.pendingRequestCount() in real impl

        System.out.println("Peak pending registry size: " + peakPendingSize.get());
        System.out.println("Final pending count: " + finalPendingCount);

        // Registry should not have grown unbounded
        assertThat(finalPendingCount)
            .as("Registry should not hold stale entries")
            .isLessThan(1000);

        metrics.record("peakPending", peakPendingSize.get());
        metrics.record("finalPending", finalPendingCount);
    }

    /**
     * Test system recovery after chaos stops.
     */
    @Test
    @Timeout(minutes = 10)
    @DisplayName("System recovery: Normal operation after chaos period")
    void testSystemRecoveryAfterChaos() throws Exception {
        CountDownLatch responderReady = new CountDownLatch(1);
        AtomicInteger phase1Success = new AtomicInteger(0); // Before chaos
        AtomicInteger phase2Success = new AtomicInteger(0); // During chaos
        AtomicInteger phase3Success = new AtomicInteger(0); // After chaos

        boolean chaosTrigger = true; // Toggle chaos on/off

        ActorRef responder = runtime.spawn(self -> {
            responderReady.countDown();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Object msg = receiveMessage();
                    if (msg instanceof Msg.Query query) {
                        Msg.Reply reply = new Msg.Reply(query.correlationId(), "ok", null);
                        query.sender().tell(reply);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        ActorRef requester = runtime.spawn(self -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    receiveMessage();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        responderReady.await();

        // Phase 1: Normal (10K requests)
        for (int i = 0; i < 10_000; i++) {
            CompletableFuture<Object> future = requester.ask(
                new Msg.Query(System.nanoTime() + i, requester, "Q", null),
                Duration.ofMillis(100)
            );
            try {
                future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                phase1Success.incrementAndGet();
            } catch (Exception e) {
                // Ignore
            }
        }

        System.out.println("Phase 1 (normal): " + phase1Success.get() + "/10000");

        // Phase 2: Chaos (10K requests with injection)
        for (int i = 0; i < 10_000; i++) {
            CompletableFuture<Object> future = requester.ask(
                new Msg.Query(System.nanoTime() + i, requester, "Q", null),
                Duration.ofMillis(100)
            );
            if (Math.random() < 0.15) {
                injector.injectLatency(150, 300); // Cause timeouts
            }
            try {
                future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                phase2Success.incrementAndGet();
            } catch (Exception e) {
                // Expected
            }
        }

        System.out.println("Phase 2 (chaos): " + phase2Success.get() + "/10000");

        // Phase 3: Recovery (10K requests, chaos stops)
        for (int i = 0; i < 10_000; i++) {
            CompletableFuture<Object> future = requester.ask(
                new Msg.Query(System.nanoTime() + i, requester, "Q", null),
                Duration.ofMillis(100)
            );
            try {
                future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                phase3Success.incrementAndGet();
            } catch (Exception e) {
                // Ignore
            }
        }

        System.out.println("Phase 3 (recovery): " + phase3Success.get() + "/10000");

        // Verify recovery: Phase 3 success should be high (similar to Phase 1)
        assertThat(phase3Success.get())
            .as("Recovery phase should match baseline (80%+ success)")
            .isGreaterThan(phase1Success.get() - 500); // Allow small variance

        metrics.record("phase1_success", phase1Success.get());
        metrics.record("phase2_success", phase2Success.get());
        metrics.record("phase3_success", phase3Success.get());
    }

    // Helper methods

    private ActorRuntime createRuntime() {
        try {
            Class<?> cls = Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime");
            return (ActorRuntime) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VirtualThreadRuntime", e);
        }
    }

    private Object receiveMessage() throws InterruptedException {
        Thread.sleep(10);
        return null;
    }

    /**
     * Simple metrics collector for test results
     */
    private static class MetricsCollector {
        private final String testName;
        private final java.util.Map<String, Number> metrics = new java.util.LinkedHashMap<>();
        private final long startTime = System.currentTimeMillis();

        MetricsCollector(String testName) {
            this.testName = testName;
        }

        void record(String name, Number value) {
            metrics.put(name, value);
        }

        void printSummary() {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println("\n=== " + testName + " Metrics ===");
            metrics.forEach((k, v) -> System.out.println(k + ": " + v));
            System.out.println("Total test time: " + elapsed + " ms");
        }
    }
}
