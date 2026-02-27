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

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.ggen.polyglot.PowlJsonMarshaller;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintMatrix;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintScorer;
import org.yawlfoundation.yawl.integration.mcp.util.McpResponseBuilder;
import org.yawlfoundation.yawl.integration.mcp.util.McpLogger;
import org.yawlfoundation.yawl.integration.util.SimilarityMetrics;
import org.yawlfoundation.yawl.integration.util.YawlConstants;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * MCP tool specifications for behavioral conformance checking — no LLM required.
 *
 * <p>Exposes two tools based on {@link FootprintExtractor} and {@link FootprintScorer}:</p>
 * <ul>
 *   <li>{@code yawl_extract_footprint} — extracts a behavioral fingerprint
 *       (footprint matrix) from a POWL model JSON string.</li>
 *   <li>{@code yawl_compare_conformance} — compares two POWL model JSON strings
 *       and returns a Jaccard conformance score in [0.0, 1.0].</li>
 * </ul>
 *
 * <p>Both tools accept POWL JSON as produced by {@code powl_generator.py} or
 * {@code PowlJsonMarshaller.toJson(model)}.</p>
 *
 * <p>Conformance interpretation:</p>
 * <ul>
 *   <li>&ge; 0.8 → HIGH conformance</li>
 *   <li>0.5 – 0.8 → MEDIUM conformance</li>
 *   <li>&lt; 0.5 → LOW conformance</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public final class YawlConformanceToolSpecifications {

    private final FootprintExtractor extractor;
    private final McpLogger logger = McpLogger.forTool(YawlConstants.TOOL_CONFORMANCE);

    public YawlConformanceToolSpecifications() {
        this.extractor = new FootprintExtractor();
    }

    /**
     * Creates all conformance MCP tool specifications.
     *
     * @return list of two tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        return List.of(
            buildExtractFootprintTool(),
            buildCompareConformanceTool()
        );
    }

    // =========================================================================
    // Tool: yawl_extract_footprint
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildExtractFootprintTool() {
        java.util.LinkedHashMap<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("powlModelJson", Map.of("type", "string",
            "description", "POWL model JSON string, e.g. from powl_generator.py or yawl_synthesize_graalpy"));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(YawlConstants.TOOL_EXTRACT_FOOTPRINT)
            .description("Extract a behavioral footprint matrix from a POWL workflow model JSON. "
                + "The footprint captures three types of control-flow relationships between activities: "
                + "directSuccession (sequential order), concurrency (parallel execution), "
                + "and exclusivity (XOR choice). No LLM required — pure structural analysis. "
                + "Input: POWL JSON as produced by powl_generator.py or yawl_synthesize_graalpy.")
            .inputSchema(new McpSchema.JsonSchema("object", props, List.of("powlModelJson"),
                false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();
            String powlJson = getString(request.arguments(), "powlModelJson", null);
            if (powlJson == null || powlJson.isBlank()) {
                return McpResponseBuilder.error("'powlModelJson' parameter is required.");
            }

            PowlModel model;
            try {
                model = PowlJsonMarshaller.fromJson(powlJson, "extract-footprint-model");
            } catch (PowlParseException e) {
                return McpResponseBuilder.error("Failed to parse POWL JSON: " + e.getMessage());
            }

            FootprintMatrix matrix = extractor.extract(model);
            long elapsed = System.currentTimeMillis() - start;

            String response = buildFootprintResponse(matrix, elapsed);
            return McpResponseBuilder.successWithTiming(response, "extractFootprint", elapsed);
        });
    }

    // =========================================================================
    // Tool: yawl_compare_conformance
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildCompareConformanceTool() {
        java.util.LinkedHashMap<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("referenceModelJson", Map.of("type", "string",
            "description", "Reference POWL model JSON (the 'ground truth' or baseline)"));
        props.put("candidateModelJson", Map.of("type", "string",
            "description", "Candidate POWL model JSON to compare against the reference"));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(YawlConstants.TOOL_COMPARE_CONFORMANCE)
            .description("Compare two POWL workflow models and compute a behavioral conformance score. "
                + "Uses macro-averaged Jaccard similarity across three footprint dimensions: "
                + "directSuccession, concurrency, and exclusivity. Score in [0.0, 1.0]. "
                + "≥ 0.8 = HIGH conformance, 0.5–0.8 = MEDIUM, < 0.5 = LOW. "
                + "No LLM required — pure mathematical comparison.")
            .inputSchema(new McpSchema.JsonSchema("object", props,
                List.of("referenceModelJson", "candidateModelJson"), false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();
            Objects.requireNonNull(request, "Request cannot be null");
            String refJson = getString(request.arguments(), "referenceModelJson", null);
            String candJson = getString(request.arguments(), "candidateModelJson", null);

            if (refJson == null || refJson.isBlank()) {
                return McpResponseBuilder.error("'referenceModelJson' parameter is required.");
            }
            if (candJson == null || candJson.isBlank()) {
                return McpResponseBuilder.error("'candidateModelJson' parameter is required.");
            }

            PowlModel refModel;
            try {
                refModel = PowlJsonMarshaller.fromJson(refJson, "reference-model");
            } catch (PowlParseException e) {
                return McpResponseBuilder.error("Failed to parse referenceModelJson: " + e.getMessage());
            }

            PowlModel candModel;
            try {
                candModel = PowlJsonMarshaller.fromJson(candJson, "candidate-model");
            } catch (PowlParseException e) {
                return McpResponseBuilder.error("Failed to parse candidateModelJson: " + e.getMessage());
            }

            FootprintMatrix refMatrix = extractor.extract(refModel);
            FootprintScorer scorer = new FootprintScorer(refMatrix);
            double score = scorer.score(candModel, "");

            FootprintMatrix candMatrix = extractor.extract(candModel);
            double dsSim = SimilarityMetrics.jaccardSimilarity(candMatrix.directSuccession(), refMatrix.directSuccession());
            double concSim = SimilarityMetrics.jaccardSimilarity(candMatrix.concurrency(), refMatrix.concurrency());
            double exclSim = SimilarityMetrics.jaccardSimilarity(candMatrix.exclusive(), refMatrix.exclusive());

            long elapsed = System.currentTimeMillis() - start;

            // Build conformance response
            String interpretation = SimilarityMetrics.interpretScore(score);
            StringBuilder sb = new StringBuilder();
            sb.append("Conformance Check Result\n");
            sb.append("========================\n");
            sb.append(String.format("Overall Score:        %.4f  [%s]%n", score, interpretation));
            sb.append(String.format("Direct Succession:    %.4f%n", dsSim));
            sb.append(String.format("Concurrency:          %.4f%n", concSim));
            sb.append(String.format("Exclusivity:          %.4f%n", exclSim));
            sb.append("\nInterpretation:\n");
            sb.append("  ").append(SimilarityMetrics.getDetailedInterpretation(score)).append("\n");
            sb.append("\nElapsed: ").append(elapsed).append("ms");

            String response = sb.toString();
            return McpResponseBuilder.successWithTiming(response, "compareConformance", elapsed);
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildFootprintResponse(FootprintMatrix matrix, long elapsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("POWL Behavioral Footprint\n");
        sb.append("=========================\n");
        sb.append("Activity count (approx): ").append(countActivities(matrix)).append("\n\n");

        sb.append("Direct Succession (").append(matrix.directSuccession().size()).append(" pairs):\n");
        if (matrix.directSuccession().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            matrix.directSuccession().stream()
                .limit(20)
                .sorted((a, b) -> a.get(0).compareTo(b.get(0)))
                .forEach(pair -> sb.append("  ").append(pair.get(0))
                    .append(" → ").append(pair.get(1)).append("\n"));
            if (matrix.directSuccession().size() > 20) {
                sb.append("  ... (").append(matrix.directSuccession().size() - 20)
                  .append(" more)\n");
            }
        }

        sb.append("\nConcurrency (").append(matrix.concurrency().size()).append(" pairs):\n");
        if (matrix.concurrency().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            matrix.concurrency().stream()
                .limit(20)
                .sorted((a, b) -> a.get(0).compareTo(b.get(0)))
                .forEach(pair -> sb.append("  ").append(pair.get(0))
                    .append(" ‖ ").append(pair.get(1)).append("\n"));
            if (matrix.concurrency().size() > 20) {
                sb.append("  ... (").append(matrix.concurrency().size() - 20).append(" more)\n");
            }
        }

        sb.append("\nExclusivity (").append(matrix.exclusive().size()).append(" pairs):\n");
        if (matrix.exclusive().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            matrix.exclusive().stream()
                .limit(20)
                .sorted((a, b) -> a.get(0).compareTo(b.get(0)))
                .forEach(pair -> sb.append("  ").append(pair.get(0))
                    .append(" ⊕ ").append(pair.get(1)).append("\n"));
            if (matrix.exclusive().size() > 20) {
                sb.append("  ... (").append(matrix.exclusive().size() - 20).append(" more)\n");
            }
        }

        sb.append("\nElapsed: ").append(elapsed).append("ms");
        return sb.toString();
    }

  
    private String interpretScore(double score) {
        return SimilarityMetrics.interpretScore(score);
    }

    private String interpretDetail(double score) {
        return SimilarityMetrics.getDetailedInterpretation(score);
    }

    private int countActivities(FootprintMatrix matrix) {
        var activities = new java.util.HashSet<String>();
        matrix.directSuccession().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        matrix.concurrency().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        matrix.exclusive().forEach(p -> { activities.add(p.get(0)); activities.add(p.get(1)); });
        return activities.size();
    }

    
    private String getString(Map<String, Object> args, String key, String defaultValue) {
        if (args == null) {
            throw new IllegalArgumentException("Arguments map cannot be null");
        }
        Object val = args.get(key);
        return val instanceof String s ? s : defaultValue;
    }
}
