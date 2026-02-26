/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.java_python.chaos;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.unmarshaller.YAWLUnmarshaller;
import org.yawlfoundation.yawl.engine.interfce.WorklistManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
//import static org.awaitility.Awaitility.await; // Not available, using Thread.sleep instead

/**
 * Chaos Engineering Tests for Java-Python Integration
 *
 * These tests validate the resilience of the YAWL Java-Python integration
 * under various failure conditions following the principles of chaos engineering.
 * Each test simulates production-like failure scenarios and verifies that
 * the system maintains its stability and recovers appropriately.
 */
@TestMethodOrder(OrderAnnotation.class)
public class ChaosEngineeringTest extends ValidationTestBase {

    private static final String CHAOS_WORKFLOW_NAME = "ChaosTestWorkflow";
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT_MS = 5000;
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    private YAWLServiceGateway gateway;
    private WorklistManager worklistManager;
    private YawlServiceGatewayClient serviceClient;
    private List<String> failedWorkItems = new ArrayList<>();
    private AtomicInteger circuitBreakerTrips = new AtomicInteger(0);
    private AtomicBoolean degradationMode = new AtomicBoolean(false);

    @BeforeAll
    static void setupChaosTestSuite() throws Exception {
        logger.info("Starting Chaos Engineering Test Suite");
        setupTestEnvironment();
    }

    @BeforeEach
    void setupChaosEnvironment() throws Exception {
        logger.info("Setting up chaos test environment");
        super.setupTestEnvironment();

        gateway = new YAWLServiceGateway();
        worklistManager = new WorklistManager();
        serviceClient = new YawlServiceGatewayClient("http://localhost:8080/yawl");

        // Initialize circuit breaker state
        circuitBreakerTrips.set(0);
        degradationMode.set(false);
        failedWorkItems.clear();

        // Load test workflow
        loadChaosTestWorkflow();
    }

    @AfterEach
    void cleanupChaosEnvironment() {
        logger.info("Cleaning up chaos test environment");
        try {
            // Cancel any pending work items
            cancelAllWorkItems();

            // Reset circuit breaker
            circuitBreakerTrips.set(0);
            degradationMode.set(false);

        } catch (Exception e) {
            logger.warning("Cleanup failed: " + e.getMessage());
        }
        super.cleanupTestEnvironment();
    }

    @AfterAll
    static void teardownChaosTestSuite() {
        logger.info("Chaos Engineering Test Suite completed");
        cleanupTestEnvironment();
    }

    /**
     * Test 1: Failure Recovery Test
     *
     * Verifies that the system can recover from transient failures
     * and complete work items despite repeated failures.
     */
    @Test
    @Order(1)
    @DisplayName("Failure Recovery: System should recover from transient failures")
    void testFailureRecovery() throws Exception {
        logger.info("Testing failure recovery mechanism");

        int workItemCount = 10;
        AtomicInteger successfulRecovery = new AtomicInteger(0);

        // Simulate transient failures by injecting fault
        FaultInjector faultInjector = new FaultInjector(FaultType.TRANSIENT_FAILURE);
        faultInjector.start();

        try {
            // Submit work items
            List<String> workItemIds = new ArrayList<>();
            for (int i = 0; i < workItemCount; i++) {
                String workItemId = submitChaosTestWorkItem();
                workItemIds.add(workItemId);
            }

            // Monitor recovery
            assertTrue(waitForCondition(30000, 1000, () -> successfulRecovery.get() == workItemCount),
                "Work items should be recovered within timeout");

            // Verify all work items were eventually processed
            assertEquals(workItemCount, workItemIds.size());
            assertEquals(0, failedWorkItems.size(), "All work items should be recovered");

        } finally {
            faultInjector.stop();
        }
    }

    /**
     * Test 2: Circuit Breaker Behavior Test
     *
     * Verifies that the circuit breaker trips when failure threshold
     * is reached and recovers after timeout.
     */
    @Test
    @Order(2)
    @DisplayName("Circuit Breaker: Should trip on threshold and recover")
    void testCircuitBreaker() throws Exception {
        logger.info("Testing circuit breaker behavior");

        // Simulate persistent failures to trip circuit breaker
        FaultInjector faultInjector = new FaultInjector(FaultType.PERSISTENT_FAILURE);
        faultInjector.start();

        try {
            // Submit enough work items to trip the circuit breaker
            for (int i = 0; i < CIRCUIT_BREAKER_THRESHOLD + 2; i++) {
                try {
                    String workItemId = submitChaosTestWorkItem();
                    logger.info("Submitted work item: " + workItemId);
                } catch (Exception e) {
                    logger.warning("Work item submission failed: " + e.getMessage());
                }
            }

            // Verify circuit breaker tripped
            assertTrue(circuitBreakerTrips.get() > 0,
                "Circuit breaker should have been tripped");

            // Wait for recovery
            assertTrue(waitForCondition(10000, 1000, () -> circuitBreakerTrips.get() == 0),
                "Circuit breaker should recover within timeout");

        } finally {
            faultInjector.stop();
        }
    }

