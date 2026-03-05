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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.tooling.intellij.visualization;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Builds a platform-independent graph model of a YAWL net for use in the
 * IntelliJ IDEA diagram preview pane.
 *
 * The resulting {@link NetGraph} is a pure Java data structure with no IntelliJ
 * Platform SDK dependencies, enabling testing without an IDE process. The IntelliJ
 * plugin's diagram panel adapter converts the {@link NetGraph} into a
 * {@code com.intellij.uml.api.diagram.UmlDiagramProvider} data model.
 *
 * Layout algorithm:
 * <ul>
 *   <li>The graph is rendered left-to-right (LR) following topological order</li>
 *   <li>The input condition is always at x=0</li>
 *   <li>Subsequent elements are placed at x = predecessor_x + HORIZONTAL_STEP</li>
 *   <li>Parallel branches are offset vertically by VERTICAL_STEP per rank</li>
 *   <li>XOR/OR splits are annotated with a diamond decorator</li>
 *   <li>AND splits are annotated with a bar decorator</li>
 * </ul>
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
public final class YawlNetGraphBuilder {

    /** Horizontal pixel spacing between node ranks */
    public static final int HORIZONTAL_STEP = 160;

    /** Vertical pixel spacing between parallel branches */
    public static final int VERTICAL_STEP = 80;

    private YawlNetGraphBuilder() {
        throw new UnsupportedOperationException("YawlNetGraphBuilder is a utility class");
    }

    /**
     * Build a {@link NetGraph} from the given YAWL net.
     *
     * @param net the YAWL net to visualise (must not be null)
     * @return platform-independent graph model
     */
    public static NetGraph build(YNet net) {
        if (net == null) {
            throw new IllegalArgumentException("YNet cannot be null");
        }

        Collection<YExternalNetElement> elements = net.getNetElements().values();
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        // Build nodes
        for (YExternalNetElement el : elements) {
            NodeShape shape  = determineShape(el);
            String    label  = buildLabel(el);
            String    detail = buildDetail(el);
            // Coordinates are set during layout pass below
            nodes.add(new GraphNode(el.getID(), label, detail, shape, 0, 0));
        }

        // Build edges from flows
        int taskCount      = 0;
        int conditionCount = 0;
        for (YExternalNetElement el : elements) {
            for (YFlow flow : el.getPostsetFlows()) {
                EdgeStyle style = flow.isDefaultFlow() ? EdgeStyle.DEFAULT_FLOW
                        : (flow.getXpathPredicate() != null ? EdgeStyle.PREDICATE_FLOW
                        : EdgeStyle.NORMAL_FLOW);
                String label = flow.getXpathPredicate() != null
                        ? abbreviate(flow.getXpathPredicate(), 30) : null;
                edges.add(new GraphEdge(
                        el.getID(),
                        flow.getNextElement().getID(),
                        label,
                        style));
            }

            if (el instanceof YAtomicTask || el instanceof YCompositeTask) taskCount++;
            else if (el instanceof YCondition) conditionCount++;
        }

        // Assign layout coordinates using a simple BFS topological rank
        applyLayout(net, nodes);

        return new NetGraph(net.getID(), nodes, edges, taskCount, conditionCount);
    }

    // ---- Layout --------------------------------------------------------------

    /**
     * Assign x,y coordinates to nodes using BFS from the input condition.
     * Nodes are placed in topological order; parallel branches are offset vertically.
     */
    private static void applyLayout(YNet net, List<GraphNode> nodes) {
        // Build a mutable index for updating coordinates
        java.util.Map<String, int[]> coords = new java.util.HashMap<>();
        for (GraphNode n : nodes) {
            coords.put(n.id(), new int[]{0, 0});
        }

        // BFS from input condition
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        java.util.Set<String>   visited = new java.util.HashSet<>();

        if (net.getInputCondition() != null) {
            queue.add(net.getInputCondition().getID());
        }

        int yOffset = 0;
        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            if (!visited.add(currentId)) continue;

            YExternalNetElement current = net.getNetElement(currentId);
            if (current == null) continue;

            int[] currentCoords = coords.get(currentId);
            int rank = 0;
            for (YFlow flow : current.getPostsetFlows()) {
                String nextId = flow.getNextElement().getID();
                if (!visited.contains(nextId)) {
                    int[] nextCoords = coords.get(nextId);
                    nextCoords[0] = currentCoords[0] + HORIZONTAL_STEP;
                    nextCoords[1] = currentCoords[1] + (rank * VERTICAL_STEP);
                    queue.add(nextId);
                    rank++;
                }
                yOffset = Math.max(yOffset, currentCoords[1] + (rank * VERTICAL_STEP));
            }
        }

