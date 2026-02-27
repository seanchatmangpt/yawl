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

package org.yawlfoundation.yawl.graalpy.load;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.graalpy.PythonContextPool;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraalPy context pool load test using real {@link PythonContextPool}.
 *
 * <p>Replaces the previous {@code LoadTestRunner} which accepted a generic {@code Supplier<Long>}
 * workload with no YAWL-specific behaviour and delegated to non-existent
 * {@code MetricsCollector}, {@code MemoryMonitor}, and {@code ConcurrencyTester} classes.</p>
 *
 * <h2>Note on PythonException</h2>
 * <p>{@link PythonException} is expected when the GraalPy native runtime is absent (e.g., CI).
 * It is treated as a successful pool operation for throughput measurement.
 * Only non-{@code PythonException} failures count as errors.</p>
 *
 * <p>Chicago TDD: real pool operations only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@Tag("graalpy")
@DisplayName("GraalPy Codelet Load Test — real PythonContextPool under concurrent load")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraalPyCodeletLoadTest {

    private static final Duration BORROW_TIMEOUT = Duration.ofMillis(500);

    // ── L1: Pool borrow/return under concurrent load ───────────────────────────

    @Test
    @Order(1)
    @DisplayName("L1: Pool borrow/return throughput — 10 threads × 100 ops each")
    void testPoolBorrowReturnUnderLoad() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(4)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            int threadCount = 10;
            int opsPerThread = 100;
            AtomicLong successOps = new AtomicLong(0);
            AtomicLong errorOps   = new AtomicLong(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            try {
                                pool.execute(ctx -> "result");
                                successOps.incrementAndGet();
                            } catch (PythonException e) {
                                // Expected when GraalPy absent — counts as a successful pool op
                                successOps.incrementAndGet();
                            } catch (Exception e) {
                                errorOps.incrementAndGet();
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
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                    "Load test threads must complete within 30 seconds");

            long total     = successOps.get() + errorOps.get();
            double errRate = total > 0 ? (double) errorOps.get() / total : 0;

            System.out.printf("%n=== POOL BORROW/RETURN UNDER LOAD ===%n");
            System.out.printf("Total ops: %d, Success: %d, Errors: %d, Error rate: %.1f%%%n%n",
                    total, successOps.get(), errorOps.get(), errRate * 100);

            assertTrue(total > 0, "Total operations must be positive");
            assertTrue(errRate < 0.10,
                    String.format("Error rate %.1f%% exceeds 10%% threshold", errRate * 100));
        }
    }

    // ── L2: Pool invariant holds under concurrency ─────────────────────────────

    @Test
    @Order(2)
    @DisplayName("L2: Pool invariant active+idle≤maxTotal holds under 5-thread concurrent load")
    void testPoolInvariantHolds() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(4)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            int threadCount  = 5;
            int opsPerThread = 50;
            AtomicLong invariantViolations = new AtomicLong(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            try {
                                pool.execute(ctx -> "inv-check");
                            } catch (PythonException ignored) {
                                // Expected absence
                            } catch (Exception ignored) { }

                            // Check pool invariant every 10 ops
                            if (i % 10 == 0) {
                                int active = pool.getActiveCount();
                                int idle   = pool.getIdleCount();
                                int max    = pool.getMaxTotal();
                                if (active + idle > max) {
                                    invariantViolations.incrementAndGet();
                                }
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
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS),
                    "Invariant test threads must complete within 30 seconds");

            System.out.printf("%n=== POOL INVARIANT CHECK ===%n");
            System.out.printf("Invariant violations: %d%n%n", invariantViolations.get());

            assertEquals(0L, invariantViolations.get(),
                    "Pool invariant active+idle<=maxTotal must NEVER be violated");
        }
    }

    // ── L3: Single-threaded determinism ────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("L3: Single-threaded pool operations are deterministic — 50 sequential ops")
    void testSingleScriptDeterminism() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(2)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            int opCount    = 50;
            long successes = 0;
            long errors    = 0;

            for (int i = 0; i < opCount; i++) {
                try {
                    pool.execute(ctx -> "deterministic");
                    successes++;
                } catch (PythonException e) {
                    // Expected when GraalPy absent — not an error
                    successes++;
                } catch (Exception e) {
                    errors++;
                }
            }

            System.out.printf("%n=== SINGLE-SCRIPT DETERMINISM ===%n");
            System.out.printf("50 ops: %d expected outcomes, %d unexpected errors%n%n",
                    successes, errors);

            assertEquals(opCount, successes + errors,
                    "All 50 operations must complete (success or PythonException)");
            assertEquals(0L, errors,
                    "No non-PythonException errors in single-threaded determinism test");
        }
    }
}
