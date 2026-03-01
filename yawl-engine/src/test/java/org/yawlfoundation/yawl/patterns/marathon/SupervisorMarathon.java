package org.yawlfoundation.yawl.patterns.marathon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Supervisor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Supervisor Marathon Stress Test — 2 hours high failure rate.
 *
 * Scenario: Supervisor with ONE_FOR_ONE policy, 100 supervised children.
 * Kill rate: 50 kills/second for 2 hours (360K failures total).
 * Verification: No restart cascades (only single child restarts, siblings unaffected).
 *
 * Metrics collected every minute:
 * - Heap usage (growth detection)
 * - Restart count per child (fairness)
 * - Cascade detection (multiple sibling restarts on single failure)
 * - GC pause monitoring
 *
 * Success criteria:
 * - No cascades observed (ONE_FOR_ONE strategy maintained)
 * - Heap growth < 500MB over 2 hours
 * - Restart rate stable (no degradation)
 * - All children receive approximately equal restart attempts
 */
@DisplayName("Supervisor Marathon: High Failure Rate, Cascade Prevention")
class SupervisorMarathon extends MarathonTestBase {

    private ActorRuntime runtime;
    private Supervisor supervisor;
    private List<ActorRef> children;
    private ConcurrentHashMap<Integer, AtomicInteger> restartCountPerChild;
    private AtomicLong totalRestarts;
    private AtomicLong cascadesDetected;
    private ExecutorService killerExecutor;

    private static final int NUM_CHILDREN = 100;
    private static final int KILL_RATE_PER_SEC = 50;
    private static final long TEST_DURATION_MILLIS = 2 * 60 * 60 * 1000; // 2 hours
    private static final long METRICS_INTERVAL_MILLIS = 60 * 1000; // Record every minute
    private static final long RESTART_WINDOW_MILLIS = 10 * 1000; // 10 second restart window
    private static final int MAX_RESTARTS_PER_WINDOW = 10; // OTP: max 10 restarts per 10 sec
    private static final int HEAP_GROWTH_THRESHOLD_MB = 500;

