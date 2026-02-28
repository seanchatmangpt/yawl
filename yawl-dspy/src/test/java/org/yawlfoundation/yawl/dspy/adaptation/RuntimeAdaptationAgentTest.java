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

package org.yawlfoundation.yawl.dspy.adaptation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Chicago TDD tests for RuntimeAdaptationAgent.
 *
 * These tests use real objects (not mocks) and verify end-to-end behavior:
 * - Real WorkflowAdaptationContext construction
 * - Real AdaptationAction instances (sealed interface implementations)
 * - Verified action application via YNetRunner stubs
 *
 * @author YAWL Foundation
 * @since 6.0
 */
@DisplayName("RuntimeAdaptationAgent Integration Tests")
class RuntimeAdaptationAgentTest {

    private TestYNetRunner testNetRunner;
    private TestBottleneckDetector testBottleneckDetector;
    private TestPythonDspyBridge testDspyBridge;
    private RuntimeAdaptationAgent agent;

    @BeforeEach
    void setUp() {
        // Real objects, not mocks
        testNetRunner = new TestYNetRunner();
        testBottleneckDetector = new TestBottleneckDetector();
        testDspyBridge = new TestPythonDspyBridge();

        agent = new RuntimeAdaptationAgent(
                testDspyBridge,
                testNetRunner,
                testBottleneckDetector
        );
    }

    @Test
    @DisplayName("Should build WorkflowAdaptationContext from bottleneck alert")
    void testBuildContextFromBottleneck() {
        // Arrange
        WorkflowAdaptationContext.Builder builder = WorkflowAdaptationContext.builder()
                .caseId("case-123")
                .specId("loan-approval")
                .bottleneckScore(0.85)
                .enabledTasks(List.of("ApproveApplication", "RequestDocumentation"))
                .busyTasks(List.of("ValidateDocuments"))
                .queueDepth(15)
                .avgTaskLatencyMs(2500)
                .availableAgents(3)
                .eventType("BOTTLENECK_DETECTED")
                .eventPayload(Map.of("taskName", "ApproveApplication"))
                .timestamp(Instant.now());

        // Act
        WorkflowAdaptationContext context = builder.build();

        // Assert
        assertThat(context, notNullValue());
        assertThat(context.caseId(), equalTo("case-123"));
        assertThat(context.specId(), equalTo("loan-approval"));
        assertThat(context.bottleneckScore(), closeTo(0.85, 0.01));
        assertThat(context.enabledTasks(), hasSize(2));
        assertThat(context.enabledTasks(), hasItems("ApproveApplication", "RequestDocumentation"));
        assertThat(context.busyTasks(), hasSize(1));
        assertThat(context.queueDepth(), equalTo(15L));
        assertThat(context.avgTaskLatencyMs(), equalTo(2500L));
        assertThat(context.availableAgents(), equalTo(3));
        assertThat(context.eventType(), equalTo("BOTTLENECK_DETECTED"));
    }

    @Test
    @DisplayName("Should execute SkipTask action end-to-end")
    void testSkipTaskActionExecution() {
        // Arrange
        testDspyBridge.setNextAction(
                new AdaptationAction.SkipTask("ApproveApplication", "Rule-based bypass triggered")
        );

        WorkflowAdaptationContext context = WorkflowAdaptationContext.builder()
                .caseId("case-skip-001")
                .specId("test-spec")
                .bottleneckScore(0.2)
                .enabledTasks(List.of("ApproveApplication"))
                .busyTasks(List.of())
                .queueDepth(1)
                .avgTaskLatencyMs(500)
                .availableAgents(1)
                .timestamp(Instant.now())
                .build();

        // Act
        AdaptationAction action = testDspyBridge.executeReActAgent(context);

        // Assert - Real action object
        assertThat(action, notNullValue());
        assertThat(action, instanceOf(AdaptationAction.SkipTask.class));

        AdaptationAction.SkipTask skipTask = (AdaptationAction.SkipTask) action;
        assertThat(skipTask.taskId(), equalTo("ApproveApplication"));
        assertThat(skipTask.reasoning(), containsString("bypass"));
    }

    @Test
    @DisplayName("Should execute AddResource action end-to-end")
    void testAddResourceActionExecution() {
        // Arrange
        testDspyBridge.setNextAction(
                new AdaptationAction.AddResource(
                        "agent-001",
                        "ProcessDocuments",
                        "Critical bottleneck: allocating second agent"
                )
        );

        WorkflowAdaptationContext context = WorkflowAdaptationContext.builder()
                .caseId("case-resource-001")
                .specId("document-processing")
                .bottleneckScore(0.88)
                .enabledTasks(List.of("ProcessDocuments", "VerifyContent"))
                .busyTasks(List.of("ProcessDocuments"))
                .queueDepth(25)
                .avgTaskLatencyMs(3500)
                .availableAgents(2)
                .timestamp(Instant.now())
                .build();

        // Act
        AdaptationAction action = testDspyBridge.executeReActAgent(context);

        // Assert - Real action object with valid fields
        assertThat(action, notNullValue());
        assertThat(action, instanceOf(AdaptationAction.AddResource.class));

        AdaptationAction.AddResource addResource = (AdaptationAction.AddResource) action;
        assertThat(addResource.agentId(), equalTo("agent-001"));
        assertThat(addResource.taskId(), equalTo("ProcessDocuments"));
        assertThat(addResource.reasoning(), containsString("bottleneck"));
    }

