package org.yawlfoundation.yawl.engine.agent.core;

import java.io.Closeable;
import java.util.function.Consumer;

/**
 * Public API for the actor runtime. Manages actor lifecycle and messaging.
 *
 * Implementations:
 * - YawlActorRuntime: Single-JVM implementation with virtual threads
 * - DistributedActorRuntime: Multi-node implementation (future)
 */
public interface ActorRuntime extends Closeable {

    /**
     * Spawn a new actor with the given behavior.
     * Returns an ActorRef for type-safe, location-transparent messaging.
     *
     * @param behavior the message handler (runs on virtual thread per message)
     * @return an opaque reference to the spawned actor
     */
    ActorRef spawn(Consumer<Object> behavior);

    /**
     * Send a message to an actor by id.
     * Package-private: Called by ActorRef.tell().
     * No-op if id doesn't exist (handles crashed/terminated actors gracefully).
     */
    void send(int targetId, Object msg);

    /**
     * Stop an actor immediately.
     * Package-private: Called by ActorRef.stop().
     * Interrupts the actor's virtual thread.
     */
    void stop(int actorId);

    /**
     * Current number of live actors in this runtime.
     */
    int size();

    /**
     * Shutdown the runtime.
     * All virtual threads are interrupted, registry is cleared.
     */
    @Override
    void close();
}
