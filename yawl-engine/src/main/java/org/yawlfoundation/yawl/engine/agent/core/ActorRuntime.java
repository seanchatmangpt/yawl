package org.yawlfoundation.yawl.engine.agent.core;

import java.io.Closeable;

/**
 * Public API for the actor runtime. Manages actor lifecycle and messaging.
 *
 * Implementations:
 * - VirtualThreadRuntime: High-level implementation with ActorBehavior (throws InterruptedException)
 * - Runtime: Low-level implementation with Consumer<Object> behavior (Agent-level API)
 */
public interface ActorRuntime extends Closeable {

    /**
     * Spawn a new actor with the given behavior.
     * The behavior receives its own ActorRef for self-reference (tell/ask/recv).
     * The behavior may call recv() which throws InterruptedException.
     *
     * @param behavior the actor behavior; receives ActorRef self; may throw InterruptedException
     * @return an opaque reference to the spawned actor
     */
    ActorRef spawn(ActorBehavior behavior);

    /**
     * Spawn a new actor with a bounded mailbox for backpressure.
     * Default implementation throws UnsupportedOperationException.
     * VirtualThreadRuntime provides the full implementation.
     *
     * @param behavior           the actor behavior
     * @param mailboxCapacity    maximum messages in the mailbox (must be > 0)
     * @return an opaque reference to the spawned actor
     * @throws UnsupportedOperationException if not implemented by the runtime
     */
    default ActorRef spawnBounded(ActorBehavior behavior, int mailboxCapacity) {
        throw new UnsupportedOperationException(
            "spawnBounded not implemented by " + getClass().getSimpleName());
    }

    /**
     * Send a message to an actor by id. No-op if id not found.
     * Called by ActorRef.tell() and injectException().
     */
    void send(int targetId, Object msg);

    /**
     * Stop an actor immediately. Removes from registry and interrupts virtual thread.
     * Called by ActorRef.stop().
     */
    void stop(int actorId);

    /**
     * Check if an actor is still alive (in the registry).
     * Called by ActorRef.isAlive().
     */
    boolean isAlive(int actorId);

    /**
     * Block until a message arrives in the actor's mailbox.
     * Called by ActorRef.recv(). ONLY valid to call from within the actor's own virtual thread.
     * Throws ExceptionTrigger.cause() if an ExceptionTrigger sentinel is received.
     *
     * @param actorId the ID of the calling actor
     * @return the next message from the mailbox
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    Object recv(int actorId) throws InterruptedException;

    /**
     * Inject an exception into an actor's behavior.
     * Puts an ExceptionTrigger in the mailbox AND interrupts the actor's virtual thread.
     * Called by ActorRef.injectException().
     *
     * @param actorId the target actor ID
     * @param cause the RuntimeException to inject
     */
    void injectException(int actorId, RuntimeException cause);

    /**
     * Current number of live actors in this runtime.
     */
    int size();

    /**
     * Shutdown the runtime. All virtual threads are interrupted, registry cleared.
     */
    @Override
    void close();
}
