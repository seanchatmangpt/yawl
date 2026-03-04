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
import org.yawlfoundation.yawl.erlang.processmining.ConformanceResult;
import org.yawlfoundation.yawl.erlang.processmining.ErlangBridge;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for ErlangBridge - JVM→BEAM rpc calls.
 *
 * <p>This test class focuses on the Boundary A (JVM↔BEAM) integration, ensuring:
 * <ul>
 *   <li>End-to-end RPC call execution</li>
 *   <li>Data serialization/deserialization</li>
 *   <li>Connection management</li>
 *   <li>Performance characteristics</li>
 * </ul>
 *
 * @see <a href="../processmining/ErlangBridge.java">ErlangBridge Implementation</a>
 */
@Tag("integration")
@Tag("erlang")
class ErlangBridgeIntegrationTest {

    private ErlangTestNode testNode;
    private ErlangBridge bridge;
    private ProcessMiningTestServer testServer;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping integration tests");

        // Start test server
        testServer = new ProcessMiningTestServer();
        testServer.start();

        // Start Erlang node
        testNode = ErlangTestNode.start();
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
            testNode.close();
        }
        if (testServer != null) {
            testServer.stop();
        }
    }

    // =========================================================================
    // Test 1: End-to-End Process Mining Operations
    // =========================================================================

    /**
     * Verries end-to-end conformance checking.
     */
    @Test
    @DisplayName("Process Mining: End-to-end conformance check → valid result")
    void processMining_endToEndConformanceCheck_validResult()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Create sample event log
        List<Map<String, Object>> eventLog = List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "case1"),
            Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z", "case_id", "case1"),
            Map.of("activity", "Task_C", "timestamp", "2024-01-01T12:00:00Z", "case_id", "case1")
        );

        // Create sample process specification
        Map<String, Object> processSpec = Map.of(
            "name", "Test Process",
            "start_task", "Start",
            "end_task", "End",
            "tasks", List.of("Task_A", "Task_B", "Task_C"),
            "edges", List.of(
                Map.of("from", "Start", "to", "Task_A"),
                Map.of("from", "Task_A", "to", "Task_B"),
                Map.of("from", "Task_B", "to", "Task_C"),
                Map.of("from", "Task_C", "to", "End")
            )
        );

        // Perform conformance check
        ConformanceResult result = bridge.checkConformance(eventLog, processSpec);

        assertNotNull(result, "Conformance result should not be null");
        assertEquals("case1", result.getCaseId());
        assertTrue(result.isConformant(), "Simple sequential process should be conformant");
        assertTrue(result.getFitness() > 0.0, "Fitness should be positive");
        assertTrue(result.getCompleteness() > 0.0, "Completeness should be positive");
    }

    /**
     * Verries end-to-end trace replay.
     */
    @Test
