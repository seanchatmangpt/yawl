package org.yawlfoundation.yawl.rust4pm.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.rust4pm.petgraph.PetriNetGraph;

import java.util.List;
import java.util.Optional;

/**
 * A directly-follows graph (DFG) discovered from an OCEL2 event log.
 *
 * <p>Represents activities (nodes) and direct transitions (edges) derived from process mining.
 * Optionally backed by petgraph for high-performance reachability and path analysis.
 *
 * @param nodes list of activity nodes with occurrence counts
 * @param edges list of directly-follows edges with transition counts
 */
public record DirectlyFollowsGraph(List<DfgNode> nodes, List<DfgEdge> edges) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Find a node by its activity id. */
    public Optional<DfgNode> findNode(String id) {
        return nodes.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /** Sum of all edge counts. */
    public long totalTransitions() {
        return edges.stream().mapToLong(DfgEdge::count).sum();
    }

    /**
     * Create an optimized petgraph-backed version of this DFG for fast reachability queries.
     *
     * <p>This optional high-performance backend is useful for large DFGs where:
     * <ul>
     *   <li>Many reachability checks are needed (e.g., conformance checking)
     *   <li>Graph has millions of edges
     *   <li>Performance is critical (batch processing, real-time analysis)
     * </ul>
     *
     * <p>The returned graph is independent of this object and must be closed when no longer needed.
     *
     * @return a petgraph-backed DFG (thread-safe, zero-copy)
     * @see PetriNetGraph#hasPath
     * @see PetriNetGraph#successors
     */
    public PetriNetGraph toPetriNetGraph() {
        PetriNetGraph graph = new PetriNetGraph();

        // Add nodes with their counts and labels
        var nodeMap = new java.util.HashMap<String, Integer>();
        for (DfgNode node : nodes) {
            var nodeData = MAPPER.createObjectNode();
            nodeData.put("id", node.id());
            nodeData.put("label", node.label());
            nodeData.put("count", node.count());

            int idx = graph.addNode(node.id(), nodeData);
            nodeMap.put(node.id(), idx);
        }

        // Add edges with their counts
        for (DfgEdge edge : edges) {
            Integer fromIdx = nodeMap.get(edge.source());
            Integer toIdx = nodeMap.get(edge.target());

            if (fromIdx != null && toIdx != null) {
                var edgeData = MAPPER.createObjectNode();
                edgeData.put("source", edge.source());
                edgeData.put("target", edge.target());
                edgeData.put("count", edge.count());

                graph.addEdge(fromIdx, toIdx, edgeData);
            }
        }

        return graph;
    }
}
