/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL - Yet Another Workflow Language.
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observatory.rdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.integration.processmining.XesToYawlSpecGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for observatory module components working together.
 *
 * Tests the end-to-end workflow from facts collection to drift detection
 * and risk assessment through the WorkflowDNAOracle.
 */
public class ObservatoryIntegrationTest {

    private static final String TEST_SPEC_NAME = "order-processing-v2";

    @Nested
    @DisplayName("Full Workflow Integration")
    class FullWorkflowIntegration {

        @Test
        @DisplayName("Complete workflow: facts → RDF → drift detection → risk assessment")
        void testCompleteWorkflow(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment
            Path factsDir = tempDir.resolve("facts");
            Path rdfDir = tempDir.resolve("rdf");
            Path receiptsDir = tempDir.resolve("receipts");

            Files.createDirectories(factsDir);
            Files.createDirectories(rdfDir);
            Files.createDirectories(receiptsDir);

            // Create test facts
            createTestFacts(factsDir);

            // Step 1: Initialize components
            DriftDetector driftDetector = new DriftDetector(factsDir);
            XesToYawlSpecGenerator xesGenerator = new XesToYawlSpecGenerator(1);
            WorkflowDNAOracle oracle = new WorkflowDNAOracle(xesGenerator);

            // Step 2: Check for initial drift
            assertFalse(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "First run should detect no drift");

            // Step 3: Absorb some cases into DNA oracle
            oracle.absorb("order-001", TEST_SPEC_NAME,
                List.of("create", "validate", "ship"),
                Map.of("create", 100L, "validate", 50L, "ship", 200L),
                false);

            oracle.absorb("order-002", TEST_SPEC_NAME,
                List.of("create", "validate", "ship"),
                Map.of("create", 150L, "validate", 75L, "ship", 250L),
                false);

            // Step 4: Simulate fact changes
            simulateFactChanges(factsDir);

            // Step 5: Detect drift
            assertTrue(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Fact changes should be detected as drift");

            // Step 6: Update hash file (simulating successful rebuild)
            driftDetector.updateHashFile(receiptsDir);

            // Step 7: Verify drift is resolved
            assertFalse(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Updated hash should show no drift");

            // Step 8: Continue with risk assessment workflow
            oracle.absorb("order-003", TEST_SPEC_NAME,
                List.of("create", "validate", "ship"),
                Map.of("create", 120L, "validate", 60L, "ship", 220L),
                true);

            // Step 9: Assess new case
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "order-004", TEST_SPEC_NAME,
                List.of("create", "validate", "ship"));

            // Verify assessment works
            assertNotNull(assessment);
            assertFalse(assessment.riskMessage().contains("High failure risk"),
                "Should show low risk with only 1 failure out of 3 cases");

            // Step 10: Verify statistics
            assertEquals(3, oracle.getAbsorbedCaseCount(), "Should have 3 absorbed cases");
        }

        @Test
        @DisplayName("Concurrent workflow: drift detection and case absorption")
        void testConcurrentWorkflow(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment
            Path factsDir = tempDir.resolve("facts");
            Path receiptsDir = tempDir.resolve("receipts");

            Files.createDirectories(factsDir);
            Files.createDirectories(receiptsDir);

            createTestFacts(factsDir);

            // Initialize components
            DriftDetector driftDetector = new DriftDetector(factsDir);
            XesToYawlSpecGenerator xesGenerator = new XesToYawlSpecGenerator(1);
            WorkflowDNAOracle oracle = new WorkflowDNAOracle(xesGenerator);

            // Start case absorption in background
            CompletableFuture<Void> absorptionFuture = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 10; i++) {
                    try {
                        oracle.absorb("concurrent-case-" + i, TEST_SPEC_NAME,
                            List.of("task1", "task2", "task3"),
                            Map.of("task1", 100L, "task2", 50L, "task3", 150L),
                            i % 3 == 0); // 33% failure rate
                        Thread.sleep(10); // Small delay between operations
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

            // Monitor for drift in background
            CompletableFuture<Boolean> driftFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate periodic drift checking
                    for (int i = 0; i < 5; i++) {
                        Thread.sleep(20);
                        simulateFactChanges(factsDir);
                        try {
                            if (driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256"))) {
                                driftDetector.updateHashFile(receiptsDir);
                                return true;
                            }
                        } catch (Exception e) {
                            fail("Drift detection failed: " + e.getMessage());
                        }
                    }
                    return false;
                } catch (Exception e) {
                    fail("Drift monitoring failed: " + e.getMessage());
                    return false;
                }
            });

