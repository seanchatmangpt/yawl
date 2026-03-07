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

package org.yawlfoundation.yawl.engine.fault;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD Tests for FaultInjector — Armstrong-Style Fault Injection.
 *
 * <p>Uses real fault injection (no mocks) to verify that the injector
 * correctly applies faults at specified points with correct probabilities.</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li><b>Basic Injection</b>: Verify fault injection at specific points</li>
 *   <li><b>Random Mode</b>: Verify probabilistic fault injection</li>
 *   <li><b>Scheduled Faults</b>: Verify ordered fault injection</li>
 *   <li><b>Observer Pattern</b>: Verify fault observation callbacks</li>
 *   <li><b>Thread Safety</b>: Verify concurrent access correctness</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Fault Injector Tests (Armstrong-Style)")
class FaultInjectorTest {

    private FaultInjector injector;

    @BeforeEach
    void setUp() {
        injector = new FaultInjector();
    }

    // =========================================================================
    // Basic Injection Tests
    // =========================================================================

    @Nested
    @DisplayName("Basic Fault Injection")
    class BasicInjectionTests {

        @Test
        @DisplayName("Injects process crash at specified point")
        void injectsProcessCrashAtSpecifiedPoint() {
            CrashPoint crashPoint = CrashPoint.atWorkItemCheckout(
                "task_A", FaultModel.PROCESS_CRASH);

            FaultInjector.InjectedFaultException exception = assertThrows(
                FaultInjector.ProcessCrashException.class,
                () -> injector.inject(crashPoint)
            );

            assertTrue(exception.getMessage().contains("Process Crash"));
            assertTrue(exception.getMessage().contains("task_A"));
        }

        @Test
        @DisplayName("Injects message loss fault")
        void injectsMessageLossFault() {
            CrashPoint crashPoint = CrashPoint.immediate(
                CrashPoint.InjectionPhase.MESSAGE_LOSS,
                "net_A",
                FaultModel.MESSAGE_LOSS
            );

            assertThrows(
                FaultInjector.MessageLossException.class,
                () -> injector.inject(crashPoint)
            );
        }

        @Test
        @DisplayName("Injects timing failure fault")
        void injectsTimingFailureFault() {
            CrashPoint crashPoint = CrashPoint.timingFailure(
                CrashPoint.InjectionPhase.EXTERNAL_SERVICE,
                "service_A",
                100
            );

            assertThrows(
                FaultInjector.TimingFailureException.class,
                () -> injector.inject(crashPoint)
            );
        }

        @Test
        @DisplayName("Injects Byzantine failure fault")
        void injectsByzantineFailureFault() {
            CrashPoint crashPoint = CrashPoint.atCaseStart(
                "case_123", FaultModel.BYZANTINE_FAILURE);

            assertThrows(
                FaultInjector.ByzantineFailureException.class,
                () -> injector.inject(crashPoint)
            );
        }

        @Test
        @DisplayName("Does not inject when disabled")
        void doesNotInjectWhenDisabled() {
            injector.setEnabled(false);

            CrashPoint crashPoint = CrashPoint.atCaseStart(
                "case_123", FaultModel.PROCESS_CRASH);

            // Should not throw
            assertDoesNotThrow(() -> injector.inject(crashPoint));
        }

