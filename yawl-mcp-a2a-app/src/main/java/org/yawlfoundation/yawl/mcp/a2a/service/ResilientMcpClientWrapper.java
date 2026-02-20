package org.yawlfoundation.yawl.mcp.a2a.service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Resilient wrapper for MCP client with circuit breaker and retry patterns.
 *
 * <p>This wrapper provides fault tolerance for MCP client operations by implementing:</p>
 * <ul>
 *   <li><strong>Circuit Breaker</strong>: Prevents cascade failures by stopping calls to
 *       failing servers. Transitions through CLOSED, OPEN, and HALF_OPEN states.</li>
 *   <li><strong>Retry with Jitter</strong>: Retries transient failures with exponential
 *       backoff and randomized jitter to avoid thundering herd.</li>
 *   <li><strong>Fallback Strategies</strong>: Provides degraded responses when circuits
 *       are open, including caching and stale-while-revalidate patterns.</li>
 *   <li><strong>Metrics</strong>: Exposes circuit breaker state and call statistics.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create wrapper with configuration
 * CircuitBreakerProperties props = CircuitBreakerProperties.defaults();
 * ResilientMcpClientWrapper wrapper = new ResilientMcpClientWrapper(props);
 *
 * // Connect with resilience
 * wrapper.connectSse("mcp-server-1", "http://localhost:8081/mcp");
 *
 * // Call tool with automatic retry and circuit breaker
 * McpSchema.CallToolResult result = wrapper.callTool("mcp-server-1", "myTool", args);
 *
 * // Check circuit breaker state
 * McpCircuitBreakerState state = wrapper.getCircuitBreakerState("mcp-server-1");
 * }</pre>
 *
 * <h2>Configuration via application.yml</h2>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     resilience:
 *       enabled: true
 *       circuit-breaker:
 *         failure-rate-threshold: 50
 *         wait-duration-open-state: 30s
 *       retry:
 *         max-attempts: 3
 *         jitter-factor: 0.5
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ResilientMcpClientWrapper implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientMcpClientWrapper.class);
    private static final String CLIENT_NAME = "yawl-resilient-mcp-client";
    private static final String CLIENT_VERSION = "6.0.0";

    private final Map<String, McpSyncClient> clients;
    private final Map<String, Boolean> connectionStatus;
    private final McpCircuitBreakerRegistry circuitBreakerRegistry;
    private final Map<String, McpRetryWithJitter> retryRegistry;
    private final CircuitBreakerProperties properties;
    private final McpFallbackHandler fallbackHandler;
    private final JacksonMcpJsonMapper jsonMapper;

    /**
     * Creates a new resilient MCP client wrapper.
     *
     * @param properties the circuit breaker configuration properties
     */
    public ResilientMcpClientWrapper(CircuitBreakerProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clients = new ConcurrentHashMap<>();
        this.connectionStatus = new ConcurrentHashMap<>();
        this.circuitBreakerRegistry = new McpCircuitBreakerRegistry(properties);
        this.retryRegistry = new ConcurrentHashMap<>();
        this.fallbackHandler = new McpFallbackHandler(
            properties.fallback() != null
                ? properties.fallback()
                : CircuitBreakerProperties.FallbackConfig.defaults());
        this.jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());

        LOGGER.info("Initialized resilient MCP client wrapper with circuit breaker enabled: {}",
                   properties.enabled());
    }

    /**
     * Connects to an MCP server via SSE transport with resilience tracking.
     *
     * @param serverId the unique identifier for this server connection
     * @param serverUrl the URL of the MCP server SSE endpoint
     * @throws IllegalStateException if already connected to this server
     */
    public void connectSse(String serverId, String serverUrl) {
        if (clients.containsKey(serverId)) {
            throw new IllegalStateException("Already connected to server: " + serverId);
        }

        LOGGER.info("Connecting to MCP server via SSE: id={}, url={}", serverId, serverUrl);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(serverUrl)
            .jsonMapper(jsonMapper)
            .build();

        McpSyncClient client = McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
            .requestTimeout(Duration.ofSeconds(30))
            .build();

        client.initialize();
        clients.put(serverId, client);
        connectionStatus.put(serverId, true);
        initializeResilienceForServer(serverId);

        LOGGER.info("Connected to MCP server via SSE: {}", serverId);
    }

    /**
     * Connects to an MCP server via STDIO transport with resilience tracking.
     *
     * @param serverId the unique identifier for this server connection
     * @param command the command to launch the MCP server process
     * @param args arguments to pass to the command
     * @throws IllegalStateException if already connected to this server
     */
    public void connectStdio(String serverId, String command, String... args) {
        if (clients.containsKey(serverId)) {
            throw new IllegalStateException("Already connected to server: " + serverId);
        }

        LOGGER.info("Connecting to MCP server via STDIO: id={}, command={}", serverId, command);

        ServerParameters serverParams = ServerParameters.builder(command)
            .args(args)
            .build();

        StdioClientTransport transport = new StdioClientTransport(serverParams, jsonMapper);

        McpSyncClient client = McpClient.sync(transport)
            .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
            .requestTimeout(Duration.ofSeconds(30))
            .build();

        client.initialize();
        clients.put(serverId, client);
        connectionStatus.put(serverId, true);
        initializeResilienceForServer(serverId);

        LOGGER.info("Connected to MCP server via STDIO: {}", serverId);
    }

    /**
     * Lists all tools available on the connected MCP server with circuit breaker protection.
     *
     * @param serverId the server identifier
     * @return list of tool definitions
     * @throws McpClientException if the call fails after retries
     * @throws CallNotPermittedException if the circuit is open
     */
    public List<McpSchema.Tool> listTools(String serverId) {
        return executeWithResilience(serverId, "listTools", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.ListToolsResult result = client.listTools();
            return result.tools() != null ? result.tools() : Collections.emptyList();
        });
    }

    /**
     * Calls a tool on the connected MCP server with circuit breaker and retry protection.
     *
     * @param serverId the server identifier
     * @param toolName name of the tool to invoke
     * @param arguments arguments to pass to the tool
     * @return the tool call result
     * @throws McpClientException if the call fails after retries
     * @throws CallNotPermittedException if the circuit is open
     */
    public McpSchema.CallToolResult callTool(String serverId, String toolName,
                                              Map<String, Object> arguments) {
        return executeWithResilience(serverId, "callTool:" + toolName, () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, arguments);
            return client.callTool(request);
        });
    }

    /**
     * Lists all resources available on the connected MCP server with resilience.
     *
     * @param serverId the server identifier
     * @return list of resource definitions
     * @throws McpClientException if the call fails after retries
     */
    public List<McpSchema.Resource> listResources(String serverId) {
        return executeWithResilience(serverId, "listResources", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.ListResourcesResult result = client.listResources();
            return result.resources() != null ? result.resources() : Collections.emptyList();
        });
    }

    /**
     * Reads a resource from the connected MCP server with resilience.
     *
     * @param serverId the server identifier
     * @param resource the resource to read
     * @return the resource contents
     * @throws McpClientException if the call fails after retries
     */
    public McpSchema.ReadResourceResult readResource(String serverId, McpSchema.Resource resource) {
        return executeWithResilience(serverId, "readResource:" + resource.uri(), () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            return client.readResource(resource);
        });
    }

    /**
     * Reads a resource by URI with resilience.
     *
     * @param serverId the server identifier
     * @param uri the resource URI to read
     * @return the resource contents
     * @throws McpClientException if the call fails after retries
     */
    public McpSchema.ReadResourceResult readResourceByUri(String serverId, String uri) {
        return executeWithResilience(serverId, "readResourceByUri:" + uri, () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
            return client.readResource(request);
        });
    }

    /**
     * Lists all prompts available on the connected MCP server with resilience.
     *
     * @param serverId the server identifier
     * @return list of prompt definitions
     * @throws McpClientException if the call fails after retries
     */
    public List<McpSchema.Prompt> listPrompts(String serverId) {
        return executeWithResilience(serverId, "listPrompts", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.ListPromptsResult result = client.listPrompts();
            return result.prompts() != null ? result.prompts() : Collections.emptyList();
        });
    }

    /**
     * Gets a prompt from the connected MCP server with resilience.
     *
     * @param serverId the server identifier
     * @param promptName name of the prompt
     * @param arguments arguments to pass to the prompt
     * @return the prompt result with generated messages
     * @throws McpClientException if the call fails after retries
     */
    public McpSchema.GetPromptResult getPrompt(String serverId, String promptName,
                                                Map<String, Object> arguments) {
        return executeWithResilience(serverId, "getPrompt:" + promptName, () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            McpSchema.GetPromptRequest request = new McpSchema.GetPromptRequest(promptName, arguments);
            return client.getPrompt(request);
        });
    }

    /**
     * Gets server capabilities after initialization with resilience.
     *
     * @param serverId the server identifier
     * @return the server's capabilities
     * @throws McpClientException if the call fails after retries
     */
    public McpSchema.ServerCapabilities getServerCapabilities(String serverId) {
        return executeWithResilience(serverId, "getServerCapabilities", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            return client.getServerCapabilities();
        });
    }

    /**
     * Gets server info after initialization with resilience.
     *
     * @param serverId the server identifier
     * @return the server's implementation info
     * @throws McpClientException if the call fails after retries
     */
    public McpSchema.Implementation getServerInfo(String serverId) {
        return executeWithResilience(serverId, "getServerInfo", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            return client.getServerInfo();
        });
    }

    /**
     * Pings the server with resilience.
     *
     * @param serverId the server identifier
     * @throws McpClientException if the ping fails after retries
     */
    public void ping(String serverId) {
        executeWithResilience(serverId, "ping", () -> {
            McpSyncClient client = getClientOrThrow(serverId);
            client.ping();
            return null;
        });
    }

    /**
     * Checks if connected to an MCP server.
     *
     * @param serverId the server identifier
     * @return true if connected
     */
    public boolean isConnected(String serverId) {
        return Boolean.TRUE.equals(connectionStatus.get(serverId)) && clients.containsKey(serverId);
    }

    /**
     * Gets the current circuit breaker state for a server.
     *
     * @param serverId the server identifier
     * @return the current circuit breaker state
     */
    public McpCircuitBreakerState getCircuitBreakerState(String serverId) {
        return circuitBreakerRegistry.getState(serverId);
    }

    /**
     * Checks if calls are permitted for the specified server.
     *
     * @param serverId the server identifier
     * @return true if calls are permitted
     */
    public boolean isCallPermitted(String serverId) {
        return circuitBreakerRegistry.isCallPermitted(serverId);
    }

    /**
     * Gets all registered server identifiers.
     *
     * @return iterable of server identifiers
     */
    public Iterable<String> getServerIds() {
        return clients.keySet();
    }

    /**
     * Disconnects from a specific MCP server.
     *
     * @param serverId the server identifier
     */
    public void disconnect(String serverId) {
        McpSyncClient client = clients.remove(serverId);
        connectionStatus.remove(serverId);
        if (client != null) {
            client.closeGracefully();
            circuitBreakerRegistry.remove(serverId);
            retryRegistry.remove(serverId);
            LOGGER.info("Disconnected from MCP server: {}", serverId);
        }
    }

    /**
     * Resets the circuit breaker for a specific server.
     *
     * @param serverId the server identifier
     */
    public void resetCircuitBreaker(String serverId) {
        McpCircuitBreakerState state = circuitBreakerRegistry.getState(serverId);
        if (state instanceof McpCircuitBreakerState.Open) {
            LOGGER.info("Resetting circuit breaker for MCP server: {}", serverId);
        }
        CircuitBreaker cb = circuitBreakerRegistry.getResilience4jRegistry().circuitBreaker(serverId);
        if (cb != null) {
            cb.reset();
        }
    }

    @Override
    public void close() {
        clients.keySet().forEach(this::disconnect);
        fallbackHandler.shutdown();
        LOGGER.info("Closed all MCP client connections");
    }

    private void initializeResilienceForServer(String serverId) {
        // Initialize circuit breaker
        circuitBreakerRegistry.getOrCreate(serverId);

        // Initialize retry with jitter (use defaults if retry config is null)
        CircuitBreakerProperties.RetryConfig retryConfig = properties.retry() != null
            ? properties.retry()
            : CircuitBreakerProperties.RetryConfig.defaults();
        McpRetryWithJitter retry = new McpRetryWithJitter(serverId, retryConfig);
        retryRegistry.put(serverId, retry);

        LOGGER.debug("Initialized resilience patterns for server: {}", serverId);
    }

    private <T> T executeWithResilience(String serverId, String operation, Supplier<T> supplier) {
        if (!properties.enabled()) {
            return supplier.get();
        }

        // Check if circuit breaker permits the call
        if (!circuitBreakerRegistry.isCallPermitted(serverId)) {
            McpCircuitBreakerState state = circuitBreakerRegistry.getState(serverId);
            LOGGER.warn("Circuit breaker OPEN for MCP server {}, rejecting call to {}",
                       serverId, operation);

            // Try fallback
            Optional<T> fallback = fallbackHandler.getFallback(serverId, operation);
            if (fallback.isPresent()) {
                LOGGER.info("Returning fallback response for {} on server {}",
                           operation, serverId);
                return fallback.get();
            }

            throw new McpClientException(
                "Circuit breaker is OPEN for server " + serverId,
                serverId, operation);
        }

        McpRetryWithJitter retry = retryRegistry.get(serverId);
        if (retry == null) {
            CircuitBreakerProperties.RetryConfig retryConfig = properties.retry() != null
                ? properties.retry()
                : CircuitBreakerProperties.RetryConfig.defaults();
            retry = new McpRetryWithJitter(retryConfig);
        }

        try {
            T result = retry.execute(serverId, operation, () -> {
                try {
                    T r = supplier.get();
                    circuitBreakerRegistry.recordSuccess(serverId);
                    return r;
                } catch (Exception e) {
                    circuitBreakerRegistry.recordFailure(serverId, e.getMessage());
                    throw e;
                }
            });

            // Cache successful result for potential fallback
            fallbackHandler.cacheResult(serverId, operation, result);

            return result;
        } catch (McpClientException e) {
            LOGGER.error("MCP operation failed after retries: server={}, operation={}, error={}",
                        serverId, operation, e.getMessage());
            throw e;
        }
    }

    private McpSyncClient getClientOrThrow(String serverId) {
        McpSyncClient client = clients.get(serverId);
        if (client == null) {
            throw new McpClientException(
                "Not connected to MCP server: " + serverId,
                serverId, "getConnection");
        }
        if (!Boolean.TRUE.equals(connectionStatus.get(serverId))) {
            throw new McpClientException(
                "Connection to MCP server lost: " + serverId,
                serverId, "checkConnection");
        }
        return client;
    }
}
