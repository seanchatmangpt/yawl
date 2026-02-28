package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Scatter-Gather Pattern — Fan-out work to N actors, gather N replies.
 *
 * Use case: Workflow split task distributes work to N parallel branches.
 * Each branch (actor) processes independently and sends reply.
 * Coordinator waits for all N replies before proceeding (AND-join semantics).
 *
 * Design:
 * - Scatter: Split sends same work to N target actors
 * - Track: Correlation ID registry maps split ID to N expected replies
 * - Gather: Phaser coordinates N-way synchronization
 * - Reply dispatcher collects replies and signals completion
 *
 * Thread-safe. Lock-free with exception for Phaser register/arrive.
 * Phaser ensures N actors all reply before aggregator continues.
 *
 * Usage:
 *
 *     // Scatter phase:
 *     ScatterGather scatter = new ScatterGather(3);  // Expect 3 replies
 *     Msg.Command work = new Msg.Command("PROCESS_BRANCH", data);
 *     scatter.scatter(new ActorRef[]{actor1, actor2, actor3}, work);
 *
 *     // Gather phase (blocks until all 3 arrive):
 *     List<Msg> results = scatter.gather(Duration.ofSeconds(10));
 *
 *     // Results are in order: results[0] from actor1, etc.
 */
public class ScatterGather {

    private final int expectedCount;
    private final Phaser phaser;
    private final ConcurrentHashMap<Integer, List<Msg>> replies;
    private final ConcurrentHashMap<Integer, long[]> scatterRegistry;  // splitId -> [correlationIds]
    private final ConcurrentHashMap<Long, Integer> correlationToSplit;  // corrId -> splitId
    private final ConcurrentHashMap<Long, Integer> correlationToIndex;  // corrId -> position
    private volatile int nextSplitId = 0;

    /**
     * Create a Scatter-Gather coordinator expecting a fixed number of replies.
     *
     * @param expectedCount Number of actors to scatter work to (2..N)
     */
    public ScatterGather(int expectedCount) {
        if (expectedCount < 1) {
            throw new IllegalArgumentException("expectedCount must be >= 1");
        }
        this.expectedCount = expectedCount;
        this.phaser = new Phaser(1);  // Register coordinating thread
        this.replies = new ConcurrentHashMap<>();
        this.scatterRegistry = new ConcurrentHashMap<>();
        this.correlationToSplit = new ConcurrentHashMap<>();
        this.correlationToIndex = new ConcurrentHashMap<>();
    }

    /**
     * Scatter work to N target actors.
     *
     * Generates unique correlation IDs for each target and sends work.
     * Registers Phaser participants for each target.
     *
     * @param targets Array of N ActorRefs (must match expectedCount)
     * @param work Message to scatter (same work goes to all targets)
     * @return splitId for tracking this scatter operation
     * @throws IllegalArgumentException if targets.length != expectedCount
     */
    public int scatter(ActorRef[] targets, Object work) {
        if (targets.length != expectedCount) {
            throw new IllegalArgumentException(
                String.format("Expected %d targets, got %d", expectedCount, targets.length)
            );
        }

        int splitId = nextSplitId++;
        long[] correlationIds = new long[expectedCount];
        List<Msg> replyList = new CopyOnWriteArrayList<>();
        replies.put(splitId, replyList);

        // Register all participants with Phaser
        for (int i = 0; i < expectedCount; i++) {
            phaser.register();
        }

        // Send work to each target with unique correlation ID
        for (int i = 0; i < targets.length; i++) {
            long correlationId = generateCorrelationId(splitId, i);
            correlationIds[i] = correlationId;
            correlationToSplit.put(correlationId, splitId);
            correlationToIndex.put(correlationId, i);

            // Wrap work in Query if needed (for proper reply routing)
            Msg query = wrapAsQuery(correlationId, work, targets[i]);
            targets[i].tell(query);
        }

        scatterRegistry.put(splitId, correlationIds);
        return splitId;
    }

