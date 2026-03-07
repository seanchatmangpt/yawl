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

import java.util.Map;

/**
 * Graph node in a ProcessGraph.
 *
 * @param id Unique node identifier
 * @param type Node type: "task", "condition", "xor_gateway", "and_gateway", "or_gateway"
 * @param name Human-readable name
 * @param taskRef Reference to TaskDef ID (for task nodes)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphNode(
    String id,
    String type,
    String name,
    String taskRef,
    Map<String, Object> metadata
) {

    public GraphNode {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a task node.
     */
    public static GraphNode task(String id, String name, String taskRef) {
        return new GraphNode(id, "task", name, taskRef, Map.of());
    }

    /**
     * Create a condition node (input/output).
     */
    public static GraphNode condition(String id, String name) {
        return new GraphNode(id, "condition", name, null, Map.of());
    }

    /**
     * Create an input condition.
     */
    public static GraphNode inputCondition(String id) {
        return condition(id, "Input Condition");
    }

    /**
     * Create an output condition.
     */
    public static GraphNode outputCondition(String id) {
        return condition(id, "Output Condition");
    }

    /**
     * Create an XOR gateway.
     */
    public static GraphNode xorGateway(String id, String name) {
        return new GraphNode(id, "xor_gateway", name, null, Map.of());
    }

    /**
     * Create an AND gateway.
     */
    public static GraphNode andGateway(String id, String name) {
        return new GraphNode(id, "and_gateway", name, null, Map.of());
    }

    /**
     * Create an OR gateway (YAWL-specific).
     */
    public static GraphNode orGateway(String id, String name) {
        return new GraphNode(id, "or_gateway", name, null, Map.of());
    }

    /**
     * Check if this is a task node.
     */
    public boolean isTask() {
        return "task".equals(type);
    }

    /**
     * Check if this is a gateway node.
     */
    public boolean isGateway() {
        return type != null && type.endsWith("_gateway");
    }

    /**
     * Check if this is a condition node.
     */
    public boolean isCondition() {
        return "condition".equals(type);
    }

    /**
     * Check if this is an OR gateway.
     */
    public boolean isOrGateway() {
        return "or_gateway".equals(type);
    }
}
