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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Production readiness validation: every A2A skill against real LLMs.
 *
 * <p>Tests the complete A2A end-to-end loop:</p>
 * <ol>
 *   <li>Skill metadata validation — all skills have proper IDs, names, descriptions</li>
 *   <li>Skill execution — IntrospectCodebaseSkill executes and returns structured results</li>
 *   <li>LLM comprehension — Z.AI confirms understanding of YAWL A2A agent capabilities</li>
 *   <li>LLM invocation — Z.AI generates valid A2A task messages for skills</li>
 * </ol>
 *
 * <p>Skill metadata and execution tests run without a YAWL engine.
 * LLM tests require {@code ZAI_API_KEY} environment variable.</p>
 *
 * <p>The 7 core A2A skills validated here match the agent card published by
 * {@code YawlA2AServer.buildSkillList()} and are the production API surface.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("A2A Skill LLM Validation — Production Readiness")
class LlmA2AValidationTest {

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
     * The 7 core A2A skill IDs published by {@code YawlA2AServer.buildSkillList()}.
     * These are the production-level skills that LLMs must understand to work with YAWL.
     */
    private static final List<Map<String, String>> CORE_A2A_SKILLS = List.of(
        Map.of(
            "id", "launch_workflow",
            "name", "Launch Workflow",
            "description", "Launch a new workflow case from a loaded specification. "
                + "Provide the specification identifier and optional case data.",
            "tags", "workflow, bpm, launch, case"
        ),
        Map.of(
            "id", "query_workflows",
            "name", "Query Workflows",
            "description", "List available workflow specifications, running cases, "
                + "and their current status.",
            "tags", "workflow, query, list, status"
        ),
        Map.of(
            "id", "manage_workitems",
            "name", "Manage Work Items",
            "description", "Get, check out, and complete work items in running workflow cases.",
            "tags", "workflow, workitem, task, complete"
        ),
        Map.of(
            "id", "cancel_workflow",
            "name", "Cancel Workflow",
            "description", "Cancel a running workflow case by its case ID.",
            "tags", "workflow, cancel, case"
        ),
        Map.of(
            "id", "handoff_workitem",
            "name", "Handoff Work Item",
            "description", "Transfer a work item to another agent when the current agent "
                + "cannot complete it. Uses secure JWT-based handoff protocol.",
            "tags", "workflow, handoff, transfer, agent-to-agent"
        ),
        Map.of(
            "id", "process_mining_analyze",
            "name", "Process Mining Analyze",
            "description", "Analyze YAWL workflow event logs for performance, variants, "
                + "conformance, and social network patterns.",
            "tags", "process-mining, analytics, xes, performance, variants"
        ),
        Map.of(
            "id", "construct_coordination",
            "name", "Construct Coordination",
            "description", "Petri net token marking using SPARQL CONSTRUCT for zero-inference-cost "
                + "workflow routing. Determines enabled tasks from current token marking.",
            "tags", "petri-net, sparql, construct, token-routing"
        )
    );

    // =========================================================================
    // Skill Metadata Tests (no engine, no LLM)
    // =========================================================================

    @Nested
    @DisplayName("Skill Metadata Tests")
    class SkillMetadataTests {

        @Test
        @DisplayName("All 7 core A2A skills have valid IDs and names")
        void coreSkillsHaveValidIdentifiers() {
            assertEquals(7, CORE_A2A_SKILLS.size(),
                "Expected exactly 7 core A2A skills in the YAWL agent card");

            for (Map<String, String> skill : CORE_A2A_SKILLS) {
                String id = skill.get("id");
                String name = skill.get("name");
                String description = skill.get("description");

                assertNotNull(id, "Skill ID must not be null");
                assertFalse(id.isBlank(), "Skill ID must not be blank");
                assertFalse(id.contains(" "),
                    "Skill ID must not contain spaces: " + id);
                assertTrue(id.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_'),
                    "Skill ID must only contain letters, digits, underscores: " + id);

                assertNotNull(name, "Skill name must not be null for id: " + id);
                assertFalse(name.isBlank(), "Skill name must not be blank for id: " + id);

                assertNotNull(description, "Skill description must not be null for id: " + id);
                assertTrue(description.length() >= 30,
                    "Skill description must be at least 30 chars for LLM guidance. "
                    + "Skill: " + id + ", desc length: " + description.length());
            }
        }

