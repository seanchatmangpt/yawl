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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link EndpointCardinality}.
 */
@DisplayName("EndpointCardinality")
class EndpointCardinalityTest {

    @Nested
    @DisplayName("min/max values")
    class MinMax {

        @Test
        void zeroOne_minIsZero_maxIsOne() {
            assertThat(EndpointCardinality.ZERO_ONE.getMin(), is(0));
            assertThat(EndpointCardinality.ZERO_ONE.getMax(), is(1));
        }

        @Test
        void oneOne_minIsOne_maxIsOne() {
            assertThat(EndpointCardinality.ONE_ONE.getMin(), is(1));
            assertThat(EndpointCardinality.ONE_ONE.getMax(), is(1));
        }

        @Test
        void zeroMany_minIsZero_maxIsUnbounded() {
            assertThat(EndpointCardinality.ZERO_MANY.getMin(), is(0));
            assertThat(EndpointCardinality.ZERO_MANY.getMax(), is(EndpointCardinality.UNBOUNDED));
        }

        @Test
        void oneMany_minIsOne_maxIsUnbounded() {
            assertThat(EndpointCardinality.ONE_MANY.getMin(), is(1));
            assertThat(EndpointCardinality.ONE_MANY.getMax(), is(EndpointCardinality.UNBOUNDED));
        }
    }

    @Nested
    @DisplayName("semantic predicates")
    class Predicates {

        @Test
        void isMandatory_onlyForOneVariants() {
            assertFalse(EndpointCardinality.ZERO_ONE.isMandatory());
            assertTrue(EndpointCardinality.ONE_ONE.isMandatory());
            assertFalse(EndpointCardinality.ZERO_MANY.isMandatory());
            assertTrue(EndpointCardinality.ONE_MANY.isMandatory());
        }

        @Test
        void isMultiValued_onlyForManyVariants() {
            assertFalse(EndpointCardinality.ZERO_ONE.isMultiValued());
            assertFalse(EndpointCardinality.ONE_ONE.isMultiValued());
            assertTrue(EndpointCardinality.ZERO_MANY.isMultiValued());
            assertTrue(EndpointCardinality.ONE_MANY.isMultiValued());
        }

        @Test
        void isUnbounded_onlyForManyVariants() {
            assertFalse(EndpointCardinality.ZERO_ONE.isUnbounded());
            assertFalse(EndpointCardinality.ONE_ONE.isUnbounded());
            assertTrue(EndpointCardinality.ZERO_MANY.isUnbounded());
            assertTrue(EndpointCardinality.ONE_MANY.isUnbounded());
        }
    }

    @Nested
    @DisplayName("accepts(count)")
    class Accepts {

        @Test
        void zeroOne_accepts_0and1_rejects_2() {
            assertTrue(EndpointCardinality.ZERO_ONE.accepts(0));
            assertTrue(EndpointCardinality.ZERO_ONE.accepts(1));
            assertFalse(EndpointCardinality.ZERO_ONE.accepts(2));
        }

        @Test
        void oneOne_accepts_only1() {
            assertFalse(EndpointCardinality.ONE_ONE.accepts(0));
            assertTrue(EndpointCardinality.ONE_ONE.accepts(1));
            assertFalse(EndpointCardinality.ONE_ONE.accepts(2));
        }

        @Test
        void zeroMany_accepts_anyNonNegative() {
            assertTrue(EndpointCardinality.ZERO_MANY.accepts(0));
            assertTrue(EndpointCardinality.ZERO_MANY.accepts(1));
            assertTrue(EndpointCardinality.ZERO_MANY.accepts(1000));
        }

        @Test
        void oneMany_requires_atLeastOne() {
            assertFalse(EndpointCardinality.ONE_MANY.accepts(0));
            assertTrue(EndpointCardinality.ONE_MANY.accepts(1));
            assertTrue(EndpointCardinality.ONE_MANY.accepts(500));
        }
    }

    @Nested
    @DisplayName("notation strings")
    class Notation {

        @Test
        void canonicalNotations() {
            assertThat(EndpointCardinality.ZERO_ONE.getNotation(), is("0..1"));
            assertThat(EndpointCardinality.ONE_ONE.getNotation(), is("1..1"));
            assertThat(EndpointCardinality.ZERO_MANY.getNotation(), is("0..*"));
            assertThat(EndpointCardinality.ONE_MANY.getNotation(), is("1..*"));
        }

        @Test
        void toString_returnsNotation() {
            assertThat(EndpointCardinality.ONE_MANY.toString(), is("1..*"));
        }
    }

    @Nested
    @DisplayName("fromNotation()")
    class FromNotation {

        @Test
        void canonicalStrings_parse() {
            assertThat(EndpointCardinality.fromNotation("0..1"), is(EndpointCardinality.ZERO_ONE));
            assertThat(EndpointCardinality.fromNotation("1..1"), is(EndpointCardinality.ONE_ONE));
            assertThat(EndpointCardinality.fromNotation("0..*"), is(EndpointCardinality.ZERO_MANY));
            assertThat(EndpointCardinality.fromNotation("1..*"), is(EndpointCardinality.ONE_MANY));
        }

        @Test
        void aliases_parse() {
            assertThat(EndpointCardinality.fromNotation("mandatory"), is(EndpointCardinality.ONE_ONE));
            assertThat(EndpointCardinality.fromNotation("optional"), is(EndpointCardinality.ZERO_ONE));
            assertThat(EndpointCardinality.fromNotation("*"), is(EndpointCardinality.ZERO_MANY));
            assertThat(EndpointCardinality.fromNotation("+"), is(EndpointCardinality.ONE_MANY));
        }

        @Test
        void unknownString_defaultsToZeroOne() {
            assertThat(EndpointCardinality.fromNotation("unknown-value"), is(EndpointCardinality.ZERO_ONE));
        }

        @Test
        void nullInput_defaultsToZeroOne() {
            assertThat(EndpointCardinality.fromNotation(null), is(EndpointCardinality.ZERO_ONE));
        }
    }
}
