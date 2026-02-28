/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining.ocpm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Discovers an Object-Centric Petri Net (OCPN) from OCEL 2.0 logs.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Parse OCEL 2.0 log input</li>
 *   <li>For each object type, build a per-type DFG (Directly-Follows Graph)</li>
 *   <li>Convert each DFG to a Petri net (Alpha-like: causal relations → places)</li>
 *   <li>Share transitions (activities) across object types where they overlap</li>
 *   <li>Return the combined OCPN model</li>
 * </ol>
 *
 * <p>Per-object-type DFG construction:
 * <ul>
 *   <li>For each object of type OT, extract its event sequence (sorted by timestamp)</li>
 *   <li>Build directly-follows edges from consecutive events in that object's trace</li>
 *   <li>Accumulate frequencies across all objects of type OT</li>
 * </ul>
 *
 * @author YAWL Foundation (van der Aalst Object-Centric PM)
 * @version 6.0
 */
public final class OcpmDiscovery {

    /**
     * Result of OCPM discovery: object types, shared transitions, and places.
     *
     * @param objectTypes list of discovered object types with statistics
     * @param transitions list of shared transitions (activities appearing in multiple types)
     * @param placesByType map of objectType → list of places for that type
     * @param modelJson JSON representation of the OCPN model
     * @param sourceDfg the OC-DFG used for discovery
     */
    public record OcpmResult(
        List<ObjectType> objectTypes,
        List<SharedTransition> transitions,
        Map<String, List<ObjectPlace>> placesByType,
        String modelJson,
        ObjectCentricDFG sourceDfg
    ) {}

    /**
     * An object type with statistics.
     *
     * @param name object type name
     * @param eventCount total events involving this object type
     * @param objectCount number of distinct objects of this type
     */
    public record ObjectType(String name, int eventCount, int objectCount) {}

    /**
     * A transition (activity) shared across multiple object types.
     *
     * @param activity activity name
     * @param objectTypes set of object types in which this activity appears
     */
    public record SharedTransition(String activity, Set<String> objectTypes) {}

    /**
     * A place in the OCPN for a specific object type.
     *
     * Places represent control flow conditions between activities.
     * Generated from causal relationships in the DFG.
     *
     * @param id unique place identifier
     * @param objectType which object type this place belongs to
     * @param preTransitions activities that produce tokens into this place
     * @param postTransitions activities that consume tokens from this place
     */
    public record ObjectPlace(
        String id,
        String objectType,
        Set<String> preTransitions,
        Set<String> postTransitions
    ) {}

    /**
     * Discover an OCPN from OCEL 2.0 input.
     *
     * @param input OCEL 2.0 log data
     * @return discovery result with OCPN model
     * @throws NullPointerException if input is null
     */
    public OcpmResult discover(OcpmInput input) {
        Objects.requireNonNull(input, "input is required");

        // Step 1: Build per-object-type DFGs
        ObjectCentricDFG ocdfg = buildObjectCentricDfg(input);

        // Step 2: Extract object types and statistics
        List<ObjectType> objectTypes = extractObjectTypes(input, ocdfg);

        // Step 3: Find shared transitions (activities in multiple types)
        List<SharedTransition> sharedTransitions = findSharedTransitions(ocdfg);

        // Step 4: Build places per object type
        Map<String, List<ObjectPlace>> placesByType = buildPlaces(ocdfg);

        // Step 5: Generate JSON representation
        String modelJson = generateModelJson(objectTypes, sharedTransitions, placesByType);

        return new OcpmResult(
            Collections.unmodifiableList(objectTypes),
            Collections.unmodifiableList(sharedTransitions),
            Collections.unmodifiableMap(placesByType),
            modelJson,
            ocdfg
        );
    }

    /**
     * Build object-centric DFG by per-object-type analysis.
     */
    private ObjectCentricDFG buildObjectCentricDfg(OcpmInput input) {
        Map<String, ObjectCentricDFG.OcDfgEntry> dfgByType = new HashMap<>();

        // Group objects by type
        Map<String, List<OcpmInput.OcpmObject>> objectsByType = input.objects()
            .stream()
            .collect(Collectors.groupingBy(OcpmInput.OcpmObject::objectType));

        // For each object type, build DFG
        for (String objectType : objectsByType.keySet()) {
            dfgByType.put(
                objectType,
                buildDfgForObjectType(objectType, input)
            );
        }

        return new ObjectCentricDFG(dfgByType);
    }

    /**
     * Build DFG for a single object type.
     */
    private ObjectCentricDFG.OcDfgEntry buildDfgForObjectType(
            String objectType,
            OcpmInput input) {

        Map<String, Long> activityCounts = new HashMap<>();
        Map<String, Map<String, Long>> followsEdges = new HashMap<>();
        Set<String> startActivities = new HashSet<>();
        Set<String> endActivities = new HashSet<>();

        // Group events by object (for this object type only)
        Map<String, List<OcpmInput.OcpmEvent>> eventsByObject = new HashMap<>();

        for (OcpmInput.OcpmEvent event : input.events()) {
            String objectId = event.relatedObjects().get(objectType);
            if (objectId != null) {
                eventsByObject.computeIfAbsent(objectId, k -> new ArrayList<>())
                    .add(event);
            }
        }

        // For each object, build trace and extract DFG
        for (List<OcpmInput.OcpmEvent> objectTrace : eventsByObject.values()) {
            // Events are already sorted by timestamp (OcpmInput guarantees this)
            for (int i = 0; i < objectTrace.size(); i++) {
                String activity = objectTrace.get(i).activity();

                // Count activity
                activityCounts.merge(activity, 1L, Long::addExact);

                // Mark start/end
                if (i == 0) {
                    startActivities.add(activity);
                }
                if (i == objectTrace.size() - 1) {
                    endActivities.add(activity);
                }

                // Add directly-follows edge
                if (i < objectTrace.size() - 1) {
                    String nextActivity = objectTrace.get(i + 1).activity();
                    followsEdges.computeIfAbsent(activity, k -> new HashMap<>())
                        .merge(nextActivity, 1L, Long::addExact);
                }
            }
        }

        return new ObjectCentricDFG.OcDfgEntry(
            objectType,
            activityCounts,
            followsEdges,
            startActivities,
            endActivities
        );
    }

