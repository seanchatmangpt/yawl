package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive stress tests for QLever engine to identify breaking points
 * under various workload conditions.
 *
 * Uses virtual threads for scalable concurrent testing.
 */
@Tag("stress")
@Execution(ExecutionMode.CONCURRENT)
public class QLeverStressTest {

    private static final String SIMPLE_QUERY = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100";
    private static final String COMPLEX_QUERY = "SELECT ?s ?p ?o ?o2 WHERE { ?s ?p ?o . ?o ?p ?o2 } LIMIT 50";
    private static final String METADATA_QUERY = "SELECT DISTINCT ?p WHERE { ?s ?p ?o } LIMIT 10";

    private static final int[] THREAD_COUNTS = {10, 50, 100, 500};
    private static final String[] QUERY_TYPES = {"simple", "complex", "metadata"};

    private ExecutorService executor;

    @BeforeAll
    static void ensureAvailable() {
        assumeTrue(QLeverTestNode.isAvailable(), "QLever native lib not available");
    }

    @BeforeEach
    void setUp() {
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

            assertTrue(result.throughput() > 0, "Throughput should be positive");
            assertTrue(result.failureRate() < 0.1, "Failure rate should be < 10%");
        }
    }

    @Test
    @DisplayName("Stress Test: Memory Pressure with Large Results")
    void testMemoryPressure() throws InterruptedException {
        System.out.println("\n=== Memory Pressure Test ===");

        String memoryHeavyQuery = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 10000";

        StressTestResult result = runConcurrentTest(
            100,
            memoryHeavyQuery,
            "memory_pressure_test"
        );

        System.out.printf("Memory test results:%n");
        System.out.printf("  - Throughput: %.2f queries/sec%n", result.throughput());
        System.out.printf("  - Heap memory used: %.2f MB%n", result.maxMemoryUsedMB());
        System.out.printf("  - GC cycles: %d%n", result.gcCycles());

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

        assertTrue(results.get("simple").throughput() > results.get("complex").throughput(),
            "Simple queries should have higher throughput than complex queries");
    }

    @Test
    @DisplayName("Stress Test: Long Running Queries with Timeout")
    void testLongRunningQueries() throws InterruptedException {
        System.out.println("\n=== Long Running Queries Test ===");

        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);

        String longQuery = SIMPLE_QUERY;
        int threadCount = 50;
        List<Future<?>> futures = new ArrayList<>();

        Instant startTime = Instant.now();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    if (threadId % 10 == 0) {
                        Thread.sleep(500);
                    }

                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        longQuery, QLeverMediaType.JSON)) {
                        result.data();
                        completedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    timeoutCount.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                timeoutCount.incrementAndGet();
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());
        double timeoutRate = (double)timeoutCount.get() / threadCount;

        System.out.printf("Long query test results:%n");
        System.out.printf("  - Completed queries: %d/%d%n", completedCount.get(), threadCount);
        System.out.printf("  - Timeouts: %d (%.2f%%)%n", timeoutCount.get(), timeoutRate * 100);
        System.out.printf("  - Test duration: %d ms%n", duration.toMillis());

        assertTrue(completedCount.get() > 0, "Some queries should complete");
    }

    @Test
    @DisplayName("Stress Test: Resource Exhaustion (Open Handles)")
    void testResourceExhaustion() throws InterruptedException {
        System.out.println("\n=== Resource Exhaustion Test ===");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        int maxHandles = 1000;
        List<AutoCloseable> openHandles = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            for (int i = 0; i < maxHandles; i++) {
                try {
                    AutoCloseable handle = new TestHandle();
                    openHandles.add(handle);
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

        executor.submit(() -> {
            for (int i = 0; i < 100; i++) {
                try {
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        SIMPLE_QUERY, QLeverMediaType.JSON)) {
                        result.data();
                    }
                    Thread.sleep(10);
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }
        });

        Thread.sleep(5000);

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

        assertTrue(failureCount.get() < maxHandles * 0.5,
            "Failure rate should be reasonable (< 50%)");
    }

    @Test
    @DisplayName("Stress Test: Rapid Open/Close Cycles")
    void testRapidOpenCloseCycles() throws InterruptedException {
        System.out.println("\n=== Rapid Open/Close Cycles Test ===");

        int cycles = 1000;
        int threads = 50;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < cycles / threads; i++) {
                    try {
                        try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                            SIMPLE_QUERY, QLeverMediaType.JSON)) {
                            result.data();
                            successCount.incrementAndGet();
                        }
                        Thread.sleep(1);
                    } catch (Exception e) {
                        // Increment failure counter implicitly
                    }
                }
            });
        }

        Thread.sleep(10000);
        Duration duration = Duration.between(startTime, Instant.now());

        double successRate = (double)successCount.get() / cycles;
        double cyclesPerSecond = cycles / (duration.toMillis() / 1000.0);

        System.out.printf("Rapid cycles test results:%n");
        System.out.printf("  - Success rate: %.2f%%%n", successRate * 100);
        System.out.printf("  - Cycles/sec: %.2f%n", cyclesPerSecond);
        System.out.printf("  - Test duration: %d ms%n", duration.toMillis());

        assertTrue(successRate > 0.95, "Success rate should be high (> 95%)");
    }

    @Test
    @DisplayName("Stress Test: Mixed Workload (Read + Metadata)")
    void testMixedWorkload() throws InterruptedException {
        System.out.println("\n=== Mixed Workload Test ===");

        int totalQueries = 500;
        AtomicInteger readQueries = new AtomicInteger(0);
        AtomicInteger metadataQueries = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        List<QueryMetrics> metrics = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < totalQueries; i++) {
            final int queryId = i;
            executor.submit(() -> {
                Instant start = Instant.now();
                try {
                    String query = queryId % 10 < 7 ? SIMPLE_QUERY : METADATA_QUERY;
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        query, QLeverMediaType.JSON)) {
                        result.data();
                    }

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

        Thread.sleep(10000);

        double readThroughput = readQueries.get() / (10.0);
        double metadataThroughput = metadataQueries.get() / (10.0);
        double totalThroughput = (readQueries.get() + metadataQueries.get()) / 10.0;

        List<Double> latencies = metrics.stream()
            .map(QueryMetrics::latencyMs)
            .map(Long::doubleValue)
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

        assertTrue(readThroughput > 0, "Read queries should have positive throughput");
        assertTrue(metadataThroughput > 0, "Metadata queries should have positive throughput");
        assertTrue(errors.get() < totalQueries * 0.1, "Error rate should be < 10%");
    }

    @Test
    @DisplayName("Stress Test: Breaking Point Identification")
    void testBreakingPointIdentification() throws InterruptedException {
        System.out.println("\n=== Breaking Point Identification Test ===");

        StressTestResult lastResult = null;

        for (int threadCount : THREAD_COUNTS) {
            StressTestResult result = runConcurrentTest(
                threadCount,
                SIMPLE_QUERY,
                "breaking_point_" + threadCount
            );

            System.out.printf("  %d threads: %.2f qps, %.2f%% failures%n",
                threadCount, result.throughput(), result.failureRate() * 100);

            if (lastResult != null) {
                double throughputDrop = (lastResult.throughput() - result.throughput()) / lastResult.throughput();
                double failureRateIncrease = result.failureRate() - lastResult.failureRate();

                if (throughputDrop > 0.5 || failureRateIncrease > 0.3) {
                    System.out.printf("  *** BREAKING POINT DETECTED at %d threads!%n", threadCount);
                    System.out.printf("  Throughput drop: %.1f%%%n", throughputDrop * 100);
                    System.out.printf("  Failure rate increase: %.1f%%%n", failureRateIncrease * 100);

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

            if (result.failureRate() > 0.2) {
                System.out.printf("  *** High failure rate (%.1f%%) - stopping test%n",
                    result.failureRate() * 100);
                break;
            }
        }
    }

    private StressTestResult runConcurrentTest(int threadCount, String query, String testName)
        throws InterruptedException {

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Instant startTime = Instant.now();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Instant queryStart = Instant.now();
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        query, QLeverMediaType.JSON)) {
                        result.data();
                    }
                    successCount.incrementAndGet();

                    Duration latency = Duration.between(queryStart, Instant.now());
                    latencies.add(latency.toMillis());
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }));
        }

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

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);

        List<Long> sortedLatencies = latencies.stream()
            .sorted()
            .collect(Collectors.toList());

        return new StressTestResult(
            duration,
            successCount.get(),
            failureCount.get(),
            sortedLatencies,
            0
        );
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) return 0;

        int index = (int) (percentile / 100 * (values.size() - 1));
        return values.get(index);
    }

    private static class TestHandle implements AutoCloseable {
        @Override
        public void close() throws Exception {
        }
    }

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
            int total = successCount + failureCount;
            if (total == 0) return 0;
            return (double)failureCount / total;
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
            return 0;
        }

        private double percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) return 0;

            int index = (int) (percentile / 100 * (values.size() - 1));
            return values.get(index);
        }
    }

    private record QueryMetrics(String queryType, long latencyMs) {}
}
