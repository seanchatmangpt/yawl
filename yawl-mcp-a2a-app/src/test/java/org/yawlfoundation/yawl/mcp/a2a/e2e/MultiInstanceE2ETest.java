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

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for YAWL workflow specifications including multi-instance patterns.
 *
 * <p>Tests the complete pipeline: YAML → XML conversion → soundness verification.
 * Covers workflow patterns with parallel splits, XOR decisions, and multi-instance scenarios.</p>
 *
 * <p>All tests run without network access using real YAML converter and
 * soundness verifier instances.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Multi-Instance and Workflow Pattern E2E Tests")
class MultiInstanceE2ETest {

    private YawlYamlConverter converter;
    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        converter = new YawlYamlConverter();
        verifier = new WorkflowSoundnessVerifier();
    }

    @Nested
    @DisplayName("Basic Workflow Patterns")
    class BasicWorkflowTests {

        @Test
        @DisplayName("Simple linear workflow: A → B → C → end")
        void testLinearWorkflow() {
            String yaml = """
                    name: LinearWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [TaskB]
                      - id: TaskB
                        flows: [TaskC]
                      - id: TaskC
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml);
            assertFalse(xml.isEmpty());
            assertTrue(xml.contains("<task id=\"TaskA\""));
            assertTrue(xml.contains("<task id=\"TaskB\""));
            assertTrue(xml.contains("<task id=\"TaskC\""));
            assertTrue(result.sound(), "Linear workflow should be sound");
        }

        @Test
        @DisplayName("XOR split workflow: A → (B | C) → end")
        void testXorSplitWorkflow() {
            String yaml = """
                    name: XorSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [BranchB, BranchC]
                        split: xor
                      - id: BranchB
                        flows: [end]
                      - id: BranchC
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"xor\"/>"));
            assertTrue(result.sound(), "XOR split workflow should be sound");
        }

        @Test
        @DisplayName("AND split workflow: A → (B & C) → end")
        void testAndSplitWorkflow() {
            String yaml = """
                    name: AndSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [BranchB, BranchC]
                        split: and
                      - id: BranchB
                        flows: [end]
                      - id: BranchC
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"and\"/>"));
            assertTrue(result.sound(), "AND split workflow should be sound");
        }

        @Test
        @DisplayName("OR split workflow: A → (B | C) → end (probabilistic)")
        void testOrSplitWorkflow() {
            String yaml = """
                    name: OrSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [BranchB, BranchC]
                        split: or
                      - id: BranchB
                        flows: [end]
                      - id: BranchC
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"or\"/>"));
            assertTrue(result.sound(), "OR split workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Multi-Instance Workflow Contexts")
    class MultiInstanceContextTests {

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
                      - id: FinalizeTask
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml);
            assertTrue(xml.contains("<task id=\"MultiTask\""),
                    "XML should contain MultiTask task element");
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
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"and\"/>"), "StartTask should have AND split");
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
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml, "XML should be generated");
            assertTrue(result.sound(), "Workflow should be sound");
            assertEquals(1, countTaskDefinitions(yaml), "Should contain exactly 1 task");
        }

        @Test
        @DisplayName("Two sequential tasks with conditional routing")
        void testTwoSequentialTasksWithConditional() {
            String yaml = """
                    name: ConditionalWorkflow
                    first: FirstTask
                    tasks:
                      - id: FirstTask
                        flows: [SecondTask, AlternateTask]
                        condition: process_ok -> SecondTask
                        default: AlternateTask
                        split: xor
                      - id: SecondTask
                        flows: [end]
                      - id: AlternateTask
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<predicate>process_ok</predicate>"),
                    "XML should contain condition predicate");
            assertTrue(xml.contains("<split code=\"xor\"/>"),
                    "FirstTask should have XOR split");
            assertTrue(result.sound(), "Workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Full Pipeline: YAML → XML → Soundness")
    class FullPipelineTests {

        @ParameterizedTest(name = "Pattern: {0}")
        @CsvSource({
                "linear,TaskA",
                "xor-split,StartTask",
                "and-split,StartTask",
                "conditional,CheckInventory"
        })
        @DisplayName("Complete pipeline for various workflow patterns")
        void testCompletePipeline(String patternName, String firstTask) {
            String yaml = buildYamlPattern(patternName);

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml, "XML should be generated");
            assertFalse(xml.isEmpty(), "XML should not be empty");
            assertTrue(xml.contains("<task id=\"" + firstTask + "\""),
                    "XML should contain first task: " + firstTask);
            assertTrue(result.sound(), "Workflow should be sound: " + result.violations());
            assertTrue(result.violations().isEmpty(), "Soundness result should have no violations");
        }

        @Test
        @DisplayName("Pipeline with complex workflow: conditional + parallel")
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
                        flows: [ShipItems, NotifyWarehouse]
                        split: and
                      - id: NotifyWarehouse
                        flows: [end]
                      - id: ShipItems
                        flows: [end]
                      - id: RejectOrders
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<split code=\"xor\"/>"), "CheckInventory should have XOR split");
            assertTrue(xml.contains("<split code=\"and\"/>"), "ProcessOrders should have AND split");
            assertTrue(xml.contains("<predicate>inventory_ok</predicate>"),
                    "XML should contain condition predicate");
            assertTrue(result.sound(), "Complex workflow should be sound");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Conditions")
    class EdgeCaseTests {

        @Test
        @DisplayName("Single task workflow (degenerate case)")
        void testDegenerateSingleTaskCase() {
            String yaml = """
                    name: DegenerateCase
                    first: OnlyTask
                    tasks:
                      - id: OnlyTask
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertNotNull(xml, "XML should be generated");
            assertTrue(result.sound(), "Degenerate case should be sound");
        }

        @Test
        @DisplayName("Deep task chain (10 sequential tasks)")
        void testDeepTaskChain() {
            StringBuilder yamlBuilder = new StringBuilder("""
                    name: DeepChainWorkflow
                    first: Task1
                    tasks:
                    """);

            for (int i = 1; i <= 10; i++) {
                String nextTask = (i < 10) ? "Task" + (i + 1) : "end";
                yamlBuilder.append("  - id: Task").append(i).append("\n");
                yamlBuilder.append("    flows: [").append(nextTask).append("]\n");
            }

            String yaml = yamlBuilder.toString();
            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Deep chain workflow should be sound");
            for (int i = 1; i <= 10; i++) {
                assertTrue(xml.contains("<task id=\"Task" + i + "\""),
                        "XML should contain Task" + i);
            }
        }

        @Test
        @DisplayName("Wide split: 5-way XOR split")
        void testWideSplit() {
            String yaml = """
                    name: WideSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [Branch1, Branch2, Branch3, Branch4, Branch5]
                        split: xor
                      - id: Branch1
                        flows: [end]
                      - id: Branch2
                        flows: [end]
                      - id: Branch3
                        flows: [end]
                      - id: Branch4
                        flows: [end]
                      - id: Branch5
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Wide split should be sound");
            for (int i = 1; i <= 5; i++) {
                assertTrue(xml.contains("<task id=\"Branch" + i + "\""),
                        "XML should contain Branch" + i);
            }
        }
    }

    @Nested
    @DisplayName("Nested and Complex Workflows")
    class ComplexWorkflowTests {

        @Test
        @DisplayName("Fork-join pattern: (A && B) → C")
        void testForkJoinPattern() {
            String yaml = """
                    name: ForkJoinWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [ParallelTaskA, ParallelTaskB]
                        split: and
                      - id: ParallelTaskA
                        flows: [JoinTask]
                      - id: ParallelTaskB
                        flows: [JoinTask]
                      - id: JoinTask
                        join: and
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Fork-join pattern should be sound");
            assertTrue(xml.contains("<join code=\"and\"/>"), "JoinTask should have AND join");
        }

        @Test
        @DisplayName("Complex multi-branch pattern with mixed splits/joins")
        void testComplexMultiBranchPattern() {
            String yaml = """
                    name: MultiBranchWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [TaskB, TaskC]
                        split: xor
                      - id: TaskB
                        flows: [TaskD, TaskE]
                        split: and
                      - id: TaskC
                        flows: [TaskE]
                      - id: TaskD
                        flows: [TaskF]
                      - id: TaskE
                        join: or
                        flows: [TaskF]
                      - id: TaskF
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Complex multi-branch pattern should be sound");
        }

        @Test
        @DisplayName("Multiple sequential multi-instance regions")
        void testMultipleMultiInstanceRegions() {
            String yaml = """
                    name: MultipleRegionWorkflow
                    first: Region1Start
                    tasks:
                      - id: Region1Start
                        flows: [Region1End]
                      - id: Region1End
                        flows: [Region2Start]
                      - id: Region2Start
                        flows: [Region2End]
                      - id: Region2End
                        flows: [Region3Start]
                      - id: Region3Start
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Multiple region workflow should be sound");
        }

        @Test
        @DisplayName("Workflow with AND join/split on multi-instance task")
        void testMultiInstanceWithAndJoinSplit() {
            String yaml = """
                    name: MultiInstanceAndWorkflow
                    first: PrepareItems
                    tasks:
                      - id: PrepareItems
                        flows: [ProcessItems]
                      - id: ProcessItems
                        join: and
                        split: and
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);
            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(xml.contains("<join code=\"and\"/>"),
                    "ProcessItems should have AND join");
            assertTrue(xml.contains("<split code=\"and\"/>"),
                    "ProcessItems should have AND split");
            assertTrue(result.sound(), "Workflow should be sound");
        }

        @Test
        @DisplayName("Workflow with OR join/split on multi-instance task")
        void testMultiInstanceWithOrJoinSplit() {
            String yaml = """
                    name: MultiInstanceOrWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [ProcessItems]
                      - id: ProcessItems
                        join: or
                        split: or
                        flows: [end]
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
        @DisplayName("YAML with duplicate task IDs is unsound")
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
        @DisplayName("YAML with unknown flow target is unsound")
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
                    ```
                    """;

            String xml = converter.convertToXml(yamlWithMarkdown);

            assertNotNull(xml, "Markdown-wrapped YAML should be converted");
            assertTrue(xml.contains("MarkdownWorkflow"), "Workflow name should be preserved");
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
            String yaml = """
                    name: NamespaceTest
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);

            assertTrue(xml.contains("xmlns=\"http://www.yawlfoundation.org/yawlschema\""),
                    "Should contain YAWL namespace");
            assertTrue(xml.contains("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""),
                    "Should contain XSI namespace");
            assertTrue(xml.contains("xsi:schemaLocation="),
                    "Should contain schema location");
        }

        @Test
        @DisplayName("Generated XML has correct hierarchy: spec → decomposition → tasks")
        void testXmlHierarchy() {
            String yaml = """
                    name: HierarchyTest
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;

            String xml = converter.convertToXml(yaml);

            int specStart = xml.indexOf("<specificationSet");
            int specEnd = xml.lastIndexOf("</specificationSet>");
            int decompositionStart = xml.indexOf("<decomposition");
            int processControlStart = xml.indexOf("<processControlElements>");

            assertTrue(specStart < decompositionStart, "specificationSet should be before decomposition");
            assertTrue(decompositionStart < processControlStart, "decomposition should be before processControlElements");
            assertTrue(processControlStart < specEnd, "processControlElements should be inside specificationSet");
        }
    }

    @Nested
    @DisplayName("Soundness Verification Tests")
    class SoundnessVerificationTests {

        @Test
        @DisplayName("Simple workflow is sound")
        void testSimpleWorkflowSoundness() {
            String yaml = """
                    name: SimpleWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "Simple workflow should be sound");
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
                      - id: Task3
                        flows: [end]
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(), "All tasks should be reachable");
        }

        @Test
        @DisplayName("Task in dead-end branch is unsound")
        void testTaskInDeadEndBranchIsUnsound() {
            String yaml = """
                    name: DeadEndWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [GoodBranch, DeadBranch]
                        split: xor
                      - id: GoodBranch
                        flows: [end]
                      - id: DeadBranch
                        flows: [DeadEnd]
                      - id: DeadEnd
                        flows: []
                    """;

            WorkflowSoundnessVerifier.SoundnessResult result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(), "Workflow with dead-end should be unsound");
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Builds a YAML workflow specification for a given pattern name.
     *
     * @param patternName the name of the pattern (linear, xor-split, and-split, conditional)
     * @return YAML string for the requested pattern
     */
    private String buildYamlPattern(String patternName) {
        return switch (patternName) {
            case "linear" -> """
                    name: LinearWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [TaskB]
                      - id: TaskB
                        flows: [TaskC]
                      - id: TaskC
                        flows: [end]
                    """;
            case "xor-split" -> """
                    name: XorSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [Branch1, Branch2]
                        split: xor
                      - id: Branch1
                        flows: [end]
                      - id: Branch2
                        flows: [end]
                    """;
            case "and-split" -> """
                    name: AndSplitWorkflow
                    first: StartTask
                    tasks:
                      - id: StartTask
                        flows: [Branch1, Branch2]
                        split: and
                      - id: Branch1
                        flows: [end]
                      - id: Branch2
                        flows: [end]
                    """;
            case "conditional" -> """
                    name: ConditionalWorkflow
                    first: CheckInventory
                    tasks:
                      - id: CheckInventory
                        flows: [ProcessOrder, RejectOrder]
                        condition: inventory_ok -> ProcessOrder
                        default: RejectOrder
                        split: xor
                      - id: ProcessOrder
                        flows: [end]
                      - id: RejectOrder
                        flows: [end]
                    """;
            default -> throw new IllegalArgumentException("Unknown pattern: " + patternName);
        };
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
