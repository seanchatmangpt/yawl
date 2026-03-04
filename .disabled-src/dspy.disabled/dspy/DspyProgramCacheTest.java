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

package org.yawlfoundation.yawl.dspy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for DspyProgramCache.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>LRU eviction when cache is full</li>
 *   <li>Thread-safe concurrent access</li>
 *   <li>Cache hit/miss detection</li>
 *   <li>Cache clear and snapshot operations</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("DspyProgramCache Tests")
public class DspyProgramCacheTest {

    private DspyProgramCache cache;

    @BeforeEach
    void setUp() {
        cache = new DspyProgramCache(5);  // Small cache for testing eviction
    }

    @Test
    @DisplayName("Should store and retrieve entries")
    void testBasicPutGet() {
        // Act
        cache.put("program1", "compiled_var_1");
        String retrieved = cache.get("program1");

        // Assert
        assertThat(retrieved, equalTo("compiled_var_1"));
    }

    @Test
    @DisplayName("Should return null for missing entries")
    void testGetMissingEntry() {
        // Act
        String result = cache.get("nonexistent");

        // Assert
        assertThat(result, nullValue());
    }

    @Test
    @DisplayName("Should detect cache hits and misses")
    void testContains() {
        // Arrange
        cache.put("existing", "var");

        // Act & Assert
        assertThat(cache.contains("existing"), is(true));
        assertThat(cache.contains("missing"), is(false));
    }

    @Test
    @DisplayName("Should report correct cache size")
    void testSize() {
        // Arrange
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");

        // Act
        int size = cache.size();

        // Assert
        assertThat(size, equalTo(3));
    }

    @Test
    @DisplayName("Should evict least-recently-used entry when full")
    void testLruEviction() {
        // Arrange: Fill cache to capacity
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");
        cache.put("key4", "var4");
        cache.put("key5", "var5");
        assertThat(cache.size(), equalTo(5));

        // Act: Access key2 (makes it most recently used)
        cache.get("key2");

        // Add a new entry (should evict key1, the least recently used)
        cache.put("key6", "var6");

        // Assert
        assertThat(cache.size(), equalTo(5));
        assertThat(cache.contains("key1"), is(false));  // Evicted
        assertThat(cache.contains("key2"), is(true));   // Still present
        assertThat(cache.contains("key6"), is(true));   // New entry
    }

    @Test
    @DisplayName("Should update LRU order on access")
    void testLruOrderUpdate() {
        // Arrange: Fill cache
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");
        cache.put("key4", "var4");
        cache.put("key5", "var5");

        // Act: Access key1 (most recently used now)
        cache.get("key1");

        // Add new entry (should evict key2, not key1)
        cache.put("key6", "var6");

        // Assert
        assertThat(cache.contains("key1"), is(true));   // Still present
        assertThat(cache.contains("key2"), is(false));  // Evicted (was least recently used)
        assertThat(cache.contains("key6"), is(true));
    }

    @Test
    @DisplayName("Should clear all entries")
    void testClear() {
        // Arrange
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");
        assertThat(cache.size(), equalTo(3));

        // Act
        cache.clear();

        // Assert
        assertThat(cache.size(), equalTo(0));
        assertThat(cache.contains("key1"), is(false));
    }

    @Test
    @DisplayName("Should return cache snapshot")
    void testSnapshot() {
        // Arrange
        cache.put("key1", "var1");
        cache.put("key2", "var2");

        // Act
        Map<String, String> snapshot = cache.snapshot();

        // Assert
        assertThat(snapshot, notNullValue());
        assertThat(snapshot.size(), equalTo(2));
        assertThat(snapshot.get("key1"), equalTo("var1"));
        assertThat(snapshot.get("key2"), equalTo("var2"));
    }

    @Test
    @DisplayName("Should return max size configuration")
    void testMaxSize() {
        // Act
        int maxSize = cache.maxSize();

        // Assert
        assertThat(maxSize, equalTo(5));
    }

    @Test
    @DisplayName("Should support custom max size")
    void testCustomMaxSize() {
        // Arrange
        DspyProgramCache customCache = new DspyProgramCache(100);

        // Act
        int maxSize = customCache.maxSize();

        // Assert
        assertThat(maxSize, equalTo(100));
    }

