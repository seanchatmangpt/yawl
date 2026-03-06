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
package org.yawlfoundation.yawl.erlang.fluent;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable specification for per-stage circuit breaker protection.
 *
 * <p>When a stage fails {@code failureThreshold} consecutive times, the circuit
 * opens and subsequent executions fail immediately with {@link CircuitOpenException}
 * until {@code resetTimeout} elapses.</p>
 *
 * <p>Usage via fluent builder:
 * <pre>{@code
 * CircuitBreakerSpec spec = CircuitBreakerSpec.builder()
 *     .failureThreshold(5)
 *     .resetTimeout(Duration.ofSeconds(60))
 *     .build();
 * }</pre>
 *
 * @param failureThreshold consecutive failures before circuit opens (default: 3)
 * @param resetTimeout     duration before half-open trial (default: 30 seconds)
 */
public record CircuitBreakerSpec(
        int failureThreshold,
        Duration resetTimeout
) {

    /** Default spec: open after 3 failures, reset after 30 seconds. */
    public static final CircuitBreakerSpec DEFAULT = new CircuitBreakerSpec(
            3, Duration.ofSeconds(30));

    public CircuitBreakerSpec {
        Objects.requireNonNull(resetTimeout, "resetTimeout must not be null");
        if (failureThreshold < 1)
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        if (resetTimeout.isNegative() || resetTimeout.isZero())
            throw new IllegalArgumentException("resetTimeout must be positive");
    }

    /** Returns a new builder with default values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link CircuitBreakerSpec}. */
    public static final class Builder {
        private int failureThreshold = 3;
        private Duration resetTimeout = Duration.ofSeconds(30);

        Builder() {}

        public Builder failureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
            return this;
        }

        public Builder resetTimeout(Duration resetTimeout) {
            this.resetTimeout = Objects.requireNonNull(resetTimeout);
            return this;
        }

        public CircuitBreakerSpec build() {
            return new CircuitBreakerSpec(failureThreshold, resetTimeout);
        }
    }
}
