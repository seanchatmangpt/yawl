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

package org.yawlfoundation.yawl.integration.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.execution.ExecutionMode;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for YawlPromptSpecifications.
 *
 * Tests all 4 MCP prompts with real prompt specifications and real MCP SDK types:
 * 1. workflow_analysis - arguments: spec_identifier (required), analysis_type (optional)
 * 2. task_completion_guide - arguments: work_item_id (required), context (optional)
 * 3. case_troubleshooting - arguments: case_id (required), symptom (optional)
 * 4. workflow_design_review - arguments: spec_identifier (required)
 *
 * Uses a test double for InterfaceB_EnvironmentBasedClient to avoid requiring a live
 * YAWL engine while still testing the real prompt handler logic.
 *
 * Coverage targets:
 * - Valid prompt generation with all arguments
 * - Invalid/missing required arguments
 * - Prompt template rendering and content structure
 * - Argument validation (required vs optional)
 * - Multi-step prompt workflows
 * - Edge cases (null values, empty strings, special characters)
 * - Concurrent prompt execution
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("unit")
@Execution(ExecutionMode.CONCURRENT)
class YawlPromptProviderTest {

    private TestInterfaceBClient testClient;
    private AtomicReference<String> sessionHandle;
    private Supplier<String> sessionSupplier;

    @BeforeEach
    void setUp(TestInfo testInfo) {
        testClient = new TestInterfaceBClient();
        sessionHandle = new AtomicReference<>("test-session-" + System.currentTimeMillis());
        sessionSupplier = sessionHandle::get;
    }

    // =========================================================================
    // Prompt Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Prompt Registration")
    @Execution(ExecutionMode.CONCURRENT)
    class PromptRegistrationTests {

        @Test
        @DisplayName("createAll returns exactly 4 prompt specifications")
        void testCreateAllReturnsFourPrompts() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            assertNotNull(prompts, "Prompt list should not be null");
            assertEquals(4, prompts.size(), "Should return exactly 4 prompts");
        }

