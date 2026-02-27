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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


/**
 * Comprehensive unit tests for {@link NullCheckModernizer}.
 *
 * <p>Coverage: all public methods, null and non-null paths, edge cases.
 * Uses Chicago TDD style (real inputs, no mocks).
 *
 * @author YAWL Foundation
 * @since YAWL 6.0.0-Beta
 */
@DisplayName("NullCheckModernizer")
@Tag("unit")
class TestNullCheckModernizer {


    // =========================================================================
    // Instantiation prevention
    // =========================================================================

    @Test
    @DisplayName("constructor throws UnsupportedOperationException")
    void constructorThrows() throws Exception {
        var constructor = NullCheckModernizer.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }


    // =========================================================================
    // requirePresent — guard clauses
    // =========================================================================

    @Nested
    @DisplayName("requirePresent")
    class RequirePresent {

        static class WorkflowException extends Exception {
            WorkflowException(String msg) { super(msg); }
        }

        @Test
        @DisplayName("returns value when non-null")
        void returnsValue() throws WorkflowException {
            String input = "hello";
            String result = NullCheckModernizer.requirePresent(
                    input, "value must not be null", WorkflowException::new);
            assertSame(input, result);
        }

        @Test
        @DisplayName("throws exception when null")
        void throwsOnNull() {
            var ex = assertThrows(WorkflowException.class, () ->
                    NullCheckModernizer.requirePresent(
                            null, "spec is null", WorkflowException::new));
            assertEquals("spec is null", ex.getMessage());
        }

        @Test
        @DisplayName("works with RuntimeException factory")
        void worksWithRuntimeException() {
            var ex = assertThrows(IllegalArgumentException.class, () ->
                    NullCheckModernizer.requirePresent(
                            null, "runner", IllegalArgumentException::new));
            assertEquals("runner", ex.getMessage());
        }

        @Test
        @DisplayName("non-null object returned without boxing overhead")
        void returnsSameReference() throws WorkflowException {
            List<String> list = new ArrayList<>();
            List<String> result = NullCheckModernizer.requirePresent(
                    list, "list is null", WorkflowException::new);
            assertSame(list, result);
        }
    }


    // =========================================================================
    // requireNonNull — NullPointerException guard
    // =========================================================================

    @Nested
    @DisplayName("requireNonNull")
    class RequireNonNull {

        @Test
        @DisplayName("returns value when non-null")
        void returnsValue() {
            String s = "yawl";
            assertSame(s, NullCheckModernizer.requireNonNull(s, "taskID"));
        }

        @Test
        @DisplayName("throws NullPointerException with field name in message")
        void throwsWithFieldName() {
            NullPointerException ex = assertThrows(NullPointerException.class, () ->
                    NullCheckModernizer.requireNonNull(null, "caseID"));
            assertTrue(ex.getMessage().contains("caseID"),
                    "Expected 'caseID' in message but got: " + ex.getMessage());
        }
    }


    // =========================================================================
    // ifPresent — conditional consumer
    // =========================================================================

    @Nested
    @DisplayName("ifPresent")
    class IfPresent {

        @Test
        @DisplayName("executes action when value is non-null")
        void executesAction() {
            AtomicBoolean executed = new AtomicBoolean(false);
            NullCheckModernizer.ifPresent("someValue", v -> executed.set(true));
            assertTrue(executed.get());
        }

        @Test
        @DisplayName("passes the value to the consumer")
        void passesValue() {
            AtomicReference<String> captured = new AtomicReference<>();
            NullCheckModernizer.ifPresent("engine", captured::set);
            assertEquals("engine", captured.get());
        }

        @Test
        @DisplayName("does not execute action when value is null")
        void skipsActionForNull() {
            AtomicBoolean executed = new AtomicBoolean(false);
            NullCheckModernizer.ifPresent(null, v -> executed.set(true));
            assertFalse(executed.get());
        }
    }


    // =========================================================================
    // ifPresentOrElse — two-branch conditional
    // =========================================================================

    @Nested
    @DisplayName("ifPresentOrElse")
    class IfPresentOrElse {

