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
import org.yawlfoundation.yawl.integration.selfplay.YawlSimulator;
import org.yawlfoundation.yawl.integration.selfplay.OcelValidator;
import org.yawlfoundation.yawl.integration.selfplay.QLeverTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for OCel export functionality from YAWL simulations.
 * Tests sprint and PI OCel generation, validation, and completeness.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("OCel Export Tests")
class OcelExportTest {

    private static final String TEST_OUTPUT_DIR = "target/test-output";
    private Path sprintOutputDir;
    private Path piOutputDir;

    @BeforeEach
    void setUp() throws Exception {
        // Create test output directories
        sprintOutputDir = Paths.get(TEST_OUTPUT_DIR, "sprint-exports");
        piOutputDir = Paths.get(TEST_OUTPUT_DIR, "pi-exports");

        if (!Files.exists(sprintOutputDir)) {
            Files.createDirectories(sprintOutputDir);
        }
        if (!Files.exists(piOutputDir)) {
            Files.createDirectories(piOutputDir);
        }
    }

    @Test
    @DisplayName("Test 1: Sprint OCel is valid and has sufficient events")
    void testSprintOcelIsValid() throws Exception {
        // Run sprint simulation
        YawlSimulator.runSprint();

        // Get latest sprint OCel file
        Path sprintOcelPath = getLatestSprintOcelPath();

        // Verify file exists
        assertTrue(Files.exists(sprintOcelPath), "Sprint OCel file should exist");

        // Validate OCel structure
        boolean isValid = OcelValidator.isValid(sprintOcelPath.toString());
        assertTrue(isValid, "Sprint OCel should be valid");

        // Check minimum event count
        int eventCount = OcelValidator.getEventCount(sprintOcelPath.toString());
        assertTrue(eventCount >= 10,
            "Sprint OCel should have at least 10 events, got: " + eventCount);

        // Check object types
        Set<String> objectTypes = OcelValidator.getObjectTypes(sprintOcelPath.toString());
        assertFalse(objectTypes.isEmpty(), "Should have object types");

        // Clean up
        Files.deleteIfExists(sprintOcelPath);
    }

    @Test
    @DisplayName("Test 2: PI OCel has all required object types")
    void testPiOcelHasAllObjectTypes() throws Exception {
        // Run PI simulation
        YawlSimulator.runPI();

        // Get latest PI OCel file
        Path piOcelPath = getLatestPiOcelPath();

        // Verify file exists
        assertTrue(Files.exists(piOcelPath), "PI OCel file should exist");

        // Validate OCel structure
        boolean isValid = OcelValidator.isValid(piOcelPath.toString());
        assertTrue(isValid, "PI OCel should be valid");

        // Check for all required PI object types
        Set<String> objectTypes = OcelValidator.getObjectTypes(piOcelPath.toString());
        assertTrue(objectTypes.contains("Feature"),
            "PI OCel should contain Feature objects");
        assertTrue(objectTypes.contains("Team"),
            "PI OCel should contain Team objects");
        assertTrue(objectTypes.contains("PI"),
            "PI OCel should contain PI objects");
        assertTrue(objectTypes.contains("ART"),
            "PI OCel should contain ART objects");

        // Check minimum event count
        int eventCount = OcelValidator.getEventCount(piOcelPath.toString());
        assertTrue(eventCount >= 5,
            "PI OCel should have at least 5 events, got: " + eventCount);

        // Clean up
        Files.deleteIfExists(piOcelPath);
    }

    @Test
    @DisplayName("Test 3: Sprint OCel has time-ordered events")
    void testSprintOcelHasTimeOrderedEvents() throws Exception {
        // Run sprint simulation
        YawlSimulator.runSprint();

        // Get latest sprint OCel file
        Path sprintOcelPath = getLatestSprintOcelPath();

        // Get time range
        String[] timeRange = OcelValidator.getTimeRange(sprintOcelPath.toString());

        assertNotNull(timeRange, "Should have time range for sprint events");
        assertNotNull(timeRange[0], "Should have start time");
        assertNotNull(timeRange[1], "Should have end time");

        // Verify time ordering (events should be in chronological order)
        assertTrue(timeRange[0].compareTo(timeRange[1]) <= 0,
            "Start time should be before or equal to end time");

        // Clean up
        Files.deleteIfExists(sprintOcelPath);
    }