    /**
     * Extract object types with event and object counts.
     */
    private List<ObjectType> extractObjectTypes(
            OcpmInput input,
            ObjectCentricDFG ocdfg) {
        List<ObjectType> result = new ArrayList<>();

        for (String objectType : ocdfg.getObjectTypes()) {
            // Count distinct objects of this type
            long objectCount = input.objects()
                .stream()
                .filter(obj -> obj.objectType().equals(objectType))
                .count();

            // Count events for this object type
            long eventCount = input.events()
                .stream()
                .filter(evt -> evt.relatedObjects().containsKey(objectType))
                .count();

            result.add(new ObjectType(objectType, (int)eventCount, (int)objectCount));
        }

        result.sort((a, b) -> a.name().compareTo(b.name()));
        return result;
    }

    /**
     * Find shared transitions (activities appearing in multiple object types).
     */
    private List<SharedTransition> findSharedTransitions(ObjectCentricDFG ocdfg) {
        Map<String, Set<String>> activityToTypes = new HashMap<>();

        for (String objectType : ocdfg.getObjectTypes()) {
            ocdfg.getDfg(objectType).ifPresent(entry ->
                entry.activityCounts().keySet().forEach(activity ->
                    activityToTypes.computeIfAbsent(activity, k -> new HashSet<>())
                        .add(objectType)
                )
            );
        }

        // Filter to activities appearing in 2+ object types
        List<SharedTransition> result = new ArrayList<>();
        for (String activity : activityToTypes.keySet()) {
            Set<String> types = activityToTypes.get(activity);
            if (types.size() >= 2) {
                result.add(new SharedTransition(activity, new HashSet<>(types)));
            }
        }

        result.sort((a, b) -> a.activity().compareTo(b.activity()));
        return result;
    }

    /**
     * Build places from DFG edges (Alpha-like algorithm).
     *
     * For each directly-follows edge (a → b) in the DFG:
     * - Create a place p_a_b with pre-transition a and post-transition b
     */
    private Map<String, List<ObjectPlace>> buildPlaces(ObjectCentricDFG ocdfg) {
        Map<String, List<ObjectPlace>> placesByType = new HashMap<>();

        for (String objectType : ocdfg.getObjectTypes()) {
            List<ObjectPlace> places = new ArrayList<>();
            ocdfg.getDfg(objectType).ifPresent(entry -> {
                int placeId = 0;
                for (String fromActivity : entry.followsEdges().keySet()) {
                    for (String toActivity : entry.followsEdges().get(fromActivity).keySet()) {
                        String placeIdStr = objectType + "_p_" + (placeId++);
                        places.add(new ObjectPlace(
                            placeIdStr,
                            objectType,
                            Set.of(fromActivity),
                            Set.of(toActivity)
                        ));
                    }
                }
            });
            placesByType.put(objectType, places);
        }

        return placesByType;
    }

    /**
     * Generate JSON representation of the OCPN model.
     */
    private String generateModelJson(
            List<ObjectType> objectTypes,
            List<SharedTransition> transitions,
            Map<String, List<ObjectPlace>> placesByType) {

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();

        // Object types
        ArrayNode objectTypesArray = mapper.createArrayNode();
        for (ObjectType ot : objectTypes) {
            ObjectNode otNode = mapper.createObjectNode();
            otNode.put("name", ot.name());
            otNode.put("eventCount", ot.eventCount());
            otNode.put("objectCount", ot.objectCount());
            objectTypesArray.add(otNode);
        }
        root.set("objectTypes", objectTypesArray);

        // Shared transitions
        ArrayNode transitionsArray = mapper.createArrayNode();
        for (SharedTransition st : transitions) {
            ObjectNode stNode = mapper.createObjectNode();
            stNode.put("activity", st.activity());
            stNode.putPOJO("objectTypes", new ArrayList<>(st.objectTypes()));
            transitionsArray.add(stNode);
        }
        root.set("sharedTransitions", transitionsArray);

        // Places by type
        ObjectNode placesNode = mapper.createObjectNode();
        for (String objectType : placesByType.keySet()) {
            ArrayNode typePlaces = mapper.createArrayNode();
            for (ObjectPlace place : placesByType.get(objectType)) {
                ObjectNode placeNode = mapper.createObjectNode();
                placeNode.put("id", place.id());
                placeNode.putPOJO("preTransitions", new ArrayList<>(place.preTransitions()));
                placeNode.putPOJO("postTransitions", new ArrayList<>(place.postTransitions()));
                typePlaces.add(placeNode);
            }
            placesNode.set(objectType, typePlaces);
        }
        root.set("placesByObjectType", placesNode);

        return root.toPrettyString();
    }
}
