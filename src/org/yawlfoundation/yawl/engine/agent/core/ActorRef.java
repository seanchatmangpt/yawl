package org.yawlfoundation.yawl.engine.agent.core;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Opaque handle to an actor instance. Wraps the actor's internal id and a reference
 * to the runtime, providing a type-safe messaging API without exposing the Actor itself.
 *
 * ActorRef enables:
 * - Serialization (just send the id across the network)
 * - Location transparency (future: resolve id to remote runtime)
 * - Clean API separation (behavior code never sees Actor objects)
 *
 * Thread-safe: All methods are safe to call from any thread.
 */
public final class ActorRef {
    private final int id;
    private final ActorRuntime runtime;

    /**
     * Create a reference to an actor.
     * Package-private: only ActorRuntime implementations can create ActorRefs.
     */
    ActorRef(int id, ActorRuntime runtime) {
        if (id < 0) throw new IllegalArgumentException("id must be non-negative");
        this.id = id;
        this.runtime = Objects.requireNonNull(runtime, "runtime cannot be null");
    }

    /**
     * Send a message to this actor asynchronously (fire-and-forget).
     * Non-blocking. No guarantee of delivery if actor has terminated.
     *
     * @param message the message object (typically a record or sealed type)
     */
    public void tell(Object message) {
        Objects.requireNonNull(message, "message cannot be null");
        runtime.send(id, message);
    }

    /**
     * Send a message and await a reply (request-reply pattern).
     * Blocks until timeout expires or a reply is received.
     *
     * Requires correlation-based request-reply mechanism with request IDs to pair
     * requests with replies. This enables multiple concurrent ask() calls on the same ActorRef.
     *
     * @param message  the request message
     * @param timeout  how long to wait for a reply
     * @return the reply message from the actor
     * @throws TimeoutException if no reply received within timeout
     * @throws InterruptedException if the waiting thread is interrupted
     * @throws UnsupportedOperationException until correlation-based implementation is available
     */
    public Object ask(Object message, Duration timeout)
            throws TimeoutException, InterruptedException {
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(timeout, "timeout cannot be null");
        throw new UnsupportedOperationException(
            "ask() requires implementation of correlation-based request-reply mechanism " +
            "with request IDs. See ACTOR-IMPLEMENTATION-GUIDE.md for design details."
        );
    }

    /**
     * Stop this actor immediately. After calling stop(), tell() and ask() will be no-ops.
     * The actor's virtual thread will receive an InterruptedException.
     */
    public void stop() {
        runtime.stop(id);
    }

    /**
     * Get the actor's id (useful for debugging and logging).
     * @return the actor's numeric id
     */
    public int id() {
        return id;
    }

    /**
     * Get the runtime that manages this actor (package-private for testing).
     */
    ActorRuntime runtime() {
        return runtime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorRef that)) return false;
        return id == that.id && runtime.equals(that.runtime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, runtime);
    }

    @Override
    public String toString() {
        return "ActorRef{" + "id=" + id + '}';
    }
}
