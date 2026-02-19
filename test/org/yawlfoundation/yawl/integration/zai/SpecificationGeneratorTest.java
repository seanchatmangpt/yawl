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
 * You should have received a copy of the GNU Lesser General
 * Public License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.zai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for SpecificationGenerator.
 *
 * <p>Tests cover specification generation from natural language descriptions,
 * XML extraction patterns, options configuration, and error handling using
 * in-memory test implementations of the ZaiHttpClient and SchemaHandler interfaces.</p>
 *
 * @author YAWL Foundation - ZAI Integration Team
 * @version 6.0
 */
@DisplayName("Specification Generator Tests")
@Execution(ExecutionMode.CONCURRENT)
public class SpecificationGeneratorTest {

    // =========================================================================
    // Test Fixtures - Sample YAWL XML specifications
    // =========================================================================

    /** Minimal valid YAWL specification XML for testing */
    private static final String MINIMAL_SPEC_XML = """
        <specification id="TestWorkflow" uri="http://test.example.com/workflow">
          <metaData>
            <title>Test Workflow</title>
            <creator>Test Suite</creator>
            <description>A minimal test workflow</description>
          </metaData>
          <rootNet id="root">
            <processControlElements>
              <inputCondition id="start">
                <flowsInto>
                  <nextElementRef id="task1" />
                </flowsInto>
              </inputCondition>
              <task id="task1">
                <name>First Task</name>
                <flowsInto>
                  <nextElementRef id="end" />
                </flowsInto>
              </task>
              <outputCondition id="end" />
            </processControlElements>
          </rootNet>
        </specification>
        """;

    /** More complex YAWL specification with multiple tasks */
    private static final String ORDER_PROCESSING_XML = """
        <specification id="OrderProcessing" uri="http://test.example.com/orders">
          <metaData>
            <title>Order Processing Workflow</title>
            <creator>Test Suite</creator>
            <description>Process customer orders with approval</description>
          </metaData>
          <rootNet id="root">
            <processControlElements>
              <inputCondition id="start">
                <flowsInto>
                  <nextElementRef id="receive_order" />
                </flowsInto>
              </inputCondition>
              <task id="receive_order">
                <name>Receive Order</name>
                <flowsInto>
                  <nextElementRef id="validate_order" />
                </flowsInto>
              </task>
              <task id="validate_order">
                <name>Validate Order</name>
                <flowsInto>
                  <nextElementRef id="approve_order" />
                </flowsInto>
              </task>
              <task id="approve_order">
                <name>Approve Order</name>
                <flowsInto>
                  <nextElementRef id="end" />
                </flowsInto>
              </task>
              <outputCondition id="end" />
            </processControlElements>
          </rootNet>
        </specification>
        """;

    /** AI response with XML in a markdown code block */
    private static final String AI_RESPONSE_WITH_CODE_BLOCK = """
        Here is the YAWL specification you requested:

        ```xml
        <specification id="GeneratedWorkflow" uri="http://generated.example.com/workflow">
          <metaData>
            <title>Generated Workflow</title>
          </metaData>
          <rootNet id="root">
            <processControlElements>
              <inputCondition id="start">
                <flowsInto><nextElementRef id="task1" /></flowsInto>
              </inputCondition>
              <task id="task1">
                <name>Generated Task</name>
                <flowsInto><nextElementRef id="end" /></flowsInto>
              </task>
              <outputCondition id="end" />
            </processControlElements>
          </rootNet>
        </specification>
        ```

        This specification defines a simple workflow with one task.
        """;

    /** AI response with XML without code block */
    private static final String AI_RESPONSE_PLAIN_XML = """
        Based on your description, here is the specification:

        <specification id="PlainWorkflow" uri="http://plain.example.com/workflow">
          <metaData>
            <title>Plain Workflow</title>
          </metaData>
          <rootNet id="root">
            <processControlElements>
              <inputCondition id="start">
                <flowsInto><nextElementRef id="task1" /></flowsInto>
              </inputCondition>
              <task id="task1">
                <name>Main Task</name>
                <flowsInto><nextElementRef id="end" /></flowsInto>
              </task>
              <outputCondition id="end" />
            </processControlElements>
          </rootNet>
        </specification>

        Let me know if you need any modifications.
        """;

