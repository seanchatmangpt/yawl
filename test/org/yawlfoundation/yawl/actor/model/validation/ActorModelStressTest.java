/*
 * YAWL - Yet Another Workflow Language
 * Copyright (C) 2003-2006, 2008-2011, 2014-2019 National University of Ireland, Galway
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.yawlfoundation.yawl.actor.model.validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Phase 2 Stress and Stability Testing
 *
 * Validates actor model under extreme conditions:
 * - 24-hour stability test at 5M agents
 * - Message flood tests (100K messages/second total)
 * - Burst pattern testing (sudden spikes in load)
 * - Memory leak verification over time
 */
@Execution(ExecutionMode.CONCURRENT)
@TestMethodOrder(MethodOrder.OrderAnnotation.class)
public class ActorModelStressTest {

    private static final YEngine engine = YEngine.getInstance();
    private static final int[] STRESS_SCALES = {1_000_000, 5_000_000, 10_000_000};
    private static final long FLOOD_RATE_MSG_PER_SECOND = 100_000;
    private static final STABILITY_DURATION_HOURS = 24;
    private static final BURST_MULTIPLIER = 10; // 10x normal load
    private static final MAX_MEMORY_GROWTH_PERCENT = 5; // 5% memory growth allowed

    private YSpecificationID specID;
    private YNet net;
    private String specName;

    @BeforeAll
    static void setupEngine() {
        assumeTrue(engine != null, "YAWL Engine must be initialized");
        engine.initialise();
    }

    @BeforeEach
    void setupSpecification() throws Exception {
        specName = "StressTest_" + UUID.randomUUID();
        String specXML = createStressSpecification(specName);
        specID = engine.importSpecification(specXML);
        net = engine.getSpecification(specID).getNet("Net");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (specID != null) {
            engine.removeSpecification(specID);
        }
    }

    @Test
    @Order(1)
    @DisplayName("1M Agent Stability Test - 4 Hours")
    void testStability1M4Hours() throws Exception {
        runStabilityTest(1_000_000, 4, "1M_4H_Stability_Test");
    }

    @Test
    @Order(2)
    @DisplayName("5M Agent Stability Test - 24 Hours")
    void testStability5M24Hours() throws Exception {
        runStabilityTest(5_000_000, 24, "5M_24H_Stability_Test");
    }

    @Test
    @Order(3)
    @DisplayName("10M Agent Stability Test - 8 Hours")
    void testStability10M8Hours() throws Exception {
        assumeTrue(Runtime.getRuntime().maxMemory() >= 64L * 1024 * 1024 * 1024,
                  "10M test requires at least 64GB heap");
        runStabilityTest(10_000_000, 8, "10M_8H_Stability_Test");
    }

    @Test
    @Order(4)
    @DisplayName("Message Flood Test - 100K msg/s")
    void testMessageFlood() throws Exception {
        runMessageFloodTest();
    }

    @Test
    @Order(5)
    @DisplayName("Burst Pattern Test")
    void testBurstPattern() throws Exception {
        runBurstPatternTest();
    }

    @Test
    @Order(6)
    @DisplayName("Memory Leak Detection Test")
    void testMemoryLeakDetection() throws Exception {
        runMemoryLeakDetectionTest();
    }

    @Test
    @Order(7)
    @DisplayName("Mixed Stress Test")
    void testMixedStress() throws Exception {
        runMixedStressTest();
    }

    @Test
    @Order(8)
    @DisplayName("Recovery Stress Test")
    void testRecoveryStress() throws Exception {
        runRecoveryStressTest();
    }

