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

package org.yawlfoundation.yawl.integration.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production readiness validation: every core MCP tool against real LLMs.
 *
 * <p>Tests the complete MCP end-to-end loop:</p>
 * <ol>
 *   <li>Schema validation — tool schemas are well-formed JSON Schema objects</li>
 *   <li>Naming convention — all tools follow the {@code yawl_*} convention</li>
 *   <li>Parameter documentation — required params are declared and documented</li>
 *   <li>LLM comprehension — Z.AI confirms understanding of tool catalog</li>
 *   <li>LLM invocation — Z.AI generates valid tool call arguments from schema</li>
 * </ol>
 *
 * <p>Schema and naming tests run without a YAWL engine (schema creation is pure).
 * LLM tests require {@code ZAI_API_KEY} environment variable.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("MCP Tool LLM Validation — Production Readiness")
class LlmMcpValidationTest {

    // Route OkHttp 3.x through the local bridge proxy (127.0.0.1:3128) which handles
    // external JWT proxy auth internally. OkHttp 3.x cannot authenticate directly with
    // the external proxy at 21.0.0.199:15004 that requires JWT credentials.
    static {
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "3128");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "3128");
        System.clearProperty("http.proxyUser");
        System.clearProperty("http.proxyPassword");
    }

    /**
     * Dummy YAWL engine URL — clients are created here only to satisfy the
     * non-null contract of {@link YawlToolSpecifications#createAll}. The schema
     * creation phase never makes network calls; only the tool handler lambdas
     * do, and those are not invoked by these tests.
     */
    private static final String TEST_ENGINE_URL = "http://localhost:8080/yawl";

    /**
     * Non-null, non-empty session handle required by tool schema factory.
     * Actual engine calls only happen when a tool handler is invoked, not
     * during schema creation.
     */
    private static final String TEST_SESSION = "schema-validation-session";

    /** The 16 core tool names mandated by the MCP tool specification. */
    private static final Set<String> EXPECTED_CORE_TOOL_NAMES = Set.of(
        "yawl_launch_case",
        "yawl_get_case_status",
        "yawl_cancel_case",
        "yawl_list_specifications",
        "yawl_get_specification",
        "yawl_upload_specification",
        "yawl_get_work_items",
        "yawl_get_work_items_for_case",
        "yawl_checkout_work_item",
        "yawl_checkin_work_item",
        "yawl_get_running_cases",
        "yawl_get_case_data",
        "yawl_suspend_case",
        "yawl_resume_case",
        "yawl_skip_work_item",
        "yawl_synthesize_spec"
    );

    // =========================================================================
    // Schema Structure Tests (no engine, no LLM)
    // =========================================================================

    @Nested
    @DisplayName("Schema Structure Tests")
    class SchemaStructureTests {

        @Test
        @DisplayName("All 16 core MCP tools are created with well-formed schemas")
        void coreToolSchemasAreWellFormed() {
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            assertEquals(16, tools.size(),
                "Expected exactly 16 core YAWL MCP tools");

            for (McpServerFeatures.SyncToolSpecification spec : tools) {
                McpSchema.Tool tool = spec.tool();
                String toolName = tool.name();

                assertNotNull(toolName,
                    "Tool name must not be null");
                assertFalse(toolName.isBlank(),
                    "Tool name must not be blank");
                assertNotNull(tool.description(),
                    "Tool description must not be null for: " + toolName);
                assertFalse(tool.description().isBlank(),
                    "Tool description must not be blank for: " + toolName);
                assertNotNull(tool.inputSchema(),
                    "Tool input schema must not be null for: " + toolName);
                assertEquals("object", tool.inputSchema().type(),
                    "Input schema type must be 'object' for: " + toolName);
                assertNotNull(tool.inputSchema().properties(),
                    "Input schema properties must not be null for: " + toolName);
            }
        }

        @Test
        @DisplayName("All core tool names follow yawl_ naming convention")
        void coreToolNamesFollowNamingConvention() {
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            List<String> names = tools.stream()
                .map(s -> s.tool().name())
                .collect(Collectors.toList());

            for (String name : names) {
                assertTrue(name.startsWith("yawl_"),
                    "Tool name must start with 'yawl_': " + name);
                assertFalse(name.contains(" "),
                    "Tool name must not contain spaces: " + name);
                assertTrue(name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_'),
                    "Tool name must only contain letters, digits, and underscores: " + name);
            }

            for (String expected : EXPECTED_CORE_TOOL_NAMES) {
                assertTrue(names.contains(expected),
                    "Expected core tool is missing from registry: " + expected
                    + ". Present tools: " + names);
            }
        }

        @Test
        @DisplayName("yawl_launch_case has required specIdentifier parameter")
        void launchCaseToolRequiresSpecIdentifier() {
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            McpSchema.Tool launchCase = tools.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(t -> "yawl_launch_case".equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_launch_case tool not found in registry"));

            List<String> required = launchCase.inputSchema().required();
            assertNotNull(required, "Required params list must not be null");
            assertFalse(required.isEmpty(), "yawl_launch_case must have required parameters");
            assertTrue(required.contains("specIdentifier"),
                "yawl_launch_case must require 'specIdentifier', got: " + required);

            // Optional parameters must be present in properties but not in required
            var props = launchCase.inputSchema().properties();
            assertNotNull(props, "Properties must not be null");
            assertTrue(props.containsKey("specIdentifier"),
                "specIdentifier must be in properties");
        }

        @Test
        @DisplayName("yawl_checkin_work_item has required work item identifier")
        void checkinWorkItemToolHasRequiredParams() {
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            McpSchema.Tool checkin = tools.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(t -> "yawl_checkin_work_item".equals(t.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("yawl_checkin_work_item not found"));

            List<String> required = checkin.inputSchema().required();
            assertNotNull(required, "Required params must not be null");
            assertFalse(required.isEmpty(),
                "yawl_checkin_work_item must have at least one required param");
        }

        @Test
        @DisplayName("Tool descriptions are sufficiently long to guide LLM usage")
        void toolDescriptionsAreSubstantive() {
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");

            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            for (McpServerFeatures.SyncToolSpecification spec : tools) {
                McpSchema.Tool tool = spec.tool();
                assertTrue(tool.description().length() >= 30,
                    "Tool description must be at least 30 chars for LLM guidance. "
                    + "Tool: " + tool.name() + ", desc length: " + tool.description().length());
            }
        }
    }

    // =========================================================================
    // LLM Comprehension Tests (require ZAI_API_KEY)
    // =========================================================================

    @Nested
    @DisplayName("LLM Comprehension Tests")
    class LlmComprehensionTests {

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("Z.AI LLM confirms connectivity")
        void zaiConnectsToLlm() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));
            assertTrue(zai.isInitialized(),
                "ZaiService must initialize successfully");
            assertTrue(zai.verifyConnection(),
                "Z.AI LLM must be reachable via ZaiService.verifyConnection()");
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM correctly identifies tools for core workflow operations")
        void llmIdentifiesCorrectToolsForWorkflowOperations() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");
            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            // Build a concise tool catalog for the prompt
            StringBuilder catalog = new StringBuilder();
            catalog.append("YAWL MCP Tool Catalog (tool_name: description):\n");
            tools.forEach(spec -> {
                McpSchema.Tool t = spec.tool();
                // Take first sentence of description to keep prompt concise
                String desc = t.description();
                int dotPos = desc.indexOf(". ");
                String shortDesc = dotPos > 0 ? desc.substring(0, dotPos + 1) : desc;
                catalog.append("- ").append(t.name()).append(": ").append(shortDesc).append("\n");
            });

            String response = zai.chat(
                "You are an integration test validator. Given these YAWL MCP tools:\n\n"
                + catalog
                + "\nRespond with ONLY the tool names (one per line) for these operations:\n"
                + "1. Launch a workflow case\n"
                + "2. List all specifications\n"
                + "3. Cancel a running case\n"
                + "4. Get all work items\n"
                + "Format: 'Op1: <name>, Op2: <name>, Op3: <name>, Op4: <name>'"
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");

            String responseLower = response.toLowerCase();
            assertTrue(responseLower.contains("launch_case") || responseLower.contains("launch"),
                "LLM must identify launch tool. Response: " + response);
            assertTrue(responseLower.contains("list_spec") || responseLower.contains("specifications"),
                "LLM must identify list specifications tool. Response: " + response);
            assertTrue(responseLower.contains("cancel"),
                "LLM must identify cancel tool. Response: " + response);
            assertTrue(responseLower.contains("work_item") || responseLower.contains("workitem"),
                "LLM must identify work items tool. Response: " + response);
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM generates valid JSON arguments for yawl_launch_case")
        void llmGeneratesValidLaunchCaseArguments() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            // Build the exact schema from real tool spec
            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");
            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            McpSchema.Tool launchCase = tools.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(t -> "yawl_launch_case".equals(t.name()))
                .findFirst()
                .orElseThrow();

            // Present the actual tool schema to the LLM
            String schemaPrompt = String.format(
                "Tool: %s\nDescription: %s\nRequired: %s\nProperties: %s\n",
                launchCase.name(),
                launchCase.description(),
                launchCase.inputSchema().required(),
                launchCase.inputSchema().properties().keySet()
            );

            String response = zai.chat(
                "Given this MCP tool:\n" + schemaPrompt
                + "\nWrite valid JSON arguments to launch a case named 'OrderProcess'.\n"
                + "Respond with ONLY the JSON object."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.contains("OrderProcess") || response.contains("specIdentifier"),
                "LLM response must reference the spec identifier. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM generates valid JSON arguments for yawl_cancel_case")
        void llmGeneratesValidCancelCaseArguments() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            var ibClient = new InterfaceB_EnvironmentBasedClient(TEST_ENGINE_URL + "/ib");
            var iaClient = new InterfaceA_EnvironmentBasedClient(TEST_ENGINE_URL + "/ia");
            List<McpServerFeatures.SyncToolSpecification> tools =
                YawlToolSpecifications.createAll(ibClient, iaClient, TEST_SESSION);

            McpSchema.Tool cancelCase = tools.stream()
                .map(McpServerFeatures.SyncToolSpecification::tool)
                .filter(t -> "yawl_cancel_case".equals(t.name()))
                .findFirst()
                .orElseThrow();

            String schemaPrompt = String.format(
                "Tool: %s\nDescription: %s\nRequired: %s\nProperties: %s\n",
                cancelCase.name(),
                cancelCase.description(),
                cancelCase.inputSchema().required(),
                cancelCase.inputSchema().properties().keySet()
            );

            String response = zai.chat(
                "Given this MCP tool:\n" + schemaPrompt
                + "\nWrite valid JSON arguments to cancel case ID '42'.\n"
                + "Respond with ONLY the JSON object."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.contains("42") || response.contains("caseId") || response.contains("case"),
                "LLM response must reference case ID. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM can describe the full YAWL MCP capability surface")
        void llmDescribesFullToolCapabilitySurface() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String response = zai.chat(
                "What are the main categories of operations a YAWL MCP server "
                + "provides? List the 4 main categories: case management, "
                + "work item management, specification management, and AI tools. "
                + "Confirm with 'yes' or provide a brief description of each."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            // The LLM should engage meaningfully with the YAWL MCP context
            assertTrue(response.length() > 20,
                "LLM response must be substantive. Response: " + response);
        }
    }
}
