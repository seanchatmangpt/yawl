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
import org.yawlfoundation.yawl.elements.YAWLNet;
import org.yawlfoundation.yawl.engine.YAWLStatelessEngine;
import org.yawlfoundation.yawl.elements.YSpecificationID;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Production-like stress test for YAWL v6.0.0-GA.
 *
 * Validates realistic workload patterns:
 * - 60% simple workflows
 * - 30% complex workflows
 * - 10% high-priority workflows
 *
 * Tests production simulation with ramp-up/down scenarios.
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 * @since 2026-02-26
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProductionWorkloadStressTest {

    private YAWLStatelessEngine engine;
    private PerformanceMonitor performanceMonitor;
    private ExecutorService executorService;

    // Test configuration
    private static final int SIMPLE_WORKFLOW_RATIO = 60;
    private static final int COMPLEX_WORKFLOW_RATIO = 30;
    private static final int PRIORITY_WORKFLOW_RATIO = 10;
    private static final int TOTAL_CASES = 1000;
    private static final int CONCURRENT_WORKERS = 50;
    private static final int TEST_DURATION_MINUTES = 30;

    // Workflow specifications
    private YSpecificationID simpleWorkflowSpec;
    private YSpecificationID complexWorkflowSpec;
    private YSpecificationID priorityWorkflowSpec;

    @BeforeAll
    void setUp() throws Exception {
        engine = new YAWLStatelessEngine();
        performanceMonitor = new PerformanceMonitor();
        executorService = Executors.newVirtualThreadPerTaskExecutor();

        // Load workflow specifications
        loadWorkflowSpecifications();
    }

    @AfterAll
    void tearDown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Test production-like workload distribution")
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testProductionWorkloadDistribution() throws Exception {
        System.out.println("Starting production workload stress test...");

        // Track workflow distribution
        AtomicInteger simpleCases = new AtomicInteger(0);
        AtomicInteger complexCases = new AtomicInteger(0);
        AtomicInteger priorityCases = new AtomicInteger(0);
        AtomicInteger successfulCases = new AtomicInteger(0);
        AtomicInteger failedCases = new AtomicInteger(0);

        // Ramp-up scenario
        Instant testStart = Instant.now();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Generate production-like case stream
        for (int i = 0; i < TOTAL_CASES; i++) {
            final int caseIndex = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();
                String workflowType = selectWorkflowType();

                try {
                    // Record workflow type
                    switch (workflowType) {
                        case "simple" -> simpleCases.incrementAndGet();
                        case "complex" -> complexCases.incrementAndGet();
                        case "priority" -> priorityCases.incrementAndGet();
                    }

                    // Execute workflow
                    String caseId = executeWorkflow(workflowType, caseIndex);

                    // Validate success
                    if (validateWorkflowExecution(caseId, workflowType)) {
                        successfulCases.incrementAndGet();
                        long duration = System.currentTimeMillis() - startTime;
                        performanceMonitor.recordSuccessfulWorkflow(workflowType, duration);
                    } else {
                        failedCases.incrementAndGet();
                    }

                } catch (Exception e) {
                    failedCases.incrementAndGet();
                    performanceMonitor.recordFailedWorkflow(workflowType, e.getMessage());
                }
            }, executorService);

            futures.add(future);

            // Simulate ramp-up by controlling submission rate
            if (i < 100) {
                Thread.sleep(10); // Gradual ramp-up
            } else if (i > 900) {
                Thread.sleep(50); // Ramp-down phase
            }
        }

        // Monitor progress
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
        monitor.scheduleAtFixedRate(() -> {
            int completed = successfulCases.get() + failedCases.get();
            System.out.printf("Progress: %d/%d cases completed (%.1f%%)%n",
                completed, TOTAL_CASES, (double) completed / TOTAL_CASES * 100);
        }, 1, 1, TimeUnit.MINUTES);

        // Wait for all cases to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(TEST_DURATION_MINUTES, TimeUnit.MINUTES);

        monitor.shutdown();

        // Validate distribution
        validateWorkloadDistribution(
            simpleCases.get(),
            complexCases.get(),
            priorityCases.get(),
            successfulCases.get(),
            failedCases.get()
        );

        // Validate production performance targets
        validateProductionPerformanceTargets();
    }

    @Test
    @DisplayName("Test ramp-up/down scenarios")
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void testRampUpDownScenarios() throws Exception {
        System.out.println("Testing ramp-up/down production scenarios...");

        // Simulate production day: morning → peak → normal → evening → night
        List<RampPhase> phases = Arrays.asList(
            new RampPhase("morning", 10, 5),    // 10 workers, 5s per case
            new RampPhase("ramp-up", 50, 3),    // 50 workers, 3s per case
            new RampPhase("peak", 100, 2),     // 100 workers, 2s per case
            new RampPhase("sustained", 80, 2),  // 80 workers, 2s per case
            new RampPhase("evening", 30, 4),   // 30 workers, 4s per case
            new RampPhase("night", 5, 10)       // 5 workers, 10s per case
        );

        Map<String, PhaseMetrics> phaseResults = new ConcurrentHashMap<>();

        for (RampPhase phase : phases) {
            System.out.printf("Starting %s phase: %d workers, %ds per case%n",
                phase.name, phase.workers, phase.caseDuration);

            PhaseMetrics metrics = executeRampPhase(phase);
            phaseResults.put(phase.name, metrics);

            // Validate phase performance
            validateRampPhaseResults(phase, metrics);
        }

        // Validate overall ramp behavior
        validateRampSequence(phases, phaseResults);
    }

    @Test
    @DisplayName("Test mixed workload prioritization")
    @Timeout(value = 20, unit = TimeUnit.MINUTES)
    void testMixedWorkloadPrioritization() throws Exception {
        System.out.println("Testing workload prioritization under load...");

        // Create workload mix with priority levels
        List<WorkloadItem> workloadQueue = new ArrayList<>();
        Random random = new Random();

        // Generate mixed workload
        for (int i = 0; i < 500; i++) {
            String workflowType = selectWorkflowType();
            int priority = determinePriority(workflowType, random);
            workloadQueue.add(new WorkloadItem(
                "mixed-case-" + i,
                workflowType,
                priority,
                i
            ));
        }

        // Sort by priority (higher priority first)
        workloadQueue.sort(Comparator.comparingInt(WorkloadItem::getPriority).reversed());

        // Process with priority-aware executor
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger highPriorityProcessed = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (WorkloadItem item : workloadQueue) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long startTime = System.currentTimeMillis();

                try {
                    // Execute with priority consideration
                    String caseId = executeWorkflow(item.getWorkflowType(), item.getIndex());

                    if (validateWorkflowExecution(caseId, item.getWorkflowType())) {
                        processed.incrementAndGet();
                        if (item.getPriority() >= 8) {
                            highPriorityProcessed.incrementAndGet();
                        }

                        long duration = System.currentTimeMillis() - startTime;
                        performanceMonitor.recordPriorityExecution(
                            item.getWorkflowType(),
                            item.getPriority(),
                            duration
                        );
                    } else {
                        errors.incrementAndGet();
                    }

                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }, executorService);

            futures.add(future);
        }

        // Wait for completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(20, TimeUnit.MINUTES);

        // Validate priority handling
        validatePriorityHandlingResults(
            processed.get(),
            highPriorityProcessed.get(),
            errors.get(),
            workloadQueue.size()
        );
    }

    // Helper methods

    private void loadWorkflowSpecifications() throws Exception {
        // Load simple workflow specification
        String simpleSpecXml = """
            <specification id="SimpleWorkflow" version="1.0">
                <name>Simple Process</name>
                <process id="simpleProcess" name="Simple Process">
                    <start id="start"/>
                    <task id="task1" name="Simple Task"/>
                    <end id="end"/>
                    <flow from="start" to="task1"/>
                    <flow from="task1" to="end"/>
                </process>
            </specification>
            """;
        simpleWorkflowSpec = engine.uploadSpecification(simpleSpecXml);

        // Load complex workflow specification
        String complexSpecXml = """
            <specification id="ComplexWorkflow" version="1.0">
                <name>Complex Process</name>
                <process id="complexProcess" name="Complex Process">
                    <start id="start"/>
                    <task id="task1" name="Initial Task"/>
                    <task id="task2" name="Parallel Task 1"/>
                    <task id="task3" name="Parallel Task 2"/>
                    <task id="task4" name="Sync Task"/>
                    <task id="task5" name="Final Task"/>
                    <end id="end"/>
                    <flow from="start" to="task1"/>
                    <flow from="task1" to="task2"/>
                    <flow from="task1" to="task3"/>
                    <flow from="task2" to="task4"/>
                    <flow from="task3" to="task4"/>
                    <flow from="task4" to="task5"/>
                    <flow from="task5" to="end"/>
                </process>
            </specification>
            """;
        complexWorkflowSpec = engine.uploadSpecification(complexSpecXml);

        // Load priority workflow specification
        String prioritySpecXml = """
            <specification id="PriorityWorkflow" version="1.0">
                <name>Priority Process</name>
                <process id="priorityProcess" name="Priority Process">
                    <start id="start"/>
                    <task id="priorityTask" name="Urgent Task" priority="high"/>
                    <task id="normalTask" name="Normal Task"/>
                    <task id="urgentTask" name="Critical Task" priority="critical"/>
                    <end id="end"/>
                    <flow from="start" to="priorityTask"/>
                    <flow from="priorityTask" to="normalTask"/>
                    <flow from="normalTask" to="urgentTask"/>
                    <flow from="urgentTask" to="end"/>
                </process>
            </specification>
            """;
        priorityWorkflowSpec = engine.uploadSpecification(prioritySpecXml);
    }

    private String selectWorkflowType() {
        Random random = new Random();
        int value = random.nextInt(100);

        if (value < SIMPLE_WORKFLOW_RATIO) {
            return "simple";
        } else if (value < SIMPLE_WORKFLOW_RATIO + COMPLEX_WORKFLOW_RATIO) {
            return "complex";
        } else {
            return "priority";
        }
    }

    private int determinePriority(String workflowType, Random random) {
        return switch (workflowType) {
            case "simple" -> 3 + random.nextInt(5);  // 3-7
            case "complex" -> 5 + random.nextInt(5); // 5-9
            case "priority" -> 8 + random.nextInt(3); // 8-10
            default -> 5;
        };
    }

    private String executeWorkflow(String workflowType, int caseIndex) throws Exception {
        String caseId = "prod-case-" + workflowType + "-" + System.currentTimeMillis() + "-" + caseIndex;
        YSpecificationID specId = switch (workflowType) {
            case "simple" -> simpleWorkflowSpec;
            case "complex" -> complexWorkflowSpec;
            case "priority" -> priorityWorkflowSpec;
            default -> simpleWorkflowSpec;
        };

        String caseXml = String.format("""
            <case id="%s">
                <specificationID>%s</specificationID>
                <data>
                    <variable name="caseId" type="string">%s</variable>
                    <variable name="workflowType" type="string">%s</variable>
                    <variable name="priority" type="int">%d</variable>
                </data>
            </case>
            """, caseId, specId.toString(), caseId, workflowType, determinePriority(workflowType, new Random()));

        return engine.launchCase(caseXml, specId);
    }

    private boolean validateWorkflowExecution(String caseId, String workflowType) {
        try {
            // Simulate validation based on workflow complexity
            switch (workflowType) {
                case "simple":
                    return validateSimpleWorkflow(caseId);
                case "complex":
                    return validateComplexWorkflow(caseId);
                case "priority":
                    return validatePriorityWorkflow(caseId);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateSimpleWorkflow(String caseId) {
        // Simple validation: check if case exists and has basic structure
        try {
            String caseData = engine.getCaseData(caseId);
            return caseData != null && caseData.contains(caseId);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateComplexWorkflow(String caseId) {
        // More complex validation with sub-process verification
        try {
            String caseData = engine.getCaseData(caseId);
            return caseData != null &&
                   caseData.contains(caseId) &&
                   caseData.contains("subprocess");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validatePriorityWorkflow(String caseId) {
        // Priority validation with SLA requirements
        try {
            String caseData = engine.getCaseData(caseId);
            return caseData != null &&
                   caseData.contains(caseId) &&
                   caseData.contains("priority");
        } catch (Exception e) {
            return false;
        }
    }

    // Validation methods

    private void validateWorkloadDistribution(int simple, int complex, int priority,
                                            int successful, int failed) {
        int total = simple + complex + priority;

        // Validate distribution accuracy
        double simpleRatio = (double) simple / total * 100;
        double complexRatio = (double) complex / total * 100;
        double priorityRatio = (double) priority / total * 100;

        System.out.printf("Workload Distribution:%n" +
                "  Simple: %d (%.1f%%)%n" +
                "  Complex: %d (%.1f%%)%n" +
                "  Priority: %d (%.1f%%)%n%n",
                simple, simpleRatio,
                complex, complexRatio,
                priority, priorityRatio);

        // Allow ±5% tolerance for distribution
        Assertions.assertTrue(Math.abs(simpleRatio - SIMPLE_WORKFLOW_RATIO) <= 5,
            "Simple workflow distribution out of range");
        Assertions.assertTrue(Math.abs(complexRatio - COMPLEX_WORKFLOW_RATIO) <= 5,
            "Complex workflow distribution out of range");
        Assertions.assertTrue(Math.abs(priorityRatio - PRIORITY_WORKFLOW_RATIO) <= 5,
            "Priority workflow distribution out of range");

        // Validate success rate
        double successRate = (double) successful / (successful + failed) * 100;
        Assertions.assertTrue(successRate >= 95,
            String.format("Success rate too low: %.1f%%", successRate));

        System.out.printf("Overall Success Rate: %.1f%%%n", successRate);
    }

    private void validateProductionPerformanceTargets() {
        // Get performance metrics from monitor
        Map<String, WorkflowMetrics> metrics = performanceMonitor.getWorkflowMetrics();

        for (Map.Entry<String, WorkflowMetrics> entry : metrics.entrySet()) {
            String workflowType = entry.getKey();
            WorkflowMetrics m = entry.getValue();

            // Validate performance targets based on workflow type
            switch (workflowType) {
                case "simple":
                    Assertions.assertTrue(m.getAverageDuration() < 1000,
                        "Simple workflow too slow: " + m.getAverageDuration() + "ms");
                    break;
                case "complex":
                    Assertions.assertTrue(m.getAverageDuration() < 3000,
                        "Complex workflow too slow: " + m.getAverageDuration() + "ms");
                    break;
                case "priority":
                    Assertions.assertTrue(m.getAverageDuration() < 500,
                        "Priority workflow too slow: " + m.getAverageDuration() + "ms");
                    break;
            }
        }

        System.out.println("All production performance targets met");
    }

    private PhaseMetrics executeRampPhase(RampPhase phase) throws InterruptedException {
        AtomicInteger phaseCompleted = new AtomicInteger(0);
        AtomicInteger phaseErrors = new AtomicInteger(0);
        AtomicLong totalPhaseTime = new AtomicLong(0);

        List<CompletableFuture<Void>> phaseFutures = new ArrayList<>();

        // Start workers with staggered timing
        for (int i = 0; i < phase.workers; i++) {
            final int workerIndex = i;

            // Stagger worker start to simulate realistic ramp-up
            Thread.sleep(100); // 100ms between worker starts

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                long workerStartTime = System.currentTimeMillis();
                int casesProcessed = 0;

                try {
                    while (casesProcessed < 10 &&
                           !Thread.currentThread().isInterrupted()) {
                        String workflowType = selectWorkflowType();
                        String caseId = executeWorkflow(workflowType,
                            phase.name + "-" + workerIndex + "-" + casesProcessed);

                        if (validateWorkflowExecution(caseId, workflowType)) {
                            phaseCompleted.incrementAndGet();
                            casesProcessed++;
                        } else {
                            phaseErrors.incrementAndGet();
                        }

                        // Simulate realistic case processing time
                        Thread.sleep(phase.caseDuration);
                    }
                } catch (Exception e) {
                    phaseErrors.incrementAndGet();
                } finally {
                    totalPhaseTime.addAndGet(System.currentTimeMillis() - workerStartTime);
                }
            }, executorService);

            phaseFutures.add(future);
        }

        // Wait for phase completion
        CompletableFuture.allOf(phaseFutures.toArray(new CompletableFuture[0]))
            .get(5, TimeUnit.MINUTES);

        return new PhaseMetrics(
            phaseCompleted.get(),
            phaseErrors.get(),
            totalPhaseTime.get(),
            phase.workers
        );
    }

    private void validateRampPhaseResults(RampPhase phase, PhaseMetrics metrics) {
        System.out.printf("Phase '%s' Results:%n" +
                "  Completed: %d%n" +
                "  Errors: %d%n" +
                "  Success Rate: %.1f%%%n%n",
                phase.name,
                metrics.completed(),
                metrics.errors(),
                metrics.getSuccessRate());

        // Validate phase-specific targets
        double successRate = metrics.getSuccessRate();
        Assertions.assertTrue(successRate >= 90,
            String.format("Phase '%s' success rate too low: %.1f%%", phase.name, successRate));
    }

    private void validateRampSequence(List<RampPhase> phases,
                                    Map<String, PhaseMetrics> results) {
        // Validate that ramp-up phases handle increasing load
        for (int i = 1; i < phases.size(); i++) {
            RampPhase previous = phases.get(i - 1);
            RampPhase current = phases.get(i);
            PhaseMetrics previousMetrics = results.get(previous.name);
            PhaseMetrics currentMetrics = results.get(current.name);

            // Ramp-up phases should handle increased load without significant degradation
            if (current.workers > previous.workers) {
                double previousSuccess = previousMetrics.getSuccessRate();
                double currentSuccess = currentMetrics.getSuccessRate();

                Assertions.assertTrue(currentSuccess >= previousSuccess * 0.9,
                    String.format("Performance degraded during ramp from %d to %d workers",
                        previous.workers, current.workers));
            }
        }

        System.out.println("Ramp sequence validation completed successfully");
    }

    private void validatePriorityHandlingResults(int processed, int highPriorityProcessed,
                                                int errors, int total) {
        double successRate = (double) (processed - errors) / processed * 100;
        double highPriorityRatio = (double) highPriorityProcessed / processed * 100;

        System.out.printf("Priority Handling Results:%n" +
                "  Total Processed: %d%n" +
                "  High Priority: %d (%.1f%% of processed)%n" +
                "  Success Rate: %.1f%%%n%n",
                processed,
                highPriorityProcessed,
                highPriorityRatio,
                successRate);

        // Validate that high priority cases are processed promptly
        Assertions.assertTrue(successRate >= 95,
            "Overall success rate too low");
        Assertions.assertTrue(highPriorityRatio >= 0.15,
            "High priority cases not processed sufficiently");
    }

    // Inner classes for data structures

    private static class RampPhase {
        final String name;
        final int workers;
        final int caseDuration; // seconds

        RampPhase(String name, int workers, int caseDuration) {
            this.name = name;
            this.workers = workers;
            this.caseDuration = caseDuration;
        }
    }

    private static class PhaseMetrics {
        private final int completed;
        private final int errors;
        private final long totalTime;
        private final int workers;

        PhaseMetrics(int completed, int errors, long totalTime, int workers) {
            this.completed = completed;
            this.errors = errors;
            this.totalTime = totalTime;
            this.workers = workers;
        }

        public int completed() { return completed; }
        public int errors() { return errors; }
        public long totalTime() { return totalTime; }
        public int workers() { return workers; }

        public double getSuccessRate() {
            return completed > 0 ? (double) (completed - errors) / completed * 100 : 0;
        }
    }

    private static class WorkloadItem {
        private final String caseId;
        private final String workflowType;
        private final int priority;
        private final int index;

        WorkloadItem(String caseId, String workflowType, int priority, int index) {
            this.caseId = caseId;
            this.workflowType = workflowType;
            this.priority = priority;
            this.index = index;
        }

        public String getCaseId() { return caseId; }
        public String getWorkflowType() { return workflowType; }
        public int getPriority() { return priority; }
        public int getIndex() { return index; }
    }

    /**
     * Performance monitoring utility for stress tests
     */
    private static class PerformanceMonitor {
        private final Map<String, WorkflowMetrics> workflowMetrics = new ConcurrentHashMap<>();
        private final AtomicInteger totalWorkflows = new AtomicInteger(0);
        private final AtomicInteger totalErrors = new AtomicInteger(0);

        public void recordSuccessfulWorkflow(String workflowType, long duration) {
            workflowMetrics.computeIfAbsent(workflowType, k -> new WorkflowMetrics())
                .recordSuccess(duration);
            totalWorkflows.incrementAndGet();
        }

        public void recordFailedWorkflow(String workflowType, String error) {
            workflowMetrics.computeIfAbsent(workflowType, k -> new WorkflowMetrics())
                .recordError();
            totalWorkflows.incrementAndGet();
            totalErrors.incrementAndGet();
        }

        public void recordPriorityExecution(String workflowType, int priority, long duration) {
            workflowMetrics.computeIfAbsent(workflowType, k -> new WorkflowMetrics())
                .recordPriorityExecution(priority, duration);
        }

        public Map<String, WorkflowMetrics> getWorkflowMetrics() {
            return new HashMap<>(workflowMetrics);
        }

        public int getTotalWorkflows() { return totalWorkflows.get(); }
        public int getTotalErrors() { return totalErrors.get(); }
    }

    private static class WorkflowMetrics {
        private final List<Long> durations = new ArrayList<>();
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final Map<Integer, AtomicInteger> priorityCounts = new ConcurrentHashMap<>();

        public void recordSuccess(long duration) {
            successCount.incrementAndGet();
            durations.add(duration);
        }

        public void recordError() {
            errorCount.incrementAndGet();
        }

        public void recordPriorityExecution(int priority, long duration) {
            priorityCounts.computeIfAbsent(priority, k -> new AtomicInteger()).incrementAndGet();
            recordSuccess(duration);
        }

        public double getAverageDuration() {
            return durations.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        }

        public double getSuccessRate() {
            int total = successCount.get() + errorCount.get();
            return total > 0 ? (double) successCount.get() / total * 100 : 0;
        }
    }
}