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

package org.yawlfoundation.yawl.integration.a2a;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.a2a.A2A;
import io.a2a.server.ServerCallContext;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.MainEventBus;
import io.a2a.server.events.MainEventBusProcessor;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.AgentEmitter;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.spec.*;
import io.a2a.transport.rest.handler.RestHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffException;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.metrics.VirtualThreadMetrics;
// import org.yawlfoundation.yawl.integration.mcp.zai.ZaiFunctionService;
import org.yawlfoundation.yawl.util.SafeNumberParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent-to-Agent (A2A) Server for YAWL optimized for Java 25 virtual threads.
 *
 * <p>This implementation extends the capabilities of {@link YawlA2AServer} with
 * full Java 25 virtual thread integration:</p>
 *
 * <ul>
 *   <li><b>Virtual threads for all I/O-bound operations</b>: HTTP handlers, YAWL engine
 *       calls, and A2A message processing run on lightweight virtual threads</li>
 *   <li><b>Structured concurrency</b>: Parallel work item processing with automatic
 *       cancellation on failure using {@link StructuredTaskScope}</li>
 *   <li><b>Virtual thread-aware HttpClient</b>: Connection pooling optimized for
 *       virtual threads with proper timeout configuration</li>
 *   <li><b>Graceful shutdown</b>: Respects virtual thread behavior with configurable
 *       drain timeout</li>
 *   <li><b>Metrics collection</b>: Tracks virtual thread counts, carrier thread usage,
 *       and request latencies</li>
 * </ul>
 *
 * <h2>Thread Model</h2>
 *
 * <p>All HTTP request handlers execute on virtual threads provided by
 * {@link Executors#newVirtualThreadPerTaskExecutor()}. This enables:</p>
 *
 * <ul>
 *   <li>Thousands of concurrent connections without thread pool exhaustion</li>
 *   <li>Blocking I/O operations (HTTP calls to YAWL engine) that don't block
 *       carrier threads</li>
 *   <li>Simplified concurrency model - write synchronous code, get async scalability</li>
 * </ul>
 *
 * <h2>Structured Concurrency</h2>
 *
 * <p>For batch operations (e.g., querying multiple work items in parallel), this
 * server uses {@link StructuredTaskScope}:</p>
 *
 * <pre>{@code
 * try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
 *     List<Subtask<WorkItemResult>> tasks = items.stream()
 *         .map(item -> scope.fork(() -> processWorkItem(item)))
 *         .toList();
 *     scope.join();
 *     scope.throwIfFailed();
 *     return tasks.stream().map(Subtask::resultNow).toList();
 * }
 * }</pre>
 *
 * <h2>Configuration</h2>
 *
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code YAWL_ENGINE_URL} - YAWL engine base URL (required)</li>
 *   <li>{@code YAWL_USERNAME} - YAWL admin username (required)</li>
 *   <li>{@code YAWL_PASSWORD} - YAWL admin password (required)</li>
 *   <li>{@code A2A_PORT} - Port to run the A2A server on (default: 8081)</li>
 *   <li>{@code A2A_GRACEFUL_SHUTDOWN_SECONDS} - Graceful shutdown timeout (default: 30)</li>
 *   <li>{@code A2A_HTTP_CLIENT_TIMEOUT_SECONDS} - HTTP client timeout (default: 60)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 * @see YawlA2AServer
 * @see <a href="https://openjdk.org/jeps/444">JEP 444: Virtual Threads</a>
 * @see <a href="https://openjdk.org/jeps/505">JEP 505: Structured Concurrency</a>
 */
