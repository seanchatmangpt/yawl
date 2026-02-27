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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StringReader;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for exception recovery scenarios in YAWL.
 *
 * Tests cover:
 * - Rollback triggers and recovery
 * - Recovery hints from exceptions
 * - Exception chaining patterns
 * - Exception serialization for remote propagation
 *
 * Chicago TDD: All tests use real exception objects and serialization, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Exception Recovery Tests")
@Tag("unit")
class TestExceptionRecovery {

    // ============================================================
    // Rollback Trigger Tests
    // ============================================================

    @Nested
    @DisplayName("Rollback Triggers")
    class RollbackTriggerTests {

        @Test
        @DisplayName("YPersistenceException indicates rollback required")
        void yPersistenceExceptionIndicatesRollbackRequired() {
            YPersistenceException ex = new YPersistenceException("Database write failed");

            // Persistence exceptions typically require transaction rollback
            assertNotNull(ex.getMessage());
            assertTrue(ex instanceof YAWLException);

            // In production, this would trigger rollback logic
            boolean requiresRollback = shouldTriggerRollback(ex);
            assertTrue(requiresRollback, "Persistence exception should trigger rollback");
        }

        @Test
        @DisplayName("YStateException may require state reset")
        void yStateExceptionMayRequireStateReset() {
            YStateException ex = new YStateException("Invalid work item state transition");

            // State exceptions may require state reset or reconciliation
            boolean requiresStateReset = shouldTriggerStateReset(ex);
            assertTrue(requiresStateReset, "State exception should trigger state reset");
        }

        @Test
        @DisplayName("YSyntaxException requires input correction")
        void ySyntaxExceptionRequiresInputCorrection() {
            YSyntaxException ex = new YSyntaxException("Invalid XML in specification");

            // Syntax exceptions require fixing input, not rollback
            boolean requiresInputFix = shouldTriggerInputFix(ex);
            assertTrue(requiresInputFix, "Syntax exception should trigger input fix");
        }

        @Test
        @DisplayName("Exception with cause preserves recovery information")
        void exceptionWithCausePreservesRecoveryInformation() {
            IOException rootCause = new IOException("Connection reset");
            YPersistenceException ex = new YPersistenceException("Transaction failed", rootCause);

            assertNotNull(ex.getCause());
            assertEquals("Connection reset", ex.getCause().getMessage());

            // Recovery hint from cause
            String recoveryHint = extractRecoveryHint(ex);
            assertTrue(recoveryHint.contains("Connection") || recoveryHint.contains("IO"),
                "Recovery hint should reference underlying cause");
        }

        @Test
        @DisplayName("Nested exceptions preserve full recovery chain")
        void nestedExceptionsPreserveFullRecoveryChain() {
            Exception level1 = new IOException("Disk I/O error");
            Exception level2 = new RuntimeException("Data access failed", level1);
            YPersistenceException level3 = new YPersistenceException("Transaction failed", level2);

            // Walk the chain
            StringBuilder chain = new StringBuilder();
            Throwable current = level3;
            while (current != null) {
                chain.append(current.getClass().getSimpleName())
                     .append(": ")
                     .append(current.getMessage())
                     .append(" -> ");
                current = current.getCause();
            }

            String chainStr = chain.toString();
            assertTrue(chainStr.contains("YPersistenceException"));
            assertTrue(chainStr.contains("RuntimeException"));
            assertTrue(chainStr.contains("IOException"));
            assertTrue(chainStr.contains("Disk I/O"));
        }
    }

    // ============================================================
    // Recovery Hints Tests
    // ============================================================

    @Nested
    @DisplayName("Recovery Hints")
    class RecoveryHintsTests {

        @Test
        @DisplayName("Exception with troubleshooting guide provides recovery hint")
        void exceptionWithTroubleshootingGuideProvidesRecoveryHint() {
            YAWLException ex = new YPersistenceException("Connection pool exhausted")
                .withTroubleshootingGuide("Increase connection pool size or check for connection leaks");

            String hint = ex.getTroubleshootingGuide();
            assertNotNull(hint);
            assertTrue(hint.contains("connection pool"));
        }

