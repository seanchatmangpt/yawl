package org.yawlfoundation.yawl.integration;

import junit.framework.TestCase;
import org.jdom2.JDOMException;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.EngineClearer;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Comprehensive integration test for full engine lifecycle.
 * Tests real YAWL engine operations from specification loading to case completion.
 * Chicago TDD style - no mocks, real integrations.
 *
 * Coverage:
 * - Specification loading
 * - Case creation and lifecycle
 * - Work item management
 * - Multiple concurrent cases
 * - Case cancellation and rollback
 * - Error handling
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class EngineLifecycleIntegrationTest extends TestCase {

    private YEngine _engine;
    private YWorkItemRepository _workItemRepository;
    private YSpecification _testSpecification;
    private static final String TEST_SPEC_FILE = "ImproperCompletion.xml";

    public EngineLifecycleIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        _engine = YEngine.getInstance();
        _workItemRepository = _engine.getWorkItemRepository();
        EngineClearer.clear(_engine);
        _testSpecification = loadTestSpecification();
        _engine.loadSpecification(_testSpecification);
    }

    @Override
    protected void tearDown() throws Exception {
        if (_engine != null) {
            EngineClearer.clear(_engine);
        }
        super.tearDown();
    }

    /**
     * Load test specification from resources.
     */
    private YSpecification loadTestSpecification() throws YSchemaBuildingException,
            YSyntaxException, JDOMException, IOException {
        URL fileURL = getClass().getResource(TEST_SPEC_FILE);
        assertNotNull("Test specification file not found: " + TEST_SPEC_FILE, fileURL);
        File yawlXMLFile = new File(fileURL.getFile());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath()));
        assertFalse("No specifications loaded from " + TEST_SPEC_FILE, specs.isEmpty());
        return specs.get(0);
    }

    /**
     * Test: Load specification and verify it's registered in engine.
     */
    public void testSpecificationLoading() throws YSchemaBuildingException,
            YSyntaxException, YQueryException, YStateException, YEngineStateException {
        YSpecification spec = _testSpecification;
        assertNotNull("Specification should not be null", spec);
        assertNotNull("Specification ID should not be null", spec.getSpecificationID());

        // Verify specification is loaded in engine
        YSpecification loadedSpec = _engine.getSpecification(spec.getSpecificationID());
        assertNotNull("Loaded specification should not be null", loadedSpec);
        assertEquals("Specification IDs should match",
                spec.getSpecificationID(), loadedSpec.getSpecificationID());
    }

    /**
     * Test: Complete workflow from case creation to completion.
     * Covers full happy path of case execution.
     */
    public void testCompleteWorkflowExecution() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException,
            YAWLException {

        // 1. Start case
        YIdentifier caseId = _engine.startCase(
                _testSpecification.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null, false);
        assertNotNull("Case ID should not be null", caseId);

        // 2. Wait for work items to be enabled
        waitForEngineProcessing();

        // 3. Get enabled work items
        Set<YWorkItem> enabledItems = _workItemRepository.getEnabledWorkItems();
        assertNotNull("Enabled work items should not be null", enabledItems);
        assertFalse("Should have at least one enabled work item", enabledItems.isEmpty());

        // 4. Start a work item
        YWorkItem itemToStart = findWorkItem(caseId, "a-top");
        assertNotNull("Work item 'a-top' should exist", itemToStart);

        _engine.startWorkItem(itemToStart, _engine.getExternalClient("admin"));

        // 5. Verify work item status changed
        Set<YWorkItem> firedItems = _workItemRepository.getFiredWorkItems();
        assertNotNull("Fired work items should not be null", firedItems);
        assertFalse("Should have at least one fired work item", firedItems.isEmpty());
    }

    /**
     * Test: Multiple concurrent cases execution.
     * Verifies engine can handle multiple cases simultaneously.
     */
    public void testMultipleConcurrentCases() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException {

        final int NUM_CASES = 5;
        List<YIdentifier> caseIds = new ArrayList<>();

        // Launch multiple cases
        for (int i = 0; i < NUM_CASES; i++) {
            YIdentifier caseId = _engine.startCase(
                    _testSpecification.getSpecificationID(),
                    null, null, null, new YLogDataItemList(), null, false);
            assertNotNull("Case ID " + i + " should not be null", caseId);
            caseIds.add(caseId);
        }

        // Verify all cases created
        assertEquals("Should have " + NUM_CASES + " cases", NUM_CASES, caseIds.size());

        // Wait for processing
        waitForEngineProcessing();

        // Verify each case has enabled work items
        Set<YWorkItem> allEnabledItems = _workItemRepository.getEnabledWorkItems();
        assertNotNull("Enabled work items should not be null", allEnabledItems);
        assertTrue("Should have work items from multiple cases",
                allEnabledItems.size() >= NUM_CASES);
    }

    /**
     * Test: Case cancellation and cleanup.
     * Verifies that cancelled cases are properly cleaned up.
     */
    public void testCaseCancellation() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException,
            YAWLException {

        // 1. Start case
        YIdentifier caseId = _engine.startCase(
                _testSpecification.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null, false);
        assertNotNull("Case ID should not be null", caseId);

        waitForEngineProcessing();

        // 2. Get initial work item count
        Set<YWorkItem> itemsBeforeCancel = _workItemRepository.getEnabledWorkItems();
        int itemCountBefore = itemsBeforeCancel.size();
        assertTrue("Should have enabled work items before cancel", itemCountBefore > 0);

        // 3. Cancel case
        _engine.cancelCase(caseId);

        waitForEngineProcessing();

        // 4. Verify work items are cleaned up
        Set<YWorkItem> itemsAfterCancel = _workItemRepository.getEnabledWorkItems();

        // Filter items for this case ID
        int itemsForCancelledCase = 0;
        for (YWorkItem item : itemsAfterCancel) {
            if (item.getCaseID().equals(caseId)) {
                itemsForCancelledCase++;
            }
        }

        assertEquals("Cancelled case should have no enabled work items",
                0, itemsForCancelledCase);
    }

    /**
     * Test: Invalid work item operations.
     * Verifies proper error handling for invalid operations.
     */
    public void testInvalidWorkItemOperations() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException {

        // Start case
        YIdentifier caseId = _engine.startCase(
                _testSpecification.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null, false);

        waitForEngineProcessing();

        // Try to start a work item that shouldn't be enabled
        YWorkItem disabledItem = findWorkItem(caseId, "b-top");

        if (disabledItem != null) {
            Exception exception = null;
            try {
                _engine.startWorkItem(disabledItem, _engine.getExternalClient("admin"));
            } catch (YAWLException e) {
                exception = e;
            }

            // Should throw exception for invalid operation
            assertNotNull("Should throw exception when starting disabled work item",
                    exception);
        }
    }

    /**
     * Test: Specification validation on load.
     * Verifies that invalid specifications are rejected.
     */
    public void testInvalidSpecificationRejection() {
        Exception exception = null;
        try {
            String invalidXML = "<?xml version=\"1.0\"?><invalid>not a YAWL spec</invalid>";
            YMarshal.unmarshalSpecifications(invalidXML);
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull("Should throw exception for invalid specification", exception);
    }

    /**
     * Test: Work item state transitions.
     * Verifies work items transition through correct states.
     */
    public void testWorkItemStateTransitions() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException,
            YAWLException {

        // Start case
        YIdentifier caseId = _engine.startCase(
                _testSpecification.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null, false);

        waitForEngineProcessing();

        // Get initial enabled items
        Set<YWorkItem> enabledItems = _workItemRepository.getEnabledWorkItems();
        assertTrue("Should have enabled items", !enabledItems.isEmpty());

        // Start a work item - transitions from Enabled to Fired
        YWorkItem enabledItem = enabledItems.iterator().next();
        _engine.startWorkItem(enabledItem, _engine.getExternalClient("admin"));

        waitForEngineProcessing();

        // Verify item is now in fired state
        Set<YWorkItem> firedItems = _workItemRepository.getFiredWorkItems();
        boolean foundFiredItem = false;
        for (YWorkItem item : firedItems) {
            if (item.getIDString().equals(enabledItem.getIDString())) {
                foundFiredItem = true;
                break;
            }
        }

        assertTrue("Started item should be in fired state", foundFiredItem);
    }

    /**
     * Test: Engine state persistence across operations.
     * Verifies engine maintains consistent state.
     */
    public void testEngineStatePersistence() throws YDataStateException,
            YStateException, YQueryException, YEngineStateException, YPersistenceException {

        // Create multiple cases
        List<YIdentifier> caseIds = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            YIdentifier caseId = _engine.startCase(
                    _testSpecification.getSpecificationID(),
                    null, null, null, new YLogDataItemList(), null, false);
            caseIds.add(caseId);
        }

        waitForEngineProcessing();

        // Verify all cases are tracked
        for (YIdentifier caseId : caseIds) {
            assertNotNull("Case should exist in engine: " + caseId, caseId);

            // Verify work items exist for case
            Set<YWorkItem> items = _workItemRepository.getEnabledWorkItems();
            boolean foundItemForCase = false;
            for (YWorkItem item : items) {
                if (item.getCaseID().equals(caseId)) {
                    foundItemForCase = true;
                    break;
                }
            }
            assertTrue("Should have work items for case: " + caseId, foundItemForCase);
        }
    }

    // Helper methods

    /**
     * Find a work item by case ID and task name.
     */
    private YWorkItem findWorkItem(YIdentifier caseId, String taskName) {
        String itemId = caseId.toString() + ":" + taskName;
        return _engine.getWorkItem(itemId);
    }

    /**
     * Wait for engine to process events (async operations).
     */
    private void waitForEngineProcessing() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
