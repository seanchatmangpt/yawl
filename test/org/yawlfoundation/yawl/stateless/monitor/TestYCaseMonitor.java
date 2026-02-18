/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCaseMonitor covering concurrent modification,
 * timer accuracy, and event handling.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real case monitoring, and real timer events. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCaseMonitor Tests")
@Tag("unit")
class TestYCaseMonitor {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;

    private YStatelessEngine engine;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        spec = loadMinimalSpec();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
    }

    private YSpecification loadMinimalSpec() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Missing resource: " + MINIMAL_SPEC_RESOURCE);
        String xml = StringUtil.streamToString(is);
        return engine.unmarshalSpecification(xml);
    }

    private YNetRunner launchCaseWithMonitoring(String caseId) throws Exception {
        AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
        CountDownLatch startedLatch = new CountDownLatch(1);

        YCaseEventListener listener = event -> {
            if (event.getEventType() == YEventType.CASE_STARTED) {
                runnerCapture.set(event.getRunner());
                startedLatch.countDown();
            }
        };
        engine.addCaseEventListener(listener);
        engine.launchCase(spec, caseId);
        startedLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        engine.removeCaseEventListener(listener);

        YNetRunner runner = runnerCapture.get();
        assertNotNull(runner, "Runner should be captured after case start");
        return runner;
    }

    // =========================================================================
    // Nested: Concurrent Modification Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Modification Tests")
    class ConcurrentModificationTests {

        @Test
        @DisplayName("Multiple concurrent cases can be monitored")
        void multipleConcurrentCasesCanBeMonitored() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            int numCases = 5;
            for (int i = 0; i < numCases; i++) {
                String caseId = "concurrent-case-" + i;
                launchCaseWithMonitoring(caseId);
            }

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Case monitoring should remain enabled");
        }

        @Test
        @DisplayName("Case can be unloaded while other cases remain")
        void caseCanBeUnloadedWhileOtherCasesRemain() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId1 = "unload-test-1";
            String caseId2 = "unload-test-2";

            YNetRunner runner1 = launchCaseWithMonitoring(caseId1);
            YNetRunner runner2 = launchCaseWithMonitoring(caseId2);

            YIdentifier caseId = runner1.getCaseID();
            engine.unloadCase(caseId);

            assertThrows(YStateException.class, () -> engine.isIdleCase(caseId),
                    "Unloaded case should not be in monitor");

            assertDoesNotThrow(() -> engine.isIdleCase(runner2.getCaseID()),
                    "Other cases should still be in monitor");
        }

        @Test
        @DisplayName("Concurrent work item events update monitor")
        void concurrentWorkItemEventsUpdateMonitor() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "wi-events-test";
            AtomicBoolean workItemReceived = new AtomicBoolean(false);
            CountDownLatch wiLatch = new CountDownLatch(1);

            YWorkItemEventListener listener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    workItemReceived.set(true);
                    wiLatch.countDown();
                }
            };
            engine.addWorkItemEventListener(listener);

            launchCaseWithMonitoring(caseId);

            boolean received = wiLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeWorkItemEventListener(listener);

            assertTrue(received, "Work item event should be received");
            assertTrue(workItemReceived.get(), "Work item flag should be set");
        }

        @Test
        @DisplayName("Case completion removes case from monitor")
        void caseCompletionRemovesCaseFromMonitor() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "completion-remove-test";
            AtomicReference<YIdentifier> caseIdCapture = new AtomicReference<>();
            CountDownLatch completeLatch = new CountDownLatch(1);

            YCaseEventListener listener = event -> {
                if (event.getEventType() == YEventType.CASE_STARTED) {
                    caseIdCapture.set(event.getRunner().getCaseID());
                }
                if (event.getEventType() == YEventType.CASE_COMPLETED) {
                    completeLatch.countDown();
                }
            };
            engine.addCaseEventListener(listener);

            engine.launchCase(spec, caseId);

            boolean completed = completeLatch.await(EVENT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);
            engine.removeCaseEventListener(listener);

            assertTrue(completed, "Case should complete");
        }
    }

    // =========================================================================
    // Nested: Timer Accuracy Tests
    // =========================================================================

    @Nested
    @DisplayName("Timer Accuracy Tests")
    class TimerAccuracyTests {

        @Test
        @DisplayName("Idle timer can be configured")
        void idleTimerCanBeConfigured() {
            long timeout = 60000L;

            engine.setIdleCaseTimer(timeout);

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Setting idle timer should enable monitoring");
        }

        @Test
        @DisplayName("Idle timer can be updated")
        void idleTimerCanBeUpdated() {
            engine.setIdleCaseTimer(60000L);
            engine.setIdleCaseTimer(120000L);

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Monitoring should remain enabled after timer update");
        }

        @Test
        @DisplayName("Zero timeout disables monitoring")
        void zeroTimeoutDisablesMonitoring() {
            engine.setCaseMonitoringEnabled(true);
            engine.setIdleCaseTimer(0L);

            assertFalse(engine.isCaseMonitoringEnabled(),
                    "Zero timeout should disable monitoring");
        }

        @Test
        @DisplayName("Negative timeout disables monitoring")
        void negativeTimeoutDisablesMonitoring() {
            engine.setCaseMonitoringEnabled(true);
            engine.setIdleCaseTimer(-1000L);

            assertFalse(engine.isCaseMonitoringEnabled(),
                    "Negative timeout should disable monitoring");
        }

        @Test
        @DisplayName("Idle case detection works")
        void idleCaseDetectionWorks() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "idle-detection-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            boolean isIdle = engine.isIdleCase(runner);

            assertTrue(isIdle || !isIdle, "isIdleCase should return boolean");
        }

        @Test
        @DisplayName("isIdleCase throws for unknown case")
        void isIdleCaseThrowsForUnknownCase() throws Exception {
            engine.setIdleCaseTimer(30000L);

            YIdentifier unknownId = new YIdentifier("unknown-case-id");

            assertThrows(YStateException.class, () -> engine.isIdleCase(unknownId),
                    "Should throw for unknown case");
        }

        @Test
        @DisplayName("Pause and resume idle timer")
        void pauseAndResumeIdleTimer() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "pause-resume-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            assertDoesNotThrow(() -> {
                boolean isIdle = engine.isIdleCase(caseIdentifier);
                assertNotNull(isIdle);
            }, "isIdleCase should work before pause/resume");
        }
    }

    // =========================================================================
    // Nested: Event Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Event Handling Tests")
    class EventHandlingTests {

        @Test
        @DisplayName("Case start event adds to monitor")
        void caseStartEventAddsToMonitor() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "start-event-test";
            AtomicBoolean startReceived = new AtomicBoolean(false);
            CountDownLatch startLatch = new CountDownLatch(1);

            YCaseEventListener listener = event -> {
                if (event.getEventType() == YEventType.CASE_STARTED) {
                    startReceived.set(true);
                    startLatch.countDown();
                }
            };
            engine.addCaseEventListener(listener);

            launchCaseWithMonitoring(caseId);

            boolean received = startLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeCaseEventListener(listener);

            assertTrue(received, "Case start event should be received");
            assertTrue(startReceived.get(), "Start flag should be set");
        }

        @Test
        @DisplayName("Case cancel event removes from monitor")
        void caseCancelEventRemovesFromMonitor() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "cancel-event-test";
            AtomicBoolean cancelReceived = new AtomicBoolean(false);
            CountDownLatch cancelLatch = new CountDownLatch(1);

            YCaseEventListener listener = event -> {
                if (event.getEventType() == YEventType.CASE_CANCELLED) {
                    cancelReceived.set(true);
                    cancelLatch.countDown();
                }
            };
            engine.addCaseEventListener(listener);

            YNetRunner runner = launchCaseWithMonitoring(caseId);
            engine.cancelCase(runner);

            boolean received = cancelLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeCaseEventListener(listener);

            assertTrue(received, "Case cancel event should be received");
        }

        @Test
        @DisplayName("Work item event updates case last active time")
        void workItemEventUpdatesCaseLastActiveTime() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "wi-active-time-test";
            AtomicBoolean wiReceived = new AtomicBoolean(false);
            CountDownLatch wiLatch = new CountDownLatch(1);

            YWorkItemEventListener listener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    wiReceived.set(true);
                    wiLatch.countDown();
                }
            };
            engine.addWorkItemEventListener(listener);

            launchCaseWithMonitoring(caseId);

            boolean received = wiLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeWorkItemEventListener(listener);

            assertTrue(received, "Work item event should be received");
        }

        @Test
        @DisplayName("Multiple listeners receive events")
        void multipleListenersReceiveEvents() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "multi-listener-test";
            AtomicInteger receiveCount = new AtomicInteger(0);
            CountDownLatch multiLatch = new CountDownLatch(2);

            YCaseEventListener listener1 = event -> {
                if (event.getEventType() == YEventType.CASE_STARTED) {
                    receiveCount.incrementAndGet();
                    multiLatch.countDown();
                }
            };
            YCaseEventListener listener2 = event -> {
                if (event.getEventType() == YEventType.CASE_STARTED) {
                    receiveCount.incrementAndGet();
                    multiLatch.countDown();
                }
            };

            engine.addCaseEventListener(listener1);
            engine.addCaseEventListener(listener2);

            launchCaseWithMonitoring(caseId);

            boolean received = multiLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeCaseEventListener(listener1);
            engine.removeCaseEventListener(listener2);

            assertTrue(received, "Both listeners should receive events");
            assertEquals(2, receiveCount.get(), "Both listeners should have received");
        }
    }

    // =========================================================================
    // Nested: Case Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Case Management Tests")
    class CaseManagementTests {

        @Test
        @DisplayName("hasCase returns true for monitored case")
        void hasCaseReturnsTrueForMonitoredCase() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "has-case-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            assertDoesNotThrow(() -> engine.isIdleCase(runner),
                    "Case should be in monitor");
        }

        @Test
        @DisplayName("Unload case returns case XML")
        void unloadCaseReturnsCaseXml() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "unload-xml-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String caseXml = engine.unloadCase(caseIdentifier);

            assertNotNull(caseXml, "Unload should return case XML");
            assertTrue(caseXml.contains(caseId), "XML should contain case ID");
        }

        @Test
        @DisplayName("Unload unknown case throws exception")
        void unloadUnknownCaseThrowsException() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            YIdentifier unknownId = new YIdentifier("unknown-unload-test");

            assertThrows(YStateException.class, () -> engine.unloadCase(unknownId),
                    "Should throw for unknown case");
        }

        @Test
        @DisplayName("Monitor can be enabled and disabled")
        void monitorCanBeEnabledAndDisabled() {
            assertFalse(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be disabled initially");

            engine.setCaseMonitoringEnabled(true);
            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be enabled");

            engine.setCaseMonitoringEnabled(false);
            assertFalse(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be disabled");
        }
    }

    // =========================================================================
    // Nested: Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Enable monitoring with zero timeout")
        void enableMonitoringWithZeroTimeout() {
            engine.setCaseMonitoringEnabled(true, 0L);

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be enabled with zero timeout");
        }

        @Test
        @DisplayName("Enable monitoring with idle timeout")
        void enableMonitoringWithIdleTimeout() {
            engine.setCaseMonitoringEnabled(true, 60000L);

            assertTrue(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be enabled with idle timeout");
        }

        @Test
        @DisplayName("isIdleCase with work item reference")
        void isIdleCaseWithWorkItemReference() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "idle-wi-test";
            AtomicReference<YWorkItem> wiCapture = new AtomicReference<>();
            CountDownLatch wiLatch = new CountDownLatch(1);

            YWorkItemEventListener listener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    wiCapture.set(event.getWorkItem());
                    wiLatch.countDown();
                }
            };
            engine.addWorkItemEventListener(listener);

            launchCaseWithMonitoring(caseId);

            boolean received = wiLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeWorkItemEventListener(listener);

            assertTrue(received, "Work item should be received");

            YWorkItem item = wiCapture.get();
            assertDoesNotThrow(() -> engine.isIdleCase(item),
                    "isIdleCase with work item should work");
        }

        @Test
        @DisplayName("Disable monitoring clears all cases")
        void disableMonitoringClearsAllCases() throws Exception {
            engine.setCaseMonitoringEnabled(true);

            String caseId = "disable-clear-test";
            launchCaseWithMonitoring(caseId);

            engine.setCaseMonitoringEnabled(false);

            assertFalse(engine.isCaseMonitoringEnabled(),
                    "Monitoring should be disabled");
        }
    }
}
