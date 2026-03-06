package org.yawlfoundation.yawl.rust4pm.petgraph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * A typed wrapper around petgraph's directed graph implementation via JNI.
 *
 * <p>This class provides a Java interface to a Rust-based {@code DiGraph<Value, Value>}
 * for high-performance graph operations. Nodes and edges carry arbitrary JSON data,
 * enabling flexible representation of process models and directly-follows graphs.
 *
 * <p><strong>Thread Safety</strong>: The underlying Rust graph is protected by a Mutex,
 * allowing concurrent reads and writes from multiple Java threads.
 *
 * <p><strong>Example</strong>:
 * <pre>
 * PetriNetGraph dfg = new PetriNetGraph();
 * int nodeA = dfg.addNode("A", ObjectMapper.create().createObjectNode()
 *     .put("label", "Activity A"));
 * int nodeB = dfg.addNode("B", ObjectMapper.create().createObjectNode()
 *     .put("label", "Activity B"));
 * dfg.addEdge(nodeA, nodeB, ObjectMapper.create().createObjectNode()
 *     .put("weight", 42));
 * </pre>
 *
 * @see DirectlyFollowsGraph for mining-specific wrapper
 * @see PetriNet for workflow model representation
 */
public class PetriNetGraph implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final long graphPtr;
    private volatile boolean closed = false;

    static {
        System.loadLibrary("petgraph_jni");
    }

    /**
     * Create a new empty directed graph.
     *
     * <p>The graph starts with zero nodes and edges. Capacity grows automatically
     * as nodes and edges are added.
     */
    public PetriNetGraph() {
        this.graphPtr = createGraph();
    }

    /**
     * Add a node to the graph with arbitrary JSON data.
     *
     * <p>Each node is assigned a unique index (0, 1, 2, ...) on the Rust side.
     * This index is returned and must be used for subsequent edge operations.
     *
     * @param id unique identifier for the node (e.g., "p1" for places, "t1" for transitions)
     * @param data JSON object to attach to the node (e.g., label, occurrence count)
     * @return node index in the graph (non-negative integer)
     * @throws IllegalStateException if graph is closed
     */
    public int addNode(String id, JsonNode data) {
        checkNotClosed();
        String dataJson = data.toString();
        return addNode(graphPtr, id, dataJson);
    }

    /**
     * Add a directed edge from one node to another with JSON data.
     *
     * <p>The edge is directed from {@code fromIdx} to {@code toIdx}.
     * Multiple edges between the same pair of nodes are allowed.
     *
     * @param fromIdx source node index
     * @param toIdx   target node index
     * @param data    JSON object to attach to the edge (e.g., weight, arc weight)
     * @return edge index
     * @throws IllegalStateException if graph is closed
     * @throws IndexOutOfBoundsException if node indices are invalid
     */
    public int addEdge(int fromIdx, int toIdx, JsonNode data) {
        checkNotClosed();
        String dataJson = data.toString();
        return addEdge(graphPtr, fromIdx, toIdx, dataJson);
    }

    /**
     * Return the number of nodes in the graph.
     *
     * @return node count (≥ 0)
     */
    public int nodeCount() {
        checkNotClosed();
        return nodeCount(graphPtr);
    }

    /**
     * Return the number of edges in the graph.
     *
     * @return edge count (≥ 0)
     */
    public int edgeCount() {
        checkNotClosed();
        return edgeCount(graphPtr);
    }

    /**
     * Check if a directed path exists from {@code fromIdx} to {@code toIdx}.
     *
     * <p>Uses depth-first search (DFS) on the Rust side. Returns true only if
     * a sequence of directed edges leads from source to target.
     *
     * @param fromIdx source node index
     * @param toIdx   target node index
     * @return {@code true} if a path exists, {@code false} otherwise
     */
    public boolean hasPath(int fromIdx, int toIdx) {
        checkNotClosed();
        return hasPath(graphPtr, fromIdx, toIdx);
    }

    /**
     * Get all direct successors (out-neighbors) of a node.
     *
     * <p>Returns an unmodifiable list of node indices reachable via single edges.
     *
     * @param nodeIdx source node index
     * @return immutable list of successor indices (may be empty)
     */
    public List<Integer> successors(int nodeIdx) {
        checkNotClosed();
        String json = successors(graphPtr, nodeIdx);
        try {
            JsonNode node = MAPPER.readTree(json);
            List<Integer> result = new ArrayList<>();
            for (JsonNode elem : node) {
                result.add(elem.asInt());
            }
            return Collections.unmodifiableList(result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse successors JSON", e);
        }
    }

    /**
     * Serialize the entire graph to JSON format.
     *
     * <p>The returned JSON contains arrays of nodes and edges, each with their
     * attached data. This is useful for persistence, visualization, or transmission.
     *
     * <p><strong>Format</strong>:
     * <pre>
     * {
     *   "nodes": [
     *     { "index": 0, "data": { ... } },
     *     { "index": 1, "data": { ... } }
     *   ],
     *   "edges": [
     *     { "from": 0, "to": 1, "data": { ... } }
     *   ]
     * }
     * </pre>
     *
     * @return JSON representation of the graph
     */
    public JsonNode toJson() {
        checkNotClosed();
        String json = toJson(graphPtr);
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse graph JSON", e);
        }
    }

    /**
     * Check if the graph is closed.
     *
     * @return {@code true} if close() has been called, {@code false} otherwise
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Free memory on the Rust side.
     *
     * <p>After calling this method (or when this object is garbage-collected
     * after being passed to a try-with-resources block), the underlying Rust graph
     * is deallocated. Subsequent method calls will raise {@link IllegalStateException}.
     */
    @Override
    public void close() {
        if (!closed) {
            destroy(graphPtr);
            closed = true;
        }
    }

    private void checkNotClosed() {
        if (closed) {
            throw new IllegalStateException("Graph is closed");
        }
    }

    // ─── JNI Method Signatures ─────────────────────────────────────────────────────

    private static native long createGraph();
    private static native int addNode(long graphPtr, String nodeId, String nodeData);
    private static native int addEdge(long graphPtr, int fromIdx, int toIdx, String edgeData);
    private static native int nodeCount(long graphPtr);
    private static native int edgeCount(long graphPtr);
    private static native boolean hasPath(long graphPtr, int fromIdx, int toIdx);
    private static native String successors(long graphPtr, int nodeIdx);
    private static native String toJson(long graphPtr);
    private static native void destroy(long graphPtr);
}