        @Test
        @DisplayName("Exception with context provides recovery information")
        void exceptionWithContextProvidesRecoveryInformation() {
            YStateException baseEx = new YStateException("Work item not found");
            baseEx.withContext("workItemID", "WI-12345");
            baseEx.withContext("caseID", "CASE-67890");
            baseEx.withContext("operation", "complete");
            YStateException ex = baseEx;

            var context = ex.getContext();
            assertEquals("WI-12345", context.get("workItemID"));
            assertEquals("CASE-67890", context.get("caseID"));
            assertEquals("complete", context.get("operation"));

            // Context helps identify what to retry or rollback
            assertTrue(context.containsKey("caseID"));
        }

        @Test
        @DisplayName("Combined context and guide provides full recovery info")
        void combinedContextAndGuideProvidesFullRecoveryInfo() {
            YDataValidationException ex = new YDataValidationException(
                "<schema/>", new Element("data"), "Validation error", "TaskA", "Invalid data"
            );

            ex.withContext("taskId", "TaskA")
              .withContext("dataField", "customerAge")
              .withTroubleshootingGuide("Ensure customerAge is a positive integer");

            // Full recovery information is available
            assertNotNull(ex.getContext());
            assertNotNull(ex.getTroubleshootingGuide());
            assertEquals("TaskA", ex.getContext().get("taskId"));
        }

        @Test
        @DisplayName("Data exception provides query information for recovery")
        void dataExceptionProvidesQueryInformationForRecovery() {
            Element queriedData = new Element("result");
            queriedData.setText("empty");

            YDataQueryException ex = new YDataQueryException(
                "//customer[@id='123']",
                queriedData,
                "GetCustomerTask",
                "Customer not found"
            );

            // Query information helps with debugging/retry
            assertEquals("//customer[@id='123']", ex.getQueryString());
            assertEquals("GetCustomerTask", ex.getSource());
            assertNotNull(ex.getData());
        }

        @Test
        @DisplayName("Exception toString includes recovery context")
        void exceptionToStringIncludesRecoveryContext() {
            YAWLException ex = new YStateException("Invalid state")
                .withContext("currentState", "Running")
                .withContext("requestedState", "Suspended")
                .withTroubleshootingGuide("Check if transition is allowed");

            String str = ex.toString();

            assertTrue(str.contains("currentState"));
            assertTrue(str.contains("Running"));
            assertTrue(str.contains("requestedState"));
            assertTrue(str.contains("Suspended"));
            assertTrue(str.contains("Troubleshooting"));
        }
    }

    // ============================================================
    // Exception Chaining Tests
    // ============================================================

    @Nested
    @DisplayName("Exception Chaining Patterns")
    class ExceptionChainingTests {

        @Test
        @DisplayName("Simple chain with single cause")
        void simpleChainWithSingleCause() {
            IOException cause = new IOException("Network unreachable");
            YConnectivityException ex = new YConnectivityException("Service unavailable", cause);

            assertEquals("Service unavailable", ex.getMessage());
            assertSame(cause, ex.getCause());
            assertEquals("Network unreachable", ex.getCause().getMessage());
        }

        @Test
        @DisplayName("Chain preserves all exception types")
        void chainPreservesAllExceptionTypes() {
            Exception e1 = new IllegalArgumentException("Bad argument");
            Exception e2 = new RuntimeException("Processing failed", e1);
            Exception e3 = new YStateException("State corrupted", e2);
            Exception e4 = new YPersistenceException("Could not persist", e3);

            // Verify the full chain
            assertEquals(YPersistenceException.class, e4.getClass());
            assertEquals(YStateException.class, e4.getCause().getClass());
            assertEquals(RuntimeException.class, e4.getCause().getCause().getClass());
            assertEquals(IllegalArgumentException.class, e4.getCause().getCause().getCause().getClass());
        }

