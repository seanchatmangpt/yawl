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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import java.util.Objects;

/**
 * An MCP tool descriptor derived from the marketplace schema graph.
 *
 * <p>Each instance corresponds to one marketplace operation (e.g.
 * {@code marketplace_list_agents}), with the JSON Schema for its input parameters
 * stored as a string for direct inclusion in an MCP server's tool manifest.</p>
 *
 * @param name            MCP tool name (snake_case, e.g. {@code "marketplace_list_agents"})
 * @param description     human-readable description of what the tool does
 * @param inputSchemaJson JSON Schema object string for the tool's input parameters
 * @since YAWL 6.0
 */
public record McpToolDescriptor(
        String name,
        String description,
        String inputSchemaJson) {

    public McpToolDescriptor {
        Objects.requireNonNull(name,            "name must not be null");
        Objects.requireNonNull(description,     "description must not be null");
        Objects.requireNonNull(inputSchemaJson, "inputSchemaJson must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
    }
}
