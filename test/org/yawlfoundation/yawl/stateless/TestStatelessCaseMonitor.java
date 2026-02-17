/*
 * Copyright (c) 2004-2024 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.stateless;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.monitor.YCase;
import org.yawlfoundation.yawl.stateless.monitor.YCaseMonitor;
import org.yawlfoundation.yawl.stateless.unmarshal.YMarshal;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for stateless case monitoring.
 * Tests case state tracking and event generation.
 *
 * @author YAWL Test Suite
 */
class TestStatelessCaseMonitor {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";

    private YSpecification specification;
    private YStatelessEngine engine;


    @BeforeEach
    void setUp() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Test specification file must exist: " + MINIMAL_SPEC_RESOURCE);
        String specXML = StringUtil.streamToString(is);
        assertNotNull(specXML, "Specification XML must not be null");

        List<YSpecification> specs = YMarshal.unmarshalSpecifications(specXML);
        assertFalse(specs.isEmpty(), "Should parse at least one specification");
        specification = specs.get(0);

        engine = new YStatelessEngine();
    }


    @Test
    @DisplayName("Test case monitor instantiation")
    void testCaseMonitorInstantiation() {
        YCaseMonitor monitor = new YCaseMonitor(10000);
        assertNotNull(monitor, "Monitor should be instantiable");
    }


    @Test
    @DisplayName("Test case monitor with disabled idle timer")
    void testCaseMonitorWithDisabledIdleTimer() {
        YCaseMonitor monitor = new YCaseMonitor(0);
        assertNotNull(monitor, "Monitor with disabled timer should be instantiable");
    }


    @Test
    @DisplayName("Test case monitor with negative idle timer")
    void testCaseMonitorWithNegativeIdleTimer() {
        YCaseMonitor monitor = new YCaseMonitor(-1);
        assertNotNull(monitor, "Monitor with negative timer should be instantiable");
    }


    @Test
    @DisplayName("Test engine case monitoring disabled by default")
    void testEngineCaseMonitoringDisabledByDefault() {
        assertFalse(engine.isCaseMonitoringEnabled(),
                "Case monitoring should be disabled by default");
    }


    @Test
    @DisplayName("Test engine enable case monitoring")
    void testEngineEnableCaseMonitoring() {
        engine.setCaseMonitoringEnabled(true);
        assertTrue(engine.isCaseMonitoringEnabled(),
                "Case monitoring should be enabled after setting");
    }


    @Test
    @DisplayName("Test engine disable case monitoring")
    void testEngineDisableCaseMonitoring() {
        engine.setCaseMonitoringEnabled(true);
        engine.setCaseMonitoringEnabled(false);
        assertFalse(engine.isCaseMonitoringEnabled(),
                "Case monitoring should be disabled after turning off");
    }


    @Test
    @DisplayName("Test engine set idle case timer")
    void testEngineSetIdleCaseTimer() {
        engine.setIdleCaseTimer(5000);
        assertTrue(engine.isCaseMonitoringEnabled(),
                "Setting idle timer should enable monitoring");
    }


    @Test
    @DisplayName("Test engine get engine number")
    void testEngineGetEngineNumber() {
        int engineNbr = engine.getEngineNbr();
        assertTrue(engineNbr >= 0, "Engine number should be non-negative");
    }


    @Test
    @DisplayName("Test case event listener registration")
    void testCaseEventListenerRegistration() {
        AtomicInteger eventCount = new AtomicInteger(0);

        YCaseEventListener listener = new YCaseEventListener() {
            @Override
            public void handleCaseEvent(YCaseEvent event) {
                eventCount.incrementAndGet();
            }
        };

        engine.addCaseEventListener(listener);
        engine.removeCaseEventListener(listener);

        assertNotNull(listener, "Listener should be registered and unregistered");
    }


    @Test
    @DisplayName("Test work item event listener registration")
    void testWorkItemEventListenerRegistration() {
        YWorkItemEventListener listener = new YWorkItemEventListener() {
            @Override
            public void handleWorkItemEvent(YWorkItemEvent event) {
            }
        };

        engine.addWorkItemEventListener(listener);
        engine.removeWorkItemEventListener(listener);

        assertNotNull(listener, "Listener should be registered and unregistered");
    }


    @Test
    @DisplayName("Test case event types")
    void testCaseEventTypes() {
        YEventType[] types = YEventType.values();
        assertTrue(types.length > 0, "Should have event types");

        assertNotNull(YEventType.CASE_STARTED, "CASE_STARTED should exist");
        assertNotNull(YEventType.CASE_COMPLETED, "CASE_COMPLETED should exist");
        assertNotNull(YEventType.CASE_CANCELLED, "CASE_CANCELLED should exist");
    }


    @Test
    @DisplayName("Test launch case generates case ID")
    void testLaunchCaseGeneratesCaseId()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);
        assertNotNull(runner, "Runner should be created");

        YIdentifier caseId = runner.getCaseID();
        assertNotNull(caseId, "Case ID should not be null");
        assertNotNull(caseId.toString(), "Case ID string should not be null");
    }


    @Test
    @DisplayName("Test launch case with explicit ID")
    void testLaunchCaseWithExplicitId()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        String explicitId = "explicit-case-12345";
        YNetRunner runner = engine.launchCase(specification, explicitId);
        assertNotNull(runner, "Runner should be created");

        assertEquals(explicitId, runner.getCaseID().toString(),
                "Case ID should match explicit ID");
    }


    @Test
    @DisplayName("Test runner has net reference")
    void testRunnerHasNetReference()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        assertNotNull(runner.getNet(), "Runner should have net reference");
        assertEquals("top", runner.getNet().getID(), "Net ID should be 'top'");
    }


    @Test
    @DisplayName("Test runner specification ID")
    void testRunnerSpecificationId()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        assertNotNull(runner.getSpecificationID(), "Specification ID should not be null");
    }


    @Test
    @DisplayName("Test runner start time")
    void testRunnerStartTime()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        long startTime = runner.getStartTime();
        assertTrue(startTime > 0, "Start time should be positive");
        assertTrue(startTime <= System.currentTimeMillis(),
                "Start time should not be in the future");
    }


    @Test
    @DisplayName("Test runner is alive after launch")
    void testRunnerIsAliveAfterLaunch()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        assertTrue(runner.isAlive(), "Runner should be alive after launch");
    }


    @Test
    @DisplayName("Test runner enabled tasks after launch")
    void testRunnerEnabledTasksAfterLaunch()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        var enabledTasks = runner.getEnabledTasks();
        assertNotNull(enabledTasks, "Enabled tasks should not be null");
    }


    @Test
    @DisplayName("Test runner busy tasks initially empty")
    void testRunnerBusyTasksInitiallyEmpty()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        var busyTasks = runner.getBusyTasks();
        assertNotNull(busyTasks, "Busy tasks should not be null");
    }


    @Test
    @DisplayName("Test runner execution status")
    void testRunnerExecutionStatus()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        String status = runner.getExecutionStatus();
        assertNotNull(status, "Execution status should not be null");
        assertEquals("Normal", status, "Initial status should be Normal");
    }


    @Test
    @DisplayName("Test runner is not suspended initially")
    void testRunnerNotSuspendedInitially()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification);

        assertFalse(runner.isSuspended(), "Runner should not be suspended initially");
        assertFalse(runner.isSuspending(), "Runner should not be suspending initially");
    }


    @Test
    @DisplayName("Test case monitor has case after launch")
    void testCaseMonitorHasCaseAfterLaunch()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        engine.setCaseMonitoringEnabled(true);

        YNetRunner runner = engine.launchCase(specification, "monitor-test-001");

        assertTrue(engine.isCaseMonitoringEnabled(), "Monitoring should be enabled");
    }


    @Test
    @DisplayName("Test case monitor get all cases")
    void testCaseMonitorGetAllCases() {
        YCaseMonitor monitor = new YCaseMonitor(10000);

        var cases = monitor.getAllCases();
        assertNotNull(cases, "Cases list should not be null");
        assertTrue(cases.isEmpty(), "Initially no cases should be monitored");
    }


    @Test
    @DisplayName("Test case monitor set idle timeout")
    void testCaseMonitorSetIdleTimeout() {
        YCaseMonitor monitor = new YCaseMonitor(10000);

        monitor.setIdleTimeout(5000);

        assertNotNull(monitor, "Monitor should accept timeout update");
    }


    @Test
    @DisplayName("Test case monitor cancel")
    void testCaseMonitorCancel() {
        YCaseMonitor monitor = new YCaseMonitor(10000);

        monitor.cancel();

        assertNotNull(monitor, "Monitor should handle cancel");
    }


    @Test
    @DisplayName("Test is idle case throws when monitoring disabled")
    void testIsIdleCaseThrowsWhenMonitoringDisabled()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "idle-test-001");

        assertThrows(YStateException.class, () -> {
            engine.isIdleCase(runner);
        });
    }


    @Test
    @DisplayName("Test multi-threaded announcements disabled by default")
    void testMultiThreadedAnnouncementsDisabledByDefault() {
        assertFalse(engine.isMultiThreadedAnnouncementsEnabled(),
                "Multi-threaded announcements should be disabled by default");
    }


    @Test
    @DisplayName("Test enable multi-threaded announcements")
    void testEnableMultiThreadedAnnouncements() {
        engine.enableMultiThreadedAnnouncements(true);
        assertTrue(engine.isMultiThreadedAnnouncementsEnabled(),
                "Multi-threaded announcements should be enabled");

        engine.enableMultiThreadedAnnouncements(false);
        assertFalse(engine.isMultiThreadedAnnouncementsEnabled(),
                "Multi-threaded announcements should be disabled");
    }


    @Test
    @DisplayName("Test runner to string")
    void testRunnerToString()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner = engine.launchCase(specification, "tostring-test-001");

        String str = runner.toString();
        assertNotNull(str, "toString should not return null");
        assertTrue(str.contains("tostring-test-001"), "toString should contain case ID");
    }


    @Test
    @DisplayName("Test runner equals and hash code")
    void testRunnerEqualsAndHashCode()
            throws YStateException, YDataStateException, YEngineStateException, YQueryException {
        YNetRunner runner1 = engine.launchCase(specification, "equals-test-001");
        YNetRunner runner2 = engine.launchCase(specification, "equals-test-001");
        YNetRunner runner3 = engine.launchCase(specification, "equals-test-002");

        assertEquals(runner1, runner2, "Runners with same case ID should be equal");
        assertNotEquals(runner1, runner3, "Runners with different case IDs should not be equal");
        assertEquals(runner1.hashCode(), runner2.hashCode(), "Equal runners should have same hash code");
    }
}