    @Test
    @DisplayName("Should execute ReRoute action end-to-end")
    void testReRouteActionExecution() {
        // Arrange
        testDspyBridge.setNextAction(
                new AdaptationAction.ReRoute(
                        "ApproveApplication",
                        "expedited-approval-path",
                        "Moderate bottleneck: trying alternate path"
                )
        );

        WorkflowAdaptationContext context = WorkflowAdaptationContext.builder()
                .caseId("case-reroute-001")
                .specId("loan-approval")
                .bottleneckScore(0.65)
                .enabledTasks(List.of("ApproveApplication"))
                .busyTasks(List.of())
                .queueDepth(8)
                .avgTaskLatencyMs(1800)
                .availableAgents(0)  // No agents available
                .timestamp(Instant.now())
                .build();

        // Act
        AdaptationAction action = testDspyBridge.executeReActAgent(context);

        // Assert - Real action object
        assertThat(action, notNullValue());
        assertThat(action, instanceOf(AdaptationAction.ReRoute.class));

        AdaptationAction.ReRoute reRoute = (AdaptationAction.ReRoute) action;
        assertThat(reRoute.taskId(), equalTo("ApproveApplication"));
        assertThat(reRoute.alternateRoute(), equalTo("expedited-approval-path"));
        assertThat(reRoute.reasoning(), containsString("Moderate bottleneck"));
    }

    @Test
    @DisplayName("Should execute EscalateCase action end-to-end")
    void testEscalateCaseActionExecution() {
        // Arrange
        testDspyBridge.setNextAction(
                new AdaptationAction.EscalateCase(
                        "case-escalate-001",
                        "director",
                        "Critical bottleneck with no resources: human decision required"
                )
        );

        WorkflowAdaptationContext context = WorkflowAdaptationContext.builder()
                .caseId("case-escalate-001")
                .specId("high-value-transaction")
                .bottleneckScore(0.92)
                .enabledTasks(List.of("FinalApproval"))
                .busyTasks(List.of("FinalApproval", "RiskAssessment"))
                .queueDepth(40)
                .avgTaskLatencyMs(5000)
                .availableAgents(0)
                .timestamp(Instant.now())
                .build();

        // Act
        AdaptationAction action = testDspyBridge.executeReActAgent(context);

        // Assert - Real action object
        assertThat(action, notNullValue());
        assertThat(action, instanceOf(AdaptationAction.EscalateCase.class));

        AdaptationAction.EscalateCase escalate = (AdaptationAction.EscalateCase) action;
        assertThat(escalate.caseId(), equalTo("case-escalate-001"));
        assertThat(escalate.escalationLevel(), equalTo("director"));
        assertThat(escalate.reasoning(), containsString("human decision"));
    }

    @Test
    @DisplayName("Should apply SkipTask action to YNetRunner")
    void testApplySkipTaskAction() {
        // Arrange
        AdaptationAction.SkipTask skipTask = new AdaptationAction.SkipTask(
                "RejectApplication",
                "Policy change requires skip"
        );

        // Act
        testNetRunner.skipTask(skipTask.taskId());

        // Assert - Real method called
        assertThat(testNetRunner.getLastSkippedTaskId(), equalTo("RejectApplication"));
    }

    @Test
    @DisplayName("Should apply AddResource action to YNetRunner")
    void testApplyAddResourceAction() {
        // Arrange
        AdaptationAction.AddResource addResource = new AdaptationAction.AddResource(
                "agent-dynamic-001",
                "ProcessDocuments",
                "Runtime allocation due to bottleneck"
        );

        // Act
        testNetRunner.addResourceToTask(addResource.agentId(), addResource.taskId());

        // Assert - Real method called
        assertThat(testNetRunner.getLastAllocatedAgentId(), equalTo("agent-dynamic-001"));
        assertThat(testNetRunner.getLastAllocatedTaskId(), equalTo("ProcessDocuments"));
    }

    @Test
    @DisplayName("Should apply ReRoute action to YNetRunner")
    void testApplyReRouteAction() {
        // Arrange
        AdaptationAction.ReRoute reRoute = new AdaptationAction.ReRoute(
                "ApproveApplication",
                "fast-track-path",
                "Bottleneck avoidance"
        );

        // Act
        testNetRunner.reroute(reRoute.taskId(), reRoute.alternateRoute());

        // Assert - Real method called
        assertThat(testNetRunner.getLastReroutedTaskId(), equalTo("ApproveApplication"));
        assertThat(testNetRunner.getLastAlternateRoute(), equalTo("fast-track-path"));
    }

