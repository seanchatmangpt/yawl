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

package org.yawlfoundation.yawl.pi.bridge;

import org.yawlfoundation.yawl.pi.PIException;

import java.util.Map;

/**
 * Factory for creating OcedBridge implementations by format name.
 * Supports auto-detection based on content sniffing.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class OcedBridgeFactory {

    private static final SchemaInferenceEngine INFERENCE_ENGINE = new SchemaInferenceEngine(null);
    private static final Map<String, OcedBridge> BRIDGES = Map.of(
        "csv", new CsvOcedBridge(INFERENCE_ENGINE),
        "json", new JsonOcedBridge(INFERENCE_ENGINE),
        "xml", new XmlOcedBridge(INFERENCE_ENGINE)
    );

    private OcedBridgeFactory() {
        throw new UnsupportedOperationException(
            "OcedBridgeFactory is a static utility class and cannot be instantiated.");
    }

    /**
     * Get bridge for a named format (case-insensitive).
     *
     * @param format format name (csv, json, or xml)
     * @return OcedBridge implementation for the format
     * @throws PIException if format is unknown
     */
    public static OcedBridge forFormat(String format) throws PIException {
        if (format == null || format.isEmpty()) {
            throw new PIException("Format cannot be null or empty", "dataprep");
        }
        OcedBridge bridge = BRIDGES.get(format.toLowerCase());
        if (bridge == null) {
            throw new PIException("Unknown format: " + format + ". Supported: csv, json, xml", "dataprep");
        }
        return bridge;
    }

    /**
     * Auto-detect format from raw data content.
     * Tries XML first (starts with '<'), then JSON (starts with '[' or '{'), then CSV.
     *
     * @param rawData raw event data content
     * @return OcedBridge implementation detected from content
     * @throws PIException if autodetection fails
     */
    public static OcedBridge autoDetect(String rawData) throws PIException {
        if (rawData == null || rawData.isEmpty()) {
            throw new PIException("Raw data cannot be null or empty for auto-detection", "dataprep");
        }

        String trimmed = rawData.strip();
        if (trimmed.startsWith("<")) {
            return BRIDGES.get("xml");
        } else if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return BRIDGES.get("json");
        } else {
            return BRIDGES.get("csv");
        }
    }
}