        @Test
        @DisplayName("executes present action when non-null")
        void executesPresentBranch() {
            AtomicInteger counter = new AtomicInteger(0);
            NullCheckModernizer.ifPresentOrElse(
                    "spec",
                    v -> counter.set(1),
                    () -> counter.set(2));
            assertEquals(1, counter.get());
        }

        @Test
        @DisplayName("executes absent action when null")
        void executesAbsentBranch() {
            AtomicInteger counter = new AtomicInteger(0);
            NullCheckModernizer.ifPresentOrElse(
                    null,
                    v -> counter.set(1),
                    () -> counter.set(2));
            assertEquals(2, counter.get());
        }

        @Test
        @DisplayName("does not execute absent branch when value is non-null")
        void doesNotRunAbsentBranchWhenPresent() {
            AtomicBoolean absentRan = new AtomicBoolean(false);
            NullCheckModernizer.ifPresentOrElse(
                    42,
                    v -> { /* intentional no-op for present branch */ },
                    () -> absentRan.set(true));
            assertFalse(absentRan.get());
        }
    }


    // =========================================================================
    // mapOrNull — null-safe mapping
    // =========================================================================

    @Nested
    @DisplayName("mapOrNull")
    class MapOrNull {

        @Test
        @DisplayName("applies mapper when value is non-null")
        void appliesMapper() {
            String result = NullCheckModernizer.mapOrNull("hello", String::toUpperCase);
            assertEquals("HELLO", result);
        }

        @Test
        @DisplayName("returns null when value is null")
        void returnsNullForNull() {
            String result = NullCheckModernizer.mapOrNull((String) null, String::toUpperCase);
            assertNull(result);
        }

        @Test
        @DisplayName("supports method reference chaining")
        void supportsChaining() {
            List<String> list = Arrays.asList("a", "b");
            Integer size = NullCheckModernizer.mapOrNull(list, List::size);
            assertEquals(2, size);
        }

        @Test
        @DisplayName("returns null when mapper returns null")
        void returnsMappedNull() {
            String result = NullCheckModernizer.mapOrNull("test", s -> null);
            assertNull(result);
        }
    }


    // =========================================================================
    // mapOrElse — null-safe mapping with default
    // =========================================================================

    @Nested
    @DisplayName("mapOrElse")
    class MapOrElse {

        @Test
        @DisplayName("applies mapper when value is non-null")
        void appliesMapper() {
            int result = NullCheckModernizer.mapOrElse("yawl", String::length, -1);
            assertEquals(4, result);
        }

        @Test
        @DisplayName("returns default when value is null")
        void returnsDefaultForNull() {
            int result = NullCheckModernizer.mapOrElse(null, String::length, -1);
            assertEquals(-1, result);
        }

        @Test
        @DisplayName("returns default when value is present but mapper returns null")
        void handlesNullMappedResult() {
            String result = NullCheckModernizer.mapOrElse(
                    "test", s -> null, "fallback");
            assertNull(result, "mapOrElse should return the mapped result even if null");
        }
    }


    // =========================================================================
    // mapOrGet — lazy default
    // =========================================================================

    @Nested
    @DisplayName("mapOrGet")
    class MapOrGet {

        @Test
        @DisplayName("applies mapper when value is non-null")
        void appliesMapper() {
            String result = NullCheckModernizer.mapOrGet("net", String::toUpperCase,
                    () -> "ABSENT");
            assertEquals("NET", result);
        }

        @Test
        @DisplayName("evaluates supplier when value is null")
        void evaluatesSupplier() {
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            String result = NullCheckModernizer.mapOrGet((String) null, String::toUpperCase, () -> {
                supplierCalled.set(true);
                return "COMPUTED";
            });
            assertEquals("COMPUTED", result);
            assertTrue(supplierCalled.get());
        }

        @Test
        @DisplayName("does not evaluate supplier when value is non-null")
        void doesNotEvaluateSupplierWhenPresent() {
            AtomicBoolean supplierCalled = new AtomicBoolean(false);
            NullCheckModernizer.mapOrGet("present", s -> s,
                    () -> { supplierCalled.set(true); return "lazy"; });
            assertFalse(supplierCalled.get());
        }
    }


