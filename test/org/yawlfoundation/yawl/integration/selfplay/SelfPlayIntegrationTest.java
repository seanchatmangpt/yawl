/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Self-Play Test Orchestrator.
 *
 * These tests validate the integration with the actual YAWL engine
 * and verify the complete workflow from XML generation to execution.
 *
 * NOTE: Tests that require a running YAWL engine are skipped if the
 * engine is not available.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SelfPlayIntegrationTest {

    private SelfPlayOrchestrator orchestrator;
    private SelfPlayConfig config;
    private Path testOutputDir;
    private InterfaceA_EnvironmentBasedClient specClient;
    private InterfaceB_EnvironmentBasedClient engineClient;
    private String sessionHandle;
    private static boolean engineAvailable = false;

    @BeforeAll
    static void checkEngineAvailability() {
        // Check if engine is available
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            engineUrl = "http://localhost:8080/yawl";
        }

        try {
            InterfaceA_EnvironmentBasedClient testClient = new InterfaceA_EnvironmentBasedClient(engineUrl + "/ia");
            String handle = testClient.connect("admin", "admin");
            if (handle != null && !handle.isEmpty()) {
                engineAvailable = true;
                testClient.disconnect(handle);
            }
        } catch (Exception e) {
            engineAvailable = false;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Create test output directory
        testOutputDir = Paths.get("integration-test-results");
        Files.createDirectories(testOutputDir);

        // Initialize configuration with test settings
        config = new SelfPlayConfig();
        config.loadFromEnvironment();
        config.updateFromArgs(new String[]{
            "--engine-url", System.getenv().getOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl"),
            "--username", System.getenv().getOrDefault("YAWL_USERNAME", "admin"),
            "--password", System.getenv().getOrDefault("YAWL_PASSWORD", "admin"),
            "--iterations", "1",
            "--output-dir", testOutputDir.toString()
        });

        // Initialize orchestrator
        orchestrator = new SelfPlayOrchestrator();
        orchestrator.setEngineUrl(config.getEngineUrl());
        orchestrator.setUsername(config.getUsername());
        orchestrator.setPassword(config.getPassword());
        orchestrator.setIterations(1);
        orchestrator.setOutputDirectory(testOutputDir);

        // Initialize engine client if engine is available
        if (engineAvailable) {
            specClient = new InterfaceA_EnvironmentBasedClient(config.getEngineUrl() + "/ia");
            engineClient = new InterfaceB_EnvironmentBasedClient(config.getEngineUrl() + "/ib");
            sessionHandle = specClient.connect(config.getUsername(), config.getPassword());
        }
    }

    @AfterEach
    void tearDown() {
        try {
            if (specClient != null && sessionHandle != null) {
                specClient.disconnect(sessionHandle);
            }
            if (engineClient != null && sessionHandle != null) {
                engineClient.disconnect(sessionHandle);
            }
            if (orchestrator != null) {
                orchestrator.shutdown();
            }
            // Clean up test output
            if (Files.exists(testOutputDir)) {
                Files.walk(testOutputDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test Engine Connectivity")
    void testEngineConnectivity() throws Exception {
        Assumptions.assumeTrue(engineAvailable, "YAWL engine not available - skipping engine connectivity test");

        // Verify we can connect and get specifications
        assertNotNull(sessionHandle, "Should establish connection");

        // Get specifications
        var specs = engineClient.getSpecificationList(sessionHandle);
        assertNotNull(specs, "Should get specifications");
    }

    @Test
    @Order(2)
    @DisplayName("Test XML Generation and Validation")
    void testXmlGenerationAndValidation() {
        // Generate workflow XML
        String xml = orchestrator.generateSequenceWorkflow(1);
        assertNotNull(xml, "Should generate XML");
        assertTrue(xml.contains("specificationSet"), "Should be valid XML");

        // Validate XML
        boolean isValid = orchestrator.validateSpecification(xml);
        assertTrue(isValid, "Should validate successfully");
    }

    @Test
    @Order(3)
    @DisplayName("Test Configuration Management")
    void testConfigurationManagement() {
        // Test config values - these match what was set in setUp
        assertNotNull(config.getEngineUrl());
        assertNotNull(config.getUsername());
        assertNotNull(config.getPassword());
        assertEquals(1, config.getIterations());
    }

    @Test
    @Order(4)
    @DisplayName("Test Z.ai Integration")
    void testZaiIntegration() {
        // Test Z.ai generator
        ZaiWorkflowGenerator generator = new ZaiWorkflowGenerator(config);
        // Z.ai may or may not be available depending on environment
        // Just verify the generator works
        assertNotNull(generator.getStatus());

        // Generate fallback workflow
        String xml = generator.generateWorkflow("sequence", Map.of("task_count", "2"));
        assertNotNull(xml, "Should generate XML");
        assertTrue(xml.contains("specificationSet"), "Should be valid XML");
    }

    @Test
    @Order(5)
    @DisplayName("Test Single Iteration Workflow")
    void testSingleIterationWorkflow() throws Exception {
        Assumptions.assumeTrue(engineAvailable, "YAWL engine not available - skipping workflow execution test");

        // Generate and validate XML
        String xml = orchestrator.generateSequenceWorkflow(1);
        assertTrue(orchestrator.validateSpecification(xml), "Should validate XML");

        // Upload specification
        String specId = orchestrator.uploadSpecification(xml);
        assertNotNull(specId, "Should upload specification");

        // Execute workflow
        String caseId = orchestrator.executeWorkflow(specId);
        assertNotNull(caseId, "Should execute case");

        // Verify execution
        boolean verified = orchestrator.verifyExecution(caseId, specId);
        assertTrue(verified, "Should verify execution successfully");
    }

    @Test
    @Order(6)
    @DisplayName("Test Metrics Collection")
    void testMetricsCollection() throws Exception {
        Assumptions.assumeTrue(engineAvailable, "YAWL engine not available - skipping metrics test");

        // Run a complete iteration
        orchestrator.runSelfPlayTest();

        // Verify metrics were collected
        Map<String, Long> metrics = orchestrator.getMetrics();
        assertTrue(metrics.size() > 0, "Should collect metrics");

        // Verify specific metrics exist
        assertTrue(metrics.containsKey("iteration.0.generation.duration"),
            "Should have generation duration");
        assertTrue(metrics.containsKey("iteration.0.validation.duration"),
            "Should have validation duration");
    }

    @Test
    @Order(7)
    @DisplayName("Test Report Generation")
    void testReportGeneration() throws IOException {
        // Run test and generate report
        orchestrator.runSelfPlayTest();
        orchestrator.generateReport();

        // Verify report file exists
        Path reportFile = testOutputDir.resolve("self-play-report.json");
        assertTrue(Files.exists(reportFile), "Should generate report file");

        // Verify report content
        String reportContent = Files.readString(reportFile);
        assertTrue(reportContent.contains("\"total_iterations\""),
            "Should contain iteration count");
        assertTrue(reportContent.contains("\"success_rate\""),
            "Should contain success rate");
        assertTrue(reportContent.contains("\"metrics\""),
            "Should contain metrics section");
        assertTrue(reportContent.contains("\"iterations\""),
            "Should contain iterations section");
    }

    @Test
    @Order(8)
    @DisplayName("Test Error Handling")
    void testErrorHandling() {
        // Test that invalid engine URL is handled appropriately
        assertDoesNotThrow(() -> {
            SelfPlayOrchestrator testOrchestrator = new SelfPlayOrchestrator();
            testOrchestrator.setEngineUrl("invalid-url");
            testOrchestrator.setOutputDirectory(testOutputDir);
            // Validation should work even with invalid URL
            String xml = testOrchestrator.generateSequenceWorkflow(1);
            assertTrue(testOrchestrator.validateSpecification(xml));
            testOrchestrator.shutdown();
        });
    }

    @Test
    @Order(9)
    @DisplayName("Test Performance Metrics")
    void testPerformanceMetrics() {
        // Measure test execution time
        long startTime = System.currentTimeMillis();

        // Run a quick test - just generation and validation
        String xml = orchestrator.generateSequenceWorkflow(1);
        assertTrue(orchestrator.validateSpecification(xml));

        long duration = System.currentTimeMillis() - startTime;
        assertTrue(duration < 10000, "Should complete quickly (under 10s)");

        // Record performance metric
        orchestrator.getMetrics().put("performance.test.duration", duration);
        assertEquals(duration, orchestrator.getMetrics().get("performance.test.duration"));
    }

    @Test
    @Order(10)
    @DisplayName("Test Concurrent Operations")
    void testConcurrentOperations() throws InterruptedException {
        // Test concurrent generation
        var futures = new java.util.ArrayList<CompletableFuture<String>>();

        for (int i = 0; i < 3; i++) {
            final int iteration = i;
            futures.add(CompletableFuture.supplyAsync(() ->
                orchestrator.generateSequenceWorkflow(iteration),
                orchestrator.getVirtualThreadExecutor()));
        }

        // Wait for all generations to complete
        var results = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList())
            .join();

        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(xml -> xml.contains("specificationSet")));
    }
}
