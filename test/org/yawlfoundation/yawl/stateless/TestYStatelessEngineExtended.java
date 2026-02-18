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

package org.yawlfoundation.yawl.stateless;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
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
 * Extended comprehensive tests for YStatelessEngine covering malformed XML handling,
 * timeout configuration, multi-threaded announcements, and complex pattern execution.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real YSpecification objects, and real concurrent operations. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YStatelessEngine Extended Tests")
@Tag("unit")
class TestYStatelessEngineExtended {

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

    // =========================================================================
    // Nested: Malformed XML Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Malformed XML Handling Tests")
    class MalformedXmlHandlingTests {

        @Test
        @DisplayName("Empty XML throws YSyntaxException")
        void emptyXmlThrowsSyntaxException() {
            assertThrows(YSyntaxException.class, () ->
                    engine.unmarshalSpecification(""),
                    "Empty XML should throw YSyntaxException");
        }

        @Test
        @DisplayName("Null XML throws exception")
        void nullXmlThrowsException() {
            assertThrows(Exception.class, () ->
                    engine.unmarshalSpecification(null),
                    "Null XML should throw exception");
        }

        @Test
        @DisplayName("Invalid XML structure throws YSyntaxException")
        void invalidXmlStructureThrowsSyntaxException() {
            String invalidXml = "<not-a-specification></not-a-specification>";

            assertThrows(YSyntaxException.class, () ->
                    engine.unmarshalSpecification(invalidXml),
                    "Invalid XML should throw YSyntaxException");
        }

        @Test
        @DisplayName("Malformed specification throws YSyntaxException")
        void malformedSpecificationThrowsSyntaxException() {
            String malformedXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0">
                  <specification>
                    <invalid>content</invalid>
                  </specification>
                </specificationSet>
                """;

            assertThrows(YSyntaxException.class, () ->
                    engine.unmarshalSpecification(malformedXml),
                    "Malformed specification should throw");
        }

        @Test
        @DisplayName("Missing root net throws exception")
        void missingRootNetThrowsException() {
            String noRootNetXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <specificationSet version="4.0" xmlns="http://www.yawlfoundation.org/yawlschema">
                  <specification uri="NoRootNet">
                    <decomposition id="SubNet" xsi:type="NetFactsType">
                      <processControlElements>
                        <inputCondition id="start"/>
                      </processControlElements>
                    </decomposition>
                  </specification>
                </specificationSet>
                """;

            assertThrows(Exception.class, () ->
                    engine.unmarshalSpecification(noRootNetXml),
                    "Missing root net should throw");
        }

        @Test
        @DisplayName("Restore with invalid XML throws exception")
        void restoreWithInvalidXmlThrowsException() {
            String invalidCaseXml = "<case><invalid></case>";

            assertThrows(Exception.class, () ->
                    engine.restoreCase(invalidCaseXml),
                    "Invalid restore XML should throw");
        }

        @Test
        @DisplayName("Restore with null XML throws exception")
        void restoreWithNullXmlThrowsException() {
            assertThrows(Exception.class, () ->
                    engine.restoreCase(null),
                    "Null restore XML should throw");
        }
    }

    // =========================================================================
    // Nested: Timeout Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Timeout Configuration Tests")
    class TimeoutConfigurationTests {

        @Test
        @DisplayName("Idle timer can be set via constructor")
        void idleTimerCanBeSetViaConstructor() {
            YStatelessEngine engineWithTimeout = new YStatelessEngine(60000L);

            assertTrue(engineWithTimeout.isCaseMonitoringEnabled(),
                    "Engine with timeout should have monitoring enabled");

            engineWithTimeout.setCaseMonitoringEnabled(false);
        }

        @Test
        @DisplayName("Idle timer can be updated")
        void idleTimerCanBeUpdated() {
            engine.setIdleCaseTimer(30000L);
            assertTrue(engine.isCaseMonitoringEnabled());

            engine.setIdleCaseTimer(60000L);
            assertTrue(engine.isCaseMonitoringEnabled());
        }

        @Test
        @DisplayName("Zero timeout disables monitoring")
        void zeroTimeoutDisablesMonitoring() {
            engine.setIdleCaseTimer(30000L);
            assertTrue(engine.isCaseMonitoringEnabled());

            engine.setIdleCaseTimer(0L);
            assertFalse(engine.isCaseMonitoringEnabled());
        }

