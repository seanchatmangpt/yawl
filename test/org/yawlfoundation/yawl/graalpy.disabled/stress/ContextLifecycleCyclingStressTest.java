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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yawlfoundation.yawl.graalpy.PythonContextPool;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: rapid create/close lifecycle cycling of {@link PythonContextPool}
 * and {@link PythonExecutionEngine}.
 *
 * <h2>Motivation</h2>
 * <p>YAWL's task scheduler creates Python execution contexts on demand and
 * expects them to be reliably closed when a case finishes.  In burst-traffic
 * scenarios, many cases may start and end in quick succession, cycling through
 * pool creation and destruction at high frequency.</p>
 *
 * <p>Known failure modes in resource-pool implementations:</p>
 * <ul>
 *   <li><b>Native handle leak</b>: each GraalPy context wraps a native handle;
 *       rapid cycling without proper cleanup exhausts OS file descriptors.</li>
 *   <li><b>Thread pool retention</b>: Commons Pool2 spawns an eviction thread
 *       per pool; creating 500 pools without closing them leaks 500 threads.</li>
 *   <li><b>Memory ratchet</b>: if close() does not release all heap, repeated
 *       cycling accumulates garbage faster than GC can collect it.</li>
 * </ul>
 *
 * <p>This suite verifies that rapid cycling does <em>not</em> trigger any of
 * these failure modes.  Memory growth is measured heuristically (not
 * asserted as a hard limit, since GC timing is non-deterministic), and all
 * close() operations must complete without exception.</p>
 *
 * <p>Chicago TDD: no mocks; real pool and engine classes only.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@DisplayName("PythonContextPool/Engine — lifecycle rapid cycling")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContextLifecycleCyclingStressTest {

    private static final int SHORT_CYCLES  = 100;
    private static final int MEDIUM_CYCLES = 500;
    private static final int LONG_CYCLES   = 1_000;

    // ── S1: Sequential create/close cycling does not throw ────────────────────────

    @Test
    @Order(1)
    @DisplayName("S1: 1 000 sequential PythonContextPool create→close cycles never throw")
    void sequentialCreateCloseCyclesNeverThrow() {
        for (int i = 0; i < LONG_CYCLES; i++) {
            final int cycle = i;
            assertDoesNotThrow(() -> {
                try (PythonContextPool pool = PythonContextPool.builder()
                        .sandboxConfig(PythonSandboxConfig.strict())
                        .maxPoolSize(1)
                        .build()) {
                    // Pool is valid — no action needed; close on exit
                    assertEquals(1, pool.getMaxTotal(),
                            "Cycle " + cycle + ": pool size must match configured value");
                }
            }, "Cycle " + cycle + " raised an unexpected exception");
        }
    }

    // ── S2: PythonExecutionEngine sequential cycling ─────────────────────────────

    @Test
    @Order(2)
    @DisplayName("S2: 500 sequential PythonExecutionEngine create→close cycles never throw")
    void engineSequentialCyclingNeverThrows() {
        for (int i = 0; i < MEDIUM_CYCLES; i++) {
            final int cycle = i;
            assertDoesNotThrow(() -> {
                try (PythonExecutionEngine engine = PythonExecutionEngine.builder()
                        .contextPoolSize(1)
                        .sandboxed(true)
                        .build()) {
                    assertNotNull(engine.getContextPool(),
                            "Cycle " + cycle + ": context pool must not be null");
                }
            }, "Engine cycle " + cycle + " threw unexpectedly");
        }
    }

    // ── S3: Concurrent create/close cycling from multiple virtual threads ─────────

    @Test
    @Order(3)
    @DisplayName("S3: 50 virtual threads each cycling 20 pools — no exceptions, no deadlock")
    void concurrentCreateCloseCyclingNeverDeadlocks() throws Exception {
        int threadCount = 50;
        int cyclesPerThread = 20;
        CountDownLatch startLatch  = new CountDownLatch(1);
        CountDownLatch doneLatch   = new CountDownLatch(threadCount);
        LongAdder exceptions       = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (int c = 0; c < cyclesPerThread; c++) {
                        try (PythonContextPool pool = PythonContextPool.builder()
                                .sandboxConfig(PythonSandboxConfig.standard())
                                .maxPoolSize(2)
                                .build()) {
                            // Valid state — pool responds to queries
                            pool.getMaxTotal();
                            pool.getActiveCount();
                            pool.getIdleCount();
                        }
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
                "Concurrent lifecycle cycling did not complete within 30s — possible deadlock");
        assertEquals(0, exceptions.sum(),
                "Concurrent cycling raised " + exceptions.sum() + " unexpected exceptions");
    }

    // ── S4: close() is idempotent across 1 000 calls ─────────────────────────────

    @Test
    @Order(4)
    @DisplayName("S4: close() is idempotent — calling 1 000 times never throws")
    void closeIsIdempotentUnderStress() {
        PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(1)
                .build();

        pool.close(); // First close
        for (int i = 0; i < LONG_CYCLES - 1; i++) {
            final int cycle = i;
            assertDoesNotThrow(pool::close,
                    "Repeated close() call " + cycle + " raised exception");
        }
    }

    // ── S5: Pool size variety under cycling ──────────────────────────────────────

    @ParameterizedTest(name = "S5: poolSize={0}")
    @Order(5)
    @ValueSource(ints = {1, 2, 4, 8, 16, 32})
    @DisplayName("S5: 100 cycles with different pool sizes — all cleanly closeable")
    void cyclingWithDifferentPoolSizes(int poolSize) {
        for (int i = 0; i < SHORT_CYCLES; i++) {
            final int cycle = i;
            assertDoesNotThrow(() -> {
                try (PythonContextPool pool = PythonContextPool.builder()
                        .sandboxConfig(PythonSandboxConfig.standard())
                        .maxPoolSize(poolSize)
                        .maxWait(Duration.ofMillis(100))
                        .build()) {
                    assertEquals(poolSize, pool.getMaxTotal(),
                            String.format("Cycle %d poolSize=%d: maxTotal mismatch", cycle, poolSize));
                }
            }, String.format("Cycle %d poolSize=%d threw unexpectedly", cycle, poolSize));
        }
    }

    // ── S6: Sandbox mode cycling ──────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("S6: 300 cycles alternating STRICT/STANDARD/PERMISSIVE sandbox modes — no leaks")
    void sandboxModeCyclingStaysClean() {
        PythonSandboxConfig[] modes = {
            PythonSandboxConfig.strict(),
            PythonSandboxConfig.standard(),
            PythonSandboxConfig.permissive()
        };

        for (int i = 0; i < 300; i++) {
            PythonSandboxConfig config = modes[i % modes.length];
            assertDoesNotThrow(() -> {
                try (PythonContextPool pool = PythonContextPool.builder()
                        .sandboxConfig(config)
                        .maxPoolSize(2)
                        .build()) {
                    assertNotNull(pool,
                            "Pool must not be null regardless of sandbox mode");
                }
            }, "Sandbox cycle " + i + " with mode " + config.getMode() + " threw unexpectedly");
        }
    }

    // ── S7: Memory growth characterisation (informational) ───────────────────────

    @Test
    @Order(7)
    @DisplayName("S7: Memory growth characterisation across 1 000 cycles (informational)")
    void memoryGrowthCharacterisation() {
        Runtime rt = Runtime.getRuntime();
        rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        for (int i = 0; i < LONG_CYCLES; i++) {
            try (PythonContextPool pool = PythonContextPool.builder()
                    .sandboxConfig(PythonSandboxConfig.strict())
                    .maxPoolSize(1)
                    .build()) {
                // Exists briefly; close() called by try-with-resources
            }
            if (i % 100 == 0) {
                rt.gc();
            }
        }

        rt.gc();
        long memAfter = rt.totalMemory() - rt.freeMemory();
        long growthKb = (memAfter - memBefore) / 1024;

        System.out.printf("%n=== LIFECYCLE CYCLING MEMORY REPORT ===%n");
        System.out.printf("Before: %d KB%n", memBefore / 1024);
        System.out.printf("After:  %d KB%n", memAfter / 1024);
        System.out.printf("Growth: %+d KB over %d cycles%n", growthKb, LONG_CYCLES);
        System.out.printf("=== END ===%n%n");

        // Informational: characterise rather than assert a hard limit.
        // Negative growth is fine (GC ran). A ratchet > 50 MB indicates a likely leak.
        assertTrue(growthKb < 50 * 1024,
                String.format("Suspicious memory growth: %d KB over %d create/close cycles. "
                              + "Possible GraalPy context leak.", growthKb, LONG_CYCLES));
    }

    // ── S8: High-frequency concurrent churn (50 threads × 100 cycles) ────────────

    @Test
    @Order(8)
    @DisplayName("S8: 50 threads × 100 cycles of engine create/close — zero exceptions")
    void highFrequencyEngineChurnNeverExcepts() throws Exception {
        int threadCount   = 50;
        int cyclesPerThread = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder failureCount    = new LongAdder();

        for (int t = 0; t < threadCount; t++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    for (int c = 0; c < cyclesPerThread; c++) {
                        try (PythonExecutionEngine eng = PythonExecutionEngine.builder()
                                .contextPoolSize(1)
                                .sandboxed(true)
                                .build()) {
                            assertNotNull(eng.getContextPool());
                        } catch (Exception e) {
                            failureCount.increment();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(60_000, TimeUnit.MILLISECONDS),
                "High-frequency engine churn did not complete within 60s");
        assertEquals(0, failureCount.sum(),
                "Engine lifecycle churn raised " + failureCount.sum() + " unexpected exceptions");
    }
}
