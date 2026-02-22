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

package org.yawlfoundation.yawl.integration.processmining.pnml;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Complete Petri Net process parsed from PNML.
 * Aggregate of places, transitions, and arcs forming a process model.
 * Immutable record.
 *
 * @param id          Unique identifier for the process
 * @param name        Human-readable process name
 * @param places      List of all places in the net
 * @param transitions List of all transitions in the net
 * @param arcs        List of all arcs connecting places and transitions
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record PnmlProcess(
        String id,
        String name,
        List<PnmlPlace> places,
        List<PnmlTransition> transitions,
        List<PnmlArc> arcs
) {

    /**
     * Validates that id, name, places, transitions, and arcs are non-null.
     * Lists may be empty.
     */
    public PnmlProcess {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Process id cannot be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Process name cannot be null or empty");
        }
        if (places == null) {
            throw new IllegalArgumentException("Process places list cannot be null");
        }
        if (transitions == null) {
            throw new IllegalArgumentException("Process transitions list cannot be null");
        }
        if (arcs == null) {
            throw new IllegalArgumentException("Process arcs list cannot be null");
        }
        places = List.copyOf(places);
        transitions = List.copyOf(transitions);
        arcs = List.copyOf(arcs);
    }

    /**
     * Finds the single start place (with initialMarking > 0).
     *
     * @return the start place
     * @throws IllegalStateException if no start place or multiple start places exist
     */
    public PnmlPlace startPlace() {
        List<PnmlPlace> startPlaces = places.stream()
            .filter(PnmlPlace::isStartPlace)
            .toList();

        if (startPlaces.isEmpty()) {
            throw new IllegalStateException("No start place found (place with initialMarking > 0)");
        }
        if (startPlaces.size() > 1) {
            throw new IllegalStateException(
                "Multiple start places found: " + startPlaces.size() + ". Expected exactly one.");
        }
        return startPlaces.get(0);
    }

    /**
     * Finds all end places (places with no outgoing arcs).
     *
     * @return list of end places, possibly empty
     */
    public List<PnmlPlace> endPlaces() {
        return places.stream()
            .filter(place -> outgoingArcs(place.id()).isEmpty())
            .toList();
    }

    /**
     * Finds a transition by its ID.
     *
     * @param id Transition ID
     * @return Optional containing the transition if found
     */
    public Optional<PnmlTransition> transitionById(String id) {
        return transitions.stream()
            .filter(t -> t.id().equals(id))
            .findFirst();
    }

    /**
     * Finds a place by its ID.
     *
     * @param id Place ID
     * @return Optional containing the place if found
     */
    public Optional<PnmlPlace> placeById(String id) {
        return places.stream()
            .filter(p -> p.id().equals(id))
            .findFirst();
    }

    /**
     * Finds all arcs with a given source node.
     *
     * @param nodeId Source node ID (place or transition)
     * @return list of outgoing arcs
     */
    public List<PnmlArc> outgoingArcs(String nodeId) {
        return arcs.stream()
            .filter(arc -> arc.sourceId().equals(nodeId))
            .toList();
    }

    /**
     * Finds all arcs with a given target node.
     *
     * @param nodeId Target node ID (place or transition)
     * @return list of incoming arcs
     */
    public List<PnmlArc> incomingArcs(String nodeId) {
        return arcs.stream()
            .filter(arc -> arc.targetId().equals(nodeId))
            .toList();
    }

    /**
     * Validates the structural consistency of the process.
     * Checks:
     * 1. At least one start place exists
     * 2. At least one end place exists
     * 3. All arc references (sourceId, targetId) point to existing nodes
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        try {
            startPlace();
        } catch (IllegalStateException e) {
            return false;
        }

        if (endPlaces().isEmpty()) {
            return false;
        }

        Map<String, Boolean> nodeExists = new HashMap<>();
        for (PnmlPlace place : places) {
            nodeExists.put(place.id(), true);
        }
        for (PnmlTransition transition : transitions) {
            nodeExists.put(transition.id(), true);
        }

        for (PnmlArc arc : arcs) {
            if (!nodeExists.getOrDefault(arc.sourceId(), false)
                || !nodeExists.getOrDefault(arc.targetId(), false)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the count of observable (non-silent) transitions.
     *
     * @return number of observable transitions
     */
    public int observableTransitionCount() {
        return (int) transitions.stream()
            .filter(PnmlTransition::isObservable)
            .count();
    }

    /**
     * Gets the count of silent (tau) transitions.
     *
     * @return number of silent transitions
     */
    public int silentTransitionCount() {
        return (int) transitions.stream()
            .filter(PnmlTransition::isSilent)
            .count();
    }
}
