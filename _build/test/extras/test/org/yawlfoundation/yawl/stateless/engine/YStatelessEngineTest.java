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

import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.WorkItemCompletion;
import org.yawlfoundation.yawl.exceptions.YDataStateException;
import org.yawlfoundation.yawl.exceptions.YEngineStateException;
import org.yawlfoundation.yawl.exceptions.YQueryException;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
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
 * Comprehensive tests for YStatelessEngine core functionality.
 *
 * <p>Chicago TDD: Tests use real YStatelessEngine instances, real YSpecification objects,
 * and real listener callbacks. No mocks for domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("YStatelessEngine Core Tests")
@Tag("unit")
class YStatelessEngineTest implements YCaseEventListener, YWorkItemEventListener {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final String SEQUENCE_SPEC_RESOURCE = "resources/SequenceSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 10L;

    private YStatelessEngine engine;
    private final List<YCaseEvent> caseEvents = Collections.synchronizedList(new ArrayList<>());
    private final List<YWorkItemEvent> workItemEvents = Collections.synchronizedList(new ArrayList<>());
    private CountDownLatch caseCompleteLatch;
    private CountDownLatch itemEnabledLatch;
    private final AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
    private final AtomicReference<YWorkItem> workItemCapture = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        engine = new YStatelessEngine();
        engine.addCaseEventListener(this);
        engine.addWorkItemEventListener(this);
        caseEvents.clear();
        workItemEvents.clear();
        runnerCapture.set(null);
        workItemCapture.set(null);
    }

    @AfterEach
    void tearDown() {
        engine.removeCaseEventListener(this);
        engine.removeWorkItemEventListener(this);
    }

    // ==================== Test Specifications ====================

    private YSpecification loadSpecification(String resource) throws YSyntaxException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(is, "Resource not found: " + resource);
            return engine.unmarshalSpecification(StringUtil.inputStreamToString(is));
        } catch (Exception e) {
            throw new YSyntaxException("Failed to load specification: " + resource, e);
        }
    }

    // ==================== Basic Engine Tests ====================

    @Test
    @DisplayName("Engine initialization - default settings")
    void initializeEngine_defaultSettings() {
        // Assert
        assertNotNull(engine);
        // No exceptions thrown, basic initialization works
    }

    @Test
    @DisplayName("Engine initialization - with timeout")
    void initializeEngine_withTimeout() {
        // Arrange & Act
        YStatelessEngine engineWithTimeout = new YStatelessEngine(60000);

        // Assert
        assertNotNull(engineWithTimeout);
    }

    @Test
    @DisplayName("Engine listeners - add and remove case event listener")
    void addRemoveCaseEventListener() {
        // Act
        engine.addCaseEventListener(this);
        engine.removeCaseEventListener(this);

        // Assert
        // No exceptions thrown, listener management works
    }

    @Test
    @DisplayName("Engine listeners - add and remove work item event listener")
    void addRemoveWorkItemEventListener() {
        // Act
        engine.addWorkItemEventListener(this);
        engine.removeWorkItemEventListener(this);

        // Assert
        // No exceptions thrown, listener management works
    }

    @Test
    @DisplayName("Engine listeners - add null listener")
    void addNullListener() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            engine.addCaseEventListener(null);
            engine.addWorkItemEventListener(null);
        });
    }

    // ==================== Specification Tests ====================

    @Test
    @DisplayName("Load valid specification")
    void loadSpecification_valid() throws YSyntaxException {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);

        // Assert
        assertNotNull(spec);
        assertNotNull(spec.getID());
    }

    @Test
    @DisplayName("Load invalid specification - null input")
    void loadSpecification_nullInput() {
        // Act & Assert
        YSyntaxException exception = assertThrows(YSyntaxException.class, () -> {
            engine.unmarshalSpecification(null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Load invalid specification - empty string")
    void loadSpecification_emptyString() {
        // Act & Assert
        YSyntaxException exception = assertThrows(YSyntaxException.class, () -> {
            engine.unmarshalSpecification("");
        });
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Load invalid specification - invalid XML")
    void loadSpecification_invalidXml() {
        // Act & Assert
        YSyntaxException exception = assertThrows(YSyntaxException.class, () -> {
            engine.unmarshalSpecification("<invalid><xml>");
        });
        assertTrue(exception.getMessage().contains("XML"));
    }

    // ==================== Case Lifecycle Tests ====================

    @Test
    @DisplayName("Launch case with valid specification")
    void launchCase_validSpecification() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        Map<String, String> initialData = new HashMap<>();
        initialData.put("caseId", "test-case-123");

        // Act
        YNetRunner runner = engine.launchCase(spec, "test-case-123", initialData);

        // Assert
        assertNotNull(runner);
        assertEquals("test-case-123", runner.getCaseID());
        assertNotNull(runner.getSpecificationID());
    }

    @Test
    @DisplayName("Launch case - null specification")
    void launchCase_nullSpecification() {
        // Arrange
        Map<String, String> initialData = new HashMap<>();

        // Act & Assert
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.launchCase(null, "test-case-123", initialData);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Launch case - null case ID")
    void launchCase_nullCaseId() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        Map<String, String> initialData = new HashMap<>();

        // Act & Assert
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.launchCase(spec, null, initialData);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Launch case - duplicate case ID")
    void launchCase_duplicateCaseId() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        Map<String, String> initialData = new HashMap<>();
        initialData.put("caseId", "test-case-123");

        // Act
        YNetRunner runner1 = engine.launchCase(spec, "test-case-123", initialData);

        // Act & Assert - launch same case ID again
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.launchCase(spec, "test-case-123", initialData);
        });
        assertTrue(exception.getMessage().contains("duplicate"));
    }

    @Test
    @DisplayName("Launch case - empty initial data")
    void launchCase_emptyInitialData() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);

        // Act
        YNetRunner runner = engine.launchCase(spec, "test-case-123", Collections.emptyMap());

        // Assert
        assertNotNull(runner);
    }

    @Test
    @DisplayName("Launch case - null initial data")
    void launchCase_nullInitialData() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);

        // Act
        YNetRunner runner = engine.launchCase(spec, "test-case-123", null);

        // Assert
        assertNotNull(runner);
    }

    @Test
    @DisplayName("Get work items for case")
    void getWorkItems_forCase() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());

        // Act
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());

        // Assert
        assertNotNull(workItems);
        // Minimal spec should have enabled work items
        assertFalse(workItems.isEmpty());
    }

    @Test
    @DisplayName("Get work items - null case ID")
    void getWorkItems_nullCaseId() {
        // Act & Assert
        YQueryException exception = assertThrows(YQueryException.class, () -> {
            engine.getWorkItemsForCase(null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Get work items - invalid case ID")
    void getWorkItems_invalidCaseId() {
        // Act & Assert
        YQueryException exception = assertThrows(YQueryException.class, () -> {
            engine.getWorkItemsForCase("non-existent-case");
        });
        assertTrue(exception.getMessage().contains("not found"));
    }

    // ==================== Work Item Lifecycle Tests ====================

    @Test
    @DisplayName("Start work item")
    void startWorkItem() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);
        YWorkItemID itemId = workItem.getID();

        // Act
        engine.startWorkItem(workItem);

        // Assert
        // No exceptions thrown, work item started successfully
        assertTrue(itemEnabledLatch != null && itemEnabledLatch.getCount() == 0);
    }

    @Test
    @DisplayName("Start work item - null work item")
    void startWorkItem_nullWorkItem() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());

        // Act & Assert
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.startWorkItem(null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Start work item - work item from different case")
    void startWorkItem_differentCase() throws Exception {
        // Arrange
        YSpecification spec1 = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner1 = engine.launchCase(spec1, "case-123", new HashMap<>());

        YSpecification spec2 = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner2 = engine.launchCase(spec2, "case-456", new HashMap<>());

        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner1.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);

        // Act & Assert - try to start work item with different runner
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.startWorkItem(workItem);
        });
        assertTrue(exception.getMessage().contains("different case"));
    }

    @Test
    @DisplayName("Complete work item")
    void completeWorkItem() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);
        Map<String, String> outputData = new HashMap<>();
        outputData.put("result", "completed");

        // Act
        engine.completeWorkItem(workItem, outputData, null);

        // Assert
        // No exceptions thrown, work item completed successfully
        assertTrue(caseCompleteLatch != null && caseCompleteLatch.getCount() == 0);
    }

    @Test
    @DisplayName("Complete work item - null work item")
    void completeWorkItem_nullWorkItem() throws Exception {
        // Arrange
        Map<String, String> outputData = new HashMap<>();

        // Act & Assert
        YEngineStateException exception = assertThrows(YEngineStateException.class, () -> {
            engine.completeWorkItem(null, outputData, null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Complete work item - null output data")
    void completeWorkItem_nullOutputData() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);

        // Act
        engine.completeWorkItem(workItem, null, null);

        // Assert
        // Should work with null output data
    }

    @Test
    @DisplayName("Complete work item - with work item completion")
    void completeWorkItem_withWorkItemCompletion() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);
        WorkItemCompletion completion = new WorkItemCompletion(workItem.getID());
        completion.setOutputData(Map.of("result", "completed"));

        // Act
        engine.completeWorkItem(workItem, null, completion);

        // Assert
        // No exceptions thrown, work item completed successfully
        assertTrue(caseCompleteLatch != null && caseCompleteLatch.getCount() == 0);
    }

    // ==================== Case State Tests ====================

    @Test
    @DisplayName("Unload case")
    void unloadCase() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        String caseID = runner.getCaseID();

        // Act
        String caseXML = engine.unloadCase(caseID);

        // Assert
        assertNotNull(caseXML);
        assertFalse(caseXML.isEmpty());
        assertTrue(caseXML.contains("<case>"));
    }

    @Test
    @DisplayName("Unload case - null case ID")
    void unloadCase_nullCaseId() {
        // Act & Assert
        YDataStateException exception = assertThrows(YDataStateException.class, () -> {
            engine.unloadCase(null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Unload case - invalid case ID")
    void unloadCase_invalidCaseId() {
        // Act & Assert
        YDataStateException exception = assertThrows(YDataStateException.class, () -> {
            engine.unloadCase("non-existent-case");
        });
        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("Restore case")
    void restoreCase() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        String caseID = runner.getCaseID();
        String caseXML = engine.unloadCase(caseID);

        // Act
        YNetRunner restoredRunner = engine.restoreCase(caseXML);

        // Assert
        assertNotNull(restoredRunner);
        assertEquals(caseID, restoredRunner.getCaseID());
    }

    @Test
    @DisplayName("Restore case - null case XML")
    void restoreCase_nullCaseXml() {
        // Act & Assert
        YDataStateException exception = assertThrows(YDataStateException.class, () -> {
            engine.restoreCase(null);
        });
        assertTrue(exception.getMessage().contains("null"));
    }

    @Test
    @DisplayName("Restore case - empty case XML")
    void restoreCase_emptyCaseXml() {
        // Act & Assert
        YDataStateException exception = assertThrows(YDataStateException.class, () -> {
            engine.restoreCase("");
        });
        assertTrue(exception.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Restore case - invalid case XML")
    void restoreCase_invalidCaseXml() {
        // Act & Assert
        YDataStateException exception = assertThrows(YDataStateException.class, () -> {
            engine.restoreCase("<invalid><case>xml</case></invalid>");
        });
        assertTrue(exception.getMessage().contains("XML"));
    }

    // ==================== Event Listener Tests ====================

    @Test
    @DisplayName("Case events - case start")
    void caseEvents_caseStart() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        caseCompleteLatch = new CountDownLatch(1);

        // Act
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());

        // Assert
        assertTrue(caseCompleteLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(caseEvents.isEmpty());
        assertEquals(YEventType.CaseStarted, caseEvents.get(0).getType());
    }

    @Test
    @DisplayName("Case events - case complete")
    void caseEvents_caseComplete() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(SEQUENCE_SPEC_RESOURCE);
        caseCompleteLatch = new CountDownLatch(1);

        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);
        Map<String, String> outputData = new HashMap<>();
        outputData.put("result", "completed");

        // Act
        engine.completeWorkItem(workItem, outputData, null);

        // Assert
        assertTrue(caseCompleteLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(caseEvents.isEmpty());
        assertEquals(YEventType.CaseCompleted, caseEvents.get(caseEvents.size() - 1).getType());
    }

    @Test
    @DisplayName("Work item events - work item enabled")
    void workItemEvents_workItemEnabled() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(MINIMAL_SPEC_RESOURCE);
        itemEnabledLatch = new CountDownLatch(1);

        // Act
        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());

        // Assert
        assertTrue(itemEnabledLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(workItemEvents.isEmpty());
        assertEquals(YEventType.WorkItemEnabled, workItemEvents.get(0).getType());
    }

    @Test
    @DisplayName("Work item events - work item completed")
    void workItemEvents_workItemCompleted() throws Exception {
        // Arrange
        YSpecification spec = loadSpecification(SEQUENCE_SPEC_RESOURCE);
        itemEnabledLatch = new CountDownLatch(1);

        YNetRunner runner = engine.launchCase(spec, "test-case-123", new HashMap<>());
        List<YWorkItem> workItems = engine.getWorkItemsForCase(runner.getCaseID());
        assertFalse(workItems.isEmpty());

        YWorkItem workItem = workItems.get(0);
        Map<String, String> outputData = new HashMap<>();
        outputData.put("result", "completed");

        // Act
        engine.completeWorkItem(workItem, outputData, null);

        // Assert
        assertFalse(workItemEvents.isEmpty());
        // Should have both enabled and completed events
        boolean hasEnabled = workItemEvents.stream()
            .anyMatch(e -> e.getType() == YEventType.WorkItemEnabled);
        boolean hasCompleted = workItemEvents.stream()
            .anyMatch(e -> e.getType() == YEventType.WorkItemCompleted);
        assertTrue(hasEnabled);
        assertTrue(hasCompleted);
    }

    // ==================== Event Listener Implementations ====================

    @Override
    public void caseEventOccurred(YCaseEvent event) {
        caseEvents.add(event);

        if (event.getType() == YEventType.CaseStarted) {
            runnerCapture.set((YNetRunner) event.getSource());
        } else if (event.getType() == YEventType.CaseCompleted) {
            caseCompleteLatch.countDown();
        }
    }

    @Override
    public void workItemEventOccurred(YWorkItemEvent event) {
        workItemEvents.add(event);

        if (event.getType() == YEventType.WorkItemEnabled) {
            workItemCapture.set(event.getWorkItem());
            itemEnabledLatch.countDown();
        }
    }
}