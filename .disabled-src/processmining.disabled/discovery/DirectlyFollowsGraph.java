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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.discovery;

import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Public representation of a directly-follows graph (DFG).
 *
 * <p>A DFG models activity execution patterns from event logs:
 * nodes are activities, edges represent directly-follows relationships
 * (activity A directly precedes activity B in traces), and edge weights
 * count occurrences of the relation in the log.</p>
 *
 * <p><strong>Construction</strong>:</p>
 * <ul>
 *   <li>{@link #discover(List)} — Construct DFG from event traces</li>
 *   <li>{@link #addNode(String)} / {@link #addEdge(String, String)} — Manual construction</li>
 * </ul>
 *
 * <p><strong>Query API</strong>:</p>
 * <ul>
 *   <li>{@link #getActivities()} — All activity nodes</li>
 *   <li>{@link #getStartActivities()} — Activities with no predecessors</li>
 *   <li>{@link #getEndActivities()} — Activities with no successors</li>
 *   <li>{@link #getEdgeCount(String, String)} — Count of A→B occurrences (0 if none)</li>
 *   <li>{@link #getSuccessors(String)} / {@link #getPredecessors(String)} — Neighbor activities</li>
 * </ul>
 *
 * <p><strong>Output</strong>:</p>
 * <ul>
 *   <li>{@link #toJson()} — JSON for debugging/serialization</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Build from event traces: List<List<String>> = activity sequences per case
 * List<List<String>> traces = List.of(
 *     List.of("a", "b", "c", "d"),
 *     List.of("a", "c", "b", "d"),
 *     List.of("a", "e", "d")
 * );
 * DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);
 *
 * // Query
 * Set<String> activities = dfg.getActivities();  // {a, b, c, d, e}
 * long count_a_to_b = dfg.getEdgeCount("a", "b");  // 2
 * Set<String> start = dfg.getStartActivities();  // {a}
 * Set<String> end = dfg.getEndActivities();  // {d}
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class DirectlyFollowsGraph {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Set<String>> adjacencyList = new HashMap<>();
    private final Map<DFGEdge, Long> edgeWeights = new HashMap<>();

    /**
     * Construct an empty DFG.
     */
    public DirectlyFollowsGraph() {
    }

    /**
     * Discover a DFG from event traces.
     *
     * <p>Each trace is an ordered sequence of activity names (strings).
     * For each pair of consecutive activities in a trace, an edge is added/weighted.
     * The result is a DFG with all activities and directly-follows relations.</p>
     *
     * @param traces List of activity sequences (each inner list = one case's activities)
     * @return Constructed DFG
     * @throws NullPointerException if traces or any trace is null
     */
    public static DirectlyFollowsGraph discover(List<List<String>> traces) {
        Objects.requireNonNull(traces, "traces cannot be null");

        DirectlyFollowsGraph dfg = new DirectlyFollowsGraph();

        for (List<String> trace : traces) {
            Objects.requireNonNull(trace, "each trace cannot be null");

            for (int i = 0; i < trace.size() - 1; i++) {
                String from = trace.get(i);
                String to = trace.get(i + 1);
                dfg.addEdge(from, to);
            }
        }

        return dfg;
    }

    /**
     * Add a node (activity) to the graph.
     *
     * @param node Activity name (typically non-null, non-empty string)
     */
    public void addNode(String node) {
        Objects.requireNonNull(node, "node cannot be null");
        adjacencyList.putIfAbsent(node, new HashSet<>());
    }

    /**
     * Add or weight an edge (directly-follows relation).
     *
     * <p>If the edge already exists, its count increments by 1.
     * If either node does not exist, it is created.</p>
     *
     * @param from Source activity
     * @param to Target activity
     * @throws NullPointerException if from or to is null
     */
    public void addEdge(String from, String to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");

        addNode(from);
        addNode(to);
        adjacencyList.get(from).add(to);

        DFGEdge edge = new DFGEdge(from, to);
        edgeWeights.put(edge, edgeWeights.getOrDefault(edge, 0L) + 1);
    }

    /**
     * Remove an edge from the graph.
     *
     * @param from Source activity
     * @param to Target activity
     */
    public void removeEdge(String from, String to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");

        adjacencyList.getOrDefault(from, new HashSet<>()).remove(to);
        edgeWeights.remove(new DFGEdge(from, to));
    }

    /**
     * Remove a node and all its incident edges.
     *
     * @param node Activity to remove
     */
    public void removeNode(String node) {
        Objects.requireNonNull(node, "node cannot be null");
        adjacencyList.remove(node);
        edgeWeights.keySet().removeIf(edge ->
            edge.getFrom().equals(node) || edge.getTo().equals(node));
    }

    /**
     * Get all activity nodes in the graph.
     *
     * @return Immutable set of activity names
     */
    public Set<String> getActivities() {
        return Collections.unmodifiableSet(adjacencyList.keySet());
    }

    /**
     * Get all activities that appear as start activities (no predecessors).
     *
     * @return Set of start activities (empty set if none)
     */
    public Set<String> getStartActivities() {
        return adjacencyList.keySet().stream()
            .filter(node -> getPredecessors(node).isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Get all activities that appear as end activities (no successors).
     *
     * @return Set of end activities (empty set if none)
     */
    public Set<String> getEndActivities() {
        return adjacencyList.keySet().stream()
            .filter(node -> getSuccessors(node).isEmpty())
            .collect(Collectors.toSet());
    }

    /**
     * Get the count of edges from A to B.
     *
     * <p>Returns the number of times B directly followed A in the traces.
     * If no such edge exists, returns 0.</p>
     *
     * @param from Source activity
     * @param to Target activity
     * @return Edge count (0 if not present)
     */
    public long getEdgeCount(String from, String to) {
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(to, "to cannot be null");
        return edgeWeights.getOrDefault(new DFGEdge(from, to), 0L);
    }

    /**
     * Get the set of activities that directly follow the given activity.
     *
     * @param activity Source activity
     * @return Set of successor activities (empty set if none)
     */
    public Set<String> getSuccessors(String activity) {
        Objects.requireNonNull(activity, "activity cannot be null");
        return Collections.unmodifiableSet(
            adjacencyList.getOrDefault(activity, new HashSet<>())
        );
    }

    /**
     * Get the set of activities that directly precede the given activity.
     *
     * @param activity Target activity
     * @return Set of predecessor activities (empty set if none)
     */
    public Set<String> getPredecessors(String activity) {
        Objects.requireNonNull(activity, "activity cannot be null");
        return adjacencyList.entrySet().stream()
            .filter(e -> e.getValue().contains(activity))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Get all edges in the graph (for internal use by mining algorithms).
     *
     * @return Set of all edges
     */
    Set<DFGEdge> getEdges() {
        return Collections.unmodifiableSet(edgeWeights.keySet());
    }

    /**
     * Get all activity nodes (same as getActivities but with package-private access).
     *
     * @return Set of activity names
     */
    Set<String> getNodes() {
        return getActivities();
    }

    /**
     * Get edge weight as integer (for compatibility with HeuristicMiner).
     *
     * @param from Source activity
     * @param to Target activity
     * @return Edge count as integer (0 if not present)
     */
    int getEdgeWeightInt(String from, String to) {
        return (int) getEdgeCount(from, to);
    }

    /**
     * Convert the DFG to JSON for debugging and serialization.
     *
     * <p>Format:</p>
     * <pre>{@code
     * {
     *   "activities": ["a", "b", "c"],
     *   "edges": [
     *     { "from": "a", "to": "b", "count": 2 },
     *     { "from": "b", "to": "c", "count": 1 }
     *   ],
     *   "startActivities": ["a"],
     *   "endActivities": ["c"]
     * }
     * }</pre>
     *
     * @return JSON string representation
     */
    public String toJson() {
        ObjectNode root = objectMapper.createObjectNode();

        // Activities
        ArrayNode activitiesArray = root.putArray("activities");
        getActivities().stream()
            .sorted()
            .forEach(activitiesArray::add);

        // Edges
        ArrayNode edgesArray = root.putArray("edges");
        edgeWeights.forEach((edge, count) -> {
            ObjectNode edgeNode = objectMapper.createObjectNode();
            edgeNode.put("from", edge.getFrom());
            edgeNode.put("to", edge.getTo());
            edgeNode.put("count", count);
            edgesArray.add(edgeNode);
        });

        // Start activities
        ArrayNode startArray = root.putArray("startActivities");
        getStartActivities().stream()
            .sorted()
            .forEach(startArray::add);

        // End activities
        ArrayNode endArray = root.putArray("endActivities");
        getEndActivities().stream()
            .sorted()
            .forEach(endArray::add);

        return root.toString();
    }

    /**
     * Internal record for DFG edges.
     */
    private static final class DFGEdge {
        private final String from;
        private final String to;

        DFGEdge(String from, String to) {
            this.from = from;
            this.to = to;
        }

        String getFrom() { return from; }
        String getTo() { return to; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DFGEdge dfgEdge = (DFGEdge) o;
            return from.equals(dfgEdge.from) && to.equals(dfgEdge.to);
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to);
        }
    }
}
