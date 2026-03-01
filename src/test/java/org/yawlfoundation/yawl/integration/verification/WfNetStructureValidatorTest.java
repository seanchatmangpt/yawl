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

package org.yawlfoundation.yawl.integration.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WfNetStructureValidator.
 * Validates the 4 structural properties of a Workflow Net.
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
@DisplayName("WfNetStructureValidator Tests")
class WfNetStructureValidatorTest {

    private final WfNetStructureValidator validator = new WfNetStructureValidator();

    /**
     * Simple valid WF-net: i → t1 → p1 → t2 → o
     */
    @Test
    @DisplayName("Sequence net (WCP-1) is valid WF-net")
    void testSimpleSequenceNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p1", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p1"));
        transitionToPlaces.put("t2", Set.of("o"));

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertTrue(result.isWfNet(), "Sequence net should be valid WF-net");
        assertEquals("i", result.sourcePlaceId().get());
        assertEquals("o", result.sinkPlaceId().get());
        assertTrue(result.isolatedPlaces().isEmpty());
        assertTrue(result.isolatedTransitions().isEmpty());
        assertTrue(result.violations().isEmpty());
    }

    /**
     * Net with no source place
     */
    @Test
    @DisplayName("Net with no source place is invalid")
    void testNoSourcePlace() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p1", Set.of("t1"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p2"));
        transitionToPlaces.put("t2", Set.of("p1"));  // p1 has incoming arc

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.sourcePlaceId().isEmpty());
        assertFalse(result.violations().isEmpty());
    }

    /**
     * Net with multiple source places
     */
    @Test
    @DisplayName("Net with multiple source places is invalid")
    void testMultipleSourcePlaces() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i1", Set.of("t1"));
        placeToTransitions.put("i2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("o"));
        transitionToPlaces.put("t2", Set.of("o"));

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.sourcePlaceId().isEmpty());
        assertFalse(result.violations().isEmpty());
    }

    /**
     * Net with isolated place (not on any i→o path)
     */
    @Test
    @DisplayName("Net with isolated place is invalid")
    void testIsolatedPlace() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p_dead", Set.of());  // Not on path

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("o"));

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.isolatedPlaces().contains("p_dead"));
        assertFalse(result.violations().isEmpty());
    }

    /**
     * Net with isolated transition (not on any i→o path)
     */
    @Test
    @DisplayName("Net with isolated transition is invalid")
    void testIsolatedTransition() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("o"));
        transitionToPlaces.put("t_dead", Set.of("p_dead"));  // Not on path

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.isolatedTransitions().contains("t_dead"));
        assertFalse(result.violations().isEmpty());
    }

    /**
     * Parallel split and join (WCP-2, WCP-3)
     */
    @Test
    @DisplayName("Parallel split/join net is valid WF-net")
    void testParallelSplitJoin() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t_split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t_split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("p_join"));
        transitionToPlaces.put("t2", Set.of("p_join"));
        transitionToPlaces.put("t_join", Set.of("o"));

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertTrue(result.isWfNet());
        assertEquals("i", result.sourcePlaceId().get());
        assertEquals("o", result.sinkPlaceId().get());
    }

    /**
     * Empty net
     */
    @Test
    @DisplayName("Empty net is invalid")
    void testEmptyNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        Map<String, Set<String>> transitionToPlaces = new HashMap<>();

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.sourcePlaceId().isEmpty());
        assertTrue(result.sinkPlaceId().isEmpty());
    }

    /**
     * Net with no sink place
     */
    @Test
    @DisplayName("Net with no sink place is invalid")
    void testNoSinkPlace() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p1", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p1"));
        transitionToPlaces.put("t2", Set.of("p2"));
        transitionToPlaces.put("t3", Set.of("p1"));  // p2 has outgoing arc via implicit

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertFalse(result.isWfNet());
        assertTrue(result.sinkPlaceId().isEmpty());
    }

    /**
     * Complex diamond pattern
     */
    @Test
    @DisplayName("Diamond pattern (split then join) is valid WF-net")
    void testDiamondPattern() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p_top", Set.of("top_task"));
        placeToTransitions.put("p_bottom", Set.of("bottom_task"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p_top", "p_bottom"));
        transitionToPlaces.put("top_task", Set.of("p_merge"));
        transitionToPlaces.put("bottom_task", Set.of("p_merge"));
        transitionToPlaces.put("join", Set.of("o"));

        var result = validator.validate(placeToTransitions, transitionToPlaces);

        assertTrue(result.isWfNet());
        assertEquals("i", result.sourcePlaceId().get());
        assertEquals("o", result.sinkPlaceId().get());
    }

    /**
     * Test null input handling
     */
    @Test
    @DisplayName("Null placeToTransitions throws NullPointerException")
    void testNullPlaceToTransitions() {
        assertThrows(NullPointerException.class,
            () -> validator.validate(null, new HashMap<>()));
    }

    /**
     * Test null input handling
     */
    @Test
    @DisplayName("Null transitionToPlaces throws NullPointerException")
    void testNullTransitionToPlaces() {
        assertThrows(NullPointerException.class,
            () -> validator.validate(new HashMap<>(), null));
    }
}