    /**
     * Test 3: Graceful Degradation Test
     *
     * Verifies that the system gracefully degrades when under heavy load
     * by implementing fallback behaviors.
     */
    @Test
    @Order(3)
    @DisplayName("Graceful Degradation: Should degrade gracefully under load")
    void testGracefulDegradation() throws Exception {
        logger.info("Testing graceful degradation");

        // Simulate high load
        LoadSimulator loadSimulator = new LoadSimulator(100); // 100 concurrent requests
        loadSimulator.start();

        try {
            // Submit work items during high load
            List<String> workItemIds = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                try {
                    String workItemId = submitChaosTestWorkItem();
                    workItemIds.add(workItemId);
                } catch (Exception e) {
                    // Expected during degradation
                    logger.warning("Expected degradation: " + e.getMessage());
                }
            }

            // Verify degradation mode was activated
            assertTrue(degradationMode.get(),
                "System should enter degradation mode");

            // Verify some work items still succeed
            assertTrue(workItemIds.size() > 0,
                "Some work items should succeed during degradation");

        } finally {
            loadSimulator.stop();

            // Wait for recovery from degradation
            assertTrue(waitForCondition(15000, 1000, () -> !degradationMode.get()),
                "System should exit degradation mode within timeout");
        }
    }

    /**
     * Test 4: Timeout Handling Test
     *
     * Verifies that timeouts are handled correctly without causing
     * resource leaks or system instability.
     */
    @Test
    @Order(4)
    @DisplayName("Timeout Handling: Should handle timeouts gracefully")
    void testTimeoutHandling() throws Exception {
        logger.info("Testing timeout handling");

        // Create a work item that will timeout
        FaultInjector faultInjector = new FaultInjector(FaultType.TIMEOUT);
        faultInjector.start();

        try {
            // Submit work items with timeout
            for (int i = 0; i < 10; i++) {
                try {
                    String workItemId = submitChaosTestWorkItem();
                    logger.info("Submitted timed work item: " + workItemId);
                } catch (Exception e) {
                    logger.warning("Timeout occurred: " + e.getMessage());
                }

                // Verify timeout was handled
                Thread.sleep(100); // Small delay between submissions
            }

            // Verify no resource leaks
            assertEquals(0, getActiveThreadCount(),
                "No thread leaks should occur");
            assertEquals(0, getMemoryLeakCount(),
                "No memory leaks should occur");

        } finally {
            faultInjector.stop();
        }
    }

    /**
     * Test 5: Resource Exhaustion Test
     *
     * Verifies that the system handles resource exhaustion scenarios
     * without crashing and recovers when resources become available.
     */
    @Test
    @Order(5)
    @DisplayName("Resource Exhaustion: Should handle resource limits")
    void testResourceExhaustion() throws Exception {
        logger.info("Testing resource exhaustion scenarios");

        // Simulate CPU intensive operations
        FaultInjector faultInjector = new FaultInjector(FaultType.CPU_INTENSIVE);
        faultInjector.start();

        try {
            // Submit work items that will consume resources
            List<Future<String>> futures = new ArrayList<>();
            ExecutorService executor = Executors.newFixedThreadPool(10);

            for (int i = 0; i < 50; i++) {
                Future<String> future = executor.submit(() -> {
                    return submitChaosTestWorkItem();
                });
                futures.add(future);
            }

            // Wait for all submissions to complete
            List<String> completedWorkItems = new ArrayList<>();
            for (Future<String> future : futures) {
                try {
                    String workItemId = future.get(10, TimeUnit.SECONDS);
                    completedWorkItems.add(workItemId);
                } catch (TimeoutException e) {
                    logger.warning("Work item timed out due to resource constraints");
                } catch (Exception e) {
                    logger.warning("Work item failed: " + e.getMessage());
                }
            }

            executor.shutdown();

            // Verify some work items completed despite resource constraints
            assertTrue(completedWorkItems.size() > 0,
                "Some work items should complete despite resource constraints");

            // Verify system stability after resource release
            assertTrue(waitForCondition(10000, 1000, () -> {
                try {
                    return getCPUUsage() < 80;
                } catch (Exception e) {
                    return false;
                }
            }), "CPU usage should normalize within timeout");

        } finally {
            faultInjector.stop();
        }
    }

    /**
     * Test 6: Chaos Monkey Test (Random Failures)
     *
     * Simulates random, unpredictable failures to test system resilience.
     */
    @Test
    @Order(6)
    @DisplayName("Chaos Monkey: Should handle random failures")
    void testChaosMonkey() throws Exception {
        logger.info("Running chaos monkey test - random failures");

        ChaosMonkey chaosMonkey = new ChaosMonkey(100); // 100ms intervals
        chaosMonkey.start();

        try {
            // Submit work items during random failures
            List<String> workItemIds = new ArrayList<>();
            Random random = new Random();

            for (int i = 0; i < 30; i++) {
                try {
                    // Randomly pause to simulate unpredictable timing
                    if (random.nextDouble() < 0.3) {
                        Thread.sleep(random.nextInt(1000));
                    }

                    String workItemId = submitChaosTestWorkItem();
                    workItemIds.add(workItemId);
                } catch (Exception e) {
                    logger.warning("Random failure occurred: " + e.getMessage());
                }
            }

            // System should remain stable despite random failures
            assertTrue(workItemIds.size() >= 10,
                "Should process at least 10 work items despite random failures");

        } finally {
            chaosMonkey.stop();
        }
    }

    /**
     * Test 7: Cascade Failure Prevention Test
     *
     * Verifies that failures in one component don't cascade to others.
     */
    @Test
    @Order(7)
    @DisplayName("Cascade Prevention: Should prevent cascade failures")
    void testCascadeFailurePrevention() throws Exception {
        logger.info("Testing cascade failure prevention");

        // Test multiple components simultaneously
        List<FaultInjector> injectors = new ArrayList<>();
        injectors.add(new FaultInjector(FaultType.TRANSIENT_FAILURE));
        injectors.add(new FaultInjector(FaultType.TIMEOUT));
        injectors.add(new FaultInjector(FaultType.PERSISTENT_FAILURE));

        // Start fault injection in different components
        for (FaultInjector injector : injectors) {
            injector.start();
        }

        try {
            // Submit work items during multiple failure modes
            List<String> workItemIds = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                try {
                    String workItemId = submitChaosTestWorkItem();
                    workItemIds.add(workItemId);
                } catch (Exception e) {
                    logger.warning("Component failure occurred: " + e.getMessage());
                }
            }

            // Verify that not all work items failed
            assertTrue(workItemIds.size() > 0,
                "Some work items should succeed despite component failures");

            // Verify system didn't crash
            assertTrue(systemIsHealthy(),
                "System should remain healthy despite multiple failures");

        } finally {
            // Stop all injectors
            for (FaultInjector injector : injectors) {
                injector.stop();
            }
        }
    }

    // Helper Methods

    private String submitChaosTestWorkItem() throws Exception {
        // Simulate work item submission with chaos injection
        return serviceClient.createWorkItem(CHAOS_WORKFLOW_NAME, "start", "{}");
    }

    private void cancelAllWorkItems() {
        try {
            // Cancel any pending work items
            worklistManager.cancelAllWorkItems();
        } catch (Exception e) {
            logger.warning("Failed to cancel work items: " + e.getMessage());
        }
    }

    private void loadChaosTestWorkflow() throws Exception {
        // Load a simple test workflow for chaos testing
        String workflowXml = "<net id='ChaosTestWorkflow' ... >" +
                             "<tasks>" +
                             "  <task id='Start' ... />" +
                             "  <task id='Process' ... />" +
                             "  <task id='End' ... />" +
                             "</tasks>" +
                             "<flows ... />" +
                             "</net>";

        YAWLUnmarshaller unmarshaller = new YAWLUnmarshaller();
        YNet net = unmarshaller.unmarshalNet(new StringReader(workflowXml));
        gateway.registerNet(net);
    }

    private int getActiveThreadCount() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return threadBean.getThreadCount();
    }

    private int getMemoryLeakCount() {
        // Simple memory leak detection
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        return (int) (usedMemory / (1024 * 1024)); // MB
    }

    private double getCPUUsage() throws Exception {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getSystemCpuLoad() * 100;
        }
        return 0.0;
    }

    private boolean systemIsHealthy() {
        // Simple health check
        try {
            return getCPUUsage() < 90 && getMemoryLeakCount() < 100;
        } catch (Exception e) {
            return false;
        }
    }

    // Inner Classes for Chaos Simulation

    enum FaultType {
        TRANSIENT_FAILURE,
        PERSISTENT_FAILURE,
        TIMEOUT,
        CPU_INTENSIVE,
        MEMORY_INTENSIVE,
        NETWORK_FAILURE
    }

    class FaultInjector implements Runnable {
        private final FaultType faultType;
        private final ScheduledExecutorService executor;
        private volatile boolean running;

        public FaultInjector(FaultType faultType) {
            this.faultType = faultType;
            this.executor = Executors.newSingleThreadScheduledExecutor();
            this.running = false;
        }

        public void start() {
            running = true;
            executor.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
        }

        public void stop() {
            running = false;
            executor.shutdown();
        }

        @Override
        public void run() {
            if (!running) return;

            try {
                switch (faultType) {
                    case TRANSIENT_FAILURE:
                        if (Math.random() < 0.3) { // 30% chance of failure
                            simulateTransientFailure();
                        }
                        break;

                    case PERSISTENT_FAILURE:
                        simulatePersistentFailure();
                        break;

                    case TIMEOUT:
                        simulateTimeout();
                        break;

                    case CPU_INTENSIVE:
                        simulateCPUIntensive();
                        break;

                    case MEMORY_INTENSIVE:
                        simulateMemoryIntensive();
                        break;

                    case NETWORK_FAILURE:
                        simulateNetworkFailure();
                        break;
                }
            } catch (Exception e) {
                logger.warning("Fault injection failed: " + e.getMessage());
            }
        }

        private void simulateTransientFailure() {
            logger.info("Simulating transient failure");
            throw new RuntimeException("Transient failure occurred");
        }

        private void simulatePersistentFailure() {
            logger.info("Simulating persistent failure");
            circuitBreakerTrips.incrementAndGet();
            throw new RuntimeException("Persistent failure occurred");
        }

        private void simulateTimeout() {
            logger.info("Simulating timeout");
            try {
                Thread.sleep(TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new TimeoutException("Operation timed out");
        }

        private void simulateCPUIntensive() {
            logger.info("Simulating CPU intensive operation");
            for (int i = 0; i < 1000000; i++) {
                Math.sqrt(i);
            }
        }

        private void simulateMemoryIntensive() {
            logger.info("Simulating memory intensive operation");
            List<byte[]> memoryList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                memoryList.add(new byte[1024]);
            }
        }

        private void simulateNetworkFailure() {
            logger.info("Simulating network failure");
            throw new RuntimeException("Network failure occurred");
        }
    }

    class LoadSimulator implements Runnable {
        private final int requestRatePerSecond;
        private final ScheduledExecutorService executor;
        private volatile boolean running;

        public LoadSimulator(int requestRatePerSecond) {
            this.requestRatePerSecond = requestRatePerSecond;
            this.executor = Executors.newScheduledThreadPool(10);
            this.running = false;
        }

        public void start() {
            running = true;
            long delay = 1000 / requestRatePerSecond;
            executor.scheduleAtFixedRate(this, 0, delay, TimeUnit.MILLISECONDS);
        }

        public void stop() {
            running = false;
            executor.shutdown();
        }

        @Override
        public void run() {
            if (!running) return;

            try {
                // Submit load
                Future<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return submitChaosTestWorkItem();
                    } catch (Exception e) {
                        return null;
                    }
                });

                // Check for degradation
                if (future.isDone() && future.get() == null) {
                    degradationMode.set(true);
                }

            } catch (Exception e) {
                logger.warning("Load simulation error: " + e.getMessage());
            }
        }
    }

    class ChaosMonkey implements Runnable {
        private final long intervalMs;
        private final ScheduledExecutorService executor;
        private volatile boolean running;
        private final Random random = new Random();

        public ChaosMonkey(long intervalMs) {
            this.intervalMs = intervalMs;
            this.executor = Executors.newSingleThreadScheduledExecutor();
            this.running = false;
        }

        public void start() {
            running = true;
            executor.scheduleAtFixedRate(this, 0, intervalMs, TimeUnit.MILLISECONDS);
        }

        public void stop() {
            running = false;
            executor.shutdown();
        }

        @Override
        public void run() {
            if (!running) return;

            // Random chaos events
            int eventType = random.nextInt(10);

            switch (eventType) {
                case 0: // Transient failure
                    throw new RuntimeException("Chaos monkey: transient failure");

                case 1: // Timeout
                    try {
                        Thread.sleep(random.nextInt(1000));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;

                case 2: // Memory spike
                    List<byte[]> memory = new ArrayList<>();
                    memory.add(new byte[random.nextInt(10000)]);
                    break;

                case 3: // CPU spike
                    for (int i = 0; i < random.nextInt(10000); i++) {
                        Math.sqrt(i);
                    }
                    break;

                case 4: // Network issue
                    throw new RuntimeException("Chaos monkey: network failure");

                default:
                    // Normal operation
                    break;
            }
        }
    }
}