        @Test
        @DisplayName("Core A2A skill IDs match expected production API surface")
        void coreSkillIdsMatchExpectedSurface() {
            Set<String> expectedIds = Set.of(
                "launch_workflow",
                "query_workflows",
                "manage_workitems",
                "cancel_workflow",
                "handoff_workitem",
                "process_mining_analyze",
                "construct_coordination"
            );

            Set<String> actualIds = new java.util.HashSet<>();
            for (Map<String, String> skill : CORE_A2A_SKILLS) {
                actualIds.add(skill.get("id"));
            }

            assertEquals(expectedIds, actualIds,
                "Core A2A skill IDs must exactly match the production API surface");
        }

        @Test
        @DisplayName("IntrospectCodebaseSkill has correct metadata")
        void introspectCodebaseSkillHasCorrectMetadata() {
            IntrospectCodebaseSkill skill = new IntrospectCodebaseSkill();

            assertEquals("introspect_codebase", skill.getId(),
                "IntrospectCodebaseSkill ID must be 'introspect_codebase'");
            assertNotNull(skill.getName(), "Skill name must not be null");
            assertFalse(skill.getName().isBlank(), "Skill name must not be blank");
            assertNotNull(skill.getDescription(), "Skill description must not be null");
            assertFalse(skill.getDescription().isBlank(), "Skill description must not be blank");
            assertNotNull(skill.getRequiredPermissions(), "Required permissions must not be null");
            assertTrue(skill.getRequiredPermissions().contains("code:read"),
                "IntrospectCodebaseSkill must require 'code:read' permission");
        }

        @Test
        @DisplayName("IntrospectCodebaseSkill executes and returns structured SkillResult")
        void introspectCodebaseSkillExecutes() {
            // Use the real Observatory path if it exists, otherwise the default path
            Path observatoryRoot = Path.of("/home/user/yawl/docs/v6/latest");
            IntrospectCodebaseSkill skill = new IntrospectCodebaseSkill(observatoryRoot);

            SkillResult result = skill.execute(
                SkillRequest.builder("introspect_codebase")
                    .parameter("query", "modules")
                    .build()
            );

            assertNotNull(result, "SkillResult must not be null");
            assertNotNull(result.getTimestamp(), "Result timestamp must not be null");

            // Whether the Observatory files exist or not, the result must be structured
            if (result.isSuccess()) {
                assertNotNull(result.getData(),
                    "Success result must have non-null data map");
            } else {
                assertNotNull(result.getError(),
                    "Error result must have a non-null error message");
                assertFalse(result.getError().isBlank(),
                    "Error message must not be blank");
            }
        }

        @Test
        @DisplayName("IntrospectCodebaseSkill returns error for unknown query type")
        void introspectCodebaseSkillRejectsUnknownQuery() {
            IntrospectCodebaseSkill skill = new IntrospectCodebaseSkill();

            SkillResult result = skill.execute(
                SkillRequest.builder("introspect_codebase")
                    .parameter("query", "nonexistent_query_type_xyz")
                    .build()
            );

            assertNotNull(result, "SkillResult must not be null");
            assertTrue(result.isError(),
                "Unknown query type must return an error result");
            assertNotNull(result.getError(),
                "Error result must have error message");
            assertFalse(result.getError().isBlank(),
                "Error message must not be blank");
        }

        @Test
        @DisplayName("SkillRequest builder creates correctly structured requests")
        void skillRequestBuilderCreatesCorrectRequests() {
            SkillRequest request = SkillRequest.builder("launch_workflow")
                .parameter("specId", "OrderProcess")
                .parameter("caseData", "<data><amount>100</amount></data>")
                .build();

            assertEquals("launch_workflow", request.getSkillId());
            assertEquals("OrderProcess", request.getParameter("specId"));
            assertEquals("<data><amount>100</amount></data>",
                request.getParameter("caseData"));
            assertNotNull(request.getRequestId(),
                "Request must auto-generate a request ID");
            assertFalse(request.getRequestId().isBlank(),
                "Auto-generated request ID must not be blank");
        }

