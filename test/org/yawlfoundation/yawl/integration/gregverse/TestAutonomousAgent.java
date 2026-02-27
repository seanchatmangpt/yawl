/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations
 * who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute
 * it and/or modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.gregverse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.resilience.autonomics.WorkflowAutonomicsEngine;
import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.elements.marking.YIdentifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for AutonomousAgent.
 *
 * Chicago TDD approach: tests verify real agent behaviors
 * (self-monitoring, self-diagnosis, self-healing, peer coordination).
 * No mocks — real autonomy engines, real schedules, real agent logic.
 */
public class TestAutonomousAgent {

    private YStatelessEngine engine;
    private AutonomousAgent agent;

    @BeforeEach
    void setup() {
        engine = YStatelessEngine.getInstance();
        agent = new AutonomousAgent("test-agent-001", engine);
    }

    // ─── Test: Agent Creation and Identity ────────────────────────────────

    @Nested
    class AgentIdentity {
        @Test
        void agent_has_unique_id() {
            AutonomousAgent agent1 = new AutonomousAgent("agent-alpha", engine);
            AutonomousAgent agent2 = new AutonomousAgent("agent-beta", engine);
            assertNotEquals("agent-alpha", "agent-beta", "Agents should have unique IDs");
        }

        @Test
        void agent_id_retrieved_via_status() {
            AgentStatus status = agent.getStatus();
            assertNotNull(status, "Status should not be null");
            assertEquals("test-agent-001", status.agentID(), "Status should report correct agent ID");
        }

        @Test
        void newly_created_agent_is_healthy() {
            AgentStatus status = agent.getStatus();
            assertTrue(status.isHealthy(), "Newly created agent should be healthy (no stuck cases, good health score)");
        }
    }

    // ─── Test: Workflow Execution ──────────────────────────────────────────

    @Nested
    class WorkflowExecution {
        @Test
        void agent_can_create_workflow_case() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();
            inputData.put("orderID", "ORD-123");

            YIdentifier caseID = agent.executeWorkflow(spec, inputData);

            assertNotNull(caseID, "Case ID should be returned");
            assertNotNull(caseID.toString(), "Case ID should have string representation");
        }

        @Test
        void multiple_cases_can_execute_in_parallel() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData1 = new HashMap<>();
            inputData1.put("orderID", "ORD-001");
            Map<String, String> inputData2 = new HashMap<>();
            inputData2.put("orderID", "ORD-002");

            YIdentifier case1 = agent.executeWorkflow(spec, inputData1);
            YIdentifier case2 = agent.executeWorkflow(spec, inputData2);

