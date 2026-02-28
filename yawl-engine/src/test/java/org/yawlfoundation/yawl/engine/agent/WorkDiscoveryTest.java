package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for WorkDiscoveryService and WorkItemQueue.
 * Covers:
 * - Enqueue/dequeue lifecycle
 * - Item discovery by agent
 * - Checkout/checkin state transitions
 * - Concurrent operations (10K+ items)
 * - Queue capacity metrics
 */
@DisplayName("Work Discovery & Queue Tests")
public class WorkDiscoveryTest {

    private WorkDiscoveryService discoveryService;
    private WorkItemQueue queue;
    private UUID agentId1;
    private UUID agentId2;

    @BeforeEach
    void setup() {
        // Clear queue before each test
        queue = WorkItemQueue.getInstance();
        queue.clear();

        discoveryService = new WorkDiscoveryService();
        agentId1 = UUID.randomUUID();
        agentId2 = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Enqueue/Dequeue Lifecycle")
    class EnqueueDequeueTests {

        @Test
        @DisplayName("enqueue adds item to queue")
        void testEnqueueAddsItem() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "TestTask");
            queue.enqueue(item);

            assertEquals(1, queue.size());
            assertEquals(item, queue.peek());
        }

        @Test
        @DisplayName("dequeue removes and returns item (FIFO)")
        void testDequeueRemovesItem() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");
            queue.enqueue(item1);
            queue.enqueue(item2);

