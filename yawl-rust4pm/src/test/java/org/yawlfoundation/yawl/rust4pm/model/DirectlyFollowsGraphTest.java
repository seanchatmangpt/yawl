package org.yawlfoundation.yawl.rust4pm.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
