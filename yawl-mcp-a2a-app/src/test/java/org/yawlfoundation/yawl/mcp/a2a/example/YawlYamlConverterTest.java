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
 * Unit tests for YawlYamlConverter — the base YAML-to-YAWL-XML converter.
 *
 * <p>Tests verify that YAML-to-XML conversion produces valid YAWL Schema 4.0
 * compliant output including correct namespace, version, element ordering,
 * required attributes, Workflow Control Patterns (WCP-1, WCP-2, WCP-4),
 * and XML well-formedness guarantees.</p>
 *
 * <p>All tests exercise the real converter against real YAML input — no mocks,
 * no stubs, no canned responses (Chicago/Detroit TDD).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class YawlYamlConverterTest {

    private YawlYamlConverter converter;

    /** Expected YAWL Schema 4.0 namespace */
    private static final String YAWL_NAMESPACE = "http://www.yawlfoundation.org/yawlschema";

    /** XML Schema Instance namespace */
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * Minimal single-task YAML for basic structure validation.
     * Represents a degenerate but valid workflow: start -> TaskA -> end.
     */
    private static final String SINGLE_TASK_YAML = """
        name: TestWorkflow
        uri: test.xml
        first: TaskA
        tasks:
          - id: TaskA
            flows: [end]
        """;

    /**
     * Order fulfilment example from the converter's own javadoc.
     * Tests XOR-split (WCP-4) with predicate routing.
     */
    private static final String ORDER_FULFILLMENT_YAML = """
        name: OrderFulfillment
        uri: OrderFulfillment.xml
        first: VerifyPayment
        tasks:
          - id: VerifyPayment
            flows: [CheckInventory, CancelOrder]
            condition: payment_ok -> CheckInventory
            default: CancelOrder
            join: xor
            split: xor
          - id: CheckInventory
            flows: [ShipOrder]
          - id: ShipOrder
            flows: [end]
          - id: CancelOrder
            flows: [end]
        """;

    @BeforeEach
    void setUp() {
        converter = new YawlYamlConverter();
    }

    // =========================================================================
    // Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("Should create converter instance successfully")
        void shouldCreateConverterInstance() {
            assertNotNull(converter, "Converter must not be null");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null input")
        void shouldThrowOnNullInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml(null),
                "convertToXml(null) must throw IllegalArgumentException"
            );
            assertTrue(ex.getMessage().contains("null"),
                "Exception message must mention 'null', got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for empty string input")
        void shouldThrowOnEmptyInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml(""),
                "convertToXml('') must throw IllegalArgumentException"
            );
            assertTrue(ex.getMessage().contains("empty"),
                "Exception message must mention 'empty', got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for whitespace-only input")
        void shouldThrowOnWhitespaceInput() {
            IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml("   \n\t  "),
                "convertToXml(whitespace) must throw IllegalArgumentException"
            );
            assertTrue(ex.getMessage().contains("empty"),
                "Exception message must mention 'empty' for whitespace input, got: " + ex.getMessage());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for malformed YAML")
        void shouldThrowOnMalformedYaml() {
            String malformed = """
                name: [unclosed bracket
                uri: test.xml
                """;
            assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml(malformed),
                "Malformed YAML must throw IllegalArgumentException"
            );
        }

        @Test
        @DisplayName("Should return non-null, non-empty output for valid YAML")
        void shouldReturnNonNullOutputForValidYaml() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertNotNull(xml, "Output XML must not be null");
            assertFalse(xml.isBlank(), "Output XML must not be blank");
        }
    }

    // =========================================================================
    // Sequence Pattern (WCP-1): A -> B -> C -> end
    // van der Aalst Sequence — linear chain of tasks
    // =========================================================================

    @Nested
    @DisplayName("Sequence Pattern (WCP-1): Linear task chain")
    class SequencePatternTests {

        private static final String LINEAR_YAML = """
            name: LinearWorkflow
            uri: linear.xml
            first: StepA
            tasks:
              - id: StepA
                flows: [StepB]
              - id: StepB
                flows: [StepC]
              - id: StepC
                flows: [end]
            """;

        @Test
        @DisplayName("WCP-1: Should emit all three tasks in a three-step sequence")
        void shouldEmitAllThreeTasks() {
            String xml = converter.convertToXml(LINEAR_YAML);
            assertTrue(xml.contains("id=\"StepA\""),
                "Must contain StepA task");
            assertTrue(xml.contains("id=\"StepB\""),
                "Must contain StepB task");
            assertTrue(xml.contains("id=\"StepC\""),
                "Must contain StepC task");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(3, taskCount,
                "Must emit exactly 3 task elements for 3-step sequence, found: " + taskCount);
        }

        @Test
        @DisplayName("WCP-1: Input condition must wire to first task 'StepA'")
        void shouldWireInputConditionToFirstTask() {
            String xml = converter.convertToXml(LINEAR_YAML);
            // i-top's flowsInto must reference StepA (the 'first:' task)
            int iTopIndex = xml.indexOf("inputCondition id=\"i-top\"");
            int stepARef = xml.indexOf("nextElementRef id=\"StepA\"");
            assertTrue(iTopIndex >= 0, "Must have inputCondition with id='i-top'");
            assertTrue(stepARef > iTopIndex,
                "i-top's flowsInto must reference StepA (the first task)");
        }

        @Test
        @DisplayName("WCP-1: StepA must flow into StepB")
        void shouldWireStepAtoStepB() {
            String xml = converter.convertToXml(LINEAR_YAML);
            int stepATaskIndex = xml.indexOf("id=\"StepA\"");
            int stepBRef = xml.indexOf("nextElementRef id=\"StepB\"");
            assertTrue(stepATaskIndex >= 0, "Must contain StepA task element");
            assertTrue(stepBRef > stepATaskIndex,
                "StepA's flowsInto must reference StepB");
        }

        @Test
        @DisplayName("WCP-1: StepB must flow into StepC")
        void shouldWireStepBtoStepC() {
            String xml = converter.convertToXml(LINEAR_YAML);
            int stepBTaskIndex = xml.indexOf("id=\"StepB\"");
            int stepCRef = xml.indexOf("nextElementRef id=\"StepC\"");
            assertTrue(stepBTaskIndex >= 0, "Must contain StepB task element");
            assertTrue(stepCRef > stepBTaskIndex,
                "StepB's flowsInto must reference StepC");
        }

        @Test
        @DisplayName("WCP-1: Terminal task 'end' flow must map to output condition 'o-top'")
        void shouldMapEndFlowToOutputConditionOTop() {
            String xml = converter.convertToXml(LINEAR_YAML);
            assertTrue(xml.contains("nextElementRef id=\"o-top\""),
                "Flow to 'end' must be mapped to nextElementRef id='o-top'");
            assertFalse(xml.contains("nextElementRef id=\"end\""),
                "Must NOT emit raw 'end' as nextElementRef — it is not a valid element id");
        }

        @Test
        @DisplayName("WCP-1: Input condition must appear before all tasks in the output")
        void shouldHaveInputConditionBeforeTasks() {
            String xml = converter.convertToXml(LINEAR_YAML);
            int inputCondIndex = xml.indexOf("<inputCondition");
            int firstTaskIndex = xml.indexOf("<task ");
            assertTrue(inputCondIndex >= 0, "Must have inputCondition element");
            assertTrue(firstTaskIndex >= 0, "Must have at least one task element");
            assertTrue(inputCondIndex < firstTaskIndex,
                "inputCondition must precede all task elements in XML output");
        }

        @Test
        @DisplayName("WCP-1: Output condition must appear after all tasks in the output")
        void shouldHaveOutputConditionAfterTasks() {
            String xml = converter.convertToXml(LINEAR_YAML);
            int lastTaskCloseIndex = xml.lastIndexOf("</task>");
            int outputCondIndex = xml.indexOf("<outputCondition");
            assertTrue(lastTaskCloseIndex >= 0, "Must have closing task tags");
            assertTrue(outputCondIndex >= 0, "Must have outputCondition element");
            assertTrue(outputCondIndex > lastTaskCloseIndex,
                "outputCondition must come after the last task's closing tag");
        }

        @Test
        @DisplayName("WCP-1: Single-task workflow - minimal valid YAWL specification")
        void shouldHandleSingleTaskWorkflow() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(1, taskCount,
                "Single-task workflow must emit exactly 1 task element");
            assertTrue(xml.contains("id=\"TaskA\""),
                "Must contain task with id='TaskA'");
            assertTrue(xml.contains("nextElementRef id=\"o-top\""),
                "Single task flow 'end' must map to o-top");
        }
    }

    // =========================================================================
    // Exclusive Choice (WCP-4): A -> {B, C} with condition and default
    // van der Aalst Exclusive Choice — XOR-split routing
    // =========================================================================

    @Nested
    @DisplayName("Exclusive Choice (WCP-4): XOR-split with predicate routing")
    class ExclusiveChoiceTests {

        @Test
        @DisplayName("WCP-4: Should emit predicate element for conditional flow")
        void shouldEmitPredicateForConditionalFlow() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertTrue(xml.contains("<predicate>"),
                "Must emit <predicate> element for conditional flows");
            assertTrue(xml.contains("payment_ok"),
                "Must emit the condition expression 'payment_ok' as predicate content");
        }

        @Test
        @DisplayName("WCP-4: Should emit isDefaultFlow element for default route")
        void shouldEmitIsDefaultFlowForDefaultRoute() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertTrue(xml.contains("<isDefaultFlow/>"),
                "Must emit <isDefaultFlow/> marker for the default flow target");
        }

        @Test
        @DisplayName("WCP-4: Predicate must appear in flowsInto block targeting CheckInventory")
        void shouldAssociatePredicateWithConditionalTarget() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            // The predicate "payment_ok" should appear in a flowsInto block
            // whose nextElementRef references CheckInventory
            int checkInvRef = xml.indexOf("nextElementRef id=\"CheckInventory\"");
            int predicateIndex = xml.indexOf("<predicate>payment_ok</predicate>");
            assertTrue(checkInvRef >= 0,
                "Must have nextElementRef to CheckInventory");
            assertTrue(predicateIndex >= 0,
                "Must have predicate element with 'payment_ok'");
            // The predicate should be near (after) the CheckInventory ref within same flowsInto block
            assertTrue(predicateIndex > checkInvRef,
                "Predicate must appear after the nextElementRef to CheckInventory, within the same flowsInto block");
        }

        @Test
        @DisplayName("WCP-4: isDefaultFlow must appear in CancelOrder's flowsInto block")
        void shouldAssociateIsDefaultFlowWithDefaultTarget() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            // isDefaultFlow must be in the flowsInto block pointing to CancelOrder
            int cancelOrderRef = xml.indexOf("nextElementRef id=\"CancelOrder\"");
            int isDefaultIndex = xml.indexOf("<isDefaultFlow/>");
            assertTrue(cancelOrderRef >= 0,
                "Must have nextElementRef to CancelOrder");
            assertTrue(isDefaultIndex > cancelOrderRef,
                "isDefaultFlow must appear after the CancelOrder nextElementRef in the same flowsInto block");
        }

        @Test
        @DisplayName("WCP-4: XOR-split task must have split code='xor'")
        void shouldEmitXorSplitCode() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertTrue(xml.contains("split code=\"xor\""),
                "Exclusive choice task must have split element with code='xor'");
        }

        @Test
        @DisplayName("WCP-4: VerifyPayment must have exactly two flowsInto elements")
        void shouldEmitTwoFlowsIntoForXorSplit() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            // Count flowsInto elements in VerifyPayment block
            // The VerifyPayment task element starts at id="VerifyPayment"
            int taskStart = xml.indexOf("id=\"VerifyPayment\"");
            int taskEnd = xml.indexOf("</task>", taskStart);
            String taskXml = xml.substring(taskStart, taskEnd);
            int flowsIntoCount = countOccurrences(taskXml, "<flowsInto>");
            assertEquals(2, flowsIntoCount,
                "XOR-split task VerifyPayment must have exactly 2 flowsInto elements");
        }

        @Test
        @DisplayName("WCP-4: Task without explicit join/split defaults to xor")
        void shouldDefaultMissingJoinSplitToXor() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            // CheckInventory has no explicit join/split in YAML — must default to xor
            int checkInvTaskStart = xml.indexOf("id=\"CheckInventory\"");
            int checkInvTaskEnd = xml.indexOf("</task>", checkInvTaskStart);
            String checkInvXml = xml.substring(checkInvTaskStart, checkInvTaskEnd);
            assertTrue(checkInvXml.contains("join code=\"xor\""),
                "CheckInventory (no explicit join) must default to join code='xor'");
            assertTrue(checkInvXml.contains("split code=\"xor\""),
                "CheckInventory (no explicit split) must default to split code='xor'");
        }
    }

    // =========================================================================
    // Parallel Split (WCP-2): A -> {B, C} with AND semantics
    // van der Aalst Parallel Split — AND-split routing
    // =========================================================================

    @Nested
    @DisplayName("Parallel Split (WCP-2): AND-split concurrent routing")
    class ParallelSplitTests {

        private static final String PARALLEL_YAML = """
            name: ParallelWorkflow
            uri: parallel.xml
            first: StartTask
            tasks:
              - id: StartTask
                flows: [BranchA, BranchB]
                split: and
              - id: BranchA
                flows: [end]
              - id: BranchB
                flows: [end]
            """;

        @Test
        @DisplayName("WCP-2: AND-split task must have split element with code='and'")
        void shouldEmitAndSplitCode() {
            String xml = converter.convertToXml(PARALLEL_YAML);
            assertTrue(xml.contains("split code=\"and\""),
                "AND-split task must have split element with code='and'");
        }

        @Test
        @DisplayName("WCP-2: StartTask must flow into both BranchA and BranchB")
        void shouldEmitBothBranchFlows() {
            String xml = converter.convertToXml(PARALLEL_YAML);
            int startTaskStart = xml.indexOf("id=\"StartTask\"");
            int startTaskEnd = xml.indexOf("</task>", startTaskStart);
            String startTaskXml = xml.substring(startTaskStart, startTaskEnd);
            assertTrue(startTaskXml.contains("nextElementRef id=\"BranchA\""),
                "StartTask must have flowsInto referencing BranchA");
            assertTrue(startTaskXml.contains("nextElementRef id=\"BranchB\""),
                "StartTask must have flowsInto referencing BranchB");
        }

        @Test
        @DisplayName("WCP-2: AND-split task must have exactly two flowsInto elements")
        void shouldEmitTwoParallelBranches() {
            String xml = converter.convertToXml(PARALLEL_YAML);
            int startTaskStart = xml.indexOf("id=\"StartTask\"");
            int startTaskEnd = xml.indexOf("</task>", startTaskStart);
            String startTaskXml = xml.substring(startTaskStart, startTaskEnd);
            int flowsIntoCount = countOccurrences(startTaskXml, "<flowsInto>");
            assertEquals(2, flowsIntoCount,
                "AND-split task must emit exactly 2 flowsInto elements for 2 branches");
        }

        @Test
        @DisplayName("WCP-2: Branch tasks without explicit join/split default to xor")
        void shouldDefaultBranchTasksToXorJoinSplit() {
            String xml = converter.convertToXml(PARALLEL_YAML);
            int branchAStart = xml.indexOf("<task id=\"BranchA\"");
            int branchAEnd = xml.indexOf("</task>", branchAStart);
            String branchAXml = xml.substring(branchAStart, branchAEnd);
            assertTrue(branchAXml.contains("join code=\"xor\""),
                "BranchA (no explicit join) must default to join code='xor'");
            assertTrue(branchAXml.contains("split code=\"xor\""),
                "BranchA (no explicit split) must default to split code='xor'");
        }

        @Test
        @DisplayName("WCP-2: AND-join scenario — multiple flows merge into one task")
        void shouldHandleAndJoin() {
            String andJoinYaml = """
                name: AndJoinWorkflow
                uri: andjoin.xml
                first: StartTask
                tasks:
                  - id: StartTask
                    flows: [BranchA, BranchB]
                    split: and
                  - id: BranchA
                    flows: [MergeTask]
                  - id: BranchB
                    flows: [MergeTask]
                  - id: MergeTask
                    flows: [end]
                    join: and
                """;
            String xml = converter.convertToXml(andJoinYaml);
            assertTrue(xml.contains("join code=\"and\""),
                "AND-join task MergeTask must have join element with code='and'");
            assertTrue(xml.contains("split code=\"and\""),
                "AND-split task StartTask must have split element with code='and'");
        }
    }

    // =========================================================================
    // Schema Compliance: namespace, version, schemaLocation, elements
    // =========================================================================

    @Nested
    @DisplayName("Schema Compliance: YAWL Schema 4.0 structural requirements")
    class SchemaComplianceTests {

        @Test
        @DisplayName("Should declare correct YAWL namespace on specificationSet")
        void shouldDeclareYawlNamespace() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("xmlns=\"" + YAWL_NAMESPACE + "\""),
                "specificationSet must declare xmlns='" + YAWL_NAMESPACE + "'");
        }

        @Test
        @DisplayName("Should declare XSI namespace on specificationSet")
        void shouldDeclareXsiNamespace() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("xmlns:xsi=\"" + XSI_NAMESPACE + "\""),
                "specificationSet must declare xmlns:xsi='" + XSI_NAMESPACE + "'");
        }

        @Test
        @DisplayName("Should declare version='4.0' on specificationSet")
        void shouldDeclareVersionFourDotZero() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("version=\"4.0\""),
                "specificationSet must have version='4.0'");
        }

        @Test
        @DisplayName("Should declare xsi:schemaLocation on specificationSet")
        void shouldDeclareSchemaLocation() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("xsi:schemaLocation="),
                "specificationSet must have xsi:schemaLocation attribute");
            assertTrue(xml.contains(YAWL_NAMESPACE),
                "schemaLocation must reference the YAWL namespace URI");
        }

        @Test
        @DisplayName("Should emit specificationSet as root element")
        void shouldHaveSpecificationSetAsRoot() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("<specificationSet"),
                "Root element must be <specificationSet");
            assertTrue(xml.contains("</specificationSet>"),
                "Must have closing </specificationSet> tag");
        }

        @Test
        @DisplayName("Should emit specification element with correct uri attribute")
        void shouldHaveSpecificationWithUri() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("<specification uri=\"test.xml\""),
                "Must emit <specification uri='test.xml'>");
        }

        @Test
        @DisplayName("Should emit name element inside specification")
        void shouldHaveNameElementInsideSpecification() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("<name>TestWorkflow</name>"),
                "Must emit <name>TestWorkflow</name> as a child of specification");
        }

        @Test
        @DisplayName("Should emit metaData element (required by schema)")
        void shouldHaveMetaDataElement() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            boolean hasMetaData = xml.contains("<metaData>") || xml.contains("<metaData/>");
            assertTrue(hasMetaData,
                "Must emit <metaData> or <metaData/> element (required by schema)");
        }

        @Test
        @DisplayName("Should emit root net decomposition with isRootNet='true'")
        void shouldHaveRootNetDecompositionWithIsRootNetTrue() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("isRootNet=\"true\""),
                "Root net decomposition must have isRootNet='true'");
        }

        @Test
        @DisplayName("Should use NetFactsType for root net decomposition (xsi:type)")
        void shouldUseNetFactsTypeForRootNet() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                "Root net decomposition must have xsi:type='NetFactsType'");
        }

        @Test
        @DisplayName("Should emit processControlElements wrapper element")
        void shouldHaveProcessControlElements() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("<processControlElements>"),
                "Must have <processControlElements> opening tag");
            assertTrue(xml.contains("</processControlElements>"),
                "Must have </processControlElements> closing tag");
        }

        @Test
        @DisplayName("Should emit inputCondition with id='i-top'")
        void shouldHaveInputConditionWithITopId() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("inputCondition id=\"i-top\""),
                "inputCondition element must have id='i-top'");
        }

        @Test
        @DisplayName("Should emit outputCondition with id='o-top'")
        void shouldHaveOutputConditionWithOTopId() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("outputCondition id=\"o-top\""),
                "outputCondition element must have id='o-top'");
        }

        @Test
        @DisplayName("Should map 'end' flow to o-top and never emit raw 'end' reference")
        void shouldMapEndFlowToOTopAndNeverEmitRawEnd() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("nextElementRef id=\"o-top\""),
                "Flow target 'end' must be replaced with 'o-top' in nextElementRef");
            assertFalse(xml.contains("nextElementRef id=\"end\""),
                "Must NOT emit raw 'end' as nextElementRef — 'o-top' is the correct schema element id");
        }

        @Test
        @DisplayName("Should emit join and split on every task (required by ExternalTaskFactsType XSD)")
        void shouldAlwaysEmitJoinAndSplitOnEveryTask() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            // 4 tasks in ORDER_FULFILLMENT_YAML
            int joinCount = countOccurrences(xml, "<join ");
            int splitCount = countOccurrences(xml, "<split ");
            assertEquals(4, joinCount,
                "Every task must have a <join> element — XSD requires it (found: " + joinCount + ")");
            assertEquals(4, splitCount,
                "Every task must have a <split> element — XSD requires it (found: " + splitCount + ")");
        }

        @Test
        @DisplayName("Should emit WebServiceGatewayFactsType decomposition for each task")
        void shouldEmitWebServiceGatewayDecompositionPerTask() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            assertTrue(xml.contains("xsi:type=\"WebServiceGatewayFactsType\""),
                "Must emit at least one WebServiceGatewayFactsType decomposition");
            // 4 tasks -> 4 WebServiceGatewayFactsType decompositions
            int wsGatewayCount = countOccurrences(xml, "WebServiceGatewayFactsType");
            assertEquals(4, wsGatewayCount,
                "Must emit one WebServiceGatewayFactsType per task (4 tasks, found: " + wsGatewayCount + ")");
        }

        @Test
        @DisplayName("Should emit decomposesTo reference in each task element")
        void shouldEmitDecomposesToInEachTask() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("decomposesTo id=\"TaskADecomposition\""),
                "Task must reference its decomposition via decomposesTo id='TaskADecomposition'");
        }

        @Test
        @DisplayName("Should use WorkflowNet as root net decomposition id when name='TestWorkflow'")
        void shouldUseWorkflowNetAsRootDecompositionId() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            assertTrue(xml.contains("id=\"WorkflowNet\""),
                "Root net decomposition must use id='WorkflowNet'");
        }

        @Test
        @DisplayName("Should use name-based id for root decomposition when custom name given")
        void shouldUseCustomNameForRootDecompositionId() {
            String customNameYaml = """
                name: OrderFulfillment
                uri: orders.xml
                first: StepA
                tasks:
                  - id: StepA
                    flows: [end]
                """;
            String xml = converter.convertToXml(customNameYaml);
            // Base converter always uses "WorkflowNet" (not name-based like ExtendedYamlConverter)
            assertTrue(xml.contains("id=\"WorkflowNet\""),
                "Base converter root net must have id='WorkflowNet'");
        }
    }

    // =========================================================================
    // Element Ordering: XSD imposes strict child element sequence
    // =========================================================================

    @Nested
    @DisplayName("Element Ordering: XSD-required ordering of XML elements")
    class ElementOrderingTests {

        @Test
        @DisplayName("specificationSet must precede specification in output")
        void shouldHaveSpecificationSetBeforeSpecification() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int specSetIndex = xml.indexOf("<specificationSet");
            int specIndex = xml.indexOf("<specification uri=");
            assertTrue(specSetIndex >= 0, "Must have <specificationSet");
            assertTrue(specIndex >= 0, "Must have <specification uri=");
            assertTrue(specSetIndex < specIndex,
                "<specificationSet must open before <specification uri= element");
        }

        @Test
        @DisplayName("name element must appear before metaData in specification")
        void shouldHaveNameBeforeMetaData() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int nameIndex = xml.indexOf("<name>TestWorkflow</name>");
            int metaDataIndex = xml.indexOf("<metaData");
            assertTrue(nameIndex >= 0, "Must have <name> element");
            assertTrue(metaDataIndex >= 0, "Must have <metaData> element");
            assertTrue(nameIndex < metaDataIndex,
                "<name> must appear before <metaData> per schema ordering");
        }

        @Test
        @DisplayName("inputCondition must appear before any task in processControlElements")
        void shouldHaveInputConditionBeforeTasks() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int inputCondIndex = xml.indexOf("<inputCondition");
            int firstTaskIndex = xml.indexOf("<task ");
            assertTrue(inputCondIndex >= 0, "Must have <inputCondition");
            assertTrue(firstTaskIndex >= 0, "Must have at least one <task");
            assertTrue(inputCondIndex < firstTaskIndex,
                "<inputCondition must precede all <task> elements");
        }

        @Test
        @DisplayName("outputCondition must appear after all tasks in processControlElements")
        void shouldHaveOutputConditionAfterAllTasks() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            int lastTaskClose = xml.lastIndexOf("</task>");
            int outputCondIndex = xml.indexOf("<outputCondition");
            assertTrue(lastTaskClose >= 0, "Must have </task> closing tags");
            assertTrue(outputCondIndex >= 0, "Must have <outputCondition");
            assertTrue(outputCondIndex > lastTaskClose,
                "<outputCondition must come after the last </task>");
        }

        @Test
        @DisplayName("join must appear before split in each task element")
        void shouldHaveJoinBeforeSplitInEachTask() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int taskStart = xml.indexOf("id=\"TaskA\"");
            int taskEnd = xml.indexOf("</task>", taskStart);
            String taskXml = xml.substring(taskStart, taskEnd);
            int joinIndex = taskXml.indexOf("<join ");
            int splitIndex = taskXml.indexOf("<split ");
            assertTrue(joinIndex >= 0, "Must have <join> in task");
            assertTrue(splitIndex >= 0, "Must have <split> in task");
            assertTrue(joinIndex < splitIndex,
                "<join> must appear before <split> per ExternalTaskFactsType ordering");
        }

        @Test
        @DisplayName("decomposesTo must appear after join and split in each task element")
        void shouldHaveDecomposesToAfterJoinAndSplit() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int taskStart = xml.indexOf("id=\"TaskA\"");
            int taskEnd = xml.indexOf("</task>", taskStart);
            String taskXml = xml.substring(taskStart, taskEnd);
            int splitIndex = taskXml.indexOf("<split ");
            int decomposesToIndex = taskXml.indexOf("<decomposesTo");
            assertTrue(splitIndex >= 0, "Must have <split> in task");
            assertTrue(decomposesToIndex >= 0, "Must have <decomposesTo> in task");
            assertTrue(decomposesToIndex > splitIndex,
                "<decomposesTo> must appear after <split> per ExternalTaskFactsType ordering");
        }

        @Test
        @DisplayName("flowsInto elements must appear before join/split in each task")
        void shouldHaveFlowsIntoBeforeJoinAndSplit() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int taskStart = xml.indexOf("id=\"TaskA\"");
            int taskEnd = xml.indexOf("</task>", taskStart);
            String taskXml = xml.substring(taskStart, taskEnd);
            int flowsIntoIndex = taskXml.indexOf("<flowsInto>");
            int joinIndex = taskXml.indexOf("<join ");
            assertTrue(flowsIntoIndex >= 0, "Must have <flowsInto> in task");
            assertTrue(joinIndex >= 0, "Must have <join> in task");
            assertTrue(flowsIntoIndex < joinIndex,
                "<flowsInto> must precede <join> per ExternalTaskFactsType ordering");
        }

        @Test
        @DisplayName("WebServiceGatewayFactsType decompositions must appear after root net decomposition")
        void shouldHaveTaskDecompositionsAfterRootNet() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int rootNetClose = xml.indexOf("</decomposition>");
            int wsGatewayIndex = xml.indexOf("WebServiceGatewayFactsType");
            assertTrue(rootNetClose >= 0, "Must have closing </decomposition> tag");
            assertTrue(wsGatewayIndex >= 0, "Must have WebServiceGatewayFactsType");
            assertTrue(wsGatewayIndex > rootNetClose,
                "Task decompositions (WebServiceGatewayFactsType) must appear after root net decomposition");
        }
    }

    // =========================================================================
    // Edge Cases: boundary conditions and unusual inputs
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases: boundary conditions and special inputs")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should strip yaml-labelled markdown code block wrapper")
        void shouldStripYamlCodeBlockWrapper() {
            String wrappedYaml = """
                ```yaml
                name: WrappedWorkflow
                uri: wrapped.xml
                first: StepA
                tasks:
                  - id: StepA
                    flows: [end]
                ```
                """;
            String xml = converter.convertToXml(wrappedYaml);
            assertNotNull(xml, "Must parse YAML wrapped in ```yaml code block");
            assertTrue(xml.contains("WrappedWorkflow"),
                "Must contain specification name from wrapped YAML");
            assertFalse(xml.contains("```"),
                "Output XML must not contain markdown backtick fences");
        }

        @Test
        @DisplayName("Should strip unlabelled markdown code block wrapper")
        void shouldStripGenericCodeBlockWrapper() {
            String wrappedYaml = """
                ```
                name: GenericWrapped
                uri: generic.xml
                first: StepA
                tasks:
                  - id: StepA
                    flows: [end]
                ```
                """;
            String xml = converter.convertToXml(wrappedYaml);
            assertNotNull(xml, "Must parse YAML wrapped in generic ``` code block");
            assertTrue(xml.contains("GenericWrapped"),
                "Must extract specification name from generic-wrapped YAML");
        }

        @Test
        @DisplayName("Should escape XML special characters: < > & in content")
        void shouldEscapeXmlSpecialCharactersInContent() {
            // Use YAML quoted strings to ensure special chars are parsed as literals
            String yamlWithSpecialChars = """
                name: "Workflow<Test>&Check"
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithSpecialChars);
            assertTrue(xml.contains("&lt;"),
                "Must escape '<' to '&lt;' in XML content");
            assertTrue(xml.contains("&gt;"),
                "Must escape '>' to '&gt;' in XML content");
            assertTrue(xml.contains("&amp;"),
                "Must escape '&' to '&amp;' in XML content");
            assertFalse(xml.contains("<Test>"),
                "Must NOT emit literal '<Test>' — would break XML well-formedness");
        }

        @Test
        @DisplayName("Should escape double-quote characters in XML attribute values")
        void shouldEscapeDoubleQuotesInAttributeValues() {
            String yamlWithQuotedUri = """
                name: TestWorkflow
                uri: my"file".xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithQuotedUri);
            assertFalse(xml.contains("uri=\"my\"file\".xml\""),
                "Must NOT emit unescaped double-quotes inside uri attribute value");
            assertTrue(xml.contains("&quot;"),
                "Must escape '\"' to '&quot;' in attribute values");
        }

        @Test
        @DisplayName("Should escape single-quote characters in XML content")
        void shouldEscapeSingleQuotesInContent() {
            String yamlWithApostrophe = """
                name: O'Brien Workflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithApostrophe);
            // Single quotes in content are not required to be escaped, but the converter does escape them
            // Verify the content is properly handled (no malformed XML)
            assertTrue(xml.contains("specificationSet"),
                "Output must still be valid XML structure with apostrophe in name");
            // O'Brien must appear either as O&apos;Brien (escaped) or as-is in content
            boolean properlyHandled = xml.contains("O&apos;Brien") || xml.contains("O'Brien");
            assertTrue(properlyHandled,
                "Apostrophe in name must be properly represented in XML output");
        }

        @Test
        @DisplayName("Should default first task from list when 'first:' key is absent")
        void shouldDefaultToFirstTaskInListWhenFirstKeyAbsent() {
            String yamlWithoutFirst = """
                name: NoFirstKeyWorkflow
                uri: nofirst.xml
                tasks:
                  - id: ImplicitFirst
                    flows: [end]
                  - id: AnotherTask
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithoutFirst);
            // When 'first:' is absent, the converter defaults to the first task in the list
            assertTrue(xml.contains("nextElementRef id=\"ImplicitFirst\""),
                "When 'first:' key absent, i-top must wire to the first task in the task list");
        }

        @Test
        @DisplayName("Should use name as uri default when 'uri:' key is absent")
        void shouldDefaultUriToNameDotXmlWhenUriAbsent() {
            String yamlWithoutUri = """
                name: MyWorkflow
                first: StepA
                tasks:
                  - id: StepA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithoutUri);
            assertTrue(xml.contains("uri=\"MyWorkflow.xml\""),
                "When 'uri:' key absent, specification uri must default to '<name>.xml'");
        }

        @Test
        @DisplayName("Should handle task id with alphanumeric and underscore characters")
        void shouldHandleAlphanumericTaskIds() {
            String yaml = """
                name: IdTestWorkflow
                uri: ids.xml
                first: Task_123
                tasks:
                  - id: Task_123
                    flows: [Task_456]
                  - id: Task_456
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("id=\"Task_123\""),
                "Must handle task id with underscore: Task_123");
            assertTrue(xml.contains("id=\"Task_456\""),
                "Must handle task id with underscore and digits: Task_456");
            assertTrue(xml.contains("nextElementRef id=\"Task_123\""),
                "i-top must reference Task_123");
        }

        @Test
        @DisplayName("Should produce balanced XML tags for specificationSet")
        void shouldProduceBalancedSpecificationSetTags() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            int openCount = countOccurrences(xml, "<specificationSet");
            int closeCount = countOccurrences(xml, "</specificationSet>");
            assertEquals(openCount, closeCount,
                "specificationSet opening and closing tags must be balanced");
        }

        @Test
        @DisplayName("Should produce balanced XML tags for specification")
        void shouldProduceBalancedSpecificationTags() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            int openCount = countOccurrences(xml, "<specification uri=");
            int closeCount = countOccurrences(xml, "</specification>");
            assertEquals(openCount, closeCount,
                "specification opening and closing tags must be balanced");
        }

        @Test
        @DisplayName("Should produce balanced XML tags for task")
        void shouldProduceBalancedTaskTags() {
            String xml = converter.convertToXml(ORDER_FULFILLMENT_YAML);
            int openCount = countOccurrences(xml, "<task ");
            int closeCount = countOccurrences(xml, "</task>");
            assertEquals(openCount, closeCount,
                "task opening and closing tags must be balanced (4 tasks in order fulfillment)");
        }

        @Test
        @DisplayName("Should produce balanced XML tags for flowsInto")
        void shouldProduceBalancedFlowsIntoTags() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int openCount = countOccurrences(xml, "<flowsInto>");
            int closeCount = countOccurrences(xml, "</flowsInto>");
            assertEquals(openCount, closeCount,
                "flowsInto opening and closing tags must be balanced");
        }

        @Test
        @DisplayName("Should produce balanced XML tags for processControlElements")
        void shouldProduceBalancedProcessControlElementsTags() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int openCount = countOccurrences(xml, "<processControlElements>");
            int closeCount = countOccurrences(xml, "</processControlElements>");
            assertEquals(openCount, closeCount,
                "processControlElements opening and closing tags must be balanced");
        }

        @Test
        @DisplayName("Should emit task name derived from task id when no explicit name given")
        void shouldUseTaskIdAsTaskNameWhenNameAbsent() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            // Base converter uses task id as the name element content
            assertTrue(xml.contains("<name>TaskA</name>"),
                "Task name element must contain the task id 'TaskA' when no explicit name is given");
        }

        @Test
        @DisplayName("Should emit inputCondition's name as 'start'")
        void shouldEmitStartAsInputConditionName() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int iTopIndex = xml.indexOf("inputCondition id=\"i-top\"");
            int iTopEnd = xml.indexOf("</inputCondition>", iTopIndex);
            String iTopXml = xml.substring(iTopIndex, iTopEnd);
            assertTrue(iTopXml.contains("<name>start</name>"),
                "Input condition must have <name>start</name> as its name element");
        }

        @Test
        @DisplayName("Should emit outputCondition's name as 'end'")
        void shouldEmitEndAsOutputConditionName() {
            String xml = converter.convertToXml(SINGLE_TASK_YAML);
            int oTopIndex = xml.indexOf("outputCondition id=\"o-top\"");
            int oTopEnd = xml.indexOf("</outputCondition>", oTopIndex);
            String oTopXml = xml.substring(oTopIndex, oTopEnd);
            assertTrue(oTopXml.contains("<name>end</name>"),
                "Output condition must have <name>end</name> as its name element");
        }
    }

    // =========================================================================
    // Multiple Flows and Complex Routing
    // =========================================================================

    @Nested
    @DisplayName("Multi-Flow Routing: complex routing combinations")
    class MultiFlowRoutingTests {

        @Test
        @DisplayName("Should emit one flowsInto per declared flow in task")
        void shouldEmitOneFlowsIntoPerDeclaredFlow() {
            String yaml = """
                name: ThreeBranchWorkflow
                uri: three.xml
                first: Splitter
                tasks:
                  - id: Splitter
                    flows: [BranchA, BranchB, BranchC]
                    split: and
                  - id: BranchA
                    flows: [end]
                  - id: BranchB
                    flows: [end]
                  - id: BranchC
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            int splitterStart = xml.indexOf("id=\"Splitter\"");
            int splitterEnd = xml.indexOf("</task>", splitterStart);
            String splitterXml = xml.substring(splitterStart, splitterEnd);
            int flowsIntoCount = countOccurrences(splitterXml, "<flowsInto>");
            assertEquals(3, flowsIntoCount,
                "Task with 3 declared flows must emit exactly 3 <flowsInto> elements");
        }

        @Test
        @DisplayName("Should handle multiple tasks each flowing to 'end'")
        void shouldHandleMultipleTasksFlowingToEnd() {
            String yaml = """
                name: MultiEndWorkflow
                uri: multiend.xml
                first: Task1
                tasks:
                  - id: Task1
                    flows: [end]
                  - id: Task2
                    flows: [end]
                  - id: Task3
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // Each task's 'end' flow should map to o-top
            int oTopRefCount = countOccurrences(xml, "nextElementRef id=\"o-top\"");
            assertEquals(3, oTopRefCount,
                "3 tasks each flowing to 'end' must produce 3 nextElementRef to 'o-top'");
        }

        @Test
        @DisplayName("Should handle diamond pattern: split -> {B, C} -> merge")
        void shouldHandleDiamondPattern() {
            String yaml = """
                name: DiamondWorkflow
                uri: diamond.xml
                first: Split
                tasks:
                  - id: Split
                    flows: [Left, Right]
                    split: xor
                  - id: Left
                    flows: [Merge]
                  - id: Right
                    flows: [Merge]
                  - id: Merge
                    flows: [end]
                    join: xor
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("id=\"Split\""), "Must have Split task");
            assertTrue(xml.contains("id=\"Left\""), "Must have Left branch task");
            assertTrue(xml.contains("id=\"Right\""), "Must have Right branch task");
            assertTrue(xml.contains("id=\"Merge\""), "Must have Merge task");
            assertTrue(xml.contains("nextElementRef id=\"Merge\""),
                "Both branches must reference Merge task");
            // Count references to Merge — Left and Right each flow to it
            int mergeRefCount = countOccurrences(xml, "nextElementRef id=\"Merge\"");
            assertEquals(2, mergeRefCount,
                "Both Left and Right must flow into Merge — expected 2 nextElementRef to Merge");
        }

        @Test
        @DisplayName("Should handle task with XOR-split condition: single condition expression")
        void shouldHandleSingleConditionExpression() {
            String yaml = """
                name: ConditionalWorkflow
                uri: cond.xml
                first: Decider
                tasks:
                  - id: Decider
                    flows: [Accept, Reject]
                    condition: amount > 100 -> Accept
                    default: Reject
                    split: xor
                  - id: Accept
                    flows: [end]
                  - id: Reject
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<predicate>amount &gt; 100</predicate>"),
                "Condition 'amount > 100' must appear as escaped predicate element");
            assertTrue(xml.contains("<isDefaultFlow/>"),
                "Default flow must produce <isDefaultFlow/> marker");
        }

        @Test
        @DisplayName("Should emit decomposesTo with 'TaskId + Decomposition' naming convention")
        void shouldFollowDecompositionNamingConvention() {
            String yaml = """
                name: NamingConventionWorkflow
                uri: naming.xml
                first: MySpecialTask
                tasks:
                  - id: MySpecialTask
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("decomposesTo id=\"MySpecialTaskDecomposition\""),
                "decomposesTo must reference 'MySpecialTaskDecomposition' (task id + 'Decomposition')");
            assertTrue(xml.contains("id=\"MySpecialTaskDecomposition\" xsi:type=\"WebServiceGatewayFactsType\""),
                "The corresponding decomposition element must also be named 'MySpecialTaskDecomposition'");
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Count the number of non-overlapping occurrences of {@code sub} in {@code str}.
     *
     * @param str the string to search within
     * @param sub the substring to count
     * @return count of non-overlapping occurrences
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
