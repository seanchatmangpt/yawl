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

/**
 * Health endpoint for the OTP node integration.
 *
 * <p>Aggregates health metrics from the circuit breaker and lifecycle manager
 * into a single snapshot. Suitable for exposing as a /health or /metrics endpoint.</p>
 */
public final class OtpHealthEndpoint {

    /**
     * Snapshot of health metrics at a point in time.
     *
     * @param status         "UP" (CLOSED), "RECOVERING" (HALF_OPEN), or "DOWN" (OPEN)
     * @param restarts       cumulative OTP node restart count
     * @param totalCalls     total RPC call count (including fast-fails)
     * @param failedCalls    failed RPC call count
     * @param p99LatencyMs   p99 latency of recent 100 calls in ms (-1 if no data)
     */
    public record Snapshot(
        String status,
        long restarts,
        long totalCalls,
        long failedCalls,
        long p99LatencyMs
    ) {}

    private final OtpCircuitBreaker breaker;
    private final RestartCountSource lifecycle;

    /**
     * Source for restart count — allows wiring to OtpNodeLifecycleManager or a stub.
     */
    @FunctionalInterface
    public interface RestartCountSource {
        int getRestartCount();
    }

    /**
     * Creates a health endpoint.
     *
     * @param breaker    circuit breaker to query
     * @param lifecycle  restart count source (e.g. OtpNodeLifecycleManager::getRestartCount)
     */
    public OtpHealthEndpoint(OtpCircuitBreaker breaker, RestartCountSource lifecycle) {
        if (breaker == null) throw new IllegalArgumentException("breaker must not be null");
        if (lifecycle == null) throw new IllegalArgumentException("lifecycle must not be null");
        this.breaker = breaker;
        this.lifecycle = lifecycle;
    }

    /**
     * Returns a current health snapshot.
     */
    public Snapshot snapshot() {
        OtpCircuitBreakerState state = breaker.getState();
        String status = switch (state) {
            case CLOSED -> "UP";
            case HALF_OPEN -> "RECOVERING";
            case OPEN -> "DOWN";
        };
        return new Snapshot(
            status,
            lifecycle.getRestartCount(),
            breaker.getTotalCalls(),
            breaker.getFailedCalls(),
            breaker.getP99LatencyMs()
        );
    }

    /**
     * Returns a JSON string representation of the current snapshot.
     * Format: {"status":"UP","restarts":0,"totalCalls":42,"failedCalls":2,"p99LatencyMs":15}
     */
    public String toJsonString() {
        Snapshot s = snapshot();
        return "{\"status\":\"" + s.status() + "\","
            + "\"restarts\":" + s.restarts() + ","
            + "\"totalCalls\":" + s.totalCalls() + ","
            + "\"failedCalls\":" + s.failedCalls() + ","
            + "\"p99LatencyMs\":" + s.p99LatencyMs() + "}";
    }
}
