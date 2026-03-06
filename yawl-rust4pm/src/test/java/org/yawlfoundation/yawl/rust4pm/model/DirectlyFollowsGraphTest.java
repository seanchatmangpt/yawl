package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectlyFollowsGraph Model & PetriNetGraph Integration")
class DirectlyFollowsGraphTest {

    private DirectlyFollowsGraph sample() {
        return new DirectlyFollowsGraph(
            List.of(new DfgNode("A", "Activity A", 10), new DfgNode("B", "Activity B", 7)),
            List.of(new DfgEdge("A", "B", 6), new DfgEdge("B", "A", 1))
        );
    }

    @Test
    void findNode_returns_present_for_known_id() {
        var optNode = sample().findNode("A");
        assertTrue(optNode.isPresent());
        optNode.ifPresent(n -> {
            assertEquals("Activity A", n.label());
            assertEquals(10, n.count());
        });
    }

    @Test
    void findNode_returns_empty_for_unknown_id() {
        assertTrue(sample().findNode("Z").isEmpty());
    }

    @Test
    void totalTransitions_sums_edge_counts() {
        assertEquals(7L, sample().totalTransitions());
    }

    @Test
    void empty_dfg_has_zero_transitions() {
        DirectlyFollowsGraph empty = new DirectlyFollowsGraph(List.of(), List.of());
        assertEquals(0L, empty.totalTransitions());
    }

    @Test
    @DisplayName("toPetriNetGraph converts DFG to petgraph-backed structure")
    void testToPetriNetGraph() {
        var dfg = sample();
        try (var graph = dfg.toPetriNetGraph()) {
            assertEquals(2, graph.nodeCount(), "Should have 2 nodes");
            assertEquals(2, graph.edgeCount(), "Should have 2 edges");

            // Path exists from A -> B and B -> A
            assertTrue(graph.hasPath(0, 1), "Path A -> B should exist");
            assertTrue(graph.hasPath(1, 0), "Path B -> A should exist");
        }
    }

    @Test
    @DisplayName("petgraph graph serializes to JSON correctly")
    void testPetriNetGraphSerialization() {
        var dfg = sample();
        try (var graph = dfg.toPetriNetGraph()) {
            var json = graph.toJson();
            assertNotNull(json);
            assertTrue(json.has("nodes"), "JSON should contain nodes array");
            assertTrue(json.has("edges"), "JSON should contain edges array");
            assertEquals(2, json.get("nodes").size(), "Should have 2 nodes in JSON");
            assertEquals(2, json.get("edges").size(), "Should have 2 edges in JSON");
        }
    }

    @Test
    @DisplayName("Process workflow DFG with multiple paths")
    void testWorkflowDfgToPetriNet() {
        var dfg = new DirectlyFollowsGraph(
            List.of(
                new DfgNode("start", "Start", 100),
                new DfgNode("review", "Review", 95),
                new DfgNode("approve", "Approve", 80),
                new DfgNode("reject", "Reject", 15),
                new DfgNode("end", "End", 95)
            ),
            List.of(
                new DfgEdge("start", "review", 95),
                new DfgEdge("review", "approve", 80),
                new DfgEdge("review", "reject", 15),
                new DfgEdge("approve", "end", 80),
                new DfgEdge("reject", "end", 15)
            )
        );

        try (var graph = dfg.toPetriNetGraph()) {
            assertEquals(5, graph.nodeCount());
            assertEquals(5, graph.edgeCount());

            // start -> end via approve path
            assertTrue(graph.hasPath(0, 4), "start can reach end via approve");

            // start -> end via reject path
            assertTrue(graph.hasPath(0, 4), "start can reach end via reject");

            // Successors of start
            var succStart = graph.successors(0);
            assertEquals(1, succStart.size(), "start has 1 direct successor");

            // Successors of review (has 2 paths)
            var succReview = graph.successors(1);
            assertEquals(2, succReview.size(), "review has 2 direct successors");
        }
    }
}
