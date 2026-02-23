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

package org.yawlfoundation.yawl.mcp.a2a.e2e;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.yawlfoundation.yawl.mcp.a2a.example.WorkflowSoundnessVerifier;
import org.yawlfoundation.yawl.mcp.a2a.example.YawlYamlConverter;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for multi-instance (m×n) YAWL workflow specifications.
 *
 * <p>Tests the complete pipeline: YAML → XML conversion → soundness verification.
 * Covers all combinations of min instances (m), max instances (n), threshold (t),
 * and creation modes (static, dynamic).</p>
 *
 * <p>All tests run without network access using real YAML converter and
 * soundness verifier instances.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Multi-Instance Workflow E2E Tests")
class MultiInstanceE2ETest {

    private YawlYamlConverter converter;
    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        converter = new YawlYamlConverter();
        verifier = new WorkflowSoundnessVerifier();
    }

    @Nested
    @DisplayName("Parameter Matrix Tests (m × n combinations)")
    class ParameterMatrixTests {

        /**
         * Provides test cases for valid m×n parameter combinations.
         * Format: min, max, threshold, mode
         */
        static Stream<org.junit.jupiter.params.provider.Arguments> validMultiInstanceParams() {
            return Stream.of(
                // Single instance (degenerate case)
                org.junit.jupiter.params.provider.Arguments.of(1, 1, 1, "static"),

                // Small static instances
                org.junit.jupiter.params.provider.Arguments.of(1, 5, 3, "static"),
                org.junit.jupiter.params.provider.Arguments.of(2, 4, 2, "static"),
                org.junit.jupiter.params.provider.Arguments.of(1, 3, 1, "static"),

                // Dynamic instances
                org.junit.jupiter.params.provider.Arguments.of(1, 10, 5, "dynamic"),
                org.junit.jupiter.params.provider.Arguments.of(2, 8, 3, "dynamic"),
                org.junit.jupiter.params.provider.Arguments.of(1, 1, 1, "dynamic"),

                // Unbounded max (0 or -1 means unlimited)
                org.junit.jupiter.params.provider.Arguments.of(1, 0, 2, "dynamic"),
                org.junit.jupiter.params.provider.Arguments.of(2, -1, 2, "static"),

                // Higher instance counts
                org.junit.jupiter.params.provider.Arguments.of(5, 15, 8, "static"),
                org.junit.jupiter.params.provider.Arguments.of(3, 100, 10, "dynamic")
            );
        }

        @ParameterizedTest(name = "m={0}, n={1}, t={2}, mode={3}")
        @MethodSource("validMultiInstanceParams")
        @DisplayName("Valid m×n parameter combinations convert to XML")
        void testValidParameterCombinations(int min, int max, int threshold, String mode) {
            String yaml = buildYamlWithMultiInstance("TestWorkflow", min, max, threshold, mode);

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "Generated XML should not be null");
            assertFalse(xml.isEmpty(), "Generated XML should not be empty");
            assertTrue(xml.contains("<specificationSet"), "XML should contain specificationSet");
            assertTrue(xml.contains("<multiInstance>"), "XML should contain multiInstance element");
            assertTrue(xml.contains("<minimum>" + min + "</minimum>"),
                    "XML should contain correct minimum value");
            assertTrue(xml.contains("<maximum>" + max + "</maximum>"),
                    "XML should contain correct maximum value");
            assertTrue(xml.contains("<threshold>" + threshold + "</threshold>"),
                    "XML should contain correct threshold value");
            assertTrue(xml.contains("<creationMode code=\"" + mode + "\"/>"),
                    "XML should contain correct creation mode");
        }

        /**
         * Provides invalid m×n combinations.
         * Format: min, max (should fail when max < min)
         */
        static Stream<org.junit.jupiter.params.provider.Arguments> invalidMultiInstanceParams() {
            return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(5, 3),
                org.junit.jupiter.params.provider.Arguments.of(10, 5),
                org.junit.jupiter.params.provider.Arguments.of(3, 1)
            );
        }

        @ParameterizedTest(name = "min={0}, max={1}")
        @MethodSource("invalidMultiInstanceParams")
        @DisplayName("Invalid m×n (max < min) should be detectable")
        void testInvalidParameterCombinations(int min, int max) {
            String yaml = buildYamlWithMultiInstance("InvalidWorkflow", min, max, min, "static");
            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "XML should still generate (validation is semantic, not syntactic)");
            assertTrue(xml.contains("<minimum>" + min + "</minimum>"),
                    "XML should contain minimum even if invalid");
            assertTrue(xml.contains("<maximum>" + max + "</maximum>"),
                    "XML should contain maximum even if invalid");
        }
    }

    @Nested
    @DisplayName("Multi-Instance in Workflow Context")
    class WorkflowContextTests {

        @Test
        @DisplayName("Multi-instance task in sequence: A → MI_Task → B → end")
        void testMultiInstanceInSequence() {
            String yaml = """
                    name: SequenceWorkflow
                    first: PrepareTask
                    tasks:
                      - id: PrepareTask
                        flows: [MultiTask]
                      - id: MultiTask
                        flows: [FinalizeTask]
                        multiInstance:
                          min: 1
                          max: 5
                          threshold: 3
                          mode: static
                      - id: FinalizeTask
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml, "XML should be generated");
            assertTrue(xml.contains("<task id=\"MultiTask\""),
                    "XML should contain MultiTask task element");
            assertTrue(xml.contains("<multiInstance>"), "XML should contain multiInstance");
            assertTrue(result.sound(), "Workflow should be sound");
        }

        @Test
        @DisplayName("Multi-instance task in parallel branch")
        void testMultiInstanceInParallelBranch() {
            String yaml = """
                    name: ParallelWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [Branch1, Branch2]
                        split: and
                      - id: Branch1
                        flows: [end]
                      - id: Branch2
                        flows: [end]
                        multiInstance:
                          min: 2
                          max: 4
                          threshold: 2
                          mode: dynamic
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"and\"/>"), "StartTask should have AND split");
            assertTrue(xml.contains("<multiInstance>"), "Branch2 should have multiInstance");
            assertTrue(result.sound(), "Workflow should be sound");
        }

        @Test
        @DisplayName("Multi-instance task as only task: MI_Task → end")
        void testMultiInstanceAsOnlyTask() {
            String yaml = """
                    name: SingleMultiInstanceWorkflow
                    first: OnlyTask
                    tasks:
                      - id: OnlyTask
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 3
                          threshold: 2
                          mode: static
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<multiInstance>"), "OnlyTask should have multiInstance");
            assertTrue(result.sound(), "Workflow should be sound");
            assertEquals(1, countTaskDefinitions(yaml), "Should contain exactly 1 task");
        }

        @Test
        @DisplayName("Two sequential multi-instance tasks")
        void testTwoSequentialMultiInstanceTasks() {
            String yaml = """
                    name: TwoMultiInstanceWorkflow
                    first: FirstMulti
                    tasks:
                      - id: FirstMulti
                        flows: [SecondMulti]
                        multiInstance:
                          min: 1
                          max: 3
                          threshold: 2
                          mode: static
                      - id: SecondMulti
                        flows: [end]
                        multiInstance:
                          min: 2
                          max: 5
                          threshold: 3
                          mode: dynamic
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<task id=\"FirstMulti\""), "Should contain FirstMulti task");
            assertTrue(xml.contains("<task id=\"SecondMulti\""), "Should contain SecondMulti task");
            assertTrue(xml.contains("<multiInstance>"), "XML should contain multiInstance elements");
            assertTrue(result.sound(), "Workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Full Pipeline: YAML → XML → Soundness")
    class FullPipelineTests {

        @ParameterizedTest(name = "Pipeline test: m={0}, n={1}, t={2}, mode={3}")
        @CsvSource({
                "1,1,1,static",
                "1,5,3,static",
                "1,10,5,dynamic",
                "2,4,2,static",
                "3,7,4,dynamic"
        })
        @DisplayName("Complete pipeline for various m×n parameters")
        void testCompletePipeline(int min, int max, int threshold, String mode) {
            // Step 1: Build YAML workflow spec
            String yaml = buildYamlWithMultiInstance("PipelineTest", min, max, threshold, mode);

            // Step 2: Convert to YAWL XML
            String xml = converter.convertToXml(yaml);

            // Step 3: Verify XML contains correct multiInstance elements
            String expectedMinimum = "<minimum>" + min + "</minimum>";
            String expectedMaximum = "<maximum>" + max + "</maximum>";
            String expectedThreshold = "<threshold>" + threshold + "</threshold>";
            String expectedMode = "<creationMode code=\"" + mode + "\"/>";

            assertTrue(xml.contains(expectedMinimum),
                    "XML should contain minimum value: " + expectedMinimum);
            assertTrue(xml.contains(expectedMaximum),
                    "XML should contain maximum value: " + expectedMaximum);
            assertTrue(xml.contains(expectedThreshold),
                    "XML should contain threshold value: " + expectedThreshold);
            assertTrue(xml.contains(expectedMode),
                    "XML should contain creation mode: " + expectedMode);

            // Step 4: Verify soundness
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(), "Workflow should be sound: " + result.violations());
            assertTrue(result.violations().isEmpty(), "Soundness result should have no violations");
        }

        @Test
        @DisplayName("Pipeline with complex workflow: conditional + multi-instance")
        void testComplexPipelineWithConditionals() {
            String yaml = """
                    name: ComplexWorkflow
                    first: CheckInventory
                    tasks:
                      - id: CheckInventory
                        flows: [ProcessOrders, RejectOrders]
                        condition: inventory_ok -> ProcessOrders
                        default: RejectOrders
                        split: xor
                      - id: ProcessOrders
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 10
                          threshold: 5
                          mode: dynamic
                      - id: RejectOrders
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"xor\"/>"), "CheckInventory should have XOR split");
            assertTrue(xml.contains("<multiInstance>"), "ProcessOrders should have multiInstance");
            assertTrue(xml.contains("<predicate>inventory_ok</predicate>"),
                    "XML should contain condition predicate");
            assertTrue(result.sound(), "Complex workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Multi-instance task with max=1 (degenerate, equivalent to single instance)")
        void testDegenerateSingleInstanceCase() {
            String yaml = buildYamlWithMultiInstance("DegenerateCase", 1, 1, 1, "static");

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<minimum>1</minimum>"), "Should have minimum=1");
            assertTrue(xml.contains("<maximum>1</maximum>"), "Should have maximum=1");
            assertTrue(xml.contains("<threshold>1</threshold>"), "Should have threshold=1");
            assertTrue(result.sound(), "Degenerate case should be sound");
        }

        @Test
        @DisplayName("Multi-instance with unbounded max (0)")
        void testUnboundedMaxZero() {
            String yaml = buildYamlWithMultiInstance("UnboundedZero", 1, 0, 1, "dynamic");

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<maximum>0</maximum>"),
                    "Should contain maximum=0 (unbounded)");
            assertTrue(result.sound(), "Unbounded max=0 should be sound");
        }

        @Test
        @DisplayName("Multi-instance with unbounded max (-1)")
        void testUnboundedMaxNegative() {
            String yaml = buildYamlWithMultiInstance("UnboundedNegative", 2, -1, 2, "static");

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<maximum>-1</maximum>"),
                    "Should contain maximum=-1 (unbounded)");
            assertTrue(result.sound(), "Unbounded max=-1 should be sound");
        }

        @Test
        @DisplayName("Empty multiInstance block (should still convert)")
        void testEmptyMultiInstanceBlock() {
            String yaml = """
                    name: EmptyMultiInstanceWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                        multiInstance:
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            // Should not crash; multiInstance should be omitted
            assertNotNull(xml, "XML should be generated");
            assertTrue(result.sound(), "Workflow should be sound");
        }

        @Test
        @DisplayName("Multi-instance with large threshold")
        void testLargeThresholdValue() {
            String yaml = buildYamlWithMultiInstance("LargeThreshold", 10, 1000, 500, "dynamic");

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<threshold>500</threshold>"),
                    "Should contain large threshold value");
            assertTrue(result.sound(), "Workflow with large threshold should be sound");
        }
    }

    @Nested
    @DisplayName("Nested and Complex Workflows")
    class ComplexWorkflowTests {

        @Test
        @DisplayName("Nested workflow: outer net calls subprocess with multi-instance")
        void testNestedWorkflowWithMultiInstance() {
            String yaml = """
                    name: OuterWorkflow
                    first: StartProcess
                    tasks:
                      - id: StartProcess
                        flows: [CallSubprocess]
                      - id: CallSubprocess
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 5
                          threshold: 3
                          mode: static
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<multiInstance>"), "Subprocess call should have multiInstance");
            assertTrue(result.sound(), "Nested workflow should be sound");
        }

        @Test
        @DisplayName("Multiple multi-instance tasks with different parameters")
        void testMultipleMultiInstanceTasksWithDifferentParams() {
            String yaml = """
                    name: MultipleMultiInstanceWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [Task2]
                        multiInstance:
                          min: 1
                          max: 3
                          threshold: 2
                          mode: static
                      - id: Task2
                        flows: [Task3]
                        multiInstance:
                          min: 2
                          max: 8
                          threshold: 4
                          mode: dynamic
                      - id: Task3
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 5
                          threshold: 1
                          mode: static
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Multiple multi-instance tasks should be sound");
            int miCount = countOccurrences(xml, "<multiInstance>");
            assertEquals(3, miCount, "XML should contain exactly 3 multiInstance elements");
        }

        @Test
        @DisplayName("Multi-instance task with AND join/split")
        void testMultiInstanceWithAndJoinSplit() {
            String yaml = """
                    name: MultiInstanceWithAndWorkflow
                    first: PrepareItems
                    tasks:
                      - id: PrepareItems
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [end]
                        join: and
                        split: and
                        multiInstance:
                          min: 2
                          max: 6
                          threshold: 3
                          mode: dynamic
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<join code=\"and\"/>"),
                    "ProcessItems should have AND join");
            assertTrue(xml.contains("<split code=\"and\"/>"),
                    "ProcessItems should have AND split");
            assertTrue(xml.contains("<multiInstance>"), "Should have multiInstance");
            assertTrue(result.sound(), "Workflow should be sound");
        }

        @Test
        @DisplayName("Multi-instance task with OR join/split")
        void testMultiInstanceWithOrJoinSplit() {
            String yaml = """
                    name: MultiInstanceWithOrWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [end]
                        join: or
                        split: or
                        multiInstance:
                          min: 1
                          max: 4
                          threshold: 1
                          mode: static
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<join code=\"or\"/>"),
                    "ProcessItems should have OR join");
            assertTrue(xml.contains("<split code=\"or\"/>"),
                    "ProcessItems should have OR split");
            assertTrue(result.sound(), "Workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Error Handling and Validation")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Null YAML input throws exception")
        void testNullYamlThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> converter.convertToXml(null),
                    "Null YAML should throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Empty YAML input throws exception")
        void testEmptyYamlThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> converter.convertToXml(""),
                    "Empty YAML should throw IllegalArgumentException");
        }

        @Test
        @DisplayName("Malformed YAML throws exception")
        void testMalformedYamlThrowsException() {
            String malformedYaml = """
                    name: BadWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [Task2
                    """;

            assertThrows(IllegalArgumentException.class, () -> converter.convertToXml(malformedYaml),
                    "Malformed YAML should throw IllegalArgumentException");
        }

        @Test
        @DisplayName("YAML without tasks is unsound")
        void testYamlWithoutTasksIsUnsound() {
            String yaml = """
                    name: NoTasksWorkflow
                    first: None
                    tasks: []
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(), "Workflow without tasks should be unsound");
            assertFalse(result.violations().isEmpty(), "Should have violations");
        }

        @Test
        @DisplayName("YAML with unreachable task is unsound")
        void testYamlWithUnreachableTaskIsUnsound() {
            String yaml = """
                    name: UnreachableTaskWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [end]
                      - id: Task2
                        flows: [end]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(), "Workflow with unreachable task should be unsound");
            assertTrue(result.violations().stream()
                    .anyMatch(v -> v.contains("Task2")),
                    "Violation should mention unreachable Task2");
        }

        @Test
        @DisplayName("Invalid multiInstance parameters are still converted to XML")
        void testInvalidMultiInstanceParametersConverted() {
            String yaml = buildYamlWithMultiInstance("InvalidParams", 10, 5, 7, "static");

            String xml = converter.convertToXml(yaml);

            assertNotNull(xml, "Invalid parameters should still convert");
            assertTrue(xml.contains("<multiInstance>"), "Should contain multiInstance element");
            assertTrue(xml.contains("<minimum>10</minimum>"), "Should contain minimum=10");
            assertTrue(xml.contains("<maximum>5</maximum>"), "Should contain maximum=5");
        }
    }

    @Nested
    @DisplayName("Markdown Code Block Handling")
    class MarkdownCodeBlockTests {

        @Test
        @DisplayName("YAML with markdown code block fence is stripped correctly")
        void testMarkdownCodeBlockStripped() {
            String yamlWithMarkdown = """
                    ```yaml
                    name: MarkdownWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 3
                          threshold: 1
                          mode: static
                    ```
                    """;

            String xml = converter.convertToXml(yamlWithMarkdown);

            assertNotNull(xml, "Markdown-wrapped YAML should be converted");
            assertTrue(xml.contains("<multiInstance>"), "Should contain multiInstance");
            assertTrue(xml.contains("<maximum>3</maximum>"), "Should extract correct parameters");
        }

        @Test
        @DisplayName("YAML with plain markdown fence (no language tag)")
        void testPlainMarkdownFence() {
            String yamlWithFence = """
                    ```
                    name: PlainFenceWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    ```
                    """;

            String xml = converter.convertToXml(yamlWithFence);

            assertNotNull(xml, "Plain-fenced YAML should be converted");
            assertTrue(xml.contains("PlainFenceWorkflow"), "Workflow name should be preserved");
        }
    }

    @Nested
    @DisplayName("XML Structure Validation")
    class XmlStructureTests {

        @Test
        @DisplayName("Generated XML has correct namespace declarations")
        void testXmlNamespaceDeclarations() {
            String yaml = buildYamlWithMultiInstance("NamespaceTest", 1, 3, 1, "static");

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("xmlns=\"http://www.yawlfoundation.org/yawlschema\""),
                    "Should contain YAWL namespace");
            assertTrue(xml.contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""),
                    "Should contain XSI namespace");
            assertTrue(xml.contains("xsi:schemaLocation="),
                    "Should contain schema location");
        }

        @Test
        @DisplayName("Generated XML has correct structure: spec → decomposition → tasks")
        void testXmlHierarchy() {
            String yaml = buildYamlWithMultiInstance("HierarchyTest", 1, 2, 1, "dynamic");

            String xml = converter.convertToXml(yaml);

            int specStart = xml.indexOf("<specificationSet");
            int specEnd = xml.lastIndexOf("</specificationSet>");
            int decompositionStart = xml.indexOf("<decomposition");
            int processControlStart = xml.indexOf("<processControlElements>");

            assertTrue(specStart < decompositionStart, "specificationSet should be before decomposition");
            assertTrue(decompositionStart < processControlStart, "decomposition should be before processControlElements");
            assertTrue(processControlStart < specEnd, "processControlElements should be inside specificationSet");
        }

        @Test
        @DisplayName("MultiInstance element positioned between split and decomposesTo")
        void testMultiInstanceElementPosition() {
            String yaml = buildYamlWithMultiInstance("PositionTest", 1, 5, 2, "static");

            String xml = converter.convertToXml(yaml);

            int splitPos = xml.indexOf("<split code=\"xor\"/>");
            int miPos = xml.indexOf("<multiInstance>");
            int decomposesPos = xml.indexOf("<decomposesTo");

            assertTrue(splitPos < miPos, "split should come before multiInstance");
            assertTrue(miPos < decomposesPos, "multiInstance should come before decomposesTo");
        }
    }

    @Nested
    @DisplayName("Soundness Verification")
    class SoundnessVerificationTests {

        @Test
        @DisplayName("Simple multi-instance workflow is sound")
        void testSimpleMultiInstanceSoundness() {
            String yaml = buildYamlWithMultiInstance("SimpleMultiInstance", 1, 3, 1, "static");

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Simple multi-instance workflow should be sound");
            assertTrue(result.violations().isEmpty(), "Should have no violations");
        }

        @Test
        @DisplayName("All tasks reachable and can reach end")
        void testAllTasksReachable() {
            String yaml = """
                    name: ReachabilityTest
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [Task2]
                      - id: Task2
                        flows: [Task3]
                        multiInstance:
                          min: 1
                          max: 5
                          threshold: 2
                          mode: static
                      - id: Task3
                        flows: [end]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "All tasks should be reachable");
        }

        @Test
        @DisplayName("Duplicate task IDs make workflow unsound")
        void testDuplicateTaskIdsMakeUnsound() {
            String yaml = """
                    name: DuplicateIdWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [Task1, end]
                      - id: Task1
                        flows: [end]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(), "Workflow with duplicate IDs should be unsound");
        }

        @Test
        @DisplayName("Unknown flow target makes workflow unsound")
        void testUnknownFlowTargetMakesUnsound() {
            String yaml = """
                    name: UnknownFlowWorkflow
                    first: Task1
                    tasks:
                      - id: Task1
                        flows: [UnknownTask]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(), "Workflow with unknown flow target should be unsound");
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Builds a YAML workflow specification with multi-instance parameters.
     *
     * @param workflowName the name of the workflow
     * @param min minimum instances
     * @param max maximum instances
     * @param threshold completion threshold
     * @param mode creation mode (static or dynamic)
     * @return YAML string with multi-instance configuration
     */
    private String buildYamlWithMultiInstance(String workflowName, int min, int max,
                                               int threshold, String mode) {
        return """
                name: %s
                first: MainTask
                tasks:
                  - id: MainTask
                    flows: [end]
                    multiInstance:
                      min: %d
                      max: %d
                      threshold: %d
                      mode: %s
                """.formatted(workflowName, min, max, threshold, mode);
    }

    /**
     * Counts task definitions in a YAML specification.
     *
     * @param yaml the YAML workflow specification
     * @return the number of tasks defined
     */
    private int countTaskDefinitions(String yaml) {
        return (int) yaml.lines()
                .filter(line -> line.trim().startsWith("- id:"))
                .count();
    }

    /**
     * Counts occurrences of a substring in a string.
     *
     * @param text the text to search in
     * @param substring the substring to count
     * @return the number of occurrences
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