            // Wait for both operations to complete
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(absorptionFuture, driftFuture);

            // Set reasonable timeout
            boolean completed = allFutures.get(2, TimeUnit.SECONDS);
            assertTrue(completed, "Concurrent operations should complete within timeout");

            // Verify results
            assertEquals(10, oracle.getAbsorbedCaseCount(), "Should have absorbed 10 cases");
            assertTrue(driftFuture.get(), "Drift should have been detected and handled");
        }

        @Test
        @DisplayName("Integration: FactsToRDFConverter → GgenObservationBridge")
        void testFactsToRdfBridgeIntegration(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment
            Path factsDir = tempDir.resolve("facts");
            Path rdfDir = tempDir.resolve("rdf");

            Files.createDirectories(factsDir);
            Files.createDirectories(rdfDir);

            // Create comprehensive test facts
            createComprehensiveTestFacts(factsDir);

            // Initialize components
            GgenObservationBridge bridge = new GgenObservationBridge();

            // Step 1: Convert facts to RDF (simulate FactsToRDFConverter)
            // In real implementation, this would use FactsToRDFConverter
            Path factsTtl = createTestFactsTtl(rdfDir);
            bridge.loadFacts(factsTtl);

            // Step 2: Query for module statistics
            List<String> modules = bridge.getModules();
            assertEquals(4, modules.size(), "Should find 4 modules");

            // Step 3: Query for dependencies
            List<Map<String, String>> dependencies = bridge.getDependencies();
            assertTrue(dependencies.size() > 0, "Should find dependencies");

            // Step 4: Check for low coverage modules
            List<Map<String, String>> lowCoverage = bridge.getLowCoverageModules();
            assertTrue(lowCoverage.size() > 0, "Should find low coverage modules");

            // Step 5: Get comprehensive statistics
            Map<String, Object> stats = bridge.getStatistics();
            assertEquals(4, stats.get("moduleCount"), "Should have 4 modules");

            // Step 6: Verify SPARQL queries work
            String customQuery = """
                SELECT ?moduleName ?lineCoverage
                WHERE {
                    ?m ex:moduleName ?moduleName ;
                       ex:lineCoverage ?lineCoverage .
                    FILTER (?lineCoverage > 60.0)
                }
                ORDER BY DESC(?lineCoverage)
                """;

            List<Map<String, String>> results = bridge.query(customQuery);
            assertTrue(results.size() >= 2, "Should find at least 2 modules with >60% coverage");

            // Verify bridge can handle complex queries
            assertTrue(bridge.isInitialized(), "Bridge should remain initialized");
        }
    }

    @Nested
    @DisplayName("Error Recovery Integration")
    class ErrorRecoveryIntegration {

        @Test
        @DisplayName("Recover from corrupt facts file")
        void testRecoverFromCorruptFacts(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment
            Path factsDir = tempDir.resolve("facts");
            Path receiptsDir = tempDir.resolve("receipts");

            Files.createDirectories(factsDir);
            Files.createDirectories(receiptsDir);

            // Create initial valid facts
            createTestFacts(factsDir);

            // Initialize components
            DriftDetector driftDetector = new DriftDetector(factsDir);
            XesToYawlSpecGenerator xesGenerator = new XesToYawlSpecGenerator(1);
            WorkflowDNAOracle oracle = new WorkflowDNAOracle(xesGenerator);

            // Step 1: Normal operation
            assertFalse(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Should detect no drift with valid facts");

            // Step 2: Create corrupt facts file
            Path corruptFile = factsDir.resolve("modules.json");
            Files.writeString(corruptFile, "this is not valid json");

            // Step 3: Verify graceful handling
            assertThrows(IOException.class,
                () -> driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Should detect corrupt facts file");

            // Step 4: Recover by fixing the file
            createTestFacts(factsDir);

            // Step 5: Verify normal operation resumes
            assertFalse(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Should detect no drift after recovery");
        }

        @Test
        @DisplayName("Recover from XES generator failure")
        void testRecoverFromXesGeneratorFailure(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment with high failure rate
            Path factsDir = tempDir.resolve("facts");

            Files.createDirectories(factsDir);
            createTestFacts(factsDir);

            // Initialize components
            XesToYawlSpecGenerator xesGenerator = new XesToYawlSpecGenerator(1);
            WorkflowDNAOracle oracle = new WorkflowDNAOracle(xesGenerator);

            // Step 1: Absorb cases with high failure rate
            for (int i = 0; i < 10; i++) {
                oracle.absorb("fail-case-" + i, TEST_SPEC_NAME,
                    List.of("risky-task"),
                    Map.of("risky-task", 100L),
                    true); // All cases fail
            }

            // Step 2: Assess should not crash even with high failure rate
            WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                "new-case", TEST_SPEC_NAME, List.of("risky-task"));

            // Step 3: Verify graceful degradation
            assertNotNull(assessment);
            assertEquals(1.0, assessment.historicalFailureRate(), 0.001);
            assertTrue(assessment.riskMessage().contains("High failure risk"));

            // Step 4: Alternative path should be empty (graceful degradation)
            assertTrue(assessment.alternativePathXml().isEmpty(),
                "Alternative path should be empty when XES generation fails");
        }
    }

    @Nested
    @DisplayName("Performance Integration Tests")
    class PerformanceIntegrationTests {

        @Test
        @DisplayName("Handle large number of cases efficiently")
        void testLargeScaleCaseHandling(@TempDir Path tempDir) throws Exception {
            // Given - Setup test environment
            Path factsDir = tempDir.resolve("facts");
            Path receiptsDir = tempDir.resolve("receipts");

            Files.createDirectories(factsDir);
            Files.createDirectories(receiptsDir);

            createTestFacts(factsDir);

            // Initialize components
            DriftDetector driftDetector = new DriftDetector(factsDir);
            XesToYawlSpecGenerator xesGenerator = new XesToYawlSpecGenerator(1);
            WorkflowDNAOracle oracle = new WorkflowDNAOracle(xesGenerator);

            // Step 1: Absorb large number of cases
            long startTime = System.currentTimeMillis();
            final int caseCount = 1000;

            for (int i = 0; i < caseCount; i++) {
                oracle.absorb("bulk-case-" + i, TEST_SPEC_NAME,
                    List.of("task1", "task2", "task3"),
                    Map.of("task1", 100L, "task2", 50L, "task3", 150L),
                    i % 100 < 20); // 20% failure rate
            }
            long absorptionTime = System.currentTimeMillis() - startTime;

            // Step 2: Verify performance
            assertTrue(absorptionTime < 30000, // Should complete within 30 seconds
                "Absorption of " + caseCount + " cases should complete within 30 seconds");

            assertEquals(caseCount, oracle.getAbsorbedCaseCount(),
                "Should have absorbed all cases");

            // Step 3: Test drift detection performance
            simulateFactChanges(factsDir);
            startTime = System.currentTimeMillis();
            assertTrue(driftDetector.checkDrift(receiptsDir.resolve("observatory-facts.sha256")),
                "Should detect drift");
            long driftTime = System.currentTimeMillis() - startTime;

            assertTrue(driftTime < 5000,
                "Drift detection should complete within 5 seconds");

            // Step 4: Test risk assessment performance
            startTime = System.currentTimeMillis();
            for (int i = 0; i < 100; i++) {
                WorkflowDNAOracle.DNARecommendation assessment = oracle.assess(
                    "assessment-case-" + i, TEST_SPEC_NAME,
                    List.of("task1", "task2", "task3"));
                assertNotNull(assessment);
            }
            long assessmentTime = System.currentTimeMillis() - startTime;

            assertTrue(assessmentTime < 10000,
                "100 risk assessments should complete within 10 seconds");
        }
    }

    // Helper methods
    private void createTestFacts(Path factsDir) throws IOException {
        // modules.json
        String modulesJson = """
            {
                "modules": [
                    {
                        "moduleName": "yawl-engine",
                        "version": "6.0.0",
                        "lineCoverage": 78.5,
                        "testCount": 234,
                        "dependencies": []
                    },
                    {
                        "moduleName": "yawl-stateless",
                        "version": "6.0.0",
                        "lineCoverage": 65.2,
                        "testCount": 156,
                        "dependencies": ["yawl-engine"]
                    }
                ]
            }
            """;

        // deps-conflicts.json
        String depsJson = """
            {
                "dependencyConflicts": []
            }
            """;

        // coverage.json
        String coverageJson = """
            {
                "totalLineCoverage": 71.8,
                "totalBranchCoverage": 68.3,
                "modules": ["yawl-engine", "yawl-stateless"]
            }
            """;

        Files.writeString(factsDir.resolve("modules.json"), modulesJson);
        Files.writeString(factsDir.resolve("deps-conflicts.json"), depsJson);
        Files.writeString(factsDir.resolve("coverage.json"), coverageJson);
    }

    private void createComprehensiveTestFacts(Path factsDir) throws IOException {
        // More comprehensive test data
        String comprehensiveJson = """
            {
                "modules": [
                    {
                        "moduleName": "yawl-engine",
                        "version": "6.0.0",
                        "lineCoverage": 78.5,
                        "testCount": 234,
                        "dependencies": []
                    },
                    {
                        "moduleName": "yawl-stateless",
                        "version": "6.0.0",
                        "lineCoverage": 65.2,
                        "testCount": 156,
                        "dependencies": ["yawl-engine"]
                    },
                    {
                        "moduleName": "yawl-integration",
                        "version": "6.0.0",
                        "lineCoverage": 45.0,
                        "testCount": 89,
                        "dependencies": ["yawl-engine", "yawl-stateless"]
                    },
                    {
                        "moduleName": "yawl-mcp-a2a-app",
                        "version": "6.0.0",
                        "lineCoverage": 82.1,
                        "testCount": 312,
                        "dependencies": ["yawl-engine", "yawl-integration"]
                    }
                ],
                "dependencyConflicts": [],
                "integrationPoints": [
                    {
                        "type": "MCP",
                        "server": "localhost",
                        "toolCount": 5
                    },
                    {
                        "type": "A2A",
                        "server": "service",
                        "skillCount": 3
                    }
                ]
            }
            """;

        Files.writeString(factsDir.resolve("comprehensive.json"), comprehensiveJson);
    }

    private void simulateFactChanges(Path factsDir) throws IOException {
        // Simulate fact changes by adding/updating files
        String newModulesJson = """
            {
                "modules": [
                    {
                        "moduleName": "yawl-engine",
                        "version": "6.0.1",
                        "lineCoverage": 80.2,
                        "testCount": 245,
                        "dependencies": []
                    },
                    {
                        "moduleName": "yawl-stateless",
                        "version": "6.0.0",
                        "lineCoverage": 65.2,
                        "testCount": 156,
                        "dependencies": ["yawl-engine"]
                    },
                    {
                        "moduleName": "yawl-new-module",
                        "version": "1.0.0",
                        "lineCoverage": 90.0,
                        "testCount": 45,
                        "dependencies": ["yawl-engine"]
                    }
                ]
            }
            """;

        Files.writeString(factsDir.resolve("modules.json"), newModulesJson);
    }

    private Path createTestFactsTtl(Path rdfDir) throws IOException {
        String factsTtl = """
            @prefix ex: <http://yawlfoundation.org/facts#> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .

            <yawl-engine> a ex:Module ;
                ex:moduleName "yawl-engine" ;
                ex:version "6.0.0" ;
                ex:lineCoverage 78.5 ;
                ex:testCount 234 ;
                ex:dependencies () .

            <yawl-stateless> a ex:Module ;
                ex:moduleName "yawl-stateless" ;
                ex:version "6.0.0" ;
                ex:lineCoverage 65.2 ;
                ex:testCount 156 ;
                ex:dependencies (<yawl-engine>) .

            <yawl-integration> a ex:Module ;
                ex:moduleName "yawl-integration" ;
                ex:version "6.0.0" ;
                ex:lineCoverage 45.0 ;
                ex:testCount 89 ;
                ex:dependencies (<yawl-engine>, <yawl-stateless>) .

            <yawl-mcp-a2a-app> a ex:Module ;
                ex:moduleName "yawl-mcp-a2a-app" ;
                ex:version "6.0.0" ;
                ex:lineCoverage 82.1 ;
                ex:testCount 312 ;
                ex:dependencies (<yawl-engine>, <yawl-integration>) .

            <integration-mcp> a ex:IntegrationPoint ;
                ex:integrationType "MCP" ;
                ex:server "localhost" ;
                ex:toolCount 5 .

            <integration-a2a> a ex:IntegrationPoint ;
                ex:integrationType "A2A" ;
                ex:server "service" ;
                ex:skillCount 3 .
            """;

        Path factsTtlFile = rdfDir.resolve("facts.ttl");
        Files.writeString(factsTtlFile, factsTtl);
        return factsTtlFile;
    }
}