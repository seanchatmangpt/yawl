/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.bridge;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.lifecycle.OtpInstallationVerifier;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Performance benchmarks for Three-Domain Native Bridge latency.
 *
 * <p>This test class focuses on measuring the latency characteristics of the
 * Three-Domain Native Bridge Pattern, specifically targeting:
 * <ul>
 *   <li>QLever: <10ns latency</li>
 *   <li>Erlang RPC: 5-20µs latency</li>
 *   <li>NIF: <1µs latency</li>
 * </ul>
 *
 * @see <a href="../processmining/ErlangBridge.java">ErlangBridge Implementation</a>
 */
@Tag("benchmark")
@Tag("performance")
@Tag("latency")
class BridgeLatencyBenchmark {

    private ErlangTestNode testNode;
    private ErlangBridge bridge;
    private BenchmarkConfig benchmarkConfig;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping benchmarks");

        // Initialize benchmark configuration
        benchmarkConfig = new BenchmarkConfig();
        benchmarkConfig.load();

        // Start Erlang node
        testNode = ErlangTestNode.start();
        testNode.setBenchmarkMode(true);
        testNode.awaitReady();

        // Connect bridge
        bridge = ErlangBridge.connect(testNode.NODE_NAME, testNode.COOKIE);
    }

    @AfterEach
    void cleanup() {
        if (bridge != null) {
            bridge.close();
        }
        if (testNode != null) {
            testNode.setBenchmarkMode(false);
            testNode.close();
        }
    }

    // =========================================================================
    // Test 1: QLever Layer Latency (JVM Domain)
    // =========================================================================

    /**
     * Verries QLever native bridge latency targets: <10ns.
     */
    @Test
    @DisplayName("Benchmark: QLever → under 10ns latency")
    void benchmark_qlever_under10nsLatency()
            throws ErlangConnectionException, ErlangRpcException {
        // Warmup
        for (int i = 0; i < 100; i++) {
            bridge.qleverLaunchCase("warmup_" + i);
        }

        // Measure QLever latency
        int iterations = 10000;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            String caseId = bridge.qleverLaunchCase("qlever_test_" + i);
            long end = System.nanoTime();

            assertNotNull(caseId, "Case ID should not be null");
            latencies[i] = end - start;
        }

        // Calculate statistics
        BenchmarkResults results = calculateStatistics(latencies);
        System.out.println("QLever Latency Benchmark Results:");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Min: " + results.minNs + "ns");
        System.out.println("  Max: " + results.maxNs + "ns");
        System.out.println("  Mean: " + results.meanNs + "ns");
        System.out.println("  Median: " + results.medianNs + "ns");
        System.out.println("  P95: " + results.p95Ns + "ns");
        System.out.println("  P99: " + results.p99Ns + "ns");
        System.out.println("  P99.9: " + results.p999Ns + "ns");

        // Target: <10ns average, <100ns P99
        assertTrue(results.meanNs < 10,
            "Mean QLever latency should be under 10ns (got " + results.meanNs + "ns)");
        assertTrue(results.p99Ns < 100,
            "P99 QLever latency should be under 100ns (got " + results.p99Ns + "ns)");
    }

    /**
     * Verries QLever throughput under load.
     */
    @Test
    @DisplayName("Benchmark: QLever throughput → 1M operations/sec")
    void benchmark_qleverThroughput_1MOperationsPerSec()
            throws ErlangConnectionException, ErlangRpcException {
        int numOperations = 1_000_000;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = IntStream.range(0, numOperations)
            .mapToObj(i -> executor.submit(() ->
                bridge.qleverLaunchCase("throughput_test_" + i)))
            .collect(Collectors.toList());

        long start = System.nanoTime();
        futures.forEach(f -> {
            try {
                assertNotNull(f.get(1, TimeUnit.SECONDS), "Case ID should not be null");
            } catch (Exception e) {
                fail("Operation should succeed: " + e.getMessage());
            }
        });
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;
        double throughput = numOperations * 1000.0 / durationMs;

        System.out.println("QLever Throughput Benchmark:");
        System.out.println("  Operations: " + numOperations);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Throughput: " + throughput + " ops/sec");

        // Target: >1M ops/sec
        assertTrue(throughput > 1_000_000,
            "QLever throughput should exceed 1M ops/sec (got " + throughput + " ops/sec)");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    // =========================================================================
    // Test 2: Erlang RPC Latency (Boundary A: JVM↔BEAM)
    // =========================================================================

    /**
     * Verries Erlang RPC latency targets: 5-20µs.
     */
    @Test
    @DisplayName("Benchmark: Erlang RPC → 5-20µs latency")
    void benchmark_erlangRpc_5To20usLatency()
            throws ErlangConnectionException, ErlangRpcException {
        // Warmup
        for (int i = 0; i < 100; i++) {
            bridge.launchCase("warmup_" + i);
        }

        // Measure Erlang RPC latency
        int iterations = 10000;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            String caseId = bridge.launchCase("rpc_test_" + i);
            long end = System.nanoTime();

            assertNotNull(caseId, "Case ID should not be null");
            latencies[i] = end - start;
        }

        // Calculate statistics
        BenchmarkResults results = calculateStatistics(latencies);
        System.out.println("Erlang RPC Latency Benchmark Results:");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Min: " + (results.minNs / 1000.0) + "µs");
        System.out.println("  Max: " + (results.maxNs / 1000.0) + "µs");
        System.out.println("  Mean: " + (results.meanNs / 1000.0) + "µs");
        System.out.println("  Median: " + (results.medianNs / 1000.0) + "µs");
        System.out.println("  P95: " + (results.p95Ns / 1000.0) + "µs");
        System.out.println("  P99: " + (results.p99Ns / 1000.0) + "µs");
        System.out.println("  P99.9: " + (results.p999Ns / 1000.0) + "µs");

        // Target: 5-20µs mean, <100µs P99
        assertTrue(results.meanNs >= 5000 && results.meanNs <= 20000,
            "Mean Erlang RPC latency should be 5-20µs (got " + (results.meanNs / 1000.0) + "µs)");
        assertTrue(results.p99Ns < 100_000,
            "P99 Erlang RPC latency should be under 100µs (got " + (results.p99Ns / 1000.0) + "µs)");
    }

    /**
     * Verries Erlang RPC throughput.
     */
    @Test
    @DisplayName("Benchmark: Erlang RPC throughput → 100K operations/sec")
    void benchmark_erlangRpcThroughput_100KOperationsPerSec()
            throws ErlangConnectionException, ErlangRpcException {
        int numOperations = 100_000;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = IntStream.range(0, numOperations)
            .mapToObj(i -> executor.submit(() ->
                bridge.launchCase("rpc_throughput_test_" + i)))
            .collect(Collectors.toList());

        long start = System.nanoTime();
        futures.forEach(f -> {
            try {
                assertNotNull(f.get(1, TimeUnit.SECONDS), "Case ID should not be null");
            } catch (Exception e) {
                fail("Operation should succeed: " + e.getMessage());
            }
        });
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;
        double throughput = numOperations * 1000.0 / durationMs;

        System.out.println("Erlang RPC Throughput Benchmark:");
        System.out.println("  Operations: " + numOperations);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Throughput: " + throughput + " ops/sec");

        // Target: >100K ops/sec
        assertTrue(throughput > 100_000,
            "Erlang RPC throughput should exceed 100K ops/sec (got " + throughput + " ops/sec)");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    /**
     * Verries concurrent Erlang RPC performance.
     */
    @Test
    @DisplayName("Benchmark: Erlang RPC concurrent → linear scalability")
    void benchmark_erlangRpcConcurrent_linearScalability()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        int[] threadCounts = {1, 5, 10, 20, 50};
        int operationsPerThread = 1000;
        List<BenchmarkResults> results = new ArrayList<>();

        for (int threadCount : threadCounts) {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<Future<String>> futures = new ArrayList<>();

            long start = System.nanoTime();

            // Submit operations
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    try {
                        for (int i = 0; i < operationsPerThread; i++) {
                            String caseId = bridge.launchCase(
                                "concurrent_rpc_test_" + threadId + "_" + i);
                            assertNotNull(caseId, "Case ID should not be null");
                        }
                    } finally {
                        latch.countDown();
                    }
                }));
            }

            // Wait for completion
            assertTrue(latch.await(30, TimeUnit.SECONDS),
                "All operations should complete within 30 seconds");

            long end = System.nanoTime();
            long durationNs = end - start;
            double throughput = (threadCount * operationsPerThread) * 1_000_000_000.0 / durationNs;

            BenchmarkResults result = new BenchmarkResults();
            result.totalNs = durationNs;
            result.operations = threadCount * operationsPerThread;
            result.throughput = throughput;
            results.add(result);

            System.out.println("Erlang RPC Concurrent Benchmark (Threads: " + threadCount + "):");
            System.out.println("  Operations: " + (threadCount * operationsPerThread));
            System.out.println("  Duration: " + (durationNs / 1_000_000.0) + "ms");
            System.out.println("  Throughput: " + throughput + " ops/sec");

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
                "Executor should shutdown cleanly");
        }

        // Verify linear scalability
        verifyLinearScalability(results, threadCounts);
    }

    // =========================================================================
    // Test 3: NIF Latency (Boundary B: BEAM↔Rust)
    // =========================================================================

    /**
     * Verries NIF latency targets: <1µs.
     */
    @Test
    @DisplayName("Benchmark: NIF → under 1µs latency")
    void benchmark_nif_under1usLatency()
            throws ErlangConnectionException, ErlangRpcException {
        // Warmup
        for (int i = 0; i < 100; i++) {
            bridge.nifProcessData("warmup_" + i, new byte[10]);
        }

        // Measure NIF latency
        int iterations = 10000;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            String result = bridge.nifProcessData("nif_test_" + i, new byte[10]);
            long end = System.nanoTime();

            assertNotNull(result, "NIF result should not be null");
            latencies[i] = end - start;
        }

        // Calculate statistics
        BenchmarkResults results = calculateStatistics(latencies);
        System.out.println("NIF Latency Benchmark Results:");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Min: " + (results.minNs / 1000.0) + "µs");
        System.out.println("  Max: " + (results.maxNs / 1000.0) + "µs");
        System.out.println("  Mean: " + (results.meanNs / 1000.0) + "µs");
        System.out.println("  Median: " + (results.medianNs / 1000.0) + "µs");
        System.out.println("  P95: " + (results.p95Ns / 1000.0) + "µs");
        System.out.println("  P99: " + (results.p99Ns / 1000.0) + "µs");
        System.out.println("  P99.9: " + (results.p999Ns / 1000.0) + "µs");

        // Target: <1µs average, <10µs P99
        assertTrue(results.meanNs < 1000,
            "Mean NIF latency should be under 1µs (got " + (results.meanNs / 1000.0) + "µs)");
        assertTrue(results.p99Ns < 10_000,
            "P99 NIF latency should be under 10µs (got " + (results.p99Ns / 1000.0) + "µs)");
    }

    /**
     * Verries NIF memory bandwidth.
     */
    @Test
    @DisplayName("Benchmark: NIF memory bandwidth → 1GB/sec")
    void benchmark_nifMemoryBandwidth_1GBPerSec()
            throws ErlangConnectionException, ErlangRpcException {
        // Generate large datasets
        int[] dataSizes = {1024, 1024*1024, 10*1024*1024}; // 1KB, 1MB, 10MB
        long totalBytes = 0;
        long totalTimeNs = 0;

        for (int dataSize : dataSizes) {
            byte[] data = new byte[dataSize];

            long start = System.nanoTime();
            String result = bridge.nifProcessData("bandwidth_test_" + dataSize, data);
            long end = System.nanoTime();

            assertNotNull(result, "NIF result should not be null");

            long durationNs = end - start;
            double bandwidth = (dataSize * 8.0) / (durationNs / 1_000_000_000.0); // bits per second

            totalBytes += dataSize;
            totalTimeNs += durationNs;

            System.out.println("NIF Memory Bandwidth (" + dataSize + " bytes):");
            System.out.println("  Duration: " + (durationNs / 1_000_000.0) + "ms");
            System.out.println("  Bandwidth: " + (bandwidth / 1_000_000_000.0) + " Gbps");
        }

        // Calculate overall bandwidth
        double overallBandwidth = (totalBytes * 8.0) / (totalTimeNs / 1_000_000_000.0);

        System.out.println("Overall NIF Memory Bandwidth:");
        System.out.println("  Total bytes: " + totalBytes);
        System.out.println("  Total time: " + (totalTimeNs / 1_000_000_000.0) + "s");
        System.out.println("  Overall bandwidth: " + (overallBandwidth / 1_000_000_000.0) + " Gbps");

        // Target: >1 GB/sec (8 Gbps)
        assertTrue(overallBandwidth > 8_000_000_000,
            "NIF memory bandwidth should exceed 8 Gbps (got " + (overallBandwidth / 1_000_000_000.0) + " Gbps)");
    }

    // =========================================================================
    // Test 4: End-to-End Latency
    // =========================================================================

    /**
     * Verries end-to-end latency from JVM through all domains.
     */
    @Test
    @DisplayName("Benchmark: End-to-end → under 100ms for complete process mining")
    void benchmark_endToEnd_under100msForCompleteProcessMining()
            throws ErlangConnectionException, ErlangRpcException {
        // Generate test event log
        List<Map<String, Object>> eventLog = generateEventLog(1000);

        // Measure end-to-end latency
        int iterations = 100;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();

            // QLever → JVM
            String caseId = bridge.qleverLaunchCase("end_to_end_" + i);

            // JVM → BEAM
            String processId = bridge.launchCase(caseId);

            // BEAM → NIF
            ConformanceResult result = bridge.nifCheckConformance(eventLog, getProcessSpec());

            long end = System.nanoTime();

            assertNotNull(caseId, "Case ID should not be null");
            assertNotNull(processId, "Process ID should not be null");
            assertNotNull(result, "Conformance result should not be null");

            latencies[i] = end - start;
        }

        // Calculate statistics
        BenchmarkResults results = calculateStatistics(latencies);
        System.out.println("End-to-End Process Mining Benchmark Results:");
        System.out.println("  Iterations: " + iterations);
        System.out.println("  Min: " + (results.minNs / 1_000_000.0) + "ms");
        System.out.println("  Max: " + (results.maxNs / 1_000_000.0) + "ms");
        System.out.println("  Mean: " + (results.meanNs / 1_000_000.0) + "ms");
        System.out.println("  Median: " + (results.medianNs / 1_000_000.0) + "ms");
        System.out.println("  P95: " + (results.p95Ns / 1_000_000.0) + "ms");
        System.out.println("  P99: " + (results.p99Ns / 1_000_000.0) + "ms");

        // Target: <100ms average, <500ms P99
        assertTrue(results.meanNs < 100_000_000,
            "Mean end-to-end latency should be under 100ms (got " + (results.meanNs / 1_000_000.0) + "ms)");
        assertTrue(results.p99Ns < 500_000_000,
            "P99 end-to-end latency should be under 500ms (got " + (results.p99Ns / 1_000_000.0) + "ms)");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private BenchmarkResults calculateStatistics(long[] latencies) {
        // Sort latencies for percentile calculations
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);

        long min = sorted[0];
        long max = sorted[sorted.length - 1];
        long sum = Arrays.stream(sorted).sum();
        double mean = sum / (double) sorted.length;

        // Median
        long median;
        if (sorted.length % 2 == 0) {
            median = (sorted[sorted.length/2] + sorted[sorted.length/2 - 1]) / 2;
        } else {
            median = sorted[sorted.length/2];
        }

        // Percentiles
        long p95 = sorted[(int) (0.95 * sorted.length)];
        long p99 = sorted[(int) (0.99 * sorted.length)];
        long p999 = sorted[(int) (0.999 * sorted.length)];

        BenchmarkResults results = new BenchmarkResults();
        results.minNs = min;
        results.maxNs = max;
        results.meanNs = (long) mean;
        results.medianNs = median;
        results.p95Ns = p95;
        results.p99Ns = p99;
        results.p999Ns = p999;
        results.totalNs = sum;
        results.operations = latencies.length;

        return results;
    }

    private void verifyLinearScalability(List<BenchmarkResults> results, int[] threadCounts) {
        if (results.size() < 2) return;

        System.out.println("Scalability Analysis:");
        for (int i = 0; i < results.size(); i++) {
            double efficiency = (results.get(i).throughput / threadCounts[i]) /
                              (results.get(0).throughput / threadCounts[0]);
            System.out.printf("  Threads: %2d → Efficiency: %.1f%%%n",
                threadCounts[i], efficiency * 100);
        }

        // Verify good scalability (efficiency > 0.8 for all thread counts)
        for (int i = 0; i < results.size(); i++) {
            double baseline = results.get(0).throughput / threadCounts[0];
            double current = results.get(i).throughput / threadCounts[i];
            double efficiency = current / baseline;

            assertTrue(efficiency > 0.8,
                String.format("Scalability should be >80%% at %d threads (got %.1f%%)",
                    threadCounts[i], efficiency * 100));
        }
    }

    private List<Map<String, Object>> generateEventLog(int size) {
        List<Map<String, Object>> eventLog = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            eventLog.add(Map.of(
                "activity", "Task_" + (i % 3 + 1),
                "timestamp", "2024-01-01T10:" + (i/60) + ":" + (i%60) + "Z",
                "case_id", "case_" + (i/100 + 1),
                "duration", 1000 + (i % 500)
            ));
        }
        return eventLog;
    }

    private Map<String, Object> getProcessSpec() {
        return Map.of(
            "name", "Benchmark Process",
            "start_task", "Start",
            "end_task", "End",
            "tasks", List.of("Task_1", "Task_2", "Task_3"),
            "edges", List.of(
                Map.of("from", "Start", "to", "Task_1"),
                Map.of("from", "Task_1", "to", "Task_2"),
                Map.of("from", "Task_2", "to", "Task_3"),
                Map.of("from", "Task_3", "to", "End")
            )
        );
    }

    /**
     * Benchmark configuration
     */
    private static class BenchmarkConfig {
        private boolean enableProfiling = false;
        private int warmupIterations = 100;
        private int benchmarkIterations = 10000;
        private int timeoutSeconds = 30;

        public void load() {
            // Load from system properties or config file
            enableProfiling = Boolean.parseBoolean(
                System.getProperty("benchmark.enableProfiling", "false"));
            warmupIterations = Integer.parseInt(
                System.getProperty("benchmark.warmupIterations", "100"));
            benchmarkIterations = Integer.parseInt(
                System.getProperty("benchmark.benchmarkIterations", "10000"));
            timeoutSeconds = Integer.parseInt(
                System.getProperty("benchmark.timeoutSeconds", "30"));
        }

        public boolean isEnableProfiling() { return enableProfiling; }
        public int getWarmupIterations() { return warmupIterations; }
        public int getBenchmarkIterations() { return benchmarkIterations; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
    }

    /**
     * Benchmark results container
     */
    private static class BenchmarkResults {
        long minNs;
        long maxNs;
        long meanNs;
        long medianNs;
        long p95Ns;
        long p99Ns;
        long p999Ns;
        long totalNs;
        int operations;
        double throughput;

        public double getLatencyMicroseconds() {
            return meanNs / 1000.0;
        }

        public double getThroughputPerSecond() {
            return operations * 1_000_000_000.0 / totalNs;
        }
    }
}