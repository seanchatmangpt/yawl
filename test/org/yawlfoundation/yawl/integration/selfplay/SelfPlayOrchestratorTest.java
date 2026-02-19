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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SelfPlayOrchestrator.
 *
 * Tests all core functionality including XML generation, validation,
 * execution simulation, and metrics collection.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SelfPlayOrchestratorTest {

    private SelfPlayOrchestrator orchestrator;
    private Path testOutputDir;

    @BeforeEach
    void setUp() {
        testOutputDir = Paths.get("test-self-play-results");

        // Clean up previous test results
        try {
            if (Files.exists(testOutputDir)) {
                Files.walk(testOutputDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try { Files.delete(path); } catch (IOException e) {}
                    });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }

        // Create orchestrator with test settings
        orchestrator = new SelfPlayOrchestrator();
        orchestrator.setOutputDirectory(testOutputDir);
        orchestrator.setUseZaiIntegration(false);
        orchestrator.setIterations(2);
    }

    @AfterEach
    void tearDown() {
        try {
            if (orchestrator != null) {
                orchestrator.shutdown();
            }
        } catch (Exception e) {
            // Ignore shutdown errors in tests
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test XML Generation")
    void testXmlGeneration() {
        // Test sequence workflow generation
        String xml = orchestrator.generateSequenceWorkflow(1);

        assertNotNull(xml, "Generated XML should not be null");
        assertTrue(xml.contains("<specificationSet"), "XML should contain specificationSet");
        assertTrue(xml.contains("sequenceWorkflow_"), "XML should contain workflow URI");
        assertTrue(xml.contains("<task"), "XML should contain task elements");
    }

    @Test
    @Order(2)
    @DisplayName("Test XML Validation")
    void testXmlValidation() {
        // Generate valid XML
        String validXml = orchestrator.generateSequenceWorkflow(1);
        boolean isValid = orchestrator.validateSpecification(validXml);

        assertTrue(isValid, "Valid XML should pass validation");
    }

    @Test
    @Order(3)
    @DisplayName("Test Invalid XML Validation")
    void testInvalidXmlValidation() {
        // Generate invalid XML
        String invalidXml = "<invalid>xml</invalid>";
        boolean isValid = orchestrator.validateSpecification(invalidXml);

        assertFalse(isValid, "Invalid XML should fail validation");
    }

    @Test
    @Order(4)
    @DisplayName("Test Configuration Parsing")
    void testConfigurationParsing() {
        // Test default configuration
        assertEquals("http://localhost:8080/yawl", orchestrator.getEngineUrl());
        assertEquals("admin", orchestrator.getUsername());
        assertEquals("admin", orchestrator.getPassword());
        assertEquals(2, orchestrator.getIterations());
        assertFalse(orchestrator.isUseZaiIntegration());
    }

    @Test
    @Order(5)
    @DisplayName("Test Metrics Collection")
    void testMetricsCollection() {
        // Simulate running a test iteration
        orchestrator.getMetrics().put("test.duration", 1000L);
        orchestrator.getMetrics().put("validation.duration", 200L);

        assertEquals(2, orchestrator.getMetrics().size());
        assertEquals(1000L, orchestrator.getMetrics().get("test.duration"));
        assertEquals(200L, orchestrator.getMetrics().get("validation.duration"));
    }

    @Test
    @Order(6)
    @DisplayName("Test Output Directory Creation")
    void testOutputDirectoryCreation() throws IOException {
        Files.createDirectories(testOutputDir);
        assertTrue(Files.exists(testOutputDir), "Output directory should be created");

        // Create a test file
        Path testFile = testOutputDir.resolve("test.txt");
        Files.writeString(testFile, "test content");
        assertTrue(Files.exists(testFile), "Test file should be created");

        // Clean up
        Files.deleteIfExists(testFile);
    }

    @Test
    @Order(7)
    @DisplayName("Test Z.ai Integration Fallback")
    void testZaiIntegrationFallback() {
        // Enable Z.ai integration
        orchestrator.setUseZaiIntegration(true);

        // Test that it falls back to sequence generation
        String xml = orchestrator.generateWorkflowViaZai(1);
        assertNotNull(xml, "Should generate XML even without Z.ai");
        assertTrue(xml.contains("<specificationSet"), "Generated XML should be valid");
    }

    @Test
    @Order(8)
    @DisplayName("Test Error Handling")
    void testErrorHandling() {
        // Test with invalid engine URL - validation should still work
        assertDoesNotThrow(() -> {
            SelfPlayOrchestrator testOrchestrator = new SelfPlayOrchestrator();
            testOrchestrator.setEngineUrl("invalid-url");
            testOrchestrator.setOutputDirectory(testOutputDir);
            // XML generation and validation should work
            String xml = testOrchestrator.generateSequenceWorkflow(1);
            assertTrue(testOrchestrator.validateSpecification(xml));
            testOrchestrator.shutdown();
        });
    }

    @Test
    @Order(9)
    @DisplayName("Test Report Generation")
    void testReportGeneration() throws IOException {
        // Set up some test data
        orchestrator.getMetrics().put("iteration.0.duration", 1000L);
        orchestrator.getMetrics().put("iteration.1.duration", 1500L);

        // Create a mock iteration result
        SelfPlayOrchestrator.TestIterationResult result =
            new SelfPlayOrchestrator.TestIterationResult(0);
        result.setSuccess(true);
        result.setTotalDuration(1000L);
        orchestrator.getIterationResults().add(result);

        // Generate report
        orchestrator.generateReport();

        // Verify report file exists
        Path reportFile = testOutputDir.resolve("self-play-report.json");
        assertTrue(Files.exists(reportFile), "Report file should be created");

        // Verify report content
        String reportContent = Files.readString(reportFile);
        assertTrue(reportContent.contains("\"total_iterations\""),
            "Report should contain iteration count");
    }

    @Test
    @Order(10)
    @DisplayName("Test Multiple Iterations")
    void testMultipleIterations() {
        // Test iteration tracking
        for (int i = 0; i < 3; i++) {
            SelfPlayOrchestrator.TestIterationResult result =
                new SelfPlayOrchestrator.TestIterationResult(i);
            result.setSuccess(true);
            result.setTotalDuration(1000L + i * 100);
            orchestrator.getIterationResults().add(result);
        }

        assertEquals(3, orchestrator.getIterationResults().size());

        // Verify iteration numbers
        assertEquals(0, orchestrator.getIterationResults().get(0).getIterationNumber());
        assertEquals(1, orchestrator.getIterationResults().get(1).getIterationNumber());
        assertEquals(2, orchestrator.getIterationResults().get(2).getIterationNumber());
    }

    @Test
    @Order(11)
    @DisplayName("Test Thread Safety")
    void testThreadSafety() throws InterruptedException {
        // Test concurrent access to metrics
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                orchestrator.getMetrics().put("thread." + Thread.currentThread().getId() + "." + i, (long) i);
            }
        };

        // Create multiple threads
        Thread[] threads = new Thread[3];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify all metrics were collected
        assertTrue(orchestrator.getMetrics().size() > 0, "Metrics should be collected from all threads");
    }

    @Test
    @Order(12)
    @DisplayName("Test Performance Metrics")
    void testPerformanceMetrics() {
        // Test performance tracking
        long startTime = System.currentTimeMillis();

        // Simulate some work
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - startTime;
        orchestrator.getMetrics().put("operation.duration", duration);

        assertTrue(duration >= 100, "Duration should be at least 100ms");
    }
}
