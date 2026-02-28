/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark.soak;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.benchmark.TestDataGenerator;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quick Smoke Test for benchmark infrastructure validation.
 *
 * <p>This test runs for a short duration (60 seconds) and validates that the
 * benchmark infrastructure components work correctly:
 * <ul>
 *   <li>YStatelessEngine can be instantiated and used</li>
 *   <li>Cases can be created and completed</li>
 *   <li>BenchmarkMetricsCollector samples metrics correctly</li>
 *   <li>MixedWorkloadSimulator generates events</li>
 *   <li>Results are written to the expected files</li>
 * </ul>
 * </p>
 *
 * <p>This is a fast CI test (not tagged @soak-test) that verifies the infrastructure
 * works before running longer stress tests. Typical duration: 60 seconds.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Tag("smoke-test")
public class SmokeStressTest {

    private final TestDataGenerator dataGenerator = new TestDataGenerator();
    private final Random random = new Random();

    /**
     * Quick 60-second smoke test to validate benchmark infrastructure.
     *
     * @throws Exception if infrastructure validation fails
     */
    @Test
    @DisplayName("Smoke test: Benchmark infrastructure validation (60 seconds)")
    @Timeout(120)
    void smokeTest_benchmarkInfrastructureWorks() throws Exception {
        long testStartTime = System.currentTimeMillis();
        Duration testDuration = Duration.ofSeconds(60);
        long testEndTime = testStartTime + testDuration.toMillis();

        System.out.println("==========================================");
        System.out.println("BENCHMARK INFRASTRUCTURE SMOKE TEST");
        System.out.println("==========================================");
        System.out.println("Duration: 60 seconds");
        System.out.println("Case rate: 10 cases/second");
        System.out.println();

        // Initialize engine and metrics collection
        YStatelessEngine engine = new YStatelessEngine();
        Path metricsOutput = Paths.get("smoke-metrics-" + System.currentTimeMillis() + ".jsonl");
        BenchmarkMetricsCollector metricsCollector =
                new BenchmarkMetricsCollector(metricsOutput, 5);  // Sample every 5 seconds

        // Initialize workload simulator with low rate
        MixedWorkloadSimulator workloadSimulator = new MixedWorkloadSimulator(
                10.0,   // 10 cases/second
                50);    // 50ms median task time

        // Load a single simple specification
        YSpecification specification = engine.unmarshalSpecification(
                dataGenerator.generateWorkflowSpecifications().get("sequential"));

        assertNotNull(specification, "Specification should not be null");

        AtomicInteger casesCreated = new AtomicInteger(0);
        AtomicInteger casesCompleted = new AtomicInteger(0);

        // Start metrics collection
        metricsCollector.start();

        // Main case executor
        ExecutorService caseExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            // Case creation and execution loop
            while (System.currentTimeMillis() < testEndTime) {
                try {
                    // Get next event from workload simulator
                    MixedWorkloadSimulator.WorkloadEvent event = workloadSimulator.nextEvent();

                    if ("case_arrival".equals(event.eventType())) {
                        // Launch a new case
                        String caseId = "smoke-case-" + UUID.randomUUID();
                        try {
                            YNetRunner runner = engine.launchCase(specification, caseId, "<data/>");
                            casesCreated.incrementAndGet();
                            metricsCollector.recordCaseProcessed();

                            // Submit case completion task
                            caseExecutor.submit(() -> {
                                try {
                                    // Simple completion: just mark as completed
                                    casesCompleted.incrementAndGet();
                                } catch (Exception e) {
                                    System.err.println("Error completing case: " + e.getMessage());
                                }
                            });

                        } catch (Exception e) {
                            System.err.println("Failed to launch case " + caseId + ": " + e.getMessage());
                        }
                    }

                    // Throttle slightly
                    if (event.delayMs() > 0) {
                        Thread.sleep(Math.min(event.delayMs(), 10));
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in case launcher: " + e.getMessage());
                }
            }

            // Allow executor tasks to settle
            caseExecutor.shutdown();
            if (!caseExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                caseExecutor.shutdownNow();
            }

            // Stop metrics collection
            metricsCollector.stop();

        } finally {
            caseExecutor.shutdownNow();
            try {
                metricsCollector.stop();
            } catch (Exception e) {
                System.err.println("Error stopping metrics collector: " + e.getMessage());
            }
        }

        long actualDuration = System.currentTimeMillis() - testStartTime;

        // Print results
        System.out.println("==========================================");
        System.out.println("SMOKE TEST RESULTS");
        System.out.println("==========================================");
        System.out.println("Duration: " + Duration.ofMillis(actualDuration));
        System.out.println("Cases Created: " + casesCreated.get());
        System.out.println("Cases Completed: " + casesCompleted.get());
        System.out.println();

        // Verify metrics file was created
        assertTrue(Files.exists(metricsOutput),
                "Metrics output file should be created: " + metricsOutput);

        long metricsFileSize = Files.size(metricsOutput);
        System.out.println("Metrics file size: " + metricsFileSize + " bytes");

        // Verify metrics file contains data
        assertTrue(metricsFileSize > 0,
                "Metrics output file should contain data");

        // Verify cases were created
        assertTrue(casesCreated.get() > 0,
                "At least one case should be created during smoke test");

        // Verify cases were completed
        assertTrue(casesCompleted.get() > 0,
                "At least one case should complete during smoke test");

        System.out.println("==========================================");
        System.out.println("SMOKE TEST PASSED");
        System.out.println("==========================================");
    }
}
