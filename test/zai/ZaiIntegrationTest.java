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

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive Z.AI integration tests for YAWL workflow engine.
 *
 * <p>Tests verify:
 * <ul>
 *   <li>ZAI service initialization with API key validation</li>
 *   <li>XML generation via ZAI API for workflow specifications</li>
 *   <li>XML validation against YAWL Schema 4.0</li>
 *   <li>Workflow instantiation from AI-generated specifications</li>
 *   <li>Data transformation and validation capabilities</li>
 *   <li>Error handling for API unavailability</li>
 *   <li>Workflow context analysis and decision making</li>
 * </ul>
 *
 * <p>Chicago TDD: tests use real ZAI API calls when available,
 * gracefully degrade when API is unavailable. No mocks in production code.
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 */
@DisplayName("Z.AI Integration Tests")
@Tag("integration")
class ZaiIntegrationTest {

    private static ZaiService zaiService;
    private static boolean apiAvailable;

    // Test workflow specifications for validation
    private static final String SIMPLE_WORKFLOW_SPEC_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <specificationSet xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.citi.qut.edu.au/yawl schema/YAWL_Schema4.0.xsd"
                xmlns="http://www.citi.qut.edu.au/yawl">
            <specification uri="SimpleTestWorkflow.xml">
                <metaData/>
                <rootNet id="top">
                    <inputParam name="name"><type>xs:string</type></inputParam>
                    <inputParam name="amount"><type>xs:decimal</type></inputParam>
                    <processControlElements>
                        <inputCondition id="i-top">
                            <name>i</name>
                            <flowsInto>
                                <nextElementRef id="task1"/>
                            </flowsInto>
                        </inputCondition>
                        <task id="task1">
                            <name>Process Request</name>
                            <flowsInto>
                                <nextElementRef id="o-top"/>
                            </flowsInto>
                            <join code="xor"/>
                            <split code="and"/>
                        </task>
                        <outputCondition id="o-top">
                            <name>o</name>
                        </outputCondition>
                    </processControlElements>
                </rootNet>
            </specification>
        </specificationSet>
        """;

    @BeforeAll
    static void setUp() {
        // Check if ZAI API key is available
        String apiKey = System.getenv("ZAI_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isEmpty(),
                  "ZAI_API_KEY environment variable not set - skipping API tests");

        try {
            zaiService = new ZaiService(apiKey);
            apiAvailable = zaiService.verifyConnection();
            assumeTrue(apiAvailable, "ZAI API connection test failed");

            // Set system prompt for all tests
            zaiService.setSystemPrompt("You are an intelligent assistant for generating YAWL workflow specifications.");
        } catch (Exception e) {
            apiAvailable = false;
            System.err.println("ZAI service initialization failed: " + e.getMessage());
        }
    }

    @AfterAll
    static void tearDown() {
        if (zaiService != null) {
            zaiService.clearHistory();
        }
    }

    @Nested
    @DisplayName("Service Initialization Tests")
    class ServiceInitializationTests {

        @Test
        @DisplayName("Service should be properly initialized with API key")
        void testServiceInitialization() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            assertNotNull(zaiService, "ZAI service should be initialized");
            assertTrue(zaiService.isInitialized(), "Service should be marked as initialized");
            assertTrue(zaiService.verifyConnection(), "Connection should be verified");
        }

        @Test
        @DisplayName("Service should handle missing API key gracefully")
        void testMissingApiKey() {
            // This should throw IllegalStateException when no API key is provided
            assertThrows(IllegalStateException.class,
                        () -> new ZaiService(""),
                        "Should throw exception for empty API key");
        }

        @Test
        @DisplayName("Available models should be non-empty list")
        void testAvailableModels() {
            List<String> models = ZaiService.getAvailableModels();

            assertFalse(models.isEmpty(), "Should have available models");
            assertTrue(models.contains("GLM-4.7-Flash"), "Should include default model");
            assertTrue(models.contains("glm-4.6"), "Should include glm-4.6 model");
        }
    }

    @Nested
    @DisplayName("XML Generation Tests")
    class XmlGenerationTests {

        @Test
        @DisplayName("Generate simple workflow specification XML")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testGenerateWorkflowSpecXml() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String prompt = """
                Generate a simple YAWL workflow specification XML with:
                1. One input task named "Review Request"
                2. One output condition
                3. Basic data inputs: customerID (string) and amount (decimal)
                Follow YAWL Schema 4.0 format.
                """;

            String generatedXml = zaiService.chat(prompt);

            assertNotNull(generatedXml, "Generated XML should not be null");
            assertFalse(generatedXml.trim().isEmpty(), "Generated XML should not be empty");

            // Validate the generated XML contains expected elements
            assertTrue(generatedXml.contains("<specification"), "Should be a valid YAWL specification");
            assertTrue(generatedXml.contains("<rootNet"), "Should contain root net");
            assertTrue(generatedXml.contains("<task"), "Should contain at least one task");
        }

        @Test
        @DisplayName("Generate complex workflow specification with branches")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testGenerateComplexWorkflowSpecXml() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String prompt = """
                Generate a YAWL workflow with conditional branching:
                - Input: orderValue (decimal)
                - Task "Evaluate Order" that splits to two branches:
                  * Branch 1: "Process Large Order" (if orderValue > 1000)
                  * Branch 2: "Process Small Order" (if orderValue <= 1000)
                - Both branches join at "Complete Order" task
                - Include proper join/split codes
                """;

            String generatedXml = zaiService.chat(prompt);

            // Validate structure
            assertTrue(generatedXml.contains("<split code=\"xor\""), "Should have XOR split");
            assertTrue(generatedXml.contains("<join code=\"and\""), "Should have AND join");
            assertTrue(generatedXml.contains("> 1000 <"), "Should contain condition check");
        }
    }

    @Nested
    @DisplayName("XML Validation Tests")
    class XmlValidationTests {

        @Test
        @DisplayName("Validate against YAWL Schema 4.0")
        void testXmlSchemaValidation() throws Exception {
            // Test with valid known specification
            assertTrue(validateAgainstSchema(SIMPLE_WORKFLOW_SPEC_XML),
                      "Valid XML should pass schema validation");

            // Test with AI-generated XML when API is available
            if (apiAvailable) {
                String generatedXml = zaiService.chat("Generate a simple valid YAWL workflow");
                boolean isValid = validateAgainstSchema(generatedXml);
                assertTrue(isValid, "AI-generated XML should be schema-valid");
            }
        }

        @Test
        @DisplayName("Reject invalid XML structure")
        void testInvalidXmlRejection() {
            String invalidXml = """
                <?xml version="1.0"?>
                <invalidRoot>
                    <thisIsNotYAWL>structure</thisIsNotYAWL>
                </invalidRoot>
                """;

            assertThrows(Exception.class,
                        () -> validateAgainstSchema(invalidXml),
                        "Invalid XML should throw validation exception");
        }

        @Test
        @DisplayName("Validate workflow instantiation from XML")
        void testWorkflowInstantiation() throws Exception {
            // Parse valid XML to YSpecification
            YSpecification spec = YMarshal.unmarshalSpecifications(SIMPLE_WORKFLOW_SPEC_XML).get(0);

            assertNotNull(spec, "Should parse to valid YSpecification");
            assertNotNull(spec.getRootNet(), "Specification should have root net");
            assertNotNull(spec.getID(), "Specification should have ID");

            // Test AI-generated XML when available
            if (apiAvailable) {
                String generatedXml = zaiService.chat("Generate a valid YAWL workflow with input and output");
                YSpecification generatedSpec = YMarshal.unmarshalSpecifications(generatedXml).get(0);

                assertNotNull(generatedSpec, "AI-generated XML should parse to YSpecification");
                assertNotNull(generatedSpec.getRootNet(), "Generated spec should have root net");
            }
        }

        private boolean validateAgainstSchema(String xml) throws Exception {
            try {
                SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = factory.newSchema(
                    new StreamSource(getClass().getResourceAsStream("/schema/YAWL_Schema4.0.xsd"))
                );

                Validator validator = schema.newValidator();
                validator.validate(new StreamSource(new StringReader(xml)));
                return true;
            } catch (Exception e) {
                System.err.println("XML validation failed: " + e.getMessage());
                return false;
            }
        }
    }

    @Nested
    @DisplayName("Workflow Context Tests")
    class WorkflowContextTests {

        @Test
        @DisplayName("Analyze workflow context and suggest action")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testWorkflowContextAnalysis() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String analysis = zaiService.analyzeWorkflowContext(
                "CASE-123",
                "Review Document",
                "{\"documentType\": \"Contract\", \"value\": 50000, \"department\": \"Legal\"}"
            );

            assertNotNull(analysis, "Should return analysis");
            assertFalse(analysis.trim().isEmpty(), "Analysis should not be empty");
        }

        @Test
        @DisplayName("Make workflow decision with multiple options")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testWorkflowDecision() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            List<String> options = List.of("Approve", "Reject", "Escalate");
            String decision = zaiService.makeWorkflowDecision(
                "Approval Level",
                "{\"amount\": 2500, \"requester\": \"john.doe@company.com\", \"priority\": \"medium\"}",
                options
            );

            assertNotNull(decision, "Should return decision");
            assertFalse(decision.trim().isEmpty(), "Decision should not be empty");
            assertTrue(decision.contains("CHOICE:"), "Should contain choice identifier");
            assertTrue(decision.contains("REASONING:"), "Should contain reasoning");
        }
    }

    @Nested
    @DisplayName("Data Processing Tests")
    class DataProcessingTests {

        @Test
        @DisplayName("Transform data using AI")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testDataTransformation() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String input = "John Doe, 123 Main St, Anytown, CA 90210, (555) 123-4567";
            String rule = "Convert to JSON with fields: firstName, lastName, street, city, state, zip, phone";

            String result = zaiService.transformData(input, rule);

            assertNotNull(result, "Should return transformed data");
            assertFalse(result.trim().isEmpty(), "Transformed data should not be empty");
            assertTrue(result.contains("{"), "Should be valid JSON format");
            assertTrue(result.contains("firstName"), "Should contain expected fields");
        }

        @Test
        @DisplayName("Extract structured information")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testInformationExtraction() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String text = "Customer order #ORD-456 for 2 widgets to be shipped to 123 Oak Street.";
            String fields = "orderNumber, product, quantity, address";

            String result = zaiService.extractInformation(text, fields);

            assertNotNull(result, "Should return extracted information");
            assertTrue(result.contains("{"), "Should be JSON format");
            assertTrue(result.contains("orderNumber"), "Should contain extracted fields");
        }

        @Test
        @DisplayName("Validate workflow data")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testDataValidation() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String data = "{\"age\": 25, \"email\": \"user@example.com\"}";
            String rules = "age must be between 18 and 65, email must contain @ symbol";

            String result = zaiService.validateData(data, rules);

            assertNotNull(result, "Should return validation result");
            assertTrue(result.contains("VALID") || result.contains("issue"),
                       "Should indicate valid or issues");
        }
    }

    @Nested
    @DisplayName("Documentation Tests")
    class DocumentationTests {

        @Test
        @DisplayName("Generate workflow documentation")
        @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
        void testDocumentationGeneration() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            String doc = zaiService.generateDocumentation(SIMPLE_WORKFLOW_SPEC_XML);

            assertNotNull(doc, "Should generate documentation");
            assertFalse(doc.trim().isEmpty(), "Documentation should not be empty");
            assertTrue(doc.contains("Overview") || doc.contains("Process") || doc.contains("Input"),
                       "Should include documentation sections");
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Handle API timeouts gracefully")
        void testTimeoutHandling() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            // Set very short timeout
            zaiService.getHttpClient().setConnectTimeout(100);
            zaiService.getHttpClient().setReadTimeout(100);

            // This should either complete quickly or fail gracefully
            assertDoesNotThrow(() -> {
                String result = zaiService.chat("Hello");
                // Either successful response or exception, but no infinite hang
            }, "Should not hang indefinitely");
        }

        @Test
        @DisplayName("Handle invalid model gracefully")
        void testInvalidModelHandling() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            assertThrows(RuntimeException.class,
                        () -> zaiService.chat("test", "invalid-model-name"),
                        "Should throw exception for invalid model");
        }

        @Test
        @DisplayName("Test service maintains state across requests")
        void testStateMaintenance() {
            assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

            // Clear history
            zaiService.clearHistory();
            assertEquals(0, zaiService.getConversationHistory().size(),
                        "History should be empty after clear");

            // Make a request
            String response1 = zaiService.chat("What is the capital of France?");
            assertNotNull(response1);

            // Make another request - should maintain context
            String response2 = zaiService.chat("What about the capital of Spain?");
            assertNotNull(response2);

            // History should contain both requests
            List<Map<String, String>> history = zaiService.getConversationHistory();
            assertTrue(history.size() >= 2, "Should maintain conversation history");
        }
    }

    @Test
    @DisplayName("Integration test: End-to-end workflow generation and execution")
    @EnabledIfEnvironmentVariable(named = "ZAI_API_KEY", matches = ".+")
    void testEndToEndWorkflow() throws Exception {
        assumeTrue(apiAvailable, "Skipping test - ZAI API not available");

        // 1. Generate workflow specification
        String prompt = """
            Generate a YAWL workflow for invoice processing:
            - Input: invoiceAmount (decimal), vendor (string)
            - Task "Validate Invoice" - splits to:
              * "Process Large Invoice" (if > 1000)
              * "Process Small Invoice" (if <= 1000)
            - Both lead to "Approve Invoice" task
            - Output: approvalStatus (string)
            """;

        String generatedXml = zaiService.chat(prompt);
        assertNotNull(generatedXml, "Should generate workflow XML");

        // 2. Validate XML
        assertTrue(validateAgainstSchema(generatedXml),
                   "Generated XML should be schema-valid");

        // 3. Parse to YSpecification
        YSpecification spec = YMarshal.unmarshalSpecifications(generatedXml).get(0);
        assertNotNull(spec, "Should parse to YSpecification");

        // 4. Test workflow decision
        List<String> options = List.of("Approve", "Reject", "Escalate");
        String decision = zaiService.makeWorkflowDecision(
            "Invoice Approval",
            "{\"amount\": 1500, \"vendor\": \"Office Supplies Inc.\", \"priority\": \"normal\"}",
            options
        );
        assertNotNull(decision, "Should generate decision");
    }
}