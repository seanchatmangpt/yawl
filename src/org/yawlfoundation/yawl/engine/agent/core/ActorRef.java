package org.yawlfoundation.yawl.engine.agent.core;

import java.util.Objects;

/**
 * Opaque handle to an actor instance.
 *
 * Wraps the actor's internal id and a reference to the runtime,
 * providing a type-safe messaging API without exposing the Agent itself.
 *
 * Thread-safe: All methods are safe to call from any thread.
 */
public final class ActorRef {
    private final int id;
    private final ActorRuntime runtime;

    /**
     * Package-private: only ActorRuntime implementations can create ActorRefs.
     */
    ActorRef(int id, ActorRuntime runtime) {
        this.id = id;
        this.runtime = Objects.requireNonNull(runtime, "runtime cannot be null");
    }

    /**
     * Send a message to this actor asynchronously (fire-and-forget).
     * Non-blocking. No guarantee of delivery if actor has terminated.
     */
    public void tell(Object message) {
        Objects.requireNonNull(message, "message cannot be null");
        runtime.send(id, message);
    }

    /**
     * Block until this actor receives a message from its mailbox.
     * ONLY valid to call from within this actor's own virtual thread.
     *
     * If an ExceptionTrigger was injected (via injectException()), this method
     * will throw the injected RuntimeException instead of returning a message.
     *
     * @return the next message from this actor's mailbox
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public Object recv() throws InterruptedException {
        return runtime.recv(id);
    }

    /**
     * Check if this actor is currently alive (registered in the runtime).
     */
    public boolean isAlive() {
        return runtime.isAlive(id);
    }

    /**
     * Inject a RuntimeException into this actor's behavior.
     *
     * Places an ExceptionTrigger sentinel in the actor's mailbox AND interrupts
     * its virtual thread. The next call to recv() will throw the specified exception.
     */
    public void injectException(RuntimeException cause) {
        Objects.requireNonNull(cause, "cause cannot be null");
        runtime.injectException(id, cause);
    }

    /**
     * Stop this actor immediately. The actor's virtual thread is interrupted
     * and removed from the registry.
     */
    public void stop() {
        runtime.stop(id);
    }

    /**
     * Get the actor's id (useful for debugging and logging).
     */
    public int id() {
        return id;
    }

    /** Package-private for testing. */
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
        return "ActorRef{id=" + id + '}';
    }
}
