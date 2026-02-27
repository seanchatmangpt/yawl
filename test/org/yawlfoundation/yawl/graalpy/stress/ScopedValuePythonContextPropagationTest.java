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

import java.lang.ScopedValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stress test: Java 25 {@link ScopedValue} propagation of GraalPy pool configuration
 * through YAWL workflow execution chains.
 *
 * <h2>Blue-ocean innovation: ScopedValue + GraalPy</h2>
 * <p>{@link ScopedValue} (stable in Java 24, available in Java 25) provides
 * an immutable, inheritable thread-local alternative that integrates cleanly with
 * virtual threads.  Unlike {@link ThreadLocal}, a {@code ScopedValue} binding is:</p>
 * <ul>
 *   <li><b>Inherited</b> by child virtual threads started within its scope.</li>
 *   <li><b>Immutable</b> once bound — cannot be corrupted by concurrent writes.</li>
 *   <li><b>Automatically unbound</b> when the scope closes, preventing leakage.</li>
 * </ul>
 *
 * <h2>YAWL workflow pattern</h2>
 * <p>A YAWL case initiator binds a {@link PythonSandboxConfig} into a
 * {@code ScopedValue} before launching task threads.  Each task virtual thread
 * inherits the binding and uses it to borrow the correct pool context —
 * without passing the config as an explicit parameter through every call frame.</p>
 *
 * <pre>
 * [Case initiator] → ScopedValue.where(PYTHON_SANDBOX, sandboxConfig).run(() → {
 *     [Task A virtual thread] → PYTHON_SANDBOX.get() → borrow pool context
 *     [Task B virtual thread] → PYTHON_SANDBOX.get() → borrow pool context
 * });
 * </pre>
 *
 * <p>This test verifies:</p>
 * <ul>
 *   <li><b>Correct inheritance</b>: child vthreads see the parent's binding.</li>
 *   <li><b>Isolation between cases</b>: concurrent cases with different configs
 *       do not see each other's binding.</li>
 *   <li><b>No binding leakage</b>: outside a scope, {@code isBound()} is false.</li>
 *   <li><b>Nested scope override</b>: inner scope can shadow outer binding.</li>
 *   <li><b>Stress at scale</b>: 1 000 concurrent cases, each with its own binding,
 *       produce zero cross-contamination errors.</li>
 * </ul>
 *
 * <p>Chicago TDD: no mocks; real {@link PythonContextPool} and
 * {@link PythonSandboxConfig} instances.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see java.lang.ScopedValue
 * @see PythonContextPool
 */
@Tag("stress")
@DisplayName("ScopedValue — GraalPy pool config propagation through workflow chains")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Execution(ExecutionMode.SAME_THREAD)
class ScopedValuePythonContextPropagationTest {

    /**
     * ScopedValue carrying the sandbox mode name for the current workflow case.
     * Mirrors the pattern used by {@code YNetRunner.CASE_CONTEXT}.
     */
    private static final ScopedValue<String> PYTHON_SANDBOX_MODE =
            ScopedValue.newInstance();

    /**
     * ScopedValue carrying the pool configuration for the current workflow case.
     * Allows task threads to resolve their execution context without explicit parameters.
     */
    private static final ScopedValue<PythonSandboxConfig> PYTHON_POOL_CONFIG =
            ScopedValue.newInstance();

    private static final long WALL_MS = 30_000;

    // ── S1: ScopedValue is unbound outside a scope ────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("S1: PYTHON_SANDBOX_MODE is unbound outside a ScopedValue scope")
    void scopedValueIsUnboundOutsideScope() {
        assertFalse(PYTHON_SANDBOX_MODE.isBound(),
                "ScopedValue must not be bound outside an explicit scope");
    }

    // ── S2: ScopedValue is bound inside a scope ───────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("S2: PYTHON_SANDBOX_MODE is bound and readable inside its scope")
    void scopedValueIsBoundInsideScope() {
        String expectedMode = "STRICT";

        ScopedValue.where(PYTHON_SANDBOX_MODE, expectedMode).run(() -> {
            assertTrue(PYTHON_SANDBOX_MODE.isBound(),
                    "ScopedValue must be bound inside its scope");
            assertEquals(expectedMode, PYTHON_SANDBOX_MODE.get(),
                    "ScopedValue must return the bound value");
        });
    }

