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

package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for NullCheckModernizer utility class.
 * Tests null-safe operations and modern Java patterns.
 *
 * @author YAWL Test Agent
 * @since YAWL 6.0.0
 */
class NullCheckModernizerTest {

    private TestService testService;
    private List<String> testList;
    private Set<String> testSet;
    private Map<String, Integer> testMap;
    private String testString;

    @BeforeEach
    void setUp() {
        testService = new TestService();
        testList = Arrays.asList("item1", "item2", "item3");
        testSet = new HashSet<>(testList);
        testMap = Map.of("key1", 1, "key2", 2, "key3", 3);
        testString = "test string";
    }

    // Test helper class
    static class TestService {
        String process(String input) {
            return "processed: " + input;
        }

        String getFullName() {
            return "John Doe";
        }

        List<String> getItems() {
            return Arrays.asList("a", "b", "c");
        }
    }

    // Require Present tests
    @Test
    @DisplayName("Should return non-null value when value is present")
    void requirePresent_nonValue_returnsValue() {
        String result = NullCheckModernizer.requirePresent("test", "error", IllegalArgumentException::new);

        assertEquals("test", result,
            "Should return the non-null value");
    }

    @Test
    @DisplayName("Should throw exception when value is null")
    void requirePresent_nullValue_throwsException() {
        assertThrows(IllegalArgumentException.class,
            () -> NullCheckModernizer.requirePresent(null, "value is required", IllegalArgumentException::new));
    }

    @Test
    @DisplayName("Should use custom exception factory")
    void requirePresent_customExceptionFactory_throwsCustomException() {
        class CustomException extends Exception {
            CustomException(String message) {
                super(message);
            }
        }

        assertThrows(CustomException.class,
            () -> NullCheckModernizer.requirePresent(null, "custom error", CustomException::new));
    }

    @Test
    @DisplayName("Should include message in exception")
    void requirePresent_exceptionIncludesMessage() {
        try {
            NullCheckModernizer.requirePresent(null, "field cannot be null", IllegalArgumentException::new);
            fail("Should have thrown exception");
        } catch (IllegalArgumentException e) {
            assertEquals("field cannot be null", e.getMessage(),
                "Exception message should match the provided message");
        }
    }

    // Require Non Null tests
    @Test
    @DisplayName("Should return non-null value when value is present")
    void requireNonNull_nonValue_returnsValue() {
        String result = NullCheckModernizer.requireNonNull("test", "field");

        assertEquals("test", result,
            "Should return the non-null value");
    }

