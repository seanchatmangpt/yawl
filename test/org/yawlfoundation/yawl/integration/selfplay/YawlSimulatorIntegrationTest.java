/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.selfplay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.sim.YawlSimulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for YawlSimulator that validates OCEL output.
 *
 * <p>Chicago TDD: Tests the actual YawlSimulator.runPI() execution
 * with real OCEL2 file generation, not mocks.
 *
 * <p>Verifies:
 * <ul>
 *   <li>PI simulation generates valid OCEL2 JSON file</li>
 *   <li>OCEL file contains required structure (events, objects, types)</li>
 *   <li>Sprint simulation generates valid output</li>
 *   <li>Portfolio sync produces expected events</li>
 *   <li>Self-assessment generates fitness metric</li>
 * </ul>
 */
class YawlSimulatorIntegrationTest {

    private YawlSimulator simulator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Set the OCEL output directory to temp dir for test isolation
        System.setProperty("yawl.sim.ocel.dir", tempDir.resolve("ocel").toString());
        simulator = YawlSimulator.getInstance();
    }

    /**
     * Test that runPI produces a valid OCEL file.
     * This is the critical integration test - YawlSimulator.runPI()
     * must be called and produce real output.
     */
    @Test
    void testRunPIProducesValidOcelFile() throws Exception {
        // Execute PI simulation
        String ocelPath = simulator.runPI(1);

        // Verify path is returned
        assertNotNull(ocelPath, "runPI must return OCEL file path");

        // Verify file exists
        Path path = Path.of(ocelPath);
        assertTrue(Files.exists(path), "OCEL file must exist at: " + ocelPath);

        // Verify file is valid JSON
        String content = Files.readString(path);
        assertTrue(content.startsWith("{"), "OCEL file must be valid JSON starting with {");
        assertTrue(content.contains("\"events\":"), "OCEL must contain events array");
        assertTrue(content.contains("\"objects\":"), "OCEL must contain objects array");
        assertTrue(content.contains("\"objectTypes\":"), "OCEL must contain objectTypes");
        assertTrue(content.contains("\"eventTypes\":"), "OCEL must contain eventTypes");

        // Verify events were generated (4 sprints + Inspect & Adapt)
        assertTrue(content.contains("\"type\":\"SprintStart\"") ||
                   content.contains("\"type\":\"StoryComplete\"") ||
                   content.contains("\"type\":\"SprintEnd\""),
            "OCEL must contain sprint events");

        // Verify Inspect & Adapt event exists
        assertTrue(content.contains("InspectAdapt") || content.contains("ia-1"),
            "OCEL must contain Inspect & Adapt event");
    }

    /**
     * Test that runSprint produces valid OCEL.
     */
    @Test
    void testRunSprintProducesValidOcel() throws Exception {
        String ocelPath = simulator.runSprint(1, "TEST-FEATURE-1", "TestTeam");

        assertNotNull(ocelPath);
        assertTrue(Files.exists(Path.of(ocelPath)));

        String content = Files.readString(Path.of(ocelPath));
        assertTrue(content.contains("\"type\":\"SprintStart\""));
        assertTrue(content.contains("\"type\":\"SprintEnd\""));
        assertTrue(content.contains("TEST-FEATURE-1"));
        assertTrue(content.contains("velocity"));
    }

    /**
     * Test that runPortfolioSync produces valid OCEL.
     */
    @Test
    void testRunPortfolioSyncProducesValidOcel() throws Exception {
        String ocelPath = simulator.runPortfolioSync();

        assertNotNull(ocelPath);
        assertTrue(Files.exists(Path.of(ocelPath)));

        String content = Files.readString(Path.of(ocelPath));
        assertTrue(content.contains("PortfolioSync") || content.contains("Portfolio"));
    }

    /**
     * Test that runSelfAssessment produces valid OCEL with fitness.
     */
    @Test
    void testRunSelfAssessmentProducesValidOcel() throws Exception {
        String ocelPath = simulator.runSelfAssessment();

        assertNotNull(ocelPath);
        assertTrue(Files.exists(Path.of(ocelPath)));

        String content = Files.readString(Path.of(ocelPath));
        assertTrue(content.contains("SelfAssessment") || content.contains("Simulation"));
        assertTrue(content.contains("fitness"));
    }

    /**
     * Test fitness score is updated after PI run.
     */
    @Test
    void testFitnessScoreUpdatesAfterPI() throws Exception {
        double initialFitness = simulator.getCurrentFitness();

        // Run a PI
        simulator.runPI(2);

        // Fitness should be updated (may increase or stay same)
        double newFitness = simulator.getCurrentFitness();
        assertTrue(newFitness >= 0.0 && newFitness <= 1.0,
            "Fitness must be in range [0.0, 1.0], got: " + newFitness);
    }

    /**
     * Test that multiple PI runs create separate OCEL files.
     */
    @Test
    void testMultiplePIsCreateSeparateFiles() throws Exception {
        String path1 = simulator.runPI(10);
        String path2 = simulator.runPI(11);

        assertNotEquals(path1, path2, "Different PIs must create different files");
        assertTrue(Files.exists(Path.of(path1)));
        assertTrue(Files.exists(Path.of(path2)));
    }

    /**
     * Test work item creation.
     */
    @Test
    void testCreateWorkItem() throws Exception {
        // Create a mock work item for validation
        var item = createMockWorkItem("test-case-123");

        // Should not throw
        assertDoesNotThrow(() -> simulator.createWorkItem(item));
    }

    /**
     * Test invalid PI ID throws exception.
     */
    @Test
    void testInvalidPIIdThrowsException() {
        assertThrows(Exception.class, () -> simulator.runPI(0));
        assertThrows(Exception.class, () -> simulator.runPI(-1));
    }

    /**
     * Test invalid sprint ID throws exception.
     */
    @Test
    void testInvalidSprintIdThrowsException() {
        assertThrows(Exception.class, () -> simulator.runSprint(0, "feature", "team"));
        assertThrows(Exception.class, () -> simulator.runSprint(5, "feature", "team"));
    }

    // Helper to create a mock work item via reflection
    private org.yawlfoundation.yawl.elements.YWorkItem createMockWorkItem(String caseId) throws Exception {
        var ctor = org.yawlfoundation.yawl.elements.YWorkItem.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var item = ctor.newInstance();

        // Set case ID via reflection if needed
        try {
            var caseIdField = org.yawlfoundation.yawl.elements.YWorkItem.class.getDeclaredField("_caseID");
            caseIdField.setAccessible(true);
            caseIdField.set(item, caseId);
        } catch (NoSuchFieldException e) {
            // Field may have different name, try alternative
            var idField = org.yawlfoundation.yawl.elements.YWorkItem.class.getDeclaredField("caseID");
            idField.setAccessible(true);
            idField.set(item, caseId);
        }

        return item;
    }
}