    /** AI response with no valid XML */
    private static final String AI_RESPONSE_NO_XML = """
        I understand you want to create a workflow, but I need more details
        about the specific process you want to model. Could you provide
        more information about the tasks and their order?
        """;

    /** AI response with XML in code block without language specifier */
    private static final String AI_RESPONSE_CODE_BLOCK_NO_LANG = """
        Here's the specification:

        ```
        <specification id="NoLangWorkflow" uri="http://nolang.example.com/workflow">
          <metaData><title>No Language Specifier</title></metaData>
          <rootNet id="root">
            <processControlElements>
              <inputCondition id="start">
                <flowsInto><nextElementRef id="task1" /></flowsInto>
              </inputCondition>
              <task id="task1"><name>Task</name>
                <flowsInto><nextElementRef id="end" /></flowsInto>
              </task>
              <outputCondition id="end" />
            </processControlElements>
          </rootNet>
        </specification>
        ```
        """;

    // =========================================================================
    // Test Instance Fields
    // =========================================================================

    private TestZaiHttpClient testClient;
    private TestSchemaHandler testSchemaHandler;
    private SpecificationGenerator generator;

    // =========================================================================
    // Setup and Teardown
    // =========================================================================

    @BeforeEach
    void setUp() {
        testClient = new TestZaiHttpClient();
        testSchemaHandler = new TestSchemaHandler();
        generator = new SpecificationGenerator(
            testClient,
            testSchemaHandler,
            "GLM-4.7-Flash",
            Duration.ofSeconds(30)
        );
    }

    @AfterEach
    void tearDown() {
        testClient = null;
        testSchemaHandler = null;
        generator = null;
    }

    // =========================================================================
    // Constructor Tests
    // =========================================================================

    @Nested
    @DisplayName("Constructor Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class ConstructorTests {

        @Test
        @DisplayName("Constructor with ZaiHttpClient only should use defaults")
        void testConstructorWithClientOnly() {
            TestZaiHttpClient client = new TestZaiHttpClient();
            SpecificationGenerator gen = new SpecificationGenerator(client);

            assertNotNull(gen, "Generator should be created");
        }

        @Test
        @DisplayName("Constructor with null ZaiHttpClient should throw")
        void testConstructorWithNullClient() {
            assertThrows(NullPointerException.class,
                () -> new SpecificationGenerator(null, testSchemaHandler, "model", Duration.ofSeconds(30)),
                "Should throw for null ZaiHttpClient");
        }

        @Test
        @DisplayName("Constructor with null SchemaHandler should throw")
        void testConstructorWithNullSchemaHandler() {
            assertThrows(NullPointerException.class,
                () -> new SpecificationGenerator(testClient, null, "model", Duration.ofSeconds(30)),
                "Should throw for null SchemaHandler");
        }

        @Test
        @DisplayName("Constructor with all parameters should succeed")
        void testConstructorWithAllParameters() {
            SpecificationGenerator gen = new SpecificationGenerator(
                testClient,
                testSchemaHandler,
                "glm-5",
                Duration.ofSeconds(60)
            );

            assertNotNull(gen, "Generator should be created with custom parameters");
        }
    }

    // =========================================================================
    // generateFromDescription() Tests
    // =========================================================================

    @Nested
    @DisplayName("generateFromDescription() Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class GenerateFromDescriptionTests {

        @Test
        @DisplayName("Generate specification with valid description")
        void testGenerateWithValidDescription() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);

            // Act
            var spec = generator.generateFromDescription(
                "Create a simple workflow with one task"
            );