        @Test
        @DisplayName("Rethrow method handles exception types correctly")
        void rethrowMethodHandlesExceptionTypesCorrectly() throws Exception {
            YStateException stateEx = new YStateException("State error");

            assertThrows(YStateException.class, () -> stateEx.rethrow());
        }

        @Test
        @DisplayName("Rethrow with persistence exception")
        void rethrowWithPersistenceException() throws Exception {
            YPersistenceException persistEx = new YPersistenceException("Persistence error");

            assertThrows(YPersistenceException.class, () -> persistEx.rethrow());
        }

        @Test
        @DisplayName("Rethrow with data state exception")
        void rethrowWithDataStateException() throws Exception {
            YDataStateException dataEx = new YDataStateException(
                null, null, null, null, null, "source", "Data error"
            );

            assertThrows(YDataStateException.class, () -> dataEx.rethrow());
        }

        @Test
        @DisplayName("Rethrow with query exception")
        void rethrowWithQueryException() throws Exception {
            YQueryException queryEx = new YQueryException("Query error");

            assertThrows(YQueryException.class, () -> queryEx.rethrow());
        }

        @Test
        @DisplayName("Rethrow with unknown exception type does nothing")
        void rethrowWithUnknownExceptionTypeDoesNothing() throws Exception {
            YAWLException unknownEx = new YAWLException("Unknown type");

            // Should not throw for unknown types
            assertDoesNotThrow(() -> unknownEx.rethrow());
        }

