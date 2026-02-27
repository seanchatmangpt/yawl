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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YNetRunner class.
 *
 * <p>Chicago TDD: Tests use real YNetRunner instances, real YSpecification objects,
 * and real workflow execution. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YNetRunner Tests")
@Tag("unit")
class YNetRunnerTest {

    private YStatelessEngine engine;
    private YNetRunner runner;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();

        // Load minimal specification for testing
        String specXML = StringUtil.inputStreamToString(
            getClass().getClassLoader().getResourceAsStream("resources/MinimalSpec.xml"));
        spec = engine.unmarshalSpecification(specXML);

        // Create a new runner for testing
        Map<String, String> initialData = Map.of("caseId", "test-case-123");
        runner = engine.launchCase(spec, "test-case-123", initialData);
    }

    // ==================== Basic Properties Tests ====================

    @Test
    @DisplayName("Get case ID")
    void getCaseID() {
        // Assert
        assertEquals("test-case-123", runner.getCaseID());
    }

    @Test
    @DisplayName("Get specification ID")
    void getSpecificationID() {
        // Assert
        assertNotNull(runner.getSpecificationID());
        assertEquals(spec.getID().toString(), runner.getSpecificationID().toString());
    }

    @Test
    @DisplayName("Get containing task ID - null for top-level")
    void getContainingTaskID_topLevel() {
        // Assert
        assertNull(runner.getContainingTaskID());
    }

    @Test
    @DisplayName("Get case ID for net")
    void getCaseIDForNet() {
        // Assert
        assertNotNull(runner.getCaseIDForNet());
    }

    @Test
    @DisplayName("Get net")
    void getNet() {
        // Assert
        assertNotNull(runner.getNet());
        assertEquals(spec.getID(), runner.getNet().getSpecification().getID());
    }

    @Test
    @DisplayName("Get net data")
    void getNetData() {
        // Assert
        assertNotNull(runner.getNetData());
        assertNotNull(runner.getNetData().getCaseID());
    }

    @Test
    @DisplayName("Get start time")
    void getStartTime() {
        // Assert
        assertTrue(runner.getStartTime() > 0);
    }

    // ==================== Execution Status Tests ====================

    @Test
    @DisplayName("Get execution status - Normal")
    void getExecutionStatus_normal() {
        // Assert
        assertEquals(YNetRunner.ExecutionStatus.Normal, runner.getExecutionStatus());
    }

    @Test
    @DisplayName("Set and get execution status - Suspending")
    void setExecutionStatus_suspending() {
        // Act
        runner.setExecutionStatus(YNetRunner.ExecutionStatus.Suspending);

        // Assert
        assertEquals(YNetRunner.ExecutionStatus.Suspending, runner.getExecutionStatus());
    }

    @Test
    @DisplayName("Set and get execution status - Suspended")
    void setExecutionStatus_suspended() {
        // Act
        runner.setExecutionStatus(YNetRunner.ExecutionStatus.Suspended);

        // Assert
        assertEquals(YNetRunner.ExecutionStatus.Suspended, runner.getExecutionStatus());
    }

    @Test
    @DisplayName("Set and get execution status - Resuming")
    void setExecutionStatus_resuming() {
        // Act
        runner.setExecutionStatus(YNetRunner.ExecutionStatus.Resuming);

        // Assert
        assertEquals(YNetRunner.ExecutionStatus.Resuming, runner.getExecutionStatus());
    }

    // ==================== Task Management Tests ====================

    @Test
    @DisplayName("Get enabled tasks")
    void getEnabledTasks() throws Exception {
        // Act
        Set<YTask> enabledTasks = runner.getEnabledTasks();

        // Assert
        assertNotNull(enabledTasks);
        assertFalse(enabledTasks.isEmpty());
    }

    @Test
    @DisplayName("Get enabled task names")
    void getEnabledTaskNames() throws Exception {
        // Act
        Set<String> enabledTaskNames = runner.getEnabledTaskNames();

        // Assert
        assertNotNull(enabledTaskNames);
        assertFalse(enabledTaskNames.isEmpty());
    }

    @Test
    @DisplayName("Get busy tasks")
    void getBusyTasks() throws Exception {
        // Act
        Set<YTask> busyTasks = runner.getBusyTasks();

        // Assert
        // Initially no tasks should be busy
        assertTrue(busyTasks.isEmpty());
    }

    @Test
    @DisplayName("Get busy task names")
    void getBusyTaskNames() throws Exception {
        // Act
        Set<String> busyTaskNames = runner.getBusyTaskNames();

        // Assert
        // Initially no task names should be busy
        assertTrue(busyTaskNames.isEmpty());
    }

    @Test
    @DisplayName("Add enabled task")
    void addEnabledTask() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        int initialSize = enabledTasks.size();

        // Act
        // Try to add a task that's already enabled
        if (!enabledTasks.isEmpty()) {
            runner.addEnabledTask(enabledTasks.iterator().next());
        }

        // Assert
        Set<YTask> updatedTasks = runner.getEnabledTasks();
        // Size should be the same (no duplicates allowed)
        assertEquals(initialSize, updatedTasks.size());
    }

    @Test
    @DisplayName("Remove enabled task")
    void removeEnabledTask() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask taskToRemove = enabledTasks.iterator().next();
        int initialSize = enabledTasks.size();

        // Act
        runner.removeEnabledTask(taskToRemove);

        // Assert
        Set<YTask> updatedTasks = runner.getEnabledTasks();
        assertEquals(initialSize - 1, updatedTasks.size());
    }

    @Test
    @DisplayName("Add busy task")
    void addBusyTask() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask taskToMakeBusy = enabledTasks.iterator().next();

        // Act
        runner.addBusyTask(taskToMakeBusy);

        // Assert
        Set<YTask> busyTasks = runner.getBusyTasks();
        assertEquals(1, busyTasks.size());
        assertTrue(busyTasks.contains(taskToMakeBusy));
    }

    @Test
    @DisplayName("Remove busy task")
    void removeBusyTask() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask taskToMakeBusy = enabledTasks.iterator().next();
        runner.addBusyTask(taskToMakeBusy);

        // Act
        runner.removeBusyTask(taskToMakeBusy);

        // Assert
        Set<YTask> busyTasks = runner.getBusyTasks();
        assertTrue(busyTasks.isEmpty());
    }

    // ==================== Work Item Management Tests ====================

    @Test
    @DisplayName("Get enabled work items")
    void getEnabledWorkItems() throws Exception {
        // Act
        List<YWorkItem> enabledWorkItems = runner.getEnabledWorkItems();

        // Assert
        assertNotNull(enabledWorkItems);
        assertFalse(enabledWorkItems.isEmpty());
    }

    @Test
    @DisplayName("Get work items for task")
    void getWorkItemsForTask() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask task = enabledTasks.iterator().next();

        // Act
        List<YWorkItem> workItemsForTask = runner.getWorkItemsForTask(task);

        // Assert
        assertNotNull(workItemsForTask);
        assertFalse(workItemsForTask.isEmpty());
    }

    @Test
    @DisplayName("Get work items for non-existent task")
    void getWorkItemsForNonExistentTask() throws Exception {
        // Create a mock task that doesn't exist in the specification
        YTask nonExistentTask = new YTask();
        nonExistentTask.setID("non-existent-task");

        // Act
        List<YWorkItem> workItems = runner.getWorkItemsForTask(nonExistentTask);

        // Assert
        assertNotNull(workItems);
        assertTrue(workItems.isEmpty());
    }

    // ==================== Case State Tests ====================

    @Test
    @DisplayName("Get case identifier")
    void getCaseIdentifier() {
        // Assert
        assertNotNull(runner.getCaseIdentifier());
        assertEquals("test-case-123", runner.getCaseIdentifier().getID());
    }

    @Test
    @DisplayName("Is cancelled")
    void isCancelled() {
        // Assert
        assertFalse(runner.isCancelled());
    }

    @Test
    @DisplayName("Set and is cancelled")
    void setIsCancelled() {
        // Act
        runner.setCancelled(true);

        // Assert
        assertTrue(runner.isCancelled());
    }

    @Test
    @DisplayName("Get containing composite task - null for top-level")
    void getContainingCompositeTask_topLevel() {
        // Assert
        assertNull(runner.getContainingCompositeTask());
    }

    // ==================== Locking Tests ====================

    @Test
    @DisplayName("Lock and unlock runner")
    void lockAndUnlockRunner() throws Exception {
        // Act
        runner.lockRunner();

        // Do some operations with the lock
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        assertNotNull(enabledTasks);

        // Act
        runner.unlockRunner();

        // Assert
        // No exceptions thrown, locking works
    }

    @Test
    @DisplayName("Lock runner twice - should not block")
    void lockRunnerTwice() throws Exception {
        // Act
        runner.lockRunner();
        runner.lockRunner(); // Should not block or throw exception

        // Assert
        // No exceptions thrown, can lock multiple times
    }

    @Test
    @DisplayName("Unlock runner without lock")
    void unlockRunnerWithoutLock() {
        // Act & Assert
        // Should not throw exception
        assertDoesNotThrow(() -> runner.unlockRunner());
    }

    // ==================== Announcements Tests ====================

    @Test
    @DisplayName("Get work item announcements")
    void getWorkItemAnnouncements() {
        // Assert
        Set<YWorkItemEvent> announcements = runner.getWorkItemAnnouncements();
        assertNotNull(announcements);
        // Initially empty
        assertTrue(announcements.isEmpty());
    }

    @Test
    @DisplayName("Add work item announcement")
    void addWorkItemAnnouncement() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask task = enabledTasks.iterator().next();
        YWorkItem workItem = runner.getWorkItemsForTask(task).get(0);
        YWorkItemEvent event = new YWorkItemEvent(workItem, YEventType.WorkItemEnabled, runner);

        // Act
        runner.addWorkItemAnnouncement(event);

        // Assert
        Set<YWorkItemEvent> announcements = runner.getWorkItemAnnouncements();
        assertEquals(1, announcements.size());
        assertTrue(announcements.contains(event));
    }

    @Test
    @DisplayName("Clear work item announcements")
    void clearWorkItemAnnouncements() throws Exception {
        // Arrange
        Set<YTask> enabledTasks = runner.getEnabledTasks();
        if (enabledTasks.isEmpty()) {
            return; // Skip if no enabled tasks
        }

        YTask task = enabledTasks.iterator().next();
        YWorkItem workItem = runner.getWorkItemsForTask(task).get(0);
        YWorkItemEvent event = new YWorkItemEvent(workItem, YEventType.WorkItemEnabled, runner);
        runner.addWorkItemAnnouncement(event);

        // Act
        runner.clearWorkItemAnnouncements();

        // Assert
        Set<YWorkItemEvent> announcements = runner.getWorkItemAnnouncements();
        assertTrue(announcements.isEmpty());
    }

    // ==================== Timer Tests ====================

    @Test
    @DisplayName("Get timer states")
    void getTimerStates() {
        // Assert
        Map<String, String> timerStates = runner.getTimerStates();
        assertNotNull(timerStates);
        // Initially empty
        assertTrue(timerStates.isEmpty());
    }

    @Test
    @DisplayName("Set timer state")
    void setTimerState() {
        // Act
        runner.setTimerState("timer1", "running");

        // Assert
        Map<String, String> timerStates = runner.getTimerStates();
        assertEquals(1, timerStates.size());
        assertEquals("running", timerStates.get("timer1"));
    }

    @Test
    @DisplayName("Set timer states - multiple timers")
    void setTimerStates_multiple() {
        // Act
        runner.setTimerState("timer1", "running");
        runner.setTimerState("timer2", "expired");
        runner.setTimerState("timer3", "paused");

        // Assert
        Map<String, String> timerStates = runner.getTimerStates();
        assertEquals(3, timerStates.size());
        assertEquals("running", timerStates.get("timer1"));
        assertEquals("expired", timerStates.get("timer2"));
        assertEquals("paused", timerStates.get("timer3"));
    }

    // ==================== Task Creation Tests ====================

    @Test
    @DisplayName("Create task - atomic task")
    void createTask_atomic() throws Exception {
        // Act
        YTask task = runner.createTask("atomicTask", spec.getID());

        // Assert
        assertNotNull(task);
        assertTrue(task.isAtomic());
    }

    @Test
    @DisplayName("Create task - null task name")
    void createTask_nullName() {
        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            runner.createTask(null, spec.getID());
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Create task - empty task name")
    void createTask_emptyName() {
        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            runner.createTask("", spec.getID());
        });
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Create task - null specification ID")
    void createTask_nullSpecId() {
        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            runner.createTask("task", null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    // ==================== Export/Import Tests ====================

    @Test
    @DisplayName("Export case to XML")
    void exportCaseToXml() {
        // Act
        String xml = runner.exportCaseToXML();

        // Assert
        assertNotNull(xml);
        assertFalse(xml.isEmpty());
        assertTrue(xml.contains("<case>"));
        assertTrue(xml.contains("test-case-123"));
    }

    @Test
    @DisplayName("Import case from XML")
    void importCaseFromXml() {
        // Arrange
        String xml = runner.exportCaseToXML();

        // Act
        YNetRunner imported = new YNetRunner();
        imported.importCaseFromXML(xml, spec);

        // Assert
        assertNotNull(imported);
        assertEquals("test-case-123", imported.getCaseID());
        assertEquals(spec.getID(), imported.getSpecificationID());
    }

    @Test
    @DisplayName("Import case - null XML")
    void importCaseFromXml_nullXml() {
        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            runner.importCaseFromXML(null, spec);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Import case - null specification")
    void importCaseFromXml_nullSpec() {
        // Arrange
        String xml = runner.exportCaseToXML();

        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            runner.importCaseFromXML(xml, null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }
}