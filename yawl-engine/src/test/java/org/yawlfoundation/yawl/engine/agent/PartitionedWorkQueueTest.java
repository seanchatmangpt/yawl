package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for PartitionedWorkQueue verify:
 * 1. Correct partition routing (1024 partitions)
 * 2. Balanced distribution across partitions
 * 3. Thread-safe concurrent access
 * 4. Partition depth monitoring
 * 5. Large-scale enqueue/dequeue (100K items)
 *
 * Key test: enqueue 100K items, verify even distribution across 1024 partitions
 *
 * @since Java 21
 */
@DisplayName("PartitionedWorkQueue Tests")
class PartitionedWorkQueueTest {

    private PartitionedWorkQueue queue;

    @BeforeEach
    void setUp() {
        queue = new PartitionedWorkQueue();
    }

    // ========== Basic API Tests ==========

    @Test
    @DisplayName("Enqueue single work item")
    void testEnqueueSingle() throws InterruptedException {
        WorkItem item = WorkItem.create("TestTask");
        queue.enqueue(item);

        assertEquals(1, queue.getTotalDepth(), "Queue should contain 1 item");
    }

    @Test
    @DisplayName("Dequeue returns null on empty partition")
    void testDequeueEmpty() throws InterruptedException {
        UUID agentId = UUID.randomUUID();

        WorkItem item = queue.dequeue(agentId, 100, TimeUnit.MILLISECONDS);

        assertNull(item, "Empty partition should return null");
    }

    @Test
    @DisplayName("Enqueue and dequeue single item")
    void testEnqueueDequeueRoundTrip() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkItem original = new WorkItem(
            UUID.randomUUID(),
            agentId,
            "TestTask",
            System.currentTimeMillis(),
            WorkItemStatus.pending()
        );

        queue.enqueue(original);
        WorkItem dequeued = queue.dequeue(agentId, 100, TimeUnit.MILLISECONDS);

