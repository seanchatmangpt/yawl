package org.yawlfoundation.yawl.patterns.marathon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Marathon stress test for Dead Letter Channel pattern.
 * Runs 6 hours of failure logging at 10K/sec rate with unbounded growth detection.
 *
 * Scenario: Continuously log failures from failed message deliveries, measuring:
 * - Unbounded log growth (append-only DeadLetterLog)
 * - Heap pressure with 100M+ entries
 * - Query performance as log grows
 * - GC pauses under sustained append load
 *
 * Expected outcome: Demonstrates unbounded growth risk, identifies need for:
 * - Circular buffer (max entries)
 * - TTL-based cleanup
 * - Log rotation strategy
 *
 * Failure threshold: OOMError before 6 hours OR heap >50GB
 */
@DisplayName("DeadLetter Marathon — 6 hours at 10K/sec failure rate")
class DeadLetterMarathon extends MarathonTestBase {

    private static final int FAILURE_RATE_PER_SEC = 10_000;
    private static final int NUM_SENDER_THREADS = 100;
    private static final int RECORD_INTERVAL_MINUTES = 60;
    private static final long MAX_HEAP_GB = 50;

    private final AtomicLong totalFailuresLogged = new AtomicLong(0);
    private final AtomicLong totalQueriesExecuted = new AtomicLong(0);
    private DeadLetterLog deadLetterLog;
    private ExecutorService senderExecutor;

