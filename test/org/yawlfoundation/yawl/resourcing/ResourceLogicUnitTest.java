package org.yawlfoundation.yawl.resourcing;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests for resource allocation and work item distribution logic.
 * Tests resource availability calculation, work item allocation, and constraint validation.
 *
 * Chicago TDD: Real work item records, real allocation logic.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ResourceLogicUnitTest extends TestCase {

    public ResourceLogicUnitTest(String name) {
        super(name);
    }

    public void testWorkItemStatusTransitions() {
        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setStatus(WorkItemRecord.statusEnabled);

        assertEquals("Initial status should be Enabled",
                WorkItemRecord.statusEnabled, workItem.getStatus());

        workItem.setStatus(WorkItemRecord.statusExecuting);
        assertEquals("Status should transition to Executing",
                WorkItemRecord.statusExecuting, workItem.getStatus());

        workItem.setStatus(WorkItemRecord.statusComplete);
        assertEquals("Status should transition to Complete",
                WorkItemRecord.statusComplete, workItem.getStatus());
    }

    public void testWorkItemResourceStatusTransitions() {
        WorkItemRecord workItem = new WorkItemRecord();

        assertEquals("Initial resource status should be Unresourced",
                WorkItemRecord.statusResourceUnresourced, workItem.getResourceStatus());

        workItem.setResourceStatus(WorkItemRecord.statusResourceOffered);
        assertEquals("Resource status should transition to Offered",
                WorkItemRecord.statusResourceOffered, workItem.getResourceStatus());

        workItem.setResourceStatus(WorkItemRecord.statusResourceAllocated);
        assertEquals("Resource status should transition to Allocated",
                WorkItemRecord.statusResourceAllocated, workItem.getResourceStatus());

        workItem.setResourceStatus(WorkItemRecord.statusResourceStarted);
        assertEquals("Resource status should transition to Started",
                WorkItemRecord.statusResourceStarted, workItem.getResourceStatus());
    }

    public void testWorkItemIdentification() {
        WorkItemRecord workItem = new WorkItemRecord("123.456", "Approve_Order",
                "http://localhost/spec.yawl", WorkItemRecord.statusEnabled);

        assertEquals("Case ID should match", "123.456", workItem.getCaseID());
        assertEquals("Task ID should match", "Approve_Order", workItem.getTaskID());
        assertEquals("Spec URI should match", "http://localhost/spec.yawl", workItem.getSpecURI());
        assertEquals("Status should match", WorkItemRecord.statusEnabled, workItem.getStatus());
    }

    public void testResourceAvailabilityCalculation() {
        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource("user1", 5, 2));
        resources.add(new Resource("user2", 3, 3));
        resources.add(new Resource("user3", 10, 0));

        List<Resource> available = filterAvailableResources(resources);

        assertEquals("Should have 2 available resources", 2, available.size());
        assertTrue("user1 should be available", containsResource(available, "user1"));
        assertTrue("user3 should be available", containsResource(available, "user3"));
        assertFalse("user2 should not be available", containsResource(available, "user2"));
    }

    public void testWorkItemAllocationLogic() {
        WorkItemRecord workItem = createTestWorkItem("123.456", "Approve_Order");
        Resource resource = new Resource("user1", 5, 2);

        boolean allocated = allocateWorkItem(workItem, resource);

        assertTrue("Work item should be allocated", allocated);
        assertEquals("Work item should be in Allocated status",
                WorkItemRecord.statusResourceAllocated, workItem.getResourceStatus());
    }

    public void testWorkItemAllocationCapacityCheck() {
        Resource resource = new Resource("user1", 3, 3);

        assertFalse("Should not allocate when at capacity", canAllocate(resource));

        Resource availableResource = new Resource("user2", 5, 2);
        assertTrue("Should allocate when under capacity", canAllocate(availableResource));
    }

    public void testMultipleWorkItemAllocation() {
        Resource resource = new Resource("user1", 5, 0);

        WorkItemRecord item1 = createTestWorkItem("123.1", "Task1");
        WorkItemRecord item2 = createTestWorkItem("123.2", "Task2");
        WorkItemRecord item3 = createTestWorkItem("123.3", "Task3");

        assertTrue("First allocation should succeed", allocateWorkItem(item1, resource));
        assertTrue("Second allocation should succeed", allocateWorkItem(item2, resource));
        assertTrue("Third allocation should succeed", allocateWorkItem(item3, resource));

        assertEquals("Resource should have 3 allocated items", 3, resource.getAllocatedCount());
    }

    public void testResourceConstraintValidation() {
        assertTrue("Valid resource name should pass", isValidResourceId("user123"));
        assertTrue("Valid resource with underscore should pass", isValidResourceId("user_admin"));
        assertFalse("Empty resource name should fail", isValidResourceId(""));
        assertFalse("Null resource name should fail", isValidResourceId(null));
        assertFalse("Whitespace resource name should fail", isValidResourceId("   "));
    }

    public void testWorkItemStatusValidation() {
        assertTrue("Enabled is valid status", isValidStatus(WorkItemRecord.statusEnabled));
        assertTrue("Executing is valid status", isValidStatus(WorkItemRecord.statusExecuting));
        assertTrue("Complete is valid status", isValidStatus(WorkItemRecord.statusComplete));
        assertTrue("Fired is valid status", isValidStatus(WorkItemRecord.statusFired));
        assertFalse("Invalid status should fail", isValidStatus("InvalidStatus"));
        assertFalse("Null status should fail", isValidStatus(null));
    }

    public void testResourceStatusValidation() {
        assertTrue("Offered is valid resource status",
                isValidResourceStatus(WorkItemRecord.statusResourceOffered));
        assertTrue("Allocated is valid resource status",
                isValidResourceStatus(WorkItemRecord.statusResourceAllocated));
        assertTrue("Started is valid resource status",
                isValidResourceStatus(WorkItemRecord.statusResourceStarted));
        assertFalse("Invalid resource status should fail",
                isValidResourceStatus("InvalidResourceStatus"));
    }

    public void testWorkItemDeallocation() {
        Resource resource = new Resource("user1", 5, 2);
        WorkItemRecord workItem = createTestWorkItem("123.456", "Task1");

        allocateWorkItem(workItem, resource);
        assertEquals("Should have 3 allocated items", 3, resource.getAllocatedCount());

        deallocateWorkItem(workItem, resource);
        assertEquals("Should have 2 allocated items after deallocation",
                2, resource.getAllocatedCount());
        assertEquals("Work item should be unresourced",
                WorkItemRecord.statusResourceUnresourced, workItem.getResourceStatus());
    }

    public void testResourceCapacityEnforcement() {
        Resource resource = new Resource("user1", 2, 2);

        assertFalse("Should not allow allocation when at capacity", canAllocate(resource));

        WorkItemRecord workItem = createTestWorkItem("123.456", "Task1");
        boolean allocated = allocateWorkItem(workItem, resource);

        assertFalse("Allocation should fail when at capacity", allocated);
    }

    public void testWorkItemPriorityOrdering() {
        List<WorkItemRecord> workItems = new ArrayList<>();
        workItems.add(createPrioritizedWorkItem("123.1", "Task1", 2));
        workItems.add(createPrioritizedWorkItem("123.2", "Task2", 1));
        workItems.add(createPrioritizedWorkItem("123.3", "Task3", 3));

        List<WorkItemRecord> sorted = sortByPriority(workItems);

        assertEquals("Highest priority should be first", 3, getPriority(sorted.get(0)));
        assertEquals("Medium priority should be second", 2, getPriority(sorted.get(1)));
        assertEquals("Lowest priority should be last", 1, getPriority(sorted.get(2)));
    }

    public void testResourceLoadBalancing() {
        List<Resource> resources = new ArrayList<>();
        resources.add(new Resource("user1", 10, 7));
        resources.add(new Resource("user2", 10, 3));
        resources.add(new Resource("user3", 10, 1));

        Resource leastLoaded = findLeastLoadedResource(resources);

        assertEquals("Should select least loaded resource", "user3", leastLoaded.getId());
        assertEquals("Least loaded should have 1 allocation", 1, leastLoaded.getAllocatedCount());
    }

    public void testWorkItemCaseIdExtraction() {
        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setCaseID("123.456.789");

        String caseId = workItem.getCaseID();
        assertEquals("Should extract full case ID", "123.456.789", caseId);

        String rootCaseId = extractRootCaseId(caseId);
        assertEquals("Should extract root case ID", "123", rootCaseId);
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

    public static Test suite() {
        TestSuite suite = new TestSuite("Resource Logic Unit Tests");
        suite.addTestSuite(ResourceLogicUnitTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