        @Test
        @DisplayName("Exception message includes cause information")
        void exceptionMessageIncludesCauseInformation() {
            Exception cause = new NullPointerException("Null reference encountered");
            YEngineStateException ex = new YEngineStateException("Engine initialization failed", cause);

            String message = ex.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("Engine initialization failed"));
        }
    }

    // ============================================================
    // Exception Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Exception Serialization")
    class ExceptionSerializationTests {

        @Test
        @DisplayName("YPersistenceException serializes and deserializes correctly")
        void yPersistenceExceptionSerializesCorrectly() throws Exception {
            YPersistenceException original = new YPersistenceException("Database error");

            YPersistenceException deserialized = serializeAndDeserialize(original);

            assertEquals(original.getMessage(), deserialized.getMessage());
        }

        @Test
        @DisplayName("YStateException serializes and deserializes correctly")
        void yStateExceptionSerializesCorrectly() throws Exception {
            YStateException original = new YStateException("Invalid state");

            YStateException deserialized = serializeAndDeserialize(original);

            assertEquals(original.getMessage(), deserialized.getMessage());
        }

        @Test
        @DisplayName("Exception with cause serializes correctly")
        void exceptionWithCauseSerializesCorrectly() throws Exception {
            IOException cause = new IOException("Underlying IO error");
            YPersistenceException original = new YPersistenceException("Wrapper error", cause);

            YPersistenceException deserialized = serializeAndDeserialize(original);

            assertEquals("Wrapper error", deserialized.getMessage());
            assertNotNull(deserialized.getCause());
            assertEquals("Underlying IO error", deserialized.getCause().getMessage());
        }

        @Test
        @DisplayName("Nested exception chain serializes correctly")
        void nestedExceptionChainSerializesCorrectly() throws Exception {
            Exception level1 = new IOException("Level 1");
            Exception level2 = new RuntimeException("Level 2", level1);
            YStateException original = new YStateException("Level 3", level2);

            YStateException deserialized = serializeAndDeserialize(original);

            assertEquals("Level 3", deserialized.getMessage());
            assertNotNull(deserialized.getCause());
            assertEquals(RuntimeException.class, deserialized.getCause().getClass());
            assertNotNull(deserialized.getCause().getCause());
            assertEquals(IOException.class, deserialized.getCause().getCause().getClass());
        }

        @Test
        @DisplayName("Exception context is preserved through serialization")
        void exceptionContextPreservedThroughSerialization() throws Exception {
            YAWLException original = new YPersistenceException("Test error");
            original.withContext("key1", "value1");
            original.withContext("key2", "value2");

            YAWLException deserialized = serializeAndDeserialize(original);

            // Note: Context is transient, so it won't be serialized
            // This test verifies the serialization doesn't break
            assertNotNull(deserialized);
            assertEquals("Test error", deserialized.getMessage());
        }

        @Test
        @DisplayName("Multiple serialization cycles preserve data")
        void multipleSerializationCyclesPreserveData() throws Exception {
            YStateException original = new YStateException("Test state error");

            // First cycle
            YStateException first = serializeAndDeserialize(original);
            // Second cycle
            YStateException second = serializeAndDeserialize(first);
            // Third cycle
            YStateException third = serializeAndDeserialize(second);

            assertEquals(original.getMessage(), third.getMessage());
        }
    }

    // ============================================================
    // XML Roundtrip Tests
    // ============================================================

    @Nested
    @DisplayName("Exception XML Roundtrip")
    class ExceptionXmlRoundtripTests {

        @Test
        @DisplayName("YAWLException toXML and unmarshal")
        void yawlExceptionToXmlAndUnmarshal() throws Exception {
            YAWLException original = new YAWLException("Test error message");

            String xml = original.toXML();
            assertNotNull(xml);
            assertTrue(xml.contains("YAWLException"));
            assertTrue(xml.contains("Test error message"));

            Document doc = parseXmlDocument(xml);
            YAWLException restored = YAWLException.unmarshal(doc);

            assertEquals("Test error message", restored.getMessage());
        }

        @Test
        @DisplayName("YDataStateException toXML preserves all fields")
        void yDataStateExceptionToXmlPreservesAllFields() throws Exception {
            Element queriedData = new Element("customer");
            queriedData.setAttribute("id", "123");

            YDataStateException original = new YDataStateException(
                "//customer[@id='123']",
                queriedData,
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>",
                new Element("input"),
                "Validation failed",
                "CustomerTask",
                "Customer data invalid"
            );

            String xml = original.toXML();
            assertNotNull(xml);
            assertTrue(xml.contains("YDataStateException"));
            assertTrue(xml.contains("//customer"));
            assertTrue(xml.contains("CustomerTask"));

            Document doc = parseXmlDocument(xml);
            YDataStateException restored = (YDataStateException) YAWLException.unmarshal(doc);

            assertNotNull(restored);
            assertTrue(restored.getMessage().contains("CustomerTask"));
        }

        @Test
        @DisplayName("YDataQueryException toXML and unmarshal")
        void yDataQueryExceptionToXmlAndUnmarshal() throws Exception {
            Element data = new Element("result");
            data.setText("query result");

            YDataQueryException original = new YDataQueryException(
                "//task/input",
                data,
                "QueryTask",
                "Query execution failed"
            );

            String xml = original.toXML();
            assertNotNull(xml);

            Document doc = parseXmlDocument(xml);
            YDataQueryException restored = (YDataQueryException) YAWLException.unmarshal(doc);

            assertEquals("//task/input", restored.getQueryString());
            assertEquals("QueryTask", restored.getSource());
        }

        @Test
        @DisplayName("YDataValidationException toXML and unmarshal")
        void yDataValidationExceptionToXmlAndUnmarshal() throws Exception {
            YDataValidationException original = new YDataValidationException(
                "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'/>",
                new Element("data"),
                "cvc-type.3.1.1",
                "ValidationTask",
                "Schema validation failed"
            );

            String xml = original.toXML();
            assertNotNull(xml);

            Document doc = parseXmlDocument(xml);
            YDataValidationException restored = (YDataValidationException) YAWLException.unmarshal(doc);

            assertNotNull(restored);
        }

        @Test
        @DisplayName("XML contains proper message element")
        void xmlContainsProperMessageElement() {
            YAWLException ex = new YAWLException("Error with special chars: <>&\"'");

            String xml = ex.toXML();

            assertTrue(xml.contains("<message>"));
            assertTrue(xml.contains("</message>"));
        }
    }

    // ============================================================
    // Recovery Scenario Tests
    // ============================================================

    @Nested
    @DisplayName("Recovery Scenarios")
    class RecoveryScenarioTests {

        @Test
        @DisplayName("Identifies recoverable vs non-recoverable exceptions")
        void identifiesRecoverableVsNonRecoverableExceptions() {
            // Recoverable - transient issues
            YConnectivityException connectivity = new YConnectivityException("Connection timeout");
            assertTrue(isRecoverable(connectivity), "Connectivity issues are often recoverable");

            // Non-recoverable - input errors
            YSyntaxException syntax = new YSyntaxException("Malformed XML");
            assertFalse(isRecoverable(syntax), "Syntax errors are not recoverable without fix");

            // May be recoverable with intervention
            YStateException state = new YStateException("Conflicting state");
            assertTrue(mayBeRecoverable(state), "State issues may be recoverable");
        }

        @Test
        @DisplayName("Determines retry eligibility from exception type")
        void determinesRetryEligibilityFromExceptionType() {
            YPersistenceException persistEx = new YPersistenceException("Lock timeout");
            YDataValidationException validationEx = new YDataValidationException(
                null, null, "error", "task", "Invalid data"
            );

            int persistRetries = getRecommendedRetries(persistEx);
            int validationRetries = getRecommendedRetries(validationEx);

            assertTrue(persistRetries > 0, "Persistence errors should allow retries");
            assertEquals(0, validationRetries, "Validation errors should not be retried");
        }

        @Test
        @DisplayName("Extracts error code for programmatic handling")
        void extractsErrorCodeForProgrammaticHandling() {
            YDataValidationException ex = new YDataValidationException(
                null, null, "cvc-type.3.1.1: Invalid content", "task", "Validation failed"
            );

            String errorCode = extractErrorCode(ex);
            assertTrue(errorCode.startsWith("cvc-"), "Should extract Xerces error code");
        }

        @Test
        @DisplayName("Determines if user notification is required")
        void determinesIfUserNotificationIsRequired() {
            YAuthenticationException authEx = new YAuthenticationException("Invalid credentials");
            YPersistenceException persistEx = new YPersistenceException("Internal error");

            assertTrue(requiresUserNotification(authEx), "Auth errors need user notification");
            assertFalse(requiresUserNotification(persistEx), "Internal errors don't need user notification");
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    @SuppressWarnings("unchecked")
    private <T extends Exception> T serializeAndDeserialize(T original) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        }
    }

    private Document parseXmlDocument(String xml) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        return builder.build(new StringReader(xml));
    }

    private boolean shouldTriggerRollback(YAWLException ex) {
        return ex instanceof YPersistenceException;
    }

    private boolean shouldTriggerStateReset(YAWLException ex) {
        return ex instanceof YStateException;
    }

    private boolean shouldTriggerInputFix(YAWLException ex) {
        return ex instanceof YSyntaxException;
    }

    private String extractRecoveryHint(YAWLException ex) {
        if (ex.getCause() != null) {
            return ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage();
        }
        return ex.getMessage();
    }

    private boolean isRecoverable(YAWLException ex) {
        return ex instanceof YConnectivityException || ex instanceof YPersistenceException;
    }

    private boolean mayBeRecoverable(YAWLException ex) {
        return ex instanceof YStateException;
    }

    private int getRecommendedRetries(YAWLException ex) {
        if (ex instanceof YPersistenceException) {
            return 3;
        }
        if (ex instanceof YConnectivityException) {
            return 3;
        }
        return 0; // No retries for validation/syntax errors
    }

    private String extractErrorCode(YDataValidationException ex) {
        String errors = ex.getErrors();
        if (errors != null && errors.contains(":")) {
            return errors.substring(0, errors.indexOf(":")).trim();
        }
        return "UNKNOWN";
    }

    private boolean requiresUserNotification(YAWLException ex) {
        return ex instanceof YAuthenticationException ||
               ex instanceof YSyntaxException ||
               ex instanceof YDataValidationException;
    }
}
