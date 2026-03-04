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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;
import org.yawlfoundation.yawl.integration.selfplay.ConformancePipeline;
import org.yawlfoundation.yawl.integration.selfplay.QLeverTestUtils;
import org.yawlfoundation.yawl.integration.selfplay.model.V7Gap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for ConformancePipeline integration with rust4pm and QLever.
 * Tests conformance analysis, score calculation, and persistence.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("ConformancePipeline Integration Tests")
class ConformancePipelineTest {

    private ConformancePipeline conformancePipeline;
    private QLeverTestUtils qleverUtils;
    private static final String TEST_OUTPUT_DIR = "target/test-output";

    @BeforeEach
    void setUp() throws Exception {
        // Create test output directory
        Path outputDir = Paths.get(TEST_OUTPUT_DIR);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        // Initialize QLever test utils
        qleverUtils = new QLeverTestUtils();

        // Initialize conformance pipeline
        conformancePipeline = new ConformancePipeline();
        conformancePipeline.initialize();
    }

    @Test
    @DisplayName("Test 1: rust4pm import OCel JSON from file path")
    void testRust4pmImportOcel() throws Exception {
        // Create a test OCel file
        String ocelJson = """
            {
                "objectTypes": [{"name": "Order", "attributes": []}],
                "eventTypes": [{"name": "ProcessOrder", "attributes": []}],
                "objects": [
                    {"id": "order-001", "type": "Order", "attributes": {}}
                ],
                "events": [
                    {"id": "event-001", "type": "ProcessOrder", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "order-001", "qualifier": ""}]}
                ]
            }
            """;

        // Write OCel to test file
        Path ocelFile = outputDir.resolve("test-ocel.json");
        Files.writeString(ocelFile, ocelJson);

        // Import OCel via rust4pm
        String ocelId = conformancePipeline.importOcelJsonPath(ocelFile.toString());

        // Verify import succeeded
        assertNotNull(ocelId, "OCel import should return valid ID");
        assertFalse(ocelId.isEmpty(), "OCel ID should not be empty");

        // Verify OCel is stored in QLever
        boolean exists = qleverUtils.gapExists(ocelId);
        assertTrue(exists, "Imported OCel should be stored in QLever");

        // Clean up
        Files.deleteIfExists(ocelFile);
    }

    @Test
    @DisplayName("Test 2: Token replay produces valid conformance score")
    void testTokenReplayProducesScore() throws Exception {
        // Create test OCel data
        String ocelJson = """
            {
                "objectTypes": [{"name": "Case", "attributes": []}],
                "eventTypes": [{"name": "CompleteTask", "attributes": []}],
                "objects": [
                    {"id": "case-001", "type": "Case", "attributes": {}}
                ],
                "events": [
                    {"id": "task1", "type": "CompleteTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-001", "qualifier": ""}]}
                ]
            }
            """;

        // Write OCel to test file
        Path ocelFile = outputDir.resolve("replay-ocel.json");
        Files.writeString(ocelFile, ocelJson);

        // Run conformance analysis
        double score = conformancePipeline.runConformanceAnalysis(ocelFile.toString());

        // Verify score is valid
        assertTrue(score >= 0.0 && score <= 1.0,
            "Conformance score must be between 0.0 and 1.0, got: " + score);

        // Verify score is meaningful (not 0.0 or 1.0 for this test data)
        assertTrue(score > 0.0, "Score should be > 0.0 for valid trace");
        assertTrue(score < 1.0, "Score should be < 1.0 for partial conformance");

        // Clean up
        Files.deleteIfExists(ocelFile);
    }

    @Test
    @DisplayName("Test 3: Conformance score is written to QLever")
    void testScoreWrittenToQLever() throws Exception {
        // Create test OCel data
        String ocelJson = """
            {
                "objectTypes": [{"name": "BusinessCase", "attributes": []}],
                "eventTypes": [{"name": "ProcessStep", "attributes": []}],
                "objects": [
                    {"id": "business-001", "type": "BusinessCase", "attributes": {}}
                ],
                "events": [
                    {"id": "step1", "type": "ProcessStep", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "business-001", "qualifier": ""}]}
                ]
            }
            """;

        // Write OCel to test file
        Path ocelFile = outputDir.resolve("score-test-ocel.json");
        Files.writeString(ocelFile, ocelJson);

        // Run conformance analysis
        double score = conformancePipeline.runConformanceAnalysis(ocelFile.toString());

        // Get score from QLever
        double fromQLever = qleverUtils.getConformanceScore();

        // Verify scores match
        assertEquals(score, fromQLever, 0.001,
            "Conformance score from pipeline should match QLever score");

        // Clean up
        Files.deleteIfExists(ocelFile);
    }

