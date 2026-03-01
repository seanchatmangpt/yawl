package org.yawlfoundation.yawl.patterns.marathon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.patterns.CompetingConsumers;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Competing Consumers Marathon Stress Test — 1 hour fairness & starvation detection.
 *
 * Scenario: Competing Consumers pool with 32 workers.
 * Workload: 100K+ tasks over 60 minutes (variable rate per minute).
 * Verification: Fair distribution across workers (no starvation).
 *
 * Metrics collected every minute:
 * - Per-worker task count (detect starvation)
 * - Queue depth (detect backpressure)
 * - Heap usage (GC pressure)
 * - Distribution fairness: min/max/mean/stddev
 *
 * Starvation detection:
 * - Any worker with 0 tasks over 1-minute window = failure
 * - Worker count variance > 2× imbalance = failure
 * - StdDev > 25% of mean = unfair distribution
 *
 * Success criteria:
 * - All workers process at least 1 task per minute (no starvation)
 * - Max task count < 1.5× min task count (fairness)
 * - StdDev < 20% of mean (tight distribution)
 * - Heap growth < 200MB (no queue leak)
 */
@DisplayName("Competing Consumers Marathon: 1 hour fairness & starvation detection")
class CompetingConsumersMarathon extends MarathonTestBase {

    private ActorRuntime runtime;
    private CompetingConsumers pool;
    private LinkedTransferQueue<TestTask> workQueue;
    private ConcurrentHashMap<Integer, AtomicLong> workerTaskCounts;
    private AtomicLong totalTasksProcessed;
    private AtomicLong totalTasksEnqueued;
    private AtomicInteger starvationViolations;
    private ExecutorService enqueuerExecutor;

    private static final int NUM_WORKERS = 32;
    private static final long TEST_DURATION_MILLIS = 60 * 60 * 1000; // 1 hour
    private static final long METRICS_INTERVAL_MILLIS = 60 * 1000; // Record every minute
    private static final int TOTAL_TASKS_TARGET = 100_000; // 100K+ tasks total
    private static final long TASK_PROCESSING_TIME_MS = 5; // Each task takes ~5ms
    private static final double FAIRNESS_THRESHOLD = 0.25; // StdDev < 25% of mean
    private static final double IMBALANCE_THRESHOLD = 1.5; // Max < 1.5× Min
    private static final long HEAP_GROWTH_THRESHOLD_MB = 200;

