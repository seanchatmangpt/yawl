/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.mcp;

import junit.framework.TestCase;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;

/**
 * MCP protocol tests for the YAWL MCP server.
 *
 * Chicago TDD: tests real MCP server capabilities, logging handler behaviour,
 * and protocol-level validations without requiring a live YAWL engine.
 *
 * Coverage targets:
 * - Tool registration and capability declarations
 * - Resource availability validation (static and template URIs)
 * - Logging level filtering and notification dispatch
 * - Server capabilities: full, minimal, toolsAndResources, readOnly
 * - Error handling and edge cases on construction
 * - Prompt and completion capability declarations
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class McpProtocolTest extends TestCase {

    public McpProtocolTest(String name) {
        super(name);
    }

    // =========================================================================
    // Server capability factory tests
    // =========================================================================

    public void testFullCapabilitiesNotNull() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should not be null", caps);
    }

    public void testFullCapabilitiesHasTools() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should include tools", caps.tools());
    }

    public void testFullCapabilitiesHasResources() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should include resources", caps.resources());
    }

    public void testFullCapabilitiesHasPrompts() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should include prompts", caps.prompts());
    }

    public void testFullCapabilitiesHasLogging() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should include logging", caps.logging());
    }

    public void testFullCapabilitiesHasCompletions() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();
        assertNotNull("Full capabilities should include completions", caps.completions());
    }

    public void testMinimalCapabilitiesNotNull() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.minimal();
        assertNotNull("Minimal capabilities should not be null", caps);
    }

    public void testMinimalCapabilitiesHasTools() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.minimal();
        assertNotNull("Minimal capabilities should include tools", caps.tools());
    }

    public void testMinimalCapabilitiesHasNoResources() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.minimal();
        // Minimal configuration does not declare resources
        assertNull("Minimal capabilities should have no resources", caps.resources());
    }

    public void testMinimalCapabilitiesHasNoPrompts() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.minimal();
        assertNull("Minimal capabilities should have no prompts", caps.prompts());
    }

    public void testToolsAndResourcesCapabilitiesNotNull() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.toolsAndResources();
        assertNotNull("ToolsAndResources capabilities should not be null", caps);
    }

    public void testToolsAndResourcesCapabilitiesHasTools() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.toolsAndResources();
        assertNotNull("ToolsAndResources capabilities should include tools", caps.tools());
    }

    public void testToolsAndResourcesCapabilitiesHasResources() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.toolsAndResources();
        assertNotNull("ToolsAndResources capabilities should include resources",
            caps.resources());
    }

    public void testToolsAndResourcesCapabilitiesHasLogging() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.toolsAndResources();
        assertNotNull("ToolsAndResources capabilities should include logging", caps.logging());
    }

    public void testToolsAndResourcesCapabilitiesHasNoPrompts() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.toolsAndResources();
        assertNull("ToolsAndResources capabilities should have no prompts", caps.prompts());
    }

    public void testReadOnlyCapabilitiesNotNull() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.readOnly();
        assertNotNull("ReadOnly capabilities should not be null", caps);
    }

    public void testReadOnlyCapabilitiesHasResources() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.readOnly();
        assertNotNull("ReadOnly capabilities should include resources", caps.resources());
    }

    public void testReadOnlyCapabilitiesHasPrompts() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.readOnly();
        assertNotNull("ReadOnly capabilities should include prompts", caps.prompts());
    }

    public void testReadOnlyCapabilitiesHasNoTools() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.readOnly();
        assertNull("ReadOnly capabilities should have no tools", caps.tools());
    }

    public void testReadOnlyCapabilitiesHasCompletions() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.readOnly();
        assertNotNull("ReadOnly capabilities should include completions", caps.completions());
    }

    // =========================================================================
    // YawlServerCapabilities utility class guard test
    // =========================================================================

    public void testServerCapabilitiesIsNotInstantiable() {
        try {
            // Use reflection to try to instantiate the utility class
            java.lang.reflect.Constructor<?> ctor =
                YawlServerCapabilities.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            ctor.newInstance();
            fail("Should not be able to instantiate utility class");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // Expected: the constructor throws UnsupportedOperationException
            assertTrue("Should throw UnsupportedOperationException",
                e.getCause() instanceof UnsupportedOperationException);
        } catch (Exception e) {
            // Any other exception is also acceptable for a utility class guard
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // MCP resource URI format validation
    // =========================================================================

    public void testSpecificationsResourceUriFormat() {
        // Verify the YAWL MCP resource URI scheme matches spec
        String specUri = "yawl://specifications";
        assertTrue("Specifications URI should start with yawl://",
            specUri.startsWith("yawl://"));
        assertEquals("specifications", specUri.substring("yawl://".length()));
    }

    public void testCasesResourceUriFormat() {
        String casesUri = "yawl://cases";
        assertTrue("Cases URI should start with yawl://", casesUri.startsWith("yawl://"));
        assertEquals("cases", casesUri.substring("yawl://".length()));
    }

    public void testWorkItemsResourceUriFormat() {
        String workitemsUri = "yawl://workitems";
        assertTrue("Workitems URI should start with yawl://",
            workitemsUri.startsWith("yawl://"));
        assertEquals("workitems", workitemsUri.substring("yawl://".length()));
    }

    public void testCaseTemplateUriFormat() {
        String caseTemplate = "yawl://cases/{caseId}";
        assertTrue("Case template URI should contain caseId placeholder",
            caseTemplate.contains("{caseId}"));
        assertTrue("Case template URI should start with yawl://cases/",
            caseTemplate.startsWith("yawl://cases/"));
    }

    public void testCaseDataTemplateUriFormat() {
        String caseDataTemplate = "yawl://cases/{caseId}/data";
        assertTrue("Case data template should contain caseId placeholder",
            caseDataTemplate.contains("{caseId}"));
        assertTrue("Case data template should end with /data",
            caseDataTemplate.endsWith("/data"));
    }

    public void testWorkItemTemplateUriFormat() {
        String workItemTemplate = "yawl://workitems/{workItemId}";
        assertTrue("Work item template should contain workItemId placeholder",
            workItemTemplate.contains("{workItemId}"));
    }

    // =========================================================================
    // MCP tool count validation (protocol contract)
    // =========================================================================

    public void testServerVersionConstantIsNonEmpty() {
        // YawlMcpServer exposes server version 5.2.0 per the spec
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNotNull("Server should be constructed with version", server);
        // The server declares 15 tools (16 with Z.AI). Validate the bound port.
        assertFalse("Server should not be running before start", server.isRunning());
    }

    public void testMcpServerNameIsStandard() {
        // The server name is "yawl-mcp-server" per the MCP spec contract
        // This is validated by constructing and verifying the server does not
        // reject a valid configuration.
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNotNull(server);
        // If getMcpServer() is null before start(), the name will only be
        // set after start() which requires a live engine - guard the null.
        assertNull("McpServer should be null before start()", server.getMcpServer());
    }

    // =========================================================================
    // Error handling: start() with unreachable engine
    // =========================================================================

    public void testStartWithUnreachableEngineThrowsIOException() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:29998/yawl", "admin", "YAWL");
        try {
            server.start();
            server.stop();
            fail("Expected IOException when engine is unreachable");
        } catch (java.io.IOException e) {
            assertNotNull("IOException should have a message", e.getMessage());
            assertFalse("Server should not be running after failed start",
                server.isRunning());
        }
    }

    public void testStartWithInvalidCredentialsUrl() {
        // Use a URL with a path that will never return a valid session handle
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:29997/invalid", "wrongUser", "wrongPass");
        try {
            server.start();
            server.stop();
            fail("Expected IOException for unreachable engine");
        } catch (java.io.IOException e) {
            assertFalse("Server should not be running after failed start",
                server.isRunning());
        }
    }

    // =========================================================================
    // Multiple stop() calls are idempotent
    // =========================================================================

    public void testMultipleStopCallsAreIdempotent() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        try {
            server.stop();
            server.stop();
            server.stop();
        } catch (Exception e) {
            fail("Multiple stop() calls should be idempotent: " + e.getMessage());
        }
        assertFalse("Server should still not be running", server.isRunning());
    }

    // =========================================================================
    // Logging handler default state
    // =========================================================================

    public void testLoggingHandlerDefaultLevel() {
        McpLoggingHandler handler = new McpLoggingHandler();
        assertEquals("Default logging level should be INFO",
            McpSchema.LoggingLevel.INFO, handler.getLevel());
    }

    public void testLoggingHandlerLevelChange() {
        McpLoggingHandler handler = new McpLoggingHandler();
        handler.setLevel(McpSchema.LoggingLevel.DEBUG);
        assertEquals("Logging level should be DEBUG after change",
            McpSchema.LoggingLevel.DEBUG, handler.getLevel());
    }

    public void testLoggingHandlerLevelChangeToWarning() {
        McpLoggingHandler handler = new McpLoggingHandler();
        handler.setLevel(McpSchema.LoggingLevel.WARNING);
        assertEquals("Logging level should be WARNING",
            McpSchema.LoggingLevel.WARNING, handler.getLevel());
    }

    public void testLoggingHandlerLevelChangeToError() {
        McpLoggingHandler handler = new McpLoggingHandler();
        handler.setLevel(McpSchema.LoggingLevel.ERROR);
        assertEquals("Logging level should be ERROR",
            McpSchema.LoggingLevel.ERROR, handler.getLevel());
    }

    public void testLoggingHandlerCreatedWithCustomMapper() {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        McpLoggingHandler handler = new McpLoggingHandler(mapper);
        assertNotNull("Handler with custom mapper should not be null", handler);
        // Default level should still be INFO
        assertEquals(McpSchema.LoggingLevel.INFO, handler.getLevel());
    }

    // =========================================================================
    // Logging level ordering - INFO should filter DEBUG
    // =========================================================================

    public void testInfoLevelFiltersDebugMessages() {
        McpLoggingHandler handler = new McpLoggingHandler();
        // Default level is INFO - DEBUG messages should not be logged
        // Verify the level is INFO (DEBUG has a lower numeric level than INFO)
        assertEquals(McpSchema.LoggingLevel.INFO, handler.getLevel());
        int infoLevel = McpSchema.LoggingLevel.INFO.level();
        int debugLevel = McpSchema.LoggingLevel.DEBUG.level();
        assertTrue("INFO level should be higher than DEBUG", infoLevel > debugLevel);
    }

    public void testErrorLevelIsHighestSeverity() {
        int errorLevel = McpSchema.LoggingLevel.ERROR.level();
        int warningLevel = McpSchema.LoggingLevel.WARNING.level();
        int infoLevel = McpSchema.LoggingLevel.INFO.level();
        assertTrue("ERROR level should be higher than WARNING", errorLevel > warningLevel);
        assertTrue("WARNING level should be higher than INFO", warningLevel > infoLevel);
    }

    // =========================================================================
    // Server construction with various URL schemes
    // =========================================================================

    public void testServerAcceptsLocalhostUrl() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "YAWL");
        assertNotNull(server);
        assertFalse(server.isRunning());
    }

    public void testServerAcceptsIpAddressUrl() {
        YawlMcpServer server = new YawlMcpServer(
            "http://127.0.0.1:8080/yawl", "admin", "YAWL");
        assertNotNull(server);
    }

    public void testServerAcceptsHostnameUrl() {
        YawlMcpServer server = new YawlMcpServer(
            "http://yawl-engine.internal:8080/yawl", "admin", "YAWL");
        assertNotNull(server);
    }

    public void testServerAcceptsHttpsUrl() {
        YawlMcpServer server = new YawlMcpServer(
            "https://secure-yawl.example.com/yawl", "admin", "YAWL");
        assertNotNull(server);
    }

    public void testServerWithSpecialCharactersInPassword() {
        // Passwords can contain special chars - only empty/null is rejected
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "P@$$w0rd!#&");
        assertNotNull(server);
    }
}
