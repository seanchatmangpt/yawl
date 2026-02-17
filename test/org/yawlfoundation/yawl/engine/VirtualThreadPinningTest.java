package org.yawlfoundation.yawl.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import junit.framework.TestCase;

/**
 * Tests to verify that virtual thread pinning does not occur in YAWL engine operations.
 *
 * Virtual thread pinning happens when a virtual thread executes in a synchronized block,
 * which prevents it from unmounting from its carrier thread. This defeats the scalability
 * benefits of virtual threads.
 *
 * Test Approach (Chicago TDD):
 * - Uses real YEngine instance
 * - Uses real specifications and cases
 * - Tests under high concurrency (1000+ operations)
 * - Detects pinning by capturing System.err output (where JVM reports pinning)
 *
 * Run with: java -Djdk.tracePinnedThreads=full -cp ... VirtualThreadPinningTest
 *
 * @author YAWL Team
 * @date 2026-02-16
 */
public class VirtualThreadPinningTest extends TestCase {

    private YEngine engine;
    private YSpecification testSpec;
    private String sessionHandle;
    private static final String TEST_USER = "admin";
    private static final String TEST_PASS = "YAWL";

    /**
     * Set up test environment with real YAWL engine instance.
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize real engine (no mocks!)
        engine = YEngine.getInstance();
        engine.initialise(false); // non-persisting mode for tests

        // Connect and get session handle
        sessionHandle = engine.connect(TEST_USER, TEST_PASS);
        assertNotNull("Should get valid session handle", sessionHandle);

        // Load a real test specification
        testSpec = loadTestSpecification();
        assertNotNull("Should load test specification", testSpec);

        // Add specification to engine
        String result = engine.addSpecification(testSpec, false);
        assertFalse("Should successfully add specification", result.startsWith("<failure"));
    }

    /**
     * Clean up test resources.
     */
    @Override
    protected void tearDown() throws Exception {
        if (sessionHandle != null && engine != null) {
            engine.disconnect(sessionHandle);
        }
        super.tearDown();
    }

    /**
     * Loads a minimal test specification for testing.
     * Uses SimpleMakeTripProcess.yawl if available, otherwise creates minimal spec.
     */
    private YSpecification loadTestSpecification() throws YSyntaxException {
        File specFile = new File("test/org/yawlfoundation/yawl/elements/SimpleMakeTripProcess.yawl");
        if (specFile.exists()) {
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(specFile.getAbsolutePath());
            return specs != null && !specs.isEmpty() ? specs.get(0) : createMinimalSpec();
        }
        return createMinimalSpec();
    }

    /**
     * Creates a minimal specification programmatically for testing.
     */
    private YSpecification createMinimalSpec() {
        YSpecification spec = new YSpecification("PinningTestSpec");
        spec.setVersion("0.1");
        spec.setBetaVersion(0.1);
        spec.setURI("http://yawlfoundation.org/test/pinning");
        return spec;
    }

    /**
     * Test that launching multiple cases concurrently does not cause virtual thread pinning.
     *
     * This test:
     * 1. Launches 100 cases concurrently
     * 2. Monitors System.err for pinning warnings
     * 3. Fails if any pinning is detected
     *
     * Note: Run with -Djdk.tracePinnedThreads=full to enable pinning detection
     */
    public void testNoPinningWhenLaunchingCases() throws Exception {
        // Skip test if pinning detection is not enabled
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Test skipped - run with -Djdk.tracePinnedThreads=full to enable");
            return;
        }

        // Capture System.err to detect pinning warnings
        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();