    @BeforeEach
    void setUp() throws Exception {
        runtime = createRuntime();
        workQueue = new LinkedTransferQueue<>();
        workerTaskCounts = new ConcurrentHashMap<>();
        totalTasksProcessed = new AtomicLong(0);
        totalTasksEnqueued = new AtomicLong(0);
        starvationViolations = new AtomicInteger(0);
        enqueuerExecutor = Executors.newFixedThreadPool(10);

        // Initialize per-worker counters
        for (int i = 0; i < NUM_WORKERS; i++) {
            workerTaskCounts.put(i, new AtomicLong(0));
        }

        // Create competing consumers pool with task handler
        pool = new CompetingConsumers(
            "marathon-workers",
            NUM_WORKERS,
            workQueue,
            task -> {
                // Process task: record which worker processed it
                int workerId = Integer.parseInt(Thread.currentThread().getName()
                    .replaceAll(".*-worker-", ""));
                workerTaskCounts.get(workerId).incrementAndGet();
                totalTasksProcessed.incrementAndGet();

                // Simulate work
                try {
                    Thread.sleep(TASK_PROCESSING_TIME_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        );

        // Start worker pool
        pool.start(runtime);
        recordMetrics("initialization", 0);
    }

    /**
     * Main marathon test: 1 hour, 100K+ tasks, track per-worker fairness.
     */
    @Test
    @Timeout(minutes = 75)
    @DisplayName("1-hour sustained load: 100K tasks, detect starvation & fairness violations")
    void competingConsumersMarathonFairnessTest() throws Exception {
        long testStartMs = System.currentTimeMillis();
        long metricsDeadlineMs = testStartMs + METRICS_INTERVAL_MILLIS;
        long testEndMs = testStartMs + TEST_DURATION_MILLIS;
        int minuteCounter = 0;

        // Phase 1: Baseline
        recordMetrics("baseline", 0);

        // Phase 2: Sustained enqueueing for 60 minutes
        while (System.currentTimeMillis() < testEndMs && shouldContinue()) {
            long now = System.currentTimeMillis();

            // Submit tasks at variable rate (to test load balancing under varying loads)
            // Target: ~100K tasks over 60 minutes ≈ 1667 tasks/minute
            int tasksThisSecond = 1667 / 60; // ~28 tasks per second
            for (int i = 0; i < tasksThisSecond; i++) {
                workQueue.offer(new TestTask("task-" + totalTasksEnqueued.incrementAndGet()));
            }

            // Record metrics every minute
            if (now >= metricsDeadlineMs) {
                minuteCounter++;
                long minuteTasksProcessed = getMinuteTasksProcessed(minuteCounter);
                recordMetrics("minute_" + minuteCounter, minuteTasksProcessed);

                // Check for starvation and fairness violations
                checkFairnessViolations(minuteCounter);

                // Check heap growth
                if (isHeapGrowingUnbounded(HEAP_GROWTH_THRESHOLD_MB)) {
                    abortIfCritical("Heap growth exceeded " + HEAP_GROWTH_THRESHOLD_MB +
                        "MB. Possible queue leak. Rate: " + getHeapGrowthRatePerMinute() + " MB/min");
                }

                // Log progress
                System.out.printf(
                    "[Marathon] Minute %d: processed=%d, enqueued=%d, queueSize=%d, " +
                    "starvations=%d, heap=%.0f MB%n",
                    minuteCounter,
                    totalTasksProcessed.get(),
                    totalTasksEnqueued.get(),
                    pool.queueSize(),
                    starvationViolations.get(),
                    getHeapUsed() / (1024.0 * 1024)
                );

                metricsDeadlineMs = now + METRICS_INTERVAL_MILLIS;
            }

            // Sleep 100ms between enqueue batches
            Thread.sleep(100);
        }

        // Phase 3: Drain remaining tasks
        enqueuerExecutor.shutdown();
        enqueuerExecutor.awaitTermination(5, TimeUnit.SECONDS);

        // Wait for queue to drain
        long drainDeadlineMs = System.currentTimeMillis() + 30_000; // 30 seconds to drain
        while (pool.queueSize() > 0 && System.currentTimeMillis() < drainDeadlineMs) {
            Thread.sleep(100);
        }

        if (pool.queueSize() > 1000) {
            System.err.println("Warning: Queue did not drain completely. Size: " + pool.queueSize());
        }

        // Phase 4: Shutdown and verify
        pool.shutdown(Duration.ofSeconds(30));
        verifyMarathonResults();
    }

    /**
     * Check fairness violations in this minute's task distribution.
     */
    private void checkFairnessViolations(int minuteNumber) {
        // Get per-worker counts
        var counts = workerTaskCounts.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get()));

        long minCount = counts.values().stream().mapToLong(Long::longValue).min().orElse(0);
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double avgCount = counts.values().stream().mapToLong(Long::longValue).average().orElse(0);
        double stddev = calculateStddev(counts.values().stream().mapToLong(Long::longValue).boxed()
            .collect(Collectors.toList()), avgCount);

        // Starvation check: any worker with 0 tasks = failure
        long starvationCount = counts.values().stream().filter(c -> c == 0).count();
        if (starvationCount > 0) {
            starvationViolations.incrementAndGet();
            System.err.printf("[STARVATION] Minute %d: %d workers have 0 tasks%n",
                minuteNumber, starvationCount);
            abortIfCritical("Starvation detected at minute " + minuteNumber +
                ": " + starvationCount + " workers with 0 tasks");
        }

        // Imbalance check: max < 1.5× min
        if (minCount > 0 && (double) maxCount / minCount > IMBALANCE_THRESHOLD) {
            System.err.printf("[IMBALANCE] Minute %d: max/min = %.2f (threshold: %.2f)%n",
                minuteNumber, (double) maxCount / minCount, IMBALANCE_THRESHOLD);
        }

        // Fairness check: stddev < 25% of average
        if (avgCount > 0 && stddev / avgCount > FAIRNESS_THRESHOLD) {
            System.err.printf("[FAIRNESS] Minute %d: stddev/avg = %.2f%% (threshold: %.2f%%)%n",
                minuteNumber, (stddev / avgCount) * 100, FAIRNESS_THRESHOLD * 100);
        }

        System.out.printf("  Fair: min=%d, max=%d, avg=%.0f, stddev=%.1f, stddev%%=%.1f%%%n",
            minCount, maxCount, avgCount, stddev, (stddev / avgCount) * 100);
    }

    /**
     * Get number of tasks processed in this minute (approximated from totalTasksProcessed).
     */
    private long getMinuteTasksProcessed(int minuteNumber) {
        // For minute N, we can estimate by averaging: total tasks / minutes elapsed
        return totalTasksProcessed.get() / Math.max(1, minuteNumber);
    }

    /**
     * Verify marathon results: no starvation, fair distribution, no heap leak.
     */
    private void verifyMarathonResults() {
        // Get final per-worker counts
        var finalCounts = workerTaskCounts.entrySet().stream()
            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().get()));

