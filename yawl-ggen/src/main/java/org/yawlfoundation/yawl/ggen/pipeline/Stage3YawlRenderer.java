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

package org.yawlfoundation.yawl.ggen.pipeline;

import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.dspy.fluent.DspyModule;
import org.yawlfoundation.yawl.dspy.fluent.DspyResult;
import org.yawlfoundation.yawl.dspy.fluent.DspySignature;
import org.yawlfoundation.yawl.ggen.model.ProcessGraph;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;

/**
 * Stage 3: ProcessGraph → YAWL XML renderer.
 *
 * <p>Transforms ProcessGraph into valid YAWL XML specification.
 * Uses DSPy Predict (not Chain-of-Thought) for structured output.
 *
 * <h2>YAWL XML Structure:</h2>
 * <ul>
 *   <li>Specification element with uri, version</li>
 *   <li>Decompositions (root net and sub-nets)</li>
 *   <li>Net elements: conditions, tasks, gateways</li>
 *   <li>Flows connecting elements</li>
 *   <li>OR-join/OR-split constructs</li>
 *   <li>Cancellation regions</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Stage3YawlRenderer renderer = new Stage3YawlRenderer();
 *
 * YawlSpec spec = renderer.render(graph, "PatientAdmission", "1.0");
 *
 * // spec.yawlXml() contains complete YAWL XML
 * // spec.specId() == "PatientAdmission"
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class Stage3YawlRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DspyModule yawlPredictor;

    /**
     * Create a new Stage3YawlRenderer.
     */
    public Stage3YawlRenderer() {
        DspySignature sig = Dspy.signatureBuilder()
            .description("Transform process graph into valid YAWL XML specification")
            .input("process_graph", "ProcessGraph from Stage 2 as JSON")
            .input("spec_id", "YAWL specification ID")
            .input("spec_version", "Specification version (e.g., 0.1, 1.0)")
            .output("yawl_xml", "Complete YAWL XML specification conforming to YAWL XSD")
            .output("decomposition_id", "Root decomposition ID")
            .output("metadata", "JSON object with generation metadata (task_count, or_join_count, etc.)")
            .build();

        this.yawlPredictor = Dspy.predict(sig);
    }

    /**
     * Render ProcessGraph to YAWL XML.
     *
     * @param graph ProcessGraph from Stage 2
     * @param specId YAWL specification ID
     * @param version Specification version
     * @return Generated YawlSpec
     */
    public YawlSpec render(ProcessGraph graph, String specId, String version) {
        long startTime = System.currentTimeMillis();

        DspyResult result = yawlPredictor.predict(
            "process_graph", graph.toJson(),
            "spec_id", specId,
            "spec_version", version
        );

        long generationTime = System.currentTimeMillis() - startTime;

        return parseResult(result, specId, version, generationTime);
    }

    /**
     * Render with default version (1.0).
     */
    public YawlSpec render(ProcessGraph graph, String specId) {
        return render(graph, specId, "1.0");
    }

    private YawlSpec parseResult(DspyResult result, String specId, String version, long generationTimeMs) {
        String yawlXml = result.getString("yawl_xml");
        String decompositionId = result.getString("decomposition_id");
        Map<String, Object> metadata = parseMetadata(result.getString("metadata"));

        return YawlSpec.full(
            yawlXml,
            specId,
            version,
            decompositionId,
            "http://yawlfoundation.org/specs/" + specId,
            generationTimeMs
        );
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
