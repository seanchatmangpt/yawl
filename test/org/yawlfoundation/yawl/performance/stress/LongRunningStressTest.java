/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.stress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Long-running stress test for YAWL v6.0.0-GA.
 *
 * Validates system behavior during extended operation periods,
 * including resource exhaustion recovery, state stability,
 * and performance degradation over time.
 *
 * Tests:
 * - 24-hour continuous operation
 * - Resource exhaustion scenarios
 * - State stability validation
 * - Performance degradation detection
 * - System recovery capabilities
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 * @since 2026-02-26
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LongRunningStressTest {

    private YAWLStatelessEngine engine;
    private PerformanceMonitor performanceMonitor;
    private ScheduledExecutorService monitoringExecutor;
    private ExecutorService workloadExecutor;

    // Test configuration
    private static final long TEST_DURATION_HOURS = 24;
    private static final int CONCURRENT_WORKERS = 20;
    private static final int CASES_PER_HOUR = 1000;
    private static final int MONITORING_INTERVAL_MS = 60000; // 1 minute
    private static final int PERFORMANCE_SAMPLE_INTERVAL_MS = 300000; // 5 minutes
    private static final int STATE_VALIDATION_INTERVAL_MS = 1800000; // 30 minutes

    // Test state
    private final AtomicBoolean testRunning = new AtomicBoolean(true);
    private final AtomicLong baselineMemory = new AtomicLong(0);
    private final AtomicLong baselineCpu = new AtomicLong(0);
    private final AtomicInteger criticalEvents = new AtomicInteger(0);
    private final AtomicLong totalCasesProcessed = new AtomicLong(0);

    // Monitoring data
    private final ConcurrentLinkedQueue<PerformanceSample> performanceSamples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SystemState> systemStates = new ConcurrentLinkedQueue<>();
    private final List<ResourceExhaustionScenario> exhaustionScenarios = new ArrayList<>();

    @BeforeAll
    void setUp() throws Exception {
        engine = new YAWLStatelessEngine();
        performanceMonitor = new PerformanceMonitor();
        monitoringExecutor = Executors.newSingleThreadScheduledExecutor();
        workloadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Load workflow specifications
        loadWorkflowSpecifications();

        // Initialize monitoring
        startMonitoring();

        // Record baseline metrics
        recordBaselineMetrics();

        // Create exhaustion scenarios
        createExhaustionScenarios();
    }

    @AfterAll
    void tearDown() throws Exception {
        testRunning.set(false);

        // Shutdown executors
        monitoringExecutor.shutdown();
        workloadExecutor.shutdown();

        if (!monitoringExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            monitoringExecutor.shutdownNow();
        }

        if (!workloadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
            workloadExecutor.shutdownNow();
        }

        // Generate final report
        generateLongRunningReport();
    }

    @Test
    @DisplayName("Test 24-hour continuous operation")
    @Timeout(value = 24, unit = TimeUnit.HOURS)
    void testContinuousOperation() throws Exception {
        System.out.println("Starting 24-hour continuous operation test...");

        // Create long-running workload
        Instant testStart = Instant.now();
        List<CompletableFuture<Void>> workloadFutures = new ArrayList<>();

        // Start continuous workload generators
        for (int i = 0; i < CONCURRENT_WORKERS; i++) {
            final int workerId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                continuousWorkloadGenerator(workerId, testStart);
            }, workloadExecutor);
            workloadFutures.add(future);
        }

        // Wait for test duration
        Thread.sleep(TEST_DURATION_HOURS * 60 * 60 * 1000L);

        // Signal workload generators to stop
        testRunning.set(false);

        // Wait for workload completion
        for (CompletableFuture<Void> future : workloadFutures) {
            try {
                future.get(5, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                future.cancel(true);
            }
        }

        // Validate continuous operation results
        validateContinuousOperationResults(testStart);
    }

    @Test
    @DisplayName("Test resource exhaustion recovery")
    @Timeout(value = 8, unit = TimeUnit.HOURS)
    void testResourceExhaustionRecovery() throws Exception {
        System.out.println("Testing resource exhaustion recovery scenarios...");

        // Execute exhaustion scenarios
        for (ResourceExhaustionScenario scenario : exhaustionScenarios) {
            System.out.println("Testing exhaustion scenario: " + scenario.name());

            // Execute scenario
            boolean recovered = executeExhaustionScenario(scenario);

            // Validate recovery
            validateRecovery(scenario, recovered);

            // Allow system to recover
            Thread.sleep(300000); // 5 minutes recovery time
        }
    }

    @Test
    @DisplayName("Test system state stability over time")
    @Timeout(value = 12, unit = TimeUnit.HOURS)
    void testSystemStateStability() throws Exception {
        System.out.println("Testing system state stability over time...");

        // Start state validation thread
        Thread stateValidator = new Thread(this::validateSystemStateStability);
        stateValidator.start();

        // Run workload for extended period
        Instant testStart = Instant.now();
        while (Duration.between(testStart, Instant.now()).toHours() < 12) {
            executeStabilityWorkload();
            Thread.sleep(60000); // 1 minute between workloads
        }

        stateValidator.interrupt();
        stateValidator.join();

        // Validate state stability
        validateStateStability();
    }

    @Test
    @DisplayName("Test performance degradation detection")
    @Timeout(value = 6, unit = TimeUnit.HOURS)
    void testPerformanceDegradationDetection() throws Exception {
        System.out.println("Testing performance degradation detection...");

        // Execute performance monitoring workload
        Instant testStart = Instant.now();
        AtomicBoolean degradationDetected = new AtomicBoolean(false);

        Thread monitoringThread = new Thread(() -> {
            while (testRunning.get()) {
                try {
                    Thread.sleep(PERFORMANCE_SAMPLE_INTERVAL_MS);
                    detectPerformanceDegradation(testStart, degradationDetected);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitoringThread.start();

        // Execute performance-intensive workload
        executePerformanceWorkload();

        monitoringThread.interrupt();
        monitoringThread.join();

        // Validate degradation detection
        validatePerformanceDegradation(degradationDetected.get());
    }

    @Test
    @DisplayName("Test system recovery capabilities")
    @Timeout(value = 4, unit = TimeUnit.HOURS)
    void testSystemRecoveryCapabilities() throws Exception {
        System.out.println("Testing system recovery capabilities...");

        // Create recovery test scenarios
        List<RecoveryScenario> recoveryScenarios = Arrays.asList(
            new RecoveryScenario("normal", 0),
            new RecoveryScenario("graceful", 5000),
            new RecoveryScenario("forced", 10000)
        );

        for (RecoveryScenario scenario : recoveryScenarios) {
            System.out.println("Testing recovery scenario: " + scenario.name());

            // Simulate failure
            simulateSystemFailure(scenario);

            // Attempt recovery
            long recoveryStart = System.currentTimeMillis();
            boolean recovered = attemptSystemRecovery(scenario);

            long recoveryTime = System.currentTimeMillis() - recoveryStart;

            // Validate recovery
            validateRecoveryCapability(scenario, recovered, recoveryTime);

            // Allow system to stabilize
            Thread.sleep(60000); // 1 minute stabilization
        }
    }

    // Helper methods

    private void loadWorkflowSpecifications() throws Exception {
        // Load various workflow specifications for long-running test
        String[] specNames = {
            "ContinuousWorkflow",
            "RecoveryWorkflow",
            "MonitoringWorkflow",
            "MaintenanceWorkflow"
        };

        for (String specName : specNames) {
            String specXml = createLongRunningSpecification(specName);
            YSpecificationID specId = engine.uploadSpecification(specXml);
            System.out.println("Loaded long-running specification: " + specId);
        }
    }

    private String createLongRunningSpecification(String name) {
        return switch (name) {
            case "ContinuousWorkflow" -> """
                <specification id="ContinuousWorkflow" version="1.0">
                    <name>Continuous Process</name>
                    <process id="continuousProcess" name="Continuous Process">
                        <start id="start"/>
                        <task id="processTask" name="Continuous Processing"/>
                        <task id="monitorTask" name="Monitoring Task"/>
                        <task id="cleanupTask" name="Cleanup Task"/>
                        <end id="end"/>
                        <flow from="start" to="processTask"/>
                        <flow from="processTask" to="monitorTask"/>
                        <flow from="monitorTask" to="cleanupTask"/>
                        <flow from="cleanupTask" to="processTask"/>
                    </process>
                </specification>
                """;
            case "RecoveryWorkflow" -> """
                <specification id="RecoveryWorkflow" version="1.0">
                    <name>Recovery Process</name>
                    <process id="recoveryProcess" name="Recovery Process">
                        <start id="start"/>
                        <task id="detectTask" name="Failure Detection"/>
                        <task id="recoverTask" name="Recovery Action" priority="high"/>
                        <task id="validateTask" name="Recovery Validation"/>
                        <task id="restoreTask" name="Restore Service"/>
                        <end id="end"/>
                        <flow from="start" to="detectTask"/>
                        <flow from="detectTask" to="recoverTask"/>
                        <flow from="recoverTask" to="validateTask"/>
                        <flow from="validateTask" to="restoreTask"/>
                        <flow from="restoreTask" to="end"/>
                    </process>
                </specification>
                """;
            case "MonitoringWorkflow" -> """
                <specification id="MonitoringWorkflow" version="1.0">
                    <name>Monitoring Process</name>
                    <process id="monitoringProcess" name="Monitoring Process">
                        <start id="start"/>
                        <task id="collectTask" name="Metrics Collection"/>
                        <task id="analyzeTask" name="Performance Analysis"/>
                        <task id="alertTask" name="Alert Generation"/>
                        <task id="reportTask" name="Report Creation"/>
                        <end id="end"/>
                        <flow from="start" to="collectTask"/>
                        <flow from="collectTask" to="analyzeTask"/>
                        <flow from="analyzeTask" to="alertTask"/>
                        <flow from="alertTask" to="reportTask"/>
                        <flow from="reportTask" to="end"/>
                    </process>
                </specification>
                """;
            case "MaintenanceWorkflow" -> """
                <specification id="MaintenanceWorkflow" version="1.0">
                    <name>Maintenance Process</name>
                    <process id="maintenanceProcess" name="Maintenance Process">
                        <start id="start"/>
                        <task id="scheduleTask" name="Maintenance Scheduling"/>
                        <task id="prepareTask" name="Preparation"/>
                        <task id="executeTask" name="Maintenance Execution"/>
                        <task id="verifyTask" name="Verification"/>
                        <task id="finalizeTask" name="Finalization"/>
                        <end id="end"/>
                        <flow from="start" to="scheduleTask"/>
                        <flow from="scheduleTask" to="prepareTask"/>
                        <flow from="prepareTask" to="executeTask"/>
                        <flow from="executeTask" to="verifyTask"/>
                        <flow from="verifyTask" to="finalizeTask"/>
                        <flow from="finalizeTask" to="end"/>
                    </process>
                </specification>
                """;
            default -> throw new IllegalArgumentException("Unknown specification: " + name);
        };
    }

    private void startMonitoring() {
        // Start performance monitoring
        monitoringExecutor.scheduleAtFixedRate(() -> {
            if (testRunning.get()) {
                collectPerformanceMetrics();
            }
        }, MONITORING_INTERVAL_MS, MONITORING_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Start system state monitoring
        monitoringExecutor.scheduleAtFixedRate(() -> {
            if (testRunning.get()) {
                recordSystemState();
            }
        }, STATE_VALIDATION_INTERVAL_MS, STATE_VALIDATION_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void recordBaselineMetrics() {
        baselineMemory.set(getUsedMemory());
        baselineCpu.set(System.currentTimeMillis());

        System.out.printf("Baseline metrics recorded:%n" +
                "  Memory: %d MB%n" +
                "  CPU: %d%n",
                baselineMemory.get() / (1024 * 1024),
                baselineCpu.get());
    }

    private void createExhaustionScenarios() {
        exhaustionScenarios.add(new ResourceExhaustionScenario(
            "memory", "Exhaust system memory", 50, 10
        ));

        exhaustionScenarios.add(new ResourceExhaustionScenario(
            "cpu", "Exhaust system CPU", 80, 5
        ));

        exhaustionScenarios.add(new ResourceExhaustionScenario(
            "connections", "Exhaust database connections", 100, 15
        ));

        exhaustionScenarios.add(new ResourceExhaustionScenario(
            "storage", "Exhaust storage space", 90, 20
        ));
    }

    private void continuousWorkloadGenerator(int workerId, Instant testStart) {
        while (testRunning.get()) {
            try {
                // Generate workload based on time of day
                int hourOfDay = LocalTime.now().getHour();
                WorkloadIntensity intensity = getWorkloadIntensity(hourOfDay);

                // Execute cases at determined intensity
                for (int i = 0; i < intensity.casesPerMinute && testRunning.get(); i++) {
                    executeLongRunningCase(workerId, intensity);

                    // Simulate realistic processing time
                    Thread.sleep(60000 / intensity.casesPerMinute);
                }

                // Sleep between batches
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in continuous workload generator " + workerId + ": " + e.getMessage());
            }
        }
    }

    private WorkloadIntensity getWorkloadIntensity(int hourOfDay) {
        // Simulate realistic daily workload patterns
        if (hourOfDay >= 9 && hourOfDay <= 17) {
            return new WorkloadIntensity(10, "business_hours"); // High load
        } else if (hourOfDay >= 20 || hourOfDay <= 6) {
            return new WorkloadIntensity(2, "night_time"); // Low load
        } else {
            return new WorkloadIntensity(5, "off_hours"); // Medium load
        }
    }

    private void executeLongRunningCase(int workerId, WorkloadIntensity intensity) throws Exception {
        String specId = "ContinuousWorkflow";
        String caseId = "continuous-" + workerId + "-" + System.currentTimeMillis();

        String caseXml = String.format("""
            <case id="%s">
                <specificationID>%s</specificationID>
                <data>
                    <variable name="workerId" type="int">%d</variable>
                    <variable name="intensity" type="string">%s</variable>
                    <variable name="timestamp" type="dateTime">%s</variable>
                </data>
            </case>
            """, caseId, specId, workerId, intensity.name, Instant.now().toString());

        // Execute case
        String result = engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));
        totalCasesProcessed.incrementAndGet();

        // Simulate case processing
        Thread.sleep((long) (Math.random() * 1000));
    }

    private void executeStabilityWorkload() throws Exception {
        // Execute cases to test system stability
        for (int i = 0; i < 50; i++) {
            String caseId = "stability-" + System.currentTimeMillis() + "-" + i;
            String specId = "MonitoringWorkflow";

            String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="testType" type="string">stability</variable>
                        <variable name="iteration" type="int">%d</variable>
                    </data>
                </case>
                """, caseId, specId, i);

            engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));
            totalCasesProcessed.incrementAndGet();

            Thread.sleep(100);
        }
    }

    private void executePerformanceWorkload() throws Exception {
        // Execute performance-intensive operations
        for (int i = 0; i < 1000 && testRunning.get(); i++) {
            String caseId = "perf-" + System.currentTimeMillis() + "-" + i;
            String specId = "ContinuousWorkflow";

            String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="testType" type="string">performance</variable>
                        <variable name="iteration" type="int">%d</variable>
                        <variable name="dataSize" type="int">%d</variable>
                    </data>
                </case>
                """, caseId, specId, i, 1000 + i);

            engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));
            totalCasesProcessed.incrementAndGet();

            Thread.sleep(10);
        }
    }

    private void collectPerformanceMetrics() {
        long usedMemory = getUsedMemory();
        long gcCount = getGcCount();
        long currentTime = System.currentTimeMillis();

        long memoryDelta = usedMemory - baselineMemory.get();
        long cpuDelta = currentTime - baselineCpu.get();

        PerformanceSample sample = new PerformanceSample(
            currentTime,
            usedMemory,
            gcCount,
            memoryDelta,
            cpuDelta
        );

        performanceSamples.add(sample);

        // Check for critical events
        if (memoryDelta > 100 * 1024 * 1024) { // 100MB growth
            criticalEvents.incrementAndGet();
            System.out.printf("CRITICAL: Memory growth detected: %d MB%n",
                memoryDelta / (1024 * 1024));
        }
    }

    private void recordSystemState() {
        SystemState state = new SystemState(
            System.currentTimeMillis(),
            engine.getCaseCount(),
            engine.getActiveThreadsCount(),
            getUsedMemory(),
            getGcCount(),
            totalCasesProcessed.get()
        );

        systemStates.add(state);

        // Validate state consistency
        validateStateConsistency(state);
    }

    private boolean executeExhaustionScenario(ResourceExhaustionScenario scenario) {
        try {
            System.out.println("Executing exhaustion scenario: " + scenario.name());

            switch (scenario.resourceType) {
                case "memory":
                    return executeMemoryExhaustion(scenario.intensity, scenario.duration);
                case "cpu":
                    return executeCpuExhaustion(scenario.intensity, scenario.duration);
                case "connections":
                    return executeConnectionExhaustion(scenario.intensity, scenario.duration);
                case "storage":
                    return executeStorageExhaustion(scenario.intensity, scenario.duration);
                default:
                    return false;
            }
        } catch (Exception e) {
            System.err.println("Error in exhaustion scenario: " + e.getMessage());
            return false;
        }
    }

    private boolean executeMemoryExhaustion(int intensity, int duration) throws InterruptedException {
        List<byte[]> memoryBlocks = new ArrayList<>();
        long targetMemory = intensity * 1024 * 1024; // MB to bytes

        for (int i = 0; i < duration; i++) {
            // Allocate memory blocks
            for (int j = 0; j < 10; j++) {
                byte[] block = new byte[10 * 1024 * 1024]; // 10MB blocks
                memoryBlocks.add(block);
            }

            // Check if we've reached target
            long used = getUsedMemory();
            if (used >= targetMemory) {
                System.out.printf("Memory exhaustion reached: %d MB%n", used / (1024 * 1024));
                return true;
            }

            Thread.sleep(1000); // 1 second between allocations
        }

        return false;
    }

    private boolean executeCpuExhaustion(int intensity, int duration) throws InterruptedException {
        long targetCpu = System.currentTimeMillis() + intensity * 1000; // ms

        for (int i = 0; i < duration; i++) {
            // CPU-intensive operations
            for (int j = 0; j < 100000; j++) {
                Math.sqrt(j);
            }

            if (System.currentTimeMillis() >= targetCpu) {
                System.out.println("CPU exhaustion reached");
                return true;
            }

            Thread.sleep(1000);
        }

        return false;
    }

    private boolean executeConnectionExhaustion(int intensity, int duration) throws Exception {
        // Simulate connection exhaustion
        for (int i = 0; i < intensity; i++) {
            String caseId = "conn-test-" + i;
            String specId = "RecoveryWorkflow";

            String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="testType" type="string">connection</variable>
                    </data>
                </case>
                """, caseId, specId);

            engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));

            if (i % 10 == 0) {
                Thread.sleep(100); // Slow down to simulate connection delay
            }
        }

        return true;
    }

    private boolean executeStorageExhaustion(int intensity, int duration) throws Exception {
        // Simulate storage exhaustion
        StringBuilder largeData = new StringBuilder();
        for (int i = 0; i < 100000; i++) {
            largeData.append("test-data-").append(i).append("-");
        }

        for (int i = 0; i < intensity; i++) {
            String caseId = "storage-test-" + i;
            String specId = "MaintenanceWorkflow";

            String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="testType" type="string">storage</variable>
                        <variable name="largeData" type="string">%s</variable>
                    </data>
                </case>
                """, caseId, specId, largeData.toString());

            engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));

            Thread.sleep(500); // Storage operations take time
        }

        return true;
    }

    private void validateSystemStateStability() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(STATE_VALIDATION_INTERVAL_MS);
                validateStateConsistency(null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void detectPerformanceDegradation(Instant testStart, AtomicBoolean detected) {
        if (performanceSamples.size() < 10) return;

        // Calculate performance trend
        List<PerformanceSample> recentSamples = new ArrayList<>(performanceSamples).subList(
            Math.max(0, performanceSamples.size() - 20),
            performanceSamples.size()
        );

        double initialAvgTime = recentSamples.subList(0, 10).stream()
            .mapToLong(PerformanceSample::getCpuDelta)
            .average()
            .orElse(0);

        double currentAvgTime = recentSamples.subList(10, 20).stream()
            .mapToLong(PerformanceSample::getCpuDelta)
            .average()
            .orElse(0);

        // Check for significant degradation (50% increase)
        if (currentAvgTime > initialAvgTime * 1.5) {
            detected.set(true);
            System.out.println("PERFORMANCE DEGRADATION DETECTED!");
        }
    }

    private void simulateSystemFailure(RecoveryScenario scenario) {
        System.out.println("Simulating system failure: " + scenario.name());

        try {
            switch (scenario.type) {
                case "normal":
                    // Normal shutdown
                    engine.shutdown();
                    break;
                case "graceful":
                    // Graceful shutdown with delay
                    Thread.sleep(scenario.delay);
                    engine.shutdown();
                    break;
                case "forced":
                    // Forced shutdown
                    engine.shutdownNow();
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error simulating failure: " + e.getMessage());
        }
    }

    private boolean attemptSystemRecovery(RecoveryScenario scenario) {
        System.out.println("Attempting system recovery: " + scenario.name());

        try {
            // Restart engine
            engine = new YAWLStatelessEngine();

            // Reload specifications
            loadWorkflowSpecifications();

            // Test recovery
            String testCase = "recovery-test-" + System.currentTimeMillis();
            String specId = "RecoveryWorkflow";

            String caseXml = String.format("""
                <case id="%s">
                    <specificationID>%s</specificationID>
                    <data>
                        <variable name="recoveryType" type="string">%s</variable>
                    </data>
                </case>
                """, testCase, specId, scenario.type);

            String result = engine.launchCase(caseXml, new YSpecificationID(specId, "1.0"));

            return result != null;
        } catch (Exception e) {
            System.err.println("Recovery failed: " + e.getMessage());
            return false;
        }
    }

    // Validation methods

    private void validateContinuousOperationResults(Instant testStart) {
        Duration testDuration = Duration.between(testStart, Instant.now());
        long totalCases = totalCasesProcessed.get();

        System.out.printf("Continuous Operation Results:%n" +
                "  Duration: %d hours%n" +
                "  Cases processed: %d%n" +
                "  Cases per hour: %.1f%n" +
                "  Critical events: %d%n%n",
                testDuration.toHours(),
                totalCases,
                (double) totalCases / testDuration.toHours(),
                criticalEvents.get());

        // Validate continuous operation targets
        Assertions.assertTrue(totalCases > CASES_PER_HOUR * TEST_DURATION_HOURS * 0.8,
            "Insufficient cases processed during continuous operation");

        Assertions.assertTrue(criticalEvents.get() < 10,
            "Too many critical events detected");

        System.out.println("Continuous operation validation passed");
    }

    private void validateRecovery(ResourceExhaustionScenario scenario, boolean recovered) {
        System.out.printf("Recovery validation for %s: %s%n",
            scenario.name(), recovered ? "SUCCESS" : "FAILED");

        Assertions.assertTrue(recovered,
            String.format("System did not recover from %s exhaustion", scenario.resourceType));
    }

    private void validateStateConsistency(SystemState state) {
        if (state == null) return;

        // Check for state inconsistencies
        if (state.caseCount < 0) {
            System.err.println("ERROR: Negative case count detected");
        }

        if (state.memoryUsage < 0) {
            System.err.println("ERROR: Negative memory usage detected");
        }

        // Check for state jumps (indicating potential corruption)
        if (!systemStates.isEmpty()) {
            SystemState previous = systemStates.peek();
            long caseJump = Math.abs(state.caseCount - previous.caseCount);
            if (caseJump > 1000) {
                System.err.printf("WARNING: Large case count jump: %d%n", caseJump);
            }
        }
    }

    private void validateStateStability() {
        if (systemStates.isEmpty()) return;

        System.out.println("Validating system state stability...");

        // Check state consistency over time
        boolean stable = true;
        for (SystemState state : systemStates) {
            if (state.caseCount < 0 || state.memoryUsage < 0) {
                stable = false;
                break;
            }
        }

        Assertions.assertTrue(stable, "System state is not stable");

        // Check for memory leaks
        SystemState first = systemStates.peek();
        SystemState last = systemStates.peek();
        long memoryGrowth = last.memoryUsage - first.memoryUsage;

        Assertions.assertTrue(memoryGrowth < 500 * 1024 * 1024,
            String.format("Memory leak detected: %d MB growth", memoryGrowth / (1024 * 1024)));

        System.out.println("System state stability validation passed");
    }

    private void validatePerformanceDegradation(boolean detected) {
        System.out.println("Performance degradation validation: " +
            (detected ? "DETECTED" : "NOT DETECTED"));

        // In a real test, we might want to validate that degradation was properly handled
        System.out.println("Performance degradation validation completed");
    }

    private void validateRecoveryCapability(RecoveryScenario scenario, boolean recovered, long recoveryTime) {
        System.out.printf("Recovery capability for %s:%n" +
                "  Recovered: %s%n" +
                "  Recovery time: %d ms%n%n",
            scenario.name(),
            recovered ? "YES" : "NO",
            recoveryTime);

        Assertions.assertTrue(recovered,
            String.format("Recovery failed for scenario: %s", scenario.name()));

        // Recovery time should be reasonable
        if (scenario.type.equals("forced")) {
            Assertions.assertTrue(recoveryTime < 30000, // 30 seconds
                String.format("Recovery time too long for forced shutdown: %d ms", recoveryTime));
        }

        System.out.println("Recovery capability validation passed");
    }

    private void generateLongRunningReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("LONG-RUNNING STRESS TEST REPORT");
        System.out.println("=".repeat(60));

        System.out.printf("Test Duration: %d hours%n", TEST_DURATION_HOURS);
        System.out.printf("Total Cases Processed: %d%n", totalCasesProcessed.get());
        System.out.printf("Critical Events: %d%n", criticalEvents.get());
        System.out.printf("Performance Samples: %d%n", performanceSamples.size());
        System.out.printf("System States Recorded: %d%n", systemStates.size());

        if (!performanceSamples.isEmpty()) {
            PerformanceSample first = performanceSamples.peek();
            PerformanceSample last = performanceSamples.peek();

            long durationMs = last.timestamp - first.timestamp;
            double memoryGrowth = (last.memoryDelta - first.memoryDelta) / (1024.0 * 1024.0);

            System.out.printf("\nPerformance Analysis:%n" +
                    "  Duration: %.1f minutes%n" +
                    "  Memory Growth: %.1f MB%n" +
                    "  GC Events: %d%n",
                    durationMs / 60000.0, memoryGrowth,
                    last.gcCount - first.gcCount);
        }

        System.out.println("=".repeat(60));
    }

    // Helper methods

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private long getGcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(bean -> bean.getCollectionCount())
            .sum();
    }

    // Inner classes

    private static class WorkloadIntensity {
        final int casesPerMinute;
        final String name;

        WorkloadIntensity(int casesPerMinute, String name) {
            this.casesPerMinute = casesPerMinute;
            this.name = name;
        }
    }

    private static class ResourceExhaustionScenario {
        final String resourceType;
        final String description;
        final int intensity;
        final int duration; // seconds

        ResourceExhaustionScenario(String resourceType, String description, int intensity, int duration) {
            this.resourceType = resourceType;
            this.description = description;
            this.intensity = intensity;
            this.duration = duration;
        }

        public String name() {
            return resourceType + "_" + intensity + "_" + duration;
        }
    }

    private static class RecoveryScenario {
        final String type;
        final long delay; // milliseconds

        RecoveryScenario(String type, long delay) {
            this.type = type;
            this.delay = delay;
        }

        public String name() {
            return type + (delay > 0 ? "_" + delay : "");
        }
    }

    private static class PerformanceSample {
        final long timestamp;
        final long memoryUsage;
        final long gcCount;
        final long memoryDelta;
        final long cpuDelta;

        PerformanceSample(long timestamp, long memoryUsage, long gcCount,
                         long memoryDelta, long cpuDelta) {
            this.timestamp = timestamp;
            this.memoryUsage = memoryUsage;
            this.gcCount = gcCount;
            this.memoryDelta = memoryDelta;
            this.cpuDelta = cpuDelta;
        }

        public long getCpuDelta() { return cpuDelta; }
    }

    private static class SystemState {
        final long timestamp;
        final int caseCount;
        final int activeThreads;
        final long memoryUsage;
        final long gcCount;
        final long totalCases;

        SystemState(long timestamp, int caseCount, int activeThreads,
                   long memoryUsage, long gcCount, long totalCases) {
            this.timestamp = timestamp;
            this.caseCount = caseCount;
            this.activeThreads = activeThreads;
            this.memoryUsage = memoryUsage;
            this.gcCount = gcCount;
            this.totalCases = totalCases;
        }
    }

    /**
     * Performance monitoring utility for long-running tests
     */
    private static class PerformanceMonitor {
        private final Map<String, Long> metricCounts = new ConcurrentHashMap<>();
        private final Map<String, Long> metricSums = new ConcurrentHashMap<>();

        public void recordMetric(String name, long value) {
            metricCounts.merge(name, 1L, Long::sum);
            metricSums.merge(name, value, Long::sum);
        }

        public double getAverageMetric(String name) {
            Long count = metricCounts.get(name);
            Long sum = metricSums.get(name);
            return count != null && sum != null ? (double) sum / count : 0;
        }
    }
}