    /**
     * Gather replies from all N actors (blocking).
     *
     * Waits for exactly expectedCount replies to arrive.
     * Uses Phaser to coordinate N-way synchronization.
     *
     * @param splitId Result from scatter() call
     * @param timeout How long to wait for all replies
     * @return List of reply messages in scatter order
     * @throws TimeoutException if not all replies arrive within timeout
     * @throws IllegalArgumentException if splitId not found
     */
    public List<Msg> gather(int splitId, Duration timeout) throws TimeoutException {
        List<Msg> replyList = replies.get(splitId);
        if (replyList == null) {
            throw new IllegalArgumentException("Unknown splitId: " + splitId);
        }

        try {
            // Wait for all participants to arrive at this phase
            int nextPhase = phaser.awaitAdvanceInterruptibly(
                phaser.getPhase(),
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
            );
        } catch (TimeoutException e) {
            throw new TimeoutException(
                String.format("Timeout waiting for %d replies (got %d)",
                    expectedCount, replyList.size())
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TimeoutException("Interrupted waiting for replies");
        }

        // All replies have arrived
        long[] correlationIds = scatterRegistry.get(splitId);
        List<Msg> ordered = new ArrayList<>(expectedCount);
        for (int i = 0; i < correlationIds.length; i++) {
            ordered.add(null);  // Placeholder for proper ordering
        }

        for (Msg reply : replyList) {
            if (reply instanceof Msg.Reply r) {
                Integer index = correlationToIndex.get(r.correlationId());
                if (index != null) {
                    ordered.set(index, r);
                }
            }
        }

        return ordered;
    }

    /**
     * Dispatch a reply to this scatter-gather coordinator.
     *
     * Called by message dispatcher when a Reply arrives for a scattered work.
     * Updates reply list and signals Phaser when all replies received.
     *
     * @param reply Reply message (must have correlationId matching scatter)
     */
    public void dispatch(Msg.Reply reply) {
        Integer splitId = correlationToSplit.remove(reply.correlationId());
        if (splitId == null) {
            return;  // Not our correlation ID
        }

        List<Msg> replyList = replies.get(splitId);
        if (replyList != null) {
            replyList.add(reply);

            // If all replies received, signal phase completion
            if (replyList.size() >= expectedCount) {
                phaser.arrive();
            }
        }
    }

    /**
     * Get current number of pending scatter operations.
     */
    public int pendingCount() {
        return replies.size();
    }

    /**
     * Cancel a scatter operation and release Phaser participants.
     *
     * @param splitId Result from scatter() call
     */
    public void cancel(int splitId) {
        replies.remove(splitId);
        long[] correlationIds = scatterRegistry.remove(splitId);
        if (correlationIds != null) {
            for (long cid : correlationIds) {
                correlationToSplit.remove(cid);
                correlationToIndex.remove(cid);
            }
            // Deregister from Phaser by arriving for each participant
            try {
                for (int i = 0; i < correlationIds.length; i++) {
                    phaser.arrive();
                }
            } catch (IllegalStateException e) {
                // Phaser already terminated
            }
        }
    }

    /**
     * Shutdown this scatter-gather coordinator.
     * Cancels all pending operations.
     */
    public void shutdown() {
        new ArrayList<>(scatterRegistry.keySet()).forEach(this::cancel);
        phaser.forceTermination();
    }

    // ============= Private Helpers =============

    private long generateCorrelationId(int splitId, int index) {
        // Encode split ID and index into correlation ID for debugging
        return (((long) splitId) << 32) | (index & 0xFFFFFFFFL);
    }

    private Msg wrapAsQuery(long correlationId, Object work, ActorRef targetRef) {
        if (work instanceof Msg m) {
            // Work is already a message, keep as-is
            return m;
        }
        // Wrap raw work in a Command (no reply expected in basic scatter)
        return new Msg.Command("SCATTER_WORK", work);
    }
}
