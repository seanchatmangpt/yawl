package org.yawlfoundation.yawl.performance;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Load testing suite for YAWL Engine.
 *
 * Uses YEngine singleton - @Execution(ExecutionMode.SAME_THREAD) applied below.
 *
 * Simulates production-like load scenarios:
 * - Sustained load (50 concurrent users, 5 minutes)
 * - Burst load (100 concurrent users, 1 minute)
 * - Stress test (increasing load until failure)
 * - Soak test (moderate load, 30 minutes)
 * 
 * @author YAWL Performance Team
 * @version 6.0
 * @since 2026-02-16
 */
@Execution(ExecutionMode.SAME_THREAD)  // Uses YEngine singleton
public class LoadTestSuite {

    private YEngine engine;
    private YSpecification spec;

    @BeforeEach
    public void setUp() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        
        URL fileURL = getClass().getResource("../engine/ImproperCompletion.xml");
        if (fileURL == null) {
            fileURL = getClass().getResource("/test/org/yawlfoundation/yawl/engine/ImproperCompletion.xml");
        }
        
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            spec = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
            engine.loadSpecification(spec);
        } else {
            fail("Could not load test specification");
        }
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
        }
    }
    
    /**
     * LOAD TEST 1: Sustained Load
     * 50 concurrent users for 5 minutes
     * Target: > 99% success rate, p95 latency < 1000ms
     */
    @Test
    public void testSustainedLoad() throws Exception {
        System.out.println("\n=== LOAD TEST 1: Sustained Load ===");
        System.out.println("Configuration: 50 users, 5 minutes");
        
        int concurrentUsers = 50;
        int durationSeconds = 300; // 5 minutes
        
        LoadTestResult result = runLoadTest(concurrentUsers, durationSeconds);
        
        System.out.println("\nResults (coordinated omission corrected):");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Latency avg:       " + result.avgLatencyMs + " ms");
        System.out.println("  Latency p50:       " + result.p50Ms + " ms");
        System.out.println("  Latency p99:       " + result.p99Ms + " ms");
        System.out.println("  Latency p99.9:     " + result.p999Ms + " ms");
        System.out.println("  Latency max:       " + result.maxLatencyMs + " ms");
        System.out.println("  Status:            " + (result.getSuccessRate() > 99.0 ? "✓ PASS" : "✗ FAIL"));

        assertTrue("Success rate below 99% (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 99.0);
    }

    /**
     * LOAD TEST 2: Burst Load
     * 100 concurrent users for 1 minute
     * Target: > 95% success rate
     */
    @Test
    public void testBurstLoad() throws Exception {
        System.out.println("\n=== LOAD TEST 2: Burst Load ===");
        System.out.println("Configuration: 100 users, 1 minute");

        int concurrentUsers = 100;
        int durationSeconds = 60;

        LoadTestResult result = runLoadTest(concurrentUsers, durationSeconds);

        System.out.println("\nResults (coordinated omission corrected):");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Latency avg:       " + result.avgLatencyMs + " ms");
        System.out.println("  Latency p50:       " + result.p50Ms + " ms");
        System.out.println("  Latency p99:       " + result.p99Ms + " ms");
        System.out.println("  Latency p99.9:     " + result.p999Ms + " ms");
        System.out.println("  Latency max:       " + result.maxLatencyMs + " ms");
        System.out.println("  Status:            " + (result.getSuccessRate() > 95.0 ? "✓ PASS" : "✗ FAIL"));

        assertTrue("Success rate below 95% (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 95.0);
    }
    
    /**
     * LOAD TEST 3: Ramp-up Test
     * Start with 10 users, ramp to 50 over 2 minutes
     * Target: Success rate remains > 99%
     */
    @Test
    public void testRampUp() throws Exception {
        System.out.println("\n=== LOAD TEST 3: Ramp-up Test ===");
        System.out.println("Configuration: 10 → 50 users over 2 minutes");
        
        int startUsers = 10;
        int endUsers = 50;
        int durationSeconds = 120;
        
        LoadTestResult result = runRampUpTest(startUsers, endUsers, durationSeconds);
        
        System.out.println("\nResults (coordinated omission corrected):");
        System.out.println("  Total requests:    " + result.totalRequests);
        System.out.println("  Successful:        " + result.successfulRequests);
        System.out.println("  Failed:            " + result.failedRequests);
        System.out.println("  Success rate:      " + String.format("%.2f%%", result.getSuccessRate()));
        System.out.println("  Duration:          " + result.durationMs + " ms");
        System.out.println("  Throughput:        " + String.format("%.1f", result.getThroughput()) + " req/sec");
        System.out.println("  Latency p50:       " + result.p50Ms + " ms");
        System.out.println("  Latency p99:       " + result.p99Ms + " ms");
        System.out.println("  Status:            " + (result.getSuccessRate() > 99.0 ? "✓ PASS" : "✗ FAIL"));

        assertTrue("Success rate below 99% during ramp-up (" + result.getSuccessRate() + "%)",
                   result.getSuccessRate() > 99.0);
    }
    
    /**
     * Run a load test with fixed concurrent users using an open-model producer.
     *
     * <p><b>Coordinated omission correction</b>: each worker maintains a
     * {@code scheduled} timestamp that advances by {@code intervalMs} every
     * iteration, regardless of actual completion time. Latency is measured as
     * {@code actual_completion - scheduled_start}, not {@code actual_completion
     * - actual_start}. When the system is slow and the worker falls behind
     * schedule, the overdue time is absorbed into the next measured latency
     * rather than silently dropped. This produces honest tail latency numbers.
     *
     * <p>Without this correction, a system that blocks for 500ms is recorded as
     * having 500ms latency but the 490ms of "missing" idle time vanishes from
     * the distribution, making p99 appear 10-100× better than reality.</p>
     *
     * @param concurrentUsers number of parallel worker threads
     * @param durationSeconds test duration
     * @return result with coordinated-omission-corrected percentile latencies
     */
    private LoadTestResult runLoadTest(int concurrentUsers, int durationSeconds) throws Exception {
        // One virtual thread per user — no pool cap, maps naturally to Loom model.
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        // Thread-safe latency collection for percentile computation.
        // At 50 users * 300s / 10ms interval ≈ 1.5M entries — well within heap.
        ConcurrentLinkedQueue<Long> allLatencies = new ConcurrentLinkedQueue<>();

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);
        // Target inter-request interval per worker (open-model: rate-based, not closed-loop).
        long intervalMs = 10L;

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentUsers; i++) {
            Future<?> future = executor.submit(() -> {
                // Each worker maintains its own schedule. When an operation takes
                // longer than intervalMs, the next request fires immediately (no
                // additional sleep) — preserving the intended arrival rate rather
                // than silently widening the gap.
                long scheduled = System.currentTimeMillis();

                while (scheduled < endTime) {
                    try {
                        YIdentifier caseId = engine.startCase(
                            spec.getSpecificationID(), null, null, null,
                            new YLogDataItemList(), null, false);

                        if (caseId != null) {
                            successfulRequests.incrementAndGet();
                            engine.cancelCase(caseId);
                        } else {
                            failedRequests.incrementAndGet();
                        }

                    } catch (Exception e) {
                        failedRequests.incrementAndGet();
                    }

                    long now = System.currentTimeMillis();
                    // Coordinated omission: measure from *scheduled* time, not actual start.
                    // If the system was slow, this latency absorbs the queuing time.
                    long latency = now - scheduled;
                    totalLatency.addAndGet(latency);
                    totalRequests.incrementAndGet();
                    allLatencies.add(latency);

                    long currentMax = maxLatency.get();
                    while (latency > currentMax) {
                        if (maxLatency.compareAndSet(currentMax, latency)) break;
                        currentMax = maxLatency.get();
                    }

                    scheduled += intervalMs;
                    long sleepMs = scheduled - System.currentTimeMillis();
                    if (sleepMs > 0) {
                        try {
                            Thread.sleep(sleepMs);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    // If sleepMs <= 0, we are behind schedule — fire immediately.
                }
            });

            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // Worker interrupted or failed — continue collecting results.
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long actualDuration = System.currentTimeMillis() - startTime;

        // Sort collected latencies for percentile computation.
        List<Long> sortedLatencies = new ArrayList<>(allLatencies);
        Collections.sort(sortedLatencies);

        LoadTestResult result = new LoadTestResult();
        result.totalRequests = totalRequests.get();
        result.successfulRequests = successfulRequests.get();
        result.failedRequests = failedRequests.get();
        result.durationMs = actualDuration;
        result.avgLatencyMs = totalRequests.get() > 0
            ? totalLatency.get() / totalRequests.get() : 0;
        result.maxLatencyMs = maxLatency.get();
        result.p50Ms  = percentile(sortedLatencies, 50.0);
        result.p99Ms  = percentile(sortedLatencies, 99.0);
        result.p999Ms = percentile(sortedLatencies, 99.9);

        return result;
    }
    
    /**
     * Run a ramp-up load test with coordinated omission correction.
     * Workers are added incrementally; each uses the open-model pattern.
     */
    private LoadTestResult runRampUpTest(int startUsers, int endUsers, int durationSeconds)
            throws Exception {

        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicLong maxLatency = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> allLatencies = new ConcurrentLinkedQueue<>();

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000L);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        int userIncrement = endUsers - startUsers;
        long rampInterval = (durationSeconds * 1000L) / userIncrement;

        for (int i = 0; i < startUsers; i++) {
            futures.add(submitWorker(executor, endTime, totalRequests,
                successfulRequests, failedRequests, totalLatency, maxLatency, allLatencies));
        }

        for (int i = 0; i < userIncrement; i++) {
            Thread.sleep(rampInterval);
            futures.add(submitWorker(executor, endTime, totalRequests,
                successfulRequests, failedRequests, totalLatency, maxLatency, allLatencies));
            System.out.println("  Current users: " + (startUsers + i + 1));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                // Continue collecting results.
            }
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long actualDuration = System.currentTimeMillis() - startTime;
        List<Long> sortedLatencies = new ArrayList<>(allLatencies);
        Collections.sort(sortedLatencies);

        LoadTestResult result = new LoadTestResult();
        result.totalRequests = totalRequests.get();
        result.successfulRequests = successfulRequests.get();
        result.failedRequests = failedRequests.get();
        result.durationMs = actualDuration;
        result.avgLatencyMs = totalRequests.get() > 0
            ? totalLatency.get() / totalRequests.get() : 0;
        result.maxLatencyMs = maxLatency.get();
        result.p50Ms  = percentile(sortedLatencies, 50.0);
        result.p99Ms  = percentile(sortedLatencies, 99.0);
        result.p999Ms = percentile(sortedLatencies, 99.9);

        return result;
    }

    /**
     * Submit an open-model worker task with coordinated omission correction.
     * Latency is measured from the scheduled start time, not actual start time.
     */
    private Future<?> submitWorker(ExecutorService executor, long endTime,
                                   AtomicInteger totalRequests,
                                   AtomicInteger successfulRequests,
                                   AtomicInteger failedRequests,
                                   AtomicLong totalLatency,
                                   AtomicLong maxLatency,
                                   ConcurrentLinkedQueue<Long> allLatencies) {
        return executor.submit(() -> {
            long scheduled = System.currentTimeMillis();
            long intervalMs = 10L;

            while (scheduled < endTime) {
                try {
                    YIdentifier caseId = engine.startCase(
                        spec.getSpecificationID(), null, null, null,
                        new YLogDataItemList(), null, false);

                    if (caseId != null) {
                        successfulRequests.incrementAndGet();
                        engine.cancelCase(caseId);
                    } else {
                        failedRequests.incrementAndGet();
                    }

                } catch (Exception e) {
                    failedRequests.incrementAndGet();
                }

                long now = System.currentTimeMillis();
                long latency = now - scheduled;
                totalLatency.addAndGet(latency);
                totalRequests.incrementAndGet();
                allLatencies.add(latency);

                long currentMax = maxLatency.get();
                while (latency > currentMax) {
                    if (maxLatency.compareAndSet(currentMax, latency)) break;
                    currentMax = maxLatency.get();
                }

                scheduled += intervalMs;
                long sleepMs = scheduled - System.currentTimeMillis();
                if (sleepMs > 0) {
                    try {
                        Thread.sleep(sleepMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        });
    }
    
    /**
     * Result container for load tests.
     *
     * Latency fields use coordinated omission correction: latency is measured as
     * {@code actual_completion_time - scheduled_start_time} rather than
     * {@code actual_completion_time - actual_start_time}. When the system is slow
     * and a worker falls behind schedule, the "missing" intervals are captured in
     * the next measurement rather than silently omitted. This produces honest tail
     * latency numbers; the difference at p99.9 can be 10-100× under back-pressure.
     */
    private static class LoadTestResult {
        int totalRequests;
        int successfulRequests;
        int failedRequests;
        long durationMs;
        long avgLatencyMs;
        long maxLatencyMs;
        // Percentiles from coordinated-omission-corrected latency distribution
        long p50Ms;
        long p99Ms;
        long p999Ms;

        double getSuccessRate() {
            if (totalRequests == 0) return 0.0;
            return (successfulRequests * 100.0) / totalRequests;
        }

        double getThroughput() {
            if (durationMs == 0) return 0.0;
            return (totalRequests * 1000.0) / durationMs;
        }
    }

    /**
     * Compute a percentile value from a list of latencies.
     * List must be sorted in ascending order before calling.
     *
     * @param sortedLatencies sorted latency values in ms
     * @param percentile      0.0–100.0
     * @return latency at the given percentile, or 0 if list is empty
     */
    private static long percentile(List<Long> sortedLatencies, double percentile) {
        if (sortedLatencies.isEmpty()) return 0L;
        int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.size()) - 1;
        return sortedLatencies.get(Math.max(0, Math.min(index, sortedLatencies.size() - 1)));
    }
}
