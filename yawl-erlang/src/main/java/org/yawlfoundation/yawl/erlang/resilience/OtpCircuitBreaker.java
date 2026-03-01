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
package org.yawlfoundation.yawl.erlang.resilience;

import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Circuit breaker for OTP node RPC calls.
 *
 * <p>Protects callers from cascading failures when the OTP node is unavailable.
 * In OPEN state, calls fail immediately (< 1ms) with OtpNodeUnavailableException
 * rather than blocking on a network timeout.</p>
 *
 * <p>Usage with a real bridge:
 * <pre>
 *   OtpCircuitBreaker cb = new OtpCircuitBreaker(
 *       (mod, fn, args) -> bridge.getNode().rpc(mod, fn, args),
 *       3, Duration.ofSeconds(30));
 * </pre>
 *
 * <p>Usage in unit tests (lambda injection, no network):
 * <pre>
 *   AtomicBoolean fail = new AtomicBoolean(false);
 *   OtpCircuitBreaker cb = new OtpCircuitBreaker(
 *       (mod, fn, args) -> {
 *           if (fail.get()) throw new ErlangRpcException(mod, fn, "test");
 *           return new ErlAtom("ok");
 *       },
 *       3, Duration.ofSeconds(30));
 * </pre>
 */
public final class OtpCircuitBreaker {

    /**
     * Functional interface for the underlying RPC call.
     * Used by both unit tests (lambdas) and production code (bridge delegation).
     */
    @FunctionalInterface
    public interface ErlRpcCallable {
        ErlTerm call(String module, String function, List<ErlTerm> args)
            throws ErlangRpcException, ErlangConnectionException;
    }

    private final ErlRpcCallable callable;
    private final int failureThreshold;
    private final Duration halfOpenAfter;
    private final Supplier<Instant> clock;

    private volatile OtpCircuitBreakerState state = OtpCircuitBreakerState.CLOSED;
    private volatile int consecutiveFailures = 0;
    private volatile Instant openedAt = null;

    private final AtomicLong totalCalls = new AtomicLong(0);
    private final AtomicLong failedCalls = new AtomicLong(0);
    private final ArrayDeque<Long> recentLatenciesMs = new ArrayDeque<>(100);
    private final Object latencyLock = new Object();

    /**
     * Full constructor with clock injection (for testing time-based transitions).
     *
     * @param callable         the underlying RPC call implementation
     * @param failureThreshold number of consecutive failures before opening
     * @param halfOpenAfter    time to wait in OPEN state before trying HALF_OPEN
     * @param clock            time source (use {@code Instant::now} in production)
     */
    public OtpCircuitBreaker(ErlRpcCallable callable, int failureThreshold,
                              Duration halfOpenAfter, Supplier<Instant> clock) {
        if (callable == null) throw new IllegalArgumentException("callable must not be null");
        if (failureThreshold <= 0) throw new IllegalArgumentException("failureThreshold must be > 0");
        if (halfOpenAfter == null) throw new IllegalArgumentException("halfOpenAfter must not be null");
        if (clock == null) throw new IllegalArgumentException("clock must not be null");
        this.callable = callable;
        this.failureThreshold = failureThreshold;
        this.halfOpenAfter = halfOpenAfter;
        this.clock = clock;
    }

    /**
     * Standard constructor using the system clock.
     *
     * @param callable         the underlying RPC call implementation
     * @param failureThreshold number of consecutive failures before opening
     * @param halfOpenAfter    time to wait in OPEN state before trying HALF_OPEN
     */
    public OtpCircuitBreaker(ErlRpcCallable callable, int failureThreshold, Duration halfOpenAfter) {
        this(callable, failureThreshold, halfOpenAfter, Instant::now);
    }

