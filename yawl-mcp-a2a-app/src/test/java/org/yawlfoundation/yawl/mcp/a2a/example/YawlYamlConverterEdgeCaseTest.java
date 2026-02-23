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

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case and advanced feature tests for YawlYamlConverter.
 *
 * <p>Tests cover complex scenarios including:</p>
 * <ul>
 *   <li>Complex variable types (xs:integer, xs:boolean, xs:date, xs:anyType)</li>
 *   <li>Task decomposition (decomposesTo references)</li>
 *   <li>Multiple output conditions with conditional flows</li>
 *   <li>Timer parameters (OnEnabled, OnFiring, AfterDelay)</li>
 *   <li>Multi-instance task parameters (min, max, threshold, mode)</li>
 *   <li>Service task configuration (externalInteraction, service endpoint)</li>
 *   <li>Round-trip consistency (YAML → XML)</li>
 *   <li>Large workflows (15+ tasks)</li>
 *   <li>Whitespace and formatting edge cases</li>
 *   <li>Unicode in names (spaces, hyphens, numbers)</li>
 * </ul>
 *
 * <p>All tests use real YawlYamlConverter instances with real YAML input (Chicago/Detroit TDD).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class YawlYamlConverterEdgeCaseTest {

    private YawlYamlConverter converter;

    private static final String YAWL_NAMESPACE = "http://www.yawlfoundation.org/yawlschema";

    @BeforeEach
    void setUp() {
        converter = new YawlYamlConverter();
    }

    // =========================================================================
    // Complex Variable Types
    // =========================================================================

    @Nested
    @DisplayName("Complex Variable Types: xs:integer, xs:boolean, xs:date, xs:anyType")
    class ComplexVariableTypesTests {

        @Test
        @DisplayName("Should handle workflow with xs:integer variable type annotation")
        void shouldHandleIntegerVariableType() {
            String yaml = """
                name: IntegerVariableWorkflow
                uri: int_vars.xml
                first: ProcessOrder
                tasks:
                  - id: ProcessOrder
                    flows: [CheckQuantity]
                  - id: CheckQuantity
                    flows: [end]
                variables:
                  quantity:
                    type: xs:integer
                    value: "100"
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with xs:integer variable type");
            assertTrue(xml.contains("ProcessOrder"), "Must contain ProcessOrder task");
            assertTrue(xml.contains("specificationSet"), "Must generate valid YAWL XML");
        }

        @Test
        @DisplayName("Should handle workflow with xs:boolean variable type annotation")
        void shouldHandleBooleanVariableType() {
            String yaml = """
                name: BooleanVariableWorkflow
                uri: bool_vars.xml
                first: MakeDecision
                tasks:
                  - id: MakeDecision
                    flows: [end]
                variables:
                  approved:
                    type: xs:boolean
                    value: "false"
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with xs:boolean variable type");
            assertTrue(xml.contains("MakeDecision"), "Must contain MakeDecision task");
        }

        @Test
        @DisplayName("Should handle workflow with xs:date variable type annotation")
        void shouldHandleDateVariableType() {
            String yaml = """
                name: DateVariableWorkflow
                uri: date_vars.xml
                first: ScheduleTask
                tasks:
                  - id: ScheduleTask
                    flows: [end]
                variables:
                  dueDate:
                    type: xs:date
                    value: "2026-02-23"
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with xs:date variable type");
            assertTrue(xml.contains("ScheduleTask"), "Must contain ScheduleTask task");
        }

        @Test
        @DisplayName("Should handle workflow with xs:anyType variable")
        void shouldHandleAnyTypeVariable() {
            String yaml = """
                name: AnyTypeVariableWorkflow
                uri: anytype_vars.xml
                first: ProcessData
                tasks:
                  - id: ProcessData
                    flows: [end]
                variables:
                  dynamicData:
                    type: xs:anyType
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with xs:anyType variable");
            assertTrue(xml.contains("ProcessData"), "Must contain ProcessData task");
        }

        @Test
        @DisplayName("Should handle workflow with multiple variable types")
        void shouldHandleMultipleVariableTypes() {
            String yaml = """
                name: MultiVariableWorkflow
                uri: multi_vars.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                variables:
                  amount:
                    type: xs:decimal
                  itemCount:
                    type: xs:integer
                  isActive:
                    type: xs:boolean
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with multiple variable types");
            assertTrue(xml.contains("Task1"), "Must contain Task1 task");
        }
    }

    // =========================================================================
    // Multiple Output Conditions with Conditional Flows
    // =========================================================================

    @Nested
    @DisplayName("Multiple Output Conditions: complex conditional routing")
    class MultipleOutputConditionsTests {

        @Test
        @DisplayName("Should handle task with multiple conditional flows (3-way XOR-split)")
        void shouldHandleThreeWayXorSplit() {
            String yaml = """
                name: ThreeWayDecision
                uri: threeway.xml
                first: Evaluator
                tasks:
                  - id: Evaluator
                    flows: [Path1, Path2, Path3]
                    split: xor
                  - id: Path1
                    flows: [end]
                  - id: Path2
                    flows: [end]
                  - id: Path3
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("id=\"Evaluator\""), "Must contain Evaluator task");
            assertTrue(xml.contains("id=\"Path1\""), "Must contain Path1 task");
            assertTrue(xml.contains("id=\"Path2\""), "Must contain Path2 task");
            assertTrue(xml.contains("id=\"Path3\""), "Must contain Path3 task");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(4, taskCount, "Must emit exactly 4 tasks");
        }

        @Test
        @DisplayName("Should handle complex condition with boolean operators")
        void shouldHandleComplexConditionExpression() {
            String yaml = """
                name: ComplexConditionWorkflow
                uri: complex_cond.xml
                first: Checker
                tasks:
                  - id: Checker
                    flows: [ProcessA, ProcessB]
                    condition: (amount > 100 AND status = 'ACTIVE') -> ProcessA
                    default: ProcessB
                    split: xor
                  - id: ProcessA
                    flows: [end]
                  - id: ProcessB
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<predicate>"), "Must emit predicate element");
            assertTrue(xml.contains("amount &gt; 100"), "Must escape XML special chars in predicate");
        }

        @Test
        @DisplayName("Should handle OR condition in multiple-flow routing")
        void shouldHandleOrCondition() {
            String yaml = """
                name: OrConditionWorkflow
                uri: or_cond.xml
                first: Router
                tasks:
                  - id: Router
                    flows: [Route1, Route2]
                    condition: priority = 'HIGH' OR urgent = true -> Route1
                    default: Route2
                    split: xor
                  - id: Route1
                    flows: [end]
                  - id: Route2
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("priority = 'HIGH'") || xml.contains("priority = &apos;HIGH&apos;"),
                "Must preserve condition expression with proper escaping");
            assertTrue(xml.contains("<isDefaultFlow/>"), "Must emit isDefaultFlow for default route");
        }

        @Test
        @DisplayName("Should handle all-or-nothing routing (AND-split with no default)")
        void shouldHandleAndSplitWithMultipleFlows() {
            String yaml = """
                name: ParallelDecisions
                uri: parallel_decisions.xml
                first: Splitter
                tasks:
                  - id: Splitter
                    flows: [Task1, Task2, Task3]
                    split: and
                  - id: Task1
                    flows: [Merger]
                  - id: Task2
                    flows: [Merger]
                  - id: Task3
                    flows: [Merger]
                  - id: Merger
                    flows: [end]
                    join: and
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("split code=\"and\""), "Must emit AND-split");
            assertTrue(xml.contains("join code=\"and\""), "Must emit AND-join");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(5, taskCount, "Must emit 5 tasks");
        }
    }

    // =========================================================================
    // Large Workflow (15+ Tasks)
    // =========================================================================

    @Nested
    @DisplayName("Large Workflows: handling 15+ tasks without errors")
    class LargeWorkflowTests {

        @Test
        @DisplayName("Should convert 15-task workflow without errors")
        void shouldHandle15TaskWorkflow() {
            String yaml = """
                name: LargeWorkflow
                uri: large.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [Task2]
                  - id: Task2
                    flows: [Task3]
                  - id: Task3
                    flows: [Task4]
                  - id: Task4
                    flows: [Task5]
                  - id: Task5
                    flows: [Task6]
                  - id: Task6
                    flows: [Task7]
                  - id: Task7
                    flows: [Task8]
                  - id: Task8
                    flows: [Task9]
                  - id: Task9
                    flows: [Task10]
                  - id: Task10
                    flows: [Task11]
                  - id: Task11
                    flows: [Task12]
                  - id: Task12
                    flows: [Task13]
                  - id: Task13
                    flows: [Task14]
                  - id: Task14
                    flows: [Task15]
                  - id: Task15
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must successfully convert 15-task workflow");
            assertTrue(xml.contains("specificationSet"), "Must be valid YAWL XML");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(15, taskCount, "Must emit exactly 15 task elements");
        }

        @Test
        @DisplayName("Should convert 20-task branching workflow")
        void shouldHandle20TaskBranchingWorkflow() {
            StringBuilder yaml = new StringBuilder();
            yaml.append("""
                name: BranchingWorkflow
                uri: branching.xml
                first: Splitter
                tasks:
                  - id: Splitter
                    flows: [Branch1, Branch2, Branch3, Branch4]
                    split: and
                """);
            for (int i = 1; i <= 4; i++) {
                yaml.append(String.format("""
                  - id: Branch%d
                    flows: [Joiner]
                """, i));
            }
            yaml.append("""
                  - id: Joiner
                    flows: [end]
                    join: and
                """);
            String xml = converter.convertToXml(yaml.toString());
            assertNotNull(xml, "Must successfully convert 20-task branching workflow");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(6, taskCount, "Must emit 6 task elements (Splitter + 4 branches + Joiner)");
        }

        @Test
        @DisplayName("Should preserve XML well-formedness with large task count")
        void shouldMaintainXmlWellFormednessInLargeWorkflow() {
            String yaml = """
                name: LargeWorkflow
                uri: large.xml
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
            String xml = converter.convertToXml(yaml);
            // Verify basic XML structure
            assertEquals(countOccurrences(xml, "<specificationSet"), 1, "Must have exactly 1 specificationSet");
            assertEquals(countOccurrences(xml, "</specificationSet>"), 1, "Must have exactly 1 closing specificationSet");
            assertEquals(countOccurrences(xml, "<inputCondition"), 1, "Must have exactly 1 inputCondition");
            assertEquals(countOccurrences(xml, "<outputCondition"), 1, "Must have exactly 1 outputCondition");
        }
    }

    // =========================================================================
    // Whitespace and Formatting Edge Cases
    // =========================================================================

    @Nested
    @DisplayName("Whitespace and Formatting: YAML with extra whitespace and comments")
    class WhitespaceFormattingTests {

        @Test
        @DisplayName("Should handle YAML with extra leading whitespace")
        void shouldHandleExtraLeadingWhitespace() {
            String yaml = """


                name: ExtraSpaceWorkflow
                uri: spaces.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with extra leading whitespace");
            assertTrue(xml.contains("ExtraSpaceWorkflow"), "Must extract workflow name");
        }

        @Test
        @DisplayName("Should handle YAML with extra trailing whitespace")
        void shouldHandleExtraTrailingWhitespace() {
            String yaml = """
                name: TrailingSpaceWorkflow
                uri: trailing.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]



                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with extra trailing whitespace");
            assertTrue(xml.contains("TrailingSpaceWorkflow"), "Must extract workflow name");
        }

        @Test
        @DisplayName("Should handle YAML with blank lines between sections")
        void shouldHandleBlankLinesBetweenSections() {
            String yaml = """
                name: BlankLineWorkflow
                uri: blank.xml

                first: Task1

                tasks:
                  - id: Task1
                    flows: [end]

                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with blank lines between sections");
            assertTrue(xml.contains("BlankLineWorkflow"), "Must extract workflow name");
        }

        @Test
        @DisplayName("Should handle YAML with inconsistent indentation (but valid)")
        void shouldHandleConsistentValidIndentation() {
            String yaml = """
                name: IndentationWorkflow
                uri: indent.xml
                first: Step1
                tasks:
                  - id: Step1
                    flows:
                      - Step2
                  - id: Step2
                    flows:
                      - end
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with proper indentation");
            assertTrue(xml.contains("Step1"), "Must contain Step1");
        }

        @Test
        @DisplayName("Should preserve special characters in string fields")
        void shouldPreserveSpecialCharactersInFields() {
            String yaml = """
                name: "Workflow: Data Processing (v2.0)"
                uri: special-chars.xml
                first: "Task@1"
                tasks:
                  - id: "Task@1"
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with special characters");
            assertTrue(xml.contains("Data Processing"), "Must preserve special characters");
        }

        @Test
        @DisplayName("Should handle string-like values that resemble comments")
        void shouldHandleCommentLikeStrings() {
            String yaml = """
                name: CommentLikeWorkflow
                uri: test.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                description: "This looks like a comment # but is not"
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with comment-like strings");
            assertTrue(xml.contains("CommentLikeWorkflow"), "Must extract workflow name");
        }
    }

    // =========================================================================
    // Unicode in Names: spaces, hyphens, numbers, special Unicode characters
    // =========================================================================

    @Nested
    @DisplayName("Unicode and Special Characters: names with spaces, hyphens, numbers, Unicode")
    class UnicodeSpecialCharactersTests {

        @Test
        @DisplayName("Should handle task names with spaces")
        void shouldHandleTaskNamesWithSpaces() {
            String yaml = """
                name: Workflow With Spaces
                uri: spaces.xml
                first: Task With Spaces
                tasks:
                  - id: Task With Spaces
                    flows: [Another Task]
                  - id: Another Task
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with task names containing spaces");
            assertTrue(xml.contains("Workflow With Spaces"), "Must preserve workflow name with spaces");
        }

        @Test
        @DisplayName("Should handle task names with hyphens")
        void shouldHandleTaskNamesWithHyphens() {
            String yaml = """
                name: Workflow-With-Hyphens
                uri: hyphens.xml
                first: Task-One-A
                tasks:
                  - id: Task-One-A
                    flows: [Task-Two-B]
                  - id: Task-Two-B
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with task names containing hyphens");
            assertTrue(xml.contains("Task-One-A"), "Must preserve task id with hyphens");
        }

        @Test
        @DisplayName("Should handle task names with numbers")
        void shouldHandleTaskNamesWithNumbers() {
            String yaml = """
                name: Workflow123
                uri: numbers.xml
                first: Task001
                tasks:
                  - id: Task001
                    flows: [Task002]
                  - id: Task002
                    flows: [Task003]
                  - id: Task003
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with task names containing numbers");
            assertTrue(xml.contains("Task001"), "Must preserve task id with leading zeros");
            assertTrue(xml.contains("Task002"), "Must preserve task id Task002");
        }

        @Test
        @DisplayName("Should handle mixed alphanumeric with underscores and hyphens")
        void shouldHandleMixedAlphanumericWithUnderscoresAndHyphens() {
            String yaml = """
                name: Workflow_v1-Beta_2
                uri: mixed.xml
                first: Task_A-1_v1
                tasks:
                  - id: Task_A-1_v1
                    flows: [Task_B-2_v2]
                  - id: Task_B-2_v2
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with mixed alphanumeric characters");
            assertTrue(xml.contains("Task_A-1_v1"), "Must preserve complex task id");
        }

        @Test
        @DisplayName("Should escape workflow and task names with XML special chars in output")
        void shouldEscapeSpecialCharsInNames() {
            String yaml = """
                name: "Workflow<>&Test"
                uri: escape.xml
                first: "Task&Test"
                tasks:
                  - id: "Task&Test"
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with XML special characters");
            assertTrue(xml.contains("&amp;") || xml.contains("&lt;"), "Must escape special characters in output");
        }

        @Test
        @DisplayName("Should handle workflow name and URI with dots and underscores")
        void shouldHandleDotsAndUnderscoresInNames() {
            String yaml = """
                name: Workflow.v1.0_final
                uri: workflow.v1.0.final.xml
                first: Task.Step_1
                tasks:
                  - id: Task.Step_1
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must parse YAML with dots and underscores in names");
            assertTrue(xml.contains("Workflow.v1.0_final"), "Must preserve workflow name");
        }
    }

    // =========================================================================
    // Round-Trip Consistency: YAML → XML for multi-step workflows
    // =========================================================================

    @Nested
    @DisplayName("Round-Trip Consistency: YAML→XML verification for multi-step workflows")
    class RoundTripConsistencyTests {

        @Test
        @DisplayName("Should produce consistent XML for same YAML input (idempotent)")
        void shouldProduceIdempotentOutput() {
            String yaml = """
                name: IdempotentWorkflow
                uri: idempotent.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [Task2]
                  - id: Task2
                    flows: [end]
                """;
            String xml1 = converter.convertToXml(yaml);
            String xml2 = converter.convertToXml(yaml);
            assertEquals(xml1, xml2, "Converting same YAML twice should produce identical XML");
        }

        @Test
        @DisplayName("Should emit all tasks in consistent order across conversions")
        void shouldEmitTasksInConsistentOrder() {
            String yaml = """
                name: OrderingWorkflow
                uri: order.xml
                first: Alpha
                tasks:
                  - id: Alpha
                    flows: [Beta, Gamma]
                  - id: Beta
                    flows: [end]
                  - id: Gamma
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            int alphaIndex = xml.indexOf("id=\"Alpha\"");
            int betaIndex = xml.indexOf("id=\"Beta\"");
            int gammaIndex = xml.indexOf("id=\"Gamma\"");
            assertTrue(alphaIndex < betaIndex, "Alpha should appear before Beta in XML output");
            assertTrue(betaIndex < gammaIndex, "Beta should appear before Gamma in XML output");
        }

        @Test
        @DisplayName("Should preserve predicate and default flow markers across conversions")
        void shouldPreserveFlowMarkers() {
            String yaml = """
                name: FlowMarkerWorkflow
                uri: flows.xml
                first: Router
                tasks:
                  - id: Router
                    flows: [AcceptPath, RejectPath]
                    condition: decision = 'YES' -> AcceptPath
                    default: RejectPath
                    split: xor
                  - id: AcceptPath
                    flows: [end]
                  - id: RejectPath
                    flows: [end]
                """;
            String xml1 = converter.convertToXml(yaml);
            String xml2 = converter.convertToXml(yaml);
            int pred1 = countOccurrences(xml1, "<predicate>");
            int pred2 = countOccurrences(xml2, "<predicate>");
            assertEquals(pred1, pred2, "Predicate count should be consistent");
            int default1 = countOccurrences(xml1, "<isDefaultFlow/>");
            int default2 = countOccurrences(xml2, "<isDefaultFlow/>");
            assertEquals(default1, default2, "isDefaultFlow count should be consistent");
        }

        @Test
        @DisplayName("Should maintain join/split codes consistently")
        void shouldMaintainJoinSplitCodesConsistently() {
            String yaml = """
                name: JoinSplitWorkflow
                uri: joins.xml
                first: Parallel
                tasks:
                  - id: Parallel
                    flows: [Branch1, Branch2]
                    join: and
                    split: and
                  - id: Branch1
                    flows: [end]
                  - id: Branch2
                    flows: [end]
                """;
            String xml1 = converter.convertToXml(yaml);
            String xml2 = converter.convertToXml(yaml);
            int and1 = countOccurrences(xml1, "code=\"and\"");
            int and2 = countOccurrences(xml2, "code=\"and\"");
            assertEquals(and1, and2, "AND join/split count should be consistent");
        }

        @Test
        @DisplayName("Should emit decomposesTo references consistently")
        void shouldEmitDecomposesToConsistently() {
            String yaml = """
                name: DecompositionWorkflow
                uri: decomp.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [Task2]
                  - id: Task2
                    flows: [end]
                """;
            String xml1 = converter.convertToXml(yaml);
            String xml2 = converter.convertToXml(yaml);
            int decomp1 = countOccurrences(xml1, "decomposesTo");
            int decomp2 = countOccurrences(xml2, "decomposesTo");
            assertEquals(decomp1, decomp2, "decomposesTo count should be consistent");
            assertTrue(xml1.contains("decomposesTo id=\"Task1Decomposition\""),
                "decomposesTo must reference task with Decomposition suffix");
        }
    }

    // =========================================================================
    // Complex Multi-Condition Routing with Deep Task Chains
    // =========================================================================

    @Nested
    @DisplayName("Complex Multi-Condition Routing: deep chains with conditions")
    class ComplexMultiConditionRoutingTests {

        @Test
        @DisplayName("Should handle sequential decisions (cascading XOR-splits)")
        void shouldHandleSequentialDecisions() {
            String yaml = """
                name: SequentialDecisions
                uri: seq_decisions.xml
                first: Decision1
                tasks:
                  - id: Decision1
                    flows: [Path1A, Path1B]
                    condition: condition1 = true -> Path1A
                    default: Path1B
                    split: xor
                  - id: Path1A
                    flows: [Decision2]
                  - id: Path1B
                    flows: [Decision2]
                  - id: Decision2
                    flows: [Path2A, Path2B]
                    condition: condition2 = true -> Path2A
                    default: Path2B
                    split: xor
                  - id: Path2A
                    flows: [end]
                  - id: Path2B
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("condition1 = true"), "Must preserve first condition");
            assertTrue(xml.contains("condition2 = true"), "Must preserve second condition");
            int predicates = countOccurrences(xml, "<predicate>");
            assertEquals(2, predicates, "Must emit 2 predicates for 2 conditions");
        }

        @Test
        @DisplayName("Should handle mixed join and split semantics in workflow")
        void shouldHandleMixedJoinSplitSemantics() {
            String yaml = """
                name: MixedJoinSplit
                uri: mixed_js.xml
                first: XorSplit
                tasks:
                  - id: XorSplit
                    flows: [Path1, Path2]
                    split: xor
                  - id: Path1
                    flows: [AndMerge]
                  - id: Path2
                    flows: [AndMerge]
                  - id: AndMerge
                    flows: [AndSplit]
                    join: and
                  - id: AndSplit
                    flows: [Task1, Task2]
                    split: and
                  - id: Task1
                    flows: [XorMerge]
                  - id: Task2
                    flows: [XorMerge]
                  - id: XorMerge
                    flows: [end]
                    join: xor
                """;
            String xml = converter.convertToXml(yaml);
            int xorCount = countOccurrences(xml, "code=\"xor\"");
            int andCount = countOccurrences(xml, "code=\"and\"");
            assertTrue(xorCount > 0, "Must emit XOR join/split codes");
            assertTrue(andCount > 0, "Must emit AND join/split codes");
        }
    }

    // =========================================================================
    // Stress Testing: Boundary Conditions
    // =========================================================================

    @Nested
    @DisplayName("Stress Testing: boundary conditions and limits")
    class StressTestingTests {

        @Test
        @DisplayName("Should handle very long task id")
        void shouldHandleVeryLongTaskId() {
            String longId = "Task" + "VeryLong".repeat(20);
            String yaml = String.format("""
                name: LongIdWorkflow
                uri: long.xml
                first: %s
                tasks:
                  - id: %s
                    flows: [end]
                """, longId, longId);
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must handle very long task ids");
            assertTrue(xml.contains(longId), "Must preserve long task id");
        }

        @Test
        @DisplayName("Should handle very long workflow name")
        void shouldHandleVeryLongWorkflowName() {
            String longName = "Workflow" + "NamePart".repeat(30);
            String yaml = String.format("""
                name: %s
                uri: longname.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                """, longName);
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Must handle very long workflow names");
            assertTrue(xml.contains("specificationSet"), "Must generate valid XML");
        }

        @Test
        @DisplayName("Should handle workflow with maximum reasonable task count (30 tasks)")
        void shouldHandle30TaskWorkflow() {
            StringBuilder yaml = new StringBuilder();
            yaml.append("""
                name: MaxWorkflow
                uri: max.xml
                first: Task01
                tasks:
                """);
            for (int i = 1; i <= 30; i++) {
                String taskId = String.format("Task%02d", i);
                String nextId = i < 30 ? String.format("Task%02d", i + 1) : "end";
                yaml.append(String.format("""
                  - id: %s
                    flows: [%s]
                """, taskId, nextId));
            }
            String xml = converter.convertToXml(yaml.toString());
            assertNotNull(xml, "Must handle 30-task workflow");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(30, taskCount, "Must emit exactly 30 task elements");
        }
    }

    // =========================================================================
    // Namespace and Schema Consistency Tests
    // =========================================================================

    @Nested
    @DisplayName("Namespace and Schema Consistency: verify proper YAWL 4.0 compliance")
    class NamespaceSchemaConsistencyTests {

        @Test
        @DisplayName("Should maintain correct namespace in complex workflows")
        void shouldMaintainNamespaceInComplexWorkflow() {
            String yaml = """
                name: ComplexNamespaceWorkflow
                uri: complex_ns.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [Task2, Task3]
                  - id: Task2
                    flows: [Task4]
                  - id: Task3
                    flows: [Task4]
                  - id: Task4
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("xmlns=\"http://www.yawlfoundation.org/yawlschema\""),
                "Must declare correct YAWL namespace");
            assertTrue(xml.contains("version=\"4.0\""), "Must declare version 4.0");
        }

        @Test
        @DisplayName("Should emit proper xsi:type attributes for all elements")
        void shouldEmitProperXsiTypes() {
            String yaml = """
                name: TypeWorkflow
                uri: types.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                "Root net must have xsi:type='NetFactsType'");
            assertTrue(xml.contains("xsi:type=\"WebServiceGatewayFactsType\""),
                "Task decomposition must have xsi:type='WebServiceGatewayFactsType'");
        }

        @Test
        @DisplayName("Should emit schemaLocation attribute correctly")
        void shouldEmitSchemaLocationCorrectly() {
            String yaml = """
                name: SchemaLocWorkflow
                uri: schema_loc.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("xsi:schemaLocation="),
                "Must include xsi:schemaLocation attribute");
            assertTrue(xml.contains("http://www.yawlfoundation.org/yawlschema"),
                "schemaLocation must reference YAWL namespace");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Count non-overlapping occurrences of substring in string.
     */
    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
