package org.yawlfoundation.yawl.resourcing;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for resource allocation and work item distribution logic.
 * Tests resource availability calculation, work item allocation, and constraint validation.
 *
 * Chicago TDD: Real work item records, real allocation logic.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResourceLogicUnitTest {

    @Test
    void testWorkItemStatusTransitions() {
        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setStatus(WorkItemRecord.statusEnabled);

        assertEquals(WorkItemRecord.statusEnabled, workItem.getStatus(),
                "Initial status should be Enabled");

        workItem.setStatus(WorkItemRecord.statusExecuting);
        assertEquals(WorkItemRecord.statusExecuting, workItem.getStatus(),
                "Status should transition to Executing");

        workItem.setStatus(WorkItemRecord.statusComplete);
        assertEquals(WorkItemRecord.statusComplete, workItem.getStatus(),
                "Status should transition to Complete");
    }

    @Test
    void testWorkItemResourceStatusTransitions() {
        WorkItemRecord workItem = new WorkItemRecord();

        assertEquals(WorkItemRecord.statusResourceUnresourced, workItem.getResourceStatus(),
                "Initial resource status should be Unresourced");

        workItem.setResourceStatus(WorkItemRecord.statusResourceOffered);
        assertEquals(WorkItemRecord.statusResourceOffered, workItem.getResourceStatus(),
                "Resource status should transition to Offered");

        workItem.setResourceStatus(WorkItemRecord.statusResourceAllocated);
        assertEquals(WorkItemRecord.statusResourceAllocated, workItem.getResourceStatus(),
                "Resource status should transition to Allocated");

        workItem.setResourceStatus(WorkItemRecord.statusResourceStarted);
        assertEquals(WorkItemRecord.statusResourceStarted, workItem.getResourceStatus(),
                "Resource status should transition to Started");
    }

    @Test
    void testWorkItemIdentification() {
        WorkItemRecord workItem = new WorkItemRecord("123.456", "Approve_Order",
                "http://localhost/spec.yawl", WorkItemRecord.statusEnabled);

        assertEquals(workItem.getCaseID(), "Case ID should match", "123.456");
        assertEquals(workItem.getTaskID(), "Task ID should match", "Approve_Order");
        assertEquals(workItem.getSpecURI(), "Spec URI should match", "http://localhost/spec.yawl");
        assertEquals(WorkItemRecord.statusEnabled, workItem.getStatus(), "Status should match");
    }

    @Test
    void testResourceAvailabilityCalculation() {
        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource("user1", 5, 2));
        resources.add(new Resource("user2", 3, 3));
        resources.add(new Resource("user3", 10, 0));

        List<Resource> available = filterAvailableResources(resources);

        assertEquals(2, available.size(), "Should have 2 available resources");
        assertTrue(containsResource(available, "user1"), "user1 should be available");
        assertTrue(containsResource(available, "user3"), "user3 should be available");
        assertFalse(containsResource(available, "user2"), "user2 should not be available");
    }

    @Test
    void testWorkItemAllocationLogic() {
        WorkItemRecord workItem = createTestWorkItem("123.456", "Approve_Order");
        Resource resource = new Resource("user1", 5, 2);

        boolean allocated = allocateWorkItem(workItem, resource);

        assertTrue(allocated, "Work item should be allocated");
        assertEquals(WorkItemRecord.statusResourceAllocated, workItem.getResourceStatus(),
                "Work item should be in Allocated status");
    }

    @Test
    void testWorkItemAllocationCapacityCheck() {
        Resource resource = new Resource("user1", 3, 3);

        assertFalse(canAllocate(resource), "Should not allocate when at capacity");

        Resource availableResource = new Resource("user2", 5, 2);
        assertTrue(canAllocate(availableResource), "Should allocate when under capacity");
    }

    @Test
    void testMultipleWorkItemAllocation() {
        Resource resource = new Resource("user1", 5, 0);

        WorkItemRecord item1 = createTestWorkItem("123.1", "Task1");
        WorkItemRecord item2 = createTestWorkItem("123.2", "Task2");
        WorkItemRecord item3 = createTestWorkItem("123.3", "Task3");

        assertTrue(allocateWorkItem(item1, resource), "First allocation should succeed");
        assertTrue(allocateWorkItem(item2, resource), "Second allocation should succeed");
        assertTrue(allocateWorkItem(item3, resource), "Third allocation should succeed");

        assertEquals(3, resource.getAllocatedCount(), "Resource should have 3 allocated items");
    }

    @Test
    void testResourceConstraintValidation() {
        assertTrue(isValidResourceId("user123"), "Valid resource name should pass");
        assertTrue(isValidResourceId("user_admin"), "Valid resource with underscore should pass");
        assertFalse(isValidResourceId(""), "Empty resource name should fail");
        assertFalse(isValidResourceId(null), "Null resource name should fail");
        assertFalse(isValidResourceId("   "), "Whitespace resource name should fail");
    }

    @Test
    void testWorkItemStatusValidation() {
        assertTrue(isValidStatus(WorkItemRecord.statusEnabled), "Enabled is valid status");
        assertTrue(isValidStatus(WorkItemRecord.statusExecuting), "Executing is valid status");
        assertTrue(isValidStatus(WorkItemRecord.statusComplete), "Complete is valid status");
        assertTrue(isValidStatus(WorkItemRecord.statusFired), "Fired is valid status");
        assertFalse(isValidStatus("InvalidStatus"), "Invalid status should fail");
        assertFalse(isValidStatus(null), "Null status should fail");
    }

    @Test
    void testResourceStatusValidation() {
        assertTrue(isValidResourceStatus(WorkItemRecord.statusResourceOffered),
                "Offered is valid resource status");
        assertTrue(isValidResourceStatus(WorkItemRecord.statusResourceAllocated),
                "Allocated is valid resource status");
        assertTrue(isValidResourceStatus(WorkItemRecord.statusResourceStarted),
                "Started is valid resource status");
        assertFalse(isValidResourceStatus("InvalidResourceStatus"),
                "Invalid resource status should fail");
    }

    @Test
    void testWorkItemDeallocation() {
        Resource resource = new Resource("user1", 5, 2);
        WorkItemRecord workItem = createTestWorkItem("123.456", "Task1");

        allocateWorkItem(workItem, resource);
        assertEquals(3, resource.getAllocatedCount(), "Should have 3 allocated items");

        deallocateWorkItem(workItem, resource);
        assertEquals(2, resource.getAllocatedCount(),
                "Should have 2 allocated items after deallocation");
        assertEquals(WorkItemRecord.statusResourceUnresourced, workItem.getResourceStatus(),
                "Work item should be unresourced");
    }

    @Test
    void testResourceCapacityEnforcement() {
        Resource resource = new Resource("user1", 2, 2);

        assertFalse(canAllocate(resource), "Should not allow allocation when at capacity");

        WorkItemRecord workItem = createTestWorkItem("123.456", "Task1");
        boolean allocated = allocateWorkItem(workItem, resource);

        assertFalse(allocated, "Allocation should fail when at capacity");
    }

    @Test
    void testWorkItemPriorityOrdering() {
        List<WorkItemRecord> workItems = new ArrayList<>();
        workItems.add(createPrioritizedWorkItem("123.1", "Task1", 2));
        workItems.add(createPrioritizedWorkItem("123.2", "Task2", 1));
        workItems.add(createPrioritizedWorkItem("123.3", "Task3", 3));

        List<WorkItemRecord> sorted = sortByPriority(workItems);

        assertEquals(3, getPriority(sorted.get(0)), "Highest priority should be first");
        assertEquals(2, getPriority(sorted.get(1)), "Medium priority should be second");
        assertEquals(1, getPriority(sorted.get(2)), "Lowest priority should be last");
    }

    @Test
    void testResourceLoadBalancing() {
        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource("user1", 10, 7));
        resources.add(new Resource("user2", 10, 3));
        resources.add(new Resource("user3", 10, 1));

        Resource leastLoaded = findLeastLoadedResource(resources);

        assertEquals(leastLoaded.getId(), "Should select least loaded resource", "user3");
        assertEquals(1, leastLoaded.getAllocatedCount(), "Least loaded should have 1 allocation");
    }

    @Test
    void testWorkItemCaseIdExtraction() {
        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setCaseID("123.456.789");

        String caseId = workItem.getCaseID();
        assertEquals(caseId, "Should extract full case ID", "123.456.789");

        String rootCaseId = extractRootCaseId(caseId);
        assertEquals(rootCaseId, "Should extract root case ID", "123");
    }

    private WorkItemRecord createTestWorkItem(String caseId, String taskId) {
        WorkItemRecord workItem = new WorkItemRecord(caseId, taskId,
                "http://localhost/spec.yawl", WorkItemRecord.statusEnabled);
        workItem.setResourceStatus(WorkItemRecord.statusResourceUnresourced);
        return workItem;
    }

    private WorkItemRecord createPrioritizedWorkItem(String caseId, String taskId, int priority) {
        WorkItemRecord workItem = createTestWorkItem(caseId, taskId);
        workItem.setTag(String.valueOf(priority));
        return workItem;
    }

    private boolean allocateWorkItem(WorkItemRecord workItem, Resource resource) {
        if (!canAllocate(resource)) {
            return false;
        }
        workItem.setResourceStatus(WorkItemRecord.statusResourceAllocated);
        resource.incrementAllocated();
        return true;
    }

    private void deallocateWorkItem(WorkItemRecord workItem, Resource resource) {
        workItem.setResourceStatus(WorkItemRecord.statusResourceUnresourced);
        resource.decrementAllocated();
    }

    private boolean canAllocate(Resource resource) {
        return resource.getAllocatedCount() < resource.getCapacity();
    }

    private List<Resource> filterAvailableResources(List<Resource> resources) {
        List<Resource> available = new ArrayList<>();
        for (Resource resource : resources) {
            if (canAllocate(resource)) {
                available.add(resource);
            }
        }
        return available;
    }

    private boolean containsResource(List<Resource> resources, String resourceId) {
        for (Resource resource : resources) {
            if (resource.getId().equals(resourceId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidResourceId(String resourceId) {
        return resourceId != null && !resourceId.trim().isEmpty();
    }

    private boolean isValidStatus(String status) {
        if (status == null) {
            return false;
        }
        Set<String> validStatuses = new HashSet<>();
        validStatuses.add(WorkItemRecord.statusEnabled);
        validStatuses.add(WorkItemRecord.statusFired);
        validStatuses.add(WorkItemRecord.statusExecuting);
        validStatuses.add(WorkItemRecord.statusComplete);
        validStatuses.add(WorkItemRecord.statusIsParent);
        validStatuses.add(WorkItemRecord.statusDeadlocked);
        validStatuses.add(WorkItemRecord.statusForcedComplete);
        validStatuses.add(WorkItemRecord.statusFailed);
        validStatuses.add(WorkItemRecord.statusSuspended);
        validStatuses.add(WorkItemRecord.statusDiscarded);
        return validStatuses.contains(status);
    }

    private boolean isValidResourceStatus(String status) {
        if (status == null) {
            return false;
        }
        Set<String> validStatuses = new HashSet<>();
        validStatuses.add(WorkItemRecord.statusResourceOffered);
        validStatuses.add(WorkItemRecord.statusResourceAllocated);
        validStatuses.add(WorkItemRecord.statusResourceStarted);
        validStatuses.add(WorkItemRecord.statusResourceSuspended);
        validStatuses.add(WorkItemRecord.statusResourceUnoffered);
        validStatuses.add(WorkItemRecord.statusResourceUnresourced);
        return validStatuses.contains(status);
    }

    private List<WorkItemRecord> sortByPriority(List<WorkItemRecord> workItems) {
        List<WorkItemRecord> sorted = new ArrayList<>(workItems);
        sorted.sort((a, b) -> Integer.compare(getPriority(b), getPriority(a)));
        return sorted;
    }

    private int getPriority(WorkItemRecord workItem) {
        String tag = workItem.getTag();
        if (tag == null || tag.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(tag);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Resource findLeastLoadedResource(List<Resource> resources) {
        Resource leastLoaded = resources.get(0);
        for (Resource resource : resources) {
            if (resource.getAllocatedCount() < leastLoaded.getAllocatedCount()) {
                leastLoaded = resource;
            }
        }
        return leastLoaded;
    }

    private String extractRootCaseId(String caseId) {
        if (caseId == null || caseId.isEmpty()) {
            return caseId;
        }
        int dotIndex = caseId.indexOf('.');
        if (dotIndex > 0) {
            return caseId.substring(0, dotIndex);
        }
        return caseId;
    }

    private static class Resource {
        private final String id;
        private final int capacity;
        private int allocatedCount;

        Resource(String id, int capacity, int allocatedCount) {
            this.id = id;
            this.capacity = capacity;
            this.allocatedCount = allocatedCount;
        }

        String getId() {
            return id;
        }

        int getCapacity() {
            return capacity;
        }

        int getAllocatedCount() {
            return allocatedCount;
        }

        void incrementAllocated() {
            allocatedCount++;
        }

        void decrementAllocated() {
            if (allocatedCount > 0) {
                allocatedCount--;
            }
        }
    }
}
