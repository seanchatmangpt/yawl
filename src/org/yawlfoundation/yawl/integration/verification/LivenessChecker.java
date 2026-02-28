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

import java.util.*;

/**
 * Checks if a Petri net is live according to the van der Aalst definition.
 *
 * <p>A Petri net is <strong>live</strong> if for every reachable marking M and
 * every transition t, there exists a marking M' reachable from M in which t is enabled.
 *
 * <p>In other words: from any reachable state, every transition can fire again
 * (possibly after other transitions fire).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Explore reachable markings via BFS from initial marking</li>
 *   <li>At each marking, compute which transitions are enabled</li>
 *   <li>Fire enabled transitions and add resulting markings to queue</li>
 *   <li>Track all transitions that fire at least once</li>
 *   <li>Check if all transitions appear in the firing history</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * var liveness = new LivenessChecker();
 * var result = liveness.check(placeToTransitions, transitionToPlaces,
 *     "start_place", 10000);
 * System.out.println("Live: " + result.isLive());
 * if (!result.isLive()) {
 *     result.deadTransitions().forEach(t ->
 *         System.out.println("  Dead: " + t));
 * }
 * </pre>
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
public final class LivenessChecker {

    /**
     * Result of liveness checking.
     *
     * @param isLive true iff every transition can fire from some reachable marking
     * @param deadTransitions transitions that can never fire
     * @param reachableMarkingsChecked how many markings were explored
     */
    public record LivenessResult(
        boolean isLive,
        Set<String> deadTransitions,
        int reachableMarkingsChecked
    ) {}

    /**
     * Checks if a Petri net is live.
     *
     * <p>Performs BFS exploration of reachable markings starting from the initial
     * state (one token at initialPlaceId). Tracks which transitions can fire.
     *
     * @param placeToTransitions map of place ID to output transition IDs
     * @param transitionToPlaces map of transition ID to output place IDs
     * @param initialPlaceId ID of place with initial token
     * @param maxMarkingsToExplore maximum markings to explore (safety limit)
     * @return LivenessResult with liveness status and dead transitions
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if maxMarkingsToExplore is negative
     */
    public LivenessResult check(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String initialPlaceId,
        int maxMarkingsToExplore
    ) {
        Objects.requireNonNull(placeToTransitions, "placeToTransitions must not be null");
        Objects.requireNonNull(transitionToPlaces, "transitionToPlaces must not be null");
        Objects.requireNonNull(initialPlaceId, "initialPlaceId must not be null");

        if (maxMarkingsToExplore < 0) {
            throw new IllegalArgumentException("maxMarkingsToExplore must be non-negative");
        }

        Set<String> firedTransitions = new HashSet<>();
        Set<Marking> visited = new HashSet<>();
        Queue<Marking> queue = new LinkedList<>();

        // Initial marking: one token at initialPlaceId
        Marking initial = new Marking();
        initial.addToken(initialPlaceId, 1);
        queue.add(initial);
        visited.add(initial);

        int markingsChecked = 0;
        while (!queue.isEmpty() && markingsChecked < maxMarkingsToExplore) {
            Marking current = queue.poll();
            markingsChecked++;

            // Find enabled transitions in current marking
            Set<String> enabledTransitions = findEnabledTransitions(
                current, placeToTransitions, transitionToPlaces
            );

            // Fire each enabled transition and explore resulting marking
            for (String transition : enabledTransitions) {
                firedTransitions.add(transition);

                // Fire: remove tokens from inputs, add to outputs
                Marking next = current.copy();
                fireTransition(next, transition, placeToTransitions, transitionToPlaces);

                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        // Dead transitions are those never fired
        Set<String> deadTransitions = new HashSet<>(transitionToPlaces.keySet());
        deadTransitions.removeAll(firedTransitions);

        boolean isLive = deadTransitions.isEmpty();
        return new LivenessResult(
            isLive,
            Collections.unmodifiableSet(deadTransitions),
            markingsChecked
        );
    }

    /**
     * Finds all enabled transitions in a given marking.
     *
     * <p>A transition is enabled if all its input places have at least one token.
     *
     * @param marking current marking state
     * @param placeToTransitions place → transition map
     * @param transitionToPlaces transition → place map
     * @return set of enabled transition IDs
     */
    private Set<String> findEnabledTransitions(
        Marking marking,
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {
        Set<String> enabled = new HashSet<>();

        for (String transition : transitionToPlaces.keySet()) {
            Set<String> inputPlaces = findInputPlaces(transition, placeToTransitions);

            // Transition enabled if all inputs have ≥1 token
            boolean canFire = !inputPlaces.isEmpty() &&
                inputPlaces.stream().allMatch(p -> marking.getTokens(p) > 0);

            if (canFire) {
                enabled.add(transition);
            }
        }

        return enabled;
    }

    /**
     * Fires a transition: removes one token from each input place,
     * adds one token to each output place.
     *
     * @param marking marking to modify (in-place)
     * @param transition transition to fire
     * @param placeToTransitions place → transition map
     * @param transitionToPlaces transition → place map
     */
    private void fireTransition(
        Marking marking,
        String transition,
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {
        // Remove tokens from input places
        Set<String> inputPlaces = findInputPlaces(transition, placeToTransitions);
        for (String place : inputPlaces) {
            int current = marking.getTokens(place);
            if (current > 0) {
                marking.setTokens(place, current - 1);
            }
        }

        // Add tokens to output places
        Set<String> outputPlaces = transitionToPlaces.getOrDefault(transition, Set.of());
        for (String place : outputPlaces) {
            int current = marking.getTokens(place);
            marking.setTokens(place, current + 1);
        }
    }

    /**
     * Finds input places for a transition (reverse lookup).
     *
     * @param transition the transition
     * @param placeToTransitions place → transition map
     * @return set of input place IDs
     */
    private Set<String> findInputPlaces(
        String transition,
        Map<String, Set<String>> placeToTransitions
    ) {
        Set<String> inputs = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : placeToTransitions.entrySet()) {
            if (entry.getValue().contains(transition)) {
                inputs.add(entry.getKey());
            }
        }
        return inputs;
    }

    /**
     * Represents a marking (token distribution) in a Petri net.
     * Immutable for use in visited set.
     */
    private static final class Marking {
        private final Map<String, Integer> tokens = new HashMap<>();

        void addToken(String place, int count) {
            tokens.put(place, tokens.getOrDefault(place, 0) + count);
        }

        void setTokens(String place, int count) {
            if (count <= 0) {
                tokens.remove(place);
            } else {
                tokens.put(place, count);
            }
        }

        int getTokens(String place) {
            return tokens.getOrDefault(place, 0);
        }

        Marking copy() {
            Marking m = new Marking();
            m.tokens.putAll(this.tokens);
            return m;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Marking m)) return false;
            return tokens.equals(m.tokens);
        }

        @Override
        public int hashCode() {
            return tokens.hashCode();
        }

        @Override
        public String toString() {
            return tokens.toString();
        }
    }
}
