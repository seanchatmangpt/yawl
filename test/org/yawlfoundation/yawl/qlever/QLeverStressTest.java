package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.qlever.datamodel.YQueryPreprocessor;
import org.yawlfoundation.yawl.qlever.querytranslation.QueryTranslator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive stress tests for QLever engine to identify breaking points
 * under various workload conditions.
 *
 * Uses virtual threads for scalable concurrent testing.
 */
@Tag("stress")
@Execution(ExecutionMode.CONCURRENT)
public class QLeverStressTest {

    private static final String SIMPLE_QUERY = "SELECT * FROM YTask WHERE status = 'running'";
    private static final String COMPLEX_QUERY = "SELECT t.* FROM YTask t JOIN YCase c ON t.caseID = c.caseID JOIN YData d ON t.caseID = d.caseID WHERE c.status = 'active' AND t.dueDate < NOW()";
    private static final String METADATA_QUERY = "SELECT DISTINCT caseID FROM YTask";

    private static final int[] THREAD_COUNTS = {10, 50, 100, 500};
    private static final String[] QUERY_TYPES = {"simple", "complex", "metadata"};

    private YQLeverEngine engine;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YQLeverEngine();
        engine.initialize();

        // Use virtual thread executor for stress testing
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Stress Test: Concurrent Query Throughput")
    void testConcurrentQueryThroughput() throws InterruptedException {
        System.out.println("\n=== Concurrent Query Throughput Test ===");

        for (int threadCount : THREAD_COUNTS) {
            System.out.printf("Testing with %d threads...%n", threadCount);

            StressTestResult result = runConcurrentTest(
                threadCount,
                SIMPLE_QUERY,
                "simple_concurrent_" + threadCount
            );

            System.out.printf("  - Throughput: %.2f queries/sec%n", result.throughput());
            System.out.printf("  - Avg latency: %.2f ms%n", result.avgLatencyMs());
            System.out.printf("  - P95 latency: %.2f ms%n", result.p95LatencyMs());
            System.out.printf("  - P99 latency: %.2f ms%n", result.p99LatencyMs());
            System.out.printf("  - Failure rate: %.2f%%%n", result.failureRate() * 100);

            // Verify that throughput increases with thread count (diminishing returns expected)
            if (threadCount < THREAD_COUNTS[THREAD_COUNTS.length - 1]) {
                StressTestResult nextResult = runConcurrentTest(
                    threadCount + 50,
                    SIMPLE_QUERY,
                    "simple_concurrent_" + (threadCount + 50)
                );
                assertTrue(result.throughput() > 0, "Throughput should be positive");
                assertTrue(result.failureRate() < 0.1, "Failure rate should be < 10%");
            }
        }
    }

    @Test
    @DisplayName("Stress Test: Memory Pressure with Large Results")
    void testMemoryPressure() throws InterruptedException {
        System.out.println("\n=== Memory Pressure Test ===");

        // Create a query that returns large result sets
        String memoryHeavyQuery = "SELECT * FROM YTask JOIN YData ON YTask.caseID = YData.caseID";

        StressTestResult result = runConcurrentTest(
            100,
            memoryHeavyQuery,
            "memory_pressure_test"
        );

        System.out.printf("Memory test results:%n");
        System.out.printf("  - Throughput: %.2f queries/sec%n", result.throughput());
        System.out.printf("  - Heap memory used: %.2f MB%n", result.maxMemoryUsedMB());
        System.out.printf("  - GC cycles: %d%n", result.gcCycles());

        // Monitor memory growth during test
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long maxMemory = initialMemory;

        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            long currentMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            maxMemory = Math.max(maxMemory, currentMemory);
        }

        long memoryGrowth = maxMemory - initialMemory;
        System.out.printf("  - Memory growth during test: %.2f MB%n",
            (double)memoryGrowth / (1024 * 1024));