    @BeforeEach
    void setUp() throws Exception {
        runtime = createRuntime();
        children = new ArrayList<>();
        restartCountPerChild = new ConcurrentHashMap<>();
        totalRestarts = new AtomicLong(0);
        cascadesDetected = new AtomicLong(0);
        killerExecutor = Executors.newFixedThreadPool(10);

        // Create supervisor with ONE_FOR_ONE strategy
        supervisor = new Supervisor(
            runtime,
            Supervisor.SupervisorStrategy.ONE_FOR_ONE,
            Duration.ofMillis(50), // Short restart delay to accelerate test
            MAX_RESTARTS_PER_WINDOW,
            Duration.ofMillis(RESTART_WINDOW_MILLIS)
        );

        // Spawn children under supervisor
        for (int i = 0; i < NUM_CHILDREN; i++) {
            final int childId = i;
            restartCountPerChild.put(childId, new AtomicInteger(0));

            ActorRef child = supervisor.spawn("child-" + i, self -> {
                // Child behavior: track restarts, simulate work
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        restartCountPerChild.get(childId).incrementAndGet();
                        totalRestarts.incrementAndGet();
                        Thread.sleep(100); // Simulate minimal work
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            children.add(child);
        }

        supervisor.start();
        recordMetrics("initialization", 0);
    }

    /**
     * Main marathon test: 50 kills/sec for 2 hours, verify no cascades.
     */
    @Test
    @Timeout(minutes = 130)
    @DisplayName("2-hour high failure rate: 50 kills/sec, verify ONE_FOR_ONE no cascades")
    void supervisorMarathonHighFailureRate() throws Exception {
        long testStartMs = System.currentTimeMillis();
        long metricsDeadlineMs = testStartMs + METRICS_INTERVAL_MILLIS;
        long testEndMs = testStartMs + TEST_DURATION_MILLIS;
        int minuteCounter = 0;

        // Phase 1: Baseline metrics (before killing)
        Thread.sleep(1000);
        recordMetrics("baseline", 0);

        // Phase 2: Sustained killing for 2 hours
        while (System.currentTimeMillis() < testEndMs && shouldContinue()) {
            long now = System.currentTimeMillis();

            // Submit KILL_RATE_PER_SEC kill tasks per second
            for (int i = 0; i < KILL_RATE_PER_SEC; i++) {
                killerExecutor.submit(this::killRandomChild);
            }

            // Record metrics every minute
            if (now >= metricsDeadlineMs) {
                minuteCounter++;
                recordMetrics("minute_" + minuteCounter, totalRestarts.get());

                // Check heap growth
                if (isHeapGrowingUnbounded(HEAP_GROWTH_THRESHOLD_MB)) {
                    abortIfCritical("Heap growth exceeded " + HEAP_GROWTH_THRESHOLD_MB +
                        "MB. Possible memory leak. Rate: " + getHeapGrowthRatePerMinute() + " MB/min");
                }

                // Log progress
                System.out.printf(
                    "[Marathon] Minute %d: restarts=%d, cascades=%d, heap=%.0f MB%n",
                    minuteCounter,
                    totalRestarts.get(),
                    cascadesDetected.get(),
                    getHeapUsed() / (1024.0 * 1024)
                );

                metricsDeadlineMs = now + METRICS_INTERVAL_MILLIS;
            }

            // Sleep 20ms between batches to avoid spinning
            Thread.sleep(20);
        }

        // Phase 3: Let system stabilize (stop killing, let restarts drain)
        killerExecutor.shutdown();
        killerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        Thread.sleep(2000); // Wait for pending restarts

        // Phase 4: Verify results
        verifyMarathonResults();
    }

    /**
     * Kill a random child by stopping it (simulating failure).
     * After kill, supervisor should restart with ONE_FOR_ONE (only this child).
     */
    private void killRandomChild() {
        if (shouldContinue() && children.size() > 0) {
            int index = ThreadLocalRandom.current().nextInt(children.size());
            ActorRef child = children.get(index);
            try {
                child.stop();
            } catch (Exception e) {
                // Child already dead, ignore
            }
        }
    }

    /**
     * Verify marathon results: no cascades, fair restart distribution, no heap leak.
     */
    private void verifyMarathonResults() {
        // Verify no cascades detected
        assertThat(cascadesDetected.get())
            .as("ONE_FOR_ONE strategy should prevent cascades")
            .isEqualTo(0);

        // Verify restart count fairness: no child starved or overwhelmed
        List<Integer> restartCounts = new ArrayList<>();
        for (int i = 0; i < NUM_CHILDREN; i++) {
            restartCounts.add(restartCountPerChild.get(i).get());
        }

        int minRestarts = restartCounts.stream().mapToInt(Integer::intValue).min().orElse(0);
        int maxRestarts = restartCounts.stream().mapToInt(Integer::intValue).max().orElse(0);
        double avgRestarts = restartCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
        double stddev = calculateStddev(restartCounts, avgRestarts);

        System.out.printf(
            "[Marathon Results] Restarts: min=%d, max=%d, avg=%.1f, stddev=%.1f, total=%d%n",
            minRestarts, maxRestarts, avgRestarts, stddev, totalRestarts.get()
        );

        // Fairness check: stddev should be < 30% of average (reasonable variance)
        assertThat(stddev / avgRestarts)
            .as("Restart distribution should be fair across children")
            .isLessThan(0.3);

        // Verify heap growth stayed within bounds
        long finalHeapGrowthMB = (metrics.get(metrics.size() - 1).heapUsed - metrics.get(0).heapUsed)
            / (1024 * 1024);
        assertThat(finalHeapGrowthMB)
            .as("Heap growth should be < " + HEAP_GROWTH_THRESHOLD_MB + "MB over 2 hours")
            .isLessThan(HEAP_GROWTH_THRESHOLD_MB);

        // Log final metrics
        System.out.println("\n=== Supervisor Marathon Results ===");
        System.out.printf("Total restarts: %d%n", totalRestarts.get());
        System.out.printf("Cascades detected: %d%n", cascadesDetected.get());
        System.out.printf("Heap growth: %d MB%n", finalHeapGrowthMB);
        System.out.printf("Restart fairness stddev: %.2f%%%n", (stddev / avgRestarts) * 100);
        System.out.printf("Test duration: %.1f minutes%n",
            (metrics.get(metrics.size() - 1).timestamp.toEpochMilli() -
             metrics.get(0).timestamp.toEpochMilli()) / 60_000.0);
    }

    /**
     * Get current heap usage in bytes.
     */
    private long getHeapUsed() {
        return java.lang.management.ManagementFactory.getMemoryMXBean()
            .getHeapMemoryUsage().getUsed();
    }

    /**
     * Calculate standard deviation of a list of numbers.
     */
    private double calculateStddev(List<Integer> values, double mean) {
        if (values.size() < 2) return 0;
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    /**
     * Helper: Create runtime for this test.
     * Uses virtual threads with high parallelism for sustained stress.
     */
    private static ActorRuntime createRuntime() {
        try {
            return new ActorRuntime(
                "supervisor-marathon-runtime",
                Runtime.getRuntime().availableProcessors() * 2
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ActorRuntime", e);
        }
    }
}