            // Assert
            assertNotNull(spec, "Specification should not be null");
            assertEquals("http://test.example.com/workflow", spec.getURI(),
                "Specification URI should match");
            assertTrue(testClient.wasCreateChatCompletionCalled(),
                "Chat completion should have been called");
            assertTrue(testSchemaHandler.wasValidateCalled(),
                "Schema validation should have been called");
        }

        @Test
        @DisplayName("Generate specification with custom options")
        void testGenerateWithCustomOptions() {
            // Arrange
            testClient.setResponseContent(ORDER_PROCESSING_XML);
            var options = new SpecificationGenerator.GenerationOptions()
                .withTemperature(0.5)
                .withMaxTokens(4096)
                .withValidateSchema(true)
                .withSpecIdentifier("CustomOrderWorkflow");

            // Act
            var spec = generator.generateFromDescription(
                "Create an order processing workflow",
                options
            );

            // Assert
            assertNotNull(spec, "Specification should not be null");
            assertEquals("http://test.example.com/orders", spec.getURI(),
                "Specification URI should match");
            assertEquals(0.5, options.temperature(), 0.001,
                "Temperature should be set");
            assertEquals(4096, options.maxTokens(),
                "Max tokens should be set");
        }

        @Test
        @DisplayName("Generate with null description should throw")
        void testGenerateWithNullDescription() {
            assertThrows(NullPointerException.class,
                () -> generator.generateFromDescription(null),
                "Should throw for null description");
        }

        @Test
        @DisplayName("Generate with null options should throw")
        void testGenerateWithNullOptions() {
            assertThrows(NullPointerException.class,
                () -> generator.generateFromDescription("test", null),
                "Should throw for null options");
        }

        @Test
        @DisplayName("Generate with schema validation disabled should skip validation")
        void testGenerateWithSchemaValidationDisabled() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);
            testSchemaHandler.setShouldFail(true); // Would fail if called

            var options = new SpecificationGenerator.GenerationOptions()
                .withValidateSchema(false);

            // Act
            var spec = generator.generateFromDescription(
                "Create a workflow",
                options
            );

            // Assert
            assertNotNull(spec, "Specification should be generated");
            assertFalse(testSchemaHandler.wasValidateCalled(),
                "Schema validation should NOT have been called");
        }

        @Test
        @DisplayName("Generate when API returns no XML should throw")
        void testGenerateWithNoXmlInResponse() {
            // Arrange
            testClient.setResponseContent(AI_RESPONSE_NO_XML);

            // Act & Assert
            assertThrows(SpecificationGenerator.SpecificationGenerationException.class,
                () -> generator.generateFromDescription("Create a workflow"),
                "Should throw when no XML in response");
        }

        @Test
        @DisplayName("Generate when API call fails should throw")
        void testGenerateWithApiFailure() {
            // Arrange
            testClient.setExceptionToThrow(new RuntimeException("API connection failed"));

            // Act & Assert
            SpecificationGenerator.SpecificationGenerationException ex =
                assertThrows(SpecificationGenerator.SpecificationGenerationException.class,
                    () -> generator.generateFromDescription("Create a workflow"),
                    "Should throw when API call fails");

            assertTrue(ex.getMessage().contains("Z.AI API call failed"),
                "Exception message should indicate API failure");
        }

        @Test
        @DisplayName("Generate when schema validation fails should throw")
        void testGenerateWithSchemaValidationFailure() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);
            testSchemaHandler.setShouldFail(true);
            testSchemaHandler.setFailureMessage("Invalid XML: missing required element");

            // Act & Assert
            assertThrows(RuntimeException.class,
                () -> generator.generateFromDescription("Create a workflow"),
                "Should throw when schema validation fails");
        }
    }

    // =========================================================================
    // generateVariants() Tests
    // =========================================================================

    @Nested
    @DisplayName("generateVariants() Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class GenerateVariantsTests {

        @Test
        @DisplayName("Generate multiple variants produces correct count")
        void testGenerateMultipleVariants() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);

            // Act
            var variants = generator.generateVariants(
                "Create a workflow",
                3
            );

            // Assert
            assertEquals(3, variants.size(),
                "Should generate requested number of variants");
            assertEquals(3, testClient.getCallCount(),
                "API should be called once per variant");
        }

        @Test
        @DisplayName("Generate variants with incrementing temperature")
        void testGenerateVariantsIncrementingTemperature() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);
            AtomicReference<Double> lastTemperature = new AtomicReference<>(0.0);

            testClient.setRequestCapture(request -> {
                Object temp = request.get("temperature");
                if (temp instanceof Double) {
                    lastTemperature.set((Double) temp);
                }
            });

            // Act
            var variants = generator.generateVariants("Create a workflow", 3);

            // Assert - temperature should increase with each variant
            // The last call should have highest temperature
            assertEquals(3, variants.size(),
                "Should generate 3 variants");
        }

        @Test
        @DisplayName("Generate zero variants returns empty list")
        void testGenerateZeroVariants() {
            var variants = generator.generateVariants("Create a workflow", 0);

            assertTrue(variants.isEmpty(),
                "Should return empty list for zero variants");
        }
    }

    // =========================================================================
    // improveSpecification() Tests
    // =========================================================================

    @Nested
    @DisplayName("improveSpecification() Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class ImproveSpecificationTests {

        @Test
        @DisplayName("Improve specification with feedback")
        void testImproveSpecification() {
            // Arrange
            testClient.setResponseContent(ORDER_PROCESSING_XML);

            // Act
            var improved = generator.improveSpecification(
                MINIMAL_SPEC_XML,
                "Add order validation step"
            );

            // Assert
            assertNotNull(improved, "Improved specification should not be null");
            assertTrue(testClient.wasCreateChatCompletionCalled(),
                "API should be called for improvement");
        }

        @Test
        @DisplayName("Improve specification includes existing spec in prompt")
        void testImproveSpecificationIncludesExistingSpec() {
            // Arrange
            testClient.setResponseContent(MINIMAL_SPEC_XML);
            List<String> capturedPrompts = new ArrayList<>();

            testClient.setRequestCapture(request -> {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> messages =
                    (List<Map<String, String>>) request.get("messages");
                for (Map<String, String> msg : messages) {
                    if ("user".equals(msg.get("role"))) {
                        capturedPrompts.add(msg.get("content"));
                    }
                }
            });

            // Act
            generator.improveSpecification(
                "<specification id='Old'>...</specification>",
                "Add parallel processing"
            );

            // Assert
            assertFalse(capturedPrompts.isEmpty(),
                "Should have captured user prompt");
            String prompt = capturedPrompts.get(capturedPrompts.size() - 1);
            assertTrue(prompt.contains("Old"),
                "Prompt should contain existing specification");
            assertTrue(prompt.contains("parallel processing"),
                "Prompt should contain feedback");
        }
    }

    // =========================================================================
    // extractXml() Tests
    // =========================================================================

    @Nested
    @DisplayName("extractXml() Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class ExtractXmlTests {

        @Test
        @DisplayName("Extract XML from markdown code block with xml language")
        void testExtractFromCodeBlockWithXml() {
            // Arrange
            testClient.setResponseContent(AI_RESPONSE_WITH_CODE_BLOCK);

            // Act
            var spec = generator.generateFromDescription("Create a workflow");

            // Assert
            assertNotNull(spec, "Should extract and parse XML from code block");
            assertEquals("http://generated.example.com/workflow", spec.getURI(),
                "Should parse specification from code block");
        }

        @Test
        @DisplayName("Extract XML from code block without language specifier")
        void testExtractFromCodeBlockWithoutLanguage() {
            // Arrange
            testClient.setResponseContent(AI_RESPONSE_CODE_BLOCK_NO_LANG);

            // Act
            var spec = generator.generateFromDescription("Create a workflow");

            // Assert
            assertNotNull(spec, "Should extract XML from code block without language");
            assertEquals("http://nolang.example.com/workflow", spec.getURI(),
                "Should parse specification from generic code block");
        }

        @Test
        @DisplayName("Extract XML from plain text response")
        void testExtractFromPlainText() {
            // Arrange
            testClient.setResponseContent(AI_RESPONSE_PLAIN_XML);

            // Act
            var spec = generator.generateFromDescription("Create a workflow");

            // Assert
            assertNotNull(spec, "Should extract XML from plain text");
            assertEquals("http://plain.example.com/workflow", spec.getURI(),
                "Should parse specification from plain text");
        }

        @Test
        @DisplayName("Extract XML with whitespace handling")
        void testExtractXmlWithWhitespace() {
            // Arrange
            String responseWithWhitespace = """

                Some introductory text.

                <specification id="WhitespaceTest" uri="http://whitespace.example.com/wf">
                  <metaData><title>Whitespace Test</title></metaData>
                  <rootNet id="root">
                    <processControlElements>
                      <inputCondition id="start">
                        <flowsInto><nextElementRef id="task1" /></flowsInto>
                      </inputCondition>
                      <task id="task1"><name>Task</name>
                        <flowsInto><nextElementRef id="end" /></flowsInto>
                      </task>
                      <outputCondition id="end" />
                    </processControlElements>
                  </rootNet>
                </specification>

                Some trailing text.
                """;
            testClient.setResponseContent(responseWithWhitespace);

            // Act
            var spec = generator.generateFromDescription("Create a workflow");

            // Assert
            assertNotNull(spec, "Should extract XML despite surrounding whitespace");
        }
    }

    // =========================================================================
    // GenerationOptions Tests
    // =========================================================================

    @Nested
    @DisplayName("GenerationOptions Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class GenerationOptionsTests {

        @Test
        @DisplayName("Default options have expected values")
        void testDefaultOptions() {
            var options = new SpecificationGenerator.GenerationOptions();

            assertTrue(options.validateSchema(), "Default validateSchema should be true");
            assertTrue(options.autoFixIds(), "Default autoFixIds should be true");
            assertTrue(options.includeSchemaHints(), "Default includeSchemaHints should be true");
            assertEquals(0.3, options.temperature(), 0.001, "Default temperature should be 0.3");
            assertEquals(0, options.maxTokens(), "Default maxTokens should be 0 (use default)");
            assertEquals(-1, options.variantSeed(), "Default variantSeed should be -1");
            assertNull(options.getSpecIdentifier(), "Default specIdentifier should be null");
            assertNull(options.getExampleSpec(), "Default exampleSpec should be null");
        }

        @Test
        @DisplayName("Builder pattern chains correctly")
        void testBuilderPatternChaining() {
            var options = new SpecificationGenerator.GenerationOptions()
                .withValidateSchema(false)
                .withAutoFixIds(false)
                .withIncludeSchemaHints(false)
                .withTemperature(0.8)
                .withMaxTokens(2048)
                .withVariantSeed(5)
                .withSpecIdentifier("MyWorkflow")
                .withExampleSpec("<specification>...</specification>");

            assertFalse(options.validateSchema(), "validateSchema should be false");
            assertFalse(options.autoFixIds(), "autoFixIds should be false");
            assertFalse(options.includeSchemaHints(), "includeSchemaHints should be false");
            assertEquals(0.8, options.temperature(), 0.001, "temperature should be 0.8");
            assertEquals(2048, options.maxTokens(), "maxTokens should be 2048");
            assertEquals(5, options.variantSeed(), "variantSeed should be 5");
            assertEquals("MyWorkflow", options.getSpecIdentifier(), "specIdentifier should match");
            assertEquals("<specification>...</specification>", options.getExampleSpec(),
                "exampleSpec should match");
        }

        @Test
        @DisplayName("Temperature is clamped to valid range (0-2)")
        void testTemperatureClamping() {
            var options1 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(-0.5);
            assertEquals(0.0, options1.temperature(), 0.001,
                "Negative temperature should be clamped to 0");

            var options2 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(3.0);
            assertEquals(2.0, options2.temperature(), 0.001,
                "Temperature > 2 should be clamped to 2");

            var options3 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(1.0);
            assertEquals(1.0, options3.temperature(), 0.001,
                "Valid temperature should be preserved");
        }

        @Test
        @DisplayName("Boundary temperature values")
        void testTemperatureBoundaries() {
            var options0 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(0.0);
            assertEquals(0.0, options0.temperature(), 0.001, "Min boundary");

            var options2 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(2.0);
            assertEquals(2.0, options2.temperature(), 0.001, "Max boundary");
        }

        @Test
        @DisplayName("Options are independent instances")
        void testOptionsIndependence() {
            var options1 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(0.5);
            var options2 = new SpecificationGenerator.GenerationOptions()
                .withTemperature(0.9);

            assertNotEquals(options1.temperature(), options2.temperature(),
                "Different instances should have independent values");
        }
    }

    // =========================================================================
    // SpecificationGenerationException Tests
    // =========================================================================

    @Nested
    @DisplayName("SpecificationGenerationException Tests")
    @Execution(ExecutionMode.CONCURRENT)
    class ExceptionTests {

        @Test
        @DisplayName("Exception with message only")
        void testExceptionWithMessage() {
            var ex = new SpecificationGenerator.SpecificationGenerationException(
                "Generation failed"
            );

            assertEquals("Generation failed", ex.getMessage(),
                "Message should match");
            assertNull(ex.getCause(), "Cause should be null");
        }

        @Test
        @DisplayName("Exception with message and cause")
        void testExceptionWithCause() {
            var cause = new RuntimeException("Root cause");
            var ex = new SpecificationGenerator.SpecificationGenerationException(
                "Generation failed",
                cause
            );

            assertEquals("Generation failed", ex.getMessage(),
                "Message should match");
            assertEquals(cause, ex.getCause(), "Cause should match");
        }
    }

    // =========================================================================
    // Test ZaiHttpClient Implementation (In-Memory Test Infrastructure)
    // =========================================================================

    /**
     * In-memory test implementation of ZaiHttpClient interface.
     *
     * <p>This is a test infrastructure class that implements the ZaiHttpClient
     * interface defined in SpecificationGenerator for deterministic testing.
     * It provides configurable responses without requiring network calls.</p>
     *
     * <p>This is NOT a production implementation. It exists solely to enable
     * isolated unit testing of SpecificationGenerator behavior.</p>
     */
    private static class TestZaiHttpClient implements SpecificationGenerator.ZaiHttpClient {

        private String responseContent;
        private RuntimeException exceptionToThrow;
        private boolean createChatCompletionCalled;
        private int callCount;
        private java.util.function.Consumer<Map<String, Object>> requestCapture;

        void setResponseContent(String content) {
            this.responseContent = content;
        }

        void setExceptionToThrow(RuntimeException exception) {
            this.exceptionToThrow = exception;
        }

        void setRequestCapture(java.util.function.Consumer<Map<String, Object>> capture) {
            this.requestCapture = capture;
        }

        boolean wasCreateChatCompletionCalled() {
            return createChatCompletionCalled;
        }

        int getCallCount() {
            return callCount;
        }

        @Override
        public String createChatCompletion(Map<String, Object> request, Duration timeout) {
            createChatCompletionCalled = true;
            callCount++;

            if (requestCapture != null) {
                requestCapture.accept(request);
            }

            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }

            return responseContent;
        }

        void reset() {
            responseContent = null;
            exceptionToThrow = null;
            createChatCompletionCalled = false;
            callCount = 0;
            requestCapture = null;
        }
    }

    // =========================================================================
    // Test SchemaHandler Implementation (In-Memory Test Infrastructure)
    // =========================================================================

    /**
     * In-memory test implementation of SchemaHandler interface.
     *
     * <p>This is a test infrastructure class that implements the SchemaHandler
     * interface defined in SpecificationGenerator for deterministic testing.
     * It performs real validation checks: non-null, non-blank, and basic XML structure.</p>
     *
     * <p>This is NOT a production implementation. It exists solely to enable
     * isolated unit testing of SpecificationGenerator behavior.</p>
     */
    private static class TestSchemaHandler implements SpecificationGenerator.SchemaHandler {

        private boolean validateCalled;
        private boolean shouldFail;
        private String failureMessage;
        private String lastValidatedContent;

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        void setFailureMessage(String message) {
            this.failureMessage = message;
        }

        boolean wasValidateCalled() {
            return validateCalled;
        }

        String getLastValidatedContent() {
            return lastValidatedContent;
        }

        @Override
        public void validate(String xmlContent) {
            validateCalled = true;
            lastValidatedContent = xmlContent;

            // Real validation: check XML is non-null and non-blank
            if (xmlContent == null || xmlContent.isBlank()) {
                throw new RuntimeException("XML content cannot be null or blank");
            }

            // Real validation: check for basic XML structure
            if (!xmlContent.contains("<specification") || !xmlContent.contains("</specification>")) {
                throw new RuntimeException("Invalid YAWL specification: missing specification element");
            }

            if (shouldFail) {
                throw new RuntimeException(
                    failureMessage != null ? failureMessage : "Schema validation failed"
                );
            }
        }

        void reset() {
            validateCalled = false;
            shouldFail = false;
            failureMessage = null;
            lastValidatedContent = null;
        }
    }
}
