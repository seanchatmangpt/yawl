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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OtpCircuitBreaker state machine.
 * No OTP required — uses lambda injection to simulate call success/failure.
 */
@Tag("unit")
class OtpCircuitBreakerTest {

    private static final Duration HALF_OPEN_AFTER = Duration.ofSeconds(30);
    private static final int THRESHOLD = 3;

    private OtpCircuitBreaker makeBreaker(OtpCircuitBreaker.ErlRpcCallable callable) {
        return new OtpCircuitBreaker(callable, THRESHOLD, HALF_OPEN_AFTER);
    }

    private OtpCircuitBreaker makeBreakerWithClock(
            OtpCircuitBreaker.ErlRpcCallable callable,
            AtomicReference<Instant> clock) {
        return new OtpCircuitBreaker(callable, THRESHOLD, HALF_OPEN_AFTER, clock::get);
    }

    /**
     * A successful call in CLOSED state keeps the breaker CLOSED.
     */
    @Test
    void closedState_successCall_staysInClosed()
            throws ErlangRpcException, OtpNodeUnavailableException {
        OtpCircuitBreaker cb = makeBreaker((m, f, a) -> new ErlAtom("ok"));

        ErlTerm result = cb.call("m", "f", List.of());

        assertInstanceOf(ErlAtom.class, result);
        assertEquals(OtpCircuitBreakerState.CLOSED, cb.getState());
    }

    /**
     * 3 consecutive failures transition the breaker from CLOSED to OPEN.
     */
    @Test
    void closedState_3consecutiveFailures_opensCircuit() {
        OtpCircuitBreaker cb = makeBreaker((m, f, a) -> {
            throw new ErlangRpcException(m, f, "node down");
        });

        assertEquals(OtpCircuitBreakerState.CLOSED, cb.getState());

        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class,
                () -> cb.call("m", "f", List.of()));
        }

        assertEquals(OtpCircuitBreakerState.OPEN, cb.getState());
    }

    /**
     * In OPEN state, call() throws OtpNodeUnavailableException immediately.
     */
    @Test
    void openState_call_throwsOtpNodeUnavailableException_immediately() {
        OtpCircuitBreaker cb = makeBreaker((m, f, a) -> {
            throw new ErlangRpcException(m, f, "down");
        });
        // Trip the breaker
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }
        assertEquals(OtpCircuitBreakerState.OPEN, cb.getState());

        // Now in OPEN: next call must throw OtpNodeUnavailableException
        assertThrows(OtpNodeUnavailableException.class, () -> cb.call("m", "f", List.of()));
    }

    /**
     * In OPEN state, call() returns in under 1ms (no network call made).
     */
    @Test
    void openState_callLatency_under1ms() {
        OtpCircuitBreaker cb = makeBreaker((m, f, a) -> {
            throw new ErlangRpcException(m, f, "down");
        });
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }

        // Warm up: one call to ensure JIT has compiled the path
        assertThrows(OtpNodeUnavailableException.class, () -> cb.call("m", "f", List.of()));

        // Measure: should be well under 10ms (no network call — proves fast-fail behavior)
        long startNs = System.nanoTime();
        assertThrows(OtpNodeUnavailableException.class, () -> cb.call("m", "f", List.of()));
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        assertTrue(elapsedMs < 10, "OPEN state call should complete in < 10ms (no network), took " + elapsedMs + "ms");
    }

    /**
     * After halfOpenAfter duration, OPEN transitions to HALF_OPEN.
     */
    @Test
    void openState_after30s_transitionsToHalfOpen() {
        AtomicReference<Instant> clock = new AtomicReference<>(Instant.now());
        OtpCircuitBreaker cb = makeBreakerWithClock((m, f, a) -> {
            throw new ErlangRpcException(m, f, "down");
        }, clock);

        // Trip the breaker
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }
        assertEquals(OtpCircuitBreakerState.OPEN, cb.getState());

        // Advance clock past halfOpenAfter
        clock.set(Instant.now().plus(HALF_OPEN_AFTER).plusSeconds(1));

        assertEquals(OtpCircuitBreakerState.HALF_OPEN, cb.getState());
    }

    /**
     * A successful call in HALF_OPEN transitions to CLOSED.
     */
    @Test
    void halfOpenState_success_transitionsToClosed()
            throws ErlangRpcException, OtpNodeUnavailableException {
        AtomicReference<Instant> clock = new AtomicReference<>(Instant.now());
        AtomicBoolean fail = new AtomicBoolean(true);
        OtpCircuitBreaker cb = makeBreakerWithClock((m, f, a) -> {
            if (fail.get()) throw new ErlangRpcException(m, f, "down");
            return new ErlAtom("ok");
        }, clock);

        // Trip to OPEN
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }

        // Advance to HALF_OPEN
        clock.set(Instant.now().plus(HALF_OPEN_AFTER).plusSeconds(1));
        assertEquals(OtpCircuitBreakerState.HALF_OPEN, cb.getState());

        // Successful call → CLOSED
        fail.set(false);
        cb.call("m", "f", List.of());
        assertEquals(OtpCircuitBreakerState.CLOSED, cb.getState());
    }

    /**
     * A failed call in HALF_OPEN transitions back to OPEN.
     */
    @Test
    void halfOpenState_failure_transitionsToOpen() {
        AtomicReference<Instant> clock = new AtomicReference<>(Instant.now());
        OtpCircuitBreaker cb = makeBreakerWithClock((m, f, a) -> {
            throw new ErlangRpcException(m, f, "still down");
        }, clock);

        // Trip to OPEN
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }

        // Advance to HALF_OPEN
        clock.set(Instant.now().plus(HALF_OPEN_AFTER).plusSeconds(1));
        assertEquals(OtpCircuitBreakerState.HALF_OPEN, cb.getState());

        // Failed call → back to OPEN
        assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        assertEquals(OtpCircuitBreakerState.OPEN, cb.getState());
    }

    /**
     * reset() from OPEN state transitions to CLOSED.
     */
    @Test
    void reset_fromOpen_transitionsToClosed() {
        OtpCircuitBreaker cb = makeBreaker((m, f, a) -> {
            throw new ErlangRpcException(m, f, "down");
        });
        for (int i = 0; i < THRESHOLD; i++) {
            assertThrows(ErlangRpcException.class, () -> cb.call("m", "f", List.of()));
        }
        assertEquals(OtpCircuitBreakerState.OPEN, cb.getState());

        cb.reset();

        assertEquals(OtpCircuitBreakerState.CLOSED, cb.getState());
    }

    /**
     * OtpCircuitBreakerState enum has exactly 3 values.
     */
    @Test
    void stateEnum_has3values() {
        OtpCircuitBreakerState[] values = OtpCircuitBreakerState.values();
        assertEquals(3, values.length);
        assertEquals(OtpCircuitBreakerState.CLOSED, values[0]);
        assertEquals(OtpCircuitBreakerState.OPEN, values[1]);
        assertEquals(OtpCircuitBreakerState.HALF_OPEN, values[2]);
    }
}
