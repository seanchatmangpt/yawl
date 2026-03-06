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
 * Immutable specification for OTP-inspired pipeline supervision.
 *
 * <p>Controls how failed pipeline stages are restarted. Mirrors Erlang/OTP
 * supervisor configuration with Java-native types.
 *
 * <p>Usage via fluent builder:
 * <pre>{@code
 * SupervisionSpec spec = SupervisionSpec.builder()
 *     .strategy(RestartStrategy.ONE_FOR_ONE)
 *     .maxRestarts(3)
 *     .window(Duration.ofMinutes(5))
 *     .build();
 * }</pre>
 *
 * @param strategy             restart strategy (default: ONE_FOR_ONE)
 * @param maxRestarts          maximum restarts within window before permanent failure (default: 3)
 * @param window               time window for counting restarts (default: 5 minutes)
 * @param healthCheckInterval  interval between health probes (default: 5 seconds)
 */
public record SupervisionSpec(
        RestartStrategy strategy,
        int maxRestarts,
        Duration window,
        Duration healthCheckInterval
) {

    /** Default supervision spec: ONE_FOR_ONE, 3 restarts in 5 minutes, 5s health checks. */
    public static final SupervisionSpec DEFAULT = new SupervisionSpec(
            RestartStrategy.ONE_FOR_ONE, 3, Duration.ofMinutes(5), Duration.ofSeconds(5));

    public SupervisionSpec {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Objects.requireNonNull(window, "window must not be null");
        Objects.requireNonNull(healthCheckInterval, "healthCheckInterval must not be null");
        if (maxRestarts < 0) throw new IllegalArgumentException("maxRestarts must be >= 0");
        if (window.isNegative() || window.isZero())
            throw new IllegalArgumentException("window must be positive");
        if (healthCheckInterval.isNegative() || healthCheckInterval.isZero())
            throw new IllegalArgumentException("healthCheckInterval must be positive");
    }

    /** Returns a new builder with default values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link SupervisionSpec}. */
    public static final class Builder {
        private RestartStrategy strategy = RestartStrategy.ONE_FOR_ONE;
        private int maxRestarts = 3;
        private Duration window = Duration.ofMinutes(5);
        private Duration healthCheckInterval = Duration.ofSeconds(5);

        Builder() {}

        public Builder strategy(RestartStrategy strategy) {
            this.strategy = Objects.requireNonNull(strategy);
            return this;
        }

        public Builder maxRestarts(int maxRestarts) {
            this.maxRestarts = maxRestarts;
            return this;
        }

        public Builder window(Duration window) {
            this.window = Objects.requireNonNull(window);
            return this;
        }

        public Builder healthCheckInterval(Duration healthCheckInterval) {
            this.healthCheckInterval = Objects.requireNonNull(healthCheckInterval);
            return this;
        }

        public SupervisionSpec build() {
            return new SupervisionSpec(strategy, maxRestarts, window, healthCheckInterval);
        }
    }
}
