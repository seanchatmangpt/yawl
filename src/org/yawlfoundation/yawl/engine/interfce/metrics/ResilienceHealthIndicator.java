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

package org.yawlfoundation.yawl.engine.interfce.metrics;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Aggregate health indicator for all resilience patterns.
 *
 * <h2>Overview</h2>
 * <p>This indicator aggregates health status from all Resilience4j patterns
 * and calculates an overall system resilience score.</p>
 *
 * <h2>Resilience Patterns Monitored</h2>
 * <ul>
 *   <li><b>Circuit Breakers</b>: Failure protection for external service calls</li>
 *   <li><b>Retries</b>: Automatic retry mechanisms for transient failures</li>
 *   <li><b>Rate Limiters</b>: Protection against overload conditions</li>
 *   <li><b>Bulkheads</b>: Isolation of failure domains with concurrent call limits</li>
 * </ul>
 *
 * <h2>Resilience Score Calculation</h2>
 * <p>The overall resilience score (0-100) is calculated as:</p>
 * <pre>
 *   score = 100 - (circuitBreakerPenalty + retryPenalty + rateLimiterPenalty + bulkheadPenalty)
 * </pre>
 *
 * <h2>Health Status</h2>
 * <table border="1">
 *   <tr><th>Status</th><th>Score Range</th></tr>
 *   <tr><td>UP</td><td>70-100</td></tr>
 *   <tr><td>DEGRADED</td><td>40-69</td></tr>
 *   <tr><td>DOWN</td><td>0-39</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Component
public class ResilienceHealthIndicator implements HealthIndicator {

    private static final Logger _logger = LogManager.getLogger(ResilienceHealthIndicator.class);

    private static final int SCORE_THRESHOLD_UP = 70;
    private static final int SCORE_THRESHOLD_DEGRADED = 40;
    private static final double CIRCUIT_BREAKER_WEIGHT = 30.0;
    private static final double RETRY_WEIGHT = 20.0;
    private static final double RATE_LIMITER_WEIGHT = 25.0;
    private static final double BULKHEAD_WEIGHT = 25.0;
    private static final double HIGH_RETRY_RATE_THRESHOLD = 10.0;

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final BulkheadRegistry bulkheadRegistry;

    public ResilienceHealthIndicator(
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            BulkheadRegistry bulkheadRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.bulkheadRegistry = bulkheadRegistry;
        _logger.info("ResilienceHealthIndicator initialized");
    }

    @Override
    public Health health() {
        try {
            ResilienceHealthDetails details = collectHealthDetails();
            return buildHealthResponse(details);
        } catch (Exception e) {
            _logger.error("Error checking resilience health", e);
            return Health.down()
                    .withDetail("error", e.getClass().getName())
                    .withDetail("message", e.getMessage())
                    .build();
        }
    }

    private ResilienceHealthDetails collectHealthDetails() {
        ResilienceHealthDetails details = new ResilienceHealthDetails();
        collectCircuitBreakerStatus(details);
        collectRetryStatus(details);
        collectRateLimiterStatus(details);
        collectBulkheadStatus(details);
        calculateResilienceScore(details);
        determineOverallStatus(details);
        return details;
    }

    private void collectCircuitBreakerStatus(ResilienceHealthDetails details) {
        int total = 0;
        int openCount = 0;
        int halfOpenCount = 0;

        for (CircuitBreaker cb : circuitBreakerRegistry.allCircuitBreakers()) {
            total++;
            CircuitBreaker.State state = cb.getState();
            CircuitBreaker.Metrics metrics = cb.getMetrics();

            CircuitBreakerStatus status = new CircuitBreakerStatus();
            status.name = cb.getName();
            status.state = state.toString();
            status.failureRate = metrics.getFailureRate();
            status.slowCallRate = metrics.getSlowCallRate();
            status.bufferedCalls = metrics.getNumberOfBufferedCalls();
            status.failedCalls = metrics.getNumberOfFailedCalls();
            status.successfulCalls = metrics.getNumberOfSuccessfulCalls();

            details.circuitBreakers.put(cb.getName(), status);

            if (state == CircuitBreaker.State.OPEN || state == CircuitBreaker.State.FORCED_OPEN) {
                openCount++;
            } else if (state == CircuitBreaker.State.HALF_OPEN) {
                halfOpenCount++;
            }
        }

        details.totalCircuitBreakers = total;
        details.openCircuitBreakers = openCount;
        details.halfOpenCircuitBreakers = halfOpenCount;
    }

