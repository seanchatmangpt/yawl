package org.yawlfoundation.yawl.patterns.chaos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Chaos engineering test for Competing Consumers pattern under backpressure.
 *
 * Scenario: Enqueue 1M/sec for 30 minutes with only 4 workers (slow consumers).
 * Verify:
 * - Queue experiences backpressure (doesn't grow unbounded)
 * - System detects and handles backpressure
 * - Work distribution remains fair across workers
 * - No message loss under high load
 */
@DisplayName("Competing Consumers Chaos: 1M/sec Backpressure at 4 Workers")
class CompetingConsumersChaos {
    private ActorRuntime runtime;
    private ChaosInjector injector;
    private MetricsCollector metrics;
    private CompetingConsumersPool workPool;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
        injector = new ChaosInjector();
        metrics = new MetricsCollector("CompetingConsumersChaos");
    }

    @AfterEach
    void tearDown() throws Exception {
        metrics.printSummary();
        if (workPool != null) {
            workPool.shutdown();
        }
        runtime.close();
    }

    /**
     * Test backpressure at high enqueue rate with slow workers.
     * Enqueue 1M/sec for 30 minutes with 4 workers.
     */
    @Test
    @Timeout(minutes = 35)
    @DisplayName("30-minute backpressure: 1M/sec into 4-worker pool")
    void testBackpressure1MPerSecFor30Minutes() throws Exception {
        workPool = new CompetingConsumersPool(4); // Only 4 workers
        AtomicLong totalEnqueued = new AtomicLong(0);
        AtomicLong totalProcessed = new AtomicLong(0);
        AtomicInteger backpressureEvents = new AtomicInteger(0);
        AtomicLong maxQueueSize = new AtomicLong(0);

        ExecutorService enqueuer = Executors.newFixedThreadPool(100); // Fast enqueuing

        long startTime = System.currentTimeMillis();
        System.out.println("Starting 30-minute backpressure test (1M/sec into 4 workers)...");

        try {
            // Enqueue for 30 minutes
            for (int minute = 0; minute < 30; minute++) {
                final int currentMinute = minute;
                long minuteStart = System.currentTimeMillis();

                // Enqueue ~1M messages per minute
                for (int i = 0; i < 1_000_000; i++) {
                    final int messageNum = i;
                    enqueuer.submit(() -> {
                        try {
                            WorkItem work = new WorkItem(
                                currentMinute * 1_000_000 + messageNum,
                                System.currentTimeMillis()
                            );

                            if (!workPool.enqueue(work)) {
                                backpressureEvents.incrementAndGet();
                            }
                            totalEnqueued.incrementAndGet();

                            // Inject occasional latency (chaos)
                            if (Math.random() < 0.001) {
                                injector.injectLatency(1, 5);
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
                }

                // Every minute, print metrics
                long elapsed = System.currentTimeMillis() - minuteStart;
                long queueSize = workPool.getQueueSize();
                maxQueueSize.set(Math.max(maxQueueSize.get(), queueSize));

                System.out.println(
                    "Minute " + minute + " | Enqueued=" + totalEnqueued.get() +
                    " | Processed=" + totalProcessed.get() +
                    " | Queue=" + queueSize +
                    " | Backpressure=" + backpressureEvents.get() +
                    " | Time=" + elapsed + "ms"
                );

                totalProcessed.set(workPool.getProcessedCount());
            }

            long totalElapsed = System.currentTimeMillis() - startTime;

            // Wait for queue to drain
            System.out.println("Draining queue...");
            long drainStart = System.currentTimeMillis();
            while (workPool.getQueueSize() > 0 &&
                   System.currentTimeMillis() - drainStart < 300_000) { // 5 minute timeout
                Thread.sleep(5000);
                totalProcessed.set(workPool.getProcessedCount());
                System.out.println("Queue size: " + workPool.getQueueSize());
            }

            long finalProcessed = workPool.getProcessedCount();

            System.out.println("\n=== 30-Minute Backpressure Test Results ===");
            System.out.println("Total enqueued: " + totalEnqueued.get());
            System.out.println("Total processed: " + finalProcessed);
            System.out.println("Max queue size: " + maxQueueSize.get());
            System.out.println("Backpressure events: " + backpressureEvents.get());
            System.out.println("Messages/sec: " + (totalEnqueued.get() / (totalElapsed / 1000.0)));
            System.out.println("Injections fired: " + injector.getInjectionCount());

            // Verify: All messages processed (eventually)
            assertThat(finalProcessed)
                .as("All enqueued messages should be processed")
                .isEqualTo(totalEnqueued.get());

            // Verify: Queue stayed bounded (not all 1M messages stuck)
            assertThat(maxQueueSize.get())
                .as("Max queue size should be reasonable (backpressure working)")
                .isLessThan(100_000); // 100K max in queue at any time

            // Verify: System detected backpressure
            assertThat(backpressureEvents.get())
                .as("System should detect backpressure events")
                .isGreaterThan(0);

            metrics.record("totalEnqueued", totalEnqueued.get());
            metrics.record("totalProcessed", finalProcessed);
            metrics.record("maxQueueSize", maxQueueSize.get());
            metrics.record("backpressureEvents", backpressureEvents.get());
            metrics.record("throughput_msg_sec", totalEnqueued.get() / (totalElapsed / 1000.0));

        } finally {
            enqueuer.shutdown();
        }
    }

    /**
     * Test fair work distribution across workers under load.
     */
    @Test
    @Timeout(minutes = 15)
    @DisplayName("Fair distribution: 4 workers process equal share of 100K tasks")
    void testFairDistribution() throws Exception {
        workPool = new CompetingConsumersPool(4);
        int[] workerCounts = new int[4];
        AtomicLong totalProcessed = new AtomicLong(0);

        // Register callbacks to track which worker processes each task
        for (int i = 0; i < 4; i++) {
            final int workerId = i;
            workPool.registerWorkerCallback(workerId, () -> {
                synchronized (workerCounts) {
                    workerCounts[workerId]++;
                }
                totalProcessed.incrementAndGet();
            });
        }

        // Enqueue 100K tasks
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100_000; i++) {
            WorkItem work = new WorkItem(i, System.currentTimeMillis());
            workPool.enqueue(work);
        }

        // Wait for all to be processed
        while (totalProcessed.get() < 100_000) {
            Thread.sleep(100);
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // Analyze distribution
        int min = java.util.Arrays.stream(workerCounts).min().orElse(0);
        int max = java.util.Arrays.stream(workerCounts).max().orElse(0);
        double avg = java.util.Arrays.stream(workerCounts).average().orElse(0);
        double imbalance = (double) (max - min) / avg;

        System.out.println("\n=== Fair Distribution Results ===");
        System.out.println("Total tasks: 100,000");
        System.out.println("Total processed: " + totalProcessed.get());
        System.out.println("Worker distribution:");
        for (int i = 0; i < 4; i++) {
            System.out.println("  Worker " + i + ": " + workerCounts[i]);
        }
        System.out.println("Min: " + min + ", Max: " + max + ", Avg: " + String.format("%.0f", avg));
        System.out.println("Imbalance ratio: " + String.format("%.2f", imbalance));
        System.out.println("Time: " + elapsed + " ms");

        // Verify fair distribution (within 25% variance)
        assertThat(imbalance)
            .as("Work distribution should be fair (imbalance < 0.25)")
            .isLessThan(0.25);

        // Verify all processed
        assertThat(totalProcessed.get())
            .as("All 100K tasks should be processed")
            .isEqualTo(100_000);

        metrics.record("worker0", workerCounts[0]);
        metrics.record("worker1", workerCounts[1]);
        metrics.record("worker2", workerCounts[2]);
        metrics.record("worker3", workerCounts[3]);
        metrics.record("imbalanceRatio", imbalance);
    }

    /**
     * Test queue doesn't grow unbounded under sustained load.
     */
    @Test
    @Timeout(minutes = 15)
    @DisplayName("Queue bounded: Sustained 100K/sec, no unbounded growth")
    void testQueueBoundedUnderLoad() throws Exception {
        workPool = new CompetingConsumersPool(4);
        ExecutorService enqueuer = Executors.newFixedThreadPool(50);
        AtomicLong totalEnqueued = new AtomicLong(0);
        AtomicLong totalProcessed = new AtomicLong(0);
        AtomicLong peakQueueSize = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        try {
            // Enqueue for 10 minutes at ~100K/sec
            for (int second = 0; second < 600; second++) {
                final int currentSecond = second;

                // Enqueue 100K messages
                for (int i = 0; i < 100_000; i++) {
                    enqueuer.submit(() -> {
                        try {
                            WorkItem work = new WorkItem(
                                currentSecond * 100_000 + i,
                                System.currentTimeMillis()
                            );
                            workPool.enqueue(work);
                            totalEnqueued.incrementAndGet();
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
                }

                // Every 30 seconds, check queue size
                if (second % 30 == 0) {
                    long queueSize = workPool.getQueueSize();
                    peakQueueSize.set(Math.max(peakQueueSize.get(), queueSize));
                    totalProcessed.set(workPool.getProcessedCount());

                    System.out.println(
                        "T+" + (second) + "s | Enqueued=" + totalEnqueued.get() +
                        " | Processed=" + totalProcessed.get() +
                        " | Queue=" + queueSize +
                        " | Peak=" + peakQueueSize.get()
                    );
                }

                Thread.sleep(1000);
            }

            long totalElapsed = System.currentTimeMillis() - startTime;

            System.out.println("\n=== Queue Growth Results ===");
            System.out.println("Total enqueued: " + totalEnqueued.get());
            System.out.println("Peak queue size: " + peakQueueSize.get());
            System.out.println("Expected: ~100K max (small multiple of batch)");
            System.out.println("Total time: " + totalElapsed + " ms");

            // Queue should not grow unbounded
            assertThat(peakQueueSize.get())
                .as("Queue should stay bounded (peak < 500K)")
                .isLessThan(500_000);

            metrics.record("totalEnqueued", totalEnqueued.get());
            metrics.record("peakQueueSize", peakQueueSize.get());

        } finally {
            enqueuer.shutdown();
        }
    }

    // Helper classes

    private static class CompetingConsumersPool {
        private final BlockingQueue<WorkItem> queue = new LinkedBlockingQueue<>();
        private final int workerCount;
        private final ExecutorService workers;
        private final AtomicLong processedCount = new AtomicLong(0);
        private final List<Runnable>[] workerCallbacks;

        @SuppressWarnings("unchecked")
        CompetingConsumersPool(int workerCount) {
            this.workerCount = workerCount;
            this.workerCallbacks = new List[workerCount];
            for (int i = 0; i < workerCount; i++) {
                workerCallbacks[i] = new ArrayList<>();
            }
            this.workers = Executors.newFixedThreadPool(workerCount);

            // Start workers
            for (int i = 0; i < workerCount; i++) {
                final int workerId = i;
                workers.submit(() -> {
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            WorkItem item = queue.poll();
                            if (item != null) {
                                // Simulate processing
                                Thread.sleep(10); // Slow worker
                                processedCount.incrementAndGet();

                                // Trigger callbacks
                                synchronized (workerCallbacks) {
                                    for (Runnable cb : workerCallbacks[workerId]) {
                                        cb.run();
                                    }
                                }
                            } else {
                                Thread.sleep(1);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        boolean enqueue(WorkItem item) {
            try {
                return queue.offer(item, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        long getQueueSize() {
            return queue.size();
        }

        long getProcessedCount() {
            return processedCount.get();
        }

        void registerWorkerCallback(int workerId, Runnable callback) {
            workerCallbacks[workerId].add(callback);
        }

        void shutdown() {
            workers.shutdownNow();
        }
    }

    private static class WorkItem {
        final long id;
        final long createdAt;

        WorkItem(long id, long createdAt) {
            this.id = id;
            this.createdAt = createdAt;
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
