package org.yawlfoundation.yawl.integration.mcp.spring;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Spring-managed registry for YAWL MCP resources.
 *
 * <p>This registry bridges YAWL's existing MCP resource implementations
 * ({@link YawlResourceProvider}) with Spring's dependency injection
 * and lifecycle management. It also discovers and registers custom
 * Spring-managed resources that implement {@link YawlMcpResource}.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Automatic registration of core YAWL MCP resources and templates</li>
 *   <li>Discovery of custom {@link YawlMcpResource} Spring beans</li>
 *   <li>Separate handling of static resources and resource templates</li>
 *   <li>Priority-based resource ordering</li>
 *   <li>Conditional resource registration based on {@link YawlMcpResource#isEnabled()}</li>
 *   <li>Thread-safe resource management</li>
 * </ul>
 *
 * <p>This bean is automatically configured by {@link YawlMcpConfiguration}.</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YawlMcpResource
 * @see YawlMcpConfiguration
 */
public class YawlMcpResourceRegistry {

    private static final Logger LOGGER = Logger.getLogger(YawlMcpResourceRegistry.class.getName());

    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final YawlMcpSessionManager sessionManager;

    private final Map<String, YawlMcpResource> customStaticResources = new ConcurrentHashMap<>();
    private final Map<String, YawlMcpResource> customTemplateResources = new ConcurrentHashMap<>();

    private volatile List<McpServerFeatures.SyncResourceSpecification> allResourceSpecs;
    private volatile List<McpServerFeatures.SyncResourceTemplateSpecification> allTemplateSpecs;

    /**
     * Construct resource registry with required dependencies.
     *
     * @param interfaceBClient YAWL InterfaceB client
     * @param sessionManager session manager
     */
    public YawlMcpResourceRegistry(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            YawlMcpSessionManager sessionManager) {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionManager == null) {
            throw new IllegalArgumentException("sessionManager is required");
        }

        this.interfaceBClient = interfaceBClient;
        this.sessionManager = sessionManager;

        initializeCoreResources();
    }

    /**
     * Register a custom Spring-managed resource.
     * Called automatically by Spring when {@link YawlMcpResource} beans are discovered.
     *
     * @param resource custom resource to register
     */
    public void registerResource(YawlMcpResource resource) {
        if (resource == null) {
            throw new IllegalArgumentException("resource cannot be null");
        }

        String resourceUri = resource.getUri();
        if (resourceUri == null || resourceUri.isEmpty()) {
            throw new IllegalArgumentException("Resource URI cannot be null or empty");
        }

        if (!resource.isEnabled()) {
            LOGGER.info("Skipping disabled resource: " + resourceUri);
            return;
        }

        if (resource.isTemplate()) {
            LOGGER.info("Registering custom MCP resource template: " + resourceUri);
            customTemplateResources.put(resourceUri, resource);
        } else {
            LOGGER.info("Registering custom MCP resource: " + resourceUri);
            customStaticResources.put(resourceUri, resource);
        }

        // Invalidate cached specifications
        allResourceSpecs = null;
        allTemplateSpecs = null;
    }

    /**
     * Unregister a custom resource.
     *
     * @param resourceUri URI of resource to unregister
     * @return true if resource was registered and removed
     */
    public boolean unregisterResource(String resourceUri) {
        if (resourceUri == null || resourceUri.isEmpty()) {
            return false;
        }

        LOGGER.info("Unregistering custom MCP resource: " + resourceUri);

        boolean removed = customStaticResources.remove(resourceUri) != null ||
                         customTemplateResources.remove(resourceUri) != null;

        if (removed) {
            // Invalidate cached specifications
            allResourceSpecs = null;
            allTemplateSpecs = null;
        }

        return removed;
    }

    /**
     * Get all static resource specifications for MCP server registration.
     * Combines core YAWL resources with custom Spring-managed resources.
     *
     * @return list of all enabled static resource specifications
     */
    public List<McpServerFeatures.SyncResourceSpecification> getAllResourceSpecifications() {
        if (allResourceSpecs != null) {
            return allResourceSpecs;
        }

        List<McpServerFeatures.SyncResourceSpecification> specs = new ArrayList<>();

        // Add core YAWL static resources from existing implementation
        specs.addAll(YawlResourceProvider.createAllResources(
            interfaceBClient,
            sessionManager.getSessionHandle()
        ));

        // Add custom Spring-managed static resources
        List<YawlMcpResource> sortedResources = customStaticResources.values().stream()
            .filter(YawlMcpResource::isEnabled)
            .sorted(Comparator.comparingInt(YawlMcpResource::getPriority))
            .toList();

        for (YawlMcpResource resource : sortedResources) {
            specs.add(createResourceSpecification(resource));
        }

        allResourceSpecs = specs;
        LOGGER.info("Registered " + specs.size() + " total MCP static resources " +
                   "(" + customStaticResources.size() + " custom)");

        return specs;
    }

    /**
     * Get all resource template specifications for MCP server registration.
     * Combines core YAWL resource templates with custom Spring-managed templates.
     *
     * @return list of all enabled resource template specifications
     */
    public List<McpServerFeatures.SyncResourceTemplateSpecification> getAllResourceTemplateSpecifications() {
        if (allTemplateSpecs != null) {
            return allTemplateSpecs;
        }

        List<McpServerFeatures.SyncResourceTemplateSpecification> specs = new ArrayList<>();

        // Add core YAWL resource templates from existing implementation
        specs.addAll(YawlResourceProvider.createAllResourceTemplates(
            interfaceBClient,
            sessionManager.getSessionHandle()
        ));

        // Add custom Spring-managed resource templates
        List<YawlMcpResource> sortedTemplates = customTemplateResources.values().stream()
            .filter(YawlMcpResource::isEnabled)
            .sorted(Comparator.comparingInt(YawlMcpResource::getPriority))
            .toList();

        for (YawlMcpResource template : sortedTemplates) {
            specs.add(createResourceTemplateSpecification(template));
        }

        allTemplateSpecs = specs;
        LOGGER.info("Registered " + specs.size() + " total MCP resource templates " +
                   "(" + customTemplateResources.size() + " custom)");

        return specs;
    }

    /**
     * Get the number of registered static resources (core + custom).
     *
     * @return static resource count
     */
    public int getResourceCount() {
        return getAllResourceSpecifications().size();
    }

    /**
     * Get the number of registered resource templates (core + custom).
     *
     * @return resource template count
     */
    public int getTemplateCount() {
        return getAllResourceTemplateSpecifications().size();
    }

    /**
     * Get the number of custom Spring-managed static resources.
     *
     * @return custom static resource count
     */
    public int getCustomResourceCount() {
        return customStaticResources.size();
    }

    /**
     * Get the number of custom Spring-managed resource templates.
     *
     * @return custom resource template count
     */
    public int getCustomTemplateCount() {
        return customTemplateResources.size();
    }

    /**
     * Check if a resource with the given URI is registered.
     *
     * @param resourceUri resource URI to check
     * @return true if resource is registered
     */
    public boolean hasCustomResource(String resourceUri) {
        return customStaticResources.containsKey(resourceUri) ||
               customTemplateResources.containsKey(resourceUri);
    }

    /**
     * Initialize core YAWL resources from existing implementation.
     * Called automatically during construction.
     */
    private void initializeCoreResources() {
        LOGGER.info("Initializing core YAWL MCP resources: 3 static, 3 templates");
    }

    /**
     * Create an MCP static resource specification from a Spring-managed resource.
     *
     * @param resource Spring-managed resource implementation
     * @return MCP sync resource specification
     */
    private McpServerFeatures.SyncResourceSpecification createResourceSpecification(
            YawlMcpResource resource) {

        McpSchema.Resource mcpResource = new McpSchema.Resource(
            resource.getUri(),
            resource.getName(),
            resource.getDescription(),
            resource.getMimeType(),
            null
        );

        return new McpServerFeatures.SyncResourceSpecification(
            mcpResource,
            (exchange, request) -> {
                try {
                    return resource.read(request.uri());
                } catch (Exception e) {
                    LOGGER.severe("Error reading resource " + resource.getUri() + ": " + e.getMessage());
                    throw new RuntimeException(
                        "Resource read error: " + e.getMessage(), e);
                }
            }
        );
    }

    /**
     * Create an MCP resource template specification from a Spring-managed resource.
     *
     * @param resource Spring-managed resource template implementation
     * @return MCP sync resource template specification
     */
    private McpServerFeatures.SyncResourceTemplateSpecification createResourceTemplateSpecification(
            YawlMcpResource resource) {

        McpSchema.ResourceTemplate mcpTemplate = new McpSchema.ResourceTemplate(
            resource.getUri(),
            resource.getName(),
            null,  // annotations
            resource.getDescription(),
            resource.getMimeType(),
            null   // metadata
        );

        return new McpServerFeatures.SyncResourceTemplateSpecification(
            mcpTemplate,
            (exchange, request) -> {
                try {
                    return resource.read(request.uri());
                } catch (Exception e) {
                    LOGGER.severe("Error reading resource template " + resource.getUri() +
                                 ": " + e.getMessage());
                    throw new RuntimeException(
                        "Resource template read error: " + e.getMessage(), e);
                }
            }
        );
    }
}
