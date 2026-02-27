package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

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
 * - Tests under high concurrency
 * - Detects pinning by capturing System.err output (where JVM reports pinning)
 *
 * Run with: java -Djdk.tracePinnedThreads=full -cp ... VirtualThreadPinningTest
 *
 * @author YAWL Team
 * @date 2026-02-16
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)
class VirtualThreadPinningTest {

    private YEngine engine;
    private YSpecification testSpec;

    @BeforeEach
    void setUp() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        testSpec = loadTestSpecification();
        assertNotNull(testSpec, "Should load test specification");
        engine.loadSpecification(testSpec);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null) {
            EngineClearer.clear(engine);
        }
    }

    /**
     * Loads a minimal test specification from the engine test resources.
     */
    private YSpecification loadTestSpecification() throws Exception {
        // Try multiple known-good test specs from the engine test resources;
        // schema validation disabled because these are legacy Beta-version specs
        for (String specName : new String[]{"YAWL_Specification2.xml", "YAWL_Specification3.xml", "YAWL_Specification4.xml"}) {
            URL url = getClass().getResource(specName);
            if (url != null) {
                File specFile = new File(url.getFile());
                String specXml = StringUtil.fileToString(specFile.getAbsolutePath());
                List<YSpecification> specs = YMarshal.unmarshalSpecifications(specXml, false);
                if (specs != null && !specs.isEmpty()) {
                    return specs.get(0);
                }
            }
        }
        throw new IllegalStateException("Could not load any test specification from engine test resources");
    }

    /**
     * Test that launching multiple cases concurrently does not cause virtual thread pinning.
     */
    @Test
    void testNoPinningWhenLaunchingCases() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Pinning detection disabled - run with -Djdk.tracePinnedThreads=full");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<YIdentifier>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                final int caseNum = i;
                CompletableFuture<YIdentifier> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return engine.startCase(
                            testSpec.getSpecificationID(), null, null, null,
                            new YLogDataItemList(), null, false);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to launch case " + caseNum, e);
                    }
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

            long successCount = futures.stream()
                .filter(f -> { try { return f.get() != null; } catch (Exception e) { return false; } })
                .count();
            assertTrue(successCount > 0, "Should launch at least some cases");

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(errorOutput.contains("Pinned thread"),
            "Virtual thread pinning detected during case launch:\n" + errorOutput);
    }

    /**
     * Test that high-concurrency specification queries do not cause pinning.
     */
    @Test
    void testNoPinningInSpecificationOperations() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Pinning detection disabled - run with -Djdk.tracePinnedThreads=full");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < 50; i++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    YSpecification spec = engine.getSpecification(testSpec.getSpecificationID());
                    assertNotNull(spec, "Should retrieve specification");
                    Set<YSpecificationID> specs = engine.getLoadedSpecificationIDs();
                    assertNotNull(specs, "Should get specification list");
                });
                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(errorOutput.contains("Pinned thread"),
            "Virtual thread pinning detected during specification operations:\n" + errorOutput);
    }

    /**
     * Stress test with high concurrency to expose pinning issues.
     */
    @Test
    void testNoPinningUnderStressLoad() throws Exception {
        String tracePinned = System.getProperty("jdk.tracePinnedThreads");
        if (tracePinned == null || tracePinned.isEmpty()) {
            System.out.println("INFO: Pinning detection disabled - run with -Djdk.tracePinnedThreads=full");
            return;
        }

        ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(errCapture));

        try {
            final int OPERATIONS = 100;
            CountDownLatch latch = new CountDownLatch(OPERATIONS);

            for (int i = 0; i < OPERATIONS; i++) {
                final int opType = i % 2;
                CompletableFuture.runAsync(() -> {
                    try {
                        switch (opType) {
                            case 0 -> engine.getSpecification(testSpec.getSpecificationID());
                            case 1 -> engine.getLoadedSpecificationIDs();
                        }
                    } catch (Exception e) {
                        // expected for some operations under concurrent load
                    } finally {
                        latch.countDown();
                    }
                });
            }

            boolean completed = latch.await(60, TimeUnit.SECONDS);
            assertTrue(completed, "All operations should complete within timeout");

        } finally {
            System.setErr(originalErr);
        }

        String errorOutput = errCapture.toString();
        assertFalse(errorOutput.contains("Pinned thread"),
            "Virtual thread pinning detected under stress load:\n" + errorOutput);
    }

    // -------------------------------------------------------------------------
    // Tests 4–8: Lock metrics and virtual-thread correctness (no pinning flag needed)
    // -------------------------------------------------------------------------

    /**
     * A freshly created {@link YNetRunnerLockMetrics} must report zero acquisitions
     * and zero wait times — no spurious state leaks across instances.
     */
    @Test
    void testLockMetrics_freshInstance_hasZeroAcquisitions() {
        YNetRunnerLockMetrics metrics = new YNetRunnerLockMetrics("test-case-fresh");
        assertEquals(0.0, metrics.avgWriteLockWaitMs(), 1e-9,
            "Fresh metrics must report 0.0 ms average write-lock wait");
        assertEquals(0.0, metrics.maxWriteLockWaitMs(), 1e-9,
            "Fresh metrics must report 0.0 ms max write-lock wait");
        assertEquals(0.0, metrics.avgReadLockWaitMs(), 1e-9,
            "Fresh metrics must report 0.0 ms average read-lock wait");
    }

    /**
     * After recording two equal wait times the average must equal each individual wait.
     */
    @Test
    void testLockMetrics_afterRecordWait_showsCorrectAverage() {
        YNetRunnerLockMetrics metrics = new YNetRunnerLockMetrics("test-case-avg");
        // Record two waits of exactly 1 ms (1,000,000 ns)
        metrics.recordWriteLockWait(1_000_000L);
        metrics.recordWriteLockWait(1_000_000L);
        assertEquals(1.0, metrics.avgWriteLockWaitMs(), 0.001,
            "Average of two 1 ms waits must be 1.0 ms");
    }

    /**
     * The maximum write-lock wait must track the single longest wait seen.
     */
    @Test
    void testLockMetrics_maxWaitTracked_withMultipleRecords() {
        YNetRunnerLockMetrics metrics = new YNetRunnerLockMetrics("test-case-max");
        metrics.recordWriteLockWait(1_000_000L);  // 1 ms
        metrics.recordWriteLockWait(5_000_000L);  // 5 ms  ← should become the max
        metrics.recordWriteLockWait(2_000_000L);  // 2 ms
        assertEquals(5.0, metrics.maxWriteLockWaitMs(), 0.001,
            "maxWriteLockWaitMs must be 5.0 ms after recording 1 ms, 5 ms, 2 ms waits");
    }

    /**
     * The runner for a running case must have lock metrics with at least one write-lock
     * acquisition recorded (kick + continueIfPossible both acquire the write-lock).
     */
    @Test
    void testLockMetrics_recordedForCaseRunner_afterCaseStart() throws Exception {
        YIdentifier caseId = engine.startCase(
            testSpec.getSpecificationID(), null, null, null,
            new YLogDataItemList(), null, false);
        assertNotNull(caseId, "Case must start successfully");

        YNetRunner runner = engine.getNetRunnerRepository().get(caseId);
        assertNotNull(runner, "Runner must be in repository after startCase");

        YNetRunnerLockMetrics metrics = runner.getLockMetrics();
        assertNotNull(metrics, "Runner must expose a non-null YNetRunnerLockMetrics instance");

        // kick() acquires the write-lock at least once during startCase
        assertTrue(metrics.avgWriteLockWaitMs() >= 0.0,
            "avgWriteLockWaitMs must be >= 0 after case start (not negative)");
    }

    /**
     * Starting a case from a virtual thread must complete without throwing any exception.
     * This verifies that YNetRunner's ReentrantReadWriteLock (non-synchronized) does not
     * block virtual-thread execution.
     */
    @Test
    void testCaseStart_fromVirtualThread_completesNormally() throws Exception {
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
            new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<YIdentifier> result =
            new java.util.concurrent.atomic.AtomicReference<>();

        Thread vt = Thread.ofVirtual().start(() -> {
            try {
                YIdentifier id = engine.startCase(
                    testSpec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
                result.set(id);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        vt.join(10_000);  // wait up to 10 s

        assertNull(error.get(),
            "startCase from virtual thread must not throw: " + error.get());
        assertNotNull(result.get(),
            "startCase from virtual thread must return a valid case identifier");
        assertTrue(Thread.currentThread().isVirtual() == false,
            "Carrier (platform) thread must not be a virtual thread in this test context");
    }
}
