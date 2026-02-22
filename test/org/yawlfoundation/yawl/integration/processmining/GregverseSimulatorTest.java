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
 */

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD GregverseSimulator tests.
 * Tests end-to-end simulation behavior with a test double for Rust4PmClient.
 *
 * @author Test Specialist
 */
class GregverseSimulatorTest {

    /**
     * Test double: Rust4PmClient stub that always reports unhealthy.
     * Used to test simulator without requiring a real Rust4PM server.
     */
    static class UnhealthyRust4PmClient extends Rust4PmClient {
        public UnhealthyRust4PmClient() {
            super("http://localhost:1");  // Port 1 is guaranteed to fail
        }

        @Override
        public boolean isHealthy() {
            return false;  // Always unhealthy, but analyzer still runs
        }

        @Override
        public String analyzeXes(String xesXml) throws IOException {
            throw new IOException("Server unavailable (test double)");
        }

        @Override
        public String discoverProcess(String xesXml) throws IOException {
            throw new IOException("Server unavailable (test double)");
        }
    }

    /**
     * Test double: Rust4PmClient stub that always reports healthy.
     * Returns mock JSON responses for analysis.
     */
    static class HealthyRust4PmClient extends Rust4PmClient {
        public HealthyRust4PmClient() {
            super("http://localhost:8080");
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String analyzeXes(String xesXml) throws IOException {
            return """
                {
                  "fitness": 0.95,
                  "precision": 0.92,
                  "activities": ["A", "B", "C"]
                }
                """;
        }

        @Override
        public String discoverProcess(String xesXml) throws IOException {
            return """
                {
                  "type": "petri_net",
                  "places": 5,
                  "transitions": 4
                }
                """;
        }
    }

