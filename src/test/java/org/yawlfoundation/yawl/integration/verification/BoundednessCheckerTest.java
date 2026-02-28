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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BoundednessChecker.
 * Checks if places have bounded token counts.
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
@DisplayName("BoundednessChecker Tests")
class BoundednessCheckerTest {

    private final BoundednessChecker checker = new BoundednessChecker();

    /**
     * Safe sequence net: never has more than 1 token in any place
     */
    @Test
    @DisplayName("Safe sequence net is 1-bounded")
    void testSafeSequenceNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p1", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p1"));
        transitionToPlaces.put("t2", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertTrue(result.isSafe(), "Sequence net should be safe (1-bounded)");
        assertEquals(1, result.boundK());
        assertTrue(result.unboundedPlaces().isEmpty());
    }

    /**
     * Parallel net: tokens split, each place gets 1
     */
    @Test
    @DisplayName("Parallel safe net is 1-bounded")
    void testParallelSafeNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("p_join"));
        transitionToPlaces.put("t2", Set.of("p_join"));
        transitionToPlaces.put("join", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertTrue(result.isSafe());
        assertEquals(1, result.boundK());
    }

    /**
     * Cyclic net that accumulates tokens (unbounded)
     */
    @Test
    @DisplayName("Cyclic net that accumulates tokens is unbounded")
    void testUnboundedCyclicNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("loop"));
        placeToTransitions.put("p", Set.of("loop"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        // Each time t fires: remove 1 token from p, add 2 tokens to p
        // This is the classic unbounded pattern
        transitionToPlaces.put("loop", Set.of("p", "p"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 10, 1000);

        assertFalse(result.isBounded(), "Cyclic net should be unbounded");
        assertTrue(result.unboundedPlaces().contains("p"));
        assertEquals(-1, result.boundK());
    }

    /**
     * Single transition
     */
    @Test
    @DisplayName("Single transition net is safe")
    void testSingleTransition() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertTrue(result.isSafe());
        assertEquals(1, result.boundK());
    }

    /**
     * Empty net
     */
    @Test
    @DisplayName("Empty net is bounded")
    void testEmptyNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        Map<String, Set<String>> transitionToPlaces = new HashMap<>();

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertTrue(result.isSafe());
    }

    /**
     * Net where multiple tokens accumulate at join point
     */
    @Test
    @DisplayName("Join point with multiple inputs can accumulate tokens")
    void testJoinAccumulation() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("p_join"));
        transitionToPlaces.put("t2", Set.of("p_join"));
        // p_join can have at most 2 tokens (from t1 and t2)
        transitionToPlaces.put("join", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertFalse(result.isSafe(), "Join point should have 2 tokens");
        assertEquals(2, result.boundK());
    }

    /**
     * Test max tokens tracking
     */
    @Test
    @DisplayName("Tracks max tokens per place correctly")
    void testMaxTokensTracking() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("o"));
        transitionToPlaces.put("t2", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 1000);

        assertTrue(result.isBounded());
        assertEquals(1, result.maxTokensPerPlace().getOrDefault("i", 0));
        assertEquals(1, result.maxTokensPerPlace().getOrDefault("p1", 0));
        assertEquals(1, result.maxTokensPerPlace().getOrDefault("p2", 0));
        assertEquals(2, result.maxTokensPerPlace().getOrDefault("o", 0),
                     "Final place should have 2 tokens from split");
    }

    /**
     * Test respects maxK threshold
     */
    @Test
    @DisplayName("Reports unbounded when exceeds maxK")
    void testMaxKThreshold() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("loop"));
        placeToTransitions.put("p", Set.of("loop"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        // Each fire: consume 1 from p, add 2 to p
        transitionToPlaces.put("loop", Set.of("p", "p"));

        // Set very low maxK to trigger quickly
        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 3, 1000);

        assertFalse(result.isBounded());
        assertTrue(result.unboundedPlaces().contains("p"));
    }

    /**
     * Test maxMarkings limit
     */
    @Test
    @DisplayName("Respects maxMarkingsToExplore limit")
    void testMaxMarkingsLimit() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("loop"));
        placeToTransitions.put("p", Set.of("loop"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("loop", Set.of("p"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 100, 5);

        // Should stop exploring after 5 markings
        assertTrue(result.isBounded()); // Stopped early, not unbounded
    }

    /**
     * Test null input
     */
    @Test
    @DisplayName("Null placeToTransitions throws NullPointerException")
    void testNullPlaceToTransitions() {
        assertThrows(NullPointerException.class,
            () -> checker.check(null, new HashMap<>(), "i", 100, 1000));
    }

    /**
     * Test null input
     */
    @Test
    @DisplayName("Null transitionToPlaces throws NullPointerException")
    void testNullTransitionToPlaces() {
        assertThrows(NullPointerException.class,
            () -> checker.check(new HashMap<>(), null, "i", 100, 1000));
    }

    /**
     * Test null initial place
     */
    @Test
    @DisplayName("Null initialPlaceId throws NullPointerException")
    void testNullInitialPlace() {
        assertThrows(NullPointerException.class,
            () -> checker.check(new HashMap<>(), new HashMap<>(), null, 100, 1000));
    }

    /**
     * Test negative maxK
     */
    @Test
    @DisplayName("Negative maxK throws IllegalArgumentException")
    void testNegativeMaxK() {
        assertThrows(IllegalArgumentException.class,
            () -> checker.check(new HashMap<>(), new HashMap<>(), "i", -1, 1000));
    }

    /**
     * Test negative maxMarkings
     */
    @Test
    @DisplayName("Negative maxMarkingsToExplore throws IllegalArgumentException")
    void testNegativeMaxMarkings() {
        assertThrows(IllegalArgumentException.class,
            () -> checker.check(new HashMap<>(), new HashMap<>(), "i", 100, -1));
    }
}
