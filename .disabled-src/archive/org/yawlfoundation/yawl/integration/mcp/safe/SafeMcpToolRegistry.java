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

package org.yawlfoundation.yawl.integration.mcp.safe;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * SAFe governance MCP tool registry.
 *
 * <p>Provides 6 MCP tools for SAFe artifact management and Responsible AI governance:
 * <ul>
 *   <li>safe_validate_nfrs — Validate file against NFR catalog</li>
 *   <li>safe_approve_model_promotion — Promote model with ResponsibleAiReceipt proof</li>
 *   <li>safe_check_guardrails — Verify budget and utilization thresholds</li>
 *   <li>safe_create_receipt — Create governance receipt with hash</li>
 *   <li>safe_register_model — Register new model version</li>
 *   <li>safe_promote_model — Alias to safe_approve_model_promotion</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class SafeMcpToolRegistry {
    private final NfrCatalog nfrCatalog;
    private final ModelRegistry modelRegistry;

    /**
     * Construct tool registry with dependencies.
     *
     * @param nfrCatalog non-null NFR catalog
     * @param modelRegistry non-null model registry
     */
    public SafeMcpToolRegistry(NfrCatalog nfrCatalog, ModelRegistry modelRegistry) {
        if (nfrCatalog == null) throw new IllegalArgumentException("nfrCatalog must not be null");
        if (modelRegistry == null) throw new IllegalArgumentException("modelRegistry must not be null");
        this.nfrCatalog = nfrCatalog;
        this.modelRegistry = modelRegistry;
    }

    /**
     * Create all SAFe MCP tool specifications.
     *
     * @return list of 6 tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        return List.of(
            createValidateNfrsTool(),
            createApproveModelPromotionTool(),
            createCheckGuardrailsTool(),
            createCreateReceiptTool(),
            createRegisterModelTool(),
            createPromoteModelTool()
        );
    }

    // =========================================================================
    // Tool: safe_validate_nfrs
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createValidateNfrsTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("path", Map.of("type", "string", "description", "Path to Java source file to validate"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("path"), false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_validate_nfrs")
                .description("Validate source file against Responsible AI NFR policies (privacy, fairness, security, reliability, transparency, accountability)")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String path = (String) params.get("path");
                    if (path == null || path.isBlank()) return errorResult("path parameter required");

                    List<NfrViolation> violations = nfrCatalog.validate(Path.of(path));
                    List<Map<String, Object>> violationList = new ArrayList<>();
                    for (NfrViolation v : violations) {
                        Map<String, Object> vmap = new LinkedHashMap<>();
                        vmap.put("attribute", v.attribute());
                        vmap.put("line", v.line());
                        vmap.put("description", v.description());
                        violationList.add(vmap);
                    }

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("path", path);
                    result.put("violationCount", violations.size());
                    result.put("violations", violationList);
                    result.put("status", violations.isEmpty() ? "PASS" : "FAIL");
                    return textResult(toJson(result));
                } catch (Exception e) {
                    return errorResult("Failed to validate NFRs: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Tool: safe_approve_model_promotion
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createApproveModelPromotionTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("modelId", Map.of("type", "string", "description", "Unique model identifier"));
        props.put("version", Map.of("type", "string", "description", "Semantic version"));
        props.put("responsibleAiReceiptJson", Map.of("type", "string",
            "description", "Non-blank JSON proof of responsible AI review"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("modelId", "version", "responsibleAiReceiptJson"),
            false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_approve_model_promotion")
                .description("Approve and promote AI model version with ResponsibleAiReceipt proof")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String modelId = (String) params.get("modelId");
                    String version = (String) params.get("version");
                    String receipt = (String) params.get("responsibleAiReceiptJson");
                    if (modelId == null || modelId.isBlank()) return errorResult("modelId required");
                    if (version == null || version.isBlank()) return errorResult("version required");

                    ModelRegistryEntry promoted = modelRegistry.promote(modelId, version, receipt);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("modelId", promoted.modelId());
                    result.put("version", promoted.version());
                    result.put("status", promoted.status().name());
                    result.put("promotedAt", Instant.now().toString());
                    return textResult(toJson(result));
                } catch (IllegalStateException | NoSuchElementException e) {
                    return errorResult(e.getMessage());
                } catch (Exception e) {
                    return errorResult("Failed to promote model: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Tool: safe_check_guardrails
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createCheckGuardrailsTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("budgetTotal", Map.of("type", "number", "description", "Total budget"));
        props.put("budgetUsed", Map.of("type", "number", "description", "Amount used so far"));
        props.put("threshold", Map.of("type", "number", "description", "Threshold percentage (e.g. 80.0)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("budgetTotal", "budgetUsed", "threshold"),
            false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_check_guardrails")
                .description("Check budget and utilization guardrails")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    double budgetTotal = ((Number) params.get("budgetTotal")).doubleValue();
                    double budgetUsed = ((Number) params.get("budgetUsed")).doubleValue();
                    double threshold = ((Number) params.get("threshold")).doubleValue();

                    if (budgetTotal <= 0) return errorResult("budgetTotal must be positive");
                    double utilization = (budgetUsed / budgetTotal) * 100.0;
                    boolean passed = utilization <= threshold;

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("guardrailPassed", passed);
                    result.put("budgetRemaining", budgetTotal - budgetUsed);
                    result.put("utilizationPct", Math.round(utilization * 100.0) / 100.0);
                    result.put("thresholdPct", threshold);
                    return textResult(toJson(result));
                } catch (Exception e) {
                    return errorResult("Failed to check guardrails: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Tool: safe_create_receipt
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createCreateReceiptTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("phase", Map.of("type", "string", "description", "Governance phase"));
        props.put("caseId", Map.of("type", "string", "description", "Related case or artifact ID"));
        props.put("delta", Map.of("type", "string", "description", "Change delta description"));
        props.put("status", Map.of("type", "string", "description", "Status (APPROVED, REJECTED, PENDING)"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("phase", "caseId", "delta", "status"),
            false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_create_receipt")
                .description("Create governance receipt with SHA256 hash of inputs")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String phase = (String) params.get("phase");
                    String caseId = (String) params.get("caseId");
                    String delta = (String) params.get("delta");
                    String status = (String) params.get("status");

                    String canonical = phase + ":" + caseId + ":" + delta + ":" + status;
                    String hash = computeSha256(canonical);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("hash", hash);
                    result.put("phase", phase);
                    result.put("caseId", caseId);
                    result.put("timestamp", Instant.now().toString());
                    result.put("status", status);
                    return textResult(toJson(result));
                } catch (Exception e) {
                    return errorResult("Failed to create receipt: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Tool: safe_register_model
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createRegisterModelTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("modelId", Map.of("type", "string", "description", "Unique model identifier"));
        props.put("version", Map.of("type", "string", "description", "Semantic version"));
        props.put("datasetLineage", Map.of("type", "string", "description", "Dataset origins"));
        props.put("modelCard", Map.of("type", "string", "description", "Model behavior documentation"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("modelId", "version", "datasetLineage", "modelCard"),
            false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_register_model")
                .description("Register new AI model version as candidate in model registry")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String modelId = (String) params.get("modelId");
                    String version = (String) params.get("version");
                    String datasetLineage = (String) params.getOrDefault("datasetLineage", "");
                    String modelCard = (String) params.getOrDefault("modelCard", "");
                    if (modelId == null || modelId.isBlank()) return errorResult("modelId required");
                    if (version == null || version.isBlank()) return errorResult("version required");

                    String versionHash = computeSha256(modelId + ":" + version);
                    ModelRegistryEntry entry = new ModelRegistryEntry(
                        modelId, version, datasetLineage, modelCard,
                        "", versionHash, new ArrayList<>(),
                        ModelRegistryEntry.PromotionStatus.CANDIDATE, Instant.now()
                    );
                    modelRegistry.register(entry);

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("modelId", modelId);
                    result.put("version", version);
                    result.put("status", "REGISTERED");
                    result.put("versionHash", versionHash);
                    return textResult(toJson(result));
                } catch (IllegalArgumentException e) {
                    return errorResult("Model version already registered: " + e.getMessage());
                } catch (Exception e) {
                    return errorResult("Failed to register model: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Tool: safe_promote_model (alias)
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification createPromoteModelTool() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("modelId", Map.of("type", "string", "description", "Unique model identifier"));
        props.put("version", Map.of("type", "string", "description", "Semantic version"));
        props.put("responsibleAiReceiptJson", Map.of("type", "string",
            "description", "Non-blank JSON proof of responsible AI review"));

        McpSchema.JsonSchema schema = new McpSchema.JsonSchema(
            "object", props, List.of("modelId", "version", "responsibleAiReceiptJson"),
            false, null, Map.of());

        return new McpServerFeatures.SyncToolSpecification(
            McpSchema.Tool.builder()
                .name("safe_promote_model")
                .description("Promote AI model version to production (requires ResponsibleAiReceipt proof)")
                .inputSchema(schema)
                .build(),
            (exchange, args) -> {
                try {
                    Map<String, Object> params = args.arguments();
                    String modelId = (String) params.get("modelId");
                    String version = (String) params.get("version");
                    String receipt = (String) params.get("responsibleAiReceiptJson");
                    if (modelId == null || modelId.isBlank()) return errorResult("modelId required");
                    if (version == null || version.isBlank()) return errorResult("version required");

                    ModelRegistryEntry promoted = modelRegistry.promote(modelId, version, receipt);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("modelId", promoted.modelId());
                    result.put("version", promoted.version());
                    result.put("status", promoted.status().name());
                    result.put("promotedAt", Instant.now().toString());
                    return textResult(toJson(result));
                } catch (IllegalStateException | NoSuchElementException e) {
                    return errorResult(e.getMessage());
                } catch (Exception e) {
                    return errorResult("Failed to promote model: " + e.getMessage());
                }
            }
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static McpSchema.CallToolResult textResult(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), false, null, null);
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(message)), true, null, null);
    }

    private static String computeSha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) { sb.append("null"); }
        else if (value instanceof String s) { sb.append('"').append(escapeJson(s)).append('"'); }
        else if (value instanceof Number n) { sb.append(n); }
        else if (value instanceof Boolean b) { sb.append(b); }
        else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                appendJsonValue(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> m) {
            sb.append(toJson((Map<String, Object>) m));
        } else {
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private static String escapeJson(String text) {
        if (text == null) throw new IllegalArgumentException("Cannot JSON-escape null");
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c >= 0x20) sb.append(c); else sb.append(String.format("\\u%04x", (int) c)); }
            }
        }
        return sb.toString();
    }
}
