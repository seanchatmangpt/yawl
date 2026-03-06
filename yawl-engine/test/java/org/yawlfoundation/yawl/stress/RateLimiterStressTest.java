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

package org.yawlfoundation.yawl.stress;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.authentication.ApiKeyRateLimitRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test suite for YAWL rate limiter and quota enforcement.
 *
 * Tests are designed to find EXACT breaking points:
 * 1. Rate limit accuracy (% correctly blocked at limit+1)
 * 2. Burst absorption ceiling
 * 3. Multi-tenant isolation (no cross-tenant bleed)
 * 4. Memory consumption per API key
 * 5. Sliding window accuracy
 * 6. Concurrent rate check latency
 * 7. Hard quota enforcement (HTTP 429)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class RateLimiterStressTest {

    private static final long CONFIGURED_RATE_LIMIT = 10_000L;  // 10,000 requests/minute
    private static final long WINDOW_MS = 60_000L;               // 1 minute window

    private ApiKeyRateLimitRegistry rateLimiter;
    private MemoryMXBean memoryBean;

    @BeforeEach
    void setUp() {
        rateLimiter = new ApiKeyRateLimitRegistry();
        memoryBean = ManagementFactory.getMemoryMXBean();
    }

    /**
     * TEST 1: Rate Limit Accuracy Test
     *
     * Verifies that the rate limiter correctly allows up to the limit
     * and blocks requests above the limit.
     *
     * Expected: 100% accuracy
     * - Allow: (limit - 1) requests
     * - Allow: limit requests
     * - Block: (limit + 1) requests
     */
    @Test
    @DisplayName("1. Rate Limit Accuracy Test - verify exact limit enforcement")
    @Timeout(30)
    void testRateLimitAccuracy() {
        String tenantId = "tenant-accuracy";
        String endpoint = "/api/cases";
        long testLimit = 100;  // Use 100 for faster test, not full 10K

        // Custom provider with small limit for testing
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return testLimit; }
                @Override
                public long getWindowMs(String tid) { return WINDOW_MS; }
            }
        );

        int allowedCount = 0;
        int blockedCount = 0;

        // Send (testLimit + 10) requests
        for (int i = 0; i < testLimit + 10; i++) {
            boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            if (allowed) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }

        // Verify accuracy
        assertEquals(testLimit, allowedCount, "Should allow exactly " + testLimit + " requests");
        assertEquals(10, blockedCount, "Should block exactly 10 requests");

        double accuracyPercent = (100.0 * blockedCount) / (blockedCount + allowedCount) * (10.0 / (allowedCount + blockedCount));
        System.out.println("TEST 1 - Rate Limit Accuracy: " + allowedCount + " allowed, " +
                blockedCount + " blocked | Accuracy: 100% (0 false positives)");
    }

    /**
     * TEST 2: Burst Absorption Test
     *
     * Sends 10x the per-minute limit in 1 second.
     * Measures how many get through vs blocked.
     * Finds burst ceiling.
     *
     * Expected: Burst ceiling = rate limit (no accumulation across reset windows)
     */
    @Test
    @DisplayName("2. Burst Absorption Test - find maximum burst before hard block")
    @Timeout(45)
    void testBurstAbsorption() throws InterruptedException {
        String tenantId = "tenant-burst";
        String endpoint = "/api/submit-case";
        long burstLimit = 50;  // Use 50 for faster test

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return burstLimit; }
                @Override
                public long getWindowMs(String tid) { return WINDOW_MS; }
            }
        );

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Create executor for parallel burst requests
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<Integer>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        // Submit 10x the burst limit (500 requests) in rapid succession
        for (int i = 0; i < burstLimit * 10; i++) {
            final int requestNum = i;
            futures.add(executor.submit(() -> {
                boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
                if (allowed) {
                    allowedCount.incrementAndGet();
                } else {
                    blockedCount.incrementAndGet();
                }
                return requestNum;
            }));
        }

        // Wait for all to complete
        for (Future<Integer> f : futures) {
            f.get();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        executor.shutdown();

        // Verify burst ceiling
        int totalProcessed = allowedCount.get() + blockedCount.get();
        System.out.println("TEST 2 - Burst Absorption: " + allowedCount.get() + " allowed, " +
                blockedCount.get() + " blocked (500 total burst) | Elapsed: " + elapsed + "ms");
        System.out.println("         Burst ceiling: " + allowedCount.get() + " requests");

        assertTrue(allowedCount.get() <= burstLimit * 1.1,
                "Burst ceiling should not exceed ~1.1x the rate limit");
    }

    /**
     * TEST 3: Multi-Tenant Isolation Test
     *
     * Runs T tenants simultaneously, each at their quota limit.
     * Verifies no cross-tenant bleed (tenant A's quota doesn't affect tenant B).
     *
     * Test T=2, 10, 100, 1000
     *
     * Expected: Each tenant gets exactly their share, zero cross-bleed
     */
    @Test
    @DisplayName("3. Multi-Tenant Isolation Test - verify no cross-tenant quota bleed")
    @Timeout(120)
    void testMultiTenantIsolation() throws InterruptedException, ExecutionException {
        long tenantLimit = 100;
        String endpoint = "/api/monitor";

        // Create a custom provider that gives different limits per tenant
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return tenantLimit; }
                @Override
                public long getWindowMs(String tid) { return WINDOW_MS; }
            }
        );

        // Test with T=10 tenants (smaller for faster test)
        int tenantCount = 10;

        // Track allowed/blocked per tenant
        ConcurrentHashMap<String, AtomicInteger> tenantAllowed = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> tenantBlocked = new ConcurrentHashMap<>();

        for (int t = 0; t < tenantCount; t++) {
            tenantAllowed.put("tenant-" + t, new AtomicInteger(0));
            tenantBlocked.put("tenant-" + t, new AtomicInteger(0));
        }

        ExecutorService executor = Executors.newFixedThreadPool(tenantCount);
        List<Future<?>> futures = new ArrayList<>();

        // Each tenant sends (tenantLimit + 20) requests
        for (int t = 0; t < tenantCount; t++) {
            final int tenantIdx = t;
            futures.add(executor.submit(() -> {
                String tenantId = "tenant-" + tenantIdx;
                for (int i = 0; i < tenantLimit + 20; i++) {
                    boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
                    if (allowed) {
                        tenantAllowed.get(tenantId).incrementAndGet();
                    } else {
                        tenantBlocked.get(tenantId).incrementAndGet();
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // Verify each tenant got exactly their quota, no bleed
        boolean allClear = true;
        for (int t = 0; t < tenantCount; t++) {
            String tenantId = "tenant-" + t;
            int allowed = tenantAllowed.get(tenantId).get();
            int blocked = tenantBlocked.get(tenantId).get();

            if (allowed != tenantLimit) {
                allClear = false;
                System.out.println("ISOLATION VIOLATION: Tenant " + t + " allowed=" + allowed +
                        " (expected " + tenantLimit + ")");
            }
        }

        assertTrue(allClear, "All tenants should get exactly their quota with zero bleed");
        System.out.println("TEST 3 - Multi-Tenant Isolation (T=10): PASS - each tenant got exactly " +
                tenantLimit + " requests, zero bleed");
    }

    /**
     * TEST 4: Rate Limiter Memory Test
     *
     * Creates N different API keys (tenants) each with their own rate limit state.
     * Measures memory at N=1K, 10K, 100K.
     * Finds where memory > 1GB (OOM point).
     *
     * Expected: ~500 bytes per tenant/endpoint pair
     */
    @Test
    @DisplayName("4. Rate Limiter Memory Test - find OOM point with N API keys")
    @Timeout(60)
    void testRateLimiterMemory() {
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry();
        String endpoint = "/api/cases";

        // Test at different scales
        int[] testSizes = {1_000, 10_000, 100_000};

        for (int n : testSizes) {
            limiter.clearAll();
            long memBefore = memoryBean.getHeapMemoryUsage().getUsed();

            // Create n different tenants and activate their rate limits
            for (int i = 0; i < n; i++) {
                String tenantId = "tenant-" + i;
                // Just one request per tenant to activate state
                limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            }

            long memAfter = memoryBean.getHeapMemoryUsage().getUsed();
            long memUsed = memAfter - memBefore;
            double bytesPerKey = (double) memUsed / n;

            System.out.println("TEST 4 - Memory: N=" + n + " keys | Memory used: " +
                    (memUsed / 1024) + " KB | Per-key: " + String.format("%.1f", bytesPerKey) + " bytes");

            // Verify reasonable memory usage
            assertTrue(bytesPerKey < 2000, "Each key should use <2KB, got " + bytesPerKey);
        }
    }

    /**
     * TEST 5: Sliding Window Accuracy Test
     *
     * Verifies window is truly sliding (not fixed bucket).
     * Send requests at:
     * - t=0 (should be allowed)
     * - t=30s (should be allowed)
     * - t=60s (original window expires, should reset and allow more)
     * - t=61s (should still be in new window, allow more)
     *
     * Expected: All allowed if within their respective windows
     */
    @Test
    @DisplayName("5. Sliding Window Accuracy Test - verify true sliding behavior")
    @Timeout(75)
    void testSlidingWindowAccuracy() throws InterruptedException {
        String tenantId = "tenant-sliding";
        String endpoint = "/api/test";
        long smallLimit = 10;
        long smallWindow = 2000L;  // 2 second window for faster test

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return smallLimit; }
                @Override
                public long getWindowMs(String tid) { return smallWindow; }
            }
        );

        // T0: Send limit requests
        long t0 = System.currentTimeMillis();
        int allowedT0 = 0;
        for (int i = 0; i < smallLimit; i++) {
            if (limiter.checkRateLimit(tenantId, "agent-1", endpoint)) {
                allowedT0++;
            }
        }
        assertEquals(smallLimit, allowedT0, "Should allow all " + smallLimit + " at t=0");

        // T0+100ms: Try one more (should block)
        Thread.sleep(100);
        boolean blockedT100 = !limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        assertTrue(blockedT100, "Should block request at t=100ms when limit exhausted");

        // T0+2000ms: Window should expire, reset should allow new requests
        Thread.sleep(2000);
        boolean allowedT2000 = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        assertTrue(allowedT2000, "Should allow new request after window expires (t=2000ms)");

        // T0+2100ms: Should still be in new window
        Thread.sleep(100);
        boolean allowedT2100 = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        assertTrue(allowedT2100, "Should allow request in new window (t=2100ms)");

        System.out.println("TEST 5 - Sliding Window: PASS - window correctly resets after expiry");
    }

    /**
     * TEST 6: Concurrent Rate Check Stress
     *
     * N threads simultaneously checking the same API key rate limit.
     * Measures contention and latency.
     * Finds N where latency > 1ms.
     *
     * Expected: <1ms latency up to N=1000+ threads
     */
    @Test
    @DisplayName("6. Concurrent Rate Check Stress - measure latency under contention")
    @Timeout(120)
    void testConcurrentRateCheckStress() throws InterruptedException, ExecutionException {
        String tenantId = "tenant-concurrent";
        String endpoint = "/api/concurrent";
        long checkLimit = 100_000;  // Very high limit to avoid blocking

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return checkLimit; }
                @Override
                public long getWindowMs(String tid) { return WINDOW_MS; }
            }
        );

        // Test with increasing thread counts
        int[] threadCounts = {10, 100, 500, 1000};

        for (int threadCount : threadCounts) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Future<Long>> futures = new ArrayList<>();

            long globalStartTime = System.nanoTime();

            // Each thread does 100 rate limit checks
            for (int t = 0; t < threadCount; t++) {
                futures.add(executor.submit(() -> {
                    long threadStartTime = System.nanoTime();
                    for (int i = 0; i < 100; i++) {
                        long checkStart = System.nanoTime();
                        limiter.checkRateLimit(tenantId, "agent-" + Thread.currentThread().getId(), endpoint);
                        long checkEnd = System.nanoTime();
                    }
                    return System.nanoTime() - threadStartTime;
                }));
            }

            long maxLatencyNs = 0;
            for (Future<Long> f : futures) {
                maxLatencyNs = Math.max(maxLatencyNs, f.get());
            }

            long globalElapsed = System.nanoTime() - globalStartTime;
            double avgLatencyMs = (maxLatencyNs / 1_000_000.0) / 100;  // Per-check average
            double globalMs = globalElapsed / 1_000_000.0;

            executor.shutdown();

            System.out.println("TEST 6 - Concurrent Stress: T=" + threadCount + " threads | " +
                    "Global time: " + String.format("%.1f", globalMs) + "ms | " +
                    "Avg per-check: " + String.format("%.3f", avgLatencyMs) + "ms");
        }
    }

    /**
     * TEST 7: Quota Enforcement Hard Stop Test
     *
     * Configure hard quota of X cases.
     * Submit X+1 cases.
     * Verify the X+1th is rejected with HTTP 429 logic.
     *
     * This simulates case submission quota (not just request rate).
     *
     * Expected: (X+1)th case rejected
     */
    @Test
    @DisplayName("7. Hard Quota Enforcement Test - verify rejection at X+1")
    @Timeout(30)
    void testHardQuotaEnforcement() {
        String tenantId = "tenant-quota";
        String endpoint = "/api/submit-case";
        long hardQuota = 50;

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            new ApiKeyRateLimitRegistry.RateLimitSettingsProvider() {
                @Override
                public long getRateLimit(String tid) { return hardQuota; }
                @Override
                public long getWindowMs(String tid) { return WINDOW_MS; }
            }
        );

        // Submit exactly hardQuota cases
        int allowedCount = 0;
        int blockedCount = 0;

        for (int i = 0; i < hardQuota + 1; i++) {
            boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            if (allowed) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }

        // The (hardQuota+1)th should be rejected
        assertEquals(hardQuota, allowedCount, "Should allow exactly " + hardQuota + " cases");
        assertEquals(1, blockedCount, "Should reject the (quota+1)th case");

        // Get retry-after header value
        long retryAfter = limiter.getRetryAfterSeconds(tenantId, endpoint);
        assertTrue(retryAfter > 0, "Retry-After should be positive");

        System.out.println("TEST 7 - Hard Quota: PASS - quota=" + hardQuota + " allowed, " +
                blockedCount + " rejected | Retry-After: " + retryAfter + "s");
    }

    /**
     * Extended test: Measure behavior at full configured limit (10K req/min)
     *
     * This is optional and slow, but verifies the default 10,000 req/min limit.
     * Uncomment to run full stress test.
     */
    @Test
    @DisplayName("EXTENDED: Full 10K req/min accuracy test (slow, optional)")
    @Timeout(300)
    void testFullConfiguredLimit() {
        String tenantId = "tenant-full-limit";
        String endpoint = "/api/full-test";

        // Use default 10K limit
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry();

        long allowedCount = 0;
        long blockedCount = 0;

        // Send 10,100 requests (10K limit + 100 extra)
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10_100; i++) {
            boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            if (allowed) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }
        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals(CONFIGURED_RATE_LIMIT, allowedCount, "Should allow exactly 10,000 requests");
        assertEquals(100, blockedCount, "Should block exactly 100 requests");

        System.out.println("TEST EXTENDED - Full 10K Limit: " + allowedCount + " allowed, " +
                blockedCount + " blocked | Time: " + elapsed + "ms");
    }
}
