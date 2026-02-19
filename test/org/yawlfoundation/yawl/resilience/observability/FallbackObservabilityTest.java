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

package org.yawlfoundation.yawl.resilience.observability;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FallbackObservability.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
class FallbackObservabilityTest {

    private SimpleMeterRegistry meterRegistry;
    private TestAndonAlertService andonService;
    private FallbackObservability fallbackObs;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonService = new TestAndonAlertService();
        fallbackObs = FallbackObservability.initialize(
            meterRegistry,
            null,  // No OTEL for unit tests
            andonService,
            Duration.ofMinutes(5)  // 5 minute staleness threshold
        );
    }

    @Test
    @DisplayName("Should record fallback invocation with fresh data")
    void shouldRecordFreshFallback() {
        // Data is 1 minute old (fresh)
        Instant dataTimestamp = Instant.now().minus(Duration.ofMinutes(1));

        FallbackObservability.FallbackResult result = fallbackObs.recordFallback(
            "test-component",
            "test-operation",
            FallbackObservability.FallbackReason.SERVICE_ERROR,
            FallbackObservability.FallbackSource.LOCAL_CACHE,
            () -> "fallback-value",
            dataTimestamp,
            new RuntimeException("primary failed")
        );

        assertTrue(result.usedFallback());
        assertFalse(result.isStale());
        assertEquals("fallback-value", result.getValue());
        assertNull(result.getAndonAlertId());  // No Andon for fresh data
        assertNotNull(result.getPrimaryError());
    }

    @Test
    @DisplayName("Should fire Andon P1 alert for stale data")
    void shouldFireAndonForStaleData() {
        // Data is 10 minutes old (stale, threshold is 5 minutes)
        Instant dataTimestamp = Instant.now().minus(Duration.ofMinutes(10));

        FallbackObservability.FallbackResult result = fallbackObs.recordFallback(
            "test-component",
            "test-operation",
            FallbackObservability.FallbackReason.CIRCUIT_OPEN,
            FallbackObservability.FallbackSource.STALE_DATA,
            () -> "stale-value",
            dataTimestamp,
            null
        );

        assertTrue(result.usedFallback());
        assertTrue(result.isStale());
        assertEquals("stale-value", result.getValue());
        assertNotNull(result.getAndonAlertId());  // Andon alert should be fired
        assertEquals("P1", andonService.getLastSeverity());
        assertEquals("test-component", andonService.getLastComponent());
    }

    @Test
    @DisplayName("Should detect stale data correctly")
    void shouldDetectStaleData() {
        // Fresh data
        Instant freshTimestamp = Instant.now().minus(Duration.ofMinutes(1));
        assertFalse(fallbackObs.isStale(freshTimestamp));

        // Exactly at threshold (5 minutes)
        Instant atThreshold = Instant.now().minus(Duration.ofMinutes(5));
        assertFalse(fallbackObs.isStale(atThreshold));

        // Just beyond threshold
        Instant beyondThreshold = Instant.now().minus(Duration.ofMinutes(6));
        assertTrue(fallbackObs.isStale(beyondThreshold));

        // Very old data
        Instant veryOld = Instant.now().minus(Duration.ofHours(1));
        assertTrue(fallbackObs.isStale(veryOld));

        // Null timestamp = potentially stale
        assertTrue(fallbackObs.isStale(null));
    }

    @Test
    @DisplayName("Should record default fallback")
    void shouldRecordDefaultFallback() {
        FallbackObservability.FallbackResult result = fallbackObs.recordDefaultFallback(
            "test-component",
            "test-operation",
            FallbackObservability.FallbackReason.SERVICE_UNAVAILABLE,
            "default-value"
        );

        assertTrue(result.usedFallback());
        assertEquals("default-value", result.getValue());
        assertEquals(FallbackObservability.FallbackSource.DEFAULT_VALUE, result.getSource());
    }

    @Test
    @DisplayName("Should record cache fallback with staleness tracking")
    void shouldRecordCacheFallback() {
        Instant cacheTimestamp = Instant.now().minus(Duration.ofMinutes(3));

        FallbackObservability.FallbackResult result = fallbackObs.recordCacheFallback(
            "test-component",
            "test-operation",
            () -> "cached-value",
            cacheTimestamp
        );

        assertTrue(result.usedFallback());
        assertEquals("cached-value", result.getValue());
        assertEquals(FallbackObservability.FallbackSource.LOCAL_CACHE, result.getSource());
        assertEquals(FallbackObservability.FallbackReason.CACHE_FALLBACK, result.getReason());
    }

    @Test
    @DisplayName("Should record circuit breaker fallback")
    void shouldRecordCircuitBreakerFallback() {
        FallbackObservability.FallbackResult result = fallbackObs.recordCircuitBreakerFallback(
            "test-component",
            "test-operation",
            () -> "fallback-value"
        );

        assertTrue(result.usedFallback());
        assertEquals("fallback-value", result.getValue());
        assertEquals(FallbackObservability.FallbackReason.CIRCUIT_OPEN, result.getReason());
        assertEquals(FallbackObservability.FallbackSource.SECONDARY_SERVICE, result.getSource());
    }

    @Test
    @DisplayName("Should record retry exhausted fallback")
    void shouldRecordRetryExhaustedFallback() {
        Throwable lastError = new RuntimeException("final retry failed");

        FallbackObservability.FallbackResult result = fallbackObs.recordRetryExhaustedFallback(
            "test-component",
            "test-operation",
            () -> "fallback-value",
            lastError
        );

        assertTrue(result.usedFallback());
        assertEquals("fallback-value", result.getValue());
        assertEquals(FallbackObservability.FallbackReason.RETRY_EXHAUSTED, result.getReason());
        assertEquals(lastError, result.getPrimaryError());
    }

    @Test
    @DisplayName("Should record degradation fallback")
    void shouldRecordDegradationFallback() {
        FallbackObservability.FallbackResult result = fallbackObs.recordDegradationFallback(
            "test-component",
            "test-operation",
            () -> "degraded-value"
        );

        assertTrue(result.usedFallback());
        assertEquals("degraded-value", result.getValue());
        assertEquals(FallbackObservability.FallbackReason.DEGRADATION, result.getReason());
    }

    @Test
    @DisplayName("Should track statistics correctly")
    void shouldTrackStatistics() {
        // Record several fallbacks
        fallbackObs.recordDefaultFallback("comp1", "op1",
            FallbackObservability.FallbackReason.SERVICE_ERROR, "value1");

        fallbackObs.recordFallback("comp2", "op2",
            FallbackObservability.FallbackReason.CIRCUIT_OPEN,
            FallbackObservability.FallbackSource.STALE_DATA,
            () -> "stale-value",
            Instant.now().minus(Duration.ofMinutes(10)),
            null);

        FallbackObservability.FallbackStats stats = fallbackObs.getStats();

        assertEquals(2, stats.getTotalFallbacks());
        assertEquals(1, stats.getTotalStaleDataServed());  // Only the second one was stale
        assertEquals(1, stats.getTotalAndonAlertsFired());  // One P1 alert for stale data
        assertEquals(0.5, stats.getStaleDataRatio(), 0.01);  // 50% stale
        assertEquals(2, stats.getTotalSuccesses());  // Both succeeded
        assertEquals(0, stats.getTotalErrors());  // No errors
        assertEquals(0.0, stats.getErrorRate(), 0.01);  // 0% error rate
    }

    @Test
    @DisplayName("Should throw on null required parameters")
    void shouldThrowOnNullParameters() {
        assertThrows(NullPointerException.class, () ->
            fallbackObs.recordFallback(null, "op",
                FallbackObservability.FallbackReason.SERVICE_ERROR,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                () -> "value", Instant.now(), null)
        );

        assertThrows(NullPointerException.class, () ->
            fallbackObs.recordFallback("comp", null,
                FallbackObservability.FallbackReason.SERVICE_ERROR,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                () -> "value", Instant.now(), null)
        );

        assertThrows(NullPointerException.class, () ->
            fallbackObs.recordFallback("comp", "op",
                null,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                () -> "value", Instant.now(), null)
        );

        assertThrows(NullPointerException.class, () ->
            fallbackObs.recordFallback("comp", "op",
                FallbackObservability.FallbackReason.SERVICE_ERROR,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                null, Instant.now(), null)
        );
    }

    @Test
    @DisplayName("Should propagate fallback supplier exceptions")
    void shouldPropagateFallbackExceptions() {
        RuntimeException expectedException = new RuntimeException("fallback failed");

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
            fallbackObs.recordFallback("comp", "op",
                FallbackObservability.FallbackReason.SERVICE_ERROR,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                () -> { throw expectedException; },
                Instant.now(), null)
        );

        assertEquals(expectedException, thrown.getCause());
    }

    @Test
    @DisplayName("Should track error rate correctly")
    void shouldTrackErrorRate() {
        // Record successful fallbacks
        fallbackObs.recordDefaultFallback("comp1", "op1",
            FallbackObservability.FallbackReason.SERVICE_ERROR, "value1");
        fallbackObs.recordDefaultFallback("comp2", "op2",
            FallbackObservability.FallbackReason.SERVICE_ERROR, "value2");

        // Record a failed fallback
        try {
            fallbackObs.recordFallback("comp3", "op3",
                FallbackObservability.FallbackReason.SERVICE_ERROR,
                FallbackObservability.FallbackSource.DEFAULT_VALUE,
                () -> { throw new RuntimeException("intentional test failure"); },
                Instant.now(), null);
        } catch (RuntimeException expected) {
            // Expected
        }

        FallbackObservability.FallbackStats stats = fallbackObs.getStats();

        assertEquals(2, stats.getTotalSuccesses());
        assertEquals(1, stats.getTotalErrors());
        assertEquals(1.0 / 3.0, stats.getErrorRate(), 0.01);  // 33.3% error rate
    }

    @Test
    @DisplayName("Should track data age correctly")
    void shouldTrackDataAge() {
        Instant dataTimestamp = Instant.now().minus(Duration.ofMinutes(7));
        long expectedAgeMs = Duration.ofMinutes(7).toMillis();

        FallbackObservability.FallbackResult result = fallbackObs.recordFallback(
            "test-component",
            "test-operation",
            FallbackObservability.FallbackReason.SERVICE_ERROR,
            FallbackObservability.FallbackSource.STALE_DATA,
            () -> "value",
            dataTimestamp,
            null
        );

        // Allow for some timing variance (within 500ms)
        assertTrue(result.getDataAgeMs() >= expectedAgeMs - 500);
        assertTrue(result.getDataAgeMs() <= expectedAgeMs + 500);
    }

    @Test
    @DisplayName("Should return singleton instance")
    void shouldReturnSingleton() {
        FallbackObservability instance1 = FallbackObservability.getInstance();
        FallbackObservability instance2 = FallbackObservability.getInstance();

        assertSame(instance1, instance2);
    }

    /**
     * Test implementation of AndonAlertService.
     */
    private static class TestAndonAlertService implements FallbackObservability.AndonAlertService {
        private final AtomicInteger alertCount = new AtomicInteger(0);
        private volatile String lastSeverity;
        private volatile String lastComponent;
        private volatile String lastOperation;
        private volatile String lastMessage;

        @Override
        public String fireAlert(String severity, String component, String operation,
                               String message, Map<String, Object> context) {
            alertCount.incrementAndGet();
            lastSeverity = severity;
            lastComponent = component;
            lastOperation = operation;
            lastMessage = message;
            return "test-alert-" + System.currentTimeMillis();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        public String getLastSeverity() { return lastSeverity; }
        public String getLastComponent() { return lastComponent; }
        public String getLastOperation() { return lastOperation; }
        public String getLastMessage() { return lastMessage; }
        public int getAlertCount() { return alertCount.get(); }
    }
}