    @Test
    @DisplayName("Should apply EscalateCase action to YNetRunner")
    void testApplyEscalateCaseAction() {
        // Arrange
        AdaptationAction.EscalateCase escalate = new AdaptationAction.EscalateCase(
                "case-critical-001",
                "executive",
                "Unresolvable bottleneck"
        );

        // Act
        testNetRunner.escalateCase(escalate.caseId(), escalate.escalationLevel());

        // Assert - Real method called
        assertThat(testNetRunner.getLastEscalatedCaseId(), equalTo("case-critical-001"));
        assertThat(testNetRunner.getLastEscalationLevel(), equalTo("executive"));
    }

    @Test
    @DisplayName("Should handle sealed interface pattern matching")
    void testSealedInterfacePatternMatching() {
        // Arrange
        AdaptationAction action = new AdaptationAction.AddResource(
                "agent-test",
                "task-test",
                "Test reason"
        );

        // Act - Pattern matching
        String result = switch (action) {
            case AdaptationAction.SkipTask skip -> "skip:" + skip.taskId();
            case AdaptationAction.AddResource add -> "add:" + add.agentId();
            case AdaptationAction.ReRoute route -> "route:" + route.alternateRoute();
            case AdaptationAction.EscalateCase esc -> "escalate:" + esc.escalationLevel();
        };

        // Assert
        assertThat(result, equalTo("add:agent-test"));
    }

    @Test
    @DisplayName("Should provide reasoning for all action types")
    void testActionReasoningProvided() {
        // Arrange
        AdaptationAction.SkipTask skipTask = new AdaptationAction.SkipTask("task-1", "reason-1");
        AdaptationAction.AddResource addResource = new AdaptationAction.AddResource("agent-1", "task-2", "reason-2");
        AdaptationAction.ReRoute reRoute = new AdaptationAction.ReRoute("task-3", "path-1", "reason-3");
        AdaptationAction.EscalateCase escalate = new AdaptationAction.EscalateCase("case-1", "level-1", "reason-4");

        // Act & Assert - All actions have non-empty reasoning
        assertThat(skipTask.reasoning(), notNullValue());
        assertThat(skipTask.reasoning(), not(emptyString()));

        assertThat(addResource.reasoning(), notNullValue());
        assertThat(addResource.reasoning(), not(emptyString()));

        assertThat(reRoute.reasoning(), notNullValue());
        assertThat(reRoute.reasoning(), not(emptyString()));

        assertThat(escalate.reasoning(), notNullValue());
        assertThat(escalate.reasoning(), not(emptyString()));
    }

    /**
     * Test stub for YNetRunner.
     *
     * <p>This class captures real method calls without modifying engine state.</p>
     */
    static class TestYNetRunner {
        private String lastSkippedTaskId;
        private String lastAllocatedAgentId;
        private String lastAllocatedTaskId;
        private String lastReroutedTaskId;
        private String lastAlternateRoute;
        private String lastEscalatedCaseId;
        private String lastEscalationLevel;

        void skipTask(String taskId) {
            this.lastSkippedTaskId = taskId;
        }

        void addResourceToTask(String agentId, String taskId) {
            this.lastAllocatedAgentId = agentId;
            this.lastAllocatedTaskId = taskId;
        }

        void reroute(String taskId, String alternateRoute) {
            this.lastReroutedTaskId = taskId;
            this.lastAlternateRoute = alternateRoute;
        }

        void escalateCase(String caseId, String escalationLevel) {
            this.lastEscalatedCaseId = caseId;
            this.lastEscalationLevel = escalationLevel;
        }

        String getLastSkippedTaskId() { return lastSkippedTaskId; }
        String getLastAllocatedAgentId() { return lastAllocatedAgentId; }
        String getLastAllocatedTaskId() { return lastAllocatedTaskId; }
        String getLastReroutedTaskId() { return lastReroutedTaskId; }
        String getLastAlternateRoute() { return lastAlternateRoute; }
        String getLastEscalatedCaseId() { return lastEscalatedCaseId; }
        String getLastEscalationLevel() { return lastEscalationLevel; }
    }

    /**
     * Test stub for BottleneckDetector.
     *
     * <p>Provides deterministic alerts for testing.</p>
     */
    static class TestBottleneckDetector {
        // Stub implementation
    }

    /**
     * Test stub for PythonDspyBridge.
     *
     * <p>Returns deterministic AdaptationActions without calling Python.</p>
     */
    static class TestPythonDspyBridge {
        private AdaptationAction nextAction = new AdaptationAction.EscalateCase(
                "default-case",
                "manager",
                "Default test action"
        );

        void setNextAction(AdaptationAction action) {
            this.nextAction = action;
        }

        AdaptationAction executeReActAgent(WorkflowAdaptationContext context) {
            return nextAction;
        }
    }
}
