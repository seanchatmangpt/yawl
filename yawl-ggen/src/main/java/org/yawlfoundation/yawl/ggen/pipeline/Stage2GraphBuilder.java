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
import org.yawlfoundation.yawl.ggen.model.ProcessSpec;
import org.yawlfoundation.yawl.ggen.model.ProcessGraph;
import org.yawlfoundation.yawl.ggen.model.GraphNode;
import org.yawlfoundation.yawl.ggen.model.GraphEdge;
import org.yawlfoundation.yawl.ggen.model.SplitDef;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Stage 2: ProcessSpec → ProcessGraph builder.
 *
 * <p>Transforms structured ProcessSpec into graph representation with explicit
 * control flow constructs (XOR/AND/OR splits and joins).
 *
 * <h2>YAWL-Specific Features:</h2>
 * <ul>
 *   <li>OR-join/OR-split for multi-instance synchronization</li>
 *   <li>Cancellation regions for exception handling</li>
 *   <li>Deferred choice via OR constructs</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Stage2GraphBuilder builder = new Stage2GraphBuilder();
 *
 * ProcessGraph graph = builder.build(spec);
 *
 * // graph.nodes() contains tasks + gateways
 * // graph.edges() contains flow connections
 * // graph.orSplits() contains OR split definitions
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class Stage2GraphBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DspyModule graphPredictor;

    /**
     * Create a new Stage2GraphBuilder.
     */
    public Stage2GraphBuilder() {
        DspySignature sig = Dspy.signatureBuilder()
            .description("Transform structured specification into process graph with control flow")
            .input("process_spec", "ProcessSpec from Stage 1 as JSON")
            .output("nodes", "JSON array of graph nodes (tasks, gateways, conditions)")
            .output("edges", "JSON array of graph edges with conditions")
            .output("input_condition", "Input condition node ID")
            .output("output_condition", "Output condition node ID")
            .output("xor_splits", "JSON array of XOR split definitions")
            .output("and_splits", "JSON array of AND split definitions")
            .output("or_splits", "JSON array of OR split definitions (YAWL-specific)")
            .build();

        this.graphPredictor = Dspy.chainOfThought(sig);
    }

    /**
     * Build ProcessGraph from ProcessSpec.
     *
     * @param spec ProcessSpec from Stage 1
     * @return Generated ProcessGraph
     */
    public ProcessGraph build(ProcessSpec spec) {
        DspyResult result = graphPredictor.predict(
            "process_spec", spec.toJson()
        );

        return parseResult(result);
    }

    private ProcessGraph parseResult(DspyResult result) {
        List<GraphNode> nodes = parseNodes(result.getString("nodes"));
        List<GraphEdge> edges = parseEdges(result.getString("edges"));
        String inputCondition = result.getString("input_condition");
        String outputCondition = result.getString("output_condition");
        List<SplitDef> xorSplits = parseSplits(result.getString("xor_splits"));
        List<SplitDef> andSplits = parseSplits(result.getString("and_splits"));
        List<SplitDef> orSplits = parseSplits(result.getString("or_splits"));

        return new ProcessGraph(nodes, edges, inputCondition, outputCondition,
            xorSplits, andSplits, orSplits, Map.of());
    }

    private List<GraphNode> parseNodes(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<GraphNode>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<GraphEdge> parseEdges(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<GraphEdge>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<SplitDef> parseSplits(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<SplitDef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
