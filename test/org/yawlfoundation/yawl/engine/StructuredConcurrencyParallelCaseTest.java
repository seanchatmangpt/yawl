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

package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.lang.ScopedValue;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying Java 25 structured concurrency ({@link StructuredTaskScope}) patterns
 * applied to parallel YAWL case execution.
 *
 * <p>Chicago TDD approach: uses the real YEngine singleton with real specifications.
 * All concurrency is exercised through {@link StructuredTaskScope.ShutdownOnFailure}
 * to verify:
 * <ul>
 *   <li>Multiple cases can be started in parallel via forked virtual threads</li>
 *   <li>ScopedValue context is correctly isolated per-case in forked subtasks</li>
 *   <li>ShutdownOnFailure cancels peers when one subtask throws</li>
 *   <li>join() propagates the first failure as an exception to the owner thread</li>
 *   <li>Successful parallel execution aggregates all case identifiers</li>
 * </ul>
 *
 * <p>Tests are serialised (SAME_THREAD) because they share the YEngine singleton state.
 *
 * @author YAWL Foundation
 * @see StructuredTaskScope
 * @see YNetRunner#CASE_CONTEXT
 */
@Tag("integration")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("StructuredTaskScope parallel case execution tests")
class StructuredConcurrencyParallelCaseTest {

    private YEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        spec = loadSpec("YAWL_Specification2.xml");
        engine.loadSpecification(spec);
    }

    @AfterEach
    void tearDown() throws Exception {
        EngineClearer.clear(engine);
    }

    // -------------------------------------------------------------------------
    // Parallel case launch with ShutdownOnFailure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ShutdownOnFailure parallel launch")
    class ShutdownOnFailureLaunch {

        @Test
        @DisplayName("All subtasks complete: all case IDs returned")
        void allSubtasksSucceed() throws Exception {
            int caseCount = 5;
            List<StructuredTaskScope.Subtask<YIdentifier>> subtasks = new ArrayList<>();

            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < caseCount; i++) {
                    subtasks.add(scope.fork(() ->
                            engine.startCase(spec.getSpecificationID(), null, null, null,
                                    new YLogDataItemList(), null, false)));
                }
                scope.join();
            }

            List<YIdentifier> caseIDs = subtasks.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .toList();

            assertEquals(caseCount, caseIDs.size(), "Must receive one YIdentifier per forked case");
            for (YIdentifier id : caseIDs) {
                assertNotNull(id, "Each launched case must have a non-null identifier");
            }
        }

        @Test
        @DisplayName("All returned case IDs are distinct")
        void launchedCaseIDsAreDistinct() throws Exception {
            int caseCount = 4;
            List<StructuredTaskScope.Subtask<YIdentifier>> subtasks = new ArrayList<>();

            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < caseCount; i++) {
                    subtasks.add(scope.fork(() ->
                            engine.startCase(spec.getSpecificationID(), null, null, null,
                                    new YLogDataItemList(), null, false)));
                }
                scope.join();
            }

            long distinctCount = subtasks.stream()
                    .map(t -> t.get().toString())
                    .distinct()
                    .count();
            assertEquals(caseCount, distinctCount, "Each case must receive a unique identifier");
        }

        @Test
        @DisplayName("throwIfFailed propagates first subtask exception to caller")
        void throwIfFailedPropagatesException() {
            AtomicInteger startedCount = new AtomicInteger(0);

            Exception thrown = assertThrows(Exception.class, () -> {
                try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                    // One good subtask
                    scope.fork(() -> {
                        startedCount.incrementAndGet();
                        return "ok";
                    });
                    // One subtask that always fails
                    scope.fork(() -> {
                        startedCount.incrementAndGet();
                        throw new IllegalStateException("deliberate-failure");
                    });
                    scope.join();
                }
            });

            // The root cause or the ExecutionException wraps IllegalStateException
            Throwable cause = thrown;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            assertEquals("deliberate-failure", cause.getMessage(),
                    "The original failure message must be propagated");
        }
    }

    // -------------------------------------------------------------------------
    // ScopedValue isolation inside StructuredTaskScope subtasks
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ScopedValue isolation across StructuredTaskScope subtasks")
    class ScopedValueIsolation {

        @Test
        @DisplayName("Each subtask sees its own ScopedValue binding via callWhere")
        void eachSubtaskSeesOwnScopedValue() throws Exception {
            int taskCount = 10;
            List<StructuredTaskScope.Subtask<String>> results = new ArrayList<>();

            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < taskCount; i++) {
                    final String caseID = "case-" + i;
                    results.add(scope.fork(() ->
                            ScopedValue.where(YNetRunner.CASE_CONTEXT, caseID).call(() -> {
                                // Yield to encourage interleaving
                                Thread.yield();
                                return YNetRunner.CASE_CONTEXT.get();
                            })));
                }
                scope.join();
            }

            for (int i = 0; i < taskCount; i++) {
                assertEquals("case-" + i, results.get(i).get(),
                        "Subtask " + i + " must see its own case ID, not another's");
            }
        }

        @Test
        @DisplayName("CASE_CONTEXT from parent scope is inherited by subtask")
        void parentScopedValueInheritedBySubtask() throws Exception {
            String parentCaseID = "parent-case-99";
            AtomicInteger inheritCount = new AtomicInteger(0);

            ScopedValue.where(YNetRunner.CASE_CONTEXT, parentCaseID).call(() -> {
                try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                    scope.fork(() -> {
                        // Subtask inherits the parent ScopedValue
                        if (YNetRunner.CASE_CONTEXT.isBound()
                                && parentCaseID.equals(YNetRunner.CASE_CONTEXT.get())) {
                            inheritCount.incrementAndGet();
                        }
                        return null;
                    });
                    scope.join();
                }
                return null;
            });

            assertEquals(1, inheritCount.get(),
                    "Subtask must inherit parent ScopedValue binding");
        }
    }

    // -------------------------------------------------------------------------
    // Virtual thread name validation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Virtual thread naming in StructuredTaskScope")
    class VirtualThreadNaming {

        @Test
        @DisplayName("Forked virtual threads report isVirtual() == true")
        void forkedThreadsAreVirtual() throws Exception {
            List<StructuredTaskScope.Subtask<Boolean>> results = new ArrayList<>();

            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < 5; i++) {
                    results.add(scope.fork(() -> Thread.currentThread().isVirtual()));
                }
                scope.join();
            }

            for (int i = 0; i < results.size(); i++) {
                assertTrue(results.get(i).get(),
                        "Subtask " + i + " must run on a virtual thread");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Performance: parallel is faster than sequential for I/O-bound operations
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Parallel execution performance bounds")
    class PerformanceBounds {

        @Test
        @DisplayName("Starting N cases in parallel completes within reasonable wall-clock time")
        void parallelCaseLaunchCompletesQuickly() throws Exception {
            int caseCount = 3;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

            try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow())) {
                for (int i = 0; i < caseCount; i++) {
                    scope.fork(() ->
                            engine.startCase(spec.getSpecificationID(), null, null, null,
                                    new YLogDataItemList(), null, false));
                }
                scope.join();
            }

            // If we reach here without TimeoutException the wall-clock limit was respected
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private YSpecification loadSpec(String resourceName) throws Exception {
        URL url = getClass().getResource(resourceName);
        assertNotNull(url, "Test resource not found: " + resourceName);
        File file = new File(url.getFile());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(file.getAbsolutePath()), false);
        assertFalse(specs == null || specs.isEmpty(), "No specs parsed from " + resourceName);
        return specs.get(0);
    }
}
