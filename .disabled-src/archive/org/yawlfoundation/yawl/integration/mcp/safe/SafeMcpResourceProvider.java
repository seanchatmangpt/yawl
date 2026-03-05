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

import java.util.*;

/**
 * SAFe artifact MCP resource provider.
 *
 * Exposes 6 SAFe resources as MCP resource specifications:
 * - safe://backlog/{artId} - ART backlog with features and enablers
 * - safe://pi-objectives/{artId}/{pi} - PI planning objectives
 * - safe://nfrs - NFR catalog policies
 * - safe://model-registry/{modelId} - AI model registry entry
 * - safe://policy/responsible-ai - Responsible AI governance policy
 * - safe://telemetry - Flow metrics and incident signals
 *
 * Backed by {@link ModelRegistry} for model artifact tracking.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class SafeMcpResourceProvider {
    private final ModelRegistry modelRegistry;

    /**
     * Construct provider with model registry.
     *
     * @param modelRegistry non-null model registry instance
     */
    public SafeMcpResourceProvider(ModelRegistry modelRegistry) {
        if (modelRegistry == null) {
            throw new IllegalArgumentException("modelRegistry must not be null");
        }
        this.modelRegistry = modelRegistry;
    }

    /**
     * Create all SAFe MCP resource specifications (static resources only).
     *
     * @return list of 3 static resource specifications
     */
    public List<McpServerFeatures.SyncResourceSpecification> createAllResources() {
        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();
        resources.add(createNfrsResource());
        resources.add(createResponsibleAiPolicyResource());
        resources.add(createTelemetryResource());
        return resources;
    }

    /**
     * Create all SAFe MCP resource template specifications.
     *
     * @return list of 3 resource template specifications
     */
    public List<McpServerFeatures.SyncResourceTemplateSpecification> createAllResourceTemplates() {
        List<McpServerFeatures.SyncResourceTemplateSpecification> templates = new ArrayList<>();
        templates.add(createBacklogResource());
        templates.add(createPiObjectivesTemplate());
        templates.add(createModelRegistryTemplate());
        return templates;
    }

    /**
     * Create all SAFe MCP resources and templates.
     * Combines both static and parameterized resources.
     *
     * @return list of all 6 resource and template specifications (mixed types)
     */
    public List<Object> createAll() {
        List<Object> all = new ArrayList<>();
        all.addAll(createAllResources());
        all.addAll(createAllResourceTemplates());
        return all;
    }

    /**
     * Resource: safe://backlog/{artId} - ART backlog with features and enablers.
     */
    private McpServerFeatures.SyncResourceTemplateSpecification createBacklogResource() {
        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "safe://backlog/{artId}",
            "ART Backlog",
            null,
            "Agile Release Train backlog with features and enablers",
            "application/json",
            null, null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String artId = extractSegment(request.uri(), "safe://backlog/");
                if (artId == null || artId.isEmpty()) {
                    return errorResponse(request.uri(), "ART ID required");
                }

                Map<String, Object> backlog = new LinkedHashMap<>();
                backlog.put("artId", artId);
                backlog.put("features", new ArrayList<>());
                backlog.put("enablers", new ArrayList<>());
                backlog.put("note", "ART backlog resource");

                String json = toJson(backlog);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Resource: safe://pi-objectives/{artId}/{pi} - PI planning objectives.
     */
    private McpServerFeatures.SyncResourceTemplateSpecification createPiObjectivesTemplate() {
        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "safe://pi-objectives/{artId}/{pi}",
            "PI Objectives",
            null,
            "Program Increment objectives for an ART",
            "application/json",
            null, null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String uri = request.uri();
                String remainder = extractSegment(uri, "safe://pi-objectives/");
                if (remainder == null) {
                    return errorResponse(uri, "Format required: safe://pi-objectives/{artId}/{pi}");
                }

                String[] parts = remainder.split("/");
                if (parts.length < 2) {
                    return errorResponse(uri, "ART ID and PI number required");
                }

                String artId = parts[0];
                String pi = parts[1];

                Map<String, Object> objectives = new LinkedHashMap<>();
                objectives.put("artId", artId);
                objectives.put("pi", pi);
                objectives.put("objectives", new ArrayList<>());
                objectives.put("note", "PI Objectives");

                String json = toJson(objectives);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(uri, "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Resource: safe://nfrs - NFR catalog policies.
     */
    private McpServerFeatures.SyncResourceSpecification createNfrsResource() {
        McpSchema.Resource resource = new McpSchema.Resource(
            "safe://nfrs",
            "NFR Catalog",
            "Responsible AI Non-Functional Requirement policies",
            "application/json",
            null, 0L, null, Map.of()
        );

        NfrCatalog catalog = new NfrCatalog();

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String json = catalog.getPolicy();
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Resource: safe://model-registry/{modelId} - AI model registry entry.
     */
    private McpServerFeatures.SyncResourceTemplateSpecification createModelRegistryTemplate() {
        McpSchema.ResourceTemplate template = new McpSchema.ResourceTemplate(
            "safe://model-registry/{modelId}",
            "Model Registry",
            null,
            "AI model artifact registry with promotion history",
            "application/json",
            null, null
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) -> {
            try {
                String modelId = extractSegment(request.uri(), "safe://model-registry/");
                if (modelId == null || modelId.isEmpty()) {
                    return errorResponse(request.uri(), "Model ID required");
                }

                Optional<ModelRegistryEntry> entry = modelRegistry.getEntry(modelId);
                if (entry.isEmpty()) {
                    Map<String, Object> notFound = Map.of("error", "Model not found: " + modelId);
                    String json = toJson(notFound);
                    return new McpSchema.ReadResourceResult(List.of(
                        new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
                }

                ModelRegistryEntry e = entry.get();
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("modelId", e.modelId());
                data.put("version", e.version());
                data.put("datasetLineage", e.datasetLineage());
                data.put("modelCard", e.modelCard());
                data.put("evalSuiteRef", e.evalSuiteRef());
                data.put("versionHash", e.versionHash());
                data.put("responsibleAiEvidence", e.responsibleAiEvidence());
                data.put("status", e.status().name());
                data.put("registeredAt", e.registeredAt().toString());

                String json = toJson(data);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Resource: safe://policy/responsible-ai - Responsible AI governance policy.
     */
    private McpServerFeatures.SyncResourceSpecification createResponsibleAiPolicyResource() {
        McpSchema.Resource resource = new McpSchema.Resource(
            "safe://policy/responsible-ai",
            "Responsible AI Policy",
            "SAFe Responsible AI governance with 13 attributes",
            "application/json",
            null, 0L, null, Map.of()
        );

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                String json = getResponsibleAiPolicy();
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Resource: safe://telemetry - Flow metrics and incident signals.
     */
    private McpServerFeatures.SyncResourceSpecification createTelemetryResource() {
        McpSchema.Resource resource = new McpSchema.Resource(
            "safe://telemetry",
            "Telemetry",
            "Flow metrics, drift detection, and incident signals",
            "application/json",
            null, 0L, null, Map.of()
        );

        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) -> {
            try {
                Map<String, Object> telemetry = new LinkedHashMap<>();
                telemetry.put("flowMetrics", new LinkedHashMap<>());
                telemetry.put("driftMetrics", new LinkedHashMap<>());
                telemetry.put("incidentSignals", new ArrayList<>());

                String json = toJson(telemetry);
                return new McpSchema.ReadResourceResult(List.of(
                    new McpSchema.TextResourceContents(request.uri(), "application/json", json)));
            } catch (Exception e) {
                return errorResponse(request.uri(), e.getMessage());
            }
        });
    }

    /**
     * Hardcoded Responsible AI policy with 13 SAFe attributes.
     */
    private String getResponsibleAiPolicy() {
        return """
            {
              "version": "1.0",
              "attributes": [
                {"name": "privacy", "description": "Data privacy and personal information protection"},
                {"name": "security", "description": "System security and threat mitigation"},
                {"name": "resilience", "description": "System resilience and fault tolerance"},
                {"name": "reliability", "description": "System reliability and consistent operation"},
                {"name": "accuracy", "description": "Model accuracy and prediction validity"},
                {"name": "transparency", "description": "Algorithmic transparency and explainability"},
                {"name": "interpretability", "description": "Model interpretability and auditability"},
                {"name": "accountability", "description": "Decision accountability and responsibility"},
                {"name": "safety", "description": "System safety and risk mitigation"},
                {"name": "fairness", "description": "Fairness and absence of discrimination"},
                {"name": "ethics", "description": "Ethical alignment with organizational values"},
                {"name": "inclusiveness", "description": "Inclusive design and accessibility"},
                {"name": "sustainability", "description": "Environmental and operational sustainability"}
              ]
            }
            """;
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private static String extractSegment(String uri, String prefix) {
        if (uri == null || !uri.startsWith(prefix)) {
            return null;
        }
        return uri.substring(prefix.length());
    }

    private static McpSchema.ReadResourceResult errorResponse(String uri, String message) {
        Map<String, Object> error = Map.of("error", message);
        String json = toJson(error);
        return new McpSchema.ReadResourceResult(List.of(
            new McpSchema.TextResourceContents(uri, "application/json", json)));
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            sb.append('"').append(escapeJson(str)).append('"');
        } else if (value instanceof Number num) {
            sb.append(num);
        } else if (value instanceof Boolean bool) {
            sb.append(bool);
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                appendJsonValue(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Map<?, ?> map) {
            sb.append(toJson((Map<String, Object>) map));
        } else {
            sb.append('"').append(escapeJson(value.toString())).append('"');
        }
    }

    private static String escapeJson(String text) {
        if (text == null) {
            throw new IllegalArgumentException(
                "Cannot JSON-escape null string. Caller must handle null values before escapeJson.");
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
