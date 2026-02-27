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

package org.yawlfoundation.yawl.graalpy.integration;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlMcpContext;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.McpToolRegistry;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive MCP (Model Context Protocol) validation tests for YAWL workflow engine.
 *
 * <p>Tests validate MCP tool compliance with Interface A/B client contracts,
 * ensuring all 15 MCP tools function correctly against both design-time (InterfaceA)
 * and runtime (InterfaceB) YAWL operations.</p>
 *
 * <p>All tests follow Chicago TDD methodology with strict assertions and comprehensive
 * coverage. No mocks are used - tests validate against real InterfaceA/B clients.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
@DisplayName("MCP Protocol Validation Tests")
public class McpProtocolValidationTest {

    // Test configuration
    private static final String YAWL_ENGINE_URL = "http://localhost:8080/yawl";
    private static final String YAWL_USERNAME = "admin";
    private static final String YAWL_PASSWORD = "admin";
    private static final String TEST_SPEC_ID = "SimpleWorkflow";
    private static final String TEST_SPEC_VERSION = "1.0";
    private static final String TEST_SPEC_URI = "http://example.com/simple.xml";

    // Test clients
    private InterfaceA_EnvironmentBasedClient interfaceAClient;
    private InterfaceB_EnvironmentBasedClient interfaceBClient;
    private String sessionHandle;

    // Tool specifications
    private List<McpServerFeatures.SyncToolSpecification> toolSpecifications;

    @BeforeAll
    void setUp() throws Exception {
        // Initialize InterfaceA client (design-time operations)
        interfaceAClient = new InterfaceA_EnvironmentBasedClient(YAWL_ENGINE_URL + "/ia");

        // Initialize InterfaceB client (runtime operations)
        interfaceBClient = new InterfaceB_EnvironmentBasedClient(YAWL_ENGINE_URL + "/ib");

        // Connect to YAWL engine and establish session
        sessionHandle = interfaceBClient.connect(YAWL_USERNAME, YAWL_PASSWORD);
        assertNotNull(sessionHandle, "Session handle must not be null");

        // Load test specification if not already present
        loadTestSpecification();

        // Get all registered tool specifications
        toolSpecifications = McpToolRegistry.createAll(
            new YawlMcpContext(interfaceBClient, interfaceAClient, sessionHandle, null)
        );

        // Verify we have at least the core 15 tools
        assertEquals(15, toolSpecifications.size(),
            "Expected exactly 15 MCP tools to be registered");
    }

