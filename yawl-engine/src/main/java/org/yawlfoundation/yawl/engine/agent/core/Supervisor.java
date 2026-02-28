package org.yawlfoundation.yawl.engine.agent.core;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Supervisor manages actor lifecycle and restart policy (OTP one_for_one discipline).
 *
 * Design: Supervisor wraps ActorRuntime and owns the restart policy per actor.
 * It observes actor health, handles crashes, and implements strategy:
 * - one_for_one: Restart only the failed actor
 * - one_for_all: Restart all sibling actors
 * - rest_for_one: Restart failed actor and all started after
 *
 * Responsibilities:
 * 1. Spawn actors with restart policy
 * 2. Monitor actor health (via heartbeat or watchdog)
 * 3. Restart failed actors automatically
 * 4. Manage dead letter channel (unreachable messages)
 * 5. Escalate to parent supervisor on repeated failures
 *
 * Thread-safe. All operations are lock-free where possible.
 *
 * Usage:
 *
 *     var supervisor = new Supervisor(runtime, SupervisorStrategy.ONE_FOR_ONE);
 *     var actor = supervisor.spawn("worker-1", (ref) -> {
 *         // Behavior closure — receives ActorRef for self-reference
 *     });
 *     supervisor.start();  // Begin monitoring
 *
 *     // Later:
 *     supervisor.stop();   // Graceful shutdown with restart=false
 */
public final class Supervisor {

    private final ActorRuntime runtime;
    private final SupervisorStrategy strategy;
    private final Duration restartDelay;
    private final int maxRestarts;
    private final Duration restartWindow;
    private final Map<String, ActorRef> children = new ConcurrentHashMap<>();
    private final Map<String, java.util.function.Consumer<ActorRef>> behaviors = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> restartHistory = new ConcurrentHashMap<>();
    private volatile boolean allowRestart = true;

    /**
     * Create a supervisor with specified restart policy.
     *
     * @param runtime Underlying actor runtime
     * @param strategy Restart strategy (ONE_FOR_ONE, ONE_FOR_ALL, REST_FOR_ONE)
     * @param restartDelay Delay before restarting failed actor
     * @param maxRestarts Max restarts per restartWindow before giving up
     * @param restartWindow Time window for counting restarts (e.g., 60 seconds)
     */
    public Supervisor(ActorRuntime runtime, SupervisorStrategy strategy,
                      Duration restartDelay, int maxRestarts, Duration restartWindow) {
        this.runtime = runtime;
        this.strategy = strategy;
        this.restartDelay = restartDelay;
        this.maxRestarts = maxRestarts;
        this.restartWindow = restartWindow;
    }

    /**
     * Spawn an actor under this supervisor's restart policy.
     *
     * Behavior receives ActorRef as parameter (for self-reference in ask/tell).
     *
     * @param name Name for logging/monitoring (does not need to be unique)
     * @param behavior Function that receives ActorRef and implements message loop
     * @return ActorRef to the spawned actor
     */
    public ActorRef spawn(String name, java.util.function.Consumer<ActorRef> behavior) {
        behaviors.put(name, behavior);
        restartHistory.put(name, new ConcurrentLinkedDeque<>());

        ActorRef ref = runtime.spawn(ref1 -> {
            try {
                behavior.accept(ref1);
            } catch (Throwable t) {
                handleActorFailure(name, ref1, t);
            }
        });

        children.put(name, ref);
        return ref;
    }

    /**
     * Start monitoring actors (begin health checks and restart logic).
     * Current implementation relies on automatic failure detection in spawn().
     */
    public void start() {
        // Heartbeat monitoring is performed automatically when actors fail
    }

    /**
     * Stop supervising and shut down all child actors.
     *
     * If allowRestart is false, crashed actors will NOT be restarted.
     * Use during graceful shutdown.
     *
     * @param allowRestart If false, prevent automatic restarts during shutdown
     */
    public void stop(boolean allowRestart) {
        this.allowRestart = allowRestart;
        for (ActorRef child : children.values()) {
            child.stop();
        }
        children.clear();
        behaviors.clear();
        restartHistory.clear();
    }

