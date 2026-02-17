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

package org.yawlfoundation.yawl.integration.gateway;

import java.util.Objects;

/**
 * Configuration model for API gateway circuit breaker settings.
 *
 * <p>Gateway-level circuit breakers protect upstream YAWL engine instances from
 * receiving requests when they are unhealthy. They complement the engine-side
 * {@link org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker}
 * which protects YAWL from failing downstream services.
 *
 * <h2>Two-Level Circuit Breaker Architecture</h2>
 * <pre>
 *   Client
 *     |
 *     v
 *  [API Gateway]
 *     |-- Gateway Circuit Breaker (this class)
 *     |   Opens when YAWL engine returns 5xx or times out
 *     |   Returns 503 to clients while open
 *     |
 *     v
 *  [YAWL Engine]
 *     |-- Engine Circuit Breaker (CircuitBreaker.java)
 *         Opens when downstream services (LDAP, external APIs) fail
 *         Returns 502/503 to gateway
 * </pre>
 *
 * <h2>Kong Configuration Example</h2>
 * <pre>
 * # kong.yml snippet
 * plugins:
 *   - name: request-termination
 *     config:
 *       status_code: 503
 *       message: "YAWL engine circuit breaker open"
 *
 * # Using Kong's upstream health checks as the circuit breaker mechanism:
 * upstreams:
 *   - name: yawl-engine
 *     algorithm: round-robin
 *     healthchecks:
 *       active:
 *         healthy:
 *           interval: 5           # seconds between health checks
 *           successes: 2          # successes to mark target healthy
 *         unhealthy:
 *           interval: 5
 *           http_failures: 3      # failures to trip the breaker
 *           tcp_failures: 3
 *           timeouts: 3
 *       passive:
 *         healthy:
 *           successes: 5
 *         unhealthy:
 *           http_failures: 5      # passively detected failures to mark unhealthy
 *           http_statuses: [500, 502, 503, 504]
 *           timeouts: 5
 * </pre>
 *
 * <h2>Traefik CircuitBreaker Middleware Example</h2>
 * <pre>
 * http:
 *   middlewares:
 *     yawl-circuit-breaker:
 *       circuitBreaker:
 *         expression: "NetworkErrorRatio() > 0.30 || ResponseCodeRatio(500, 600, 0, 600) > 0.25"
 *         checkPeriod: 10s
 *         fallbackDuration: 30s
 *         recoveryDuration: 10s
 * </pre>
 *
 * <h2>AWS API Gateway Circuit Breaker (via Lambda Authorizer + CloudWatch)</h2>
 * <pre>
 * # AWS does not have native circuit breakers in API Gateway.
 * # Pattern: use a Lambda Authorizer that checks a DynamoDB or ElastiCache flag.
 * # When CloudWatch alarm fires on 5xx rate > threshold, a Lambda sets the flag.
 * # The authorizer returns 403 (circuit open) until the flag is cleared.
 *
 * # Recommended: use AWS App Mesh / ECS service connect circuit breaking instead,
 * # or route through AWS CloudFront with custom error responses.
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class GatewayCircuitBreakerConfig {

    /**
     * Expression language used to evaluate the circuit breaker condition.
     */
    public enum ExpressionType {
        /** Traefik circuit breaker expression language. */
        TRAEFIK,
        /** Kong health check threshold configuration. */
        KONG_HEALTH_CHECK,
        /** Custom expression for AWS or other gateways. */
        CUSTOM
    }

    private final String         name;
    private final ExpressionType expressionType;
    private final String         openExpression;
    private final int            checkPeriodSeconds;
    private final int            fallbackDurationSeconds;
    private final int            recoveryDurationSeconds;
    private final int            httpFailureThreshold;
    private final int            errorRatePercent;

    private GatewayCircuitBreakerConfig(Builder builder) {
        this.name                    = Objects.requireNonNull(builder.name, "name");
        this.expressionType          = Objects.requireNonNull(builder.expressionType, "expressionType");
        this.openExpression          = builder.openExpression;
        this.checkPeriodSeconds      = builder.checkPeriodSeconds;
        this.fallbackDurationSeconds = builder.fallbackDurationSeconds;
        this.recoveryDurationSeconds = builder.recoveryDurationSeconds;
        this.httpFailureThreshold    = builder.httpFailureThreshold;
        this.errorRatePercent        = builder.errorRatePercent;
    }

    // -------------------------------------------------------------------------
    // Pre-built configurations
    // -------------------------------------------------------------------------

    /**
     * Returns the standard YAWL Traefik circuit breaker configuration.
     * Opens when network error ratio exceeds 30% or 5xx response ratio exceeds 25%.
     *
     * @return Traefik circuit breaker config for YAWL
     */
    public static GatewayCircuitBreakerConfig traefikDefault() {
        return new Builder("yawl-circuit-breaker")
                .expressionType(ExpressionType.TRAEFIK)
                .openExpression(
                    "NetworkErrorRatio() > 0.30 || ResponseCodeRatio(500, 600, 0, 600) > 0.25")
                .checkPeriodSeconds(10)
                .fallbackDurationSeconds(30)
                .recoveryDurationSeconds(10)
                .build();
    }

    /**
     * Returns the standard YAWL Kong health-check circuit breaker configuration.
     * Marks target unhealthy after 5 consecutive HTTP failures or timeouts.
     *
     * @return Kong circuit breaker config for YAWL
     */
    public static GatewayCircuitBreakerConfig kongDefault() {
        return new Builder("yawl-upstream")
                .expressionType(ExpressionType.KONG_HEALTH_CHECK)
                .httpFailureThreshold(5)
                .errorRatePercent(25)
                .checkPeriodSeconds(5)
                .recoveryDurationSeconds(10)
                .build();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Circuit breaker name for gateway configuration. */
    public String getName()                    { return name; }

    /** Expression type (Traefik, Kong, or custom). */
    public ExpressionType getExpressionType()  { return expressionType; }

    /** Open condition expression (Traefik expression language). */
    public String getOpenExpression()          { return openExpression; }

    /** How often the circuit breaker evaluates the open condition (seconds). */
    public int getCheckPeriodSeconds()         { return checkPeriodSeconds; }

    /** How long the circuit stays open before entering half-open state (seconds). */
    public int getFallbackDurationSeconds()    { return fallbackDurationSeconds; }

    /** How long the circuit stays in half-open before closing on success (seconds). */
    public int getRecoveryDurationSeconds()    { return recoveryDurationSeconds; }

    /** Number of consecutive HTTP failures before opening (Kong). */
    public int getHttpFailureThreshold()       { return httpFailureThreshold; }

    /** Error rate percentage threshold to open the circuit (0-100). */
    public int getErrorRatePercent()           { return errorRatePercent; }

    /**
     * Renders this configuration as a Traefik YAML middleware snippet.
     *
     * @return Traefik YAML string
     * @throws UnsupportedOperationException if expressionType is not TRAEFIK
     */
    public String toTraefikYaml() {
        if (expressionType != ExpressionType.TRAEFIK) {
            throw new UnsupportedOperationException(
                    "toTraefikYaml() requires expressionType=TRAEFIK, got: " + expressionType);
        }
        return "http:\n"
             + "  middlewares:\n"
             + "    " + name + ":\n"
             + "      circuitBreaker:\n"
             + "        expression: \"" + openExpression + "\"\n"
             + "        checkPeriod: " + checkPeriodSeconds + "s\n"
             + "        fallbackDuration: " + fallbackDurationSeconds + "s\n"
             + "        recoveryDuration: " + recoveryDurationSeconds + "s\n";
    }

    /**
     * Renders this configuration as a Kong YAML upstream health check snippet.
     *
     * @return Kong YAML string
     * @throws UnsupportedOperationException if expressionType is not KONG_HEALTH_CHECK
     */
    public String toKongYaml() {
        if (expressionType != ExpressionType.KONG_HEALTH_CHECK) {
            throw new UnsupportedOperationException(
                    "toKongYaml() requires expressionType=KONG_HEALTH_CHECK, got: " + expressionType);
        }
        return "upstreams:\n"
             + "  - name: " + name + "\n"
             + "    algorithm: round-robin\n"
             + "    healthchecks:\n"
             + "      active:\n"
             + "        healthy:\n"
             + "          interval: " + checkPeriodSeconds + "\n"
             + "          successes: 2\n"
             + "        unhealthy:\n"
             + "          interval: " + checkPeriodSeconds + "\n"
             + "          http_failures: " + httpFailureThreshold + "\n"
             + "          tcp_failures: " + httpFailureThreshold + "\n"
             + "          timeouts: " + httpFailureThreshold + "\n"
             + "      passive:\n"
             + "        unhealthy:\n"
             + "          http_failures: " + httpFailureThreshold + "\n"
             + "          http_statuses: [500, 502, 503, 504]\n"
             + "          timeouts: " + httpFailureThreshold + "\n";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Builder for {@link GatewayCircuitBreakerConfig}. */
    public static final class Builder {
        private final String name;
        private ExpressionType expressionType    = ExpressionType.CUSTOM;
        private String         openExpression;
        private int            checkPeriodSeconds      = 10;
        private int            fallbackDurationSeconds = 30;
        private int            recoveryDurationSeconds = 10;
        private int            httpFailureThreshold    = 5;
        private int            errorRatePercent        = 25;

        /** Start building with the circuit breaker name. */
        public Builder(String name) {
            this.name = name;
        }

        /** Set expression type. */
        public Builder expressionType(ExpressionType type) {
            this.expressionType = type; return this;
        }

        /** Set open condition expression. */
        public Builder openExpression(String expr) {
            this.openExpression = expr; return this;
        }

        /** Set check period in seconds. */
        public Builder checkPeriodSeconds(int seconds) {
            this.checkPeriodSeconds = seconds; return this;
        }

        /** Set fallback (open) duration in seconds. */
        public Builder fallbackDurationSeconds(int seconds) {
            this.fallbackDurationSeconds = seconds; return this;
        }

        /** Set recovery (half-open) duration in seconds. */
        public Builder recoveryDurationSeconds(int seconds) {
            this.recoveryDurationSeconds = seconds; return this;
        }

        /** Set HTTP failure threshold for Kong. */
        public Builder httpFailureThreshold(int threshold) {
            this.httpFailureThreshold = threshold; return this;
        }

        /** Set error rate percentage threshold. */
        public Builder errorRatePercent(int percent) {
            this.errorRatePercent = percent; return this;
        }

        /** Build the configuration. */
        public GatewayCircuitBreakerConfig build() {
            return new GatewayCircuitBreakerConfig(this);
        }
    }
}