    @AfterAll
    void tearDown() {
        // Disconnect from YAWL engine
        if (interfaceBClient != null && sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
            } catch (Exception e) {
                // Ignore disconnection errors during cleanup
            }
        }
    }

    // =========================================================================
    // Test Categories as specified in requirements
    // =========================================================================

    /**
     * Test tool registration and discovery functionality.
     * Validates that all 15 MCP tools are properly registered with correct metadata.
     */
    @Nested
    @DisplayName("Tool Registration Tests")
    class ToolRegistrationTests {

        @Test
        @DisplayName("Should register exactly 15 MCP tools")
        void testToolRegistration() {
            assertEquals(15, toolSpecifications.size(),
                "Expected exactly 15 MCP tools to be registered");

            // Verify all tools have unique names
            List<String> toolNames = toolSpecifications.stream()
                .map(spec -> spec.name())
                .collect(Collectors.toList());

            assertEquals(toolNames.stream().distinct().count(), toolNames.size(),
                "All tool names must be unique");
        }

        @Test
        @DisplayName("Should register required workflow management tools")
        void testRequiredWorkflowToolsRegistered() {
            List<String> requiredTools = Arrays.asList(
                "yawl_launch_case",
                "yawl_get_case_state",
                "yawl_get_work_items",
                "yawl_check_out_work_item",
                "yawl_check_in_work_item",
                "yawl_cancel_case",
                "yawl_get_specification"
            );

            for (String requiredTool : requiredTools) {
                assertTrue(toolSpecifications.stream()
                    .anyMatch(spec -> spec.name().equals(requiredTool)),
                    "Required tool '" + requiredTool + "' must be registered");
            }
        }

        @Test
        @DisplayName("Should register specification management tools")
        void testSpecificationToolsRegistered() {
            List<String> specTools = Arrays.asList(
                "yawl_list_specifications",
                "yawl_upload_specification",
                "yawl_synthesize_spec"
            );

            for (String specTool : specTools) {
                assertTrue(toolSpecifications.stream()
                    .anyMatch(spec -> spec.name().equals(specTool)),
                    "Specification tool '" + specTool + "' must be registered");
            }
        }

        @Test
        @DisplayName("Should register case management tools")
        void testCaseManagementToolsRegistered() {
            List<String> caseTools = Arrays.asList(
                "yawl_get_case_status",
                "yawl_suspend_case",
                "yawl_resume_case"
            );

            for (String caseTool : caseTools) {
                assertTrue(toolSpecifications.stream()
                    .anyMatch(spec -> spec.name().equals(caseTool)),
                    "Case management tool '" + caseTool + "' must be registered");
            }
        }

        @Test
        @DisplayName("Should register work item management tools")
        void testWorkItemToolsRegistered() {
            List<String> workItemTools = Arrays.asList(
                "yawl_get_work_items_for_case",
                "yawl_skip_work_item"
            );

            for (String workItemTool : workItemTools) {
                assertTrue(toolSpecifications.stream()
                    .anyMatch(spec -> spec.name().equals(workItemTool)),
                    "Work item tool '" + workItemTool + "' must be registered");
            }
        }

        @Test
        @DisplayName("Should validate tool schema compliance")
        void testToolSchemaCompliance() {
            for (McpServerFeatures.SyncToolSpecification spec : toolSpecifications) {
                // Each tool must have a valid name and description
                assertFalse(spec.name().isBlank(), "Tool name must not be blank");
                assertFalse(spec.description().isBlank(), "Tool description must not be blank");

                // Each tool must have an input schema
                assertNotNull(spec.inputSchema(), "Tool must have input schema");
                assertEquals("object", spec.inputSchema().type(),
                    "Tool schema must be of type 'object'");

                // Validate schema properties
                Map<String, Object> properties = spec.inputSchema().properties();
                assertNotNull(properties, "Schema must have properties defined");

                // Check that required parameters are properly defined
                if (spec.inputSchema().required() != null && !spec.inputSchema().required().isEmpty()) {
                    for (String requiredParam : spec.inputSchema().required()) {
                        assertTrue(properties.containsKey(requiredParam),
                            "Schema must contain required parameter: " + requiredParam);
                    }
                }
            }
        }
    }

    /**
     * Test tool execution functionality.
     * Validates that all MCP tools execute correctly with proper inputs.
     */
    @Nested
    @DisplayName("Tool Execution Tests")
    class ToolExecutionTests {

        private McpSchema.CallToolExchange createTestExchange(String toolName, Map<String, Object> arguments) {
            return new McpSchema.CallToolExchange(
                "test-call-id",
                toolName,
                new McpSchema.CallToolArguments(arguments, null),
                null
            );
        }

        @Test
        @DisplayName("Should execute yawl_launch_case successfully")
        void testLaunchCaseExecution() {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("specIdentifier", TEST_SPEC_ID);
            args.put("specVersion", TEST_SPEC_VERSION);
            args.put("specUri", TEST_SPEC_URI);

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_launch_case", args);

            // Find the tool specification
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_launch_case"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_launch_case tool not found"));

            // Execute the tool
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Launch case should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
            String content = result.contents().get(0).text();
            assertTrue(content.contains("Case launched successfully"),
                "Success message must be present");
            assertTrue(content.contains("Case ID:"),
                "Case ID must be returned");
        }

        @Test
        @DisplayName("Should execute yawl_get_case_state successfully")
        void testGetCaseStateExecution() throws Exception {
            // First launch a case
            String caseId = launchTestCase();
            assertNotNull(caseId, "Case must be launched");

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("caseId", caseId);

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_get_case_state", args);

            // Find and execute tool
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_get_case_state"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_get_case_state tool not found"));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Get case state should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
            String content = result.contents().get(0).text();
            assertTrue(content.contains("Case ID:"), "Case ID must be in response");
            assertTrue(content.contains("State:"), "State must be in response");
        }

        @Test
        @DisplayName("Should execute yawl_get_work_items successfully")
        void testGetWorkItemsExecution() throws Exception {
            // Launch a case first
            String caseId = launchTestCase();
            assertNotNull(caseId, "Case must be launched");

            Map<String, Object> args = new LinkedHashMap<>();

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_get_work_items", args);

            // Find and execute tool
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_get_work_items"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_get_work_items tool not found"));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Get work items should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
        }

        @Test
        @DisplayName("Should execute yawl_check_out_work_item successfully")
        void testCheckoutWorkItemExecution() throws Exception {
            // Launch case and get work items
            String caseId = launchTestCase();
            List<WorkItemRecord> workItems = interfaceBClient.getWorkItems(caseId, sessionHandle);
            assertFalse(workItems.isEmpty(), "Must have work items to checkout");

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("caseId", caseId);
            args.put("workItemId", workItems.get(0).getID());

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_check_out_work_item", args);

            // Find and execute tool
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_check_out_work_item"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_check_out_work_item tool not found"));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Checkout work item should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
        }

        @Test
        @DisplayName("Should execute yawl_cancel_case successfully")
        void testCancelCaseExecution() throws Exception {
            // Launch a case
            String caseId = launchTestCase();
            assertNotNull(caseId, "Case must be launched");

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("caseId", caseId);

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_cancel_case", args);

            // Find and execute tool
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_cancel_case"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_cancel_case tool not found"));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Cancel case should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
        }

        @Test
        @DisplayName("Should execute yawl_get_specification successfully")
        void testGetSpecificationExecution() throws Exception {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("specIdentifier", TEST_SPEC_ID);
            args.put("specVersion", TEST_SPEC_VERSION);

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_get_specification", args);

            // Find and execute tool
            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals("yawl_get_specification"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_get_specification tool not found"));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Validate successful execution
            assertFalse(result.isError(), "Get specification should succeed");
            assertTrue(result.contents().size() > 0, "Result must have content");
        }

        private String launchTestCase() throws Exception {
            YSpecificationID specId = new YSpecificationID(TEST_SPEC_ID, TEST_SPEC_VERSION, TEST_SPEC_URI);
            return interfaceBClient.launchCase(specId, "<data></data>", null, sessionHandle);
        }
    }

    /**
     * Test parameter schema validation.
     * Validates that tools properly validate their input parameters.
     */
    @Nested
    @DisplayName("Parameter Validation Tests")
    class ParameterValidationTests {

        @Test
        @DisplayName("Should validate required parameters are provided")
        void testRequiredParameterValidation() {
            TestParameterBuilder builder = new TestParameterBuilder();

            TestScenario scenario = new TestScenario(
                "yawl_launch_case",
                builder.buildMissingRequiredArgs(),
                "specIdentifier is required"
            );

            assertParameterValidationError(scenario);
        }

        @Test
        @DisplayName("Should validate parameter types are correct")
        void testParameterTypeValidation() {
            TestParameterBuilder builder = new TestParameterBuilder();

            TestScenario scenario = new TestScenario(
                "yawl_launch_case",
                builder.withSpecIdentifier(123), // Integer instead of string
                "specIdentifier must be string"
            );

            assertParameterTypeValidation(scenario);
        }

        @Test
        @DisplayName("Should validate specification identifier format")
        void testSpecIdentifierFormatValidation() {
            TestParameterBuilder builder = new TestParameterBuilder();

            TestScenario scenario = new TestScenario(
                "yawl_launch_case",
                builder.withSpecIdentifier(""), // Empty string
                "specIdentifier cannot be empty"
            );

            assertParameterValidationError(scenario);
        }

        @Test
        @DisplayName("Should validate case ID format for case operations")
        void testCaseIdValidation() {
            TestParameterBuilder builder = new TestParameterBuilder();

            TestScenario scenario = new TestScenario(
                "yawl_get_case_state",
                builder.withCaseId("invalid-case-id"),
                "caseId must be valid YAWL case identifier"
            );

            assertParameterValidationError(scenario);
        }

        @Test
        @DisplayName("Should validate work item ID format")
        void testWorkItemIdValidation() {
            TestParameterBuilder builder = new TestParameterBuilder();

            TestScenario scenario = new TestScenario(
                "yawl_check_out_work_item",
                builder.withWorkItemId("invalid-work-item-id"),
                "workItemId must be valid YAWL work item identifier"
            );

            assertParameterValidationError(scenario);
        }

        private void assertParameterValidationError(TestScenario scenario) {
            McpSchema.CallToolExchange exchange = createTestExchange(
                scenario.toolName(),
                scenario.arguments()
            );

            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals(scenario.toolName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + scenario.toolName()));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Should return error for invalid parameters
            assertTrue(result.isError(), "Tool should return error for invalid parameters");
            assertTrue(result.contents().size() > 0, "Error message must be provided");
            String errorMessage = result.contents().get(0).text();
            assertTrue(errorMessage.contains(scenario.expectedErrorMessage()),
                "Error message must indicate validation failure");
        }

        private void assertParameterTypeValidation(TestScenario scenario) {
            McpSchema.CallToolExchange exchange = createTestExchange(
                scenario.toolName(),
                scenario.arguments()
            );

            McpServerFeatures.SyncToolSpecification toolSpec = toolSpecifications.stream()
                .filter(spec -> spec.name().equals(scenario.toolName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + scenario.toolName()));

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Should return error for wrong parameter types
            assertTrue(result.isError(), "Tool should return error for wrong parameter types");
        }
    }

    /**
     * Test response handling functionality.
     * Validates that tools return properly formatted responses.
     */
    @Nested
    @DisplayName("Response Handling Tests")
    class ResponseHandlingTests {

        @Test
        @DisplayName("Should return successful response with case ID on case launch")
        void testLaunchCaseResponseFormat() throws Exception {
            // Launch a case
            String caseId = launchTestCase();
            assertNotNull(caseId, "Case must be launched");

            // Verify response format
            assertTrue(caseId.startsWith("urn:yawl:case:"),
                "Case ID must be valid YAWL case identifier");
        }

        @Test
        @DisplayName("Should return XML state response for case status")
        void testCaseStatusResponseFormat() throws Exception {
            // Launch a case
            String caseId = launchTestCase();

            Map<String, Object> args = new LinkedHashMap<>();
            args.put("caseId", caseId);

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_get_case_state", args);
            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_get_case_state");

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertFalse(result.isError(), "Should succeed");
            String response = result.contents().get(0).text();
            assertTrue(response.contains("<"), "Response should contain XML content");
            assertTrue(response.contains(">"), "Response should contain XML content");
        }

        @Test
        @DisplayName("Should return work item list response")
        void testWorkItemListResponseFormat() throws Exception {
            // Launch a case first
            String caseId = launchTestCase();

            McpSchema.CallToolExchange exchange = createTestExchange("yawl_get_work_items", Map.of());
            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_get_work_items");

            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertFalse(result.isError(), "Should succeed");
            String response = result.contents().get(0).text();
            // Response should be parsable as work item list
            assertTrue(response.contains("WorkItemRecord") || response.trim().isEmpty(),
                "Response should contain work item data or be empty");
        }

        @Test
        @DisplayName("Should return proper error response format")
        void testErrorResponseFormat() {
            TestParameterBuilder builder = new TestParameterBuilder();

            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_launch_case",
                builder.withSpecIdentifier("") // Invalid - empty spec ID
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_launch_case");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // Error response format
            assertTrue(result.isError(), "Should return error");
            assertEquals(1, result.contents().size(), "Should have exactly one error content");
            assertTrue(result.contents().get(0).text().length() > 0,
                "Error message should not be empty");
        }

        private McpServerFeatures.SyncToolSpecification findToolSpec(String toolName) {
            return toolSpecifications.stream()
                .filter(spec -> spec.name().equals(toolName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + toolName));
        }
    }

    /**
     * Test error handling for invalid tool calls.
     * Validates proper error responses for various failure scenarios.
     */
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null parameters gracefully")
        void testNullParameterHandling() {
            McpSchema.CallToolExchange exchange = new McpSchema.CallToolExchange(
                "test-id",
                "yawl_launch_case",
                new McpSchema.CallToolArguments(null, null),
                null
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_launch_case");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertTrue(result.isError(), "Should handle null parameters");
            assertTrue(result.contents().get(0).text().contains("null") ||
                      result.contents().get(0).text().contains("argument"),
                "Error message should indicate null parameter issue");
        }

        @Test
        @DisplayName("Should handle disconnected YAWL engine gracefully")
        void testDisconnectedEngineHandling() throws Exception {
            // Disconnect from engine
            interfaceBClient.disconnect(sessionHandle);

            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_get_case_state",
                Map.of("caseId", "nonexistent-case")
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_get_case_state");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertTrue(result.isError(), "Should handle disconnected engine");
            assertTrue(result.contents().get(0).text().contains("connection") ||
                      result.contents().get(0).text().contains("failed") ||
                      result.contents().get(0).text().contains("error"),
                "Error message should indicate connection failure");
        }

        @Test
        @DisplayName("Should handle nonexistent case gracefully")
        void testNonexistentCaseHandling() {
            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_get_case_state",
                Map.of("caseId", "urn:yawl:case:nonexistent-12345")
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_get_case_state");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertTrue(result.isError(), "Should handle nonexistent case");
            assertTrue(result.contents().get(0).text().contains("not found") ||
                      result.contents().get(0).text().contains("does not exist"),
                "Error message should indicate case not found");
        }

        @Test
        @DisplayName("Should handle nonexistent specification gracefully")
        void testNonexistentSpecificationHandling() {
            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_launch_case",
                Map.of("specIdentifier", "NonexistentSpec", "specVersion", "1.0")
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_launch_case");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertTrue(result.isError(), "Should handle nonexistent specification");
            assertTrue(result.contents().get(0).text().contains("not found") ||
                      result.contents().0).text().contains("does not exist"),
                "Error message should indicate specification not found");
        }

        @Test
        @DisplayName("Should handle invalid work item operations gracefully")
        void testInvalidWorkItemHandling() {
            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_check_out_work_item",
                Map.of("caseId", "urn:yawl:case:test-123", "workItemId", "invalid-work-item")
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_check_out_work_item");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            assertTrue(result.isError(), "Should handle invalid work item");
            assertTrue(result.contents().get(0).text().contains("invalid") ||
                      result.contents().get(0).text().contains("not found"),
                "Error message should indicate invalid work item");
        }

        @Test
        @DisplayName("Should handle XML parsing errors gracefully")
        void testXmlParsingErrorHandling() {
            // Test with malformed XML data
            McpSchema.CallToolExchange exchange = createTestExchange(
                "yawl_launch_case",
                Map.of("specIdentifier", TEST_SPEC_ID, "caseData", "<invalid<xml>")
            );

            McpServerFeatures.SyncToolSpecification toolSpec = findToolSpec("yawl_launch_case");
            McpSchema.CallToolResult result = toolSpec.handler().handle(exchange);

            // May succeed or fail gracefully depending on implementation
            // Key is that it doesn't throw uncaught exceptions
            assertNotNull(result, "Should return a valid result");
        }
    }

    // =========================================================================
    // Helper Classes and Methods
    // =========================================================================

    /**
     * Helper class for building test parameters in a fluent way.
     */
    private static class TestParameterBuilder {
        private final Map<String, Object> arguments = new LinkedHashMap<>();

        public TestParameterBuilder withSpecIdentifier(String specId) {
            arguments.put("specIdentifier", specId);
            return this;
        }

        public TestParameterBuilder withCaseId(String caseId) {
            arguments.put("caseId", caseId);
            return this;
        }

        public TestParameterBuilder withWorkItemId(String workItemId) {
            arguments.put("workItemId", workItemId);
            return this;
        }

        public TestParameterBuilder withSpecVersion(String version) {
            arguments.put("specVersion", version);
            return this;
        }

        public TestParameterBuilder withSpecUri(String uri) {
            arguments.put("specUri", uri);
            return this;
        }

        public Map<String, Object> buildMissingRequiredArgs() {
            // Empty map - no required arguments
            return Map.of();
        }

        public Map<String, Object> build() {
            return new LinkedHashMap<>(arguments);
        }
    }

    /**
     * Helper class for defining test scenarios.
     */
    private record TestScenario(String toolName, Map<String, Object> arguments, String expectedErrorMessage) {}

    /**
     * Helper method to create test exchanges.
     */
    private McpSchema.CallToolExchange createTestExchange(String toolName, Map<String, Object> arguments) {
        return new McpSchema.CallToolExchange(
            "test-call-id-" + System.currentTimeMillis(),
            toolName,
            new McpSchema.CallToolArguments(arguments, null),
            null
        );
    }

    /**
     * Load a test specification for testing purposes.
     */
    private void loadTestSpecification() throws Exception {
        try {
            // Check if specification already exists
            YSpecificationID specId = new YSpecificationID(TEST_SPEC_ID, TEST_SPEC_VERSION, TEST_SPEC_URI);
            interfaceAClient.getSpecification(specId, sessionHandle);
        } catch (Exception e) {
            // Specification doesn't exist, create it
            String xmlSpec = """
                <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema">
                    <specification id="SimpleWorkflow" name="SimpleWorkflow" version="1.0" uri="http://example.com/simple.xml">
                        <process id="SimpleWorkflow_process">
                            <inputCondition id="i-top"/>
                            <outputCondition id="o-top"/>
                            <task id="TaskA" name="Task A">
                                <flow id="flow1" condition="true" target="o-top"/>
                            </task>
                            <decomposition id="TaskA_decomposition" decompositionRef="SimpleWorkflow_process"/>
                        </process>
                    </specification>
                </specificationSet>
                """;

            interfaceAClient.uploadSpecification(xmlSpec, sessionHandle);
        }
    }

    // Interface for the exchange object (simplified for testing)
    private static class TestCallToolExchange implements McpSchema.CallToolExchange {
        private final String id;
        private final String toolName;
        private final McpSchema.CallToolArguments arguments;
        private final Map<String, Object> context;

        public TestCallToolExchange(String id, String toolName, McpSchema.CallToolArguments arguments, Map<String, Object> context) {
            this.id = id;
            this.toolName = toolName;
            this.arguments = arguments;
            this.context = context;
        }

        @Override
        public String id() { return id; }
        @Override
        public String tool() { return toolName; }
        @Override
        public McpSchema.CallToolArguments arguments() { return arguments; }
        @Override
        public Map<String, Object> context() { return context; }
    }
}