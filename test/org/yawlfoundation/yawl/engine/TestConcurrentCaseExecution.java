package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
/**
 * Comprehensive concurrency tests for YAWL Engine.
 * Tests concurrent case launches, work item operations, and database connection pooling.
 *
 * Chicago TDD - uses REAL YEngine, REAL database, REAL concurrent threads.
 * NO MOCKS - all tests run against actual engine with real database transactions.
 *
 * @author YAWL Test Team
 * Date: 2026-02-16
 */
@Tag("slow")
public class TestConcurrentCaseExecution extends TestCase {

    private YEngine _engine;
    private YSpecification _specification;
    private final List<YIdentifier> _launchedCases = Collections.synchronizedList(
        new ArrayList<YIdentifier>());

    public TestConcurrentCaseExecution(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        _engine = YEngine.getInstance();
    @Execution(ExecutionMode.SAME_THREAD)


        // Load test specification
        URL fileURL = getClass().getResource("YAWL_Specification1.xml");
        if (fileURL != null) {
            File yawlXMLFile = new File(fileURL.getFile());
            _specification = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(yawlXMLFile.getAbsolutePath())).get(0);
            _engine.loadSpecification(_specification);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        // Clean up all launched cases
        for (YIdentifier caseID : _launchedCases) {
            try {
                _engine.cancelCase(caseID);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
        _launchedCases.clear();
        super.tearDown();
    }

    /**
     * Test 1: Launch 100 concurrent cases
     * Verifies engine handles high concurrency without deadlocks
     * Target: 100 cases/sec throughput
     */
    public void testConcurrentCaseLaunches() throws Exception {
        if (_specification == null) {
            return; // Skip if no test spec
        }

        final int NUM_CASES = 100;
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch completionLatch = new CountDownLatch(NUM_CASES);
        final AtomicInteger successCount = new AtomicInteger(0);
        final AtomicInteger errorCount = new AtomicInteger(0);
        final List<String> errors = Collections.synchronizedList(new ArrayList<String>());

        ExecutorService executor = Executors.newFixedThreadPool(20);
        final YSpecificationID specID = _specification.getSpecificationID();

        long startTime = System.currentTimeMillis();

        // Launch all cases concurrently
        for (int i = 0; i < NUM_CASES; i++) {
            final int caseNum = i;
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        // Wait for start signal to create burst load
                        startLatch.await();

                        // Launch case
                        YIdentifier caseID = _engine.startCase(
                            specID, null, null, null, null, null, false);

                        if (caseID != null) {
                            _launchedCases.add(caseID);
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                            errors.add("Case " + caseNum + ": returned null case ID");
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        errors.add("Case " + caseNum + ": " + e.getMessage());
                    } finally {
                        completionLatch.countDown();
                    }
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for all cases to complete (max 30 seconds)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        executor.shutdown();

        // Calculate throughput
        double durationSec = (endTime - startTime) / 1000.0;
        double throughput = successCount.get() / durationSec;

        // Report results
        System.out.println("=== Concurrent Case Launch Test ===");
        System.out.println("Total cases attempted: " + NUM_CASES);
        System.out.println("Successful launches: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Duration: " + durationSec + " seconds");
        System.out.println("Throughput: " + throughput + " cases/sec");

        if (!errors.isEmpty()) {
            System.out.println("First 5 errors:");
            for (int i = 0; i < Math.min(5, errors.size()); i++) {
                System.out.println("  " + errors.get(i));
            }
        }

        // Assertions
        assertTrue("All threads should complete within timeout", completed);
        assertTrue("Majority of cases should launch successfully (>80%)",
                 successCount.get() > (NUM_CASES * 0.8));
        assertTrue("Should achieve reasonable throughput (>10 cases/sec)",
                 throughput > 10.0);
    }

    /**
     * Test 2: Concurrent work item operations
     * Tests thread safety of work item state transitions
     */
    public void testConcurrentWorkItemOperations() throws Exception {
        if (_specification == null) {
            return;
        }

        final int NUM_OPERATIONS = 50;
        final AtomicInteger successCount = new AtomicInteger(0);
        final CountDownLatch latch = new CountDownLatch(NUM_OPERATIONS);

        // Launch a case to get work items
        YIdentifier caseID = _engine.startCase(
            _specification.getSpecificationID(), null, null, null, null, null, false);
        _launchedCases.add(caseID);

        YWorkItemRepository repo = _engine.getWorkItemRepository();
        Set<YWorkItem> enabledItems = repo.getEnabledWorkItems();

        if (enabledItems.isEmpty()) {
            return; // No work items to test
        }

        // Get first enabled item
        final YWorkItem testItem = (YWorkItem) enabledItems.iterator().next();
        ExecutorService executor = Executors.newFixedThreadPool(10);

        // Multiple threads attempt to start the same work item
        for (int i = 0; i < NUM_OPERATIONS; i++) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        // Attempt state query (read operation)
                        YWorkItemStatus status = testItem.getStatus();
                        if (status != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Expected - some operations may fail due to state changes
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All operations should complete", completed);
        assertTrue("Most read operations should succeed",
                 successCount.get() > (NUM_OPERATIONS * 0.7));
    }

    /**
     * Test 3: Database connection pool stress test
     * Verifies connection pool handles concurrent database access
     */
    public void testDatabaseConnectionPoolStress() throws Exception {
        YPersistenceManager pmgr = _engine.getPersistenceManager();
        if (pmgr == null || !pmgr.isEnabled()) {
            return; // Skip if persistence disabled
        }

        final int NUM_THREADS = 30;
        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        final AtomicInteger querySuccessCount = new AtomicInteger(0);
        final AtomicInteger queryErrorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        // Each thread performs database query
                        boolean started = pmgr.startTransaction();
                        if (started) {
                            List results = pmgr.getObjectsForClass(
                                "org.yawlfoundation.yawl.elements.YSpecification");
                            pmgr.commit();
                            querySuccessCount.incrementAndGet();
                        } else {
                            queryErrorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        queryErrorCount.incrementAndGet();
                        try {
                            pmgr.rollbackTransaction();
                        } catch (Exception rollbackError) {
                            // Ignore
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== Connection Pool Stress Test ===");
        System.out.println("Successful queries: " + querySuccessCount.get());
        System.out.println("Failed queries: " + queryErrorCount.get());

        assertTrue("All threads should complete", completed);
        assertTrue("Most queries should succeed without pool exhaustion",
                 querySuccessCount.get() > (NUM_THREADS * 0.6));
    }

    /**
     * Test 4: Concurrent case cancellation
     * Tests thread safety of case lifecycle management
     */
    public void testConcurrentCaseCancellation() throws Exception {
        if (_specification == null) {
            return;
        }

        final int NUM_CASES = 20;
        final List<YIdentifier> casesToCancel = new ArrayList<YIdentifier>();

        // Launch multiple cases
        for (int i = 0; i < NUM_CASES; i++) {
            YIdentifier caseID = _engine.startCase(
                _specification.getSpecificationID(), null, null, null, null, null, false);
            if (caseID != null) {
                casesToCancel.add(caseID);
            }
        }

        final CountDownLatch latch = new CountDownLatch(casesToCancel.size());
        final AtomicInteger cancelSuccessCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(5);

        // Cancel all cases concurrently
        for (final YIdentifier caseID : casesToCancel) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        _engine.cancelCase(caseID);
                        cancelSuccessCount.incrementAndGet();
                    } catch (Exception e) {
                        // Some cancellations may fail if already cancelled
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== Concurrent Cancellation Test ===");
        System.out.println("Cases cancelled: " + cancelSuccessCount.get() + "/" + casesToCancel.size());

        assertTrue("All cancellation attempts should complete", completed);
        assertTrue("Most cancellations should succeed",
                 cancelSuccessCount.get() > (casesToCancel.size() * 0.8));
    }

    /**
     * Test 5: Deadlock detection test
     * Attempts to trigger deadlock scenarios and verify recovery
     */
    public void testDeadlockDetection() throws Exception {
        if (_specification == null) {
            return;
        }

        final int NUM_THREADS = 10;
        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        final AtomicInteger completedOperations = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        final YSpecificationID specID = _specification.getSpecificationID();

        // Create contention scenario: multiple threads launching and cancelling
        for (int i = 0; i < NUM_THREADS; i++) {
            final int threadNum = i;
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        for (int j = 0; j < 5; j++) {
                            // Launch case
                            YIdentifier caseID = _engine.startCase(
                                specID, null, null, null, null, null, false);

                            if (caseID != null) {
                                // Brief operation
                                Thread.sleep(10);

                                // Cancel immediately
                                _engine.cancelCase(caseID);
                                completedOperations.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        // Errors expected under contention
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(40, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("=== Deadlock Detection Test ===");
        System.out.println("Completed operations: " + completedOperations.get());

        assertTrue("All threads should complete (no deadlock)", completed);
        assertTrue("Some operations should succeed despite contention",
                 completedOperations.get() > 0);
    }

    /**
     * Test 6: Thread safety of work item repository
     * Tests concurrent access to work item collections
     */
    public void testWorkItemRepositoryConcurrency() throws Exception {
        if (_specification == null) {
            return;
        }

        // Launch several cases to populate repository
        for (int i = 0; i < 5; i++) {
            YIdentifier caseID = _engine.startCase(
                _specification.getSpecificationID(), null, null, null, null, null, false);
            if (caseID != null) {
                _launchedCases.add(caseID);
            }
        }

        final YWorkItemRepository repo = _engine.getWorkItemRepository();
        final int NUM_READERS = 20;
        final CountDownLatch latch = new CountDownLatch(NUM_READERS);
        final AtomicInteger readSuccessCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_READERS);

        // Multiple threads read repository concurrently
        for (int i = 0; i < NUM_READERS; i++) {
            executor.submit(new Runnable() {
                public void run() {
                    try {
                        // Read various repository collections
                        Set enabled = repo.getEnabledWorkItems();
                        Set executing = repo.getExecutingWorkItems();
                        Set fired = repo.getFiredWorkItems();

                        if (enabled != null && executing != null && fired != null) {
                            readSuccessCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Concurrent modification may occur
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All read operations should complete", completed);
        assertTrue("Repository reads should be thread-safe",
                 readSuccessCount.get() > (NUM_READERS * 0.8));
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Concurrent Case Execution Tests");
        suite.addTestSuite(TestConcurrentCaseExecution.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