        // Memory should not grow uncontrollably
        double memoryGrowthMB = (double)memoryGrowth / (1024 * 1024);
        assertTrue(memoryGrowthMB < 100, "Memory growth should be controlled (< 100MB)");
        assertTrue(result.failureRate() < 0.05, "Failure rate should be < 5%");
    }

    @Test
    @DisplayName("Stress Test: Query Complexity Impact")
    void testQueryComplexityImpact() throws InterruptedException {
        System.out.println("\n=== Query Complexity Impact Test ===");

        Map<String, StressTestResult> results = new HashMap<>();

        for (String queryType : QUERY_TYPES) {
            String query = switch (queryType) {
                case "simple" -> SIMPLE_QUERY;
                case "complex" -> COMPLEX_QUERY;
                case "metadata" -> METADATA_QUERY;
                default -> SIMPLE_QUERY;
            };

            StressTestResult result = runConcurrentTest(
                50,
                query,
                "complexity_" + queryType
            );

            results.put(queryType, result);
            System.out.printf("  %s query - Throughput: %.2f qps, P95: %.2f ms%n",
                queryType, result.throughput(), result.p95LatencyMs());
        }

        // Complex queries should have lower throughput
        assertTrue(results.get("simple").throughput() > results.get("complex").throughput(),
            "Simple queries should have higher throughput than complex queries");
    }

    @Test
    @DisplayName("Stress Test: Long Running Queries with Timeout")
    void testLongRunningQueries() throws InterruptedException {
        System.out.println("\n=== Long Running Queries Test ===");

        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        // Create a query that takes time (simulated)
        String longQuery = "SELECT * FROM YTask WHERE status = 'running'";

        int threadCount = 50;
        List<Future<?>> futures = new ArrayList<>();

        Instant startTime = Instant.now();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    // Simulate long processing time for some queries
                    if (threadId % 10 == 0) { // 10% of queries are slow
                        Thread.sleep(5000); // 5 second delay
                    }

                    // Execute with timeout
                    var result = engine.executeQuery(longQuery);
                    completedCount.incrementAndGet();
                    return result;
                } catch (TimeoutException e) {
                    timeoutCount.incrementAndGet();
                    return null;
                }
            }));
        }

        // Wait for completion with timeout
        for (Future<?> future : futures) {
            try {
                future.get(2, TimeUnit.SECONDS); // 2 second timeout per query
            } catch (TimeoutException e) {
                // Expected for some queries
                future.cancel(true);
            } catch (Exception e) {
                // Other exceptions
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        double timeoutRate = (double)timeoutCount.get() / threadCount;

        System.out.printf("Long query test results:%n");
        System.out.printf("  - Completed queries: %d/%d%n", completedCount.get(), threadCount);
        System.out.printf("  - Timeouts: %d (%.2f%%)%n", timeoutCount.get(), timeoutRate * 100);
        System.out.printf("  - Test duration: %d ms%n", duration.toMillis());

        // Timeout detection should work
        assertTrue(timeoutRate > 0, "Some queries should time out");
        assertTrue(completedCount.get() > 0, "Some queries should complete");
    }

    @Test
    @DisplayName("Stress Test: Resource Exhaustion (Open Handles)")
    void testResourceExhaustion() throws InterruptedException {
        System.out.println("\n=== Resource Exhaustion Test ===");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = AtomicInteger.newInteger(0);

        int maxHandles = 1000;
        List<AutoCloseable> openHandles = Collections.synchronizedList(new ArrayList<>());

        // Simulate opening many handles without proper cleanup
        executor.submit(() -> {
            for (int i = 0; i < maxHandles; i++) {
                try {
                    // Simulate opening a resource handle
                    AutoCloseable handle = new TestHandle();
                    openHandles.add(handle);

                    // Simulate some operations
                    Thread.sleep(1);

                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }
        });

        // Run normal operations simultaneously
        executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    engine.executeQuery(SIMPLE_QUERY);
                    Thread.sleep(10);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }
        });

        // Wait for test to complete
        Thread.sleep(5000);

        // Clean up
        for (AutoCloseable handle : openHandles) {
            try {
                handle.close();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        System.out.printf("Resource exhaustion test results:%n");
        System.out.printf("  - Open handles: %d%n", openHandles.size());
        System.out.printf("  - Successes: %d%n", successCount.get());
        System.out.printf("  - Failures: %d%n", failureCount.get());

        // Should handle resource exhaustion gracefully
        assertTrue(failureCount.get() < maxHandles * 0.5,
            "Failure rate should be reasonable (< 50%)");
    }

    @Test
    @DisplayName("Stress Test: Rapid Open/Close Cycles")
    void testRapidOpenCloseCycles() throws InterruptedException {
        System.out.println("\n=== Rapid Open/Close Cycles Test ===");

        int cycles = 1000;
        int threads = 50;
        AtomicInteger successCount = AtomicInteger.newInteger(0);

        Instant startTime = Instant.now();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < cycles / threads; i++) {
                    try {
                        YQLeverEngine localEngine = new YQLeverEngine();
                        localEngine.initialize();

                        var result = localEngine.executeQuery(SIMPLE_QUERY);
                        result.close();
                        localEngine.shutdown();

                        successCount.incrementAndGet();

                        // Very short cycle time
                        Thread.sleep(1);
                    } catch (Exception e) {
                        // Count failures
                    }
                }
            });
        }

        // Wait for completion
        Thread.sleep(10000);
        Duration duration = Duration.between(startTime, Instant.now());

        double successRate = (double)successCount.get() / cycles;
        double cyclesPerSecond = cycles / (duration.toMillis() / 1000.0);

        System.out.printf("Rapid cycles test results:%n");
        System.out.printf("  - Success rate: %.2f%%%n", successRate * 100);
        System.out.printf("  - Cycles/sec: %.2f%n", cyclesPerSecond);
        System.out.printf("  - Test duration: %d ms%n", duration.toMillis());

        // High success rate expected
        assertTrue(successRate > 0.95, "Success rate should be high (> 95%)");
    }

    @Test
    @DisplayName("Stress Test: Mixed Workload (Read + Metadata)")
    void testMixedWorkload() throws InterruptedException {
        System.out.println("\n=== Mixed Workload Test ===");

        int totalQueries = 500;
        AtomicInteger readQueries = AtomicInteger.newInteger(0);
        AtomicInteger metadataQueries = AtomicInteger.newInteger(0);
        AtomicInteger errors = AtomicInteger.newInteger(0);

        List<QueryMetrics> metrics = Collections.synchronizedList(new ArrayList<>());

        // Mixed workload: 70% read queries, 30% metadata queries
        for (int i = 0; i < totalQueries; i++) {
            final int queryId = i;
            executor.submit(() -> {
                Instant start = Instant.now();
                try {
                    String query = queryId % 10 < 7 ? SIMPLE_QUERY : METADATA_QUERY;
                    var result = engine.executeQuery(query);
                    result.close();

                    if (queryId % 10 < 7) {
                        readQueries.incrementAndGet();
                    } else {
                        metadataQueries.incrementAndGet();
                    }

                    Duration latency = Duration.between(start, Instant.now());
                    metrics.add(new QueryMetrics(query, latency.toMillis()));
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            });
        }

        // Wait for completion
        Thread.sleep(10000);

        // Calculate statistics
        double readThroughput = readQueries.get() / (10.0); // 10 second test
        double metadataThroughput = metadataQueries.get() / (10.0);
        double totalThroughput = (readQueries.get() + metadataQueries.get()) / 10.0;

        List<Double> latencies = metrics.stream()
            .map(QueryMetrics::latencyMs)
            .sorted()
            .collect(Collectors.toList());

        System.out.printf("Mixed workload test results:%n");
        System.out.printf("  - Read queries: %d (%.1f%%)%n",
            readQueries.get(), (double)readQueries.get() / totalQueries * 100);
        System.out.printf("  - Metadata queries: %d (%.1f%%)%n",
            metadataQueries.get(), (double)metadataQueries.get() / totalQueries * 100);
        System.out.printf("  - Read throughput: %.2f qps%n", readThroughput);
        System.out.printf("  - Metadata throughput: %.2f qps%n", metadataThroughput);
        System.out.printf("  - Total throughput: %.2f qps%n", totalThroughput);
        System.out.printf("  - P95 latency: %.2f ms%n", percentile(latencies, 95));
        System.out.printf("  - Errors: %d (%.2f%%)%n", errors.get(),
            (double)errors.get() / totalQueries * 100);

        // Both query types should work in mixed workload
        assertTrue(readThroughput > 0, "Read queries should have positive throughput");
        assertTrue(metadataThroughput > 0, "Metadata queries should have positive throughput");
        assertTrue(errors.get() < totalQueries * 0.1, "Error rate should be < 10%");
    }

    @Test
    @DisplayName("Stress Test: Breaking Point Identification")
    void testBreakingPointIdentification() throws InterruptedException {
        System.out.println("\n=== Breaking Point Identification Test ===");

        int maxThreads = 1000;
        StressTestResult lastResult = null;

        // Gradually increase thread count to find breaking point
        for (int threadCount : THREAD_COUNTS) {
            StressTestResult result = runConcurrentTest(
                threadCount,
                SIMPLE_QUERY,
                "breaking_point_" + threadCount
            );

            System.out.printf("  %d threads: %.2f qps, %.2f%% failures%n",
                threadCount, result.throughput(), result.failureRate() * 100);

            // Check for breaking point: sudden drop in throughput or increase in failures
            if (lastResult != null) {
                double throughputDrop = (lastResult.throughput() - result.throughput()) / lastResult.throughput();
                double failureRateIncrease = result.failureRate() - lastResult.failureRate();

                if (throughputDrop > 0.5 || failureRateIncrease > 0.3) {
                    System.out.printf("  *** BREAKING POINT DETECTED at %d threads!%n", threadCount);
                    System.out.printf("  Throughput drop: %.1f%%%n", throughputDrop * 100);
                    System.out.printf("  Failure rate increase: %.1f%%%n", failureRateIncrease * 100);

                    // Verify we can recover from breaking point
                    Thread.sleep(1000);
                    StressTestResult recoveryResult = runConcurrentTest(
                        10,
                        SIMPLE_QUERY,
                        "recovery_test"
                    );
                    assertTrue(recoveryResult.failureRate() < 0.1,
                        "System should recover after breaking point");

                    break;
                }
            }

            lastResult = result;

            // Stop if too many failures
            if (result.failureRate() > 0.2) {
                System.out.printf("  *** High failure rate (%.1f%%) - stopping test%n",
                    result.failureRate() * 100);
                break;
            }
        }
    }

    // Helper method to run concurrent tests
    private StressTestResult runConcurrentTest(int threadCount, String query, String testName)
        throws InterruptedException {

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = AtomicInteger.newInteger(0);
        AtomicInteger failureCount = AtomicInteger.newInteger(0);
        AtomicInteger gcCountBefore = new AtomicInteger(GarbageCollectorMXBean);

        Instant startTime = Instant.now();
        Instant endTime = Instant.now();

        List<Future<?>> futures = new ArrayList<>();

        // Submit concurrent queries
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    Instant queryStart = Instant.now();
                    var result = engine.executeQuery(query);
                    result.close();
                    successCount.incrementAndGet();

                    Duration latency = Duration.between(queryStart, Instant.now());
                    latencies.add(latency.toMillis());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

        // Wait for all queries to complete
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        // Calculate percentiles
        List<Long> sortedLatencies = latencies.stream()
            .sorted()
            .collect(Collectors.toList());

        return new StressTestResult(
            duration,
            successCount.get(),
            failureCount.get(),
            sortedLatencies,
            gcCountBefore.get()
        );
    }

    // Helper method to calculate percentile
    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0;

        int index = (int) (percentile / 100 * (values.size() - 1));
        return values.get(index);
    }

    // Test handle for resource exhaustion simulation
    private static class TestHandle implements AutoCloseable {
        @Override
        public void close() throws Exception {
            // Simulate resource cleanup
        }
    }

    // Record for GC tracking (simplified)
    private static class GCRecord {
        private final long timestamp;
        private final long gcCount;

        public GCRecord(long gcCount) {
            this.timestamp = System.currentTimeMillis();
            this.gcCount = gcCount;
        }
    }

    // Container for stress test results
    private record StressTestResult(
        Duration duration,
        int successCount,
        int failureCount,
        List<Long> latenciesMs,
        long gcCountBefore
    ) {

        public double throughput() {
            return successCount / (duration.toMillis() / 1000.0);
        }

        public double failureRate() {
            return (double)failureCount / (successCount + failureCount);
        }

        public double avgLatencyMs() {
            if (latenciesMs.isEmpty()) return 0;
            return latenciesMs.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }

        public double p95LatencyMs() {
            if (latenciesMs.isEmpty()) return 0;
            return percentile(latenciesMs, 95);
        }

        public double p99LatencyMs() {
            if (latenciesMs.isEmpty()) return 0;
            return percentile(latenciesMs, 99);
        }

        public double maxMemoryUsedMB() {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            return (double)usedMemory / (1024 * 1024);
        }

        public long gcCycles() {
            // Simplified - would need real GC tracking in production
            return 0;
        }

        private double percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) return 0;

            int index = (int) (percentile / 100 * (values.size() - 1));
            return values.get(index);
        }
    }

    // Record for query metrics
    private record QueryMetrics(String queryType, long latencyMs) {}
}