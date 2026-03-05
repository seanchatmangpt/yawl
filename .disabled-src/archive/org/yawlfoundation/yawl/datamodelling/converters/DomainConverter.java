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

import org.yawlfoundation.yawl.datamodelling.models.DataModellingDomain;

/**
 * Converter for domain JSON ↔ typed {@link DataModellingDomain} objects.
 *
 * <p>Provides bidirectional conversion between raw JSON strings from
 * DataModellingBridge and type-safe domain domain objects.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DomainConverter {

    private DomainConverter() {
        // Utility class, no instantiation
    }

    /**
     * Parses domain JSON into a typed DataModellingDomain object.
     *
     * @param json  domain JSON string; must not be null
     * @return typed domain; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON parsing fails
     */
    public static DataModellingDomain fromJson(String json) {
        return JsonObjectMapper.parseJson(json, DataModellingDomain.class);
    }

    /**
     * Serializes a DataModellingDomain to JSON string.
     *
     * @param domain  the domain to serialize; must not be null
     * @return JSON string; never null
     * @throws org.yawlfoundation.yawl.datamodelling.DataModellingException
     *         if JSON serialization fails
     */
    public static String toJson(DataModellingDomain domain) {
        return JsonObjectMapper.toJson(domain);
    }

    /**
     * Creates a new domain builder with default ID.
     *
     * @return a new builder; never null
     */
    public static DataModellingDomain.Builder newBuilder() {
        return DataModellingDomain.builder();
    }
}
