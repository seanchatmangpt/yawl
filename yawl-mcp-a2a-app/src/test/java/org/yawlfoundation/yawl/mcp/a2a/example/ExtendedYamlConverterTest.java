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
