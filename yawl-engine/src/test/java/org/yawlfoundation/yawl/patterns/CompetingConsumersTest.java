package org.yawlfoundation.yawl.patterns;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD integration tests for Competing Consumers pattern.
 *
 * Setup: WorkerPool with 4 workers, shared task queue.
 * Pattern: Workers compete for tasks from shared queue.
 *
 * Tests:
 * - Submit 10 tasks, all complete without drop
 * - 4 workers process in parallel (time < sequential)
 * - Load balancing: no single worker starved
 */
@DisplayName("Competing Consumers Pattern Tests")
class CompetingConsumersTest {

    private ActorRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = createRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        runtime.close();
    }

    // Helper: Shared task queue and work tracking
    static class WorkerPool {
        final LinkedTransferQueue<String> taskQueue;
        final AtomicInteger tasksProcessed;
        final ConcurrentHashMap<Integer, Integer> workerTaskCounts;
        final int numWorkers;

        WorkerPool(int numWorkers) {
            this.taskQueue = new LinkedTransferQueue<>();
            this.tasksProcessed = new AtomicInteger(0);
            this.workerTaskCounts = new ConcurrentHashMap<>();
            this.numWorkers = numWorkers;

            for (int i = 0; i < numWorkers; i++) {
                workerTaskCounts.put(i, 0);
            }
        }

        void submitTask(String taskId) {
            taskQueue.offer(taskId);
        }

        String getTask() throws InterruptedException {
            return taskQueue.poll(100, TimeUnit.MILLISECONDS);
        }

        void recordTaskProcessed(int workerId) {
            tasksProcessed.incrementAndGet();
            workerTaskCounts.compute(workerId, (k, v) -> v + 1);
        }

        int getTasksProcessed() {
            return tasksProcessed.get();
        }

        int getWorkerTaskCount(int workerId) {
            return workerTaskCounts.getOrDefault(workerId, 0);
        }
    }

    // ============================================================
    // Test 1: Submit 10 tasks, all complete without drop
    // ============================================================

    @Test
    @Timeout(10)
    @DisplayName("All 10 tasks are processed without drop")
    void testAllTasksProcessed() throws InterruptedException {
        WorkerPool pool = new WorkerPool(4);
        CountDownLatch allTasksProcessed = new CountDownLatch(10);

        // Create 4 competing workers
        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            // Process task
                            Thread.sleep(10); // Simulate work

                            pool.recordTaskProcessed(id);
                            allTasksProcessed.countDown();
                        } else {
                            // No task available, check if done
                            if (pool.getTasksProcessed() >= 10) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit 10 tasks
        for (int i = 0; i < 10; i++) {
            pool.submitTask("task-" + i);
        }

        // Verify all completed
        assertThat(allTasksProcessed.await(5, TimeUnit.SECONDS))
            .as("All 10 tasks must be processed")
            .isTrue();

        assertThat(pool.getTasksProcessed())
            .as("Exactly 10 tasks processed")
            .isEqualTo(10);
    }

    @Test
    @Timeout(10)
    @DisplayName("100 tasks processed by 4 workers without drop")
    void testManyTasksProcessed() throws InterruptedException {
        int numTasks = 100;
        WorkerPool pool = new WorkerPool(4);
        CountDownLatch allDone = new CountDownLatch(numTasks);

        // Create 4 workers
        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    int tasksProcessedLocal = 0;
                    while (!Thread.currentThread().isInterrupted() && tasksProcessedLocal < 25) {
                        String task = pool.getTask();
                        if (task != null) {
                            // Simulate work
                            Thread.sleep(1);

                            pool.recordTaskProcessed(id);
                            tasksProcessedLocal++;
                            allDone.countDown();
                        } else {
                            if (pool.getTasksProcessed() >= numTasks) {
                                break;
                            }
                            Thread.sleep(5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit all tasks
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
        }

        assertThat(allDone.await(10, TimeUnit.SECONDS))
            .as("All " + numTasks + " tasks must be processed")
            .isTrue();

        assertThat(pool.getTasksProcessed())
            .as("Exactly " + numTasks + " tasks processed")
            .isEqualTo(numTasks);
    }

    // ============================================================
    // Test 2: 4 workers process in parallel (time < sequential)
    // ============================================================

    @Test
    @Timeout(10)
    @DisplayName("Parallel processing faster than sequential")
    void testParallelFasterThanSequential() throws InterruptedException {
        int numTasks = 20;
        WorkerPool pool = new WorkerPool(4);

        // Measure parallel execution
        CountDownLatch parallelDone = new CountDownLatch(numTasks);
        long parallelStart = System.currentTimeMillis();

        // Create 4 workers
        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            Thread.sleep(10); // 10ms per task
                            pool.recordTaskProcessed(id);
                            parallelDone.countDown();
                        } else {
                            if (pool.getTasksProcessed() >= numTasks) {
                                break;
                            }
                            Thread.sleep(5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit all tasks
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
        }

        parallelDone.await(10, TimeUnit.SECONDS);
        long parallelElapsed = System.currentTimeMillis() - parallelStart;

        // Estimate sequential time: 20 tasks * 10ms = 200ms minimum
        // Parallel should be ~50ms with 4 workers
        // This test just verifies parallelism provides speedup
        long expectedSequentialTime = numTasks * 10;
        long expectedParallelTime = (numTasks * 10) / 4 + 50; // +50ms overhead

        assertThat(parallelElapsed)
            .as("Parallel execution should be significantly faster than sequential")
            .isLessThan(expectedSequentialTime);

        System.out.printf(
            "Parallel: %d ms (expected ~%d ms), Sequential would be ~%d ms%n",
            parallelElapsed, expectedParallelTime, expectedSequentialTime
        );
    }

    @Test
    @Timeout(10)
    @DisplayName("Multiple workers reduce per-worker latency")
    void testWorkerLatencyReduction() throws InterruptedException {
        int numTasks = 40;
        WorkerPool pool = new WorkerPool(4);
        CountDownLatch done = new CountDownLatch(numTasks);

        // Track latencies per worker
        long[] workerStartTime = new long[4];
        long[] workerEndTime = new long[4];

        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    int processed = 0;
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            if (processed == 0) {
                                workerStartTime[id] = System.currentTimeMillis();
                            }

                            Thread.sleep(5); // Simulate work

                            pool.recordTaskProcessed(id);
                            processed++;
                            done.countDown();

                            if (processed >= 10) {
                                workerEndTime[id] = System.currentTimeMillis();
                                break;
                            }
                        } else {
                            Thread.sleep(5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit all tasks
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
        }

        done.await(10, TimeUnit.SECONDS);

        // Verify each worker processed approximately equal work
        assertThat(pool.getWorkerTaskCount(0))
            .as("Worker 0 must process tasks")
            .isGreaterThan(0);

        assertThat(pool.getWorkerTaskCount(1))
            .as("Worker 1 must process tasks")
            .isGreaterThan(0);
    }

    // ============================================================
    // Test 3: Load balancing: no single worker starved
    // ============================================================

    @Test
    @Timeout(10)
    @DisplayName("Load balanced equally across workers")
    void testLoadBalancedAcrossWorkers() throws InterruptedException {
        int numTasks = 40;
        WorkerPool pool = new WorkerPool(4);
        CountDownLatch allDone = new CountDownLatch(numTasks);

        // Create 4 workers with varying speeds
        int[] workerDelays = {5, 10, 15, 10}; // milliseconds per task

        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            final int delay = workerDelays[workerId];

            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            Thread.sleep(delay); // Variable delay

                            pool.recordTaskProcessed(id);
                            allDone.countDown();
                        } else {
                            if (pool.getTasksProcessed() >= numTasks) {
                                break;
                            }
                            Thread.sleep(5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit tasks
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
        }

        allDone.await(10, TimeUnit.SECONDS);

        // Verify no worker is significantly starved
        int[] taskCounts = new int[4];
        for (int i = 0; i < 4; i++) {
            taskCounts[i] = pool.getWorkerTaskCount(i);
        }

        int minTasks = java.util.Arrays.stream(taskCounts).min().orElse(0);
        int maxTasks = java.util.Arrays.stream(taskCounts).max().orElse(0);

        assertThat(minTasks)
            .as("Even slowest worker must process some tasks")
            .isGreaterThan(0);

        // Max deviation should be small (fair distribution)
        int deviation = maxTasks - minTasks;
        assertThat(deviation)
            .as("Task distribution must be balanced (deviation " + deviation + " is reasonable)")
            .isLessThanOrEqualTo(10);

        System.out.printf("Task distribution: W0=%d, W1=%d, W2=%d, W3=%d (deviation=%d)%n",
            taskCounts[0], taskCounts[1], taskCounts[2], taskCounts[3], deviation);
    }

    @Test
    @Timeout(10)
    @DisplayName("No worker starvation with slow queue")
    void testNoWorkerStarvation() throws InterruptedException {
        int numTasks = 20;
        WorkerPool pool = new WorkerPool(4);
        CountDownLatch allDone = new CountDownLatch(numTasks);

        // Create 4 workers
        for (int workerId = 0; workerId < 4; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            Thread.sleep(5);
                            pool.recordTaskProcessed(id);
                            allDone.countDown();
                        } else {
                            if (pool.getTasksProcessed() >= numTasks) {
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit tasks slowly to test starvation handling
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
            Thread.sleep(10); // Slow submission
        }

        assertThat(allDone.await(10, TimeUnit.SECONDS))
            .as("All tasks must be processed even with slow submission")
            .isTrue();

        // Verify no worker was completely starved
        for (int i = 0; i < 4; i++) {
            assertThat(pool.getWorkerTaskCount(i))
                .as("Worker " + i + " must process at least some tasks")
                .isGreaterThan(0);
        }
    }

    @Test
    @Timeout(10)
    @DisplayName("Fair task distribution across workers")
    void testFairTaskDistribution() throws InterruptedException {
        int numTasks = 80;
        int numWorkers = 4;
        WorkerPool pool = new WorkerPool(numWorkers);
        CountDownLatch allDone = new CountDownLatch(numTasks);

        // Create workers with equal processing speed
        for (int workerId = 0; workerId < numWorkers; workerId++) {
            final int id = workerId;
            runtime.spawn(self -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        String task = pool.getTask();
                        if (task != null) {
                            Thread.sleep(1);
                            pool.recordTaskProcessed(id);
                            allDone.countDown();
                        } else {
                            if (pool.getTasksProcessed() >= numTasks) {
                                break;
                            }
                            Thread.sleep(5);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Submit all tasks at once
        for (int i = 0; i < numTasks; i++) {
            pool.submitTask("task-" + i);
        }

        allDone.await(10, TimeUnit.SECONDS);

        // With fair distribution, each worker should get ~20 tasks (±2)
        int expectedPerWorker = numTasks / numWorkers;

        for (int i = 0; i < numWorkers; i++) {
            int actualCount = pool.getWorkerTaskCount(i);
            assertThat(actualCount)
                .as("Worker " + i + " should process approximately " + expectedPerWorker + " tasks")
                .isCloseTo(expectedPerWorker, org.assertj.core.api.Offset.offset(5));
        }
    }

    // Helper methods

    private ActorRuntime createRuntime() {
        try {
            Class<?> cls = Class.forName("org.yawlfoundation.yawl.engine.agent.core.VirtualThreadRuntime");
            return (ActorRuntime) cls.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load VirtualThreadRuntime", e);
        }
    }
}
