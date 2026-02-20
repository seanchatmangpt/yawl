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

package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiterRegistry.
 */
@DisplayName("Rate Limiter Registry Tests")
class TestRateLimiterRegistry {

    private RateLimiterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RateLimiterRegistry();
    }

    @Test
    @DisplayName("Global rate limiter initialized by default")
    void testGlobalLimiterInitialized() {
        assertTrue(registry.isGlobalLimiterEnabled());
        assertTrue(registry.getLimiterCount() > 0);
    }

    @Test
    @DisplayName("Global rate limiter can be toggled")
    void testToggleGlobalLimiter() {
        registry.setGlobalLimiterEnabled(false);
        assertFalse(registry.isGlobalLimiterEnabled());

        // When disabled, should always return true
        assertTrue(registry.acquireGlobal());

        registry.setGlobalLimiterEnabled(true);
        assertTrue(registry.isGlobalLimiterEnabled());
    }

    @Test
    @DisplayName("Per-client rate limiter tracks separate clients")
    void testPerClientRateLimiting() {
        String client1 = "client-192.168.1.100";
        String client2 = "client-192.168.1.101";

        // Both clients start with permits available
        assertTrue(registry.acquirePerClient(client1));
        assertTrue(registry.acquirePerClient(client2));

        assertEquals(3, registry.getLimiterCount()); // global + 2 clients
    }

    @Test
    @DisplayName("Per-endpoint rate limiter rejects null endpoint")
    void testPerEndpointNullEndpoint() {
        assertThrows(NullPointerException.class,
                () -> registry.acquirePerEndpoint(null, 100));
    }

    @Test
    @DisplayName("Per-endpoint rate limiter rejects invalid permits")
    void testPerEndpointInvalidPermits() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquirePerEndpoint("/api/cases", 0));

        assertThrows(IllegalArgumentException.class,
                () -> registry.acquirePerEndpoint("/api/cases", -10));
    }

    @Test
    @DisplayName("Custom limiter creation with validation")
    void testCustomLimiterCreation() {
        registry.createCustomLimiter("test-limiter", 50, 10);
        assertEquals(2, registry.getLimiterCount()); // global + custom

        assertThrows(NullPointerException.class,
                () -> registry.createCustomLimiter(null, 50, 10));

        assertThrows(IllegalArgumentException.class,
                () -> registry.createCustomLimiter("", 50, 10));

        assertThrows(IllegalArgumentException.class,
                () -> registry.createCustomLimiter("test", 0, 10));

        assertThrows(IllegalArgumentException.class,
                () -> registry.createCustomLimiter("test", 50, 0));
    }

    @Test
    @DisplayName("Reset all limiters")
    void testResetAll() {
        registry.createCustomLimiter("test1", 100, 10);
        registry.createCustomLimiter("test2", 100, 10);

        registry.resetAll();
        // After reset, limiters should be available again
        assertTrue(registry.acquireGlobal());
    }

    @Test
    @DisplayName("Per-client with empty ID rejected")
    void testPerClientEmptyId() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquirePerClient(""));
    }

    @Test
    @DisplayName("Per-client with null ID rejected")
    void testPerClientNullId() {
        assertThrows(NullPointerException.class,
                () -> registry.acquirePerClient(null));
    }
}