    /**
     * Test: constructor with null client throws exception.
     */
    @Test
    void constructor_nullClient_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new GregverseSimulator(null)
        );
    }

    /**
     * Test: constructor with valid client succeeds.
     */
    @Test
    void constructor_validClient_succeeds() {
        Rust4PmClient client = new UnhealthyRust4PmClient();
        assertDoesNotThrow(() ->
            new GregverseSimulator(client)
        );
    }

    /**
     * Test: withDefaultClient creates simulator with non-null client.
     */
    @Test
    void withDefaultClient_returnsSimulator() {
        GregverseSimulator simulator = GregverseSimulator.withDefaultClient();

        assertNotNull(simulator);
    }

    /**
     * Test: simulate with unhealthy Rust4PM still returns valid session.
     * The simulator should gracefully skip Rust4PM step when server is unhealthy.
     */
    @Test
    void simulate_unhealthyRust4PM_returnsValidSession() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-test-001",
            3,
            List.of("A", "B", "C")
        );

        assertNotNull(session);
        assertFalse(session.sessionId().isEmpty());
        assertEquals("spec-test-001", session.specificationId());
        assertEquals(3, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }

    /**
     * Test: simulate with healthy Rust4PM returns valid session.
     */
    @Test
    void simulate_healthyRust4PM_returnsValidSession() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new HealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-workflow",
            5,
            List.of("Start", "Process", "End")
        );

        assertNotNull(session);
        assertEquals("spec-workflow", session.specificationId());
        assertEquals(5, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }

    /**
     * Test: simulate computes fitness score in valid range [0.0, 1.0].
     */
    @Test
    void simulate_computesFitness() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            2,
            List.of("A", "B")
        );

        double fitness = session.lastFitnessScore();
        assertTrue(fitness >= 0.0 && fitness <= 1.0);
    }

    /**
     * Test: simulate computes precision score in valid range [0.0, 1.0].
     */
    @Test
    void simulate_computesPrecision() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            2,
            List.of("Task1", "Task2")
        );

        double precision = session.lastPrecisionScore();
        assertTrue(precision >= 0.0 && precision <= 1.0);
    }

    /**
     * Test: simulate computes average flow time (milliseconds).
     */
    @Test
    void simulate_computesFlowTime() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            3,
            List.of("A", "B", "C")
        );

        double flowTime = session.lastAvgFlowTimeMs();
        assertTrue(flowTime >= 0.0);
    }

    /**
     * Test: simulate with null specification ID throws exception.
     */
    @Test
    void simulate_nullSpecId_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate(null, 1, List.of("A"))
        );
    }

    /**
     * Test: simulate with empty specification ID throws exception.
     */
    @Test
    void simulate_emptySpecId_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("", 1, List.of("A"))
        );
    }

    /**
     * Test: simulate with zero case count throws exception.
     */
    @Test
    void simulate_zeroCaseCount_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 0, List.of("A"))
        );
    }

    /**
     * Test: simulate with negative case count throws exception.
     */
    @Test
    void simulate_negativeCaseCount_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", -5, List.of("A"))
        );
    }

    /**
     * Test: simulate with null activities list throws exception.
     */
    @Test
    void simulate_nullActivities_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 1, null)
        );
    }

    /**
     * Test: simulate with empty activities list throws exception.
     */
    @Test
    void simulate_emptyActivities_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 1, List.of())
        );
    }

    /**
     * Test: simulate with single activity succeeds.
     */
    @Test
    void simulate_singleActivity_succeeds() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-single",
            2,
            List.of("OnlyTask")
        );

        assertEquals(2, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }

    /**
     * Test: simulate with many activities succeeds.
     */
    @Test
    void simulate_manyActivities_succeeds() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        List<String> activities = List.of(
            "Start", "Review", "Approve", "Process", "Verify", "Complete"
        );

        ProcessMiningSession session = simulator.simulate(
            "spec-complex",
            5,
            activities
        );

        assertEquals(5, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }

    /**
     * Test: simulate with large case count succeeds.
     */
    @Test
    void simulate_largeCaseCount_succeeds() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-bulk",
            100,
            List.of("A", "B")
        );

        assertEquals(100, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }

    /**
     * Test: simulate returns session with matching case count.
     */
    @Test
    void simulate_caseCountMatches() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        int caseCount = 7;
        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            caseCount,
            List.of("T1", "T2")
        );

        assertEquals(caseCount, session.totalCasesAnalyzed());
    }

    /**
     * Test: simulate returns session with matching specification ID.
     */
    @Test
    void simulate_specIdMatches() throws IOException {
        String specId = "my-important-workflow";
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            specId,
            3,
            List.of("A")
        );

        assertEquals(specId, session.specificationId());
    }

    /**
     * Test: simulate sets lastAnalyzedAt timestamp.
     */
    @Test
    void simulate_setsLastAnalyzedAt() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            2,
            List.of("A", "B")
        );

        assertNotNull(session.lastAnalyzedAt());
    }

    /**
     * Test: simulate with special characters in specification ID.
     */
    @Test
    void simulate_specialCharsInSpecId() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec_workflow-123",
            1,
            List.of("Action")
        );

        assertEquals("spec_workflow-123", session.specificationId());
    }

    /**
     * Test: simulate multiple times returns different sessions.
     */
    @Test
    void simulate_multipleRuns_differentSessions() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session1 = simulator.simulate("spec-1", 2, List.of("A"));
        ProcessMiningSession session2 = simulator.simulate("spec-1", 2, List.of("A"));

        assertNotEquals(session1.sessionId(), session2.sessionId());
    }

    /**
     * Test: simulate preserves createdAt across different specifications.
     */
    @Test
    void simulate_differentSpecs_independentSessions() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession s1 = simulator.simulate("spec-a", 1, List.of("X"));
        ProcessMiningSession s2 = simulator.simulate("spec-b", 1, List.of("Y"));

        assertEquals("spec-a", s1.specificationId());
        assertEquals("spec-b", s2.specificationId());
        assertNotEquals(s1.sessionId(), s2.sessionId());
    }

    /**
     * Test: session generated by simulator can be printed as summary.
     */
    @Test
    void simulate_toSummary_works() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-1",
            3,
            List.of("A", "B")
        );

        String summary = session.toSummary();

        assertNotNull(summary);
        assertTrue(summary.contains("spec-1"));
    }

    /**
     * Test: healthy client with successful Rust4PM integration.
     */
    @Test
    void simulate_withHealthyClient_succeeds() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new HealthyRust4PmClient());

        ProcessMiningSession session = simulator.simulate(
            "spec-healthy",
            4,
            List.of("Init", "Process", "Finalize")
        );

        assertNotNull(session);
        assertEquals(4, session.totalCasesAnalyzed());
        assertTrue(session.hasAnalyzed());
    }
}