        @Test
        @DisplayName("Respects delay before injection")
        void respectsDelayBeforeInjection() {
            CrashPoint crashPoint = new CrashPoint(
                CrashPoint.InjectionPhase.WORKITEM_CHECKOUT,
                "task_A",
                FaultModel.TIMING_FAILURE,
                50 // 50ms delay
            );

            long start = System.currentTimeMillis();
            assertThrows(FaultInjector.TimingFailureException.class,
                () -> injector.inject(crashPoint));
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed >= 50, "Should wait at least 50ms before injecting");
        }
    }

    // =========================================================================
    // Random Mode Tests
    // =========================================================================

    @Nested
    @DisplayName("Random Mode Fault Injection")
    class RandomModeTests {

        @Test
        @DisplayName("Injects faults with configured probability")
        void injectsFaultsWithConfiguredProbability() {
            injector.setRandomMode(true);
            injector.setFaultProbability(0.5); // 50% chance

            int injections = 0;
            int totalAttempts = 1000;

            for (int i = 0; i < totalAttempts; i++) {
                try {
                    injector.maybeInject(
                        CrashPoint.InjectionPhase.WORKITEM_CHECKOUT,
                        "task_" + i
                    );
                } catch (FaultInjector.InjectedFaultException e) {
                    injections++;
                }
            }

            // With 50% probability and 1000 attempts, expect ~500 injections
            // Allow 20% tolerance (400-600)
            assertTrue(injections >= 400 && injections <= 600,
                "Expected ~500 injections (±100), got: " + injections);
        }

        @Test
        @DisplayName("Does not inject when probability is 0")
        void doesNotInjectWhenProbabilityIsZero() {
            injector.setRandomMode(true);
            injector.setFaultProbability(0.0);

            int injections = 0;
            for (int i = 0; i < 100; i++) {
                try {
                    injector.maybeInject(
                        CrashPoint.InjectionPhase.WORKITEM_CHECKOUT,
                        "task_" + i
                    );
                } catch (FaultInjector.InjectedFaultException e) {
                    injections++;
                }
            }

            assertEquals(0, injections, "Should not inject with 0% probability");
        }

        @Test
        @DisplayName("Always injects when probability is 1")
        void alwaysInjectsWhenProbabilityIsOne() {
            injector.setRandomMode(true);
            injector.setFaultProbability(1.0);

            int injections = 0;
            for (int i = 0; i < 100; i++) {
                try {
                    injector.maybeInject(
                        CrashPoint.InjectionPhase.WORKITEM_CHECKOUT,
                        "task_" + i
                    );
                } catch (FaultInjector.InjectedFaultException e) {
                    injections++;
                }
            }

            assertEquals(100, injections, "Should always inject with 100% probability");
        }

        @Test
        @DisplayName("Rejects invalid probability values")
        void rejectsInvalidProbabilityValues() {
            assertThrows(IllegalArgumentException.class,
                () -> injector.setFaultProbability(-0.1));
            assertThrows(IllegalArgumentException.class,
                () -> injector.setFaultProbability(1.1));
        }
    }

    // =========================================================================
    // Scheduled Faults Tests
    // =========================================================================

    @Nested
    @DisplayName("Scheduled Fault Injection")
    class ScheduledFaultsTests {

        @Test
        @DisplayName("Injects scheduled faults in order")
        void injectsScheduledFaultsInOrder() {
            List<String> injectedFaults = new ArrayList<>();

            injector.addFaultPoint(CrashPoint.atWorkItemCheckout("A", FaultModel.PROCESS_CRASH));
            injector.addFaultPoint(CrashPoint.atWorkItemCheckin("B", FaultModel.MESSAGE_LOSS));

            injector.onFault(fault -> injectedFaults.add(fault.crashPoint().targetId()));

            // First injection should trigger first scheduled fault
            try {
                injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_CHECKOUT, "A");
            } catch (FaultInjector.ProcessCrashException e) {
                // Expected
            }

            // Second injection should trigger second scheduled fault
            try {
                injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_CHECKIN, "B");
            } catch (FaultInjector.MessageLossException e) {
                // Expected
            }

            assertEquals(List.of("A", "B"), injectedFaults);
        }

        @Test
        @DisplayName("Only injects scheduled fault at matching phase")
        void onlyInjectsScheduledFaultAtMatchingPhase() {
            injector.addFaultPoint(CrashPoint.atWorkItemCheckout("A", FaultModel.PROCESS_CRASH));

            // Try different phase - should not inject
            assertDoesNotThrow(() ->
                injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_ENABLE, "A")
            );

            // Try matching phase - should inject
            assertThrows(FaultInjector.ProcessCrashException.class,
                () -> injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_CHECKOUT, "A")
            );
        }

        @Test
        @DisplayName("Clears scheduled faults correctly")
        void clearsScheduledFaultsCorrectly() {
            injector.addFaultPoint(CrashPoint.atWorkItemCheckout("A", FaultModel.PROCESS_CRASH));
            injector.clearScheduledFaults();

            // Should not inject after clearing
            assertDoesNotThrow(() ->
                injector.maybeInject(CrashPoint.InjectionPhase.WORKITEM_CHECKOUT, "A")
            );
        }
    }

    // =========================================================================
    // Observer Pattern Tests
    // =========================================================================

    @Nested
    @DisplayName("Fault Observer Pattern")
    class ObserverPatternTests {

        @Test
        @DisplayName("Notifies observers on fault injection")
        void notifiesObserversOnFaultInjection() {
            List<FaultInjector.InjectedFault> observedFaults = new ArrayList<>();
            injector.onFault(observedFaults::add);

            CrashPoint crashPoint = CrashPoint.atCaseStart("case_123", FaultModel.PROCESS_CRASH);

            try {
                injector.inject(crashPoint);
            } catch (FaultInjector.ProcessCrashException e) {
                // Expected
            }

            assertEquals(1, observedFaults.size());
            assertEquals(crashPoint, observedFaults.get(0).crashPoint());
        }

        @Test
        @DisplayName("Records context at injection time")
        void recordsContextAtInjectionTime() {
            List<FaultInjector.InjectedFault> observedFaults = new ArrayList<>();
            injector.onFault(observedFaults::add);

            injector.setContext("caseId", "case_123");
            injector.setContext("taskId", "task_A");

            try {
                injector.inject(CrashPoint.atWorkItemCheckout("task_A", FaultModel.PROCESS_CRASH));
            } catch (FaultInjector.ProcessCrashException e) {
                // Expected
            }

            assertEquals(1, observedFaults.size());
            assertEquals("case_123", observedFaults.get(0).context().get("caseId"));
            assertEquals("task_A", observedFaults.get(0).context().get("taskId"));
        }

        @Test
        @DisplayName("Counts faults by type")
        void countsFaultsByType() {
            for (int i = 0; i < 5; i++) {
                try {
                    injector.inject(CrashPoint.atCaseStart(
                        "case_" + i, FaultModel.PROCESS_CRASH));
                } catch (FaultInjector.ProcessCrashException e) {
                    // Expected
                }
            }

            for (int i = 0; i < 3; i++) {
                try {
                    injector.inject(CrashPoint.atCaseStart(
                        "case_" + i, FaultModel.MESSAGE_LOSS));
                } catch (FaultInjector.MessageLossException e) {
                    // Expected
                }
            }

            assertEquals(5, injector.getFaultCount(FaultModel.PROCESS_CRASH));
            assertEquals(3, injector.getFaultCount(FaultModel.MESSAGE_LOSS));
            assertEquals(8, injector.getTotalFaultCount());
        }
    }

    // =========================================================================
    // Thread Safety Tests
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Handles concurrent fault injection safely")
        void handlesConcurrentFaultInjectionSafely() throws Exception {
            injector.setRandomMode(true);
            injector.setFaultProbability(0.1);

            AtomicInteger injectionCount = new AtomicInteger(0);
            injector.onFault(f -> injectionCount.incrementAndGet());

            int threadCount = 100;
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 100; j++) {
                            try {
                                injector.maybeInject(
                                    CrashPoint.InjectionPhase.WORKITEM_CHECKOUT,
                                    "task_" + threadId + "_" + j
                                );
                            } catch (FaultInjector.InjectedFaultException e) {
                                // Expected
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            assertTrue(completed, "All threads should complete");

            executor.shutdown();

            // Verify some faults were injected (with 10% probability, 10000 attempts)
            assertTrue(injectionCount.get() > 0, "Should have injected some faults");
        }
    }

    // =========================================================================
    // Reset Tests
    // =========================================================================

    @Nested
    @DisplayName("Reset Functionality")
    class ResetTests {

        @Test
        @DisplayName("Resets all counters and scheduled faults")
        void resetsAllCountersAndScheduledFaults() {
            // Inject some faults
            try {
                injector.inject(CrashPoint.atCaseStart("A", FaultModel.PROCESS_CRASH));
            } catch (Exception e) { }
            try {
                injector.inject(CrashPoint.atCaseStart("B", FaultModel.MESSAGE_LOSS));
            } catch (Exception e) { }

            injector.addFaultPoint(CrashPoint.atCaseStart("C", FaultModel.PROCESS_CRASH));

            // Reset
            injector.reset();

            // Verify reset
            assertEquals(0, injector.getTotalFaultCount());
            assertEquals(0, injector.getFaultCount(FaultModel.PROCESS_CRASH));
            assertEquals(0, injector.getFaultCount(FaultModel.MESSAGE_LOSS));

            // Scheduled faults should be cleared
            assertDoesNotThrow(() ->
                injector.maybeInject(CrashPoint.InjectionPhase.CASE_START, "C")
            );
        }
    }
}
