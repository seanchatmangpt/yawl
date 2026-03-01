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
package org.yawlfoundation.yawl.erlang.schema;

/**
 * A single field declared in an ODCS schema.
 *
 * @param name     the JSON field name (case-sensitive)
 * @param type     the declared type string (e.g., "string", "integer", "number", "boolean", "object", "array")
 * @param required whether the field must be present in conforming JSON
 */
public record SchemaField(String name, String type, boolean required) {

    public SchemaField {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name must be non-blank");
        if (type == null || type.isBlank())
            throw new IllegalArgumentException("type must be non-blank");
    }
}