public class VirtualThreadYawlA2AServer {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadYawlA2AServer.class);
    private static final String SERVER_VERSION = "6.0.0";

    // Default configuration
    private static final int DEFAULT_GRACEFUL_SHUTDOWN_SECONDS = 30;
    private static final int DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS = 60;

    // Configuration
    private final String yawlEngineUrl;
    private final String yawlUsername;
    private final String yawlPassword;
    private final int port;
    private final int gracefulShutdownSeconds;
    private final int httpClientTimeoutSeconds;
    private final A2AAuthenticationProvider authProvider;
      private final Object zaiFunctionService;
    private final Method processWithFunctionsMethod;

    // HTTP server and executor
    private HttpServer httpServer;
    private ExecutorService virtualThreadExecutor;
    private HttpClient httpClient;

    // YAWL engine connection
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private volatile String sessionHandle;

    // Handoff services
    private HandoffProtocol handoffProtocol;

    // Metrics
    private final VirtualThreadMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong requestsProcessed = new AtomicLong(0);
    private final AtomicLong activeRequests = new AtomicLong(0);

    /**
     * Construct a VirtualThreadYawlA2AServer with explicit configuration.
     *
     * @param yawlEngineUrl  base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username       YAWL admin username
     * @param password       YAWL admin password
     * @param port           port to run the A2A server on
     * @param gracefulShutdownSeconds graceful shutdown timeout in seconds
     * @param httpClientTimeoutSeconds HTTP client timeout for YAWL engine calls
     * @param authProvider   authentication provider for request validation
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public VirtualThreadYawlA2AServer(String yawlEngineUrl,
                                       String username,
                                       String password,
                                       int port,
                                       int gracefulShutdownSeconds,
                                       int httpClientTimeoutSeconds,
                                       A2AAuthenticationProvider authProvider) {
        validateConstructorParameters(yawlEngineUrl, username, password, port,
                                       gracefulShutdownSeconds, httpClientTimeoutSeconds, authProvider);

        this.yawlEngineUrl = yawlEngineUrl;
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.port = port;
        this.gracefulShutdownSeconds = gracefulShutdownSeconds;
        this.httpClientTimeoutSeconds = httpClientTimeoutSeconds;
        this.authProvider = authProvider;

        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(yawlEngineUrl + "/ib");
        this.metrics = new VirtualThreadMetrics();

        String zaiApiKey = System.getenv("ZAI_API_KEY");
        if (zaiApiKey != null && !zaiApiKey.isEmpty()) {
            try {
                Class<?> zaiFunctionServiceClass = Class.forName("org.yawlfoundation.yawl.integration.zai.ZaiFunctionService");
                this.zaiFunctionService = zaiFunctionServiceClass.getConstructor(
                    String.class, String.class, String.class, String.class)
                    .newInstance(zaiApiKey, yawlEngineUrl, username, password);

                this.processWithFunctionsMethod = zaiFunctionServiceClass.getMethod(
                    "processWithFunctions", String.class);
            } catch (Exception e) {
                _logger.warn("Failed to initialize ZaiFunctionService via reflection: {}", e.getMessage());
                this.zaiFunctionService = null;
                this.processWithFunctionsMethod = null;
            }
        } else {
            this.zaiFunctionService = null;
            this.processWithFunctionsMethod = null;
        }

        _logger.info("VirtualThreadYawlA2AServer configured: port={}, shutdownTimeout={}s, httpTimeout={}s",
                     port, gracefulShutdownSeconds, httpClientTimeoutSeconds);
    }

    /**
     * Construct a VirtualThreadYawlA2AServer with default timeouts.
     *
     * @param yawlEngineUrl  base URL of YAWL engine
     * @param username       YAWL admin username
     * @param password       YAWL admin password
     * @param port           port to run the A2A server on
     * @param authProvider   authentication provider for request validation
     */
    public VirtualThreadYawlA2AServer(String yawlEngineUrl,
                                       String username,
                                       String password,
                                       int port,
                                       A2AAuthenticationProvider authProvider) {
        this(yawlEngineUrl, username, password, port,
             DEFAULT_GRACEFUL_SHUTDOWN_SECONDS, DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS, authProvider);
    }

    private void validateConstructorParameters(String yawlEngineUrl,
                                                String username,
                                                String password,
                                                int port,
                                                int gracefulShutdownSeconds,
                                                int httpClientTimeoutSeconds,
                                                A2AAuthenticationProvider authProvider) {
        if (yawlEngineUrl == null || yawlEngineUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required (e.g. http://localhost:8080/yawl)");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (gracefulShutdownSeconds < 1) {
            throw new IllegalArgumentException("Graceful shutdown seconds must be positive");
        }
        if (httpClientTimeoutSeconds < 1) {
            throw new IllegalArgumentException("HTTP client timeout seconds must be positive");
        }
        if (authProvider == null) {
            throw new IllegalArgumentException(
                "An authentication provider is required. "
                + "Use CompositeAuthenticationProvider.production() for the recommended stack.");
        }
    }

    /**
     * Build and start the A2A server with virtual thread optimization.
     *
     * <p>This method initializes:</p>
     * <ul>
     *   <li>Virtual thread executor for HTTP request handling</li>
     *   <li>HttpClient with virtual thread-aware connection pooling</li>
     *   <li>A2A server components (task store, event bus, request handler)</li>
     *   <li>HTTP server on the configured port</li>
     * </ul>
     *
     * @throws IOException if the HTTP server cannot bind to the port
     */
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            _logger.info("Starting VirtualThreadYawlA2AServer v{} on port {}", SERVER_VERSION, port);

            // Initialize virtual thread executor
            virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

            // Initialize HTTP client optimized for virtual threads
            httpClient = createVirtualThreadHttpClient();

            // Initialize handoff services
            initializeHandoffServices();

            // Build agent card and A2A components
            AgentCard agentCard = buildAgentCard();
            InMemoryTaskStore taskStore = new InMemoryTaskStore();
            MainEventBus mainEventBus = new MainEventBus();
            InMemoryQueueManager queueManager = new InMemoryQueueManager(taskStore, mainEventBus);
            InMemoryPushNotificationConfigStore pushStore = new InMemoryPushNotificationConfigStore();
            MainEventBusProcessor busProcessor = new MainEventBusProcessor(
                mainEventBus, taskStore, null, queueManager);
            busProcessor.ensureStarted();

            // Create agent executor with virtual thread support
            VirtualThreadAgentExecutor agentExecutor = new VirtualThreadAgentExecutor();

            // Create request handler with virtual thread executors
            DefaultRequestHandler requestHandler = DefaultRequestHandler.create(
                agentExecutor, taskStore, queueManager, pushStore,
                busProcessor, virtualThreadExecutor, virtualThreadExecutor);

            RestHandler restHandler = new RestHandler(
                agentCard, requestHandler, virtualThreadExecutor);

            // Create HTTP server with virtual thread executor
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.setExecutor(virtualThreadExecutor);

            // Register endpoints
            registerEndpoints(restHandler);

            httpServer.start();
            metrics.recordServerStart();

            _logger.info("YAWL A2A Server v{} started on port {}", SERVER_VERSION, port);
            _logger.info("Agent card: http://localhost:{}/.well-known/agent.json", port);
            _logger.info("Authentication: {}", authProvider.scheme());
            _logger.info("Thread model: Virtual threads (Java 25)");
        }
    }

    /**
     * Stop the A2A server with graceful shutdown.
     *
     * <p>The graceful shutdown process:</p>
     * <ol>
     *   <li>Stop accepting new HTTP connections</li>
     *   <li>Wait up to {@code gracefulShutdownSeconds} for in-flight requests to complete</li>
     *   <li>Force shutdown if timeout exceeded</li>
     *   <li>Disconnect from YAWL engine</li>
     * </ol>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            _logger.info("Stopping VirtualThreadYawlA2AServer (graceful timeout: {}s)", gracefulShutdownSeconds);
            metrics.recordServerStop();

            // Stop HTTP server first (stops accepting new connections)
            if (httpServer != null) {
                httpServer.stop(gracefulShutdownSeconds);
                httpServer = null;
            }

            // Gracefully shutdown executor with timeout
            if (virtualThreadExecutor != null) {
                virtualThreadExecutor.shutdown();
                try {
                    if (!virtualThreadExecutor.awaitTermination(gracefulShutdownSeconds, TimeUnit.SECONDS)) {
                        _logger.warn("Virtual thread executor did not terminate gracefully, forcing shutdown");
                        List<Runnable> pending = virtualThreadExecutor.shutdownNow();
                        _logger.warn("Forced shutdown with {} pending tasks", pending.size());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    virtualThreadExecutor.shutdownNow();
                }
                virtualThreadExecutor = null;
            }

            // Close HTTP client
            if (httpClient != null) {
                httpClient.close();
                httpClient = null;
            }

            disconnectFromEngine();

            _logger.info("YAWL A2A Server stopped. Total requests processed: {}", requestsProcessed.get());
            _logger.info("Final metrics: {}", metrics.getSummary());
        }
    }

    /**
     * Check if the server is running.
     *
     * @return true if the server is running
     */
    public boolean isRunning() {
        return running.get() && httpServer != null;
    }

    /**
     * Get current virtual thread metrics.
     *
     * @return snapshot of current metrics
     */
    public VirtualThreadMetrics.MetricsSnapshot getMetrics() {
        return metrics.getSnapshot();
    }

    /**
     * Get the count of requests processed since server start.
     *
     * @return total requests processed
     */
    public long getRequestsProcessed() {
        return requestsProcessed.get();
    }

    /**
     * Get the count of currently active requests.
     *
     * @return active request count
     */
    public long getActiveRequests() {
        return activeRequests.get();
    }

    // =========================================================================
    // HTTP Client Configuration for Virtual Threads
    // =========================================================================

    /**
     * Create an HttpClient optimized for use with virtual threads.
     *
     * <p>Virtual threads work best with HTTP clients that:</p>
     * <ul>
     *   <li>Use non-blocking I/O under the hood (Java HttpClient does this)</li>
     *   <li>Have appropriate connection pool sizing</li>
     *   <li>Have reasonable timeouts to prevent thread accumulation</li>
     * </ul>
     *
     * @return configured HttpClient instance
     */
    private HttpClient createVirtualThreadHttpClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(httpClientTimeoutSeconds))
            .executor(virtualThreadExecutor)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    // =========================================================================
    // HTTP Endpoint Registration
    // =========================================================================

    private void registerEndpoints(RestHandler restHandler) {
        // Agent card endpoint: no authentication required (public discovery)
        httpServer.createContext("/.well-known/agent.json", exchange -> {
            long startTime = System.nanoTime();
            activeRequests.incrementAndGet();
            try {
                metrics.recordRequestStart();
                handleRestCall(exchange, () -> restHandler.getAgentCard());
            } finally {
                activeRequests.decrementAndGet();
                requestsProcessed.incrementAndGet();
                metrics.recordRequestComplete(System.nanoTime() - startTime);
            }
        });

        // Metrics endpoint: virtual thread statistics
        httpServer.createContext("/metrics", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            handleMetricsRequest(exchange);
        });

        // Health check endpoint
        httpServer.createContext("/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            handleHealthCheck(exchange);
        });

        // All other endpoints: authentication required
        httpServer.createContext("/", exchange -> {
            long startTime = System.nanoTime();
            activeRequests.incrementAndGet();
            try {
                metrics.recordRequestStart();
                handleAuthenticatedRequest(exchange, restHandler);
            } finally {
                activeRequests.decrementAndGet();
                requestsProcessed.incrementAndGet();
                metrics.recordRequestComplete(System.nanoTime() - startTime);
            }
        });
    }

    // =========================================================================
    // Request Handling
    // =========================================================================

    private void handleAuthenticatedRequest(HttpExchange exchange, RestHandler restHandler)
            throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Authenticate before processing any workflow operation
        AuthenticatedPrincipal principal;
        try {
            principal = authProvider.authenticate(exchange);
        } catch (A2AAuthenticationException authEx) {
            sendAuthenticationChallenge(exchange, authEx);
            return;
        }

        // Reject expired credentials
        if (principal.isExpired()) {
            sendAuthenticationChallenge(exchange,
                new A2AAuthenticationException(
                    "Credential has expired. Obtain a new credential and retry.",
                    authProvider.scheme()));
            return;
        }

        ServerCallContext callContext = new ServerCallContext(
            principal, new java.util.HashMap<>(), Collections.emptySet());

        if ("POST".equals(method) && "/".equals(path)) {
            handleMessageEndpoint(exchange, restHandler, callContext, principal);
        } else if ("GET".equals(method) && path.startsWith("/tasks/")) {
            handleTaskGet(exchange, restHandler, callContext, path, principal);
        } else if ("POST".equals(method) && path.matches("/tasks/.+/cancel")) {
            handleTaskCancel(exchange, restHandler, callContext, path, principal);
        } else if ("POST".equals(method) && "/handoff".equals(path)) {
            handleHandoffEndpoint(exchange, callContext, principal);
        } else {
            sendNotFound(exchange);
        }
    }

    private void handleMessageEndpoint(HttpExchange exchange, RestHandler restHandler,
                                        ServerCallContext callContext,
                                        AuthenticatedPrincipal principal) throws IOException {
        if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
            sendForbidden(exchange, "Insufficient permissions to invoke A2A message endpoint.");
            return;
        }
        String body = readRequestBody(exchange);
        handleRestCall(exchange, () -> restHandler.sendMessage(callContext, null, body));
    }

    private void handleTaskGet(HttpExchange exchange, RestHandler restHandler,
                                ServerCallContext callContext, String path,
                                AuthenticatedPrincipal principal) throws IOException {
        if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
            sendForbidden(exchange, "Insufficient permissions to query task status.");
            return;
        }
        String taskId = path.substring("/tasks/".length());
        if (taskId.endsWith("/cancel")) {
            exchange.sendResponseHeaders(405, -1);
        } else {
            handleRestCall(exchange, () -> restHandler.getTask(callContext, taskId, null, null));
        }
    }

    private void handleTaskCancel(HttpExchange exchange, RestHandler restHandler,
                                   ServerCallContext callContext, String path,
                                   AuthenticatedPrincipal principal) throws IOException {
        if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
            sendForbidden(exchange, "Insufficient permissions to cancel a task.");
            return;
        }
        String taskId = path.replace("/tasks/", "").replace("/cancel", "");
        handleRestCall(exchange, () -> restHandler.cancelTask(callContext, taskId, null));
    }

    private void handleHandoffEndpoint(HttpExchange exchange, ServerCallContext callContext,
                                        AuthenticatedPrincipal principal) throws IOException {
        if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE)
                && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
            sendForbidden(exchange, "Insufficient permissions to process handoff messages.");
            return;
        }
        String body = readRequestBody(exchange);
        handleHandoffMessage(exchange, callContext, body);
    }

    private void handleMetricsRequest(HttpExchange exchange) throws IOException {
        String metricsJson = metrics.toJson();
        byte[] response = metricsJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private void handleHealthCheck(HttpExchange exchange) throws IOException {
        boolean healthy = isRunning();
        String status = healthy ? "healthy" : "unhealthy";
        String response = "{\"status\":\"" + status + "\",\"version\":\"" + SERVER_VERSION + "\"}";
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(healthy ? 200 : 503, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // =========================================================================
    // Agent Card definition
    // =========================================================================

    private AgentCard buildAgentCard() {
        return AgentCard.builder()
            .name("YAWL Workflow Engine")
            .description("YAWL (Yet Another Workflow Language) BPM engine agent. "
                + "Manages workflow specifications, cases, and work items. "
                + "Supports launching workflows, querying status, managing tasks, "
                + "and orchestrating complex business processes. "
                + "Optimized for Java 25 virtual threads.")
            .version(SERVER_VERSION)
            .provider(new AgentProvider("YAWL Foundation", "https://yawlfoundation.github.io"))
            .capabilities(AgentCapabilities.builder()
                .streaming(false)
                .pushNotifications(false)
                .build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(
                AgentSkill.builder()
                    .id("launch_workflow")
                    .name("Launch Workflow")
                    .description("Launch a new workflow case from a loaded specification. "
                        + "Provide the specification identifier and optional case data.")
                    .tags(List.of("workflow", "bpm", "launch", "case"))
                    .examples(List.of(
                        "Launch the OrderProcessing workflow",
                        "Start a new case of specification 'InvoiceApproval' with order data"
                    ))
                    .inputModes(List.of("text"))
                    .outputModes(List.of("text"))
                    .build(),
                AgentSkill.builder()
                    .id("query_workflows")
                    .name("Query Workflows")
                    .description("List available workflow specifications, running cases, "
                        + "and their current status.")
                    .tags(List.of("workflow", "query", "list", "status"))
                    .examples(List.of(
                        "List all loaded workflow specifications",
                        "Show running cases and their status"
                    ))
                    .inputModes(List.of("text"))
                    .outputModes(List.of("text"))
                    .build(),
                AgentSkill.builder()
                    .id("manage_workitems")
                    .name("Manage Work Items")
                    .description("Get, check out, and complete work items in running "
                        + "workflow cases. Supports batch operations with structured concurrency.")
                    .tags(List.of("workflow", "workitem", "task", "complete", "batch"))
                    .examples(List.of(
                        "Show work items for case 42",
                        "Complete work item 42:ReviewOrder with approved status",
                        "Batch process all work items for case 100"
                    ))
                    .inputModes(List.of("text"))
                    .outputModes(List.of("text"))
                    .build(),
                AgentSkill.builder()
                    .id("cancel_workflow")
                    .name("Cancel Workflow")
                    .description("Cancel a running workflow case by its case ID.")
                    .tags(List.of("workflow", "cancel", "case"))
                    .examples(List.of(
                        "Cancel case 42",
                        "Stop the running workflow case with ID 15"
                    ))
                    .inputModes(List.of("text"))
                    .outputModes(List.of("text"))
                    .build(),
                AgentSkill.builder()
                    .id("handoff_workitem")
                    .name("Handoff Work Item")
                    .description("Transfer a work item to another agent when the current "
                        + "agent cannot complete it. Uses secure JWT-based handoff protocol.")
                    .tags(List.of("workflow", "handoff", "transfer", "agent-to-agent"))
                    .examples(List.of(
                        "Handoff work item WI-42 to a specialized agent",
                        "Transfer document review task to agent-2"
                    ))
                    .inputModes(List.of("text"))
                    .outputModes(List.of("text"))
                    .build()
            ))
            .build();
    }

    // =========================================================================
    // Agent Executor with Virtual Thread Support and Structured Concurrency
    // =========================================================================

    private class VirtualThreadAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            emitter.startWork();

            try {
                Message userMessage = context.getMessage();
                String userText = extractTextFromMessage(userMessage);

                String response = processWorkflowRequest(userText);

                emitter.complete(A2A.toAgentMessage(response));
            } catch (IOException e) {
                _logger.error("YAWL engine error during execution", e);
                emitter.fail(A2A.toAgentMessage("YAWL engine error: " + e.getMessage()));
            } catch (Exception e) {
                _logger.error("Processing error during execution", e);
                emitter.fail(A2A.toAgentMessage("Processing error: " + e.getMessage()));
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            String taskId = context.getTaskId();
            try {
                ensureEngineConnection();
                String result = interfaceBClient.cancelCase(taskId, sessionHandle);
                if (result != null && result.contains("<failure>")) {
                    emitter.fail(A2A.toAgentMessage("Failed to cancel case: " + result));
                } else {
                    emitter.cancel(A2A.toAgentMessage("Case " + taskId + " cancelled successfully"));
                }
            } catch (IOException e) {
                _logger.error("Failed to cancel case {}", taskId, e);
                emitter.fail(A2A.toAgentMessage("Failed to cancel: " + e.getMessage()));
            }
        }

        private String processWorkflowRequest(String userText) throws IOException {
            if (zaiFunctionService != null && processWithFunctionsMethod != null) {
                try {
                    return (String) processWithFunctionsMethod.invoke(zaiFunctionService, userText);
                } catch (Exception e) {
                    return "Z.AI processing error: " + e.getMessage();
                }
            }
            ensureEngineConnection();
            String lower = userText.toLowerCase().trim();

            if (lower.contains("batch") && lower.contains("work item")) {
                return handleBatchWorkItems(userText);
            }

            if (lower.contains("list") && (lower.contains("spec") || lower.contains("workflow"))) {
                return handleListSpecifications();
            }

            if (lower.contains("launch") || lower.contains("start")) {
                return handleLaunchCase(userText);
            }

            if (lower.contains("status") || lower.contains("case")) {
                return handleCaseQuery(userText);
            }

            if (lower.contains("work item") || lower.contains("workitem") || lower.contains("task")) {
                return handleWorkItemQuery(userText);
            }

            if (lower.contains("cancel") || lower.contains("stop")) {
                return handleCancelCase(userText);
            }

            return handleListSpecifications();
        }

        /**
         * Handle batch work item processing using structured concurrency.
         *
         * <p>Uses {@link StructuredTaskScope.ShutdownOnFailure} to process multiple
         * work items in parallel with automatic cancellation on failure.</p>
         *
         * @param userText the user request text
         * @return summary of batch processing results
         */
        private String handleBatchWorkItems(String userText) throws IOException {
            String caseId = extractNumber(userText);
            List<WorkItemRecord> items;

            if (caseId != null) {
                items = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
            } else {
                items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
            }

            if (items == null || items.isEmpty()) {
                return "No work items available for batch processing.";
            }

            // Filter to only enabled/fired items that can be processed
            List<WorkItemRecord> processableItems = items.stream()
                .filter(item -> "statusEnabled".equals(item.getStatus())
                             || "statusFired".equals(item.getStatus()))
                .toList();

            if (processableItems.isEmpty()) {
                return "No processable work items (all items are in executing or terminal state).";
            }

            int processed = 0;
            int failed = 0;
            StringBuilder errors = new StringBuilder();

            // Use structured concurrency for parallel processing
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<WorkItemResult>awaitAllSuccessfulOrThrow())) {
                List<StructuredTaskScope.Subtask<WorkItemResult>> tasks = processableItems.stream()
                    .map(item -> scope.fork(() -> processWorkItemWithResult(item)))
                    .toList();

                scope.join();

                for (StructuredTaskScope.Subtask<WorkItemResult> task : tasks) {
                    WorkItemResult result = task.get();
                    if (result.success()) {
                        processed++;
                    } else {
                        failed++;
                        if (result.error() != null) {
                            errors.append("  - ").append(result.workItemId())
                                  .append(": ").append(result.error()).append("\n");
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Batch processing interrupted.";
            } catch (Exception e) {
                return "Batch processing failed: " + e.getMessage();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Batch processing complete.\n");
            sb.append("  Processed: ").append(processed).append("\n");
            sb.append("  Failed: ").append(failed).append("\n");
            if (errors.length() > 0) {
                sb.append("  Errors:\n").append(errors);
            }
            return sb.toString();
        }

        /**
         * Process a single work item and return a result record.
         *
         * <p>This method runs on a virtual thread as part of structured concurrency.</p>
         *
         * @param item the work item to process
         * @return result record indicating success or failure
         */
        private WorkItemResult processWorkItemWithResult(WorkItemRecord item) {
            try {
                ensureEngineConnection();

                String workItemId = item.getID();

                // Check out the work item
                String checkoutResult = interfaceBClient.checkOutWorkItem(workItemId, sessionHandle);
                if (checkoutResult == null || checkoutResult.contains("<failure>")) {
                    return new WorkItemResult(workItemId, false, "Checkout failed: " + checkoutResult);
                }

                // Check in with original data (autoprocess)
                String checkinResult = interfaceBClient.checkInWorkItem(
                    workItemId, item.getDataListString(), sessionHandle);

                if (checkinResult == null || !checkinResult.contains("success")) {
                    return new WorkItemResult(workItemId, false, "Checkin failed: " + checkinResult);
                }

                return new WorkItemResult(workItemId, true, null);

            } catch (Exception e) {
                return new WorkItemResult(item.getID(), false, e.getMessage());
            }
        }

        private String handleListSpecifications() throws IOException {
            List<SpecificationData> specs = interfaceBClient.getSpecificationList(sessionHandle);
            if (specs == null || specs.isEmpty()) {
                return "No workflow specifications currently loaded in the YAWL engine.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Loaded workflow specifications (").append(specs.size()).append("):\n\n");
            for (SpecificationData spec : specs) {
                YSpecificationID specId = spec.getID();
                sb.append("- ").append(specId.getIdentifier())
                    .append(" v").append(specId.getVersionAsString());
                if (spec.getName() != null) {
                    sb.append(" (").append(spec.getName()).append(")");
                }
                sb.append("\n  URI: ").append(specId.getUri());
                sb.append("\n  Status: ").append(spec.getStatus());
                if (spec.getDocumentation() != null) {
                    sb.append("\n  Docs: ").append(spec.getDocumentation());
                }
                sb.append("\n");
            }
            return sb.toString();
        }

        private String handleLaunchCase(String userText) throws IOException {
            String specId = extractIdentifier(userText);
            if (specId == null) {
                return "Please specify a workflow identifier to launch. "
                    + "Use 'list specifications' to see available workflows.";
            }

            YSpecificationID specID = new YSpecificationID(specId, "0.1", specId);
            String caseId = interfaceBClient.launchCase(specID, null, null, sessionHandle);

            if (caseId == null || caseId.contains("<failure>")) {
                return "Failed to launch workflow '" + specId + "': " + caseId;
            }

            return "Workflow launched successfully.\n"
                + "  Specification: " + specId + "\n"
                + "  Case ID: " + caseId + "\n"
                + "  Status: Running\n"
                + "Use 'status case " + caseId + "' to check progress.";
        }

        private String handleCaseQuery(String userText) throws IOException {
            String caseId = extractNumber(userText);
            if (caseId != null) {
                String state = interfaceBClient.getCaseState(caseId, sessionHandle);
                List<WorkItemRecord> items = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);

                StringBuilder sb = new StringBuilder();
                sb.append("Case ").append(caseId).append(":\n");
                sb.append("  State: ").append(state != null ? state : "unknown").append("\n");
                if (items != null && !items.isEmpty()) {
                    sb.append("  Work Items (").append(items.size()).append("):\n");
                    for (WorkItemRecord wir : items) {
                        sb.append("    - ").append(wir.getID())
                            .append(" [").append(wir.getStatus()).append("]")
                            .append(" Task: ").append(wir.getTaskID())
                            .append("\n");
                    }
                } else {
                    sb.append("  No active work items.\n");
                }
                return sb.toString();
            }

            String allCases = interfaceBClient.getAllRunningCases(sessionHandle);
            return "Running cases:\n" + (allCases != null ? allCases : "None");
        }

        private String handleWorkItemQuery(String userText) throws IOException {
            String caseId = extractNumber(userText);
            List<WorkItemRecord> items;
            if (caseId != null) {
                items = interfaceBClient.getWorkItemsForCase(caseId, sessionHandle);
            } else {
                items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);
            }

            if (items == null || items.isEmpty()) {
                return "No active work items" + (caseId != null ? " for case " + caseId : "") + ".";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Work Items (").append(items.size()).append("):\n\n");
            for (WorkItemRecord wir : items) {
                sb.append("- ID: ").append(wir.getID()).append("\n");
                sb.append("  Case: ").append(wir.getCaseID()).append("\n");
                sb.append("  Task: ").append(wir.getTaskID()).append("\n");
                sb.append("  Status: ").append(wir.getStatus()).append("\n");
                sb.append("  Spec: ").append(wir.getSpecURI()).append("\n\n");
            }
            return sb.toString();
        }

        private String handleCancelCase(String userText) throws IOException {
            String caseId = extractNumber(userText);
            if (caseId == null) {
                return "Please specify a case ID to cancel.";
            }

            String result = interfaceBClient.cancelCase(caseId, sessionHandle);
            if (result != null && result.contains("<failure>")) {
                return "Failed to cancel case " + caseId + ": " + result;
            }
            return "Case " + caseId + " cancelled successfully.";
        }

        private String extractTextFromMessage(Message message) {
            if (message == null || message.parts() == null) {
                throw new IllegalArgumentException("Message has no content parts");
            }
            StringBuilder text = new StringBuilder();
            for (Part<?> part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    text.append(textPart.text());
                }
            }
            if (text.length() == 0) {
                throw new IllegalArgumentException("Message contains no text parts");
            }
            return text.toString();
        }

        private String extractIdentifier(String text) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (("launch".equalsIgnoreCase(parts[i]) || "start".equalsIgnoreCase(parts[i]))
                        && i + 1 < parts.length) {
                    String next = parts[i + 1];
                    if (!"the".equalsIgnoreCase(next) && !"a".equalsIgnoreCase(next)
                            && !"workflow".equalsIgnoreCase(next)) {
                        return next;
                    }
                    if (i + 2 < parts.length) {
                        return parts[i + 2];
                    }
                }
            }
            String[] quoted = text.split("'");
            if (quoted.length >= 2) {
                return quoted[1];
            }
            return null;
        }

        private String extractNumber(String text) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d+)\\b").matcher(text);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }
    }

    /**
     * Record for work item processing results in batch operations.
     *
     * @param workItemId the work item identifier
     * @param success whether processing succeeded
     * @param error error message if processing failed, null otherwise
     */
    private record WorkItemResult(String workItemId, boolean success, String error) {}

    // =========================================================================
    // HTTP handling helpers
    // =========================================================================

    @FunctionalInterface
    private interface RestCallable {
        RestHandler.HTTPRestResponse call() throws Exception;
    }

    private void handleRestCall(HttpExchange exchange, RestCallable callable) throws IOException {
        try {
            RestHandler.HTTPRestResponse response = callable.call();
            String body = response.getBody();
            String contentType = response.getContentType();
            int statusCode = response.getStatusCode();

            byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", contentType);
            }
            exchange.sendResponseHeaders(statusCode, bodyBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bodyBytes);
            }
        } catch (Exception e) {
            byte[] err = ("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, err.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(err);
            }
        }
    }

    private void sendAuthenticationChallenge(HttpExchange exchange, A2AAuthenticationException authEx)
            throws IOException {
        String reason = authEx.getMessage() != null ? authEx.getMessage() : "Authentication required";
        String schemes = authEx.getSupportedSchemes();
        String safeReason = reason.replace("\"", "'");
        byte[] body = ("{\"error\":\"" + safeReason + "\"}").getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set(
            "WWW-Authenticate",
            "Bearer realm=\"YAWL A2A\", supported_schemes=\"" + schemes + "\"");
        exchange.sendResponseHeaders(401, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
        _logger.warn("A2A auth rejected [{}]: {}", exchange.getRemoteAddress(), reason);
    }

    private void sendForbidden(HttpExchange exchange, String reason) throws IOException {
        String safeReason = reason.replace("\"", "'");
        byte[] body = ("{\"error\":\"" + safeReason + "\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(403, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private void sendNotFound(HttpExchange exchange) throws IOException {
        byte[] resp = "{\"error\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(404, resp.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // =========================================================================
    // Handoff message handling
    // =========================================================================

    private void handleHandoffMessage(HttpExchange exchange, ServerCallContext callContext, String body) {
        // Process handoff asynchronously to avoid race condition
        Thread.ofVirtual().name("handoff-processor").start(() -> {
            try {
                io.a2a.spec.Message message = io.a2a.spec.Message.fromJson(body);

                String messageText = message.parts().stream()
                    .filter(part -> part instanceof io.a2a.spec.TextPart)
                    .map(part -> ((io.a2a.spec.TextPart) part).text())
                    .findFirst()
                    .orElse("");

                if (!messageText.startsWith("YAWL_HANDOFF:")) {
                    sendHandoffError(exchange, 400, "Not a handoff message");
                    return;
                }

                String workItemId = extractWorkItemIdFromHandoff(messageText);

                if (!callContext.getPrincipal().hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE) &&
                    !callContext.getPrincipal().hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
                    sendHandoffError(exchange, 403, "Insufficient permissions");
                    return;
                }

                HandoffToken token = validateHandoffToken(messageText);

                // Process handoff first before sending success response
                processHandoff(workItemId, token);

                // Only send success response after handoff processing completes successfully
                String response = "Work item " + workItemId + " handed off successfully and checkout completed.";
                byte[] resp = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }

            } catch (HandoffException e) {
                _logger.error("Handoff validation failed: {}", e.getMessage());
                try {
                    sendHandoffError(exchange, 400, "Handoff failed: " + e.getMessage());
                } catch (IOException ioe) {
                    _logger.error("Failed to send handoff error response", ioe);
                }
            } catch (Exception e) {
                _logger.error("Handoff processing failed: {}", e.getMessage());
                try {
                    sendHandoffError(exchange, 500, "Handoff failed: " + e.getMessage());
                } catch (IOException ioe) {
                    _logger.error("Failed to send handoff error response", ioe);
                }
            }
        });
    }

    private String extractWorkItemIdFromHandoff(String messageText) {
        String[] parts = messageText.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        throw new IllegalArgumentException("Invalid handoff message format");
    }

    private HandoffToken validateHandoffToken(String messageText) {
        try {
            // Extract JWT from message (assuming message format: "handoff:<jwt>")
            String[] parts = messageText.split(":", 2);
            if (parts.length < 2) {
                throw new HandoffException("Invalid handoff message format");
            }

            String jwt = parts[1];

            // Use HandoffProtocol to verify the token
            HandoffProtocol protocol = new HandoffProtocol();
            HandoffToken token = protocol.verifyHandoffToken(jwt);

            // Additional validation - check if token is still valid
            if (!token.isValid()) {
                throw new HandoffException("Handoff token has expired");
            }

            return token;
        } catch (HandoffException e) {
            throw e; // Re-throw handoff exceptions
        } catch (Exception e) {
            throw new HandoffException("Failed to validate handoff token: " + e.getMessage(), e);
        }
    }

    private void sendHandoffError(HttpExchange exchange, int statusCode, String message) throws IOException {
        byte[] resp = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, resp.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(resp);
        }
    }

    private void processHandoff(String workItemId, HandoffToken token) {
        try {
            _logger.info("Processing handoff for work item {} from agent {} to agent {}",
                workItemId, token.fromAgent(), token.toAgent());

            ensureEngineConnection();
            String rollbackResult = interfaceBClient.rollbackWorkItem(workItemId, sessionHandle);

            if (rollbackResult != null && rollbackResult.contains("<failure>")) {
                _logger.error("Failed to rollback work item {}: {}", workItemId, rollbackResult);
                throw new HandoffException("Failed to rollback work item: " + rollbackResult);
            }

            _logger.info("Handoff processed successfully for work item {}", workItemId);

        } catch (IOException e) {
            _logger.error("Error processing handoff: {}", e.getMessage());
            throw new HandoffException("Handoff processing failed: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private synchronized void ensureEngineConnection() throws IOException {
        if (sessionHandle != null) {
            String check = interfaceBClient.checkConnection(sessionHandle);
            if (check != null && !check.contains("<failure>")) {
                return;
            }
        }
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException("Failed to connect to YAWL engine: " + sessionHandle);
        }
    }

    private void initializeHandoffServices() {
        JwtAuthenticationProvider jwtProvider = JwtAuthenticationProvider.fromEnvironment();
        this.handoffProtocol = new HandoffProtocol(jwtProvider);
        _logger.info("Handoff services initialized");
    }

    private void disconnectFromEngine() {
        if (sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
            } catch (IOException e) {
                _logger.warn("Failed to disconnect from YAWL engine: {}", e.getMessage());
            }
            sessionHandle = null;
        }
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Entry point for running the YAWL A2A Server with virtual threads.
     *
     * <p>Environment variables:</p>
     * <ul>
     *   <li>{@code YAWL_ENGINE_URL} - YAWL engine base URL (required)</li>
     *   <li>{@code YAWL_USERNAME} - YAWL admin username (required)</li>
     *   <li>{@code YAWL_PASSWORD} - YAWL admin password (required)</li>
     *   <li>{@code A2A_PORT} - Port to run on (default: 8081)</li>
     *   <li>{@code A2A_GRACEFUL_SHUTDOWN_SECONDS} - Shutdown timeout (default: 30)</li>
     *   <li>{@code A2A_HTTP_CLIENT_TIMEOUT_SECONDS} - HTTP timeout (default: 60)</li>
     * </ul>
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        String engineUrl = getRequiredEnv("YAWL_ENGINE_URL",
            "Set it with: export YAWL_ENGINE_URL=http://localhost:8080/yawl");
        String username = getRequiredEnv("YAWL_USERNAME",
            "Set it with: export YAWL_USERNAME=admin");
        String password = getRequiredEnv("YAWL_PASSWORD",
            "Set it with: export YAWL_PASSWORD=YAWL");

        int port = parseIntEnv("A2A_PORT", 8081);
        int gracefulShutdown = parseIntEnv("A2A_GRACEFUL_SHUTDOWN_SECONDS", DEFAULT_GRACEFUL_SHUTDOWN_SECONDS);
        int httpTimeout = parseIntEnv("A2A_HTTP_CLIENT_TIMEOUT_SECONDS", DEFAULT_HTTP_CLIENT_TIMEOUT_SECONDS);

        A2AAuthenticationProvider authProvider = CompositeAuthenticationProvider.production();

        _logger.info("Starting YAWL A2A Server v{} with Java 25 virtual threads", SERVER_VERSION);
        _logger.info("Engine URL: {}", engineUrl);
        _logger.info("A2A Port: {}", port);
        _logger.info("Graceful shutdown: {}s", gracefulShutdown);
        _logger.info("HTTP timeout: {}s", httpTimeout);
        _logger.info("Auth schemes: {}", authProvider.scheme());

        try {
            VirtualThreadYawlA2AServer server = new VirtualThreadYawlA2AServer(
                engineUrl, username, password, port, gracefulShutdown, httpTimeout, authProvider);

            // Use virtual thread for shutdown hook
            Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual()
                    .name("yawl-a2a-shutdown-hook")
                    .unstarted(() -> {
                        _logger.info("Shutting down YAWL A2A Server...");
                        server.stop();
                    })
            );

            server.start();

            _logger.info("Server running. Send SIGTERM or press Ctrl+C to stop.");
            Thread.sleep(Long.MAX_VALUE);

        } catch (IOException e) {
            _logger.error("Failed to start A2A server: {}", e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getRequiredEnv(String name, String help) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(
                name + " environment variable is required.\n" + help);
        }
        return value;
    }

    private static int parseIntEnv(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value != null && !value.isEmpty()) {
            return SafeNumberParser.parseIntOrThrow(value, name + " environment variable");
        }
        return defaultValue;
    }
}