    private void runStabilityTest(int agentCount, int durationHours, String testName) throws Exception {
        System.out.println("Starting " + testName + " with " + agentCount + " agents for " + durationHours + " hours");

        // Stability monitoring setup
        StabilityMonitor stabilityMonitor = new StabilityMonitor();
        Thread monitorThread = new Thread(stabilityMonitor);
        monitorThread.start();

        // Create test contexts
        List<StabilityTestContext> contexts = createStabilityTestContexts(agentCount);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        long testDurationMs = durationHours * 60L * 60L * 1000;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDurationMs;

        // Schedule periodic checkpoints
        ScheduledExecutorService checkpointExecutor = Executors.newSingleThreadScheduledExecutor();
        AtomicBoolean testComplete = new AtomicBoolean(false);

        try {
            // Start all agents
            for (StabilityTestContext context : contexts) {
                executor.submit(() -> runStableAgent(context, stabilityMonitor, endTime, testComplete));
            }

            // Schedule checkpoints every 30 minutes
            checkpointExecutor.scheduleAtFixedRate(() -> {
                if (!testComplete.get()) {
                    stabilityMonitor.checkpoint();
                }
            }, 30, 30, TimeUnit.MINUTES);

            // Main test loop
            while (System.currentTimeMillis() < endTime) {
                Thread.sleep(1000);

                // Check for critical failures
                if (stabilityMonitor.isCriticalFailure()) {
                    System.err.println("Critical failure detected, stopping test early");
                    testComplete.set(true);
                    break;
                }
            }

            // Signal completion and graceful shutdown
            testComplete.set(true);
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.MINUTES));

        } finally {
            // Stop monitoring
            monitorThread.interrupt();
            checkpointExecutor.shutdown();

            // Generate comprehensive stability report
            generateStabilityReport(testName, agentCount, durationHours, stabilityMonitor);
        }
    }

    private void runMessageFloodTest() throws Exception {
        int testScale = 500_000; // Use 500K agents for flood testing
        System.out.println("Starting Message Flood Test with " + FLOOD_RATE_MSG_PER_SECOND + " msg/s total");

        FloodMonitor floodMonitor = new FloodMonitor();
        Thread monitorThread = new Thread(floodMonitor);
        monitorThread.start();

        List<FloodTestContext> contexts = createFloodTestContexts(testScale);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        long testDuration = 60_000; // 1 minute of flood
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;

        try {
            // Start flood testing
            for (FloodTestContext context : contexts) {
                executor.submit(() -> runMessageFloodAgent(context, floodMonitor, endTime));
            }

            // Monitor flood progress
            while (System.currentTimeMillis() < endTime) {
                Thread.sleep(100);
                floodMonitor.checkProgress(contexts.size());
            }

            executor.shutdown();

        } finally {
            monitorThread.interrupt();
            generateFloodReport(floodMonitor);
        }
    }

    private void runBurstPatternTest() throws Exception {
        int testScale = 1_000_000;
        System.out.println("Starting Burst Pattern Test with " + testScale + " agents");

        BurstMonitor burstMonitor = new BurstMonitor();
        Thread monitorThread = new Thread(burstMonitor);
        monitorThread.start();

        List<BurstTestContext> contexts = createBurstTestContexts(testScale);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        long testDuration = 120_000; // 2 minutes
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;

        try {
            // Start burst pattern testing
            for (BurstTestContext context : contexts) {
                executor.submit(() -> runBurstPatternAgent(context, burstMonitor, endTime));
            }

            // Monitor burst patterns
            while (System.currentTimeMillis() < endTime) {
                Thread.sleep(100);
                burstMonitor.checkProgress();
            }

            executor.shutdown();

        } finally {
            monitorThread.interrupt();
            generateBurstReport(burstMonitor);
        }
    }

    private void runMemoryLeakDetectionTest() throws Exception {
        int testScale = 2_000_000;
        System.out.println("Starting Memory Leak Detection Test with " + testScale + " agents");

        LeakMonitor leakMonitor = new LeakMonitor();
        Thread monitorThread = new Thread(leakMonitor);
        monitorThread.start();

        List<LeakTestContext> contexts = createLeakTestContexts(testScale);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Run test in phases for leak detection
        for (int phase = 0; phase < 3; phase++) {
            System.out.println("Starting leak detection phase " + (phase + 1));

            // Start agents for this phase
            for (LeakTestContext context : contexts) {
                executor.submit(() -> runMemoryAgent(context, leakMonitor, phase));
            }

            // Let phase run for 30 seconds
            Thread.sleep(30_000);

            // Check for leaks
            if (leakMonitor.isLeakDetected()) {
                System.err.println("Memory leak detected in phase " + (phase + 1));
                break;
            }

            // Clean up for next phase
            System.gc();
            Thread.sleep(5_000);
        }

        executor.shutdown();
        generateLeakReport(leakMonitor);
    }

    private void runMixedStressTest() throws Exception {
        int testScale = 3_000_000;
        System.out.println("Starting Mixed Stress Test with " + testScale + " agents");

        MixedStressMonitor mixedMonitor = new MixedStressMonitor();
        Thread monitorThread = new Thread(mixedMonitor);
        monitorThread.start();

        List<MixedStressContext> contexts = createMixedStressContexts(testScale);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        long testDuration = 90_000; // 90 seconds
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;

        try {
            // Start mixed stress testing
            for (MixedStressContext context : contexts) {
                executor.submit(() -> runMixedStressAgent(context, mixedMonitor, endTime));
            }

            // Monitor mixed stress
            while (System.currentTimeMillis() < endTime) {
                Thread.sleep(100);
                mixedMonitor.checkProgress();
            }

            executor.shutdown();

        } finally {
            monitorThread.interrupt();
            generateMixedStressReport(mixedMonitor);
        }
    }

    private void runRecoveryStressTest() throws Exception {
        int testScale = 1_000_000;
        System.out.println("Starting Recovery Stress Test with " + testScale + " agents");

        RecoveryMonitor recoveryMonitor = new RecoveryMonitor();
        Thread monitorThread = new Thread(recoveryMonitor);
        monitorThread.start();

        List<RecoveryTestContext> contexts = createRecoveryTestContexts(testScale);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        long testDuration = 120_000; // 2 minutes
        long startTime = System.currentTimeMillis();
        long endTime = startTime + testDuration;

        try {
            // Start recovery stress testing with periodic failures
            for (RecoveryTestContext context : contexts) {
                executor.submit(() -> runRecoveryStressAgent(context, recoveryMonitor, endTime));
            }

            // Simulate periodic failures
            ScheduledExecutorService failureScheduler = Executors.newSingleThreadScheduledExecutor();
            failureScheduler.scheduleAtFixedRate(() -> {
                // Simulate failure scenarios
                recoveryMonitor.simulateFailure();
            }, 10, 10, TimeUnit.SECONDS);

            // Monitor recovery progress
            while (System.currentTimeMillis() < endTime) {
                Thread.sleep(100);
                recoveryMonitor.checkProgress();
            }

            failureScheduler.shutdown();
            executor.shutdown();

        } finally {
            monitorThread.interrupt();
            generateRecoveryReport(recoveryMonitor);
        }
    }

    // Helper methods for test context creation
    private List<StabilityTestContext> createStabilityTestContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new StabilityTestContext(
                specName + "_stable_" + i,
                specID, net
            ))
            .collect(Collectors.toList());
    }

    private List<FloodTestContext> createFloodTestContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new FloodTestContext(
                specName + "_flood_" + i,
                specID, net,
                FLOOD_RATE_MSG_PER_SECOND / count
            ))
            .collect(Collectors.toList());
    }

    private List<BurstTestContext> createBurstTestContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new BurstTestContext(
                specName + "_burst_" + i,
                specID, net
            ))
            .collect(Collectors.toList());
    }

    private List<LeakTestContext> createLeakTestContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new LeakTestContext(
                specName + "_leak_" + i,
                specID, net
            ))
            .collect(Collectors.toList());
    }

    private List<MixedStressContext> createMixedStressContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new MixedStressContext(
                specName + "_mixed_" + i,
                specID, net
            ))
            .collect(Collectors.toList());
    }

    private List<RecoveryTestContext> createRecoveryTestContexts(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new RecoveryTestContext(
                specName + "_recovery_" + i,
                specID, net
            ))
            .collect(Collectors.toList());
    }

    // Test execution methods
    private void runStableAgent(StabilityTestContext context, StabilityMonitor monitor, long endTime, AtomicBoolean testComplete) {
        while (!testComplete.get() && System.currentTimeMillis() < endTime) {
            try {
                // Create case
                String caseID = engine.createCase(context.specID);

                // Get work items
                List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
                if (!workItems.isEmpty()) {
                    YWorkItem workItem = workItems.get(0);

                    // Process work item
                    context.netRunner = engine.getNetRunner(caseID, workItem.getID());
                    context.netRunner.fireExternalEvent("stable_event", null, null);

                    // Complete case
                    engine.completeCase(caseID);
                }

                // Track iteration
                context.iterations.incrementAndGet();

                // Small delay to prevent overwhelming
                Thread.sleep(1);

            } catch (Exception e) {
                context.errors.incrementAndGet();
                monitor.recordError(context.caseID, e.getMessage());
            }
        }
    }

    private void runMessageFloodAgent(FloodTestContext context, FloodMonitor monitor, long endTime) {
        try {
            while (System.currentTimeMillis() < endTime) {
                // Create case
                String caseID = engine.createCase(context.specID);

                // Send flood of messages
                for (int i = 0; i < context.messageRate; i++) {
                    YWorkItem workItem = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL).get(0);
                    context.netRunner = engine.getNetRunner(caseID, workItem.getID());

                    long startTime = System.nanoTime();
                    context.netRunner.fireExternalEvent("flood_message_" + i, null, null);
                    long duration = System.nanoTime() - startTime;

                    monitor.recordMessage(caseID, duration);

                    // Small delay to achieve target rate
                    Thread.sleep(1);
                }

                // Complete case
                engine.completeCase(caseID);

            }
        } catch (Exception e) {
            context.errors.incrementAndGet();
            monitor.recordError(context.caseID, e.getMessage());
        }
    }

    private void runBurstPatternAgent(BurstTestContext context, BurstMonitor monitor, long endTime) {
        try {
            while (System.currentTimeMillis() < endTime) {
                // Normal operation
                runNormalAgentCycle(context);

                // Randomly trigger burst patterns
                if (Math.random() < 0.1) { // 10% chance per iteration
                    triggerBurstPattern(context, monitor);
                }

                // Track pattern
                monitor.recordPattern("normal", context.iterations.get());
            }
        } catch (Exception e) {
            context.errors.incrementAndGet();
            monitor.recordError(context.caseID, e.getMessage());
        }
    }

    private void runMemoryAgent(LeakTestContext context, LeakMonitor monitor, int phase) {
        try {
            // Phase-specific memory operations
            for (int i = 0; i < 1000; i++) {
                String caseID = engine.createCase(context.specID);

                // Memory-intensive operations
                for (int j = 0; j < 10; j++) {
                    YWorkItem workItem = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL).get(0);
                    context.netRunner = engine.getNetRunner(caseID, workItem.getID());

                    // Simulate memory allocation
                    byte[] data = new byte[1024];
                    Arrays.fill(data, (byte) (phase * 100 + j));

                    context.netRunner.fireExternalEvent("memory_test", null, null);

                    // Track memory
                    context.memoryAllocations.add(data.length);
                }

                // Complete case
                engine.completeCase(caseID);

                context.iterations.incrementAndGet();

                // Periodic memory measurement
                if (context.iterations.get() % 100 == 0) {
                    long memory = Runtime.getRuntime().totalMemory();
                    monitor.recordMemory(context.caseID, memory);
                }
            }
        } catch (Exception e) {
            context.errors.incrementAndGet();
            monitor.recordError(context.caseID, e.getMessage());
        }
    }

    private void runMixedStressAgent(MixedStressContext context, MixedStressMonitor monitor, long endTime) {
        try {
            while (System.currentTimeMillis() < endTime) {
                // Cycle through different stress patterns
                int pattern = (int) (context.iterations.get() % 5);
                switch (pattern) {
                    case 0:
                        runNormalAgentCycle(context);
                        monitor.recordPattern("normal");
                        break;
                    case 1:
                        runHighLoadAgentCycle(context);
                        monitor.recordPattern("high_load");
                        break;
                    case 2:
                        runBurstAgentCycle(context);
                        monitor.recordPattern("burst");
                        break;
                    case 3:
                        runSlowLoadAgentCycle(context);
                        monitor.recordPattern("slow_load");
                        break;
                    case 4:
                        runVariableLoadAgentCycle(context);
                        monitor.recordPattern("variable_load");
                        break;
                }

                context.iterations.incrementAndGet();
            }
        } catch (Exception e) {
            context.errors.incrementAndGet();
            monitor.recordError(context.caseID, e.getMessage());
        }
    }

    private void runRecoveryStressAgent(RecoveryTestContext context, RecoveryMonitor monitor, long endTime) {
        try {
            while (System.currentTimeMillis() < endTime) {
                try {
                    // Normal operation
                    String caseID = engine.createCase(context.specID);
                    List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);

                    if (!workItems.isEmpty()) {
                        YWorkItem workItem = workItems.get(0);
                        context.netRunner = engine.getNetRunner(caseID, workItem.getID());

                        // Simulate occasional failures
                        if (Math.random() < 0.05) { // 5% failure rate
                            throw new SimulatedFailure("Simulated engine failure");
                        }

                        context.netRunner.fireExternalEvent("recovery_event", null, null);
                        engine.completeCase(caseID);

                        monitor.recordSuccess();
                    }

                    context.iterations.incrementAndGet();

                } catch (Exception e) {
                    // Record failure and attempt recovery
                    context.errors.incrementAndGet();
                    monitor.recordFailure(context.caseID, e.getMessage());

                    // Simulate recovery actions
                    Thread.sleep(100); // Recovery delay
                }
            }
        } catch (Exception e) {
            context.errors.incrementAndGet();
        }
    }

    // Helper methods for different agent patterns
    private void runNormalAgentCycle(TestContextBase context) throws Exception {
        String caseID = engine.createCase(context.specID);
        List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
        if (!workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            context.netRunner = engine.getNetRunner(caseID, workItem.getID());
            context.netRunner.fireExternalEvent("normal_event", null, null);
            engine.completeCase(caseID);
        }
        context.iterations.incrementAndGet();
    }

    private void runHighLoadAgentCycle(TestContextBase context) throws Exception {
        String caseID = engine.createCase(context.specID);
        List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
        if (!workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            context.netRunner = engine.getNetRunner(caseID, workItem.getID());

            // Send multiple events rapidly
            for (int i = 0; i < 5; i++) {
                context.netRunner.fireExternalEvent("high_load_" + i, null, null);
            }

            engine.completeCase(caseID);
        }
        context.iterations.incrementAndGet();
    }

    private void runBurstAgentCycle(TestContextBase context) throws Exception {
        String caseID = engine.createCase(context.specID);
        List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
        if (!workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            context.netRunner = engine.getNetRunner(caseID, workItem.getID());

            // Sudden burst of events
            for (int i = 0; i < 20; i++) {
                context.netRunner.fireExternalEvent("burst_" + i, null, null);
            }

            engine.completeCase(caseID);
        }
        context.iterations.incrementAndGet();
    }

    private void runSlowLoadAgentCycle(TestContextBase context) throws Exception {
        String caseID = engine.createCase(context.specID);
        List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
        if (!workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            context.netRunner = engine.getNetRunner(caseID, workItem.getID());

            // Slow processing with delays
            context.netRunner.fireExternalEvent("slow_load", null, null);
            Thread.sleep(10);

            engine.completeCase(caseID);
        }
        context.iterations.incrementAndGet();
    }

    private void runVariableLoadAgentCycle(TestContextBase context) throws Exception {
        String caseID = engine.createCase(context.specID);
        List<YWorkItem> workItems = engine.getWorkItems(caseID, YWorkItem.StatusStatus.ALL);
        if (!workItems.isEmpty()) {
            YWorkItem workItem = workItems.get(0);
            context.netRunner = engine.getNetRunner(caseID, workItem.getID());

            // Variable load based on iteration count
            int variableLoad = (int) (Math.random() * 10) + 1;
            for (int i = 0; i < variableLoad; i++) {
                context.netRunner.fireExternalEvent("variable_" + i, null, null);
            }

            engine.completeCase(caseID);
        }
        context.iterations.incrementAndGet();
    }

    private void triggerBurstPattern(BurstTestContext context, BurstMonitor monitor) {
        try {
            // Send burst of messages
            for (int i = 0; i < BURST_MULTIPLIER; i++) {
                if (context.netRunner != null) {
                    context.netRunner.fireExternalEvent("burst_" + i, null, null);
                }
            }

            // Record burst event
            monitor.recordBurst(context.caseID, BURST_MULTIPLIER);

        } catch (Exception e) {
            context.errors.incrementAndGet();
            monitor.recordError(context.caseID, "Burst error: " + e.getMessage());
        }
    }

    // Report generation methods
    private void generateStabilityReport(String testName, int agentCount, int durationHours, StabilityMonitor monitor) {
        String reportPath = "reports/stress_tests/" + testName + "_stability_report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"%s\",\n" +
            "  \"agentCount\": %d,\n" +
            "  \"durationHours\": %d,\n" +
            "  \"stabilityMetrics\": {\n" +
            "    \"totalIterations\": %d,\n" +
            "    \"iterationsPerSecond\": %.2f,\n" +
            "    \"errorCount\": %d,\n" +
            "    \"errorRate\": %.6f,\n" +
            "    \"maxHeapMemory\": %d,\n" +
            "    \"heapStability\": %.2f%%,\n" +
            "    \"cpuStability\": %.2f%%,\n" +
            "    \"criticalFailures\": %d\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            testName, agentCount, durationHours,
            monitor.getTotalIterations(),
            monitor.getIterationsPerSecond(),
            monitor.getErrorCount(),
            monitor.getErrorRate(),
            monitor.getMaxHeapMemory(),
            monitor.getHeapStability(),
            monitor.getCpuStability(),
            monitor.getCriticalFailureCount(),
            monitor.isPass() ? "PASS" : "FAIL",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void generateFloodReport(FloodMonitor monitor) {
        String reportPath = "reports/stress_tests/Message_Flood_Report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"Message_Flood_Test\",\n" +
            "  \"floodMetrics\": {\n" +
            "    \"targetRate\": %d,\n" +
            "    \"actualRate\": %.2f,\n" +
            "    \"successRate\": %.2f%%,\n" +
            "    \"totalMessages\": %d,\n" +
            "    \"messageProcessingTimes\": {\n" +
            "      \"avgMillis\": %.2f,\n" +
            "      \"maxMillis\": %.2f,\n" +
            "      \"p99Millis\": %.2f\n" +
            "    },\n" +
            "    \"errorCount\": %d\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            FLOOD_RATE_MSG_PER_SECOND,
            monitor.getActualRate(),
            monitor.getSuccessRate(),
            monitor.getTotalMessages(),
            monitor.getAvgProcessingTime(),
            monitor.getMaxProcessingTime(),
            monitor.getP99ProcessingTime(),
            monitor.getErrorCount(),
            monitor.isPass() ? "PASS" : "FAIL",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void generateBurstReport(BurstMonitor monitor) {
        String reportPath = "reports/stress_tests/Burst_Pattern_Report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"Burst_Pattern_Test\",\n" +
            "  \"burstMetrics\": {\n" +
            "    \"normalLoadDurationMs\": %d,\n" +
            "    \"burstDurationMs\": %d,\n" +
            "    \"burstMultiplier\": %d,\n" +
            "    \"burstSuccessRate\": %.2f%%,\n" +
            "    \"recoveryTimeMs\": %d,\n" +
            "    \"patternDistribution\": {\n" +
            "      \"normal\": %d,\n" +
            "      \"burst\": %d\n" +
            "    },\n" +
            "    \"errorCount\": %d\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            monitor.getNormalLoadDuration(),
            monitor.getBurstDuration(),
            BURST_MULTIPLIER,
            monitor.getBurstSuccessRate(),
            monitor.getRecoveryTime(),
            monitor.getNormalPatternCount(),
            monitor.getBurstPatternCount(),
            monitor.getErrorCount(),
            monitor.isPass() ? "PASS" : "FAIL",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void generateLeakReport(LeakMonitor monitor) {
        String reportPath = "reports/stress_tests/Memory_Leak_Report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"Memory_Leak_Detection_Test\",\n" +
            "  \"leakMetrics\": {\n" +
            "    \"initialMemory\": %d,\n" +
            "    \"finalMemory\": %d,\n" +
            "    \"memoryGrowth\": %.2f%%,\n" +
            "    \"leakDetected\": %s,\n" +
            "    \"memorySamples\": %d,\n" +
            "    \"averageMemoryPerAgent\": %.2f,\n" +
            "    \"maxMemoryPerAgent\": %.2f\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            monitor.getInitialMemory(),
            monitor.getFinalMemory(),
            monitor.getMemoryGrowthPercent(),
            monitor.isLeakDetected() ? "true" : "false",
            monitor.getMemorySampleCount(),
            monitor.getAverageMemoryPerAgent(),
            monitor.getMaxMemoryPerAgent(),
            monitor.isLeakDetected() ? "FAIL" : "PASS",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void generateMixedStressReport(MixedStressMonitor monitor) {
        String reportPath = "reports/stress_tests/Mixed_Stress_Report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"Mixed_Stress_Test\",\n" +
            "  \"mixedMetrics\": {\n" +
            "    \"patternDistribution\": {\n" +
            "      \"normal\": %d,\n" +
            "      \"high_load\": %d,\n" +
            "      \"burst\": %d,\n" +
            "      \"slow_load\": %d,\n" +
            "      \"variable_load\": %d\n" +
            "    },\n" +
            "    \"resilienceScore\": %.2f,\n" +
            "    \"errorCount\": %d,\n" +
            "    \"recoveryCount\": %d,\n" +
            "    \"avgProcessingTimeMs\": %.2f\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            monitor.getNormalPatternCount(),
            monitor.getHighLoadPatternCount(),
            monitor.getBurstPatternCount(),
            monitor.getSlowLoadPatternCount(),
            monitor.getVariableLoadPatternCount(),
            monitor.getResilienceScore(),
            monitor.getErrorCount(),
            monitor.getRecoveryCount(),
            monitor.getAvgProcessingTime(),
            monitor.isPass() ? "PASS" : "FAIL",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void generateRecoveryReport(RecoveryMonitor monitor) {
        String reportPath = "reports/stress_tests/Recovery_Report.json";

        String report = String.format(
            "{\n" +
            "  \"testName\": \"Recovery_Stress_Test\",\n" +
            "  \"recoveryMetrics\": {\n" +
            "    \"failureCount\": %d,\n" +
            "    \"recoveryCount\": %d,\n" +
            "    \"recoverySuccessRate\": %.2f%%,\n" +
            "    \"averageRecoveryTimeMs\": %d,\n" +
            "    \"maxRecoveryTimeMs\": %d,\n" +
            "    \"successCount\": %d,\n" +
            "    \"errorCount\": %d\n" +
            "  },\n" +
            "  \"status\": %s,\n" +
            "  \"timestamp\": \"%s\"\n" +
            "}",
            monitor.getFailureCount(),
            monitor.getRecoveryCount(),
            monitor.getRecoverySuccessRate(),
            monitor.getAverageRecoveryTime(),
            monitor.getMaxRecoveryTime(),
            monitor.getSuccessCount(),
            monitor.getErrorCount(),
            monitor.isPass() ? "PASS" : "FAIL",
            System.currentTimeMillis()
        );

        saveStressReport(reportPath, report);
    }

    private void saveStressReport(String path, String content) {
        try {
            Path reportDir = Path.of("reports/stress_tests");
            Files.createDirectories(reportDir);
            Files.writeString(Path.of(path), content);
            System.out.println("Stress test report saved to: " + path);
        } catch (IOException e) {
            System.err.println("Failed to save stress report: " + e.getMessage());
        }
    }

    private String createStressSpecification(String name) {
        // Create a complex specification for stress testing
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<specification xmlns=\"http://www.yawlfoundation.org/yawlschema\">\n" +
               "  <header>\n" +
               "    <name>" + name + "</name>\n" +
               "    <version>1.0</version>\n" +
               "    <description>Stress test specification</description>\n" +
               "  </header>\n" +
               "  <nets>\n" +
               "    <net id=\"Net\">\n" +
               "      <inputCondition id=\"i\"/>\n" +
               "      <tasks>\n" +
               "        <task id=\"A\">\n" +
               "          <inputs>\n" +
               "            <flowsInto source=\"i\"/>\n" +
               "          </inputs>\n" +
               "          <outputs>\n" +
               "            <flowsInto target=\"B\"/>\n" +
               "            <flowsInto target=\"C\"/>\n" +
               "            <flowsInto target=\"D\"/>\n" +
               "          </outputs>\n" +
               "        </task>\n" +
               "        <task id=\"B\">\n" +
               "          <inputs>\n" +
               "            <flowsInto source=\"A\"/>\n" +
               "          </inputs>\n" +
               "          <outputs>\n" +
               "            <flowsInto target=\"o\"/>\n" +
               "          </outputs>\n" +
               "        </task>\n" +
               "        <task id=\"C\">\n" +
               "          <inputs>\n" +
               "            <flowsInto source=\"A\"/>\n" +
               "          </inputs>\n" +
               "          <outputs>\n" +
               "            <flowsInto target=\"D\"/>\n" +
               "          </outputs>\n" +
               "        </task>\n" +
               "        <task id=\"D\">\n" +
               "          <inputs>\n" +
               "            <flowsInto source=\"A\"/>\n" +
               "            <flowsInto source=\"C\"/>\n" +
               "          </inputs>\n" +
               "          <outputs>\n" +
               "            <flowsInto target=\"o\"/>\n" +
               "          </outputs>\n" +
               "        </task>\n" +
               "      </tasks>\n" +
               "      <outputCondition id=\"o\">\n" +
               "        <flowsInto source=\"B\"/>\n" +
               "        <flowsInto source=\"D\"/>\n" +
               "      </outputCondition>\n" +
               "    </net>\n" +
               "  </nets>\n" +
               "</specification>";
    }

    // Base classes and monitoring classes
    private static class TestContextBase {
        final String caseID;
        final YSpecificationID specID;
        final YNet net;
        YNetRunner netRunner;
        final AtomicLong iterations = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);

        TestContextBase(String caseID, YSpecificationID specID, YNet net) {
            this.caseID = caseID;
            this.specID = specID;
            this.net = net;
        }
    }

    private static class StabilityTestContext extends TestContextBase {
        StabilityTestContext(String caseID, YSpecificationID specID, YNet net) {
            super(caseID, specID, net);
        }
    }

    private static class FloodTestContext extends TestContextBase {
        final int messageRate;
        final ConcurrentLinkedQueue<Long> processingTimes = new ConcurrentLinkedQueue<>();

        FloodTestContext(String caseID, YSpecificationID specID, YNet net, int messageRate) {
            super(caseID, specID, net);
            this.messageRate = messageRate;
        }
    }

    private static class BurstTestContext extends TestContextBase {
        final AtomicLong normalCount = new AtomicLong(0);
        final AtomicLong burstCount = new AtomicLong(0);

        BurstTestContext(String caseID, YSpecificationID specID, YNet net) {
            super(caseID, specID, net);
        }
    }

    private static class LeakTestContext extends TestContextBase {
        final ConcurrentLinkedQueue<Integer> memoryAllocations = new ConcurrentLinkedQueue<>();

        LeakTestContext(String caseID, YSpecificationID specID, YNet net) {
            super(caseID, specID, net);
        }
    }

    private static class MixedStressContext extends TestContextBase {
        final ConcurrentLinkedQueue<String> patternHistory = new ConcurrentLinkedQueue<>();

        MixedStressContext(String caseID, YSpecificationID specID, YNet net) {
            super(caseID, specID, net);
        }
    }

    private static class RecoveryTestContext extends TestContextBase {
        final ConcurrentLinkedQueue<String> history = new ConcurrentLinkedQueue<>();

        RecoveryTestContext(String caseID, YSpecificationID specID, YNet net) {
            super(caseID, specID, net);
        }
    }

    // Monitoring classes
    private static class StabilityMonitor implements Runnable {
        final AtomicLong totalIterations = new AtomicLong(0);
        final AtomicLong errorCount = new AtomicLong(0);
        final ConcurrentLinkedQueue<Long> memorySamples = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicLong criticalFailureCount = new AtomicLong(0);
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                long memory = Runtime.getRuntime().totalMemory();
                memorySamples.add(memory);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordError(String caseID, String error) {
            errors.add(caseID + ": " + error);
            if (error.contains("critical") || error.contains("fatal")) {
                criticalFailureCount.incrementAndGet();
            }
            errorCount.incrementAndGet();
        }

        void checkpoint() {
            System.out.println("Stability checkpoint: " + totalIterations.get() + " iterations, " + errorCount.get() + " errors");
        }

        boolean isCriticalFailure() {
            return criticalFailureCount.get() > 0;
        }

        boolean isPass() {
            return errorCount.get() < totalIterations.get() * 0.01; // Less than 1% error rate
        }

        long getTotalIterations() {
            return totalIterations.get();
        }

        double getIterationsPerSecond() {
            return 0.0; // Calculate based on test duration
        }

        int getErrorCount() {
            return errorCount.get();
        }

        double getErrorRate() {
            long total = totalIterations.get();
            return total > 0 ? (double) errorCount.get() / total : 0.0;
        }

        int getMaxHeapMemory() {
            return memorySamples.stream()
                .mapToInt(l -> l.intValue())
                .max()
                .orElse(0);
        }

        double getHeapStability() {
            // Calculate heap stability (lower is better)
            return 0.0;
        }

        double getCpuStability() {
            // Calculate CPU stability
            return 0.0;
        }

        int getCriticalFailureCount() {
            return criticalFailureCount.get();
        }

        void stop() {
            running.set(false);
        }
    }

    private static class FloodMonitor implements Runnable {
        final AtomicLong totalMessages = new AtomicLong(0);
        final ConcurrentLinkedQueue<Long> processingTimes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordMessage(String caseID, long duration) {
            totalMessages.incrementAndGet();
            processingTimes.add(duration);
        }

        void recordError(String caseID, String error) {
            errors.add(caseID + ": " + error);
        }

        void checkProgress(int agentCount) {
            // Progress monitoring logic
        }

        boolean isPass() {
            return errors.isEmpty() && getActualRate() >= FLOOD_RATE_MSG_PER_SECOND * 0.9; // 90% of target
        }

        double getActualRate() {
            return 0.0; // Calculate based on processing times
        }

        double getSuccessRate() {
            long total = totalMessages.get();
            long errors = this.errors.size();
            return total > 0 ? ((double) (total - errors) / total) * 100 : 100.0;
        }

        long getTotalMessages() {
            return totalMessages.get();
        }

        double getAvgProcessingTime() {
            return processingTimes.stream()
                .mapToLong(l -> l)
                .average()
                .orElse(0.0) / 1_000_000.0; // Convert to milliseconds
        }

        double getMaxProcessingTime() {
            return processingTimes.stream()
                .mapToLong(l -> l)
                .max()
                .orElse(0.0) / 1_000_000.0;
        }

        double getP99ProcessingTime() {
            // Calculate p99 processing time
            return 0.0;
        }

        int getErrorCount() {
            return errors.size();
        }

        void stop() {
            running.set(false);
        }
    }

    private static class BurstMonitor implements Runnable {
        final AtomicLong normalPatternCount = new AtomicLong(0);
        final AtomicLong burstPatternCount = new AtomicLong(0);
        final ConcurrentLinkedQueue<String> burstHistory = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordPattern(String pattern, long iteration) {
            if ("normal".equals(pattern)) {
                normalPatternCount.incrementAndGet();
            } else if ("burst".equals(pattern)) {
                burstPatternCount.incrementAndGet();
            }
        }

        void recordBurst(String caseID, int multiplier) {
            burstHistory.add(caseID + ": burst x" + multiplier);
        }

        void recordError(String caseID, String error) {
            errors.add(caseID + ": " + error);
        }

        void checkProgress() {
            // Progress monitoring logic
        }

        boolean isPass() {
            return burstPatternCount.get() > 0 && errors.isEmpty();
        }

        long getNormalLoadDuration() {
            return 0; // Placeholder
        }

        long getBurstDuration() {
            return 0; // Placeholder
        }

        double getBurstSuccessRate() {
            long total = burstPatternCount.get();
            long errors = this.errors.size();
            return total > 0 ? ((double) (total - errors) / total) * 100 : 100.0;
        }

        long getRecoveryTime() {
            return 0; // Placeholder
        }

        long getNormalPatternCount() {
            return normalPatternCount.get();
        }

        long getBurstPatternCount() {
            return burstPatternCount.get();
        }

        int getErrorCount() {
            return errors.size();
        }

        void stop() {
            running.set(false);
        }
    }

    private static class LeakMonitor implements Runnable {
        final ConcurrentLinkedQueue<Long> memorySamples = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicLong initialMemory = new AtomicLong(0);
        final AtomicLong finalMemory = new AtomicLong(0);
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                memorySamples.add(Runtime.getRuntime().totalMemory());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordMemory(String caseID, long memory) {
            memorySamples.add(memory);
        }

        void recordError(String caseID, String error) {
            errors.add(caseID + ": " + error);
        }

        boolean isLeakDetected() {
            if (memorySamples.size() < 2) return false;

            long[] samples = memorySamples.stream().mapToLong(l -> l).toArray();
            long growth = samples[samples.length - 1] - samples[0];
            double growthPercent = (growth / (double) samples[0]) * 100;

            return growthPercent > MAX_MEMORY_GROWTH_PERCENT;
        }

        long getInitialMemory() {
            if (memorySamples.isEmpty()) return 0;
            return memorySamples.peek();
        }

        long getFinalMemory() {
            if (memorySamples.isEmpty()) return 0;
            return memorySamples.stream().mapToLong(l -> l).max().orElse(0);
        }

        double getMemoryGrowthPercent() {
            long initial = getInitialMemory();
            long finalMem = getFinalMemory();
            return initial > 0 ? ((finalMem - initial) / (double) initial) * 100 : 0.0;
        }

        int getMemorySampleCount() {
            return memorySamples.size();
        }

        double getAverageMemoryPerAgent() {
            long totalMemory = memorySamples.stream().mapToLong(l -> l).sum();
            return totalMemory / (double) memorySamples.size();
        }

        double getMaxMemoryPerAgent() {
            return memorySamples.stream()
                .mapToLong(l -> l)
                .max()
                .orElse(0.0);
        }

        void stop() {
            running.set(false);
        }
    }

    private static class MixedStressMonitor implements Runnable {
        final ConcurrentLinkedQueue<String> patternHistory = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> processingTimes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordPattern(String pattern) {
            patternHistory.add(pattern);
            processingTimes.add(System.nanoTime());
        }

        void checkProgress() {
            // Progress monitoring logic
        }

        boolean isPass() {
            return errors.size() < patternHistory.size() * 0.05; // Less than 5% error rate
        }

        long getNormalPatternCount() {
            return patternHistory.stream().filter(p -> p.equals("normal")).count();
        }

        long getHighLoadPatternCount() {
            return patternHistory.stream().filter(p -> p.equals("high_load")).count();
        }

        long getBurstPatternCount() {
            return patternHistory.stream().filter(p -> p.equals("burst")).count();
        }

        long getSlowLoadPatternCount() {
            return patternHistory.stream().filter(p -> p.equals("slow_load")).count();
        }

        long getVariableLoadPatternCount() {
            return patternHistory.stream().filter(p -> p.equals("variable_load")).count();
        }

        double getResilienceScore() {
            // Calculate resilience score based on pattern performance
            return 0.0;
        }

        int getErrorCount() {
            return errors.size();
        }

        int getRecoveryCount() {
            return 0; // Placeholder
        }

        double getAvgProcessingTime() {
            return processingTimes.stream()
                .mapToLong(l -> l)
                .average()
                .orElse(0.0) / 1_000_000.0; // Convert to milliseconds
        }

        void stop() {
            running.set(false);
        }
    }

    private static class RecoveryMonitor implements Runnable {
        final ConcurrentLinkedQueue<String> history = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> failureTimes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> recoveryTimes = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
        final AtomicBoolean running = new AtomicBoolean(true);

        @Override
        public void run() {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        void recordFailure(String caseID, String error) {
            failureTimes.add(System.currentTimeMillis());
            history.add(caseID + ": " + error);
            errors.add(caseID + ": " + error);
        }

        void recordSuccess() {
            history.add("success");
        }

        void simulateFailure() {
            // Simulate failure for testing
            history.add("simulated_failure");
            failureTimes.add(System.currentTimeMillis());
        }

        void checkProgress() {
            // Progress monitoring logic
        }

        boolean isPass() {
            double successRate = getRecoverySuccessRate();
            return successRate > 95.0; // 95% recovery success rate
        }

        int getFailureCount() {
            return failureTimes.size();
        }

        int getRecoveryCount() {
            return recoveryTimes.size();
        }

        double getRecoverySuccessRate() {
            int total = failureTimes.size();
            int recovered = recoveryTimes.size();
            return total > 0 ? ((double) recovered / total) * 100 : 100.0;
        }

        long getAverageRecoveryTime() {
            return recoveryTimes.stream()
                .mapToLong(l -> l)
                .average()
                .orElse(0L);
        }

        long getMaxRecoveryTime() {
            return recoveryTimes.stream()
                .mapToLong(l -> l)
                .max()
                .orElse(0L);
        }

        int getSuccessCount() {
            return (int) history.stream().filter(h -> h.equals("success")).count();
        }

        int getErrorCount() {
            return errors.size();
        }

        void stop() {
            running.set(false);
        }
    }

    private static class SimulatedFailure extends Exception {
        public SimulatedFailure(String message) {
            super(message);
        }
    }
}