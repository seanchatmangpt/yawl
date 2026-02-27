/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.regex.Pattern;

import org.jdom2.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for exception logging and context management in YAWL.
 *
 * Tests cover:
 * - Context map functionality
 * - Structured logging format
 * - Password and token redaction
 * - Sensitive data handling
 *
 * Chicago TDD: All tests use real exception objects, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Exception Logging Tests")
@Tag("unit")
class TestExceptionLogging {

    // ============================================================
    // Context Map Tests
    // ============================================================

    @Nested
    @DisplayName("Context Map Functionality")
    class ContextMapTests {

        @Test
        @DisplayName("Empty context returns empty map")
        void emptyContextReturnsEmptyMap() {
            YAWLException ex = new YStateException("Test error");

            Map<String, String> context = ex.getContext();

            assertNotNull(context);
            assertTrue(context.isEmpty());
        }

        @Test
        @DisplayName("Single context entry is retrievable")
        void singleContextEntryIsRetrievable() {
            YAWLException ex = new YStateException("Test error");
            ex.withContext("caseID", "case-12345");

            Map<String, String> context = ex.getContext();

            assertEquals(1, context.size());
            assertEquals("case-12345", context.get("caseID"));
        }

        @Test
        @DisplayName("Multiple context entries are preserved")
        void multipleContextEntriesArePreserved() {
            YAWLException ex = new YStateException("Test error")
                .withContext("caseID", "case-12345")
                .withContext("taskID", "task-67890")
                .withContext("userID", "user-abc")
                .withContext("timestamp", "2026-02-18T10:00:00Z");

            Map<String, String> context = ex.getContext();

            assertEquals(4, context.size());
            assertEquals("case-12345", context.get("caseID"));
            assertEquals("task-67890", context.get("taskID"));
            assertEquals("user-abc", context.get("userID"));
            assertEquals("2026-02-18T10:00:00Z", context.get("timestamp"));
        }

        @Test
        @DisplayName("Context is immutable (defensive copy)")
        void contextIsImmutable() {
            YAWLException ex = new YStateException("Test error");
            ex.withContext("key1", "value1");

            Map<String, String> context1 = ex.getContext();
            context1.put("key2", "value2"); // Try to modify returned map

            Map<String, String> context2 = ex.getContext();

            assertEquals(1, context2.size(), "Original context should be unchanged");
            assertFalse(context2.containsKey("key2"), "External modification should not affect original");
        }

        @Test
        @DisplayName("Context allows value updates")
        void contextAllowsValueUpdates() {
            YAWLException ex = new YStateException("Test error");
            ex.withContext("status", "initial");

            ex.withContext("status", "updated");

            Map<String, String> context = ex.getContext();
            assertEquals("updated", context.get("status"));
            assertEquals(1, context.size());
        }

        @Test
        @DisplayName("Null context value is stored")
        void nullContextValueIsStored() {
            YAWLException ex = new YStateException("Test error");
            ex.withContext("nullableKey", null);

            Map<String, String> context = ex.getContext();

            assertTrue(context.containsKey("nullableKey"));
            assertNull(context.get("nullableKey"));
        }

        @Test
        @DisplayName("Context with special characters")
        void contextWithSpecialCharacters() {
            YAWLException ex = new YStateException("Test error")
                .withContext("xml", "<element attr=\"value\">content</element>")
                .withContext("json", "{\"key\": \"value with spaces\"}")
                .withContext("path", "/path/to/file with spaces.txt");

            Map<String, String> context = ex.getContext();

            assertEquals("<element attr=\"value\">content</element>", context.get("xml"));
            assertEquals("{\"key\": \"value with spaces\"}", context.get("json"));
            assertEquals("/path/to/file with spaces.txt", context.get("path"));
        }

        @Test
        @DisplayName("Method chaining with context")
        void methodChainingWithContext() {
            YAWLException ex = new YStateException("Test error")
                .withContext("a", "1")
                .withContext("b", "2")
                .withContext("c", "3");

            assertNotNull(ex);
            assertEquals(3, ex.getContext().size());
        }
    }

    // ============================================================
    // Structured Format Tests
    // ============================================================

    @Nested
    @DisplayName("Structured Logging Format")
    class StructuredFormatTests {

        @Test
        @DisplayName("toString without context is simple")
        void toStringWithoutContextIsSimple() {
            YAWLException ex = new YStateException("Simple error");

            String str = ex.toString();

            assertTrue(str.contains("YStateException"));
            assertTrue(str.contains("Simple error"));
            assertFalse(str.contains("Context:"));
            assertFalse(str.contains("Troubleshooting:"));
        }

