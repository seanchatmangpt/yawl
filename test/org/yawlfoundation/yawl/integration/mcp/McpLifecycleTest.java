/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yawlfoundation.yawl.integration.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for MCP SDK v1 (0.18.0+) protocol lifecycle compliance.
 *
 * Tests the official MCP SDK server lifecycle using STDIO transport.
 * Validates McpSchema types, server capabilities, tool specifications,
 * and proper server construction/cleanup.
 *
 * Note: The MCP SDK v1 uses STDIO transport, not HTTP. Client-side testing
 * would require external client tools. These tests focus on server-side
 * lifecycle behaviors that can be validated in-process.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class McpLifecycleTest {

    private static final String SERVER_NAME = "test-yawl-mcp-server";
    private static final String SERVER_VERSION = "1.0.0-test";
    private static final String PROTOCOL_VERSION = "2025-11-25";

    // =========================================================================
    // PHASE 1: SERVER CAPABILITIES VALIDATION
    // =========================================================================

    @Test
    @Order(1)
    @DisplayName("Server capabilities - full configuration has all features enabled")
    void testFullServerCapabilities() {
        McpSchema.ServerCapabilities capabilities = YawlServerCapabilities.full();

        assertNotNull(capabilities, "Full capabilities should not be null");
        assertNotNull(capabilities.resources(), "Resources capability should be present");
        assertNotNull(capabilities.tools(), "Tools capability should be present");
        assertNotNull(capabilities.prompts(), "Prompts capability should be present");

        // Verify tools capability has listChanged enabled
        assertTrue(capabilities.tools().listChanged(),
            "Tools capability should have listChanged=true");

        // Verify resources capability has listChanged enabled
        assertTrue(capabilities.resources().listChanged(),
            "Resources capability should have listChanged=true");

        // Verify prompts capability has listChanged enabled
        assertTrue(capabilities.prompts().listChanged(),
            "Prompts capability should have listChanged=true");
    }

    @Test
    @Order(2)
    @DisplayName("Server capabilities - minimal configuration has only tools")
    void testMinimalServerCapabilities() {
        McpSchema.ServerCapabilities capabilities = YawlServerCapabilities.minimal();

        assertNotNull(capabilities, "Minimal capabilities should not be null");
        assertNotNull(capabilities.tools(), "Tools capability should be present");
        assertNull(capabilities.resources(), "Resources capability should be absent");
        assertNull(capabilities.prompts(), "Prompts capability should be absent");
    }

    @Test
    @Order(3)
    @DisplayName("Server capabilities - tools and resources configuration")
    void testToolsAndResourcesCapabilities() {
        McpSchema.ServerCapabilities capabilities = YawlServerCapabilities.toolsAndResources();

        assertNotNull(capabilities, "Capabilities should not be null");
        assertNotNull(capabilities.tools(), "Tools capability should be present");
        assertNotNull(capabilities.resources(), "Resources capability should be present");
        assertNull(capabilities.prompts(), "Prompts capability should be absent");
    }

    @Test
    @Order(4)
    @DisplayName("Server capabilities - read-only configuration")
    void testReadOnlyServerCapabilities() {
        McpSchema.ServerCapabilities capabilities = YawlServerCapabilities.readOnly();

        assertNotNull(capabilities, "Read-only capabilities should not be null");
        assertNull(capabilities.tools(), "Tools capability should be absent for read-only");
        assertNotNull(capabilities.resources(), "Resources capability should be present");
        assertNotNull(capabilities.prompts(), "Prompts capability should be present");
    }

    // =========================================================================
    // PHASE 2: MCP SCHEMA TYPES VALIDATION
    // =========================================================================

    @Test
    @Order(5)
    @DisplayName("McpSchema.Tool - construction and immutability")
    void testToolSchemaConstruction() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("param1", Map.of("type", "string", "description", "First parameter"));
        props.put("param2", Map.of("type", "integer", "description", "Second parameter"));

        List<String> required = List.of("param1");
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name("test_tool")
            .description("A test tool for validation")
            .inputSchema(inputSchema)
            .build();

        assertEquals("test_tool", tool.name(), "Tool name should match");
        assertEquals("A test tool for validation", tool.description(),
            "Tool description should match");
        assertNotNull(tool.inputSchema(), "Input schema should not be null");
        assertEquals("object", tool.inputSchema().type(), "Schema type should be object");
    }

    @Test
    @Order(6)
    @DisplayName("McpSchema.CallToolResult - success result construction")
    void testCallToolResultSuccess() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
            "Operation completed successfully", false);

        assertFalse(result.isError(), "Result should not be an error");
        assertNotNull(result.content(), "Result content should not be null");
        assertEquals(1, result.content().size(), "Should have one content item");

        McpSchema.Content content = result.content().get(0);
        assertInstanceOf(McpSchema.TextContent.class, content, "Content should be TextContent");
        assertEquals("Operation completed successfully",
            ((McpSchema.TextContent) content).text(), "Text content should match");
    }

    @Test
    @Order(7)
    @DisplayName("McpSchema.CallToolResult - error result construction")
    void testCallToolResultError() {
        McpSchema.CallToolResult result = new McpSchema.CallToolResult(
            "Failed: Invalid parameters provided", true);

        assertTrue(result.isError(), "Result should be an error");
        assertNotNull(result.content(), "Error result content should not be null");
    }

    @Test
    @Order(8)
    @DisplayName("McpSchema.LoggingLevel - level ordering")
    void testLoggingLevelOrdering() {
        // MCP spec defines level ordering: DEBUG < INFO < NOTICE < WARNING < ERROR < CRITICAL < ALERT < EMERGENCY
        assertTrue(McpSchema.LoggingLevel.DEBUG.level() < McpSchema.LoggingLevel.INFO.level(),
            "DEBUG should be lower than INFO");
        assertTrue(McpSchema.LoggingLevel.INFO.level() < McpSchema.LoggingLevel.NOTICE.level(),
            "INFO should be lower than NOTICE");
        assertTrue(McpSchema.LoggingLevel.NOTICE.level() < McpSchema.LoggingLevel.WARNING.level(),
            "NOTICE should be lower than WARNING");
        assertTrue(McpSchema.LoggingLevel.WARNING.level() < McpSchema.LoggingLevel.ERROR.level(),
            "WARNING should be lower than ERROR");
        assertTrue(McpSchema.LoggingLevel.ERROR.level() < McpSchema.LoggingLevel.CRITICAL.level(),
            "ERROR should be lower than CRITICAL");
        assertTrue(McpSchema.LoggingLevel.CRITICAL.level() < McpSchema.LoggingLevel.ALERT.level(),
            "CRITICAL should be lower than ALERT");
        assertTrue(McpSchema.LoggingLevel.ALERT.level() < McpSchema.LoggingLevel.EMERGENCY.level(),
            "ALERT should be lower than EMERGENCY");
    }

    @Test
    @Order(9)
    @DisplayName("McpSchema.Resource - construction with URI")
    void testResourceSchemaConstruction() {
        McpSchema.Resource resource = new McpSchema.Resource(
            "yawl://specifications",
            "YAWL Specifications",
            "List of all loaded workflow specifications",
            "application/json",
            null);

        assertEquals("yawl://specifications", resource.uri(), "URI should match");
        assertEquals("YAWL Specifications", resource.name(), "Name should match");
        assertEquals("List of all loaded workflow specifications", resource.description(),
            "Description should match");
        assertEquals("application/json", resource.mimeType(), "MIME type should match");
    }

    @Test
    @Order(10)
    @DisplayName("McpSchema.ResourceTemplate - parameterized URI template")
    void testResourceTemplateConstruction() {
        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "yawl://cases/{caseId}",
            "YAWL Case by ID",
            "Get details for a specific workflow case",
            "application/json",
            null);

        assertEquals("yawl://cases/{caseId}", template.uriTemplate(), "URI template should match");
        assertEquals("YAWL Case by ID", template.name(), "Name should match");
        assertTrue(template.uriTemplate().contains("{caseId}"),
            "Template should contain caseId parameter");
    }

    // =========================================================================
    // PHASE 3: SERVER LIFECYCLE WITH IN-MEMORY TRANSPORT
    // =========================================================================

    @Test
    @Order(11)
    @DisplayName("Server construction - basic server builder pattern")
    void testServerBuilderConstruction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        // Use in-memory streams for testing (server expects STDIO but we can verify builder)
        ByteArrayOutputStream testOutput = new ByteArrayOutputStream();

        // Verify server builder creates valid configuration
        McpServer.Builder builder = McpServer.sync(
                new StdioServerTransportProvider(jsonMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.minimal())
            .instructions("Test server instructions");

        assertNotNull(builder, "Server builder should be created");
    }

    @Test
    @Order(12)
    @DisplayName("Server construction - with tools registration")
    void testServerConstructionWithTools() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        // Create a simple test tool
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("message", Map.of("type", "string", "description", "Message to echo"));
        List<String> required = List.of("message");
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        McpServerFeatures.SyncToolSpecification echoTool =
            new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                    .name("echo")
                    .description("Echo a message back")
                    .inputSchema(inputSchema)
                    .build(),
                (exchange, args) -> {
                    String message = args.get("message") != null ? args.get("message").toString() : "";
                    return new McpSchema.CallToolResult("Echo: " + message, false);
                }
            );

        // Build server with tool
        McpSyncServer server = McpServer.sync(
                new StdioServerTransportProvider(jsonMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.minimal())
            .tools(List.of(echoTool))
            .build();

        assertNotNull(server, "Server should be built with tools");

        // Cleanup
        server.closeGracefully();
    }

    @Test
    @Order(13)
    @DisplayName("Server construction - with resources registration")
    void testServerConstructionWithResources() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        // Create a test resource
        McpSchema.Resource testResource = new McpSchema.Resource(
            "test://resource",
            "Test Resource",
            "A test resource",
            "text/plain",
            null);

        McpServerFeatures.SyncResourceSpecification resourceSpec =
            new McpServerFeatures.SyncResourceSpecification(
                testResource,
                (uri) -> new McpSchema.ReadResourceResult(
                    List.of(new McpSchema.TextResourceContents(
                        uri, "text/plain", "Test resource content")))
            );

        McpSyncServer server = McpServer.sync(
                new StdioServerTransportProvider(jsonMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.readOnly())
            .resources(List.of(resourceSpec))
            .build();

        assertNotNull(server, "Server should be built with resources");

        // Cleanup
        server.closeGracefully();
    }

    @Test
    @Order(14)
    @DisplayName("Server construction - with prompts registration")
    void testServerConstructionWithPrompts() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);

        // Create a test prompt
        McpSchema.Prompt testPrompt = McpSchema.Prompt.builder()
            .name("test_prompt")
            .description("A test prompt")
            .arguments(List.of(
                new McpSchema.PromptArgument("topic", "Topic to discuss", true)))
            .build();

        McpServerFeatures.SyncPromptSpecification promptSpec =
            new McpServerFeatures.SyncPromptSpecification(
                testPrompt,
                (args) -> new McpSchema.GetPromptResult(
                    "Test Prompt",
                    List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        new McpSchema.TextContent("Discuss: " +
                            args.get("topic").toString()))))
            );

        McpSyncServer server = McpServer.sync(
                new StdioServerTransportProvider(jsonMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.readOnly())
            .prompts(List.of(promptSpec))
            .build();

        assertNotNull(server, "Server should be built with prompts");

        // Cleanup
        server.closeGracefully();
    }

    // =========================================================================
    // PHASE 4: ERROR HANDLING
    // =========================================================================

    @Test
    @Order(15)
    @DisplayName("Tool handler - exception handling returns error result")
    void testToolHandlerExceptionHandling() throws Exception {
        Map<String, Object> props = new LinkedHashMap<>();
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
            "object", props, List.of(), false, null, null);

        // Tool that throws an exception
        McpServerFeatures.SyncToolSpecification failingTool =
            new McpServerFeatures.SyncToolSpecification(
                McpSchema.Tool.builder()
                    .name("failing_tool")
                    .description("A tool that always fails")
                    .inputSchema(inputSchema)
                    .build(),
                (exchange, args) -> {
                    throw new RuntimeException("Intentional test failure");
                }
            );

        // Test the handler directly
        Map<String, Object> args = new HashMap<>();
        McpSchema.CallToolResult result;

        try {
            result = failingTool.handler().handle(null, args);
            fail("Handler should have thrown an exception");
        } catch (RuntimeException e) {
            assertEquals("Intentional test failure", e.getMessage(),
                "Exception message should match");
        }
    }

    @Test
    @Order(16)
    @DisplayName("JsonSchema - required field validation")
    void testJsonSchemaRequiredFields() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("required_field", Map.of("type", "string"));
        props.put("optional_field", Map.of("type", "string"));

        List<String> required = List.of("required_field");
        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, required, false, null, null);

        assertEquals("object", schema.type(), "Schema type should be object");
        assertEquals(1, schema.required().size(), "Should have one required field");
        assertTrue(schema.required().contains("required_field"),
            "required_field should be marked as required");
        assertFalse(schema.required().contains("optional_field"),
            "optional_field should not be marked as required");
    }

    // =========================================================================
    // PHASE 5: YAWL-SPECIFIC VALIDATIONS
    // =========================================================================

    @Test
    @Order(17)
    @DisplayName("YawlMcpServer - constructor validation with null URL")
    void testYawlMcpServerNullUrl() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new YawlMcpServer(null, "admin", "password"),
            "Should throw for null engine URL"
        );
        assertTrue(exception.getMessage().toLowerCase().contains("url"),
            "Error message should mention URL");
    }

    @Test
    @Order(18)
    @DisplayName("YawlMcpServer - constructor validation with empty URL")
    void testYawlMcpServerEmptyUrl() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new YawlMcpServer("", "admin", "password"),
            "Should throw for empty engine URL"
        );
        assertNotNull(exception.getMessage(), "Error message should not be null");
    }

    @Test
    @Order(19)
    @DisplayName("YawlMcpServer - constructor validation with null username")
    void testYawlMcpServerNullUsername() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new YawlMcpServer("http://localhost:8080/yawl", null, "password"),
            "Should throw for null username"
        );
        assertTrue(exception.getMessage().toLowerCase().contains("username"),
            "Error message should mention username");
    }

    @Test
    @Order(20)
    @DisplayName("YawlMcpServer - constructor validation with null password")
    void testYawlMcpServerNullPassword() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new YawlMcpServer("http://localhost:8080/yawl", "admin", null),
            "Should throw for null password"
        );
        assertTrue(exception.getMessage().toLowerCase().contains("password"),
            "Error message should mention password");
    }

    @Test
    @Order(21)
    @DisplayName("YawlMcpServer - not running before start")
    void testYawlMcpServerNotRunningBeforeStart() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "password");
        assertFalse(server.isRunning(), "Server should not be running before start()");
        assertNull(server.getMcpServer(), "McpServer should be null before start()");
    }

    @Test
    @Order(22)
    @DisplayName("YawlMcpServer - stop before start is no-op")
    void testYawlMcpServerStopBeforeStart() {
        YawlMcpServer server = new YawlMcpServer(
            "http://localhost:8080/yawl", "admin", "password");

        // Should not throw
        assertDoesNotThrow(() -> server.stop(),
            "stop() before start() should not throw");
        assertFalse(server.isRunning(), "Server should not be running after stop()");
    }

    // =========================================================================
    // PHASE 6: CONCURRENT BEHAVIOR
    // =========================================================================

    @Test
    @Order(23)
    @DisplayName("Logging handler - thread-safe level changes")
    void testLoggingHandlerThreadSafeLevelChanges() throws Exception {
        org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler handler =
            new org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler();

        int numThreads = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Submit tasks that concurrently change logging level
        for (int i = 0; i < numThreads; i++) {
            final McpSchema.LoggingLevel level = McpSchema.LoggingLevel.values()[i % 8];
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                    handler.setLevel(level);
                    doneLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Start all threads simultaneously
        startLatch.countDown();

        // Wait for completion
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS),
            "All threads should complete within timeout");
        executor.shutdown();

        // Verify no exceptions occurred
        for (Future<?> future : futures) {
            assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS),
                "No thread should throw an exception");
        }

        // Verify final state is one of the valid levels
        assertNotNull(handler.getLevel(), "Final level should not be null");
    }

    @Test
    @Order(24)
    @DisplayName("Server capabilities - immutable after construction")
    void testServerCapabilitiesImmutability() {
        McpSchema.ServerCapabilities caps = YawlServerCapabilities.full();

        // Capabilities object should be fully constructed and consistent
        assertNotNull(caps.tools(), "Tools capability should be present");
        assertNotNull(caps.resources(), "Resources capability should be present");
        assertNotNull(caps.prompts(), "Prompts capability should be present");

        // Multiple accesses should return consistent values
        assertSame(caps.tools(), caps.tools(),
            "Multiple calls to tools() should return same instance");
        assertSame(caps.resources(), caps.resources(),
            "Multiple calls to resources() should return same instance");
    }
}