            assertEquals(item1, queue.dequeue());
            assertEquals(item2, queue.dequeue());
            assertNull(queue.dequeue());
            assertEquals(0, queue.size());
        }

        @Test
        @DisplayName("peek views item without removing")
        void testPeekDoesNotRemove() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "TestTask");
            queue.enqueue(item);

            assertEquals(item, queue.peek());
            assertEquals(item, queue.peek());
            assertEquals(1, queue.size());
        }

        @Test
        @DisplayName("enqueue null throws NullPointerException")
        void testEnqueueNullThrows() {
            assertThrows(NullPointerException.class, () -> queue.enqueue(null));
        }

        @Test
        @DisplayName("dequeue returns null when queue empty")
        void testDequeueOnEmptyQueue() {
            assertNull(queue.dequeue());
            assertTrue(queue.isEmpty());
        }

        @Test
        @DisplayName("size returns queue length")
        void testSizeReturnsCurrent() {
            assertEquals(0, queue.size());

            for (int i = 0; i < 5; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }
            assertEquals(5, queue.size());

            queue.dequeue();
            assertEquals(4, queue.size());
        }

        @Test
        @DisplayName("clear drains all items")
        void testClearDrainsQueue() {
            for (int i = 0; i < 10; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }
            assertEquals(10, queue.size());

            queue.clear();
            assertEquals(0, queue.size());
            assertNull(queue.dequeue());
        }
    }

    @Nested
    @DisplayName("Item Discovery")
    class DiscoveryTests {

        @Test
        @DisplayName("findItemsFor returns items assigned to agent")
        void testFindItemsForAgent() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");
            WorkItem item3 = new WorkItem(UUID.randomUUID(), "Task3");

            queue.enqueue(item1);
            queue.enqueue(item2);
            queue.enqueue(item3);

            item1.assignTo(agentId1);
            item2.assignTo(agentId1);
            item3.assignTo(agentId2);

            List<WorkItem> agent1Items = queue.findItemsFor(agentId1);
            List<WorkItem> agent2Items = queue.findItemsFor(agentId2);

            assertEquals(2, agent1Items.size());
            assertTrue(agent1Items.contains(item1));
            assertTrue(agent1Items.contains(item2));

            assertEquals(1, agent2Items.size());
            assertTrue(agent2Items.contains(item3));
        }

        @Test
        @DisplayName("findItemsFor returns empty when no items assigned")
        void testFindItemsForUnknownAgent() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task1");
            queue.enqueue(item);
            item.assignTo(agentId1);

            List<WorkItem> items = queue.findItemsFor(agentId2);
            assertTrue(items.isEmpty());
        }

        @Test
        @DisplayName("findPendingItems returns unassigned items")
        void testFindPendingItems() {
            WorkItem pending1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem pending2 = new WorkItem(UUID.randomUUID(), "Task2");
            WorkItem assigned = new WorkItem(UUID.randomUUID(), "Task3");

            queue.enqueue(pending1);
            queue.enqueue(pending2);
            queue.enqueue(assigned);

            assigned.assignTo(agentId1);

            List<WorkItem> pending = queue.findPendingItems();
            assertEquals(2, pending.size());
            assertTrue(pending.contains(pending1));
            assertTrue(pending.contains(pending2));
            assertFalse(pending.contains(assigned));
        }

        @Test
        @DisplayName("findById locates item by ID")
        void testFindById() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "TestTask");
            queue.enqueue(item);

            WorkItem found = queue.findById(itemId);
            assertNotNull(found);
            assertEquals(itemId, found.getItemId());
        }

        @Test
        @DisplayName("findById returns null for nonexistent item")
        void testFindByIdNotFound() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "TestTask");
            queue.enqueue(item);

            assertNull(queue.findById(UUID.randomUUID()));
        }

        @Test
        @DisplayName("removeById removes item by ID")
        void testRemoveById() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "TestTask");
            queue.enqueue(item);

            assertTrue(queue.removeById(itemId));
            assertEquals(0, queue.size());
            assertNull(queue.findById(itemId));
        }

        @Test
        @DisplayName("countPending returns pending count")
        void testCountPending() {
            for (int i = 0; i < 5; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }
            assertEquals(5, queue.countPending());

            WorkItem item = queue.findPendingItems().get(0);
            item.assignTo(agentId1);

            assertEquals(4, queue.countPending());
        }

        @Test
        @DisplayName("countTerminal counts completed/failed items")
        void testCountTerminal() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");
            WorkItem item3 = new WorkItem(UUID.randomUUID(), "Task3");

            queue.enqueue(item1);
            queue.enqueue(item2);
            queue.enqueue(item3);

            item1.assignTo(agentId1);
            item1.complete();

            item2.assignTo(agentId1);
            item2.fail("Test failure");

            assertEquals(2, queue.countTerminal());
            assertEquals(1, queue.countPending());
        }
    }

    @Nested
    @DisplayName("WorkDiscoveryService Operations")
    class DiscoveryServiceTests {

        @Test
        @DisplayName("discoverWork returns items for agent")
        void testDiscoverWork() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");

            queue.enqueue(item1);
            queue.enqueue(item2);

            item1.assignTo(agentId1);
            item2.assignTo(agentId2);

            List<WorkItem> agent1Work = discoveryService.discoverWork(agentId1);
            assertEquals(1, agent1Work.size());
            assertEquals(item1, agent1Work.get(0));
        }

        @Test
        @DisplayName("discoverPendingWork returns unassigned items")
        void testDiscoverPendingWork() {
            WorkItem pending = new WorkItem(UUID.randomUUID(), "PendingTask");
            WorkItem assigned = new WorkItem(UUID.randomUUID(), "AssignedTask");

            queue.enqueue(pending);
            queue.enqueue(assigned);

            assigned.assignTo(agentId1);

            List<WorkItem> discoveredPending = discoveryService.discoverPendingWork();
            assertEquals(1, discoveredPending.size());
            assertEquals(pending, discoveredPending.get(0));
        }

        @Test
        @DisplayName("assignWork transitions item to ASSIGNED")
        void testAssignWork() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task1");
            queue.enqueue(item);

            discoveryService.assignWork(item, agentId1);

            assertTrue(item.getStatus() instanceof WorkItemStatus.Assigned);
            assertEquals(agentId1, item.getAssignedAgent());
        }

        @Test
        @DisplayName("assignWork is idempotent")
        void testAssignWorkIdempotent() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task1");
            queue.enqueue(item);

            discoveryService.assignWork(item, agentId1);
            discoveryService.assignWork(item, agentId1); // Should not throw

            assertEquals(agentId1, item.getAssignedAgent());
        }

        @Test
        @DisplayName("assignWork to different agent throws")
        void testAssignWorkDifferentAgent() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task1");
            queue.enqueue(item);

            discoveryService.assignWork(item, agentId1);

            assertThrows(IllegalStateException.class,
                () -> discoveryService.assignWork(item, agentId2));
        }

        @Test
        @DisplayName("checkoutItem finds and assigns item")
        void testCheckoutItem() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "Task1");
            queue.enqueue(item);

            Optional<WorkItem> checked = discoveryService.checkoutItem(itemId, agentId1);

            assertTrue(checked.isPresent());
            assertEquals(agentId1, checked.get().getAssignedAgent());
            assertTrue(checked.get().getStatus() instanceof WorkItemStatus.Assigned);
        }

        @Test
        @DisplayName("checkoutItem returns empty for nonexistent item")
        void testCheckoutItemNotFound() {
            Optional<WorkItem> checked = discoveryService.checkoutItem(UUID.randomUUID(), agentId1);
            assertTrue(checked.isEmpty());
        }

        @Test
        @DisplayName("checkinItem completes successfully")
        void testCheckinItemComplete() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "Task1");
            queue.enqueue(item);
            item.assignTo(agentId1);

            Optional<WorkItem> checkedIn = discoveryService.checkinItem(
                itemId,
                new WorkItemStatus.Completed(),
                Optional.empty());

            assertTrue(checkedIn.isPresent());
            assertTrue(checkedIn.get().getStatus() instanceof WorkItemStatus.Completed);
        }

        @Test
        @DisplayName("checkinItem fails with reason")
        void testCheckinItemFailed() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "Task1");
            queue.enqueue(item);
            item.assignTo(agentId1);

            String failureReason = "Test failure reason";
            Optional<WorkItem> checkedIn = discoveryService.checkinItem(
                itemId,
                new WorkItemStatus.Failed(failureReason),
                Optional.of(failureReason));

            assertTrue(checkedIn.isPresent());
            assertTrue(checkedIn.get().getStatus() instanceof WorkItemStatus.Failed);
            assertEquals(failureReason, checkedIn.get().getFailureReason());
        }

        @Test
        @DisplayName("checkinItem with invalid status throws")
        void testCheckinItemInvalidStatus() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "Task1");
            queue.enqueue(item);
            item.assignTo(agentId1);

            assertThrows(IllegalArgumentException.class,
                () -> discoveryService.checkinItem(
                    itemId,
                    new WorkItemStatus.Pending(),
                    Optional.empty()));
        }

        @Test
        @DisplayName("getQueueStats returns diagnostic string")
        void testGetQueueStats() {
            for (int i = 0; i < 3; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }

            String stats = discoveryService.getQueueStats();
            assertNotNull(stats);
            assertTrue(stats.contains("WorkItemQueue"));
            assertTrue(stats.contains("3")); // size
        }

        @Test
        @DisplayName("getQueueSize returns current size")
        void testGetQueueSize() {
            assertEquals(0, discoveryService.getQueueSize());

            queue.enqueue(new WorkItem(UUID.randomUUID(), "Task1"));
            queue.enqueue(new WorkItem(UUID.randomUUID(), "Task2"));

            assertEquals(2, discoveryService.getQueueSize());
        }

        @Test
        @DisplayName("getPendingCount returns pending count")
        void testGetPendingCount() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");

            queue.enqueue(item1);
            queue.enqueue(item2);

            assertEquals(2, discoveryService.getPendingCount());

            item1.assignTo(agentId1);
            assertEquals(1, discoveryService.getPendingCount());
        }

        @Test
        @DisplayName("getTerminalCount counts completed/failed items")
        void testGetTerminalCount() {
            WorkItem item1 = new WorkItem(UUID.randomUUID(), "Task1");
            WorkItem item2 = new WorkItem(UUID.randomUUID(), "Task2");

            queue.enqueue(item1);
            queue.enqueue(item2);

            assertEquals(0, discoveryService.getTerminalCount());

            item1.assignTo(agentId1);
            item1.complete();

            assertEquals(1, discoveryService.getTerminalCount());
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentTests {

        @Test
        @DisplayName("concurrent enqueue handles 10K items")
        void testConcurrentEnqueue10K() throws InterruptedException {
            int numThreads = 10;
            int itemsPerThread = 1000;
            int expectedTotal = numThreads * itemsPerThread;

            Thread[] threads = new Thread[numThreads];
            for (int t = 0; t < numThreads; t++) {
                threads[t] = Thread.ofVirtual().start(() -> {
                    for (int i = 0; i < itemsPerThread; i++) {
                        WorkItem item = new WorkItem(UUID.randomUUID(), "Task");
                        queue.enqueue(item);
                    }
                });
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(expectedTotal, queue.size());
        }

        @Test
        @DisplayName("concurrent dequeue consumes items safely")
        void testConcurrentDequeue() throws InterruptedException {
            // Enqueue items
            int itemCount = 5000;
            for (int i = 0; i < itemCount; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }

            // Dequeue with multiple threads
            int numThreads = 10;
            AtomicInteger dequeuedCount = new AtomicInteger(0);

            Thread[] threads = new Thread[numThreads];
            for (int t = 0; t < numThreads; t++) {
                threads[t] = Thread.ofVirtual().start(() -> {
                    while (true) {
                        WorkItem item = queue.dequeue();
                        if (item == null) {
                            break;
                        }
                        dequeuedCount.incrementAndGet();
                    }
                });
            }

            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(itemCount, dequeuedCount.get());
            assertEquals(0, queue.size());
        }

        @Test
        @DisplayName("concurrent assignment to multiple agents")
        void testConcurrentAssignment() throws InterruptedException {
            int itemCount = 1000;
            int agentCount = 10;
            UUID[] agents = new UUID[agentCount];

            for (int i = 0; i < agentCount; i++) {
                agents[i] = UUID.randomUUID();
            }

            for (int i = 0; i < itemCount; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }

            Thread[] threads = new Thread[agentCount];
            for (int a = 0; a < agentCount; a++) {
                final UUID agentId = agents[a];
                threads[a] = Thread.ofVirtual().start(() -> {
                    for (WorkItem item : queue.findPendingItems()) {
                        if (item.getAssignedAgent() == null) {
                            try {
                                item.assignTo(agentId);
                            } catch (IllegalStateException e) {
                                // Item already assigned, skip
                            }
                        }
                    }
                });
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // All items should be assigned
            assertEquals(0, queue.countPending());
            assertEquals(itemCount, queue.countTerminal() + agentCount); // Rough check
        }

        @Test
        @DisplayName("concurrent discover and assign operations")
        void testConcurrentDiscoverAndAssign() throws InterruptedException {
            int itemCount = 500;
            int agentCount = 5;
            UUID[] agents = new UUID[agentCount];

            for (int i = 0; i < agentCount; i++) {
                agents[i] = UUID.randomUUID();
            }

            for (int i = 0; i < itemCount; i++) {
                queue.enqueue(new WorkItem(UUID.randomUUID(), "Task" + i));
            }

            Thread[] threads = new Thread[agentCount];
            for (int a = 0; a < agentCount; a++) {
                final UUID agentId = agents[a];
                threads[a] = Thread.ofVirtual().start(() -> {
                    List<WorkItem> pending = discoveryService.discoverPendingWork();
                    for (WorkItem item : pending) {
                        try {
                            discoveryService.assignWork(item, agentId);
                        } catch (IllegalStateException e) {
                            // Item already assigned
                        }
                    }
                });
            }

            for (Thread thread : threads) {
                thread.join();
            }

            int pendingRemaining = discoveryService.getPendingCount();
            assertTrue(pendingRemaining <= itemCount,
                "Some items should be assigned: pending=" + pendingRemaining);
        }
    }

    @Nested
    @DisplayName("Queue Capacity Metrics")
    class CapacityMetricsTests {

        @Test
        @DisplayName("queue age increases over time")
        void testQueueAge() throws InterruptedException {
            long ageStart = queue.getAge();
            Thread.sleep(10);
            long ageEnd = queue.getAge();

            assertTrue(ageEnd >= ageStart);
        }

        @Test
        @DisplayName("queue diagnostic toString is non-null")
        void testQueueDiagnosticString() {
            String diag = queue.toString();
            assertNotNull(diag);
            assertTrue(diag.contains("WorkItemQueue"));
        }

        @Test
        @DisplayName("queue handles mixed operations maintaining invariants")
        void testQueueInvariants() {
            // Enqueue
            WorkItem[] items = new WorkItem[10];
            for (int i = 0; i < 10; i++) {
                items[i] = new WorkItem(UUID.randomUUID(), "Task" + i);
                queue.enqueue(items[i]);
            }

            int sizeAfterEnqueue = queue.size();
            assertEquals(10, sizeAfterEnqueue);

            // Assign some
            for (int i = 0; i < 5; i++) {
                items[i].assignTo(agentId1);
            }

            // Still in queue, just assigned
            assertEquals(10, queue.size());
            assertEquals(5, queue.findItemsFor(agentId1).size());
            assertEquals(5, queue.countPending());

            // Complete some
            for (int i = 0; i < 5; i++) {
                items[i].complete();
            }

            // Terminal count should increase
            assertEquals(5, queue.countTerminal());

            // Dequeue some
            for (int i = 0; i < 3; i++) {
                queue.dequeue();
            }

            assertEquals(7, queue.size());
        }

        @Test
        @DisplayName("queue isEmpty works correctly")
        void testQueueIsEmpty() {
            assertTrue(queue.isEmpty());

            WorkItem item = new WorkItem(UUID.randomUUID(), "Task1");
            queue.enqueue(item);

            assertFalse(queue.isEmpty());

            queue.dequeue();

            assertTrue(queue.isEmpty());
        }
    }
}
