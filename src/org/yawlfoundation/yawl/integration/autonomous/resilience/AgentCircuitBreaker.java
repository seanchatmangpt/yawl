/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Circuit breaker for autonomous agent communication with state machine transitions.
 *
 * <p>Protects YAWL-to-agent calls using the circuit breaker pattern:
 * {@code CLOSED → OPEN → HALF_OPEN → CLOSED}. Failure rates are tracked
 * in a sliding count window, and recovery probes run on virtual threads.</p>
 *
 * <h2>State Machine</h2>
 * <ul>
 *   <li><strong>CLOSED</strong>: Normal operation. Failures are counted.</li>
 *   <li><strong>OPEN</strong>: All calls rejected immediately. Transitions to HALF_OPEN after timeout.</li>
 *   <li><strong>HALF_OPEN</strong>: Limited probe calls allowed. Success → CLOSED, Failure → OPEN.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class AgentCircuitBreaker {

    private static final Logger LOG = LogManager.getLogger(AgentCircuitBreaker.class);

    /**
     * Circuit breaker states.
     */
    public enum State { CLOSED, OPEN, HALF_OPEN }

    /**
     * Configuration for the circuit breaker.
     *
     * @param failureThreshold number of failures in window to trip the breaker
     * @param windowSize       sliding window size (number of calls tracked)
     * @param openTimeout      how long to stay OPEN before transitioning to HALF_OPEN
     * @param halfOpenProbes   number of probe calls allowed in HALF_OPEN
     * @param callTimeout      timeout for individual calls
     */
    public record Config(
        int failureThreshold,
        int windowSize,
        Duration openTimeout,
        int halfOpenProbes,
        Duration callTimeout
    ) {
        public Config {
            if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be >= 1");
            if (windowSize < failureThreshold) throw new IllegalArgumentException("windowSize must be >= failureThreshold");
            if (halfOpenProbes < 1) throw new IllegalArgumentException("halfOpenProbes must be >= 1");
            Objects.requireNonNull(openTimeout, "openTimeout must not be null");
            Objects.requireNonNull(callTimeout, "callTimeout must not be null");
        }

        /**
         * Sensible defaults: 5 failures in 10 calls, 30s open, 3 probes, 10s call timeout.
         */
        public static Config defaults() {
            return new Config(5, 10, Duration.ofSeconds(30), 3, Duration.ofSeconds(10));
        }
    }

    /**
     * Events emitted by the circuit breaker.
     */
    public sealed interface CircuitEvent {
        Instant timestamp();

        record StateTransition(State from, State to, Instant timestamp) implements CircuitEvent {}
        record CallSuccess(String callId, Duration elapsed, Instant timestamp) implements CircuitEvent {}
        record CallFailure(String callId, String error, Duration elapsed, Instant timestamp) implements CircuitEvent {}
    }

    /**
     * Exception thrown when the circuit is open.
     */
    public static final class CircuitOpenException extends RuntimeException {
        private final Duration retryAfter;

        public CircuitOpenException(Duration retryAfter) {
            super("Circuit breaker is OPEN, retry after " + retryAfter);
            this.retryAfter = retryAfter;
        }

        public Duration getRetryAfter() { return retryAfter; }
    }

    private final String agentId;
    private final Config config;
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final ReentrantLock stateLock = new ReentrantLock();

    // Sliding window
    private final boolean[] outcomes; // true = success, false = failure
    private int windowIndex;
    private final AtomicInteger failureCount = new AtomicInteger();
    private final AtomicInteger successCount = new AtomicInteger();
    private final AtomicInteger totalCalls = new AtomicInteger();

    // Open state tracking
    private volatile Instant openedAt;
    private final AtomicInteger halfOpenProbeCount = new AtomicInteger();

    // Event listeners
    private final List<Consumer<CircuitEvent>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a circuit breaker for the specified agent.
     *
     * @param agentId the agent this breaker protects
     * @param config  breaker configuration
     */
    public AgentCircuitBreaker(String agentId, Config config) {
        this.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.outcomes = new boolean[config.windowSize()];
        this.windowIndex = 0;
    }

    /**
     * Executes an action through the circuit breaker.
     *
     * @param <T>    the return type
     * @param action the action to execute
     * @return the result of the action
     * @throws CircuitOpenException if the circuit is open
     * @throws Exception            if the action fails
     */
    public <T> T execute(Callable<T> action) throws Exception {
        State current = state.get();

        if (current == State.OPEN) {
            Duration elapsed = Duration.between(openedAt, Instant.now());
            if (elapsed.compareTo(config.openTimeout()) >= 0) {
                transitionTo(State.HALF_OPEN);
            } else {
                throw new CircuitOpenException(config.openTimeout().minus(elapsed));
            }
        }

        if (state.get() == State.HALF_OPEN) {
            if (halfOpenProbeCount.incrementAndGet() > config.halfOpenProbes()) {
                throw new CircuitOpenException(Duration.ofSeconds(5));
            }
        }

        String callId = agentId + "-" + totalCalls.incrementAndGet();
        Instant start = Instant.now();

        try {
            T result = action.call();
            Duration elapsed = Duration.between(start, Instant.now());
            recordSuccess(callId, elapsed);
            return result;
        } catch (Exception e) {
            Duration elapsed = Duration.between(start, Instant.now());
            recordFailure(callId, e.getMessage(), elapsed);
            throw e;
        }
    }

    private void recordSuccess(String callId, Duration elapsed) {
        stateLock.lock();
        try {
            outcomes[windowIndex % outcomes.length] = true;
            windowIndex++;
            successCount.incrementAndGet();

            emit(new CircuitEvent.CallSuccess(callId, elapsed, Instant.now()));

            if (state.get() == State.HALF_OPEN) {
                transitionTo(State.CLOSED);
                halfOpenProbeCount.set(0);
                failureCount.set(0);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void recordFailure(String callId, String error, Duration elapsed) {
        stateLock.lock();
        try {
            outcomes[windowIndex % outcomes.length] = false;
            windowIndex++;
            failureCount.incrementAndGet();

            emit(new CircuitEvent.CallFailure(callId, error, elapsed, Instant.now()));

            if (state.get() == State.HALF_OPEN) {
                transitionTo(State.OPEN);
                halfOpenProbeCount.set(0);
            } else if (state.get() == State.CLOSED && failureCount.get() >= config.failureThreshold()) {
                transitionTo(State.OPEN);
            }
        } finally {
            stateLock.unlock();
        }
    }

    private void transitionTo(State newState) {
        State oldState = state.getAndSet(newState);
        if (oldState != newState) {
            if (newState == State.OPEN) {
                openedAt = Instant.now();
            }
            LOG.info("Circuit breaker [{}] transition: {} → {}", agentId, oldState, newState);
            emit(new CircuitEvent.StateTransition(oldState, newState, Instant.now()));
        }
    }

    /**
     * Returns the current state.
     */
    public State getState() { return state.get(); }

    /**
     * Returns the agent ID this breaker protects.
     */
    public String getAgentId() { return agentId; }

    /**
     * Forces the circuit back to CLOSED.
     */
    public void reset() {
        stateLock.lock();
        try {
            transitionTo(State.CLOSED);
            failureCount.set(0);
            halfOpenProbeCount.set(0);
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Registers a listener for circuit events.
     */
    public void onEvent(Consumer<CircuitEvent> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    private void emit(CircuitEvent event) {
        for (Consumer<CircuitEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                LOG.warn("Event listener error: {}", e.getMessage());
            }
        }
    }
}
