/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago-style integration tests for per-tenant API rate limiting.
 *
 * Tests real rate limiting enforcement with sliding window counters.
 */
class ApiKeyRateLimitRegistryTest {

    private ApiKeyRateLimitRegistry registry;

    @BeforeEach
    void setUp() {
        // Settings provider: 100 requests/min for tenant-1, 1000 for tenant-2
        ApiKeyRateLimitRegistry.RateLimitQuery rateLimitQuery =
                tenantId -> "tenant-1".equals(tenantId) ? 100L : 1000L;

        ApiKeyRateLimitRegistry.WindowQuery windowQuery =
                tenantId -> 60_000L;  // 1 minute window

        registry = new ApiKeyRateLimitRegistry(rateLimitQuery, windowQuery);
    }

    @Test
    @DisplayName("Should allow requests within limit")
    void testAllowWithinLimit() {
        assertTrue(registry.checkRateLimit("tenant-1", "agent-1", "/api/dispatch"));
        assertTrue(registry.checkRateLimit("tenant-1", "agent-1", "/api/dispatch"));
        assertTrue(registry.checkRateLimit("tenant-1", "agent-1", "/api/dispatch"));

        assertEquals(3, registry.getCurrentRequestCount("tenant-1", "/api/dispatch"));
    }

    @Test
    @DisplayName("Should reject requests exceeding limit")
    void testRejectExceedingLimit() {
        String tenantId = "tenant-1";
        String endpoint = "/api/dispatch";
        long limit = 100;

        // Fill up to limit
        for (int i = 0; i < limit; i++) {
            assertTrue(registry.checkRateLimit(tenantId, "agent", endpoint));
        }

        // Verify at limit
        assertEquals(limit, registry.getCurrentRequestCount(tenantId, endpoint));

        // Next request should be rejected
        assertFalse(registry.checkRateLimit(tenantId, "agent", endpoint));
    }

    @Test
    @DisplayName("Should maintain independent limits per tenant")
    void testIndependentTenantLimits() {
        // tenant-1 has limit of 100
        // tenant-2 has limit of 1000

        // Fill tenant-1 to limit
        for (int i = 0; i < 100; i++) {
            assertTrue(registry.checkRateLimit("tenant-1", "agent", "/api/dispatch"));
        }

        // tenant-1 should be at limit
        assertEquals(100, registry.getCurrentRequestCount("tenant-1", "/api/dispatch"));
        assertFalse(registry.checkRateLimit("tenant-1", "agent", "/api/dispatch"));

        // tenant-2 should still have capacity
        assertTrue(registry.checkRateLimit("tenant-2", "agent", "/api/dispatch"));
        assertEquals(1, registry.getCurrentRequestCount("tenant-2", "/api/dispatch"));
    }

    @Test
    @DisplayName("Should track requests per endpoint")
    void testPerEndpointTracking() {
        String tenantId = "tenant-1";

        // Add requests to different endpoints
        assertTrue(registry.checkRateLimit(tenantId, "agent", "/api/dispatch"));
        assertTrue(registry.checkRateLimit(tenantId, "agent", "/api/status"));
        assertTrue(registry.checkRateLimit(tenantId, "agent", "/api/dispatch"));

        // Verify separate counts
        assertEquals(2, registry.getCurrentRequestCount(tenantId, "/api/dispatch"));
        assertEquals(1, registry.getCurrentRequestCount(tenantId, "/api/status"));
    }

    @Test
    @DisplayName("Should provide Retry-After header value")
    void testGetRetryAfterSeconds() {
        String tenantId = "tenant-1";
        String endpoint = "/api/dispatch";

        // Fill to limit
        for (int i = 0; i < 100; i++) {
            registry.checkRateLimit(tenantId, "agent", endpoint);
        }

        // Get retry-after
        long retryAfter = registry.getRetryAfterSeconds(tenantId, endpoint);

        // Should be within reasonable bounds (1-60 seconds)
        assertTrue(retryAfter >= 1 && retryAfter <= 60);
    }

    @Test
    @DisplayName("Should reset rate limit for cleanup")
    void testResetRateLimit() {
        String tenantId = "tenant-1";
        String endpoint = "/api/dispatch";

        // Add requests
        for (int i = 0; i < 50; i++) {
            registry.checkRateLimit(tenantId, "agent", endpoint);
        }

        assertEquals(50, registry.getCurrentRequestCount(tenantId, endpoint));

        // Reset
        registry.resetRateLimit(tenantId, endpoint);

        // Count should be cleared
        assertEquals(0, registry.getCurrentRequestCount(tenantId, endpoint));

        // Should be able to add more
        assertTrue(registry.checkRateLimit(tenantId, "agent", endpoint));
        assertEquals(1, registry.getCurrentRequestCount(tenantId, endpoint));
    }

