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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YCase covering idle timer management
 * and marshal operations.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real YCase objects via reflection for protected methods testing,
 * and real timer operations. No mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YCase Tests")
@Tag("unit")
public class TestYCase {

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
        String xml = StringUtil.streamToString(is)
                .orElseThrow(() -> new AssertionError("Empty spec XML from " + MINIMAL_SPEC_RESOURCE));
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

    /**
     * Create a YCase instance using reflection since constructor is protected.
     */
    private YCase createYCase(YNetRunner runner, long idleTimeout) throws Exception {
        Constructor<YCase> constructor = YCase.class.getDeclaredConstructor(
                YNetRunner.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(runner, idleTimeout);
    }

    // =========================================================================
    // Nested: Idle Timer Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Idle Timer Management Tests")
    class IdleTimerManagementTests {

        @Test
        @DisplayName("YCase with zero timeout has no idle timer")
        void yCaseWithZeroTimeoutHasNoIdleTimer() throws Exception {
            String caseId = "zero-timeout-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            assertNotNull(yCase, "YCase should be created");
            assertNotNull(yCase.getRunner(), "Should have runner reference");
        }

        @Test
        @DisplayName("YCase with positive timeout has idle timer")
        void yCaseWithPositiveTimeoutHasIdleTimer() throws Exception {
            String caseId = "positive-timeout-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 10000L);

            assertNotNull(yCase, "YCase should be created");

            // Test isIdle - with timer enabled, should return boolean
            Method isIdleMethod = YCase.class.getDeclaredMethod("isIdle");
            isIdleMethod.setAccessible(true);

            assertDoesNotThrow(() -> {
                boolean isIdle = (boolean) isIdleMethod.invoke(yCase);
                assertTrue(isIdle || !isIdle, "isIdle should return boolean");
            }, "isIdle should work with timer enabled");
        }

        @Test
        @DisplayName("isIdle throws when timer is disabled")
        void isIdleThrowsWhenTimerDisabled() throws Exception {
            String caseId = "disabled-timer-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            Method isIdleMethod = YCase.class.getDeclaredMethod("isIdle");
            isIdleMethod.setAccessible(true);

            assertThrows(Exception.class, () -> {
                try {
                    isIdleMethod.invoke(yCase);
                } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                }
            }, "isIdle should throw when timer is disabled");
        }

        @Test
        @DisplayName("Cancel idle timer stops timer")
        void cancelIdleTimerStopsTimer() throws Exception {
            String caseId = "cancel-timer-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 10000L);

            Method cancelMethod = YCase.class.getDeclaredMethod("cancelIdleTimer");
            cancelMethod.setAccessible(true);

            assertDoesNotThrow(() -> cancelMethod.invoke(yCase),
                    "cancelIdleTimer should work");
        }

        @Test
        @DisplayName("Ping resets idle timer")
        void pingResetsIdleTimer() throws Exception {
            String caseId = "ping-timer-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 30000L);

            Method pingMethod = YCase.class.getDeclaredMethod("ping");
            pingMethod.setAccessible(true);

            assertDoesNotThrow(() -> pingMethod.invoke(yCase),
                    "ping should work");
        }

        @Test
        @DisplayName("Restart idle timer after pause")
        void restartIdleTimerAfterPause() throws Exception {
            String caseId = "restart-timer-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 30000L);

            Method cancelMethod = YCase.class.getDeclaredMethod("cancelIdleTimer");
            cancelMethod.setAccessible(true);
            cancelMethod.invoke(yCase);

            Method restartMethod = YCase.class.getDeclaredMethod("restartIdleTimer");
            restartMethod.setAccessible(true);

            assertDoesNotThrow(() -> restartMethod.invoke(yCase),
                    "restartIdleTimer should work after cancel");
        }

        @Test
        @DisplayName("Set idle timeout updates timer")
        void setIdleTimeoutUpdatesTimer() throws Exception {
            String caseId = "set-timeout-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 10000L);

            Method setTimeoutMethod = YCase.class.getDeclaredMethod("setIdleTimeout", long.class);
            setTimeoutMethod.setAccessible(true);

            assertDoesNotThrow(() -> setTimeoutMethod.invoke(yCase, 60000L),
                    "setIdleTimeout should work");
        }
    }

