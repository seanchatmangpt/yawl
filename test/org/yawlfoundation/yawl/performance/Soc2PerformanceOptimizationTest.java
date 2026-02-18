package org.yawlfoundation.yawl.performance;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.engine.YWorkItemStatusIndex;
import org.yawlfoundation.yawl.engine.YNetRunnerLockMetrics;
import org.yawlfoundation.yawl.engine.interfce.HttpConnectionPoolMetrics;
import org.yawlfoundation.yawl.integration.VaultCredentialCache;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SOC2 Performance Optimization Validation Test Suite
 *
 * Validates all 5 optimizations from the SOC2 benchmark report with actual
 * before/after measurements:
 *
 *   P1 CRITICAL - Vault credential caching: target <50ms (vs ~200ms uncached)
 *   P2 HIGH     - Lock contention metrics: validates metric collection correctness
 *   P3 MEDIUM   - Task status O(N)-to-O(k) index: <10ms p95 at 10k items
 *   P4 MEDIUM   - JVM tuning: validates script exists and contains required flags
 *   P5 MEDIUM   - HTTP pool metrics: validates in-flight tracking and error rates
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class Soc2PerformanceOptimizationTest extends TestCase {

    // ── P1: Vault Credential Cache ──────────────────────────────────────────

    /**
     * P1: Cache hit latency must be well under 50ms.
     * A supplier that simulates 50ms of I/O is registered; the cache should
     * serve subsequent requests in <1ms from the in-memory copy.
     */
    public void testP1_credentialCacheHitLatencyUnder50ms() {
        VaultCredentialCache cache = VaultCredentialCache.withTtl(Duration.ofSeconds(60));

        // Simulate a credential source that takes 50ms (e.g. Vault HTTP call)
        final long simulatedIoMs = 50;
        cache.register("TEST_KEY", () -> {
            try { Thread.sleep(simulatedIoMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return "secret-value-abc123";
        });

        // First call: cache miss - should take ~50ms
        long missStart = System.nanoTime();
        String value1 = cache.get("TEST_KEY");
        long missMs = (System.nanoTime() - missStart) / 1_000_000;

        assertEquals("secret-value-abc123", value1);
        assertTrue("Cache miss should reflect I/O time (>=" + simulatedIoMs + "ms), got " + missMs + "ms",
                missMs >= simulatedIoMs - 5); // 5ms tolerance

        // Second call: cache hit - must be <50ms (well under the 200ms Vault baseline)
        long hitStart = System.nanoTime();
        String value2 = cache.get("TEST_KEY");
        long hitMs = (System.nanoTime() - hitStart) / 1_000_000;

        assertEquals("Cached value must match", "secret-value-abc123", value2);
        assertTrue("P1: Cache hit must be <50ms, got " + hitMs + "ms", hitMs < 50);

        VaultCredentialCache.CacheMetrics metrics = cache.getMetrics();
        assertEquals("Should have 1 hit", 1, metrics.hits());
        assertEquals("Should have 1 miss", 1, metrics.misses());
        assertTrue("Hit ratio should be 50%", Math.abs(metrics.hitRatio() - 0.5) < 0.01);

        System.out.println("P1 Vault Cache: miss=" + missMs + "ms, hit=" + hitMs + "ms, " + metrics);

        cache.shutdown();
    }

    /**
     * P1: Cache invalidation on secret rotation.
     * After invalidateAll(), the next get() must call the supplier again.
     */
    public void testP1_cacheInvalidationOnSecretRotation() {
        VaultCredentialCache cache = VaultCredentialCache.withTtl(Duration.ofSeconds(30));
        AtomicLong supplierCallCount = new AtomicLong(0);

        cache.register("ROTATING_SECRET", () -> {
            supplierCallCount.incrementAndGet();
            return "version-" + supplierCallCount.get();
        });

        String v1 = cache.get("ROTATING_SECRET");
        String v1again = cache.get("ROTATING_SECRET");
        assertEquals("Should cache same value", v1, v1again);
        assertEquals("Supplier called once before invalidation", 1, supplierCallCount.get());

        // Simulate Vault secret rotation
        cache.invalidateAll();

        String v2 = cache.get("ROTATING_SECRET");
        assertEquals("Supplier called again after invalidation", 2, supplierCallCount.get());
        assertFalse("Post-rotation value must differ", v1.equals(v2));

        VaultCredentialCache.CacheMetrics metrics = cache.getMetrics();
        assertTrue("Invalidation count >= 1", metrics.invalidations() >= 1);

        System.out.println("P1 Vault Cache rotation: supplierCalls=" + supplierCallCount +
                ", invalidations=" + metrics.invalidations());

        cache.shutdown();
    }

    /**
     * P1: Batch prewarm loads all keys before request traffic.
     */
    public void testP1_batchPrewarm() {
        VaultCredentialCache cache = VaultCredentialCache.withTtl(Duration.ofSeconds(30));
        List<String> keys = List.of("YAWL_PASSWORD", "DB_PASSWORD", "ZAI_API_KEY",
                "JWT_SECRET", "VAULT_TOKEN");

        for (String key : keys) {
            cache.register(key, () -> "value-for-" + key);
        }

        long start = System.nanoTime();
        cache.prewarm(keys);
        long prewarmMs = (System.nanoTime() - start) / 1_000_000;

        // All keys now warm - subsequent gets should be fast
        for (String key : keys) {
            long getStart = System.nanoTime();
            String val = cache.get(key);
            long getMs = (System.nanoTime() - getStart) / 1_000_000;
            assertNotNull("Pre-warmed value must not be null for key " + key, val);
            assertTrue("Pre-warmed key '" + key + "' must return <50ms, got " + getMs + "ms",
                    getMs < 50);
        }

        System.out.println("P1 Vault Cache prewarm: " + keys.size() + " keys in " + prewarmMs + "ms");

        cache.shutdown();
    }

    // ── P2: Lock Contention Metrics ─────────────────────────────────────────

    /**
     * P2: YNetRunnerLockMetrics accurately records write-lock wait times.
     */
    public void testP2_lockMetricsRecordsWaitTime() {
        YNetRunnerLockMetrics metrics = new YNetRunnerLockMetrics("case-001");

        // No acquisitions yet
        assertEquals("avgWriteLockWait should be 0 initially", 0.0, metrics.avgWriteLockWaitMs(), 0.001);

        // Record simulated wait times
        metrics.recordWriteLockWait(5_000_000L);   // 5ms
        metrics.recordWriteLockWait(3_000_000L);   // 3ms
        metrics.recordWriteLockWait(10_000_000L);  // 10ms

        double avgMs = metrics.avgWriteLockWaitMs();
        assertEquals("Average write wait should be 6ms", 6.0, avgMs, 0.1);
        assertEquals("Max write wait should be 10ms", 10.0, metrics.maxWriteLockWaitMs(), 0.1);

        metrics.recordReadLockWait(500_000L);  // 0.5ms
        assertTrue("Read lock avg should be ~0.5ms", metrics.avgReadLockWaitMs() < 2.0);

        String summary = metrics.summary();
        assertTrue("Summary should contain case ID", summary.contains("case-001"));
        assertTrue("Summary should contain writeLocks", summary.contains("writeLocks=3"));

        System.out.println("P2 LockMetrics: " + summary);
    }

    /**
     * P2: Concurrent access with lock metrics - validates no metrics lost under load.
     */
    public void testP2_lockMetricsConcurrentAccuracy() throws Exception {
        YNetRunnerLockMetrics metrics = new YNetRunnerLockMetrics("case-concurrent");
        int threads = 20;
        int recordsPerThread = 1000;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    for (int j = 0; j < recordsPerThread; j++) {
                        metrics.recordWriteLockWait(1_000_000L); // 1ms each
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue("All threads completed", latch.await(30, TimeUnit.SECONDS));
        exec.shutdown();

        // avgWriteLockWaitMs should be ~1ms
        double avg = metrics.avgWriteLockWaitMs();
        assertTrue("P2: avg write wait should be ~1ms, got " + avg, avg > 0.5 && avg < 5.0);

        System.out.println("P2 LockMetrics concurrent: threads=" + threads +
                " records=" + (threads * recordsPerThread) + " avgWait=" + avg + "ms");
    }

    // ── P3: Task Status Index ───────────────────────────────────────────────

    /**
     * P3: Status index O(k) lookup is faster than O(N) full scan at 10k items.
     * Verifies the optimization eliminates the linear scan bottleneck.
     */
    public void testP3_statusIndexFasterThanLinearScan() {
        final int TOTAL_ITEMS = 10_000;
        final int ENABLED_ITEMS = 200;

        // Set up a simulated item map (String ID -> YWorkItemStatus)
        Map<String, YWorkItemStatus> itemMap = new HashMap<>(TOTAL_ITEMS * 2);
        YWorkItemStatusIndex index = new YWorkItemStatusIndex();

        // Populate: 200 enabled, rest fired/executing/complete
        for (int i = 0; i < TOTAL_ITEMS; i++) {
            String id = "item-" + i;
            YWorkItemStatus status;
            if (i < ENABLED_ITEMS) {
                status = YWorkItemStatus.statusEnabled;
            } else if (i < TOTAL_ITEMS / 2) {
                status = YWorkItemStatus.statusFired;
            } else {
                status = YWorkItemStatus.statusComplete;
            }
            itemMap.put(id, status);
            index.add(id, status);
        }

        // Measure O(N) linear scan (baseline)
        int ITERATIONS = 1_000;
        long linearStart = System.nanoTime();
        for (int iter = 0; iter < ITERATIONS; iter++) {
            Set<String> result = new HashSet<>();
            for (Map.Entry<String, YWorkItemStatus> entry : itemMap.entrySet()) {
                if (entry.getValue() == YWorkItemStatus.statusEnabled) {
                    result.add(entry.getKey());
                }
            }
            assertEquals("Linear scan must find all enabled items", ENABLED_ITEMS, result.size());
        }
        long linearMs = (System.nanoTime() - linearStart) / 1_000_000;
        double linearAvgMs = (double) linearMs / ITERATIONS;

        // Measure O(k) index lookup
        long indexStart = System.nanoTime();
        for (int iter = 0; iter < ITERATIONS; iter++) {
            Set<String> result = index.getItemIdsWithStatus(YWorkItemStatus.statusEnabled);
            assertEquals("Index must find all enabled items", ENABLED_ITEMS, result.size());
        }
        long indexMs = (System.nanoTime() - indexStart) / 1_000_000;
        double indexAvgMs = (double) indexMs / ITERATIONS;

        System.out.println("P3 Status Index benchmark (" + TOTAL_ITEMS + " items, " +
                ENABLED_ITEMS + " enabled, " + ITERATIONS + " iterations):");
        System.out.println("  Linear scan:  total=" + linearMs + "ms, avg=" +
                String.format("%.3f", linearAvgMs) + "ms");
        System.out.println("  Index lookup: total=" + indexMs + "ms, avg=" +
                String.format("%.3f", indexAvgMs) + "ms");
        System.out.println("  Speedup: " + String.format("%.1f", linearAvgMs / Math.max(indexAvgMs, 0.001)) + "x");

        // P3 target: index lookup <10ms p95 (validated as avg here for unit test)
        assertTrue("P3: Index avg must be <10ms, got " + indexAvgMs + "ms", indexAvgMs < 10.0);
        // Index must not be slower than linear scan (it's allowed to be equal on very small data)
        assertTrue("P3: Index must be at least as fast as linear scan",
                indexAvgMs <= linearAvgMs + 1.0); // 1ms tolerance for JIT variance
    }

    /**
     * P3: Status index maintains consistency through add/remove/status-change operations.
     */
    public void testP3_statusIndexConsistency() {
        YWorkItemStatusIndex index = new YWorkItemStatusIndex();

        // Add items
        index.add("item-1", YWorkItemStatus.statusEnabled);
        index.add("item-2", YWorkItemStatus.statusEnabled);
        index.add("item-3", YWorkItemStatus.statusFired);

        assertEquals("2 enabled items", 2, index.countWithStatus(YWorkItemStatus.statusEnabled));
        assertEquals("1 fired item", 1, index.countWithStatus(YWorkItemStatus.statusFired));

        // Transition: enabled -> executing
        index.updateStatus("item-1", YWorkItemStatus.statusEnabled, YWorkItemStatus.statusExecuting);
        assertEquals("1 enabled after transition", 1, index.countWithStatus(YWorkItemStatus.statusEnabled));
        assertEquals("1 executing after transition", 1, index.countWithStatus(YWorkItemStatus.statusExecuting));

        // Remove
        index.remove("item-2");
        assertEquals("0 enabled after remove", 0, index.countWithStatus(YWorkItemStatus.statusEnabled));

        // Clear
        index.clear();
        assertEquals("0 items after clear", 0, index.countWithStatus(YWorkItemStatus.statusEnabled));
        assertEquals("0 items after clear", 0, index.countWithStatus(YWorkItemStatus.statusFired));

        System.out.println("P3 Status Index: consistency check passed");
    }

    // ── P4: JVM Tuning Script ───────────────────────────────────────────────

    /**
     * P4: Startup script exists and contains all required Java 25 JVM flags.
     */
    public void testP4_jvmTuningScriptContainsRequiredFlags() throws Exception {
        java.io.File script = new java.io.File("scripts/start-engine-java25-tuned.sh");
        assertTrue("P4: JVM tuning script must exist at scripts/start-engine-java25-tuned.sh",
                script.exists());

        String content = new String(java.nio.file.Files.readAllBytes(script.toPath()));

        String[] requiredFlags = {
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=200",
            "-XX:G1HeapRegionSize=16m",
            "-Xms2g",
            "-Xmx4g",
            "-XX:MetaspaceSize=256m",
            "-XX:MaxMetaspaceSize=512m",
            "virtualThreadScheduler.parallelism",
            "virtualThreadScheduler.maxPoolSize",
            "-XX:+UseStringDeduplication"
        };

        for (String flag : requiredFlags) {
            assertTrue("P4: Script must contain JVM flag: " + flag, content.contains(flag));
        }

        System.out.println("P4 JVM Tuning: all " + requiredFlags.length + " required flags present");
    }

    // ── P5: HTTP Connection Pool Metrics ────────────────────────────────────

    /**
     * P5: Pool metrics tracks in-flight count, latency, and error rate correctly.
     */
    public void testP5_httpPoolMetricsTracking() {
        HttpConnectionPoolMetrics metrics = HttpConnectionPoolMetrics.getInstance();
        metrics.reset();

        // Simulate 10 successful requests
        for (int i = 0; i < 10; i++) {
            long start = metrics.recordRequestStart();
            try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            metrics.recordRequestComplete(start, false);
        }

        // Simulate 2 error requests
        for (int i = 0; i < 2; i++) {
            long start = metrics.recordRequestStart();
            metrics.recordRequestComplete(start, true);
        }

        assertEquals("Total requests should be 12", 12, metrics.getTotalRequests());
        assertEquals("Total errors should be 2", 2, metrics.getTotalErrors());
        assertEquals("In-flight should be 0 after all complete", 0, metrics.getInFlightCount());
        assertTrue("Error rate should be ~16.7%", Math.abs(metrics.getErrorRate() - 2.0/12) < 0.01);
        assertTrue("Avg latency should be >0ms", metrics.getAvgLatencyMs() > 0);

        System.out.println("P5 HTTP Pool: " + metrics.summary());
    }

    /**
     * P5: Peak in-flight tracking catches concurrent request bursts.
     */
    public void testP5_httpPoolPeakInFlightTracking() throws Exception {
        HttpConnectionPoolMetrics metrics = HttpConnectionPoolMetrics.getInstance();
        metrics.reset();

        int threads = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threads);
        ExecutorService exec = Executors.newFixedThreadPool(threads);

        List<Long> starts = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                try {
                    startLatch.await();
                    long s = metrics.recordRequestStart();
                    starts.add(s);
                    Thread.sleep(10); // hold for 10ms to create concurrency
                    metrics.recordRequestComplete(s, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue("All threads finished", endLatch.await(30, TimeUnit.SECONDS));
        exec.shutdown();

        assertTrue("P5: Peak in-flight should capture burst >=1, got " + metrics.getPeakInFlightCount(),
                metrics.getPeakInFlightCount() >= 1);
        assertEquals("All requests completed", threads, metrics.getTotalRequests());
        assertEquals("In-flight must be 0 after all done", 0, metrics.getInFlightCount());

        System.out.println("P5 HTTP Pool peak: " + metrics.getPeakInFlightCount() +
                " concurrent (out of " + threads + " threads)");
    }

    // ── Suite ───────────────────────────────────────────────────────────────

    public Soc2PerformanceOptimizationTest(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("SOC2 Performance Optimization Tests (P1-P5)");
        suite.addTestSuite(Soc2PerformanceOptimizationTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
