package org.yawlfoundation.yawl.engine.agent.fluent;

import org.yawlfoundation.yawl.engine.agent.core.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluent builder for composing OTP-style actor systems.
 *
 * <p>Wraps {@link ActorRuntime} and {@link Supervisor} with a named-actor registry,
 * providing an ergonomic API for building supervised actor hierarchies.</p>
 *
 * <p>Usage:
 * <pre>
 *   var system = ActorSystem.builder()
 *       .runtime(new VirtualThreadRuntime())
 *       .supervisor(s -&gt; s
 *           .strategy(Supervisor.SupervisorStrategy.ONE_FOR_ONE)
 *           .maxRestarts(3)
 *           .within(Duration.ofMinutes(1))
 *           .restartDelay(Duration.ofSeconds(1)))
 *       .actor("worker-1", self -&gt; {
 *           while (true) {
 *               Object msg = self.recv();
 *               // handle message
 *           }
 *       })
 *       .actor("worker-2", self -&gt; {
 *           // another behavior
 *       })
 *       .build();
 *
 *   system.start();
 *   system.tell("worker-1", new Msg.Command("PROCESS", data));
 *   // ...
 *   system.close();
 * </pre>
 *
 * <p>Thread-safe after construction. All operations on the built system
 * delegate to the underlying runtime and supervisor.</p>
 */
public final class ActorSystem implements AutoCloseable {

    private final ActorRuntime runtime;
    private final Supervisor supervisor;
    private final Map<String, ActorRef> actors;

    private ActorSystem(ActorRuntime runtime, Supervisor supervisor,
                        Map<String, ActorRef> actors) {
        this.runtime = runtime;
        this.supervisor = supervisor;
        this.actors = new ConcurrentHashMap<>(actors);
    }

    /**
     * Create a new builder for configuring an actor system.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Start the supervisor's monitoring loop.
     */
    public void start() {
        supervisor.start();
    }

    /**
     * Send a message to a named actor.
     *
     * @param actorName registered actor name
     * @param message   message to send
     * @throws IllegalArgumentException if actorName is not registered
     */
    public void tell(String actorName, Object message) {
        ActorRef ref = actors.get(actorName);
        if (ref == null) {
            throw new IllegalArgumentException(
                "No actor registered with name '" + actorName + "'. " +
                "Available: " + actors.keySet());
        }
        ref.tell(message);
    }

    /**
     * Get the {@link ActorRef} for a named actor.
     *
     * @param actorName registered actor name
     * @return the actor reference, or empty if not found
     */
    public Optional<ActorRef> lookup(String actorName) {
        return Optional.ofNullable(actors.get(actorName));
    }

    /**
     * Get the {@link ActorRef} for a named actor, throwing if absent.
     *
     * @param actorName registered actor name
     * @return the actor reference
     * @throws IllegalArgumentException if not found
     */
    public ActorRef require(String actorName) {
        return lookup(actorName).orElseThrow(() ->
            new IllegalArgumentException(
                "No actor registered with name '" + actorName + "'"));
    }

    /**
     * Check if a named actor is alive.
     *
     * @param actorName registered actor name
     * @return true if the actor exists and is alive
     */
    public boolean isAlive(String actorName) {
        ActorRef ref = actors.get(actorName);
        return ref != null && ref.isAlive();
    }

    /**
     * Get all registered actor names.
     *
     * @return unmodifiable set of actor names
     */
    public Set<String> actorNames() {
        return Collections.unmodifiableSet(actors.keySet());
    }

    /**
     * Get the number of registered actors.
     *
     * @return actor count
     */
    public int size() {
        return actors.size();
    }

    /**
     * Get the underlying runtime for advanced operations.
     *
     * @return the actor runtime
     */
    public ActorRuntime runtime() {
        return runtime;
    }

    /**
     * Get the underlying supervisor for advanced operations.
     *
     * @return the supervisor
     */
    public Supervisor supervisor() {
        return supervisor;
    }

    /**
     * Graceful shutdown: stop supervisor (no restarts), close runtime.
     */
    @Override
    public void close() {
        supervisor.stop(false);
        runtime.close();
    }

    /**
     * Fluent builder for {@link ActorSystem}.
     */
    public static final class Builder {

        private ActorRuntime runtime;
        private SupervisorConfig supervisorConfig;
        private final List<ActorEntry> actorEntries = new ArrayList<>();

