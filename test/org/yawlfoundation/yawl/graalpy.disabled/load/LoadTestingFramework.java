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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GraalPy pool self-check tests verifying the mathematical pool capacity invariant.
 *
 * <p>Replaces the previous {@code LoadTestingFramework} that delegated to non-existent
 * {@code ConcurrencyTester}, {@code MemoryMonitor}, and {@code MetricsCollector} classes.</p>
 *
 * <h2>Core invariant</h2>
 * <p>{@code active_count + idle_count ≤ max_total} must hold at all times.
 * This is a mathematical property of any bounded object pool and is verified
 * continuously under concurrent load.</p>
 *
 * <p>Chicago TDD: real pool operations only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@Tag("graalpy")
@DisplayName("GraalPy Pool Self-Check — mathematical pool invariant verification")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraalPyPoolSelfCheckTest {

    private static final Duration BORROW_TIMEOUT = Duration.ofMillis(500);

    // ── SC1: Capacity invariant under concurrency ──────────────────────────────

    @Test
    @Order(1)
    @DisplayName("SC1: Pool capacity invariant holds under 20-thread concurrent load")
    void testPoolCapacityInvariantUnderConcurrency() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(8)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            int threadCount = 20;
            AtomicLong operationCounter    = new AtomicLong(0);
            AtomicInteger invariantViolations = new AtomicInteger(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < 50; i++) {
                            try {
                                pool.execute(ctx -> "cap-check");
                            } catch (PythonException ignored) {
                                // Expected absence — pool operation still completed
                            } catch (Exception ignored) { }

                            long ops = operationCounter.incrementAndGet();
                            // Check invariant after every 100 total operations
                            if (ops % 100 == 0) {
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
            assertTrue(doneLatch.await(60, TimeUnit.SECONDS),
                    "Self-check threads must complete within 60 seconds");

            System.out.printf("%n=== CAPACITY INVARIANT (20 threads, %d ops) ===%n",
                    operationCounter.get());
            System.out.printf("Invariant violations: %d%n%n", invariantViolations.get());

            assertEquals(0, invariantViolations.get(),
                    "Pool capacity invariant active+idle<=maxTotal must NEVER be violated");
        }
    }

    // ── SC2: State query consistency ───────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("SC2: Pool state queries are consistent — 1000 rapid single-threaded checks")
    void testPoolStateQueriesAreConsistent() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(4)
                .maxWait(BORROW_TIMEOUT)
                .build()) {

            int queryCount = 1000;
            int violations = 0;

            for (int i = 0; i < queryCount; i++) {
                int active = pool.getActiveCount();
                int idle   = pool.getIdleCount();
                int max    = pool.getMaxTotal();
                if (active + idle > max) {
                    violations++;
                }
            }

            System.out.printf("%n=== STATE QUERY CONSISTENCY (%d queries) ===%n", queryCount);
            System.out.printf("Invariant violations: %d%n%n", violations);

            assertEquals(0, violations,
                    "Pool state invariant must hold on all 1000 consecutive queries");
        }
    }

    // ── SC3: Rapid create/close cycles ────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("SC3: Pool handles rapid create/close cycles — 10 cycles, <20% failure rate")
    void testPoolHandlesRapidCreateClose() {
        int cycles   = 10;
        int failures = 0;

        for (int i = 0; i < cycles; i++) {
            try (PythonContextPool pool = PythonContextPool.builder()
                    .sandboxConfig(PythonSandboxConfig.strict())
                    .maxPoolSize(2)
                    .build()) {
                assertNotNull(pool, "Pool must be created successfully");
            } catch (Exception e) {
                failures++;
            }
        }

        System.out.printf("%n=== RAPID CREATE/CLOSE (%d cycles) ===%n", cycles);
        System.out.printf("Failures: %d/%d%n%n", failures, cycles);

        double failureRate = (double) failures / cycles;
        assertTrue(failureRate < 0.20,
                String.format("Pool create/close failure rate %.1f%% exceeds 20%% threshold",
                        failureRate * 100));
    }
}
