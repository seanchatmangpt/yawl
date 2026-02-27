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

package org.yawlfoundation.yawl.integration.oauth2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SecurityEventBus}.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class SecurityEventBusTest {

    private SecurityEventBus eventBus;
    private List<SecurityEventBus.SecurityEvent> capturedEvents;

    @BeforeEach
    void setUp() {
        SecurityEventBus.resetForTesting();
        eventBus = SecurityEventBus.getInstance();
        capturedEvents = new ArrayList<>();
        eventBus.subscribe(capturedEvents::add);
    }

    @AfterEach
    void tearDown() {
        capturedEvents.clear();
        SecurityEventBus.resetForTesting();
    }

    @Test
    @DisplayName("JwksRefreshSuccess event has correct properties")
    void jwksRefreshSuccessHasCorrectProperties() {
        String jwksUri = "https://auth.example.com/certs";
        int cacheSize = 5;

        SecurityEventBus.JwksRefreshSuccess event =
            new SecurityEventBus.JwksRefreshSuccess(jwksUri, cacheSize);

        assertNotNull(event.eventId());
        assertEquals("jwks.refresh.success", event.eventType());
        assertEquals(SecurityEventBus.Severity.P2, event.severity());
        assertNotNull(event.timestamp());
        assertEquals(jwksUri, event.jwksUri());
        assertEquals(cacheSize, event.cacheSize());
        assertTrue(event.message().contains("5 keys"));
        assertTrue(event.message().contains(jwksUri));
    }

    @Test
    @DisplayName("JwksRefreshFailure event has correct properties")
    void jwksRefreshFailureHasCorrectProperties() {
        String jwksUri = "https://auth.example.com/certs";
        IOException cause = new IOException("Connection refused");
        int cacheSize = 3;
        long cacheAgeSeconds = 1200L;

        SecurityEventBus.JwksRefreshFailure event =
            new SecurityEventBus.JwksRefreshFailure(jwksUri, cause, cacheSize, cacheAgeSeconds);

        assertNotNull(event.eventId());
        assertEquals("jwks.refresh.failure", event.eventType());
        assertEquals(SecurityEventBus.Severity.P0, event.severity());
        assertNotNull(event.timestamp());
        assertEquals(jwksUri, event.jwksUri());
        assertEquals(cause, event.cause());
        assertEquals(cacheSize, event.cacheSize());
        assertEquals(cacheAgeSeconds, event.cacheAgeSeconds());
        assertTrue(event.message().contains("P0") || event.severity() == SecurityEventBus.Severity.P0);
        assertTrue(event.message().contains(jwksUri));
        assertTrue(event.message().contains("1200"));
    }

    @Test
    @DisplayName("JwksStaleCacheWarning event has correct properties")
    void jwksStaleCacheWarningHasCorrectProperties() {
        String jwksUri = "https://auth.example.com/certs";
        long cacheAgeSeconds = 900L;
        long thresholdSeconds = 600L;

        SecurityEventBus.JwksStaleCacheWarning event =
            new SecurityEventBus.JwksStaleCacheWarning(jwksUri, cacheAgeSeconds, thresholdSeconds);

        assertNotNull(event.eventId());
        assertEquals("jwks.cache.stale", event.eventType());
        assertEquals(SecurityEventBus.Severity.P1, event.severity());
        assertNotNull(event.timestamp());
        assertEquals(jwksUri, event.jwksUri());
        assertEquals(cacheAgeSeconds, event.cacheAgeSeconds());
        assertEquals(thresholdSeconds, event.thresholdSeconds());
        assertTrue(event.message().contains("900"));
        assertTrue(event.message().contains("600"));
    }

    @Test
    @DisplayName("Publish event notifies subscribers")
    void publishEventNotifiesSubscribers() {
        SecurityEventBus.JwksRefreshSuccess event =
            new SecurityEventBus.JwksRefreshSuccess("https://test.com/certs", 2);

        eventBus.publish(event);

        assertEquals(1, capturedEvents.size());
        assertSame(event, capturedEvents.get(0));
    }

    @Test
    @DisplayName("Multiple subscribers all receive events")
    void multipleSubscribersReceiveEvents() {
        List<SecurityEventBus.SecurityEvent> events1 = new ArrayList<>();
        List<SecurityEventBus.SecurityEvent> events2 = new ArrayList<>();

        eventBus.subscribe(e -> events1.add(e));
        eventBus.subscribe(e -> events2.add(e));

        SecurityEventBus.JwksRefreshSuccess event =
            new SecurityEventBus.JwksRefreshSuccess("https://test.com/certs", 1);
        eventBus.publish(event);

        assertEquals(1, events1.size());
        assertEquals(1, events2.size());
    }

    @Test
    @DisplayName("Unsubscribe removes subscriber")
    void unsubscribeRemovesSubscriber() {
        AtomicReference<SecurityEventBus.SecurityEvent> received = new AtomicReference<>();
        SecurityEventBus.Subscriber subscriber = received::set;

        eventBus.subscribe(subscriber);
        eventBus.unsubscribe(subscriber);

        SecurityEventBus.JwksRefreshSuccess event =
            new SecurityEventBus.JwksRefreshSuccess("https://test.com/certs", 1);
        eventBus.publish(event);

        assertNull(received.get());
    }

    @Test
    @DisplayName("Event toMap returns correct data")
    void eventToMapReturnsCorrectData() {
        SecurityEventBus.JwksRefreshFailure event =
            new SecurityEventBus.JwksRefreshFailure(
                "https://test.com/certs",
                new IOException("timeout"),
                5,
                300L
            );

        Map<String, Object> map = event.toMap();

        assertEquals("jwks.refresh.failure", map.get("eventType"));
        assertEquals("https://test.com/certs", map.get("jwksUri"));
        assertEquals("java.io.IOException", map.get("errorType"));
        assertEquals("timeout", map.get("errorMessage"));
        assertEquals(5, map.get("cacheSize"));
        assertEquals(300L, map.get("cacheAgeSeconds"));
        assertNotNull(map.get("eventId"));
        assertNotNull(map.get("timestamp"));
    }

    @Test
    @DisplayName("Subscriber exception does not prevent other subscribers")
    void subscriberExceptionDoesNotBlockOthers() {
        List<SecurityEventBus.SecurityEvent> successfulEvents = new ArrayList<>();

        // This subscriber throws an exception
        eventBus.subscribe(e -> { throw new RuntimeException("Test exception"); });
        // This subscriber should still receive events
        eventBus.subscribe(successfulEvents::add);

        SecurityEventBus.JwksRefreshSuccess event =
            new SecurityEventBus.JwksRefreshSuccess("https://test.com/certs", 1);
        eventBus.publish(event);

        // The second subscriber should have received the event despite the first throwing
        assertEquals(1, successfulEvents.size());
    }

    @Test
    @DisplayName("Null subscriber throws NullPointerException")
    void nullSubscriberThrowsException() {
        assertThrows(NullPointerException.class, () -> eventBus.subscribe(null));
    }

    @Test
    @DisplayName("Null event throws NullPointerException")
    void nullEventThrowsException() {
        assertThrows(NullPointerException.class, () -> eventBus.publish(null));
    }

    @Test
    @DisplayName("Severity levels are ordered correctly")
    void severityLevelsOrderedCorrectly() {
        SecurityEventBus.Severity[] severities = SecurityEventBus.Severity.values();

        assertEquals(4, severities.length);
        assertEquals(SecurityEventBus.Severity.P0, severities[0]);
        assertEquals(SecurityEventBus.Severity.P1, severities[1]);
        assertEquals(SecurityEventBus.Severity.P2, severities[2]);
        assertEquals(SecurityEventBus.Severity.P3, severities[3]);
    }

    // =========================================================================
    // Gauge Metrics Tests
    // =========================================================================

    @Test
    @DisplayName("updateCacheAge sets the internal cache age value")
    void updateCacheAgeSetsInternalValue() {
        eventBus.updateCacheAge(300L);
        assertEquals(300L, eventBus.getCurrentCacheAge());

        eventBus.updateCacheAge(-1L);
        assertEquals(-1L, eventBus.getCurrentCacheAge());
    }

    @Test
    @DisplayName("updateCacheStale sets the internal stale flag")
    void updateCacheStaleSetsInternalFlag() {
        eventBus.updateCacheStale(true);
        assertTrue(eventBus.isCurrentlyStale());

        eventBus.updateCacheStale(false);
        assertFalse(eventBus.isCurrentlyStale());
    }

    @Test
    @DisplayName("setCacheAgeSupplier accepts supplier function")
    void setCacheAgeSupplierAcceptsFunction() {
        AtomicLong age = new AtomicLong(100L);
        eventBus.setCacheAgeSupplier(age::get);

        // The supplier is set; we can't directly verify the gauge callback
        // but we can verify the method doesn't throw
        assertNotNull(eventBus);
    }

    @Test
    @DisplayName("setCacheStaleSupplier accepts supplier function")
    void setCacheStaleSupplierAcceptsFunction() {
        AtomicBoolean stale = new AtomicBoolean(false);
        eventBus.setCacheStaleSupplier(stale::get);

        // The supplier is set; we can't directly verify the gauge callback
        // but we can verify the method doesn't throw
        assertNotNull(eventBus);
    }

    @Test
    @DisplayName("Null supplier throws NullPointerException for cache age")
    void nullSupplierThrowsExceptionForCacheAge() {
        assertThrows(NullPointerException.class, () -> eventBus.setCacheAgeSupplier(null));
    }

    @Test
    @DisplayName("Null supplier throws NullPointerException for cache stale")
    void nullSupplierThrowsExceptionForCacheStale() {
        assertThrows(NullPointerException.class, () -> eventBus.setCacheStaleSupplier(null));
    }
}
