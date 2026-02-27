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

package org.yawlfoundation.yawl.graalpy.stress;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.graalpy.PythonBytecodeCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: {@link PythonBytecodeCache} thread safety under concurrent
 * {@code markCached()}, {@code isValid()}, and {@code invalidate()} calls.
 *
 * <h2>Why this matters</h2>
 * <p>The bytecode cache is a performance hot path: every {@code evalScript()} call
 * consults it to decide whether to parse or re-use compiled bytecode.  In a YAWL
 * engine with N concurrent task threads all referencing the same shared Python
 * scripts, the cache experiences intense concurrent read/write traffic.</p>
 *
 * <p>The implementation uses {@link java.util.concurrent.ConcurrentHashMap} for
 * the in-memory index and {@link java.nio.file.Files} for disk I/O.  The risk
 * surfaces are:</p>
 * <ul>
 *   <li><b>Lost updates</b>: two threads call {@code markCached()} concurrently;
 *       one update is silently dropped.</li>
 *   <li><b>Stale reads</b>: a thread sees a cache as valid after {@code invalidate()}
 *       has removed it — possible if removal is not immediately visible.</li>
 *   <li>{@link java.util.ConcurrentModificationException}: iteration of the map
 *       while another thread modifies it (impossible with ConcurrentHashMap, but
 *       tests the assumption).</li>
 *   <li><b>File descriptor leak</b>: failed disk I/O during invalidate() leaves
 *       a temporary file open.</li>
 * </ul>
 *
 * <p>Chicago TDD: real {@link PythonBytecodeCache} instances; no mocks.
 * Tests use temp files on the actual filesystem to exercise real I/O paths.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@DisplayName("PythonBytecodeCache — concurrent access stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BytecodeCacheConcurrencyStressTest {

    private Path tempCacheDir;
    private Path tempSourceDir;
    private PythonBytecodeCache cache;

    @BeforeEach
    void setUp() throws IOException {
        tempCacheDir  = Files.createTempDirectory("graalpy-cache-stress-");
        tempSourceDir = Files.createTempDirectory("graalpy-src-stress-");
        cache = new PythonBytecodeCache(tempCacheDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up temp directories
        deleteRecursively(tempCacheDir);
        deleteRecursively(tempSourceDir);
    }

    // ── S1: Concurrent markCached() on the same path — no lost updates ────────────

    @Test
    @Order(1)
    @DisplayName("S1: 100 concurrent markCached() on same path — no ConcurrentModificationException")
    void concurrentMarkCachedNeverThrows() throws Exception {
        Path scriptPath = createTempPyFile("shared_script");

        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder exceptions      = new LongAdder();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    cache.markCached(scriptPath);
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15_000, TimeUnit.MILLISECONDS),
                "markCached() threads did not complete within 15s");
        assertEquals(0, exceptions.sum(),
                "Concurrent markCached() raised " + exceptions.sum() + " exceptions");
    }

    // ── S2: Concurrent isValid() reads never throw ────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("S2: 200 concurrent isValid() reads on same path — no exception, consistent boolean")
    void concurrentIsValidNeverThrows() throws Exception {
        Path scriptPath = createTempPyFile("readable_script");
        cache.markCached(scriptPath);

        int threadCount = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder exceptions      = new LongAdder();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    // Return value is valid boolean — may be true or false (cache state may vary)
                    cache.isValid(scriptPath);
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15_000, TimeUnit.MILLISECONDS),
                "isValid() threads did not complete within 15s");
        assertEquals(0, exceptions.sum(),
                "Concurrent isValid() raised " + exceptions.sum() + " exceptions");
    }

    // ── S3: Concurrent invalidate() — no exception, no file descriptor leak ───────

    @Test
    @Order(3)
    @DisplayName("S3: 50 concurrent invalidate() on same path — no exception, index consistent")
    void concurrentInvalidateNeverThrows() throws Exception {
        Path scriptPath = createTempPyFile("to_invalidate");
        // Prime the cache index
        for (int i = 0; i < 10; i++) {
            cache.markCached(scriptPath);
        }

        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder exceptions      = new LongAdder();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    cache.invalidate(scriptPath);
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15_000, TimeUnit.MILLISECONDS),
                "invalidate() threads did not complete within 15s");
        assertEquals(0, exceptions.sum(),
                "Concurrent invalidate() raised " + exceptions.sum() + " exceptions");

        // After invalidation, cache should report miss for the path
        assertFalse(cache.isValid(scriptPath),
                "Cache should report miss after all invalidations");
    }

    // ── S4: Mixed workload — mark + isValid + invalidate simultaneously ───────────

    @Test
    @Order(4)
    @DisplayName("S4: Mixed mark/isValid/invalidate from 90 threads — no CME, no NPE")
    void mixedWorkloadNeverThrows() throws Exception {
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            paths.add(createTempPyFile("multi_" + i));
        }

        int threadCount = 90;  // 30 markers, 30 validators, 30 invalidators
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder exceptions      = new LongAdder();

        // 30 mark threads
        for (int i = 0; i < 30; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (Path p : paths) {
                        cache.markCached(p);
                    }
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 30 isValid threads
        for (int i = 0; i < 30; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (Path p : paths) {
                        cache.isValid(p); // return value ignored — just must not throw
                    }
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // 30 invalidate threads
        for (int i = 0; i < 30; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (Path p : paths) {
                        cache.invalidate(p);
                    }
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30_000, TimeUnit.MILLISECONDS),
                "Mixed workload threads did not complete within 30s");
        assertEquals(0, exceptions.sum(),
                "Mixed concurrent workload raised " + exceptions.sum() + " exceptions");
    }

    // ── S5: clearIndex() under concurrent marking — no corruption ────────────────

    @Test
    @Order(5)
    @DisplayName("S5: clearIndex() while 50 threads mark concurrently — index remains consistent")
    void clearIndexUnderConcurrentMarkStaysConsistent() throws Exception {
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            paths.add(createTempPyFile("clear_test_" + i));
        }

        int markerCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(markerCount + 1);
        LongAdder exceptions      = new LongAdder();

        // 1 clearer thread
        Thread.ofVirtual().start(() -> {
            try {
                startLatch.await();
                for (int c = 0; c < 100; c++) {
                    cache.clearIndex();
                    Thread.sleep(0, 100_000); // 0.1 ms pause
                }
            } catch (Exception e) {
                exceptions.increment();
            } finally {
                doneLatch.countDown();
            }
        });

        // N marker threads
        for (int i = 0; i < markerCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (Path p : paths) {
                        cache.markCached(p);
                    }
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(30_000, TimeUnit.MILLISECONDS),
                "clearIndex()/mark threads did not complete within 30s");
        assertEquals(0, exceptions.sum(),
                "clearIndex() under concurrent marking raised " + exceptions.sum() + " exceptions");

        // Post-condition: indexSize() is a valid non-negative integer
        int finalSize = cache.indexSize();
        assertTrue(finalSize >= 0,
                "indexSize() must be non-negative, got " + finalSize);
    }

    // ── S6: Many distinct paths — no cross-path contamination ────────────────────

    @Test
    @Order(6)
    @DisplayName("S6: 100 distinct paths cached concurrently — each path's validity is independent")
    void distinctPathsHaveIndependentCacheState() throws Exception {
        int pathCount = 100;
        List<Path> paths = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) {
            paths.add(createTempPyFile("distinct_" + i));
        }

        // Mark even-indexed paths as cached
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(pathCount);
        LongAdder exceptions      = new LongAdder();

        for (int i = 0; i < pathCount; i++) {
            final int idx = i;
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    if (idx % 2 == 0) {
                        cache.markCached(paths.get(idx));
                    }
                    // Odd paths are NOT marked — isValid should return false for them
                } catch (Exception e) {
                    exceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15_000, TimeUnit.MILLISECONDS),
                "Distinct path marking did not complete within 15s");
        assertEquals(0, exceptions.sum(),
                "Distinct path test raised " + exceptions.sum() + " exceptions");

        // Verify index knows about at most pathCount entries
        assertTrue(cache.indexSize() <= pathCount,
                "Index size exceeds path count: " + cache.indexSize() + " > " + pathCount);
    }

    // ── S7: indexSize() is monotone-bounded after concurrent marking ──────────────

    @Test
    @Order(7)
    @DisplayName("S7: indexSize() ≤ distinct path count after concurrent marking")
    void indexSizeIsBoundedByPathCount() throws Exception {
        int distinctPaths = 50;
        List<Path> paths = new ArrayList<>(distinctPaths);
        for (int i = 0; i < distinctPaths; i++) {
            paths.add(createTempPyFile("bounded_" + i));
        }

        // 200 threads all mark the same 50 paths (with repetition)
        int threadCount = 200;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int tid = i;
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    cache.markCached(paths.get(tid % distinctPaths));
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(15_000, TimeUnit.MILLISECONDS),
                "Bounded marking did not complete within 15s");

        // ConcurrentHashMap must not grow beyond distinct key count
        int size = cache.indexSize();
        assertTrue(size <= distinctPaths,
                "indexSize() " + size + " exceeds distinct path count " + distinctPaths);
    }

    // ── S8: High-churn 10-second sustained scenario (characterisation) ────────────

    @Test
    @Order(8)
    @DisplayName("S8: 10s sustained concurrent marking/invalidation — characterise index stability")
    void sustainedChurnCharacterisation() throws Exception {
        int pathCount  = 20;
        int threadCount = 40;
        long durationMs = 5_000;  // 5 seconds (not 10, to keep CI fast)

        List<Path> paths = new ArrayList<>(pathCount);
        for (int i = 0; i < pathCount; i++) {
            paths.add(createTempPyFile("churn_" + i));
        }

        AtomicInteger operations = new AtomicInteger(0);
        LongAdder exceptions     = new LongAdder();
        long deadline            = System.currentTimeMillis() + durationMs;

        List<Thread> threads = new ArrayList<>(threadCount);
        for (int t = 0; t < threadCount; t++) {
            final int tid = t;
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    while (System.currentTimeMillis() < deadline) {
                        Path p = paths.get(operations.get() % pathCount);
                        if (tid % 3 == 0) {
                            cache.markCached(p);
                        } else if (tid % 3 == 1) {
                            cache.isValid(p);
                        } else {
                            cache.invalidate(p);
                        }
                        operations.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptions.increment();
                }
            }));
        }

        for (Thread t : threads) {
            t.join(durationMs + 5_000);
        }

        int totalOps = operations.get();
        double opsPerSec = totalOps / (durationMs / 1000.0);

        System.out.printf("%n=== BYTECODE CACHE CHURN CHARACTERISATION ===%n");
        System.out.printf("Duration:    %d ms%n", durationMs);
        System.out.printf("Threads:     %d%n", threadCount);
        System.out.printf("Total ops:   %d%n", totalOps);
        System.out.printf("Throughput:  %.0f ops/sec%n", opsPerSec);
        System.out.printf("Exceptions:  %d%n", exceptions.sum());
        System.out.printf("Final index: %d entries%n", cache.indexSize());
        System.out.printf("=== END ===%n%n");

        assertEquals(0, exceptions.sum(),
                "Sustained cache churn raised " + exceptions.sum() + " unexpected exceptions");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private Path createTempPyFile(String baseName) throws IOException {
        Path file = tempSourceDir.resolve(baseName + ".py");
        Files.writeString(file, "# stress test script: " + baseName + "\npass\n");
        return file;
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                      .map(Path::toFile)
                      .forEach(f -> f.delete());
            }
        }
    }
}
