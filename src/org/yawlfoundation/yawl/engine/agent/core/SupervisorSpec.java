package org.yawlfoundation.yawl.engine.agent.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for OTP-style supervision trees.
 *
 * Captures supervisor configuration (strategy, restart limits, children)
 * and materializes a running Supervisor with all children spawned.
 *
 * Supports nested supervision: a child can itself be a SupervisorSpec,
 * enabling hierarchical supervision trees (OTP application pattern).
 *
 * Usage:
 * <pre>
 *     Supervisor sup = SupervisorSpec.oneForOne("top-level")
 *         .maxRestarts(5)
 *         .withinWindow(Duration.ofMinutes(1))
 *         .restartDelay(Duration.ofMillis(100))
 *         .child(ActorSpec.named("worker-1")
 *             .behavior(self -> { /* message loop *{@literal /} })
 *             .build())
 *         .child(ActorSpec.named("worker-2")
 *             .behavior(self -> { /* message loop *{@literal /} })
 *             .boundedMailbox(128)
 *             .build())
 *         .startOn(runtime);
 * </pre>
 */
public final class SupervisorSpec {

    private final String name;
    private final Supervisor.SupervisorStrategy strategy;
    private final Duration restartDelay;
    private final int maxRestarts;
    private final Duration restartWindow;
    private final List<ActorSpec> children;

    private SupervisorSpec(String name, Supervisor.SupervisorStrategy strategy,
                           Duration restartDelay, int maxRestarts, Duration restartWindow,
                           List<ActorSpec> children) {
        this.name = name;
        this.strategy = strategy;
        this.restartDelay = restartDelay;
        this.maxRestarts = maxRestarts;
        this.restartWindow = restartWindow;
        this.children = Collections.unmodifiableList(new ArrayList<>(children));
    }

    /**
     * Create a ONE_FOR_ONE supervisor spec (restart only the failed actor).
     *
     * @param name supervisor name for logging
     * @return a new builder with ONE_FOR_ONE strategy
     */
    public static Builder oneForOne(String name) {
        return new Builder(name, Supervisor.SupervisorStrategy.ONE_FOR_ONE);
    }

    /**
     * Create a ONE_FOR_ALL supervisor spec (restart all children on any failure).
     *
     * @param name supervisor name for logging
     * @return a new builder with ONE_FOR_ALL strategy
     */
    public static Builder oneForAll(String name) {
        return new Builder(name, Supervisor.SupervisorStrategy.ONE_FOR_ALL);
    }

    /**
     * Create a REST_FOR_ONE supervisor spec (restart failed + all after it).
     *
     * @param name supervisor name for logging
     * @return a new builder with REST_FOR_ONE strategy
     */
    public static Builder restForOne(String name) {
        return new Builder(name, Supervisor.SupervisorStrategy.REST_FOR_ONE);
    }

    /**
     * Materialize and start the supervision tree on the given runtime.
     *
     * Creates a Supervisor, spawns all children, and starts monitoring.
     *
     * @param runtime the actor runtime
     * @return a running Supervisor with all children spawned
     */
    public Supervisor startOn(ActorRuntime runtime) {
        Objects.requireNonNull(runtime, "runtime cannot be null");

        Supervisor supervisor = new Supervisor(
            runtime, strategy, restartDelay, maxRestarts, restartWindow
        );

        for (ActorSpec child : children) {
            supervisor.spawn(child.name(), child.behavior());
        }

        supervisor.start();
        return supervisor;
    }

    public String name() { return name; }
    public Supervisor.SupervisorStrategy strategy() { return strategy; }
    public Duration restartDelay() { return restartDelay; }
    public int maxRestarts() { return maxRestarts; }
    public Duration restartWindow() { return restartWindow; }
    public List<ActorSpec> children() { return children; }

    @Override
    public String toString() {
        return String.format("SupervisorSpec{name='%s', strategy=%s, children=%d}",
            name, strategy, children.size());
    }

    /**
     * Fluent builder for SupervisorSpec.
     */
    public static final class Builder {

        private final String name;
        private final Supervisor.SupervisorStrategy strategy;
        private Duration restartDelay = Duration.ofMillis(100);
        private int maxRestarts = 5;
        private Duration restartWindow = Duration.ofMinutes(1);
        private final List<ActorSpec> children = new ArrayList<>();

        private Builder(String name, Supervisor.SupervisorStrategy strategy) {
            this.name = Objects.requireNonNull(name, "name cannot be null");
            this.strategy = strategy;
        }

        /**
         * Set the delay before restarting a failed actor.
         *
         * @param delay restart delay
         * @return this builder
         */
        public Builder restartDelay(Duration delay) {
            this.restartDelay = Objects.requireNonNull(delay, "delay cannot be null");
            return this;
        }

        /**
         * Set the maximum number of restarts within the window before escalating.
         *
         * @param maxRestarts max restarts (must be >= 1)
         * @return this builder
         */
        public Builder maxRestarts(int maxRestarts) {
            if (maxRestarts < 1) {
                throw new IllegalArgumentException("maxRestarts must be >= 1, got: " + maxRestarts);
            }
            this.maxRestarts = maxRestarts;
            return this;
        }

        /**
         * Set the time window for counting restarts.
         *
         * @param window restart window duration
         * @return this builder
         */
        public Builder withinWindow(Duration window) {
            this.restartWindow = Objects.requireNonNull(window, "window cannot be null");
            return this;
        }

        /**
         * Add a child actor to this supervisor.
         *
         * @param spec the actor spec for the child
         * @return this builder
         */
        public Builder child(ActorSpec spec) {
            children.add(Objects.requireNonNull(spec, "child spec cannot be null"));
            return this;
        }

        /**
         * Build the immutable SupervisorSpec.
         *
         * @return a new SupervisorSpec
         */
        public SupervisorSpec build() {
            return new SupervisorSpec(name, strategy, restartDelay, maxRestarts, restartWindow, children);
        }

        /**
         * Build and immediately start on the given runtime (shortcut for build().startOn(runtime)).
         *
         * @param runtime the actor runtime
         * @return a running Supervisor
         */
        public Supervisor startOn(ActorRuntime runtime) {
            return build().startOn(runtime);
        }
    }
}