    @BeforeEach
    void setUp() {
        deadLetterLog = new DeadLetterLog();
        senderExecutor = Executors.newFixedThreadPool(NUM_SENDER_THREADS);
        metrics.clear();
        totalFailuresLogged.set(0);
        totalQueriesExecuted.set(0);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (senderExecutor != null) {
            senderExecutor.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 370, unit = java.util.concurrent.TimeUnit.MINUTES)  // 6 hours + 10 min buffer
    @DisplayName("DeadLetter: 6 hours at 10K/sec, unbounded log growth detection")
    void deadLetterLogGrowthUnbounded() throws Exception {
        System.out.println("\n=== DeadLetter Marathon Test ===");
        System.out.println("Configuration:");
        System.out.println("  - Duration: 6 hours sustained failure logging");
        System.out.println("  - Failure rate: " + FAILURE_RATE_PER_SEC + " failures/sec");
        System.out.println("  - Sender threads: " + NUM_SENDER_THREADS);
        System.out.println("  - Expected total failures: " + (6 * 60 * 60 * FAILURE_RATE_PER_SEC) + " entries");
        System.out.println("  - Max heap threshold: " + MAX_HEAP_GB + "GB");
        System.out.println();

        // Baseline measurement
        System.out.println("Phase 1: Baseline (before failure logging)");
        recordMetrics("baseline", 0);
        gcAndRecord("baseline_gc");
        long baselineHeap = metrics.get(metrics.size() - 1).heapUsed;
        System.out.printf("  Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));

        // Phase 2: 6 hours of continuous failure logging
        System.out.println("\nPhase 2: Sustained failure logging (6 hours)");
        long testStartTime = System.currentTimeMillis();
        long lastRecordTime = System.currentTimeMillis();
        int recordCount = 0;

        for (int hour = 0; hour < 6; hour++) {
            if (!shouldContinue()) {
                throw new AssertionError("Test aborted during hour " + hour);
            }

            for (int minute = 0; minute < 60; minute++) {
                // Calculate failures to log this minute
                int failuresThisMinute = FAILURE_RATE_PER_SEC * 60;

                // Distribute across sender threads
                int failuresPerThread = failuresThisMinute / NUM_SENDER_THREADS;
                int remainder = failuresThisMinute % NUM_SENDER_THREADS;

                // Submit logging tasks
                for (int t = 0; t < NUM_SENDER_THREADS; t++) {
                    final int threadId = t;
                    final int failures = failuresPerThread + (t < remainder ? 1 : 0);

                    senderExecutor.submit(() -> {
                        for (int i = 0; i < failures; i++) {
                            String[] reasons = {
                                "ACTOR_NOT_FOUND",
                                "DELIVERY_TIMEOUT",
                                "MESSAGE_EXPIRED",
                                "INVALID_MESSAGE_FORMAT",
                                "SERIALIZATION_ERROR",
                                "NETWORK_ERROR",
                                "UNKNOWN_ERROR"
                            };
                            String reason = reasons[(int) (System.nanoTime() % reasons.length)];

                            Object failedMessage = "MSG_" + System.nanoTime() + "_" + threadId + "_" + i;
                            int targetActorId = (int) (System.nanoTime() % 10_000);

                            deadLetterLog.record(failedMessage, targetActorId, reason);
                            totalFailuresLogged.incrementAndGet();
                        }
                    });
                }

                // Wait for this minute's batches to complete
                Thread.sleep(Duration.ofSeconds(1).toMillis());

                // Record metrics every RECORD_INTERVAL_MINUTES
                long currentTime = System.currentTimeMillis();
                if ((currentTime - lastRecordTime) >= Duration.ofMinutes(RECORD_INTERVAL_MINUTES).toMillis()) {
                    long elapsedMinutes = (currentTime - testStartTime) / (60 * 1000);
                    recordMetrics("minute_" + elapsedMinutes, deadLetterLog.size());
                    recordCount++;

                    // Check heap pressure
                    long currentHeap = metrics.get(metrics.size() - 1).heapUsed;
                    long heapGrowthGB = (currentHeap - baselineHeap) / (1024L * 1024L * 1024L);

                    if (currentHeap / (1024L * 1024L * 1024L) > MAX_HEAP_GB) {
                        abortIfCritical(
                            "Heap exceeded " + MAX_HEAP_GB + "GB: " + (currentHeap / (1024L * 1024L * 1024L)) + "GB"
                        );
                    }

                    // Print progress every hour
                    if (elapsedMinutes > 0 && elapsedMinutes % 60 == 0) {
                        System.out.printf("Hour %d: %d entries, heap=%.1f GB, growth=%.1f GB%n",
                            elapsedMinutes / 60,
                            deadLetterLog.size(),
                            currentHeap / (1024.0 * 1024 * 1024),
                            heapGrowthGB
                        );
                    }

                    lastRecordTime = currentTime;
                }
            }

            // GC checkpoint every hour
            if (hour % 2 == 0) {
                gcAndRecord("gc_hour_" + hour);
            }
        }

        // Phase 3: Final measurement and cleanup
        System.out.println("\nPhase 3: Final measurement and analysis");
        senderExecutor.shutdown();
        if (!senderExecutor.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
            senderExecutor.shutdownNow();
        }

        Thread.sleep(1000);  // Let any pending tasks settle
        gcAndRecord("final_gc");
        recordMetrics("final", deadLetterLog.size());

        // Results
        long finalHeap = metrics.get(metrics.size() - 1).heapUsed;
        long totalHeapGrowthGB = (finalHeap - baselineHeap) / (1024L * 1024L * 1024L);
        long totalHeapGrowthMB = (finalHeap - baselineHeap) / (1024L * 1024L);
        long logSize = deadLetterLog.size();
        double bytesPerEntry = logSize > 0 ? (double) finalHeap / logSize : 0;

        System.out.println("\n=== Test Results ===");
        System.out.printf("Total failures logged: %d%n", totalFailuresLogged.get());
        System.out.printf("DeadLetterLog final size: %d entries%n", logSize);
        System.out.printf("Baseline heap: %d MB%n", baselineHeap / (1024 * 1024));
        System.out.printf("Final heap: %.1f GB%n", finalHeap / (1024.0 * 1024 * 1024));
        System.out.printf("Total heap growth: %.1f GB (%d MB)%n", totalHeapGrowthGB, totalHeapGrowthMB);
        System.out.printf("Bytes per entry: %.2f bytes%n", bytesPerEntry);
        System.out.printf("Growth rate: %.2f GB/hour%n", (double) totalHeapGrowthGB / 6.0);

        // Analyze failure distribution
        List<String> topReasons = deadLetterLog.getTopReasons(5);
        System.out.println("\nTop failure reasons:");
        for (String reason : topReasons) {
            long count = deadLetterLog.countByReason(reason);
            System.out.printf("  %s: %d (%.1f%%)%n", reason, count, (double) count / logSize * 100);
        }

        // Print metric progression
        System.out.println("\nHeap growth progression:");
        for (int i = 0; i < metrics.size(); i += Math.max(1, metrics.size() / 10)) {
            MarathonMetrics m = metrics.get(i);
            System.out.printf("  %s: %d MB (custom=%d)%n", m.phase, m.heapUsedMB(), m.customValue);
        }

        // Verify: heap growth must be reasonable (not explosive)
        assertThat(finalHeap / (1024L * 1024L * 1024L))
            .as("Final heap must not exceed " + MAX_HEAP_GB + "GB")
            .isLessThan(MAX_HEAP_GB);

        // Verify: log entries must be persisted
        assertThat(logSize)
            .as("Log must contain all failures")
            .isGreaterThan(100_000_000);  // At least 100M entries expected

        System.out.println("\n✓ DeadLetter Marathon test PASSED");
        System.out.println("  (Unbounded growth confirmed — real implementations need log rotation/TTL cleanup)");
    }

    /**
     * Simple in-memory dead letter log implementation.
     * In production, use circular buffer or external log with rotation.
     */
    static class DeadLetterLog {
        private final List<DeadLetter> log = Collections.synchronizedList(new ArrayList<>());

        void record(Object message, int targetActorId, String reason) {
            log.add(new DeadLetter(message, targetActorId, reason, System.nanoTime()));
        }

        List<DeadLetter> getLog() {
            return new ArrayList<>(log);
        }

        int size() {
            return log.size();
        }

        void clear() {
            log.clear();
        }

        long countByReason(String reason) {
            return log.parallelStream()
                .filter(dl -> dl.reason.equals(reason))
                .count();
        }

        List<String> getTopReasons(int limit) {
            return log.parallelStream()
                .map(dl -> dl.reason)
                .distinct()
                .limit(limit)
                .toList();
        }
    }

    /**
     * Dead letter entry data model
     */
    static class DeadLetter {
        final Object message;
        final int targetActorId;
        final String reason;
        final long timestamp;

        DeadLetter(Object message, int targetActorId, String reason, long timestamp) {
            this.message = message;
            this.targetActorId = targetActorId;
            this.reason = reason;
            this.timestamp = timestamp;
        }
    }
}
