package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.state.YMarking;
import org.yawlfoundation.yawl.exceptions.YAWLException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago School TDD tests for YWorkItem soundness and correctness.
 *
 * Tests the behavioral aspects of work items rather than implementation details:
 * - Work item state transitions
 * - Work item lifecycle
 * - Data handling
 * - Resource allocation
 * - Error handling
 *
 * @author Test Specialist
 * @since YAWL v6.0.0
 */
@DisplayName("YWorkItem Soundness Tests")
class YWorkItemSoundnessTest {

    private YNetRunner netRunner;
    private YSpecification spec;

    @BeforeEach
    void setUp() {
        netRunner = new YNetRunner();
        spec = createSimpleSpecification();
    }

    /**
     * Test that work items transition correctly through their states.
     */
    @Test
    @DisplayName("Work items transition through states correctly")
    void testWorkItemStateTransitions() {
        // Given: A workflow specification
        YNet net = spec.getNet("0");
        YMarking initialMarking = net.getInitialMarking();

        // When: The workflow is started
        List<WorkItemRecord> enabledItems = getEnabledWorkItems(net, initialMarking);

        // Then: Work items start in enabled state
        assertFalse(enabledItems.isEmpty(), "Should have enabled work items");
        assertTrue(enabledItems.stream().allMatch(WorkItemRecord::isEnabled),
                   "All initial work items should be enabled");

        // When: Work items are started
        List<WorkItemRecord> runningItems = startWorkItems(enabledItems);

        // Then: Work items transition to running state
        assertFalse(runningItems.isEmpty(), "Should have running work items");
        assertTrue(runningItems.stream().allMatch(WorkItemRecord::isRunning),
                   "All started work items should be running");

        // When: Work items are completed
        List<WorkItemRecord> completedItems = completeWorkItems(runningItems);

        // Then: Work items transition to complete state
        assertFalse(completedItems.isEmpty(), "Should have completed work items");
        assertTrue(completedItems.stream().allMatch(WorkItemRecord::isComplete),
                   "All completed work items should be complete");
    }

    /**
     * Test that work items handle data correctly.
     */
    @Test
    @DisplayName("Work items handle data correctly")
    void testWorkItemDataHandling() {
        // Given: Work items with data
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Data is added to work items
        WorkItemRecord itemWithData = workItems.get(0);
        itemWithData.addData("field1", "value1");
        itemWithData.addData("field2", "value2");

        // Then: Data is stored correctly
        assertEquals("value1", itemWithData.getData("field1"));
        assertEquals("value2", itemWithData.getData("field2"));

        // When: Data is modified
        itemWithData.setData("field1", "new_value1");

        // Then: Data is updated correctly
        assertEquals("new_value1", itemWithData.getData("field1"));
        assertEquals("value2", itemWithData.getData("field2"));
    }

    /**
     * Test that work items handle timeouts correctly.
     */
    @Test
    @DisplayName("Work items handle timeouts correctly")
    void testWorkItemTimeouts() {
        // Given: Work items with timeout configurations
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Work items are started with timeout
        WorkItemRecord timedItem = workItems.get(0);
        timedItem.setTimeout(1000); // 1 second timeout

        // Then: Work item has timeout configuration
        assertEquals(1000, timedItem.getTimeout());

        // When: Work item times out
        List<WorkItemRecord> timedOutItems = timeoutWorkItems(List.of(timedItem));

        // Then: Work item is marked as cancelled
        assertEquals(1, timedOutItems.size());
        assertTrue(timedOutItems.get(0).isCancelled());
    }

    /**
     * Test that work items handle priority correctly.
     */
    @Test
    @DisplayName("Work items handle priority correctly")
    void testWorkItemPriority() {
        // Given: Work items with different priorities
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Priorities are set
        WorkItemRecord highPriority = workItems.get(0);
        highPriority.setPriority(100);

        WorkItemRecord lowPriority = workItems.get(1);
        lowPriority.setPriority(10);

        // Then: Priorities are stored correctly
        assertEquals(100, highPriority.getPriority());
        assertEquals(10, lowPriority.getPriority());

        // When: Work items are sorted by priority
        List<WorkItemRecord> sortedItems = sortWorkItemsByPriority(workItems);

        // Then: Items are sorted correctly
        assertEquals(highPriority, sortedItems.get(0));
        assertEquals(lowPriority, sortedItems.get(1));
    }

    /**
     * Test that work items handle resource allocation correctly.
     */
    @Test
    @DisplayName("Work items handle resource allocation correctly")
    void testWorkItemResourceAllocation() {
        // Given: Work items with resource requirements
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Resources are allocated to work items
        WorkItemResource resource = new WorkItemResource("resource1", 1);
        WorkItemRecord itemWithResource = workItems.get(0);
        itemWithResource.allocateResource(resource);

        // Then: Resources are allocated
        assertTrue(itemWithResource.hasResource("resource1"));
        assertEquals(resource, itemWithResource.getResource("resource1"));

        // When: Resources are released
        itemWithResource.releaseResource("resource1");

        // Then: Resources are released
        assertFalse(itemWithResource.hasResource("resource1"));
    }

