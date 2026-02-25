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
 */

package org.yawlfoundation.yawl.pi.bridge;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record describing the schema of an Object-Centric Event Data (OCED) log.
 *
 * <p>Captures the mapping from raw data columns/fields to OCEL2 semantic elements:
 * case identifier, activity, timestamp, and auxiliary object/attribute columns.
 * Includes metadata on whether the schema was AI-inferred and when.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public record OcedSchema(
    String schemaId,
    String caseIdColumn,
    String activityColumn,
    String timestampColumn,
    List<String> objectTypeColumns,
    List<String> attributeColumns,
    String inferredFormat,
    boolean aiInferred,
    Instant inferredAt
) {
    /**
     * Create a new OCED schema with explicit format inference.
     */
    public OcedSchema {
        if (schemaId == null || schemaId.isEmpty()) {
            throw new IllegalArgumentException("schemaId is required");
        }
        if (caseIdColumn == null || caseIdColumn.isEmpty()) {
            throw new IllegalArgumentException("caseIdColumn is required");
        }
        if (activityColumn == null || activityColumn.isEmpty()) {
            throw new IllegalArgumentException("activityColumn is required");
        }
        if (timestampColumn == null || timestampColumn.isEmpty()) {
            throw new IllegalArgumentException("timestampColumn is required");
        }
        if (inferredFormat == null || inferredFormat.isEmpty()) {
            throw new IllegalArgumentException("inferredFormat is required");
        }
    }
}
