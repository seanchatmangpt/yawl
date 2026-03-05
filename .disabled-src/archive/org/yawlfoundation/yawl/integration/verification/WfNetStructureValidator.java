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
 * Validates the 4 structural properties of a Workflow Net (WF-net)
 * as defined by van der Aalst (1997).
 *
 * <p>A WF-net must satisfy:
 * <ol>
 *   <li>Exactly one source place i (no incoming arcs)</li>
 *   <li>Exactly one sink place o (no outgoing arcs)</li>
 *   <li>Every place p lies on a directed path from i to o</li>
 *   <li>Every transition t lies on a directed path from i to o</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * var validator = new WfNetStructureValidator();
 * var result = validator.validate(placeToTransitions, transitionToPlaces);
 * if (result.isWfNet()) {
 *     System.out.println("Valid WF-net structure!");
 * } else {
 *     result.violations().forEach(System.out::println);
 * }
 * </pre>
 *
 * @author YAWL Foundation (Engineer T3)
 * @version 6.0.0
 */
public final class WfNetStructureValidator {

    /**
     * Result of WF-net structure validation.
     *
     * @param isWfNet true iff all 4 structural properties hold
     * @param sourcePlaceId the unique source place (if valid)
     * @param sinkPlaceId the unique sink place (if valid)
     * @param isolatedPlaces places not on any i→o path
     * @param isolatedTransitions transitions not on any i→o path
     * @param violations human-readable violation descriptions
     */
    public record WfNetStructureResult(
        boolean isWfNet,
        Optional<String> sourcePlaceId,
        Optional<String> sinkPlaceId,
        Set<String> isolatedPlaces,
        Set<String> isolatedTransitions,
        List<String> violations
    ) {}

