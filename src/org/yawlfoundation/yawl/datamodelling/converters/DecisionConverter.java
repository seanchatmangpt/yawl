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

import org.yawlfoundation.yawl.datamodelling.models.DataModellingDecision;

/**
 * Converter for decision JSON ↔ typed {@link DataModellingDecision} objects.
 *
 * <p>Provides bidirectional conversion between raw JSON strings from
 * DataModellingBridge (MADR format) and type-safe decision domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DecisionConverter {

    private DecisionConverter() {
        // Utility class, no instantiation
    }

    /**
     * Parses decision JSON into a typed DataModellingDecision object.
     *
     * @param json  decision JSON string (MADR format); must not be null
     * @return typed decision; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON parsing fails
     */
    public static DataModellingDecision fromJson(String json) {
        return JsonObjectMapper.parseJson(json, DataModellingDecision.class);
    }

    /**
     * Serializes a DataModellingDecision to JSON string.
     *
     * @param decision  the decision to serialize; must not be null
     * @return JSON string; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON serialization fails
     */
    public static String toJson(DataModellingDecision decision) {
        return JsonObjectMapper.toJson(decision);
    }

    /**
     * Creates a new decision builder with default ID.
     *
     * @return a new builder; never null
     */
    public static DataModellingDecision.Builder newBuilder() {
        return DataModellingDecision.builder();
    }
}
