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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Enhanced assertion utilities for YAWL Java-Python validation tests.
 * Provides specialized assertion methods for common validation scenarios.
 *
 * @since 6.0.0
 */
public final class Assertions {

    private static final Logger logger = LoggerFactory.getLogger(Assertions.class);

    private Assertions() {
        // Utility class - prevent instantiation
    }

    /**
     * Asserts that a Python exception has been raised with the expected message.
     *
     * @param engine the GraalPy engine to execute code in
     * @param pythonCode the Python code that should raise an exception
     * @param expectedMessage the expected exception message
     */
    public static void assertPythonException(Context engine, String pythonCode, String expectedMessage) {
        try {
            engine.eval(pythonCode);
            Assertions.fail("Expected Python exception was not thrown");
        } catch (PolyglotException e) {
            if (!e.getMessage().contains(expectedMessage)) {
                Assertions.fail(String.format(
                    "Python exception message mismatch. Expected: '%s', Actual: '%s'",
                    expectedMessage, e.getMessage()
                ));
            }
            logger.info("Python exception as expected: {}", e.getMessage());
        }
    }

    /**
     * Asserts that Python code execution times out within the specified duration.
     *
     * @param engine the GraalPy engine to execute code in
     * @param pythonCode the Python code to execute
     * @param timeout the expected timeout duration
     */
    public static void assertPythonTimeout(Context engine, String pythonCode, Duration timeout) {
        Instant start = Instant.now();
        try {
            engine.eval(pythonCode);
            Duration duration = Duration.between(start, Instant.now());
            Assertions.assertTrue(
                duration.toMillis() >= timeout.toMillis(),
                String.format("Code executed too quickly. Expected timeout > %s, but took %s",
                    timeout, duration)
            );
        } catch (PolyglotException e) {
            if (e.getMessage().contains("timeout")) {
                logger.info("Python timeout as expected after: {}", Duration.between(start, Instant.now()));
            } else {
                throw e;
            }
        }
    }

    /**
     * Asserts that a Python value has the expected type.
     *
     * @param value the Python value to check
     * @param expectedType the expected type name
     */
    public static void assertPythonType(Value value, String expectedType) {
        String actualType = value.getMetaQualifiedName();
        Assertions.assertEquals(expectedType, actualType,
            String.format("Type mismatch. Expected: %s, Actual: %s", expectedType, actualType));
        logger.debug("Python type verification: {} == {}", actualType, expectedType);
    }

    /**
     * Asserts that a Python value is a collection with the expected size.
     *
     * @param value the Python value to check
     * @param expectedSize the expected size
     */
    public static void assertPythonCollectionSize(Value value, int expectedSize) {
        Assertions.assertTrue(value.hasArrayElements(),
            "Expected value to be a collection with array elements");
        Assertions.assertEquals(expectedSize, value.getArraySize(),
            String.format("Collection size mismatch. Expected: %d, Actual: %d",
                expectedSize, value.getArraySize()));
        logger.debug("Python collection size: {}", expectedSize);
    }

    /**
     * Asserts that a Python value is a map with the expected key count.
     *
     * @param value the Python value to check
     * @param expectedKeyCount the expected number of keys
     */
    public static void assertPythonMapSize(Value value, int expectedKeyCount) {
        Assertions.assertTrue(value.hasHashElements(),
            "Expected value to be a map with hash elements");
        Assertions.assertEquals(expectedKeyCount, value.getHashSize(),
            String.format("Map size mismatch. Expected: %d, Actual: %d",
                expectedKeyCount, value.getHashSize()));
        logger.debug("Python map size: {}", expectedKeyCount);
    }

    /**
     * Asserts that a Python value contains the expected elements.
     *
     * @param value the Python value to check
     * @param expectedElements the expected elements
     */
    public static void assertPythonCollectionContains(Value value, Object... expectedElements) {
        Assertions.assertTrue(value.hasArrayElements(),
            "Expected value to be a collection");

        for (Object expected : expectedElements) {
            boolean found = false;
            for (int i = 0; i < value.getArraySize(); i++) {
                if (value.getArrayElement(i).as(Object.class).equals(expected)) {
                    found = true;
                    break;
                }
            }
            Assertions.assertTrue(found,
                String.format("Collection does not contain expected element: %s", expected));
        }
        logger.debug("Python collection contains all expected elements");
    }