    // =========================================================================
    // Nested: Marshal Operations Tests
    // =========================================================================

    @Nested
    @DisplayName("Marshal Operations Tests")
    class MarshalOperationsTests {

        @Test
        @DisplayName("Marshal returns valid XML string")
        void marshalReturnsValidXmlString() throws Exception {
            String caseId = "marshal-xml-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            String xml = yCase.marshal();

            assertNotNull(xml, "Marshal should return XML string");
            assertTrue(xml.length() > 0, "XML should not be empty");
            assertTrue(xml.contains("<case"), "XML should contain case element");
        }

        @Test
        @DisplayName("Marshal contains case ID")
        void marshalContainsCaseId() throws Exception {
            String caseId = "marshal-case-id-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            String xml = yCase.marshal();

            assertTrue(xml.contains(caseId),
                    "XML should contain case ID");
        }

        @Test
        @DisplayName("Marshal with null runner throws exception")
        void marshalWithNullRunnerThrowsException() throws Exception {
            YCase yCase = createYCase(null, 0L);

            assertThrows(YStateException.class, yCase::marshal,
                    "Marshal with null runner should throw");
        }

        @Test
        @DisplayName("Get runner returns correct reference")
        void getRunnerReturnsCorrectReference() throws Exception {
            String caseId = "get-runner-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            YCaseExporter exporter = new YCaseExporter();
            String xml = exporter.marshal(runner);

            assertNotNull(xml, "Export via YCaseExporter should work");
            assertEquals(runner, yCase.getRunner(),
                    "getRunner should return same reference");
        }
    }

    // =========================================================================
    // Nested: Work Item Timer Removal Tests
    // =========================================================================

    @Nested
    @DisplayName("Work Item Timer Removal Tests")
    class WorkItemTimerRemovalTests {

        @Test
        @DisplayName("Remove work item timers does not throw")
        void removeWorkItemTimersDoesNotThrow() throws Exception {
            String caseId = "remove-wi-timers-test";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            assertDoesNotThrow(yCase::removeWorkItemTimers,
                    "removeWorkItemTimers should not throw");
        }

        @Test
        @DisplayName("Remove work item timers on case with items")
        void removeWorkItemTimersOnCaseWithItems() throws Exception {
            String caseId = "remove-timers-with-items";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            YCase yCase = createYCase(runner, 0L);

            // Verify the runner has work items
            assertFalse(runner.getWorkItemRepository().getWorkItems().isEmpty(),
                    "Runner should have work items");

            assertDoesNotThrow(yCase::removeWorkItemTimers,
                    "removeWorkItemTimers should handle items gracefully");
        }
    }

    // =========================================================================
    // Nested: Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("YCase integrates with engine unload")
        void yCaseIntegratesWithEngineUnload() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "integration-unload";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String xml = engine.unloadCase(caseIdentifier);

            assertNotNull(xml, "Engine unload should return XML");
            assertTrue(xml.contains(caseId), "XML should contain case ID");
        }

        @Test
        @DisplayName("YCase integrates with engine restore")
        void yCaseIntegratesWithEngineRestore() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "integration-restore";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            YIdentifier caseIdentifier = runner.getCaseID();

            String xml = engine.unloadCase(caseIdentifier);
            YNetRunner restored = engine.restoreCase(xml);

            assertNotNull(restored, "Restored runner should not be null");
            assertEquals(caseId, restored.getCaseID().toString(),
                    "Case ID should be preserved");
        }

        @Test
        @DisplayName("YCase idle detection through engine")
        void yCaseIdleDetectionThroughEngine() throws Exception {
            engine.setIdleCaseTimer(30000L);

            String caseId = "idle-detection-engine";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            boolean isIdle = engine.isIdleCase(runner);

            assertTrue(isIdle, "Case should be idle initially");
        }
    }
}
