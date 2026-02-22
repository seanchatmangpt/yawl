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

package org.yawlfoundation.yawl.integration.processmining;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Result of process discovery from event logs via Rust4PM.
 *
 * <p>Captures the output of process discovery algorithms (Alpha, Heuristic, DFG)
 * applied to YAWL event logs. Includes the discovered process model (Petri net or
 * Directly-Follows Graph), conformance metrics, and performance statistics.</p>
 *
 * <p>This is an immutable record; all fields are final and initialized at construction.
 * Equality and hashCode are auto-generated based on all fields. Suitable for
 * caching in ProcessMiningSession and serialization to JSON for MCP tools.</p>
 *
 * @param processModelJson Discovered process model as JSON (e.g., Petri net or DFG).
 *                         Format: {@code {"places": [...], "transitions": [...], "arcs": [...]}}.
 *                         Must be valid JSON; empty object {@code {}} if discovery failed.
 * @param fitness Conformance fitness score (0.0-1.0). Fraction of traces replaying
 *                without violation. 1.0 = perfect fitness.
 * @param precision Conformance precision score (0.0-1.0). Fraction of model behavior
 *                  covered by observed traces. 1.0 = no overfitting.
 * @param caseCount Total number of cases (traces) analyzed.
 * @param activityCount Distinct activities discovered in the event log.
 * @param activityFrequencies Execution frequency per activity. Key = activity name,
 *                            Value = count. Unmodifiable map; empty if no activities found.
 * @param analyzedAt Timestamp when analysis was performed (UTC). Used to detect stale
 *                   results; results older than specification's last modification are
 *                   candidates for re-analysis.
 *
 * @since YAWL 6.0
 * @author YAWL Foundation
 */
public record ProcessDiscoveryResult(
        @JsonProperty("processModelJson") String processModelJson,
        @JsonProperty("fitness") double fitness,
        @JsonProperty("precision") double precision,
        @JsonProperty("caseCount") int caseCount,
        @JsonProperty("activityCount") int activityCount,
        @JsonProperty("activityFrequencies") Map<String, Long> activityFrequencies,
        @JsonProperty("analyzedAt") Instant analyzedAt) {

    /**
     * Constructs a ProcessDiscoveryResult with validation.
     *
     * @throws IllegalArgumentException if fitness or precision not in [0.0, 1.0],
     *                                  or if caseCount/activityCount are negative,
     *                                  or if analyzedAt is null
     */
    public ProcessDiscoveryResult {
        if (fitness < 0.0 || fitness > 1.0) {
            throw new IllegalArgumentException("fitness must be in [0.0, 1.0], got " + fitness);
        }
        if (precision < 0.0 || precision > 1.0) {
            throw new IllegalArgumentException("precision must be in [0.0, 1.0], got " + precision);
        }
        if (caseCount < 0) {
            throw new IllegalArgumentException("caseCount must be non-negative, got " + caseCount);
        }
        if (activityCount < 0) {
            throw new IllegalArgumentException("activityCount must be non-negative, got " + activityCount);
        }
        if (analyzedAt == null) {
            throw new IllegalArgumentException("analyzedAt must not be null");
        }

        // Defensive copy: wrap activityFrequencies as unmodifiable map
        activityFrequencies = activityFrequencies != null
                ? Collections.unmodifiableMap(activityFrequencies)
                : Collections.emptyMap();
    }

    /**
     * Returns an empty/zero ProcessDiscoveryResult.
     *
     * <p>Used as a sentinel when discovery fails or no data is available.
     * All metrics are zero; processModelJson is "{}".</p>
     *
     * @return empty result with current timestamp
     */
    public static ProcessDiscoveryResult empty() {
        return new ProcessDiscoveryResult(
                "{}",
                0.0,
                0.0,
                0,
                0,
                Collections.emptyMap(),
                Instant.now()
        );
    }

    /**
     * Converts this result to JSON string representation.
     *
     * <p>Serializes all fields to JSON using Jackson ObjectMapper.
     * ProcessModelJson is embedded as-is (parsed as JSON object if valid).
     * UsedBy MCP tools and external storage.</p>
     *
     * @return JSON string representation
     * @throws RuntimeException if JSON serialization fails (should not happen
     *                          with valid input)
     */
    public String toJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();

        // Embed processModelJson as parsed JSON object (not string)
        try {
            node.set("processModel", mapper.readTree(processModelJson));
        } catch (Exception e) {
            // If processModelJson is not valid JSON, embed as string
            node.put("processModel", processModelJson);
        }

        node.put("fitness", fitness);
        node.put("precision", precision);
        node.put("caseCount", caseCount);
        node.put("activityCount", activityCount);

        // Embed activityFrequencies as object
        var freqNode = mapper.createObjectNode();
        for (var entry : activityFrequencies.entrySet()) {
            freqNode.put(entry.getKey(), entry.getValue());
        }
        node.set("activityFrequencies", freqNode);

        node.put("analyzedAt", analyzedAt.toString());

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize ProcessDiscoveryResult to JSON", e);
        }
    }
}