    // =========================================================================
    // toOptional — Optional bridge
    // =========================================================================

    @Nested
    @DisplayName("toOptional")
    class ToOptional {

        @Test
        @DisplayName("wraps non-null value in Optional")
        void wrapsValue() {
            Optional<String> opt = NullCheckModernizer.toOptional("task");
            assertTrue(opt.isPresent());
            assertEquals("task", opt.get());
        }

        @Test
        @DisplayName("returns empty Optional for null")
        void returnsEmpty() {
            Optional<String> opt = NullCheckModernizer.toOptional(null);
            assertTrue(opt.isEmpty());
        }
    }


    // =========================================================================
    // emptyIfNull — collection and string null-safety
    // =========================================================================

    @Nested
    @DisplayName("emptyIfNull (collections)")
    class EmptyIfNullCollections {

        @Test
        @DisplayName("returns same list when non-null")
        void returnsSameList() {
            List<String> list = new ArrayList<>();
            assertSame(list, NullCheckModernizer.emptyIfNull(list));
        }

        @Test
        @DisplayName("returns empty list when null")
        void returnsEmptyListForNull() {
            List<String> result = NullCheckModernizer.emptyIfNull((List<String>) null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns same set when non-null")
        void returnsSameSet() {
            Set<String> set = new HashSet<>();
            assertSame(set, NullCheckModernizer.emptyIfNull(set));
        }

        @Test
        @DisplayName("returns empty set when null")
        void returnsEmptySetForNull() {
            Set<String> result = NullCheckModernizer.emptyIfNull((Set<String>) null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns same map when non-null")
        void returnsSameMap() {
            Map<String, Integer> map = new HashMap<>();
            assertSame(map, NullCheckModernizer.emptyIfNull(map));
        }

        @Test
        @DisplayName("returns empty map when null")
        void returnsEmptyMapForNull() {
            Map<String, Integer> result =
                    NullCheckModernizer.emptyIfNull((Map<String, Integer>) null);
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }


    @Nested
    @DisplayName("emptyIfNull (String)")
    class EmptyIfNullString {

        @Test
        @DisplayName("returns value when non-null")
        void returnsValue() {
            assertEquals("test", NullCheckModernizer.emptyIfNull("test"));
        }

        @Test
        @DisplayName("returns empty string when null")
        void returnsEmptyForNull() {
            assertEquals("", NullCheckModernizer.emptyIfNull((String) null));
        }

        @Test
        @DisplayName("returns empty string unchanged")
        void returnsEmptyStringUnchanged() {
            assertEquals("", NullCheckModernizer.emptyIfNull(""));
        }
    }


    // =========================================================================
    // nonEmptyOrElseGet
    // =========================================================================

    @Nested
    @DisplayName("nonEmptyOrElseGet")
    class NonEmptyOrElseGet {

        @Test
        @DisplayName("returns non-empty collection when present")
        void returnsNonEmptyCollection() {
            List<String> list = Arrays.asList("a");
            assertSame(list, NullCheckModernizer.nonEmptyOrElseGet(list, ArrayList::new));
        }

        @Test
        @DisplayName("evaluates supplier when collection is null")
        void evaluatesSupplierForNull() {
            List<String> result = NullCheckModernizer.nonEmptyOrElseGet(
                    null, () -> Arrays.asList("default"));
            assertEquals(Collections.singletonList("default"), result);
        }

        @Test
        @DisplayName("evaluates supplier when collection is empty")
        void evaluatesSupplierForEmpty() {
            List<String> result = NullCheckModernizer.nonEmptyOrElseGet(
                    new ArrayList<>(), () -> Arrays.asList("default"));
            assertEquals(Collections.singletonList("default"), result);
        }
    }


    // =========================================================================
    // isNullOrBlank / hasContent
    // =========================================================================

    @Nested
    @DisplayName("isNullOrBlank / hasContent")
    class StringContentChecks {

        @Test
        @DisplayName("isNullOrBlank: null input returns true")
        void nullIsBlank() {
            assertTrue(NullCheckModernizer.isNullOrBlank(null));
        }

        @Test
        @DisplayName("isNullOrBlank: empty string returns true")
        void emptyIsBlank() {
            assertTrue(NullCheckModernizer.isNullOrBlank(""));
        }

        @Test
        @DisplayName("isNullOrBlank: whitespace-only string returns true")
        void whitespaceIsBlank() {
            assertTrue(NullCheckModernizer.isNullOrBlank("   "));
        }

        @Test
        @DisplayName("isNullOrBlank: content string returns false")
        void contentStringIsNotBlank() {
            assertFalse(NullCheckModernizer.isNullOrBlank("YAWL"));
        }

        @Test
        @DisplayName("hasContent: null returns false")
        void nullHasNoContent() {
            assertFalse(NullCheckModernizer.hasContent(null));
        }

        @Test
        @DisplayName("hasContent: empty string returns false")
        void emptyHasNoContent() {
            assertFalse(NullCheckModernizer.hasContent(""));
        }

        @Test
        @DisplayName("hasContent: whitespace-only returns false")
        void whitespaceHasNoContent() {
            assertFalse(NullCheckModernizer.hasContent("  "));
        }

        @Test
        @DisplayName("hasContent: content string returns true")
        void contentStringHasContent() {
            assertTrue(NullCheckModernizer.hasContent("netRunner"));
        }
    }


    // =========================================================================
    // isTrue / isFalseOrNull
    // =========================================================================

    @Nested
    @DisplayName("isTrue / isFalseOrNull")
    class BooleanChecks {

        @Test
        @DisplayName("isTrue: null returns false")
        void nullIsNotTrue() {
            assertFalse(NullCheckModernizer.isTrue(null));
        }

        @Test
        @DisplayName("isTrue: Boolean.FALSE returns false")
        void falseIsNotTrue() {
            assertFalse(NullCheckModernizer.isTrue(Boolean.FALSE));
        }

        @Test
        @DisplayName("isTrue: Boolean.TRUE returns true")
        void trueIsTrue() {
            assertTrue(NullCheckModernizer.isTrue(Boolean.TRUE));
        }

        @Test
        @DisplayName("isFalseOrNull: null returns true")
        void nullIsFalseOrNull() {
            assertTrue(NullCheckModernizer.isFalseOrNull(null));
        }

        @Test
        @DisplayName("isFalseOrNull: Boolean.FALSE returns true")
        void falseIsFalseOrNull() {
            assertTrue(NullCheckModernizer.isFalseOrNull(Boolean.FALSE));
        }

        @Test
        @DisplayName("isFalseOrNull: Boolean.TRUE returns false")
        void trueIsNotFalseOrNull() {
            assertFalse(NullCheckModernizer.isFalseOrNull(Boolean.TRUE));
        }
    }


    // =========================================================================
    // firstNonNull — two-arg and varargs
    // =========================================================================

    @Nested
    @DisplayName("firstNonNull")
    class FirstNonNull {

        @Test
        @DisplayName("two-arg: returns first when non-null")
        void returnsFirstWhenPresent() {
            assertEquals("first",
                    NullCheckModernizer.firstNonNull("first", "second"));
        }

        @Test
        @DisplayName("two-arg: returns second when first is null")
        void returnsSecondWhenFirstNull() {
            assertEquals("second",
                    NullCheckModernizer.firstNonNull(null, "second"));
        }

        @Test
        @DisplayName("two-arg: returns null when both are null")
        void returnNullWhenBothNull() {
            assertNull(NullCheckModernizer.firstNonNull((String) null, null));
        }

        @Test
        @DisplayName("varargs: returns first non-null from sequence")
        void returnsFirstNonNullVarargs() {
            assertEquals("third",
                    NullCheckModernizer.firstNonNull(null, null, "third", "fourth"));
        }

        @Test
        @DisplayName("varargs: returns null when all null")
        void returnsNullWhenAllNull() {
            assertNull(NullCheckModernizer.firstNonNull(null, null, null));
        }

        @Test
        @DisplayName("varargs: single non-null element")
        void singleNonNull() {
            assertEquals("only",
                    NullCheckModernizer.firstNonNull("only"));
        }
    }
}
