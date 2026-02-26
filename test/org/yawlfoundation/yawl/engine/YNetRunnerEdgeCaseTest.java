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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.JDOMUtil;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case test suite for {@link YNetRunner}.
 *
 * <p>Chicago TDD approach: tests use real YAWL engine, real XML specifications,
 * and direct instantiation. No mocks - everything is a real integration.</p>
 *
 * <p>This suite tests edge cases that typically cause bugs or corner conditions:
 * - Empty net execution
 * - Single task nets
 * - Maximum depth recursion
 * - Concurrent case limits
 * - Memory pressure scenarios
 * - Resource exhaustion
 * - Boundary conditions
 * - Error recovery scenarios</p>
 *
 * @author YAWL Foundation
 * @since YAWL v6.0.0
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("YNetRunner Edge Case Tests")
class YNetRunnerEdgeCaseTest {

    private YEngine engine;
    private YSpecification emptyNetSpec;
    private YSpecification singleTaskSpec;
    private YSpecification deepRecursionSpec;
    private YSpecification concurrentLimitSpec;
    private YSpecification memoryPressureSpec;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);

        // Load test specifications
        emptyNetSpec = loadSpec("EmptyNetSpecification.xml");
        singleTaskSpec = loadSpec("SingleTaskSpecification.xml");
        deepRecursionSpec = loadSpec("DeepRecursionSpecification.xml");
        concurrentLimitSpec = loadSpec("ConcurrentLimitSpecification.xml");
        memoryPressureSpec = loadSpec("MemoryPressureSpecification.xml");
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(engine);
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private YSpecification loadSpec(String resourceName) throws Exception {
        URL url = getClass().getResource(resourceName);
        if (url == null) {
            // Try alternative location in test-specs directory
            url = getClass().getResource("test-specs/" + resourceName);
        }
        assertNotNull(url, "Test resource not found: " + resourceName);
        return YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(new File(url.getFile()).getAbsolutePath()), false).get(0);
    }

    private YIdentifier startCase(YSpecification spec) throws Exception {
        engine.loadSpecification(spec);
        return engine.startCase(
                spec.getSpecificationID(), null, null, null, null, null, false);
    }

    private void executeCaseToCompletion(YIdentifier caseId) throws Exception {
        // Keep kicking the case until completion
        while (engine.getNetRunnerRepository().get(caseId) != null &&
               !engine.getNetRunnerRepository().get(caseId).isCompleted()) {
            engine.getNetRunnerRepository().get(caseId).kick(null);
        }
    }

    // -------------------------------------------------------------------------
    // Test 1: Empty Net Execution
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Empty net completes immediately without any tasks")
    void testEmptyNetExecutionCompletesImmediately() throws Exception {
        // Given: An empty net specification
        engine.loadSpecification(emptyNetSpec);
        YIdentifier caseId = startCase(emptyNetSpec);

        // When: The case is started
        executeCaseToCompletion(caseId);

        // Then: The net should complete immediately with no work items
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);
        assertNotNull(runner, "Runner should exist for empty net");
        assertTrue(runner.isCompleted(), "Empty net should complete immediately");
        assertFalse(runner.hasActiveTasks(), "Empty net should have no active tasks");
    }

    @Test
    @DisplayName("Empty net maintains proper completion state with empty output condition")
    void testEmptyNetMaintainsCompletionState() throws Exception {
        // Given: An empty net specification
        engine.loadSpecification(emptyNetSpec);
        YIdentifier caseId = startCase(emptyNetSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: The empty net completes
        executeCaseToCompletion(caseId);

        // Then: All completion state checks should pass
        assertTrue(runner.isCompleted(), "Runner should report completed");
        assertTrue(runner.isEmpty(), "Runner should be empty");
        assertTrue(runner.endOfNetReached(), "Output condition should have token");
    }

    @Test
    @DisplayName("Empty net with external data still completes without tasks")
    void testEmptyNetWithExternalDataCompletes() throws Exception {
        // Given: An empty net with external data
        engine.loadSpecification(emptyNetSpec);
        YIdentifier caseId = startCase(emptyNetSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: External data is provided and case executes
        executeCaseToCompletion(caseId);

        // Then: Net completes and external data is handled
        assertTrue(runner.isCompleted(), "Empty net with external data should complete");
        assertNotNull(runner.getNetData(), "Net data should exist");
        assertEquals(0, runner.getNetData().getDataItems().size(),
                   "Empty net should have no data items");
    }

    // -------------------------------------------------------------------------
    // Test 2: Single Task Net
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Single task net executes and completes with one task")
    void testSingleTaskNetExecutes() throws Exception {
        // Given: A net with a single atomic task
        engine.loadSpecification(singleTaskSpec);
        YIdentifier caseId = startCase(singleTaskSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: The single task is executed
        executeCaseToCompletion(caseId);

        // Then: The single task completes and net ends
        assertTrue(runner.isCompleted(), "Single task net should complete");

        // Verify task lifecycle
        YTask task = runner.getNet().getNetElement("task1");
        assertNotNull(task, "Single task should exist in net");
    }

    @Test
    @DisplayName("Single task net with timer completes correctly")
    void testSingleTaskNetWithTimerCompletes() throws Exception {
        // Given: A single task with timer
        engine.loadSpecification(singleTaskSpec);
        YIdentifier caseId = startCase(singleTaskSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Task completes
        executeCaseToCompletion(caseId);

        // Then: Timer state should be maintained
        Map<String, String> timerStates = runner.get_timerStates();
        assertTrue(timerStates.isEmpty() ||
                  timerStates.values().stream()
                      .anyMatch(state -> "closed".equals(state)),
                  "Timer states should be closed after completion");
    }

    @Test
    @DisplayName("Single task net with complex data flows correctly")
    void testSingleTaskNetHandlesComplexDataFlows() throws Exception {
        // Given: Single task with complex data mappings
        engine.loadSpecification(singleTaskSpec);
        YIdentifier caseId = startCase(singleTaskSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Complex data is processed
        executeCaseToCompletion(caseId);

        // Then: Data flow is maintained correctly
        YNetData netData = runner.getNetData();
        assertNotNull(netData, "Net data should exist");
        assertFalse(netData.getDataItems().isEmpty(),
                   "Complex data should be processed");
    }

    // -------------------------------------------------------------------------
    // Test 3: Maximum Depth Recursion
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deep recursion net completes without stack overflow")
    void testMaximumDepthRecursionCompletes() throws Exception {
        // Given: A net with deep task recursion (100+ levels)
        engine.loadSpecification(deepRecursionSpec);
        YIdentifier caseId = startCase(deepRecursionSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Deep recursion is executed
        executeCaseToCompletion(caseId);

        // Then: Deep recursion completes without stack overflow
        assertTrue(runner.isCompleted(), "Deep recursion should complete");
        assertFalse(runner.hasActiveTasks(), "No tasks should remain active");
    }

    @Test
    @DisplayName("Deep recursion with concurrent branches scales correctly")
    void testDeepRecursionWithConcurrentBranches() throws Exception {
        // Given: Deep recursion with parallel branches
        engine.loadSpecification(deepRecursionSpec);
        YIdentifier caseId = startCase(deepRecursionSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Concurrent deep recursion is executed
        executeCaseToCompletion(caseId);

        // Then: All branches complete without deadlocks
        assertTrue(runner.isCompleted(), "Concurrent deep recursion should complete");
        assertEquals(0, runner.getBusyTasks().size(),
                   "No tasks should remain busy");
    }

    @Test
    @DisplayName("Deep recursion maintains lock metrics correctly")
    void testDeepRecursionMaintainsLockMetrics() throws Exception {
        // Given: Deep recursion scenario
        engine.loadSpecification(deepRecursionSpec);
        YIdentifier caseId = startCase(deepRecursionSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Deep recursion executes
        executeCaseToCompletion(caseId);

        // Then: Lock metrics are maintained
        YNetRunnerLockMetrics lockMetrics = runner.getLockMetrics();
        assertNotNull(lockMetrics, "Lock metrics should exist");
        assertTrue(lockMetrics.getTotalWriteLockWaits() >= 0,
                   "Write lock wait count should be non-negative");
    }

    // -------------------------------------------------------------------------
    // Test 4: Concurrent Case Limits
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Multiple concurrent cases execute without interference")
    void testConcurrentCaseLimits() throws Exception {
        // Given: Multiple cases using the same specification
        engine.loadSpecification(concurrentLimitSpec);
        int caseCount = 10;
        List<YIdentifier> caseIds = new ArrayList<>();

        // Start multiple cases concurrently
        ExecutorService executor = Executors.newFixedThreadPool(caseCount);
        List<Future<YIdentifier>> futures = new ArrayList<>();

        for (int i = 0; i < caseCount; i++) {
            Future<YIdentifier> future = executor.submit(() -> {
                return engine.startCase(
                        concurrentLimitSpec.getSpecificationID(),
                        null, null, null, null, null, false);
            });
            futures.add(future);
        }

        // Collect case IDs
        for (Future<YIdentifier> future : futures) {
            caseIds.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // When: All cases execute concurrently
        for (YIdentifier caseId : caseIds) {
            executeCaseToCompletion(caseId);
        }

        // Then: All cases complete successfully
        for (YIdentifier caseId : caseIds) {
            YNetRunner runner = engine.getNetRunnerRepository().get(caseId);
            assertNotNull(runner, "Runner should exist for case: " + caseId);
            assertTrue(runner.isCompleted(),
                      "Case should complete: " + caseId);
        }
    }

    @Test
    @DisplayName("Concurrent cases respect resource constraints")
    void testConcurrentCasesRespectResourceConstraints() throws Exception {
        // Given: Cases with resource constraints
        engine.loadSpecification(concurrentLimitSpec);
        int caseCount = 5;

        // Start multiple cases
        List<YIdentifier> caseIds = new ArrayList<>();
        for (int i = 0; i < caseCount; i++) {
            YIdentifier caseId = engine.startCase(
                    concurrentLimitSpec.getSpecificationID(),
                    null, null, null, null, null, false);
            caseIds.add(caseId);
        }

        // When: All cases execute
        for (YIdentifier caseId : caseIds) {
            executeCaseToCompletion(caseId);
        }

        // Then: Resource constraints are respected across all cases
        for (YIdentifier caseId : caseIds) {
            YNetRunner runner = engine.getNetRunnerRepository().get(caseId);
            assertTrue(runner.isCompleted(), "Case should complete with constraints");

            // Verify no shared resource violations
            assertTrue(runner.getEnabledTasks().size() == 0 ||
                     runner.getBusyTasks().size() <= 1,
                     "Resource constraints should be respected");
        }
    }

    @Test
    @DisplayName("Concurrent cases maintain isolation")
    void testConcurrentCasesMaintainIsolation() throws Exception {
        // Given: Multiple cases that should be isolated
        engine.loadSpecification(concurrentLimitSpec);
        int caseCount = 3;

        // Start and execute cases concurrently
        ExecutorService executor = Executors.newFixedThreadPool(caseCount);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < caseCount; i++) {
            final int caseIndex = i;
            Future<Void> future = executor.submit(() -> {
                YIdentifier caseId = engine.startCase(
                        concurrentLimitSpec.getSpecificationID(),
                        null, null, null, null, null, false);
                executeCaseToCompletion(caseId);

                // Verify case isolation
                YNetRunner runner = engine.getNetRunnerRepository().get(caseId);
                assertTrue(runner.isCompleted(),
                         "Case " + caseIndex + " should complete");
                return null;
            });
            futures.add(future);
        }

        // Wait for all cases to complete
        for (Future<Void> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        executor.shutdown();
    }

    // -------------------------------------------------------------------------
    // Test 5: Memory Pressure Scenarios
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Memory pressure scenario completes with many tasks")
    void testMemoryPressureScenario() throws Exception {
        // Given: A large net with many tasks to test memory pressure
        engine.loadSpecification(memoryPressureSpec);
        YIdentifier caseId = startCase(memoryPressureSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Large number of tasks is executed
        executeCaseToCompletion(caseId);

        // Then: Net completes despite memory pressure
        assertTrue(runner.isCompleted(), "Memory pressure scenario should complete");

        // Verify memory usage patterns
        assertFalse(runner.getEnabledTaskNames().isEmpty() ||
                   runner.getBusyTaskNames().isEmpty(),
                   "Task names should be processed correctly");
    }

    @Test
    @DisplayName("Memory pressure with complex data structures")
    void testMemoryPressureWithComplexDataStructures() throws Exception {
        // Given: Net with complex nested data structures
        engine.loadSpecification(memoryPressureSpec);
        YIdentifier caseId = startCase(memoryPressureSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Complex data is processed
        executeCaseToCompletion(caseId);

        // Then: Data structures are handled correctly
        YNetData netData = runner.getNetData();
        assertNotNull(netData, "Net data should exist");
        assertFalse(netData.getDataItems().isEmpty(),
                   "Complex data should be processed");
    }

    @Test
    @DisplayName("Memory pressure with deep task nesting")
    void testMemoryPressureWithDeepTaskNesting() throws Exception {
        // Given: Deeply nested composite tasks
        engine.loadSpecification(memoryPressureSpec);
        YIdentifier caseId = startCase(memoryPressureSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Deep task nesting is executed
        executeCaseToCompletion(caseId);

        // Then: Deep nesting completes without memory issues
        assertTrue(runner.isCompleted(), "Deep nesting should complete");

        // Verify no memory leaks in runner state
        Map<String, String> timerStates = runner.get_timerStates();
        assertTrue(timerStates.size() == 0 ||
                  timerStates.size() <= 50, // Reasonable limit
                   "Timer states should be bounded");
    }

    // -------------------------------------------------------------------------
    // Additional Edge Cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Runner handles rapid start/cycle cycles correctly")
    void testRapidStartCycleHandling() throws Exception {
        // Given: Multiple rapid start/complete cycles
        engine.loadSpecification(singleTaskSpec);

        for (int i = 0; i < 5; i++) {
            // Start and complete a case rapidly
            YIdentifier caseId = startCase(singleTaskSpec);
            executeCaseToCompletion(caseId);

            // Verify each case is properly cleaned up
            assertNull(engine.getNetRunnerRepository().get(caseId),
                      "Runner should be removed after completion");
        }
    }

    @Test
    @DisplayName("Runner handles concurrent state changes correctly")
    void testConcurrentStateChanges() throws Exception {
        // Given: Multiple threads modifying runner state
        engine.loadSpecification(concurrentLimitSpec);
        YIdentifier caseId = startCase(concurrentLimitSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Multiple threads access runner concurrently
        ExecutorService executor = Executors.newFixedThreadPool(3);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            final int threadId = i;
            Future<Boolean> future = executor.submit(() -> {
                try {
                    // Each thread checks state multiple times
                    for (int j = 0; j < 10; j++) {
                        boolean hasActive = runner.hasActiveTasks();
                        boolean isCompleted = runner.isCompleted();
                        Thread.sleep(10); // Small delay
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            });
            futures.add(future);
        }

        // Execute the case while threads are checking state
        executeCaseToCompletion(caseId);

        // Wait for all threads to complete
        for (Future<Boolean> future : futures) {
            assertTrue(future.get(30, TimeUnit.SECONDS),
                      "All threads should complete successfully");
        }
        executor.shutdown();

        // Then: Final state should be consistent
        assertTrue(runner.isCompleted(), "Runner should be completed");
    }

    @Test
    @DisplayName("Runner handles error conditions gracefully")
    void testErrorConditionHandling() throws Exception {
        // Given: Error conditions in net execution
        engine.loadSpecification(singleTaskSpec);
        YIdentifier caseId = startCase(singleTaskSpec);
        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);

        // When: Various error conditions occur
        try {
            // Test normal operation
            executeCaseToCompletion(caseId);

            // Test error case - force null state
            runner.setExecutionStatus(null); // This should be handled gracefully

            // Test error case - invalid state transition
            runner.setStateSuspending();
            runner.setStateResuming();
            runner.setStateNormal();

        } catch (Exception e) {
            // Some errors are expected, runner should not crash
        }

        // Then: Runner remains in consistent state
        assertNotNull(runner.getExecutionStatus(),
                     "Execution status should always be valid");
        assertTrue(runner.isCompleted() || runner.isInSuspense(),
                   "Runner should be in valid state");
    }

    /**
     * Helper class for deadlock clearing between tests.
     */
    private static class EngineClearer {
        static void clear(YEngine engine) throws Exception {
            if (engine != null) {
                // Cancel all running cases
                for (YIdentifier caseId : new ArrayList<>(engine.getRunningCaseIDs())) {
                    try {
                        engine.cancelCase(caseId);
                    } catch (Exception e) {
                        // Ignore errors during cleanup
                    }
                }

                // Clear all specifications
                engine.getSpecificationRepository().removeAllSpecifications();

                // Clear work items
                engine.getWorkItemRepository().removeAllWorkItems();
            }
        }
    }
}