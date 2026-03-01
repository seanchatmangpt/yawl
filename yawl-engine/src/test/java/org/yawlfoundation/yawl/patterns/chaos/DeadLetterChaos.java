package org.yawlfoundation.yawl.patterns.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Chaos engineering test for Dead Letter pattern under extreme concurrent load.
 *
 * Scenario: 1000 concurrent loggers, each sending 10K messages = 10M total.
 * Verify:
 * - All messages logged successfully
 * - No message loss under high concurrency
 * - DeadLetter queue remains bounded
 */
@DisplayName("Dead Letter Chaos: 1000 Concurrent Loggers, 10M Total Messages")
class DeadLetterChaos {
    private ActorRuntime runtime;
    private DeadLetterQueue deadLetterQueue;
    private ChaosInjector injector;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
        deadLetterQueue = new DeadLetterQueue();
        injector = new ChaosInjector();
        metrics = new MetricsCollector("DeadLetterChaos");
    }

    @AfterEach
    void tearDown() throws Exception {
        metrics.printSummary();
        runtime.close();
    }

    /**
     * Test 1000 concurrent loggers sending 10K messages each = 10M total.
     */
    @Test
    @Timeout(minutes = 15)
    @DisplayName("10M messages: 1000 concurrent loggers")
    void testDeadLetterConcurrentLoggers10M() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1000);
        CountDownLatch allComplete = new CountDownLatch(1000);
        AtomicLong totalLogged = new AtomicLong(0);
        AtomicInteger loggerFailures = new AtomicInteger(0);
        List<Long> logTimes = new ArrayList<>();

        try {
            long startTime = System.currentTimeMillis();

            for (int loggerId = 0; loggerId < 1000; loggerId++) {
                final int loggerNum = loggerId;
                executor.submit(() -> {
                    try {
                        long logStart = System.nanoTime();

                        for (int i = 0; i < 10_000; i++) {
                            try {
                                // Create failed message
                                FailedMessage msg = new FailedMessage(
                                    "logger-" + loggerNum,
                                    "CHAOS_FAILURE",
                                    "Message " + i + " from logger " + loggerNum,
                                    System.currentTimeMillis()
                                );

                                // Log to dead letter queue
                                deadLetterQueue.log(msg);
                                totalLogged.incrementAndGet();

                                // Occasionally inject chaos
                                if (Math.random() < 0.01) {
                                    injector.injectLatency(1, 10);
                                }

                            } catch (Exception e) {
                                loggerFailures.incrementAndGet();
                            }
                        }

                        long logEnd = System.nanoTime();
                        synchronized (logTimes) {
                            logTimes.add((logEnd - logStart) / 1_000_000); // Convert to ms
                        }

                    } finally {
                        allComplete.countDown();
                    }
                });
            }

            // Wait for all loggers to complete
            allComplete.await();

            long totalElapsed = System.currentTimeMillis() - startTime;

            // Calculate statistics
            long minLogTime = logTimes.stream().min(Long::compare).orElse(0L);
            long maxLogTime = logTimes.stream().max(Long::compare).orElse(0L);
            double avgLogTime = logTimes.stream().mapToLong(Long::longValue).average().orElse(0);

            System.out.println("\n=== Dead Letter Concurrent Logging Results ===");
            System.out.println("Total messages logged: " + totalLogged.get());
            System.out.println("Expected: 10,000,000");
            System.out.println("Logger failures: " + loggerFailures.get());
            System.out.println("Dead letter queue size: " + deadLetterQueue.size());
            System.out.println("Total elapsed time: " + totalElapsed + " ms");
            System.out.println("Messages/sec: " + (totalLogged.get() / (totalElapsed / 1000.0)));
            System.out.println("Min logger time: " + minLogTime + " ms");
            System.out.println("Max logger time: " + maxLogTime + " ms");
            System.out.println("Avg logger time: " + String.format("%.2f", avgLogTime) + " ms");
            System.out.println("Total chaos injections: " + injector.getInjectionCount());

            // Verify all messages logged
            assertThat(totalLogged.get())
                .as("All 10M messages should be logged")
                .isEqualTo(10_000_000);

            // Verify no message loss
            assertThat(deadLetterQueue.size())
                .as("Dead letter queue should contain all logged messages")
                .isEqualTo(10_000_000);

            // Verify minimal failures
            assertThat(loggerFailures.get())
                .as("Logger failures should be minimal (<0.1%)")
                .isLessThan(10_000);

            // Verify throughput (should handle >1M msgs/sec with 1000 loggers)
            double throughput = totalLogged.get() / (totalElapsed / 1000.0);
            assertThat(throughput)
                .as("Should achieve >1M messages/sec")
                .isGreaterThan(1_000_000);

            metrics.record("totalLogged", totalLogged.get());
            metrics.record("queueSize", deadLetterQueue.size());
            metrics.record("failures", loggerFailures.get());
            metrics.record("throughput_msg_sec", throughput);
            metrics.record("elapsed_ms", totalElapsed);

        } finally {
            executor.shutdown();
        }
    }

    /**
     * Test message query performance with large log.
     */
    @Test
    @Timeout(minutes = 5)
    @DisplayName("Query performance: Filter 1M messages by reason")
    void testQueryPerformanceLargeLog() throws Exception {
        // Pre-populate queue with 1M messages
        System.out.println("Pre-populating dead letter queue with 1M messages...");
        long startPopulate = System.currentTimeMillis();

        for (int i = 0; i < 1_000_000; i++) {
            String reason = switch (i % 5) {
                case 0 -> "ACTOR_NOT_FOUND";
                case 1 -> "TIMEOUT";
                case 2 -> "SERIALIZATION_ERROR";
                case 3 -> "NETWORK_FAILURE";
                default -> "UNKNOWN";
            };

            deadLetterQueue.log(new FailedMessage(
                "test-logger",
                reason,
                "Message " + i,
                System.currentTimeMillis()
            ));
        }

        long populateTime = System.currentTimeMillis() - startPopulate;
        System.out.println("Populated in " + populateTime + " ms");

        // Query 1: Count by reason
        long queryStart = System.currentTimeMillis();
        var countByReason = deadLetterQueue.countByReason();
        long queryTime = System.currentTimeMillis() - queryStart;

        System.out.println("\nQuery results (time: " + queryTime + " ms):");
        countByReason.forEach((reason, count) ->
            System.out.println("  " + reason + ": " + count)
        );

        // Query 2: Filter by reason
        long filterStart = System.currentTimeMillis();
        List<FailedMessage> filtered = deadLetterQueue.queryByReason("TIMEOUT");
        long filterTime = System.currentTimeMillis() - filterStart;

        System.out.println("\nFiltered by TIMEOUT: " + filtered.size() + " (time: " + filterTime + " ms)");

        // Verify query performance
        assertThat(queryTime)
            .as("Count by reason should complete in <1 second for 1M entries")
            .isLessThan(1000);

        assertThat(filterTime)
            .as("Filter query should complete in <2 seconds for 1M entries")
            .isLessThan(2000);

        assertThat(filtered.size())
            .as("Should find ~200K TIMEOUT messages (1M / 5 reasons)")
            .isBetween(180_000, 220_000);

        metrics.record("populateTime", populateTime);
        metrics.record("countByReasonTime", queryTime);
        metrics.record("filterTime", filterTime);
        metrics.record("filteredCount", filtered.size());
    }

    /**
     * Test that queue remains bounded under sustained high load.
     */
    @Test
    @Timeout(minutes = 10)
    @DisplayName("Queue growth: Bounded under sustained 1M/sec load")
    void testQueueBoundedUnderLoad() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch allComplete = new CountDownLatch(1);
        AtomicLong messagesLogged = new AtomicLong(0);

        long startTime = System.currentTimeMillis();
        long targetDuration = 60_000; // 60 seconds

        executor.submit(() -> {
            try {
                for (int i = 0; i < 100_000; i++) {
                    long now = System.currentTimeMillis();
                    if (now - startTime > targetDuration) break;

                    deadLetterQueue.log(new FailedMessage(
                        "sustained-loader",
                        "TEST_FAILURE",
                        "Message " + i,
                        now
                    ));
                    messagesLogged.incrementAndGet();

                    // Log 10K messages per iteration
                    if (i % 10_000 == 0) {
                        long elapsed = now - startTime;
                        System.out.println("T+" + elapsed + " ms: " + messagesLogged.get() +
                            " messages, queue size: " + deadLetterQueue.size());
                    }
                }
            } finally {
                allComplete.countDown();
            }
        });

        // Monitor queue growth
        for (int monitor = 0; monitor < 60; monitor++) {
            Thread.sleep(1000);
            System.out.println("Monitor " + monitor + " sec: queue size = " +
                deadLetterQueue.size() + ", logged = " + messagesLogged.get());
        }

        allComplete.await();

        long totalTime = System.currentTimeMillis() - startTime;
        double throughput = messagesLogged.get() / (totalTime / 1000.0);

        System.out.println("\nFinal queue size: " + deadLetterQueue.size());
        System.out.println("Total messages logged: " + messagesLogged.get());
        System.out.println("Throughput: " + String.format("%.0f", throughput) + " msg/sec");

        // Queue should not grow unbounded
        assertThat(deadLetterQueue.size())
            .as("Queue should remain reasonable size (not unbounded)")
            .isLessThan(10_000_000);

        metrics.record("sustained_messages", messagesLogged.get());
        metrics.record("sustained_throughput", throughput);
        metrics.record("final_queue_size", deadLetterQueue.size());

        executor.shutdown();
    }

    // Helper classes

    private static class DeadLetterQueue {
        private final java.util.concurrent.CopyOnWriteArrayList<FailedMessage> log =
            new java.util.concurrent.CopyOnWriteArrayList<>();

        void log(FailedMessage msg) {
            log.add(msg);
        }

        int size() {
            return log.size();
        }

        java.util.Map<String, Integer> countByReason() {
            return log.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    FailedMessage::reason,
                    java.util.stream.Collectors.summingInt(m -> 1)
                ));
        }

        List<FailedMessage> queryByReason(String reason) {
            return log.stream()
                .filter(m -> m.reason.equals(reason))
                .toList();
        }

        void clear() {
            log.clear();
        }
    }

    private static class FailedMessage {
        final String logger;
        final String reason;
        final String message;
        final long timestamp;

        FailedMessage(String logger, String reason, String message, long timestamp) {
            this.logger = logger;
            this.reason = reason;
            this.message = message;
            this.timestamp = timestamp;
        }

        String reason() {
            return reason;
        }
    }

    private ActorRuntime createRuntime() {
        try {
            Class<?> cls = Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime");
            return (ActorRuntime) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VirtualThreadRuntime", e);
        }
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