    @Test
    @DisplayName("Should reject non-positive max size")
    void testInvalidMaxSize() {
        // Act & Assert
        try {
            new DspyProgramCache(0);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("positive"));
        }
    }

    @Test
    @DisplayName("Should reject negative max size")
    void testNegativeMaxSize() {
        // Act & Assert
        try {
            new DspyProgramCache(-1);
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("positive"));
        }
    }

    @Test
    @DisplayName("Should reject null cache key")
    void testNullKeyRejection() {
        // Act & Assert
        try {
            cache.put(null, "var");
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), containsString("must not be null"));
        }
    }

    @Test
    @DisplayName("Should reject null compiled module")
    void testNullModuleRejection() {
        // Act & Assert
        try {
            cache.put("key", null);
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertThat(e.getMessage(), containsString("must not be null"));
        }
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent puts")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentPuts() throws InterruptedException {
        // Arrange
        int threadCount = 10;
        int operationsPerThread = 20;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // Act: Start threads that concurrently put entries
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        cache.put("key_" + threadId + "_" + i, "var_" + threadId + "_" + i);
                    }
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            threads[t].start();
        }

        // Wait for all threads
        latch.await();

        // Assert: All threads succeeded
        assertThat(successCount.get(), equalTo(threadCount));
        // Cache is full (max 5), so we can't assert exact size
        assertThat(cache.size(), lessThanOrEqualTo(5));
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent gets")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentGets() throws InterruptedException {
        // Arrange: Pre-populate cache
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");

        int threadCount = 10;
        int operationsPerThread = 50;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger hitCount = new AtomicInteger(0);

        // Act: Start threads that concurrently read
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        String result = cache.get("key1");
                        if (result != null) {
                            hitCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
            threads[t].start();
        }

        latch.await();

        // Assert: All reads succeeded
        assertThat(hitCount.get(), equalTo(threadCount * operationsPerThread));
    }

    @Test
    @DisplayName("Should be thread-safe for mixed operations")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentMixedOperations() throws InterruptedException {
        // Arrange
        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Act: Mix of puts and gets
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        cache.put("key_" + threadId, "var_" + i);
                        cache.get("key_" + ((threadId - 1 + threadCount) % threadCount));
                        cache.contains("key_" + threadId);
                    }
                } finally {
                    latch.countDown();
                }
            });
            threads[t].start();
        }

        latch.await();

        // Assert: Cache is in valid state
        assertThat(cache.size(), greaterThan(0));
        assertThat(cache.size(), lessThanOrEqualTo(5));
    }

    @Test
    @DisplayName("Should generate cache statistics")
    void testStats() {
        // Arrange
        cache.put("key1", "var1");
        cache.put("key2", "var2");

        // Act
        String stats = cache.stats();

        // Assert
        assertThat(stats, notNullValue());
        assertThat(stats, containsString("size=2"));
        assertThat(stats, containsString("maxSize=5"));
    }

    @Test
    @DisplayName("Should handle repeated puts (replace)")
    void testReplace() {
        // Arrange
        cache.put("key", "var1");

        // Act
        cache.put("key", "var2");
        String result = cache.get("key");

        // Assert
        assertThat(result, equalTo("var2"));
        assertThat(cache.size(), equalTo(1));
    }

    @Test
    @DisplayName("Should maintain insertion order within LRU semantics")
    void testInsertionOrderWithLru() {
        // Arrange & Act
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");

        Map<String, String> snapshot1 = cache.snapshot();

        // Access key1 (most recent now)
        cache.get("key1");
        cache.put("key4", "var4");
        cache.put("key5", "var5");

        // Add one more to trigger eviction
        cache.put("key6", "var6");

        Map<String, String> snapshot2 = cache.snapshot();

        // Assert: key1 should still be there (was accessed), key2 should be evicted
        assertThat(snapshot2.containsKey("key1"), is(true));
        assertThat(snapshot2.containsKey("key2"), is(false));
    }

    @Test
    @DisplayName("Should return consistent snapshot")
    void testSnapshotConsistency() {
        // Arrange
        cache.put("key1", "var1");
        cache.put("key2", "var2");
        cache.put("key3", "var3");

        // Act
        Map<String, String> snapshot1 = cache.snapshot();
        Map<String, String> snapshot2 = cache.snapshot();

        // Assert
        assertThat(snapshot1, equalTo(snapshot2));
        assertThat(snapshot1.size(), equalTo(3));
    }

    @Test
    @DisplayName("Should allow clearing and reusing")
    void testClearAndReuse() {
        // Arrange
        cache.put("key1", "var1");
        cache.clear();

        // Act
        cache.put("key2", "var2");
        String result = cache.get("key2");

        // Assert
        assertThat(result, equalTo("var2"));
        assertThat(cache.size(), equalTo(1));
        assertThat(cache.contains("key1"), is(false));
    }
}