    /**
     * Validates the structural properties of a Workflow Net.
     *
     * @param placeToTransitions map of place ID to set of output transition IDs
     * @param transitionToPlaces map of transition ID to set of output place IDs
     * @return WfNetStructureResult with validation status and violations
     * @throws NullPointerException if either parameter is null
     */
    public WfNetStructureResult validate(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {
        Objects.requireNonNull(placeToTransitions, "placeToTransitions must not be null");
        Objects.requireNonNull(transitionToPlaces, "transitionToPlaces must not be null");

        List<String> violations = new ArrayList<>();
        Optional<String> sourcePlaceId = Optional.empty();
        Optional<String> sinkPlaceId = Optional.empty();
        Set<String> isolatedPlaces = new HashSet<>();
        Set<String> isolatedTransitions = new HashSet<>();

        // Property 1: Find exactly one source place (no incoming arcs)
        Set<String> sourcePlaces = findSourcePlaces(placeToTransitions, transitionToPlaces);
        if (sourcePlaces.isEmpty()) {
            violations.add("No source place found (place with no incoming arcs)");
        } else if (sourcePlaces.size() > 1) {
            violations.add("Multiple source places found: " + sourcePlaces);
        } else {
            sourcePlaceId = Optional.of(sourcePlaces.iterator().next());
        }

        // Property 2: Find exactly one sink place (no outgoing arcs)
        Set<String> sinkPlaces = findSinkPlaces(placeToTransitions);
        if (sinkPlaces.isEmpty()) {
            violations.add("No sink place found (place with no outgoing arcs)");
        } else if (sinkPlaces.size() > 1) {
            violations.add("Multiple sink places found: " + sinkPlaces);
        } else {
            sinkPlaceId = Optional.of(sinkPlaces.iterator().next());
        }

        // Properties 3 & 4: Check connectivity only if source and sink exist
        if (sourcePlaceId.isPresent() && sinkPlaceId.isPresent()) {
            String source = sourcePlaceId.get();
            String sink = sinkPlaceId.get();

            // Forward reachability from source
            Set<String> forwardPlaces = bfsPlaces(source, placeToTransitions, transitionToPlaces, true);
            Set<String> forwardTransitions = bfsTransitions(source, placeToTransitions, transitionToPlaces, true);

            // Backward reachability from sink
            Set<String> backwardPlaces = bfsPlaces(sink, transitionToPlaces, placeToTransitions, false);
            Set<String> backwardTransitions = bfsTransitions(sink, transitionToPlaces, placeToTransitions, false);

            // Property 3: All places on i→o path
            Set<String> allPlaces = new HashSet<>(placeToTransitions.keySet());
            Set<String> unreachablePlaces = new HashSet<>(allPlaces);
            unreachablePlaces.removeAll(forwardPlaces);
            unreachablePlaces.retainAll(backwardPlaces);
            Set<String> onPath = new HashSet<>(forwardPlaces);
            onPath.retainAll(backwardPlaces);

            isolatedPlaces = new HashSet<>(allPlaces);
            isolatedPlaces.removeAll(onPath);

            if (!isolatedPlaces.isEmpty()) {
                violations.add("Places not on i→o path: " + isolatedPlaces);
            }

            // Property 4: All transitions on i→o path
            Set<String> allTransitions = new HashSet<>(transitionToPlaces.keySet());
            Set<String> onPathTransitions = new HashSet<>(forwardTransitions);
            onPathTransitions.retainAll(backwardTransitions);

            isolatedTransitions = new HashSet<>(allTransitions);
            isolatedTransitions.removeAll(onPathTransitions);

            if (!isolatedTransitions.isEmpty()) {
                violations.add("Transitions not on i→o path: " + isolatedTransitions);
            }
        }

        boolean isWfNet = violations.isEmpty();
        return new WfNetStructureResult(
            isWfNet,
            sourcePlaceId,
            sinkPlaceId,
            Collections.unmodifiableSet(isolatedPlaces),
            Collections.unmodifiableSet(isolatedTransitions),
            Collections.unmodifiableList(violations)
        );
    }

    /**
     * Finds all source places (places with no incoming arcs).
     *
     * @param placeToTransitions forward adjacency map
     * @param transitionToPlaces reverse mapping to find inputs
     * @return set of source place IDs
     */
    private Set<String> findSourcePlaces(
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {
        Set<String> sources = new HashSet<>();
        Set<String> hasInput = new HashSet<>();

        // Collect all places that have incoming arcs
        for (Set<String> outputs : transitionToPlaces.values()) {
            hasInput.addAll(outputs);
        }

        // Source places are those in placeToTransitions that don't have input
        for (String place : placeToTransitions.keySet()) {
            if (!hasInput.contains(place)) {
                sources.add(place);
            }
        }

        return sources;
    }

    /**
     * Finds all sink places (places with no outgoing arcs).
     *
     * @param placeToTransitions forward adjacency map
     * @return set of sink place IDs
     */
    private Set<String> findSinkPlaces(Map<String, Set<String>> placeToTransitions) {
        Set<String> sinks = new HashSet<>();
        Set<String> allPlaces = new HashSet<>(placeToTransitions.keySet());

        // Also consider places that only exist as outputs (not in keys)
        placeToTransitions.values().forEach(allPlaces::addAll);

        for (String place : allPlaces) {
            Set<String> outputs = placeToTransitions.getOrDefault(place, Set.of());
            if (outputs.isEmpty()) {
                sinks.add(place);
            }
        }

        return sinks;
    }

    /**
     * BFS forward (or backward with reversed maps) to find all reachable places.
     *
     * @param startPlace starting place
     * @param placeToNextLevel forward map (place → transitions or reversed)
     * @param transitionToNextLevel backward map (transition → places or reversed)
     * @param forward true for forward, false for backward
     * @return set of reachable places
     */
    private Set<String> bfsPlaces(
        String startPlace,
        Map<String, Set<String>> placeToNextLevel,
        Map<String, Set<String>> transitionToNextLevel,
        boolean forward
    ) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startPlace);
        reachable.add(startPlace);

        while (!queue.isEmpty()) {
            String place = queue.poll();
            Set<String> nextTransitions = placeToNextLevel.getOrDefault(place, Set.of());

            for (String transition : nextTransitions) {
                Set<String> nextPlaces = transitionToNextLevel.getOrDefault(transition, Set.of());
                for (String nextPlace : nextPlaces) {
                    if (reachable.add(nextPlace)) {
                        queue.add(nextPlace);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * BFS to find all reachable transitions.
     *
     * @param startPlace starting place
     * @param placeToTransitions forward map
     * @param transitionToPlaces backward map
     * @param forward true for forward, false for backward
     * @return set of reachable transitions
     */
    private Set<String> bfsTransitions(
        String startPlace,
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces,
        boolean forward
    ) {
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startPlace);
        Set<String> visitedPlaces = new HashSet<>();
        visitedPlaces.add(startPlace);

        while (!queue.isEmpty()) {
            String place = queue.poll();
            Set<String> nextTransitions = placeToTransitions.getOrDefault(place, Set.of());
            reachable.addAll(nextTransitions);

            for (String transition : nextTransitions) {
                Set<String> nextPlaces = transitionToPlaces.getOrDefault(transition, Set.of());
                for (String nextPlace : nextPlaces) {
                    if (visitedPlaces.add(nextPlace)) {
                        queue.add(nextPlace);
                    }
                }
            }
        }

        return reachable;
    }
}
