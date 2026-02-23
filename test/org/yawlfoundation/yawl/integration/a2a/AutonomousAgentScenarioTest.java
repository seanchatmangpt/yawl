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

package org.yawlfoundation.yawl.integration.a2a;

import junit.framework.TestCase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Autonomous agent scenario tests.
 *
 * Chicago TDD: Tests realistic autonomous agent scenarios using the A2A protocol.
 * Tests simulate autonomous agents that can discover workflows, make decisions,
 * and execute tasks without human intervention.
 *
 * Coverage targets:
 * - Autonomous workflow discovery and selection
 * - Decision-making based on workflow state
 * - Error recovery and retry logic
 * - Multi-agent coordination
 * - Performance under load
 * - Resource management
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-22
 */
public class AutonomousAgentScenarioTest extends TestCase {

    private static final int TEST_PORT = 19885;
    private YawlA2AServer server;
    private AutonomousAgent agent1;
    private AutonomousAgent agent2;

    public AutonomousAgentScenarioTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        // Start A2A server
        server = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT,
            createTestAuthenticationProvider());
        server.start();
        Thread.sleep(200); // allow server to start

        // Create autonomous agents
        agent1 = new AutonomousAgent("agent1", "http://localhost:" + TEST_PORT);
        agent2 = new AutonomousAgent("agent2", "http://localhost:" + TEST_PORT);
    }

    @Override
    protected void tearDown() throws Exception {
        if (agent1 != null) {
            agent1.stop();
        }
        if (agent2 != null) {
            agent2.stop();
        }
        if (server != null && server.isRunning()) {
            server.stop();
        }
        server = null;
        agent1 = null;
        agent2 = null;
        super.tearDown();
    }

    // =========================================================================
    // Basic Autonomous Agent Tests
    // =========================================================================

    public void testAutonomousWorkflowDiscovery() throws Exception {
        // Agent discovers available workflows
        String availableWorkflows = agent1.discoverWorkflows();
        assertNotNull("Agent should discover workflows", availableWorkflows);
        assertTrue("Should be JSON response", availableWorkflows.startsWith("{"));

        // Agent selects a workflow based on criteria
        String selectedWorkflow = agent1.selectWorkflow("active", "simple");
        assertNotNull("Agent should select a workflow", selectedWorkflow);
        assertFalse("Selected workflow should not be empty", selectedWorkflow.isEmpty());
    }

    public void testAutonomousWorkflowExecution() throws Exception {
        // Agent discovers and launches a workflow
        String workflowId = "simple-approval";
        String launchResult = agent1.launchWorkflow(workflowId, "{}");
        assertNotNull("Agent should launch workflow", launchResult);

        // Agent monitors workflow progress
        String status = agent1.getWorkflowStatus(launchResult);
        assertNotNull("Agent should get workflow status", status);
        assertTrue("Status should be JSON", status.startsWith("{"));

        // Agent completes work items automatically
        String completionResult = agent1.completeNextWorkItem(launchResult, "{\"status\": \"approved\"}");
        assertNotNull("Agent should complete work item", completionResult);
    }

    public void testAutonomousDecisionMaking() throws Exception {
        // Agent launches a workflow with decision points
        String workflowId = "conditional-workflow";
        String caseId = agent1.launchWorkflow(workflowId, "{\"amount\": 1500}");

        // Agent makes decisions based on workflow state
        for (int i = 0; i < 3; i++) {
            String workItem = agent1.getNextWorkItem(caseId);
            assertNotNull("Agent should get work item", workItem);

            // Make decision based on work item content
            String decision = agent1.makeDecision(workItem, 1500);
            String result = agent1.completeWorkItem(caseId, decision);

            assertNotNull("Decision should complete work item", result);
        }

        // Verify workflow completed
        String finalStatus = agent1.getWorkflowStatus(caseId);
        assertTrue("Workflow should be completed", finalStatus.contains("\"completed\""));
    }

    // =========================================================================
    // Error Recovery Tests
    // =========================================================================

    public void testWorkflowErrorRecovery() throws Exception {
        // Launch a workflow that might fail
        String workflowId = "error-prone-workflow";
        String caseId = agent1.launchWorkflow(workflowId, "{}");

        // Simulate error condition
        try {
            agent1.simulateError(caseId);
            fail("Should simulate error");
        } catch (AutonomousAgentException e) {
            // Agent should recover
            boolean recoverySuccessful = agent1.recoverFromError(caseId, e);
            assertTrue("Agent should recover from error", recoverySuccessful);
        }

        // Verify workflow continues
        String status = agent1.getWorkflowStatus(caseId);
        assertTrue("Workflow should continue after recovery", status.contains("\"status\""));
    }

    public void testRetryLogicForTransientFailures() throws Exception {
        AtomicInteger attemptCount = new AtomicInteger(0);

        // Configure agent with retry logic
        agent1.setRetryAttempts(3);
        agent1.setRetryDelay(100); // 100ms between retries

        // Execute operation that fails initially
        boolean result = agent1.executeWithRetry(() -> {
            int count = attemptCount.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("Attempt " + count + " failed");
            }
            return "success";
        });

        assertTrue("Operation should succeed after retries", result);
        assertEquals("Should have attempted 3 times", 3, attemptCount.get());
    }

    // =========================================================================
    // Multi-Agent Coordination Tests
    // =========================================================================

    public void testMultiAgentWorkflowCoordination() throws Exception {
        // Start both agents
        agent1.start();
        agent2.start();

        // Launch a workflow that requires multiple agents
        String workflowId = "multi-agent-workflow";
        String caseId = agent1.launchWorkflow(workflowId, "{\"taskCount\": 4}");

        // Agents coordinate to complete all tasks
        while (true) {
            String workItem = agent1.getNextAvailableWorkItem(caseId);
            if (workItem == null) {
                break; // No more work items
            }

            // Complete the work item
            String result = agent1.completeWorkItem(caseId, "{\"status\": \"done\"}");
            assertNotNull("Work item completion should succeed", result);
        }

        // Verify both agents participated
        String agent1Stats = agent1.getExecutionStats();
        String agent2Stats = agent2.getExecutionStats();

        assertTrue("Agent1 should have executed tasks", agent1Stats.contains("\"tasksExecuted\""));
        assertTrue("Agent2 should have executed tasks", agent2Stats.contains("\"tasksExecuted\""));
    }

    public void testAgentHandoffScenario() throws Exception {
        // Agent1 starts a workflow
        String workflowId = "handoff-workflow";
        String caseId = agent1.launchWorkflow(workflowId, "{}");

        // Complete some work items
        agent1.completeNextWorkItem(caseId, "{\"status\": \"partial\"}");

        // Handoff to agent2
        boolean handoffSuccessful = agent2.handoffWorkflow(caseId, agent1);
        assertTrue("Handoff should succeed", handoffSuccessful);

        // Agent2 continues the workflow
        String result = agent2.completeNextWorkItem(caseId, "{\"status\": \"final\"}");
        assertNotNull("Agent2 should complete workflow", result);

        // Verify workflow completion
        String status = agent2.getWorkflowStatus(caseId);
        assertTrue("Workflow should be completed", status.contains("\"completed\""));
    }

    // =========================================================================
    // Performance and Load Tests
    // =========================================================================

    public void testAutonomousAgentPerformance() throws Exception {
        // Test agent performance under load
        int workflowCount = 10;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < workflowCount; i++) {
            String workflowId = "simple-workflow-" + i;
            String caseId = agent1.launchWorkflow(workflowId, "{}");
            agent1.completeWorkflow(caseId, "{\"status\": \"done\"}");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Should be able to handle 10 workflows in reasonable time
        assertTrue("Agent should handle 10 workflows in < 10 seconds", duration < 10000);
    }

    public void testConcurrentWorkflowExecution() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Launch multiple workflows concurrently
        int workflowCount = 20;
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < workflowCount; i++) {
            final int workflowId = i;
            executor.submit(() -> {
                try {
                    String caseId = agent1.launchWorkflow("simple-workflow", "{}");
                    agent1.completeWorkflow(caseId, "{\"status\": \"done\"}");
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    // Log error but continue
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals("All workflows should be completed", workflowCount, completedCount.get());
    }

    // =========================================================================
    // Resource Management Tests
    // =========================================================================

    public void testResourceAwareWorkflowSelection() throws Exception {
        // Agent should consider available resources
        String workflowId = agent1.selectWorkflowConsideringResources(
            "resource-intensive-workflow",
            0.8); // 80% resource threshold

        assertNotNull("Agent should select workflow based on resources", workflowId);
        assertFalse("Workflow should be selected", workflowId.isEmpty());
    }

    public void testResourceMonitoring() throws Exception {
        // Start resource monitoring
        agent1.startResourceMonitoring();

        // Execute workflows
        for (int i = 0; i < 5; i++) {
            String caseId = agent1.launchWorkflow("lightweight-workflow", "{}");
            agent1.completeWorkflow(caseId, "{\"status\": \"done\"}");
        }

        // Check resource usage
        String resourceStats = agent1.getResourceStats();
        assertNotNull("Agent should track resource usage", resourceStats);
        assertTrue("Resource stats should be JSON", resourceStats.startsWith("{"));

        // Should show CPU, memory, and network usage
        assertTrue("Should contain CPU usage", resourceStats.contains("\"cpu\""));
        assertTrue("Should contain memory usage", resourceStats.contains("\"memory\""));
        assertTrue("Should contain network usage", resourceStats.contains("\"network\""));
    }

    // =========================================================================
    // Advanced Scenario Tests
    // =========================================================================

    public void testAutonomousWorkflowOptimization() throws Exception {
        // Agent analyzes workflow performance and suggests optimizations
        String workflowId = "performance-critical-workflow";
        String caseId = agent1.launchWorkflow(workflowId, "{}");

        // Complete workflow while collecting metrics
        agent1.completeWorkflowWithMetrics(caseId, "{\"status\": \"done\"}");

        // Analyze performance
        String analysis = agent1.analyzePerformance(workflowId);
        assertNotNull("Should have performance analysis", analysis);
        assertTrue("Analysis should be JSON", analysis.startsWith("{"));

        // Apply optimization
        boolean optimizationApplied = agent1.applyOptimization(analysis);
        assertTrue("Optimization should be applied", optimizationApplied);
    }

    public void testAutonomousLearningFromFailures() throws Exception {
        // Agent learns from failed workflows
        String failedWorkflowId = "error-prone-workflow";

        // Execute multiple times to learn patterns
        for (int i = 0; i < 3; i++) {
            try {
                String caseId = agent1.launchWorkflow(failedWorkflowId, "{}");
                agent1.completeWorkflow(caseId, "{\"status\": \"done\"}");
            } catch (Exception e) {
                // Learn from failure
                agent1.learnFromFailure(failedWorkflowId, e);
            }
        }

        // Should have learned patterns
        String learnedPatterns = agent1.getLearnedPatterns(failedWorkflowId);
        assertNotNull("Agent should learn from failures", learnedPatterns);
        assertTrue("Learned patterns should be JSON", learnedPatterns.startsWith("{"));
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private YawlAuthenticationProvider createTestAuthenticationProvider() {
        // Create a simple authentication provider for testing
        // In real implementation, would use proper authentication
        return new YawlAuthenticationProvider() {
            @Override
            public boolean authenticate(String username, String password, String apiKey) {
                return "test-user".equals(username) && "test-pass".equals(password);
            }

            @Override
            public String generateToken(String username) {
                return "test-token-" + username;
            }
        };
    }
}