    @Test
    @DisplayName("Should throw NullPointerException when value is null")
    void requireNonNull_nullValue_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
            () -> NullCheckModernizer.requireNonNull(null, "field"));
    }

    @Test
    @DisplayName("Should include field name in exception message")
    void requireNonNull_exceptionIncludesFieldName() {
        try {
            NullCheckModernizer.requireNonNull(null, "fieldName");
            fail("Should have thrown exception");
        } catch (NullPointerException e) {
            assertEquals("fieldName must not be null", e.getMessage(),
                "Exception message should include field name");
        }
    }

    // If Present tests
    @Test
    @DisplayName("Should execute action when value is present")
    void ifPresent_nonValue_executesAction() {
        List<String> executedActions = new ArrayList<>();

        NullCheckModernizer.ifPresent("test", value -> executedActions.add("action: " + value));

        assertEquals(1, executedActions.size(),
            "Action should be executed once");
        assertEquals("action: test", executedActions.get(0),
            "Action should receive the non-null value");
    }

    @Test
    @DisplayName("Should not execute action when value is null")
    void ifPresent_nullValue_doesNotExecuteAction() {
        List<String> executedActions = new ArrayList<>();

        NullCheckModernizer.ifPresent(null, value -> executedActions.add("action: " + value));

        assertEquals(0, executedActions.size(),
            "Action should not be executed when value is null");
    }

    @Test
    @DisplayName("Should accept null-safe action")
    void ifPresent_nullSafeAction_works() {
        List<String> executedActions = new ArrayList<>();

        Consumer<String> action = value -> executedActions.add("processed: " + value);
        NullCheckModernizer.ifPresent("test", action);

        assertEquals(1, executedActions.size());
        assertEquals("processed: test", executedActions.get(0));
    }

    // If Present Or Else tests
    @Test
    @DisplayName("Should execute present action when value is present")
    void ifPresentOrElse_nonValue_executesPresentAction() {
        List<String> executedActions = new ArrayList<>();

        NullCheckModernizer.ifPresentOrElse(
            "test",
            value -> executedActions.add("present: " + value),
            () -> executedActions.add("absent"));

        assertEquals(1, executedActions.size(),
            "Only present action should be executed");
        assertEquals("present: test", executedActions.get(0),
            "Present action should receive the value");
    }

    @Test
    @DisplayName("Should execute absent action when value is null")
    void ifPresentOrElse_nullValue_executesAbsentAction() {
        List<String> executedActions = new ArrayList<>();

        NullCheckModernizer.ifPresentOrElse(
            null,
            value -> executedActions.add("present: " + value),
            () -> executedActions.add("absent"));

        assertEquals(1, executedActions.size(),
            "Only absent action should be executed");
        assertEquals("absent", executedActions.get(0),
            "Absent action should be executed");
    }

    @Test
    @DisplayName("Should handle both actions with side effects")
    void ifPresentOrElse_bothActionsHaveSideEffects_works() {
        StringBuilder result = new StringBuilder();

        NullCheckModernizer.ifPresentOrElse(
            "test",
            value -> result.append("present:").append(value),
            () -> result.append("absent"));

        assertEquals("present:test", result.toString(),
            "Present action should be executed with value");
    }

    // Map Or Null tests
    @Test
    @DisplayName("Should map value when value is present")
    void mapOrNull_nonValue_appliesMapper() {
        String result = NullCheckModernizer.mapOrNull("test", String::toUpperCase);

        assertEquals("TEST", result,
            "Mapper should be applied to non-null value");
    }

    @Test
    @DisplayName("Should return null when value is null")
    void mapOrNull_nullValue_returnsNull() {
        String result = NullCheckModernizer.mapOrNull(null, String::toUpperCase);

        assertNull(result,
            "Should return null when input is null");
    }

    @Test
    @DisplayName("Should use complex mapper function")
    void mapOrNull_complexMapper_appliesCorrectly() {
        List<String> input = Arrays.asList("a", "b", "c");
        String result = NullCheckModernizer.mapOrNull(input, list -> String.join(",", list));

        assertEquals("a,b,c", result,
            "Complex mapper should be applied correctly");
    }

    @Test
    @DisplayName("Should handle mapper that returns null")
    void mapOrNull_mapperReturnsNull_returnsNull() {
        String result = NullCheckModernizer.mapOrNull("test", s -> null);

        assertNull(result,
            "Should return null when mapper returns null");
    }

    // Map Or Else tests
    @Test
    @DisplayName("Should map value when value is present")
    void mapOrElse_nonValue_appliesMapper() {
        String result = NullCheckModernizer.mapOrElse("test", String::toUpperCase, "DEFAULT");

        assertEquals("TEST", result,
            "Mapper should be applied to non-null value");
    }

    @Test
    @DisplayName("Should return default when value is null")
    void mapOrElse_nullValue_returnsDefault() {
        String result = NullCheckModernizer.mapOrElse(null, String::toUpperCase, "DEFAULT");

        assertEquals("DEFAULT", result,
            "Should return default value when input is null");
    }

    @Test
    @DisplayName("Should use expensive default lazily")
    void mapOrGet_lazyDefault_evaluatesSupplierOnlyWhenNeeded() {
        List<String> input = Arrays.asList("a", "b", "c");

        String result = NullCheckModernizer.mapOrGet(
            input,
            list -> list.size() + " items",
            () -> {
                throw new RuntimeException("Should not be called");
            });

        assertEquals("3 items", result,
            "Mapper should be applied, supplier should not be called");
    }

    @Test
    @DisplayName("Should evaluate supplier when value is null")
    void mapOrGet_nullValue_evaluatesSupplier() {
        List<String> input = null;
        Supplier<String> supplier = () -> "computed default";

        String result = NullCheckModernizer.mapOrGet(
            input,
            List::toString,
            supplier);

        assertEquals("computed default", result,
            "Supplier should be evaluated when input is null");
    }

    // To Optional tests
    @Test
    @DisplayName("Should wrap non-null value in Optional")
    void toOptional_nonValue_returnsOptionalWithValue() {
        Optional<String> result = NullCheckModernizer.toOptional("test");

        assertTrue(result.isPresent(),
            "Optional should contain value");
        assertEquals("test", result.get(),
            "Optional should contain the original value");
    }

    @Test
    @DisplayName("Should return empty Optional for null value")
    void toOptional_nullValue_returnsEmptyOptional() {
        Optional<String> result = NullCheckModernizer.toOptional(null);

        assertFalse(result.isPresent(),
            "Optional should be empty");
    }

    // Empty If Null collection tests
    @Test
    @DisplayName("Should return original list when list is not null")
    void emptyIfNonNull_nonNullList_returnsOriginalList() {
        List<String> result = NullCheckModernizer.emptyIfNull(testList);

        assertSame(testList, result,
            "Should return the same list instance");
    }

    @Test
    @DisplayName("Should return empty list when list is null")
    void emptyIfNonNull_nullList_returnsEmptyList() {
        List<String> result = NullCheckModernizer.emptyIfNull(null);

        assertTrue(result.isEmpty(),
            "Should return empty list");
        assertNotSame(Collections.emptyList(), result,
            "Should return a new empty list instance");
    }

    @Test
    @DisplayName("Should handle empty set")
    void emptyIfNonNull_nullSet_returnsEmptySet() {
        Set<String> result = NullCheckModernizer.emptyIfNull(null);

        assertTrue(result.isEmpty(),
            "Should return empty set");
    }

    @Test
    @DisplayName("Should handle empty map")
    void emptyIfNonNull_nullMap_returnsEmptyMap() {
        Map<String, Integer> result = NullCheckModernizer.emptyIfNull(null);

        assertTrue(result.isEmpty(),
            "Should return empty map");
    }

    // Non Empty Or Else Get tests
    @Test
    @DisplayName("Should return original collection when it is non-empty")
    void nonEmptyOrElseGet_nonEmptyCollection_returnsOriginal() {
        List<String> result = NullCheckModernizer.nonEmptyOrElseGet(
            testList, ArrayList::new);

        assertSame(testList, result,
            "Should return the original collection");
    }

    @Test
    @DisplayName("Should return default when collection is null")
    void nonEmptyOrElseGet_nullCollection_returnsDefault() {
        List<String> result = NullCheckModernizer.nonEmptyOrElseGet(
            null, ArrayList::new);

        assertTrue(result.isEmpty(),
            "Should return empty default collection");
        assertNotSame(testList, result,
            "Should return a new collection instance");
    }

    @Test
    @DisplayName("Should return default when collection is empty")
    void nonEmptyOrElseGet_emptyCollection_returnsDefault() {
        List<String> emptyList = new ArrayList<>();
        List<String> result = NullCheckModernizer.nonEmptyOrElseGet(
            emptyList, () -> Arrays.asList("default"));

        assertEquals(Arrays.asList("default"), result,
            "Should return the default collection");
    }

    // Empty If Null string tests
    @Test
    @DisplayName("Should return original string when string is not null")
    void emptyIfNonNull_nonNullString_returnsOriginalString() {
        String result = NullCheckModernizer.emptyIfNull(testString);

        assertSame(testString, result,
            "Should return the same string instance");
    }

    @Test
    @DisplayName("Should return empty string when string is null")
    void emptyIfNonNull_nullString_returnsEmptyString() {
        String result = NullCheckModernizer.emptyIfNull(null);

        assertEquals("", result,
            "Should return empty string");
    }

    // Is Null Or Blank tests
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n", "\r\n", "\t "})
    @DisplayName("Should return true for null, empty, or whitespace-only strings")
    void isNullOrBlank_nullEmptyOrWhitespace_returnsTrue(String value) {
        assertTrue(NullCheckModernizer.isNullOrBlank(value),
            "Should return true for null, empty, or whitespace-only strings");
    }

    @Test
    @DisplayName("Should return false for strings with content")
    void isNullOrBlank_stringWithContent_returnsFalse() {
        assertFalse(NullCheckModernizer.isNullOrBlank("test"),
            "Should return false for strings with content");
        assertFalse(NullCheckModernizer.isNullOrBlank(" test "),
            "Should return false for strings with content even with whitespace");
    }

    // Has Content tests
    @Test
    @DisplayName("Should return true for strings with content")
    void hasContent_stringWithContent_returnsTrue() {
        assertTrue(NullCheckModernizer.hasContent("test"),
            "Should return true for strings with content");
        assertTrue(NullCheckModernizer.hasContent(" test "),
            "Should return true for strings with content even with whitespace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"", " ", "\t", "\n", "\r\n", "\t "})
    @DisplayName("Should return false for null, empty, or whitespace-only strings")
    void hasContent_nullEmptyOrWhitespace_returnsFalse(String value) {
        assertFalse(NullCheckModernizer.hasContent(value),
            "Should return false for null, empty, or whitespace-only strings");
    }

    // Boolean utilities tests
    @Test
    @DisplayName("Should return true for Boolean.TRUE")
    void isTrue_trueBoolean_returnsTrue() {
        assertTrue(NullCheckModernizer.isTrue(Boolean.TRUE),
            "Should return true for Boolean.TRUE");
    }

    @Test
    @DisplayName("Should return false for Boolean.FALSE")
    void isTrue_falseBoolean_returnsFalse() {
        assertFalse(NullCheckModernizer.isTrue(Boolean.FALSE),
            "Should return false for Boolean.FALSE");
    }

    @Test
    @DisplayName("Should return false for null Boolean")
    void isTrue_nullBoolean_returnsFalse() {
        assertFalse(NullCheckModernizer.isTrue(null),
            "Should return false for null Boolean");
    }

    @Test
    @DisplayName("Should return true for null Boolean")
    void isFalseOrNull_nullBoolean_returnsTrue() {
        assertTrue(NullCheckModernizer.isFalseOrNull(null),
            "Should return true for null Boolean");
    }

    @Test
    @DisplayName("Should return true for Boolean.FALSE")
    void isFalseOrNull_falseBoolean_returnsTrue() {
        assertTrue(NullCheckModernizer.isFalseOrNull(Boolean.FALSE),
            "Should return true for Boolean.FALSE");
    }

    @Test
    @DisplayName("Should return false for Boolean.TRUE")
    void isFalseOrNull_trueBoolean_returnsFalse() {
        assertFalse(NullCheckModernizer.isFalseOrNull(Boolean.TRUE),
            "Should return false for Boolean.TRUE");
    }

    // First Non Null tests
    @Test
    @DisplayName("Should return first value when both are non-null")
    void firstNonNull_bothNonNull_returnsFirst() {
        String result = NullCheckModernizer.firstNonNull("first", "second");

        assertEquals("first", result,
            "Should return the first non-null value");
    }

    @Test
    @DisplayName("Should return first value when first is non-null and second is null")
    void firstNonNull_firstNonNull_returnsFirst() {
        String result = NullCheckModernizer.firstNonNull("first", null);

        assertEquals("first", result,
            "Should return the first non-null value");
    }

    @Test
    @DisplayName("Should return second value when first is null and second is non-null")
    void firstNonNull_secondNonNull_returnsSecond() {
        String result = NullCheckModernizer.firstNonNull(null, "second");

        assertEquals("second", result,
            "Should return the second non-null value");
    }

    @Test
    @DisplayName("Should return null when both are null")
    void firstNonNull_bothNull_returnsNull() {
        String result = NullCheckModernizer.firstNonNull(null, null);

        assertNull(result,
            "Should return null when both values are null");
    }

    @Test
    @DisplayName("Should return first non-null from multiple values")
    void firstNonNull_multipleValues_returnsFirstNonNull() {
        String result = NullCheckModernizer.firstNonNull(null, "first", null, "second", null);

        assertEquals("first", result,
            "Should return the first non-null value");
    }

    @Test
    @DisplayName("Should return null when all values are null")
    void firstNonNull_allNull_returnsNull() {
        String result = NullCheckModernizer.firstNonNull(null, null, null);

        assertNull(result,
            "Should return null when all values are null");
    }

    // Real-world usage tests
    @Test
    @DisplayName("Should handle real-world null-safe service pattern")
    void realWorldServicePattern_worksCorrectly() {
        // Simulate fetching user data that might be null
        String username = NullCheckModernizer.mapOrNull(
            getUserFromDatabase(),
            User::getUsername);

        assertNotNull(username,
            "Username should be extracted or null if user is null");
    }

    @Test
    @DisplayName("Should handle real-world collection processing pattern")
    void realWorldCollectionPattern_worksCorrectly() {
        // Simulate processing a list that might be null
        List<String> processedItems = NullCheckModernizer.emptyIfNull(
            getItemsFromService())
            .stream()
            .filter(NullCheckModernizer::hasContent)
            .map(String::toUpperCase)
            .collect(Collectors.toList());

        assertNotNull(processedItems,
            "Processed items list should never be null");
    }

    @Test
    @DisplayName("Should handle real-world configuration pattern")
    void realWorldConfigurationPattern_worksCorrectly() {
        // Simulate configuration loading with defaults
        int timeout = SafeNumberParser.parseIntOrDefault(
            getConfigValue("timeout"), 30);

        assertTrue(timeout > 0,
            "Timeout should be positive, either from config or default");
    }

    @Test
    @DisplayName("Should handle real-world optional action pattern")
    void realWorldOptionalActionPattern_worksCorrectly() {
        List<String> logMessages = new ArrayList<>();

        // Only process if service and result are present
        NullCheckModernizer.ifPresent(
            getOptionalService(),
            service -> NullCheckModernizer.ifPresent(
                service.getResult(),
                result -> logMessages.add("Processed: " + result)));

        assertEquals(0, logMessages.size(),
            "No log messages when service or result is null");
    }

    // Test helper methods
    private User getUserFromDatabase() {
        return null; // Simulate null user for testing
    }

    private List<String> getItemsFromService() {
        return Arrays.asList("item1", "", "item3");
    }

    private String getConfigValue(String key) {
        return null; // Simulate null config for testing
    }

    private OptionalService getOptionalService() {
        return null; // Simulate null service for testing
    }

    // Helper classes for real-world tests
    static class User {
        String getUsername() {
            return "john.doe";
        }
    }

    static class OptionalService {
        String getResult() {
            return "success";
        }
    }
}