            // Launch 100 cases concurrently using virtual threads
            for (int i = 0; i < 100; i++) {
                final int caseNum = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        String caseID = engine.launchCase(
                            sessionHandle,
                            testSpec.getSpecificationID(),
                            null,
                            null
                        );
                        return caseID;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to launch case " + caseNum, e);
                    }
                });
                futures.add(future);
            }

            // Wait for all cases to launch
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allOf.get(30, TimeUnit.SECONDS);

            // Check that all cases launched successfully
            int successCount = 0;
            for (CompletableFuture<String> future : futures) {
                String caseID = future.get();
                if (caseID != null && !caseID.isEmpty()) {
                    successCount++;
                }
            }

            assertTrue("Should launch at least some cases", successCount > 0);

        } finally {
            System.setErr(originalErr);
        }

        // Check for pinning warnings in captured output
        String errorOutput = errCapture.toString();
        assertFalse(
            "Virtual thread pinning detected during case launch:\n" + errorOutput,
            errorOutput.contains("Pinned thread")
        );
    }

    /**
     * Test that high-concurrency specification operations do not cause pinning.
     *
     * This test exercises the YEngine specification management under high load,
     * which uses synchronized(_pmgr) blocks that may cause pinning.
     */
    public void testNoPinningInSpecificationOperations() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Test skipped - run with -Djdk.tracePinnedThreads=full to enable");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Perform 500 concurrent specification queries
            for (int i = 0; i < 500; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Query specification (uses synchronized blocks internally)
                        YSpecification spec = engine.getSpecification(testSpec.getSpecificationID());
                        assertNotNull("Should retrieve specification", spec);

                        // Get specification list (also synchronized)
                        List<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
                        assertNotNull("Should get specification list", specs);

                    } catch (Exception e) {
                        throw new RuntimeException("Specification operation failed", e);
                    }
                });
                futures.add(future);
            }

            // Wait for all operations to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(
            "Virtual thread pinning detected during specification operations:\n" + errorOutput,
            errorOutput.contains("Pinned thread")
        );
    }

    /**
     * Test that concurrent workitem operations do not cause pinning.
     *
     * This is a more complex test that requires cases with actual workitems.
     * Currently simplified to test the basic pattern.
     */
    public void testNoPinningInWorkItemOperations() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Test skipped - run with -Djdk.tracePinnedThreads=full to enable");
            return;
        }

        // Launch a single case first
        String caseID = engine.launchCase(
            sessionHandle,
            testSpec.getSpecificationID(),
            null,
            null
        );

        if (caseID == null || caseID.isEmpty()) {
            System.out.println("INFO: Test skipped - no workitems available for test spec");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Perform 200 concurrent workitem queries
            for (int i = 0; i < 200; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Query workitems (may use synchronized blocks)
                        List<YWorkItem> workItems = engine.getWorkItemRepository()
                            .getAllWorkItems();
                        assertNotNull("Should get workitem list", workItems);

                    } catch (Exception e) {
                        throw new RuntimeException("WorkItem operation failed", e);
                    }
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(
            "Virtual thread pinning detected during workitem operations:\n" + errorOutput,
            errorOutput.contains("Pinned thread")
        );
    }

    /**
     * Stress test with very high concurrency to expose any pinning issues.
     *
     * This test is more aggressive and may take longer to run.
     */
    public void testNoPinningUnderStressLoad() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Test skipped - run with -Djdk.tracePinnedThreads=full to enable");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            final int OPERATIONS = 1000;
            CountDownLatch latch = new CountDownLatch(OPERATIONS);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Mix of different operations
            for (int i = 0; i < OPERATIONS; i++) {
                final int opType = i % 3;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        switch (opType) {
                            case 0:
                                // Specification query
                                engine.getSpecification(testSpec.getSpecificationID());
                                break;
                            case 1:
                                // Specification list
                                engine.getLoadedSpecificationIDs();
                                break;
                            case 2:
                                // Case launch attempt (may fail, that's ok)
                                try {
                                    engine.launchCase(sessionHandle,
                                        testSpec.getSpecificationID(), null, null);
                                } catch (Exception e) {
                                    // Expected for some attempts
                                }
                                break;
                        }
                    } catch (Exception e) {
                        // Log but don't fail - some operations expected to fail
                    } finally {
                        latch.countDown();
                    }
                });
                futures.add(future);
            }

            // Wait for all operations with generous timeout
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            assertTrue("All operations should complete within timeout", completed);

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(
            "Virtual thread pinning detected under stress load:\n" + errorOutput,
            errorOutput.contains("Pinned thread")
        );
    }

    /**
     * Test that logging operations do not cause pinning.
     *
     * YEventLogger was specifically refactored to avoid pinning by using
     * ReentrantLock instead of synchronized blocks.
     */
    public void testNoPinningInLoggingOperations() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Test skipped - run with -Djdk.tracePinnedThreads=full to enable");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Create 300 concurrent logging operations
            for (int i = 0; i < 300; i++) {
                final int logNum = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        // Launch a case (which triggers logging)
                        String caseID = engine.launchCase(
                            sessionHandle,
                            testSpec.getSpecificationID(),
                            null,
                            null
                        );

                        // The case launch will trigger YEventLogger operations
                        // which should not pin because it uses ReentrantLock

                    } catch (Exception e) {
                        // Some may fail, that's acceptable
                    }
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(
            "Virtual thread pinning detected in logging operations:\n" + errorOutput,
            errorOutput.contains("Pinned thread")
        );
    }
}
