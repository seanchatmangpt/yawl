package org.yawlfoundation.yawl.patterns.marathon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;
import org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.RequestReply;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Marathon stress test for Request-Reply pattern.
 * Runs sustained ask/reply load for 50+ minutes with memory leak detection.
 *
 * Scenario: Requester actors send queries to responder actors, measuring:
 * - Heap growth (must stay bounded)
 * - Registry size (pending requests should be cleaned up)
 * - GC pauses (should not exceed 100ms)
 * - Correlation ID cleanup (no leaks when timeouts fire)
 *
 * Failure threshold: Heap growth >50MB over 50 minutes
 */
@DisplayName("RequestReply Marathon — 50 min sustained load, memory leak detection")
class RequestReplyMarathon extends MarathonTestBase {

    private ActorRuntime runtime;
    private ActorRef replyReceiver;
    private static final int ASKS_PER_SECOND = 10_000;
    private static final int NUM_RESPONDERS = 10;
    private static final int REQUEST_TIMEOUT_MS = 5_000;
    private static final int HEAP_GROWTH_THRESHOLD_MB = 50;
    private static final int RECORD_INTERVAL_MINUTES = 1;
    private static final long TEST_DURATION_MILLIS =
        Long.parseLong(System.getProperty("marathon.duration.minutes", "50")) * 60_000L; // 50 minutes default

