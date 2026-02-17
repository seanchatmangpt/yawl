package org.yawlfoundation.yawl.integration.mcp.spring.resources;

import org.yawlfoundation.yawl.integration.mcp.stub.McpSchema;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpResource;
import org.yawlfoundation.yawl.integration.mcp.spring.YawlMcpSessionManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring-managed MCP resource for loaded YAWL specifications.
 *
 * <p>This is a working example of a Spring-managed MCP resource that demonstrates:</p>
 * <ul>
 *   <li>Dependency injection of YAWL clients</li>
 *   <li>Integration with {@link YawlMcpSessionManager}</li>
 *   <li>Real YAWL engine operations to retrieve specification data</li>
 *   <li>JSON serialization of YAWL data structures</li>
 *   <li>Static resource implementation (fixed URI)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>This resource is automatically registered when Spring component scanning
 * is enabled for this package. It can also be manually registered as a Spring bean:</p>
 * <pre>{@code
 * @Configuration
 * public class CustomResourceConfig {
 *     @Bean
 *     public SpecificationsResource specificationsResource(
 *             InterfaceB_EnvironmentBasedClient interfaceBClient,
 *             YawlMcpSessionManager sessionManager) {
 *         return new SpecificationsResource(interfaceBClient, sessionManager);
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpecificationsResource implements YawlMcpResource {

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    /**
     * Construct resource with injected dependencies.
     * Spring automatically provides these dependencies when the resource is registered as a bean.
     *
     * @param interfaceBClient YAWL InterfaceB client for querying specifications
     * @param sessionManager session manager for obtaining session handles
     */
    public SpecificationsResource(InterfaceB_EnvironmentBasedClient interfaceBClient,
                                  YawlMcpSessionManager sessionManager) {
        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }

        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;
    }

    @Override
    public String getUri() {
        return "yawl://specifications/spring";
    }

    @Override
    public String getName() {
        return "Loaded Specifications (Spring)";
    }

    @Override
    public String getDescription() {
        return "All workflow specifications currently loaded in the YAWL engine. " +
               "This is a Spring-managed example resource demonstrating dependency injection " +
               "and real YAWL engine integration.";
    }

    @Override
    public String getMimeType() {
        return "application/json";
    }

    @Override
    public boolean isTemplate() {
        return false;  // Static resource with fixed URI
    }

    @Override
    public McpSchema.ReadResourceResult read(String uri) {
        try {
            // Retrieve specifications using injected InterfaceB client and session manager
            String sessionHandle = sessionManager.getSessionHandle();
            List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);

            // Serialize to JSON
            String json = serializeSpecifications(specs);

            // Return as MCP resource content
            return new McpSchema.ReadResourceResult(List.of(
                new McpSchema.TextResourceContents(
                    uri,
                    "application/json",
                    json
                )
            ));

        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to read specifications from YAWL engine: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new RuntimeException(
                "YAWL connection error: " + e.getMessage(), e);
        }
    }

    @Override
    public int getPriority() {
        // Lower priority than core resources (registered after)
        return 100;
    }

    @Override
    public boolean isEnabled() {
        // Always enabled - can be made conditional based on configuration
        return true;
    }

    // =========================================================================
    // JSON Serialization
    // =========================================================================

    /**
     * Serialize specification list to JSON.
     *
     * @param specs list of specification data
     * @return JSON string representation
     */
    private String serializeSpecifications(List<SpecificationData> specs) {
        List<Map<String, Object>> specList = new ArrayList<>();

        if (specs != null) {
            for (SpecificationData spec : specs) {
                YSpecificationID specId = spec.getID();
                Map<String, Object> specMap = new LinkedHashMap<>();

                specMap.put("identifier", specId.getIdentifier());
                specMap.put("version", specId.getVersionAsString());
                specMap.put("uri", specId.getUri());
                specMap.put("name", spec.getName());
                specMap.put("status", spec.getStatus());

                if (spec.getDocumentation() != null && !spec.getDocumentation().isEmpty()) {
                    specMap.put("documentation", spec.getDocumentation());
                }
                if (spec.getRootNetID() != null && !spec.getRootNetID().isEmpty()) {
                    specMap.put("rootNetId", spec.getRootNetID());
                }

                specList.add(specMap);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "spring-managed-resource");
        result.put("specifications", specList);
        result.put("count", specList.size());
        result.put("note", "This resource demonstrates Spring dependency injection " +
                          "and real YAWL engine integration");

        return toJson(result);
    }

    /**
     * Convert a map to JSON string.
     * Simple manual serialization to avoid external dependencies.
     *
     * @param map data to serialize
     * @return JSON string
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;

            sb.append('"').append(escapeJson(entry.getKey())).append("\":");
            appendJsonValue(sb, entry.getValue());
        }

        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void appendJsonValue(StringBuilder sb, Object value) {
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
                if (!first) {
                    sb.append(',');
                }
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

    private String escapeJson(String text) {
        if (text == null) {
            throw new IllegalArgumentException(
                "Cannot JSON-escape a null string. Callers must handle null values " +
                "before invoking escapeJson (use appendJsonValue for null-safe serialization).");
        }

        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(switch (c) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\b' -> "\\b";
                case '\f' -> "\\f";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> c < 0x20 ? "\\u%04x".formatted((int) c) : String.valueOf(c);
            });
        }
        return sb.toString();
    }
}
