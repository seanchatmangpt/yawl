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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.graalpy.PythonContextPool;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: GraalPy {@link PythonContextPool} under Java 25 virtual thread explosion.
 *
 * <h2>Blue-ocean innovation</h2>
 * <p>Virtual threads (Java 25, stable since Java 21) are the natural executor for
 * YAWL work items — they are cheap to create, auto-scale with the OS thread count,
 * and compose with {@link java.lang.ScopedValue} for context propagation.  However,
 * GraalPy contexts are <em>expensive</em>: each one holds its own Python heap and GIL.
 * The pool provides the N:M bridge between cheap vthreads and expensive Python contexts.</p>
 *
 * <p>This test measures:</p>
 * <ul>
 *   <li><b>No deadlock</b>: launching 5 000 virtual threads against a pool of size 4
 *       must complete within a hard wall-clock bound, not hang forever.</li>
 *   <li><b>Correct error propagation</b>: without GraalPy present, every borrow attempt
 *       must surface {@link PythonException} with {@code CONTEXT_ERROR}; no silent
 *       swallowing or wrong exception type.</li>
 *   <li><b>Pool health invariant</b>: after the storm, the pool's
 *       {@code activeCount + idleCount ≤ maxTotal} invariant must hold.</li>
 *   <li><b>No thread starvation</b>: all launched virtual threads must terminate;
 *       none may be parked indefinitely in the pool borrow queue.</li>
 *   <li><b>Scalability characterisation</b>: records time-to-completion at 10×, 100×,
 *       and 1 250× oversubscription ratios for capacity-planning data.</li>
 * </ul>
 *
 * <p>Chicago TDD: all assertions are on real pool behaviour; no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see PythonContextPool
 */
