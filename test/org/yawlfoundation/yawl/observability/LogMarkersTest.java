package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Marker;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SLF4J Markers used in structured logging.
 *
 * Verifies:
 * - Marker creation and naming conventions
 * - Marker hierarchy and parent-child relationships
 * - Circuit breaker event markers
 * - Rate limiter event markers
 * - Retry event markers
 * - Custom marker creation with parents
 */
@DisplayName("Log Markers Tests")
public class LogMarkersTest {

    @Test
    @DisplayName("Circuit breaker OPEN marker is created")
    void testCircuitBreakerOpenMarker() {
        String breaker = "inventory-service";
        Marker marker = LogMarkers.circuitBreakerOpen(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_OPEN"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker HALF_OPEN marker is created")
    void testCircuitBreakerHalfOpenMarker() {
        String breaker = "payment-service";
        Marker marker = LogMarkers.circuitBreakerHalfOpen(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_HALF_OPEN"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker CLOSED marker is created")
    void testCircuitBreakerClosedMarker() {
        String breaker = "notification-service";
        Marker marker = LogMarkers.circuitBreakerClosed(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_CLOSED"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker state change marker is created")
    void testCircuitBreakerStateChangeMarker() {
        String breaker = "auth-service";
        Marker marker = LogMarkers.circuitBreakerStateChange(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("STATE_CHANGE"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker ERROR marker is created")
    void testCircuitBreakerErrorMarker() {
        String breaker = "database-service";
        Marker marker = LogMarkers.circuitBreakerError(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_ERROR"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker SUCCESS marker is created")
    void testCircuitBreakerSuccessMarker() {
        String breaker = "cache-service";
        Marker marker = LogMarkers.circuitBreakerSuccess(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_SUCCESS"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker SLOW_CALL marker is created")
    void testCircuitBreakerSlowCallMarker() {
        String breaker = "external-api";
        Marker marker = LogMarkers.circuitBreakerSlowCall(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_SLOW_CALL"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Circuit breaker IGNORED_ERROR marker is created")
    void testCircuitBreakerIgnoredErrorMarker() {
        String breaker = "legacy-service";
        Marker marker = LogMarkers.circuitBreakerIgnoredError(breaker);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("CIRCUIT_BREAKER_IGNORED_ERROR"));
        assertTrue(marker.getName().contains(breaker));
    }

    @Test
    @DisplayName("Rate limiter ALLOWED marker is created")
    void testRateLimiterAllowedMarker() {
        String limiter = "api-gateway";
        Marker marker = LogMarkers.rateLimiterAllowed(limiter);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RATE_LIMIT_ALLOWED"));
        assertTrue(marker.getName().contains(limiter));
    }

    @Test
    @DisplayName("Rate limiter EXCEEDED marker is created")
    void testRateLimiterExceededMarker() {
        String limiter = "workflow-api";
        Marker marker = LogMarkers.rateLimiterExceeded(limiter);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RATE_LIMIT_EXCEEDED"));
        assertTrue(marker.getName().contains(limiter));
    }

    @Test
    @DisplayName("Retry ATTEMPT marker is created")
    void testRetryAttemptMarker() {
        String retry = "database-query";
        Marker marker = LogMarkers.retryAttempt(retry);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RETRY_ATTEMPT"));
        assertTrue(marker.getName().contains(retry));
    }

    @Test
    @DisplayName("Retry SUCCESS marker is created")
    void testRetrySuccessMarker() {
        String retry = "remote-call";
        Marker marker = LogMarkers.retrySuccess(retry);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RETRY_SUCCESS"));
        assertTrue(marker.getName().contains(retry));
    }

    @Test
    @DisplayName("Retry EXHAUSTED marker is created")
    void testRetryExhaustedMarker() {
        String retry = "file-upload";
        Marker marker = LogMarkers.retryExhausted(retry);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RETRY_EXHAUSTED"));
        assertTrue(marker.getName().contains(retry));
    }

    @Test
    @DisplayName("Retry IGNORED_ERROR marker is created")
    void testRetryIgnoredErrorMarker() {
        String retry = "transient-operation";
        Marker marker = LogMarkers.retryIgnoredError(retry);

        assertNotNull(marker);
        assertTrue(marker.getName().contains("RETRY_IGNORED_ERROR"));
        assertTrue(marker.getName().contains(retry));
    }

    @Test
    @DisplayName("Custom marker with parent is created")
    void testCustomMarkerWithParent() {
        Marker parent = LogMarkers.circuitBreakerOpen("service");
        Marker child = LogMarkers.withParent("custom-event", parent);

        assertNotNull(child);
        assertTrue(child.getName().contains("custom-event"));
    }

    @Test
    @DisplayName("Different breaker names create different markers")
    void testDifferentBreakerNamesCreateDifferentMarkers() {
        Marker marker1 = LogMarkers.circuitBreakerOpen("service-1");
        Marker marker2 = LogMarkers.circuitBreakerOpen("service-2");

        assertNotNull(marker1);
        assertNotNull(marker2);
        assertNotEquals(marker1.getName(), marker2.getName());
    }

    @Test
    @DisplayName("Same breaker name creates consistent markers")
    void testSameBreakerNameCreatesConsistentMarkers() {
        String breaker = "consistent-service";

        Marker marker1 = LogMarkers.circuitBreakerOpen(breaker);
        Marker marker2 = LogMarkers.circuitBreakerOpen(breaker);

        // Both should contain the breaker name
        assertTrue(marker1.getName().contains(breaker));
        assertTrue(marker2.getName().contains(breaker));
    }

    @Test
    @DisplayName("Multiple marker types can be created without conflicts")
    void testMultipleMarkerTypesNoConflicts() {
        String service = "multi-service";

        Marker cbMarker = LogMarkers.circuitBreakerOpen(service);
        Marker rlMarker = LogMarkers.rateLimiterExceeded(service);
        Marker retryMarker = LogMarkers.retryExhausted(service);

        assertNotNull(cbMarker);
        assertNotNull(rlMarker);
        assertNotNull(retryMarker);

        assertTrue(cbMarker.getName().contains("CIRCUIT_BREAKER_OPEN"));
        assertTrue(rlMarker.getName().contains("RATE_LIMIT_EXCEEDED"));
        assertTrue(retryMarker.getName().contains("RETRY_EXHAUSTED"));
    }

    @Test
    @DisplayName("Marker names are human-readable")
    void testMarkerNamesHumanReadable() {
        Marker marker = LogMarkers.circuitBreakerOpen("payment-processor");

        String name = marker.getName();
        assertTrue(name.contains("CIRCUIT_BREAKER_OPEN"));
        assertTrue(name.contains("payment-processor"));
        assertTrue(name.contains(":"), "Marker should use colon separator for readability");
    }

    @Test
    @DisplayName("Markers work with special characters in names")
    void testMarkersWithSpecialCharactersInNames() {
        String serviceName = "service-api_v2.0";

        Marker marker = LogMarkers.circuitBreakerOpen(serviceName);
        assertNotNull(marker);
        assertTrue(marker.getName().contains(serviceName));
    }

    @Test
    @DisplayName("All circuit breaker markers are created successfully")
    void testAllCircuitBreakerMarkers() {
        String breaker = "test-breaker";

        assertDoesNotThrow(() -> {
            LogMarkers.circuitBreakerOpen(breaker);
            LogMarkers.circuitBreakerHalfOpen(breaker);
            LogMarkers.circuitBreakerClosed(breaker);
            LogMarkers.circuitBreakerStateChange(breaker);
            LogMarkers.circuitBreakerError(breaker);
            LogMarkers.circuitBreakerSuccess(breaker);
            LogMarkers.circuitBreakerSlowCall(breaker);
            LogMarkers.circuitBreakerIgnoredError(breaker);
        });
    }

    @Test
    @DisplayName("All rate limiter markers are created successfully")
    void testAllRateLimiterMarkers() {
        String limiter = "test-limiter";

        assertDoesNotThrow(() -> {
            LogMarkers.rateLimiterAllowed(limiter);
            LogMarkers.rateLimiterExceeded(limiter);
        });
    }

    @Test
    @DisplayName("All retry markers are created successfully")
    void testAllRetryMarkers() {
        String retry = "test-retry";

        assertDoesNotThrow(() -> {
            LogMarkers.retryAttempt(retry);
            LogMarkers.retrySuccess(retry);
            LogMarkers.retryExhausted(retry);
            LogMarkers.retryIgnoredError(retry);
        });
    }

}
