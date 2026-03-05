/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Java API that mirrors the Rust process_mining library API exactly.
 * See: https://docs.rs/process_mining/latest/process_mining/
 */
package org.yawlfoundation.yawl.erlang.processmining;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Directly-Follows Graph (DFG) representation.
 *
 * <p>This class mirrors the Rust DFG concept from the process_mining crate.
 * A DFG captures the directly-follows relationships between activities in an event log.
 *
 * <h2>Rust → Java API Mapping</h2>
 * <pre>{@code
 * // ═══════════════════════════════════════════════════════════════
 * // RUST (from process_mining crate docs)
 * // ═══════════════════════════════════════════════════════════════
 * use process_mining::discovery::dfg::*;
 *
 * let dfg = discover_dfg(&log);
 * println!("Activities: {}", dfg.activities.len());
 * println!("Edges: {}", dfg.edges.len());
 *
 * for edge in &dfg.edges {
 *     println!("{} -> {}: {}", edge.source, edge.target, edge.frequency);
 * }
 *
 * // ═══════════════════════════════════════════════════════════════
 * // JAVA (equivalent - same method names, same behavior)
 * // ═══════════════════════════════════════════════════════════════
 * import org.yawlfoundation.yawl.erlang.processmining.DFG;
 *
 * DFG dfg = log.discoverDFG();
 * System.out.println("Activities: " + dfg.activities().size());
 * System.out.println("Edges: " + dfg.edges().size());
 *
 * for (DFG.Edge edge : dfg.edges()) {
 *     System.out.println(edge.source() + " -> " + edge.target() + ": " + edge.frequency());
 * }
 * }</pre>
 *
 * @see <a href="https://docs.rs/process_mining/latest/process_mining/discovery/dfg/">Rust DFG docs</a>
 */
public final class DFG {

    private final List<Activity> activities;
    private final List<Edge> edges;

    /**
     * Creates a DFG from activity and edge lists.
     */
    DFG(List<Activity> activities, List<Edge> edges) {
        this.activities = activities;
        this.edges = edges;
    }

    /**
     * Creates a DFG from raw map data (from Erlang/NIF).
     */
    static DFG fromMap(Map<String, Object> data) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> activityData = (List<Map<String, Object>>) data.get("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edgeData = (List<Map<String, Object>>) data.get("edges");

        List<Activity> activities = activityData.stream()
            .map(a -> new Activity(
                (String) a.get("id"),
                (String) a.getOrDefault("label", a.get("id")),
                ((Number) a.getOrDefault("count", 1)).longValue()
            ))
            .toList();

        List<Edge> edges = edgeData.stream()
            .map(e -> new Edge(
                (String) e.get("source"),
                (String) e.get("target"),
                ((Number) e.getOrDefault("count", 1)).longValue()
            ))
            .toList();

        return new DFG(activities, edges);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIELD ACCESSORS (mirror Rust dfg.activities, dfg.edges)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the list of activities (nodes) in this DFG.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.activities}</pre>
     * <p><b>Rust type:</b> {@code Vec<Activity>}
     */
    public List<Activity> activities() {
        return activities;
    }

    /**
     * Returns the list of edges (directly-follows relationships) in this DFG.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.edges}</pre>
     * <p><b>Rust type:</b> {@code Vec<Edge>}
     */
    public List<Edge> edges() {
        return edges;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS (mirror common Rust patterns)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the number of unique activities.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.activities.len()}</pre>
     */
    public int activityCount() {
        return activities.size();
    }

    /**
     * Returns the number of edges.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.edges.len()}</pre>
     */
    public int edgeCount() {
        return edges.size();
    }

    /**
     * Returns the set of unique activity names.
     */
    public Set<String> activityNames() {
        return activities.stream()
            .map(Activity::name)
            .collect(Collectors.toSet());
    }

    /**
     * Returns the frequency of a specific edge.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.edges.iter().find(|e| e.source == src && e.target == tgt).map(|e| e.frequency).unwrap_or(0)}</pre>
     *
     * @param source source activity
     * @param target target activity
     * @return frequency, or 0 if the edge doesn't exist
     */
    public long edgeFrequency(String source, String target) {
        return edges.stream()
            .filter(e -> e.source().equals(source) && e.target().equals(target))
            .findFirst()
            .map(Edge::frequency)
            .orElse(0L);
    }

    /**
     * Returns the frequency of an activity.
     *
     * <p><b>Rust equivalent:</b>
     * <pre>{@code dfg.activities.iter().find(|a| a.name == name).map(|a| a.frequency).unwrap_or(0)}</pre>
     *
     * @param name activity name
     * @return frequency, or 0 if the activity doesn't exist
     */
    public long activityFrequency(String name) {
        return activities.stream()
            .filter(a -> a.name().equals(name))
            .findFirst()
            .map(Activity::frequency)
            .orElse(0L);
    }

    /**
     * Returns the most frequent edge (the "hot path").
     */
    public Edge mostFrequentEdge() {
        return edges.stream()
            .max((a, b) -> Long.compare(a.frequency(), b.frequency()))
            .orElse(null);
    }

    /**
     * Returns edges ordered by frequency (descending).
     */
    public List<Edge> edgesByFrequency() {
        return edges.stream()
            .sorted((a, b) -> Long.compare(b.frequency(), a.frequency()))
            .toList();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NESTED TYPES (mirror Rust Activity, Edge)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * An activity (node) in the DFG.
     *
     * <p><b>Rust equivalent:</b> {@code Activity}
     */
    public record Activity(
        String name,
        String label,
        long frequency
    ) {}

    /**
     * An edge (directly-follows relationship) in the DFG.
     *
     * <p><b>Rust equivalent:</b> {@code Edge}
     */
    public record Edge(
        String source,
        String target,
        long frequency
    ) {}
}
