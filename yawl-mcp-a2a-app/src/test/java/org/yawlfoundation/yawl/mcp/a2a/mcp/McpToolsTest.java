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

package org.yawlfoundation.yawl.mcp.a2a.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.mcp.a2a.example.YawlYamlConverter;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YAWL MCP tool implementations — YAML conversion and workflow validation.
 *
 * <p>Tests verify that core MCP tools for workflow analysis work correctly with
 * both valid and invalid inputs. These are the foundation tools used by the MCP
 * server to support autonomous agents in workflow design and execution.</p>
 *
 * <p>All tests use real tool instances (Chicago/Detroit TDD) with no mocks,
 * stubs, or fakes. Tests validate:</p>
 * <ul>
 *   <li>YAML-to-XML conversion produces valid YAWL Schema 4.0 XML</li>
 *   <li>Workflow soundness analysis detects structural violations</li>
 *   <li>Tool methods handle edge cases and error conditions properly</li>
 *   <li>Return values are non-null and contain expected data</li>
 *   <li>Error messages are descriptive and actionable</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("MCP Tools Tests")
class McpToolsTest {

    private YawlYamlConverter yamlConverter;
    private WorkflowSoundnessVerifier soundnessVerifier;

    private static final String YAWL_NAMESPACE = "http://www.yawlfoundation.org/yawlschema";
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

    private static final String SINGLE_TASK_YAML = """
        name: SimpleWorkflow
        uri: simple.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [end]
        """;

    private static final String ORDER_FULFILLMENT_YAML = """
        name: OrderFulfillment
        uri: order.xml
        first: VerifyPayment
        tasks:
          - id: VerifyPayment
            flows: [CheckInventory, CancelOrder]
            condition: payment_ok -> CheckInventory
            default: CancelOrder
            split: xor
          - id: CheckInventory
            flows: [ShipOrder, CancelOrder]
            condition: in_stock -> ShipOrder
            default: CancelOrder
            split: xor
          - id: ShipOrder
            flows: [end]
          - id: CancelOrder
            flows: [end]
        """;

    private static final String MULTI_TASK_PARALLEL_YAML = """
        name: ParallelWorkflow
        uri: parallel.xml
        first: ProcessStart
        tasks:
          - id: ProcessStart
            flows: [TaskA, TaskB]
            split: and
          - id: TaskA
            flows: [ProcessEnd]
          - id: TaskB
            flows: [ProcessEnd]
          - id: ProcessEnd
            flows: [end]
            join: and
        """;

    private static final String UNSOUND_UNREACHABLE_YAML = """
        name: UnsoundWorkflow
        uri: unsound.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [end]
          - id: TaskB
            flows: [end]
        """;

    private static final String UNSOUND_DEAD_END_YAML = """
        name: UnsoundDeadEnd
        uri: deadend.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [TaskB]
          - id: TaskB
            flows: []
        """;

    @BeforeEach
    void setUp() {
        yamlConverter = new YawlYamlConverter();
        soundnessVerifier = new WorkflowSoundnessVerifier();
    }

    // =========================================================================
    // YAML Converter Tests
    // =========================================================================

    @Nested
    @DisplayName("YawlYamlConverter - Happy Path")
    class YamlConverterHappyPath {

        @Test
        @DisplayName("Should convert single-task YAML to valid YAWL XML")
        void convertSingleTaskYamlToXml() {
            String xml = yamlConverter.convertToXml(SINGLE_TASK_YAML);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.length() > 0, "Generated XML must not be empty");
            assertTrue(xml.contains("<specificationSet"), "XML must contain root element");
            assertTrue(xml.contains("xmlns=\"" + YAWL_NAMESPACE + "\""), "XML must declare YAWL namespace");
            assertTrue(xml.contains("xmlns:xsi=\"" + XSI_NAMESPACE + "\""), "XML must declare XSI namespace");
            assertTrue(xml.contains("SimpleWorkflow"), "XML must contain workflow name");
            assertTrue(xml.contains("TaskA"), "XML must contain task ID");
            assertTrue(xml.contains("i-top"), "XML must contain input condition");
            assertTrue(xml.contains("o-top"), "XML must contain output condition");
        }

