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

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResilienceHealthIndicator.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("ResilienceHealthIndicator Tests")
class ResilienceHealthIndicatorTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private RetryRegistry retryRegistry;
    private RateLimiterRegistry rateLimiterRegistry;
    private BulkheadRegistry bulkheadRegistry;
    private ResilienceHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        retryRegistry = RetryRegistry.ofDefaults();
        rateLimiterRegistry = RateLimiterRegistry.ofDefaults();
        bulkheadRegistry = BulkheadRegistry.ofDefaults();
        healthIndicator = new ResilienceHealthIndicator(
                circuitBreakerRegistry,
                retryRegistry,
                rateLimiterRegistry,
                bulkheadRegistry
        );
    }

    @Nested
    @DisplayName("Health Status Tests")
    class HealthStatusTests {

        @Test
        @DisplayName("health returns UP when no resilience patterns configured")
        void health_returnsUpWhenNoPatternsConfigured() {
            Health health = healthIndicator.health();

            assertEquals(Status.UP, health.getStatus(), "Should be UP when no patterns configured");
        }

        @Test
        @DisplayName("health returns resilience score of 100 when no issues")
        void health_returnsFullScoreWhenNoIssues() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("test-cb");
            Retry retry = retryRegistry.retry("test-retry");

            Health health = healthIndicator.health();

            assertEquals(100, health.getDetails().get("resilienceScore"), "Score should be 100");
        }
    }

    @Nested
    @DisplayName("Resilience Score Calculation Tests")
    class ResilienceScoreTests {

        @Test
        @DisplayName("getResilienceScore returns 100 when all healthy")
        void getResilienceScore_returns100WhenHealthy() {
            circuitBreakerRegistry.circuitBreaker("healthy-cb");
            retryRegistry.retry("healthy-retry");
            bulkheadRegistry.bulkhead("healthy-bh");
            rateLimiterRegistry.rateLimiter("healthy-rl");

            int score = healthIndicator.getResilienceScore();

            assertEquals(100, score, "Score should be 100 when all patterns healthy");
        }

        @Test
        @DisplayName("score decreases when circuit breaker is OPEN")
        void scoreDecreasesWhenCircuitBreakerOpen() {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(
                    "open-cb",
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(1.0f)
                            .slidingWindowSize(1)
                            .build()
            );
            cb.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("Test"));

            int score = healthIndicator.getResilienceScore();

            assertTrue(score < 100, "Score should decrease when circuit breaker is open");
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Status Tests")
    class CircuitBreakerStatusTests {

        @Test
        @DisplayName("health includes circuit breaker count")
        void health_includesCircuitBreakerCount() {
            circuitBreakerRegistry.circuitBreaker("cb1");
            circuitBreakerRegistry.circuitBreaker("cb2");

            Health health = healthIndicator.health();

            assertEquals(2, health.getDetails().get("circuitBreakers.total"), "Should count 2 circuit breakers");
        }

        @Test
        @DisplayName("getCircuitBreakerStatus returns all circuit breakers")
        void getCircuitBreakerStatus_returnsAll() {
            circuitBreakerRegistry.circuitBreaker("cb1");
            circuitBreakerRegistry.circuitBreaker("cb2");

            Map<String, ResilienceHealthIndicator.CircuitBreakerStatus> status =
                    healthIndicator.getCircuitBreakerStatus();

            assertEquals(2, status.size(), "Should return 2 circuit breakers");
            assertTrue(status.containsKey("cb1"), "Should contain cb1");
            assertTrue(status.containsKey("cb2"), "Should contain cb2");
        }

        @Test
        @DisplayName("getCircuitBreakerStatus includes state information")
        void getCircuitBreakerStatus_includesState() {
            circuitBreakerRegistry.circuitBreaker("test-cb");

            Map<String, ResilienceHealthIndicator.CircuitBreakerStatus> status =
                    healthIndicator.getCircuitBreakerStatus();

            ResilienceHealthIndicator.CircuitBreakerStatus cbStatus = status.get("test-cb");
            assertEquals("CLOSED", cbStatus.getState(), "Should show CLOSED state");
            assertFalse(cbStatus.isOpen(), "Should not be open");
        }
    }

    @Nested
    @DisplayName("Bulkhead Status Tests")
    class BulkheadStatusTests {

        @Test
        @DisplayName("health includes bulkhead count")
        void health_includesBulkheadCount() {
            bulkheadRegistry.bulkhead("bh1");
            bulkheadRegistry.bulkhead("bh2");

            Health health = healthIndicator.health();

            assertEquals(2, health.getDetails().get("bulkheads.total"), "Should count 2 bulkheads");
        }

        @Test
        @DisplayName("getBulkheadStatus returns all bulkheads")
        void getBulkheadStatus_returnsAll() {
            bulkheadRegistry.bulkhead("bh1");
            bulkheadRegistry.bulkhead("bh2");

            Map<String, ResilienceHealthIndicator.BulkheadStatus> status =
                    healthIndicator.getBulkheadStatus();

            assertEquals(2, status.size(), "Should return 2 bulkheads");
        }

        @Test
        @DisplayName("getBulkheadStatus includes utilization")
        void getBulkheadStatus_includesUtilization() {
            bulkheadRegistry.bulkhead("test-bh", BulkheadConfig.custom()
                    .maxConcurrentCalls(10)
                    .build());

            Map<String, ResilienceHealthIndicator.BulkheadStatus> status =
                    healthIndicator.getBulkheadStatus();

            ResilienceHealthIndicator.BulkheadStatus bhStatus = status.get("test-bh");
            assertEquals(10, bhStatus.getMaxAllowedCalls(), "Should show max calls");
            assertEquals(10, bhStatus.getAvailableCalls(), "Should show available calls");
            assertEquals(0.0, bhStatus.getUtilizationRate(), 0.01, "Utilization should be 0%");
        }
    }

    @Nested
    @DisplayName("Retry Status Tests")
    class RetryStatusTests {

        @Test
        @DisplayName("health includes retry count")
        void health_includesRetryCount() {
            retryRegistry.retry("retry1");
            retryRegistry.retry("retry2");

            Health health = healthIndicator.health();

            assertEquals(2, health.getDetails().get("retries.total"), "Should count 2 retries");
        }
    }

    @Nested
    @DisplayName("Rate Limiter Status Tests")
    class RateLimiterStatusTests {

        @Test
        @DisplayName("health includes rate limiter count")
        void health_includesRateLimiterCount() {
            rateLimiterRegistry.rateLimiter("rl1");
            rateLimiterRegistry.rateLimiter("rl2");

            Health health = healthIndicator.health();

            assertEquals(2, health.getDetails().get("rateLimiters.total"), "Should count 2 rate limiters");
        }
    }

    @Nested
    @DisplayName("Health Response Details Tests")
    class HealthResponseDetailsTests {

        @Test
        @DisplayName("health response contains all expected details")
        void healthResponse_containsAllExpectedDetails() {
            Health health = healthIndicator.health();

            assertTrue(health.getDetails().containsKey("status"), "Should contain status");
            assertTrue(health.getDetails().containsKey("resilienceScore"), "Should contain resilience score");
            assertTrue(health.getDetails().containsKey("circuitBreakers.total"), "Should contain CB total");
            assertTrue(health.getDetails().containsKey("retries.total"), "Should contain retry total");
            assertTrue(health.getDetails().containsKey("rateLimiters.total"), "Should contain RL total");
            assertTrue(health.getDetails().containsKey("bulkheads.total"), "Should contain BH total");
        }

        @Test
        @DisplayName("health response includes penalty breakdown")
        void healthResponse_includesPenaltyBreakdown() {
            Health health = healthIndicator.health();

            assertTrue(health.getDetails().containsKey("penaltyBreakdown"), "Should contain penalty breakdown");
        }
    }

    @Nested
    @DisplayName("Degraded Status Tests")
    class DegradedStatusTests {

        @Test
        @DisplayName("status is UP when score is above threshold")
        void statusIsUpWhenScoreHigh() {
            for (int i = 0; i < 5; i++) {
                circuitBreakerRegistry.circuitBreaker("cb" + i);
            }

            CircuitBreaker openCb = circuitBreakerRegistry.circuitBreaker(
                    "open-cb",
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(1.0f)
                            .slidingWindowSize(1)
                            .build()
            );
            openCb.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("Test"));

            Health health = healthIndicator.health();

            assertTrue((int) health.getDetails().get("resilienceScore") >= 70,
                    "Score should be above DEGRADED threshold with partial penalty");
        }
    }

    @Nested
    @DisplayName("Inner Classes Tests")
    class InnerClassesTests {

        @Test
        @DisplayName("CircuitBreakerStatus has all getters")
        void circuitBreakerStatus_hasAllGetters() {
            circuitBreakerRegistry.circuitBreaker("test-cb");
            Map<String, ResilienceHealthIndicator.CircuitBreakerStatus> status =
                    healthIndicator.getCircuitBreakerStatus();

            ResilienceHealthIndicator.CircuitBreakerStatus cb = status.get("test-cb");

            assertNotNull(cb.getName(), "Should have name");
            assertNotNull(cb.getState(), "Should have state");
            assertTrue(cb.getFailureRate() >= 0, "Should have failure rate");
            assertTrue(cb.getSlowCallRate() >= 0, "Should have slow call rate");
        }

        @Test
        @DisplayName("BulkheadStatus has all getters")
        void bulkheadStatus_hasAllGetters() {
            bulkheadRegistry.bulkhead("test-bh");
            Map<String, ResilienceHealthIndicator.BulkheadStatus> status =
                    healthIndicator.getBulkheadStatus();

            ResilienceHealthIndicator.BulkheadStatus bh = status.get("test-bh");

            assertNotNull(bh.getName(), "Should have name");
            assertTrue(bh.getMaxAllowedCalls() > 0, "Should have max calls");
            assertTrue(bh.getAvailableCalls() >= 0, "Should have available calls");
            assertTrue(bh.getUtilizationRate() >= 0, "Should have utilization rate");
        }
    }
}