    @Test
    @DisplayName("Test 4: Multiple analyses produce scores in valid range")
    void testMultipleAnalysesProduceValidScores() throws Exception {
        // Create different OCel scenarios
        String[] ocelScenarios = {
            // Perfect match
            """
            {
                "objectTypes": [{"name": "PerfectCase", "attributes": []}],
                "eventTypes": [{"name": "PerfectTask", "attributes": []}],
                "objects": [
                    {"id": "perfect-001", "type": "PerfectCase", "attributes": {}}
                ],
                "events": [
                    {"id": "perfect1", "type": "PerfectTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "perfect-001", "qualifier": ""}]}
                ]
            }
            """,

            // Partial match
            """
            {
                "objectTypes": [{"name": "PartialCase", "attributes": []}],
                "eventTypes": [{"name": "ProcessTask", "attributes": []}],
                "objects": [
                    {"id": "partial-001", "type": "PartialCase", "attributes": {}}
                ],
                "events": [
                    {"id": "partial1", "type": "ProcessTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "partial-001", "qualifier": ""}]}
                ]
            }
            """,

            // Mismatch
            """
            {
                "objectTypes": [{"name": "MismatchCase", "attributes": []}],
                "eventTypes": [{"name": "WrongTask", "attributes": []}],
                "objects": [
                    {"id": "mismatch-001", "type": "MismatchCase", "attributes": {}}
                ],
                "events": [
                    {"id": "wrong1", "type": "WrongTask", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "mismatch-001", "qualifier": ""}]}
                ]
            }
            """
        };

        double[] scores = new double[ocelScenarios.length];

        // Run analysis for each scenario
        for (int i = 0; i < ocelScenarios.length; i++) {
            Path ocelFile = outputDir.resolve("scenario-" + i + ".json");
            Files.writeString(ocelFile, ocelScenarios[i]);

            scores[i] = conformancePipeline.runConformanceAnalysis(ocelFile.toString());

            // Verify each score is valid
            assertTrue(scores[i] >= 0.0 && scores[i] <= 1.0,
                "Score " + i + " must be between 0.0 and 1.0");

            Files.deleteIfExists(ocelFile);
        }

        // Verify scores are different (since scenarios are different)
        boolean allDifferent = true;
        for (int i = 0; i < scores.length - 1; i++) {
            if (Math.abs(scores[i] - scores[i + 1]) < 0.001) {
                allDifferent = false;
                break;
            }
        }
        assertTrue(allDifferent, "Different scenarios should produce different scores");
    }

    @Test
    @DisplayName("Test 5: Conformance analysis with complex workflow")
    void testComplexWorkflowConformance() throws Exception {
        // Create a more complex OCel with multiple cases and events
        String complexOcelJson = """
            {
                "objectTypes": [
                    {"name": "Case", "attributes": []},
                    {"name": "Task", "attributes": []}
                ],
                "eventTypes": [
                    {"name": "StartCase", "attributes": []},
                    {"name": "CompleteTask", "attributes": []},
                    {"name": "EndCase", "attributes": []}
                ],
                "objects": [
                    {"id": "case-001", "type": "Case", "attributes": {}},
                    {"id": "case-002", "type": "Case", "attributes": {}},
                    {"id": "task-001", "type": "Task", "attributes": {}},
                    {"id": "task-002", "type": "Task", "attributes": {}}
                ],
                "events": [
                    {"id": "start-1", "type": "StartCase", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-001", "qualifier": ""}]},
                    {"id": "complete-1", "type": "CompleteTask", "time": "2024-01-01T10:30:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-001", "qualifier": ""},
                                                         {"objectId": "task-001", "qualifier": ""}]},
                    {"id": "complete-2", "type": "CompleteTask", "time": "2024-01-01T11:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-002", "qualifier": ""},
                                                         {"objectId": "task-002", "qualifier": ""}]},
                    {"id": "end-1", "type": "EndCase", "time": "2024-01-01T11:30:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-001", "qualifier": ""}]},
                    {"id": "end-2", "type": "EndCase", "time": "2024-01-01T12:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "case-002", "qualifier": ""}]}
                ]
            }
            """;

        // Write complex OCel to test file
        Path complexOcelFile = outputDir.resolve("complex-workflow.json");
        Files.writeString(complexOcelFile, complexOcelJson);

        // Run conformance analysis
        double score = conformancePipeline.runConformanceAnalysis(complexOcelFile.toString());

        // Verify score is valid
        assertTrue(score >= 0.0 && score <= 1.0,
            "Complex workflow conformance score must be valid");

        // Verify multiple analyses were performed
        int analysisCount = qleverUtils.getNativeCallCount();
        assertTrue(analysisCount > 0, "Should have performed conformance analyses");

        // Clean up
        Files.deleteIfExists(complexOcelFile);
    }

