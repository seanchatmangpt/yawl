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
import java.time.Instant;

import org.jdom2.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for YAWL exception hierarchy.
 *
 * Tests cover:
 * - Exception message handling
 * - Exception chaining (cause propagation)
 * - Serialization (for Serializable exceptions)
 * - Specific exception types and their unique features
 * - Inheritance relationships
 *
 * Chicago TDD: All tests use real exception objects, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("YAWL Exception Hierarchy Tests")
@Tag("unit")
class TestExceptionHierarchy {

    // ============================================================
    // YPersistenceException Tests
    // ============================================================

    @Nested
    @DisplayName("YPersistenceException Tests")
    class YPersistenceExceptionTests {

        @Test
        @DisplayName("Preserves message via string constructor")
        void preservesMessageViaStringConstructor() {
            YPersistenceException ex = new YPersistenceException("Database connection failed");

            assertEquals("Database connection failed", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause via throwable constructor")
        void preservesCauseViaThrowableConstructor() {
            IOException cause = new IOException("Connection refused");
            YPersistenceException ex = new YPersistenceException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Preserves message and cause together")
        void preservesMessageAndCause() {
            RuntimeException cause = new RuntimeException("Underlying error");
            YPersistenceException ex = new YPersistenceException("Persistence failed", cause);

            assertEquals("Persistence failed", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Creates with default constructor")
        void createsWithDefaultConstructor() {
            YPersistenceException ex = new YPersistenceException();

            assertNull(ex.getMessage());
            assertNull(ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YPersistenceException ex = new YPersistenceException("test");

            assertTrue(ex instanceof YAWLException);
        }

        @Test
        @DisplayName("Is serializable")
        void isSerializable() throws Exception {
            YPersistenceException original = new YPersistenceException("Test error");

            YPersistenceException deserialized = serializeAndDeserialize(original);

            assertEquals(original.getMessage(), deserialized.getMessage());
        }

        @Test
        @DisplayName("Serializes with cause")
        void serializesWithCause() throws Exception {
            IOException cause = new IOException("IO failed");
            YPersistenceException original = new YPersistenceException("Wrapper", cause);

            YPersistenceException deserialized = serializeAndDeserialize(original);

            assertEquals("Wrapper", deserialized.getMessage());
            assertNotNull(deserialized.getCause());
            assertEquals("IO failed", deserialized.getCause().getMessage());
        }
    }

    // ============================================================
    // YStateException Tests
    // ============================================================

    @Nested
    @DisplayName("YStateException Tests")
    class YStateExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YStateException ex = new YStateException("Invalid engine state");

            assertEquals("Invalid engine state", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            IllegalStateException cause = new IllegalStateException("Bad state");
            YStateException ex = new YStateException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Preserves message and cause")
        void preservesMessageAndCause() {
            Exception cause = new Exception("Root cause");
            YStateException ex = new YStateException("State error", cause);

            assertEquals("State error", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YStateException ex = new YStateException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YAuthenticationException Tests
    // ============================================================

    @Nested
    @DisplayName("YAuthenticationException Tests")
    class YAuthenticationExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YAuthenticationException ex = new YAuthenticationException("Invalid credentials");

            assertEquals("Invalid credentials", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            SecurityException cause = new SecurityException("Access denied");
            YAuthenticationException ex = new YAuthenticationException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Preserves message and cause")
        void preservesMessageAndCause() {
            Exception cause = new Exception("Auth failure");
            YAuthenticationException ex = new YAuthenticationException("Authentication failed", cause);

            assertEquals("Authentication failed", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YAuthenticationException ex = new YAuthenticationException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YQueryException Tests
    // ============================================================

    @Nested
    @DisplayName("YQueryException Tests")
    class YQueryExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YQueryException ex = new YQueryException("Query execution failed");

            assertEquals("Query execution failed", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            SQLExceptionMock cause = new SQLExceptionMock("DB error");
            YQueryException ex = new YQueryException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YQueryException ex = new YQueryException("test");

            assertTrue(ex instanceof YAWLException);
        }

        /**
         * Simple mock for SQLException-like testing without external dependencies.
         */
        private static class SQLExceptionMock extends Exception {
            SQLExceptionMock(String message) {
                super(message);
            }
        }
    }

    // ============================================================
    // YLogException Tests
    // ============================================================

    @Nested
    @DisplayName("YLogException Tests")
    class YLogExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YLogException ex = new YLogException("Logging failed");

            assertEquals("Logging failed", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            IOException cause = new IOException("File write error");
            YLogException ex = new YLogException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YLogException ex = new YLogException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YSchemaBuildingException Tests
    // ============================================================

    @Nested
    @DisplayName("YSchemaBuildingException Tests")
    class YSchemaBuildingExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YSchemaBuildingException ex = new YSchemaBuildingException("Schema parsing error");

            assertEquals("Schema parsing error", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            RuntimeException cause = new RuntimeException("Parse error");
            YSchemaBuildingException ex = new YSchemaBuildingException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YSchemaBuildingException ex = new YSchemaBuildingException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YEngineStateException Tests
    // ============================================================

    @Nested
    @DisplayName("YEngineStateException Tests")
    class YEngineStateExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YEngineStateException ex = new YEngineStateException("Engine not initialized");

            assertEquals("Engine not initialized", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            NullPointerException cause = new NullPointerException("Null reference");
            YEngineStateException ex = new YEngineStateException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YEngineStateException ex = new YEngineStateException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YExternalDataException Tests
    // ============================================================

    @Nested
    @DisplayName("YExternalDataException Tests")
    class YExternalDataExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YExternalDataException ex = new YExternalDataException("External data access failed");

            assertEquals("External data access failed", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            IOException cause = new IOException("Network error");
            YExternalDataException ex = new YExternalDataException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YExternalDataException ex = new YExternalDataException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YSyntaxException Tests
    // ============================================================

    @Nested
    @DisplayName("YSyntaxException Tests")
    class YSyntaxExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YSyntaxException ex = new YSyntaxException("Syntax error in specification");

            assertEquals("Syntax error in specification", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            RuntimeException cause = new RuntimeException("Parse failure");
            YSyntaxException ex = new YSyntaxException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            YSyntaxException ex = new YSyntaxException("test");

            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YConnectivityException Tests
    // ============================================================

    @Nested
    @DisplayName("YConnectivityException Tests")
    class YConnectivityExceptionTests {

        @Test
        @DisplayName("Preserves message")
        void preservesMessage() {
            YConnectivityException ex = new YConnectivityException("Invalid connection");

            assertEquals("Invalid connection", ex.getMessage());
        }

        @Test
        @DisplayName("Preserves cause")
        void preservesCause() {
            IOException cause = new IOException("Connection refused");
            YConnectivityException ex = new YConnectivityException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Extends YSyntaxException")
        void extendsYSyntaxException() {
            YConnectivityException ex = new YConnectivityException("test");

            assertTrue(ex instanceof YSyntaxException);
            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YDataStateException Tests
    // ============================================================

    @Nested
    @DisplayName("YDataStateException Tests")
    class YDataStateExceptionTests {

        @Test
        @DisplayName("Creates with all parameters")
        void createsWithAllParameters() {
            Element queriedData = new Element("data");
            queriedData.setText("test data");
            String schema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
            Element dataInput = new Element("input");
            String xercesErrors = "Validation error at line 1";
            String source = "TestTask";
            String message = "Data validation failed";

            YDataStateException ex = new YDataStateException(
                "//query", queriedData, schema, dataInput, xercesErrors, source, message
            );

            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("TestTask"));
            assertTrue(ex.getMessage().contains("//query"));
        }

        @Test
        @DisplayName("Extends YAWLException")
        void extendsYAWLException() {
            Element data = new Element("data");
            YDataStateException ex = new YDataStateException(
                "query", data, null, null, null, "source", "message"
            );

            assertTrue(ex instanceof YAWLException);
        }

        @Test
        @DisplayName("Returns data input element")
        void returnsDataInputElement() {
            Element dataInput = new Element("input");
            dataInput.setText("input data");

            YDataStateException ex = new YDataStateException(
                null, null, null, dataInput, null, "source", "message"
            );

            assertEquals(dataInput, ex.get_dataInput());
        }

        @Test
        @DisplayName("Returns source")
        void returnsSource() {
            String source = "TaskABC";

            YDataStateException ex = new YDataStateException(
                null, null, null, null, null, source, "message"
            );

            assertEquals(source, ex.getSource());
        }

        @Test
        @DisplayName("Returns xerces errors")
        void returnsXercesErrors() {
            String errors = "cvc-complex-type.2.4.a: Invalid content";

            YDataStateException ex = new YDataStateException(
                null, null, null, null, errors, "source", "message"
            );

            assertEquals(errors, ex.getErrors());
        }

        @Test
        @DisplayName("Generates XML representation")
        void generatesXmlRepresentation() {
            Element queriedData = new Element("data");
            queriedData.setText("test");
            String source = "TestTask";
            String message = "Test error";

            YDataStateException ex = new YDataStateException(
                "//query", queriedData, null, null, null, source, message
            );

            String xml = ex.toXML();

            assertNotNull(xml);
            assertTrue(xml.contains("YDataStateException"));
            assertTrue(xml.contains("message"));
        }
    }

    // ============================================================
    // YDataQueryException Tests
    // ============================================================

    @Nested
    @DisplayName("YDataQueryException Tests")
    class YDataQueryExceptionTests {

        @Test
        @DisplayName("Creates with query parameters")
        void createsWithQueryParameters() {
            Element data = new Element("data");
            data.setText("query data");
            String queryString = "//task/input";
            String source = "QueryTask";
            String message = "Query failed";

            YDataQueryException ex = new YDataQueryException(queryString, data, source, message);

            assertEquals(queryString, ex.getQueryString());
            assertEquals(data, ex.getData());
        }

        @Test
        @DisplayName("Extends YDataStateException")
        void extendsYDataStateException() {
            YDataQueryException ex = new YDataQueryException(
                "//query", new Element("data"), "source", "message"
            );

            assertTrue(ex instanceof YDataStateException);
            assertTrue(ex instanceof YAWLException);
        }

        @Test
        @DisplayName("Message includes query and source")
        void messageIncludesQueryAndSource() {
            String queryString = "//test/query";
            String source = "MyTask";

            YDataQueryException ex = new YDataQueryException(
                queryString, new Element("data"), source, "Error"
            );

            String message = ex.getMessage();
            assertTrue(message.contains(queryString));
            assertTrue(message.contains(source));
        }
    }

    // ============================================================
    // YDataValidationException Tests
    // ============================================================

    @Nested
    @DisplayName("YDataValidationException Tests")
    class YDataValidationExceptionTests {

        @Test
        @DisplayName("Creates with validation parameters")
        void createsWithValidationParameters() {
            String schema = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>";
            Element dataInput = new Element("input");
            String xercesErrors = "cvc-type.3.1.1";
            String source = "ValidationTask";
            String message = "Schema validation failed";

            YDataValidationException ex = new YDataValidationException(
                schema, dataInput, xercesErrors, source, message
            );

            assertNotNull(ex.getMessage());
        }

        @Test
        @DisplayName("Extends YDataStateException")
        void extendsYDataStateException() {
            YDataValidationException ex = new YDataValidationException(
                "<schema/>", new Element("input"), "error", "source", "message"
            );

            assertTrue(ex instanceof YDataStateException);
            assertTrue(ex instanceof YAWLException);
        }
    }

    // ============================================================
    // YAWLException Base Class Tests
    // ============================================================

    @Nested
    @DisplayName("YAWLException Base Class Tests")
    class YAWLExceptionBaseTests {

        @Test
        @DisplayName("Default constructor creates exception with null message")
        void defaultConstructorCreatesNullException() {
            YAWLException ex = new YAWLException();

            assertNull(ex.getMessage());
        }

        @Test
        @DisplayName("String constructor preserves message")
        void stringConstructorPreservesMessage() {
            YAWLException ex = new YAWLException("Test message");

            assertEquals("Test message", ex.getMessage());
        }

        @Test
        @DisplayName("Throwable constructor preserves cause")
        void throwableConstructorPreservesCause() {
            Exception cause = new RuntimeException("Cause");
            YAWLException ex = new YAWLException(cause);

            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("Message and cause constructor preserves both")
        void messageAndCauseConstructorPreservesBoth() {
            Exception cause = new RuntimeException("Cause");
            YAWLException ex = new YAWLException("Message", cause);

            assertEquals("Message", ex.getMessage());
            assertSame(cause, ex.getCause());
        }

        @Test
        @DisplayName("SetMessage updates message")
        void setMessageUpdatesMessage() {
            YAWLException ex = new YAWLException();

            ex.setMessage("New message");

            assertEquals("New message", ex.getMessage());
        }

        @Test
        @DisplayName("Generates XML representation")
        void generatesXmlRepresentation() {
            YAWLException ex = new YAWLException("Test error");

            String xml = ex.toXML();

            assertNotNull(xml);
            assertTrue(xml.contains("YAWLException"));
            assertTrue(xml.contains("Test error"));
        }
    }

    // ============================================================
    // Exception Chaining Tests
    // ============================================================

    @Nested
    @DisplayName("Exception Chaining Tests")
    class ExceptionChainingTests {

        @Test
        @DisplayName("Multiple level chaining preserves full chain")
        void multipleLevelChainingPreservesFullChain() {
            Exception level1 = new IOException("Disk full");
            Exception level2 = new YPersistenceException(level1);
            Exception level3 = new YStateException("State corrupted", level2);

            assertSame(level2, level3.getCause());
            assertSame(level1, level3.getCause().getCause());
        }

        @Test
        @DisplayName("Chaining with message preserves message")
        void chainingWithMessagePreservesMessage() {
            Exception cause = new RuntimeException("Root cause");
            YStateException ex = new YStateException("State error occurred", cause);

            assertEquals("State error occurred", ex.getMessage());
            assertEquals("Root cause", ex.getCause().getMessage());
        }
    }

    // ============================================================
    // Inheritance Hierarchy Tests
    // ============================================================

    @Nested
    @DisplayName("Inheritance Hierarchy Tests")
    class InheritanceHierarchyTests {

        @Test
        @DisplayName("All core exceptions extend YAWLException")
        void allCoreExceptionsExtendYAWLException() {
            YAWLException[] exceptions = {
                new YPersistenceException(),
                new YStateException(),
                new YAuthenticationException(),
                new YQueryException(),
                new YLogException(),
                new YSchemaBuildingException(),
                new YEngineStateException(),
                new YExternalDataException(),
                new YSyntaxException(),
                new YConnectivityException()
            };

            for (YAWLException ex : exceptions) {
                assertTrue(ex instanceof YAWLException,
                    ex.getClass().getSimpleName() + " should extend YAWLException");
            }
        }

        @Test
        @DisplayName("YConnectivityException extends YSyntaxException")
        void yConnectivityExceptionExtendsYSyntaxException() {
            YConnectivityException ex = new YConnectivityException();

            assertTrue(ex instanceof YSyntaxException);
        }

        @Test
        @DisplayName("Data exceptions extend YDataStateException")
        void dataExceptionsExtendYDataStateException() {
            YDataQueryException queryEx = new YDataQueryException(
                "//q", new Element("d"), "s", "m"
            );
            YDataValidationException validationEx = new YDataValidationException(
                "<s/>", new Element("d"), "e", "s", "m"
            );

            assertTrue(queryEx instanceof YDataStateException);
            assertTrue(validationEx instanceof YDataStateException);
        }
    }

    // ============================================================
    // Problem Class Tests
    // ============================================================

    @Nested
    @DisplayName("Problem Class Tests")
    class ProblemTests {

        @Test
        @DisplayName("Set and get source")
        void setAndGetSource() {
            Problem problem = new Problem();

            problem.setSource("TestComponent");

            assertEquals("TestComponent", problem.getSource());
        }

        @Test
        @DisplayName("Set and get problem time")
        void setAndGetProblemTime() {
            Problem problem = new Problem();
            Instant now = Instant.now();

            problem.setProblemTime(now);

            assertEquals(now, problem.getProblemTime());
        }

        @Test
        @DisplayName("Set and get message type")
        void setAndGetMessageType() {
            Problem problem = new Problem();

            problem.setMessageType(Problem.EMPTY_RESOURCE_SET_MESSAGETYPE);

            assertEquals(Problem.EMPTY_RESOURCE_SET_MESSAGETYPE, problem.getMessageType());
        }

        @Test
        @DisplayName("Set and get message")
        void setAndGetMessage() {
            Problem problem = new Problem();

            problem.setMessage("Empty resource set detected");

            assertEquals("Empty resource set detected", problem.getMessage());
        }

        @Test
        @DisplayName("Equals based on source and time")
        void equalsBasedOnSourceAndTime() {
            Instant time = Instant.now();
            Problem problem1 = new Problem();
            problem1.setSource("A");
            problem1.setProblemTime(time);

            Problem problem2 = new Problem();
            problem2.setSource("A");
            problem2.setProblemTime(time);

            assertEquals(problem1, problem2);
        }

        @Test
        @DisplayName("HashCode based on source and time")
        void hashCodeBasedOnSourceAndTime() {
            Instant time = Instant.now();
            Problem problem1 = new Problem();
            problem1.setSource("A");
            problem1.setProblemTime(time);

            Problem problem2 = new Problem();
            problem2.setSource("A");
            problem2.setProblemTime(time);

            assertEquals(problem1.hashCode(), problem2.hashCode());
        }

        @Test
        @DisplayName("Empty resource set message type constant")
        void emptyResourceSetMessageTypeConstant() {
            assertEquals("EmptyResourceSetType", Problem.EMPTY_RESOURCE_SET_MESSAGETYPE);
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
}
