package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.ActorRuntime;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Competing Consumers Pattern — Worker pool for task dispatch.
 *
 * Use case: Workflow has high-throughput task queue.
 * N worker actors compete to take work from shared queue.
 * Scales with virtual threads (1000s of workers without thread pool sizing).
 *
 * Design:
 * - N actor instances all call queue.take() on same LinkedTransferQueue
 * - Blocking take() parks virtual threads when queue is empty
 * - Each worker processes and optionally sends reply
 * - No thread pool bounds (each worker is a virtual thread)
 *
 * Thread-safe. Lock-free. Virtual thread based (no thread pool).
 *
 * Usage:
 *
 *     // Create pool with 10 workers
 *     LinkedTransferQueue<Msg> workQueue = new LinkedTransferQueue<>();
 *     CompetingConsumers pool = new CompetingConsumers("workers", 10, workQueue,
 *         msg -> { System.out.println("Processing: " + msg); });
 *     pool.start(runtime);
 *
 *     // Submit work
 *     workQueue.offer(new Msg.Command("PROCESS", data));
 *
 *     // Workers compete to process
 *     // Shutdown when done
 *     pool.shutdown();
 */
public final class CompetingConsumers {

    private final String poolName;
    private final int workerCount;
    private final BlockingQueue<Object> workQueue;
    private final Consumer<Object> handler;
    private final List<ActorRef> workers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean shutdown = false;

    /**
     * Create a competing consumers worker pool.
     *
     * @param poolName Name for logging (e.g., "task-processors")
     * @param workerCount Number of worker actors to spawn
     * @param workQueue Shared queue where tasks arrive
     * @param handler Function to process each work item
     */
    public CompetingConsumers(String poolName, int workerCount,
                              BlockingQueue<Object> workQueue,
                              Consumer<Object> handler) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be >= 1");
        }
        this.poolName = poolName;
        this.workerCount = workerCount;
        this.workQueue = workQueue;
        this.handler = handler;
    }

    /**
     * Start the worker pool (spawn N worker actors).
     *
     * Each worker loops: take() from queue -> process -> repeat
     * Uses virtual threads (no thread pool bounds).
     *
     * @param runtime ActorRuntime to spawn workers under
     */
    public void start(ActorRuntime runtime) {
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            ActorRef workerRef = runtime.spawn(ref -> {
                workerLoop(ref, workerId);
            });
            workers.add(workerRef);
        }
    }

    /**
     * Submit work to the queue.
     *
     * Workers will compete to process it (first come, first served).
     *
     * @param work Task to process (any Object)
     * @throws RejectedExecutionException if shutdown
     */
    public void submit(Object work) {
        if (shutdown) {
            throw new RejectedExecutionException("Pool is shutdown");
        }
        workQueue.offer(work);
    }

    /**
     * Gracefully shutdown the pool.
     *
     * Sends SHUTDOWN message to all workers.
     * Waits for workers to drain queue and exit.
     *
     * @param timeout How long to wait for workers to stop
     * @return true if all workers stopped, false if timeout
     * @throws InterruptedException if thread is interrupted
     */
    public boolean shutdown(Duration timeout) throws InterruptedException {
        shutdown = true;

        // Send shutdown signal to all workers
        for (int i = 0; i < workerCount; i++) {
            workQueue.offer(new PoisonPill());
        }

        // Wait for all workers to complete
        long deadline = System.nanoTime() + timeout.toNanos();
        for (ActorRef worker : workers) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;  // Timeout
            }
            worker.stop();
        }

        return true;
    }

    /**
     * Get current queue size (for monitoring).
     */
    public int queueSize() {
        return workQueue.size();
    }

    /**
     * Get number of active workers.
     */
    public int activeWorkers() {
        return workers.size();
    }

    // ============= Private Implementation =============

    private void workerLoop(ActorRef self, int workerId) {
        String threadName = String.format("%s-worker-%d", poolName, workerId);
        Thread.currentThread().setName(threadName);

        try {
            while (!shutdown) {
                try {
                    Object work = workQueue.take();  // Blocks until work available

                    // Check for shutdown signal
                    if (work instanceof PoisonPill) {
                        break;  // Exit worker loop
                    }

                    // Process work
                    try {
                        handler.accept(work);
                    } catch (Exception e) {
                        // Log and continue (don't let single error kill worker)
                        System.err.printf("[%s] Worker %d failed: %s%n",
                            threadName, workerId, e.getMessage());
                        e.printStackTrace();
                    }

                } catch (InterruptedException e) {
                    // Handle interruption gracefully
                    if (shutdown) {
                        break;  // Exit loop on shutdown signal
                    }
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } finally {
            // Worker exiting, signal completion
            System.out.printf("[%s-worker-%d] Exited%n", poolName, workerId);
        }
    }

    /**
     * Internal sentinel value for shutdown signaling.
     */
    private static final class PoisonPill {
        @Override
        public String toString() {
            return "PoisonPill";
        }
    }

    /**
     * Convenience factory for creating a pool with default queue.
     *
     * @param poolName Name for logging
     * @param workerCount Number of workers
     * @param handler Processing function
     * @return New CompetingConsumers pool
     */
    public static CompetingConsumers create(String poolName, int workerCount,
                                            Consumer<Object> handler) {
        return new CompetingConsumers(
            poolName,
            workerCount,
            new LinkedTransferQueue<>(),
            handler
        );
    }

    /**
     * Builder for fluent pool configuration.
     */
    public static class Builder {
        private String poolName = "workers";
        private int workerCount = Runtime.getRuntime().availableProcessors();
        private BlockingQueue<Object> workQueue = new LinkedTransferQueue<>();
        private Consumer<Object> handler = x -> {};

        public Builder poolName(String name) {
            this.poolName = name;
            return this;
        }

        public Builder workerCount(int count) {
            this.workerCount = count;
            return this;
        }

        public Builder queue(BlockingQueue<Object> queue) {
            this.workQueue = queue;
            return this;
        }

        public Builder handler(Consumer<Object> handler) {
            this.handler = handler;
            return this;
        }

        public CompetingConsumers build() {
            return new CompetingConsumers(poolName, workerCount, workQueue, handler);
        }
    }
}
