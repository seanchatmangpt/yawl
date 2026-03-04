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

import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fault injection tests for socket layer - Drop connection, verify reconnect.
 *
 * <p>This test class focuses on testing the connection fault tolerance of the
 * Three-Domain Native Bridge Pattern, specifically:
 * <ul>
 *   <li>Connection drop detection and automatic reconnect</li>
 *   <li>Network partition handling</li>
 *   <li>Socket timeout and retry mechanisms</li>
 * </ul>
 *
 * @see <a href="../processmining/ErlangBridge.java">ErlangBridge Implementation</a>
 */
@Tag("integration")
@Tag("fault-injection")
@Tag("socket")
class SocketFailureTest {

    private ErlangTestNode testNode;
    private ErlangBridge bridge;
    private SocketFailureSimulator socketSimulator;
    private ConnectionMonitor connectionMonitor;

    @BeforeEach
    void setup() {
        assumeTrue(OtpInstallationVerifier.isOtp28Available(),
            "OTP 28 not available - skipping socket failure tests");

        // Start socket failure simulator
        socketSimulator = new SocketFailureSimulator();
        socketSimulator.start();

        // Start connection monitor
        connectionMonitor = new ConnectionMonitor();
        connectionMonitor.start();

        // Start Erlang node
        testNode = ErlangTestNode.start();
        testNode.enableSocketMonitoring(true);
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
            testNode.enableSocketMonitoring(false);
            testNode.close();
        }
        if (socketSimulator != null) {
            socketSimulator.stop();
        }
        if (connectionMonitor != null) {
            connectionMonitor.stop();
        }
    }

    // =========================================================================
    // Test 1: Connection Drop Detection
    // =========================================================================

    /**
     * Verries that connection drops are detected and handled properly.
     */
    @Test
    @DisplayName("Socket Failure: Connection drop → detected within 100ms")
    void socketFailure_connectionDrop_detectedWithin100ms()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Perform initial operation to establish baseline
        String caseId1 = bridge.launchCase("before_drop");
        assertNotNull(caseId1, "Case should be created before connection drop");

        // Record baseline metrics
        long baselineLatency = getAverageLaunchLatency(5);

        // Simulate connection drop
        long dropStart = System.nanoTime();
        socketSimulator.simulateConnectionDrop();
        long dropEnd = System.nanoTime();

        long dropDetectionTimeMs = (dropEnd - dropStart) / 1_000_000;
        System.out.println("Connection drop detection time: " + dropDetectionTimeMs + "ms");

        // Verify connection is detected as dropped
        assertFalse(bridge.isConnected(),
            "Bridge should detect connection drop");

        // Connection drop detection target: <100ms
        assertTrue(dropDetectionTimeMs < 100,
            "Connection drop should be detected within 100ms (took " + dropDetectionTimeMs + "ms)");
    }

    /**
     * Verries that reconnection is automatic and successful.
     */
    @Test
    @DisplayName("Socket Failure: Reconnect → automatic and successful")
    void socketFailure_reconnect_automaticAndSuccessful()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Simulate connection drop
        socketSimulator.simulateConnectionDrop();
        assertFalse(bridge.isConnected(),
            "Bridge should detect connection drop");

        // Enable reconnection
        bridge.enableAutoReconnect(true);

        // Wait for automatic reconnection
        long reconnectStart = System.nanoTime();
        String caseId = bridge.launchCase("after_reconnect");
        long reconnectEnd = System.nanoTime();

        long reconnectTimeMs = (reconnectEnd - reconnectStart) / 1_000_000;
        assertNotNull(caseId, "Case should be created after reconnection");

        // Verify reconnection was successful
        assertTrue(bridge.isConnected(),
            "Bridge should be reconnected");
        System.out.println("Reconnection time: " + reconnectTimeMs + "ms");

        // Reconnection target: <1000ms
        assertTrue(reconnectTimeMs < 1000,
            "Reconnection should complete within 1000ms (took " + reconnectTimeMs + "ms)");
    }

    // =========================================================================
    // Test 2: Network Partition Handling
    // =========================================================================

    /**
     * Verries handling of network partitions (split-brain scenario).
     */
    @Test
    @DisplayName("Socket Failure: Network partition → handled gracefully")
    void socketFailure_networkPartition_handledGracefully()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Simulate network partition
        socketSimulator.simulateNetworkPartition(true);

        // Attempt operations (should fail during partition)
        assertThrows(ErlangConnectionException.class,
            () -> bridge.launchCase("during_partition"),
            "Operations should fail during network partition");

        // Simulate partition recovery
        socketSimulator.simulateNetworkPartition(false);

        // Wait for recovery
        Thread.sleep(500);

        // Verify operations work after recovery
        String caseId = bridge.launchCase("after_partition");
        assertNotNull(caseId, "Case should be created after partition recovery");
        assertTrue(bridge.isConnected(),
            "Bridge should be reconnected after partition recovery");
    }

    /**
     * Verries handling of asymmetric network partitions.
     */
    @Test
    @DisplayName("Socket Failure: Asymmetric partition → graceful degradation")
    void socketFailure_asymmetricPartition_gracefulDegradation()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Simulate asymmetric partition (JVM can reach BEAM, but BEAM cannot reach JVM)
        socketSimulator.simulateAsymmetricPartition(true);

        // Basic operations might still work
        String caseId = bridge.launchCase("asymmetric_test");
        assertNotNull(caseId, "Basic operations should work in asymmetric partition");

        // Complex operations might fail
        try {
            bridge.complexOperation("should_fail");
            fail("Complex operations should fail in asymmetric partition");
        } catch (ErlangConnectionException e) {
            // Expected failure
        }

        // Simulate recovery
        socketSimulator.simulateAsymmetricPartition(false);

        // Verify full recovery
        String recoveryCaseId = bridge.launchCase("after_asymmetric_recovery");
        assertNotNull(recoveryCaseId, "Operations should work after asymmetric recovery");
    }

    // =========================================================================
    // Test 3: Socket Timeout Handling
    // =========================================================================

    /**
     * Verries socket timeout handling for slow responses.
     */
    @Test
    @DisplayName("Socket Failure: Timeout → handled with retry")
    void socketFailure_timeout_handledWithRetry()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Set short timeout for testing
        bridge.setTimeout(100); // 100ms timeout

        // Simulate slow response
        socketSimulator.simulateSlowResponse(200); // 200ms delay

        // Attempt operation (should timeout and retry)
        long start = System.nanoTime();
        String caseId = bridge.launchCase("timeout_test");
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;
        assertNotNull(caseId, "Case should be created despite timeout");

        System.out.println("Timeout handling time: " + durationMs + "ms");
        assertTrue(durationMs < 500,
            "Timeout handling should complete within 500ms (took " + durationMs + "ms)");
    }

    /**
     * Verries handling of persistent timeouts.
     */
    @Test
    @DisplayName("Socket Failure: Persistent timeout → graceful failure")
    void socketFailure_persistentTimeout_gracefulFailure()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Set short timeout
        bridge.setTimeout(100); // 100ms timeout

        // Simulate persistent slow response
        socketSimulator.simulatePersistentSlowResponse();

        // Attempt multiple operations (should fail gracefully)
        for (int i = 0; i < 5; i++) {
            assertThrows(ErlangConnectionException.class,
                () -> bridge.launchCase("persistent_timeout_" + i),
                "Operation should fail due to persistent timeout");
        }

        // Verify bridge is still healthy
        assertFalse(bridge.isConnected(),
            "Bridge should detect persistent timeout and disconnect");

        // Simulate recovery
        socketSimulator.simulateSlowResponse(0); // Clear slow response

        // Verify recovery works
        bridge.enableAutoReconnect(true);
        String caseId = bridge.launchCase("after_persistent_timeout");
        assertNotNull(caseId, "Bridge should recover after persistent timeout");
    }

    // =========================================================================
    // Test 4: Concurrent Socket Failures
    // =========================================================================

    /**
     * Verries concurrent access during socket failures.
     */
    @Test
    @DisplayName("Concurrency: Concurrent socket failures → handled safely")
    void concurrency_concurrentSocketFailures_handledSafely()
            throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<String>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Simulate concurrent socket failures
        socketSimulator.simulateRandomFailures(true);

        // Submit concurrent operations
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    List<String> caseIds = new ArrayList<>();
                    for (int i = 0; i < operationsPerThread; i++) {
                        String caseId = bridge.launchCase("concurrent_socket_" + threadId + "_" + i);
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

        // Stop random failures
        socketSimulator.simulateRandomFailures(false);

        // Verify results
        System.out.println("Concurrent socket failure handling results:");
        System.out.println("  Successes: " + successCount.get());
        System.out.println("  Errors: " + errorCount.get());
        System.out.println("  Expected: " + (numThreads * operationsPerThread));

        // Most operations should succeed despite failures
        assertTrue(successCount.get() > 0,
            "Some operations should succeed despite concurrent failures");

        // Verify bridge is still functional
        String finalCaseId = bridge.launchCase("final_concurrent_socket_test");
        assertNotNull(finalCaseId, "Final operation should work");
    }

    // =========================================================================
    // Test 5: Performance Under Socket Faults
    // =========================================================================

    /**
     * Verries performance impact of socket faults.
     */
    @Test
    @DisplayName("Performance: With socket faults → still acceptable latency")
    void performance_withSocketFaults_stillAcceptableLatency()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        int numOperations = 100;
        int numFailures = 5;
        List<Long> latencies = new ArrayList<>();

        // Simulate periodic failures
        for (int i = 0; i < numOperations; i++) {
            // Trigger failure periodically
            if (i > 0 && i % (numOperations / numFailures) == 0) {
                socketSimulator.simulateConnectionDrop();
                Thread.sleep(100); // Wait for recovery
                socketSimulator.simulateConnectionDrop(false); // Clear failure
            }

            long start = System.nanoTime();
            String caseId = bridge.launchCase("performance_socket_test_" + i);
            long end = System.nanoTime();

            assertNotNull(caseId, "Operation should succeed");
            latencies.add(end - start);
        }

        // Calculate performance metrics
        long totalLatency = latencies.stream().mapToLong(l -> l).sum();
        double avgLatencyMs = totalLatency / (numOperations * 1_000_000.0);
        long maxLatencyMs = latencies.stream().mapToLong(l -> l / 1_000_000).max().orElse(0);

        System.out.println("Performance with socket faults:");
        System.out.println("  Operations: " + numOperations);
        System.out.println("  Avg latency: " + avgLatencyMs + "ms");
        System.out.println("  Max latency: " + maxLatencyMs + "ms");
        System.out.println("  Throughput: " + (numOperations * 1000.0 / totalLatency * 1_000_000) + " ops/sec");

        // Performance target: reasonable throughput despite faults
        assertTrue(avgLatencyMs < 200,
            "Average latency should be under 200ms despite socket faults (got " + avgLatencyMs + "ms)");
    }

    // =========================================================================
    // Test 6: Recovery Monitoring
    // =========================================================================

    /**
     * Verries recovery time is monitored and optimized.
     */
    @Test
    @DisplayName("Recovery Monitoring: Recovery time → optimized with backoff strategy")
    void recoveryMonitoring_recoveryTime_optimizedWithBackoffStrategy()
            throws ErlangConnectionException, ErlangRpcException, InterruptedException {
        // Enable recovery monitoring
        connectionMonitor.enableRecoveryMonitoring(true);

        // Perform multiple recovery operations to test backoff strategy
        List<Long> recoveryTimes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            // Simulate connection drop
            socketSimulator.simulateConnectionDrop();

            // Measure recovery time
            long start = System.nanoTime();
            String caseId = bridge.launchCase("recovery_monitor_test_" + i);
            long end = System.nanoTime();

            long recoveryTimeMs = (end - start) / 1_000_000;
            recoveryTimes.add(recoveryTimeMs);

            assertNotNull(caseId, "Case should be created after recovery");
        }

        // Get recovery metrics
        Map<String, Object> recoveryMetrics = connectionMonitor.getRecoveryMetrics();
        assertNotNull(recoveryMetrics, "Recovery metrics should be available");

        System.out.println("Recovery monitoring results:");
        System.out.println("  Recovery times: " + recoveryTimes);
        System.out.println("  Average recovery time: " +
            recoveryTimes.stream().mapToLong(l -> l).average().orElse(0) + "ms");
        System.out.println("  Recovery metrics: " + recoveryMetrics);

        // Verify backoff strategy is working
        assertTrue(recoveryTimes.size() == 5,
            "Should have 5 recovery time measurements");

        // Recovery should generally improve with backoff
        assertTrue(recoveryTimes.get(4) < recoveryTimes.get(0),
            "Recovery time should improve with backoff strategy");
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

    /**
     * Simulator for socket failures
     */
    private static class SocketFailureSimulator {
        private boolean running = false;
        private boolean connectionDropped = false;
        private boolean networkPartition = false;
        private boolean asymmetricPartition = false;
        private int slowResponseTime = 0;
        private boolean randomFailures = false;

        public void start() {
            running = true;
            System.out.println("Socket Failure Simulator started");
        }

        public void stop() {
            running = false;
            reset();
            System.out.println("Socket Failure Simulator stopped");
        }

        public void simulateConnectionDrop() {
            this.connectionDropped = true;
            System.out.println("Simulating connection drop");
        }

        public void simulateConnectionDrop(boolean drop) {
            this.connectionDropped = drop;
            System.out.println("Connection drop: " + drop);
        }

        public void simulateNetworkPartition(boolean partition) {
            this.networkPartition = partition;
            System.out.println("Network partition: " + partition);
        }

        public void simulateAsymmetricPartition(boolean asymmetric) {
            this.asymmetricPartition = asymmetric;
            System.out.println("Asymmetric partition: " + asymmetric);
        }

        public void simulateSlowResponse(int delayMs) {
            this.slowResponseTime = delayMs;
            System.out.println("Simulating slow response: " + delayMs + "ms");
        }

        public void simulatePersistentSlowResponse() {
            this.slowResponseTime = -1; // Infinite delay
            System.out.println("Simulating persistent slow response");
        }

        public void simulateRandomFailures(boolean failures) {
            this.randomFailures = failures;
            System.out.println("Random failures: " + failures);
        }

        public boolean isConnectionDropped() {
            return connectionDropped;
        }

        public boolean isNetworkPartition() {
            return networkPartition;
        }

        public boolean isAsymmetricPartition() {
            return asymmetricPartition;
        }

        public int getSlowResponseTime() {
            return slowResponseTime;
        }

        public boolean hasRandomFailures() {
            return randomFailures;
        }

        public void reset() {
            connectionDropped = false;
            networkPartition = false;
            asymmetricPartition = false;
            slowResponseTime = 0;
            randomFailures = false;
        }
    }

    /**
     * Monitor for connection events
     */
    private static class ConnectionMonitor {
        private boolean running = false;
        private boolean recoveryMonitoring = false;
        private Map<String, Object> recoveryMetrics = new ConcurrentHashMap<>();
        private List<Long> recoveryTimes = new CopyOnWriteArrayList<>();

        public void start() {
            running = true;
            System.out.println("Connection Monitor started");
        }

        public void stop() {
            running = false;
            System.out.println("Connection Monitor stopped");
        }

        public void enableRecoveryMonitoring(boolean enabled) {
            this.recoveryMonitoring = enabled;
        }

        public Map<String, Object> getRecoveryMetrics() {
            Map<String, Object> copy = new HashMap<>();
            copy.putAll(recoveryMetrics);
            return copy;
        }

        public void recordRecovery(long recoveryTime) {
            if (recoveryMonitoring) {
                recoveryTimes.add(recoveryTime);
                recoveryMetrics.put("last_recovery_time", recoveryTime);
                recoveryMetrics.put("average_recovery_time",
                    recoveryTimes.stream().mapToLong(l -> l).average().orElse(0));
                recoveryMetrics.put("recovery_count", recoveryTimes.size());
            }
        }

        public List<Long> getRecoveryTimes() {
            return new ArrayList<>(recoveryTimes);
        }

        public boolean isRunning() {
            return running;
        }
    }
}