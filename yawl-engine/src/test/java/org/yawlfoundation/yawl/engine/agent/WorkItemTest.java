package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for WorkItem immutable record.
 * Tests lifecycle transitions from pending through completion or failure.
 *
 * @since Java 21
 */
@DisplayName("WorkItem Lifecycle Tests")
class WorkItemTest {

    private String taskName;
    private UUID agentId;
    private UUID workItemId;

    @BeforeEach
    void setUp() {
        taskName = "ProcessInvoice";
        agentId = UUID.randomUUID();
        workItemId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Construction and Creation")
    class CreationTests {

        @Test
        @DisplayName("Create work item with default factory")
        void testCreateDefault() {
            WorkItem item = WorkItem.create(taskName);

            assertNotNull(item);
            assertNotNull(item.itemId());
            assertEquals(taskName, item.taskName());
            assertNull(item.assignedAgent());
            assertTrue(item.status() instanceof WorkItemStatus.Pending);
            assertTrue(item.createdTime() > 0);
        }

        @Test
        @DisplayName("Create work item with explicit ID")
        void testCreateWithId() {
            WorkItem item = WorkItem.create(workItemId, taskName);

            assertEquals(workItemId, item.itemId());
            assertEquals(taskName, item.taskName());
            assertNull(item.assignedAgent());
        }

        @Test
        @DisplayName("Reject null task name")
        void testNullTaskName() {
            assertThrows(NullPointerException.class, () ->
                    WorkItem.create(null)
            );
        }

        @Test
        @DisplayName("Reject null work item ID")
        void testNullWorkItemId() {
            assertThrows(NullPointerException.class, () ->
                    WorkItem.create(null, taskName)
            );
        }

        @Test
        @DisplayName("Create work item with full constructor")
        void testFullConstructor() {
            WorkItemStatus pending = WorkItemStatus.pending();
            WorkItem item = new WorkItem(workItemId, null, taskName, 1000L, pending);

            assertEquals(workItemId, item.itemId());
            assertEquals(taskName, item.taskName());
            assertNull(item.assignedAgent());
            assertEquals(1000L, item.createdTime());
            assertEquals(pending, item.status());
        }
    }

    @Nested
    @DisplayName("Pending State")
    class PendingStateTests {

        @Test
        @DisplayName("New work item is in PENDING state")
        void testNewItemPending() {
            WorkItem item = WorkItem.create(taskName);

            assertTrue(item.isPending());
            assertNull(item.assignedAgent());
            assertFalse(item.isAssigned());
            assertFalse(item.isTerminal());
        }

        @Test
        @DisplayName("Pending work item has minimal age")
        void testPendingAge() {
            WorkItem item = WorkItem.create(taskName);
            long age = item.getAge();

            assertTrue(age >= 0);
            assertTrue(age < 100, "Age should be minimal for newly created item");
        }
    }

    @Nested
    @DisplayName("Assignment Transitions")
    class AssignmentTests {

        @Test
        @DisplayName("Assign pending work item to agent")
        void testAssignToAgent() {
            WorkItem pending = WorkItem.create(taskName);
            WorkItem assigned = pending.assign(agentId);

            assertEquals(agentId, assigned.assignedAgent());
            assertTrue(assigned.isAssigned());
            assertFalse(assigned.isPending());
            assertFalse(assigned.isTerminal());
            assertTrue(assigned.status() instanceof WorkItemStatus.Assigned);
        }

        @Test
        @DisplayName("Assignment preserves work item ID and task name")
        void testAssignmentPreservesIdentity() {
            WorkItem pending = WorkItem.create(workItemId, taskName);
            WorkItem assigned = pending.assign(agentId);

            assertEquals(workItemId, assigned.itemId());
            assertEquals(taskName, assigned.taskName());
            assertEquals(pending.createdTime(), assigned.createdTime());
        }

        @Test
        @DisplayName("Reject null agent ID on assignment")
        void testNullAgentAssignment() {
            WorkItem item = WorkItem.create(taskName);

            assertThrows(NullPointerException.class, () ->
                    item.assign(null)
            );
        }

        @Test
        @DisplayName("Assignment has assignment timestamp")
        void testAssignmentTimestamp() {
            WorkItem item = WorkItem.create(taskName);
            long beforeAssign = System.currentTimeMillis();

            WorkItem assigned = item.assign(agentId);

            long afterAssign = System.currentTimeMillis();
            WorkItemStatus.Assigned status = (WorkItemStatus.Assigned) assigned.status();

            assertTrue(status.assignmentTime() >= beforeAssign);
            assertTrue(status.assignmentTime() <= afterAssign + 100);
        }
    }

    @Nested
    @DisplayName("Completion Transitions")
    class CompletionTests {

        @Test
        @DisplayName("Complete assigned work item")
        void testCompleteWorkItem() {
            WorkItem pending = WorkItem.create(taskName);
            WorkItem assigned = pending.assign(agentId);
            WorkItem completed = assigned.complete();

            assertTrue(completed.isTerminal());
            assertFalse(completed.isPending());
            assertFalse(completed.isAssigned());
            assertTrue(completed.status() instanceof WorkItemStatus.Completed);
        }

        @Test
        @DisplayName("Completed item preserves assignment")
        void testCompletionPreservesAssignment() {
            WorkItem item = WorkItem.create(taskName)
                    .assign(agentId)
                    .complete();

            assertEquals(agentId, item.assignedAgent());
        }

        @Test
        @DisplayName("Completion has completion timestamp")
        void testCompletionTimestamp() {
            long beforeComplete = System.currentTimeMillis();
            WorkItem completed = WorkItem.create(taskName).complete();
            long afterComplete = System.currentTimeMillis();

            WorkItemStatus.Completed status = (WorkItemStatus.Completed) completed.status();

            assertTrue(status.completionTime() >= beforeComplete);
            assertTrue(status.completionTime() <= afterComplete + 100);
        }

        @Test
        @DisplayName("Can complete work item directly from pending")
        void testCompleteFromPending() {
            WorkItem item = WorkItem.create(taskName);
            WorkItem completed = item.complete();

            assertTrue(completed.isTerminal());
            assertTrue(completed.status() instanceof WorkItemStatus.Completed);
            assertNull(completed.assignedAgent());
        }
    }

    @Nested
    @DisplayName("Failure Transitions")
    class FailureTests {

        @Test
        @DisplayName("Fail work item with error reason")
        void testFailWorkItem() {
            WorkItem item = WorkItem.create(taskName);
            String reason = "Database connection timeout";
            WorkItem failed = item.fail(reason);

            assertTrue(failed.isTerminal());
            assertFalse(failed.isPending());
            assertFalse(failed.isAssigned());
            assertTrue(failed.status() instanceof WorkItemStatus.Failed);

            WorkItemStatus.Failed failedStatus = (WorkItemStatus.Failed) failed.status();
            assertEquals(reason, failedStatus.reason());
        }

        @Test
        @DisplayName("Reject null failure reason")
        void testNullFailureReason() {
            WorkItem item = WorkItem.create(taskName);

            assertThrows(NullPointerException.class, () ->
                    item.fail(null)
            );
        }

        @Test
        @DisplayName("Failed item preserves agent assignment")
        void testFailurePreservesAssignment() {
            WorkItem failed = WorkItem.create(taskName)
                    .assign(agentId)
                    .fail("Timeout");

            assertEquals(agentId, failed.assignedAgent());
        }

        @Test
        @DisplayName("Failure has failure timestamp")
        void testFailureTimestamp() {
            long beforeFail = System.currentTimeMillis();
            WorkItem failed = WorkItem.create(taskName).fail("Error");
            long afterFail = System.currentTimeMillis();

            WorkItemStatus.Failed status = (WorkItemStatus.Failed) failed.status();

            assertTrue(status.failureTime() >= beforeFail);
            assertTrue(status.failureTime() <= afterFail + 100);
        }

        @Test
        @DisplayName("Can fail work item from assigned state")
        void testFailFromAssigned() {
            WorkItem item = WorkItem.create(taskName);
            WorkItem assigned = item.assign(agentId);
            WorkItem failed = assigned.fail("Processing error");

            assertTrue(failed.isTerminal());
            assertEquals(agentId, failed.assignedAgent());
        }
    }

    @Nested
    @DisplayName("Age Tracking")
    class AgeTrackingTests {

        @Test
        @DisplayName("Age increases over time")
        void testAgeIncreases() {
            WorkItem item = WorkItem.create(taskName);
            long ageInitial = item.getAge();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            long ageAfterWait = item.getAge();

            assertTrue(ageAfterWait > ageInitial, "Age should increase over time");
        }

        @Test
        @DisplayName("Age is consistent across state transitions")
        void testAgeConsistency() {
            WorkItem pending = WorkItem.create(taskName);
            long ageInitial = pending.getAge();

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            WorkItem assigned = pending.assign(agentId);
            long ageAfterAssign = assigned.getAge();

            assertTrue(ageAfterAssign > ageInitial,
                    "Age should increase for same work item across transitions");
        }
    }

    @Nested
    @DisplayName("Full Lifecycle Transitions")
    class FullLifecycleTests {

        @Test
        @DisplayName("Complete lifecycle: pending -> assigned -> completed")
        void testSuccessfulLifecycle() {
            WorkItem pending = WorkItem.create(taskName);
            assertTrue(pending.isPending());

            WorkItem assigned = pending.assign(agentId);
            assertTrue(assigned.isAssigned());

            WorkItem completed = assigned.complete();
            assertTrue(completed.isTerminal());

            assertEquals(workItemId, completed.itemId());
            assertEquals(agentId, completed.assignedAgent());
        }

        @Test
        @DisplayName("Failed lifecycle: pending -> assigned -> failed")
        void testFailedLifecycle() {
            WorkItem pending = WorkItem.create(taskName);
            WorkItem assigned = pending.assign(agentId);
            String failureReason = "Service unavailable";
            WorkItem failed = assigned.fail(failureReason);

            assertTrue(failed.isTerminal());
            assertEquals(agentId, failed.assignedAgent());

            WorkItemStatus.Failed failedStatus = (WorkItemStatus.Failed) failed.status();
            assertEquals(failureReason, failedStatus.reason());
        }

        @Test
        @DisplayName("Alternate lifecycle: pending -> completed (no assignment)")
        void testAlternateLifecycle() {
            WorkItem pending = WorkItem.create(taskName);
            WorkItem completed = pending.complete();

            assertTrue(completed.isTerminal());
            assertNull(completed.assignedAgent());
        }
    }

    @Nested
    @DisplayName("Immutability and Equality")
    class ImmutabilityTests {

        @Test
        @DisplayName("Record provides value-based equality")
        void testValueEquality() {
            WorkItem item1 = new WorkItem(workItemId, agentId, taskName, 1000L,
                    WorkItemStatus.pending());
            WorkItem item2 = new WorkItem(workItemId, agentId, taskName, 1000L,
                    WorkItemStatus.pending());

            assertEquals(item1, item2);
            assertEquals(item1.hashCode(), item2.hashCode());
        }

        @Test
        @DisplayName("Different items are not equal")
        void testDifferentItemsNotEqual() {
            WorkItem item1 = WorkItem.create(taskName);
            WorkItem item2 = WorkItem.create(taskName);

            assertNotEquals(item1, item2);
        }

        @Test
        @DisplayName("toString provides detailed representation")
        void testToString() {
            WorkItem item = WorkItem.create(workItemId, taskName);
            String str = item.toString();

            assertNotNull(str);
            assertTrue(str.contains("WorkItem"));
            assertTrue(str.contains(taskName));
            assertTrue(str.contains("PENDING"));
        }

        @Test
        @DisplayName("toString includes assignment info when assigned")
        void testToStringAssigned() {
            WorkItem item = WorkItem.create(taskName).assign(agentId);
            String str = item.toString();

            assertTrue(str.contains("ASSIGNED"));
            assertTrue(str.contains(agentId.toString()));
        }
    }

    @Nested
    @DisplayName("State Validation")
    class StateValidationTests {

        @Test
        @DisplayName("Assigned status requires agent ID")
        void testAssignedRequiresAgent() {
            WorkItem item = WorkItem.create(taskName).assign(agentId);
            assertTrue(item.isAssigned());

            assertNotNull(item.assignedAgent());
            assertEquals(agentId, item.assignedAgent());
        }

        @Test
        @DisplayName("Terminal states are idempotent to status checks")
        void testTerminalStateIdempotency() {
            WorkItem completed = WorkItem.create(taskName).complete();
            WorkItem failed = WorkItem.create(taskName).fail("Error");

            assertTrue(completed.isTerminal());
            assertTrue(failed.isTerminal());

            // Calling again should not change
            assertTrue(completed.isTerminal());
            assertTrue(failed.isTerminal());
        }
    }

    @Nested
    @DisplayName("Concurrent Usage Patterns")
    class ConcurrentPatternsTests {

        @Test
        @DisplayName("Multiple state transitions on copies")
        void testMultipleTransitions() {
            WorkItem original = WorkItem.create(taskName);

            WorkItem branch1 = original.assign(UUID.randomUUID()).complete();
            WorkItem branch2 = original.assign(UUID.randomUUID()).fail("Error");

            assertTrue(branch1.isTerminal());
            assertTrue(branch2.isTerminal());
            assertNotEquals(branch1.assignedAgent(), branch2.assignedAgent());
        }
    }
}
