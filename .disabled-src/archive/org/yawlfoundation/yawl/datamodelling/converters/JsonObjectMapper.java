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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;

/**
 * Shared Jackson ObjectMapper singleton for all datamodelling JSON conversions.
 *
 * <p>Provides a centralized, thread-safe ObjectMapper configured for ODCS v3.1.0
 * schema marshaling/unmarshaling. Uses standard Jackson configuration with
 * pretty-printing disabled for compact JSON output.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Parse JSON to typed object
 * DataModellingWorkspace ws = JsonObjectMapper.getInstance()
 *     .readValue(jsonString, DataModellingWorkspace.class);
 *
 * // Serialize typed object to JSON
 * String json = JsonObjectMapper.getInstance()
 *     .writeValueAsString(workspace);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class JsonObjectMapper {

    private static final Logger log = LoggerFactory.getLogger(JsonObjectMapper.class);

    private static final ObjectMapper MAPPER = createMapper();

    private JsonObjectMapper() {
        // Utility class, no instantiation
    }

    /**
     * Gets the shared Jackson ObjectMapper instance.
     *
     * @return the configured ObjectMapper singleton; never null
     */
    public static ObjectMapper getInstance() {
        return MAPPER;
    }

    /**
     * Creates and configures the ObjectMapper for ODCS schema processing.
     *
     * @return a new ObjectMapper with standard settings
     */
    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.findAndRegisterModules(); // Auto-register modules for Java 8+ dates
        log.debug("JsonObjectMapper initialised with standard Jackson configuration");
        return mapper;
    }

    /**
     * Parses JSON string into a typed object.
     *
     * @param <T>        the target type
     * @param json       JSON string; must not be null
     * @param clazz      target class; must not be null
     * @return typed object; never null
     * @throws DataModellingException  JSON_PARSE_ERROR if parsing fails
     */
    public static <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            throw new DataModellingException(
                "JSON string cannot be null or empty",
                DataModellingException.ErrorKind.JSON_PARSE_ERROR
            );
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new DataModellingException(
                "Failed to parse JSON to " + clazz.getSimpleName() + ": " + e.getMessage(),
                DataModellingException.ErrorKind.JSON_PARSE_ERROR,
                e
            );
        }
    }

    /**
     * Serializes a typed object to JSON string.
     *
     * @param obj  object to serialize; must not be null
     * @return JSON string; never null
     * @throws DataModellingException  JSON_SERIALIZE_ERROR if serialization fails
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            throw new DataModellingException(
                "Object cannot be null",
                DataModellingException.ErrorKind.JSON_SERIALIZE_ERROR
            );
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new DataModellingException(
                "Failed to serialize " + obj.getClass().getSimpleName() + ": " + e.getMessage(),
                DataModellingException.ErrorKind.JSON_SERIALIZE_ERROR,
                e
            );
        }
    }
}