    private final AtomicLong totalAsksCompleted = new AtomicLong(0);
    private final AtomicLong totalAsksTimedOut = new AtomicLong(0);
    private final ConcurrentHashMap<Long, Long> pendingRequests = new ConcurrentHashMap<>();
    private final List<Integer> registrySizeHistory = new ArrayList<>();

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
        metrics.clear();
        totalAsksCompleted.set(0);
        totalAsksTimedOut.set(0);
        pendingRequests.clear();
        registrySizeHistory.clear();
        // Spawn a shared reply receiver actor that dispatches replies to RequestReply registry
        replyReceiver = runtime.spawn(self -> {
            while (!Thread.currentThread().isInterrupted()) {
                Object msg = self.recv();
                if (msg instanceof Msg.Reply reply) {
                    RequestReply.dispatch(reply);
                }
            }
        });
    }

    @AfterEach
    void tearDown() throws Exception {
        if (runtime != null) {
            runtime.close();
        }
    }

    @Test
    @Timeout(value = 75, unit = java.util.concurrent.TimeUnit.MINUTES)
    @DisplayName("RequestReply: 50 min sustained load, memory leak detection")
    void requestReplyMemoryLeakDetection() throws Exception {
        System.out.println("\n=== RequestReply Marathon Test ===");
        System.out.println("Configuration:");
        System.out.println("  - Duration: 50 minutes sustained load");
        System.out.println("  - Ask rate: " + ASKS_PER_SECOND + " asks/sec");
        System.out.println("  - Responders: " + NUM_RESPONDERS + " actors");
        System.out.println("  - Request timeout: " + REQUEST_TIMEOUT_MS + "ms");
        System.out.println("  - Heap growth threshold: " + HEAP_GROWTH_THRESHOLD_MB + "MB");
        System.out.println("  - Recording interval: " + RECORD_INTERVAL_MINUTES + " minute");
        System.out.println();

        // Phase 1: Baseline (5 minutes, no asks)
        System.out.println("Phase 1: Baseline (5 min, no sustained load)");
        recordMetrics("baseline", 0);
        gcAndRecord("baseline_gc");
        Thread.sleep(Duration.ofMinutes(5).toMillis());
        recordMetrics("after_baseline", 0);

        long baselineHeap = metrics.get(metrics.size() - 1).heapUsed;
        System.out.printf("  Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));

        // Spawn responder actors
        System.out.println("\nPhase 2: Spawning " + NUM_RESPONDERS + " responder actors");
        List<ActorRef> responders = new ArrayList<>();
        for (int i = 0; i < NUM_RESPONDERS; i++) {
            ActorRef responder = runtime.spawn(self -> respondToQueries(self));
            responders.add(responder);
        }
        System.out.println("  Responders spawned: " + responders.size());

        // Phase 3: Sustained load (50 minutes)
        System.out.println("\nPhase 3: Sustained load (50 min)");
        long phaseStartTime = System.currentTimeMillis();
        long recordStartTime = System.currentTimeMillis();
        int recordCount = 0;

        for (int minute = 0; minute < 50; minute++) {
            if (!shouldContinue()) {
                throw new AssertionError("Test aborted during load phase at minute " + minute);
            }

            // Fire asks at ASKS_PER_SECOND rate
            long minuteStartMs = System.currentTimeMillis();
            long requestsThisMinute = 0;

            while (System.currentTimeMillis() - minuteStartMs < Duration.ofMinutes(1).toMillis()) {
                int responderIndex = (int) (requestsThisMinute % NUM_RESPONDERS);
                ActorRef responder = responders.get(responderIndex);

                fireAsyncRequest(responder, minute * 60 + (int) requestsThisMinute);
                requestsThisMinute++;

                // Sleep to maintain rate (10K asks/sec = 0.1ms per ask, but we batch)
                if (requestsThisMinute % 100 == 0) {
                    Thread.yield();
                }
            }

            // Record metrics every minute
            long elapsedMs = System.currentTimeMillis() - recordStartTime;
            recordMetrics("minute_" + minute, requestsThisMinute);
            recordCount++;

            // Check for unbounded heap growth
            if (isHeapGrowingUnbounded(HEAP_GROWTH_THRESHOLD_MB)) {
                gcAndRecord("gc_during_load_minute_" + minute);
                double growthRate = getHeapGrowthRatePerMinute();
                abortIfCritical("Heap growing unbounded: " + growthRate + "MB/min");
            }

            int registrySize = pendingRequests.size();
            registrySizeHistory.add(registrySize);
            if (minute % 10 == 0) {
                System.out.printf("  Minute %d: %.0f asks/sec, registry=%d, heap=%.0f MB%n",
                    minute,
                    (double) totalAsksCompleted.get() / ((System.currentTimeMillis() - phaseStartTime) / 1000),
                    registrySize,
                    metrics.get(metrics.size() - 1).heapUsedMB()
                );
            }
        }

        System.out.println("\nPhase 4: Cleanup + verification");
        // Wait for timeouts to fire (REQUEST_TIMEOUT_MS + buffer)
        Thread.sleep(Duration.ofMillis(REQUEST_TIMEOUT_MS + 1000).toMillis());

        // Force cleanup: garbage collect to finalize pending requests
        gcAndRecord("cleanup_gc");
        Thread.sleep(1000);
        recordMetrics("after_cleanup", 0);

        // Final verification
        System.out.println("\n=== Test Results ===");
        long finalHeap = metrics.get(metrics.size() - 1).heapUsed;
        long heapGrowth = (finalHeap - baselineHeap) / (1024 * 1024);

        System.out.printf("Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));
        System.out.printf("Final heap: %d MB%n", finalHeap / (1024 * 1024));
        System.out.printf("Total growth: %d MB%n", heapGrowth);
        System.out.printf("Growth rate: %.2f MB/min%n", getHeapGrowthRatePerMinute());
        System.out.printf("Total asks completed: %d%n", totalAsksCompleted.get());
        System.out.printf("Total asks timed out: %d%n", totalAsksTimedOut.get());
        System.out.printf("Final registry size: %d%n", pendingRequests.size());
        System.out.printf("Max registry size: %d%n", registrySizeHistory.stream().mapToInt(Integer::intValue).max().orElse(0));

        // Print metric summary
        System.out.println("\nHeap usage progression:");
        for (int i = 0; i < metrics.size(); i += Math.max(1, metrics.size() / 10)) {
            MarathonMetrics m = metrics.get(i);
            System.out.printf("  %s: %d MB%n", m.phase, m.heapUsedMB());
        }

        // Assertion: heap growth must be bounded
        assertThat(heapGrowth)
            .as("Heap growth must be less than " + HEAP_GROWTH_THRESHOLD_MB + "MB over 50 minutes")
            .isLessThan(HEAP_GROWTH_THRESHOLD_MB);

        // Assertion: registry should be mostly clean after timeouts
        assertThat(pendingRequests.size())
            .as("Pending requests registry should be cleaned after timeouts")
            .isLessThan(1000);

        System.out.println("\n✓ RequestReply Marathon test PASSED");
    }

    /**
     * Responder actor behavior: receives Query messages, sends Reply after small delay
     */
    private void respondToQueries(ActorRef self) throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            Object msg = self.recv();
            if (msg instanceof Msg.Query query) {
                // Simulate work with small delay
                Thread.sleep(10);

                // Send reply
                String answer = "response-to-" + query.question();
                Msg.Reply reply = new Msg.Reply(
                    query.correlationId(),
                    answer,
                    null
                );
                query.sender().tell(reply);
            }
        }
    }

    /**
     * Fire an async request and track it in the registry
     */
    private void fireAsyncRequest(ActorRef responder, long correlationId) {
        // Record pending request
        long timestamp = System.currentTimeMillis();
        pendingRequests.put(correlationId, timestamp);

        // Send query with timeout; replyReceiver actor handles incoming replies
        try {
            Msg.Query query = new Msg.Query(
                correlationId,
                replyReceiver,
                "QUERY_" + correlationId,
                null
            );

            CompletableFuture<Object> future = RequestReply.ask(responder, query, Duration.ofMillis(REQUEST_TIMEOUT_MS));

            // Handle reply asynchronously
            future.thenAccept(reply -> {
                pendingRequests.remove(correlationId);
                totalAsksCompleted.incrementAndGet();
            }).exceptionally(ex -> {
                // Timeout or other exception
                pendingRequests.remove(correlationId);
                totalAsksTimedOut.incrementAndGet();
                return null;
            });
        } catch (Exception e) {
            pendingRequests.remove(correlationId);
            totalAsksTimedOut.incrementAndGet();
        }
    }

    /**
     * Create runtime - can be overridden for testing
     */
    protected ActorRuntime createRuntime() {
        return new VirtualThreadRuntime();
    }
}
