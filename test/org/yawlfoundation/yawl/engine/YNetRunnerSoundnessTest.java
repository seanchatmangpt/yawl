package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.state.YMarking;
import org.yawlfoundation.yawl.exceptions.YAWLException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago School TDD tests for YNetRunner soundness and correctness.
 *
 * Tests the behavioral aspects of the net runner rather than implementation details:
 * - Net execution correctness
 * - State machine behavior
 * - Transition firing
 * - Concurrency handling
 * - Error handling
 *
 * @author Test Specialist
 * @since YAWL v6.0.0
 */
@DisplayName("YNetRunner Soundness Tests")
class YNetRunnerSoundnessTest {

    private YNetRunner netRunner;
    private YSpecification spec;

    @BeforeEach
    void setUp() {
        netRunner = new YNetRunner();
        spec = createTestSpecification();
    }

    /**
     * Test that net runner executes simple workflows correctly.
     */
    @Test
    @DisplayName("Net runner executes simple workflows correctly")
    void testNetRunnerExecutesSimpleWorkflows() {
        // Given: A simple workflow specification
        YNet net = spec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);
        List<WorkItemRecord> finalItems = netRunner.executeWorkflow(net, initialMarking);

        // Then: Workflow executes correctly
        assertFalse(initialItems.isEmpty(), "Should have initial enabled work items");
        assertFalse(finalItems.isEmpty(), "Should have final work items");
    }

    /**
     * Test that net runner handles parallel execution correctly.
     */
    @Test
    @DisplayName("Net runner handles parallel execution correctly")
    void testNetRunnerHandlesParallelExecution() {
        // Given: A workflow with parallel branches
        YSpecification parallelSpec = createParallelWorkflowSpecification();
        YNet net = parallelSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);
        List<WorkItemRecord> finalItems = netRunner.executeWorkflow(net, initialMarking);

        // Then: Parallel branches execute correctly
        long parallelItems = initialItems.stream()
            .filter(item -> item.getNetElementID().contains("branch"))
            .count();

        assertTrue(parallelItems >= 2, "Should have multiple parallel branches");
        assertFalse(finalItems.isEmpty(), "Should have completed items");
    }

    /**
     * Test that net runner handles synchronization correctly.
     */
    @Test
    @DisplayName("Net runner handles synchronization correctly")
    void testNetRunnerHandlesSynchronization() {
        // Given: A workflow with synchronization
        YSpecification syncSpec = createSynchronizationWorkflowSpecification();
        YNet net = syncSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);

        // Complete some but not all branches
        completeSomeBranches(initialItems);

        // Then: Synchronization waits for all branches
        YMarking afterPartial = netRunner.calculateMarking(net, initialMarking, initialItems);
        boolean syncEnabled = netRunner.isTransitionEnabled(net, afterPartial, "sync_transition");

        assertFalse(syncEnabled, "Synchronization should not fire until all branches complete");
    }

    /**
     * Test that net runner handles choice patterns correctly.
     */
    @Test
    @DisplayName("Net runner handles choice patterns correctly")
    void testNetRunnerHandlesChoicePatterns() {
        // Given: A workflow with choice patterns
        YSpecification choiceSpec = createChoiceWorkflowSpecification();
        YNet net = choiceSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed with specific conditions
        List<WorkItemRecord> choiceItems = netRunner.getEnabledWorkItems(net, initialMarking);

        // Set conditions to choose specific branches
        setChoiceConditions(choiceItems, "branch1");

        // Then: Correct branches are enabled
        List<WorkItemRecord> enabledBranches = choiceItems.stream()
            .filter(WorkItemRecord::isEnabled)
            .filter(item -> item.getNetElementID().contains("branch"))
            .toList();

        assertEquals(1, enabledBranches.size(), "Should have exactly one enabled branch");
        assertTrue(enabledBranches.get(0).getNetElementID().contains("branch1"),
                   "Should have branch1 enabled");
    }

    /**
     * Test that net runner handles errors correctly.
     */
    @Test
    @DisplayName("Net runner handles errors correctly")
    void testNetRunnerHandlesErrors() {
        // Given: A workflow that can encounter errors
        YSpecification errorSpec = createErrorHandlingSpecification();
        YNet net = errorSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed and encounters errors
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);

        // Simulate error during execution
        WorkItemRecord errorItem = initialItems.get(0);
        errorItem.setError(new YAWLException("Test error"));

        List<WorkItemRecord> resultItems = netRunner.handleWorkItemError(net, errorItem);

        // Then: Error is handled correctly
        assertFalse(resultItems.isEmpty(), "Should have result items after error handling");
        assertTrue(resultItems.stream().anyMatch(WorkItemRecord::isFailed),
                   "Should have failed items");
    }

    /**
     * Test that net runner handles concurrency correctly.
     */
    @Test
    @DisplayName("Net runner handles concurrency correctly")
    void testNetRunnerHandlesConcurrency() {
        // Given: Multiple workflows executed concurrently
        YSpecification workflow = createTestSpecification();
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // When: Multiple workflows are executed concurrently
        List<Future<List<WorkItemRecord>>> futures = 3;
        for (int i = 0; i < 3; i++) {
            futures.add(executor.submit(() -> {
                YNet net = workflow.getNet("0");
                YMarking initialMarking = net.getInitialMarking();
                return netRunner.executeWorkflow(net, initialMarking);
            }));
        }

        // Then: All workflows complete without interference
        List<List<WorkItemRecord>> allResults = futures.stream()
            .map(future -> {
                try {
                    return future.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    fail("Workflow should complete within 10 seconds");
                    return null;
                }
            })
            .toList();

        assertEquals(3, allResults.size(), "Should have results for all workflows");
        assertFalse(allResults.stream().anyMatch(List::isEmpty),
                   "All workflows should produce results");
    }

    /**
     * Test that net runner handles data flow correctly.
     */
    @Test
    @DisplayName("Net runner handles data flow correctly")
    void testNetRunnerHandlesDataFlow() {
        // Given: A workflow with data flow between tasks
        YSpecification dataFlowSpec = createDataFlowSpecification();
        YNet net = dataFlowSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed with data
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);

        // Add data to first task
        WorkItemRecord firstTask = initialItems.get(0);
        firstTask.addData("input", "test_value");

        // Execute workflow
        List<WorkItemRecord> finalItems = netRunner.executeWorkflowWithData(net, initialMarking, firstTask);

        // Then: Data flows correctly through the workflow
        WorkItemRecord lastTask = finalItems.stream()
            .filter(WorkItemRecord::isComplete)
            .findFirst()
            .orElseThrow();

        assertEquals("test_value", lastTask.getData("output"),
                   "Data should flow from input to output");
    }

    /**
     * Test that net runner handles cancellation correctly.
     */
    @Test
    @DisplayName("Net runner handles cancellation correctly")
    void testNetRunnerHandlesCancellation() {
        // Given: A long-running workflow
        YSpecification longRunningSpec = createLongRunningSpecification();
        YNet net = longRunningSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is cancelled mid-execution
        List<WorkItemRecord> initialItems = netRunner.getEnabledWorkItems(net, initialMarking);

        // Cancel workflow
        List<WorkItemRecord> cancelledItems = netRunner.cancelWorkflow(net, initialMarking);

        // Then: All items are cancelled
        assertFalse(cancelledItems.isEmpty(), "Should have cancelled items");
        assertTrue(cancelledItems.stream().allMatch(WorkItemRecord::isCancelled),
                   "All items should be cancelled");
    }

    /**
     * Test that net runner handles resource constraints correctly.
     */
    @Test
    @DisplayName("Net runner handles resource constraints correctly")
    void testNetRunnerHandlesResourceConstraints() {
        // Given: A workflow with resource constraints
        YSpecification resourceSpec = createResourceConstrainedSpecification();
        YNet net = resourceSpec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is executed with limited resources
        ResourceConstraints constraints = new ResourceConstraints(1); // Only 1 resource unit
        List<WorkItemRecord> constrainedItems = netRunner.executeWithResourceConstraints(
            net, initialMarking, constraints);

        // Then: Resource constraints are respected
        long activeItems = constrainedItems.stream()
            .filter(WorkItemRecord::isEnabled)
            .count();

        assertEquals(1, activeItems, "Should have only 1 enabled item due to constraints");
    }

    // Helper methods for test setup

    private YSpecification createTestSpecification() {
        // Create a simple test specification
        return new YSpecification(); // Placeholder
    }

    private YSpecification createParallelWorkflowSpecification() {
        // Create a workflow with parallel branches
        return new YSpecification(); // Placeholder
    }

    private YSpecification createSynchronizationWorkflowSpecification() {
        // Create a workflow with synchronization
        return new YSpecification(); // Placeholder
    }

    private YSpecification createChoiceWorkflowSpecification() {
        // Create a workflow with choice patterns
        return new YSpecification(); // Placeholder
    }

    private YSpecification createErrorHandlingSpecification() {
        // Create a workflow with error handling
        return new YSpecification(); // Placeholder
    }

    private YSpecification createDataFlowSpecification() {
        // Create a workflow with data flow
        return new YSpecification(); // Placeholder
    }

    private YSpecification createLongRunningSpecification() {
        // Create a long-running workflow
        return new YSpecification(); // Placeholder
    }

    private YSpecification createResourceConstrainedSpecification() {
        // Create a resource-constrained workflow
        return new YSpecification(); // Placeholder
    }

    private void completeSomeBranches(List<WorkItemRecord> items) {
        // Complete some branches but not all
        items.stream()
            .filter(item -> item.getNetElementID().contains("branch"))
            .findFirst()
            .ifPresent(item -> {
                item.complete();
                netRunner.completeWorkItem(item);
            });
    }

    private void setChoiceConditions(List<WorkItemRecord> items, String chosenBranch) {
        // Set choice conditions to select specific branches
        items.stream()
            .filter(item -> item.getNetElementID().contains("choice"))
            .findFirst()
            .ifPresent(item -> {
                item.addData("chosenBranch", chosenBranch);
            });
    }

    // Helper classes
    static class ResourceConstraints {
        private final int maxUnits;

        public ResourceConstraints(int maxUnits) {
            this.maxUnits = maxUnits;
        }

        public int getMaxUnits() {
            return maxUnits;
        }
    }
}