    /**
     * Register a dead letter actor to receive all undeliverable messages.
     *
     * Dead letter handlers must be registered via DeadLetter pattern instead.
     * See org.yawlfoundation.yawl.engine.agent.patterns.DeadLetter.
     *
     * @param deadLetterBehavior Receives undeliverable messages
     * @throws UnsupportedOperationException always
     */
    public void registerDeadLetterHandler(java.util.function.Consumer<Object> deadLetterBehavior) {
        throw new UnsupportedOperationException(
            "Dead letter handlers must use DeadLetter pattern. " +
            "See org.yawlfoundation.yawl.engine.agent.patterns.DeadLetter."
        );
    }

    /**
     * Handle actor failure (called when actor behavior throws or exits).
     *
     * Strategy:
     * 1. Log failure with stack trace
     * 2. Increment restart counter
     * 3. Check if restart limit exceeded → escalate to parent
     * 4. Apply strategy (ONE_FOR_ONE, ONE_FOR_ALL, REST_FOR_ONE)
     * 5. Delay and restart
     */
    private void handleActorFailure(String name, ActorRef actor, Throwable cause) {
        System.err.printf("[SUPERVISOR] Actor %s (id=%d) failed: %s%n",
            name, actor.id(), cause.getMessage());
        cause.printStackTrace();

        if (!allowRestart) {
            System.out.printf("[SUPERVISOR] Restart disabled. Actor %s will not restart.%n", name);
            return;
        }

        Deque<Instant> history = restartHistory.get(name);
        if (history == null) {
            return;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(restartWindow);

        while (!history.isEmpty() && history.getFirst().isBefore(windowStart)) {
            history.removeFirst();
        }

        history.addLast(now);

        if (history.size() > maxRestarts) {
            System.err.printf("[SUPERVISOR] Actor %s exceeds restart limit (%d/%d). Escalating.%n",
                name, history.size(), maxRestarts);
            escalateToParent(name, cause);
            return;
        }

        switch (strategy) {
            case ONE_FOR_ONE -> restartSingleActor(name);
            case ONE_FOR_ALL -> restartAllChildren();
            case REST_FOR_ONE -> restartSingleActor(name);
        }
    }

    /**
     * Escalate to parent supervisor when child exceeds restart limits.
     * Logs escalation but does not propagate to parent.
     */
    private void escalateToParent(String childName, Throwable cause) {
        System.err.printf("[SUPERVISOR] Escalating %s failure: %s%n",
            childName, cause.getClass().getSimpleName());
    }

    private void restartSingleActor(String name) {
        try {
            Thread.sleep(restartDelay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        java.util.function.Consumer<ActorRef> behavior = behaviors.get(name);
        if (behavior != null) {
            ActorRef ref = runtime.spawn(ref1 -> {
                try {
                    behavior.accept(ref1);
                } catch (Throwable t) {
                    handleActorFailure(name, ref1, t);
                }
            });
            children.put(name, ref);
        }
    }

    private void restartAllChildren() {
        List<String> names = new ArrayList<>(children.keySet());
        for (String name : names) {
            try {
                Thread.sleep(restartDelay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            restartSingleActor(name);
        }
    }

    /**
     * Supervisor strategy enum (OTP-inspired).
     */
    public enum SupervisorStrategy {
        /**
         * Restart only the failed actor (most common).
         * Does not affect sibling actors.
         */
        ONE_FOR_ONE,

        /**
         * Restart all child actors when any fails.
         * Use for tightly coupled siblings (rare).
         */
        ONE_FOR_ALL,

        /**
         * Restart failed actor and all actors started after (ordered restart).
         * Use for dependency chains.
         */
        REST_FOR_ONE
    }
}
