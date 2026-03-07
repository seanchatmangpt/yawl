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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Process graph representation - output of Stage 2 (Spec → Graph).
 *
 * <p>This is the graph-based representation of the process with explicit
 * control flow constructs (XOR/AND/OR splits and joins).
 *
 * @param nodes Graph nodes (tasks, gateways, conditions)
 * @param edges Graph edges with conditions
 * @param inputCondition Input condition node ID
 * @param outputCondition Output condition node ID
 * @param xorSplits XOR split definitions
 * @param andSplits AND split definitions
 * @param orSplits OR split definitions (YAWL-specific)
 * @param metadata Generation metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessGraph(
    List<GraphNode> nodes,
    List<GraphEdge> edges,
    @JsonProperty("input_condition") String inputCondition,
    @JsonProperty("output_condition") String outputCondition,
    @JsonProperty("xor_splits") List<SplitDef> xorSplits,
    @JsonProperty("and_splits") List<SplitDef> andSplits,
    @JsonProperty("or_splits") List<SplitDef> orSplits,
    Map<String, Object> metadata
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ProcessGraph {
        nodes = nodes != null ? List.copyOf(nodes) : List.of();
        edges = edges != null ? List.copyOf(edges) : List.of();
        xorSplits = xorSplits != null ? List.copyOf(xorSplits) : List.of();
        andSplits = andSplits != null ? List.copyOf(andSplits) : List.of();
        orSplits = orSplits != null ? List.copyOf(orSplits) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Convert to JSON for DSPy serialization.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ProcessGraph to JSON", e);
        }
    }

    /**
     * Parse from JSON.
     */
    public static ProcessGraph fromJson(String json) {
        try {
            return MAPPER.readValue(json, ProcessGraph.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ProcessGraph from JSON", e);
        }
    }

    /**
     * Find a node by ID.
     */
    public GraphNode findNode(String id) {
        return nodes.stream()
            .filter(n -> n.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Find all edges from a node.
     */
    public List<GraphEdge> edgesFrom(String nodeId) {
        return edges.stream()
            .filter(e -> e.from().equals(nodeId))
            .toList();
    }

    /**
     * Find all edges to a node.
     */
    public List<GraphEdge> edgesTo(String nodeId) {
        return edges.stream()
            .filter(e -> e.to().equals(nodeId))
            .toList();
    }

    /**
     * Check if graph has OR constructs.
     */
    public boolean hasOrConstructs() {
        return !orSplits.isEmpty();
    }

    /**
     * Get node count.
     */
    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Get edge count.
     */
    public int edgeCount() {
        return edges.size();
    }
}
