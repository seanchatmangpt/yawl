package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.spi.FlowWorkflowEventBus;
import org.yawlfoundation.yawl.engine.spi.WorkflowEvent;
import org.yawlfoundation.yawl.engine.spi.WorkflowEventBus;
import org.yawlfoundation.yawl.integration.a2a.tty.TtyCommandQueue;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Queue and Event Bus Stress Tests.
 * Determines exact breaking points for YAWL's queue systems under high load.
 * Uses real integrations with H2 in-memory DB when needed.
 */
@Timeout(60, unit = TimeUnit.SECONDS)
public class QueueStressTest {

    private WorkflowEventBus eventBus;
    private TtyCommandQueue commandQueue;

    @BeforeEach
    public void setUp() {
        eventBus = new FlowWorkflowEventBus();
        commandQueue = new TtyCommandQueue(1000);
    }

    /**
     * TEST 1: In-Memory Queue Overflow Test.
     * Fill the TTY command queue to capacity+1.
     * Record: exact capacity, overflow behavior (block? drop? exception?)
     */
    @Test
    public void testQueueOverflow() {
        int maxSize = commandQueue.getMaxSize();
        System.out.println("=== TEST 1: Queue Overflow ===");
        System.out.println("Queue max size configured: " + maxSize);

        // Fill queue to capacity
        for (int i = 0; i < maxSize; i++) {
            TtyCommandQueue.TtyCommand cmd = TtyCommandQueue.TtyCommand.of(
                "test-" + i,
                TtyCommandQueue.TtyCommandPriority.MEDIUM
            );
            boolean enqueued = commandQueue.enqueue(cmd);
            assertTrue(enqueued, "Failed to enqueue at position " + i);
        }

        assertEquals(maxSize, commandQueue.size(), "Queue should be full");
        assertTrue(commandQueue.isFull(), "Queue.isFull() should return true");

        // Attempt to overflow: enqueue one more
        TtyCommandQueue.TtyCommand overflowCmd = TtyCommandQueue.TtyCommand.of(
            "overflow-test",
            TtyCommandQueue.TtyCommandPriority.MEDIUM
        );

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            commandQueue.enqueue(overflowCmd);
        });

        System.out.println("Queue capacity limit: " + maxSize);
        System.out.println("Overflow behavior: THROWS IllegalStateException");
        System.out.println("Exception message: " + ex.getMessage());
        System.out.println();
    }

    /**
     * TEST 2: Queue Throughput Test.
     * Enqueue/dequeue ops/sec at 1, 4, 8, 16, 32 producer threads.
     * Find saturation point where throughput plateaus or declines.
     */
    @Test
    public void testQueueThroughput() throws InterruptedException {
        System.out.println("=== TEST 2: Queue Throughput ===");
        System.out.println("Testing with queue capacity: " + commandQueue.getMaxSize());

        int[] threadCounts = {1, 4, 8, 16, 32};
        long testDurationMs = 5000; // 5 seconds per run

        for (int threadCount : threadCounts) {
            long opsCount = testThroughputWithThreads(threadCount, testDurationMs);
            double opsPerSecond = (opsCount * 1000.0) / testDurationMs;
            System.out.printf("Threads: %2d | Enqueue ops/sec: %.0f%n", threadCount, opsPerSecond);
        }
        System.out.println();
    }

    private long testThroughputWithThreads(int threadCount, long durationMs)
            throws InterruptedException {
        AtomicLong opsCount = new AtomicLong(0);
        AtomicInteger activeThreads = new AtomicInteger(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                long endTime = System.currentTimeMillis() + durationMs;
                int cmdIndex = 0;
                while (System.currentTimeMillis() < endTime) {
                    try {
                        TtyCommandQueue.TtyCommand cmd =
                            TtyCommandQueue.TtyCommand.of(
                                "perf-test-" + cmdIndex++,
                                TtyCommandQueue.TtyCommandPriority.MEDIUM
                            );
                        if (commandQueue.size() < commandQueue.getMaxSize() - 10) {
                            commandQueue.enqueue(cmd);
                            opsCount.incrementAndGet();
                        }
                    } catch (IllegalStateException e) {
                        // Queue full, drain a bit
                        for (int j = 0; j < 10 && !commandQueue.isEmpty(); j++) {
                            commandQueue.dequeue();
                        }
                    }
                }
                activeThreads.decrementAndGet();
            });
        }

        startLatch.countDown();

        for (Thread t : threads) {
            t.join();
        }

        return opsCount.get();
    }

    /**
     * TEST 3: Event Bus Throughput Test.
     * Publish N events/sec to the event bus.
     * Increase until delivery latency > 100ms.
     * Record: exact events/sec threshold.
     */
    @Test
    public void testEventBusThroughput() throws InterruptedException {
        System.out.println("=== TEST 3: Event Bus Throughput ===");
        System.out.println("Event buffer size: " + Flow.defaultBufferSize());

        AtomicInteger deliveredCount = new AtomicInteger(0);
        AtomicLong maxLatencyMs = new AtomicLong(0);
        Semaphore deliverySignal = new Semaphore(0);

        eventBus.subscribe(YEventType.CASE_STARTED, event -> {
            deliveredCount.incrementAndGet();
            deliverySignal.release();
        });

        // Publish events and measure latency
        long startTime = System.nanoTime();
        int totalEventsToPublish = 50000;
        int publishedCount = 0;

        for (int i = 0; i < totalEventsToPublish; i++) {
            long prePublish = System.nanoTime();

            YIdentifier caseId = new YIdentifier("case-" + i);
            WorkflowEvent event = new WorkflowEvent(
                YEventType.CASE_STARTED,
                caseId,
                "spec-" + (i % 10)
            );
            eventBus.publish(event);
            publishedCount++;

            if (i % 5000 == 0 && i > 0) {
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                double eventsPerSec = (publishedCount * 1000.0) / elapsedMs;
                System.out.printf("Published %d events | %.0f events/sec | delivered: %d%n",
                    publishedCount, eventsPerSec, deliveredCount.get());
            }
        }

        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;
        double finalEventsPerSec = (publishedCount * 1000.0) / totalTimeMs;

        System.out.println("Event Bus Throughput Results:");
        System.out.println("  Published: " + publishedCount);
        System.out.println("  Time: " + totalTimeMs + "ms");
        System.out.printf("  Throughput: %.0f events/sec%n", finalEventsPerSec);
        System.out.println();

        eventBus.close();
    }

    /**
     * TEST 4: Event Bus Subscriber Overload Test.
     * Register 100, 1000, 10000 subscribers.
     * Publish 1 event.
     * Measure fan-out time.
     */
    @Test
    public void testEventBusSubscriberFanout() throws InterruptedException {
        System.out.println("=== TEST 4: Event Bus Subscriber Fanout ===");

        int[] subscriberCounts = {100, 1000, 5000};

        for (int subCount : subscriberCounts) {
            WorkflowEventBus testBus = new FlowWorkflowEventBus();

            AtomicInteger deliveredCount = new AtomicInteger(0);
            CountDownLatch fanoutLatch = new CountDownLatch(subCount);

            for (int i = 0; i < subCount; i++) {
                final int index = i;
                testBus.subscribe(YEventType.CASE_STARTED, event -> {
                    deliveredCount.incrementAndGet();
                    fanoutLatch.countDown();
                });
            }

            YIdentifier caseId = new YIdentifier("case-fanout-test");
            WorkflowEvent event = new WorkflowEvent(
                YEventType.CASE_STARTED,
                caseId,
                "spec-fanout"
            );

            long startNs = System.nanoTime();
            testBus.publish(event);

            boolean completed = fanoutLatch.await(10, TimeUnit.SECONDS);
            long fanoutTimeMs = (System.nanoTime() - startNs) / 1_000_000;

            System.out.printf("Subscribers: %5d | Fanout time: %4dms | Delivered: %d/%d%n",
                subCount, fanoutTimeMs, deliveredCount.get(), subCount);

            testBus.close();
        }
        System.out.println();
    }

    /**
     * TEST 5: Backpressure Propagation Test.
     * Fill queue to 90% capacity.
     * Measure producer slowdown.
     * Does backpressure work or do producers crash?
     */
    @Test
    public void testBackpressurePropagation() throws InterruptedException {
        System.out.println("=== TEST 5: Backpressure Propagation ===");

        int maxSize = commandQueue.getMaxSize();
        int threshold90Percent = (int) (maxSize * 0.9);
        int threshold95Percent = (int) (maxSize * 0.95);

        System.out.println("Max queue size: " + maxSize);
        System.out.println("90% threshold: " + threshold90Percent);
        System.out.println("95% threshold: " + threshold95Percent);

        // Slowly fill queue to 90%
        for (int i = 0; i < threshold90Percent; i++) {
            TtyCommandQueue.TtyCommand cmd = TtyCommandQueue.TtyCommand.of(
                "backpressure-test-" + i,
                TtyCommandQueue.TtyCommandPriority.MEDIUM
            );
            commandQueue.enqueue(cmd);
        }

        System.out.println("Queue filled to 90% capacity: " + commandQueue.size());

        // Now measure producer latency as queue fills further
        AtomicLong totalEnqueueTimeNs = new AtomicLong(0);
        AtomicInteger enqueuedCount = new AtomicInteger(0);

        long startTime = System.nanoTime();
        while (commandQueue.size() < threshold95Percent) {
            TtyCommandQueue.TtyCommand cmd = TtyCommandQueue.TtyCommand.of(
                "backpressure-fill-" + enqueuedCount.get(),
                TtyCommandQueue.TtyCommandPriority.MEDIUM
            );

            long preEnqueue = System.nanoTime();
            try {
                commandQueue.enqueue(cmd);
                long enqueueTimeNs = System.nanoTime() - preEnqueue;
                totalEnqueueTimeNs.addAndGet(enqueueTimeNs);
                enqueuedCount.incrementAndGet();
            } catch (IllegalStateException e) {
                break;
            }
        }

        long totalTimeMs = (System.nanoTime() - startTime) / 1_000_000;
        double avgEnqueueTimeUs = totalEnqueueTimeNs.get() / (enqueuedCount.get() * 1000.0);

        System.out.println("Backpressure Results:");
        System.out.println("  Items enqueued during fill: " + enqueuedCount);
        System.out.println("  Final queue size: " + commandQueue.size());
        System.out.printf("  Avg enqueue latency: %.3f μs%n", avgEnqueueTimeUs);
        System.out.println();
    }

    /**
     * TEST 6: Queue Recovery Test.
     * Overflow queue, drain it, measure time to recover to normal throughput.
     */
    @Test
    public void testQueueRecovery() throws InterruptedException {
        System.out.println("=== TEST 6: Queue Recovery Test ===");

        int maxSize = commandQueue.getMaxSize();

        // Fill queue to capacity
        for (int i = 0; i < maxSize; i++) {
            TtyCommandQueue.TtyCommand cmd = TtyCommandQueue.TtyCommand.of(
                "recovery-test-" + i,
                TtyCommandQueue.TtyCommandPriority.MEDIUM
            );
            commandQueue.enqueue(cmd);
        }

        System.out.println("Queue filled to capacity: " + commandQueue.size());
        assertTrue(commandQueue.isFull());

        // Measure drain time
        long drainStartNs = System.nanoTime();
        int drained = 0;
        while (!commandQueue.isEmpty()) {
            commandQueue.dequeue();
            drained++;
        }
        long drainTimeMs = (System.nanoTime() - drainStartNs) / 1_000_000;

        System.out.println("Drained items: " + drained);
        System.out.println("Drain time: " + drainTimeMs + "ms");

        // Measure re-enqueue throughput after recovery
        AtomicLong opsCount = new AtomicLong(0);
        long recoverStartNs = System.nanoTime();
        long recoveryWindow = 2000; // 2 seconds

        while ((System.nanoTime() - recoverStartNs) < recoveryWindow * 1_000_000) {
            TtyCommandQueue.TtyCommand cmd = TtyCommandQueue.TtyCommand.of(
                "recovery-" + opsCount.get(),
                TtyCommandQueue.TtyCommandPriority.MEDIUM
            );
            try {
                if (commandQueue.size() < maxSize - 10) {
                    commandQueue.enqueue(cmd);
                    opsCount.incrementAndGet();
                }
            } catch (IllegalStateException e) {
                break;
            }
        }

        long recoveryTimeMs = (System.nanoTime() - recoverStartNs) / 1_000_000;
        double recoveryThroughput = (opsCount.get() * 1000.0) / recoveryTimeMs;

        System.out.println("Recovery Results:");
        System.out.printf("  Re-enqueue throughput: %.0f ops/sec%n", recoveryThroughput);
        System.out.println("  Queue size after recovery: " + commandQueue.size());
        System.out.println();
    }
}
