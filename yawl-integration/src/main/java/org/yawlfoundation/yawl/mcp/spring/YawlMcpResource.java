package org.yawlfoundation.yawl.integration.mcp.spring;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Interface for Spring-managed YAWL MCP resources.
 *
 * <p>Implement this interface to create custom MCP resources that expose
 * YAWL data through the Model Context Protocol. Resources provide read-only
 * access to workflow specifications, cases, work items, and other engine state.</p>
 *
 * <h2>Static Resources vs Resource Templates</h2>
 * <p>MCP supports two types of resources:</p>
 * <ul>
 *   <li><b>Static Resources:</b> Fixed URIs (e.g., {@code yawl://specifications})</li>
 *   <li><b>Resource Templates:</b> Parameterized URIs (e.g., {@code yawl://cases/{caseId}})</li>
 * </ul>
 *
 * <p>Use {@link #isTemplate()} to indicate whether this is a resource template.
 * For templates, {@link #read(String)} receives the full URI with parameters substituted.</p>
 *
 * <h2>Usage Example - Static Resource</h2>
 * <pre>{@code
 * @Component
 * public class SpecificationsResource implements YawlMcpResource {
 *
 *     private final InterfaceB_EnvironmentBasedClient interfaceBClient;
 *     private final YawlMcpSessionManager sessionManager;
 *
 *     @Autowired
 *     public SpecificationsResource(InterfaceB_EnvironmentBasedClient interfaceBClient,
 *                                   YawlMcpSessionManager sessionManager) {
 *         this.interfaceBClient = interfaceBClient;
 *         this.sessionManager = sessionManager;
 *     }
 *
 *     @Override
 *     public String getUri() {
 *         return "yawl://specifications";
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "Loaded Specifications";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "All workflow specifications currently loaded in the YAWL engine";
 *     }
 *
 *     @Override
 *     public McpSchema.ReadResourceResult read(String uri) {
 *         try {
 *             List<SpecificationData> specs = interfaceBClient.getSpecificationList(
 *                 sessionManager.getSessionHandle());
 *
 *             String json = serializeSpecifications(specs);
 *             return new McpSchema.ReadResourceResult(List.of(
 *                 new McpSchema.TextResourceContents(uri, "application/json", json)
 *             ));
 *         } catch (IOException e) {
 *             throw new RuntimeException("Failed to read specifications: " + e.getMessage(), e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Usage Example - Resource Template</h2>
 * <pre>{@code
 * @Component
 * public class CaseDetailsResource implements YawlMcpResource {
 *
 *     private final InterfaceB_EnvironmentBasedClient interfaceBClient;
 *     private final YawlMcpSessionManager sessionManager;
 *
 *     @Autowired
 *     public CaseDetailsResource(InterfaceB_EnvironmentBasedClient interfaceBClient,
 *                                YawlMcpSessionManager sessionManager) {
 *         this.interfaceBClient = interfaceBClient;
 *         this.sessionManager = sessionManager;
 *     }
 *
 *     @Override
 *     public String getUri() {
 *         return "yawl://cases/{caseId}";
 *     }
 *
 *     @Override
 *     public boolean isTemplate() {
 *         return true;
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "Case Details";
 *     }
 *
 *     @Override
 *     public String getDescription() {
 *         return "Case state and work items for a specific running case";
 *     }
 *
 *     @Override
 *     public McpSchema.ReadResourceResult read(String uri) {
 *         // Extract caseId from URI: yawl://cases/42 -> "42"
 *         String caseId = uri.substring("yawl://cases/".length());
 *
 *         try {
 *             String caseState = interfaceBClient.getCaseState(caseId,
 *                 sessionManager.getSessionHandle());
 *             String json = serializeCaseState(caseId, caseState);
 *
 *             return new McpSchema.ReadResourceResult(List.of(
 *                 new McpSchema.TextResourceContents(uri, "application/json", json)
 *             ));
 *         } catch (IOException e) {
 *             throw new RuntimeException("Failed to read case details: " + e.getMessage(), e);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YawlMcpConfiguration
 * @see YawlMcpResourceRegistry
 */
public interface YawlMcpResource {

    /**
     * Get the URI of this resource.
     *
     * <p>For static resources, this is a fixed URI (e.g., {@code yawl://specifications}).
     * For resource templates, this includes parameter placeholders
     * (e.g., {@code yawl://cases/{caseId}}).</p>
     *
     * @return resource URI
     */
    String getUri();

    /**
     * Get the human-readable name of this resource.
     *
     * @return resource name
     */
    String getName();

    /**
     * Get the description of this resource.
     * Should explain what data the resource provides.
     *
     * @return resource description
     */
    String getDescription();

    /**
     * Get the MIME type of the resource content.
     * Default is {@code application/json}.
     *
     * @return MIME type (default: "application/json")
     */
    default String getMimeType() {
        return "application/json";
    }

    /**
     * Check if this is a resource template with URI parameters.
     *
     * @return true for resource templates, false for static resources (default: false)
     */
    default boolean isTemplate() {
        return false;
    }

    /**
     * Read the resource content for the given URI.
     *
     * <p>For static resources, the URI will match {@link #getUri()}.
     * For resource templates, the URI will have parameters substituted
     * (e.g., {@code yawl://cases/42} when template is {@code yawl://cases/{caseId}}).</p>
     *
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Extract any URI parameters (for templates)</li>
     *   <li>Use injected YAWL clients for real engine operations</li>
     *   <li>Return properly formatted content (typically JSON)</li>
     *   <li>Throw exceptions for errors (will be caught and converted to MCP errors)</li>
     * </ul>
     *
     * @param uri the full resource URI (with parameters substituted for templates)
     * @return resource read result with content
     */
    McpSchema.ReadResourceResult read(String uri);

    /**
     * Get the priority of this resource for registration order.
     * Lower values are registered first. Default is 0.
     *
     * @return priority value (default: 0)
     */
    default int getPriority() {
        return 0;
    }

    /**
     * Check if this resource is currently enabled.
     * Can be used for conditional resource availability based on configuration or state.
     *
     * @return true if resource should be registered (default: true)
     */
    default boolean isEnabled() {
        return true;
    }
}
