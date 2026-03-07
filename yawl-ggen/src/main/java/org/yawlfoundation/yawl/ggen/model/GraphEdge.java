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
 * Graph edge in a ProcessGraph.
 *
 * @param from Source node ID
 * @param to Target node ID
 * @param condition Guard condition (optional)
 * @param predicate XPath predicate for conditional flow (optional)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphEdge(
    String from,
    String to,
    String condition,
    String predicate,
    Map<String, Object> metadata
) {

    public GraphEdge {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a simple edge.
     */
    public static GraphEdge of(String from, String to) {
        return new GraphEdge(from, to, null, null, Map.of());
    }

    /**
     * Create a conditional edge.
     */
    public static GraphEdge conditional(String from, String to, String condition) {
        return new GraphEdge(from, to, condition, null, Map.of());
    }

    /**
     * Create an edge with XPath predicate.
     */
    public static GraphEdge withPredicate(String from, String to, String predicate) {
        return new GraphEdge(from, to, null, predicate, Map.of());
    }

    /**
     * Check if this is a conditional edge.
     */
    public boolean isConditional() {
        return condition != null && !condition.isEmpty();
    }

    /**
     * Check if this edge has a predicate.
     */
    public boolean hasPredicate() {
        return predicate != null && !predicate.isEmpty();
    }
}
