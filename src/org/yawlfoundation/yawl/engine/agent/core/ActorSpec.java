package org.yawlfoundation.yawl.engine.agent.core;

import java.util.Objects;

/**
 * Fluent builder for actor specifications — OTP-style actor configuration.
 *
 * Captures actor identity, behavior, and mailbox configuration before spawning.
 * Immutable after build() — safe to share across threads.
 *
 * Usage:
 * <pre>
 *     ActorSpec spec = ActorSpec.named("worker-1")
 *         .behavior(self -> {
 *             while (true) {
 *                 Object msg = self.recv();
 *                 // handle msg
 *             }
 *         })
 *         .boundedMailbox(256)
 *         .build();
 *
 *     ActorRef ref = spec.spawnOn(runtime);
 * </pre>
 */
public final class ActorSpec {

    private final String name;
    private final ActorBehavior behavior;
    private final int mailboxCapacity;
    private final boolean bounded;

    private ActorSpec(String name, ActorBehavior behavior, int mailboxCapacity, boolean bounded) {
        this.name = name;
        this.behavior = behavior;
        this.mailboxCapacity = mailboxCapacity;
        this.bounded = bounded;
    }

    /**
     * Begin building an actor spec with a name (for logging/monitoring).
     *
     * @param name actor name
     * @return a new builder
     */
    public static Builder named(String name) {
        return new Builder(Objects.requireNonNull(name, "name cannot be null"));
    }

    /**
     * Spawn this actor on the given runtime.
     *
     * @param runtime the actor runtime to spawn on
     * @return ActorRef handle to the spawned actor
     */
    public ActorRef spawnOn(ActorRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime cannot be null");
        if (bounded) {
            return runtime.spawnBounded(behavior, mailboxCapacity);
        }
        return runtime.spawn(behavior);
    }

    /**
     * Get the actor name.
     */
    public String name() {
        return name;
    }

    /**
     * Get the behavior.
     */
    public ActorBehavior behavior() {
        return behavior;
    }

    /**
     * Check if this actor uses a bounded mailbox.
     */
    public boolean isBounded() {
        return bounded;
    }

    /**
     * Get the mailbox capacity (meaningful only if bounded).
     */
    public int mailboxCapacity() {
        return mailboxCapacity;
    }

    @Override
    public String toString() {
        return String.format("ActorSpec{name='%s', bounded=%s, capacity=%d}",
            name, bounded, mailboxCapacity);
    }

    /**
     * Fluent builder for ActorSpec.
     */
    public static final class Builder {

        private final String name;
        private ActorBehavior behavior;
        private int mailboxCapacity;
        private boolean bounded;

        private Builder(String name) {
            this.name = name;
        }

        /**
         * Set the actor behavior (message loop).
         *
         * @param behavior receives ActorRef self for tell/recv/ask
         * @return this builder
         */
        public Builder behavior(ActorBehavior behavior) {
            this.behavior = Objects.requireNonNull(behavior, "behavior cannot be null");
            return this;
        }

        /**
         * Configure a bounded mailbox with backpressure.
         *
         * @param capacity maximum messages in mailbox (must be > 0)
         * @return this builder
         */
        public Builder boundedMailbox(int capacity) {
            if (capacity <= 0) {
                throw new IllegalArgumentException("capacity must be > 0, got: " + capacity);
            }
            this.mailboxCapacity = capacity;
            this.bounded = true;
            return this;
        }

        /**
         * Use unbounded mailbox (default, fire-and-forget).
         *
         * @return this builder
         */
        public Builder unboundedMailbox() {
            this.bounded = false;
            this.mailboxCapacity = 0;
            return this;
        }

        /**
         * Build the immutable ActorSpec.
         *
         * @return a new ActorSpec
         * @throws IllegalStateException if behavior not set
         */
        public ActorSpec build() {
            if (behavior == null) {
                throw new IllegalStateException("behavior must be set before build()");
            }
            return new ActorSpec(name, behavior, mailboxCapacity, bounded);
        }
    }
}
