/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.yawlfoundation.yawl.mcp.a2a.mcp.McpTransportConfig;
import org.yawlfoundation.yawl.mcp.a2a.observability.config.ObservabilityConfig;
import org.yawlfoundation.yawl.mcp.a2a.observability.client.ObservabilityClient;
import org.yawlfoundation.yawl.mcp.a2a.observability.tools.ObservabilityToolSpecifications;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP Server for OpenTelemetry observability integration in YAWL workflow engine.
 *
 * <p>This server provides observability capabilities through MCP protocol,
 * allowing YAWL workflows to query metrics, traces, and health status from
 * OpenTelemetry collectors and backends like Prometheus, Jaeger, and Zipkin.</p>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><strong>Metrics Query</strong> - Query Prometheus-style metrics via OTLP</li>
 *   <li><strong>Trace Analysis</strong> - Query distributed traces from Jaeger/Zipkin</li>
 *   <li><strong>Health Status</strong> - Get system health and readiness status</li>
 *   <li><strong>Span Summary</strong> - Aggregate span statistics for workflows</li>
 *   <li><strong>Alert Status</strong> - Query current alert states from AlertManager</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Configure observability integration via {@link ObservabilityConfig}:</p>
 * <pre>{@code
 * ObservabilityConfig config = ObservabilityConfig.builder()
 *     .prometheusUrl("http://localhost:9090")
 *     .jaegerUrl("http://localhost:16686")
 *     .otlpEndpoint("http://localhost:4317")
 *     .build();
 *
 * ObservabilityMcpServer server = new ObservabilityMcpServer(config);
 * server.start();
 * }</pre>
 *
 * <h2>MCP Tools</h2>
 * <p>Provides observability-specific tools through MCP protocol:</p>
 * <ul>
 *   <li>get_metrics - Query Prometheus metrics with optional filters</li>
 *   <li>get_traces - Query distributed traces for workflow cases</li>
 *   <li>get_health_status - Get service health and readiness</li>
 *   <li>get_span_summary - Get aggregated span statistics</li>
 *   <li>get_alert_status - Query current alert states</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see ObservabilityConfig
 * @see ObservabilityClient
 * @see ObservabilityToolSpecifications
 */