    @Test
    @DisplayName("Should clear all rate limits")
    void testClearAll() {
        // Add requests to multiple endpoints
        registry.checkRateLimit("tenant-1", "agent", "/api/dispatch");
        registry.checkRateLimit("tenant-2", "agent", "/api/status");

        // Verify counts
        assertTrue(registry.getCurrentRequestCount("tenant-1", "/api/dispatch") > 0);
        assertTrue(registry.getCurrentRequestCount("tenant-2", "/api/status") > 0);

        // Clear all
        registry.clearAll();

        // All counts should be zero
        assertEquals(0, registry.getCurrentRequestCount("tenant-1", "/api/dispatch"));
        assertEquals(0, registry.getCurrentRequestCount("tenant-2", "/api/status"));
    }

    @Test
    @DisplayName("Should get current rate limit for tenant")
    void testGetRateLimit() {
        assertEquals(100, registry.getRateLimit("tenant-1"));
        assertEquals(1000, registry.getRateLimit("tenant-2"));
        assertEquals(1000, registry.getRateLimit("unknown-tenant"));
    }

    @Test
    @DisplayName("Should get snapshot of all rate limits")
    void testGetSnapshot() {
        registry.checkRateLimit("tenant-1", "agent", "/api/dispatch");
        registry.checkRateLimit("tenant-1", "agent", "/api/dispatch");
        registry.checkRateLimit("tenant-2", "agent", "/api/status");

        var snapshot = registry.getSnapshot();

        assertFalse(snapshot.isEmpty());
        assertTrue(snapshot.containsKey("tenant-1:/api/dispatch"));
        assertTrue(snapshot.containsKey("tenant-2:/api/status"));

        assertEquals(2L, snapshot.get("tenant-1:/api/dispatch"));
        assertEquals(1L, snapshot.get("tenant-2:/api/status"));
    }

    @Test
    @DisplayName("Should handle null tenant ID")
    void testNullTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.checkRateLimit(null, "agent", "/api/dispatch"));
    }

    @Test
    @DisplayName("Should handle empty tenant ID")
    void testEmptyTenantId() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.checkRateLimit("", "agent", "/api/dispatch"));
    }

    @Test
    @DisplayName("Should handle null endpoint")
    void testNullEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.checkRateLimit("tenant-1", "agent", null));
    }

    @Test
    @DisplayName("Should handle empty endpoint")
    void testEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.checkRateLimit("tenant-1", "agent", ""));
    }

    @Test
    @DisplayName("Should handle default constructor with default limits")
    void testDefaultConstructor() {
        ApiKeyRateLimitRegistry defaultRegistry = new ApiKeyRateLimitRegistry();

        // Default should allow up to 10,000 requests
        for (int i = 0; i < 100; i++) {
            assertTrue(defaultRegistry.checkRateLimit("tenant-test", "agent", "/api/test"));
        }

        assertEquals(100, defaultRegistry.getCurrentRequestCount("tenant-test", "/api/test"));
    }

    @Test
    @DisplayName("Should handle concurrent requests from same tenant")
    void testConcurrentRequestsFromTenant() throws InterruptedException {
        String tenantId = "tenant-concurrent";
        String endpoint = "/api/dispatch";

        // Simulate 10 concurrent requests
        Thread[] threads = new Thread[10];
        boolean[] results = new boolean[10];

        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() ->
                    results[index] = registry.checkRateLimit(tenantId, "agent-" + index, endpoint)
            );
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // All should have succeeded (within default limit)
        for (boolean result : results) {
            assertTrue(result);
        }

        // Count should be 10
        assertEquals(10, registry.getCurrentRequestCount(tenantId, endpoint));
    }

    @Test
    @DisplayName("Should provide accurate Retry-After when freshly rate-limited")
    void testRetryAfterAccuracy() {
        String tenantId = "tenant-1";
        String endpoint = "/api/dispatch";

        // Fill to limit
        for (int i = 0; i < 100; i++) {
            registry.checkRateLimit(tenantId, "agent", endpoint);
        }

        // Get retry-after immediately
        long retryAfter = registry.getRetryAfterSeconds(tenantId, endpoint);

        // Should be close to 60 seconds (the window size)
        assertTrue(retryAfter > 50 && retryAfter <= 60,
                "Retry-After should be close to window size. Got: " + retryAfter);
    }
}
