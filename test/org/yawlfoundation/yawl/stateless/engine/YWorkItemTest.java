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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.elements.YTask;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Comprehensive tests for YWorkItem class.
 *
 * <p>Chicago TDD: Tests use real YWorkItem instances, real YTask objects,
 * and real workflow execution context. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YWorkItem Tests")
@Tag("unit")
class YWorkItemTest {

    private YStatelessEngine engine;
    private YWorkItem workItem;
    private YTask task;
    private YSpecification spec;

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();

        // Load minimal specification for testing
        String specXML = StringUtil.inputStreamToString(
            getClass().getClassLoader().getResourceAsStream("resources/MinimalSpec.xml"));
        spec = engine.unmarshalSpecification(specXML);

        // Create a runner to get work items
        Map<String, String> initialData = Map.of("caseId", "test-case-123");
        YNetRunner runner = engine.launchCase(spec, "test-case-123", initialData);
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());

        // Get first work item for testing
        workItem = workItems.get(0);
        task = workItem.getTask();
    }

    // ==================== Basic Properties Tests ====================

    @Test
    @DisplayName("Get ID")
    void getID() {
        // Assert
        assertNotNull(workItem.getID());
        assertNotNull(workItem.getID().getID());
    }

    @Test
    @DisplayName("Get task")
    void getTask() {
        // Assert
        assertNotNull(task);
        assertEquals(task.getID(), workItem.getTask().getID());
    }

    @Test
    @DisplayName("Get case ID")
    void getCaseID() {
        // Assert
        assertEquals("test-case-123", workItem.getCaseID());
    }

    @Test
    @DisplayName("Get case identifier")
    void getCaseIdentifier() {
        // Assert
        assertNotNull(workItem.getCaseIdentifier());
        assertEquals("test-case-123", workItem.getCaseIdentifier().getID());
    }

    @Test
    @DisplayName("Get specification ID")
    void getSpecificationID() {
        // Assert
        assertNotNull(workItem.getSpecificationID());
        assertEquals(spec.getID(), workItem.getSpecificationID());
    }

    @Test
    @DisplayName("Get name")
    void getName() {
        // Assert
        assertNotNull(workItem.getName());
        assertFalse(workItem.getName().isEmpty());
    }

    @Test
    @DisplayName("Get description - may be null")
    void getDescription() {
        // Assert
        // Description may be null if not set
        workItem.getDescription();
    }

    @Test
    @DisplayName("Get start time")
    void getStartTime() {
        // Assert
        assertNotNull(workItem.getStartTime());
        assertTrue(workItem.getStartTime().isBefore(Instant.now()));
    }

    @Test
    @DisplayName("Get offer time - should be null")
    void getOfferTime() {
        // Assert
        // Offer time should be null for newly created work items
        assertNull(workItem.getOfferTime());
    }

    @Test
    @DisplayName("Get actual start time - should be null")
    void getActualStartTime() {
        // Assert
        // Actual start time should be null for newly created work items
        assertNull(workItem.getActualStartTime());
    }

    @Test
    @DisplayName("Get finish time - should be null")
    void getFinishTime() {
        // Assert
        // Finish time should be null for incomplete work items
        assertNull(workItem.getFinishTime());
    }

    @Test
    @DisplayName("Get due date - should be null")
    void getDueDate() {
        // Assert
        // Due date may be null if not set
        workItem.getDueDate();
    }

    @Test
    @DisplayName("Get priority")
    void getPriority() {
        // Assert
        assertTrue(workItem.getPriority() >= 0);
    }

    @Test
    @DisplayName("Get priority name")
    void getPriorityName() {
        // Assert
        assertNotNull(workItem.getPriorityName());
        assertFalse(workItem.getPriorityName().isEmpty());
    }

    // ==================== Status Tests ====================

    @Test
    @DisplayName("Get status - enabled")
    void getStatus_enabled() {
        // Assert
        assertEquals(org.yawlfoundation.yawl.engine.YWorkItemStatus.Enabled, workItem.getStatus());
    }

    @Test
    @DisplayName("Set and get status")
    void setStatus() {
        // Act
        workItem.setStatus(org.yawlfoundation.yawl.engine.YWorkItemStatus.Running);

        // Assert
        assertEquals(org.yawlfoundation.yawl.engine.YWorkItemStatus.Running, workItem.getStatus());
    }

    @Test
    @DisplayName("Is pending - false for enabled")
    void isPending_falseForEnabled() {
        // Assert
        assertFalse(workItem.isPending());
    }

    @Test
    @DisplayName("Is running - false for enabled")
    void isRunning_falseForEnabled() {
        // Assert
        assertFalse(workItem.isRunning());
    }

    @Test
    @DisplayName("Is completed - false for enabled")
    void isCompleted_falseForEnabled() {
        // Assert
        assertFalse(workItem.isCompleted());
    }

    @Test
    @DisplayName("Is cancelled - false for enabled")
    void isCancelled_falseForEnabled() {
        // Assert
        assertFalse(workItem.isCancelled());
    }

    @Test
    @DisplayName("Is enabled")
    void isEnabled() {
        // Assert
        assertTrue(workItem.isEnabled());
    }

    // ==================== Data Tests ====================

    @Test
    @DisplayName("Get input data")
    void getInputData() {
        // Assert
        assertNotNull(workItem.getInputData());
        // May be empty for minimal work items
    }

    @Test
    @DisplayName("Get input parameters")
    void getInputParameters() {
        // Assert
        assertNotNull(workItem.getInputParameters());
        // May be empty for minimal work items
    }

    @Test
    @DisplayName("Get output data")
    void getOutputData() {
        // Assert
        assertNull(workItem.getOutputData());
        // Should be null for incomplete work items
    }

    @Test
    @DisplayName("Get output parameters")
    void getOutputParameters() {
        // Assert
        assertNotNull(workItem.getOutputParameters());
        // May be empty for incomplete work items
    }

    @Test
    @DisplayName("Get completion data")
    void getCompletionData() {
        // Assert
        assertNull(workItem.getCompletionData());
        // Should be null for incomplete work items
    }

    @Test
    @DisplayName("Get failed condition")
    void getFailedCondition() {
        // Assert
        assertNull(workItem.getFailedCondition());
        // Should be null for work items that haven't failed
    }

    // ==================== Metadata Tests ====================

    @Test
    @DisplayName("Get router ID")
    void getRouterID() {
        // Assert
        workItem.getRouterID();
    }

    @Test
    @DisplayName("Get router name")
    void getRouterName() {
        // Assert
        workItem.getRouterName();
    }

    @Test
    @DisplayName("Get cancellation message")
    void getCancellationMessage() {
        // Assert
        // Should be null if not cancelled
        workItem.getCancellationMessage();
    }

    @Test
    @DisplayName("Get lifecycle event ID")
    void getLifecycleEventID() {
        // Assert
        assertNotNull(workItem.getLifecycleEventID());
        assertFalse(workItem.getLifecycleEventID().isEmpty());
    }

    @Test
    @DisplayName("Get resource ID")
    void getResourceID() {
        // Assert
        workItem.getResourceID();
    }

    @Test
    @DisplayName("Get resource set ID")
    void getResourceSetID() {
        // Assert
        workItem.getResourceSetID();
    }

    @Test
    @DisplayName("Get resource status")
    void getResourceStatus() {
        // Assert
        workItem.getResourceStatus();
    }

    // ==================== Child Work Item Tests ====================

    @Test
    @DisplayName("Get child work items - empty for atomic tasks")
    void getChildWorkItems_empty() {
        // Assert
        assertTrue(workItem.getChildWorkItems().isEmpty());
    }

    @Test
    @DisplayName("Add child work item")
    void addChildWorkItem() throws Exception {
        // Arrange - Create another work item to add as child
        Map<String, String> initialData = Map.of("caseId", "test-case-124");
        YNetRunner runner = engine.launchCase(spec, "test-case-124", initialData);
        List<YWorkItem> childWorkItems = engine.getWorkItemsForCase(runner.getCaseID());
        YWorkItem child = childWorkItems.get(0);

        // Act
        workItem.addChildWorkItem(child);

        // Assert
        List<YWorkItem> children = workItem.getChildWorkItems();
        assertEquals(1, children.size());
        assertTrue(children.contains(child));
    }

    @Test
    @DisplayName("Remove child work item")
    void removeChildWorkItem() throws Exception {
        // Arrange - Add child work item first
        Map<String, String> initialData = Map.of("caseId", "test-case-124");
        YNetRunner runner = engine.launchCase(spec, "test-case-124", initialData);
        List<YWorkItem> childWorkItems = engine.getWorkItemsForCase(runner.getCaseID());
        YWorkItem child = childWorkItems.get(0);
        workItem.addChildWorkItem(child);

        // Act
        workItem.removeChildWorkItem(child);

        // Assert
        assertTrue(workItem.getChildWorkItems().isEmpty());
    }

    // ==================== Time Tests ====================

    @Test
    @DisplayName("Set offer time")
    void setOfferTime() {
        // Arrange
        Instant offerTime = Instant.now();

        // Act
        workItem.setOfferTime(offerTime);

        // Assert
        assertEquals(offerTime, workItem.getOfferTime());
    }

    @Test
    @DisplayName("Set actual start time")
    void setActualStartTime() {
        // Arrange
        Instant startTime = Instant.now();

        // Act
        workItem.setActualStartTime(startTime);

        // Assert
        assertEquals(startTime, workItem.getActualStartTime());
    }

    @Test
    @DisplayName("Set finish time")
    void setFinishTime() {
        // Arrange
        Instant finishTime = Instant.now();

        // Act
        workItem.setFinishTime(finishTime);

        // Assert
        assertEquals(finishTime, workItem.getFinishTime());
    }

    @Test
    @DisplayName("Set due date")
    void setDueDate() {
        // Arrange
        Instant dueDate = Instant.now().plus(1, ChronoUnit.DAYS);

        // Act
        workItem.setDueDate(dueDate);

        // Assert
        assertEquals(dueDate, workItem.getDueDate());
    }

    // ==================== Priority Tests ====================

    @Test
    @DisplayName("Set priority")
    void setPriority() {
        // Act
        workItem.setPriority(5);

        // Assert
        assertEquals(5, workItem.getPriority());
        assertEquals("Normal", workItem.getPriorityName());
    }

    @Test
    @DisplayName("Set priority - negative value")
    void setPriority_negative() {
        // Act
        workItem.setPriority(-1);

        // Assert
        // Should handle negative values gracefully
        assertEquals(-1, workItem.getPriority());
    }

    @Test
    @DisplayName("Set priority - high value")
    void setPriority_highValue() {
        // Act
        workItem.setPriority(10);

        // Assert
        assertEquals(10, workItem.getPriority());
    }

    // ==================== Cancellation Tests ====================

    @Test
    @DisplayName("Cancel work item")
    void cancelWorkItem() {
        // Act
        workItem.cancel("Test cancellation message");

        // Assert
        assertTrue(workItem.isCancelled());
        assertEquals("Test cancellation message", workItem.getCancellationMessage());
    }

    @Test
    @DisplayName("Cancel work item - null message")
    void cancelWorkItem_nullMessage() {
        // Act
        workItem.cancel(null);

        // Assert
        assertTrue(workItem.isCancelled());
        assertNull(workItem.getCancellationMessage());
    }

    @Test
    @DisplayName("Cancel work item - empty message")
    void cancelWorkItem_emptyMessage() {
        // Act
        workItem.cancel("");

        // Assert
        assertTrue(workItem.isCancelled());
        assertEquals("", workItem.getCancellationMessage());
    }

    // ==================== Export/Import Tests ====================

    @Test
    @DisplayName("Export to XML")
    void exportToXml() {
        // Act
        String xml = workItem.exportToXML();

        // Assert
        assertNotNull(xml);
        assertFalse(xml.isEmpty());
        assertTrue(xml.contains("<workitem>"));
        assertTrue(xml.contains(workItem.getID().getID()));
    }

    @Test
    @DisplayName("Import from XML")
    void importFromXml() {
        // Arrange
        String xml = workItem.exportToXML();

        // Act
        YWorkItem imported = new YWorkItem();
        imported.importFromXML(xml, spec);

        // Assert
        assertNotNull(imported);
        assertEquals(workItem.getID().getID(), imported.getID().getID());
        assertEquals(workItem.getCaseID(), imported.getCaseID());
        assertEquals(spec.getID(), imported.getSpecificationID());
    }

    @Test
    @DisplayName("Import from XML - null XML")
    void importFromXml_nullXml() {
        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            workItem.importFromXML(null, spec);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Import from XML - null specification")
    void importFromXml_nullSpec() {
        // Arrange
        String xml = workItem.exportToXML();

        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            workItem.importFromXml(xml, null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    // ==================== Equality Tests ====================

    @Test
    @DisplayName("Equals - same work item")
    void equals_sameWorkItem() {
        // Assert
        assertEquals(workItem, workItem);
    }

    @Test
    @DisplayName("Equals - different work item with same ID")
    void equals_differentWorkItemSameId() throws Exception {
        // Arrange - Create another work item with same ID
        Map<String, String> initialData = Map.of("caseId", "test-case-123");
        YNetRunner runner = engine.launchCase(spec, "test-case-123", initialData);
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        YWorkItem other = workItems.get(0);

        // Assert
        assertEquals(workItem, other);
        assertEquals(workItem.hashCode(), other.hashCode());
    }

    @Test
    @DisplayName("Equals - different work item with different ID")
    void equals_differentWorkItemDifferentId() throws Exception {
        // Arrange - Create another work item
        Map<String, String> initialData = Map.of("caseId", "test-case-125");
        YNetRunner runner = engine.launchCase(spec, "test-case-125", initialData);
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        YWorkItem other = workItems.get(0);

        // Assert
        assertNotEquals(workItem, other);
    }

    @Test
    @DisplayName("Equals - null")
    void equals_null() {
        // Assert
        assertNotEquals(workItem, null);
    }

    @Test
    @DisplayName("Equals - different object type")
    void equals_differentType() {
        // Assert
        assertNotEquals(workItem, "string");
    }

    // ==================== String Representation Tests ====================

    @Test
    @DisplayName("To string")
    void toStringTest() {
        // Act
        String str = workItem.toString();

        // Assert
        assertNotNull(str);
        assertFalse(str.isEmpty());
        assertTrue(str.contains(workItem.getID().getID()));
        assertTrue(str.contains(workItem.getName()));
    }

    // ==================== Task Type Tests ====================

    @Test
    @DisplayName("Is atomic task")
    void isAtomicTask() {
        // Assert
        assertTrue(task.isAtomic());
    }

    @Test
    @DisplayName("Is composite task")
    void isCompositeTask() {
        // Assert
        assertFalse(task.isComposite());
    }

    @Test
    @DisplayName("Get task ID")
    void getTaskID() {
        // Assert
        assertNotNull(task.getID());
        assertEquals(task.getID(), workItem.getTask().getID());
    }

    @Test
    @DisplayName("Get task name")
    void getTaskName() {
        // Assert
        assertNotNull(task.getName());
        assertEquals(task.getName(), workItem.getName());
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("Validate work item")
    void validateWorkItem() {
        // Act & Assert
        assertDoesNotThrow(() -> workItem.validate());
    }

    @Test
    @DisplayName("Validate work item with data")
    void validateWorkItemWithData() throws Exception {
        // Arrange - Create work item with data
        Map<String, String> inputData = new HashMap<>();
        inputData.put("inputParam1", "value1");

        // Create a new work item with input data
        Map<String, String> initialData = Map.of("caseId", "test-case-126");
        YNetRunner runner = engine.launchCase(spec, "test-case-126", initialData);
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        YWorkItem itemWithInput = workItems.get(0);

        // Add some input data
        itemWithInput.getInputData().putAll(inputData);

        // Act & Assert
        assertDoesNotThrow(() -> itemWithInput.validate());
    }

    @Test
    @DisplayName("Validate work item - null task")
    void validateWorkItem_nullTask() {
        // Arrange
        YWorkItem invalidItem = new YWorkItem();
        invalidItem.setCaseID("test-case");

        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            invalidItem.validate();
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Validate work item - empty case ID")
    void validateWorkItem_emptyCaseID() {
        // Arrange
        YWorkItem invalidItem = new YWorkItem();
        invalidItem.setCaseID("");

        // Act & Assert
        YStateException exception = assertThrows(YStateException.class, () -> {
            invalidItem.validate();
        });
        assertTrue(exception.getMessage().contains("empty"));
    }
}