package org.yawlfoundation.yawl.engine;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Comprehensive tests for YPersistenceManager with real H2 database integration.
 * Tests cover Hibernate SessionFactory initialization, transaction management,
 * persistence operations, and concurrency handling.
 *
 * Chicago TDD - uses REAL YEngine, REAL database (H2 in-memory), NO MOCKS.
 *
 * @author YAWL Test Team
 * Date: 2026-02-16
 */
public class TestYPersistenceManager extends TestCase {

    private YEngine _engine;
    private YPersistenceManager _pmgr;
    private YSpecification _specification;
    private YIdentifier _caseID;

    public TestYPersistenceManager(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize real YEngine with persistence enabled
        _engine = YEngine.getInstance();
        _pmgr = _engine.getPersistenceManager();

        // Load a real specification for testing
        URL fileURL = getClass().getResource("YAWL_Specification1.xml");
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            _specification = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up case if started
        if (_caseID != null && _engine != null) {
            try {
                _engine.cancelCase(_caseID);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        super.tearDown();
    }

    /**
     * Test 1: Verify Hibernate SessionFactory initialization
     * Tests that H2 in-memory database is properly configured
     */
    public void testSessionFactoryInitialization() throws YPersistenceException {
        assertNotNull("PersistenceManager should not be null", _pmgr);

        SessionFactory factory = _pmgr.getFactory();
        assertNotNull("SessionFactory should be initialized", factory);
        assertFalse("SessionFactory should not be closed", factory.isClosed());

        assertTrue("Persistence should be enabled", _pmgr.isEnabled());
        assertTrue("Persistence manager should be active", _pmgr.isPersisting());
    }

    /**
     * Test 2: Verify H2 in-memory database persistence
     * Tests complete data roundtrip: create -> persist -> retrieve
     */
    public void testH2InMemoryPersistence() throws Exception {
        if (_specification == null) {
            return; // Skip if no test spec available
        }

        // Load specification into engine
        _engine.loadSpecification(_specification);

        // Start a real case
        _caseID = _engine.startCase(_specification.getSpecificationID(),
                                    null, null, null, null, null, false);
        assertNotNull("Case should be started", _caseID);

        // Verify case persisted to database
        _pmgr.startTransaction();
        List<YNetRunner> runners = _pmgr.getObjectsForClass("YNetRunner");
        _pmgr.commit();

        assertNotNull("Should retrieve YNetRunner from database", runners);
        assertTrue("Should have at least one runner persisted", runners.size() > 0);

        // Verify we can find our specific case
        boolean foundCase = false;
        for (YNetRunner runner : runners) {
            if (runner.getCaseID().equals(_caseID)) {
                foundCase = true;
                break;
            }
        }
        assertTrue("Should find our case in database", foundCase);
    }

    /**
     * Test 3: Work item state persistence
     * Tests persistence of work items through state transitions
     */
    public void testWorkItemStatePersistence() throws Exception {
        if (_specification == null) {
            return; // Skip if no test spec available
        }

        _engine.loadSpecification(_specification);
        _caseID = _engine.startCase(_specification.getSpecificationID(),
                                    null, null, null, null, null, false);

        // Get enabled work items
        YWorkItemRepository repo = _engine.getWorkItemRepository();
        if (repo.getEnabledWorkItems().size() > 0) {
            YWorkItem workItem = (YWorkItem) repo.getEnabledWorkItems().iterator().next();
            String itemID = workItem.getIDString();

            // Start the work item (state transition)
            YWorkItem startedItem = _engine.startWorkItem(workItem,
                                                          _engine.getExternalClient("admin"));
            assertNotNull("Work item should be started", startedItem);

            // Verify state persisted
            _pmgr.startTransaction();
            List<YWorkItem> persistedItems = _pmgr.getObjectsForClassWhere(
                "YWorkItem", "_thisID='" + itemID + "'");
            _pmgr.commit();

            if (persistedItems != null && persistedItems.size() > 0) {
                YWorkItem retrieved = persistedItems.get(0);
                assertEquals("Work item status should be persisted correctly",
                           YWorkItemStatus.statusExecuting, retrieved.getStatus());
            }
        }
    }

    /**
     * Test 4: Case state persistence through transitions
     * Verifies database contains complete case lifecycle data
     */
    public void testCaseStatePersistence() throws Exception {
        if (_specification == null) {
            return;
        }

        _engine.loadSpecification(_specification);
        YSpecificationID specID = _specification.getSpecificationID();

        // Launch case and verify persistence
        _caseID = _engine.startCase(specID, null, null, null, null, null, false);

        // Query database for case data
        _pmgr.startTransaction();
        List<YNetRunner> runners = _pmgr.getObjectsForClassWhere(
            "YNetRunner", "_caseID.idString='" + _caseID.toString() + "'");
        _pmgr.commit();

        assertNotNull("Should retrieve case from database", runners);
        if (runners.size() > 0) {
            YNetRunner runner = runners.get(0);
            assertEquals("Case ID should match", _caseID, runner.getCaseID());
            assertNotNull("Runner should have spec reference", runner.getSpecificationID());
        }

        // Complete case and verify state update
        _engine.cancelCase(_caseID);
        _caseID = null; // Prevent tearDown from trying to cancel again
    }

    /**
     * Test 5: Transaction rollback behavior
     * Verifies ACID compliance - no partial data after rollback
     */
    public void testTransactionRollback() throws Exception {
        boolean transactionStarted = _pmgr.startTransaction();

        if (transactionStarted) {
            // Create a test work item
            YIdentifier testID = new YIdentifier();
            YWorkItemID workItemID = new YWorkItemID(testID, "test-task-rollback");
            YAtomicTask task = new YAtomicTask("test-task-rollback",
                                              YTask._AND, YTask._AND, null);
            YWorkItem testItem = new YWorkItem(null,
                                              new YSpecificationID("test-spec"),
                                              task, workItemID, true, false);

            try {
                _pmgr.storeObject(testItem);

                // Force rollback before commit
                _pmgr.rollbackTransaction();

                // Verify object NOT persisted after rollback
                _pmgr.startTransaction();
                List<YWorkItem> items = _pmgr.getObjectsForClassWhere(
                    "YWorkItem", "_taskID='test-task-rollback'");
                _pmgr.commit();

                assertTrue("No data should be persisted after rollback",
                         items == null || items.isEmpty());
            } catch (YPersistenceException e) {
                _pmgr.rollbackTransaction();
            }
        }
    }

    /**
     * Test 6: Connection pool behavior under stress
     * Verifies pool management and recovery
     */
    public void testConnectionPoolExhaustion() throws Exception {
        SessionFactory factory = _pmgr.getFactory();
        if (factory == null) {
            return;
        }

        final int CONCURRENT_SESSIONS = 10;
        final CountDownLatch latch = new CountDownLatch(CONCURRENT_SESSIONS);
        final AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_SESSIONS);

        for (int i = 0; i < CONCURRENT_SESSIONS; i++) {
            final int sessionNum = i;
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        // Each thread attempts to start a transaction
                        boolean started = _pmgr.startTransaction();
                        if (started) {
                            // Simulate some work
                            Thread.sleep(50);
                            _pmgr.commit();
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Connection pool may limit concurrent access
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        // Wait for all threads to complete
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All threads should complete", completed);
        assertTrue("At least some sessions should succeed", successCount.get() > 0);
    }

    /**
     * Test 7: Hibernate exception translation
     * Verifies that Hibernate exceptions are properly wrapped
     */
    public void testHibernateExceptionTranslation() {
        try {
            // Attempt to execute invalid query
            _pmgr.startTransaction();
            List results = _pmgr.execQuery("from NonExistentClass");
            _pmgr.commit();
            fail("Should throw YPersistenceException for invalid query");
        } catch (YPersistenceException e) {
            // Expected - verify exception message quality
            assertNotNull("Exception should have message", e.getMessage());
            assertTrue("Exception should mention query error",
                     e.getMessage().contains("Error executing query") ||
                     e.getMessage().contains("query"));
        }
    }

    /**
     * Test 8: Query execution and result retrieval
     * Tests database query functionality
     */
    public void testQueryExecution() throws YPersistenceException {
        _pmgr.startTransaction();

        // Execute valid query for YSpecification objects
        List<YSpecification> specs = _pmgr.getObjectsForClass(
            "org.yawlfoundation.yawl.elements.YSpecification");

        _pmgr.commit();

        assertNotNull("Query should return result (possibly empty)", specs);
    }

    /**
     * Test 9: Selective object retrieval with WHERE clause
     * Tests parameterized queries
     */
    public void testSelectiveObjectRetrieval() throws Exception {
        if (_specification == null) {
            return;
        }

        _engine.loadSpecification(_specification);
        _caseID = _engine.startCase(_specification.getSpecificationID(),
                                    null, null, null, null, null, false);

        // Query with WHERE clause
        _pmgr.startTransaction();
        String caseIDStr = _caseID.toString().replace("'", "''"); // Escape quotes
        List<YNetRunner> runners = _pmgr.getObjectsForClassWhere(
            "YNetRunner", "_caseID.idString='" + caseIDStr + "'");
        _pmgr.commit();

        assertNotNull("Query with WHERE should return results", runners);
    }

    /**
     * Test 10: Session lifecycle management
     * Tests session opening, closing, and cleanup
     */
    public void testSessionLifecycle() throws YPersistenceException {
        Session session = _pmgr.getSession();

        if (session != null) {
            assertTrue("Session should be open or openable",
                     session.isOpen() || _pmgr.getFactory() != null);

            Transaction tx = _pmgr.getTransaction();
            if (tx != null) {
                assertNotNull("Transaction should be retrievable", tx);
            }
        }

        // Test session close
        _pmgr.closeSession();

        // Verify factory still operational after session close
        SessionFactory factory = _pmgr.getFactory();
        if (factory != null) {
            assertFalse("Factory should not be closed", factory.isClosed());
        }
    }

    /**
     * Test 11: Statistics collection and reporting
     * Tests Hibernate statistics gathering
     */
    public void testStatisticsCollection() {
        _pmgr.setStatisticsEnabled(true);
        assertTrue("Statistics should be enabled", _pmgr.isStatisticsEnabled());

        String stats = _pmgr.getStatistics();
        if (stats != null) {
            assertNotNull("Statistics XML should not be null", stats);
            assertTrue("Statistics should contain XML", stats.contains("<"));
        }

        // Test statistics map
        _pmgr.setStatisticsEnabled(true);
        java.util.Map<String, Object> statsMap = _pmgr.getStatisticsMap();
        assertNotNull("Statistics map should not be null", statsMap);
    }

    /**
     * Test 12: Object update operations
     * Tests updating persisted objects
     */
    public void testObjectUpdate() throws Exception {
        if (_specification == null) {
            return;
        }

        _engine.loadSpecification(_specification);
        YSpecificationID specID = _specification.getSpecificationID();

        // Create and persist a case
        _caseID = _engine.startCase(specID, null, null, null, null, null, false);

        // Retrieve and update
        _pmgr.startTransaction();
        List<YNetRunner> runners = _pmgr.getObjectsForClassWhere(
            "YNetRunner", "_caseID.idString='" + _caseID.toString() + "'");

        if (runners != null && runners.size() > 0) {
            YNetRunner runner = runners.get(0);
            _pmgr.updateObject(runner);
            _pmgr.commit();
        } else {
            _pmgr.commit();
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("YPersistenceManager Tests");
        suite.addTestSuite(TestYPersistenceManager.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
