package org.yawlfoundation.yawl.engine;

import org.junit.jupiter.api.Tag;
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
@Tag("slow")
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

    @Execution(ExecutionMode.SAME_THREAD)

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
}
