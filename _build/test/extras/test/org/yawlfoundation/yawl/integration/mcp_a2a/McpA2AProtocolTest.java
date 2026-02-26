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

package org.yawlfoundation.yawl.integration.mcp_a2a;

import junit.framework.TestCase;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * MCP-A2A protocol integration tests.
 *
 * Chicago TDD: Tests the integration between MCP (Model Context Protocol)
 * and A2A (Agent-to-Agent) protocols. Verifies that agents can use MCP tools
 * while maintaining A2A protocol compliance.
 *
 * Coverage targets:
 * - MCP server registration with A2A
 * - Tool discovery through A2A protocol
 * - Tool execution via MCP
 * - Authentication integration
 * - Error propagation between protocols
 * - Protocol handshake
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-22
 */
public class McpA2AProtocolTest extends TestCase {

    private static final int TEST_PORT = 19884;
    private McpA2AIntegration integration;

    public McpA2AProtocolTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        integration = new McpA2AIntegration("http://localhost:8080/yawl", TEST_PORT);
        integration.start();
        Thread.sleep(200); // allow services to start
    }

    @Override
    protected void tearDown() throws Exception {
        if (integration != null) {
            integration.stop();
        }
        integration = null;
        super.tearDown();
    }

    // =========================================================================
    // MCP Server Registration Tests
    // =========================================================================

    public void testMcpServerRegistration() throws Exception {
        // Verify MCP server is registered with A2A
        boolean isRegistered = integration.isMcpServerRegistered();
        assertTrue("MCP server should be registered with A2A", isRegistered);

        // Verify server appears in agent discovery
        String agentCard = integration.getAgentCard();
        assertNotNull("Agent card should be available", agentCard);
        assertTrue("Agent card should be JSON", agentCard.startsWith("{"));

        // Verify MCP tools are listed in capabilities
        assertTrue("Agent card should list MCP tools",
                  agentCard.contains("\"mcp_tools\"") ||
                  agentCard.contains("\"capabilities\""));
    }

    public void testMultipleMcpServersRegistration() throws Exception {
        // Register a second MCP server
        McpA2AIntegration integration2 = new McpA2AIntegration("http://localhost:8080/yawl", TEST_PORT + 1);

        try {
            integration2.start();
            Thread.sleep(100);

            // Both should be registered
            boolean isRegistered1 = integration.isMcpServerRegistered();
            boolean isRegistered2 = integration2.isMcpServerRegistered();

            assertTrue("First MCP server should be registered", isRegistered1);
            assertTrue("Second MCP server should be registered", isRegistered2);

            // Agent discovery should list both
            String agentCard = integration.getAgentCard();
            assertTrue("Agent card should reflect multiple MCP servers",
                      agentCard.length() > 100); // Should have more content with multiple servers

        } finally {
            integration2.stop();
        }
    }

    // =========================================================================
    // Tool Discovery Tests
    // =========================================================================

    public void testToolDiscoveryViaA2A() throws Exception {
        // List available tools through A2A protocol
        String tools = integration.listToolsViaA2A();
        assertNotNull("Tools should be discoverable via A2A", tools);
        assertTrue("Response should be JSON", tools.startsWith("{"));

        // Should contain MCP tool structure
        assertTrue("Should contain tools array", tools.contains("\"tools\":["));

        // Verify specific YAWL tools are available
        assertTrue("Should contain yawl engine tools", tools.contains("yawl"));
        assertTrue("Should contain workflow tools", tools.contains("workflow"));
        assertTrue("Should contain case management tools", tools.contains("case"));
    }

    public void testToolDescriptionCompliance() throws Exception {
        // Get tool descriptions
        String tools = integration.listToolsViaA2A();

        // Verify each tool has required fields
        assertTrue("Tools should have name field", tools.contains("\"name\""));
        assertTrue("Tools should have description field", tools.contains("\"description\""));
        assertTrue("Tools should have inputSchema field", tools.contains("\"inputSchema\""));
        assertTrue("Tools should have outputSchema field", tools.contains("\"outputSchema\""));
    }

    // =========================================================================
    // Tool Execution Tests
    // =========================================================================

    public void testToolExecutionViaMcp() throws Exception {
        // Execute a tool via MCP protocol
        String toolName = "yawl_query_workflows";
        String input = "{\"status\": \"active\"}";

        String result = integration.executeToolViaMcp(toolName, input);
        assertNotNull("Tool execution should succeed", result);
        assertTrue("Result should be JSON", result.startsWith("{"));

        // Verify result structure
        assertTrue("Result should contain success field", result.contains("\"success\""));
        assertTrue("Result should contain data field", result.contains("\"data\""));
    }

    public void testToolExecutionViaA2AProxy() throws Exception {
        // Execute same tool through A2A proxy
        String toolName = "yawl_query_workflows";
        String input = "{\"status\": \"active\"}";

        String result = integration.executeToolViaA2AProxy(toolName, input);
        assertNotNull("Tool execution through A2A should succeed", result);
        assertTrue("Result should be JSON", result.startsWith("{"));

        // Should be equivalent to direct MCP execution
        String directResult = integration.executeToolViaMcp(toolName, input);
        assertEquals("A2A proxy should give same result as direct MCP",
                     normalizeJson(result), normalizeJson(directResult));
    }

    public void testToolErrorHandling() throws Exception {
        // Execute nonexistent tool
        try {
            integration.executeToolViaMcp("nonexistent_tool", "{}");
            fail("Should fail for nonexistent tool");
        } catch (McpA2AException e) {
            assertTrue("Should indicate tool not found",
                      e.getMessage().toLowerCase().contains("tool") ||
                      e.getMessage().toLowerCase().contains("not found"));
        }

        // Execute with invalid input
        try {
            integration.executeToolViaMcp("yawl_query_workflows", "invalid json");
            fail("Should fail for invalid input");
        } catch (McpA2AException e) {
            assertTrue("Should indicate invalid input",
                      e.getMessage().toLowerCase().contains("input") ||
                      e.getMessage().toLowerCase().contains("invalid"));
        }
    }

    // =========================================================================
    // Authentication Integration Tests
    // =========================================================================

    public void testAuthenticationIntegration() throws Exception {
        // Test authentication flows
        boolean authWorks = integration.testAuthentication();
        assertTrue("Authentication should work", authWorks);

        // Test authenticated tool access
        String result = integration.executeAuthenticatedTool("yawl_query_workflows", "{}");
        assertNotNull("Authenticated tool access should work", result);
        assertTrue("Result should be JSON", result.startsWith("{"));
    }

    public void testAuthorizationIntegration() throws Exception {
        // Test different permission levels
        boolean adminAccess = integration.testAdminAccess();
        boolean userAccess = integration.testUserAccess();

        assertTrue("Admin should have full access", adminAccess);
        assertTrue("User should have limited access", userAccess);
    }

    // =========================================================================
    // Error Propagation Tests
    // =========================================================================

    public void testErrorPropagationFromMcpToA2A() throws Exception {
        // Simulate MCP error
        try {
            integration.simulateMcpError();
            fail("Should propagate MCP error");
        } catch (McpA2AException e) {
            assertTrue("Error should be properly propagated",
                      e.getMessage().toLowerCase().contains("mcp") ||
                      e.getMessage().toLowerCase().contains("error"));
        }
    }

    public void testErrorPropagationFromA2AToMcp() throws Exception {
        // Simulate A2A error
        try {
            integration.simulateA2aError();
            fail("Should propagate A2A error");
        } catch (McpA2AException e) {
            assertTrue("Error should be properly propagated",
                      e.getMessage().toLowerCase().contains("a2a") ||
                      e.getMessage().toLowerCase().contains("error"));
        }
    }

    // =========================================================================
    // Performance Tests
    // =========================================================================

    public void testToolDiscoveryPerformance() throws Exception {
        long startTime = System.currentTimeMillis();

        // Perform tool discovery multiple times
        for (int i = 0; i < 10; i++) {
            String tools = integration.listToolsViaA2A();
            assertNotNull("Tools should be discoverable", tools);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertTrue("Tool discovery should be fast", duration < 5000); // 5 seconds for 10 calls
    }

    public void testToolExecutionPerformance() throws Exception {
        long startTime = System.currentTimeMillis();

        // Execute tool multiple times
        for (int i = 0; i < 5; i++) {
            String result = integration.executeToolViaMcp("yawl_query_workflows", "{}");
            assertNotNull("Tool execution should succeed", result);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        assertTrue("Tool execution should be fast", duration < 3000); // 3 seconds for 5 calls
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    private String normalizeJson(String json) {
        // Remove whitespace for comparison
        return json.replaceAll("\\s+", "").trim();
    }
}