@Tag("stress")
@DisplayName("PythonContextPool — virtual thread explosion stress")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class ContextPoolVirtualThreadStressTest {

    /** Pool size intentionally small — models a resource-constrained runtime. */
    private static final int POOL_SIZE = 4;

    /** Borrow timeout short enough to fail fast without GraalPy. */
    private static final Duration BORROW_TIMEOUT = Duration.ofMillis(500);

    /** Hard wall-clock limit for any single scenario to avoid hanging CI. */
    private static final long SCENARIO_WALL_MS = 30_000;

    private PythonContextPool pool;

    @BeforeEach
    void createPool() {
        pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(POOL_SIZE)
                .maxWait(BORROW_TIMEOUT)
                .build();
    }

    @AfterEach
    void closePool() {
        if (pool != null) {
            pool.close();
        }
    }

    // ── S1: 10× oversubscription (40 threads, pool=4) ────────────────────────────

    @Test
    @Order(1)
    @DisplayName("S1: 40 virtual threads (10× oversubscription) all terminate without deadlock")
    void tenTimesOversubscriptionNeverDeadlocks() throws Exception {
        int threadCount = POOL_SIZE * 10;         // 40 threads
        runVirtualThreadStorm(threadCount);
    }

    // ── S2: 100× oversubscription (400 threads, pool=4) ──────────────────────────

    @Test
    @Order(2)
    @DisplayName("S2: 400 virtual threads (100× oversubscription) all terminate without deadlock")
    void hundredTimesOversubscriptionNeverDeadlocks() throws Exception {
        int threadCount = POOL_SIZE * 100;        // 400 threads
        runVirtualThreadStorm(threadCount);
    }

    // ── S3: 1 250× oversubscription (5 000 threads, pool=4) ──────────────────────

    @Test
    @Order(3)
    @DisplayName("S3: 5 000 virtual threads (1250× oversubscription) all terminate without deadlock")
    void thousandTimesOversubscriptionNeverDeadlocks() throws Exception {
        int threadCount = POOL_SIZE * 1_250;      // 5 000 threads
        runVirtualThreadStorm(threadCount);
    }

    // ── S4: Exception correctness under saturation ────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("S4: Every thread gets PythonException — no silent swallowing under saturation")
    void allThreadsReceivePythonExceptionNotSilentFailure() throws Exception {
        int threadCount = 200;
        LongAdder pythonExceptions   = new LongAdder();
        LongAdder otherExceptions    = new LongAdder();
        LongAdder unexpectedSuccess  = new LongAdder();

        List<Thread> threads = new ArrayList<>(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    pool.execute(ctx -> "should_not_succeed");
                    unexpectedSuccess.increment();
                } catch (PythonException e) {
                    pythonExceptions.increment();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    otherExceptions.increment();
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS),
                "Not all threads completed within wall limit");

        // No silent successes (GraalPy absent)
        assertEquals(0, unexpectedSuccess.sum(),
                "No execution should succeed without GraalPy runtime");

        // No unexpected exception types — pool must only throw PythonException
        assertEquals(0, otherExceptions.sum(),
                "Only PythonException expected; got " + otherExceptions.sum() + " other exceptions");

        // All threads received an exception
        assertEquals(threadCount, pythonExceptions.sum(),
                "Every thread must receive PythonException, got " + pythonExceptions.sum());
    }

    // ── S5: CONTEXT_ERROR kind dominates ─────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("S5: CONTEXT_ERROR is the dominant exception kind under saturation")
    void contextErrorKindDominatesUnderSaturation() throws Exception {
        int threadCount = 100;
        AtomicInteger contextErrorCount = new AtomicInteger(0);
        AtomicInteger otherKindCount    = new AtomicInteger(0);

        List<Thread> threads = new ArrayList<>(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    pool.execute(ctx -> "noop");
                } catch (PythonException e) {
                    if (e.getErrorKind() == PythonException.ErrorKind.CONTEXT_ERROR) {
                        contextErrorCount.incrementAndGet();
                    } else {
                        otherKindCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                    // ignore unexpected
                } finally {
                    doneLatch.countDown();
                }
            }));
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS),
                "Threads did not complete within wall limit");

        // CONTEXT_ERROR is the expected kind (context init fails without GraalPy)
        assertTrue(contextErrorCount.get() > 0,
                "At least one CONTEXT_ERROR expected; got 0");

        // Other kinds must be zero (no unexpected error categories)
        assertEquals(0, otherKindCount.get(),
                "Unexpected PythonException kinds detected: " + otherKindCount.get());
    }

    // ── S6: Pool health invariant survives the storm ─────────────────────────────

    @Test
    @Order(6)
    @DisplayName("S6: Pool health invariant (active + idle ≤ maxTotal) holds after storm")
    void poolHealthInvariantHoldsAfterVirtualThreadStorm() throws Exception {
        int threadCount = 500;
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.execute(ctx -> "noop");
                } catch (PythonException ignored) {
                    // Expected: GraalPy absent
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS),
                "Storm threads did not all complete within wall limit");

        // Pool invariant: active + idle ≤ maxTotal
        int active = pool.getActiveCount();
        int idle   = pool.getIdleCount();
        int max    = pool.getMaxTotal();

        assertTrue(active + idle <= max,
                String.format("Pool invariant violated: active=%d idle=%d maxTotal=%d", active, idle, max));
        assertEquals(POOL_SIZE, max,
                "Pool max size must not change after storm");
    }

    // ── S7: Pool is still closeable after the storm ───────────────────────────────

    @Test
    @Order(7)
    @DisplayName("S7: Pool closes cleanly after virtual thread storm")
    void poolClosesCleanlyAfterStorm() throws Exception {
        int threadCount = 200;
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    pool.execute(ctx -> "noop");
                } catch (PythonException ignored) {
                    // Expected
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertTrue(doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS),
                "Storm threads did not complete within wall limit");

        // Must not throw after storm
        assertDoesNotThrow(pool::close,
                "Pool close must succeed after virtual thread storm");
    }

    // ── S8: Scalability characterisation (informational, always passes) ───────────

    @Test
    @Order(8)
    @DisplayName("S8: Scalability characterisation — oversubscription ratios (informational)")
    void scalabilityCharacterisation() throws Exception {
        int[] oversubscriptionRatios = {1, 5, 25, 100, 500};
        StringBuilder report = new StringBuilder("\n=== GRAALPY POOL SCALABILITY CHARACTERISATION ===\n");
        report.append(String.format("%-20s %-15s %-15s %-20s%n",
                "Threads", "Pool Size", "Duration (ms)", "Threads/sec"));
        report.append("-".repeat(70)).append("\n");

        for (int ratio : oversubscriptionRatios) {
            int threadCount = POOL_SIZE * ratio;

            try (PythonContextPool measuredPool = PythonContextPool.builder()
                    .sandboxConfig(PythonSandboxConfig.standard())
                    .maxPoolSize(POOL_SIZE)
                    .maxWait(BORROW_TIMEOUT)
                    .build()) {

                CountDownLatch startLatch = new CountDownLatch(1);
                CountDownLatch doneLatch  = new CountDownLatch(threadCount);

                for (int i = 0; i < threadCount; i++) {
                    Thread.ofVirtual().start(() -> {
                        try {
                            startLatch.await();
                            measuredPool.execute(ctx -> "noop");
                        } catch (PythonException ignored) {
                            // Expected
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }

                long startNs = System.nanoTime();
                startLatch.countDown();
                boolean finished = doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS);
                long durationMs = (System.nanoTime() - startNs) / 1_000_000;

                if (finished) {
                    double throughput = threadCount / (durationMs / 1000.0);
                    report.append(String.format("%-20d %-15d %-15d %-20.1f%n",
                            threadCount, POOL_SIZE, durationMs, throughput));
                } else {
                    report.append(String.format("%-20d %-15d %-15s %-20s%n",
                            threadCount, POOL_SIZE, "TIMEOUT", "N/A"));
                    // Hard fail if even the characterisation hangs
                    fail("Scalability test TIMEOUT at " + ratio + "× oversubscription");
                }
            }
        }

        report.append("=== END ===\n");
        System.out.println(report);
        // Informational only — if we got here, all ratios completed
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    /**
     * Launches {@code threadCount} virtual threads all concurrently calling
     * {@link PythonContextPool#execute}. Asserts that all threads complete within
     * the scenario wall-clock limit (no deadlock / infinite park).
     */
    private void runVirtualThreadStorm(int threadCount) throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        LongAdder exceptionCount  = new LongAdder();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual()
                  .name("graalpy-storm-" + i)
                  .start(() -> {
                      try {
                          startLatch.await();
                          pool.execute(ctx -> "workflow-result");
                      } catch (PythonException e) {
                          exceptionCount.increment();
                      } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                      } finally {
                          doneLatch.countDown();
                      }
                  });
        }

        startLatch.countDown();

        boolean allDone = doneLatch.await(SCENARIO_WALL_MS, TimeUnit.MILLISECONDS);
        assertTrue(allDone,
                String.format("DEADLOCK DETECTED: %d/%d threads still parked after %dms. "
                              + "Pool: active=%d idle=%d maxTotal=%d",
                              doneLatch.getCount(), threadCount, SCENARIO_WALL_MS,
                              pool.getActiveCount(), pool.getIdleCount(), pool.getMaxTotal()));
    }
}
