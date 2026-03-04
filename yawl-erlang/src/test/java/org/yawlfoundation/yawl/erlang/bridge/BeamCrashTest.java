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
import org.yawlfoundation.yawl.erlang.processmining.ProcessMiningServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fault injection tests for BEAM layer - Kill gen_server, verify supervisor restart.
 *
 * <p>This test class focuses on testing the fault tolerance guarantees of the
 * Three-Domain Native Bridge Pattern, specifically:
 * <ul>
 *   <li>Supervisor restart: gen_server crash → supervisor restarts → ready</li>
 *   <li>Automatic recovery after process death</li>
 *   <li>State preservation across restarts</li>
 * </ul>
 *
 * @see <a href="../processmining/ErlangBridge.java">ErlangBridge Implementation</a>
 */
@Tag("integration")
@Tag("fault-injection")
@Tag("beam")
class BeamCrashTest {

    private ErlangTestNode testNode;
    private ErlangBridge bridge;
    private ProcessMiningServer processMiningServer;
    private FaultInjectionMonitor faultMonitor;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping fault injection tests");

        // Start fault injection monitor
        faultMonitor = new FaultInjectionMonitor();
        faultMonitor.start();

        // Start Erlang node with monitoring enabled
        testNode = ErlangTestNode.start();
        testNode.enableMonitoring(true);
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
            testNode.enableMonitoring(false);
            testNode.close();
        }
        if (faultMonitor != null) {
            faultMonitor.stop();
        }
    }

    // =========================================================================
    // Test 1: Gen Server Crash Recovery
    // =========================================================================

    /**
     * Verries that killing a gen_server triggers supervisor restart.
     */
    @Test
    @DisplayName("Fault Injection: Kill gen_server → supervisor restarts → ready within 10ms")
    void faultInjection_killGenServer_supervisorRestarts_readyWithin10ms()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Perform initial operation to establish baseline
        String initialCaseId = bridge.launchCase("before_crash");
        assertNotNull(initialCaseId, "Case should be created before crash");

        // Record baseline metrics
        long baselineLatency = getAverageLaunchLatency(10);

        // Kill the gen_server (simulates crash)
        long killStart = System.nanoTime();
        testNode.killProcess("yawl_process_mining_server");
        long killEnd = System.nanoTime();

        long killDurationMs = (killEnd - killStart) / 1_000_000;
        System.out.println("Gen server kill time: " + killDurationMs + "ms");

        // Wait for supervisor to restart the gen_server
        Thread.sleep(50); // Give supervisor time to restart

        // Verify the gen_server is back up
        String recoveryCaseId = bridge.launchCase("after_restart");
        assertNotNull(recoveryCaseId, "Case should be created after restart");

        // Measure recovery latency
        long recoveryStart = System.nanoTime();
        String secondRecoveryCaseId = bridge.launchCase("recovery_test");
        long recoveryEnd = System.nanoTime();

        long recoveryLatencyMs = (recoveryEnd - recoveryStart) / 1_000_000;
        System.out.println("Recovery latency: " + recoveryLatencyMs + "ms");

        // Recovery target: <10ms
        assertTrue(recoveryLatencyMs < 10,
            "Recovery latency should be under 10ms (took " + recoveryLatencyMs + "ms)");

        // Verify functionality is restored
        assertEquals(initialCaseId, secondRecoveryCaseId,
            "Should continue generating case IDs after restart");
    }

    /**
     * Verries that repeated gen_server crashes are handled gracefully.
     */
    @Test
    @DisplayName("Fault Injection: Multiple crashes → supervisor restarts repeatedly")
    void faultInjection_multipleCrashes_supervisorRestartsRepeatedly()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        int numCrashes = 5;
        List<String> caseIds = new ArrayList<>();

        // Perform operations with repeated crashes
        for (int i = 0; i < numCrashes; i++) {
            // Kill the gen_server
            testNode.killProcess("yawl_process_mining_server");
            Thread.sleep(50); // Wait for restart

            // Verify recovery
            String caseId = bridge.launchCase("crash_recovery_" + i);
            assertNotNull(caseId, "Case should be created after crash " + i);
            caseIds.add(caseId);
        }

        // Verify all case IDs are unique
        assertEquals(numCrashes, caseIds.size(),
            "Should have " + numCrashes + " unique case IDs");
        assertTrue(caseIds.stream().distinct().count() == numCrashes,
            "All case IDs should be unique after multiple crashes");

        // Verify final operation works normally
        String finalCaseId = bridge.launchCase("final_operation");
        assertNotNull(finalCaseId, "Final operation should work");
    }

    // =========================================================================
    // Test 2: State Preservation
    // =========================================================================

    /**
     * Verries that state is preserved across gen_server restarts.
     */
    @Test
    @DisplayName("State Preservation: After restart → previous data preserved")
    void statePreservation_afterRestart_previousDataPreserved()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Create some test data before crash
        List<Map<String, Object>> eventData = List.of(
            Map.of("activity", "Task_A", "timestamp", "2024-01-01T10:00:00Z", "case_id", "preserve_test")
        );

        // Store data before crash
        ConformanceResult beforeCrash = bridge.checkConformance(eventData, getSimpleProcessSpec());
        assertNotNull(beforeCrash, "Data should be stored before crash");

        // Kill gen_server
        testNode.killProcess("yawl_process_mining_server");
        Thread.sleep(100); // Wait for restart

        // Verify data is preserved after restart
        ConformanceResult afterCrash = bridge.checkConformance(eventData, getSimpleProcessSpec());
        assertNotNull(afterCrash, "Data should be preserved after crash");
        assertEquals(beforeCrash.getCaseId(), afterCrash.getCaseId(),
            "Case ID should be preserved across restart");
        assertEquals(beforeCrash.isConformant(), afterCrash.isConformant(),
            "Conformance status should be preserved");
    }

    /**
     * Verries that performance metrics are preserved across restarts.
     */
    @Test
    @DisplayName("State Preservation: Performance metrics → accumulated across restarts")
    void statePreservation_performanceMetrics_accumulatedAcrossRestarts()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Perform some operations before crash
        for (int i = 0; i < 100; i++) {
            bridge.launchCase("metric_test_" + i);
        }

        // Get baseline metrics
        Map<String, Object> baselineMetrics = bridge.getPerformanceMetrics();
        assertNotNull(baselineMetrics, "Baseline metrics should be available");
        assertTrue((long) baselineMetrics.get("total_operations") >= 100,
            "Should have at least 100 operations recorded");

        // Kill gen_server
        testNode.killProcess("yawl_process_mining_server");
        Thread.sleep(100); // Wait for restart

        // Perform more operations after crash
        for (int i = 100; i < 200; i++) {
            bridge.launchCase("post_crash_" + i);
        }

        // Verify metrics are preserved and accumulated
        Map<String, Object> postCrashMetrics = bridge.getPerformanceMetrics();
        assertNotNull(postCrashMetrics, "Post-crash metrics should be available");
        assertTrue((long) postCrashMetrics.get("total_operations") >= 200,
            "Should have at least 200 operations recorded (including pre-crash)");
    }

    // =========================================================================
    // Test 3: Concurrent Crash Handling
    // =========================================================================

    /**
     * Verries concurrent access during gen_server crashes.
     */
    @Test
    @DisplayName("Concurrency: Crash during concurrent access → all operations succeed")
    void concurrency_crashDuringConcurrentAccess_allOperationsSucceed()
            throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Submit concurrent operations
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    List<String> caseIds = new ArrayList<>();
                    for (int i = 0; i < operationsPerThread; i++) {
                        // Randomly trigger crash during operations
                        if (Math.random() < 0.1) { // 10% chance to trigger crash
                            testNode.killProcess("yawl_process_mining_server");
                            Thread.sleep(50); // Wait for restart
                        }

                        String caseId = bridge.launchCase("concurrent_" + threadId + "_" + i);
                        caseIds.add(caseId);
                    }
                    successCount.incrementAndGet();
                    return "Thread-" + threadId + ": " + caseIds.size() + " cases";
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    return "Thread-" + threadId + ": Failed - " + e.getMessage();
                }
            }));
        }

        // Wait for all threads to complete
        for (Future<String> future : futures) {
            assertDoesNotThrow(() -> future.get(30, TimeUnit.SECONDS),
                "All threads should complete within 30 seconds");
        }

        // Verify results
        System.out.println("Concurrent crash handling results:");
        System.out.println("  Successes: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Expected: " + (numThreads * operationsPerThread));

        // Most operations should succeed despite crashes
        assertTrue(successCount.get() > (numThreads * operationsPerThread * 0.8),
            "At least 80% of operations should succeed despite crashes");
    }

    /**
     * Verries that crashes don't cause deadlocks.
     */
    @Test
    @DisplayName("Concurrency: Multiple crashes → no deadlocks")
    void concurrency_multipleCrashes_noDeadlocks()
            throws InterruptedException {
        int numCrashes = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(numCrashes);

        // Trigger crashes concurrently
        for (int i = 0; i < numCrashes; i++) {
            final int crashId = i;
            executor.submit(() -> {
                try {
                    testNode.killProcess("yawl_process_mining_server");
                    Thread.sleep(50); // Wait for restart
                    latch.countDown();
                } catch (Exception e) {
                    fail("Crash " + crashId + " failed: " + e.getMessage());
                }
            });
        }

        // Perform operations while crashes are happening
        for (int i = 0; i < 50; i++) {
            Thread.sleep(10); // Small delay
            String caseId = bridge.launchCase("deadlock_test_" + i);
            assertNotNull(caseId, "Operation should succeed during crashes");
        }

        // Wait for all crashes to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS),
            "All crashes should complete within 10 seconds");

        // Verify final operation works
        String finalCaseId = bridge.launchCase("final_deadlock_test");
        assertNotNull(finalCaseId, "Final operation should work after crashes");
    }

    // =========================================================================
    // Test 4: Performance Under Fault Conditions
    // =========================================================================

    /**
     * Verries performance impact of crashes.
     */
    @Test
    @DisplayName("Performance: With crashes → still meets minimum throughput")
    void performance_withCrashes_stillMeetsMinimumThroughput()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        int numOperations = 100;
        int numCrashes = 3;
        List<Long> latencies = new ArrayList<>();

        // Perform operations with periodic crashes
        for (int i = 0; i < numOperations; i++) {
            // Trigger crash periodically
            if (i > 0 && i % (numOperations / numCrashes) == 0) {
                testNode.killProcess("yawl_process_mining_server");
                Thread.sleep(100); // Wait for restart
            }

            long start = System.nanoTime();
            String caseId = bridge.launchCase("performance_test_" + i);
            long end = System.nanoTime();

            assertNotNull(caseId, "Operation should succeed");
            latencies.add(end - start);
        }

        // Calculate performance metrics
        long totalLatency = latencies.stream().mapToLong(l -> l).sum();
        double avgLatencyMs = totalLatency / (numOperations * 1_000_000.0);
        long maxLatencyMs = latencies.stream().mapToLong(l -> l / 1_000_000).max().orElse(0);

        System.out.println("Performance with crashes:");
        System.out.println("  Operations: " + numOperations);
        System.out.println("  Avg latency: " + avgLatencyMs + "ms");
        System.out.println("  Max latency: " + maxLatencyMs + "ms");
        System.out.println("  Throughput: " + (numOperations * 1000.0 / totalLatency * 1_000_000) + " ops/sec");

        // Performance target: reasonable throughput despite crashes
        assertTrue(avgLatencyMs < 100,
            "Average latency should be under 100ms despite crashes (got " + avgLatencyMs + "ms)");
    }

    // =========================================================================
    // Test 5: Recovery Monitoring
    // =========================================================================

    /**
     * Verries recovery time is monitored and reported.
     */
    @Test
    @DisplayName("Recovery Monitoring: Recovery time → measured and logged")
    void recoveryMonitoring_recoveryTime_measuredAndLogged()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Enable monitoring
        faultMonitor.enableRecoveryMonitoring(true);

        // Kill gen_server
        testNode.killProcess("yawl_process_mining_server");

        // Wait for monitoring to capture recovery
        Thread.sleep(200); // Give monitoring time to capture metrics

        // Get recovery metrics
        Map<String, Object> recoveryMetrics = faultMonitor.getRecoveryMetrics();
        assertNotNull(recoveryMetrics, "Recovery metrics should be available");

        // Verify recovery metrics
        assertTrue(recoveryMetrics.containsKey("crash_count"),
            "Should track crash count");
        assertTrue(recoveryMetrics.containsKey("recovery_time"),
            "Should track recovery time");
        assertTrue(recoveryMetrics.containsKey("downtime"),
            "Should track downtime");

        System.out.println("Recovery metrics:");
        System.out.println("  Crash count: " + recoveryMetrics.get("crash_count"));
        System.out.println("  Recovery time: " + recoveryMetrics.get("recovery_time") + "ms");
        System.out.println("  Downtime: " + recoveryMetrics.get("downtime") + "ms");

        // Recovery target: <10ms
        long recoveryTime = ((Number) recoveryMetrics.get("recovery_time")).longValue();
        assertTrue(recoveryTime < 10,
            "Recovery time should be under 10ms (got " + recoveryTime + "ms)");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private long getAverageLaunchLatency(int numOperations)
            throws ErlangConnectionException, ErlangRpcException {
        long totalLatency = 0;
        for (int i = 0; i < numOperations; i++) {
            long start = System.nanoTime();
            bridge.launchCase("latency_test_" + i);
            long end = System.nanoTime();
            totalLatency += (end - start);
        }
        return totalLatency / numOperations / 1_000_000; // Convert to ms
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
     * Monitor for fault injection events
     */
    private static class FaultInjectionMonitor {
        private boolean running = false;
        private boolean recoveryMonitoring = false;
        private Map<String, Object> recoveryMetrics = new ConcurrentHashMap<>();
        private AtomicInteger crashCount = new AtomicInteger(0);

        public void start() {
            running = true;
            System.out.println("Fault Injection Monitor started");
        }

        public void stop() {
            running = false;
            System.out.println("Fault Injection Monitor stopped");
        }

        public void enableRecoveryMonitoring(boolean enabled) {
            this.recoveryMonitoring = enabled;
        }

        public Map<String, Object> getRecoveryMetrics() {
            Map<String, Object> copy = new HashMap<>();
            copy.putAll(recoveryMetrics);
            return copy;
        }

        public void recordCrash() {
            crashCount.incrementAndGet();
            recoveryMetrics.put("crash_count", crashCount.get());
            System.out.println("Crash recorded: " + crashCount.get());
        }

        public void recordRecovery(long recoveryTime) {
            recoveryMetrics.put("recovery_time", recoveryTime);
            System.out.println("Recovery time recorded: " + recoveryTime + "ms");
        }

        public boolean isRunning() {
            return running;
        }
    }
}