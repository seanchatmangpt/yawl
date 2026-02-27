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
import org.yawlfoundation.yawl.ggen.mining.generators.YawlExportException;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.polyglot.PowlPythonBridge;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlToYawlConverter;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.integration.synthesis.PatternBasedSynthesizer;
import org.yawlfoundation.yawl.integration.synthesis.SynthesisResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * MCP tool specifications for LLM-free workflow construction via GraalPy + pm4py.
 *
 * <p>Exposes the complete GraalPy pipeline as MCP tools:
 * <ol>
 *   <li>{@code yawl_synthesize_graalpy} — NL description → YAWL XML via pm4py (no LLM)</li>
 *   <li>{@code yawl_mine_workflow} — XES event log → YAWL XML via pm4py inductive miner</li>
 * </ol>
 *
 * <p>The pipeline is: {@link PowlPythonBridge} (GraalPy) →
 * {@link PowlToYawlConverter} → {@link YawlSpecExporter} → YAWL specificationSet XML.
 * No inference cost, no API key, no external service beyond GraalVM JDK 24.1+.
 *
 * <p>When GraalVM runtime is unavailable (standard JDK), {@code yawl_synthesize_graalpy}
 * falls back to {@link PatternBasedSynthesizer} (pure Java, WCP pattern matching).
 * {@code yawl_mine_workflow} returns an explicit error — process mining requires pm4py.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * var specs = new YawlGraalPyWorkflowToolSpecifications();
 * List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
 * // register in McpServer.sync(...).tools(tools)
 * specs.close(); // shuts down the GraalPy context pool
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlGraalPyWorkflowToolSpecifications implements AutoCloseable {

    private final PowlPythonBridge bridge;
    private final PowlToYawlConverter converter;
    private final YawlSpecExporter exporter;
    private final PatternBasedSynthesizer fallbackSynthesizer;

    /**
     * Constructs tool specifications with a fresh GraalPy bridge (pool of 2 contexts).
     *
     * <p>Construction succeeds on any JDK. GraalVM availability is checked lazily
     * at first tool invocation via {@link PythonException.ErrorKind#RUNTIME_NOT_AVAILABLE}.</p>
     */
    public YawlGraalPyWorkflowToolSpecifications() {
        this.bridge = new PowlPythonBridge();
        this.converter = new PowlToYawlConverter();
        this.exporter = new YawlSpecExporter();
        this.fallbackSynthesizer = new PatternBasedSynthesizer();
    }

    /**
     * Package-private constructor for testing with injected bridge.
     *
     * @param bridge the GraalPy bridge to use (non-null)
     */
    YawlGraalPyWorkflowToolSpecifications(PowlPythonBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
        this.converter = new PowlToYawlConverter();
        this.exporter = new YawlSpecExporter();
        this.fallbackSynthesizer = new PatternBasedSynthesizer();
    }

    /**
     * Creates both GraalPy workflow tool specifications.
     *
     * @return list containing {@code yawl_synthesize_graalpy} and {@code yawl_mine_workflow}
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();
        tools.add(createSynthesizeGraalPyTool());
        tools.add(createMineWorkflowTool());
        return tools;
    }

    /**
     * Closes the underlying GraalPy context pool.
     *
     * <p>After closing, tool invocations will throw {@link PythonException}.</p>
     */
    @Override
    public void close() {
        bridge.close();
    }

    // =========================================================================
    // Tool 1: yawl_synthesize_graalpy
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createSynthesizeGraalPyTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("description", Map.of(
            "type", "string",
            "description", "Natural-language description of the business process "
                + "(e.g., 'loan approval: submit application, credit check, decide, notify')"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("description"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_synthesize_graalpy")
                .description("Generate a valid YAWL workflow specification from a natural-language "
                    + "description using GraalPy + pm4py — no LLM, no external service. "
                    + "Runs Python pm4py inside the JVM via GraalVM. "
                    + "Falls back to WCP pattern matching (PatternBasedSynthesizer) when "
                    + "GraalVM runtime is unavailable. "
                    + "Returns YAWL specificationSet XML ready for upload via yawl_upload_specification.")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                Objects.requireNonNull(args, "Request arguments cannot be null");
                Objects.requireNonNull(args.arguments(), "Arguments map cannot be null");
                String description = (String) args.arguments().get("description");
                if (description == null || description.isBlank()) {
                    return errorResult("description is required and must not be blank");
                }
                return synthesizeFromDescription(description.trim());
            });
    }

    private McpSchema.CallToolResult synthesizeFromDescription(String description) {
        long start = System.currentTimeMillis();
        try {
            PowlModel model = bridge.generate(description);
            Objects.requireNonNull(model, "Generated PowlModel cannot be null - check description and bridge configuration");

            PetriNet petriNet = converter.convert(model);
            Objects.requireNonNull(petriNet, "Converted PetriNet cannot be null - check PowlModel completeness");

            String yawlXml = exporter.export(petriNet);
            Objects.requireNonNull(yawlXml, "Exported YAWL XML cannot be null - check PetriNet model");
            long elapsed = System.currentTimeMillis() - start;

            String response = "path: graalpy+pm4py\n"
                + "elapsed_ms: " + elapsed + "\n"
                + "source: " + description + "\n\n"
                + yawlXml;
            return successResult(response);

        } catch (PythonException pe) {
            if (isGraalVmUnavailable(pe)) {
                // GraalVM not present — fall back to pattern-based synthesis
                return synthesizeFallback(description, System.currentTimeMillis() - start);
            }
            return errorResult("GraalPy synthesis failed: " + pe.getMessage());
        } catch (PowlParseException | YawlExportException | IllegalArgumentException e) {
            return errorResult("GraalPy synthesis failed: " + e.getMessage());
        } catch (Exception e) {
            return errorResult("Unexpected error during GraalPy synthesis: " + e.getMessage());
        }
    }

    private McpSchema.CallToolResult synthesizeFallback(String description, long graalElapsed) {
        long start = System.currentTimeMillis();
        try {
            PatternBasedSynthesizer.PatternSpec spec =
                fallbackSynthesizer.parseDescription(description, List.of());
            SynthesisResult result = fallbackSynthesizer.synthesize(spec);
            long elapsed = System.currentTimeMillis() - start;

            String response = "path: fallback+PatternBasedSynthesizer (GraalVM unavailable)\n"
                + "pattern: " + spec.wcpId() + "\n"
                + "elapsed_ms: " + (graalElapsed + elapsed) + "\n"
                + "source: " + description + "\n\n"
                + result.specXml();
            return successResult(response);

        } catch (IllegalArgumentException e) {
            return errorResult("GraalVM unavailable and fallback synthesis failed: " + e.getMessage());
        }
    }

    // =========================================================================
    // Tool 2: yawl_mine_workflow
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createMineWorkflowTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("xesContent", Map.of(
            "type", "string",
            "description", "XES event log XML content to mine a workflow model from. "
                + "Uses pm4py inductive miner to discover the process structure."));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("xesContent"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("yawl_mine_workflow")
                .description("Discover a YAWL workflow specification from an XES event log "
                    + "using GraalPy + pm4py inductive miner — no LLM required. "
                    + "Mines the process model from observed execution traces, converts "
                    + "the discovered POWL model to a YAWL specificationSet XML. "
                    + "Requires GraalVM JDK 24.1+ at runtime (no fallback for mining).")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                String xesContent = (String) args.arguments().get("xesContent");
                if (xesContent == null || xesContent.isBlank()) {
                    return errorResult("xesContent is required and must not be blank");
                }
                return mineFromXes(xesContent.trim());
            });
    }

    private McpSchema.CallToolResult mineFromXes(String xesContent) {
        long start = System.currentTimeMillis();
        try {
            PowlModel model = bridge.mineFromLog(xesContent);
            PetriNet petriNet = converter.convert(model);
            String yawlXml = exporter.export(petriNet);
            long elapsed = System.currentTimeMillis() - start;

            String response = "path: graalpy+pm4py+inductive-miner\n"
                + "elapsed_ms: " + elapsed + "\n\n"
                + yawlXml;
            return successResult(response);

        } catch (PythonException pe) {
            if (isGraalVmUnavailable(pe)) {
                return errorResult(
                    "GraalVM runtime not available. yawl_mine_workflow requires GraalVM JDK 24.1+. "
                    + "No fallback is possible for process mining — install GraalVM or use "
                    + "yawl_synthesize_graalpy (which has a pure-Java fallback).");
            }
            return errorResult("Process mining failed: " + pe.getMessage());
        } catch (PowlParseException | YawlExportException | IllegalArgumentException e) {
            return errorResult("Process mining failed: " + e.getMessage());
        } catch (Exception e) {
            return errorResult("Unexpected error during process mining: " + e.getMessage());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns true when the PythonException indicates GraalVM is not available at runtime.
     * On standard JDK the pool throws CONTEXT_ERROR (context init fails), not RUNTIME_NOT_AVAILABLE.
     */
    private static boolean isGraalVmUnavailable(PythonException pe) {
        return pe.getErrorKind() == PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE
            || pe.getErrorKind() == PythonException.ErrorKind.CONTEXT_ERROR;
    }

    private static McpSchema.CallToolResult successResult(String text) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(
            List.of(new McpSchema.TextContent(message)), true, null, null);
    }
}
