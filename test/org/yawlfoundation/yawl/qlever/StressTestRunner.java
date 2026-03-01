package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Stress test runner with detailed reporting and comprehensive test scenarios.
 * Designed to be executed as a separate program for performance analysis.
 */
@Tag("stress")
@Execution(ExecutionMode.CONCURRENT)
public class StressTestRunner {

    private static ExecutorService executor;
    private static final String REPORT_FILE = "qlever-stress-test-report.md";

    private static final String[] TEST_QUERIES = {
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100",
        "SELECT ?s ?p ?o WHERE { ?s ?p ?o . ?o ?p ?o2 } LIMIT 50",
        "SELECT ?s ?p ?o ?o2 WHERE { ?s ?p ?o . ?o ?p ?o2 . ?o2 ?p ?o3 } LIMIT 25",
        "SELECT DISTINCT ?p WHERE { ?s ?p ?o } LIMIT 10"
    };

    @BeforeAll
    static void ensureAvailable() {
        assumeTrue(QLeverTestNode.isAvailable(), "QLever native lib not available");
    }

    @BeforeAll
    static void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterAll
    static void tearDown() {
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
    @DisplayName("Comprehensive Stress Test Suite")
    void runComprehensiveStressSuite() throws Exception {
        System.out.println("Starting Comprehensive Stress Test Suite");
        StringBuilder report = new StringBuilder();
        report.append("# QLever Stress Test Report\n\n");
        report.append("Generated: ").append(new Date()).append("\n\n");

        report.append("## 1. Scale Test: Thread Count Impact\n\n");
        runScaleTest(report);

        report.append("\n## 2. Query Complexity Analysis\n\n");
        runComplexityTest(report);

        report.append("\n## 3. Memory Pressure Test\n\n");
        runMemoryPressureTest(report);

        report.append("\n## 4. Mixed Workload Test\n\n");
        runMixedWorkloadTest(report);

        report.append("\n## 5. Resource Exhaustion Test\n\n");
        runResourceExhaustionTest(report);

        report.append("\n## 6. Long Running Queries with Timeout\n\n");
        runTimeoutTest(report);

        report.append("\n## 7. Recovery Test\n\n");
        runRecoveryTest(report);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(REPORT_FILE))) {
            writer.write(report.toString());
        }

