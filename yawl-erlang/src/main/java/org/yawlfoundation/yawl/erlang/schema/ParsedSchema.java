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

import java.util.List;

/**
 * A parsed ODCS schema loaded from a classpath YAML resource.
 *
 * @param name         the schema name (from the YAML {@code name} field)
 * @param version      the schema version (from the YAML {@code version} field, may be empty)
 * @param fields       all declared fields with their type and required status
 * @param resourcePath the classpath path from which this schema was loaded (for diagnostics)
 */
public record ParsedSchema(
        String name,
        String version,
        List<SchemaField> fields,
        String resourcePath) {

    public ParsedSchema {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("name must be non-blank");
        if (version == null)
            throw new IllegalArgumentException("version must be non-null (use empty string if absent)");
        if (fields == null)
            throw new IllegalArgumentException("fields must be non-null");
        if (resourcePath == null || resourcePath.isBlank())
            throw new IllegalArgumentException("resourcePath must be non-blank");
        fields = List.copyOf(fields);
    }

    /** Returns only the required fields. */
    public List<SchemaField> requiredFields() {
        return fields.stream().filter(SchemaField::required).toList();
    }

    /** Returns a human-readable summary for error messages. */
    public String summary() {
        return name + "@" + version + " (" + fields.size() + " fields, "
                + requiredFields().size() + " required)";
    }
}