    private void collectRetryStatus(ResilienceHealthDetails details) {
        int total = 0;
        int highRetryCount = 0;

        for (Retry retry : retryRegistry.allRetries()) {
            total++;
            Retry.Metrics metrics = retry.getMetrics();

            RetryStatus status = new RetryStatus();
            status.name = retry.getName();
            status.successfulCallsWithoutRetry = metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt();
            status.successfulCallsWithRetry = metrics.getNumberOfSuccessfulCallsWithRetryAttempt();
            status.failedCallsWithoutRetry = metrics.getNumberOfFailedCallsWithoutRetryAttempt();
            status.failedCallsWithRetry = metrics.getNumberOfFailedCallsWithRetryAttempt();

            long totalCalls = status.successfulCallsWithoutRetry + status.successfulCallsWithRetry
                    + status.failedCallsWithoutRetry + status.failedCallsWithRetry;
            long retryCalls = status.successfulCallsWithRetry + status.failedCallsWithRetry;
            status.retryRate = totalCalls > 0 ? (retryCalls * 100.0 / totalCalls) : 0.0;

            details.retries.put(retry.getName(), status);

            if (status.retryRate > HIGH_RETRY_RATE_THRESHOLD) {
                highRetryCount++;
            }
        }

        details.totalRetries = total;
        details.highRetryRateRetries = highRetryCount;
    }

    private void collectRateLimiterStatus(ResilienceHealthDetails details) {
        int total = 0;
        int exhaustedCount = 0;

        for (RateLimiter rateLimiter : rateLimiterRegistry.allRateLimiters()) {
            total++;
            RateLimiter.Metrics metrics = rateLimiter.getMetrics();

            RateLimiterStatus status = new RateLimiterStatus();
            status.name = rateLimiter.getName();
            status.availablePermissions = metrics.getAvailablePermissions();
            status.waitingThreads = metrics.getNumberOfWaitingThreads();
            status.exhausted = metrics.getAvailablePermissions() == 0;

            details.rateLimiters.put(rateLimiter.getName(), status);

            if (status.exhausted) {
                exhaustedCount++;
            }
        }

        details.totalRateLimiters = total;
        details.exhaustedRateLimiters = exhaustedCount;
    }

    private void collectBulkheadStatus(ResilienceHealthDetails details) {
        int total = 0;
        int fullCount = 0;

        for (Bulkhead bulkhead : bulkheadRegistry.allBulkheads()) {
            total++;
            Bulkhead.Metrics metrics = bulkhead.getMetrics();

            BulkheadStatus status = new BulkheadStatus();
            status.name = bulkhead.getName();
            status.availableCalls = metrics.getAvailableConcurrentCalls();
            status.maxAllowedCalls = metrics.getMaxAllowedConcurrentCalls();
            status.utilizationRate = (double) (status.maxAllowedCalls - status.availableCalls) / status.maxAllowedCalls * 100;
            status.full = metrics.getAvailableConcurrentCalls() == 0;

            details.bulkheads.put(bulkhead.getName(), status);

            if (status.full) {
                fullCount++;
            }
        }

        details.totalBulkheads = total;
        details.fullBulkheads = fullCount;
    }