        @Test
        @DisplayName("Negative timeout disables monitoring")
        void negativeTimeoutDisablesMonitoring() {
            engine.setIdleCaseTimer(30000L);
            assertTrue(engine.isCaseMonitoringEnabled());

            engine.setIdleCaseTimer(-1000L);
            assertFalse(engine.isCaseMonitoringEnabled());
        }

        @Test
        @DisplayName("Enable monitoring without timeout")
        void enableMonitoringWithoutTimeout() {
            engine.setCaseMonitoringEnabled(true);

            assertTrue(engine.isCaseMonitoringEnabled());
        }

        @Test
        @DisplayName("Enable monitoring with timeout")
        void enableMonitoringWithTimeout() {
            engine.setCaseMonitoringEnabled(true, 60000L);

            assertTrue(engine.isCaseMonitoringEnabled());
        }

        @Test
        @DisplayName("Engine number is unique per instance")
        void engineNumberIsUniquePerInstance() {
            YStatelessEngine engine1 = new YStatelessEngine();
            YStatelessEngine engine2 = new YStatelessEngine();

            assertNotEquals(engine1.getEngineNbr(), engine2.getEngineNbr(),
                    "Each engine should have unique number");
        }
    }

    // =========================================================================
    // Nested: Multi-Threaded Announcements Tests
    // =========================================================================

    @Nested
    @DisplayName("Multi-Threaded Announcements Tests")
    class MultiThreadedAnnouncementsTests {

        @Test
        @DisplayName("Multi-threaded announcements disabled by default")
        void multiThreadedAnnouncementsDisabledByDefault() {
            assertFalse(engine.isMultiThreadedAnnouncementsEnabled(),
                    "Multi-threaded should be disabled by default");
        }

        @Test
        @DisplayName("Multi-threaded announcements can be enabled")
        void multiThreadedAnnouncementsCanBeEnabled() {
            engine.enableMultiThreadedAnnouncements(true);

            assertTrue(engine.isMultiThreadedAnnouncementsEnabled(),
                    "Multi-threaded should be enabled");
        }

        @Test
        @DisplayName("Multi-threaded announcements can be disabled")
        void multiThreadedAnnouncementsCanBeDisabled() {
            engine.enableMultiThreadedAnnouncements(true);
            assertTrue(engine.isMultiThreadedAnnouncementsEnabled());

            engine.enableMultiThreadedAnnouncements(false);
            assertFalse(engine.isMultiThreadedAnnouncementsEnabled(),
                    "Multi-threaded should be disabled");
        }

        @Test
        @DisplayName("Case completes with multi-threaded enabled")
        void caseCompletesWithMultiThreadedEnabled() throws Exception {
            engine.enableMultiThreadedAnnouncements(true);

            CountDownLatch completeLatch = new CountDownLatch(1);
            AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
            AtomicInteger workItemCount = new AtomicInteger(0);

            YCaseEventListener caseListener = event -> {
                if (event.getEventType() == YEventType.CASE_STARTED) {
                    runnerCapture.set(event.getRunner());
                }
                if (event.getEventType() == YEventType.CASE_COMPLETED) {
                    completeLatch.countDown();
                }
            };

            YWorkItemEventListener wiListener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    workItemCount.incrementAndGet();
                    try {
                        YWorkItem item = event.getWorkItem();
                        engine.startWorkItem(item);
                        engine.completeWorkItem(item, "<data/>", null);
                    } catch (Exception e) {
                    }
                }
            };

            engine.addCaseEventListener(caseListener);
            engine.addWorkItemEventListener(wiListener);

            engine.launchCase(spec);

            boolean completed = completeLatch.await(EVENT_TIMEOUT_SEC * 2, TimeUnit.SECONDS);

            engine.removeCaseEventListener(caseListener);
            engine.removeWorkItemEventListener(wiListener);

