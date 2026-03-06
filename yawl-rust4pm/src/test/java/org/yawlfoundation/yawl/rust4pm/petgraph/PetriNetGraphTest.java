package org.yawlfoundation.yawl.rust4pm.petgraph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PetriNetGraph JNI bindings.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Graph creation and basic operations
 *   <li>Node addition and edge connectivity
 *   <li>Path reachability (DFS correctness)
 *   <li>Successor queries
 *   <li>JSON serialization and deserialization
 * </ul>
 */
@DisplayName("PetriNetGraph JNI Bindings")
class PetriNetGraphTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Graph creation initializes empty state")
    void testCreateGraph() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            assertEquals(0, graph.nodeCount());
            assertEquals(0, graph.edgeCount());
        }
    }

    @Test
    @DisplayName("addNode increments node count and returns index")
    void testAddNode() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode data = MAPPER.createObjectNode();
            data.put("label", "Activity A");

            int idx0 = graph.addNode("a1", data);
            assertEquals(0, idx0);
            assertEquals(1, graph.nodeCount());

            int idx1 = graph.addNode("a2", data);
            assertEquals(1, idx1);
            assertEquals(2, graph.nodeCount());
        }
    }

    @Test
    @DisplayName("addEdge creates directed edge between nodes")
    void testAddEdge() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode nodeData = MAPPER.createObjectNode()
                .put("label", "Activity");
            ObjectNode edgeData = MAPPER.createObjectNode()
                .put("weight", 5);

            int a = graph.addNode("a", nodeData);
            int b = graph.addNode("b", nodeData);

            int edgeIdx = graph.addEdge(a, b, edgeData);
            assertEquals(0, edgeIdx);
            assertEquals(1, graph.edgeCount());
        }
    }

    @Test
    @DisplayName("hasPath detects direct connectivity via DFS")
    void testHasPath() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode nodeData = MAPPER.createObjectNode()
                .put("label", "Node");
            ObjectNode edgeData = MAPPER.createObjectNode();

            int a = graph.addNode("a", nodeData);
            int b = graph.addNode("b", nodeData);
            int c = graph.addNode("c", nodeData);

            graph.addEdge(a, b, edgeData);
            graph.addEdge(b, c, edgeData);

            assertTrue(graph.hasPath(a, b), "Direct edge should exist");
            assertTrue(graph.hasPath(a, c), "Transitive path should exist");
            assertFalse(graph.hasPath(b, a), "Reverse path should not exist");
            assertFalse(graph.hasPath(c, a), "No path backward");
        }
    }

    @Test
    @DisplayName("successors returns direct out-neighbors")
    void testSuccessors() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode nodeData = MAPPER.createObjectNode()
                .put("label", "Node");
            ObjectNode edgeData = MAPPER.createObjectNode();

            int a = graph.addNode("a", nodeData);
            int b = graph.addNode("b", nodeData);
            int c = graph.addNode("c", nodeData);

            graph.addEdge(a, b, edgeData);
            graph.addEdge(a, c, edgeData);

            List<Integer> succ = graph.successors(a);
            assertEquals(2, succ.size());
            assertTrue(succ.contains(b), "Should contain b");
            assertTrue(succ.contains(c), "Should contain c");

            List<Integer> succB = graph.successors(b);
            assertTrue(succB.isEmpty(), "b has no successors");
        }
    }

    @Test
    @DisplayName("toJson serializes graph structure with data")
    void testToJson() throws Exception {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode nodeData = MAPPER.createObjectNode()
                .put("label", "Activity")
                .put("count", 10);
            ObjectNode edgeData = MAPPER.createObjectNode()
                .put("weight", 5);

            int a = graph.addNode("a", nodeData);
            int b = graph.addNode("b", nodeData);
            graph.addEdge(a, b, edgeData);

            var json = graph.toJson();
            assertNotNull(json);
            assertTrue(json.has("nodes"), "JSON should contain nodes");
            assertTrue(json.has("edges"), "JSON should contain edges");

            var nodes = json.get("nodes");
            assertEquals(2, nodes.size(), "Should have 2 nodes");

            var edges = json.get("edges");
            assertEquals(1, edges.size(), "Should have 1 edge");
        }
    }

    @Test
    @DisplayName("close releases Rust memory and prevents reuse")
    void testClose() {
        PetriNetGraph graph = new PetriNetGraph();
        graph.close();

        assertThrows(IllegalStateException.class, graph::nodeCount,
            "Should not allow operations on closed graph");
    }

    @Test
    @DisplayName("try-with-resources automatically closes graph")
    void testTryWithResources() {
        PetriNetGraph graph;
        try (PetriNetGraph g = new PetriNetGraph()) {
            graph = g;
            assertEquals(0, graph.nodeCount());
        }

        assertThrows(IllegalStateException.class, graph::nodeCount,
            "Graph should be closed after try-with-resources block");
    }

    @Test
    @DisplayName("Complex DFG with multiple paths")
    void testComplexGraph() {
        try (PetriNetGraph graph = new PetriNetGraph()) {
            ObjectNode nodeData = MAPPER.createObjectNode();
            ObjectNode edgeData = MAPPER.createObjectNode();

            // Build a diamond graph: a -> {b, c} -> d
            int a = graph.addNode("a", nodeData);
            int b = graph.addNode("b", nodeData);
            int c = graph.addNode("c", nodeData);
            int d = graph.addNode("d", nodeData);

            graph.addEdge(a, b, edgeData);
            graph.addEdge(a, c, edgeData);
            graph.addEdge(b, d, edgeData);
            graph.addEdge(c, d, edgeData);

            assertEquals(4, graph.nodeCount());
            assertEquals(4, graph.edgeCount());

            assertTrue(graph.hasPath(a, d), "Path via b");
            assertTrue(graph.hasPath(a, d), "Path via c");

            List<Integer> succA = graph.successors(a);
            assertEquals(2, succA.size());
        }
    }
}
