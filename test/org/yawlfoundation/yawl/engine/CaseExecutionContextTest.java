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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.ScopedValue;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CaseExecutionContext} record and its ScopedValue binding pattern.
 *
 * <p>Chicago TDD approach: tests verify real Java 25 invariants using the actual record
 * implementation. No mocks. Tests cover:
 * <ul>
 *   <li>Record construction validation (compact constructor guards)</li>
 *   <li>Record component accessors are generated correctly</li>
 *   <li>Record equality and hashing follow value semantics</li>
 *   <li>ScopedValue propagation to child virtual threads</li>
 *   <li>ScopedValue isolation between unrelated threads</li>
 *   <li>Factory method {@code CaseExecutionContext.of()}</li>
 *   <li>Log string formatting</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @see CaseExecutionContext
 * @see YNetRunner#CASE_CONTEXT
 */
@Tag("unit")
@DisplayName("CaseExecutionContext Record Tests")
class CaseExecutionContextTest {

    // -------------------------------------------------------------------------
    // Canonical construction
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Construction validation")
    class ConstructionValidation {

        @Test
        @DisplayName("Happy path: valid arguments produce record with correct components")
        void happyPathValidArguments() {
            Instant now = Instant.now();
            CaseExecutionContext ctx = new CaseExecutionContext("case-1", "spec:1.0", now);

            assertEquals("case-1",   ctx.caseID(),   "caseID component");
            assertEquals("spec:1.0", ctx.specID(),   "specID component");
            assertEquals(now,        ctx.startedAt(), "startedAt component");
        }

