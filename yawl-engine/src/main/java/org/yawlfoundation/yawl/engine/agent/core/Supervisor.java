package org.yawlfoundation.yawl.engine.agent.core;

import java.time.Duration;

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
        // Wrap behavior to restart on failure
        return runtime.spawn(ref -> {
            try {
                behavior.accept(ref);
            } catch (Throwable t) {
                handleActorFailure(name, ref, t);
            }
        });
    }

    /**
     * Start monitoring actors (begin health checks and restart logic).
     */
    public void start() {
        // TODO: Implement heartbeat-based health monitoring
        // Schedule periodic health checks, track restart counts
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
        // TODO: Implement graceful shutdown with restart control
    }

    /**
     * Register a dead letter actor to receive all undeliverable messages.
     *
     * Behavior receives Msg.DeadLetter(originalMessage, targetId, reason).
     *
     * @param deadLetterBehavior Receives undeliverable messages
     */
    public void registerDeadLetterHandler(java.util.function.Consumer<Object> deadLetterBehavior) {
        // TODO: Implement dead letter channel routing
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

        // TODO: Increment restart counter for this actor
        // TODO: Check restart limit (maxRestarts per restartWindow)
        // TODO: Apply supervision strategy
        // TODO: Delay (restartDelay) then respawn
    }

    /**
     * Escalate to parent supervisor when child exceeds restart limits.
     */
    private void escalateToParent(String childName, Throwable cause) {
        // TODO: Send Msg.Internal(ESCALATE) to parent supervisor
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