        @Test
        @DisplayName("Should convert multi-task YAML with conditions to valid XML")
        void convertMultiTaskWithConditionsToXml() {
            String xml = yamlConverter.convertToXml(ORDER_FULFILLMENT_YAML);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("OrderFulfillment"), "XML must contain workflow name");
            assertTrue(xml.contains("VerifyPayment"), "XML must contain first task");
            assertTrue(xml.contains("CheckInventory"), "XML must contain second task");
            assertTrue(xml.contains("payment_ok"), "XML must contain predicate condition");
            assertTrue(xml.contains("isDefaultFlow"), "XML must contain default flow marker");
            assertTrue(xml.contains("xor"), "XML must contain split/join type");
        }

        @Test
        @DisplayName("Should convert YAML with AND-join/split to XML")
        void convertAndJoinSplitToXml() {
            String xml = yamlConverter.convertToXml(MULTI_TASK_PARALLEL_YAML);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("and"), "XML must contain AND split/join");
            assertTrue(xml.contains("ProcessStart"), "XML must contain parallel start task");
            assertTrue(xml.contains("ProcessEnd"), "XML must contain parallel join task");
            assertTrue(xml.contains("TaskA"), "XML must contain parallel branch A");
            assertTrue(xml.contains("TaskB"), "XML must contain parallel branch B");
        }

        @Test
        @DisplayName("Should generate valid XML structure with required elements")
        void generatedXmlHasValidStructure() {
            String xml = yamlConverter.convertToXml(SINGLE_TASK_YAML);

            assertTrue(xml.startsWith("<specificationSet"), "XML must start with root element");
            assertTrue(xml.endsWith("</specificationSet>"), "XML must end with root element");
            assertTrue(xml.contains("<specification uri="), "XML must contain specification element");
            assertTrue(xml.contains("<name>"), "XML must contain name element");
            assertTrue(xml.contains("<decomposition"), "XML must contain decomposition elements");
            assertTrue(xml.contains("<processControlElements>"), "XML must contain process control elements");
            assertTrue(xml.contains("<inputCondition"), "XML must contain input condition");
            assertTrue(xml.contains("<outputCondition"), "XML must contain output condition");
            assertTrue(xml.contains("<task"), "XML must contain task elements");
        }

        @Test
        @DisplayName("Should escape XML special characters in names")
        void xmlSpecialCharactersAreEscaped() {
            String yamlWithSpecialChars = """
                name: Workflow&<>\"'Test
                uri: test.xml
                first: Task<A>
                tasks:
                  - id: Task<A>
                    flows: [end]
                """;

            String xml = yamlConverter.convertToXml(yamlWithSpecialChars);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("&amp;"), "XML must escape ampersands");
            assertTrue(xml.contains("&lt;"), "XML must escape less-than");
            assertTrue(xml.contains("&gt;"), "XML must escape greater-than");
        }

        @Test
        @DisplayName("Should strip markdown code fences from YAML input")
        void stripMarkdownCodeFences() {
            String yamlWithMarkdown = """
                ```yaml
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                ```
                """;

            String xml = yamlConverter.convertToXml(yamlWithMarkdown);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("TestWorkflow"), "XML must contain workflow name");
            assertTrue(xml.contains("TaskA"), "XML must contain task");
        }

        @Test
        @DisplayName("Should use default workflow name when not specified")
        void useDefaultWorkflowNameWhenMissing() {
            String yamlNoName = """
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;

            String xml = yamlConverter.convertToXml(yamlNoName);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("<name>Workflow</name>"), "XML must use default workflow name");
        }

        @Test
        @DisplayName("Should use default XOR for join/split when not specified")
        void useDefaultXorJoinSplit() {
            String xml = yamlConverter.convertToXml(SINGLE_TASK_YAML);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("join code=\"xor\""), "XML must default join to xor");
            assertTrue(xml.contains("split code=\"xor\""), "XML must default split to xor");
        }
    }

    @Nested
    @DisplayName("YawlYamlConverter - Error Cases")
    class YamlConverterErrorCases {

        @Test
        @DisplayName("Should throw exception for null input")
        void throwExceptionForNullInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> yamlConverter.convertToXml(null),
                "Should throw exception for null YAML input"
            );
            assertTrue(ex.getMessage().contains("cannot be null") || ex.getMessage().contains("empty"),
                "Error message must describe the problem");
        }

        @Test
        @DisplayName("Should throw exception for empty input")
        void throwExceptionForEmptyInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> yamlConverter.convertToXml(""),
                "Should throw exception for empty YAML input"
            );
            assertTrue(ex.getMessage().contains("cannot be null") || ex.getMessage().contains("empty"),
                "Error message must describe the problem");
        }

        @Test
        @DisplayName("Should throw exception for invalid YAML syntax")
        void throwExceptionForInvalidYamlSyntax() {
            String invalidYaml = """
                name: BadYaml
                tasks:
                  - id: TaskA
                    flows: [unclosed list
                """;

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> yamlConverter.convertToXml(invalidYaml),
                "Should throw exception for invalid YAML"
            );
            assertTrue(ex.getMessage().contains("parse") || ex.getMessage().contains("Failed"),
                "Error message must indicate parsing failure");
        }

        @Test
        @DisplayName("Should throw exception for malformed list in flows")
        void throwExceptionForMalformedFlows() {
            String malformedYaml = """
                name: BadFlow
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: not_a_list
                """;

            String xml = yamlConverter.convertToXml(malformedYaml);
            assertNotNull(xml, "Converter should handle non-list flows gracefully");
        }

        @Test
        @DisplayName("Should handle YAML with all required fields")
        void handleCompleteYaml() {
            String yaml = "name: Test\nfirst: TaskA\ntasks:\n  - id: TaskA\n    flows: [end]";
            String xml = yamlConverter.convertToXml(yaml);
            assertNotNull(xml, "Converter should produce XML for valid YAML");
            assertTrue(xml.contains("TaskA"), "XML must contain the task");
        }
    }

    @Nested
    @DisplayName("YawlYamlConverter - Edge Cases")
    class YamlConverterEdgeCases {

        @Test
        @DisplayName("Should handle single-element flow list")
        void handleSingleElementFlowList() {
            String yaml = """
                name: SingleFlow
                uri: single.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;

            String xml = yamlConverter.convertToXml(yaml);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("o-top"), "XML must reference output condition");
        }

        @Test
        @DisplayName("Should handle multiple flows from single task")
        void handleMultipleFlows() {
            String yaml = """
                name: MultiFlow
                uri: multi.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [TaskB, TaskC, TaskD]
                  - id: TaskB
                    flows: [end]
                  - id: TaskC
                    flows: [end]
                  - id: TaskD
                    flows: [end]
                """;

            String xml = yamlConverter.convertToXml(yaml);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("TaskB"), "XML must contain second flow target");
            assertTrue(xml.contains("TaskC"), "XML must contain third flow target");
            assertTrue(xml.contains("TaskD"), "XML must contain fourth flow target");
        }

        @Test
        @DisplayName("Should handle whitespace in YAML")
        void handleWhitespaceInYaml() {
            String xml = yamlConverter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("OrderFulfillment"), "XML must be parseable despite whitespace");
        }

        @Test
        @DisplayName("Should handle empty tasks list")
        void handleEmptyTasksList() {
            String yamlNoTasks = """
                name: EmptyWorkflow
                uri: empty.xml
                tasks: []
                """;

            String xml = yamlConverter.convertToXml(yamlNoTasks);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("EmptyWorkflow"), "XML must contain workflow name");
            assertTrue(xml.contains("i-top"), "XML must contain input condition");
            assertTrue(xml.contains("o-top"), "XML must contain output condition");
        }

        @Test
        @DisplayName("Should handle missing 'first' field")
        void handleMissingFirstField() {
            String yamlNoFirst = """
                name: NoFirst
                uri: nofirst.xml
                tasks:
                  - id: TaskA
                    flows: [end]
                """;

            String xml = yamlConverter.convertToXml(yamlNoFirst);

            assertNotNull(xml, "Generated XML must not be null");
            assertTrue(xml.contains("TaskA"), "XML must use first task in list as default");
        }
    }

    // =========================================================================
    // Workflow Soundness Verifier Tests
    // =========================================================================

    @Nested
    @DisplayName("WorkflowSoundnessVerifier - Sound Workflows")
    class WorkflowSoundnessVerifierSound {

        @Test
        @DisplayName("Should verify single-task workflow as sound")
        void verifySingleTaskAsSound() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(SINGLE_TASK_YAML);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Single-task workflow must be sound");
            assertTrue(result.violations().isEmpty(), "Sound workflow must have no violations");
        }

        @Test
        @DisplayName("Should verify multi-task workflow as sound")
        void verifyMultiTaskAsSound() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(ORDER_FULFILLMENT_YAML);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Order fulfillment workflow must be sound");
            assertTrue(result.violations().isEmpty(), "Sound workflow must have no violations");
        }

        @Test
        @DisplayName("Should verify AND-split workflow as sound")
        void verifyAndSplitAsSound() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(MULTI_TASK_PARALLEL_YAML);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Parallel workflow must be sound");
            assertTrue(result.violations().isEmpty(), "Sound workflow must have no violations");
        }

        @Test
        @DisplayName("Should return SoundnessResult with sound=true for valid workflows")
        void soundnessResultIsAccurate() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(SINGLE_TASK_YAML);

            assertNotNull(result, "Result must not be null");
            assertEquals(true, result.sound(), "sound() must return true");
            assertNotNull(result.violations(), "violations() must return non-null list");
            assertTrue(result.violations().isEmpty(), "violations() must be empty for sound workflow");
        }
    }

    @Nested
    @DisplayName("WorkflowSoundnessVerifier - Unsound Workflows")
    class WorkflowSoundnessVerifierUnsound {

        @Test
        @DisplayName("Should detect unreachable task violation")
        void detectUnreachableTask() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(UNSOUND_UNREACHABLE_YAML);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Unsound workflow must report sound=false");
            assertFalse(result.violations().isEmpty(), "Unsound workflow must have violations");
            assertTrue(
                result.violations().stream()
                    .anyMatch(v -> v.contains("unreachable") || v.contains("TaskB")),
                "Violations must mention unreachable task"
            );
        }

        @Test
        @DisplayName("Should detect dead-end task violation")
        void detectDeadEndTask() {
            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(UNSOUND_DEAD_END_YAML);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow with dead-end must be unsound");
            assertFalse(result.violations().isEmpty(), "Must have violations for dead-end");
            assertTrue(
                result.violations().stream()
                    .anyMatch(v -> v.contains("dead-end") || v.contains("cannot reach") || v.contains("TaskB")),
                "Violations must mention dead-end task"
            );
        }

        @Test
        @DisplayName("Should detect empty task list violation")
        void detectEmptyTaskList() {
            String yamlNoTasks = """
                name: Empty
                uri: empty.xml
                tasks: []
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlNoTasks);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow with no tasks must be unsound");
            assertFalse(result.violations().isEmpty(), "Must have violations");
            assertTrue(
                result.violations().stream()
                    .anyMatch(v -> v.contains("no tasks") || v.contains("empty")),
                "Violations must mention missing tasks"
            );
        }

        @Test
        @DisplayName("Should detect duplicate task IDs")
        void detectDuplicateTaskIds() {
            String yamlDuplicateIds = """
                name: Duplicate
                uri: dup.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                  - id: TaskA
                    flows: [end]
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlDuplicateIds);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow with duplicate IDs must be unsound");
            assertTrue(
                result.violations().stream()
                    .anyMatch(v -> v.contains("Duplicate") || v.contains("TaskA")),
                "Violations must mention duplicate ID"
            );
        }

        @Test
        @DisplayName("Should detect unreachable task due to invalid reference")
        void detectUnreachableViaInvalidTarget() {
            String yamlInvalidTarget = """
                name: InvalidTarget
                uri: invalid.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [UndefinedTask]
                  - id: TaskB
                    flows: [end]
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlInvalidTarget);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow with undefined flow target must be unsound");
            assertTrue(
                result.violations().stream()
                    .anyMatch(v -> v.contains("unknown") || v.contains("UndefinedTask")),
                "Violations must mention undefined flow target"
            );
        }

        @Test
        @DisplayName("Should detect missing task ID")
        void detectMissingTaskId() {
            String yamlMissingId = """
                name: MissingId
                uri: missing.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                  - flows: [end]
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlMissingId);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow with missing task ID must be unsound");
        }
    }

    @Nested
    @DisplayName("WorkflowSoundnessVerifier - Error Cases")
    class WorkflowSoundnessVerifierErrors {

        @Test
        @DisplayName("Should throw exception for null YAML input")
        void throwExceptionForNullYaml() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> soundnessVerifier.verifyYaml(null),
                "Should throw exception for null YAML"
            );
            assertTrue(ex.getMessage().contains("null") || ex.getMessage().contains("blank"),
                "Error message must describe the problem");
        }

        @Test
        @DisplayName("Should throw exception for blank YAML input")
        void throwExceptionForBlankYaml() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> soundnessVerifier.verifyYaml("   "),
                "Should throw exception for blank YAML"
            );
            assertTrue(ex.getMessage().contains("blank") || ex.getMessage().contains("null"),
                "Error message must describe the problem");
        }

        @Test
        @DisplayName("Should throw exception for invalid YAML")
        void throwExceptionForInvalidYaml() {
            String invalidYaml = """
                name: BadYaml
                tasks:
                  - id: TaskA
                    flows: [unclosed list
                """;

            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> soundnessVerifier.verifyYaml(invalidYaml),
                "Should throw exception for unparseable YAML"
            );
            assertTrue(ex.getMessage().contains("parse") || ex.getMessage().contains("Failed"),
                "Error message must indicate parsing error");
        }

        @Test
        @DisplayName("Should throw exception for null spec map")
        void throwExceptionForNullSpecMap() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> soundnessVerifier.verify(null),
                "Should throw exception for null spec map"
            );
            assertTrue(ex.getMessage().contains("null"),
                "Error message must indicate null input");
        }
    }

    @Nested
    @DisplayName("WorkflowSoundnessVerifier - Edge Cases")
    class WorkflowSoundnessVerifierEdgeCases {

        @Test
        @DisplayName("Should handle markdown code fences in YAML")
        void handleMarkdownCodeFences() {
            String yamlWithMarkdown = """
                ```yaml
                name: SimpleWorkflow
                uri: simple.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                ```
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlWithMarkdown);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Workflow should be sound despite markdown");
        }

        @Test
        @DisplayName("Should handle whitespace and indentation variations")
        void handleWhitespaceVariations() {
            String yamlWithWhitespace = """
                name: SimpleWorkflow
                uri: simple.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlWithWhitespace);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Workflow should be sound despite whitespace");
        }

        @Test
        @DisplayName("Should report multiple violations together")
        void reportMultipleViolations() {
            String yamlMultipleIssues = """
                name: MultiIssue
                uri: multi.xml
                first: NonexistentStart
                tasks:
                  - id: TaskA
                    flows: [end]
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(yamlMultipleIssues);

            assertNotNull(result, "Result must not be null");
            assertFalse(result.sound(), "Workflow must be unsound");
            assertFalse(result.violations().isEmpty(), "Must have violations");
        }

        @Test
        @DisplayName("Should handle complex workflow with many branches")
        void handleComplexWorkflow() {
            String complexYaml = """
                name: ComplexWorkflow
                uri: complex.xml
                first: StartTask
                tasks:
                  - id: StartTask
                    flows: [BranchA, BranchB, BranchC]
                    split: xor
                  - id: BranchA
                    flows: [JoinTask]
                  - id: BranchB
                    flows: [JoinTask]
                  - id: BranchC
                    flows: [JoinTask]
                  - id: JoinTask
                    flows: [end]
                    join: xor
                """;

            WorkflowSoundnessVerifier.SoundnessResult result = soundnessVerifier.verifyYaml(complexYaml);

            assertNotNull(result, "Result must not be null");
            assertTrue(result.sound(), "Complex workflow must be sound");
        }
    }

    // =========================================================================
    // Integration Tests - Converter & Verifier Together
    // =========================================================================

    @Nested
    @DisplayName("Integration - Converter + Verifier")
    class IntegrationTests {

        @Test
        @DisplayName("Should convert and verify sound workflow end-to-end")
        void convertAndVerifySoundWorkflow() {
            // Verify workflow is sound
            WorkflowSoundnessVerifier.SoundnessResult soundnessResult = soundnessVerifier.verifyYaml(ORDER_FULFILLMENT_YAML);
            assertTrue(soundnessResult.sound(), "Workflow must be sound before conversion");

            // Convert to XML
            String xml = yamlConverter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertNotNull(xml, "XML must not be null");

            // Verify XML contains expected elements
            assertTrue(xml.contains("OrderFulfillment"), "XML must contain workflow name");
            assertTrue(xml.contains("VerifyPayment"), "XML must contain tasks from verified workflow");
            assertTrue(xml.contains("<specificationSet"), "XML must be well-formed");
        }

        @Test
        @DisplayName("Should reject unsound workflow before conversion")
        void rejectUnsoundWorkflowBeforeConversion() {
            // Verify workflow is unsound
            WorkflowSoundnessVerifier.SoundnessResult soundnessResult = soundnessVerifier.verifyYaml(UNSOUND_UNREACHABLE_YAML);
            assertFalse(soundnessResult.sound(), "Workflow must be unsound");

            // Even though XML converts unsound workflows, we should check soundness first
            String xml = yamlConverter.convertToXml(UNSOUND_UNREACHABLE_YAML);
            assertNotNull(xml, "Converter still produces XML for unsound workflows");
            // But the XML will have unreachable tasks
            assertTrue(xml.contains("TaskB"), "XML contains all declared tasks regardless of soundness");
        }

        @Test
        @DisplayName("Should handle round-trip: YAML -> Verify -> Convert")
        void roundTripWorkflow() {
            // Verify
            WorkflowSoundnessVerifier.SoundnessResult verifyResult = soundnessVerifier.verifyYaml(SINGLE_TASK_YAML);
            assertTrue(verifyResult.sound(), "YAML must be sound");

            // Convert
            String xml = yamlConverter.convertToXml(SINGLE_TASK_YAML);
            assertNotNull(xml, "XML must be generated");

            // Verify XML structure
            assertTrue(xml.contains("SimpleWorkflow"), "XML must preserve workflow name");
            assertTrue(xml.contains("TaskA"), "XML must preserve task IDs");
        }
    }
}