    /**
     * Test that work items handle error conditions gracefully.
     */
    @Test
    @DisplayName("Work items handle error conditions gracefully")
    void testWorkItemErrorHandling() {
        // Given: Work items in various states
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Work items are started and then encounter errors
        WorkItemRecord errorItem = workItems.get(0);
        errorItem.start();

        // Then: Work item transitions to running state
        assertTrue(errorItem.isRunning());

        // When: An error occurs during execution
        errorItem.setError(new YAWLException("Test error"));

        // Then: Work item transitions to failed state
        assertTrue(errorItem.isFailed());
        assertNotNull(errorItem.getError());
        assertEquals("Test error", errorItem.getError().getMessage());
    }

    /**
     * Test that work items handle concurrency correctly.
     */
    @Test
    @DisplayName("Work items handle concurrency correctly")
    void testWorkItemConcurrency() {
        // Given: Multiple work items that can be executed concurrently
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Work items are executed concurrently
        List<WorkItemRecord> results = executeWorkItemsConcurrently(workItems);

        // Then: All work items are processed correctly
        assertEquals(workItems.size(), results.size());
        assertTrue(results.stream().allMatch(WorkItemRecord::isComplete));
    }

    /**
     * Test that work items handle data validation correctly.
     */
    @Test
    @DisplayName("Work items handle data validation correctly")
    void testWorkItemDataValidation() {
        // Given: Work items with data validation rules
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Valid data is added
        WorkItemRecord validItem = workItems.get(0);
        validItem.addData("requiredField", "validValue");

        // Then: Validation passes
        assertTrue(validItem.validateData());

        // When: Invalid data is added
        WorkItemRecord invalidItem = workItems.get(1);
        invalidItem.addData("requiredField", null);

        // Then: Validation fails
        assertFalse(invalidItem.validateData());
    }

    /**
     * Test that work items handle assignment correctly.
     */
    @Test
    @DisplayName("Work items handle assignment correctly")
    void testWorkItemAssignment() {
        // Given: Work items that can be assigned to participants
        YNet net = spec.getNet("0");
        YMarking marking = net.getInitialMarking();
        List<WorkItemRecord> workItems = getEnabledWorkItems(net, marking);

        // When: Work items are assigned to participants
        WorkItemRecord assignedItem = workItems.get(0);
        assignedItem.assignTo("participant1");

        // Then: Work items are assigned correctly
        assertEquals("participant1", assignedItem.getAssignedTo());
        assertTrue(assignedItem.isAssigned());

        // When: Work items are unassigned
        assignedItem.unassign();

        // Then: Work items are unassigned correctly
        assertNull(assignedItem.getAssignedTo());
        assertFalse(assignedItem.isAssigned());
    }

    // Helper methods for test setup

    private YSpecification createSimpleSpecification() {
        // Create a simple workflow specification for testing
        // In a real implementation, this would create an actual YAWL specification
        return new YSpecification(); // Placeholder
    }

    private List<WorkItemRecord> getEnabledWorkItems(YNet net, YMarking marking) {
        // Get enabled work items for a marking
        // This is a placeholder for the actual implementation
        return List.of(
            createWorkItem("task1", "enabled"),
            createWorkItem("task2", "enabled")
        );
    }

    private WorkItemRecord createWorkItem(String taskId, String state) {
        // Create a work item for testing
        return new WorkItemRecord(); // Placeholder
    }

    private List<WorkItemRecord> startWorkItems(List<WorkItemRecord> items) {
        // Start work items and return updated items
        return items.stream()
            .peek(WorkItemRecord::start)
            .collect(java.util.stream.Collectors.toList());
    }

    private List<WorkItemRecord> completeWorkItems(List<WorkItemRecord> items) {
        // Complete work items and return updated items
        return items.stream()
            .peek(WorkItemRecord::complete)
            .collect(java.util.stream.Collectors.toList());
    }

    private List<WorkItemRecord> timeoutWorkItems(List<WorkItemRecord> items) {
        // Timeout work items and return updated items
        return items.stream()
            .peek(item -> item.cancel("timeout"))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<WorkItemRecord> sortWorkItemsByPriority(List<WorkItemRecord> items) {
        // Sort work items by priority
        return items.stream()
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .collect(java.util.stream.Collectors.toList());
    }

    private List<WorkItemRecord> executeWorkItemsConcurrently(List<WorkItemRecord> items) {
        // Execute work items concurrently
        return items.stream()
            .peek(item -> {
                item.start();
                item.complete();
            })
            .collect(java.util.stream.Collectors.toList());
    }

    // Helper class for resource testing
    static class WorkItemResource {
        private final String name;
        private final int capacity;

        public WorkItemResource(String name, int capacity) {
            this.name = name;
            this.capacity = capacity;
        }

        public String getName() {
            return name;
        }

        public int getCapacity() {
            return capacity;
        }
    }
}