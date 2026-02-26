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

import org.yawlfoundation.yawl.pi.PIException;

/**
 * Bridge for converting proprietary event log formats to OCEL2 (Object-Centric Event Log v2.0).
 *
 * <p>Implementations handle format-specific parsing and conversion to standardized OCEL2 JSON.
 * Each implementation infers the schema from a sample and then converts full datasets.</p>
 *
 * <p>Supported formats:
 * <ul>
 *   <li>CSV - comma-separated values with header row</li>
 *   <li>JSON - array of objects with event attributes</li>
 *   <li>XML - hierarchical XML with event elements</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public interface OcedBridge {

    /**
     * Infer the OCEL2 schema from a raw data sample.
     *
     * <p>Uses heuristics or AI (via Z.AI) to identify which columns/fields map to:
     * case ID, activity, timestamp, and other semantic elements.</p>
     *
     * @param rawSample sample of raw data (e.g., first few rows/objects)
     * @return inferred OCEL2 schema
     * @throws PIException if schema inference fails
     */
    OcedSchema inferSchema(String rawSample) throws PIException;

    /**
     * Convert raw event data to OCEL2 v2.0 JSON format.
     *
     * <p>Uses the provided schema to map raw data columns/fields to OCEL2 events and objects.
     * Returns a complete OCEL2 v2.0 JSON structure ready for process mining.</p>
     *
     * @param rawData the complete raw event data
     * @param schema the OCEL2 schema defining column/field mapping
     * @return OCEL2 v2.0 JSON string
     * @throws PIException if conversion fails
     */
    String convert(String rawData, OcedSchema schema) throws PIException;

    /**
     * Get the canonical format name this bridge handles.
     *
     * @return format name (e.g., "csv", "json", "xml")
     */
    String formatName();
}
