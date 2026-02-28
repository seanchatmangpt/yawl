package org.yawlfoundation.yawl.engine.agent.patterns;

import org.yawlfoundation.yawl.engine.agent.core.ActorRef;
import org.yawlfoundation.yawl.engine.agent.core.Msg;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Request-Reply Pattern — Synchronous messaging with correlation IDs.
 *
 * Use case: Actor A sends a query to Actor B and waits for a typed reply.
 *
 * Design:
 * - Correlation ID registry (ConcurrentHashMap<Long, CompletableFuture>)
 * - Sender stores reply CF with correlation ID
 * - Receiver sends Reply with same correlation ID
 * - Reply dispatcher looks up CF and completes it
 *
 * Thread-safe. Lock-free. No blocking in hot path.
 *
 * Usage:
 *
 *     // Requester (Actor A):
 *     Msg.Query query = new Msg.Query(cid, selfRef, "GET_STATUS", null);
 *     CompletableFuture<Object> reply = RequestReply.ask(actorB, query, Duration.ofSeconds(5));
 *     Object result = reply.get();
 *
 *     // Responder (Actor B):
 *     case Msg.Query q -> {
 *         Object result = computeStatus();
 *         q.sender().tell(new Msg.Reply(q.correlationId(), result, null));
 *     }
 */
public final class RequestReply {

    private static final ConcurrentHashMap<Long, CompletableFuture<Object>> pendingReplies
        = new ConcurrentHashMap<>();

    private RequestReply() {
        // Utility class, not instantiated
    }

    /**
     * Send a request to an actor and wait for a reply.
     *
     * Correlation ID is used to match reply to request.
     * If no reply arrives within timeout, CF completes with TimeoutException.
     *
     * @param target ActorRef to send request to
     * @param query Query message (must have correlationId and sender set)
     * @param timeout How long to wait for reply
     * @return CompletableFuture completed with reply value
     */
    public static CompletableFuture<Object> ask(ActorRef target, Msg.Query query, Duration timeout) {
        CompletableFuture<Object> cf = new CompletableFuture<>();

        // Store in registry
        CompletableFuture<Object> existing = pendingReplies.put(query.correlationId(), cf);
        if (existing != null) {
            // Correlation ID already in use (should not happen in normal flow)
            existing.completeExceptionally(
                new IllegalStateException("Duplicate correlation ID: " + query.correlationId())
            );
        }

        // Send the query
        target.tell(query);

        // Schedule timeout cleanup
        cf.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                pendingReplies.remove(query.correlationId());
                return null;
            });

        return cf;
    }

    /**
     * Dispatch a reply to waiting requester.
     *
     * Called by message dispatcher when a Reply arrives.
     * Looks up the correlation ID and completes the CF.
     *
     * @param reply Reply message with correlation ID
     */
    public static void dispatch(Msg.Reply reply) {
        CompletableFuture<Object> cf = pendingReplies.remove(reply.correlationId());
        if (cf == null) {
            // No pending request for this reply (already timed out)
            return;
        }

        if (reply.error() != null) {
            cf.completeExceptionally(reply.error());
        } else {
            cf.complete(reply.result());
        }
    }

    /**
     * Get current number of pending requests (for monitoring).
     */
    public static int pendingCount() {
        return pendingReplies.size();
    }

    /**
     * Clear all pending requests (for testing/shutdown).
     * Completes all pending CFs with TimeoutException.
     */
    public static void clearPending() {
        pendingReplies.forEach((cid, cf) -> {
            cf.completeExceptionally(new TimeoutException("Request cleared"));
        });
        pendingReplies.clear();
    }
}
