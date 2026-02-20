/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MultiInstanceCardinalityEvaluator.
 *
 * <p>Tests cardinality evaluation for WCP-13 to WCP-14 multi-instance patterns.
 * Chicago TDD: real evaluation logic, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("MultiInstanceCardinalityEvaluator Tests")
class MultiInstanceCardinalityEvaluatorTest {

    private MultiInstanceCardinalityEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new MultiInstanceCardinalityEvaluator();
    }

    @Nested
    @DisplayName("Static Integer Cardinality (WCP-13)")
    class StaticCardinalityTests {

        @Test
        @DisplayName("Evaluates integer string '3' to 3")
        void evaluateIntegerString() {
            int result = evaluator.evaluate("3", 1);
            assertEquals(3, result);
        }

        @Test
        @DisplayName("Evaluates integer string '1' to 1")
        void evaluateMinimum() {
            int result = evaluator.evaluate("1", 1);
            assertEquals(1, result);
        }

        @Test
        @DisplayName("Evaluates null to 1")
        void evaluateNull() {
            int result = evaluator.evaluate(null, 5);
            assertEquals(1, result);
        }

        @Test
        @DisplayName("Evaluates empty string to 1")
        void evaluateEmpty() {
            int result = evaluator.evaluate("", 5);
            assertEquals(1, result);
        }

        @Test
        @DisplayName("isStatic returns true for integer")
        void isStaticForInteger() {
            assertTrue(evaluator.isStatic("5"));
        }

        @Test
        @DisplayName("isStatic returns true for 'all'")
        void isStaticForAll() {
            assertTrue(evaluator.isStatic("all"));
        }

        @Test
        @DisplayName("isStatic returns true for 'unbounded'")
        void isStaticForUnbounded() {
            assertTrue(evaluator.isStatic("unbounded"));
        }
    }

    @Nested
    @DisplayName("Dynamic XPath Cardinality (WCP-14)")
    class DynamicCardinalityTests {

        @Test
        @DisplayName("isDynamic returns true for XPath '/net/data/itemCount'")
        void isDynamicForXPath() {
            assertTrue(evaluator.isDynamic("/net/data/itemCount"));
        }

        @Test
        @DisplayName("isDynamic returns true for variable '$itemCount'")
        void isDynamicForVariable() {
            assertTrue(evaluator.isDynamic("$itemCount"));
        }

        @Test
        @DisplayName("isDynamic returns false for static integer")
        void isDynamicReturnsFalseForInteger() {
            assertFalse(evaluator.isDynamic("5"));
        }

        @Test
        @DisplayName("evaluate returns default for XPath expression")
        void evaluateXPathReturnsDefault() {
            int result = evaluator.evaluate("/net/data/itemCount", 7);
            assertEquals(7, result);
        }

        @Test
        @DisplayName("extractDynamicReference returns XPath string")
        void extractXPath() {
            String result = evaluator.extractDynamicReference("/net/data/itemCount");
            assertEquals("/net/data/itemCount", result);
        }

        @Test
        @DisplayName("extractDynamicReference returns variable reference")
        void extractVariable() {
            String result = evaluator.extractDynamicReference("$itemCount");
            assertEquals("$itemCount", result);
        }

        @Test
        @DisplayName("extractDynamicReference returns null for static integer")
        void extractReturnsNullForStatic() {
            String result = evaluator.extractDynamicReference("5");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Keyword Cardinality")
    class KeywordCardinalityTests {

        @Test
        @DisplayName("Evaluates 'all' to Integer.MAX_VALUE")
        void evaluateAll() {
            int result = evaluator.evaluate("all", 1);
            assertEquals(Integer.MAX_VALUE, result);
        }

        @Test
        @DisplayName("Evaluates 'unbounded' to Integer.MAX_VALUE")
        void evaluateUnbounded() {
            int result = evaluator.evaluate("unbounded", 1);
            assertEquals(Integer.MAX_VALUE, result);
        }

        @Test
        @DisplayName("Evaluates 'ALL' (uppercase) to Integer.MAX_VALUE")
        void evaluateAllUppercase() {
            int result = evaluator.evaluate("ALL", 1);
            assertEquals(Integer.MAX_VALUE, result);
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("validate passes for valid static config: min=1, max=3, threshold=3")
        void validateValidConfig() {
            assertDoesNotThrow(() ->
                evaluator.validate("1", "3", "3")
            );
        }

        @Test
        @DisplayName("validate throws when min > max")
        void validateMinGreaterThanMax() {
            assertThrows(IllegalArgumentException.class, () ->
                evaluator.validate("5", "3", "3")
            );
        }

        @Test
        @DisplayName("validate throws when threshold > max")
        void validateThresholdGreaterThanMax() {
            assertThrows(IllegalArgumentException.class, () ->
                evaluator.validate("1", "3", "5")
            );
        }

        @Test
        @DisplayName("validate throws when min is negative")
        void validateNegativeMin() {
            assertThrows(IllegalArgumentException.class, () ->
                evaluator.validate("-1", "3", "3")
            );
        }

        @Test
        @DisplayName("validate passes for dynamic expressions (skips validation)")
        void validateDynamicExpression() {
            assertDoesNotThrow(() ->
                evaluator.validate("/net/data/min", "/net/data/max", "3")
            );
        }
    }

    @Nested
    @DisplayName("Context Evaluation (WCP-14 Runtime)")
    class ContextEvaluationTests {

        @Test
        @DisplayName("evaluateWithContext returns map of evaluated values")
        void evaluateWithContext() {
            Map<String, String> expressions = Map.of(
                "min", "2",
                "max", "10"
            );
            Map<String, Object> context = Map.of();

            Map<String, Integer> result = evaluator.evaluateWithContext(expressions, context);

            assertEquals(2, result.get("min"));
            assertEquals(10, result.get("max"));
        }

        @Test
        @DisplayName("evaluateWithContext handles dynamic expressions")
        void evaluateWithContextDynamic() {
            Map<String, String> expressions = Map.of(
                "min", "1",
                "max", "/net/data/itemCount"
            );
            Map<String, Object> context = Map.of();

            Map<String, Integer> result = evaluator.evaluateWithContext(expressions, context);

            assertEquals(1, result.get("min"));
            assertEquals(1, result.get("max")); // default for dynamic
        }
    }
}