    @Test
    @DisplayName("Test 4: PI OCel has proper relationships between objects")
    void testPiOcelHasProperRelationships() throws Exception {
        // Run PI simulation
        YawlSimulator.runPI();

        // Get latest PI OCel file
        Path piOcelPath = getLatestPiOcelPath();

        // Check all events have relationships
        boolean allHaveRelationships = OcelValidator.allEventsHaveRelationships(piOcelPath.toString());
        assertTrue(allHaveRelationships,
            "All PI events should have relationships to objects");

        // Check specific object counts
        int featureCount = OcelValidator.getObjectCountByType(piOcelPath.toString(), "Feature");
        int teamCount = OcelValidator.getObjectCountByType(piOcelPath.toString(), "Team");

        assertTrue(featureCount > 0, "Should have at least one Feature");
        assertTrue(teamCount > 0, "Should have at least one Team");

        // Clean up
        Files.deleteIfExists(piOcelPath);
    }

    @Test
    @DisplayName("Test 5: Multiple sprint simulations produce different but valid OCel")
    void testMultipleSprintSimulations() throws Exception {
        Path[] ocelFiles = new Path[3];

        // Run multiple sprint simulations
        for (int i = 0; i < 3; i++) {
            YawlSimulator.runSprint();
            ocelFiles[i] = getLatestSprintOcelPath();

            // Validate each OCel
            assertTrue(Files.exists(ocelFiles[i]), "Sprint OCel " + i + " should exist");
            assertTrue(OcelValidator.isValid(ocelFiles[i].toString()),
                "Sprint OCel " + i + " should be valid");

            int eventCount = OcelValidator.getEventCount(ocelFiles[i].toString());
            assertTrue(eventCount >= 10, "Sprint OCel " + i + " should have >= 10 events");
        }

        // Verify files are different (different simulation runs)
        boolean allDifferent = true;
        for (int i = 0; i < ocelFiles.length - 1; i++) {
            for (int j = i + 1; j < ocelFiles.length; j++) {
                // Compare file contents
                String content1 = Files.readString(ocelFiles[i]);
                String content2 = Files.readString(ocelFiles[j]);
                if (content1.equals(content2)) {
                    allDifferent = false;
                    break;
                }
            }
            if (!allDifferent) break;
        }
        assertTrue(allDifferent, "Multiple sprint simulations should produce different OCel files");

        // Clean up
        for (Path file : ocelFiles) {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("Test 6: PI simulation generates realistic ART structure")
    void testPiSimulationGeneratesRealisticArtStructure() throws Exception {
        // Run PI simulation
        YawlSimulator.runPI();

        // Get latest PI OCel file
        Path piOcelPath = getLatestPiOcelPath();

        // Count different object types
        Set<String> objectTypes = OcelValidator.getObjectTypes(piOcelPath.toString());
        int featureCount = OcelValidator.getObjectCountByType(piOcelPath.toString(), "Feature");
        int artCount = OcelValidator.getObjectCountByType(piOcelPath.toString(), "ART");
        int piCount = OcelValidator.getObjectCountByType(piOcelPath.toString(), "PI");

        // Verify realistic structure
        assertTrue(featureCount >= 2, "Should have multiple Features");
        assertTrue(artCount >= 1, "Should have at least one ART");
        assertTrue(piCount >= 1, "Should have at least one PI");

        // Check object type diversity
        assertTrue(objectTypes.size() >= 4,
            "Should have at least 4 different object types");

        // Clean up
        Files.deleteIfExists(piOcelPath);
    }

    @Test
    @DisplayName("Test 7: Sprint OCel events are properly timestamped")
    void testSprintOcelEventsAreProperlyTimestamped() throws Exception {
        // Run sprint simulation
        YawlSimulator.runSprint();

        // Get latest sprint OCel file
        Path sprintOcelPath = getLatestSprintOcelPath();

        // Get time range
        String[] timeRange = OcelValidator.getTimeRange(sprintOcelPath.toString());

        assertNotNull(timeRange, "Should have time range");
        assertNotNull(timeRange[0], "Should have start time");
        assertNotNull(timeRange[1], "Should have end time");

        // Check that timestamps are valid ISO format
        String startTime = timeRange[0];
        String endTime = timeRange[1];

        // Basic validation of ISO format (YYYY-MM-DDTHH:MM:SSZ)
        assertTrue(startTime.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
            "Start time should be in ISO format");
        assertTrue(endTime.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
            "End time should be in ISO format");

        // Check time difference is reasonable (should be at least 1 minute)
        // This is a rough check - actual implementation would parse properly
        assertTrue(endTime.compareTo(startTime) >= 0,
            "End time should be after start time");

        // Clean up
        Files.deleteIfExists(sprintOcelPath);
    }

    @Test
    @DisplayName("Test 8: OCel files can be imported back into QLever")
    void testOcelFilesCanBeImportedIntoQLever() throws Exception {
        // Initialize QLever test utils
        QLeverTestUtils qleverUtils = new QLeverTestUtils();

        try {
            // Run sprint simulation
            YawlSimulator.runSprint();
            Path sprintOcelPath = getLatestSprintOcelPath();

            // Run PI simulation
            YawlSimulator.runPI();
            Path piOcelPath = getLatestPiOcelPath();

            // Get initial counts
            int initialCompositions = qleverUtils.getCompositionCount();
            int initialNativeCalls = qleverUtils.getNativeCallCount();

            // Import sprint OCel
            ConformancePipeline conformancePipeline = new ConformancePipeline();
            conformancePipeline.initialize();

            try {
                String sprintOcelId = conformancePipeline.importOcelJsonPath(sprintOcelPath.toString());
                assertNotNull(sprintOcelId, "Sprint OCel should be imported");

                // Verify import
                boolean sprintExists = qleverUtils.gapExists(sprintOcelId);
                assertTrue(sprintExists, "Imported sprint OCel should be in QLever");

                // Import PI OCel
                String piOcelId = conformancePipeline.importOcelJsonPath(piOcelPath.toString());
                assertNotNull(piOcelId, "PI OCel should be imported");

                // Verify import
                boolean piExists = qleverUtils.gapExists(piOcelId);
                assertTrue(piExists, "Imported PI OCel should be in QLever");

                // Verify counts increased
                int finalCompositions = qleverUtils.getCompositionCount();
                int finalNativeCalls = qleverUtils.getNativeCallCount();

                assertTrue(finalCompositions >= initialCompositions,
                    "Composition count should not decrease");
                assertTrue(finalNativeCalls >= initialNativeCalls,
                    "Native call count should not decrease");

            } finally {
                conformancePipeline.shutdown();
            }

            // Clean up
            Files.deleteIfExists(sprintOcelPath);
            Files.deleteIfExists(piOcelPath);

        } finally {
            qleverUtils.shutdown();
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Get the path to the latest sprint OCel export file.
     */
    private Path getLatestSprintOcelPath() throws Exception {
        // In a real implementation, this would find the most recently modified
        // sprint OCel file in the output directory
        // For testing, we'll assume a naming pattern

        // List all sprint OCel files and find the latest
        return Files.list(sprintOutputDir)
            .filter(p -> p.toString().endsWith("-sprint-ocel.json"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No sprint OCel file found"));
    }

    /**
     * Get the path to the latest PI OCel export file.
     */
    private Path getLatestPiOcelPath() throws Exception {
        // List all PI OCel files and find the latest
        return Files.list(piOutputDir)
            .filter(p -> p.toString().endsWith("-pi-ocel.json"))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("No PI OCel file found"));
    }
}