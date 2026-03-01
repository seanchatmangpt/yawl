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
 * Tests end-to-end simulation behavior with a test double for ProcessMiningService.
 *
 * @author Test Specialist
 */
class GregverseSimulatorTest {

    /**
     * Test double: ProcessMiningService stub that always reports unhealthy.
     * Used to test simulator without requiring a real process mining service.
     */
    static class UnhealthyMiningService implements ProcessMiningService {
        @Override
        public boolean isHealthy() {
            return false;  // Always unhealthy, but analyzer still runs
        }

        @Override
        public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
            throw new IOException("Service unavailable (test double)");
        }

        @Override
        public String discoverDfg(String xesXml) throws IOException {
            throw new IOException("Service unavailable (test double)");
        }

        @Override
        public String discoverAlphaPpp(String xesXml) throws IOException {
            throw new IOException("Service unavailable (test double)");
        }

        @Override
        public String performanceAnalysis(String xesXml) throws IOException {
            throw new IOException("Service unavailable (test double)");
        }

        @Override
        public String xesToOcel(String xesXml) throws IOException {
            throw new IOException("Service unavailable (test double)");
        }
    }

    /**
     * Test double: ProcessMiningService stub that always reports healthy.
     * Returns mock JSON responses for analysis.
     */
    static class HealthyMiningService implements ProcessMiningService {
        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
            return """
                {
                  "fitness": 0.95,
                  "produced": 1000,
                  "consumed": 950,
                  "missing": 50,
                  "remaining": 0,
                  "deviatingCases": []
                }
                """;
        }

        @Override
        public String discoverDfg(String xesXml) throws IOException {
            return """
                {
                  "nodes": [
                    {"id": "a", "count": 100},
                    {"id": "b", "count": 95},
                    {"id": "c", "count": 90}
                  ],
                  "edges": [
                    {"source": "a", "target": "b", "count": 95},
                    {"source": "b", "target": "c", "count": 90}
                  ]
                }
                """;
        }

        @Override
        public String discoverAlphaPpp(String xesXml) throws IOException {
            return """
                <?xml version="1.0"?>
                <pnml>
                  <net id="net1">
                    <page id="page1">
                      <place id="p1"/><place id="p2"/>
                      <transition id="t1"/><transition id="t2"/>
                    </page>
                  </net>
                </pnml>
                """;
        }

        @Override
        public String performanceAnalysis(String xesXml) throws IOException {
            return """
                {
                  "traceCount": 100,
                  "avgFlowTimeMs": 3600000.0,
                  "throughputPerHour": 100.0,
                  "activityStats": {
                    "A": {"count": 100, "avgDurationMs": 600000},
                    "B": {"count": 95, "avgDurationMs": 900000},
                    "C": {"count": 90, "avgDurationMs": 1200000}
                  }
                }
                """;
        }

        @Override
        public String xesToOcel(String xesXml) throws IOException {
            return """
                {
                  "ocel:version": "2.0",
                  "ocel:objectTypes": ["order", "item"],
                  "ocel:events": []
                }
                """;
        }
    }

    /**
     * Test: constructor with null service throws exception.
     */
    @Test
    void constructor_nullService_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new GregverseSimulator(null)
        );
    }

    /**
     * Test: constructor with valid service succeeds.
     */
    @Test
    void constructor_validService_succeeds() {
        ProcessMiningService service = new UnhealthyMiningService();
        assertDoesNotThrow(() ->
            new GregverseSimulator(service)
        );
    }

    /**
     * Test: withDefaultClient creates simulator with WASM-based service.
     */
    @Test
    void withDefaultClient_returnsSimulator() throws IOException {
        GregverseSimulator simulator = GregverseSimulator.withDefaultClient();

        assertNotNull(simulator);
        simulator.close();  // Clean up resources
    }

    /**
     * Test: simulate with unhealthy process mining service still returns valid session.
     * The simulator should gracefully skip mining step when service is unhealthy.
     */
    @Test
    void simulate_unhealthyService_returnsValidSession() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new HealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate(null, 1, List.of("A"))
        );
    }

    /**
     * Test: simulate with empty specification ID throws exception.
     */
    @Test
    void simulate_emptySpecId_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("", 1, List.of("A"))
        );
    }

    /**
     * Test: simulate with zero case count throws exception.
     */
    @Test
    void simulate_zeroCaseCount_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 0, List.of("A"))
        );
    }

    /**
     * Test: simulate with negative case count throws exception.
     */
    @Test
    void simulate_negativeCaseCount_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", -5, List.of("A"))
        );
    }

    /**
     * Test: simulate with null activities list throws exception.
     */
    @Test
    void simulate_nullActivities_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 1, null)
        );
    }

    /**
     * Test: simulate with empty activities list throws exception.
     */
    @Test
    void simulate_emptyActivities_throws() {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        assertThrows(IllegalArgumentException.class, () ->
            simulator.simulate("spec-1", 1, List.of())
        );
    }

    /**
     * Test: simulate with single activity succeeds.
     */
    @Test
    void simulate_singleActivity_succeeds() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

        ProcessMiningSession session1 = simulator.simulate("spec-1", 2, List.of("A"));
        ProcessMiningSession session2 = simulator.simulate("spec-1", 2, List.of("A"));

        assertNotEquals(session1.sessionId(), session2.sessionId());
    }

    /**
     * Test: simulate preserves createdAt across different specifications.
     */
    @Test
    void simulate_differentSpecs_independentSessions() throws IOException {
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new UnhealthyMiningService());

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
        GregverseSimulator simulator = new GregverseSimulator(new HealthyMiningService());

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
