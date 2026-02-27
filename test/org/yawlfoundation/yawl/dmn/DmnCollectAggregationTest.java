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

package org.yawlfoundation.yawl.dmn;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmnCollectAggregation}.
 */
@DisplayName("DmnCollectAggregation")
class DmnCollectAggregationTest {

    private static final List<Double> SCORES = List.of(72.5, 88.0, 65.0, 91.3);
    private static final List<Double> SINGLE  = List.of(42.0);
    private static final List<Double> EMPTY   = Collections.emptyList();

    // -----------------------------------------------------------------------
    // SUM
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("SUM (C+)")
    class SumTests {

        @Test
        void aggregatesMultipleValues() {
            double result = DmnCollectAggregation.SUM.aggregate(SCORES);
            assertThat(result, is(closeTo(316.8, 1e-9)));
        }

        @Test
        void singleValue_returnsThatValue() {
            assertThat(DmnCollectAggregation.SUM.aggregate(SINGLE), is(42.0));
        }

        @Test
        void emptyCollection_returnsZero() {
            assertThat(DmnCollectAggregation.SUM.aggregate(EMPTY), is(0.0));
        }

        @Test
        void symbol_isCPlus() {
            assertThat(DmnCollectAggregation.SUM.getDmnSymbol(), is("C+"));
        }
    }

    // -----------------------------------------------------------------------
    // MIN
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("MIN (C<)")
    class MinTests {

        @Test
        void returnsSmallestValue() {
            double result = DmnCollectAggregation.MIN.aggregate(SCORES);
            assertThat(result, is(65.0));
        }

        @Test
        void singleValue_returnsThatValue() {
            assertThat(DmnCollectAggregation.MIN.aggregate(SINGLE), is(42.0));
        }

        @Test
        void emptyCollection_returnsPositiveInfinity() {
            assertThat(DmnCollectAggregation.MIN.aggregate(EMPTY),
                    is(Double.POSITIVE_INFINITY));
        }

        @Test
        void negativeValues_handledCorrectly() {
            assertThat(DmnCollectAggregation.MIN.aggregate(List.of(-5.0, -10.0, 3.0)),
                    is(-10.0));
        }

        @Test
        void symbol_isCLessThan() {
            assertThat(DmnCollectAggregation.MIN.getDmnSymbol(), is("C<"));
        }
    }

    // -----------------------------------------------------------------------
    // MAX
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("MAX (C>)")
    class MaxTests {

        @Test
        void returnsLargestValue() {
            double result = DmnCollectAggregation.MAX.aggregate(SCORES);
            assertThat(result, is(91.3));
        }

        @Test
        void singleValue_returnsThatValue() {
            assertThat(DmnCollectAggregation.MAX.aggregate(SINGLE), is(42.0));
        }

        @Test
        void emptyCollection_returnsNegativeInfinity() {
            assertThat(DmnCollectAggregation.MAX.aggregate(EMPTY),
                    is(Double.NEGATIVE_INFINITY));
        }

        @Test
        void negativeValues_handledCorrectly() {
            assertThat(DmnCollectAggregation.MAX.aggregate(List.of(-5.0, -10.0, -1.0)),
                    is(-1.0));
        }

        @Test
        void symbol_isCGreaterThan() {
            assertThat(DmnCollectAggregation.MAX.getDmnSymbol(), is("C>"));
        }
    }

    // -----------------------------------------------------------------------
    // COUNT
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("COUNT (C#)")
    class CountTests {

        @Test
        void returnsCollectionSize() {
            assertThat(DmnCollectAggregation.COUNT.aggregate(SCORES), is(4.0));
        }

        @Test
        void singleValue_returnsOne() {
            assertThat(DmnCollectAggregation.COUNT.aggregate(SINGLE), is(1.0));
        }

        @Test
        void emptyCollection_returnsZero() {
            assertThat(DmnCollectAggregation.COUNT.aggregate(EMPTY), is(0.0));
        }

        @Test
        void symbol_isCHash() {
            assertThat(DmnCollectAggregation.COUNT.getDmnSymbol(), is("C#"));
        }
    }

    // -----------------------------------------------------------------------
    // aggregateDoubles()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("aggregateDoubles()")
    class AggregateDoublesTests {

        @Test
        void nonEmpty_returnsPresent() {
            OptionalDouble result = DmnCollectAggregation.SUM.aggregateDoubles(SCORES);
            assertTrue(result.isPresent());
            assertThat(result.getAsDouble(), is(closeTo(316.8, 1e-9)));
        }

        @Test
        void empty_returnsEmpty() {
            OptionalDouble result = DmnCollectAggregation.SUM.aggregateDoubles(EMPTY);
            assertTrue(result.isEmpty());
        }
    }

    // -----------------------------------------------------------------------
    // fromValue()
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("fromValue()")
    class FromValueTests {

        @Test
        void symbolsResolve() {
            assertThat(DmnCollectAggregation.fromValue("C+"), is(DmnCollectAggregation.SUM));
            assertThat(DmnCollectAggregation.fromValue("C<"), is(DmnCollectAggregation.MIN));
            assertThat(DmnCollectAggregation.fromValue("C>"), is(DmnCollectAggregation.MAX));
            assertThat(DmnCollectAggregation.fromValue("C#"), is(DmnCollectAggregation.COUNT));
        }

        @Test
        void namesResolve_caseInsensitive() {
            assertThat(DmnCollectAggregation.fromValue("sum"),   is(DmnCollectAggregation.SUM));
            assertThat(DmnCollectAggregation.fromValue("MIN"),   is(DmnCollectAggregation.MIN));
            assertThat(DmnCollectAggregation.fromValue("Max"),   is(DmnCollectAggregation.MAX));
            assertThat(DmnCollectAggregation.fromValue("COUNT"), is(DmnCollectAggregation.COUNT));
        }

        @Test
        void unknownValue_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnCollectAggregation.fromValue("AVERAGE"));
        }

        @Test
        void nullValue_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class,
                    () -> DmnCollectAggregation.fromValue(null));
        }
    }

    // -----------------------------------------------------------------------
    // isNumericAggregation()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isNumericAggregation() is false only for COUNT")
    void isNumericAggregation() {
        assertTrue(DmnCollectAggregation.SUM.isNumericAggregation());
        assertTrue(DmnCollectAggregation.MIN.isNumericAggregation());
        assertTrue(DmnCollectAggregation.MAX.isNumericAggregation());
        assertFalse(DmnCollectAggregation.COUNT.isNumericAggregation());
    }
}
