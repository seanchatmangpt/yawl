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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for DirectlyFollowsGraph.
 */
public class DirectlyFollowsGraphTest {

    private DirectlyFollowsGraph dfg;

    @BeforeEach
    public void setUp() {
        dfg = new DirectlyFollowsGraph();
    }

    @Test
    public void testDiscoverFromSimpleTraces() {
        // Arrange: Classic van der Aalst example
        List<List<String>> traces = List.of(
            List.of("a", "b", "c", "d"),
            List.of("a", "c", "b", "d"),
            List.of("a", "e", "d")
        );

        // Act
        DirectlyFollowsGraph result = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> activities = result.getActivities();
        assertEquals(5, activities.size());
        assertTrue(activities.containsAll(Set.of("a", "b", "c", "d", "e")));

        // Check directly-follows relations
        assertEquals(2, result.getEdgeCount("a", "b"));  // a→b in 2 traces
        assertEquals(2, result.getEdgeCount("a", "c"));  // a→c in 2 traces
        assertEquals(1, result.getEdgeCount("a", "e"));  // a→e in 1 trace
        assertEquals(0, result.getEdgeCount("b", "a"));  // no b→a
    }

    @Test
    public void testStartActivities() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "d", "c"),
            List.of("e", "f")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> starts = dfg.getStartActivities();
        assertEquals(2, starts.size());
        assertTrue(starts.contains("a"));
        assertTrue(starts.contains("e"));
    }

    @Test
    public void testEndActivities() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "d", "c"),
            List.of("e", "f")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> ends = dfg.getEndActivities();
        assertEquals(2, ends.size());
        assertTrue(ends.contains("c"));
        assertTrue(ends.contains("f"));
    }

    @Test
    public void testSuccessors() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "d", "c")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> successors_a = dfg.getSuccessors("a");
        assertEquals(2, successors_a.size());
        assertTrue(successors_a.containsAll(Set.of("b", "d")));

        Set<String> successors_c = dfg.getSuccessors("c");
        assertTrue(successors_c.isEmpty());
    }

    @Test
    public void testPredecessors() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a", "b", "c"),
            List.of("a", "d", "c")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> predecessors_c = dfg.getPredecessors("c");
        assertEquals(2, predecessors_c.size());
        assertTrue(predecessors_c.containsAll(Set.of("b", "d")));

        Set<String> predecessors_a = dfg.getPredecessors("a");
        assertTrue(predecessors_a.isEmpty());
    }

    @Test
    public void testAddEdgeManually() {
        // Act
        dfg.addNode("a");
        dfg.addNode("b");
        dfg.addEdge("a", "b");
        dfg.addEdge("a", "b");  // Add again to increase weight

        // Assert
        assertEquals(2, dfg.getEdgeCount("a", "b"));
        assertTrue(dfg.getActivities().containsAll(Set.of("a", "b")));
    }

    @Test
    public void testRemoveEdge() {
        // Arrange
        dfg.addEdge("a", "b");
        dfg.addEdge("a", "b");

        // Act
        dfg.removeEdge("a", "b");

        // Assert
        assertEquals(0, dfg.getEdgeCount("a", "b"));
    }

    @Test
    public void testRemoveNode() {
        // Arrange
        dfg.addEdge("a", "b");
        dfg.addEdge("a", "c");
        dfg.addEdge("b", "c");

        // Act
        dfg.removeNode("a");

        // Assert
        assertFalse(dfg.getActivities().contains("a"));
        assertEquals(0, dfg.getEdgeCount("a", "b"));
        assertEquals(1, dfg.getEdgeCount("b", "c"));  // Unaffected
    }

    @Test
    public void testEmptyLog() {
        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(List.of());

        // Assert
        assertTrue(dfg.getActivities().isEmpty());
    }

    @Test
    public void testSingleActivityTraces() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a"),
            List.of("a"),
            List.of("b")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        Set<String> activities = dfg.getActivities();
        assertEquals(2, activities.size());
        assertTrue(activities.containsAll(Set.of("a", "b")));

        // No edges for single-element traces
        assertTrue(dfg.getActivities().stream()
            .allMatch(a -> dfg.getSuccessors(a).isEmpty()));
    }

    @Test
    public void testJsonOutput() {
        // Arrange
        List<List<String>> traces = List.of(
            List.of("a", "b", "c")
        );
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Act
        String json = dfg.toJson();

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("activities"));
        assertTrue(json.contains("edges"));
        assertTrue(json.contains("startActivities"));
        assertTrue(json.contains("endActivities"));
        assertTrue(json.contains("\"a\""));
        assertTrue(json.contains("\"b\""));
        assertTrue(json.contains("\"c\""));
    }

    @Test
    public void testNullTraces() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> DirectlyFollowsGraph.discover(null));
    }

    @Test
    public void testNullActivity() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> dfg.addEdge(null, "b"));
        assertThrows(NullPointerException.class, () -> dfg.addEdge("a", null));
    }

    @Test
    public void testParallelEdges() {
        // Arrange: a and b appear in either order
        List<List<String>> traces = List.of(
            List.of("a", "b"),
            List.of("b", "a")
        );

        // Act
        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        // Assert
        assertEquals(1, dfg.getEdgeCount("a", "b"));
        assertEquals(1, dfg.getEdgeCount("b", "a"));
    }
}
