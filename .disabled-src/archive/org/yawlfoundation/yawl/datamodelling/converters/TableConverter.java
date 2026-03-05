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

import org.yawlfoundation.yawl.datamodelling.models.DataModellingTable;

/**
 * Converter for table JSON ↔ typed {@link DataModellingTable} objects.
 *
 * <p>Provides bidirectional conversion between raw JSON strings from
 * DataModellingBridge and type-safe table domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class TableConverter {

    private TableConverter() {
        // Utility class, no instantiation
    }

    /**
     * Parses table JSON into a typed DataModellingTable object.
     *
     * @param json  table JSON string; must not be null
     * @return typed table; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON parsing fails
     */
    public static DataModellingTable fromJson(String json) {
        return JsonObjectMapper.parseJson(json, DataModellingTable.class);
    }

    /**
     * Serializes a DataModellingTable to JSON string.
     *
     * @param table  the table to serialize; must not be null
     * @return JSON string; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON serialization fails
     */
    public static String toJson(DataModellingTable table) {
        return JsonObjectMapper.toJson(table);
    }

    /**
     * Creates a new table builder with default ID.
     *
     * @return a new builder; never null
     */
    public static DataModellingTable.Builder newBuilder() {
        return DataModellingTable.builder();
    }
}