    // ── S3: Child virtual threads inherit the parent's binding ────────────────────

    @Test
    @Order(3)
    @DisplayName("S3: Child virtual threads inherit PYTHON_SANDBOX_MODE from parent scope")
    void childVirtualThreadsInheritParentBinding() throws Exception {
        String parentMode = "STANDARD";
        AtomicInteger childCount = new AtomicInteger(0);
        AtomicInteger correctCount = new AtomicInteger(0);
        int numChildren = 20;

        ScopedValue.where(PYTHON_SANDBOX_MODE, parentMode).run(() -> {
            CountDownLatch doneLatch = new CountDownLatch(numChildren);
            for (int i = 0; i < numChildren; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        childCount.incrementAndGet();
                        if (PYTHON_SANDBOX_MODE.isBound()
                                && parentMode.equals(PYTHON_SANDBOX_MODE.get())) {
                            correctCount.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }
            try {
                assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                        "Child threads did not complete within wall limit");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        assertEquals(numChildren, childCount.get(),
                "All child threads must have run");
        assertEquals(numChildren, correctCount.get(),
                "All child threads must inherit the parent's ScopedValue binding");
    }

    // ── S4: Binding does not leak outside the scope ───────────────────────────────

    @Test
    @Order(4)
    @DisplayName("S4: PYTHON_SANDBOX_MODE binding does not leak outside its scope")
    void bindingDoesNotLeakOutsideScope() {
        assertFalse(PYTHON_SANDBOX_MODE.isBound(), "Pre-condition: unbound before test");

        ScopedValue.where(PYTHON_SANDBOX_MODE, "PERMISSIVE").run(() -> {
            assertTrue(PYTHON_SANDBOX_MODE.isBound(), "Must be bound inside scope");
        });

        assertFalse(PYTHON_SANDBOX_MODE.isBound(),
                "ScopedValue must be unbound after scope closes — no leakage");
    }

    // ── S5: Nested scope shadows outer binding ────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("S5: Nested scope shadows outer PYTHON_SANDBOX_MODE without corrupting it")
    void nestedScopeIsIndependentOfOuterScope() {
        String outerMode = "STANDARD";
        String innerMode = "STRICT";

        ScopedValue.where(PYTHON_SANDBOX_MODE, outerMode).run(() -> {
            assertEquals(outerMode, PYTHON_SANDBOX_MODE.get(), "Outer scope value");

            ScopedValue.where(PYTHON_SANDBOX_MODE, innerMode).run(() -> {
                assertEquals(innerMode, PYTHON_SANDBOX_MODE.get(),
                        "Inner scope must shadow outer without corrupting it");
            });

            // Outer scope must be restored after inner scope closes
            assertEquals(outerMode, PYTHON_SANDBOX_MODE.get(),
                    "Outer scope must be restored after inner scope closes");
        });
    }

    // ── S6: PythonSandboxConfig propagated through workflow call chain ────────────

    @Test
    @Order(6)
    @DisplayName("S6: PythonSandboxConfig ScopedValue propagates through 5-level call chain")
    void poolConfigPropagatesThroughWorkflowCallChain() {
        PythonSandboxConfig strictConfig = PythonSandboxConfig.strict();

        ScopedValue.where(PYTHON_POOL_CONFIG, strictConfig).run(() -> {
            // Simulate a 5-level YAWL workflow call chain (case → net → task → service → script)
            PythonSandboxConfig resolved = resolveAtLevel(5);
            assertSame(strictConfig, resolved,
                    "Config must propagate unchanged through 5-level call chain");
        });
    }

    /** Recursive call chain simulating YAWL case → net → task → ... nesting. */
    private PythonSandboxConfig resolveAtLevel(int depth) {
        if (depth == 0) {
            return PYTHON_POOL_CONFIG.get();
        }
        return resolveAtLevel(depth - 1);
    }

    // ── S7: 1 000 concurrent cases with different configs — no cross-contamination ─

    @Test
    @Order(7)
    @DisplayName("S7: 1 000 concurrent cases each with isolated ScopedValue — zero cross-contamination")
    void concurrentCasesHaveIsolatedScopedValues() throws Exception {
        int caseCount = 1_000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(caseCount);
        LongAdder contaminations  = new LongAdder();
        LongAdder exceptions      = new LongAdder();

        String[] modeNames = {"STRICT", "STANDARD", "PERMISSIVE"};

        for (int i = 0; i < caseCount; i++) {
            final String expectedMode = modeNames[i % modeNames.length];

            Thread.ofVirtual()
                  .name("workflow-case-" + i)
                  .start(() -> {
                      try {
                          startLatch.await();
                          ScopedValue.where(PYTHON_SANDBOX_MODE, expectedMode).run(() -> {
                              // Each case spawns 3 task virtual threads
                              CountDownLatch taskLatch = new CountDownLatch(3);
                              for (int t = 0; t < 3; t++) {
                                  Thread.ofVirtual().start(() -> {
                                      try {
                                          String observed = PYTHON_SANDBOX_MODE.get();
                                          if (!expectedMode.equals(observed)) {
                                              contaminations.increment();
                                          }
                                      } finally {
                                          taskLatch.countDown();
                                      }
                                  });
                              }
                              try {
                                  taskLatch.await(WALL_MS, TimeUnit.MILLISECONDS);
                              } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                              }
                          });
                      } catch (InterruptedException e) {
                          Thread.currentThread().interrupt();
                          exceptions.increment();
                      } catch (Exception e) {
                          exceptions.increment();
                      } finally {
                          doneLatch.countDown();
                      }
                  });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                "Concurrent case isolation test did not complete within wall limit");

        assertEquals(0, exceptions.sum(),
                "Unexpected exceptions in concurrent case isolation: " + exceptions.sum());
        assertEquals(0, contaminations.sum(),
                "Cross-case ScopedValue contamination detected: " + contaminations.sum() + " mismatches");
    }

    // ── S8: ScopedValue + pool execute() interaction stress ───────────────────────

    @Test
    @Order(8)
    @DisplayName("S8: 200 vthreads resolve ScopedValue config then execute() — all get PythonException")
    void scopedValueResolutionThenPoolExecuteNeverLeaks() throws Exception {
        int threadCount = 200;
        CountDownLatch startLatch    = new CountDownLatch(1);
        CountDownLatch doneLatch     = new CountDownLatch(threadCount);
        LongAdder pythonExceptions   = new LongAdder();
        LongAdder configMismatches   = new LongAdder();
        LongAdder otherExceptions    = new LongAdder();

        PythonSandboxConfig rootConfig = PythonSandboxConfig.strict();

        ScopedValue.where(PYTHON_POOL_CONFIG, rootConfig).run(() -> {
            for (int i = 0; i < threadCount; i++) {
                Thread.ofVirtual().start(() -> {
                    try {
                        startLatch.await();

                        // Resolve the config from ScopedValue — mirrors YAWL task resolution
                        PythonSandboxConfig resolvedConfig = PYTHON_POOL_CONFIG.get();
                        if (resolvedConfig != rootConfig) {
                            configMismatches.increment();
                        }

                        // Attempt to use the resolved config to execute
                        try (PythonContextPool pool = PythonContextPool.builder()
                                .sandboxConfig(resolvedConfig)
                                .maxPoolSize(1)
                                .build()) {
                            pool.execute(ctx -> "workflow-output");
                        }

                    } catch (PythonException e) {
                        pythonExceptions.increment();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        otherExceptions.increment();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            try {
                startLatch.countDown();
                assertTrue(doneLatch.await(WALL_MS, TimeUnit.MILLISECONDS),
                        "ScopedValue + execute() threads did not complete within wall limit");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        assertEquals(0, configMismatches.sum(),
                "ScopedValue config mismatch in child threads: " + configMismatches.sum());
        assertEquals(0, otherExceptions.sum(),
                "Unexpected exception types (not PythonException): " + otherExceptions.sum());
        // All threads must receive PythonException (GraalPy absent)
        assertEquals(threadCount, pythonExceptions.sum(),
                "Expected " + threadCount + " PythonExceptions, got " + pythonExceptions.sum());
    }
}
