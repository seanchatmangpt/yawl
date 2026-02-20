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
 * Unit tests for ExtendedYamlConverter schema compliance.
 *
 * <p>Tests verify that YAML-to-XML conversion produces valid YAWL 4.0 schema
 * compliant output including correct namespace, version, element ordering,
 * and required attributes.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class ExtendedYamlConverterTest {

    private ExtendedYamlConverter converter;

    /** Expected YAWL Schema 4.0 namespace */
    private static final String YAWL_NAMESPACE = "http://www.yawlfoundation.org/yawlschema";

    /** XML Schema Instance namespace */
    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";

    /** Simple test YAML for basic structure validation */
    private static final String SIMPLE_YAML = """
        name: TestWorkflow
        uri: test.xml
        first: TaskA
        tasks:
          - id: TaskA
            name: Task A
            description: First task
            flows: [end]
        """;

    @BeforeEach
    void setUp() {
        converter = new ExtendedYamlConverter();
    }

    @Nested
    @DisplayName("Converter Initialization")
    class ConverterInitializationTests {

        @Test
        @DisplayName("Should create converter instance")
        void shouldCreateConverterInstance() {
            assertNotNull(converter, "Converter should not be null");
        }

        @Test
        @DisplayName("Should throw on null input")
        void shouldThrowOnNullInput() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml(null),
                "Should throw IllegalArgumentException for null input"
            );
            assertTrue(exception.getMessage().contains("null"),
                "Exception message should mention null");
        }

        @Test
        @DisplayName("Should throw on empty input")
        void shouldThrowOnEmptyInput() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml(""),
                "Should throw IllegalArgumentException for empty input"
            );
            assertTrue(exception.getMessage().contains("empty"),
                "Exception message should mention empty");
        }

        @Test
        @DisplayName("Should throw on whitespace-only input")
        void shouldThrowOnWhitespaceOnlyInput() {
            IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToXml("   \n\t  "),
                "Should throw IllegalArgumentException for whitespace-only input"
            );
            assertTrue(exception.getMessage().contains("empty"),
                "Exception message should mention empty");
        }
    }

    @Nested
    @DisplayName("Basic XML Structure")
    class BasicXmlStructureTests {

        @Test
        @DisplayName("Should produce non-null output for valid YAML")
        void shouldProduceNonNullOutput() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertNotNull(xml, "XML output should not be null");
        }

        @Test
        @DisplayName("Should produce non-empty output")
        void shouldProduceNonEmptyOutput() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertFalse(xml.isEmpty(), "XML output should not be empty");
        }

        @Test
        @DisplayName("Should contain specificationSet element")
        void shouldContainSpecificationSet() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<specificationSet"),
                "Should contain specificationSet opening tag");
            assertTrue(xml.contains("</specificationSet>"),
                "Should contain specificationSet closing tag");
        }

        @Test
        @DisplayName("Should contain specification element")
        void shouldContainSpecification() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<specification"),
                "Should contain specification opening tag");
            assertTrue(xml.contains("</specification>"),
                "Should contain specification closing tag");
        }

        @Test
        @DisplayName("Should have correct URI attribute on specification")
        void shouldHaveCorrectUriAttribute() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("uri=\"test.xml\""),
                "Should have uri='test.xml' attribute");
        }
    }

    @Nested
    @DisplayName("Schema Compliance")
    class SchemaComplianceTests {

        @Test
        @DisplayName("Should use correct YAWL namespace")
        void shouldUseCorrectYawlNamespace() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains(YAWL_NAMESPACE),
                "Should contain YAWL namespace: " + YAWL_NAMESPACE);
        }

        @Test
        @DisplayName("Should have xmlns declaration")
        void shouldHaveXmlnsDeclaration() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("xmlns="),
                "Should have xmlns attribute");
        }

        @Test
        @DisplayName("Should have XSI namespace declaration")
        void shouldHaveXsiNamespaceDeclaration() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("xmlns:xsi="),
                "Should have xmlns:xsi attribute");
            assertTrue(xml.contains(XSI_NAMESPACE),
                "Should reference XSI namespace: " + XSI_NAMESPACE);
        }

        @Test
        @DisplayName("Should have version 4.0 on specificationSet")
        void shouldHaveVersionFourDotZero() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("version=\"4.0\""),
                "Should have version='4.0' attribute on specificationSet");
        }
    }

    @Nested
    @DisplayName("Specification Elements")
    class SpecificationElementsTests {

        @Test
        @DisplayName("Should have name element in specification")
        void shouldHaveNameElementInSpecification() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<name>"),
                "Should contain name element");
            assertTrue(xml.contains("TestWorkflow"),
                "Should contain specification name");
        }

        @Test
        @DisplayName("Should have metaData element in specification")
        void shouldHaveMetaDataElementInSpecification() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            // Accept either <metaData> or self-closing <metaData/>
            boolean hasMetaData = xml.contains("<metaData>") || xml.contains("<metaData/>");
            assertTrue(hasMetaData,
                "Should contain metaData element (either <metaData> or <metaData/>)");
        }

        @Test
        @DisplayName("Should have rootNet or decomposition with isRootNet")
        void shouldHaveRootNetOrDecompositionWithIsRootNet() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            boolean hasRootNet = xml.contains("<rootNet");
            boolean hasIsRootNet = xml.contains("isRootNet=\"true\"");
            assertTrue(hasRootNet || hasIsRootNet,
                "Should have either rootNet element or decomposition with isRootNet='true'");
        }
    }

    @Nested
    @DisplayName("Task Elements")
    class TaskElementsTests {

        @Test
        @DisplayName("Should have task elements")
        void shouldHaveTaskElements() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<task"),
                "Should contain task element");
            assertTrue(xml.contains("</task>"),
                "Should contain task closing tag");
        }

        @Test
        @DisplayName("Should have task with correct id")
        void shouldHaveTaskWithCorrectId() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("id=\"TaskA\""),
                "Should have task with id='TaskA'");
        }

        @Test
        @DisplayName("Should have name elements in tasks")
        void shouldHaveNameElementsInTasks() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            // Task name should be present
            assertTrue(xml.contains("Task A") || xml.contains("<name>"),
                "Should contain task name element or value");
        }

        @Test
        @DisplayName("Should have description in task")
        void shouldHaveDescriptionInTask() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            // YAWL 4.0 schema uses <documentation> for task descriptions
            boolean hasDescription = xml.contains("<description>") || xml.contains("<documentation>");
            assertTrue(hasDescription,
                "Should contain description or documentation element");
            assertTrue(xml.contains("First task"),
                "Should contain task description text");
        }

        @Test
        @DisplayName("Should have flowsInto elements")
        void shouldHaveFlowsIntoElements() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<flowsInto>"),
                "Should contain flowsInto element");
            assertTrue(xml.contains("<nextElementRef"),
                "Should contain nextElementRef within flowsInto");
        }
    }

    @Nested
    @DisplayName("Element Ordering")
    class ElementOrderingTests {

        @Test
        @DisplayName("Should have specificationSet before specification")
        void shouldHaveSpecificationSetBeforeSpecification() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            int specSetIndex = xml.indexOf("<specificationSet");
            // Note: "specificationSet" contains "specification" as substring, so we need
            // to find the specification element which comes INSIDE specificationSet
            int specIndex = xml.indexOf("<specification uri=");
            assertTrue(specSetIndex >= 0, "Should have specificationSet");
            assertTrue(specIndex > specSetIndex,
                "specification element should come after specificationSet opening tag");
        }

        @Test
        @DisplayName("Should have specification before decomposition")
        void shouldHaveSpecificationBeforeDecomposition() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            int specIndex = xml.indexOf("<specification");
            int decompIndex = xml.indexOf("<decomposition");
            assertTrue(specIndex >= 0, "Should have specification");
            // Decomposition may not exist in minimal output
            if (decompIndex >= 0) {
                assertTrue(decompIndex > specIndex,
                    "decomposition should come after specification");
            }
        }

        @Test
        @DisplayName("Should have inputCondition before outputCondition")
        void shouldHaveInputConditionBeforeOutputCondition() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            int inputIndex = xml.indexOf("<inputCondition");
            int outputIndex = xml.indexOf("<outputCondition");
            assertTrue(inputIndex >= 0, "Should have inputCondition");
            assertTrue(outputIndex >= 0, "Should have outputCondition");
            assertTrue(inputIndex < outputIndex,
                "inputCondition should come before outputCondition");
        }

        @Test
        @DisplayName("Should have processControlElements")
        void shouldHaveProcessControlElements() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("<processControlElements>"),
                "Should contain processControlElements element");
            assertTrue(xml.contains("</processControlElements>"),
                "Should contain processControlElements closing tag");
        }
    }

    @Nested
    @DisplayName("XML Well-formedness")
    class XmlWellFormednessTests {

        @Test
        @DisplayName("Should escape special characters in description")
        void shouldEscapeSpecialCharactersInDescription() {
            String yamlWithSpecialChars = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    description: Task with <special> & "characters"
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithSpecialChars);
            assertTrue(xml.contains("&lt;"),
                "Should escape < character");
            assertTrue(xml.contains("&gt;"),
                "Should escape > character");
            assertTrue(xml.contains("&amp;"),
                "Should escape & character");
            assertFalse(xml.contains("<special>"),
                "Should not contain unescaped <special>");
        }

        @Test
        @DisplayName("Should escape quotes in attribute values")
        void shouldEscapeQuotesInAttributeValues() {
            String yamlWithQuotes = """
                name: Test "Workflow"
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithQuotes);
            // Quotes in attributes should be escaped
            assertFalse(xml.contains("uri=\"Test \"Workflow\""),
                "Should not have unescaped quotes in attribute");
        }

        @Test
        @DisplayName("Should produce balanced tags")
        void shouldProduceBalancedTags() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            // Count opening and closing specificationSet tags
            int openCount = countOccurrences(xml, "<specificationSet");
            int closeCount = countOccurrences(xml, "</specificationSet>");
            assertEquals(openCount, closeCount,
                "specificationSet tags should be balanced");

            // Use more specific pattern to avoid substring matching from specificationSet
            openCount = countOccurrences(xml, "<specification uri=");
            closeCount = countOccurrences(xml, "</specification>");
            assertEquals(openCount, closeCount,
                "specification tags should be balanced");
        }
    }

    @Nested
    @DisplayName("Markdown Code Block Handling")
    class MarkdownCodeBlockTests {

        @Test
        @DisplayName("Should strip YAML code block wrapper")
        void shouldStripYamlCodeBlockWrapper() {
            String yamlWithWrapper = """
                ```yaml
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                ```
                """;
            String xml = converter.convertToXml(yamlWithWrapper);
            assertNotNull(xml, "Should parse YAML wrapped in code block");
            assertTrue(xml.contains("TestWorkflow"),
                "Should contain workflow name");
        }

        @Test
        @DisplayName("Should strip generic code block wrapper")
        void shouldStripGenericCodeBlockWrapper() {
            String yamlWithWrapper = """
                ```
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                ```
                """;
            String xml = converter.convertToXml(yamlWithWrapper);
            assertNotNull(xml, "Should parse YAML wrapped in generic code block");
            assertTrue(xml.contains("TestWorkflow"),
                "Should contain workflow name");
        }
    }

    @Nested
    @DisplayName("Extended Features")
    class ExtendedFeaturesTests {

        @Test
        @DisplayName("Should handle variables section")
        void shouldHandleVariablesSection() {
            String yamlWithVariables = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                variables:
                  - name: customerId
                    type: xs:string
                  - name: orderValue
                    type: xs:decimal
                    default: 0.0
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithVariables);
            // YAWL 4.0 schema uses inputParam/localVariable inside decomposition
            boolean hasVariableElements = xml.contains("<inputParam>") ||
                xml.contains("<localVariable>") ||
                xml.contains("<variableDeclarations>");
            assertTrue(hasVariableElements,
                "Should contain variable elements (inputParam, localVariable, or variableDeclarations)");
            assertTrue(xml.contains("customerId"),
                "Should contain variable name 'customerId'");
            assertTrue(xml.contains("<type>"),
                "Should contain type element for variables");
        }

        @Test
        @DisplayName("Should handle multi-instance configuration with XSD-compliant type")
        void shouldHandleMultiInstanceConfiguration() {
            String yamlWithMultiInstance = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 10
                      mode: dynamic
                      threshold: 10
                """;
            String xml = converter.convertToXml(yamlWithMultiInstance);
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                "Should use MultipleInstanceExternalTaskFactsType xsi:type");
            assertTrue(xml.contains("<minimum>"),
                "Should contain minimum element (XSD name)");
            assertTrue(xml.contains("<maximum>"),
                "Should contain maximum element (XSD name)");
            assertTrue(xml.contains("<threshold>"),
                "Should contain threshold element");
            assertTrue(xml.contains("<creationMode"),
                "Should contain creationMode element");
            assertTrue(xml.contains("<miDataInput>"),
                "Should contain miDataInput element (required by XSD)");
        }

        @Test
        @DisplayName("Should handle timer configuration with PascalCase trigger")
        void shouldHandleTimerConfiguration() {
            String yamlWithTimer = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    timer:
                      trigger: onEnabled
                      duration: PT10M
                """;
            String xml = converter.convertToXml(yamlWithTimer);
            assertTrue(xml.contains("<timer>"),
                "Should contain timer element");
            assertTrue(xml.contains("<trigger>OnEnabled</trigger>"),
                "Should normalize trigger to PascalCase 'OnEnabled' per XSD TimerTriggerType");
            assertTrue(xml.contains("<duration>PT10M</duration>"),
                "Should contain duration element with value");
        }

        @Test
        @DisplayName("Should ignore non-schema agent configuration gracefully")
        void shouldIgnoreNonSchemaAgentConfiguration() {
            String yamlWithAgent = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    agent:
                      type: human
                      binding: dynamic
                      capabilities: [order-processing, approval]
                """;
            String xml = converter.convertToXml(yamlWithAgent);
            assertFalse(xml.contains("<agent>"),
                "Should NOT contain non-schema agent element");
            assertFalse(xml.contains("<agentType>"),
                "Should NOT contain non-schema agentType element");
            // Task should still have required schema elements
            assertTrue(xml.contains("<task"),
                "Should still contain task element");
            assertTrue(xml.contains("<join"),
                "Should contain required join element");
            assertTrue(xml.contains("<split"),
                "Should contain required split element");
        }

        @Test
        @DisplayName("Should always emit join and split (required by XSD)")
        void shouldAlwaysEmitJoinAndSplit() {
            String yamlWithSplitJoin = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [TaskB, TaskC]
                    join: xor
                    split: and
                  - id: TaskB
                    flows: [end]
                  - id: TaskC
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithSplitJoin);
            assertTrue(xml.contains("code=\"and\""),
                "Should contain split code='and'");
            assertTrue(xml.contains("code=\"xor\""),
                "Should contain join/split code='xor'");
            // TaskB and TaskC have no explicit join/split, should default to xor
            // Count join elements - should be 3 (one per task)
            int joinCount = countOccurrences(xml, "<join ");
            assertEquals(3, joinCount,
                "All 3 tasks should have join element (required by XSD)");
            int splitCount = countOccurrences(xml, "<split ");
            assertEquals(3, splitCount,
                "All 3 tasks should have split element (required by XSD)");
        }

        @Test
        @DisplayName("Should convert cancellation to removesTokens per XSD")
        void shouldConvertCancellationToRemovesTokens() {
            String yamlWithCancellation = """
                name: TestWorkflow
                uri: test.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    cancels: [TaskB, TaskC]
                  - id: TaskB
                    flows: [end]
                  - id: TaskC
                    flows: [end]
                """;
            String xml = converter.convertToXml(yamlWithCancellation);
            assertTrue(xml.contains("<removesTokens id=\"TaskB\""),
                "Should use XSD-compliant removesTokens for TaskB");
            assertTrue(xml.contains("<removesTokens id=\"TaskC\""),
                "Should use XSD-compliant removesTokens for TaskC");
            assertFalse(xml.contains("<cancellation>"),
                "Should NOT contain non-schema cancellation element");
            assertFalse(xml.contains("<cancelsElement"),
                "Should NOT contain non-schema cancelsElement");
        }
    }

    @Nested
    @DisplayName("Schema Compliance - Flow Mapping")
    class FlowMappingTests {

        @Test
        @DisplayName("Should map 'end' flow to output condition id 'o-top'")
        void shouldMapEndFlowToOutputCondition() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("nextElementRef id=\"o-top\""),
                "Flow to 'end' should be mapped to 'o-top' (output condition id)");
            assertFalse(xml.contains("nextElementRef id=\"end\""),
                "Should NOT have raw 'end' as nextElementRef (not a valid element id)");
        }

        @Test
        @DisplayName("Should have output condition with id o-top")
        void shouldHaveOutputConditionWithOTop() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("outputCondition id=\"o-top\""),
                "Output condition should have id='o-top'");
        }

        @Test
        @DisplayName("Should have input condition with id i-top")
        void shouldHaveInputConditionWithITop() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("inputCondition id=\"i-top\""),
                "Input condition should have id='i-top'");
        }
    }

    @Nested
    @DisplayName("Schema Compliance - Decomposition")
    class DecompositionTests {

        @Test
        @DisplayName("Should have root net decomposition with NetFactsType")
        void shouldHaveRootNetWithNetFactsType() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                "Root net should use NetFactsType");
        }

        @Test
        @DisplayName("Should have task decompositions with WebServiceGatewayFactsType")
        void shouldHaveTaskDecompositionsWithWebServiceGatewayFactsType() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("xsi:type=\"WebServiceGatewayFactsType\""),
                "Task decompositions should use WebServiceGatewayFactsType");
        }

        @Test
        @DisplayName("Should have decomposesTo reference in task")
        void shouldHaveDecomposesToReference() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertTrue(xml.contains("decomposesTo id=\"TaskADecomposition\""),
                "Task should reference its decomposition");
        }
    }

    @Nested
    @DisplayName("Van der Aalst WCP-2: Parallel Split Pattern")
    class ParallelSplitPatternTests {

        @Test
        @DisplayName("Should produce split code='and' for AND-split task")
        void shouldProduceAndSplitCode() {
            String yaml = """
                name: ParallelWorkflow
                uri: parallel.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [BranchA, BranchB]
                  - id: BranchA
                    flows: [end]
                  - id: BranchB
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("code=\"and\""),
                "AND-split task must produce split element with code='and'");
        }

        @Test
        @DisplayName("Should produce multiple flowsInto elements for AND-split")
        void shouldProduceMultipleFlowsIntoForAndSplit() {
            String yaml = """
                name: ParallelWorkflow
                uri: parallel.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [BranchA, BranchB, BranchC]
                  - id: BranchA
                    flows: [end]
                  - id: BranchB
                    flows: [end]
                  - id: BranchC
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // Task Fork should reference BranchA, BranchB, BranchC each in its own flowsInto
            assertTrue(xml.contains("nextElementRef id=\"BranchA\""),
                "Fork should reference BranchA");
            assertTrue(xml.contains("nextElementRef id=\"BranchB\""),
                "Fork should reference BranchB");
            assertTrue(xml.contains("nextElementRef id=\"BranchC\""),
                "Fork should reference BranchC");
            // All four tasks each have at least one flowsInto; Fork has three
            int flowsIntoCount = countOccurrences(xml, "<flowsInto>");
            assertTrue(flowsIntoCount >= 3,
                "Fork task alone should contribute at least 3 flowsInto elements; found " + flowsIntoCount);
        }

        @Test
        @DisplayName("Should not produce non-schema parallel element")
        void shouldNotProduceNonSchemaParallelElement() {
            String yaml = """
                name: ParallelWorkflow
                uri: parallel.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [BranchA, BranchB]
                  - id: BranchA
                    flows: [end]
                  - id: BranchB
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertFalse(xml.contains("<parallel>"),
                "Should NOT contain non-schema <parallel> element");
            assertFalse(xml.contains("<andSplit>"),
                "Should NOT contain non-schema <andSplit> element");
        }
    }

    @Nested
    @DisplayName("Van der Aalst WCP-3: Synchronization Pattern")
    class SynchronizationPatternTests {

        @Test
        @DisplayName("Should produce join code='and' for AND-join task")
        void shouldProduceAndJoinCode() {
            String yaml = """
                name: SyncWorkflow
                uri: sync.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [BranchA, BranchB]
                  - id: BranchA
                    flows: [Sync]
                  - id: BranchB
                    flows: [Sync]
                  - id: Sync
                    join: and
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("code=\"and\""),
                "AND-join task must produce join element with code='and'");
        }

        @Test
        @DisplayName("Should produce both AND-split and AND-join in same workflow")
        void shouldProduceBothAndSplitAndAndJoin() {
            String yaml = """
                name: SyncWorkflow
                uri: sync.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [BranchA, BranchB]
                  - id: BranchA
                    flows: [Merge]
                  - id: BranchB
                    flows: [Merge]
                  - id: Merge
                    join: and
                    split: xor
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // At least two distinct code="and" occurrences: one for split, one for join
            int andCodeCount = countOccurrences(xml, "code=\"and\"");
            assertTrue(andCodeCount >= 2,
                "Both AND-split and AND-join should appear; found code='and' " + andCodeCount + " times");
        }

        @Test
        @DisplayName("Should produce multiple tasks flowing into AND-join target")
        void shouldAllowMultipleTasksFlowingIntoAndJoinTarget() {
            String yaml = """
                name: SyncWorkflow
                uri: sync.xml
                first: Fork
                tasks:
                  - id: Fork
                    split: and
                    flows: [Work1, Work2]
                  - id: Work1
                    flows: [Finish]
                  - id: Work2
                    flows: [Finish]
                  - id: Finish
                    join: and
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // Both Work1 and Work2 should reference Finish in their flowsInto
            assertTrue(xml.contains("nextElementRef id=\"Finish\""),
                "Work tasks should reference Finish (the AND-join target)");
            int finishRefCount = countOccurrences(xml, "nextElementRef id=\"Finish\"");
            assertEquals(2, finishRefCount,
                "Exactly 2 tasks should flow into Finish");
        }
    }

    @Nested
    @DisplayName("Van der Aalst WCP-4: Exclusive Choice Pattern")
    class ExclusiveChoicePatternTests {

        @Test
        @DisplayName("Should produce predicate element for conditional flow")
        void shouldProducePredicateForConditionalFlow() {
            String yaml = """
                name: XorWorkflow
                uri: xor.xml
                first: Decision
                tasks:
                  - id: Decision
                    split: xor
                    flows: [Approved, Rejected]
                    condition: amount > 100 -> Approved
                    default: Rejected
                  - id: Approved
                    flows: [end]
                  - id: Rejected
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<predicate>"),
                "XOR-split with condition should produce <predicate> element");
            // The '>' character in YAML condition is XML-escaped to '&gt;' in the output
            assertTrue(xml.contains("amount") && xml.contains("100"),
                "Predicate should contain the condition expression (amount and 100)");
        }

        @Test
        @DisplayName("Should produce isDefaultFlow element for default flow")
        void shouldProduceIsDefaultFlowElement() {
            String yaml = """
                name: XorWorkflow
                uri: xor.xml
                first: Decision
                tasks:
                  - id: Decision
                    split: xor
                    flows: [Approved, Rejected]
                    condition: amount > 100 -> Approved
                    default: Rejected
                  - id: Approved
                    flows: [end]
                  - id: Rejected
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<isDefaultFlow/>"),
                "XOR-split with default should produce <isDefaultFlow/> element");
        }

        @Test
        @DisplayName("Should produce XOR split code for exclusive choice")
        void shouldProduceXorSplitCode() {
            String yaml = """
                name: XorWorkflow
                uri: xor.xml
                first: Choice
                tasks:
                  - id: Choice
                    split: xor
                    flows: [PathA, PathB]
                    condition: flag -> PathA
                    default: PathB
                  - id: PathA
                    flows: [end]
                  - id: PathB
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("code=\"xor\""),
                "Exclusive choice must use split code='xor'");
        }

        @Test
        @DisplayName("Should not emit predicate on non-conditional flow")
        void shouldNotEmitPredicateOnNonConditionalFlow() {
            String yaml = """
                name: XorWorkflow
                uri: xor.xml
                first: Choice
                tasks:
                  - id: Choice
                    split: xor
                    flows: [Approved, Rejected]
                    condition: score > 50 -> Approved
                    default: Rejected
                  - id: Approved
                    flows: [end]
                  - id: Rejected
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // Predicate appears only once (for Approved), not for Rejected
            int predicateCount = countOccurrences(xml, "<predicate>");
            assertEquals(1, predicateCount,
                "Predicate should appear exactly once (only on the conditional flow)");
        }
    }

    @Nested
    @DisplayName("Van der Aalst WCP-12-15: Multi-Instance Patterns")
    class MultiInstancePatternTests {

        @Test
        @DisplayName("Should emit MultipleInstanceExternalTaskFactsType xsi:type")
        void shouldEmitMultipleInstanceXsiType() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 5
                      threshold: 3
                      mode: dynamic
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                "MI task must use xsi:type='MultipleInstanceExternalTaskFactsType'");
        }

        @Test
        @DisplayName("Should emit minimum element with correct value")
        void shouldEmitMinimumElement() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 2
                      max: 8
                      threshold: 4
                      mode: static
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<minimum>2</minimum>"),
                "Should emit <minimum>2</minimum>");
        }

        @Test
        @DisplayName("Should emit maximum element with correct value")
        void shouldEmitMaximumElement() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 10
                      threshold: 5
                      mode: dynamic
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<maximum>10</maximum>"),
                "Should emit <maximum>10</maximum>");
        }

        @Test
        @DisplayName("Should emit threshold element with correct value")
        void shouldEmitThresholdElement() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 5
                      threshold: 3
                      mode: dynamic
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<threshold>3</threshold>"),
                "Should emit <threshold>3</threshold>");
        }

        @Test
        @DisplayName("Should emit creationMode element")
        void shouldEmitCreationModeElement() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 5
                      threshold: 3
                      mode: dynamic
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<creationMode"),
                "Should emit <creationMode> element");
            assertTrue(xml.contains("code=\"dynamic\""),
                "Creation mode should be code='dynamic'");
        }

        @Test
        @DisplayName("Should emit miDataInput element required by XSD")
        void shouldEmitMiDataInputElement() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 5
                      threshold: 3
                      mode: dynamic
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<miDataInput>"),
                "Should emit required <miDataInput> element");
            assertTrue(xml.contains("</miDataInput>"),
                "miDataInput must have closing tag");
        }

        @Test
        @DisplayName("Should emit MI task with custom variable and query")
        void shouldEmitMiTaskWithCustomVariableAndQuery() {
            String yaml = """
                name: MiWorkflow
                uri: mi.xml
                first: MiTask
                tasks:
                  - id: MiTask
                    flows: [end]
                    multiInstance:
                      min: 1
                      max: 4
                      threshold: 2
                      mode: static
                      variable: orderLine
                      query: /case/orderLines
                      splitQuery: /orderLines/line
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("query=\"/case/orderLines\""),
                "Should use custom query expression from YAML");
            assertTrue(xml.contains("<formalInputParam>orderLine</formalInputParam>"),
                "Should use custom variable name as formalInputParam");
            assertTrue(xml.contains("query=\"/orderLines/line\""),
                "Should use custom splitQuery expression");
        }

        @Test
        @DisplayName("Non-MI task should not have MultipleInstanceExternalTaskFactsType")
        void nonMiTaskShouldNotHaveMiType() {
            String yaml = """
                name: SimpleWorkflow
                uri: simple.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertFalse(xml.contains("MultipleInstanceExternalTaskFactsType"),
                "Regular task must NOT have MultipleInstanceExternalTaskFactsType");
        }
    }

    @Nested
    @DisplayName("Van der Aalst Cancellation Patterns")
    class CancellationPatternTests {

        @Test
        @DisplayName("Should emit removesTokens element for each cancellation target")
        void shouldEmitRemovesTokensForEachCancellationTarget() {
            String yaml = """
                name: CancelWorkflow
                uri: cancel.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    cancels: [TaskB, TaskC]
                  - id: TaskB
                    flows: [end]
                  - id: TaskC
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<removesTokens id=\"TaskB\""),
                "Should emit removesTokens for TaskB");
            assertTrue(xml.contains("<removesTokens id=\"TaskC\""),
                "Should emit removesTokens for TaskC");
        }

        @Test
        @DisplayName("Should not emit non-schema cancellation elements")
        void shouldNotEmitNonSchemaCancellationElements() {
            String yaml = """
                name: CancelWorkflow
                uri: cancel.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    cancels: [TaskB]
                  - id: TaskB
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertFalse(xml.contains("<cancellation>"),
                "Should NOT emit non-schema <cancellation> element");
            assertFalse(xml.contains("<cancels>"),
                "Should NOT emit non-schema <cancels> element");
            assertFalse(xml.contains("<cancelsElement"),
                "Should NOT emit non-schema <cancelsElement> element");
        }

        @Test
        @DisplayName("Should emit multiple removesTokens for multiple cancellation targets")
        void shouldEmitMultipleRemovesTokens() {
            String yaml = """
                name: CancelWorkflow
                uri: cancel.xml
                first: Control
                tasks:
                  - id: Control
                    flows: [end]
                    cancels: [Alpha, Beta, Gamma]
                  - id: Alpha
                    flows: [end]
                  - id: Beta
                    flows: [end]
                  - id: Gamma
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            int removesTokensCount = countOccurrences(xml, "<removesTokens");
            assertEquals(3, removesTokensCount,
                "Should emit exactly 3 removesTokens elements for 3 cancellation targets");
        }

        @Test
        @DisplayName("Task without cancels should not emit removesTokens")
        void taskWithoutCancelsShouldNotEmitRemovesTokens() {
            String yaml = """
                name: SimpleWorkflow
                uri: simple.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertFalse(xml.contains("<removesTokens"),
                "Task with no cancels should not emit any removesTokens element");
        }
    }

    @Nested
    @DisplayName("Timer Pattern Tests")
    class TimerPatternTests {

        @Test
        @DisplayName("Should emit timer element with OnEnabled trigger")
        void shouldEmitTimerWithOnEnabledTrigger() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: OnEnabled
                      duration: PT30M
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<timer>"),
                "Should emit <timer> element");
            assertTrue(xml.contains("<trigger>OnEnabled</trigger>"),
                "Trigger should be OnEnabled");
        }

        @Test
        @DisplayName("Should emit timer element with OnExecuting trigger")
        void shouldEmitTimerWithOnExecutingTrigger() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: OnExecuting
                      duration: PT1H
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<trigger>OnExecuting</trigger>"),
                "Trigger should be OnExecuting");
        }

        @Test
        @DisplayName("Should emit duration element")
        void shouldEmitDurationElement() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: OnEnabled
                      duration: PT15M
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<duration>PT15M</duration>"),
                "Should emit <duration>PT15M</duration>");
        }

        @Test
        @DisplayName("Should emit expiry element when specified instead of duration")
        void shouldEmitExpiryElementWhenSpecified() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: OnEnabled
                      expiry: "2026-12-31T23:59:59"
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<expiry>"),
                "Should emit <expiry> element when expiry is set");
            // The expiry value is preserved as a string from YAML (quoted to prevent date parsing)
            assertFalse(xml.contains("<duration>"),
                "Should emit expiry, not duration, when expiry is configured");
        }

        @Test
        @DisplayName("Should normalize lowercase onenabled trigger to OnEnabled")
        void shouldNormalizeOnenabledTrigger() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: onenabled
                      duration: PT5M
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<trigger>OnEnabled</trigger>"),
                "Lowercase 'onenabled' should be normalized to PascalCase 'OnEnabled'");
            assertFalse(xml.contains("<trigger>onenabled</trigger>"),
                "Lowercase trigger should not appear in output");
        }

        @Test
        @DisplayName("Should normalize on_executing trigger to OnExecuting")
        void shouldNormalizeOnUnderscoreExecutingTrigger() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: on_executing
                      duration: PT2H
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<trigger>OnExecuting</trigger>"),
                "'on_executing' should be normalized to 'OnExecuting'");
        }

        @Test
        @DisplayName("Should use default duration PT5M when no duration or expiry given")
        void shouldUseDefaultDurationWhenNoneSpecified() {
            String yaml = """
                name: TimerWorkflow
                uri: timer.xml
                first: TimedTask
                tasks:
                  - id: TimedTask
                    flows: [end]
                    timer:
                      trigger: OnEnabled
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<duration>PT5M</duration>"),
                "Should default to PT5M when no duration or expiry provided");
        }

        @Test
        @DisplayName("Task without timer should not emit timer element")
        void taskWithoutTimerShouldNotEmitTimerElement() {
            String xml = converter.convertToXml(SIMPLE_YAML);
            assertFalse(xml.contains("<timer>"),
                "Task without timer config should not emit <timer> element");
        }
    }

    @Nested
    @DisplayName("Variable Handling Tests")
    class VariableHandlingTests {

        @Test
        @DisplayName("Should emit inputParam for input variables")
        void shouldEmitInputParamForInputVariables() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: customerId
                    type: xs:string
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<inputParam>"),
                "Input variable should produce <inputParam> element");
            assertTrue(xml.contains("customerId"),
                "Variable name 'customerId' should appear in XML");
        }

        @Test
        @DisplayName("Should emit localVariable for non-input variables")
        void shouldEmitLocalVariableForNonInputVariables() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: internalCounter
                    type: xs:integer
                    input: false
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<localVariable>"),
                "Non-input variable should produce <localVariable> element");
            assertTrue(xml.contains("internalCounter"),
                "Variable name 'internalCounter' should appear in XML");
        }

        @Test
        @DisplayName("Should emit initialValue element for variable with default")
        void shouldEmitInitialValueForVariableWithDefault() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: orderValue
                    type: xs:decimal
                    default: 0.0
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<initialValue>0.0</initialValue>"),
                "Variable with default should emit <initialValue> element");
        }

        @Test
        @DisplayName("Should normalize xs:string type to string (strip namespace prefix)")
        void shouldNormalizeXsStringTypeToString() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: field1
                    type: xs:string
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<type>string</type>"),
                "xs:string should be normalized to 'string' (strip xs: prefix)");
            assertFalse(xml.contains("<type>xs:string</type>"),
                "Should NOT emit raw xs:string with namespace prefix");
        }

        @Test
        @DisplayName("Should emit correct index for multiple variables")
        void shouldEmitCorrectIndexForMultipleVariables() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: field1
                    type: xs:string
                  - name: field2
                    type: xs:integer
                  - name: field3
                    type: xs:boolean
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<index>0</index>"),
                "First variable should have index 0");
            assertTrue(xml.contains("<index>1</index>"),
                "Second variable should have index 1");
            assertTrue(xml.contains("<index>2</index>"),
                "Third variable should have index 2");
        }

        @Test
        @DisplayName("Should include type element for each variable")
        void shouldIncludeTypeElementForEachVariable() {
            String yaml = """
                name: VarWorkflow
                uri: var.xml
                first: TaskA
                variables:
                  - name: amount
                    type: xs:decimal
                tasks:
                  - id: TaskA
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertTrue(xml.contains("<type>decimal</type>"),
                "Variable should have <type>decimal</type> after xs: prefix stripping");
        }
    }

    @Nested
    @DisplayName("Complex Workflow Pattern Tests")
    class ComplexWorkflowPatternTests {

        @Test
        @DisplayName("Should produce valid XML for workflow with all features combined")
        void shouldProduceValidXmlForAllFeaturesCombined() {
            String yaml = """
                name: ComplexWorkflow
                uri: complex.xml
                first: Start

                variables:
                  - name: customerId
                    type: xs:string
                  - name: orderAmount
                    type: xs:decimal
                    default: 0.0
                  - name: statusFlag
                    type: xs:boolean
                    input: false

                tasks:
                  - id: Start
                    name: Start Process
                    description: Initiate order processing
                    split: xor
                    flows: [Validate, Reject]
                    condition: customerId != null -> Validate
                    default: Reject
                    timer:
                      trigger: OnEnabled
                      duration: PT5M

                  - id: Validate
                    name: Validate Order
                    join: xor
                    split: and
                    flows: [ProcessA, ProcessB]

                  - id: ProcessA
                    name: Process Branch A
                    join: xor
                    flows: [Finish]
                    multiInstance:
                      min: 1
                      max: 5
                      threshold: 3
                      mode: dynamic

                  - id: ProcessB
                    name: Process Branch B
                    join: xor
                    flows: [Finish]
                    cancels: [Reject]

                  - id: Finish
                    name: Complete Order
                    join: and
                    flows: [end]

                  - id: Reject
                    name: Reject Order
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Complex workflow should produce non-null XML");
            assertTrue(xml.contains("<specificationSet"),
                "Should have specificationSet root element");
            assertTrue(xml.contains("ComplexWorkflow"),
                "Should contain workflow name");
            assertTrue(xml.contains("<inputParam>"),
                "Should have inputParam for input variables");
            assertTrue(xml.contains("<localVariable>"),
                "Should have localVariable for non-input variable");
            assertTrue(xml.contains("<timer>"),
                "Should have timer element");
            assertTrue(xml.contains("MultipleInstanceExternalTaskFactsType"),
                "Should have MI task type");
            assertTrue(xml.contains("<removesTokens id=\"Reject\""),
                "Should have cancellation via removesTokens");
        }

        @Test
        @DisplayName("Should preserve correct element ordering for complex output")
        void shouldPreserveCorrectElementOrderingForComplexOutput() {
            String yaml = """
                name: OrderedWorkflow
                uri: ordered.xml
                first: Alpha

                variables:
                  - name: param1
                    type: xs:string

                tasks:
                  - id: Alpha
                    flows: [Beta]
                    timer:
                      trigger: OnEnabled
                      duration: PT10M
                    cancels: [Beta]
                  - id: Beta
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            // inputParam should appear before processControlElements
            int inputParamIdx = xml.indexOf("<inputParam>");
            int processCtrlIdx = xml.indexOf("<processControlElements>");
            assertTrue(inputParamIdx >= 0, "Should have <inputParam>");
            assertTrue(processCtrlIdx >= 0, "Should have <processControlElements>");
            assertTrue(inputParamIdx < processCtrlIdx,
                "inputParam must appear before processControlElements (XSD ordering)");

            // flowsInto must appear before join/split in each task
            int flowsIntoIdx = xml.indexOf("<flowsInto>");
            int joinIdx = xml.indexOf("<join ");
            assertTrue(flowsIntoIdx >= 0, "Should have <flowsInto>");
            assertTrue(joinIdx >= 0, "Should have <join>");
            assertTrue(flowsIntoIdx < joinIdx,
                "flowsInto must appear before join per ExternalTaskFactsType XSD sequence");

            // join must appear before split in each task
            int splitIdx = xml.indexOf("<split ");
            assertTrue(splitIdx >= 0, "Should have <split>");
            assertTrue(joinIdx < splitIdx,
                "join must appear before split per ExternalTaskFactsType XSD sequence");

            // removesTokens must appear after join/split but before timer
            int removesTokensIdx = xml.indexOf("<removesTokens");
            int timerIdx = xml.indexOf("<timer>");
            assertTrue(removesTokensIdx >= 0, "Should have <removesTokens>");
            assertTrue(timerIdx >= 0, "Should have <timer>");
            assertTrue(removesTokensIdx > splitIdx,
                "removesTokens must appear after split per XSD sequence");
            assertTrue(removesTokensIdx < timerIdx,
                "removesTokens must appear before timer per XSD sequence");
        }

        @Test
        @DisplayName("Should handle workflow with 5+ tasks without error")
        void shouldHandleWorkflowWithFivePlusTasks() {
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
                    flows: [end]
                """;
            String xml = converter.convertToXml(yaml);
            assertNotNull(xml, "Large workflow should produce output");
            int taskCount = countOccurrences(xml, "<task ");
            assertEquals(6, taskCount,
                "Should produce exactly 6 task elements");
        }

        @Test
        @DisplayName("Should not leak agent or other non-schema elements in complex workflow")
        void shouldNotLeakNonSchemaElementsInComplexWorkflow() {
            String yaml = """
                name: CleanWorkflow
                uri: clean.xml
                first: TaskA
                tasks:
                  - id: TaskA
                    flows: [end]
                    agent:
                      type: human
                    cancels: [TaskA]
                    multiInstance:
                      min: 1
                      max: 3
                      threshold: 2
                      mode: static
                    timer:
                      trigger: OnEnabled
                      duration: PT1M
                """;
            String xml = converter.convertToXml(yaml);
            assertFalse(xml.contains("<agent>"),
                "Should NOT emit non-schema <agent> element");
            assertFalse(xml.contains("<agentType>"),
                "Should NOT emit non-schema <agentType> element");
            assertFalse(xml.contains("<cancels>"),
                "Should NOT emit non-schema <cancels> element");
            // Schema-compliant elements should be present
            assertTrue(xml.contains("<removesTokens"),
                "Cancellation should be via schema-compliant removesTokens");
            assertTrue(xml.contains("<miDataInput>"),
                "MI should produce schema-compliant miDataInput");
            assertTrue(xml.contains("<timer>"),
                "Timer should produce schema-compliant timer element");
        }
    }

    // Helper methods

    /**
     * Count occurrences of a substring in a string.
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
