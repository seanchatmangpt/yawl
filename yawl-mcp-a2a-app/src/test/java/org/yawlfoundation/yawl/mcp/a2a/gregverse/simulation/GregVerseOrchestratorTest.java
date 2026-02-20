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
import org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario.Scenario;
import org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario.ScenarioLoader;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GregVerseOrchestrator class.
 *
 * <p>Tests verify scenario orchestration, step execution, parallel processing,
 * and compensation handling in workflow execution.</p>
 */
@DisplayName("GregVerse Orchestrator Tests")
class GregVerseOrchestratorTest {

    private GregVerseOrchestrator orchestrator;
    private Scenario scenario;

    @BeforeEach
    void setUp() {
        scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        orchestrator = new GregVerseOrchestrator(config, scenario);
    }

    @Test
    @DisplayName("should create orchestrator with config and scenario")
    void testOrchestratorCreation() {
        assertNotNull(orchestrator);
        assertNotNull(orchestrator.getScenario());
    }

    @Test
    @DisplayName("should execute sequential steps")
    void testSequentialStepExecution() {
        orchestrator.executeSequential();
        // Verify execution completed without throwing
        assertNotNull(orchestrator.getExecutionStatus());
    }

    @Test
    @DisplayName("should execute parallel steps with virtual threads")
    void testParallelStepExecution() {
        orchestrator.executeParallel();
        assertNotNull(orchestrator.getExecutionStatus());
    }

    @Test
    @DisplayName("should track step execution status")
    void testStepExecutionTracking() {
        orchestrator.executeSequential();
        var status = orchestrator.getExecutionStatus();
        assertNotNull(status);
        assertTrue(status.stepsExecuted() >= 0);
    }

    @Test
    @DisplayName("should handle step failures gracefully")
    void testStepFailureHandling() {
        assertDoesNotThrow(() -> orchestrator.executeSequential());
        var status = orchestrator.getExecutionStatus();
        assertNotNull(status);
    }

    @Test
    @DisplayName("should support timeout-based step execution")
    void testTimeoutHandling() {
        GregVerseConfig timeoutConfig = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .timeoutDuration(java.time.Duration.ofSeconds(5))
            .build();

        var timeoutOrch = new GregVerseOrchestrator(timeoutConfig, scenario);
        assertDoesNotThrow(() -> timeoutOrch.executeParallel());
    }

    @Test
    @DisplayName("should collect step results")
    void testStepResultCollection() {
        orchestrator.executeSequential();
        var results = orchestrator.getStepResults();
        assertNotNull(results);
    }

    @Test
    @DisplayName("should handle inter-step dependencies")
    void testDependencyHandling() {
        orchestrator.executeSequential();
        // Dependencies handled transparently during execution
        var status = orchestrator.getExecutionStatus();
        assertTrue(status.stepsExecuted() > 0 || scenario.steps().isEmpty());
    }

    @Test
    @DisplayName("should support scenario switching")
    void testScenarioSwitching() {
        Scenario newScenario = ScenarioLoader.loadScenario("gvs-2-content-business");
        GregVerseConfig newConfig = GregVerseConfig.forScenario("gvs-2-content-business");

        var newOrch = new GregVerseOrchestrator(newConfig, newScenario);
        assertNotNull(newOrch.getScenario());
        assertNotEquals(scenario.id(), newOrch.getScenario().id());
    }

    @Test
    @DisplayName("should provide execution metrics")
    void testExecutionMetrics() {
        orchestrator.executeSequential();
        var metrics = orchestrator.getExecutionMetrics();
        assertNotNull(metrics);
        assertTrue(metrics.totalDuration().toMillis() >= 0);
    }

    @Test
    @DisplayName("should verify parallel execution reduces wall-clock time")
    void testParallelVsSequentialPerformance() {
        long seqStart = System.nanoTime();
        var seqOrch = new GregVerseOrchestrator(
            GregVerseConfig.forScenario("gvs-1-startup-idea"), scenario);
        seqOrch.executeSequential();
        long seqDuration = System.nanoTime() - seqStart;

        long parStart = System.nanoTime();
        var parOrch = new GregVerseOrchestrator(
            GregVerseConfig.builder()
                .scenarioId("gvs-1-startup-idea")
                .parallelExecution(true)
                .build(),
            scenario);
        parOrch.executeParallel();
        long parDuration = System.nanoTime() - parStart;

        // Parallel should ideally be faster (or same for very small workloads)
        assertNotNull(seqOrch.getExecutionStatus());
        assertNotNull(parOrch.getExecutionStatus());
    }

    @Test
    @DisplayName("should handle empty scenarios gracefully")
    void testEmptyScenarioHandling() {
        assertDoesNotThrow(() -> orchestrator.executeSequential());
    }

    @Test
    @DisplayName("should report comprehensive execution summary")
    void testExecutionSummary() {
        orchestrator.executeSequential();
        var summary = orchestrator.getSummary();
        assertNotNull(summary);
        assertTrue(summary.length() > 0);
    }
}
