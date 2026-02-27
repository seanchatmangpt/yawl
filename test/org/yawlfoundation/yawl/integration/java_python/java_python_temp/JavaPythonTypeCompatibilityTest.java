/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Java-Python type compatibility and marshalling.
 * Validates that types can be correctly converted between Java and Python implementations.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
public class JavaPythonTypeCompatibilityTest extends ValidationTestBase {

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        org.junit.jupiter.api.Assumptions.assumeTrue(graalpyAvailable, "GraalPy required for type compatibility tests");
    }

    @Test
    @DisplayName("Primitive type marshalling: int")
    void testPrimitiveIntegerMarshalling() throws Exception {
        String pythonCode = "42";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(Number.class));
        assertThat(((Number) result).intValue(), equalTo(42));
        assertTrue(areEquivalent(42, result));
    }

    @Test
    @DisplayName("Primitive type marshalling: double")
    void testPrimitiveDoubleMarshalling() throws Exception {
        String pythonCode = "3.14159";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(Number.class));
        assertThat(((Number) result).doubleValue(), equalTo(3.14159));
        assertTrue(areEquivalent(3.14159, result));
    }

    @Test
    @DisplayName("Primitive type marshalling: boolean")
    void testPrimitiveBooleanMarshalling() throws Exception {
        String pythonCode = "True";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(Boolean.class));
        assertThat(result, equalTo(true));
        assertTrue(areEquivalent(true, result));
    }

    @Test
    @DisplayName("Primitive type marshalling: string")
    void testPrimitiveStringMarshalling() throws Exception {
        String pythonCode = "\"Hello, YAWL!\"";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(String.class));
        assertThat(result, equalTo("Hello, YAWL!"));
        assertTrue(areEquivalent("Hello, YAWL!", result));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "None"})
    @DisplayName("Null value marshalling")
    void testNullValueMarshalling(String pythonNull) throws Exception {
        String pythonCode = pythonNull;
        Object result = executePythonCode(pythonCode);

        assertNull(result);
        assertTrue(areEquivalent(null, result));

        // Test null handling in collections
        String pythonListWithNull = "[1, " + pythonNull + ", 3]";
        Object listResult = executePythonCode(pythonListWithNull);
        assertThat(listResult, notNullValue());
    }

    @Test
    @DisplayName("Optional type handling: Python None to Java Optional")
    void testOptionalTypeHandling() throws Exception {
        String pythonCode = """
            def get_value(flag):
                if flag:
                    return "some_value"
                else:
                    return None

            value1 = get_value(True)
            value2 = get_value(False)
            """;

        executePythonCode(pythonCode);

        Object result1 = executePythonCode("value1");
        Object result2 = executePythonCode("value2");

        assertThat(result1, notNullValue());
        assertThat(result2, nullValue());
    }

    @Test
    @DisplayName("List type marshalling: List<Integer>")
    void testListIntegerMarshalling() throws Exception {
        String pythonCode = "[1, 2, 3, 4, 5]";
        Object result = executePythonCode(pythonCode);

        assertThat(result, notNullValue());
        assertTrue(areEquivalent(new Object[]{1, 2, 3, 4, 5}, result));
        assertTrue(areEquivalent(5, executePythonCode("len(" + pythonCode + ")")));
    }

    @Test
    @DisplayName("List type marshalling: List<String>")
    void testListStringMarshalling() throws Exception {
        String pythonCode = "['item1', 'item2', 'item3']";
        Object result = executePythonCode(pythonCode);

        assertThat(result, notNullValue());
        assertTrue(areEquivalent(new Object[]{"item1", "item2", "item3"}, result));
        assertTrue(areEquivalent(3, executePythonCode("len(" + pythonCode + ")")));
    }

    @Test
    @DisplayName("Dictionary type marshalling: Map<String, Object>")
    void testDictionaryMarshalling() throws Exception {
        String pythonCode = "{'key1': 'value1', 'key2': 42, 'key3': True}";
        Object result = executePythonCode(pythonCode);

        assertThat(result, notNullValue());
        assertTrue(areEquivalent(3, executePythonCode("len(" + pythonCode + ")")));
        assertTrue(areEquivalent("value1", executePythonCode(pythonCode + "['key1']")));
    }

    @Test
    @DisplayName("Custom object marshalling: MockWorkItem")
    void testCustomObjectMarshalling() throws Exception {
        String pythonCode = """
            class MockWorkItem:
                def __init__(self, id, status, data):
                    self.id = id
                    self.status = status
                    self.data = data

                def __eq__(self, other):
                    return (self.id == other.id and
                           self.status == other.status and
                           self.data == other.data)

                def __str__(self):
                    return f"WorkItem(id={self.id}, status={self.status})"
            """;

        executePythonCode(pythonCode);

        // Test object creation
        executePythonCode("mock_item = MockWorkItem('123', 'pending', {'task': 'validation'})");

        Object result = executePythonCode("mock_item");
        assertThat(result, notNullValue());

        // Test property access
        assertTrue(areEquivalent("123", executePythonCode("mock_item.id")));
        assertTrue(areEquivalent("pending", executePythonCode("mock_item.status")));
        assertTrue(areEquivalent("validation", executePythonCode("mock_item.data['task']")));

        // Test object representation
        assertTrue(areEquivalent("WorkItem(id=123, status=pending)",
                                executePythonCode("str(mock_item)")));
    }

    @Test
    @DisplayName("Custom object inheritance: Base class")
    void testCustomObjectInheritance() throws Exception {
        String pythonCode = """
            class BaseTask:
                def __init__(self, id, name):
                    self.id = id
                    self.name = name

            class WorkItem(BaseTask):
                def __init__(self, id, name, status):
                    super().__init__(id, name)
                    self.status = status

            task = WorkItem('task-123', 'Process Order', 'active')
            """;

        executePythonCode(pythonCode);
        executePythonCode("base_task = BaseTask('base-123', 'Basic Task')");

        // Test inheritance properties
        assertTrue(areEquivalent('task-123', executePythonCode("task.id")));
        assertTrue(areEquivalent('Process Order', executePythonCode("task.name")));
        assertTrue(areEquivalent('active', executePythonCode("task.status")));
        assertTrue(areEquivalent('base-123', executePythonCode("base_task.id")));
    }

    @Test
    @DisplayName("Exception type marshalling: ValueError")
    void testExceptionMarshalling() throws Exception {
        String pythonCode = """
            try:
                raise ValueError("Test error")
            except ValueError as e:
                error_obj = e
            except Exception as e:
                error_obj = e  # Fallback
            """;

        executePythonCode(pythonCode);
        Object result = executePythonCode("error_obj");

        assertThat(result, notNullValue());
        assertTrue(areEquivalent("Test error", executePythonCode("str(error_obj)")));
        assertTrue(areEquivalent("ValueError", executePythonCode("type(error_obj).__name__")));

        // Test that exception is still an exception when accessed
        assertTrue(areEquivalent(True, executePythonCode("isinstance(error_obj, Exception)")));
    }

    @Test
    @DisplayName("Exception handling in Python: try-catch blocks")
    void testExceptionHandling() throws Exception {
        String pythonCode = """
            def divide(a, b):
                try:
                    result = a / b
                except ZeroDivisionError:
                    result = "Cannot divide by zero"
                return result

            result1 = divide(10, 2)
            result2 = divide(10, 0)
            """;

        executePythonCode(pythonCode);

        assertTrue(areEquivalent(5.0, executePythonCode("result1")));
        assertTrue(areEquivalent("Cannot divide by zero", executePythonCode("result2")));
    }

    @Test
    @DisplayName("Performance benchmark: Type marshalling overhead")
    void testTypeMarshallingPerformance() throws Exception {
        String[] testCases = {
            "42",  // int
            "3.14",  // double
            "True",  // boolean
            "\"test\"",  // string
            "[1, 2, 3, 4, 5]",  // list
            "{'key': 'value'}"  // dict
        };

        for (String testCase : testCases) {
            long executionTime = benchmarkExecution(() -> {
                try {
                    executePythonCode(testCase);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 1000);

            assertThat("Type marshalling for " + testCase, executionTime, lessThan(10L));
        }
    }

    @Test
    @DisplayName("Type consistency across multiple calls")
    void testTypeConsistency() throws Exception {
        String pythonCode = "42";

        Object result1 = executePythonCode(pythonCode);
        Object result2 = executePythonCode(pythonCode);
        Object result3 = executePythonCode(pythonCode);

        assertThat(result1, instanceOf(result2.getClass()));
        assertThat(result2, instanceOf(result3.getClass()));
        assertThat(result1, equalTo(result2));
        assertThat(result2, equalTo(result3));
    }

    @Test
    @DisplayName("Dynamic type behavior: Type changing values")
    void testDynamicTypeBehavior() throws Exception {
        String pythonCode = """
            value = 42      # int
            value = "hello"  # str
            value = 3.14    # float
            value = True    # bool
            """;

        executePythonCode(pythonCode);

        // Check the final type
        assertTrue(areEquivalent(True, executePythonCode("type(value).__name__ == 'bool'")));
    }

    @Test
    @DisplayName("Unicode string marshalling")
    void testUnicodeStringMarshalling() throws Exception {
        String pythonCode = "'Hello, 世界! 🎉'";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(String.class));
        assertThat(result, equalTo("Hello, 世界! 🎉"));
        assertTrue(areEquivalent("Hello, 世界! 🎉", result));
    }

    @Test
    @DisplayName("Large data type marshalling")
    void testLargeDataMarshalling() throws Exception {
        // Generate large string
        StringBuilder largeString = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeString.append("data_").append(i).append("_");
        }

        String pythonCode = "\"" + largeString.toString() + "\"";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(String.class));
        assertThat(((String) result).length(), equalTo(largeString.length()));
        assertThat(result, equalTo(largeString.toString()));
    }

    @Test
    @DisplayName("Nested object marshalling")
    void testNestedObjectMarshalling() throws Exception {
        String pythonCode = """
            class Inner:
                def __init__(self, value):
                    self.value = value

            class Outer:
                def __init__(self, inner):
                    self.inner = inner

            inner = Inner(42)
            outer = Outer(inner)
            """;

        executePythonCode(pythonCode);

        assertThat(executePythonCode("outer.inner.value"), equalTo(42));
        assertThat(executePythonCode("outer.inner.__class__.__name__"), equalTo("Inner"));
        assertThat(executePythonCode("outer.__class__.__name__"), equalTo("Outer"));
    }

    @Test
    @DisplayName("Circular reference handling")
    void testCircularReferenceHandling() throws Exception {
        String pythonCode = """
            class Node:
                def __init__(self, value):
                    self.value = value
                    self.next = None

            node1 = Node(1)
            node2 = Node(2)
            node1.next = node2
            node2.next = node1  # Create circular reference
            """;

        // This should not cause infinite recursion
        assertDoesNotThrow(() -> executePythonCode("node1"));
    }

    @Test
    @DisplayName("Type conversion precision")
    void testTypeConversionPrecision() throws Exception {
        // Test floating point precision
        String pythonCode = "3.14159265358979323846";
        Object result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(Double.class));
        assertThat((Double) result, equalTo(3.141592653589793));

        // Test large integer preservation
        pythonCode = "12345678901234567890";
        result = executePythonCode(pythonCode);

        assertThat(result, instanceOf(Number.class));
        assertThat(((Number) result).longValue(), equalTo(12345678901234567890L));
    }

    // Test data provider for parameterized tests
    private static Stream<CompatibilityTestCase> testData() {
        return Stream.of(
            new CompatibilityTestCase("42", 42),
            new CompatibilityTestCase("3.14", 3.14),
            new CompatibilityTestCase("True", true),
            new CompatibilityTestCase("'test'", "test"),
            new CompatibilityTestCase("[1, 2, 3]", new Object[]{1, 2, 3}),
            new CompatibilityTestCase("{'a': 1, 'b': 2}", "map")
        );
    }

    @ParameterizedTest
    @MethodSource("testData")
    @DisplayName("Parameterized type compatibility test")
    void testTypeCompatibility(CompatibilityTestCase testCase) throws Exception {
        Object result = executePythonCode(testCase.pythonCode());

        switch (testCase.expectedType()) {
            case INTEGER:
                assertThat(result, instanceOf(Number.class));
                assertThat(((Number) result).intValue(), equalTo((Integer) testCase.expectedValue()));
                break;
            case DOUBLE:
                assertThat(result, instanceOf(Number.class));
                assertThat(((Number) result).doubleValue(), equalTo((Double) testCase.expectedValue()));
                break;
            case BOOLEAN:
                assertThat(result, instanceOf(Boolean.class));
                assertThat(result, equalTo(testCase.expectedValue()));
                break;
            case STRING:
                assertThat(result, instanceOf(String.class));
                assertThat(result, equalTo(testCase.expectedValue()));
                break;
            case LIST:
                assertThat(result, notNullValue());
                break;
            case MAP:
                assertThat(result, notNullValue());
                break;
        }
    }

    /**
     * Test case data class for parameterized tests
     */
    public record CompatibilityTestCase(
        String pythonCode,
        Object expectedValue,
        ExpectedType expectedType
    ) {
        public CompatibilityTestCase(String pythonCode, Object expectedValue) {
            this(pythonCode, expectedValue, determineExpectedType(expectedValue));
        }

        private static ExpectedType determineExpectedType(Object value) {
            if (value instanceof Integer) return ExpectedType.INTEGER;
            if (value instanceof Double) return ExpectedType.DOUBLE;
            if (value instanceof Boolean) return ExpectedType.BOOLEAN;
            if (value instanceof String) return ExpectedType.STRING;
            if (value.getClass().isArray()) return ExpectedType.LIST;
            return ExpectedType.MAP;
        }
    }

    /**
     * Enum for expected types
     */
    public enum ExpectedType {
        INTEGER, DOUBLE, BOOLEAN, STRING, LIST, MAP
    }
}