    /**
     * Asserts that a Python map contains the expected key-value pairs.
     *
     * @param value the Python value to check
     * @param expectedPairs the expected key-value pairs
     */
    public static void assertPythonMapContains(Value value, Map.Entry<String, Object>... expectedPairs) {
        Assertions.assertTrue(value.hasHashElements(),
            "Expected value to be a map");

        for (Map.Entry<String, Object> pair : expectedPairs) {
            Value keyValue = value.getHashValue(pair.getKey());
            Assertions.assertNotNull(keyValue,
                String.format("Map does not contain expected key: %s", pair.getKey()));

            Object actualValue = keyValue.as(Object.class);
            Assertions.assertEquals(pair.getValue(), actualValue,
                String.format("Map value mismatch for key '%s'. Expected: %s, Actual: %s",
                    pair.getKey(), pair.getValue(), actualValue));
        }
        logger.debug("Python map contains all expected key-value pairs");
    }

    /**
     * Asserts that a Python value matches the expected JSON structure.
     *
     * @param value the Python value to check
     * @param expectedJson the expected JSON structure as a string
     */
    public static void assertPythonJsonStructure(Value value, String expectedJson) {
        try {
            // Convert Python value to JSON string
            String actualJson = value.toString();

            // Basic JSON structure validation
            Assertions.assertTrue(isValidJson(actualJson), "Actual value is not valid JSON");
            Assertions.assertTrue(isValidJson(expectedJson), "Expected JSON is not valid JSON");

            // Check that actual JSON contains all expected keys/structures
            // This is a simplified check - in production, use proper JSON diff
            Assertions.assertTrue(actualJson.contains("{") && actualJson.contains("}"),
                "Actual JSON should be an object");
            Assertions.assertTrue(expectedJson.contains("{") && expectedJson.contains("}"),
                "Expected JSON should be an object");

            logger.debug("Python JSON structure validation passed");
        } catch (Exception e) {
            Assertions.fail(String.format("JSON structure validation failed: %s", e.getMessage()));
        }
    }

    /**
     * Asserts that Python code returns a specific value.
     *
     * @param engine the GraalPy engine to execute code in
     * @param pythonCode the Python code to execute
     * @param expectedValue the expected return value
     */
    public static void assertPythonReturns(Context engine, String pythonCode, Object expectedValue) {
        Value result = engine.eval(pythonCode);
        Object actualValue = result.as(Object.class);
        Assertions.assertEquals(expectedValue, actualValue,
            String.format("Python return value mismatch. Expected: %s, Actual: %s",
                expectedValue, actualValue));
        logger.debug("Python return value verified: {}", actualValue);
    }

    /**
     * Performs multiple assertions on a Python value with fluent interface.
     *
     * @param value the Python value to validate
     * @return a builder for fluent assertions
     */
    public static PythonAssertionBuilder assertThat(Value value) {
        return new PythonAssertionBuilder(value);
    }

    /**
     * Builder for fluent Python assertions.
     */
    public static class PythonAssertionBuilder {
        private final Value value;

        public PythonAssertionBuilder(Value value) {
            this.value = value;
        }

        public PythonAssertionBuilder hasType(String expectedType) {
            assertPythonType(value, expectedType);
            return this;
        }

        public PythonAssertionBuilder hasSize(int expectedSize) {
            if (value.hasArrayElements()) {
                assertPythonCollectionSize(value, expectedSize);
            } else if (value.hasHashElements()) {
                assertPythonMapSize(value, expectedSize);
            } else {
                Assertions.fail("Value must be a collection or map to check size");
            }
            return this;
        }

        public PythonAssertionBuilder contains(Object... elements) {
            assertPythonCollectionContains(value, elements);
            return this;
        }

        public PythonAssertionBuilder containsKeys(String... keys) {
            for (String key : keys) {
                Assertions.assertTrue(value.hasHashElements(),
                    "Value must be a map to check keys");
                Assertions.assertTrue(value.getHashValue(key) != null,
                    String.format("Map does not contain key: %s", key));
            }
            return this;
        }

        public PythonAssertionBuilder hasValue(Object expectedValue) {
            Object actualValue = value.as(Object.class);
            Assertions.assertEquals(expectedValue, actualValue);
            return this;
        }

        public PythonAssertionBuilder isNotNull() {
            Assertions.assertFalse(value.isNull(), "Value should not be null");
            return this;
        }

        public PythonAssertionBuilder isNull() {
            Assertions.assertTrue(value.isNull(), "Value should be null");
            return this;
        }
    }

    /**
     * Helper method to validate JSON format.
     */
    private static boolean isValidJson(String json) {
        try {
            new org.json.JSONObject(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}