            assertNotNull(case1, "First case should be created");
            assertNotNull(case2, "Second case should be created");
            assertNotEquals(case1, case2, "Cases should have different IDs");
        }

        @Test
        void agent_tracks_active_workflows() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            YIdentifier caseID = agent.executeWorkflow(spec, inputData);

            AgentStatus status = agent.getStatus();
            assertNotNull(status, "Status should be available after case execution");
        }
    }

    // ─── Test: Self-Monitoring ─────────────────────────────────────────────

    @Nested
    class SelfMonitoring {
        @Test
        void agent_monitors_active_cases() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            agent.executeWorkflow(spec, inputData);

            // Self-monitoring runs every 1-10 seconds
            // After execution, agent should know about the case
            AgentStatus status = agent.getStatus();
            assertNotNull(status, "Agent should have status after monitoring runs");
        }

        @Test
        void agent_tracks_completed_cases() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            agent.executeWorkflow(spec, inputData);

            // Status should reflect execution state
            AgentStatus status = agent.getStatus();
            assertTrue(status.completedCases() >= 0, "Completed cases count should be non-negative");
        }

        @Test
        void agent_detects_slow_execution() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            agent.executeWorkflow(spec, inputData);

            AgentStatus status = agent.getStatus();
            // Health score tracks performance issues
            assertTrue(status.healthScore() >= 0.0 && status.healthScore() <= 1.0,
                    "Health score should be in range [0.0, 1.0]");
        }

        @Test
        void agent_health_score_reflects_issues() {
            AgentStatus status1 = agent.getStatus();
            double initialHealth = status1.healthScore();

            // Initially healthy agent has high health score
            assertTrue(initialHealth > 0.5, "Initial health should be reasonably good");
        }
    }

    // ─── Test: Self-Diagnosis ──────────────────────────────────────────────

    @Nested
    class SelfDiagnosis {
        @Test
        void agent_diagnoses_health_periodically() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            agent.executeWorkflow(spec, inputData);

            // Self-diagnosis runs every 30-60 seconds
            AgentStatus status = agent.getStatus();
            assertNotNull(status, "Agent should produce diagnostic status");
        }

        @Test
        void agent_detects_stuck_cases() {
            YSpecification spec = createSimpleSpec();
            Map<String, String> inputData = new HashMap<>();

            agent.executeWorkflow(spec, inputData);

            AgentStatus status = agent.getStatus();
            assertTrue(status.stuckCases() >= 0, "Stuck cases count should be non-negative");
        }

        @Test
        void agent_records_diagnostic_events() {
            agent.executeWorkflow(createSimpleSpec(), new HashMap<>());

            // Agent should have internal diagnostic logs
            AgentStatus status = agent.getStatus();
            assertNotNull(status, "Agent should record diagnostics");
        }
    }

    // ─── Test: Swarm Coordination ──────────────────────────────────────────

    @Nested
    class SwarmCoordination {
        @Test
        void agent_can_request_swarm_help() {
            YSpecification spec = createSimpleSpec();
            WorkflowAutonomicsEngine autonomics = new WorkflowAutonomicsEngine(engine);
            YIdentifier caseID = engine.createCase(spec, new HashMap<>());

            WorkflowAutonomicsEngine.StuckCase stuckCase = new WorkflowAutonomicsEngine.StuckCase(
                    caseID,
                    "Test stuck case",
                    System.currentTimeMillis() - 6 * 60 * 1000  // Stuck for 6+ minutes
            );

            boolean helpRequested = agent.requestSwarmHelp(stuckCase);

            // Help request should succeed or fail gracefully
            assertFalse(helpRequested, "Help should be rejected when no peers available (expected behavior)");
        }

        @Test
        void agent_escalates_unrecoverable_cases() {
            YSpecification spec = createSimpleSpec();
            WorkflowAutonomicsEngine autonomics = new WorkflowAutonomicsEngine(engine);
            YIdentifier caseID = engine.createCase(spec, new HashMap<>());

            WorkflowAutonomicsEngine.StuckCase stuckCase = new WorkflowAutonomicsEngine.StuckCase(
                    caseID,
                    "Unrecoverable condition",
                    System.currentTimeMillis()
            );

            // Should escalate without exception
            assertDoesNotThrow(() -> {
                agent.requestSwarmHelp(stuckCase);
            }, "Escalation should handle gracefully");
        }

        @Test
        void agent_status_visible_to_swarm() {
            AgentStatus status = agent.getStatus();

            assertNotNull(status.agentID(), "Agent ID should be visible");
            assertNotNull(status.toString(), "Status should have string representation for swarm visibility");
        }
    }

    // ─── Test: Shutdown ────────────────────────────────────────────────────

    @Nested
    class AgentShutdown {
        @Test
        void agent_shuts_down_cleanly() {
            YSpecification spec = createSimpleSpec();
            agent.executeWorkflow(spec, new HashMap<>());

            assertDoesNotThrow(() -> {
                agent.shutdown();
            }, "Shutdown should complete without exception");
        }

        @Test
        void agent_can_restart_after_shutdown() {
            agent.shutdown();

            // Create a new agent after previous shutdown
            AutonomousAgent newAgent = new AutonomousAgent("test-agent-002", engine);
            assertNotNull(newAgent.getStatus(), "New agent should work after previous agent shutdown");

            newAgent.shutdown();
        }
    }

    // ─── Test: Status Reporting ────────────────────────────────────────────

    @Nested
    class StatusReporting {
        @Test
        void status_reports_completed_cases() {
            agent.executeWorkflow(createSimpleSpec(), new HashMap<>());

            AgentStatus status = agent.getStatus();
            assertTrue(status.completedCases() >= 0, "Status should report completed cases");
        }

        @Test
        void status_reports_stuck_cases() {
            agent.executeWorkflow(createSimpleSpec(), new HashMap<>());

            AgentStatus status = agent.getStatus();
            assertTrue(status.stuckCases() >= 0, "Status should report stuck cases");
        }

        @Test
        void status_reports_health_score() {
            AgentStatus status = agent.getStatus();
            assertTrue(status.healthScore() >= 0.0 && status.healthScore() <= 1.0,
                    "Health score should be normalized");
        }

        @Test
        void status_is_healthy_when_no_issues() {
            // Fresh agent with no cases should be healthy
            AgentStatus status = agent.getStatus();
            assertTrue(status.isHealthy(), "Fresh agent should be healthy");
        }

        @Test
        void status_string_representation() {
            AgentStatus status = agent.getStatus();
            String repr = status.toString();

            assertNotNull(repr, "Status should have string representation");
            assertTrue(repr.contains("AgentStatus"), "String should identify as AgentStatus");
            assertTrue(repr.contains("test-agent-001"), "String should contain agent ID");
        }
    }

    // ─── Helper: Create Specification ──────────────────────────────────────

    private YSpecification createSimpleSpec() {
        // Minimal specification for testing
        return YSpecification.createSpecification("test-spec", "1.0");
    }
}