            assertTrue(completed, "Case should complete with multi-threaded enabled");
        }

        @Test
        @DisplayName("Concurrent cases with multi-threaded announcements")
        void concurrentCasesWithMultiThreadedAnnouncements() throws Exception {
            engine.enableMultiThreadedAnnouncements(true);

            int numCases = 5;
            CountDownLatch completeLatch = new CountDownLatch(numCases);
            AtomicInteger completedCount = new AtomicInteger(0);

            YCaseEventListener listener = event -> {
                if (event.getEventType() == YEventType.CASE_COMPLETED) {
                    completedCount.incrementAndGet();
                    completeLatch.countDown();
                }
            };
            engine.addCaseEventListener(listener);

            YWorkItemEventListener wiListener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    try {
                        YWorkItem item = event.getWorkItem();
                        engine.startWorkItem(item);
                        engine.completeWorkItem(item, "<data/>", null);
                    } catch (Exception e) {
                    }
                }
            };
            engine.addWorkItemEventListener(wiListener);

            for (int i = 0; i < numCases; i++) {
                engine.launchCase(spec, "mt-case-" + i);
            }

            boolean completed = completeLatch.await(EVENT_TIMEOUT_SEC * 3, TimeUnit.SECONDS);

            engine.removeCaseEventListener(listener);
            engine.removeWorkItemEventListener(wiListener);

            assertTrue(completed, "All cases should complete");
            assertEquals(numCases, completedCount.get(), "All cases should be counted");
        }
    }

    // =========================================================================
    // Nested: Complex Pattern Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Complex Pattern Execution Tests")
    class ComplexPatternExecutionTests {

        @Test
        @DisplayName("Launch multiple sequential cases")
        void launchMultipleSequentialCases() throws Exception {
            int numCases = 3;

            for (int i = 0; i < numCases; i++) {
                String caseId = "sequential-case-" + i;
                YNetRunner runner = engine.launchCase(spec, caseId);

                assertNotNull(runner, "Runner should not be null");
                assertEquals(caseId, runner.getCaseID().toString());
            }
        }

        @Test
        @DisplayName("Launch case with UUID case ID")
        void launchCaseWithUuidCaseId() throws Exception {
            YNetRunner runner = engine.launchCase(spec);

            assertNotNull(runner);
            assertNotNull(runner.getCaseID());
            assertFalse(runner.getCaseID().toString().isEmpty());
        }

        @Test
        @DisplayName("Launch case with explicit case ID")
        void launchCaseWithExplicitCaseId() throws Exception {
            String explicitId = "explicit-case-id-123";
            YNetRunner runner = engine.launchCase(spec, explicitId);

            assertEquals(explicitId, runner.getCaseID().toString());
        }

        @Test
        @DisplayName("Launch case with case parameters")
        void launchCaseWithCaseParameters() throws Exception {
            String caseId = "params-case";
            String caseParams = "<data><param>value</param></data>";

            YNetRunner runner = engine.launchCase(spec, caseId, caseParams);

            assertNotNull(runner);
            assertEquals(caseId, runner.getCaseID().toString());
        }

        @Test
        @DisplayName("Launch case with null parameters succeeds")
        void launchCaseWithNullParametersSucceeds() throws Exception {
            String caseId = "null-params-case";

            YNetRunner runner = engine.launchCase(spec, caseId, null);

            assertNotNull(runner);
        }

        @Test
        @DisplayName("Cancel case stops all work items")
        void cancelCaseStopsAllWorkItems() throws Exception {
            CountDownLatch enabledLatch = new CountDownLatch(1);
            AtomicReference<YWorkItem> itemCapture = new AtomicReference<>();

            YWorkItemEventListener listener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    itemCapture.set(event.getWorkItem());
                    enabledLatch.countDown();
                }
            };
            engine.addWorkItemEventListener(listener);

            YNetRunner runner = engine.launchCase(spec, "cancel-test");

            boolean enabled = enabledLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeWorkItemEventListener(listener);

            assertTrue(enabled, "Work item should be enabled");

            engine.cancelCase(runner);

            assertFalse(runner.isAlive(), "Runner should not be alive after cancel");
        }

        @Test
        @DisplayName("Suspend and resume case")
        void suspendAndResumeCase() throws Exception {
            YNetRunner runner = engine.launchCase(spec, "suspend-resume-test");

            assertTrue(runner.isAlive(), "Runner should be alive");

            engine.suspendCase(runner);

            assertTrue(runner.isSuspending() || runner.isSuspended(),
                    "Runner should be suspending or suspended");

            engine.resumeCase(runner);

            assertTrue(runner.hasNormalState(),
                    "Runner should have normal state after resume");
        }
    }

    // =========================================================================
    // Nested: Listener Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Listener Management Tests")
    class ListenerManagementTests {

        @Test
        @DisplayName("Add and remove case event listener")
        void addAndRemoveCaseEventListener() {
            YCaseEventListener listener = event -> {};

            boolean added = engine.addCaseEventListener(listener);
            assertTrue(added, "Listener should be added");

            boolean removed = engine.removeCaseEventListener(listener);
            assertTrue(removed, "Listener should be removed");
        }

        @Test
        @DisplayName("Add and remove work item event listener")
        void addAndRemoveWorkItemEventListener() {
            YWorkItemEventListener listener = event -> {};

            boolean added = engine.addWorkItemEventListener(listener);
            assertTrue(added, "Listener should be added");

            boolean removed = engine.removeWorkItemEventListener(listener);
            assertTrue(removed, "Listener should be removed");
        }

        @Test
        @DisplayName("Add same listener twice returns false")
        void addSameListenerTwiceReturnsFalse() {
            YCaseEventListener listener = event -> {};

            boolean first = engine.addCaseEventListener(listener);
            assertTrue(first, "First add should succeed");

            boolean second = engine.addCaseEventListener(listener);
            assertFalse(second, "Second add should return false (already present)");
        }

        @Test
        @DisplayName("Remove non-existent listener returns false")
        void removeNonExistentListenerReturnsFalse() {
            YCaseEventListener listener = event -> {};

            boolean removed = engine.removeCaseEventListener(listener);
            assertFalse(removed, "Remove non-existent should return false");
        }
    }

    // =========================================================================
    // Nested: Error Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Start work item on cancelled case throws")
        void startWorkItemOnCancelledCaseThrows() throws Exception {
            AtomicReference<YWorkItem> itemCapture = new AtomicReference<>();
            CountDownLatch enabledLatch = new CountDownLatch(1);

            YWorkItemEventListener listener = event -> {
                if (event.getEventType() == YEventType.ITEM_ENABLED) {
                    itemCapture.set(event.getWorkItem());
                    enabledLatch.countDown();
                }
            };
            engine.addWorkItemEventListener(listener);

            YNetRunner runner = engine.launchCase(spec, "error-cancel-test");
            enabledLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
            engine.removeWorkItemEventListener(listener);

            YWorkItem item = itemCapture.get();
            assertNotNull(item);

            engine.cancelCase(runner);

            assertThrows(YStateException.class, () -> engine.startWorkItem(item),
                    "Starting work item on cancelled case should throw");
        }

        @Test
        @DisplayName("Marshal case with null runner throws")
        void marshalCaseWithNullRunnerThrows() {
            assertThrows(YStateException.class, () -> engine.marshalCase(null),
                    "Marshal with null runner should throw");
        }

        @Test
        @DisplayName("Unload case without monitoring throws")
        void unloadCaseWithoutMonitoringThrows() throws Exception {
            assertFalse(engine.isCaseMonitoringEnabled());

            YNetRunner runner = engine.launchCase(spec, "unload-no-monitor");

            assertThrows(YStateException.class, () -> engine.unloadCase(runner.getCaseID()),
                    "Unload without monitoring should throw");
        }

        @Test
        @DisplayName("Is idle case without monitoring throws")
        void isIdleCaseWithoutMonitoringThrows() throws Exception {
            assertFalse(engine.isCaseMonitoringEnabled());

            YNetRunner runner = engine.launchCase(spec, "idle-no-monitor");

            assertThrows(YStateException.class, () -> engine.isIdleCase(runner),
                    "isIdleCase without monitoring should throw");
        }
    }

    // =========================================================================
    // Nested: High Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("High Concurrency Tests")
    class HighConcurrencyTests {

        @Test
        @DisplayName("High volume concurrent case launches")
        void highVolumeConcurrentCaseLaunches() throws Exception {
            int numCases = 20;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(numCases);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < numCases; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        String caseId = "high-volume-" + index + "-" + System.nanoTime();
                        YNetRunner runner = engine.launchCase(spec, caseId);
                        if (runner != null && caseId.equals(runner.getCaseID().toString())) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(EVENT_TIMEOUT_SEC * 5, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All launches should complete");
            assertEquals(numCases, successCount.get(), "All cases should launch successfully");
        }

        @Test
        @DisplayName("Concurrent listener operations")
        void concurrentListenerOperations() throws Exception {
            int numOperations = 50;
            ExecutorService executor = Executors.newFixedThreadPool(10);
            CountDownLatch latch = new CountDownLatch(numOperations);
            AtomicInteger addCount = new AtomicInteger(0);
            AtomicInteger removeCount = new AtomicInteger(0);

            List<YCaseEventListener> listeners = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < numOperations; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        YCaseEventListener listener = event -> {};
                        listeners.add(listener);

                        if (engine.addCaseEventListener(listener)) {
                            addCount.incrementAndGet();
                        }

                        if (index % 2 == 0 && engine.removeCaseEventListener(listener)) {
                            removeCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(EVENT_TIMEOUT_SEC * 3, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All listener operations should complete");
            assertTrue(addCount.get() > 0, "Some listeners should be added");

            for (YCaseEventListener listener : listeners) {
                engine.removeCaseEventListener(listener);
            }
        }
    }
}
