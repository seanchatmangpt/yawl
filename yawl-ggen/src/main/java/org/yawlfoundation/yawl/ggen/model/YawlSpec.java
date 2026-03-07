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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * YAWL specification - output of Stage 3 (Graph → YAWL).
 *
 * <p>This is the final YAWL XML output ready for loading into YEngine.
 *
 * @param yawlXml Complete YAWL XML specification
 * @param specId YAWL specification ID
 * @param specVersion Specification version
 * @param decompositionId Root decomposition ID
 * @param uri Specification URI
 * @param generatedAt Generation timestamp
 * @param generationTimeMs Total generation time in milliseconds
 * @param metadata Generation metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record YawlSpec(
    @JsonProperty("yawl_xml") String yawlXml,
    @JsonProperty("spec_id") String specId,
    @JsonProperty("spec_version") String specVersion,
    @JsonProperty("decomposition_id") String decompositionId,
    String uri,
    @JsonProperty("generated_at") Instant generatedAt,
    @JsonProperty("generation_time_ms") Long generationTimeMs,
    Map<String, Object> metadata
) {

    public YawlSpec {
        generatedAt = generatedAt != null ? generatedAt : Instant.now();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a YawlSpec from XML.
     */
    public static YawlSpec of(String yawlXml, String specId, String specVersion) {
        return new YawlSpec(yawlXml, specId, specVersion, specId, null, Instant.now(), null, Map.of());
    }

    /**
     * Create a YawlSpec with all fields.
     */
    public static YawlSpec full(String yawlXml, String specId, String specVersion,
                                String decompositionId, String uri, Long generationTimeMs) {
        return new YawlSpec(yawlXml, specId, specVersion, decompositionId, uri,
            Instant.now(), generationTimeMs, Map.of());
    }

    /**
     * Get XML length in characters.
     */
    public int xmlLength() {
        return yawlXml != null ? yawlXml.length() : 0;
    }

    /**
     * Check if XML is non-empty.
     */
    public boolean hasXml() {
        return yawlXml != null && !yawlXml.isEmpty();
    }
}
