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
 * Data object definition in a ProcessSpec.
 *
 * @param id Unique data object identifier (snake_case)
 * @param name Human-readable data object name
 * @param type Data type: "string", "integer", "boolean", "date", "object"
 * @param initialValue Initial value expression (optional)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataObjectDef(
    String id,
    String name,
    String type,
    String initialValue,
    Map<String, Object> metadata
) {

    public DataObjectDef {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a simple data object.
     */
    public static DataObjectDef of(String id, String name, String type) {
        return new DataObjectDef(id, name, type, null, Map.of());
    }

    /**
     * Create a string data object.
     */
    public static DataObjectDef string(String id, String name) {
        return of(id, name, "string");
    }

    /**
     * Create an integer data object.
     */
    public static DataObjectDef integer(String id, String name) {
        return of(id, name, "integer");
    }

    /**
     * Create a boolean data object.
     */
    public static DataObjectDef bool(String id, String name) {
        return of(id, name, "boolean");
    }

    /**
     * Create a data object with initial value.
     */
    public static DataObjectDef withInitial(String id, String name, String type, String initialValue) {
        return new DataObjectDef(id, name, type, initialValue, Map.of());
    }
}