        @ParameterizedTest(name = "caseID=''{0}'' should be rejected")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Null or blank caseID throws IllegalArgumentException")
        void nullOrBlankCaseIDRejected(String badCaseID) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseExecutionContext(badCaseID, "spec:1.0", Instant.now()),
                    "Expected rejection of caseID: '" + badCaseID + "'");
        }

        @ParameterizedTest(name = "specID=''{0}'' should be rejected")
        @NullAndEmptySource
        @ValueSource(strings = {" ", "\t", "\n"})
        @DisplayName("Null or blank specID throws IllegalArgumentException")
        void nullOrBlankSpecIDRejected(String badSpecID) {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseExecutionContext("case-1", badSpecID, Instant.now()),
                    "Expected rejection of specID: '" + badSpecID + "'");
        }

        @Test
        @DisplayName("Null startedAt throws IllegalArgumentException")
        void nullStartedAtRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new CaseExecutionContext("case-1", "spec:1.0", null));
        }
    }

    // -------------------------------------------------------------------------
    // Factory method
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Factory method CaseExecutionContext.of()")
    class FactoryMethod {

        @Test
        @DisplayName("of() stamps current time and preserves IDs")
        void factoryStampsCurrentTime() {
            Instant before = Instant.now().minusMillis(1);
            CaseExecutionContext ctx = CaseExecutionContext.of("case-42", "mySpec:2.0");
            Instant after = Instant.now().plusMillis(1);

            assertEquals("case-42",   ctx.caseID());
            assertEquals("mySpec:2.0", ctx.specID());
            assertNotNull(ctx.startedAt(), "startedAt must not be null");
            assertFalse(ctx.startedAt().isBefore(before),  "startedAt should not pre-date construction");
            assertFalse(ctx.startedAt().isAfter(after),    "startedAt should not post-date construction");
        }

        @Test
        @DisplayName("Successive calls produce independent startedAt values")
        void successiveCallsProduceIndependentTimestamps() throws InterruptedException {
            CaseExecutionContext ctx1 = CaseExecutionContext.of("case-1", "spec:1.0");
            Thread.sleep(1); // ensure clock advances at least 1 ms
            CaseExecutionContext ctx2 = CaseExecutionContext.of("case-2", "spec:1.0");

            assertFalse(ctx2.startedAt().isBefore(ctx1.startedAt()),
                    "Second context must not pre-date first");
        }
    }

    // -------------------------------------------------------------------------
    // Record value semantics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Record value semantics (equals / hashCode)")
    class ValueSemantics {

        @Test
        @DisplayName("Two records with identical components are equal")
        void identicalComponentsAreEqual() {
            Instant ts = Instant.parse("2026-01-01T00:00:00Z");
            CaseExecutionContext a = new CaseExecutionContext("case-1", "spec:1.0", ts);
            CaseExecutionContext b = new CaseExecutionContext("case-1", "spec:1.0", ts);

            assertEquals(a, b, "Records with same components must be equal");
            assertEquals(a.hashCode(), b.hashCode(), "Equal records must have same hashCode");
        }

        @Test
        @DisplayName("Records with different caseID are not equal")
        void differentCaseIDNotEqual() {
            Instant ts = Instant.parse("2026-01-01T00:00:00Z");
            CaseExecutionContext a = new CaseExecutionContext("case-1", "spec:1.0", ts);
            CaseExecutionContext b = new CaseExecutionContext("case-2", "spec:1.0", ts);

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Records with different specID are not equal")
        void differentSpecIDNotEqual() {
            Instant ts = Instant.parse("2026-01-01T00:00:00Z");
            CaseExecutionContext a = new CaseExecutionContext("case-1", "spec:1.0", ts);
            CaseExecutionContext b = new CaseExecutionContext("case-1", "spec:2.0", ts);

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("Records with different startedAt are not equal")
        void differentTimestampNotEqual() {
            Instant ts1 = Instant.parse("2026-01-01T00:00:00Z");
            Instant ts2 = ts1.plus(1, ChronoUnit.SECONDS);
            CaseExecutionContext a = new CaseExecutionContext("case-1", "spec:1.0", ts1);
            CaseExecutionContext b = new CaseExecutionContext("case-1", "spec:1.0", ts2);

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("toString includes all components")
        void toStringIncludesAllComponents() {
            Instant ts = Instant.parse("2026-02-20T12:00:00Z");
            CaseExecutionContext ctx = new CaseExecutionContext("case-99", "mySpec:3.0", ts);
            String str = ctx.toString();

            assertTrue(str.contains("case-99"),    "toString must contain caseID");
            assertTrue(str.contains("mySpec:3.0"), "toString must contain specID");
            assertTrue(str.contains("2026-02-20"), "toString must contain startedAt date portion");
        }
    }

    // -------------------------------------------------------------------------
    // toLogString
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("toLogString()")
    class ToLogString {

        @Test
        @DisplayName("Format is 'case/<caseID> spec/<specID>'")
        void logStringFormat() {
            CaseExecutionContext ctx = CaseExecutionContext.of("case-7", "OrderSpec:1.2");
            assertEquals("case/case-7 spec/OrderSpec:1.2", ctx.toLogString());
        }

        @Test
        @DisplayName("Log string is stable across repeated calls")
        void logStringIsStable() {
            CaseExecutionContext ctx = CaseExecutionContext.of("case-8", "spec:5.0");
            assertEquals(ctx.toLogString(), ctx.toLogString());
        }
    }

    // -------------------------------------------------------------------------
    // ScopedValue propagation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("ScopedValue binding and propagation")
    class ScopedValuePropagation {

        /**
         * A ScopedValue that binds the full CaseExecutionContext for propagation tests.
         * This mirrors the pattern used by YNetRunner.CASE_CONTEXT but for the full record.
         */
        private static final ScopedValue<CaseExecutionContext> EXEC_CTX =
                ScopedValue.newInstance();

        @Test
        @DisplayName("callWhere makes context readable inside the scope")
        void callWhereBindsContextInsideScope() throws Exception {
            CaseExecutionContext ctx = CaseExecutionContext.of("case-10", "spec:1.0");

            CaseExecutionContext observed = ScopedValue.where(EXEC_CTX, ctx).call(() -> {
                assertTrue(EXEC_CTX.isBound(), "ScopedValue must be bound inside scope");
                return EXEC_CTX.get();
            });

            assertEquals(ctx, observed, "Observed context must match bound context");
        }

        @Test
        @DisplayName("ScopedValue is unbound outside the scope")
        void unboundOutsideScope() throws Exception {
            assertFalse(EXEC_CTX.isBound(), "ScopedValue must not be bound before callWhere");

            ScopedValue.where(EXEC_CTX, CaseExecutionContext.of("case-11", "spec:1.0"))
                    .call(() -> null);

            assertFalse(EXEC_CTX.isBound(), "ScopedValue must not be bound after callWhere exits");
        }

        @Test
        @DisplayName("ScopedValue is inherited by child virtual thread via StructuredTaskScope")
        void inheritedByChildVirtualThread() throws Exception {
            CaseExecutionContext ctx = CaseExecutionContext.of("case-12", "spec:1.0");
            AtomicReference<CaseExecutionContext> childObserved = new AtomicReference<>();

            ScopedValue.where(EXEC_CTX, ctx).call(() -> {
                // Java 25: ScopedValue bindings are propagated to StructuredTaskScope.fork()
                // subtasks, NOT to Thread.ofVirtual() — use the correct structured concurrency API.
                try (var scope = StructuredTaskScope.open()) {
                    scope.fork(() -> {
                        childObserved.set(EXEC_CTX.get());
                        return null;
                    });
                    scope.join();
                }
                return null;
            });

            assertEquals(ctx, childObserved.get(),
                    "Child virtual thread must inherit parent ScopedValue binding");
        }

        @Test
        @DisplayName("ScopedValue bindings are isolated between concurrent cases")
        void isolatedBetweenConcurrentCases() throws Exception {
            int caseCount = 20;
            List<CaseExecutionContext> contexts = new ArrayList<>();
            for (int i = 0; i < caseCount; i++) {
                contexts.add(CaseExecutionContext.of("case-" + i, "spec:1.0"));
            }

            List<CaseExecutionContext> observed = new ArrayList<>(caseCount);
            for (int i = 0; i < caseCount; i++) {
                observed.add(null);
            }
            CountDownLatch latch = new CountDownLatch(caseCount);

            try (ExecutorService vte = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < caseCount; i++) {
                    final int idx = i;
                    vte.submit(() -> {
                        try {
                            CaseExecutionContext c = contexts.get(idx);
                            CaseExecutionContext seen = ScopedValue.where(EXEC_CTX, c).call(() -> {
                                // Yield to let other virtual threads interleave
                                Thread.yield();
                                return EXEC_CTX.get();
                            });
                            synchronized (observed) {
                                observed.set(idx, seen);
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertTrue(latch.await(15, TimeUnit.SECONDS), "All cases must complete");
            }

            for (int i = 0; i < caseCount; i++) {
                assertEquals(contexts.get(i), observed.get(i),
                        "Case " + i + " must see its own context, not another's");
            }
        }

        @Test
        @DisplayName("Nested callWhere restores outer binding on exit")
        void nestedCallWhereRestoresOuter() throws Exception {
            CaseExecutionContext outer = CaseExecutionContext.of("case-outer", "spec:1.0");
            CaseExecutionContext inner = CaseExecutionContext.of("case-inner", "spec:2.0");

            CaseExecutionContext[] captured = new CaseExecutionContext[2];

            ScopedValue.where(EXEC_CTX, outer).call(() -> {
                captured[0] = EXEC_CTX.get();
                ScopedValue.where(EXEC_CTX, inner).call(() -> {
                    captured[1] = EXEC_CTX.get();
                    return null;
                });
                // outer binding should be restored after inner scope exits
                assertEquals(outer, EXEC_CTX.get(), "Outer binding must be restored after inner scope");
                return null;
            });

            assertEquals(outer, captured[0], "Outer scope must see outer context");
            assertEquals(inner, captured[1], "Inner scope must see inner context");
        }
    }

    // -------------------------------------------------------------------------
    // YNetRunner.CASE_CONTEXT (the actual production ScopedValue)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("YNetRunner.CASE_CONTEXT scoped value")
    class YNetRunnerCaseContext {

        @Test
        @DisplayName("CASE_CONTEXT is a non-null ScopedValue instance")
        void caseContextIsNonNull() {
            assertNotNull(YNetRunner.CASE_CONTEXT,
                    "YNetRunner.CASE_CONTEXT must be initialized");
        }

        @Test
        @DisplayName("CASE_CONTEXT binds String case ID")
        void caseContextBindsString() throws Exception {
            String caseID = "case-production-99";

            String observed = ScopedValue.where(YNetRunner.CASE_CONTEXT, caseID)
                    .call(() -> YNetRunner.CASE_CONTEXT.get());

            assertEquals(caseID, observed);
        }

        @Test
        @DisplayName("CASE_CONTEXT is unbound outside callWhere")
        void caseContextUnboundOutsideScope() {
            assertFalse(YNetRunner.CASE_CONTEXT.isBound(),
                    "CASE_CONTEXT must not be bound outside a callWhere scope");
        }

        @Test
        @DisplayName("CASE_CONTEXT propagates to StructuredTaskScope-forked virtual thread")
        void caseContextPropagatestoVirtualThread() throws Exception {
            String caseID = "case-forked-42";
            AtomicReference<String> childSeen = new AtomicReference<>();

            ScopedValue.where(YNetRunner.CASE_CONTEXT, caseID).call(() -> {
                // Java 25: ScopedValue bindings propagate via StructuredTaskScope.fork(),
                // not via Thread.ofVirtual() — use the structured concurrency pattern.
                try (var scope = StructuredTaskScope.open()) {
                    scope.fork(() -> {
                        childSeen.set(YNetRunner.CASE_CONTEXT.get());
                        return null;
                    });
                    scope.join();
                }
                return null;
            });

            assertEquals(caseID, childSeen.get(),
                    "Forked virtual thread must inherit CASE_CONTEXT binding");
        }
    }
}
