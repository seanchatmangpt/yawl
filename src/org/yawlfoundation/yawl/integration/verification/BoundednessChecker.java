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
 * Checks if a Petri net is k-bounded according to the van der Aalst definition.
 *
 * <p>A Petri net is <strong>k-bounded</strong> if in every reachable marking,
 * every place holds at most k tokens. A <strong>safe</strong> net is 1-bounded.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Explore reachable markings via BFS from initial marking</li>
 *   <li>Track maximum token count observed in each place</li>
 *   <li>If any place exceeds maxK, net is unbounded</li>
 *   <li>Return overall boundedness and per-place max tokens</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * var boundedness = new BoundednessChecker();
 * var result = boundedness.check(placeToTransitions, transitionToPlaces,
 *     "start_place", 100, 10000);
 * System.out.println("Safe: " + result.isSafe());
 * System.out.println("Bounded: " + result.isBounded());
 * result.maxTokensPerPlace().forEach((place, max) ->
 *     System.out.println("  " + place + ": " + max));
 * </pre>
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
public final class BoundednessChecker {

    /**
     * Result of boundedness checking.
     *
     * @param isBounded true iff no place exceeds maxK in any reachable marking
     * @param isSafe true iff net is 1-bounded (safe net)
     * @param boundK maximum tokens observed in any place (-1 = unbounded)
     * @param maxTokensPerPlace map of place ID to maximum tokens observed
     * @param unboundedPlaces places that exceeded maxK
     */
    public record BoundednessResult(
        boolean isBounded,
        boolean isSafe,
        int boundK,
        Map<String, Integer> maxTokensPerPlace,
        Set<String> unboundedPlaces
    ) {}

    /**
     * Checks if a Petri net is k-bounded.
     *
     * <p>Performs BFS exploration of reachable markings. Stops when:
     * <ul>
     *   <li>All reachable markings have been explored, OR</li>
     *   <li>Some place exceeds maxK tokens, OR</li>
     *   <li>maxMarkingsToExplore limit is reached</li>
     * </ul>
     *
     * @param placeToTransitions map of place ID to output transition IDs
     * @param transitionToPlaces map of transition ID to output place IDs
     * @param initialPlaceId ID of place with initial token
     * @param maxK maximum allowed tokens per place (e.g., 100 for safety check)
     * @param maxMarkingsToExplore maximum markings to explore (safety limit)
     * @return BoundednessResult with boundedness status
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if maxK or maxMarkingsToExplore is negative
     */
    public BoundednessResult check(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        String initialPlaceId,
        int maxK,
        int maxMarkingsToExplore
    ) {
        Objects.requireNonNull(placeToTransitions, "placeToTransitions must not be null");
        Objects.requireNonNull(transitionToPlaces, "transitionToPlaces must not be null");
        Objects.requireNonNull(initialPlaceId, "initialPlaceId must not be null");

        if (maxK < 0) {
            throw new IllegalArgumentException("maxK must be non-negative");
        }
        if (maxMarkingsToExplore < 0) {
            throw new IllegalArgumentException("maxMarkingsToExplore must be non-negative");
        }

        Map<String, Integer> maxTokens = new HashMap<>();
        Set<String> unboundedPlaces = new HashSet<>();
        Set<Marking> visited = new HashSet<>();
        Queue<Marking> queue = new LinkedList<>();

        // Initial marking: one token at initialPlaceId
        Marking initial = new Marking();
        initial.addToken(initialPlaceId, 1);
        maxTokens.put(initialPlaceId, 1);
        queue.add(initial);
        visited.add(initial);

        int markingsChecked = 0;
        boolean isBounded = true;

        while (!queue.isEmpty() && markingsChecked < maxMarkingsToExplore && isBounded) {
            Marking current = queue.poll();
            markingsChecked++;

            // Find enabled transitions in current marking
            Set<String> enabledTransitions = findEnabledTransitions(
                current, placeToTransitions, transitionToPlaces
            );

            // Fire each enabled transition and explore resulting marking
            for (String transition : enabledTransitions) {
                // Fire: remove tokens from inputs, add to outputs
                Marking next = current.copy();
                fireTransition(next, transition, placeToTransitions, transitionToPlaces);

                // Update max tokens
                for (Map.Entry<String, Integer> entry : next.getTokens().entrySet()) {
                    String place = entry.getKey();
                    int tokens = entry.getValue();
                    int currentMax = maxTokens.getOrDefault(place, 0);
                    if (tokens > currentMax) {
                        maxTokens.put(place, tokens);
                        if (tokens > maxK) {
                            unboundedPlaces.add(place);
                            isBounded = false;
                        }
                    }
                }

                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }

        boolean isSafe = isBounded && maxTokens.values().stream().allMatch(t -> t <= 1);
        int boundK = isBounded ? maxTokens.values().stream().max(Integer::compareTo).orElse(0) : -1;

        return new BoundednessResult(
            isBounded,
            isSafe,
            boundK,
            Collections.unmodifiableMap(maxTokens),
            Collections.unmodifiableSet(unboundedPlaces)
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

        Map<String, Integer> getTokens() {
            return new HashMap<>(tokens);
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
