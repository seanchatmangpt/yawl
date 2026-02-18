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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.stateless.monitor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.exceptions.YStateException;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Performance tests for import/export operations including benchmarks,
 * memory leak detection, and large case handling.
 *
 * <p>Chicago TDD: All tests use real YStatelessEngine instances,
 * real case operations, and real performance measurements. No mocks.</p>
 *
 * <p>These tests are tagged as "performance" and may take longer to execute
 * than standard unit tests.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("Import/Export Performance Tests")
@Tag("performance")
class ImportExportPerformanceTest {

    private static final String MINIMAL_SPEC_RESOURCE = "resources/MinimalSpec.xml";
    private static final long EVENT_TIMEOUT_SEC = 30L;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 10;

    private YStatelessEngine engine;
    private YSpecification spec;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    @BeforeEach
    void setUp() throws Exception {
        engine = new YStatelessEngine();
        engine.setCaseMonitoringEnabled(true);
        spec = loadMinimalSpec();
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.setCaseMonitoringEnabled(false);
        }
        System.gc();
    }

    private YSpecification loadMinimalSpec() throws YSyntaxException {
        InputStream is = getClass().getResourceAsStream(MINIMAL_SPEC_RESOURCE);
        assertNotNull(is, "Missing resource: " + MINIMAL_SPEC_RESOURCE);
        String xml = StringUtil.streamToString(is);
        return engine.unmarshalSpecification(xml);
    }

    private YNetRunner launchCaseWithMonitoring(String caseId) throws Exception {
        AtomicReference<YNetRunner> runnerCapture = new AtomicReference<>();
        CountDownLatch startedLatch = new CountDownLatch(1);

        YCaseEventListener listener = event -> {
            if (event.getEventType() == YEventType.CASE_STARTED) {
                runnerCapture.set(event.getRunner());
                startedLatch.countDown();
            }
        };
        engine.addCaseEventListener(listener);
        engine.launchCase(spec, caseId);
        startedLatch.await(EVENT_TIMEOUT_SEC, TimeUnit.SECONDS);
        engine.removeCaseEventListener(listener);

        YNetRunner runner = runnerCapture.get();
        assertNotNull(runner, "Runner should be captured after case start");
        return runner;
    }

    private long getUsedMemoryBytes() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return heapUsage.getUsed();
    }

    // =========================================================================
    // Nested: Benchmark Tests
    // =========================================================================

    @Nested
    @DisplayName("Benchmark Tests")
    class BenchmarkTests {

        @Test
        @DisplayName("Benchmark: Export case performance")
        void benchmarkExportCasePerformance() throws Exception {
            String caseId = "bench-export";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                engine.marshalCase(runner);
            }

            // Benchmark
            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                String xml = engine.marshalCase(runner);
                long end = System.nanoTime();
                totalTime += (end - start);
                assertNotNull(xml, "Export should produce XML");
            }

            double avgTimeMs = (totalTime / 1_000_000.0) / BENCHMARK_ITERATIONS;
            System.out.printf("Export average time: %.2f ms%n", avgTimeMs);

            assertTrue(avgTimeMs < 1000, "Export should complete in under 1 second");
        }

        @Test
        @DisplayName("Benchmark: Import case performance")
        void benchmarkImportCasePerformance() throws Exception {
            String caseId = "bench-import";
            YNetRunner runner = launchCaseWithMonitoring(caseId);
            String caseXml = engine.marshalCase(runner);

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                engine.restoreCase(caseXml);
            }

            // Benchmark
            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();
                YNetRunner restored = engine.restoreCase(caseXml);
                long end = System.nanoTime();
                totalTime += (end - start);
                assertNotNull(restored, "Import should produce runner");
            }

            double avgTimeMs = (totalTime / 1_000_000.0) / BENCHMARK_ITERATIONS;
            System.out.printf("Import average time: %.2f ms%n", avgTimeMs);

            assertTrue(avgTimeMs < 2000, "Import should complete in under 2 seconds");
        }

        @Test
        @DisplayName("Benchmark: Round-trip performance")
        void benchmarkRoundTripPerformance() throws Exception {
            String caseId = "bench-roundtrip";

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                YNetRunner runner = launchCaseWithMonitoring(caseId + "-warmup-" + i);
                String xml = engine.marshalCase(runner);
                engine.restoreCase(xml);
            }

            // Benchmark
            long totalTime = 0;
            for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
                long start = System.nanoTime();

                YNetRunner runner = launchCaseWithMonitoring(caseId + "-" + i);
                String xml = engine.marshalCase(runner);
                YNetRunner restored = engine.restoreCase(xml);

                long end = System.nanoTime();
                totalTime += (end - start);
                assertNotNull(restored, "Round-trip should produce runner");
            }

            double avgTimeMs = (totalTime / 1_000_000.0) / BENCHMARK_ITERATIONS;
            System.out.printf("Round-trip average time: %.2f ms%n", avgTimeMs);

            assertTrue(avgTimeMs < 3000, "Round-trip should complete in under 3 seconds");
        }

        @Test
        @DisplayName("Benchmark: Export XML size")
        void benchmarkExportXmlSize() throws Exception {
            String caseId = "bench-size";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            String xml = engine.marshalCase(runner);
            int sizeBytes = xml.getBytes().length;

            System.out.printf("Export XML size: %d bytes%n", sizeBytes);

            assertTrue(sizeBytes > 0, "XML should have content");
            assertTrue(sizeBytes < 100_000, "Simple case XML should be under 100KB");
        }
    }

    // =========================================================================
    // Nested: Memory Leak Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Memory Leak Detection Tests")
    class MemoryLeakDetectionTests {

        @Test
        @DisplayName("No memory leak on repeated exports")
        void noMemoryLeakOnRepeatedExports() throws Exception {
            String caseId = "mem-export";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            System.gc();
            Thread.sleep(100);
            long initialMemory = getUsedMemoryBytes();

            for (int i = 0; i < 100; i++) {
                engine.marshalCase(runner);
            }

            System.gc();
            Thread.sleep(100);
            long finalMemory = getUsedMemoryBytes();

            long memoryIncrease = finalMemory - initialMemory;
            double increaseMB = memoryIncrease / (1024.0 * 1024.0);

            System.out.printf("Memory increase after 100 exports: %.2f MB%n", increaseMB);

            assertTrue(increaseMB < 10, "Memory increase should be less than 10MB");
        }

        @Test
        @DisplayName("No memory leak on repeated round-trips")
        void noMemoryLeakOnRepeatedRoundTrips() throws Exception {
            System.gc();
            Thread.sleep(100);
            long initialMemory = getUsedMemoryBytes();

            for (int i = 0; i < 50; i++) {
                String caseId = "mem-roundtrip-" + i;
                YNetRunner runner = launchCaseWithMonitoring(caseId);
                String xml = engine.marshalCase(runner);
                engine.restoreCase(xml);
            }

            System.gc();
            Thread.sleep(100);
            long finalMemory = getUsedMemoryBytes();

            long memoryIncrease = finalMemory - initialMemory;
            double increaseMB = memoryIncrease / (1024.0 * 1024.0);

            System.out.printf("Memory increase after 50 round-trips: %.2f MB%n", increaseMB);

            assertTrue(increaseMB < 50, "Memory increase should be less than 50MB");
        }

        @Test
        @DisplayName("GC reclaims memory after case cleanup")
        void gcReclaimsMemoryAfterCaseCleanup() throws Exception {
            List<String> caseXmlList = new ArrayList<>();

            for (int i = 0; i < 20; i++) {
                String caseId = "gc-test-" + i;
                YNetRunner runner = launchCaseWithMonitoring(caseId);
                caseXmlList.add(engine.marshalCase(runner));
            }

            System.gc();
            Thread.sleep(100);
            long memoryWithCases = getUsedMemoryBytes();

            caseXmlList.clear();

            System.gc();
            Thread.sleep(100);
            long memoryAfterCleanup = getUsedMemoryBytes();

            assertTrue(memoryAfterCleanup <= memoryWithCases,
                    "Memory after cleanup should not exceed memory with cases");
        }
    }

    // =========================================================================
    // Nested: Large Case Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Large Case Handling Tests")
    class LargeCaseHandlingTests {

        @Test
        @DisplayName("Export handles multiple concurrent cases")
        void exportHandlesMultipleConcurrentCases() throws Exception {
            int numCases = 10;
            List<YNetRunner> runners = new ArrayList<>();

            for (int i = 0; i < numCases; i++) {
                String caseId = "large-export-" + i;
                runners.add(launchCaseWithMonitoring(caseId));
            }

            long start = System.nanoTime();
            List<String> xmlList = new ArrayList<>();
            for (YNetRunner runner : runners) {
                xmlList.add(engine.marshalCase(runner));
            }
            long end = System.nanoTime();

            double totalTimeMs = (end - start) / 1_000_000.0;
            System.out.printf("Export %d cases time: %.2f ms%n", numCases, totalTimeMs);

            assertEquals(numCases, xmlList.size(), "Should export all cases");
            for (String xml : xmlList) {
                assertNotNull(xml, "Each export should produce XML");
                assertTrue(xml.length() > 0, "XML should have content");
            }
        }

        @Test
        @DisplayName("Import handles multiple cases sequentially")
        void importHandlesMultipleCasesSequentially() throws Exception {
            int numCases = 10;
            List<String> xmlList = new ArrayList<>();

            for (int i = 0; i < numCases; i++) {
                String caseId = "large-import-" + i;
                YNetRunner runner = launchCaseWithMonitoring(caseId);
                xmlList.add(engine.marshalCase(runner));
            }

            long start = System.nanoTime();
            List<YNetRunner> restoredRunners = new ArrayList<>();
            for (String xml : xmlList) {
                restoredRunners.add(engine.restoreCase(xml));
            }
            long end = System.nanoTime();

            double totalTimeMs = (end - start) / 1_000_000.0;
            System.out.printf("Import %d cases time: %.2f ms%n", numCases, totalTimeMs);

            assertEquals(numCases, restoredRunners.size(), "Should import all cases");
            for (YNetRunner runner : restoredRunners) {
                assertNotNull(runner, "Each import should produce runner");
            }
        }

        @Test
        @DisplayName("Export performance degrades linearly")
        void exportPerformanceDegradesLinearly() throws Exception {
            int[] caseCounts = {5, 10, 20};
            double[] timesPerCase = new double[caseCounts.length];

            for (int c = 0; c < caseCounts.length; c++) {
                int count = caseCounts[c];
                List<YNetRunner> runners = new ArrayList<>();

                for (int i = 0; i < count; i++) {
                    runners.add(launchCaseWithMonitoring("linear-" + c + "-" + i));
                }

                long start = System.nanoTime();
                for (YNetRunner runner : runners) {
                    engine.marshalCase(runner);
                }
                long end = System.nanoTime();

                timesPerCase[c] = (end - start) / 1_000_000.0 / count;
                System.out.printf("%d cases: %.2f ms/case%n", count, timesPerCase[c]);
            }

            double ratio = timesPerCase[caseCounts.length - 1] / timesPerCase[0];
            System.out.printf("Performance ratio (20/5): %.2f%n", ratio);

            assertTrue(ratio < 5.0, "Performance should degrade linearly, not exponentially");
        }
    }

    // =========================================================================
    // Nested: Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("Stress Tests")
    class StressTests {

        @Test
        @DisplayName("Stress: Rapid export cycles")
        void stressRapidExportCycles() throws Exception {
            String caseId = "stress-rapid";
            YNetRunner runner = launchCaseWithMonitoring(caseId);

            int iterations = 1000;
            long errorCount = 0;

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                try {
                    String xml = engine.marshalCase(runner);
                    if (xml == null || xml.isEmpty()) {
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                }
            }
            long end = System.nanoTime();

            double totalTimeMs = (end - start) / 1_000_000.0;
            double opsPerSecond = iterations / (totalTimeMs / 1000.0);

            System.out.printf("Stress test: %d iterations in %.2f ms (%.0f ops/sec)%n",
                    iterations, totalTimeMs, opsPerSecond);
            System.out.printf("Error count: %d%n", errorCount);

            assertEquals(0, errorCount, "No errors should occur during stress test");
            assertTrue(opsPerSecond > 100, "Should handle at least 100 exports per second");
        }

        @Test
        @DisplayName("Stress: Alternating import/export")
        void stressAlternatingImportExport() throws Exception {
            int iterations = 100;
            int errorCount = 0;

            YNetRunner currentRunner = launchCaseWithMonitoring("stress-alternating");

            long start = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                try {
                    String xml = engine.marshalCase(currentRunner);
                    currentRunner = engine.restoreCase(xml);
                    if (currentRunner == null) {
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                }
            }
            long end = System.nanoTime();

            double totalTimeMs = (end - start) / 1_000_000.0;
            double opsPerSecond = iterations / (totalTimeMs / 1000.0);

            System.out.printf("Alternating stress: %d iterations in %.2f ms (%.0f ops/sec)%n",
                    iterations, totalTimeMs, opsPerSecond);
            System.out.printf("Error count: %d%n", errorCount);

            assertEquals(0, errorCount, "No errors should occur during stress test");
            assertNotNull(currentRunner, "Final runner should not be null");
        }
    }
}