public class ObservabilityMcpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObservabilityMcpServer.class);
    private static final String SERVER_NAME = "yawl-observability-mcp-server";
    private static final String SERVER_VERSION = "6.0.0";

    private final ObservabilityConfig config;
    private final ObservabilityClient observabilityClient;
    private final McpTransportConfig transportConfig;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private McpSyncServer stdioServer;
    private McpSyncServer httpServer;
    private WebMvcSseServerTransportProvider httpTransportProvider;
    private ObjectMapper objectMapper;

    /**
     * Construct an Observability MCP Server with configuration.
     *
     * @param config Observability configuration
     * @throws IllegalArgumentException if config is null
     */
    public ObservabilityMcpServer(ObservabilityConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ObservabilityConfig is required");
        }
        this.config = config;
        this.observabilityClient = new ObservabilityClient(config);
        this.transportConfig = McpTransportConfig.defaults();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Construct an Observability MCP Server with custom transport configuration.
     *
     * @param config Observability configuration
     * @param transportConfig transport configuration
     * @throws IllegalArgumentException if any parameter is null
     */
    public ObservabilityMcpServer(ObservabilityConfig config, McpTransportConfig transportConfig) {
        if (config == null) {
            throw new IllegalArgumentException("ObservabilityConfig is required");
        }
        if (transportConfig == null) {
            throw new IllegalArgumentException("McpTransportConfig is required");
        }
        this.config = config;
        this.observabilityClient = new ObservabilityClient(config);
        this.transportConfig = transportConfig;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * Start the MCP server with configured transports.
     *
     * @throws IOException if server startup fails
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            LOGGER.info("Starting YAWL Observability MCP Server v{}", SERVER_VERSION);

            if (transportConfig.enableStdio()) {
                startStdioTransport();
            }

            if (transportConfig.enabled()) {
                startHttpTransport();
            }

            LOGGER.info("YAWL Observability MCP Server started - STDIO: {}, HTTP: {}",
                transportConfig.enableStdio(), transportConfig.enabled());
        }
    }

    /**
     * Stop the MCP server gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            LOGGER.info("Stopping YAWL Observability MCP Server");

            if (stdioServer != null) {
                stdioServer.closeGracefully();
                stdioServer = null;
            }

            if (httpServer != null) {
                httpServer.closeGracefully();
                httpServer = null;
            }

            if (httpTransportProvider != null) {
                httpTransportProvider.closeGracefully().block(Duration.ofSeconds(10));
                httpTransportProvider = null;
            }

            LOGGER.info("YAWL Observability MCP Server stopped");
        }
    }

    /**
     * Check if the server is currently running.
     *
     * @return true if the server is active
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the HTTP transport's router function for Spring WebMVC integration.
     *
     * @return the RouterFunction for Observability MCP HTTP endpoints, or null if HTTP transport is disabled
     */
    public RouterFunction<ServerResponse> getRouterFunction() {
        if (httpTransportProvider != null) {
            return httpTransportProvider.getRouterFunction();
        }
        return null;
    }

    /**
     * Get the observability client for direct API access.
     *
     * @return the Observability client instance
     */
    public ObservabilityClient getObservabilityClient() {
        return observabilityClient;
    }

    /**
     * Get the observability configuration.
     *
     * @return the current observability configuration
     */
    public ObservabilityConfig getConfig() {
        return config;
    }

    /**
     * Get the transport configuration.
     *
     * @return the current transport configuration
     */
    public McpTransportConfig getTransportConfig() {
        return transportConfig;
    }

    /**
     * Check if HTTP transport is active.
     *
     * @return true if HTTP transport is enabled and running
     */
    public boolean isHttpTransportActive() {
        return httpServer != null && httpTransportProvider != null;
    }

    /**
     * Check if STDIO transport is active.
     *
     * @return true if STDIO transport is enabled and running
     */
    public boolean isStdioTransportActive() {
        return stdioServer != null;
    }

    // =========================================================================
    // Transport initialization
    // =========================================================================

    private void startStdioTransport() {
        McpSyncServer server = McpServer.sync(new StdioServerTransportProvider(objectMapper))
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(buildServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(ObservabilityToolSpecifications.createAll(observabilityClient))
            .build();

        stdioServer = server;
        LOGGER.info("STDIO transport started for Observability MCP Server");
        System.err.println("YAWL Observability MCP Server: STDIO transport active");
    }

    private void startHttpTransport() {
        String baseUrl = "http://localhost:" + transportConfig.port();

        httpTransportProvider = WebMvcSseServerTransportProvider.builder()
            .jsonMapper(objectMapper)
            .baseUrl(baseUrl)
            .sseEndpoint(transportConfig.fullSsePath())
            .messageEndpoint(transportConfig.fullMessagePath())
            .keepAliveInterval(Duration.ofSeconds(transportConfig.heartbeatIntervalSeconds()))
            .build();

        McpSyncServer server = McpServer.sync(httpTransportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(buildServerCapabilities())
            .instructions(buildServerInstructions())
            .tools(ObservabilityToolSpecifications.createAll(observabilityClient))
            .build();

        httpServer = server;
        LOGGER.info("HTTP/SSE transport started on port {}", transportConfig.port());
        LOGGER.info("SSE endpoint: {}", transportConfig.fullSsePath());
        LOGGER.info("Message endpoint: {}", transportConfig.fullMessagePath());
        System.err.println("YAWL Observability MCP Server: HTTP/SSE transport active on port " + transportConfig.port());
    }

    private McpServerFeatures.ServerCapabilities buildServerCapabilities() {
        return McpServerFeatures.ServerCapabilities.builder()
            .tools(true)
            .resources(false)
            .prompts(false)
            .completions(false)
            .logging(true)
            .build();
    }

    private String buildServerInstructions() {
        return """
            YAWL Observability Integration MCP Server v%s.

            Use tools to query OpenTelemetry metrics, traces, and health status.
            Monitor workflow performance, debug distributed traces, and check system health.

            Supported operations:
            • Query Prometheus metrics with optional filters
            • Query distributed traces for workflow cases
            • Get service health and readiness status
            • Get aggregated span statistics
            • Query current alert states from AlertManager

            Workflow monitoring:
            • Filter metrics by case_id label for workflow-specific metrics
            • Query traces by case-id span attribute for distributed tracing
            • Monitor yawl_workitem_duration_seconds for work item timing

            Configuration:
            • Prometheus URL: %s
            • Jaeger URL: %s
            • OTLP Endpoint: %s
            """.formatted(SERVER_VERSION,
                config.getPrometheusUrl() != null ? config.getPrometheusUrl() : "not configured",
                config.getJaegerUrl() != null ? config.getJaegerUrl() : "not configured",
                config.getOtlpEndpoint() != null ? config.getOtlpEndpoint() : "not configured");
    }

    // =========================================================================
    // Static factory methods
    // =========================================================================

    /**
     * Create an ObservabilityMcpServer with default transport configuration.
     *
     * @param config Observability configuration
     * @return configured server instance
     */
    public static ObservabilityMcpServer createDefault(ObservabilityConfig config) {
        return new ObservabilityMcpServer(config, McpTransportConfig.defaults());
    }

    /**
     * Create an ObservabilityMcpServer with STDIO transport only.
     *
     * @param config Observability configuration
     * @return configured server instance with STDIO only
     */
    public static ObservabilityMcpServer createStdioOnly(ObservabilityConfig config) {
        return new ObservabilityMcpServer(config, McpTransportConfig.stdioOnly());
    }

    /**
     * Create an ObservabilityMcpServer with HTTP transport only (no STDIO).
     *
     * @param config Observability configuration
     * @param port HTTP server port
     * @return configured server instance with HTTP only
     */
    public static ObservabilityMcpServer createHttpOnly(ObservabilityConfig config, int port) {
        McpTransportConfig transportConfig = new McpTransportConfig(
            true,      // enabled
            port,
            McpTransportConfig.DEFAULT_PATH,
            McpTransportConfig.DEFAULT_SSE_PATH,
            McpTransportConfig.DEFAULT_MESSAGE_PATH,
            McpTransportConfig.DEFAULT_MAX_CONNECTIONS,
            McpTransportConfig.DEFAULT_TIMEOUT_SECONDS,
            false,     // enableStdio
            true,      // enableHealthCheck
            McpTransportConfig.DEFAULT_HEARTBEAT_SECONDS
        );
        return new ObservabilityMcpServer(config, transportConfig);
    }
}