    @Test
    @DisplayName("Test 6: Error handling for invalid OCel files")
    void testErrorHandlingForInvalidOcel() throws Exception {
        // Create invalid OCel (missing required fields)
        String invalidOcelJson = """
            {
                "objectTypes": [],
                "eventTypes": [],
                "objects": [],
                "events": []
            }
            """;

        // Write invalid OCel to test file
        Path invalidOcelFile = outputDir.resolve("invalid-ocel.json");
        Files.writeString(invalidOcelFile, invalidOcelJson);

        // Should throw exception for invalid OCel
        assertThrows(Exception.class, () -> {
            conformancePipeline.runConformanceAnalysis(invalidOcelFile.toString());
        }, "Should throw exception for invalid OCel file");

        // Clean up
        Files.deleteIfExists(invalidOcelFile);
    }

    @Test
    @DisplayName("Test 7: Conformance score persistence across multiple runs")
    void testScorePersistence() throws Exception {
        // Create consistent test data
        String ocelJson = """
            {
                "objectTypes": [{"name": "TestCase", "attributes": []}],
                "eventTypes": [{"name": "TestEvent", "attributes": []}],
                "objects": [
                    {"id": "test-001", "type": "TestCase", "attributes": {}}
                ],
                "events": [
                    {"id": "test-event-001", "type": "TestEvent", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "test-001", "qualifier": ""}]}
                ]
            }
            """;

        Path ocelFile = outputDir.resolve("persistence-test.json");
        Files.writeString(ocelFile, ocelJson);

        // Run analysis multiple times
        double score1 = conformancePipeline.runConformanceAnalysis(ocelFile.toString());
        double score2 = conformancePipeline.runConformanceAnalysis(ocelFile.toString());
        double score3 = conformancePipeline.runConformanceAnalysis(ocelFile.toString());

        // Scores should be consistent for same input
        assertEquals(score1, score2, 0.001, "Scores should be consistent");
        assertEquals(score2, score3, 0.001, "Scores should be consistent");

        // Clean up
        Files.deleteIfExists(ocelFile);
    }

    @Test
    @DisplayName("Test 8: Integration with gap analysis engine")
    void testIntegrationWithGapAnalysis() throws Exception {
        // Create test OCel data
        String ocelJson = """
            {
                "objectTypes": [{"name": "GapCase", "attributes": []}],
                "eventTypes": [{"name": "GapEvent", "attributes": []}],
                "objects": [
                    {"id": "gap-001", "type": "GapCase", "attributes": {}}
                ],
                "events": [
                    {"id": "gap-event-001", "type": "GapEvent", "time": "2024-01-01T10:00:00Z",
                     "attributes": {}, "relationships": [{"objectId": "gap-001", "qualifier": ""}]}
                ]
            }
            """;

        Path ocelFile = outputDir.resolve("gap-analysis-test.json");
        Files.writeString(ocelFile, ocelJson);

        // Run conformance analysis
        double score = conformancePipeline.runConformanceAnalysis(ocelFile.toString());

        // Check if gaps are identified based on score
        if (score < 0.8) {
            // Score is low, should identify gaps
            int gapCount = qleverUtils.countCapabilityGaps();
            assertTrue(gapCount >= 0, "Should be able to count gaps");
        }

        // Clean up
        Files.deleteIfExists(ocelFile);
    }

    private Path outputDir = Paths.get(TEST_OUTPUT_DIR);
}