package org.yawlfoundation.yawl.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.*;
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
import org.yawlfoundation.yawl.stateless.monitor.YCase;
import org.yawlfoundation.yawl.stateless.monitor.YCaseExporter;
import org.yawlfoundation.yawl.stateless.monitor.YCaseImporter;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration test for YStatelessEngine.
 * Tests stateless engine operations including case export/import,
 * concurrent execution, and event handling.
 * Chicago TDD style - real integrations, no mocks.
 *
 * Coverage:
 * - Case launch and execution
 * - Work item lifecycle
 * - Case export and import (state serialization)
 * - Concurrent case execution
 * - Event listener integration
 * - Case monitoring and idle timeout
 * - Error handling
 *
 * @author YAWL Foundation
 * @version 5.2
 */
class StatelessEngineIntegrationTest implements YCaseEventListener, YWorkItemEventListener {

    private YStatelessEngine _engine;
    private YSpecification _testSpec;
    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_WAIT_TIMEOUT_MS = 5000;

    // Event tracking
    private final List<YEventType> _caseEvents = new ArrayList<>();
    private final List<YEventType> _workItemEvents = new ArrayList<>();
    private final CountDownLatch _eventLatch = new CountDownLatch(1);

    @BeforeEach
    void setUp() throws Exception {
        _engine = new YStatelessEngine();
        _engine.addCaseEventListener(this);
        _engine.addWorkItemEventListener(this);
        _caseEvents.clear();
        _workItemEvents.clear();

        // Load test specification
        String specXml = loadMinimalSpecXml();
        _testSpec = _engine.unmarshalSpecification(specXml);
        assertNotNull(_testSpec, "Test specification should not be null");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (_engine != null) {
            _engine.removeCaseEventListener(this);
            _engine.removeWorkItemEventListener(this);
        }
    }