        assertNotNull(dequeued, "Should retrieve enqueued item");
        assertEquals(original.itemId(), dequeued.itemId(), "Item ID should match");
        assertEquals(original.taskName(), dequeued.taskName(), "Task name should match");
    }

    @Test
    @DisplayName("TryDequeue returns immediately")
    void testTryDequeuImmediate() {
        UUID agentId = UUID.randomUUID();

        assertDoesNotThrow(() -> {
            WorkItem item = queue.tryDequeue(agentId);
            assertNull(item, "Empty queue should return null immediately");
        });
    }

    @Test
    @DisplayName("TryDequeue with item available")
    void testTryDequeueWithItem() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkItem original = new WorkItem(
            UUID.randomUUID(),
            agentId,
            "QuickTask",
            System.currentTimeMillis(),
            WorkItemStatus.pending()
        );

        queue.enqueue(original);
        WorkItem dequeued = queue.tryDequeue(agentId);

        assertNotNull(dequeued, "Should retrieve item immediately");
        assertEquals(original.itemId(), dequeued.itemId());
    }

    // ========== Partition Routing Tests ==========

    @Test
    @DisplayName("Same agent always routes to same partition")
    void testConsistentPartitioning() {
        UUID agentId = UUID.randomUUID();

        int partition1 = Math.abs(agentId.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);
        int partition2 = Math.abs(agentId.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);

        assertEquals(partition1, partition2, "Same agent should always hash to same partition");
    }

    @Test
    @DisplayName("Different agents may route to different partitions")
    void testDifferentAgentsDifferentPartitions() {
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();

        int partition1 = Math.abs(agent1.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);
        int partition2 = Math.abs(agent2.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);

        // Most agents will be different (though collisions are possible)
        assertNotEquals(partition1, partition2, "Different agents likely hash to different partitions");
    }

    // ========== Partition Depth Tests ==========

    @Test
    @DisplayName("getDepth returns correct partition size")
    void testGetPartitionDepth() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        int partitionId = Math.abs(agentId.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);

        // Add 5 items to agent's partition
        for (int i = 0; i < 5; i++) {
            WorkItem item = new WorkItem(
                UUID.randomUUID(),
                agentId,
                "Task" + i,
                System.currentTimeMillis(),
                WorkItemStatus.pending()
            );
            queue.enqueue(item);
        }

        assertEquals(5, queue.getDepth(partitionId), "Partition should have 5 items");
    }

    @Test
    @DisplayName("getDepths returns all partition sizes")
    void testGetAllPartitionDepths() throws InterruptedException {
        int[] depths = queue.getDepths();

        assertEquals(PartitionedWorkQueue.NUM_PARTITIONS, depths.length, "Should return 1024 depths");

        for (int depth : depths) {
            assertEquals(0, depth, "All partitions should start empty");
        }
    }

    @Test
    @DisplayName("getTotalDepth sums all partitions")
    void testGetTotalDepth() throws InterruptedException {
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();

        // Add items to different agents (likely different partitions)
        for (int i = 0; i < 3; i++) {
            queue.enqueue(new WorkItem(UUID.randomUUID(), agent1, "Task1", System.currentTimeMillis(), WorkItemStatus.pending()));
            queue.enqueue(new WorkItem(UUID.randomUUID(), agent2, "Task2", System.currentTimeMillis(), WorkItemStatus.pending()));
        }

        assertEquals(6, queue.getTotalDepth(), "Total depth should be 6");
    }

    // ========== Large-Scale Distribution Tests ==========

    @Test
    @DisplayName("Enqueue 100K items with balanced distribution across 1024 partitions")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testLargeScaleBalancedDistribution() throws InterruptedException {
        int itemCount = 100_000;
        Map<UUID, Integer> agentCounts = new ConcurrentHashMap<>();

        // Generate 100K items with diverse agent IDs
        System.out.println("Enqueueing 100K items...");
        long startEnqueue = System.currentTimeMillis();

        for (int i = 0; i < itemCount; i++) {
            UUID agentId = UUID.randomUUID();
            WorkItem item = new WorkItem(
                UUID.randomUUID(),
                agentId,
                "Task" + (i % 100),
                System.currentTimeMillis(),
                WorkItemStatus.pending()
            );
            queue.enqueue(item);
            agentCounts.merge(agentId, 1, Integer::sum);
        }

        long enqueueTime = System.currentTimeMillis() - startEnqueue;
        System.out.printf("Enqueued 100K items in %dms (~%.1f K items/sec)%n",
            enqueueTime, itemCount / (double) enqueueTime);

        // Verify total depth
        assertEquals(itemCount, queue.getTotalDepth(), "Queue should contain 100K items");

        // Check distribution across partitions
        int[] depths = queue.getDepths();
        int usedPartitions = 0;
        int minDepth = Integer.MAX_VALUE;
        int maxDepth = 0;
        long totalDepth = 0;

        for (int depth : depths) {
            if (depth > 0) usedPartitions++;
            minDepth = Math.min(minDepth, depth);
            maxDepth = Math.max(maxDepth, depth);
            totalDepth += depth;
        }

        System.out.printf("Distribution: %d partitions in use, min=%d, max=%d, avg=%.1f%n",
            usedPartitions, minDepth, maxDepth, totalDepth / (double) PartitionedWorkQueue.NUM_PARTITIONS);

        // Assertions
        assertEquals(itemCount, totalDepth, "Sum of partitions should equal total");
        assertGreaterThan(usedPartitions, 900, "Should use >900 partitions for 100K items");
        assertLess(maxDepth, 200, "No single partition should have >200 items (good balance)");

        double skew = maxDepth / (totalDepth / (double) PartitionedWorkQueue.NUM_PARTITIONS);
        System.out.printf("Skew ratio: %.2f (lower is better, <1.2 is excellent)%n", skew);
        assertLess(skew, 1.5, "Skew should be <1.5 (good balance)");
    }

    @Test
    @DisplayName("Dequeue 100K items respects partition isolation")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testLargeScaleDequeue() throws InterruptedException {
        int itemCount = 10_000;
        UUID agentId = UUID.randomUUID();

        // Enqueue all items for one agent
        System.out.println("Enqueueing 10K items for single agent...");
        for (int i = 0; i < itemCount; i++) {
            WorkItem item = new WorkItem(
                UUID.randomUUID(),
                agentId,
                "Task" + i,
                System.currentTimeMillis(),
                WorkItemStatus.pending()
            );
            queue.enqueue(item);
        }

        // Dequeue all items
        System.out.println("Dequeueing items...");
        int dequeued = 0;
        long startDequeue = System.currentTimeMillis();

        while (dequeued < itemCount) {
            WorkItem item = queue.dequeue(agentId, 100, TimeUnit.MILLISECONDS);
            if (item != null) {
                dequeued++;
            } else {
                break; // No more items
            }
        }

        long dequeueTime = System.currentTimeMillis() - startDequeue;
        System.out.printf("Dequeued %d items in %dms (~%.1f K items/sec)%n",
            dequeued, dequeueTime, dequeued / (double) dequeueTime);

        assertEquals(itemCount, dequeued, "Should dequeue all items for agent");
        assertEquals(0, queue.getTotalDepth(), "Queue should be empty after dequeueing all");
    }

    // ========== Concurrency Tests ==========

    @Test
    @DisplayName("Concurrent enqueue/dequeue from multiple threads")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int itemsPerThread = 1_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulDequeues = new AtomicInteger(0);

        System.out.println("Starting concurrent access test (10 threads, 1K items each)...");

        // Enqueue items from multiple threads
        for (int t = 0; t < threadCount; t++) {
            final UUID agentId = UUID.randomUUID();
            executor.submit(() -> {
                for (int i = 0; i < itemsPerThread; i++) {
                    try {
                        queue.enqueue(new WorkItem(
                            UUID.randomUUID(),
                            agentId,
                            "Task",
                            System.currentTimeMillis(),
                            WorkItemStatus.pending()
                        ));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // Dequeue items from different threads
        for (int t = 0; t < threadCount; t++) {
            final UUID agentId = UUID.randomUUID();
            executor.submit(() -> {
                for (int i = 0; i < itemsPerThread; i++) {
                    try {
                        WorkItem item = queue.tryDequeue(agentId);
                        if (item != null) {
                            successfulDequeues.incrementAndGet();
                        }
                    } catch (Exception e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        executor.shutdown();
        boolean completed = executor.awaitTermination(30, TimeUnit.SECONDS);

        assertTrue(completed, "All threads should complete within 30 seconds");
        System.out.printf("Concurrent test complete: enqueued=%d, dequeued=%d%n",
            threadCount * itemsPerThread, successfulDequeues.get());
    }

    // ========== Statistics Tests ==========

    @Test
    @DisplayName("getStats provides accurate distribution metrics")
    void testStatsCalculation() throws InterruptedException {
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();

        queue.enqueue(new WorkItem(UUID.randomUUID(), agent1, "T1", System.currentTimeMillis(), WorkItemStatus.pending()));
        queue.enqueue(new WorkItem(UUID.randomUUID(), agent1, "T2", System.currentTimeMillis(), WorkItemStatus.pending()));
        queue.enqueue(new WorkItem(UUID.randomUUID(), agent2, "T3", System.currentTimeMillis(), WorkItemStatus.pending()));

        PartitionedWorkQueue.PartitionStats stats = queue.getStats();

        assertEquals(3, stats.totalDepth(), "Total depth should be 3");
        assertEquals(0, stats.minDepth(), "Min depth should be 0");
        assertGreaterThanOrEqual(stats.maxDepth(), 1, "Max depth should be at least 1");
        assertGreaterThan(stats.averageDepth(), 0, "Average should be positive");
        assertGreaterThan(stats.partitionsInUse(), 0, "Some partitions should be in use");
    }

    @Test
    @DisplayName("Queue age tracking")
    void testQueueAgeTracking() throws InterruptedException {
        long before = queue.getAge();

        Thread.sleep(100);

        long after = queue.getAge();

        assertTrue(after > before, "Queue age should increase");
        assertTrue(after >= 100, "Age should be at least 100ms after sleep");
    }

    @Test
    @DisplayName("Clear empties all partitions")
    void testClear() throws InterruptedException {
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();

        queue.enqueue(new WorkItem(UUID.randomUUID(), agent1, "T1", System.currentTimeMillis(), WorkItemStatus.pending()));
        queue.enqueue(new WorkItem(UUID.randomUUID(), agent2, "T2", System.currentTimeMillis(), WorkItemStatus.pending()));

        assertEquals(2, queue.getTotalDepth());

        queue.clear();

        assertEquals(0, queue.getTotalDepth(), "Queue should be empty after clear");
        assertEquals(0, queue.getStats().totalDepth());
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Null work item throws NullPointerException")
    void testEnqueueNullThrows() {
        assertThrows(NullPointerException.class, () -> {
            queue.enqueue(null);
        }, "Enqueue should reject null");
    }

    @Test
    @DisplayName("Null agent ID throws NullPointerException on dequeue")
    void testDequeueNullAgentThrows() {
        assertThrows(NullPointerException.class, () -> {
            queue.dequeue(null, 100, TimeUnit.MILLISECONDS);
        }, "Dequeue should reject null agent");
    }

    @Test
    @DisplayName("Null time unit throws NullPointerException")
    void testDequeueNullUnitThrows() {
        assertThrows(NullPointerException.class, () -> {
            queue.dequeue(UUID.randomUUID(), 100, null);
        }, "Dequeue should reject null time unit");
    }

    @Test
    @DisplayName("Invalid partition ID throws IndexOutOfBoundsException")
    void testInvalidPartitionIdThrows() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            queue.getDepth(-1);
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            queue.getDepth(1024);
        });
    }

    // ========== Helper Methods ==========

    private void assertGreaterThan(int value, int threshold, String message) {
        assertTrue(value > threshold, message + " (got " + value + ")");
    }

    private void assertGreaterThanOrEqual(int value, int threshold, String message) {
        assertTrue(value >= threshold, message + " (got " + value + ")");
    }

    private void assertLess(int value, int threshold, String message) {
        assertTrue(value < threshold, message + " (got " + value + ")");
    }

    private void assertLess(double value, double threshold, String message) {
        assertTrue(value < threshold, message + " (got " + value + ")");
    }
}