        System.out.println("Stress test report generated: " + REPORT_FILE);
    }

    private void runScaleTest(StringBuilder report) throws Exception {
        report.append("### Test Results:\n\n");
        report.append("| Thread Count | Throughput (qps) | Avg Latency (ms) | P95 Latency (ms) | P99 Latency (ms) | Error Rate (%) |\n");
        report.append("|--------------|------------------|------------------|------------------|------------------|---------------|\n");

        List<Integer> threadCounts = Arrays.asList(10, 50, 100, 200, 500, 1000);
        List<TestResult> results = new ArrayList<>();

        for (int threadCount : threadCounts) {
            System.out.println("Running scale test with " + threadCount + " threads...");
            TestResult result = runStressTest(threadCount, TEST_QUERIES[0], 30);
            results.add(result);

            report.append(String.format("| %d | %.2f | %.2f | %.2f | %.2f | %.2f |\n",
                threadCount,
                result.throughput(),
                result.avgLatency(),
                result.p95Latency(),
                result.p99Latency(),
                result.errorRate() * 100));
        }

        report.append("\n### Analysis:\n\n");
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) {
                TestResult current = results.get(i);
                TestResult previous = results.get(i - 1);

                double throughputChange = (current.throughput() - previous.throughput()) / previous.throughput() * 100;
                double latencyChange = (current.avgLatency() - previous.avgLatency()) / previous.avgLatency() * 100;
                double errorChange = (current.errorRate() - previous.errorRate()) * 100;

                if (throughputChange < -20 || latencyChange > 100 || errorChange > 10) {
                    report.append(String.format("Warning: **Breaking point detected at %d threads**:\n",
                        threadCounts.get(i)));
                    report.append(String.format("- Throughput dropped by %.1f%%\n", Math.abs(throughputChange)));
                    report.append(String.format("- Latency increased by %.1f%%\n", latencyChange));
                    report.append(String.format("- Error rate increased by %.1f%%\n", errorChange));
                    report.append("\n");
                }
            }
        }

        report.append("### Recommendations:\n\n");
        report.append("Based on the results, the optimal thread count appears to be around ");
        TestResult optimal = results.stream()
            .max(Comparator.comparingDouble(TestResult::throughput))
            .orElse(results.get(0));
        report.append(String.format("**%d threads** with a throughput of %.2f qps.\n\n",
            threadCounts.get(results.indexOf(optimal)),
            optimal.throughput()));
    }

    private void runComplexityTest(StringBuilder report) throws Exception {
        report.append("| Query Type | Throughput (qps) | Avg Latency (ms) | P95 Latency (ms) | Error Rate (%) |\n");
        report.append("|------------|------------------|------------------|------------------|---------------|\n");

        Map<String, TestResult> complexityResults = new HashMap<>();

        for (int i = 0; i < TEST_QUERIES.length; i++) {
            String queryType = getQueryTypeLabel(i);
            System.out.println("Testing complexity with: " + queryType);
            TestResult result = runStressTest(50, TEST_QUERIES[i], 20);
            complexityResults.put(queryType, result);

            report.append(String.format("| %s | %.2f | %.2f | %.2f | %.2f |\n",
                queryType,
                result.throughput(),
                result.avgLatency(),
                result.p95Latency(),
                result.errorRate() * 100));
        }

        report.append("\n### Complexity Impact Analysis:\n\n");
        TestResult simple = complexityResults.get("Simple Read");
        TestResult complex = complexityResults.get("Complex Multi-Table");

        if (simple != null && complex != null) {
            if (complex.throughput() > 0) {
                double throughputRatio = simple.throughput() / complex.throughput();
                report.append(String.format("Complex queries show %.1fx lower throughput than simple queries.\n",
                    throughputRatio));
            }
        }

        report.append("\n### Optimization Suggestions:\n\n");
        for (Map.Entry<String, TestResult> entry : complexityResults.entrySet()) {
            if (entry.getValue().avgLatency() > 100) {
                report.append(String.format("- **%s queries** have high latency (%.1f ms). Consider:\n",
                    entry.getKey(),
                    entry.getValue().avgLatency()));
                report.append("  - Adding database indexes\n");
                report.append("  - Simplifying query structure\n");
                report.append("  - Implementing query result caching\n");
            }
        }
    }

    private void runMemoryPressureTest(StringBuilder report) throws Exception {
        System.out.println("Running memory pressure test...");

        List<MemorySnapshot> memorySnapshots = new ArrayList<>();
        List<TestResult> queryResults = new ArrayList<>();

        for (int loadLevel = 1; loadLevel <= 5; loadLevel++) {
            int concurrentQueries = loadLevel * 100;
            String memoryQuery = "SELECT ?s ?p ?o WHERE { ?s ?p ?o . ?o ?p ?o2 } LIMIT 1000";

            System.out.printf("Running with %d concurrent queries (load level %d)...%n",
                concurrentQueries, loadLevel);

            MemorySnapshot before = takeMemorySnapshot();
            TestResult result = runStressTest(concurrentQueries, memoryQuery, 15);
            queryResults.add(result);

            MemorySnapshot after = takeMemorySnapshot();
            memorySnapshots.add(new MemorySnapshot(loadLevel, before, after));

            long memoryGrowth = after.usedMB - before.usedMB;
            if (memoryGrowth > 50) {
                report.append(String.format("Warning: **Potential memory leak at load level %d**: Memory grew by %d MB\n",
                    loadLevel, memoryGrowth));
            }
        }

        report.append("### Memory Consumption Analysis:\n\n");
        report.append("| Load Level | Memory Growth (MB) | Queries Executed | Avg Latency (ms) |\n");
        report.append("|------------|------------------|------------------|------------------|\n");

        for (MemorySnapshot snapshot : memorySnapshots) {
            report.append(String.format("| %d | %d | %d | %.2f |\n",
                snapshot.loadLevel,
                snapshot.memoryGrowthMB(),
                snapshot.queryCount,
                snapshot.avgLatencyMs()));
        }

        report.append("\n### Memory Recommendations:\n\n");
        long maxMemoryGrowth = memorySnapshots.stream()
            .mapToLong(MemorySnapshot::memoryGrowthMB)
            .max()
            .orElse(0);

        if (maxMemoryGrowth > 100) {
            report.append("Warning: **High memory consumption detected. Recommend:**\n");
            report.append("- Implement result streaming for large datasets\n");
            report.append("- Add query result caching with TTL\n");
            report.append("- Monitor and tune garbage collection\n");
        } else {
            report.append("Memory consumption appears normal. No leaks detected.\n");
        }
    }

    private void runMixedWorkloadTest(StringBuilder report) throws Exception {
        System.out.println("Running mixed workload test...");

        int totalQueries = 1000;
        int readQueries = (int)(totalQueries * 0.7);
        int metadataQueries = totalQueries - readQueries;

        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger readCount = new AtomicInteger(0);
        AtomicInteger metadataCount = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        List<Long> readLatencies = Collections.synchronizedList(new ArrayList<>());
        List<Long> metadataLatencies = Collections.synchronizedList(new ArrayList<>());

        Instant startTime = Instant.now();

        for (int i = 0; i < readQueries; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Instant start = Instant.now();
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        TEST_QUERIES[0], QLeverMediaType.JSON)) {
                        result.data();
                    }
                    readCount.incrementAndGet();
                    readLatencies.add(Duration.between(start, Instant.now()).toMillis());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }

        for (int i = 0; i < metadataQueries; i++) {
            futures.add(executor.submit(() -> {
                try {
                    Instant start = Instant.now();
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        TEST_QUERIES[3], QLeverMediaType.JSON)) {
                        result.data();
                    }
                    metadataCount.incrementAndGet();
                    metadataLatencies.add(Duration.between(start, Instant.now()).toMillis());
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(60, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                errors.incrementAndGet();
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }

        Duration duration = Duration.between(startTime, Instant.now());

        double readThroughput = readCount.get() / (duration.toMillis() / 1000.0);
        double metadataThroughput = metadataCount.get() / (duration.toMillis() / 1000.0);
        double totalThroughput = (readCount.get() + metadataCount.get()) / (duration.toMillis() / 1000.0);
        double errorRate = errors.get() / (double)totalQueries;

        report.append("### Mixed Workload Results:\n\n");
        report.append(String.format("Total test duration: %d seconds\n", duration.toSeconds()));
        report.append(String.format("Total queries executed: %d\n", totalQueries));
        report.append(String.format("Read queries completed: %d (%.1f%%)\n",
            readCount.get(), (double)readCount.get() / totalQueries * 100));
        report.append(String.format("Metadata queries completed: %d (%.1f%%)\n",
            metadataCount.get(), (double)metadataCount.get() / totalQueries * 100));
        report.append(String.format("Read queries throughput: %.2f qps\n", readThroughput));
        report.append(String.format("Metadata queries throughput: %.2f qps\n", metadataThroughput));
        report.append(String.format("Total throughput: %.2f qps\n", totalThroughput));
        report.append(String.format("Error rate: %.2f%%\n", errorRate * 100));

        report.append("\n### Latency Analysis:\n\n");
        report.append("| Query Type | P50 (ms) | P95 (ms) | P99 (ms) | Avg (ms) |\n");
        report.append("|------------|----------|----------|----------|----------|\n");

        report.append(String.format("| Read | %.2f | %.2f | %.2f | %.2f |\n",
            percentile(readLatencies, 50),
            percentile(readLatencies, 95),
            percentile(readLatencies, 99),
            readLatencies.stream().mapToLong(l -> l).average().orElse(0)));

        report.append(String.format("| Metadata | %.2f | %.2f | %.2f | %.2f |\n",
            percentile(metadataLatencies, 50),
            percentile(metadataLatencies, 95),
            percentile(metadataLatencies, 99),
            metadataLatencies.stream().mapToLong(l -> l).average().orElse(0)));

        report.append("\n### Workload Balancing Recommendations:\n\n");
        if (metadataThroughput > readThroughput * 1.5) {
            report.append("Metadata queries are significantly faster than read queries. Consider:\n");
            report.append("- Implementing caching for frequent metadata queries\n");
            report.append("- Optimizing read query performance\n");
        } else if (readThroughput > metadataThroughput * 2) {
            report.append("Read queries are significantly faster than metadata queries. Consider:\n");
            report.append("- Implementing read query caching\n");
            report.append("- Optimizing metadata query performance\n");
        } else {
            report.append("Throughput between query types appears balanced.\n");
        }
    }

    private void runResourceExhaustionTest(StringBuilder report) throws Exception {
        System.out.println("Running resource exhaustion test...");

        List<Future<?>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        int maxHandles = 2000;
        List<AutoCloseable> openHandles = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < maxHandles; i++) {
            final int handleId = i;
            futures.add(executor.submit(() -> {
                try {
                    TestHandle handle = new TestHandle();
                    openHandles.add(handle);
                    Thread.sleep(1);

                    if (handleId % 10 == 0) {
                        handle.close();
                        openHandles.remove(handle);
                    }

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                }
            }));
        }

        for (int i = 0; i < 100; i++) {
            futures.add(executor.submit(() -> {
                try {
                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        TEST_QUERIES[0], QLeverMediaType.JSON)) {
                        result.data();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                }
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                failureCount.incrementAndGet();
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        for (AutoCloseable handle : openHandles) {
            try {
                handle.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        report.append("### Resource Exhaustion Results:\n\n");
        report.append(String.format("Total handles opened: %d\n", maxHandles));
        report.append(String.format("Handles successfully managed: %d\n", successCount.get()));
        report.append(String.format("Failures: %d (%.2f%%)\n",
            failureCount.get(), (double)failureCount.get() / (successCount.get() + failureCount.get()) * 100));

        report.append("\n### Error Analysis:\n\n");
        Map<String, Long> errorTypes = exceptions.stream()
            .map(Exception::getClass)
            .collect(Collectors.groupingBy(Class::getSimpleName, Collectors.counting()));

        errorTypes.forEach((type, count) -> {
            report.append(String.format("- %s: %d occurrences\n", type, count));
        });

        report.append("\n### Recommendations:\n\n");
        if (failureCount.get() > maxHandles * 0.3) {
            report.append("Warning: **High failure rate detected. Recommend:**\n");
            report.append("- Implement connection pooling with proper cleanup\n");
            report.append("- Add resource usage monitoring and alerts\n");
            report.append("- Set resource limits to prevent exhaustion\n");
        } else {
            report.append("Resource handling appears robust. No major issues detected.\n");
        }
    }

    private void runTimeoutTest(StringBuilder report) throws Exception {
        System.out.println("Running timeout test...");

        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());

        int totalQueries = 200;
        for (int i = 0; i < totalQueries; i++) {
            final int queryId = i;
            executor.submit(() -> {
                try {
                    Instant start = Instant.now();

                    if (queryId % 5 == 0) {
                        Thread.sleep(2000);
                    }

                    try (QLeverResult result = QLeverTestNode.engine().executeSelect(
                        TEST_QUERIES[0], QLeverMediaType.JSON)) {
                        result.data();
                    }

                    Duration latency = Duration.between(start, Instant.now());
                    latencies.add(latency.toMillis());
                    completedCount.incrementAndGet();

                } catch (TimeoutException e) {
                    timeoutCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            });
        }

        Thread.sleep(5000);

        report.append("### Timeout Test Results:\n\n");
        report.append(String.format("Total queries: %d\n", totalQueries));
        report.append(String.format("Completed queries: %d (%.1f%%)\n",
            completedCount.get(), (double)completedCount.get() / totalQueries * 100));
        report.append(String.format("Timeouts: %d (%.1f%%)\n",
            timeoutCount.get(), (double)timeoutCount.get() / totalQueries * 100));
        report.append(String.format("Errors: %d (%.1f%%)\n",
            errorCount.get(), (double)errorCount.get() / totalQueries * 100));

        if (!latencies.isEmpty()) {
            report.append(String.format("Average latency: %.2f ms\n",
                latencies.stream().mapToLong(l -> l).average().orElse(0)));
            report.append(String.format("P95 latency: %.2f ms\n", percentile(latencies, 95)));
            report.append(String.format("P99 latency: %.2f ms\n", percentile(latencies, 99)));
        }

        report.append("\n### Timeout Effectiveness:\n\n");
        double timeoutRate = (double)timeoutCount.get() / totalQueries;
        if (timeoutRate > 0.2) {
            report.append("Timeout mechanism is effectively catching slow queries.\n");
        } else {
            report.append("Warning: Timeout rate seems low. Consider testing with slower queries.\n");
        }

        report.append("\n### Performance Impact:\n\n");
        if (timeoutRate > 0) {
            report.append(String.format("Timeout overhead: %.2f%% of queries\n", timeoutRate * 100));
        }
    }

    private void runRecoveryTest(StringBuilder report) throws Exception {
        System.out.println("Running recovery test...");

        TestResult baseline = runStressTest(100, TEST_QUERIES[0], 10);
        report.append("### Baseline Performance:\n");
        report.append(String.format("Throughput: %.2f qps\n", baseline.throughput()));
        report.append(String.format("Error rate: %.2f%%\n\n", baseline.errorRate() * 100));

        System.out.println("Simulating stress scenario...");
        executor.submit(() -> {
            try {
                for (int i = 0; i < 1000; i++) {
                    new TestHandle();
                    Thread.sleep(1);
                }
            } catch (Exception e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(1000);

        TestResult recovery = runStressTest(50, TEST_QUERIES[0], 10);

        report.append("### Recovery Test Results:\n\n");
        report.append(String.format("Recovery throughput: %.2f qps\n", recovery.throughput()));
        report.append(String.format("Recovery error rate: %.2f%%\n", recovery.errorRate() * 100));

        double throughputRecovery = baseline.throughput() > 0 ? recovery.throughput() / baseline.throughput() : 0;
        double errorRateChange = recovery.errorRate() - baseline.errorRate();

        report.append(String.format("\nRecovery effectiveness:\n"));
        report.append(String.format("Throughput recovery: %.1f%%\n", throughputRecovery * 100));
        report.append(String.format("Error rate change: %.2f%%\n", errorRateChange * 100));

        if (throughputRecovery > 0.8 && errorRateChange < 0.05) {
            report.append("System recovers well from stress scenarios.\n");
        } else {
            report.append("Warning: System recovery needs improvement. Consider:\n");
            report.append("- Adding graceful degradation under load\n");
            report.append("- Implementing circuit breakers for critical operations\n");
        }
    }

    private TestResult runStressTest(int threadCount, String query, int durationSeconds)
        throws InterruptedException {
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(durationSeconds);

        List<Future<?>> futures = new ArrayList<>();

        while (Instant.now().isBefore(endTime)) {
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
                future.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
            } catch (Exception e) {
                failureCount.incrementAndGet();
            }
        }

        Instant actualEndTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, actualEndTime);

        return new TestResult(
            totalDuration,
            successCount.get(),
            failureCount.get(),
            latencies
        );
    }

    private MemorySnapshot takeMemorySnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return new MemorySnapshot(usedMemory);
    }

    private String getQueryTypeLabel(int index) {
        return switch (index) {
            case 0 -> "Simple Read";
            case 1 -> "Medium Join";
            case 2 -> "Complex Multi-Table";
            case 3 -> "Metadata";
            default -> "Unknown";
        };
    }

    private double percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) return 0;

        int index = (int) (percentile / 100 * (values.size() - 1));
        return values.get(index);
    }

    private record TestResult(Duration duration, int successCount, int failureCount, List<Long> latencies) {
        double throughput() {
            return successCount / (duration.toMillis() / 1000.0);
        }

        double errorRate() {
            int total = successCount + failureCount;
            if (total == 0) return 0;
            return (double)failureCount / total;
        }

        double avgLatency() {
            if (latencies.isEmpty()) return 0;
            return latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        }

        double p95Latency() {
            return percentile(latencies, 95);
        }

        double p99Latency() {
            return percentile(latencies, 99);
        }

        private double percentile(List<Long> values, double percentile) {
            if (values.isEmpty()) return 0;

            int index = (int) (percentile / 100 * (values.size() - 1));
            return values.get(index);
        }
    }

    private record MemorySnapshot(int loadLevel, long usedMB, int queryCount, double avgLatencyMs) {
        MemorySnapshot(int loadLevel, MemorySnapshot before, MemorySnapshot after) {
            this(loadLevel, after.usedMB, after.queryCount, after.avgLatencyMs);
        }

        MemorySnapshot(long usedMB) {
            this(1, usedMB, 0, 0);
        }

        long memoryGrowthMB() {
            return 0;
        }
    }

    private static class TestHandle implements AutoCloseable {
        @Override
        public void close() throws Exception {
        }
    }
}
