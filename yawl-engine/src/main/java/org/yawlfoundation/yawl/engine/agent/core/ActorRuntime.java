package org.yawlfoundation.yawl.engine.agent.core;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Actor runtime interface — abstracts the low-level actor execution engine.
 *
 * Implementations:
 * - VirtualThreadRuntime: Uses virtual threads per actor (current)
 * - Others: Potential future implementations (green threads, reactive)
 *
 * Thread-safe. All methods are lock-free where possible.
 */
public interface ActorRuntime extends Closeable {

    /**
     * Spawn an actor from a behavior function.
     *
     * @param behavior Function executed on virtual thread (receives ActorRef for self-ref)
     * @return ActorRef to the spawned actor
     */
    ActorRef spawn(java.util.function.Consumer<ActorRef> behavior);

    /**
     * Send a message to an actor by ID (fire-and-forget).
     *
     * No-op if actor is dead.
     *
     * @param targetId Actor ID
     * @param msg Message to send
     */
    void send(int targetId, Object msg);

    /**
     * Send a message and wait for reply (request-reply pattern).
     *
     * Blocks until reply arrives or timeout expires.
     *
     * @param targetId Actor ID
     * @param query Message to send
     * @param timeout How long to wait for reply
     * @return CompletableFuture completed with reply message
     */
    CompletableFuture<Object> ask(int targetId, Object query, Duration timeout);

    /**
     * Stop an actor gracefully.
     *
     * Interrupts the actor's virtual thread.
     *
     * @param targetId Actor ID
     */
    void stop(int targetId);

    /**
     * Current number of live actors.
     */
    int size();

    /**
     * Shut down the entire runtime.
     *
     * Blocks until all actors are stopped.
     */
    @Override
    void close();
}
