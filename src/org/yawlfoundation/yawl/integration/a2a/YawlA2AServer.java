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
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.interfce.SpecificationData;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationException;
import org.yawlfoundation.yawl.integration.a2a.auth.A2AAuthenticationProvider;
import org.yawlfoundation.yawl.integration.a2a.auth.AuthenticatedPrincipal;
import org.yawlfoundation.yawl.integration.a2a.auth.CompositeAuthenticationProvider;
import org.yawlfoundation.yawl.integration.zai.ZaiFunctionService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Agent-to-Agent (A2A) Server for YAWL using the official A2A Java SDK.
 *
 * Exposes YAWL workflow engine capabilities as an A2A agent over HTTP REST transport.
 * Other A2A agents can discover YAWL's capabilities via the AgentCard and invoke
 * workflow operations by sending messages.
 *
 * <p><b>Security model:</b> Every request (except {@code /.well-known/agent.json})
 * must carry verifiable credentials. The server validates credentials via a
 * pluggable {@link A2AAuthenticationProvider} chain before dispatching any
 * workflow operation. An unauthenticated request receives HTTP 401 with a
 * {@code WWW-Authenticate} challenge. There is no fallback anonymous identity.
 *
 * <p>Supported authentication schemes (configured via environment variables):
 * <ul>
 *   <li>mTLS with SPIFFE X.509 SVID ({@code A2A_SPIFFE_TRUST_DOMAIN})</li>
 *   <li>JWT Bearer token, HS256 ({@code A2A_JWT_SECRET})</li>
 *   <li>API Key, HMAC-SHA256 ({@code A2A_API_KEY_MASTER} + {@code A2A_API_KEY})</li>
 * </ul>
 *
 * Skills exposed:
 *   - launch_workflow: Launch a workflow case from a specification
 *   - query_workflows: List loaded specifications and running cases
 *   - manage_workitems: Get and complete work items
 *   - cancel_workflow: Cancel a running workflow case
 *
 * The server starts on a configurable port (default 8081) and exposes:
 *   - GET  /.well-known/agent.json  - Agent card discovery (no auth required)
 *   - POST /                        - Message send (A2A REST protocol, auth required)
 *   - GET  /tasks/{id}              - Get task status (auth required)
 *   - POST /tasks/{id}/cancel       - Cancel a task (auth required)
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlA2AServer {

    private static final String SERVER_VERSION = "5.2.0";

    private final String yawlEngineUrl;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private final int port;
    private final ZaiFunctionService zaiFunctionService;
    private final A2AAuthenticationProvider authProvider;
    private HttpServer httpServer;
    private ExecutorService executorService;
    private String sessionHandle;

    /**
     * Construct a YAWL A2A Server with an explicit authentication provider.
     *
     * @param yawlEngineUrl  base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username       YAWL admin username
     * @param password       YAWL admin password
     * @param port           port to run the A2A server on
     * @param authProvider   authentication provider that validates every inbound
     *                       request; must not be null
     */
    public YawlA2AServer(String yawlEngineUrl,
                         String username,
                         String password,
                         int port,
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
        if (authProvider == null) {
            throw new IllegalArgumentException(
                "An authentication provider is required. "
                + "Use CompositeAuthenticationProvider.production() for the recommended stack.");
        }

        this.yawlEngineUrl = yawlEngineUrl;
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(
            yawlEngineUrl + "/ib");
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.port = port;
        this.authProvider = authProvider;

        String zaiApiKey = System.getenv("ZAI_API_KEY");
        if (zaiApiKey != null && !zaiApiKey.isEmpty()) {
            this.zaiFunctionService = new ZaiFunctionService(
                zaiApiKey, yawlEngineUrl, username, password);
        } else {
            this.zaiFunctionService = null;
        }
    }

    /**
     * Build and start the A2A server on the configured port.
     *
     * Uses virtual threads for HTTP request handling and A2A message processing.
     * Virtual threads provide better scalability for I/O-bound operations like
     * HTTP requests, YAWL engine calls, and agent message processing.
     *
     * @throws IOException if the HTTP server cannot bind to the port
     */
    public void start() throws IOException {
        AgentCard agentCard = buildAgentCard();
        executorService = Executors.newVirtualThreadPerTaskExecutor();

        InMemoryTaskStore taskStore = new InMemoryTaskStore();
        MainEventBus mainEventBus = new MainEventBus();
        InMemoryQueueManager queueManager = new InMemoryQueueManager(
            taskStore, mainEventBus);
        InMemoryPushNotificationConfigStore pushStore =
            new InMemoryPushNotificationConfigStore();
        MainEventBusProcessor busProcessor = new MainEventBusProcessor(
            mainEventBus, taskStore, null, queueManager);
        busProcessor.ensureStarted();

        YawlAgentExecutor agentExecutor = new YawlAgentExecutor();
        DefaultRequestHandler requestHandler = DefaultRequestHandler.create(
            agentExecutor, taskStore, queueManager, pushStore,
            busProcessor, executorService, executorService);

        RestHandler restHandler = new RestHandler(
            agentCard, requestHandler, executorService);

        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(executorService);

        // Agent card endpoint: no authentication required (public discovery)
        httpServer.createContext("/.well-known/agent.json", exchange -> {
            handleRestCall(exchange, () -> restHandler.getAgentCard());
        });

        // All other endpoints: authentication required
        httpServer.createContext("/", exchange -> {
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

            // Reject an expired principal (e.g. certificate whose notAfter
            // has passed since the TLS handshake).
            if (principal.isExpired()) {
                sendAuthenticationChallenge(exchange,
                    new A2AAuthenticationException(
                        "Credential has expired. Obtain a new credential and retry.",
                        authProvider.scheme()));
                return;
            }

            ServerCallContext callContext = new ServerCallContext(
                principal, new HashMap<>(), Collections.emptySet());

            if ("POST".equals(method) && "/".equals(path)) {
                if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_LAUNCH)
                        && !principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
                        && !principal.hasPermission(AuthenticatedPrincipal.PERM_WORKITEM_MANAGE)
                        && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
                    sendForbidden(exchange,
                        "Insufficient permissions to invoke A2A message endpoint.");
                    return;
                }
                String body = readRequestBody(exchange);
                handleRestCall(exchange, () ->
                    restHandler.sendMessage(callContext, null, body));

            } else if ("GET".equals(method) && path.startsWith("/tasks/")) {
                if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_QUERY)
                        && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
                    sendForbidden(exchange,
                        "Insufficient permissions to query task status.");
                    return;
                }
                String taskId = path.substring("/tasks/".length());
                if (taskId.endsWith("/cancel")) {
                    exchange.sendResponseHeaders(405, -1);
                } else {
                    handleRestCall(exchange, () ->
                        restHandler.getTask(callContext, taskId, null, null));
                }

            } else if ("POST".equals(method) && path.matches("/tasks/.+/cancel")) {
                if (!principal.hasPermission(AuthenticatedPrincipal.PERM_WORKFLOW_CANCEL)
                        && !principal.hasPermission(AuthenticatedPrincipal.PERM_ALL)) {
                    sendForbidden(exchange,
                        "Insufficient permissions to cancel a task.");
                    return;
                }
                String taskId = path.replace("/tasks/", "").replace("/cancel", "");
                handleRestCall(exchange, () ->
                    restHandler.cancelTask(callContext, taskId, null));

            } else {
                byte[] resp = "{\"error\":\"Not Found\"}"
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(404, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });

        httpServer.start();
        System.out.println("YAWL A2A Server v" + SERVER_VERSION
            + " started on port " + port);
        System.out.println("Agent card: http://localhost:" + port
            + "/.well-known/agent.json");
        System.out.println("Authentication: " + authProvider.scheme());
    }

    /**
     * Stop the A2A server.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(2);
            httpServer = null;
        }
        if (executorService != null) {
            executorService.shutdown();
            executorService = null;
        }
        disconnectFromEngine();
        System.out.println("YAWL A2A Server stopped");
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return httpServer != null;
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
                + "and orchestrating complex business processes.")
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
                        + "workflow cases.")
                    .tags(List.of("workflow", "workitem", "task", "complete"))
                    .examples(List.of(
                        "Show work items for case 42",
                        "Complete work item 42:ReviewOrder with approved status"
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
                    .build()
            ))
            .build();
    }

    // =========================================================================
    // Agent Executor - handles A2A message processing with YAWL engine
    // =========================================================================

    private class YawlAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, AgentEmitter emitter)
                throws A2AError {
            emitter.startWork();

            try {
                Message userMessage = context.getMessage();
                String userText = extractTextFromMessage(userMessage);

                String response = processWorkflowRequest(userText);

                emitter.complete(A2A.toAgentMessage(response));
            } catch (IOException e) {
                emitter.fail(A2A.toAgentMessage(
                    "YAWL engine error: " + e.getMessage()));
            } catch (Exception e) {
                emitter.fail(A2A.toAgentMessage(
                    "Processing error: " + e.getMessage()));
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter)
                throws A2AError {
            String taskId = context.getTaskId();
            try {
                ensureEngineConnection();
                String result = interfaceBClient.cancelCase(taskId, sessionHandle);
                if (result != null && result.contains("<failure>")) {
                    emitter.fail(A2A.toAgentMessage(
                        "Failed to cancel case: " + result));
                } else {
                    emitter.cancel(A2A.toAgentMessage(
                        "Case " + taskId + " cancelled successfully"));
                }
            } catch (IOException e) {
                emitter.fail(A2A.toAgentMessage(
                    "Failed to cancel: " + e.getMessage()));
            }
        }

        private String processWorkflowRequest(String userText) throws IOException {
            if (zaiFunctionService != null) {
                try {
                    return zaiFunctionService.processWithFunctions(userText);
                } catch (Exception e) {
                    return "Z.AI processing error: " + e.getMessage();
                }
            }
            ensureEngineConnection();
            String lower = userText.toLowerCase().trim();

            if (lower.contains("list") && (lower.contains("spec")
                    || lower.contains("workflow"))) {
                return handleListSpecifications();
            }

            if (lower.contains("launch") || lower.contains("start")) {
                return handleLaunchCase(userText);
            }

            if (lower.contains("status") || lower.contains("case")) {
                return handleCaseQuery(userText);
            }

            if (lower.contains("work item") || lower.contains("workitem")
                    || lower.contains("task")) {
                return handleWorkItemQuery(userText);
            }

            if (lower.contains("cancel") || lower.contains("stop")) {
                return handleCancelCase(userText);
            }

            return handleListSpecifications();
        }

        private String handleListSpecifications() throws IOException {
            List<SpecificationData> specs = interfaceBClient.getSpecificationList(
                sessionHandle);
            if (specs == null || specs.isEmpty()) {
                return "No workflow specifications currently loaded in the YAWL engine.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Loaded workflow specifications (").append(specs.size())
                .append("):\n\n");
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
            String caseId = interfaceBClient.launchCase(
                specID, null, null, sessionHandle);

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
                List<WorkItemRecord> items = interfaceBClient.getWorkItemsForCase(
                    caseId, sessionHandle);

                StringBuilder sb = new StringBuilder();
                sb.append("Case ").append(caseId).append(":\n");
                sb.append("  State: ").append(state != null ? state : "unknown")
                    .append("\n");
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
                items = interfaceBClient.getWorkItemsForCase(
                    caseId, sessionHandle);
            } else {
                items = interfaceBClient.getCompleteListOfLiveWorkItems(
                    sessionHandle);
            }

            if (items == null || items.isEmpty()) {
                return "No active work items"
                    + (caseId != null ? " for case " + caseId : "") + ".";
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
                throw new IllegalArgumentException(
                    "Message contains no text parts");
            }
            return text.toString();
        }

        private String extractIdentifier(String text) {
            String[] parts = text.split("\\s+");
            for (int i = 0; i < parts.length; i++) {
                if (("launch".equalsIgnoreCase(parts[i])
                        || "start".equalsIgnoreCase(parts[i]))
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
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d+)\\b")
                .matcher(text);
            if (m.find()) {
                return m.group(1);
            }
            return null;
        }
    }

    // =========================================================================
    // HTTP handling helpers
    // =========================================================================

    @FunctionalInterface
    private interface RestCallable {
        RestHandler.HTTPRestResponse call() throws Exception;
    }

    private void handleRestCall(HttpExchange exchange, RestCallable callable)
            throws IOException {
        try {
            RestHandler.HTTPRestResponse response = callable.call();
            String body = response.getBody();
            String contentType = response.getContentType();
            int statusCode = response.getStatusCode();

            byte[] bodyBytes = body != null
                ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
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

    /**
     * Send an HTTP 401 response with a {@code WWW-Authenticate} challenge.
     *
     * <p>The challenge includes all supported schemes so clients can choose
     * the appropriate one. The response body contains the end-user-safe
     * rejection reason from the authentication exception.
     */
    private void sendAuthenticationChallenge(HttpExchange exchange,
                                             A2AAuthenticationException authEx)
            throws IOException {
        String reason = authEx.getMessage() != null
            ? authEx.getMessage()
            : "Authentication required";
        String schemes = authEx.getSupportedSchemes();

        // Escape double-quotes in the reason before embedding in JSON
        String safeReason = reason.replace("\"", "'");
        byte[] body = ("{\"error\":\"" + safeReason + "\"}")
            .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set(
            "WWW-Authenticate",
            "Bearer realm=\"YAWL A2A\", supported_schemes=\"" + schemes + "\"");
        exchange.sendResponseHeaders(401, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }

        // Log the rejection server-side (not to the client)
        System.err.println("A2A auth rejected [" + exchange.getRemoteAddress() + "]: "
            + reason);
    }

    /**
     * Send an HTTP 403 Forbidden response when a principal lacks the required
     * permission for the requested operation.
     */
    private void sendForbidden(HttpExchange exchange, String reason) throws IOException {
        String safeReason = reason.replace("\"", "'");
        byte[] body = ("{\"error\":\"" + safeReason + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(403, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private void ensureEngineConnection() throws IOException {
        if (sessionHandle != null) {
            String check = interfaceBClient.checkConnection(sessionHandle);
            if (check != null && !check.contains("<failure>")) {
                return;
            }
        }
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException(
                "Failed to connect to YAWL engine: " + sessionHandle);
        }
    }

    private void disconnectFromEngine() {
        if (sessionHandle != null) {
            try {
                interfaceBClient.disconnect(sessionHandle);
            } catch (IOException e) {
                System.err.println(
                    "Warning: failed to disconnect from YAWL engine: "
                    + e.getMessage());
            }
            sessionHandle = null;
        }
    }

    /**
     * Entry point for running the YAWL A2A Server.
     *
     * Environment variables:
     *   YAWL_ENGINE_URL          - YAWL engine base URL (default: http://localhost:8080/yawl)
     *   YAWL_USERNAME            - YAWL admin username (default: admin)
     *   YAWL_PASSWORD            - YAWL admin password
     *   A2A_PORT                 - Port to run on (default: 8081)
     *
     * Authentication (at least one set required):
     *   A2A_JWT_SECRET           - JWT HMAC-SHA256 key (min 32 chars)
     *   A2A_JWT_ISSUER           - Optional JWT issuer claim
     *   A2A_API_KEY_MASTER       - API key HMAC master key (min 16 chars)
     *   A2A_API_KEY              - Default API key to auto-register
     *   A2A_SPIFFE_TRUST_DOMAIN  - SPIFFE trust domain (default: yawl.cloud)
     */
    public static void main(String[] args) {
        String engineUrl = System.getenv("YAWL_ENGINE_URL");
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalStateException(
                "YAWL_ENGINE_URL environment variable is required.\n" +
                "Set it with: export YAWL_ENGINE_URL=http://localhost:8080/yawl");
        }

        String username = System.getenv("YAWL_USERNAME");
        if (username == null || username.isEmpty()) {
            throw new IllegalStateException(
                "YAWL_USERNAME environment variable is required.\n" +
                "Set it with: export YAWL_USERNAME=admin");
        }

        String password = System.getenv("YAWL_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException(
                "YAWL_PASSWORD environment variable is required.\n" +
                "Set it with: export YAWL_PASSWORD=YAWL");
        }

        int port = 8081;
        String portEnv = System.getenv("A2A_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            port = Integer.parseInt(portEnv);
        }

        // Build the production authentication provider stack.
        // Throws IllegalStateException if no provider can be configured.
        A2AAuthenticationProvider authProvider =
            CompositeAuthenticationProvider.production();

        System.out.println("Starting YAWL A2A Server v" + SERVER_VERSION);
        System.out.println("Engine URL: " + engineUrl);
        System.out.println("A2A Port: " + port);
        System.out.println("Auth schemes: " + authProvider.scheme());

        try {
            YawlA2AServer server = new YawlA2AServer(
                engineUrl, username, password, port, authProvider);

            Runtime.getRuntime().addShutdownHook(
                Thread.ofVirtual().unstarted(() -> {
                    System.out.println("Shutting down YAWL A2A Server...");
                    server.stop();
                })
            );

            server.start();

            System.out.println("Press Ctrl+C to stop");
            Thread.sleep(Long.MAX_VALUE);
        } catch (IOException e) {
            System.err.println("Failed to start A2A server: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
