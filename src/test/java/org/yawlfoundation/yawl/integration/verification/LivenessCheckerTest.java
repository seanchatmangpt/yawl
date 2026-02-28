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
 * Tests for LivenessChecker.
 * Checks if every transition can fire from some reachable marking.
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
@DisplayName("LivenessChecker Tests")
class LivenessCheckerTest {

    private final LivenessChecker checker = new LivenessChecker();

    /**
     * Simple live sequence net: i → t1 → p1 → t2 → o
     */
    @Test
    @DisplayName("Live sequence net has all transitions firing")
    void testLiveSequenceNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p1", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p1"));
        transitionToPlaces.put("t2", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive(), "Sequence net should be live");
        assertTrue(result.deadTransitions().isEmpty());
        assertEquals(2, result.reachableMarkingsChecked());
    }

    /**
     * Net with dead transition (unreachable input place)
     */
    @Test
    @DisplayName("Net with dead transition is not live")
    void testNetWithDeadTransition() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));
        placeToTransitions.put("p_dead", Set.of("t_dead"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("o"));
        transitionToPlaces.put("t_dead", Set.of("p_dead"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertFalse(result.isLive(), "Net should not be live (t_dead never fires)");
        assertTrue(result.deadTransitions().contains("t_dead"));
    }

    /**
     * Parallel net with synchronization: both branches must fire
     */
    @Test
    @DisplayName("Parallel live net has both branches firing")
    void testParallelLiveNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("p_join"));
        transitionToPlaces.put("t2", Set.of("p_join"));
        transitionToPlaces.put("join", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive(), "Parallel net should be live");
        assertTrue(result.deadTransitions().isEmpty());
    }

    /**
     * Cyclic net (livelock) - all transitions still fire, but may not terminate
     */
    @Test
    @DisplayName("Cyclic net with exit is live")
    void testCyclicNetWithExit() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("loop"));
        placeToTransitions.put("p_loop", Set.of("loop", "exit"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("loop", Set.of("p_loop"));
        transitionToPlaces.put("exit", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive(), "Cyclic net should be live (can reach all transitions)");
        assertTrue(result.deadTransitions().isEmpty());
    }

    /**
     * Net with AND-join: all inputs must have tokens
     */
    @Test
    @DisplayName("AND-join net is live if all inputs reachable")
    void testAndJoinLiveNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("split"));
        placeToTransitions.put("p1", Set.of("t1"));
        placeToTransitions.put("p2", Set.of("t2"));
        placeToTransitions.put("p_join_in1", Set.of("and_join"));
        placeToTransitions.put("p_join_in2", Set.of("and_join"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("split", Set.of("p1", "p2"));
        transitionToPlaces.put("t1", Set.of("p_join_in1"));
        transitionToPlaces.put("t2", Set.of("p_join_in2"));
        transitionToPlaces.put("and_join", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive(), "AND-join net should be live");
        assertTrue(result.deadTransitions().isEmpty());
    }

    /**
     * Simple single transition
     */
    @Test
    @DisplayName("Single transition net is live")
    void testSingleTransition() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("t1"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("o"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive());
        assertTrue(result.deadTransitions().isEmpty());
    }

    /**
     * Empty net (no transitions)
     */
    @Test
    @DisplayName("Empty net (no transitions) is live")
    void testEmptyNet() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        Map<String, Set<String>> transitionToPlaces = new HashMap<>();

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 1000);

        assertTrue(result.isLive()); // No dead transitions = live
        assertTrue(result.deadTransitions().isEmpty());
    }

    /**
     * Test max markings limit
     */
    @Test
    @DisplayName("Respects maxMarkingsToExplore limit")
    void testMaxMarkingsLimit() {
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("i", Set.of("loop"));
        placeToTransitions.put("p", Set.of("loop"));

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("loop", Set.of("p"));

        var result = checker.check(placeToTransitions, transitionToPlaces, "i", 5);

        // Should stop at 5 markings
        assertTrue(result.reachableMarkingsChecked() <= 5);
    }

    /**
     * Test null input
     */
    @Test
    @DisplayName("Null placeToTransitions throws NullPointerException")
    void testNullPlaceToTransitions() {
        assertThrows(NullPointerException.class,
            () -> checker.check(null, new HashMap<>(), "i", 1000));
    }

    /**
     * Test null input
     */
    @Test
    @DisplayName("Null transitionToPlaces throws NullPointerException")
    void testNullTransitionToPlaces() {
        assertThrows(NullPointerException.class,
            () -> checker.check(new HashMap<>(), null, "i", 1000));
    }

    /**
     * Test null initial place
     */
    @Test
    @DisplayName("Null initialPlaceId throws NullPointerException")
    void testNullInitialPlace() {
        assertThrows(NullPointerException.class,
            () -> checker.check(new HashMap<>(), new HashMap<>(), null, 1000));
    }

    /**
     * Test negative maxMarkings
     */
    @Test
    @DisplayName("Negative maxMarkingsToExplore throws IllegalArgumentException")
    void testNegativeMaxMarkings() {
        assertThrows(IllegalArgumentException.class,
            () -> checker.check(new HashMap<>(), new HashMap<>(), "i", -1));
    }
}
