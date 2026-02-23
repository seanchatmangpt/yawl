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
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * End-to-end A2A integration tests.
 *
 * Chicago TDD: Tests real end-to-end workflows through the A2A protocol.
 * Tests simulate complete workflow scenarios from agent discovery to task completion.
 *
 * Coverage targets:
 * - Full workflow lifecycle: discovery -> connection -> task execution -> completion
 * - Authentication across multiple requests (JWT session)
 * - Task state transitions through A2A API
 * - Error handling at integration boundaries
 * - Multiple concurrent agents
 * - Data flow between A2A and YAWL engine
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-22
 */
public class A2AIntegrationTest extends TestCase {

    private static final int TEST_PORT = 19883;
    private YawlA2AServer server;
    private YawlA2AClient client;

    public A2AIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        // Start server
        server = new YawlA2AServer(
            "http://localhost:8080/yawl", "admin", "YAWL", TEST_PORT,
            CompositeAuthenticationProvider.production());
        server.start();
        Thread.sleep(200); // allow server to fully bind

        // Create client
        client = new YawlA2AClient("http://localhost:" + TEST_PORT);
    }

    @Override
    protected void tearDown() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // ignore cleanup errors
            }
        }
        if (server != null && server.isRunning()) {
            server.stop();
        }
        server = null;
        client = null;
        super.tearDown();
    }

    // =========================================================================
    // Full Workflow Lifecycle Tests
    // =========================================================================

    public void testCompleteWorkflowLifecycle() throws Exception {
        // 1. Discover agent
        String agentCard = client.discoverAgent();
        assertNotNull("Agent card should be discovered", agentCard);
        assertTrue("Agent card should be JSON", agentCard.startsWith("{"));

        // 2. Connect with valid credentials
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");
        assertNotNull("Session should be established", sessionId);
        assertFalse("Session ID should not be empty", sessionId.isEmpty());

        // 3. Query available workflows
        String workflows = client.queryWorkflows();
        assertNotNull("Workflows should be queryable", workflows);
        assertTrue("Response should be JSON", workflows.startsWith("{"));

        // 4. Launch a workflow
        String workflowId = "simple-workflow";
        String launchResponse = client.launchWorkflow(workflowId, "{}");
        assertNotNull("Workflow should be launchable", launchResponse);
        assertTrue("Launch response should contain case ID", launchResponse.contains("\"caseId\""));

        // 5. Query work items
        String workItems = client.queryWorkItems();
        assertNotNull("Work items should be queryable", workItems);
        assertTrue("Response should be JSON", workItems.startsWith("{"));

        // 6. Complete a work item
        String workItemId = extractCaseIdFromResponse(launchResponse);
        String completeResponse = client.completeWorkItem(workItemId, "{}");
        assertNotNull("Work item should be completable", completeResponse);

        // 7. Disconnect
        client.disconnect();
        // Should not throw exception
    }

    public void testWorkflowWithMultipleSteps() throws Exception {
        // Setup
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");

        // Launch a multi-step workflow
        String workflowId = "multi-step-workflow";
        String launchResponse = client.launchWorkflow(workflowId, "{}");
        String caseId = extractCaseIdFromResponse(launchResponse);

        // Get work items (should be multiple)
        String workItems = client.queryWorkItems();
        assertTrue("Should have multiple work items", workItems.length() > 100);

        // Complete first work item
        String firstItemId = extractFirstWorkItemId(workItems);
        String completeResponse1 = client.completeWorkItem(firstItemId, "{\"status\": \"completed\"}");
        assertNotNull("First completion should succeed", completeResponse1);

        // Get updated work items
        String updatedWorkItems = client.queryWorkItems();
        assertTrue("Should have fewer work items after completion", updatedWorkItems.length() < workItems.length());

        // Complete remaining items
        String secondItemId = extractFirstWorkItemId(updatedWorkItems);
        String completeResponse2 = client.completeWorkItem(secondItemId, "{\"status\": \"final\"}");
        assertNotNull("Second completion should succeed", completeResponse2);

        // Verify workflow is complete
        String finalWorkItems = client.queryWorkItems();
        assertTrue("Should have no work items left", finalWorkItems.contains("\"items\":[]") ||
                  finalWorkItems.length() < 50);
    }

    // =========================================================================
    // Session Management Tests
    // =========================================================================

    public void testSessionManagement() throws Exception {
        // Create session
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");
        assertNotNull("Session should be created", sessionId);

        // Use session multiple times
        String workflows = client.queryWorkflows();
        assertNotNull("Should query workflows with session", workflows);

        String launchResponse = client.launchWorkflow("simple-workflow", "{}");
        assertNotNull("Should launch workflow with session", launchResponse);

        // Disconnect and verify cleanup
        client.disconnect();

        // Operations after disconnect should fail
        try {
            client.queryWorkflows();
            fail("Should fail after disconnect");
        } catch (A2AException e) {
            // Expected
        }
    }

    public void testSessionTimeoutHandling() throws Exception {
        // Create session
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");

        // Wait for session to timeout (if configured)
        // Note: This depends on server configuration
        Thread.sleep(3000);

        // Session should still work or fail gracefully
        try {
            String workflows = client.queryWorkflows();
            // If still valid, should work
            assertNotNull("Should query workflows", workflows);
        } catch (A2AException e) {
            // If expired, should get proper error
            assertTrue("Should get session expired error",
                      e.getMessage().toLowerCase().contains("session") ||
                      e.getMessage().toLowerCase().contains("expired"));
        }
    }

    // =========================================================================
    // Concurrent Agents Test
    // =========================================================================

    public void testMultipleConcurrentAgents() throws Exception {
        // Create multiple client instances
        YawlA2AClient client1 = new YawlA2AClient("http://localhost:" + TEST_PORT);
        YawlA2AClient client2 = new YawlA2AClient("http://localhost:" + TEST_PORT);

        // Connect both clients
        String session1 = client1.connect("agent1", "user1", "key1");
        String session2 = client2.connect("agent2", "user2", "key2");

        assertNotNull("Client 1 session should be created", session1);
        assertNotNull("Client 2 session should be created", session2);

        // Both clients can operate independently
        String workflows1 = client1.queryWorkflows();
        String workflows2 = client2.queryWorkflows();

        assertNotNull("Client 1 should query workflows", workflows1);
        assertNotNull("Client 2 should query workflows", workflows2);

        // Launch workflows from both
        String launch1 = client1.launchWorkflow("simple-workflow", "{}");
        String launch2 = client2.launchWorkflow("simple-workflow", "{}");

        assertNotNull("Client 1 should launch workflow", launch1);
        assertNotNull("Client 2 should launch workflow", launch2);

        // Disconnect both
        client1.disconnect();
        client2.disconnect();
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    public void testErrorHandlingForInvalidWorkflow() throws Exception {
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");

        try {
            client.launchWorkflow("nonexistent-workflow", "{}");
            fail("Should fail for nonexistent workflow");
        } catch (A2AException e) {
            assertTrue("Should indicate workflow not found",
                      e.getMessage().toLowerCase().contains("workflow") ||
                      e.getMessage().toLowerCase().contains("not found"));
        }
    }

    public void testErrorHandlingForInvalidWorkItem() throws Exception {
        String sessionId = client.connect("test-agent", "test-user", "test-api-key");

        // Launch a workflow to get a case ID
        String launchResponse = client.launchWorkflow("simple-workflow", "{}");
        String caseId = extractCaseIdFromResponse(launchResponse);

        // Try to complete nonexistent work item
        try {
            client.completeWorkItem("nonexistent-workitem", "{}");
            fail("Should fail for nonexistent work item");
        } catch (A2AException e) {
            assertTrue("Should indicate work item not found",
                      e.getMessage().toLowerCase().contains("workitem") ||
                      e.getMessage().toLowerCase().contains("not found"));
        }
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private String extractCaseIdFromResponse(String response) {
        // Simple JSON parsing - in real implementation would use JSON parser
        int caseIdStart = response.indexOf("\"caseId\":");
        if (caseIdStart >= 0) {
            int caseIdEnd = response.indexOf("\"", caseIdStart + 10);
            if (caseIdEnd > caseIdStart + 10) {
                return response.substring(caseIdStart + 10, caseIdEnd);
            }
        }
        return "test-case-id";
    }

    private String extractFirstWorkItemId(String workItemsResponse) {
        // Simple parsing - in real implementation would use JSON parser
        int itemIdStart = workItemsResponse.indexOf("\"id\":");
        if (itemIdStart >= 0) {
            int itemIdEnd = workItemsResponse.indexOf("\"", itemIdStart + 6);
            if (itemIdEnd > itemIdStart + 6) {
                return workItemsResponse.substring(itemIdStart + 6, itemIdEnd);
            }
        }
        return "test-workitem-id";
    }
}