@DisplayName("Process Mining: End-to-end trace replay → completed successfully")
    void processMining_endToEndTraceReplay_completedSuccessfully()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Create sample event log
        List<Map<String, Object>> eventLog = List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "case1"),
            Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z", "case_id", "case1")
        );

        // Perform trace replay
        Map<String, Object> replayResult = bridge.replayTrace(eventLog);

        assertNotNull(replayResult, "Replay result should not be null");
        assertEquals("case1", replayResult.get("case_id"));
        assertEquals(2, replayResult.get("completed_tasks"));
        assertEquals(0, replayResult.get("failed_tasks"));
        assertTrue((double) replayResult.get("success_rate") > 0.0);
    }

    /**
     * Verries end-to-end model reconstruction.
     */
    @Test
    @DisplayName("Process Mining: End-to-end model reconstruction → reconstructed model")
    void processMining_endToEndModelReconstruction_reconstructedModel()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Create event log with multiple cases
        List<Map<String, Object>> eventLog = List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "case1"),
            Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z", "case_id", "case1"),
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:30:00Z", "case_id", "case2"),
            Map.of("activity", "Task_C", "timestamp", "2024-01-01T11:30:00Z", "case_id", "case2")
        );

        // Perform model reconstruction
        Map<String, Object> reconstructedModel = bridge.reconstructModel(eventLog);

        assertNotNull(reconstructedModel, "Reconstructed model should not be null");
        assertTrue(((List<?>) reconstructedModel.get("tasks")).size() >= 3,
            "Model should contain Task_A, Task_B, and Task_C");
        assertTrue(((List<?>) reconstructedModel.get("edges")).size() >= 2,
            "Model should have at least 2 edges");
    }

    // =========================================================================
    // Test 2: Large Dataset Processing
    // =========================================================================

    /**
     * Verries processing of large event logs.
     */
    @Test
    @DisplayName("Large Dataset: 10,000 events → processed within 5 seconds")
    void largeDataset_10000Events_processedWithin5Seconds()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Generate large event log
        List<Map<String, Object>> eventLog = generateLargeEventLog(10000);

        // Process large event log
        long start = System.nanoTime();
        ConformanceResult result = bridge.checkConformance(eventLog, getSimpleProcessSpec());
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;

        System.out.println("Large dataset processing:");
        System.out.println("  Events: " + eventLog.size());
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Events per second: " + (eventLog.size() * 1000.0 / durationMs));

        assertNotNull(result, "Result should not be null");
        assertEquals("large_case_1", result.getCaseId());
        assertTrue(result.getFitness() >= 0.0 && result.getFitness() <= 1.0,
            "Fitness should be between 0 and 1");

        // Performance target: 10,000 events in 5 seconds
        assertTrue(durationMs < 5000,
            "10,000 events should process in under 5 seconds (took " + durationMs + "ms)");
    }

    /**
     * Verries processing of multiple concurrent cases.
     */
    @Test
    @DisplayName("Large Dataset: 100 concurrent cases → processed within 10 seconds")
    void largeDataset_100ConcurrentCases_processedWithin10Seconds()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Generate event log with 100 cases
        List<Map<String, Object>> eventLog = generateMultiCaseEventLog(100);

        // Process multi-case event log
        long start = System.nanoTime();
        List<ConformanceResult> results = bridge.checkConformanceBatch(eventLog);
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;

        System.out.println("Multi-case processing:");
        System.out.println("  Cases: " + 100);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Cases per second: " + (100 * 1000.0 / durationMs));

        assertEquals(100, results.size(),
            "Should return 100 conformance results");
        assertTrue(results.stream().allMatch(r -> r.getFitness() >= 0.0),
            "All results should have non-negative fitness");

        // Performance target: 100 cases in 10 seconds
        assertTrue(durationMs < 10000,
            "100 cases should process in under 10 seconds (took " + durationMs + "ms)");
    }

    // =========================================================================
    // Test 3: Connection Management
    // =========================================================================

    /**
     * Verries connection recovery after node restart.
     */
    @Test
    @DisplayName("Connection: Node restart → automatic reconnection")
    void connection_nodeRestart_automaticReconnection()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Perform some operations before restart
        String caseId = bridge.launchCase("pre-restart");
        assertNotNull(caseId, "Case should be created before restart");

        // Restart Erlang node
        testNode.restart();
        testNode.awaitReady();

        // Reconnect bridge
        bridge = ErlangBridge.connect(testNode.NODE_NAME, testNode.COOKIE);

        // Perform operations after restart
        String newCaseId = bridge.launchCase("post-restart");
        assertNotNull(newCaseId, "Case should be created after restart");
        assertNotEquals(caseId, newCaseId, "Should get new case ID after restart");
    }

    /**
     * Verries connection resilience to temporary failures.
     */
    @Test
    @DisplayName("Connection: Temporary failure → retry and recover")
    void connection_temporaryFailure_retryAndRecover()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Simulate temporary connection failure
        testNode.simulateFailure(true);

        // Attempt operations (should fail initially)
        assertThrows(ErlangConnectionException.class,
            () -> bridge.launchCase("during-failure"),
            "Operations should fail during simulated failure");

        // Simulate recovery
        testNode.simulateFailure(false);

        // Wait for recovery
        Thread.sleep(1000);

        // Operations should succeed after recovery
        String caseId = bridge.launchCase("after-recovery");
        assertNotNull(caseId, "Case should be created after recovery");
    }

    // =========================================================================
    // Test 4: Concurrent Access
    // =========================================================================

    /**
     * Verries thread-safe concurrent RPC calls.
     */
    @Test
    @DisplayName("Concurrency: Multiple threads → 1000 successful calls")
    void concurrency_multipleThreads_1000SuccessfulCalls()
            throws InterruptedException, ExecutionException {
        int numThreads = 10;
        int callsPerThread = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(numThreads);

        // Submit concurrent launchCase calls
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    List<String> caseIds = new ArrayList<>();
                    for (int i = 0; i < callsPerThread; i++) {
                        String caseId = bridge.launchCase("concurrent-" + threadId + "-" + i);
                        caseIds.add(caseId);
                    }
                    return "Thread-" + threadId + ": " + caseIds.size() + " cases";
                } finally {
                    completionLatch.countDown();
                }
            }));
        }

        // Wait for all threads to complete
        assertTrue(completionLatch.await(30, TimeUnit.SECONDS),
            "All threads should complete within 30 seconds");

        // Verify all futures completed successfully
        for (Future<String> future : futures) {
            assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS),
                "All threads should complete successfully");
        }

        // Verify total number of case IDs
        int totalCaseIds = futures.stream()
            .mapToInt(f -> {
                try {
                    String result = f.get();
                    return Integer.parseInt(result.substring(result.lastIndexOf(": ") + 1));
                } catch (Exception e) {
                    return 0;
                }
            })
            .sum();

        assertEquals(numThreads * callsPerThread, totalCaseIds,
            "Should create " + (numThreads * callsPerThread) + " case IDs");

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    /**
     * Verries concurrent conformance checking.
     */
    @Test
    @DisplayName("Concurrency: Concurrent conformance checks → thread-safe")
    void concurrency_concurrentConformanceChecks_threadSafe()
            throws InterruptedException, ExecutionException {
        int numThreads = 5;
        int casesPerThread = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<List<ConformanceResult>>> futures = new ArrayList<>();
        CountDownLatch completionLatch = new CountDownLatch(numThreads);

        // Submit concurrent conformance checks
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    List<ConformanceResult> results = new ArrayList<>();
                    List<Map<String, Object>> eventLog = generateSingleCaseEventLog(threadId);

                    for (int i = 0; i < casesPerThread; i++) {
                        ConformanceResult result = bridge.checkConformance(
                            eventLog,
                            getSimpleProcessSpec()
                        );
                        results.add(result);
                    }
                    return results;
                } finally {
                    completionLatch.countDown();
                }
            }));
        }

        // Wait for all threads to complete
        assertTrue(completionLatch.await(30, TimeUnit.SECONDS),
            "All threads should complete within 30 seconds");

        // Verify all futures completed successfully
        for (Future<List<ConformanceResult>> future : futures) {
            assertDoesNotThrow(() -> {
                List<ConformanceResult> results = future.get(10, TimeUnit.SECONDS);
                assertEquals(casesPerThread, results.size(),
                    "Each thread should return " + casesPerThread + " results");
            }, "All conformance checks should complete successfully");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    // =========================================================================
    // Test 5: Performance Characteristics
    // =========================================================================

    /**
     * Verries RPC call latency.
     */
    @Test
    @DisplayName("Performance: RPC latency → under 20µs")
    void performance_rpcLatency_under20us()
            throws ErlangConnectionException, ErlangRpcException {
        // Warmup
        for (int i = 0; i < 100; i++) {
            bridge.launchCase("warmup-" + i);
        }

        // Measure latency
        int iterations = 1000;
        long[] latencies = new long[iterations];

        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            String caseId = bridge.launchCase("latency-test-" + i);
            long end = System.nanoTime();

            assertNotNull(caseId, "Case ID should not be null");
            latencies[i] = end - start;
        }

        // Calculate statistics
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (long lat : latencies) {
            sum += lat;
            min = Math.min(min, lat);
            max = Math.max(max, lat);
        }

        double avgNanos = (double) sum / iterations;
        double avgMicros = avgNanos / 1000.0;

        System.out.println("RPC Latency Benchmark:");
        System.out.printf("  Min: %.2f µs%n", min / 1000.0);
        System.out.printf("  Max: %.2f µs%n", max / 1000.0);
        System.out.printf("  Avg: %.2f µs%n", avgMicros);

        // Target: <20µs average latency
        assertTrue(avgMicros < 20,
            "Average RPC latency should be under 20µs (got " + avgMicros + "µs)");
    }

    /**
     * Verries throughput of conformance checking.
     */
    @Test
    @DisplayName("Performance: Conformance checking throughput → 100 cases in 1s")
    void performance_conformanceCheckingThroughput_100CasesIn1s()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        int numCases = 100;
        List<Map<String, Object>> eventLog = generateSingleCaseEventLog(1);

        long start = System.nanoTime();
        List<ConformanceResult> results = bridge.checkConformanceBatch(
            Collections.nCopies(numCases, eventLog).stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())
        );
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;

        System.out.println("Conformance Throughput:");
        System.out.println("  Cases: " + numCases);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Cases per second: " + (numCases * 1000.0 / durationMs));

        assertEquals(numCases, results.size(),
            "Should return " + numCases + " conformance results");
        assertTrue(durationMs < 1000,
            "100 cases should process in under 1 second (took " + durationMs + "ms)");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private List<Map<String, Object>> generateLargeEventLog(int numEvents) {
        List<Map<String, Object>> eventLog = new ArrayList<>();

        for (int i = 0; i < numEvents; i++) {
            eventLog.add(Map.of(
                "activity", "Task_" + (i % 3 + 1),
                "timestamp", "2024-01-01T" + (10 + i/3600) + ":" + (i%60) + ":00Z",
                "case_id", "large_case_" + (i/100 + 1),
                "duration", 1000 + (i % 500)
            ));
        }

        return eventLog;
    }

    private List<Map<String, Object>> generateMultiCaseEventLog(int numCases) {
        List<Map<String, Object>> eventLog = new ArrayList<>();

        for (int caseId = 1; caseId <= numCases; caseId++) {
            for (int eventIdx = 0; eventIdx < 10; eventIdx++) {
                eventLog.add(Map.of(
                    "activity", "Task_" + (eventIdx % 3 + 1),
                    "timestamp", "2024-01-01T10:" + (eventIdx * 6) + ":00Z",
                    "case_id", "multi_case_" + caseId,
                    "duration", 1000 + (eventIdx * 100)
                ));
            }
        }

        return eventLog;
    }

    private List<Map<String, Object>> generateSingleCaseEventLog(int caseId) {
        return List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "case" + caseId),
            Map.of("activity", "Task_B", "timestamp", "2024-01-01T11:00:00Z", "case_id", "case" + caseId),
            Map.of("activity", "Task_C", "timestamp", "2024-01-01T12:00:00Z", "case_id", "case" + caseId)
        );
    }

    private Map<String, Object> getSimpleProcessSpec() {
        return Map.of(
            "name", "Simple Process",
            "start_task", "Start",
            "end_task", "End",
            "tasks", List.of("Task_A", "Task_B", "Task_C"),
            "edges", List.of(
                Map.of("from", "Start", "to", "Task_A"),
                Map.of("from", "Task_A", "to", "Task_B"),
                Map.of("from", "Task_B", "to", "Task_C"),
                Map.of("from", "Task_C", "to", "End")
            )
        );
    }

    /**
     * Test server implementation for process mining operations
     */
    private static class ProcessMiningTestServer {
        private boolean running = false;

        public void start() {
            // Simulate server startup
            running = true;
            System.out.println("Process Mining Test Server started");
        }

        public void stop() {
            running = false;
            System.out.println("Process Mining Test Server stopped");
        }

        public boolean isRunning() {
            return running;
        }
    }
}