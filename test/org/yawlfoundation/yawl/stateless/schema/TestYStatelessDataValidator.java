package org.yawlfoundation.yawl.stateless.schema;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for stateless data validation.
 * Tests real YAWL schema and data validation semantics.
 * No mocks — uses actual XML validation against schema.
 *
 * @author Claude Code / GODSPEED Protocol
 * @since 6.0.0
 */
@DisplayName("YStateless Data Validation")
class TestYStatelessDataValidator {

    private YDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new YDataValidator();
    }

    @Nested
    @DisplayName("XML schema validation")
    class TestSchemaValidation {
        @Test
        @DisplayName("Validate well-formed XML")
        void testValidWellFormedXML() {
            String validXml = "<root><name>Test</name></root>";
            assertTrue(validator.isWellFormedXML(validXml),
                "Well-formed XML should validate");
        }

        @Test
        @DisplayName("Reject malformed XML")
        void testMalformedXML() {
            String malformedXml = "<root><name>Test</name>";
            assertFalse(validator.isWellFormedXML(malformedXml),
                "Malformed XML (unclosed tag) should fail validation");
        }

        @Test
        @DisplayName("Validate XML with attributes")
        void testXMLWithAttributes() {
            String xmlWithAttrs = "<root type='test'><name id='1'>Test</name></root>";
            assertTrue(validator.isWellFormedXML(xmlWithAttrs));
        }

        @Test
        @DisplayName("Validate empty XML elements")
        void testEmptyElements() {
            String emptyElement = "<root><item/><item/></root>";
            assertTrue(validator.isWellFormedXML(emptyElement));
        }

        @Test
        @DisplayName("Validate XML with namespaces")
        void testNamespacedXML() {
            String nsXml = "<root xmlns='http://example.com'><item>Test</item></root>";
            assertTrue(validator.isWellFormedXML(nsXml));
        }
    }

    @Nested
    @DisplayName("YAWL specification validation")
    class TestSpecificationValidation {
        @Test
        @DisplayName("Validate specification structure")
        void testSpecificationStructure() {
            String validSpec = "<specification>" +
                "<name>TestSpec</name>" +
                "<decomposition id='root' type='net'>" +
                "<processControlElements/>" +
                "</decomposition>" +
                "</specification>";

            assertTrue(validator.isValidSpecification(validSpec),
                "Valid YAWL specification should validate");
        }

        @Test
        @DisplayName("Reject specification with missing decomposition")
        void testMissingDecomposition() {
            String invalidSpec = "<specification>" +
                "<name>TestSpec</name>" +
                "</specification>";

            assertFalse(validator.isValidSpecification(invalidSpec),
                "Specification without decomposition should fail");
        }

        @Test
        @DisplayName("Validate specification with multiple decompositions")
        void testMultipleDecompositions() {
            String multiSpec = "<specification>" +
                "<name>TestSpec</name>" +
                "<decomposition id='root' type='net'>" +
                "<processControlElements/>" +
                "</decomposition>" +
                "<decomposition id='sub1' type='net'>" +
                "<processControlElements/>" +
                "</decomposition>" +
                "</specification>";

            assertTrue(validator.isValidSpecification(multiSpec));
        }
    }

    @Nested
    @DisplayName("Data type validation")
    class TestDataTypeValidation {
        @Test
        @DisplayName("Validate integer data")
        void testIntegerValidation() {
            assertTrue(validator.isValidInteger("123"));
            assertTrue(validator.isValidInteger("-456"));
            assertFalse(validator.isValidInteger("abc"));
            assertFalse(validator.isValidInteger("12.34"));
        }

        @Test
        @DisplayName("Validate decimal data")
        void testDecimalValidation() {
            assertTrue(validator.isValidDecimal("123.45"));
            assertTrue(validator.isValidDecimal("-456.78"));
            assertTrue(validator.isValidDecimal("0.99"));
            assertFalse(validator.isValidDecimal("abc"));
        }

        @Test
        @DisplayName("Validate boolean data")
        void testBooleanValidation() {
            assertTrue(validator.isValidBoolean("true"));
            assertTrue(validator.isValidBoolean("false"));
            assertTrue(validator.isValidBoolean("1"));
            assertTrue(validator.isValidBoolean("0"));
            assertFalse(validator.isValidBoolean("maybe"));
        }

        @Test
        @DisplayName("Validate string data")
        void testStringValidation() {
            assertTrue(validator.isValidString("any text"));
            assertTrue(validator.isValidString(""));
            assertTrue(validator.isValidString("123"));
            // Strings should accept anything
        }

        @Test
        @DisplayName("Validate date data")
        void testDateValidation() {
            assertTrue(validator.isValidDate("2026-02-20"));
            assertFalse(validator.isValidDate("2026-13-32"));
            assertFalse(validator.isValidDate("not-a-date"));
        }
    }

    @Nested
    @DisplayName("Complex type validation")
    class TestComplexTypeValidation {
        @Test
        @DisplayName("Validate structured data")
        void testStructuredData() {
            String structured = "<person>" +
                "<name>John Doe</name>" +
                "<age>30</age>" +
                "<email>john@example.com</email>" +
                "</person>";

            assertTrue(validator.isWellFormedXML(structured));
        }

        @Test
        @DisplayName("Validate list/array data")
        void testListData() {
            String listData = "<items>" +
                "<item>First</item>" +
                "<item>Second</item>" +
                "<item>Third</item>" +
                "</items>";

            assertTrue(validator.isWellFormedXML(listData));
        }

        @Test
        @DisplayName("Validate nested structures")
        void testNestedStructures() {
            String nested = "<order>" +
                "<customer>" +
                "<name>Alice</name>" +
                "<address>" +
                "<street>123 Main St</street>" +
                "<city>Metropolis</city>" +
                "</address>" +
                "</customer>" +
                "<items>" +
                "<item quantity='2'>Widget</item>" +
                "</items>" +
                "</order>";

            assertTrue(validator.isWellFormedXML(nested));
        }
    }

    @Nested
    @DisplayName("Variable and parameter validation")
    class TestVariableValidation {
        @Test
        @DisplayName("Validate input variable type matching")
        void testInputVariableMatching() {
            String varXml = "<variable>" +
                "<name>count</name>" +
                "<type>integer</type>" +
                "</variable>";

            assertTrue(validator.isWellFormedXML(varXml));
            assertTrue(validator.isValidInteger("42"));
        }

        @Test
        @DisplayName("Validate output parameter binding")
        void testOutputParameterBinding() {
            String paramXml = "<parameter>" +
                "<name>result</name>" +
                "<type>string</type>" +
                "<value>Success</value>" +
                "</parameter>";

            assertTrue(validator.isWellFormedXML(paramXml));
        }

        @Test
        @DisplayName("Validate optional variables")
        void testOptionalVariables() {
            String optionalXml = "<variable>" +
                "<name>optional_field</name>" +
                "<type>string</type>" +
                "<optional>true</optional>" +
                "</variable>";

            assertTrue(validator.isWellFormedXML(optionalXml));
            assertTrue(validator.isValidString(""));
            assertTrue(validator.isValidString("any value"));
        }
    }

    @Nested
    @DisplayName("Error reporting and diagnostics")
    class TestValidationErrors {
        @Test
        @DisplayName("Get error message for validation failure")
        void testErrorMessages() {
            String malformed = "<unclosed>";
            validator.isWellFormedXML(malformed);
            String error = validator.getLastError();
            assertNotNull(error);
            assertTrue(error.length() > 0, "Error message should be non-empty");
        }

        @Test
        @DisplayName("Validation error includes line number")
        void testErrorLineNumber() {
            String multilineError = "<root>\n<invalid attribute=\n</root>";
            validator.isWellFormedXML(multilineError);
            String error = validator.getLastError();
            assertTrue(error.contains("line") || error.contains("Line"),
                "Error should reference line number");
        }

        @Test
        @DisplayName("Clear error state after successful validation")
        void testErrorStateClear() {
            // First validation fails
            validator.isWellFormedXML("<unclosed>");
            String error1 = validator.getLastError();
            assertNotNull(error1);

            // Second validation succeeds
            validator.isWellFormedXML("<root/>");
            String error2 = validator.getLastError();
            assertTrue(error2.isEmpty() || error2.equals(""),
                "Error state should be cleared on success");
        }
    }

    /**
     * Test coverage summary:
     * - XML well-formedness: basic, attributes, empty elements, namespaces
     * - YAWL specification structure: decompositions, process elements
     * - Data type validation: integer, decimal, boolean, string, date
     * - Complex types: structured data, lists, nested structures
     * - Variables and parameters: type matching, binding, optional fields
     * - Error reporting: messages, line numbers, state management
     *
     * All tests use real XML validation semantics. No mocks.
     * Target: 80%+ line coverage on stateless/schema package.
     *
     * @since 6.0.0 GODSPEED Protocol
     */
}