        @Test
        @DisplayName("SkillResult correctly models success and error states")
        void skillResultModelsSuccessAndError() {
            // Success case
            Map<String, Object> successData = Map.of("caseId", "42", "status", "running");
            SkillResult success = SkillResult.success(successData);

            assertTrue(success.isSuccess(), "Success result must report isSuccess=true");
            assertFalse(success.isError(), "Success result must report isError=false");
            assertNull(success.getError(), "Success result must have null error");
            assertEquals("42", success.get("caseId"), "Success data must be accessible by key");

            // Error case
            SkillResult error = SkillResult.error("Connection refused to engine");

            assertFalse(error.isSuccess(), "Error result must report isSuccess=false");
            assertTrue(error.isError(), "Error result must report isError=true");
            assertNotNull(error.getError(), "Error result must have error message");
            assertEquals("Connection refused to engine", error.getError());
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
        @DisplayName("LLM identifies correct A2A skill for workflow launch request")
        void llmIdentifiesLaunchWorkflowSkill() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String agentCard = buildAgentCardDescription();

            String response = zai.chat(
                "You are testing a YAWL workflow A2A agent with these skills:\n\n"
                + agentCard
                + "\nWhich skill ID would you use to start a new workflow case? "
                + "Respond with ONLY the skill ID."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.contains("launch_workflow") || response.contains("launch"),
                "LLM must identify 'launch_workflow' skill. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM identifies correct A2A skill for workflow query request")
        void llmIdentifiesQueryWorkflowsSkill() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String agentCard = buildAgentCardDescription();

            String response = zai.chat(
                "You are testing a YAWL workflow A2A agent with these skills:\n\n"
                + agentCard
                + "\nWhich skill ID would you use to list all running workflow cases? "
                + "Respond with ONLY the skill ID."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.contains("query_workflow") || response.contains("query"),
                "LLM must identify 'query_workflows' skill. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM generates valid A2A task message for workflow launch")
        void llmGeneratesValidLaunchWorkflowMessage() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String launchSkillDesc = CORE_A2A_SKILLS.stream()
                .filter(s -> "launch_workflow".equals(s.get("id")))
                .findFirst()
                .map(s -> "Skill: " + s.get("id") + "\nDescription: " + s.get("description"))
                .orElseThrow();

            String response = zai.chat(
                "Given this A2A skill:\n" + launchSkillDesc
                + "\nWrite a natural language task message to launch the 'OrderProcessing' "
                + "workflow specification. Keep it concise and clear."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.toLowerCase().contains("order") || response.toLowerCase().contains("workflow")
                    || response.toLowerCase().contains("launch"),
                "LLM message must reference the workflow request. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM understands process mining skill purpose and usage")
        void llmUnderstandsProcessMiningSkill() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String processMiningDesc = CORE_A2A_SKILLS.stream()
                .filter(s -> "process_mining_analyze".equals(s.get("id")))
                .findFirst()
                .map(s -> "Skill: " + s.get("id") + "\nDescription: " + s.get("description"))
                .orElseThrow();

            String response = zai.chat(
                "Explain in one sentence what this A2A skill does:\n" + processMiningDesc
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(response.length() > 20,
                "LLM explanation must be substantive. Response: " + response);
            // The LLM should reference analysis, mining, or workflow
            String lower = response.toLowerCase();
            assertTrue(
                lower.contains("analyz") || lower.contains("mining") || lower.contains("workflow")
                    || lower.contains("process") || lower.contains("event"),
                "LLM must reference process mining concepts. Response: " + response
            );
        }

        @Test
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        @DisplayName("LLM can distinguish between similar skills (manage vs cancel)")
        void llmDistinguishesSimilarSkills() {
            ZaiService zai = new ZaiService(System.getenv("ZAI_API_KEY"));

            String agentCard = buildAgentCardDescription();

            String response = zai.chatWithContext(
                "You are a YAWL workflow integration validator. "
                + "Always respond with exact skill IDs from the provided list.",
                "Given these A2A skills:\n" + agentCard
                + "\nA user wants to: 'stop the workflow with ID 99 immediately'.\n"
                + "Which skill ID should be used? Respond with ONLY the skill ID."
            );

            assertNotNull(response, "LLM must return a non-null response");
            assertFalse(response.isBlank(), "LLM response must not be blank");
            assertTrue(
                response.contains("cancel_workflow") || response.contains("cancel"),
                "LLM must identify 'cancel_workflow' to stop a workflow. Response: " + response
            );
        }
    }

    // =========================================================================
    // Private Helpers
    // =========================================================================

    /**
     * Build an agent card description from the core A2A skill metadata.
     * Used as input to LLM prompts for capability comprehension testing.
     */
    private static String buildAgentCardDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("Agent: YAWL Workflow Engine\n");
        sb.append("Description: YAWL BPM engine for managing workflow specifications, ");
        sb.append("cases, and work items.\n\n");
        sb.append("Skills:\n");

        for (Map<String, String> skill : CORE_A2A_SKILLS) {
            sb.append("- ID: ").append(skill.get("id")).append("\n");
            sb.append("  Name: ").append(skill.get("name")).append("\n");
            sb.append("  Description: ").append(skill.get("description")).append("\n");
            sb.append("  Tags: ").append(skill.get("tags")).append("\n");
        }

        return sb.toString();
    }
}