        @Test
        @DisplayName("all prompts have non-null prompt definitions")
        void testAllPromptsHaveNonNullDefinitions() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            for (McpServerFeatures.SyncPromptSpecification spec : prompts) {
                assertNotNull(spec.prompt(), "Prompt definition should not be null");
            }
        }

        @Test
        @DisplayName("all prompts have unique names")
        void testAllPromptsHaveUniqueNames() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            List<String> names = prompts.stream()
                .map(p -> p.prompt().name())
                .toList();

            assertEquals(4, names.size(), "Should have 4 unique names");
            assertEquals(4, names.stream().distinct().count(),
                "All prompt names should be unique");
        }

        @Test
        @DisplayName("all prompts have non-empty descriptions")
        void testAllPromptsHaveNonEmptyDescriptions() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            for (McpServerFeatures.SyncPromptSpecification spec : prompts) {
                assertNotNull(spec.prompt().description(),
                    "Prompt description should not be null");
                assertFalse(spec.prompt().description().isEmpty(),
                    "Prompt description should not be empty");
            }
        }

        @Test
        @DisplayName("all prompts have non-null handlers")
        void testAllPromptsHaveNonNullHandlers() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            for (McpServerFeatures.SyncPromptSpecification spec : prompts) {
                assertNotNull(spec.handler(),
                    "Prompt handler should not be null");
            }
        }
    }

    // =========================================================================
    // workflow_analysis Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("workflow_analysis Prompt")
    @Execution(ExecutionMode.CONCURRENT)
    class WorkflowAnalysisPromptTests {

        private McpServerFeatures.SyncPromptSpecification workflowAnalysisPrompt;

        @BeforeEach
        void setUp() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);
            workflowAnalysisPrompt = prompts.stream()
                .filter(p -> "workflow_analysis".equals(p.prompt().name()))
                .findFirst()
                .orElseThrow();
        }

        @Test
        @DisplayName("has correct prompt name")
        void testPromptName() {
            assertEquals("workflow_analysis", workflowAnalysisPrompt.prompt().name());
        }

        @Test
        @DisplayName("has spec_identifier as required argument")
        void testRequiredArgument() {
            List<McpSchema.PromptArgument> args = workflowAnalysisPrompt.prompt().arguments();

            McpSchema.PromptArgument specIdArg = args.stream()
                .filter(a -> "spec_identifier".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertTrue(specIdArg.required(),
                "spec_identifier should be required");
        }

        @Test
        @DisplayName("has analysis_type as optional argument")
        void testOptionalArgument() {
            List<McpSchema.PromptArgument> args = workflowAnalysisPrompt.prompt().arguments();

            McpSchema.PromptArgument analysisTypeArg = args.stream()
                .filter(a -> "analysis_type".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertFalse(analysisTypeArg.required(),
                "analysis_type should be optional");
        }

        @Test
        @DisplayName("generates valid prompt with all arguments")
        void testValidPromptGeneration() {
            testClient.addSpecification(createTestSpec("test-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "test-spec");
            args.put("analysis_type", "performance");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            McpSchema.GetPromptResult result = workflowAnalysisPrompt.handler()
                .apply(null, request);

            assertNotNull(result, "Result should not be null");
            assertNotNull(result.description(), "Result description should not be null");
            assertTrue(result.description().contains("test-spec"),
                "Description should contain spec identifier");
            assertEquals(1, result.messages().size(),
                "Should have exactly one message");
            assertEquals(McpSchema.Role.USER, result.messages().get(0).role(),
                "Message should be from USER");
        }

        @Test
        @DisplayName("prompt text contains analysis type focus")
        void testPromptTextContainsAnalysisType() {
            testClient.addSpecification(createTestSpec("my-spec", "2.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "my-spec");
            args.put("analysis_type", "optimization");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            McpSchema.GetPromptResult result = workflowAnalysisPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("optimization"),
                "Prompt text should contain the analysis type");
            assertTrue(content.text().contains("Key observations"),
                "Prompt should request key observations");
            assertTrue(content.text().contains("recommendations"),
                "Prompt should request recommendations");
        }

        @Test
        @DisplayName("uses general analysis type when not specified")
        void testDefaultAnalysisType() {
            testClient.addSpecification(createTestSpec("default-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "default-spec");
            // No analysis_type provided

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            McpSchema.GetPromptResult result = workflowAnalysisPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("general"),
                "Should use 'general' as default analysis type");
        }

        @Test
        @DisplayName("throws when spec_identifier is missing")
        void testMissingRequiredArgument() {
            Map<String, Object> args = new HashMap<>();
            // No spec_identifier

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> workflowAnalysisPrompt.handler().apply(null, request));

            assertTrue(exception.getMessage().contains("spec_identifier"),
                "Exception message should mention the missing argument");
        }

        @Test
        @DisplayName("handles specification not found gracefully")
        void testSpecificationNotFound() {
            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "non-existent-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            // Should not throw - returns prompt with "not found" message
            McpSchema.GetPromptResult result = workflowAnalysisPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("not found"),
                "Prompt should indicate specification was not found");
        }

        @Test
        @DisplayName("handles null spec_identifier")
        void testNullSpecIdentifier() {
            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", null);

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> workflowAnalysisPrompt.handler().apply(null, request));

            assertNotNull(exception.getMessage());
        }
    }

    // =========================================================================
    // task_completion_guide Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("task_completion_guide Prompt")
    @Execution(ExecutionMode.CONCURRENT)
    class TaskCompletionGuidePromptTests {

        private McpServerFeatures.SyncPromptSpecification taskCompletionPrompt;

        @BeforeEach
        void setUp() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);
            taskCompletionPrompt = prompts.stream()
                .filter(p -> "task_completion_guide".equals(p.prompt().name()))
                .findFirst()
                .orElseThrow();
        }

        @Test
        @DisplayName("has correct prompt name")
        void testPromptName() {
            assertEquals("task_completion_guide", taskCompletionPrompt.prompt().name());
        }

        @Test
        @DisplayName("has work_item_id as required argument")
        void testRequiredArgument() {
            List<McpSchema.PromptArgument> args = taskCompletionPrompt.prompt().arguments();

            McpSchema.PromptArgument workItemIdArg = args.stream()
                .filter(a -> "work_item_id".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertTrue(workItemIdArg.required(),
                "work_item_id should be required");
        }

        @Test
        @DisplayName("has context as optional argument")
        void testOptionalArgument() {
            List<McpSchema.PromptArgument> args = taskCompletionPrompt.prompt().arguments();

            McpSchema.PromptArgument contextArg = args.stream()
                .filter(a -> "context".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertFalse(contextArg.required(),
                "context should be optional");
        }

        @Test
        @DisplayName("generates valid prompt with work item data")
        void testValidPromptGeneration() {
            testClient.setWorkItemData("wi-12345", createTestWorkItemXml("wi-12345", "ApproveTask"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-12345");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = taskCompletionPrompt.handler()
                .apply(null, request);

            assertNotNull(result);
            assertTrue(result.description().contains("wi-12345"),
                "Description should contain work item ID");
        }

        @Test
        @DisplayName("prompt includes checkout/checkin tool references")
        void testPromptIncludesToolReferences() {
            testClient.setWorkItemData("wi-tool-test", createTestWorkItemXml("wi-tool-test", "ReviewTask"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-tool-test");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = taskCompletionPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("yawl_checkout_work_item"),
                "Prompt should mention checkout tool");
            assertTrue(content.text().contains("yawl_checkin_work_item"),
                "Prompt should mention checkin tool");
        }

        @Test
        @DisplayName("includes context when provided")
        void testPromptWithAdditionalContext() {
            testClient.setWorkItemData("wi-ctx", createTestWorkItemXml("wi-ctx", "DataTask"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-ctx");
            args.put("context", "This is an urgent approval request");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = taskCompletionPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("urgent approval request"),
                "Prompt should include the provided context");
        }

        @Test
        @DisplayName("throws when work_item_id is missing")
        void testMissingRequiredArgument() {
            Map<String, Object> args = new HashMap<>();
            // No work_item_id

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> taskCompletionPrompt.handler().apply(null, request));

            assertTrue(exception.getMessage().contains("work_item_id"),
                "Exception message should mention the missing argument");
        }

        @Test
        @DisplayName("handles work item not found")
        void testWorkItemNotFound() {
            testClient.setWorkItemData("wi-404", null); // Simulate not found

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-404");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = taskCompletionPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("not found"),
                "Prompt should indicate work item was not found");
        }

        @Test
        @DisplayName("includes data validation requirements in prompt")
        void testPromptIncludesDataValidation() {
            testClient.setWorkItemData("wi-validation",
                createTestWorkItemXml("wi-validation", "ValidateTask"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-validation");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = taskCompletionPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("validation"),
                "Prompt should mention validation requirements");
            assertTrue(content.text().contains("XML"),
                "Prompt should mention XML format");
        }
    }

    // =========================================================================
    // case_troubleshooting Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("case_troubleshooting Prompt")
    @Execution(ExecutionMode.CONCURRENT)
    class CaseTroubleshootingPromptTests {

        private McpServerFeatures.SyncPromptSpecification caseTroubleshootingPrompt;

        @BeforeEach
        void setUp() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);
            caseTroubleshootingPrompt = prompts.stream()
                .filter(p -> "case_troubleshooting".equals(p.prompt().name()))
                .findFirst()
                .orElseThrow();
        }

        @Test
        @DisplayName("has correct prompt name")
        void testPromptName() {
            assertEquals("case_troubleshooting", caseTroubleshootingPrompt.prompt().name());
        }

        @Test
        @DisplayName("has case_id as required argument")
        void testRequiredArgument() {
            List<McpSchema.PromptArgument> args = caseTroubleshootingPrompt.prompt().arguments();

            McpSchema.PromptArgument caseIdArg = args.stream()
                .filter(a -> "case_id".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertTrue(caseIdArg.required(),
                "case_id should be required");
        }

        @Test
        @DisplayName("has symptom as optional argument")
        void testOptionalArgument() {
            List<McpSchema.PromptArgument> args = caseTroubleshootingPrompt.prompt().arguments();

            McpSchema.PromptArgument symptomArg = args.stream()
                .filter(a -> "symptom".equals(a.name()))
                .findFirst()
                .orElseThrow();

            assertFalse(symptomArg.required(),
                "symptom should be optional");
        }

        @Test
        @DisplayName("generates valid prompt with case data")
        void testValidPromptGeneration() {
            testClient.setCaseState("case-100", "running");
            testClient.setWorkItemsForCase("case-100", List.of(
                createTestWorkItemRecord("case-100", "Task1", "active"),
                createTestWorkItemRecord("case-100", "Task2", "suspended")
            ));

            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-100");
            args.put("symptom", "Case appears stuck at Task2");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = caseTroubleshootingPrompt.handler()
                .apply(null, request);

            assertNotNull(result);
            assertTrue(result.description().contains("case-100"),
                "Description should contain case ID");
        }

        @Test
        @DisplayName("includes diagnostic steps in prompt")
        void testPromptIncludesDiagnosticSteps() {
            testClient.setCaseState("case-diag", "suspended");
            testClient.setWorkItemsForCase("case-diag", Collections.emptyList());

            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-diag");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = caseTroubleshootingPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Diagnostic steps"),
                "Prompt should include diagnostic steps");
            assertTrue(content.text().contains("blocked") || content.text().contains("stuck"),
                "Prompt should mention blocked or stuck items");
            assertTrue(content.text().contains("resolution steps"),
                "Prompt should request resolution steps");
        }

        @Test
        @DisplayName("uses default symptom when not provided")
        void testDefaultSymptom() {
            testClient.setCaseState("case-default", "unknown");
            testClient.setWorkItemsForCase("case-default", Collections.emptyList());

            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-default");
            // No symptom provided

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = caseTroubleshootingPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("stuck") || content.text().contains("not progressing"),
                "Should use default symptom about case being stuck");
        }

        @Test
        @DisplayName("throws when case_id is missing")
        void testMissingRequiredArgument() {
            Map<String, Object> args = new HashMap<>();
            // No case_id

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> caseTroubleshootingPrompt.handler().apply(null, request));

            assertTrue(exception.getMessage().contains("case_id"),
                "Exception message should mention the missing argument");
        }

        @Test
        @DisplayName("handles case with multiple work items")
        void testCaseWithMultipleWorkItems() {
            testClient.setCaseState("case-multi", "running");
            testClient.setWorkItemsForCase("case-multi", List.of(
                createTestWorkItemRecord("case-multi", "StartTask", "completed"),
                createTestWorkItemRecord("case-multi", "ProcessTask", "active"),
                createTestWorkItemRecord("case-multi", "ReviewTask", "fired")
            ));

            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-multi");
            args.put("symptom", "Multiple tasks pending");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = caseTroubleshootingPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Work Items: 3"),
                "Prompt should show count of work items");
        }

        @Test
        @DisplayName("includes preventive measures in prompt")
        void testPromptIncludesPreventiveMeasures() {
            testClient.setCaseState("case-prev", "completed");
            testClient.setWorkItemsForCase("case-prev", Collections.emptyList());

            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-prev");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = caseTroubleshootingPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("preventive measures"),
                "Prompt should request preventive measures");
        }
    }

    // =========================================================================
    // workflow_design_review Prompt Tests
    // =========================================================================

    @Nested
    @DisplayName("workflow_design_review Prompt")
    @Execution(ExecutionMode.CONCURRENT)
    class WorkflowDesignReviewPromptTests {

        private McpServerFeatures.SyncPromptSpecification designReviewPrompt;

        @BeforeEach
        void setUp() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);
            designReviewPrompt = prompts.stream()
                .filter(p -> "workflow_design_review".equals(p.prompt().name()))
                .findFirst()
                .orElseThrow();
        }

        @Test
        @DisplayName("has correct prompt name")
        void testPromptName() {
            assertEquals("workflow_design_review", designReviewPrompt.prompt().name());
        }

        @Test
        @DisplayName("has spec_identifier as only required argument")
        void testRequiredArgument() {
            List<McpSchema.PromptArgument> args = designReviewPrompt.prompt().arguments();

            assertEquals(1, args.size(), "Should have exactly one argument");
            assertEquals("spec_identifier", args.get(0).name());
            assertTrue(args.get(0).required(), "spec_identifier should be required");
        }

        @Test
        @DisplayName("generates valid prompt for design review")
        void testValidPromptGeneration() {
            testClient.addSpecification(createTestSpec("review-spec", "3.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "review-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            assertNotNull(result);
            assertTrue(result.description().contains("review-spec"),
                "Description should contain spec identifier");
        }

        @Test
        @DisplayName("includes YAWL pattern review criteria")
        void testPromptIncludesPatternCriteria() {
            testClient.addSpecification(createTestSpec("pattern-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "pattern-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("YAWL pattern"),
                "Prompt should mention YAWL patterns");
            assertTrue(content.text().contains("choice") || content.text().contains("parallel"),
                "Prompt should mention specific patterns");
        }

        @Test
        @DisplayName("includes resource allocation review")
        void testPromptIncludesResourceAllocation() {
            testClient.addSpecification(createTestSpec("resource-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "resource-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Resource allocation"),
                "Prompt should include resource allocation criteria");
        }

        @Test
        @DisplayName("includes exception handling review")
        void testPromptIncludesExceptionHandling() {
            testClient.addSpecification(createTestSpec("exception-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "exception-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Exception handling"),
                "Prompt should include exception handling criteria");
        }

        @Test
        @DisplayName("includes data flow correctness review")
        void testPromptIncludesDataFlow() {
            testClient.addSpecification(createTestSpec("dataflow-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "dataflow-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Data flow"),
                "Prompt should include data flow correctness criteria");
            assertTrue(content.text().contains("XPath"),
                "Prompt should mention XPath expressions");
        }

        @Test
        @DisplayName("includes cancellation region design review")
        void testPromptIncludesCancellationRegions() {
            testClient.addSpecification(createTestSpec("cancel-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "cancel-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("Cancellation"),
                "Prompt should include cancellation region criteria");
        }

        @Test
        @DisplayName("throws when spec_identifier is missing")
        void testMissingRequiredArgument() {
            Map<String, Object> args = new HashMap<>();
            // No spec_identifier

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            RuntimeException exception = assertThrows(RuntimeException.class,
                () -> designReviewPrompt.handler().apply(null, request));

            assertTrue(exception.getMessage().contains("spec_identifier"),
                "Exception message should mention the missing argument");
        }

        @Test
        @DisplayName("handles specification not found gracefully")
        void testSpecificationNotFound() {
            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "non-existent-design-spec");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_design_review", args);

            McpSchema.GetPromptResult result = designReviewPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("not found"),
                "Prompt should indicate specification was not found");
        }
    }

    // =========================================================================
    // Multi-Step Workflow Tests
    // =========================================================================

    @Nested
    @DisplayName("Multi-Step Prompt Workflows")
    @Execution(ExecutionMode.CONCURRENT)
    class MultiStepWorkflowTests {

        @Test
        @DisplayName("can chain workflow_analysis to task_completion_guide")
        void testAnalysisToCompletionChain() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification analysisPrompt = prompts.stream()
                .filter(p -> "workflow_analysis".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            McpServerFeatures.SyncPromptSpecification completionPrompt = prompts.stream()
                .filter(p -> "task_completion_guide".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            testClient.addSpecification(createTestSpec("chain-spec", "1.0.0"));

            // Step 1: Analyze workflow
            Map<String, Object> analysisArgs = new HashMap<>();
            analysisArgs.put("spec_identifier", "chain-spec");
            analysisArgs.put("analysis_type", "performance");

            McpSchema.GetPromptResult analysisResult = analysisPrompt.handler()
                .apply(null, new McpSchema.GetPromptRequest("workflow_analysis", analysisArgs));

            assertNotNull(analysisResult, "Analysis prompt should return result");

            // Step 2: Get completion guide for a work item from the analyzed spec
            testClient.setWorkItemData("wi-chain", createTestWorkItemXml("wi-chain", "ChainTask"));

            Map<String, Object> completionArgs = new HashMap<>();
            completionArgs.put("work_item_id", "wi-chain");
            completionArgs.put("context", "Based on performance analysis");

            McpSchema.GetPromptResult completionResult = completionPrompt.handler()
                .apply(null, new McpSchema.GetPromptRequest("task_completion_guide", completionArgs));

            assertNotNull(completionResult, "Completion prompt should return result");
            assertTrue(((McpSchema.TextContent) completionResult.messages().get(0).content())
                .text().contains("performance analysis"),
                "Context from analysis should be included");
        }

        @Test
        @DisplayName("can sequence troubleshooting to design review")
        void testTroubleshootingToDesignReviewChain() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification troubleshootingPrompt = prompts.stream()
                .filter(p -> "case_troubleshooting".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            McpServerFeatures.SyncPromptSpecification designReviewPrompt = prompts.stream()
                .filter(p -> "workflow_design_review".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            // Step 1: Troubleshoot a case
            testClient.setCaseState("case-chain", "failed");
            testClient.setWorkItemsForCase("case-chain", List.of(
                createTestWorkItemRecord("case-chain", "FailingTask", "failed")
            ));

            Map<String, Object> troubleshootArgs = new HashMap<>();
            troubleshootArgs.put("case_id", "case-chain");
            troubleshootArgs.put("symptom", "Task failed unexpectedly");

            McpSchema.GetPromptResult troubleshootResult = troubleshootingPrompt.handler()
                .apply(null, new McpSchema.GetPromptRequest("case_troubleshooting", troubleshootArgs));

            assertNotNull(troubleshootResult, "Troubleshooting prompt should return result");

            // Step 2: Review the design for potential issues
            testClient.addSpecification(createTestSpec("troubled-spec", "1.0.0"));

            Map<String, Object> designArgs = new HashMap<>();
            designArgs.put("spec_identifier", "troubled-spec");

            McpSchema.GetPromptResult designResult = designReviewPrompt.handler()
                .apply(null, new McpSchema.GetPromptRequest("workflow_design_review", designArgs));

            assertNotNull(designResult, "Design review prompt should return result");
        }

        @Test
        @DisplayName("all prompts can be invoked in sequence")
        void testAllPromptsInSequence() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            testClient.addSpecification(createTestSpec("seq-spec", "1.0.0"));
            testClient.setWorkItemData("wi-seq", createTestWorkItemXml("wi-seq", "SeqTask"));
            testClient.setCaseState("case-seq", "running");
            testClient.setWorkItemsForCase("case-seq", Collections.emptyList());

            List<McpSchema.GetPromptResult> results = new ArrayList<>();

            for (McpServerFeatures.SyncPromptSpecification prompt : prompts) {
                Map<String, Object> args = new HashMap<>();

                switch (prompt.prompt().name()) {
                    case "workflow_analysis" -> {
                        args.put("spec_identifier", "seq-spec");
                    }
                    case "task_completion_guide" -> {
                        args.put("work_item_id", "wi-seq");
                    }
                    case "case_troubleshooting" -> {
                        args.put("case_id", "case-seq");
                    }
                    case "workflow_design_review" -> {
                        args.put("spec_identifier", "seq-spec");
                    }
                }

                McpSchema.GetPromptResult result = prompt.handler()
                    .apply(null, new McpSchema.GetPromptRequest(prompt.prompt().name(), args));
                results.add(result);
            }

            assertEquals(4, results.size(), "All 4 prompts should return results");
            for (McpSchema.GetPromptResult result : results) {
                assertNotNull(result, "Each result should not be null");
                assertEquals(1, result.messages().size(), "Each result should have 1 message");
            }
        }
    }

    // =========================================================================
    // Argument Validation Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Argument Validation Edge Cases")
    @Execution(ExecutionMode.CONCURRENT)
    class ArgumentValidationEdgeCasesTests {

        @Test
        @DisplayName("handles empty string arguments")
        void testEmptyStringArguments() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification analysisPrompt = prompts.stream()
                .filter(p -> "workflow_analysis".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            // Empty string is technically a value, should not throw but may not find spec
            assertDoesNotThrow(() -> analysisPrompt.handler().apply(null, request));
        }

        @Test
        @DisplayName("handles special characters in arguments")
        void testSpecialCharactersInArguments() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification completionPrompt = prompts.stream()
                .filter(p -> "task_completion_guide".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            testClient.setWorkItemData("wi-special-<>&\"'",
                createTestWorkItemXml("wi-special-<>&\"'", "Task"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-special-<>&\"'");
            args.put("context", "Context with <xml> & \"quotes\" and 'apostrophes'");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = completionPrompt.handler()
                .apply(null, request);

            assertNotNull(result, "Should handle special characters without error");
        }

        @Test
        @DisplayName("handles very long argument values")
        void testLongArgumentValues() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification troubleshootingPrompt = prompts.stream()
                .filter(p -> "case_troubleshooting".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            testClient.setCaseState("case-long", "running");
            testClient.setWorkItemsForCase("case-long", Collections.emptyList());

            String longSymptom = "a".repeat(10000);
            Map<String, Object> args = new HashMap<>();
            args.put("case_id", "case-long");
            args.put("symptom", longSymptom);

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "case_troubleshooting", args);

            McpSchema.GetPromptResult result = troubleshootingPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains(longSymptom),
                "Should include full long symptom in prompt");
        }

        @Test
        @DisplayName("handles unicode in arguments")
        void testUnicodeInArguments() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification completionPrompt = prompts.stream()
                .filter(p -> "task_completion_guide".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            testClient.setWorkItemData("wi-unicode", createTestWorkItemXml("wi-unicode", "Task"));

            Map<String, Object> args = new HashMap<>();
            args.put("work_item_id", "wi-unicode");
            args.put("context", "Unicode: \u4e2d\u6587 \u0420\u0443\u0441\u0441\u043a\u0438\u0439 \u65e5\u672c\u8a9e");

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "task_completion_guide", args);

            McpSchema.GetPromptResult result = completionPrompt.handler()
                .apply(null, request);

            McpSchema.TextContent content =
                (McpSchema.TextContent) result.messages().get(0).content();

            assertTrue(content.text().contains("\u4e2d\u6587"),
                "Should preserve Chinese characters");
        }

        @Test
        @DisplayName("handles extra unexpected arguments gracefully")
        void testExtraArgumentsIgnored() {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            McpServerFeatures.SyncPromptSpecification analysisPrompt = prompts.stream()
                .filter(p -> "workflow_analysis".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            testClient.addSpecification(createTestSpec("extra-args-spec", "1.0.0"));

            Map<String, Object> args = new HashMap<>();
            args.put("spec_identifier", "extra-args-spec");
            args.put("unexpected_arg", "some value");
            args.put("another_extra", 12345);

            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(
                "workflow_analysis", args);

            // Extra arguments should be ignored, not cause errors
            assertDoesNotThrow(() -> analysisPrompt.handler().apply(null, request));
        }
    }

    // =========================================================================
    // Concurrent Execution Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Execution")
    @Execution(ExecutionMode.CONCURRENT)
    class ConcurrentExecutionTests {

        @Test
        @DisplayName("handles concurrent prompt requests safely")
        void testConcurrentPromptRequests() throws Exception {
            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, sessionSupplier);

            testClient.addSpecification(createTestSpec("concurrent-spec", "1.0.0"));
            testClient.setCaseState("concurrent-case", "running");
            testClient.setWorkItemsForCase("concurrent-case", Collections.emptyList());
            testClient.setWorkItemData("concurrent-wi", createTestWorkItemXml("concurrent-wi", "Task"));

            int numThreads = 20;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            List<Future<McpSchema.GetPromptResult>> futures = new ArrayList<>();

            for (int i = 0; i < numThreads; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();

                        McpServerFeatures.SyncPromptSpecification prompt = prompts.get(index % 4);
                        Map<String, Object> args = new HashMap<>();

                        switch (prompt.prompt().name()) {
                            case "workflow_analysis", "workflow_design_review" ->
                                args.put("spec_identifier", "concurrent-spec");
                            case "task_completion_guide" ->
                                args.put("work_item_id", "concurrent-wi");
                            case "case_troubleshooting" ->
                                args.put("case_id", "concurrent-case");
                        }

                        return prompt.handler().apply(null,
                            new McpSchema.GetPromptRequest(prompt.prompt().name(), args));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }));
            }

            int successCount = 0;
            for (Future<McpSchema.GetPromptResult> future : futures) {
                try {
                    McpSchema.GetPromptResult result = future.get(10, TimeUnit.SECONDS);
                    if (result != null && result.messages() != null) {
                        successCount++;
                    }
                } catch (Exception e) {
                    // Log but don't fail - we want to verify thread safety
                }
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

            assertEquals(numThreads, successCount,
                "All concurrent requests should succeed");
        }

        @Test
        @DisplayName("session supplier is called per request")
        void testSessionSupplierCalledPerRequest() {
            AtomicInteger callCount = new AtomicInteger(0);
            Supplier<String> countingSupplier = () -> {
                callCount.incrementAndGet();
                return "session-" + callCount.get();
            };

            List<McpServerFeatures.SyncPromptSpecification> prompts =
                YawlPromptSpecifications.createAll(testClient, countingSupplier);

            testClient.addSpecification(createTestSpec("session-spec", "1.0.0"));

            McpServerFeatures.SyncPromptSpecification analysisPrompt = prompts.stream()
                .filter(p -> "workflow_analysis".equals(p.prompt().name()))
                .findFirst().orElseThrow();

            for (int i = 0; i < 5; i++) {
                Map<String, Object> args = new HashMap<>();
                args.put("spec_identifier", "session-spec");

                analysisPrompt.handler().apply(null,
                    new McpSchema.GetPromptRequest("workflow_analysis", args));
            }

            assertTrue(callCount.get() >= 5,
                "Session supplier should be called at least once per request");
        }
    }

    // =========================================================================
    // Helper Methods and Test Data
    // =========================================================================

    private SpecificationData createTestSpec(String identifier, String version) {
        YSpecificationID specId = new YSpecificationID(identifier, version, "http://test.yawl/" + identifier);
        SpecificationData spec = new SpecificationData();
        spec.setID(specId);
        spec.setName("Test Specification: " + identifier);
        spec.setDocumentation("Test documentation for " + identifier);
        spec.setRootNetID("Net_" + identifier);
        return spec;
    }

    private String createTestWorkItemXml(String workItemId, String taskName) {
        return """
            <workItem>
              <id>%s</id>
              <taskID>%s</taskID>
              <status>active</status>
              <data><input>test data</input></data>
            </workItem>
            """.formatted(workItemId, taskName);
    }

    private WorkItemRecord createTestWorkItemRecord(String caseId, String taskId, String status) {
        WorkItemRecord wir = new WorkItemRecord();
        // WorkItemRecord.getID() returns caseID:taskID, so we need to set both
        // The workItemId is conceptually caseId:taskId
        wir.setCaseID(caseId);
        wir.setTaskID(taskId);
        wir.setStatus(status);
        return wir;
    }

    // =========================================================================
    // Test Double for InterfaceB_EnvironmentBasedClient
    // =========================================================================

    /**
     * Test implementation of InterfaceB_EnvironmentBasedClient that provides
     * controlled test data without requiring a live YAWL engine.
     *
     * This is NOT a mock - it's a test double that simulates real YAWL engine
     * behavior with predictable data for testing.
     */
    private static class TestInterfaceBClient extends InterfaceB_EnvironmentBasedClient {

        private final List<SpecificationData> specifications = new ArrayList<>();
        private final Map<String, String> workItemData = new ConcurrentHashMap<>();
        private final Map<String, String> caseStates = new ConcurrentHashMap<>();
        private final Map<String, List<WorkItemRecord>> caseWorkItems = new ConcurrentHashMap<>();

        TestInterfaceBClient() {
            super("http://test.local/yawl/ib");
        }

        void addSpecification(SpecificationData spec) {
            specifications.add(spec);
        }

        void setWorkItemData(String workItemId, String data) {
            workItemData.put(workItemId, data);
        }

        void setCaseState(String caseId, String state) {
            caseStates.put(caseId, state);
        }

        void setWorkItemsForCase(String caseId, List<WorkItemRecord> items) {
            caseWorkItems.put(caseId, items);
        }

        @Override
        public List<SpecificationData> getSpecificationList(String sessionHandle) throws IOException {
            return new ArrayList<>(specifications);
        }

        @Override
        public String getWorkItem(String workItemId, String sessionHandle) throws IOException {
            return workItemData.getOrDefault(workItemId, null);
        }

        @Override
        public String getCaseState(String caseId, String sessionHandle) throws IOException {
            return caseStates.getOrDefault(caseId, "unknown");
        }

        @Override
        public List<WorkItemRecord> getWorkItemsForCase(String caseId, String sessionHandle) throws IOException {
            return caseWorkItems.getOrDefault(caseId, Collections.emptyList());
        }

        @Override
        public String connect(String username, String password) throws IOException {
            return "test-session-handle";
        }

        @Override
        public void disconnect(String sessionHandle) throws IOException {
            // No-op for test
        }
    }
}