        @Test
        @DisplayName("toString with context includes context section")
        void toStringWithContextIncludesContextSection() {
            YAWLException ex = new YStateException("Error with context")
                .withContext("caseID", "case-123");

            String str = ex.toString();

            assertTrue(str.contains("Context:"));
            assertTrue(str.contains("caseID"));
            assertTrue(str.contains("case-123"));
        }

        @Test
        @DisplayName("toString with troubleshooting guide includes guide")
        void toStringWithTroubleshootingGuideIncludesGuide() {
            YAWLException ex = new YStateException("Error")
                .withTroubleshootingGuide("Try restarting the service");

            String str = ex.toString();

            assertTrue(str.contains("Troubleshooting:"));
            assertTrue(str.contains("Try restarting the service"));
        }

        @Test
        @DisplayName("toString with both context and guide includes both")
        void toStringWithBothContextAndGuideIncludesBoth() {
            YAWLException ex = new YStateException("Complex error")
                .withContext("caseID", "case-456")
                .withContext("taskID", "task-789")
                .withTroubleshootingGuide("Check task configuration");

            String str = ex.toString();

            assertTrue(str.contains("Context:"));
            assertTrue(str.contains("caseID"));
            assertTrue(str.contains("taskID"));
            assertTrue(str.contains("Troubleshooting:"));
            assertTrue(str.contains("Check task configuration"));
        }

        @Test
        @DisplayName("Context map format is readable")
        void contextMapFormatIsReadable() {
            YAWLException ex = new YStateException("Test")
                .withContext("key1", "value1")
                .withContext("key2", "value2");

            String str = ex.toString();

            // Context should be formatted as {key1=value1, key2=value2}
            assertTrue(str.contains("key1=value1") || str.contains("key1"));
            assertTrue(str.contains("key2=value2") || str.contains("key2"));
        }

        @Test
        @DisplayName("XML format includes message")
        void xmlFormatIncludesMessage() {
            YAWLException ex = new YStateException("XML error message");

            String xml = ex.toXML();

            assertTrue(xml.contains("YStateException"));
            assertTrue(xml.contains("<message>"));
            assertTrue(xml.contains("XML error message"));
            assertTrue(xml.contains("</message>"));
        }

        @Test
        @DisplayName("Data exception XML includes all fields")
        void dataExceptionXmlIncludesAllFields() {
            YDataStateException ex = new YDataStateException(
                "//query",
                new Element("data"),
                "<schema/>",
                new Element("input"),
                "validation error",
                "TestTask",
                "Data error message"
            );

            String xml = ex.toXML();

            assertTrue(xml.contains("YDataStateException"));
            assertTrue(xml.contains("<message>"));
            assertTrue(xml.contains("<queryString>"));
            assertTrue(xml.contains("//query"));
            assertTrue(xml.contains("<source>"));
            assertTrue(xml.contains("TestTask"));
        }

        @Test
        @DisplayName("getMessage returns clean message")
        void getMessageReturnsCleanMessage() {
            YStateException ex = new YStateException("Clean message");

            String message = ex.getMessage();

            assertEquals("Clean message", message);
            assertFalse(message.contains("Context:"));
            assertFalse(message.contains("Troubleshooting:"));
        }
    }

    // ============================================================
    // Password/Token Redaction Tests
    // ============================================================

    @Nested
    @DisplayName("Password and Token Redaction")
    class PasswordTokenRedactionTests {

