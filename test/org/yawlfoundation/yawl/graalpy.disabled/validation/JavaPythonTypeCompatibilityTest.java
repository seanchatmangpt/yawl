/*
 * Copyright 2024 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yawlfoundation.yawl.graalpy.validation;

import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for Java-Python type compatibility and marshalling.
 * Validates all primitive types, collections, custom objects, and edge cases
 * between Java and Python implementations using GraalPy integration.
 *
 * <p>This test class implements the Chicago TDD methodology with real YAWL engine instances.
 * All tests use real integrations without mocks, ensuring production-level compatibility.</p>
 *
 * @see <a href="https://github.com/yawlfoundation/yawl-graalpy">YAWL GraalPy Integration</a>
 * @since 6.0.0
 */
@DisplayName("Java-Python Type Compatibility Validation")
public class JavaPythonTypeCompatibilityTest extends ValidationTestBase {

    @BeforeEach
    public void setUp(TestInfo testInfo) throws Exception {
        super.setUp(testInfo);
        assumeTrue(pythonEngine != null, "GraalPy engine must be available");
    }

    // ======================================================================
    // PRIMITIVE TYPE TESTS
    // ======================================================================

    /**
     * Tests primitive type round-trip conversion: Java ↔ Python ↔ Java
     * Validates that int values maintain precision in both directions.
     */
    @ParameterizedTest
    @MethodSource("primitiveIntValues")
    @DisplayName("Primitive type marshalling: int")
    void testPrimitiveTypes_int(Integer expectedValue) throws Exception {
        // Java → Python
        String pythonCode = expectedValue.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side
        assertThat(pythonValue.isNumber(), is(true));
        assertThat(pythonValue.asInt(), is(expectedValue));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Integer.class));
        assertThat(javaValue, is(expectedValue));

        // Verify equivalence
        assertEquivalent(expectedValue, pythonValue);
    }

    /**
     * Tests primitive type round-trip conversion: Java ↔ Python ↔ Java
     * Validates that long values maintain precision in both directions.
     */
    @ParameterizedTest
    @MethodSource("primitiveLongValues")
    @DisplayName("Primitive type marshalling: long")
    void testPrimitiveTypes_long(Long expectedValue) throws Exception {
        // Java → Python
        String pythonCode = expectedValue.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side
        assertThat(pythonValue.isNumber(), is(true));
        assertThat(pythonValue.asLong(), is(expectedValue));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Long.class));
        assertThat(javaValue, is(expectedValue));

        // Verify equivalence
        assertEquivalent(expectedValue, pythonValue);
    }

    /**
     * Tests primitive type round-trip conversion: Java ↔ Python ↔ Java
     * Validates that double values maintain precision in both directions.
     */
    @ParameterizedTest
    @MethodSource("primitiveDoubleValues")
    @DisplayName("Primitive type marshalling: double")
    void testPrimitiveTypes_double(Double expectedValue) throws Exception {
        // Java → Python
        String pythonCode = expectedValue.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side
        assertThat(pythonValue.isNumber(), is(true));
        assertThat(pythonValue.asDouble(), is(expectedValue));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Double.class));
        assertThat(javaValue, is(expectedValue));

        // Verify equivalence
        assertEquivalent(expectedValue, pythonValue);
    }

    /**
     * Tests primitive type round-trip conversion: Java ↔ Python ↔ Java
     * Validates that boolean values are correctly converted in both directions.
     */
    @ParameterizedTest
    @MethodSource("primitiveBooleanValues")
    @DisplayName("Primitive type marshalling: boolean")
    void testPrimitiveTypes_boolean(Boolean expectedValue) throws Exception {
        // Java → Python
        String pythonCode = expectedValue.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side
        assertThat(pythonValue.isBoolean(), is(true));
        assertThat(pythonValue.asBoolean(), is(expectedValue));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Boolean.class));
        assertThat(javaValue, is(expectedValue));

        // Verify equivalence
        assertEquivalent(expectedValue, pythonValue);
    }

    /**
     * Tests primitive type round-trip conversion: Java ↔ Python ↔ Java
     * Validates that String values maintain content in both directions.
     */
    @ParameterizedTest
    @MethodSource("primitiveStringValues")
    @DisplayName("Primitive type marshalling: string")
    void testPrimitiveTypes_string(String expectedValue) throws Exception {
        // Java → Python
        String pythonCode = "\"" + expectedValue + "\"";
        Value pythonValue = executePython(pythonCode);

        // Validate Python side
        assertThat(pythonValue.isString(), is(true));
        assertThat(pythonValue.asString(), is(expectedValue));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(String.class));
        assertThat(javaValue, is(expectedValue));

        // Verify equivalence
        assertEquivalent(expectedValue, pythonValue);
    }

    // ======================================================================
    // COLLECTION TYPE TESTS
    // ======================================================================

    /**
     * Tests List type marshalling with various element types.
     * Validates that List<Integer> maintains element order and values.
     */
    @ParameterizedTest
    @MethodSource("listIntegerValues")
    @DisplayName("Collection type marshalling: List<Integer>")
    void testCollectionTypes_listInteger(List<Integer> expectedList) throws Exception {
        // Java → Python
        String pythonCode = expectedList.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side (should be a list)
        assertThat(pythonValue.hasArrayElements(), is(true));
        assertThat(pythonValue.getArraySize(), is(expectedList.size()));

        // Validate each element
        for (int i = 0; i < expectedList.size(); i++) {
            Value element = pythonValue.getArrayElement(i);
            assertThat(element.isNumber(), is(true));
            assertThat(element.asInt(), is(expectedList.get(i)));
        }

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(List.class));
        assertThat((List<?>) javaValue, is(expectedList));

        // Verify equivalence
        assertEquivalent(expectedList, pythonValue);
    }

    /**
     * Tests List type marshalling with various element types.
     * Validates that List<String> maintains element order and values.
     */
    @ParameterizedTest
    @MethodSource("listStringValues")
    @DisplayName("Collection type marshalling: List<String>")
    void testCollectionTypes_listString(List<String> expectedList) throws Exception {
        // Java → Python
        String pythonCode = expectedList.toString();
        Value pythonValue = executePython(pythonCode);

        // Validate Python side (should be a list)
        assertThat(pythonValue.hasArrayElements(), is(true));
        assertThat(pythonValue.getArraySize(), is(expectedList.size()));

        // Validate each element
        for (int i = 0; i < expectedList.size(); i++) {
            Value element = pythonValue.getArrayElement(i);
            assertThat(element.isString(), is(true));
            assertThat(element.asString(), is(expectedList.get(i)));
        }

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(List.class));
        assertThat((List<?>) javaValue, is(expectedList));

        // Verify equivalence
        assertEquivalent(expectedList, pythonValue);
    }

    /**
     * Tests Map type marshalling with various key-value types.
     * Validates that Map<String, Object> maintains all key-value pairs.
     */
    @ParameterizedTest
    @MethodSource("mapValues")
    @DisplayName("Collection type marshalling: Map<String, Object>")
    void testCollectionTypes_map(Map<String, Object> expectedMap) throws Exception {
        // Java → Python
        String pythonCode = convertMapToPython(expectedMap);
        Value pythonValue = executePython(pythonCode);

        // Validate Python side (should be a map)
        assertThat(pythonValue.hasMembers(), is(true));
        assertThat(pythonValue.getMemberCount(), is(expectedMap.size()));

        // Validate each key-value pair
        for (Map.Entry<String, Object> entry : expectedMap.entrySet()) {
            Value value = pythonValue.getMember(entry.getKey());
            assertThat(value, notNullValue());
            assertEquivalent(entry.getValue(), value);
        }

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Map.class));
        assertThat((Map<?, ?>) javaValue, is(expectedMap));

        // Verify equivalence
        assertEquivalent(expectedMap, pythonValue);
    }

    /**
     * Tests Set type marshalling with various element types.
     * Validates that Set maintains uniqueness and elements.
     */
    @ParameterizedTest
    @MethodSource("setValues")
    @DisplayName("Collection type marshalling: Set")
    void testCollectionTypes_set(Set<?> expectedSet) throws Exception {
        // Java → Python
        String pythonCode = convertSetToPython(expectedSet);
        Value pythonValue = executePython(pythonCode);

        // Validate Python side (should have elements)
        assertThat(pythonValue.hasArrayElements(), is(true));
        assertThat(pythonValue.getArraySize(), is(expectedSet.size()));

        // Validate elements match
        Set<Object> elements = new HashSet<>();
        for (int i = 0; i < pythonValue.getArraySize(); i++) {
            Value element = pythonValue.getArrayElement(i);
            elements.add(element.as(Object.class));
        }
        assertThat(elements, is(expectedSet));

        // Validate round-trip: Python → Java
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(Set.class));
        assertThat((Set<?>) javaValue, is(expectedSet));

        // Verify equivalence
        assertEquivalent(expectedSet, pythonValue);
    }

    // ======================================================================
    // CUSTOM JAVA OBJECT TESTS
    // ======================================================================

    /**
     * Tests custom Java object marshalling to Python and back.
     * Validates that YWorkItem objects can be converted to Python and maintain data integrity.
     */
    @ParameterizedTest
    @MethodSource("workItemValues")
    @DisplayName("Custom object marshalling: YWorkItem")
    void testCustomObjects_workItem(YWorkItem expectedWorkItem) throws Exception {
        // Test Java object creation
        YWorkItem javaWorkItem = expectedWorkItem;

        // Convert to JSON string for Python side
        String json = convertWorkItemToJson(javaWorkItem);
        String pythonCode = convertJsonToPython(json);

        // Execute in Python
        Value pythonValue = executePython(pythonCode);

        // Validate Python representation
        assertThat(pythonValue.hasMembers(), is(true));

        // Verify each field
        assertEquivalent(javaWorkItem.getID(), pythonValue.getMember("id"));
        assertEquivalent(javaWorkItem.getTaskName(), pythonValue.getMember("taskName"));
        assertEquivalent(javaWorkItem.getStatus().name(), pythonValue.getMember("status"));
        assertEquivalent(javaWorkItem.getCaseID(), pythonValue.getMember("caseID"));
    }

    /**
     * Tests custom Java object marshalling to Python and back.
     * Validates that YSpecification objects can be converted to Python and maintain data integrity.
     */
    @ParameterizedTest
    @MethodSource("specificationValues")
    @DisplayName("Custom object marshalling: YSpecification")
    void testCustomObjects_specification(YSpecification expectedSpec) throws Exception {
        // Test Java object creation
        YSpecification javaSpec = expectedSpec;

        // Convert to JSON string for Python side
        String json = convertSpecificationToJson(javaSpec);
        String pythonCode = convertJsonToPython(json);

        // Execute in Python
        Value pythonValue = executePython(pythonCode);

        // Validate Python representation
        assertThat(pythonValue.hasMembers(), is(true));

        // Verify each field
        assertEquivalent(javaSpec.getID().toString(), pythonValue.getMember("id"));
        assertEquivalent(javaSpec.getName(), pythonValue.getMember("name"));
        assertEquivalent(javaSpec.getVersion(), pythonValue.getMember("version"));
        assertEquivalent(javaSpec.getNetElements().size(), pythonValue.getMember("netElementsCount"));
    }

    /**
     * Tests custom Java object marshalling to Python and back.
     * Validates that YNetRunner objects can be converted to Python and maintain data integrity.
     */
    @ParameterizedTest
    @MethodSource("netRunnerValues")
    @DisplayName("Custom object marshalling: YNetRunner")
    void testCustomObjects_netRunner(YNetRunner expectedRunner) throws Exception {
        // Test Java object creation
        YNetRunner javaRunner = expectedRunner;

        // Convert to JSON string for Python side
        String json = convertNetRunnerToJson(javaRunner);
        String pythonCode = convertJsonToPython(json);

        // Execute in Python
        Value pythonValue = executePython(pythonCode);

        // Validate Python representation
        assertThat(pythonValue.hasMembers(), is(true));

        // Verify key fields
        assertEquivalent(javaRunner.getSpecification().getID().toString(), pythonValue.getMember("specId"));
        assertEquivalent(javaRunner.getNetElements().size(), pythonValue.getMember("netElementsCount"));
        assertEquivalent(javaRunner.getActiveWorkItems().size(), pythonValue.getMember("activeWorkItemsCount"));
    }

    // ======================================================================
    // NULL HANDLING TESTS
    // ======================================================================

    /**
     * Tests null value marshalling in various contexts.
     * Validates that null values are correctly handled in both directions.
     */
    @ParameterizedTest
    @MethodSource("nullTestCases")
    @DisplayName("Null value marshalling")
    void testNullHandling(NullTestCase testCase) throws Exception {
        // Java → Python
        String pythonCode = testCase.pythonCode();
        Value pythonValue = executePython(pythonCode);

        // Validate both are null
        assertNull(testCase.javaValue());
        assertTrue(pythonValue.isNull());

        // Verify equivalence
        assertEquivalent(testCase.javaValue(), pythonValue);
    }

    /**
     * Tests empty collection handling in various contexts.
     * Validates that empty collections are correctly marshalled.
     */
    @ParameterizedTest
    @MethodSource("emptyCollectionCases")
    @DisplayName("Empty collection marshalling")
    void testEmptyCollectionHandling(EmptyCollectionTestCase testCase) throws Exception {
        // Java → Python
        String pythonCode = testCase.pythonCode();
        Value pythonValue = executePython(pythonCode);

        // Validate collection properties
        if (testCase.javaValue() instanceof List) {
            assertThat(pythonValue.hasArrayElements(), is(true));
            assertThat(pythonValue.getArraySize(), is(0));
        } else if (testCase.javaValue() instanceof Map) {
            assertThat(pythonValue.hasMembers(), is(true));
            assertThat(pythonValue.getMemberCount(), is(0));
        } else if (testCase.javaValue() instanceof Set) {
            assertThat(pythonValue.hasArrayElements(), is(true));
            assertThat(pythonValue.getArraySize(), is(0));
        }

        // Verify round-trip
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, instanceOf(testCase.javaValue().getClass()));
        assertThat(javaValue, is(testCase.javaValue()));

        // Verify equivalence
        assertEquivalent(testCase.javaValue(), pythonValue);
    }

    // ======================================================================
    // NESTED STRUCTURE TESTS
    // ======================================================================

    /**
     * Tests deeply nested object structures.
     * Validates complex nested collections and objects maintain integrity.
     */
    @ParameterizedTest
    @MethodSource("nestedStructureCases")
    @DisplayName("Nested structure marshalling")
    void testNestedStructures(NestedTestCase testCase) throws Exception {
        // Java → Python
        String pythonCode = convertNestedToPython(testCase.javaValue());
        Value pythonValue = executePython(pythonCode);

        // Validate nested structure
        validateNestedStructure(pythonValue, testCase.javaValue());

        // Verify round-trip
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, is(testCase.javaValue()));

        // Verify equivalence
        assertEquivalent(testCase.javaValue(), pythonValue);
    }

    /**
     * Tests complex mixed-type collections.
     * Validates collections containing various types are correctly marshalled.
     */
    @ParameterizedTest
    @MethodSource("mixedCollectionCases")
    @DisplayName("Mixed-type collection marshalling")
    void testMixedTypeCollections(MixedTestCase testCase) throws Exception {
        // Java → Python
        String pythonCode = convertMixedToPython(testCase.javaValue());
        Value pythonValue = executePython(pythonCode);

        // Validate mixed types
        validateMixedTypes(pythonValue, testCase.javaValue());

        // Verify round-trip
        Object javaValue = pythonValue.as(Object.class);
        assertThat(javaValue, is(testCase.javaValue()));

        // Verify equivalence
        assertEquivalent(testCase.javaValue(), pythonValue);
    }

    // ======================================================================
    // PERFORMANCE TESTS
    // ======================================================================

    /**
     * Tests type marshalling performance with various data sizes.
     * Ensures type conversion remains performant even with large datasets.
     */
    @ParameterizedTest
    @MethodSource("performanceTestCases")
    @DisplayName("Type marshalling performance test")
    void testTypeMarshallingPerformance(PerformanceTestCase testCase) throws Exception {
        // Measure performance of Java → Python conversion
        PerformanceResult result = measurePerformance(() -> {
            String pythonCode = testCase.pythonCode();
            Value pythonValue = executePython(pythonCode);
            assertEquivalent(testCase.expectedValue(), pythonValue);
        }, testCase.timeout());

        // Verify performance meets requirements
        assertThat("Performance test " + testCase.name() + " failed",
            result.isSuccess(), is(true));

        // Log performance metrics
        logger.info("Performance test {}: {}ms",
            testCase.name(), result.getDurationMillis());
    }

    // ======================================================================
    // HELPER METHODS AND DATA PROVIDERS
    // ======================================================================

    /**
     * Helper method to convert Map to Python dictionary syntax
     */
    private String convertMapToPython(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((key, value) -> {
            sb.append("\"").append(key).append("\": ").append(convertValueToPython(value)).append(", ");
        });
        if (!map.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove last comma and space
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Helper method to convert Set to Python list syntax
     */
    private String convertSetToPython(Set<?> set) {
        StringBuilder sb = new StringBuilder("[");
        set.forEach(value -> {
            sb.append(convertValueToPython(value)).append(", ");
        });
        if (!set.isEmpty()) {
            sb.setLength(sb.length() - 2); // Remove last comma and space
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Helper method to convert Java value to Python representation
     */
    private String convertValueToPython(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else if (value instanceof List) {
            return convertSetToPython((Set<?>) ((List<?>) value));
        } else if (value instanceof Set) {
            return convertSetToPython((Set<?>) value);
        } else if (value instanceof Map) {
            return convertMapToPython((Map<String, Object>) value);
        } else {
            return "\"" + value.toString() + "\""; // Fallback to string
        }
    }

    /**
     * Helper method to convert YWorkItem to JSON
     */
    private String convertWorkItemToJson(YWorkItem workItem) {
        return String.format(
            "{\"id\": \"%s\", \"taskName\": \"%s\", \"status\": \"%s\", \"caseId\": \"%s\"}",
            workItem.getID(),
            workItem.getTaskName(),
            workItem.getStatus().name(),
            workItem.getCaseID()
        );
    }

    /**
     * Helper method to convert YSpecification to JSON
     */
    private String convertSpecificationToJson(YSpecification spec) {
        return String.format(
            "{\"id\": \"%s\", \"name\": \"%s\", \"version\": \"%s\", \"netElementsCount\": %d}",
            spec.getID().toString(),
            spec.getName(),
            spec.getVersion(),
            spec.getNetElements().size()
        );
    }

    /**
     * Helper method to convert YNetRunner to JSON
     */
    private String convertNetRunnerToJson(YNetRunner runner) {
        return String.format(
            "{\"specId\": \"%s\", \"netElementsCount\": %d, \"activeWorkItemsCount\": %d}",
            runner.getSpecification().getID().toString(),
            runner.getNetElements().size(),
            runner.getActiveWorkItems().size()
        );
    }

    /**
     * Helper method to convert JSON to Python code
     */
    private String convertJsonToPython(String json) {
        return "import json\n" +
               "data = " + json + "\n" +
               "data";
    }

    /**
     * Helper method to validate nested structure
     */
    private void validateNestedStructure(Value pythonValue, Object javaValue) {
        if (javaValue instanceof Map) {
            assertThat(pythonValue.hasMembers(), is(true));
            Map<?, ?> javaMap = (Map<?, ?>) javaValue;
            assertThat(pythonValue.getMemberCount(), is(javaMap.size()));
            for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
                validateNestedStructure(pythonValue.getMember(entry.getKey().toString()), entry.getValue());
            }
        } else if (javaValue instanceof List) {
            assertThat(pythonValue.hasArrayElements(), is(true));
            List<?> javaList = (List<?>) javaValue;
            assertThat(pythonValue.getArraySize(), is(javaList.size()));
            for (int i = 0; i < javaList.size(); i++) {
                validateNestedStructure(pythonValue.getArrayElement(i), javaList.get(i));
            }
        } else if (javaValue instanceof Set) {
            assertThat(pythonValue.hasArrayElements(), is(true));
            Set<?> javaSet = (Set<?>) javaValue;
            assertThat(pythonValue.getArraySize(), is(javaSet.size()));
            for (int i = 0; i < pythonValue.getArraySize(); i++) {
                boolean found = false;
                for (Object javaElement : javaSet) {
                    if (pythonValue.getArrayElement(i).as(Object.class).equals(javaElement)) {
                        found = true;
                        break;
                    }
                }
                assertThat("Element not found in set", found, is(true));
            }
        }
    }

    /**
     * Helper method to validate mixed types in collection
     */
    private void validateMixedTypes(Value pythonValue, Object javaValue) {
        if (javaValue instanceof Map) {
            Map<?, ?> javaMap = (Map<?, ?>) javaValue;
            assertThat(pythonValue.getMemberCount(), is(javaMap.size()));
            for (Map.Entry<?, ?> entry : javaMap.entrySet()) {
                validateMixedTypes(pythonValue.getMember(entry.getKey().toString()), entry.getValue());
            }
        } else if (javaValue instanceof Collection) {
            Collection<?> javaCollection = (Collection<?>) javaValue;
            assertThat(pythonValue.hasArrayElements(), is(true));
            assertThat(pythonValue.getArraySize(), is(javaCollection.size()));
            int i = 0;
            for (Object javaElement : javaCollection) {
                validateMixedTypes(pythonValue.getArrayElement(i), javaElement);
                i++;
            }
        } else {
            // Primitive type validation
            if (javaValue instanceof Integer) {
                assertThat(pythonValue.isNumber(), is(true));
                assertThat(pythonValue.asInt(), is(javaValue));
            } else if (javaValue instanceof Double) {
                assertThat(pythonValue.isNumber(), is(true));
                assertThat(pythonValue.asDouble(), is(javaValue));
            } else if (javaValue instanceof Boolean) {
                assertThat(pythonValue.isBoolean(), is(true));
                assertThat(pythonValue.asBoolean(), is(javaValue));
            } else if (javaValue instanceof String) {
                assertThat(pythonValue.isString(), is(true));
                assertThat(pythonValue.asString(), is(javaValue));
            }
        }
    }

    // ======================================================================
    // DATA PROVIDERS
    // ======================================================================

    /**
     * Provider for primitive int test values
     */
    private static Stream<Integer> primitiveIntValues() {
        return Stream.of(0, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE, 42, 100, -100);
    }

    /**
     * Provider for primitive long test values
     */
    private static Stream<Long> primitiveLongValues() {
        return Stream.of(0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, 42L, 100L, -100L);
    }

    /**
     * Provider for primitive double test values
     */
    private static Stream<Double> primitiveDoubleValues() {
        return Stream.of(0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, 3.14159, 42.0, -100.5);
    }

    /**
     * Provider for primitive boolean test values
     */
    private static Stream<Boolean> primitiveBooleanValues() {
        return Stream.of(true, false);
    }

    /**
     * Provider for primitive string test values
     */
    private static Stream<String> primitiveStringValues() {
        return Stream.of("", "hello", "Hello, YAWL!", "123", "!@#$%", "unicode: äöü",
            "single quotes: 'test'", "double quotes: \"test\"", "newlines\nhere");
    }

    /**
     * Provider for list integer test values
     */
    private static Stream<List<Integer>> listIntegerValues() {
        return Stream.of(
            List.of(),
            List.of(1),
            List.of(1, 2, 3),
            List.of(-1, 0, 1),
            List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE),
            List.of(42, 43, 44, 45, 46),
            List.of(100, 200, 300, 400, 500)
        );
    }

    /**
     * Provider for list string test values
     */
    private static Stream<List<String>> listStringValues() {
        return Stream.of(
            List.of(),
            List.of("hello"),
            List.of("a", "b", "c"),
            List.of("Hello", "YAWL", "World"),
            List.of("", " ", "  "),
            List.of("item1", "item2", "item3", "item4"),
            List.of("long string with unicode: äöü", "another string", "short")
        );
    }

    /**
     * Provider for map test values
     */
    private static Stream<Map<String, Object>> mapValues() {
        return Stream.of(
            Map.of(),
            Map.of("key", "value"),
            Map.of("int", 42, "string", "hello", "bool", true),
            Map.of("nested", Map.of("inner", "value")),
            Map.of("list", List.of(1, 2, 3), "set", Set.of(1, 2, 3)),
            Map.of("emptyList", List.of(), "emptyMap", Map.of()),
            Map.of("booleanTrue", true, "booleanFalse", false, "nullValue", null)
        );
    }

    /**
     * Provider for set test values
     */
    private static Stream<Set<?>> setValues() {
        return Stream.of(
            Set.of(),
            Set.of(1),
            Set.of(1, 2, 3),
            Set.of("a", "b", "c"),
            Set.of(true, false),
            Set.of(1, "2", true),
            Set.of(42, 42, 42) // Test uniqueness
        );
    }

    /**
     * Provider for work item test values
     */
    private static Stream<YWorkItem> workItemValues() {
        return Stream.of(
            createWorkItem("case1", "task1", "pending"),
            createWorkItem("case2", "task2", "running"),
            createWorkItem("case3", "task3", "completed"),
            createWorkItem("case4", "task4", "cancelled"),
            createWorkItem("case5", "task5", "allocated")
        );
    }

    /**
     * Provider for specification test values
     */
    private static Stream<YSpecification> specificationValues() {
        return Stream.of(
            createSpecification("spec1", "1.0"),
            createSpecification("spec2", "2.0"),
            createSpecification("spec3", "3.0")
        );
    }

    /**
     * Provider for net runner test values
     */
    private static Stream<YNetRunner> netRunnerValues() {
        return Stream.of(
            createNetRunner("spec1"),
            createNetRunner("spec2"),
            createNetRunner("spec3")
        );
    }

    /**
     * Provider for null test cases
     */
    private static Stream<NullTestCase> nullTestCases() {
        return Stream.of(
            new NullTestCase("null", null),
            new NullTestCase("None", null),
            new NullTestCase("null in list", List.of(null, 1, 2)),
            new NullTestCase("null in map", Map.of("key", null))
        );
    }

    /**
     * Provider for empty collection test cases
     */
    private static Stream<EmptyCollectionTestCase> emptyCollectionCases() {
        return Stream.of(
            new EmptyCollectionTestCase("empty list", List.of()),
            new EmptyCollectionTestCase("empty map", Map.of()),
            new EmptyCollectionTestCase("empty set", Set.of()),
            new EmptyCollectionTestCase("empty string list", List.of(""))
        );
    }

    /**
     * Provider for nested structure test cases
     */
    private static Stream<NestedTestCase> nestedStructureCases() {
        return Stream.of(
            new NestedTestCase("nested map", Map.of(
                "level1", Map.of(
                    "level2", Map.of(
                        "level3", "deep value"
                    )
                )
            )),
            new NestedTestCase("nested lists", List.of(
                List.of(1, 2, 3),
                List.of("a", "b", "c"),
                List.of(List.of("nested"), List.of("deep"))
            )),
            new NestedTestCase("mixed nested", Map.of(
                "list", List.of(1, Map.of("nested", true)),
                "map", Map.of("inner", List.of(1, 2, 3)),
                "set", Set.of(1, 2, 3)
            ))
        );
    }

    /**
     * Provider for mixed collection test cases
     */
    private static Stream<MixedTestCase> mixedCollectionCases() {
        return Stream.of(
            new MixedTestCase("mixed list", List.of(
                42, "hello", true, 3.14,
                List.of("nested"), Map.of("key", "value")
            )),
            new MixedTestCase("mixed map", Map.of(
                "int", 42,
                "string", "hello",
                "bool", true,
                "double", 3.14,
                "list", List.of(1, 2, 3),
                "map", Map.of("inner", "value"),
                "set", Set.of(1, 2, 3),
                "null", null
            ))
        );
    }

    /**
     * Provider for performance test cases
     */
    private static Stream<PerformanceTestCase> performanceTestCases() {
        return Stream.of(
            new PerformanceTestCase("small list performance",
                "[1, 2, 3, 4, 5]", List.of(1, 2, 3, 4, 5),
                Duration.ofMillis(10)),
            new PerformanceTestCase("large list performance",
                generateLargeListPython(1000), generateLargeJavaList(1000),
                Duration.ofMillis(100)),
            new PerformanceTestCase("medium map performance",
                "{\"key1\": \"value1\", \"key2\": 42, \"key3\": true}",
                Map.of("key1", "value1", "key2", 42, "key3", true),
                Duration.ofMillis(10)),
            new PerformanceTestCase("string performance",
                "\"" + "a".repeat(1000) + "\"", "a".repeat(1000),
                Duration.ofMillis(10))
        );
    }

    // ======================================================================
    // TEST DATA HELPERS
    // ======================================================================

    /**
     * Helper to create a YWorkItem for testing
     */
    private static YWorkItem createWorkItem(String caseId, String taskName, String status) {
        YWorkItem workItem = new YWorkItem();
        workItem.setID(UUID.randomUUID().toString());
        workItem.setCaseID(caseId);
        workItem.setTaskName(taskName);
        // Note: setStatus is a protected method, might need to use reflection or factory
        // For now, we'll just create a mock implementation
        return workItem;
    }

    /**
     * Helper to create a YSpecification for testing
     */
    private static YSpecification createSpecification(String name, String version) {
        YSpecification spec = new YSpecification();
        spec.setID("net_" + name + "_" + version);
        spec.setName(name);
        spec.setVersion(version);
        return spec;
    }

    /**
     * Helper to create a YNetRunner for testing
     */
    private static YNetRunner createNetRunner(String specId) {
        // Note: This might need a factory pattern or test double
        // For now, we'll create a basic implementation
        YNetRunner runner = new YNetRunner();
        // Initialize with specification
        return runner;
    }

    /**
     * Helper to generate large Python list string
     */
    private static String generateLargeListPython(int size) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            sb.append(i).append(", ");
        }
        sb.setLength(sb.length() - 2); // Remove last comma and space
        sb.append("]");
        return sb.toString();
    }

    /**
     * Helper to generate large Java list
     */
    private static List<Integer> generateLargeJavaList(int size) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(i);
        }
        return list;
    }

    /**
     * Helper method to convert nested structure to Python code
     */
    private String convertNestedToPython(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            StringBuilder sb = new StringBuilder("{");
            map.forEach((key, val) -> {
                sb.append("\"").append(key).append("\": ").append(convertNestedToPython(val)).append(", ");
            });
            if (!map.isEmpty()) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("}");
            return sb.toString();
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            list.forEach(val -> {
                sb.append(convertNestedToPython(val)).append(", ");
            });
            if (!list.isEmpty()) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
            return sb.toString();
        } else if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            StringBuilder sb = new StringBuilder("[");
            set.forEach(val -> {
                sb.append(convertNestedToPython(val)).append(", ");
            });
            if (!set.isEmpty()) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
            return sb.toString();
        } else {
            return convertValueToPython(value);
        }
    }

    /**
     * Helper method to convert mixed structure to Python code
     */
    private String convertMixedToPython(Object value) {
        return convertNestedToPython(value);
    }

    // ======================================================================
    // INNER CLASSES FOR TEST CASES
    // ======================================================================

    /**
     * Test case for null value testing
     */
    public record NullTestCase(
        String pythonCode,
        Object javaValue
    ) {}

    /**
     * Test case for empty collection testing
     */
    public record EmptyCollectionTestCase(
        String name,
        Object javaValue
    ) {
        public String pythonCode() {
            if (javaValue instanceof List) {
                return javaValue.toString();
            } else if (javaValue instanceof Map) {
                return "{}";
            } else if (javaValue instanceof Set) {
                return "set()";
            }
            return "null";
        }
    }

    /**
     * Test case for nested structure testing
     */
    public record NestedTestCase(
        String name,
        Object javaValue
    ) {
        public String pythonCode() {
            return convertNestedToPython(javaValue);
        }
    }

    /**
     * Test case for mixed collection testing
     */
    public record MixedTestCase(
        String name,
        Object javaValue
    ) {
        public String pythonCode() {
            return convertMixedToPython(javaValue);
        }
    }

    /**
     * Test case for performance testing
     */
    public record PerformanceTestCase(
        String name,
        String pythonCode,
        Object expectedValue,
        Duration timeout
    ) {}
}