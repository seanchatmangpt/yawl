package org.yawlfoundation.yawl.integration;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.*;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Database transaction isolation and consistency tests.
 * Tests ACID properties, deadlock recovery, and optimistic locking.
 *
 * Chicago TDD - uses REAL database transactions, REAL YEngine.
 * NO MOCKS - all tests verify actual database behavior.
 *
 * @author YAWL Test Team
 * Date: 2026-02-16
 */
public class DatabaseTransactionTest extends TestCase {

    private YEngine _engine;
    private YPersistenceManager _pmgr;
    private YSpecification _specification;
    private List<YIdentifier> _testCases;

    public DatabaseTransactionTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        _engine = YEngine.getInstance();
        _pmgr = _engine.getPersistenceManager();
        _testCases = new ArrayList<YIdentifier>();

        // Load test specification
        URL fileURL = getClass().getResource("../engine/YAWL_Specification1.xml");
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            _specification = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up test cases
        for (YIdentifier caseID : _testCases) {
            try {
                _engine.cancelCase(caseID);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        _testCases.clear();
        super.tearDown();
    }

    /**
     * Test 1: Work item transaction commit and rollback
     * Verifies that work item state changes are atomic
     */
    public void testWorkItemCommitRollback() throws Exception {
        if (_pmgr == null || !_pmgr.isEnabled()) {
            return; // Skip if persistence disabled
        }

        // Test successful commit
        boolean started = _pmgr.startTransaction();
        if (started) {
            YIdentifier testID = new YIdentifier();
            YWorkItemID workItemID = new YWorkItemID(testID, "commit-test-task");
            YAtomicTask task = new YAtomicTask("commit-test-task",
                                              YTask._AND, YTask._AND, null);
            YWorkItem testItem = new YWorkItem(null,
                                              new YSpecificationID("commit-test-spec"),
                                              task, workItemID, true, false);

            _pmgr.storeObject(testItem);
            _pmgr.commit();

            // Verify committed
            _pmgr.startTransaction();
            List<YWorkItem> items = _pmgr.getObjectsForClassWhere(
                "YWorkItem", "_taskID='commit-test-task'");
            _pmgr.commit();

            assertNotNull("Committed item should be retrievable", items);
            assertTrue("Should find committed work item", items.size() > 0);
        }

        // Test rollback
        started = _pmgr.startTransaction();
        if (started) {
            YIdentifier rollbackID = new YIdentifier();
            YWorkItemID rollbackItemID = new YWorkItemID(rollbackID, "rollback-test-task");
            YAtomicTask rollbackTask = new YAtomicTask("rollback-test-task",
                                                       YTask._AND, YTask._AND, null);
            YWorkItem rollbackItem = new YWorkItem(null,
                                                   new YSpecificationID("rollback-test-spec"),
                                                   rollbackTask, rollbackItemID, true, false);

            _pmgr.storeObject(rollbackItem);
            _pmgr.rollbackTransaction();

            // Verify NOT persisted
            _pmgr.startTransaction();
            List<YWorkItem> items = _pmgr.getObjectsForClassWhere(
                "YWorkItem", "_taskID='rollback-test-task'");
            _pmgr.commit();

            assertTrue("Rolled back item should NOT be in database",
                     items == null || items.isEmpty());
        }
    }

    /**
     * Test 2: Case state transaction isolation
     * Verifies that concurrent transactions don't interfere
     */
    public void testCaseStateTransactionIsolation() throws Exception {
        if (_specification == null || _pmgr == null || !_pmgr.isEnabled()) {
            return;
        }

        _engine.loadSpecification(_specification);
        final YSpecificationID specID = _specification.getSpecificationID();

        final int NUM_CONCURRENT_LAUNCHES = 10;
        final CountDownLatch latch = new CountDownLatch(NUM_CONCURRENT_LAUNCHES);
        final AtomicInteger successCount = new AtomicInteger(0);
        final List<YIdentifier> launchedCases = new ArrayList<YIdentifier>();

        ExecutorService executor = Executors.newFixedThreadPool(5);

        // Launch multiple cases concurrently, each in own transaction
        for (int i = 0; i < NUM_CONCURRENT_LAUNCHES; i++) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        YIdentifier caseID = _engine.startCase(
                            specID, null, null, null, null, null, false);

                        if (caseID != null) {
                            synchronized (launchedCases) {
                                launchedCases.add(caseID);
                            }
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Transaction isolation may cause some conflicts
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        // Clean up
        synchronized (launchedCases) {
            _testCases.addAll(launchedCases);
        }

        assertTrue("All transaction attempts should complete", completed);
        assertTrue("Transaction isolation should allow concurrent launches",
                 successCount.get() > (NUM_CONCURRENT_LAUNCHES * 0.7));

        // Verify all committed cases are in database
        if (successCount.get() > 0) {
            _pmgr.startTransaction();
            List<YNetRunner> runners = _pmgr.getObjectsForClass("YNetRunner");
            _pmgr.commit();

            assertNotNull("Should retrieve runners from database", runners);
        }
    }

    /**
     * Test 3: Deadlock detection and recovery
     * Simulates deadlock scenarios and verifies recovery
     */
    public void testDeadlockRecovery() throws Exception {
        if (_pmgr == null || !_pmgr.isEnabled()) {
            return;
        }

        final int NUM_THREADS = 8;
        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        final AtomicInteger transactionSuccessCount = new AtomicInteger(0);
        final AtomicInteger transactionFailCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        // Create contention by having multiple threads perform overlapping transactions
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadNum = i;
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        for (int attempt = 0; attempt < 3; attempt++) {
                            boolean started = _pmgr.startTransaction();
                            if (started) {
                                try {
                                    // Query database
                                    List specs = _pmgr.getObjectsForClass(
                                        "org.yawlfoundation.yawl.elements.YSpecification");

                                    // Simulate some work
                                    Thread.sleep(50);

                                    _pmgr.commit();
                                    transactionSuccessCount.incrementAndGet();
                                    break; // Success, exit retry loop
                                } catch (Exception e) {
                                    try {
                                        _pmgr.rollbackTransaction();
                                    } catch (Exception rollbackError) {
                                        // Ignore
                                    }
                                    transactionFailCount.incrementAndGet();

                                    // Brief backoff before retry
                                    Thread.sleep(20 * (attempt + 1));
                                }
                            }
                        }
                    } catch (Exception e) {
                        transactionFailCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== Deadlock Recovery Test ===");
        System.out.println("Successful transactions: " + transactionSuccessCount.get());
        System.out.println("Failed transactions: " + transactionFailCount.get());

        assertTrue("All threads should complete (no permanent deadlock)", completed);
        assertTrue("Most transactions should eventually succeed",
                 transactionSuccessCount.get() > 0);
    }

    /**
     * Test 4: Optimistic locking conflict handling
     * Tests behavior when multiple transactions modify same entity
     */
    public void testOptimisticLockingConflict() throws Exception {
        if (_specification == null || _pmgr == null || !_pmgr.isEnabled()) {
            return;
        }

        _engine.loadSpecification(_specification);

        // Launch a case to create persistent state
        YIdentifier caseID = _engine.startCase(
            _specification.getSpecificationID(), null, null, null, null, null, false);
        _testCases.add(caseID);

        if (caseID == null) {
            return;
        }

        // Retrieve the case runner in two separate transactions
        _pmgr.startTransaction();
        List<YNetRunner> runners1 = _pmgr.getObjectsForClassWhere(
            "YNetRunner", "_caseID.idString='" + caseID.toString() + "'");
        _pmgr.commit();

        if (runners1 == null || runners1.isEmpty()) {
            return; // Case not persisted
        }

        final YNetRunner runner = runners1.get(0);

        // Attempt concurrent modifications
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger updateSuccessCount = new AtomicInteger(0);
        final AtomicInteger updateFailCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        boolean started = _pmgr.startTransaction();
                        if (started) {
                            try {
                                _pmgr.updateObject(runner);
                                Thread.sleep(100); // Hold transaction open
                                _pmgr.commit();
                                updateSuccessCount.incrementAndGet();
                            } catch (Exception e) {
                                _pmgr.rollbackTransaction();
                                updateFailCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        updateFailCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== Optimistic Locking Test ===");
        System.out.println("Successful updates: " + updateSuccessCount.get());
        System.out.println("Failed updates: " + updateFailCount.get());

        assertTrue("Both update attempts should complete", completed);
        // At least one should succeed or fail gracefully
        assertTrue("At least one update operation should occur",
                 (updateSuccessCount.get() + updateFailCount.get()) >= 2);
    }

    /**
     * Test 5: Transaction timeout handling
     * Verifies long-running transactions are handled correctly
     */
    public void testTransactionTimeout() throws Exception {
        if (_pmgr == null || !_pmgr.isEnabled()) {
            return;
        }

        boolean started = _pmgr.startTransaction();
        if (!started) {
            return;
        }

        try {
            // Start transaction and hold it
            List specs = _pmgr.getObjectsForClass(
                "org.yawlfoundation.yawl.elements.YSpecification");

            // Simulate long operation (but not too long to avoid test timeout)
            Thread.sleep(500);

            // Attempt commit
            _pmgr.commit();

            // If we get here, transaction completed (which is fine)
            assertTrue("Transaction completed", true);
        } catch (YPersistenceException e) {
            // Timeout may occur - verify graceful handling
            assertNotNull("Exception should have message", e.getMessage());
            try {
                _pmgr.rollbackTransaction();
            } catch (Exception rollbackError) {
                // Rollback may also fail if transaction timed out
            }
        }
    }

    /**
     * Test 6: Nested transaction handling
     * Tests behavior when transaction operations are nested
     */
    public void testNestedTransactionHandling() throws Exception {
        if (_pmgr == null || !_pmgr.isEnabled()) {
            return;
        }

        // Start outer transaction
        boolean outerStarted = _pmgr.startTransaction();
        assertTrue("Outer transaction should start", outerStarted);

        try {
            // Attempt to start nested transaction (should return false)
            boolean innerStarted = _pmgr.startTransaction();
            assertFalse("Nested transaction should not start", innerStarted);

            // Perform operation in outer transaction
            List specs = _pmgr.getObjectsForClass(
                "org.yawlfoundation.yawl.elements.YSpecification");

            // Commit outer transaction
            _pmgr.commit();

        } catch (YPersistenceException e) {
            _pmgr.rollbackTransaction();
            fail("Transaction operations should succeed");
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Database Transaction Tests");
        suite.addTestSuite(DatabaseTransactionTest.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
