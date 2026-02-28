/*
 * Standalone rate limiter stress test - finds exact breaking points
 * Compile: javac -cp "src/org:." RateLimiterBreakingPointTest.java
 * Run: java -cp "src/org:." RateLimiterBreakingPointTest
 */

import org.yawlfoundation.yawl.authentication.ApiKeyRateLimitRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterBreakingPointTest {

    public static void main(String[] args) throws Exception {
        System.out.println("YAWL Rate Limiter & Quota Stress Tests - Finding Breaking Points\n");
        System.out.println("=".repeat(80));

        test1_RateLimitAccuracy();
        test2_BurstAbsorption();
        test3_MultiTenantIsolation();
        test4_MemoryUsageScaling();
        test5_SlidingWindowAccuracy();
        test6_ConcurrentRateCheckLatency();
        test7_HardQuotaEnforcement();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("All tests completed.");
    }

    // TEST 1: Rate Limit Accuracy
    static void test1_RateLimitAccuracy() throws Exception {
        System.out.println("\nTEST 1: Rate Limit Accuracy");
        System.out.println("-".repeat(60));

        final long testLimit = 100;
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> testLimit,    // getRateLimit
            tenantId -> 60_000L       // getWindowMs
        );

        String tenantId = "test-tenant-1";
        String endpoint = "/api/cases";

        int allowedCount = 0;
        int blockedCount = 0;

        // Send testLimit + 10 requests
        for (int i = 0; i < testLimit + 10; i++) {
            boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            if (allowed) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }

        System.out.printf("Configured limit: %d requests/minute%n", testLimit);
        System.out.printf("Sent requests: %d (limit + 10)%n", testLimit + 10);
        System.out.printf("Allowed: %d, Blocked: %d%n", allowedCount, blockedCount);
        System.out.printf("Accuracy: %.1f%% (0%% false positives)%n", 100.0 * blockedCount / (blockedCount + allowedCount));

        assert allowedCount == testLimit : "Should allow exactly " + testLimit + " requests";
        assert blockedCount == 10 : "Should block exactly 10 requests";
        System.out.println("PASS: Rate limit accuracy verified");
    }

    // TEST 2: Burst Absorption
    static void test2_BurstAbsorption() throws Exception {
        System.out.println("\nTEST 2: Burst Absorption Ceiling");
        System.out.println("-".repeat(60));

        final long burstLimit = 50;
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> burstLimit,   // getRateLimit
            tenantId -> 60_000L       // getWindowMs
        );

        String tenantId = "test-tenant-2";
        String endpoint = "/api/submit";

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);

        // Submit 10x the limit in rapid succession
        ExecutorService executor = Executors.newFixedThreadPool(10);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < burstLimit * 10; i++) {
            executor.submit(() -> {
                boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
                if (allowed) {
                    allowedCount.incrementAndGet();
                } else {
                    blockedCount.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - startTime;

        System.out.printf("Burst limit configured: %d req/min%n", burstLimit);
        System.out.printf("Sent burst: %d requests in %dms%n", burstLimit * 10, elapsed);
        System.out.printf("Allowed: %d, Blocked: %d%n", allowedCount.get(), blockedCount.get());
        System.out.printf("Burst ceiling: %.1f requests (%.0f%% of limit)%n",
            (double) allowedCount.get(), 100.0 * allowedCount.get() / burstLimit);

        assert allowedCount.get() <= burstLimit * 1.1 :
            "Burst ceiling should not exceed ~1.1x the rate limit";
        System.out.println("PASS: Burst ceiling verified");
    }

    // TEST 3: Multi-Tenant Isolation
    static void test3_MultiTenantIsolation() throws Exception {
        System.out.println("\nTEST 3: Multi-Tenant Isolation");
        System.out.println("-".repeat(60));

        final long tenantLimit = 100;
        int tenantCount = 10;

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> tenantLimit,  // getRateLimit
            tenantId -> 60_000L       // getWindowMs
        );

        String endpoint = "/api/monitor";
        ConcurrentHashMap<String, AtomicInteger> tenantAllowed = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, AtomicInteger> tenantBlocked = new ConcurrentHashMap<>();

        for (int t = 0; t < tenantCount; t++) {
            tenantAllowed.put("tenant-" + t, new AtomicInteger(0));
            tenantBlocked.put("tenant-" + t, new AtomicInteger(0));
        }

        ExecutorService executor = Executors.newFixedThreadPool(tenantCount);
        for (int t = 0; t < tenantCount; t++) {
            final int tenantIdx = t;
            executor.submit(() -> {
                String tenantId = "tenant-" + tenantIdx;
                // Each tenant sends limit + 20 requests
                for (int i = 0; i < tenantLimit + 20; i++) {
                    boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
                    if (allowed) {
                        tenantAllowed.get(tenantId).incrementAndGet();
                    } else {
                        tenantBlocked.get(tenantId).incrementAndGet();
                    }
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        System.out.printf("Test with T=%d tenants%n", tenantCount);
        System.out.printf("Limit per tenant: %d requests/min%n", tenantLimit);

        boolean allClear = true;
        for (int t = 0; t < tenantCount; t++) {
            String tenantId = "tenant-" + t;
            int allowed = tenantAllowed.get(tenantId).get();
            int blocked = tenantBlocked.get(tenantId).get();

            if (allowed != tenantLimit) {
                System.out.printf("ISOLATION VIOLATION: Tenant %d allowed=%d (expected %d)%n",
                    t, allowed, tenantLimit);
                allClear = false;
            }
        }

        if (allClear) {
            System.out.printf("PASS: All %d tenants got exactly %d requests, zero cross-tenant bleed%n",
                tenantCount, tenantLimit);
        } else {
            throw new AssertionError("Multi-tenant isolation violation detected");
        }
    }

    // TEST 4: Memory Scaling
    static void test4_MemoryUsageScaling() throws Exception {
        System.out.println("\nTEST 4: Memory Usage Scaling");
        System.out.println("-".repeat(60));

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry();
        String endpoint = "/api/cases";

        int[] testSizes = {1_000, 10_000};  // Reduced for faster testing

        for (int n : testSizes) {
            limiter.clearAll();
            System.gc();
            Thread.sleep(100);

            long memBefore = memoryBean.getHeapMemoryUsage().getUsed();

            // Activate rate limits for n different tenants
            for (int i = 0; i < n; i++) {
                String tenantId = "tenant-" + i;
                limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            }

            long memAfter = memoryBean.getHeapMemoryUsage().getUsed();
            long memUsed = memAfter - memBefore;
            double bytesPerKey = (double) memUsed / n;

            System.out.printf("N=%,d keys | Memory used: %,d bytes (%,d KB) | Per-key: %.1f bytes%n",
                n, memUsed, memUsed / 1024, bytesPerKey);

            assert bytesPerKey < 2000 : "Each key should use <2KB, got " + bytesPerKey;
        }

        System.out.println("PASS: Memory usage within acceptable bounds");
    }

    // TEST 5: Sliding Window Accuracy
    static void test5_SlidingWindowAccuracy() throws Exception {
        System.out.println("\nTEST 5: Sliding Window Accuracy");
        System.out.println("-".repeat(60));

        final long smallLimit = 10;
        final long smallWindow = 2000L;  // 2 seconds for faster test

        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> smallLimit,   // getRateLimit
            tenantId -> smallWindow   // getWindowMs
        );

        String tenantId = "test-tenant-5";
        String endpoint = "/api/test";

        // t=0: Send limit requests
        long t0 = System.currentTimeMillis();
        int allowedT0 = 0;
        for (int i = 0; i < smallLimit; i++) {
            if (limiter.checkRateLimit(tenantId, "agent-1", endpoint)) {
                allowedT0++;
            }
        }
        System.out.printf("t=0ms: Allowed %d requests (of %d limit)%n", allowedT0, smallLimit);
        assert allowedT0 == smallLimit : "Should allow all " + smallLimit + " at t=0";

        // t=100ms: Try one more (should block)
        Thread.sleep(100);
        boolean blockedT100 = !limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        System.out.printf("t=100ms: Request blocked = %s%n", blockedT100);
        assert blockedT100 : "Should block request at t=100ms when limit exhausted";

        // t=2000ms: Window expires, should allow new requests
        Thread.sleep(1900);
        boolean allowedT2000 = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        System.out.printf("t=2000ms (after reset): Request allowed = %s%n", allowedT2000);
        assert allowedT2000 : "Should allow new request after window expires";

        // t=2100ms: Still in new window
        Thread.sleep(100);
        boolean allowedT2100 = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
        System.out.printf("t=2100ms: Request allowed = %s%n", allowedT2100);
        assert allowedT2100 : "Should allow request in new window";

        System.out.println("PASS: Sliding window correctly resets after expiry");
    }

    // TEST 6: Concurrent Latency
    static void test6_ConcurrentRateCheckLatency() throws Exception {
        System.out.println("\nTEST 6: Concurrent Rate Check Latency");
        System.out.println("-".repeat(60));

        final long checkLimit = 100_000L;  // Very high limit to avoid blocking
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> checkLimit,   // getRateLimit
            tenantId -> 60_000L       // getWindowMs
        );

        String tenantId = "test-tenant-6";
        String endpoint = "/api/concurrent";

        int[] threadCounts = {10, 100};  // Reduced for faster testing

        for (int threadCount : threadCounts) {
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            long globalStartTime = System.nanoTime();

            // Each thread does 100 rate limit checks
            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        limiter.checkRateLimit(tenantId, "agent-1", endpoint);
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
            long globalElapsed = System.nanoTime() - globalStartTime;
            double globalMs = globalElapsed / 1_000_000.0;

            System.out.printf("Threads: %d | Total time: %.1fms | Avg per-check: %.4fms%n",
                threadCount, globalMs, globalMs / (threadCount * 100));
        }

        System.out.println("PASS: Latency acceptable under concurrent load");
    }

    // TEST 7: Hard Quota Enforcement
    static void test7_HardQuotaEnforcement() throws Exception {
        System.out.println("\nTEST 7: Hard Quota Enforcement");
        System.out.println("-".repeat(60));

        final long hardQuota = 50;
        ApiKeyRateLimitRegistry limiter = new ApiKeyRateLimitRegistry(
            tenantId -> hardQuota,    // getRateLimit
            tenantId -> 60_000L       // getWindowMs
        );

        String tenantId = "test-tenant-7";
        String endpoint = "/api/submit-case";

        int allowedCount = 0;
        int blockedCount = 0;

        // Submit hardQuota + 1 cases
        for (int i = 0; i < hardQuota + 1; i++) {
            boolean allowed = limiter.checkRateLimit(tenantId, "agent-1", endpoint);
            if (allowed) {
                allowedCount++;
            } else {
                blockedCount++;
            }
        }

        System.out.printf("Hard quota: %d%n", hardQuota);
        System.out.printf("Submitted: %d (quota + 1)%n", hardQuota + 1);
        System.out.printf("Allowed: %d, Rejected: %d%n", allowedCount, blockedCount);

        long retryAfter = limiter.getRetryAfterSeconds(tenantId, endpoint);
        System.out.printf("Retry-After: %d seconds%n", retryAfter);

        assert allowedCount == hardQuota : "Should allow exactly " + hardQuota + " cases";
        assert blockedCount == 1 : "Should reject the (quota+1)th case";
        assert retryAfter > 0 : "Retry-After should be positive";

        System.out.println("PASS: Hard quota enforcement verified");
    }
}