        private Builder() {
        }

        /**
         * Set the actor runtime.
         *
         * @param runtime the runtime (e.g., {@code new VirtualThreadRuntime()})
         * @return this builder
         */
        public Builder runtime(ActorRuntime runtime) {
            this.runtime = Objects.requireNonNull(runtime, "runtime");
            return this;
        }

        /**
         * Configure the supervisor via a fluent lambda.
         *
         * @param configurer lambda that configures a {@link SupervisorBuilder}
         * @return this builder
         */
        public Builder supervisor(java.util.function.Consumer<SupervisorBuilder> configurer) {
            SupervisorBuilder sb = new SupervisorBuilder();
            configurer.accept(sb);
            this.supervisorConfig = sb.build();
            return this;
        }

        /**
         * Register a named actor behavior.
         * The actor is spawned under the supervisor when {@link #build()} is called.
         *
         * @param name     unique actor name
         * @param behavior the actor behavior
         * @return this builder
         */
        public Builder actor(String name, ActorBehavior behavior) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(behavior, "behavior");
            actorEntries.add(new ActorEntry(name, behavior));
            return this;
        }

        /**
         * Build the actor system, spawning all registered actors under the supervisor.
         *
         * @return a ready-to-use {@link ActorSystem}
         * @throws IllegalStateException if runtime is not set or no actors defined
         */
        public ActorSystem build() {
            if (runtime == null) {
                runtime = new VirtualThreadRuntime();
            }

            SupervisorConfig config = supervisorConfig != null
                ? supervisorConfig
                : SupervisorConfig.defaults();

            Supervisor sup = new Supervisor(
                runtime,
                config.strategy(),
                config.restartDelay(),
                config.maxRestarts(),
                config.restartWindow()
            );

            Map<String, ActorRef> actors = new LinkedHashMap<>();
            for (ActorEntry entry : actorEntries) {
                if (actors.containsKey(entry.name())) {
                    throw new IllegalArgumentException(
                        "Duplicate actor name: '" + entry.name() + "'");
                }
                ActorRef ref = sup.spawn(entry.name(), entry.behavior());
                actors.put(entry.name(), ref);
            }

            return new ActorSystem(runtime, sup, actors);
        }

        private record ActorEntry(String name, ActorBehavior behavior) {}
    }

    /**
     * Fluent builder for supervisor configuration.
     */
    public static final class SupervisorBuilder {

        private Supervisor.SupervisorStrategy strategy =
            Supervisor.SupervisorStrategy.ONE_FOR_ONE;
        private Duration restartDelay = Duration.ofSeconds(1);
        private int maxRestarts = 3;
        private Duration restartWindow = Duration.ofMinutes(1);

        SupervisorBuilder() {
        }

        /**
         * Set the restart strategy.
         *
         * @param strategy ONE_FOR_ONE, ONE_FOR_ALL, or REST_FOR_ONE
         * @return this builder
         */
        public SupervisorBuilder strategy(Supervisor.SupervisorStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        /**
         * Set the delay before restarting a failed actor.
         *
         * @param delay restart delay
         * @return this builder
         */
        public SupervisorBuilder restartDelay(Duration delay) {
            this.restartDelay = Objects.requireNonNull(delay, "delay");
            return this;
        }

        /**
         * Set the maximum number of restarts within the restart window.
         *
         * @param maxRestarts max restarts (must be &gt;= 1)
         * @return this builder
         */
        public SupervisorBuilder maxRestarts(int maxRestarts) {
            if (maxRestarts < 1) {
                throw new IllegalArgumentException("maxRestarts must be >= 1");
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
        public SupervisorBuilder within(Duration window) {
            this.restartWindow = Objects.requireNonNull(window, "window");
            return this;
        }

        SupervisorConfig build() {
            return new SupervisorConfig(strategy, restartDelay, maxRestarts, restartWindow);
        }
    }

    /**
     * Immutable supervisor configuration snapshot.
     */
    record SupervisorConfig(
        Supervisor.SupervisorStrategy strategy,
        Duration restartDelay,
        int maxRestarts,
        Duration restartWindow
    ) {
        static SupervisorConfig defaults() {
            return new SupervisorConfig(
                Supervisor.SupervisorStrategy.ONE_FOR_ONE,
                Duration.ofSeconds(1),
                3,
                Duration.ofMinutes(1)
            );
        }
    }
}