    private void calculateResilienceScore(ResilienceHealthDetails details) {
        double score = 100.0;
        StringBuilder penaltyBreakdown = new StringBuilder();

        if (details.totalCircuitBreakers > 0) {
            double cbPenalty = CIRCUIT_BREAKER_WEIGHT * ((double) details.openCircuitBreakers / details.totalCircuitBreakers);
            score -= cbPenalty;
            penaltyBreakdown.append(String.format("CB: -%.1f", cbPenalty));
        }

        if (details.totalRetries > 0) {
            double retryPenalty = RETRY_WEIGHT * ((double) details.highRetryRateRetries / details.totalRetries);
            score -= retryPenalty;
            if (penaltyBreakdown.length() > 0) penaltyBreakdown.append(", ");
            penaltyBreakdown.append(String.format("Retry: -%.1f", retryPenalty));
        }

        if (details.totalRateLimiters > 0) {
            double rlPenalty = RATE_LIMITER_WEIGHT * ((double) details.exhaustedRateLimiters / details.totalRateLimiters);
            score -= rlPenalty;
            if (penaltyBreakdown.length() > 0) penaltyBreakdown.append(", ");
            penaltyBreakdown.append(String.format("RL: -%.1f", rlPenalty));
        }

        if (details.totalBulkheads > 0) {
            double bhPenalty = BULKHEAD_WEIGHT * ((double) details.fullBulkheads / details.totalBulkheads);
            score -= bhPenalty;
            if (penaltyBreakdown.length() > 0) penaltyBreakdown.append(", ");
            penaltyBreakdown.append(String.format("BH: -%.1f", bhPenalty));
        }

        details.resilienceScore = Math.max(0, Math.min(100, (int) Math.round(score)));
        details.penaltyBreakdown = penaltyBreakdown.toString();
    }

    private void determineOverallStatus(ResilienceHealthDetails details) {
        if (details.resilienceScore >= SCORE_THRESHOLD_UP) {
            details.status = "UP";
        } else if (details.resilienceScore >= SCORE_THRESHOLD_DEGRADED) {
            details.status = "DEGRADED";
        } else {
            details.status = "DOWN";
        }

        StringBuilder reason = new StringBuilder();
        if (details.openCircuitBreakers > 0) {
            reason.append(details.openCircuitBreakers).append(" open circuit breaker(s); ");
        }
        if (details.exhaustedRateLimiters > 0) {
            reason.append(details.exhaustedRateLimiters).append(" exhausted rate limiter(s); ");
        }
        if (details.fullBulkheads > 0) {
            reason.append(details.fullBulkheads).append(" full bulkhead(s); ");
        }
        details.reason = reason.toString().trim();
    }

    private Health buildHealthResponse(ResilienceHealthDetails details) {
        Health.Builder builder;

        if ("DOWN".equals(details.status)) {
            builder = Health.down();
        } else if ("DEGRADED".equals(details.status)) {
            builder = Health.status("DEGRADED");
        } else {
            builder = Health.up();
        }

        return builder
                .withDetail("status", details.status)
                .withDetail("resilienceScore", details.resilienceScore)
                .withDetail("reason", details.reason)
                .withDetail("penaltyBreakdown", details.penaltyBreakdown)
                .withDetail("circuitBreakers.total", details.totalCircuitBreakers)
                .withDetail("circuitBreakers.open", details.openCircuitBreakers)
                .withDetail("circuitBreakers.halfOpen", details.halfOpenCircuitBreakers)
                .withDetail("retries.total", details.totalRetries)
                .withDetail("retries.highRetryRate", details.highRetryRateRetries)
                .withDetail("rateLimiters.total", details.totalRateLimiters)
                .withDetail("rateLimiters.exhausted", details.exhaustedRateLimiters)
                .withDetail("bulkheads.total", details.totalBulkheads)
                .withDetail("bulkheads.full", details.fullBulkheads)
                .build();
    }

    public int getResilienceScore() {
        ResilienceHealthDetails details = new ResilienceHealthDetails();
        collectCircuitBreakerStatus(details);
        collectRetryStatus(details);
        collectRateLimiterStatus(details);
        collectBulkheadStatus(details);
        calculateResilienceScore(details);
        return details.resilienceScore;
    }

    public Map<String, CircuitBreakerStatus> getCircuitBreakerStatus() {
        Map<String, CircuitBreakerStatus> status = new HashMap<>();
        for (CircuitBreaker cb : circuitBreakerRegistry.getAllCircuitBreakers()) {
            CircuitBreaker.Metrics metrics = cb.getMetrics();
            CircuitBreakerStatus cbStatus = new CircuitBreakerStatus();
            cbStatus.name = cb.getName();
            cbStatus.state = cb.getState().toString();
            cbStatus.failureRate = metrics.getFailureRate();
            cbStatus.slowCallRate = metrics.getSlowCallRate();
            cbStatus.bufferedCalls = metrics.getNumberOfBufferedCalls();
            cbStatus.failedCalls = metrics.getNumberOfFailedCalls();
            cbStatus.successfulCalls = metrics.getNumberOfSuccessfulCalls();
            status.put(cb.getName(), cbStatus);
        }
        return status;
    }