        // Update node coordinates
        for (int i = 0; i < nodes.size(); i++) {
            GraphNode n    = nodes.get(i);
            int[]     xy   = coords.get(n.id());
            nodes.set(i, new GraphNode(n.id(), n.label(), n.detail(), n.shape(), xy[0], xy[1]));
        }
    }

    // ---- Node classification -------------------------------------------------

    private static NodeShape determineShape(YExternalNetElement el) {
        return switch (el) {
            case YInputCondition  ic -> NodeShape.INPUT_CONDITION;
            case YOutputCondition oc -> NodeShape.OUTPUT_CONDITION;
            case YAtomicTask      at -> {
                int splitType = at.getSplitType();
                yield splitType == YTask._AND ? NodeShape.TASK_AND_SPLIT
                     : splitType == YTask._XOR || splitType == YTask._OR ? NodeShape.TASK_XOR_SPLIT
                     : NodeShape.ATOMIC_TASK;
            }
            case YCompositeTask ct -> NodeShape.COMPOSITE_TASK;
            case YCondition      c -> NodeShape.CONDITION;
            default                -> NodeShape.ATOMIC_TASK;
        };
    }

    private static String buildLabel(YExternalNetElement el) {
        String name = el.getName();
        if (name != null && !name.isBlank()) return name;
        return el.getID();
    }

    private static String buildDetail(YExternalNetElement el) {
        if (!(el instanceof YTask task)) {
            return el.getClass().getSimpleName();
        }
        return "join:" + taskTypeToString(task.getJoinType()) +
               " split:" + taskTypeToString(task.getSplitType());
    }

    /**
     * Convert a YTask join/split type int constant to its string representation.
     * Mirrors the private {@code decoratorTypeToString} logic in YTask.
     */
    private static String taskTypeToString(int type) {
        if (type == YTask._AND) return "and";
        if (type == YTask._OR)  return "or";
        if (type == YTask._XOR) return "xor";
        return "and"; // default per YAWL spec
    }

    private static String abbreviate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    // ---- Graph model ---------------------------------------------------------

    /**
     * Node shape categories for the diagram renderer.
     */
    public enum NodeShape {
        /** Filled circle — input condition */
        INPUT_CONDITION,
        /** Double circle — output condition */
        OUTPUT_CONDITION,
        /** Rounded rectangle — atomic task */
        ATOMIC_TASK,
        /** Rounded rectangle with + badge — AND-split task */
        TASK_AND_SPLIT,
        /** Rounded rectangle with X badge — XOR/OR-split task */
        TASK_XOR_SPLIT,
        /** Thick-bordered rounded rectangle — composite (subprocess) task */
        COMPOSITE_TASK,
        /** Plain rectangle — intermediate condition */
        CONDITION
    }

    /**
     * Edge style categories for the diagram renderer.
     */
    public enum EdgeStyle {
        /** Solid line with arrow */
        NORMAL_FLOW,
        /** Dashed line with diamond label — has XPath predicate */
        PREDICATE_FLOW,
        /** Dotted line — default flow */
        DEFAULT_FLOW
    }

    /**
     * Immutable graph node with layout coordinates.
     */
    public record GraphNode(
            String id,
            String label,
            String detail,
            NodeShape shape,
            int x,
            int y
    ) { }

    /**
     * Immutable directed graph edge.
     */
    public record GraphEdge(
            String fromId,
            String toId,
            String label,
            EdgeStyle style
    ) { }

    /**
     * Immutable platform-independent net graph.
     */
    public record NetGraph(
            String netId,
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            int taskCount,
            int conditionCount
    ) {
        public int nodeCount() { return nodes.size(); }
        public int edgeCount() { return edges.size(); }

        /** Return nodes as an unmodifiable view. */
        public List<GraphNode> nodes() {
            return List.copyOf(nodes);
        }

        /** Return edges as an unmodifiable view. */
        public List<GraphEdge> edges() {
            return List.copyOf(edges);
        }
    }
}
