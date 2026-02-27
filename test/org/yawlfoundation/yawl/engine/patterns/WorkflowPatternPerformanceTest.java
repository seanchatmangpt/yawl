package org.yawlfoundation.yawl.engine.patterns;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YMarking;
import org.yawlfoundation.yawl.elements.state.YSetOfMarkings;
import org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPattern;
import org.yawlfoundation.yawl.util.java25.performance.PerformanceMetrics;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Chicago School TDD tests for workflow pattern performance.
 *
 * Tests performance characteristics of workflow patterns:
 * - Execution time scalability
 * - Memory usage patterns
 * - Concurrency performance
 * - Throughput characteristics
 * - Resource utilization
 *
 * @author Test Specialist
 * @since YAWL v6.0.0
 */
@DisplayName("Workflow Pattern Performance Tests")
class WorkflowPatternPerformanceTest {

    private YNetRunner netRunner;
    private ExecutorService testExecutor;

    @BeforeEach
    void setUp() {
        netRunner = new YNetRunner();
        testExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @AfterEach
    void tearDown() {
        testExecutor.shutdown();
    }

    /**
     * Test that pattern execution scales linearly with pattern complexity.
     *
     * @param pattern The workflow pattern to test
     */
    @ParameterizedTest
    @EnumSource(WorkflowPattern.class)
    @DisplayName("Pattern execution scales with complexity")
    void testPatternExecutionScalesWithComplexity(WorkflowPattern pattern) {
        // Given: Workflow specifications with increasing complexity
        List<YSpecification> specs = createSpecificationsWithIncreasingComplexity(pattern);

        // When: Each specification is executed
        List<Long> executionTimes = specs.stream()
            .map(this::measureExecutionTime)
            .collect(Collectors.toList());

        // Then: Execution time increases proportionally with complexity
        for (int i = 1; i < executionTimes.size(); i++) {
            double ratio = (double) executionTimes.get(i) / executionTimes.get(i - 1);
            double complexityRatio = getComplexityRatio(specs.get(i), specs.get(i - 1));

            // Allow for some overhead but ensure it scales appropriately
            assertTrue(ratio <= complexityRatio * 1.5,
                String.format("Execution time ratio %.2f should not exceed complexity ratio %.2f by more than 50%%",
                    ratio, complexityRatio));
        }
    }

    /**
     * Test that parallel patterns achieve expected speedup.
     */
    @ParameterizedTest
    @EnumSource(value = WorkflowPattern.class, names = {"PARALLEL_SPLIT"})
    @DisplayName("Parallel patterns achieve speedup")
    void testParallelPatternsAchieveSpeedup(WorkflowPattern pattern) {
        // Given: A parallel pattern with varying branch counts
        YSpecification spec = createParallelSpecificationWithBranches(2, 4, 8, 16);

        // When: The pattern is executed with different thread counts
        List<PerformanceMetrics> metrics = measureParallelPerformance(spec);

        // Then: Speedup should be close to linear for small thread counts
        for (int i = 1; i < metrics.size(); i++) {
            double speedup = (double) metrics.get(0).getExecutionTimeMs() /
                           metrics.get(i).getExecutionTimeMs();
            int threadCount = i + 1;

            // Should achieve reasonable speedup (not perfect due to overhead)
            assertTrue(speedup >= threadCount * 0.7,
                String.format("Should achieve at least 70%% of linear speedup with %d threads", threadCount));
        }
    }

    /**
     * Test that memory usage is reasonable for patterns.
     */
    @ParameterizedTest
    @EnumSource(WorkflowPattern.class)
    @DisplayName("Memory usage is reasonable")
    void testMemoryUsageIsReasonable(WorkflowPattern pattern) {
        // Given: A workflow specification using the pattern
        YSpecification spec = createSpecificationWithPattern(pattern);

        // When: The workflow is executed
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        executeWorkflow(spec);

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        // Then: Memory usage should be proportional to pattern complexity
        int expectedMemoryKB = estimateExpectedMemoryUsage(pattern) * 1024;

        assertTrue(memoryUsed <= expectedMemoryKB * 2,
            String.format("Memory used %d bytes should not exceed expected %d bytes by more than 100%%",
                memoryUsed, expectedMemoryKB));
    }

    /**
     * Test that choice patterns with many branches perform adequately.
     */
    @Test
    @DisplayName("Choice patterns with many branches perform adequately")
    void testChoicePatternsWithManyBranches() {
        // Given: A choice pattern with many branches (100+)
        YSpecification spec = createSpecificationWithManyChoiceBranches(100);

        // When: The workflow is executed
        PerformanceMetrics metrics = measureExecutionTimeWithMetrics(spec);

        // Then: Execution should complete within reasonable time
        assertTrue(metrics.getExecutionTimeMs() <= 5000,
            "Choice pattern with 100 branches should complete within 5 seconds");
    }

    /**
     * Test that patterns with external service calls handle timeouts well.
     */
    @Test
    @DisplayName("Patterns with external services handle timeouts")
    void testPatternsWithExternalServicesHandleTimeouts() {
        // Given: A pattern that calls external services with varying timeouts
        YSpecification spec = createSpecificationWithExternalServiceTimeouts();

        // When: The workflow is executed with timeout constraints
        List<WorkItemRecord> results = executeWorkflowWithTimeouts(spec, 1000, MILLISECONDS);

        // Then: Should either complete or timeout gracefully
        assertFalse(results.isEmpty(), "Should have results or timeouts");
    }

    /**
     * Test that multi-instance patterns scale with instance count.
     */
    @Test
    @DisplayName("Multi-instance patterns scale with instance count")
    void testMultiInstancePatternsScaleWithInstanceCount() {
        // Given: Multi-instance specifications with varying instance counts
        List<YSpecification> specs = createMultiInstanceSpecifications(10, 50, 100, 500);

        // When: Each specification is executed
        List<Long> executionTimes = specs.stream()
            .map(this::measureExecutionTime)
            .collect(Collectors.toList());

        // Then: Execution time should scale approximately linearly with instance count
        for (int i = 1; i < executionTimes.size(); i++) {
            double timeRatio = (double) executionTimes.get(i) / executionTimes.get(i - 1);
            int instanceRatio = getSpecInstanceCount(specs.get(i)) / getSpecInstanceCount(specs.get(i - 1));

            // Allow for some overhead due to coordination
            assertTrue(timeRatio <= instanceRatio * 1.2,
                String.format("Time ratio %.2f should not exceed instance ratio %d by more than 20%%",
                    timeRatio, instanceRatio));
        }
    }

    /**
     * Test that patterns handle concurrent access efficiently.
     */
    @Test
    @DisplayName("Patterns handle concurrent access efficiently")
    void testPatternsWithConcurrentAccess() {
        // Given: A workflow specification that supports concurrent access
        YSpecification spec = createSpecificationWithConcurrencySupport();

        // When: The workflow is accessed by multiple threads
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        List<Future<PerformanceMetrics>> futures = threadCount;
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.countDown();
                latch.await();
                return measureExecutionTimeWithMetrics(spec);
            }));
        }

        // Then: All threads should complete without excessive contention
        List<PerformanceMetrics> results = futures.stream()
            .map(future -> {
                try {
                    return future.get(30, SECONDS);
                } catch (Exception e) {
                    fail("Thread should complete within 30 seconds");
                    return null;
                }
            })
            .collect(Collectors.toList());

        long maxExecutionTime = results.stream()
            .mapToLong(PerformanceMetrics::getExecutionTimeMs)
            .max()
            .orElse(0);

        assertTrue(maxExecutionTime <= 10000,
            String.format("Concurrent access should complete within 10 seconds, max was %d ms", maxExecutionTime));
    }

    /**
     * Test that patterns handle large datasets efficiently.
     */
    @Test
    @DisplayName("Patterns handle large datasets efficiently")
    void testPatternsWithLargeDatasets() {
        // Given: Pattern specifications with large datasets
        YSpecification spec = createSpecificationWithLargeDataset(100000);

        // When: The workflow is executed
        PerformanceMetrics metrics = measureExecutionTimeWithMetrics(spec);

        // Then: Should handle large data efficiently
        assertTrue(metrics.getMemoryUsageKB() <= 100, "Memory usage should be under 100MB");
        assertTrue(metrics.getExecutionTimeMs() <= 5000, "Execution should complete within 5 seconds");
    }

    /**
     * Test that patterns maintain performance under load.
     */
    @Test
    @DisplayName("Patterns maintain performance under load")
    void testPatternsWithUnderLoad() {
        // Given: Pattern specifications with varying load levels
        List<YSpecification> specs = createSpecificationsWithVaryingLoad(1, 5, 10, 20);

        // When: Each specification is executed
        List<PerformanceMetrics> metrics = specs.stream()
            .map(this::measureExecutionTimeWithMetrics)
            .collect(Collectors.toList());

        // Then: Performance degradation should be reasonable
        double timeSlowdown = (double) metrics.get(metrics.size() - 1).getExecutionTimeMs() /
                            metrics.get(0).getExecutionTimeMs();

        assertTrue(timeSlowdown <= 10,
            String.format("Performance should not degrade more than 10x under load, slowdown was %.2fx", timeSlowdown));
    }

    // Helper methods for test setup

    private List<YSpecification> createSpecificationsWithIncreasingComplexity(WorkflowPattern pattern) {
        // Create specifications with increasing complexity levels
        // Complexity could be number of tasks, depth, branching factor, etc.
        return List.of(
            createSpecificationWithPattern(pattern),
            createSpecificationWithPattern(pattern), // Placeholder for more complex versions
            createSpecificationWithPattern(pattern)
        );
    }

    private Long measureExecutionTime(YSpecification spec) {
        long startTime = System.currentTimeMillis();
        executeWorkflow(spec);
        return System.currentTimeMillis() - startTime;
    }

    private double getComplexityRatio(YSpecification spec1, YSpecification spec2) {
        // Calculate complexity ratio between two specifications
        // This is a simplified version - in reality would count tasks, branches, etc.
        return 1.5; // Placeholder
    }

    private YSpecification createParallelSpecificationWithBranches(int... branchCounts) {
        // Create parallel specifications with different branch counts
        return new YSpecification(); // Placeholder
    }

    private List<PerformanceMetrics> measureParallelPerformance(YSpecification spec) {
        // Measure performance with different thread configurations
        return List.of(); // Placeholder
    }

    private YSpecification createSpecificationWithPattern(WorkflowPattern pattern) {
        // Create a specification using the pattern
        return new YSpecification(); // Placeholder
    }

    private void executeWorkflow(YSpecification spec) {
        // Execute workflow for performance testing
        // This is a placeholder - in real implementation would execute actual workflow
    }

    private int estimateExpectedMemoryUsage(WorkflowPattern pattern) {
        // Estimate expected memory usage based on pattern complexity
        return 1000; // Placeholder
    }

    private YSpecification createSpecificationWithManyChoiceBranches(int branchCount) {
        // Create choice pattern with many branches
        return new YSpecification(); // Placeholder
    }

    private PerformanceMetrics measureExecutionTimeWithMetrics(YSpecification spec) {
        long startTime = System.currentTimeMillis();
        executeWorkflow(spec);
        long endTime = System.currentTimeMillis();

        return new PerformanceMetrics(
            endTime - startTime,
            0, // Memory placeholder
            0  // CPU placeholder
        );
    }

    private YSpecification createSpecificationWithExternalServiceTimeouts() {
        // Create specification with external service timeout configurations
        return new YSpecification(); // Placeholder
    }

    private List<WorkItemRecord> executeWorkflowWithTimeouts(YSpecification spec, long timeout, TimeUnit unit) {
        // Execute workflow with timeout constraints
        return List.of(); // Placeholder
    }

    private List<YSpecification> createMultiInstanceSpecifications(int... instanceCounts) {
        // Create multi-instance specifications with varying instance counts
        return List.of(); // Placeholder
    }

    private int getSpecInstanceCount(YSpecification spec) {
        // Get the number of instances in a specification
        return 10; // Placeholder
    }

    private YSpecification createSpecificationWithConcurrencySupport() {
        // Create specification that supports concurrent access
        return new YSpecification(); // Placeholder
    }

    private YSpecification createSpecificationWithLargeDataset(int dataItems) {
        // Create specification with large dataset
        return new YSpecification(); // Placeholder
    }

    private List<YSpecification> createSpecificationsWithVaryingLoad(int... loadFactors) {
        // Create specifications with varying load levels
        return List.of(); // Placeholder
    }

    // Performance metrics class
    static class PerformanceMetrics {
        private final long executionTimeMs;
        private final long memoryUsageKB;
        private final double cpuUsagePercent;

        public PerformanceMetrics(long executionTimeMs, long memoryUsageKB, double cpuUsagePercent) {
            this.executionTimeMs = executionTimeMs;
            this.memoryUsageKB = memoryUsageKB;
            this.cpuUsagePercent = cpuUsagePercent;
        }

        public long getExecutionTimeMs() {
            return executionTimeMs;
        }

        public long getMemoryUsageKB() {
            return memoryUsageKB;
        }

        public double getCpuUsagePercent() {
            return cpuUsagePercent;
        }
    }
}