    /**
     * Makes an RPC call through the circuit breaker.
     *
     * <p>In OPEN state: throws OtpNodeUnavailableException immediately (no network call).
     * In CLOSED/HALF_OPEN: delegates to the callable; updates failure count on exception.
     *
     * @param module   Erlang module name
     * @param function Erlang function name
     * @param args     RPC arguments
     * @return decoded ErlTerm result
     * @throws ErlangRpcException          if the call fails
     * @throws OtpNodeUnavailableException if the circuit is OPEN
     */
    public ErlTerm call(String module, String function, List<ErlTerm> args)
            throws ErlangRpcException, OtpNodeUnavailableException {
        totalCalls.incrementAndGet();

        // Check/transition state before calling
        OtpCircuitBreakerState currentState = evaluateState();

        if (currentState == OtpCircuitBreakerState.OPEN) {
            failedCalls.incrementAndGet();
            throw new OtpNodeUnavailableException(
                "Circuit breaker is OPEN for OTP node. Wait " + halfOpenAfter.toSeconds()
                + "s before retrying. Consecutive failures: " + consecutiveFailures);
        }

        long startNs = System.nanoTime();
        try {
            ErlTerm result = callable.call(module, function, args);
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            recordLatency(latencyMs);
            onSuccess(currentState);
            return result;
        } catch (ErlangRpcException | ErlangConnectionException e) {
            long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            recordLatency(latencyMs);
            failedCalls.incrementAndGet();
            onFailure(currentState);
            if (e instanceof ErlangRpcException rpc) throw rpc;
            throw new ErlangRpcException(module, function, "Connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the current circuit breaker state.
     */
    public OtpCircuitBreakerState getState() {
        return evaluateState();
    }

    /**
     * Forces the circuit breaker back to CLOSED state.
     * Resets failure count. Use for testing or manual recovery.
     */
    public void reset() {
        state = OtpCircuitBreakerState.CLOSED;
        consecutiveFailures = 0;
        openedAt = null;
    }

    /**
     * Returns total call count (including OPEN-state fast-fails).
     */
    public long getTotalCalls() {
        return totalCalls.get();
    }

    /**
     * Returns failed call count.
     */
    public long getFailedCalls() {
        return failedCalls.get();
    }

    /**
     * Returns the p99 latency of the most recent 100 calls, in milliseconds.
     * Returns -1 if no calls have been recorded.
     */
    public long getP99LatencyMs() {
        synchronized (latencyLock) {
            if (recentLatenciesMs.isEmpty()) return -1;
            long[] sorted = recentLatenciesMs.stream()
                .mapToLong(Long::longValue)
                .sorted()
                .toArray();
            int p99Index = (int) Math.ceil(sorted.length * 0.99) - 1;
            return sorted[Math.max(0, p99Index)];
        }
    }

    // =========================================================================
    // Private
    // =========================================================================

    private OtpCircuitBreakerState evaluateState() {
        if (state == OtpCircuitBreakerState.OPEN) {
            Instant opened = openedAt;
            if (opened != null && clock.get().isAfter(opened.plus(halfOpenAfter))) {
                state = OtpCircuitBreakerState.HALF_OPEN;
                return OtpCircuitBreakerState.HALF_OPEN;
            }
        }
        return state;
    }

    private void onSuccess(OtpCircuitBreakerState stateAtCallTime) {
        consecutiveFailures = 0;
        if (stateAtCallTime == OtpCircuitBreakerState.HALF_OPEN) {
            state = OtpCircuitBreakerState.CLOSED;
            openedAt = null;
        }
    }

    private void onFailure(OtpCircuitBreakerState stateAtCallTime) {
        consecutiveFailures++;
        if (stateAtCallTime == OtpCircuitBreakerState.HALF_OPEN) {
            // One failure in HALF_OPEN → back to OPEN
            state = OtpCircuitBreakerState.OPEN;
            openedAt = clock.get();
        } else if (stateAtCallTime == OtpCircuitBreakerState.CLOSED
                && consecutiveFailures >= failureThreshold) {
            state = OtpCircuitBreakerState.OPEN;
            openedAt = clock.get();
        }
    }

    private void recordLatency(long ms) {
        synchronized (latencyLock) {
            if (recentLatenciesMs.size() >= 100) {
                recentLatenciesMs.pollFirst();
            }
            recentLatenciesMs.addLast(ms);
        }
    }
}
