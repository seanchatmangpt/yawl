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

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Object-Centric Directly-Follows Graph (OC-DFG) representation.
 *
 * <p>For object-centric process mining, each object type has its own DFG.
 * Each OC-DFG entry captures:
 * <ul>
 *   <li>Activity counts per object type</li>
 *   <li>Directly-follows edges: activity → activity with frequency</li>
 *   <li>Start and end activities observed</li>
 * </ul>
 *
 * <p>Directly-follows edges are built from each object's event sequence
 * by connecting consecutive events (ordered by timestamp) for that object.</p>
 *
 * @author YAWL Foundation (van der Aalst Object-Centric PM)
 * @version 6.0
 */
public final class ObjectCentricDFG {

    /**
     * A single Object-Centric DFG entry, indexed by object type.
     *
     * @param objectType the name of this object type (e.g., "case", "item")
     * @param activityCounts map of activity name to event count
     * @param followsEdges map of activity → (target activity → frequency)
     * @param startActivities activities that started an object's trace
     * @param endActivities activities that ended an object's trace
     */
    public record OcDfgEntry(
        String objectType,
        Map<String, Long> activityCounts,
        Map<String, Map<String, Long>> followsEdges,
        Set<String> startActivities,
        Set<String> endActivities
    ) {
        public OcDfgEntry {
            activityCounts = Collections.unmodifiableMap(activityCounts);
            followsEdges = Collections.unmodifiableMap(followsEdges);
            startActivities = Collections.unmodifiableSet(startActivities);
            endActivities = Collections.unmodifiableSet(endActivities);
        }
    }

    private final Map<String, OcDfgEntry> dfgByObjectType;
    private final ObjectMapper objectMapper;

    /**
     * Create an Object-Centric DFG from a map of object types to DFG entries.
     *
     * @param dfgByObjectType map of object type name to OcDfgEntry
     */
    public ObjectCentricDFG(Map<String, OcDfgEntry> dfgByObjectType) {
        this.dfgByObjectType = Collections.unmodifiableMap(dfgByObjectType);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get all object types represented in this OC-DFG.
     *
     * @return set of object type names
     */
    public Set<String> getObjectTypes() {
        return dfgByObjectType.keySet();
    }

    /**
     * Get the DFG for a specific object type.
     *
     * @param objectType the object type name
     * @return Optional containing the DFG entry if it exists
     */
    public Optional<OcDfgEntry> getDfg(String objectType) {
        return Optional.ofNullable(dfgByObjectType.get(objectType));
    }

    /**
     * Export this OC-DFG to JSON for debugging and MCP exposure.
     *
     * <p>Format:
     * <pre>
     * {
     *   "objectType": {
     *     "activityCounts": { "activity": count, ... },
     *     "followsEdges": {
     *       "fromActivity": { "toActivity": frequency, ... },
     *       ...
     *     },
     *     "startActivities": ["activity", ...],
     *     "endActivities": ["activity", ...]
     *   },
     *   ...
     * }
     * </pre>
     *
     * @return JSON string representation
     */
    public String toJson() {
        ObjectNode root = objectMapper.createObjectNode();

        for (String objectType : dfgByObjectType.keySet()) {
            OcDfgEntry entry = dfgByObjectType.get(objectType);
            ObjectNode entryNode = objectMapper.createObjectNode();

            // Activity counts
            ObjectNode countsNode = objectMapper.createObjectNode();
            entry.activityCounts().forEach((activity, count) ->
                countsNode.put(activity, count)
            );
            entryNode.set("activityCounts", countsNode);

            // Follows edges
            ObjectNode edgesNode = objectMapper.createObjectNode();
            entry.followsEdges().forEach((fromActivity, targets) -> {
                ObjectNode targetsNode = objectMapper.createObjectNode();
                targets.forEach((toActivity, freq) ->
                    targetsNode.put(toActivity, freq)
                );
                edgesNode.set(fromActivity, targetsNode);
            });
            entryNode.set("followsEdges", edgesNode);

            // Start/end activities
            entryNode.putPOJO("startActivities", entry.startActivities());
            entryNode.putPOJO("endActivities", entry.endActivities());

            root.set(objectType, entryNode);
        }

        return root.toPrettyString();
    }
}
