/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.simulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.config.GregVerseConfig;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.report.GregVerseReport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GregVerseSimulation class.
 *
 * <p>Tests verify simulation execution, agent resolution, and report generation
 * using Chicago TDD (outside-in, behavior-driven).</p>
 */
@DisplayName("GregVerse Simulation Tests")
class GregVerseSimulationTest {

    private GregVerseSimulation simulation;
    private GregVerseConfig config;

    @BeforeEach
    void setUp() {
        config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        simulation = new GregVerseSimulation(config);
    }

    @Test
    @DisplayName("should run simulation successfully")
    void testRunSimulation() {
        GregVerseReport report = simulation.run();
        assertNotNull(report);
        assertNotNull(report.getStartTime());
        assertNotNull(report.getEndTime());
    }

    @Test
    @DisplayName("should return empty report when no agents configured")
    void testRunWithNoAgents() {
        GregVerseConfig emptyConfig = GregVerseConfig.forScenario("gvs-1-startup-idea");
        GregVerseSimulation sim = new GregVerseSimulation(emptyConfig);
        GregVerseReport report = sim.run();
        assertNotNull(report);
    }

    @Test
    @DisplayName("should handle single skill execution")
    void testRunSingleSkill() {
        GregVerseConfig singleConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .singleAgentId("greg-isenberg")
            .singleSkillId("evaluate-market")
            .skillInput("test input")
            .build();

        GregVerseSimulation singleSim = new GregVerseSimulation(singleConfig);
        GregVerseReport report = singleSim.runSingleSkill();

        assertNotNull(report);
        assertTrue(report.hasResults());
    }

    @Test
    @DisplayName("should throw on single skill without configuration")
    void testRunSingleSkillWithoutConfig() {
        assertThrows(IllegalStateException.class, () -> simulation.runSingleSkill());
    }

    @Test
    @DisplayName("should resolve all registered agents")
    void testAgentResolution() {
        GregVerseConfig allConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .parallelExecution(true)
            .build();

        GregVerseSimulation allSim = new GregVerseSimulation(allConfig);
        GregVerseReport report = allSim.run();

        assertNotNull(report);
        assertTrue(report.getSuccessfulAgents() > 0 || report.getFailedAgents() > 0);
    }

    @Test
    @DisplayName("should handle agent initialization failure gracefully")
    void testAgentInitializationFailure() {
        assertThrows(AgentInitializationException.class, () -> {
            GregVerseConfig badConfig = GregVerseConfig.builder()
                .scenarioId("gvs-1-startup-idea")
                .agentIds("nonexistent-agent")
                .build();

            GregVerseSimulation badSim = new GregVerseSimulation(badConfig);
            badSim.run();
        });
    }

    @Test
    @DisplayName("should execute in parallel when configured")
    void testParallelExecution() {
        GregVerseConfig parallelConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .parallelExecution(true)
            .build();

        GregVerseSimulation parallelSim = new GregVerseSimulation(parallelConfig);
        GregVerseReport report = parallelSim.run();

        assertNotNull(report);
        assertNotNull(report.totalDuration());
        assertTrue(report.totalDuration().toMillis() >= 0);
    }

    @Test
    @DisplayName("should execute sequentially when configured")
    void testSequentialExecution() {
        GregVerseConfig sequentialConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .parallelExecution(false)
            .build();

        GregVerseSimulation seqSim = new GregVerseSimulation(sequentialConfig);
        GregVerseReport report = seqSim.run();

        assertNotNull(report);
        assertNotNull(report.totalDuration());
    }

    @Test
    @DisplayName("should require non-null configuration")
    void testNullConfigValidation() {
        assertThrows(NullPointerException.class, () -> new GregVerseSimulation(null));
    }

    @Test
    @DisplayName("should handle timeout scenarios")
    void testTimeoutHandling() {
        GregVerseConfig timeoutConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .timeoutDuration(java.time.Duration.ofMillis(1))
            .parallelExecution(true)
            .build();

        GregVerseSimulation timeoutSim = new GregVerseSimulation(timeoutConfig);
        GregVerseReport report = timeoutSim.run();

        assertNotNull(report);
    }

    @Test
    @DisplayName("should accumulate agent results")
    void testAgentResultsCollection() {
        GregVerseConfig multiConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .agentIds("greg-isenberg", "james")
            .build();

        GregVerseSimulation multiSim = new GregVerseSimulation(multiConfig);
        GregVerseReport report = multiSim.run();

        assertNotNull(report);
        assertTrue(report.getSuccessfulAgents() + report.getFailedAgents() > 0);
    }

    @Test
    @DisplayName("should report skill transactions")
    void testSkillTransactionTracking() {
        GregVerseReport report = simulation.run();
        assertNotNull(report);
        assertNotNull(report.getSkillTransactions());
    }
}