    // Event listener implementations
    @Override
    public void handleCaseEvent(YCaseEvent event) {
        synchronized (_caseEvents) {
            _caseEvents.add(event.getEventType());
        }
        _eventLatch.countDown();
    }

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        synchronized (_workItemEvents) {
            _workItemEvents.add(event.getEventType());
        }
    }

    /**
     * Load minimal spec XML from classpath resource.
     */
    private String loadMinimalSpecXml() {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Missing resource: " + MINIMAL_SPEC_RESOURCE);
        String xml = StringUtil.streamToString(is);
        assertNotNull(xml, "Empty spec XML");
        return xml;
    }

    /**
     * Test: Unmarshal specification and verify structure.
     */
    @Test
    void testUnmarshalSpecification() throws YSyntaxException {
        String xml = loadMinimalSpecXml();
        YSpecification spec = _engine.unmarshalSpecification(xml);

        assertNotNull(spec, "Specification should not be null");
        assertNotNull(spec.getRootNet(), "Root net should not be null");
        assertEquals("MinimalSpec", spec.getID(), "Specification ID should match");
        assertTrue(spec.getDecompositions().size() > 0, "Should have decompositions");
        assertNotNull(spec.getRootNet().getInputCondition(), "Should have input condition");
        assertNotNull(spec.getRootNet().getOutputCondition(), "Should have output condition");
    }

    /**
     * Test: Launch case and verify it starts.
     */
    @Test
    void testLaunchCase() throws YDataStateException, YStateException,
            YQueryException, YEngineStateException, YPersistenceException, YSchemaBuildingException {

        YIdentifier caseId = _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        assertNotNull(caseId, "Case ID should not be null");
        assertNotNull(caseId.toString(), "Case ID string should not be empty");

        // Verify case started event was fired
        waitForEvents();
        synchronized (_caseEvents) {
            assertTrue(_caseEvents.size() > 0, "Should have case events");
            assertTrue(_caseEvents.contains(YEventType.CASE_STARTED), "Should have case started event");
        }
    }

    /**
     * Test: Start and complete work item lifecycle.
     */
    @Test
    void testWorkItemLifecycle() throws YDataStateException, YStateException,
            YQueryException, YEngineStateException, YPersistenceException,
            YSchemaBuildingException, YAWLException {

        // Launch case
        YIdentifier caseId = _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        waitForProcessing();

        // Get enabled work items
        YNetRunner runner = _engine.getNetRunner(caseId);
        assertNotNull(runner, "Net runner should not be null");

        Set<YWorkItem> enabledItems = runner.getEnabledWorkItems();
        assertNotNull(enabledItems, "Enabled items should not be null");
        assertFalse(enabledItems.isEmpty(), "Should have enabled work items");

        // Start a work item
        YWorkItem item = enabledItems.iterator().next();
        _engine.startWorkItem(item, null);

        waitForProcessing();

        // Verify work item event
        synchronized (_workItemEvents) {
            assertTrue(_workItemEvents.size() > 0, "Should have work item events");
            assertTrue(_workItemEvents.contains(YEventType.ITEM_FIRED), "Should have work item fired event");
        }
    }

    /**
     * Test: Case export and import (state serialization).
     * Verifies stateless engine can serialize and deserialize case state.
     */
    @Test
    void testCaseExportImport() throws YDataStateException, YStateException,
            YQueryException, YEngineStateException, YPersistenceException,
            YSchemaBuildingException, YAWLException {

        // Launch case
        YIdentifier caseId = _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        waitForProcessing();

        // Export case state
        YCaseExporter exporter = new YCaseExporter(_engine.getNetRunner(caseId));
        YCase exportedCase = exporter.exportCase();

        assertNotNull(exportedCase, "Exported case should not be null");
        assertNotNull(exportedCase.getCaseID(), "Exported case ID should not be null");
        assertEquals(caseId.toString(), exportedCase.getCaseID(), "Case IDs should match");

        // Import case state into new engine instance
        YStatelessEngine newEngine = new YStatelessEngine();
        String specXml = loadMinimalSpecXml();
        YSpecification spec = newEngine.unmarshalSpecification(specXml);

        YCaseImporter importer = new YCaseImporter(spec);
        YNetRunner importedRunner = importer.importCase(exportedCase);

        assertNotNull(importedRunner, "Imported runner should not be null");
        assertEquals(caseId.toString(), importedRunner.getCaseID().toString(), "Imported case ID should match");
    }

    /**
     * Test: Multiple concurrent cases in stateless mode.
     * Verifies stateless engine handles concurrency correctly.
     */
    @Test
    void testConcurrentCaseExecution() throws InterruptedException, ExecutionException {
        final int NUM_CONCURRENT_CASES = 5;
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CONCURRENT_CASES);

        List<Future<YIdentifier>> futures = new ArrayList<>();

        // Launch cases concurrently
        for (int i = 0; i < NUM_CONCURRENT_CASES; i++) {
            Future<YIdentifier> future = executor.submit(() -> {
                try {
                    return _engine.launchCase(
                            _testSpec.getSpecificationID(),
                            null, null, null, new YLogDataItemList(), null);
                } catch (Exception e) {
                    fail("Failed to launch case: " + e.getMessage());
                    return null;
                }
            });
            futures.add(future);
        }

        // Verify all cases launched successfully
        List<YIdentifier> caseIds = new ArrayList<>();
        for (Future<YIdentifier> future : futures) {
            YIdentifier caseId = future.get(5, TimeUnit.SECONDS);
            assertNotNull(caseId, "Case ID should not be null");
            caseIds.add(caseId);
        }

        assertEquals(NUM_CONCURRENT_CASES, caseIds.size(),
                "Should have launched " + NUM_CONCURRENT_CASES + " cases");

        // Verify all case IDs are unique
        long uniqueCount = caseIds.stream().map(YIdentifier::toString).distinct().count();
        assertEquals(NUM_CONCURRENT_CASES, uniqueCount, "All case IDs should be unique");

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor should terminate");
    }

    /**
     * Test: Case monitoring with idle timeout.
     * Verifies case monitor correctly tracks and reports idle cases.
     */
    @Test
    void testCaseMonitoring() throws YDataStateException, YStateException,
            YQueryException, YEngineStateException, YPersistenceException, YSchemaBuildingException {

        // Enable case monitoring with short timeout
        final long IDLE_TIMEOUT_MS = 1000;
        _engine.setCaseMonitoringEnabled(true, IDLE_TIMEOUT_MS);
        assertTrue(_engine.isCaseMonitoringEnabled(), "Case monitoring should be enabled");

        // Launch case
        YIdentifier caseId = _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        assertNotNull(caseId, "Case ID should not be null");

        // Wait for idle timeout
        waitForProcessing(IDLE_TIMEOUT_MS + 1000);

        // Verify idle timeout event was fired
        synchronized (_caseEvents) {
            boolean hasIdleEvent = _caseEvents.contains(YEventType.CASE_IDLE_TIMEOUT);
            assertTrue(hasIdleEvent, "Should have idle timeout event");
        }

        // Cleanup
        _engine.setCaseMonitoringEnabled(false);
        assertFalse(_engine.isCaseMonitoringEnabled(), "Case monitoring should be disabled");
    }

    /**
     * Test: Engine number assignment.
     * Verifies each engine instance has unique number.
     */
    @Test
    void testEngineNumbering() {
        int engine1Number = _engine.getEngineNbr();
        assertTrue(engine1Number > 0, "Engine number should be positive");

        YStatelessEngine engine2 = new YStatelessEngine();
        int engine2Number = engine2.getEngineNbr();
        assertTrue(engine2Number > 0, "Engine 2 number should be positive");

        assertNotEquals(engine1Number, engine2Number, "Engine numbers should be unique");
    }

    /**
     * Test: Event listener add/remove.
     * Verifies listener registration and deregistration.
     */
    @Test
    void testEventListenerManagement() throws YDataStateException, YStateException,
            YQueryException, YEngineStateException, YPersistenceException, YSchemaBuildingException {

        // Create test listener
        final List<YEventType> externalCaseEvents = new ArrayList<>();
        YCaseEventListener externalListener = event -> {
            synchronized (externalCaseEvents) {
                externalCaseEvents.add(event.getEventType());
            }
        };

        // Add listener
        _engine.addCaseEventListener(externalListener);

        // Launch case
        _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        waitForEvents();

        // Verify listener received events
        synchronized (externalCaseEvents) {
            assertTrue(externalCaseEvents.size() > 0, "External listener should receive events");
        }

        // Remove listener
        _engine.removeCaseEventListener(externalListener);
        externalCaseEvents.clear();

        // Launch another case
        _engine.launchCase(
                _testSpec.getSpecificationID(),
                null, null, null, new YLogDataItemList(), null);

        waitForEvents();

        // Verify listener no longer receives events
        synchronized (externalCaseEvents) {
            assertEquals(0, externalCaseEvents.size(),
                    "External listener should not receive events after removal");
        }
    }

    /**
     * Test: Invalid specification handling.
     * Verifies proper error handling for invalid specs.
     */
    @Test
    void testInvalidSpecificationHandling() {
        Exception exception = null;
        try {
            String invalidXml = "<?xml version=\"1.0\"?><invalid>not a valid spec</invalid>";
            _engine.unmarshalSpecification(invalidXml);
        } catch (Exception e) {
            exception = e;
        }

        assertNotNull(exception, "Should throw exception for invalid specification");
        assertTrue(exception instanceof YSyntaxException || exception instanceof YSchemaBuildingException,
                "Exception should be YSyntaxException or similar");
    }

    /**
     * Test: Idle timer updates.
     * Verifies case monitor idle timer can be updated dynamically.
     */
    @Test
    void testIdleTimerUpdates() {
        // Enable monitoring with initial timeout
        final long INITIAL_TIMEOUT = 5000;
        _engine.setCaseMonitoringEnabled(true, INITIAL_TIMEOUT);
        assertTrue(_engine.isCaseMonitoringEnabled(), "Case monitoring should be enabled");

        // Update timeout
        final long UPDATED_TIMEOUT = 10000;
        _engine.setIdleCaseTimer(UPDATED_TIMEOUT);
        assertTrue(_engine.isCaseMonitoringEnabled(), "Case monitoring should still be enabled");

        // Disable monitoring
        _engine.setIdleCaseTimer(-1);
        assertFalse(_engine.isCaseMonitoringEnabled(),
                "Case monitoring should be disabled with negative timeout");
    }

    // Helper methods

    /**
     * Wait for asynchronous events to be processed.
     */
    private void waitForEvents() {
        try {
            _eventLatch.await(EVENT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait for engine processing with default timeout.
     */
    private void waitForProcessing() {
        waitForProcessing(1000);
    }

    /**
     * Wait for engine processing with specified timeout.
     */
    private void waitForProcessing(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
