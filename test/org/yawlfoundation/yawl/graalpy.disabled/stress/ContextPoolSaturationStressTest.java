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
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: {@link PythonContextPool} saturation semantics and recovery.
 *
 * <h2>What this tests</h2>
 * <p>GraalPy contexts are finite resources. In production, a pool of size N
 * serves M concurrent YAWL tasks where M ≫ N.  This test exhausts the pool
 * from multiple concurrent callers and verifies:</p>
 * <ol>
 *   <li>Saturation produces the correct exception type ({@code CONTEXT_ERROR}),
 *       not a generic {@link RuntimeException} or {@link NullPointerException}.</li>
 *   <li>The exception message contains pool-state diagnostics ({@code active=},
 *       {@code idle=}) for operator runbooks.</li>
 *   <li>The pool recovers after the saturation burst — {@code close()} still works,
 *       no internal state is corrupted.</li>
 *   <li>Pool size invariant: {@code active + idle ≤ maxTotal} never violated.</li>
 *   <li>Multiple independent saturation bursts do not degrade the pool progressively.</li>
 * </ol>
 *
 * <p>Chicago TDD: real classes only, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("stress")
@DisplayName("PythonContextPool — saturation and recovery")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContextPoolSaturationStressTest {

    private static final Duration SHORT_WAIT  = Duration.ofMillis(200);
    private static final long     WALL_MS     = 15_000;

    // ── S1: Saturation produces PythonException, not a raw exception ──────────────

    @Test
    @Order(1)
    @DisplayName("S1: Saturating pool produces PythonException — not RuntimeException or NPE")
    void saturationProducesPythonException() {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(1)
                .maxWait(SHORT_WAIT)
                .build()) {

            // Fire 20 concurrent callers into a pool of size 1
            List<Exception> caught = runConcurrentBorrows(pool, 20, WALL_MS);

            // Every caught exception must be PythonException
            for (Exception e : caught) {
                assertThat("Saturation must raise PythonException, not " + e.getClass().getName(),
                        e, instanceOf(PythonException.class));
            }
        }
    }

    // ── S2: Exception message contains pool-state diagnostics ────────────────────

    @Test
    @Order(2)
    @DisplayName("S2: CONTEXT_ERROR message contains 'active=' diagnostic for operator runbooks")
    void saturationExceptionMessageContainsPoolStateDiagnostic() {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(1)
                .maxWait(SHORT_WAIT)
                .build()) {

            List<Exception> caught = runConcurrentBorrows(pool, 10, WALL_MS);

            // Find any PythonException with the expected message fragment
            boolean foundDiagnostic = caught.stream()
                    .filter(e -> e instanceof PythonException)
                    .map(Throwable::getMessage)
                    .anyMatch(msg -> msg != null && msg.contains("active="));

            assertTrue(foundDiagnostic,
                    "At least one saturation exception must mention 'active=' in its message");
        }
    }

    // ── S3: CONTEXT_ERROR is the specific kind ────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("S3: All PythonExceptions from saturation have CONTEXT_ERROR kind")
    void allSaturationExceptionsHaveContextErrorKind() {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(2)
                .maxWait(SHORT_WAIT)
                .build()) {

            List<Exception> caught = runConcurrentBorrows(pool, 30, WALL_MS);

            for (Exception e : caught) {
                if (e instanceof PythonException pe) {
                    assertThat("Saturation must produce CONTEXT_ERROR, not " + pe.getErrorKind(),
                            pe.getErrorKind(), is(PythonException.ErrorKind.CONTEXT_ERROR));
                }
            }
        }
    }

    // ── S4: Pool health invariant never violated ──────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("S4: active + idle ≤ maxTotal invariant holds throughout saturation")
    void poolHealthInvariantHeldDuringSaturation() throws Exception {
        int maxSize = 4;

        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(maxSize)
                .maxWait(SHORT_WAIT)
                .build()) {

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(100);
            AtomicInteger invariantViolations = new AtomicInteger(0);

            // Monitor thread checks invariant continuously
            Thread monitor = Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    while (doneLatch.getCount() > 0) {
                        int active = pool.getActiveCount();
                        int idle   = pool.getIdleCount();
                        int max    = pool.getMaxTotal();
                        if (active + idle > max) {
                            invariantViolations.incrementAndGet();
                        }
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            // Worker threads: saturate the pool
            for (int i = 0; i < 100; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();
                        pool.execute(ctx -> "noop");
                    } catch (PythonException ignored) {
                        // Expected
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                    "Workers did not complete within wall limit");
            monitor.interrupt();
            monitor.join(1_000);

            assertEquals(0, invariantViolations.get(),
                    "Pool active+idle > maxTotal invariant violated " + invariantViolations.get() + " times");
        }
    }

    // ── S5: Pool recovers after repeated saturation bursts ───────────────────────

    @Test
    @Order(5)
    @DisplayName("S5: Pool remains closeable after 5 consecutive saturation bursts")
    void poolRemainsCloseableAfterRepeatedSaturationBursts() {
        PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(2)
                .maxWait(SHORT_WAIT)
                .build();

        try {
            for (int burst = 0; burst < 5; burst++) {
                runConcurrentBorrows(pool, 50, WALL_MS);
            }
        } finally {
            // Must not throw despite 5 saturation bursts
            assertDoesNotThrow(pool::close,
                    "Pool must remain closeable after repeated saturation bursts");
        }
    }

    // ── S6: Parameterised — different pool sizes produce same exception type ──────

    @ParameterizedTest(name = "S6: poolSize={0}")
    @Order(6)
    @ValueSource(ints = {1, 2, 4, 8, 16})
    @DisplayName("S6: PythonException raised regardless of pool size under saturation")
    void saturationBehaviourConsistentAcrossPoolSizes(int poolSize) {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.strict())
                .maxPoolSize(poolSize)
                .maxWait(SHORT_WAIT)
                .build()) {

            // 10× oversubscription for each pool size
            int threadCount = poolSize * 10;
            List<Exception> caught = runConcurrentBorrows(pool, threadCount, WALL_MS);

            // All must be PythonException
            long nonPython = caught.stream()
                    .filter(e -> !(e instanceof PythonException))
                    .count();
            assertEquals(0, nonPython,
                    String.format("poolSize=%d: %d non-PythonException caught", poolSize, nonPython));
        }
    }

    // ── S7: Pool maxTotal does not drift after saturation ────────────────────────

    @Test
    @Order(7)
    @DisplayName("S7: pool.getMaxTotal() does not drift after saturation burst")
    void maxTotalDoesNotDriftAfterSaturation() {
        int expectedMax = 3;

        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(expectedMax)
                .maxWait(SHORT_WAIT)
                .build()) {

            runConcurrentBorrows(pool, 200, WALL_MS);

            assertThat("Pool maxTotal must not drift after saturation",
                    pool.getMaxTotal(), is(expectedMax));
        }
    }

    // ── S8: executeVoid() propagates the same exception under saturation ──────────

    @Test
    @Order(8)
    @DisplayName("S8: executeVoid() also raises PythonException under saturation — no silent swallow")
    void executeVoidPropagatesPythonExceptionUnderSaturation() throws Exception {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(PythonSandboxConfig.standard())
                .maxPoolSize(1)
                .maxWait(SHORT_WAIT)
                .build()) {

            AtomicInteger pythonExceptions = new AtomicInteger(0);
            CountDownLatch doneLatch = new CountDownLatch(20);

            for (int i = 0; i < 20; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        pool.executeVoid(ctx -> {
                            // noop — never reached without GraalPy
                        });
                    } catch (PythonException e) {
                        pythonExceptions.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                    "Threads did not complete within wall limit");

            assertTrue(pythonExceptions.get() > 0,
                    "executeVoid() must propagate PythonException under saturation");
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────────

    /**
     * Runs {@code threadCount} concurrent {@link PythonContextPool#execute} calls,
     * each using a virtual thread. Returns the list of exceptions caught.
     * Asserts that all threads complete within {@code wallMs} milliseconds.
     */
    private List<Exception> runConcurrentBorrows(PythonContextPool pool, int threadCount, long wallMs) {
        List<Exception> caught   = new ArrayList<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        Object lock = new Object();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                try {
                    startLatch.await();
                    pool.execute(ctx -> "noop");
                } catch (Exception e) {
                    synchronized (lock) {
                        caught.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        try {
            startLatch.countDown();
            assertTrue(doneLatch.await(wallMs, TimeUnit.MILLISECONDS),
                    "Concurrent borrow threads did not complete within " + wallMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted waiting for concurrent borrows");
        }

        return caught;
    }
}
