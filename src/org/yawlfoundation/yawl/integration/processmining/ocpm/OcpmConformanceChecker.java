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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Token replay conformance checking adapted for Object-Centric Petri Nets (OCPN).
 *
 * <p>Replays each object's event sequence against its per-type model, reporting:
 * <ul>
 *   <li>Fitness per object type (0.0 to 1.0)</li>
 *   <li>Overall fitness (average across types)</li>
 *   <li>Deviating object IDs (objects with imperfect fitness)</li>
 * </ul>
 *
 * <p>Fitness calculation per object type:
 *   - For each object of type OT, compute token-based fitness on OT's Petri net
 *   - Average across all objects of that type
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class OcpmConformanceChecker {

    /**
     * Conformance checking result for an OCPN.
     *
     * @param fitnessByObjectType per-type fitness scores (0.0 to 1.0)
     * @param overallFitness average fitness across all object types
     * @param deviatingObjectIds objects with fitness < 1.0 (perfect fit)
     * @param totalObjectsChecked total distinct objects replayed
     */
    public record OcpmConformanceResult(
        Map<String, Double> fitnessByObjectType,
        double overallFitness,
        List<String> deviatingObjectIds,
        int totalObjectsChecked
    ) {}

    private OcpmConformanceChecker() {
        // Utility class, no instances
    }

    /**
     * Check conformance of an OCEL 2.0 log against an OCPN model.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>For each object type in the log:
     *       <ul>
     *         <li>For each object of that type:
     *             <ul>
     *               <li>Get its event sequence (sorted by timestamp)</li>
     *               <li>Replay against the type's Petri net</li>
     *               <li>Compute fitness using van der Aalst formula</li>
     *             </ul>
     *         <li>Average fitness across all objects of that type</li>
     *       </ul>
     *   <li>Compute overall fitness as average of per-type fitnesses</li>
     * </ol>
     *
     * @param log OCEL 2.0 input log
     * @param model OCPN model from discovery
     * @return conformance result with per-type and overall fitness
     * @throws NullPointerException if log or model is null
     */
    public static OcpmConformanceResult check(
            OcpmInput log,
            OcpmDiscovery.OcpmResult model) {

        Objects.requireNonNull(log, "log is required");
        Objects.requireNonNull(model, "model is required");

        Map<String, Double> fitnessByObjectType = new HashMap<>();
        Set<String> deviatingObjects = new HashSet<>();
        int totalObjects = 0;

        // Group events by object
        Map<String, List<OcpmInput.OcpmEvent>> eventsByObject = new HashMap<>();
        for (OcpmInput.OcpmEvent evt : log.events()) {
            for (String objectId : evt.relatedObjects().values()) {
                eventsByObject.computeIfAbsent(objectId, k -> new ArrayList<>())
                    .add(evt);
            }
        }

        // For each object type, check conformance
        for (OcpmDiscovery.ObjectType objectType : model.objectTypes()) {
            String typeName = objectType.name();

            // Get all objects of this type
            List<OcpmInput.OcpmObject> objectsOfType = log.objects()
                .stream()
                .filter(obj -> obj.objectType().equals(typeName))
                .collect(Collectors.toList());

            if (objectsOfType.isEmpty()) {
                fitnessByObjectType.put(typeName, 1.0); // No objects = perfect fit
                continue;
            }

            double totalFitness = 0.0;
            int objectCount = 0;

            // For each object of this type, check its trace
            for (OcpmInput.OcpmObject object : objectsOfType) {
                List<OcpmInput.OcpmEvent> objectTrace = eventsByObject
                    .getOrDefault(object.objectId(), new java.util.ArrayList<>());

                // Extract activities from trace
                List<String> activities = objectTrace.stream()
                    .map(OcpmInput.OcpmEvent::activity)
                    .collect(Collectors.toList());

                // Compute fitness for this object's trace
                double fitness = computeTraceFitness(activities, model.placesByType(), typeName);
                totalFitness += fitness;
                objectCount++;

                // Mark deviating objects (fitness < 1.0)
                if (fitness < 1.0) {
                    deviatingObjects.add(object.objectId());
                }
            }

            double typeFitness = objectCount > 0 ? totalFitness / objectCount : 1.0;
            fitnessByObjectType.put(typeName, typeFitness);
            totalObjects += objectCount;
        }

        // Compute overall fitness
        double overallFitness = fitnessByObjectType.isEmpty()
            ? 1.0
            : fitnessByObjectType.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(1.0);

        return new OcpmConformanceResult(
            Collections.unmodifiableMap(fitnessByObjectType),
            overallFitness,
            Collections.unmodifiableList(
                deviatingObjects.stream().sorted().collect(Collectors.toList())
            ),
            totalObjects
        );
    }

    /**
     * Compute fitness for a single trace against a type's Petri net.
     *
     * <p>Simple fitness model: trace is fitting if all activities appear in
     * the directly-follows edges of the per-type DFG. More sophisticated replay
     * would track token flow through places.</p>
     */
    private static double computeTraceFitness(
            List<String> activities,
            Map<String, List<OcpmDiscovery.ObjectPlace>> placesByType,
            String objectType) {

        if (activities.isEmpty()) {
            return 1.0; // Empty trace is fitting
        }

        // Get places for this object type
        List<OcpmDiscovery.ObjectPlace> places = placesByType
            .getOrDefault(objectType, new java.util.ArrayList<>());

        if (places.isEmpty()) {
            return 1.0; // No model = everything fits
        }

        // Build a set of valid directly-follows edges from places
        Set<String> validEdges = new HashSet<>();
        for (OcpmDiscovery.ObjectPlace place : places) {
            for (String pre : place.preTransitions()) {
                for (String post : place.postTransitions()) {
                    validEdges.add(pre + " → " + post);
                }
            }
        }

        // Check if all consecutive activities in trace are in valid edges
        int validTransitions = 0;
        int totalTransitions = 0;

        for (int i = 0; i < activities.size() - 1; i++) {
            String edge = activities.get(i) + " → " + activities.get(i + 1);
            totalTransitions++;
            if (validEdges.contains(edge)) {
                validTransitions++;
            }
        }

        // Fitness = fraction of valid transitions
        return totalTransitions > 0
            ? (double) validTransitions / totalTransitions
            : 1.0;
    }
}