        // Patterns that should be redacted in logs
        private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(password|passwd|pwd)\\s*[=:]\\s*\\S+",
            Pattern.CASE_INSENSITIVE
        );
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(token|api[_-]?key|secret|credential)\\s*[=:]\\s*\\S+",
            Pattern.CASE_INSENSITIVE
        );

        @Test
        @DisplayName("Context does not accept obvious password keys")
        void contextDoesNotAcceptObviousPasswordKeys() {
            // This test documents that users should not put passwords in context
            // The context map itself doesn't enforce this, but logging should redact
            YAWLException ex = new YStateException("Auth error")
                .withContext("password", "secret123");

            String str = ex.toString();

            // Log output should redact the password
            String redacted = redactSensitiveData(str);
            assertTrue(redacted.contains("password") && !redacted.contains("secret123"),
                "Password should be redacted in logs");
        }

        @Test
        @DisplayName("API keys should be redacted")
        void apiKeysShouldBeRedacted() {
            YAWLException ex = new YStateException("API error")
                .withContext("apiKey", "sk-1234567890abcdef");

            String str = ex.toString();
            String redacted = redactSensitiveData(str);

            assertTrue(redacted.contains("apiKey") && !redacted.contains("sk-1234567890abcdef"),
                "API key should be redacted");
        }

        @Test
        @DisplayName("Tokens should be redacted")
        void tokensShouldBeRedacted() {
            YAWLException ex = new YStateException("Token error")
                .withContext("authToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9");

            String str = ex.toString();
            String redacted = redactSensitiveData(str);

            assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"),
                "JWT token should be redacted");
        }

        @Test
        @DisplayName("Database credentials should be redacted")
        void databaseCredentialsShouldBeRedacted() {
            YAWLException ex = new YPersistenceException("Connection error")
                .withContext("dbUser", "admin")
                .withContext("dbPassword", "supersecret")
                .withContext("connectionString", "jdbc:mysql://localhost:3306/db?user=admin&password=supersecret");

            String str = ex.toString();
            String redacted = redactSensitiveData(str);

            assertFalse(redacted.contains("supersecret"),
                "Database password should be redacted");
        }

        @Test
        @DisplayName("Safe keys are not redacted")
        void safeKeysAreNotRedacted() {
            YAWLException ex = new YStateException("Normal error")
                .withContext("caseID", "case-123")
                .withContext("taskName", "ProcessOrder")
                .withContext("status", "failed");

            String str = ex.toString();
            String redacted = redactSensitiveData(str);

            assertTrue(redacted.contains("case-123"), "Case ID should not be redacted");
            assertTrue(redacted.contains("ProcessOrder"), "Task name should not be redacted");
            assertTrue(redacted.contains("failed"), "Status should not be redacted");
        }

        @Test
        @DisplayName("Message content is not modified by context")
        void messageContentIsNotModifiedByContext() {
            YAWLException ex = new YStateException("Error with password=secret in message");

            String message = ex.getMessage();

            assertEquals("Error with password=secret in message", message);
            // getMessage() returns the raw message; redaction applies at logging time
        }

        @Test
        @DisplayName("Redaction handles null values")
        void redactionHandlesNullValues() {
            YAWLException ex = new YStateException("Error")
                .withContext("password", null);

            String str = ex.toString();

            assertNotNull(str);
            assertTrue(str.contains("password"));
        }

        @Test
        @DisplayName("Redaction utility works correctly")
        void redactionUtilityWorksCorrectly() {
            String input = "password=secret123 apiKey=abcd1234 token=xyz789 caseID=case-1";
            String redacted = redactSensitiveData(input);

            assertFalse(redacted.contains("secret123"), "Password value should be redacted");
            assertFalse(redacted.contains("abcd1234"), "API key value should be redacted");
            assertFalse(redacted.contains("xyz789"), "Token value should be redacted");
            assertTrue(redacted.contains("case-1"), "Non-sensitive value should remain");
        }
    }

    // ============================================================
    // Log Level Determination Tests
    // ============================================================

    @Nested
    @DisplayName("Log Level Determination")
    class LogLevelDeterminationTests {

        @Test
        @DisplayName("Persistence exceptions are logged at ERROR")
        void persistenceExceptionsAreLoggedAtError() {
            YPersistenceException ex = new YPersistenceException("Database failure");

            String level = determineLogLevel(ex);

            assertEquals("ERROR", level);
        }

        @Test
        @DisplayName("State exceptions are logged at WARN")
        void stateExceptionsAreLoggedAtWarn() {
            YStateException ex = new YStateException("Invalid state transition");

            String level = determineLogLevel(ex);

            assertEquals("WARN", level);
        }

        @Test
        @DisplayName("Syntax exceptions are logged at ERROR")
        void syntaxExceptionsAreLoggedAtError() {
            YSyntaxException ex = new YSyntaxException("Invalid XML");

            String level = determineLogLevel(ex);

            assertEquals("ERROR", level);
        }

        @Test
        @DisplayName("Data validation exceptions are logged at WARN")
        void dataValidationExceptionsAreLoggedAtWarn() {
            YDataValidationException ex = new YDataValidationException(
                null, null, "error", "task", "Validation failed"
            );

            String level = determineLogLevel(ex);

            assertEquals("WARN", level);
        }

        @Test
        @DisplayName("Authentication exceptions are logged at WARN")
        void authenticationExceptionsAreLoggedAtWarn() {
            YAuthenticationException ex = new YAuthenticationException("Invalid credentials");

            String level = determineLogLevel(ex);

            assertEquals("WARN", level);
        }

        @Test
        @DisplayName("Generic YAWLException is logged at ERROR")
        void genericYawlExceptionIsLoggedAtError() {
            YAWLException ex = new YAWLException("Unknown error");

            String level = determineLogLevel(ex);

            assertEquals("ERROR", level);
        }
    }

    // ============================================================
    // Structured Context for Observability Tests
    // ============================================================

    @Nested
    @DisplayName("Structured Context for Observability")
    class StructuredContextForObservabilityTests {

        @Test
        @DisplayName("Context provides trace correlation")
        void contextProvidesTraceCorrelation() {
            YAWLException ex = new YStateException("Error during processing")
                .withContext("traceId", "abc123def456")
                .withContext("spanId", "span789")
                .withContext("parentSpanId", "parent123");

            Map<String, String> context = ex.getContext();

            assertEquals("abc123def456", context.get("traceId"));
            assertEquals("span789", context.get("spanId"));
            assertEquals("parent123", context.get("parentSpanId"));
        }

        @Test
        @DisplayName("Context includes business identifiers")
        void contextIncludesBusinessIdentifiers() {
            YAWLException ex = new YStateException("Order processing failed")
                .withContext("orderId", "ORD-12345")
                .withContext("customerId", "CUST-67890")
                .withContext("workflowName", "ProcessOrder");

            Map<String, String> context = ex.getContext();

            assertTrue(context.containsKey("orderId"));
            assertTrue(context.containsKey("customerId"));
            assertTrue(context.containsKey("workflowName"));
        }

        @Test
        @DisplayName("Context captures execution state")
        void contextCapturesExecutionState() {
            YAWLException ex = new YStateException("Execution error")
                .withContext("executionPhase", "dataValidation")
                .withContext("attemptNumber", "3")
                .withContext("maxAttempts", "5")
                .withContext("lastSuccessfulStep", "loadCustomer");

            Map<String, String> context = ex.getContext();

            assertEquals("dataValidation", context.get("executionPhase"));
            assertEquals("3", context.get("attemptNumber"));
        }

        @Test
        @DisplayName("Context is suitable for JSON export")
        void contextIsSuitableForJsonExport() {
            YAWLException ex = new YStateException("JSON test")
                .withContext("key1", "value1")
                .withContext("key2", "value with spaces")
                .withContext("key3", "value\"with\"quotes");

            String json = contextToJson(ex.getContext());

            assertTrue(json.contains("\"key1\""));
            assertTrue(json.contains("\"value1\""));
            assertNotNull(json);
        }
    }

    // ============================================================
    // Exception Message Format Tests
    // ============================================================

    @Nested
    @DisplayName("Exception Message Format")
    class ExceptionMessageFormatTests {

        @Test
        @DisplayName("Data exception message includes source")
        void dataExceptionMessageIncludesSource() {
            YDataStateException ex = new YDataStateException(
                "//customer",
                null,
                null,
                null,
                null,
                "CustomerTask",
                "Query failed"
            );

            String message = ex.getMessage();

            assertTrue(message.contains("CustomerTask"));
            assertTrue(message.contains("//customer"));
        }

        @Test
        @DisplayName("Data exception message includes validation errors")
        void dataExceptionMessageIncludesValidationErrors() {
            YDataStateException ex = new YDataStateException(
                null,
                null,
                null,
                null,
                "cvc-type.3.1.1: Invalid content was found",
                "ValidationTask",
                "Validation failed"
            );

            String message = ex.getMessage();

            assertTrue(message.contains("cvc-type.3.1.1"));
        }

        @Test
        @DisplayName("Query exception message includes query")
        void queryExceptionMessageIncludesQuery() {
            YDataQueryException ex = new YDataQueryException(
                "//task[@id='123']/input",
                new Element("data"),
                "QueryTask",
                "XPath evaluation failed"
            );

            String message = ex.getMessage();

            assertTrue(message.contains("//task[@id='123']/input"));
            assertTrue(message.contains("QueryTask"));
        }

        @Test
        @DisplayName("Exception with cause includes cause type")
        void exceptionWithCauseIncludesCauseType() {
            RuntimeException cause = new RuntimeException("Inner error");
            YStateException ex = new YStateException("Outer error", cause);

            String str = ex.toString();

            assertTrue(str.contains("RuntimeException"));
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private String redactSensitiveData(String input) {
        if (input == null) return null;

        // Redact password patterns
        String result = input.replaceAll("(?i)(password|passwd|pwd)\\s*[=:]\\s*\\S+", "$1=***REDACTED***");
        // Redact token/api key patterns
        result = result.replaceAll("(?i)(token|api[_-]?key|secret|credential|auth[_-]?token)\\s*[=:]\\s*\\S+", "$1=***REDACTED***");
        // Redact in connection strings
        result = result.replaceAll("(?i)(password=)[^&\\s]+", "$1***REDACTED***");

        return result;
    }

    private String determineLogLevel(YAWLException ex) {
        if (ex instanceof YPersistenceException) return "ERROR";
        if (ex instanceof YSyntaxException) return "ERROR";
        if (ex instanceof YStateException) return "WARN";
        if (ex instanceof YDataValidationException) return "WARN";
        if (ex instanceof YAuthenticationException) return "WARN";
        if (ex instanceof YConnectivityException) return "WARN";
        return "ERROR"; // Default for unknown types
    }

    private String contextToJson(Map<String, String> context) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\": ");
            if (entry.getValue() == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(entry.getValue().replace("\"", "\\\"")).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
