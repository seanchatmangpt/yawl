package org.yawlfoundation.yawl.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.engine.YEngine;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test YawlSimulator OCEL 2.0 generation capabilities.
 */
class YawlSimulatorTest {

    private YawlSimulator simulator;

    @BeforeEach
    void setUp() {
        // Create simulator with real YAWL engine
        simulator = YawlSimulator.getInstance();
    }

    /**
     * Test that sprint simulation generates valid OCEL 2.0 file.
     */
    @Test
    void testSprintSimulationGeneratesOcel2() throws Exception {
        // Run sprint simulation
        String ocelPath = simulator.runSprint(1, "TestFeature", "TeamAlpha");

        // Verify file exists
        Path path = Paths.get(ocelPath);
        assertTrue(Files.exists(path), "OCEL file should exist at: " + ocelPath);

        // Verify file contains OCEL 2.0 content
        String content = Files.readString(path);
        assertTrue(content.contains("\"ocel:version\":\"2.0\""), "File should be OCEL 2.0 format");
        assertTrue(content.contains("\"ocel:events\":"), "File should contain events section");
        assertTrue(content.contains("\"ocel:objects\":"), "File should contain objects section");

        // Verify we have sprint-specific activities
        assertTrue(content.contains("sprint_started"), "Should have sprint start event");
        assertTrue(content.contains("sprint_completed"), "Should have sprint end event");

        System.out.println("Sprint OCEL file created at: " + ocelPath);
    }

    /**
     * Test that PI simulation generates valid OCEL 2.0 file with multiple events.
     */
    @Test
    void testPISimulationGeneratesOcel2WithMultipleEvents() throws Exception {
        // Run PI simulation
        String ocelPath = simulator.runPI(1);

        // Verify file exists
        Path path = Paths.get(ocelPath);
        assertTrue(Files.exists(path), "PI OCEL file should exist at: " + ocelPath);

        // Verify file content
        String content = Files.readString(path);
        assertTrue(content.contains("\"ocel:version\":\"2.0\""), "File should be OCEL 2.0 format");

        // Count events (should have multiple sprints + PI events)
        long eventCount = content.split("\"ocel:activity\"").length - 1;
        assertTrue(eventCount >= 10, "PI simulation should generate at least 10 events, got: " + eventCount);

        System.out.println("PI OCEL file created at: " + ocelPath);
        System.out.println("Total events: " + eventCount);
    }

    /**
     * Test portfolio sync simulation.
     */
    @Test
    void testPortfolioSyncGeneratesOcel2() throws Exception {
        // Run portfolio sync
        String ocelPath = simulator.runPortfolioSync();

        // Verify file exists
        Path path = Paths.get(ocelPath);
        assertTrue(Files.exists(path), "Portfolio sync OCEL file should exist at: " + ocelPath);

        // Verify file content
        String content = Files.readString(path);
        assertTrue(content.contains("\"ocel:version\":\"2.0\""), "File should be OCEL 2.0 format");
        assertTrue(content.contains("portfolio_sync"), "Should contain portfolio sync events");

        System.out.println("Portfolio sync OCEL file created at: " + ocelPath);
    }

    /**
     * Test self-assessment simulation.
     */
    @Test
    void testSelfAssessmentGeneratesOcel2() throws Exception {
        // Run self-assessment
        String ocelPath = simulator.runSelfAssessment();

        // Verify file exists
        Path path = Paths.get(ocelPath);
        assertTrue(Files.exists(path), "Self-assessment OCEL file should exist at: " + ocelPath);

        // Verify file content
        String content = Files.readString(path);
        assertTrue(content.contains("\"ocel:version\":\"2.0\""), "File should be OCEL 2.0 format");
        assertTrue(content.contains("assessment_started"), "Should have assessment start event");
        assertTrue(content.contains("gap_discovered"), "Should have gap discovery events");
        assertTrue(content.contains("conformance_updated"), "Should have conformance update");

        System.out.println("Self-assessment OCEL file created at: " + ocelPath);
    }

    /**
     * Test that all simulation types produce valid OCEL files in sim-output.
     */
    @Test
    void testAllSimulationsGenerateValidOcelFiles() throws Exception {
        // Generate all simulation types
        String sprintPath = simulator.runSprint(2, "FeatureB", "TeamBeta");
        String piPath = simulator.runPI(2);
        String portfolioPath = simulator.runPortfolioSync();
        String assessmentPath = simulator.runSelfAssessment();

        // Verify all files exist and are valid
        validateOcelFile(sprintPath, "Sprint");
        validateOcelFile(piPath, "PI");
        validateOcelFile(portfolioPath, "Portfolio");
        validateOcelFile(assessmentPath, "Self-Assessment");

        // Verify files are in sim-output directory
        assertTrue(sprintPath.contains("/sim-output/"), "Sprint OCEL should be in sim-output");
        assertTrue(piPath.contains("/sim-output/"), "PI OCEL should be in sim-output");
        assertTrue(portfolioPath.contains("/sim-output/"), "Portfolio OCEL should be in sim-output");
        assertTrue(assessmentPath.contains("/sim-output/"), "Self-assessment OCEL should be in sim-output");
    }

    /**
     * Helper to validate OCEL file structure.
     */
    private void validateOcelFile(String path, String type) throws Exception {
        Path file = Paths.get(path);
        assertTrue(Files.exists(file), type + " OCEL file should exist: " + path);

        String content = Files.readString(file);
        assertTrue(content.contains("\"ocel:version\":\"2.0\""),
                   type + " file should be OCEL 2.0 format");
        assertTrue(content.contains("\"ocel:events\":"),
                   type + " file should have events section");
        assertTrue(content.contains("\"ocel:objects\":"),
                   type + " file should have objects section");

        System.out.println(type + " OCEL validation passed: " + path);
    }
}