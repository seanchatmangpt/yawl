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

package org.yawlfoundation.yawl.datamodelling.converters;

import org.yawlfoundation.yawl.datamodelling.models.DataModellingColumn;

/**
 * Converter for column JSON ↔ typed {@link DataModellingColumn} objects.
 *
 * <p>Provides bidirectional conversion between raw JSON strings from
 * DataModellingBridge and type-safe column domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class ColumnConverter {

    private ColumnConverter() {
        // Utility class, no instantiation
    }

    /**
     * Parses column JSON into a typed DataModellingColumn object.
     *
     * @param json  column JSON string; must not be null
     * @return typed column; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON parsing fails
     */
    public static DataModellingColumn fromJson(String json) {
        return JsonObjectMapper.parseJson(json, DataModellingColumn.class);
    }

    /**
     * Serializes a DataModellingColumn to JSON string.
     *
     * @param column  the column to serialize; must not be null
     * @return JSON string; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON serialization fails
     */
    public static String toJson(DataModellingColumn column) {
        return JsonObjectMapper.toJson(column);
    }

    /**
     * Creates a new column builder with default ID.
     *
     * @return a new builder; never null
     */
    public static DataModellingColumn.Builder newBuilder() {
        return DataModellingColumn.builder();
    }
}