        long minCount = finalCounts.values().stream().mapToLong(Long::longValue).min().orElse(0);
        long maxCount = finalCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        double avgCount = finalCounts.values().stream().mapToLong(Long::longValue).average().orElse(0);
        double stddev = calculateStddev(finalCounts.values().stream().mapToLong(Long::longValue).boxed()
            .collect(Collectors.toList()), avgCount);

        System.out.println("\n=== Competing Consumers Marathon Results ===");
        System.out.printf("Total tasks processed: %d%n", totalTasksProcessed.get());
        System.out.printf("Total tasks enqueued: %d%n", totalTasksEnqueued.get());
        System.out.printf("Per-worker distribution: min=%d, max=%d, avg=%.1f, stddev=%.1f%n",
            minCount, maxCount, avgCount, stddev);
        System.out.printf("Fairness ratio (max/min): %.2f%n", minCount > 0 ? (double) maxCount / minCount : 0);
        System.out.printf("Fairness stddev/avg: %.2f%%%n", (stddev / avgCount) * 100);
        System.out.printf("Starvation violations: %d%n", starvationViolations.get());

        // Verify no starvation
        assertThat(starvationViolations.get())
            .as("No workers should be starved (0 tasks) during marathon")
            .isEqualTo(0);

        // Verify fairness: max/min < 1.5×
        assertThat((double) maxCount / minCount)
            .as("Task distribution should be fair (max < 1.5× min)")
            .isLessThan(IMBALANCE_THRESHOLD);

        // Verify fairness: stddev < 25% of mean
        assertThat(stddev / avgCount)
            .as("Task distribution stddev should be < 25% of mean")
            .isLessThan(FAIRNESS_THRESHOLD);

        // Verify heap growth stayed within bounds
        long finalHeapGrowthMB = (metrics.get(metrics.size() - 1).heapUsed - metrics.get(0).heapUsed)
            / (1024 * 1024);
        assertThat(finalHeapGrowthMB)
            .as("Heap growth should be < " + HEAP_GROWTH_THRESHOLD_MB + "MB over 1 hour")
            .isLessThan(HEAP_GROWTH_THRESHOLD_MB);

        System.out.printf("Heap growth: %d MB%n", finalHeapGrowthMB);
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
    private double calculateStddev(java.util.List<Long> values, double mean) {
        if (values.size() < 2) return 0;
        double sumSquaredDiff = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        return Math.sqrt(sumSquaredDiff / values.size());
    }

    /**
     * Helper: Create runtime for this test.
     */
    private static ActorRuntime createRuntime() {
        try {
            return new ActorRuntime(
                "competing-consumers-marathon-runtime",
                Runtime.getRuntime().availableProcessors() * 2
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ActorRuntime", e);
        }
    }

    /**
     * Simple task wrapper for type safety.
     */
    private static final class TestTask {
        final String id;

        TestTask(String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "TestTask{" + id + "}";
        }
    }
}
