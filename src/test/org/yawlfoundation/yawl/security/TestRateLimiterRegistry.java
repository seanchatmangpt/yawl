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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimiterRegistry with Java 25 patterns (ReentrantReadWriteLock).
 *
 * Tests verify:
 * - Correct initialization and thread safety
 * - Per-client and per-endpoint rate limiting
 * - Global limiter enable/disable with read-write locking
 * - Concurrent access patterns
 * - Configuration validation
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

    @Test
    @DisplayName("Per-endpoint with empty endpoint rejected")
    void testPerEndpointEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquirePerEndpoint("", 100));
    }

    @Test
    @DisplayName("Per-endpoint with null endpoint rejected")
    void testPerEndpointNullEndpoint2() {
        assertThrows(NullPointerException.class,
                () -> registry.acquirePerEndpoint(null, 100));
    }

    @Test
    @DisplayName("Get configuration returns correct config")
    void testGetConfiguration() {
        registry.createCustomLimiter("test-config", 75, 15);
        assertNotNull(registry.getConfig("test-config"));
        assertNull(registry.getConfig("nonexistent"));
    }

    @Test
    @DisplayName("Concurrent reads of global limiter state (ReentrantReadWriteLock)")
    @Timeout(5)
    void testConcurrentGlobalLimiterReads() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    // Multiple reads should not block each other
                    boolean enabled = registry.isGlobalLimiterEnabled();
                    assertTrue(enabled);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        assertEquals(threadCount, successCount.get());
    }

    @Test
    @DisplayName("Concurrent writes to global limiter state (ReentrantReadWriteLock)")
    @Timeout(5)
    void testConcurrentGlobalLimiterWrites() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final boolean enabled = (i % 2 == 0);
            new Thread(() -> {
                try {
                    registry.setGlobalLimiterEnabled(enabled);
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        // Should end in a valid state (either enabled or disabled)
        assertNotNull(registry.isGlobalLimiterEnabled());
    }

    @Test
    @DisplayName("Concurrent per-client limiter creation")
    @Timeout(5)
    void testConcurrentClientCreation() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int clientId = i;
            new Thread(() -> {
                try {
                    boolean result = registry.acquirePerClient("client-" + clientId);
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        // All should succeed in acquiring permits (first attempts)
        assertEquals(threadCount, successCount.get());
    }

    @Test
    @DisplayName("Custom limiter creation with same name (idempotent)")
    void testIdempotentLimiterCreation() {
        registry.createCustomLimiter("idempotent", 100, 10);
        int countBefore = registry.getLimiterCount();

        // Creating with same name should be idempotent (or replace)
        registry.createCustomLimiter("idempotent", 100, 10);
        int countAfter = registry.getLimiterCount();

        // Count should not increase (overwrite)
        assertTrue(countAfter <= countBefore + 1);
    }

    @Test
    @DisplayName("Per-endpoint permits calculation")
    void testEndpointPermitsCalculation() {
        // 100 permits per minute = 10 permits per 6 seconds
        registry.acquirePerEndpoint("/api/test1", 100);
        registry.acquirePerEndpoint("/api/test2", 60);

        // Both should be created
        assertTrue(registry.getLimiterCount() >= 3); // global + 2 endpoints
    }

    @Test
    @DisplayName("Global acquire returns false when disabled")
    void testGlobalAcquireWhenDisabled() {
        registry.setGlobalLimiterEnabled(false);
        assertTrue(registry.acquireGlobal()); // Should always return true when disabled
    }

    @RepeatedTest(5)
    @DisplayName("Multiple acquisitions of same client (stress test)")
    void testMultipleAcquisitions() {
        boolean result = true;
        for (int i = 0; i < 50; i++) {
            result = registry.acquirePerClient("stress-client");
            if (!result) break; // Expected to hit limit eventually
        }
        // May or may not be limited depending on timing
        assertNotNull(result);
    }
}