    public Map<String, BulkheadStatus> getBulkheadStatus() {
        Map<String, BulkheadStatus> status = new HashMap<>();
        for (Bulkhead bh : bulkheadRegistry.getAllBulkheads()) {
            Bulkhead.Metrics metrics = bh.getMetrics();
            BulkheadStatus bhStatus = new BulkheadStatus();
            bhStatus.name = bh.getName();
            bhStatus.availableCalls = metrics.getAvailableConcurrentCalls();
            bhStatus.maxAllowedCalls = metrics.getMaxAllowedConcurrentCalls();
            bhStatus.utilizationRate = (double) (bhStatus.maxAllowedCalls - bhStatus.availableCalls)
                    / bhStatus.maxAllowedCalls * 100;
            bhStatus.full = bhStatus.availableCalls == 0;
            status.put(bh.getName(), bhStatus);
        }
        return status;
    }

    private static class ResilienceHealthDetails {
        String status = "UP";
        String reason = "";
        int resilienceScore = 100;
        String penaltyBreakdown = "";

        int totalCircuitBreakers = 0;
        int openCircuitBreakers = 0;
        int halfOpenCircuitBreakers = 0;

        int totalRetries = 0;
        int highRetryRateRetries = 0;

        int totalRateLimiters = 0;
        int exhaustedRateLimiters = 0;

        int totalBulkheads = 0;
        int fullBulkheads = 0;

        Map<String, CircuitBreakerStatus> circuitBreakers = new HashMap<>();
        Map<String, RetryStatus> retries = new HashMap<>();
        Map<String, RateLimiterStatus> rateLimiters = new HashMap<>();
        Map<String, BulkheadStatus> bulkheads = new HashMap<>();
    }

    public static class CircuitBreakerStatus {
        private String name;
        private String state;
        private float failureRate;
        private float slowCallRate;
        private int bufferedCalls;
        private int failedCalls;
        private int successfulCalls;

        public String getName() {
            return name;
        }

        public String getState() {
            return state;
        }

        public float getFailureRate() {
            return failureRate;
        }

        public float getSlowCallRate() {
            return slowCallRate;
        }

        public int getBufferedCalls() {
            return bufferedCalls;
        }

        public int getFailedCalls() {
            return failedCalls;
        }

        public int getSuccessfulCalls() {
            return successfulCalls;
        }

        public boolean isOpen() {
            return "OPEN".equals(state) || "FORCED_OPEN".equals(state);
        }
    }

    public static class RetryStatus {
        private String name;
        private long successfulCallsWithoutRetry;
        private long successfulCallsWithRetry;
        private long failedCallsWithoutRetry;
        private long failedCallsWithRetry;
        private double retryRate;

        public String getName() {
            return name;
        }

        public long getSuccessfulCallsWithoutRetry() {
            return successfulCallsWithoutRetry;
        }

        public long getSuccessfulCallsWithRetry() {
            return successfulCallsWithRetry;
        }

        public long getFailedCallsWithoutRetry() {
            return failedCallsWithoutRetry;
        }

        public long getFailedCallsWithRetry() {
            return failedCallsWithRetry;
        }

        public double getRetryRate() {
            return retryRate;
        }
    }

    public static class RateLimiterStatus {
        private String name;
        private int availablePermissions;
        private int waitingThreads;
        private boolean exhausted;

        public String getName() {
            return name;
        }

        public int getAvailablePermissions() {
            return availablePermissions;
        }

        public int getWaitingThreads() {
            return waitingThreads;
        }

        public boolean isExhausted() {
            return exhausted;
        }
    }

    public static class BulkheadStatus {
        private String name;
        private int availableCalls;
        private int maxAllowedCalls;
        private double utilizationRate;
        private boolean full;

        public String getName() {
            return name;
        }

        public int getAvailableCalls() {
            return availableCalls;
        }

        public int getMaxAllowedCalls() {
            return maxAllowedCalls;
        }

        public double getUtilizationRate() {
            return utilizationRate;
        }

        public boolean isFull() {
            return full;
        }
    }
}
