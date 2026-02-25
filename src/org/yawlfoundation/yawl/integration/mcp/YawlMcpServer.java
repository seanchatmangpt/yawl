package org.yawlfoundation.yawl.integration.mcp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.engine.interfce.interfaceA.InterfaceA_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.resource.MermaidStateResource;
import org.yawlfoundation.yawl.integration.mcp.resource.YawlResourceProvider;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import org.yawlfoundation.yawl.integration.factory.ConversationalWorkflowFactory;
import org.yawlfoundation.yawl.integration.mcp.spec.CancellationAuditorTools;
import org.yawlfoundation.yawl.integration.mcp.spec.CaseDivergenceTools;
import org.yawlfoundation.yawl.integration.mcp.spec.ComplexityBoundTools;
import org.yawlfoundation.yawl.integration.mcp.spec.ConstructCoordinationTools;
import org.yawlfoundation.yawl.integration.mcp.spec.CounterfactualSimulatorTools;
import org.yawlfoundation.yawl.integration.mcp.spec.DataLineageTools;
import org.yawlfoundation.yawl.integration.mcp.spec.DeadPathAnalyzerTools;
import org.yawlfoundation.yawl.integration.mcp.spec.LivenessOracleTools;
import org.yawlfoundation.yawl.integration.mcp.spec.OntologyDrivenToolFactory;
import org.yawlfoundation.yawl.integration.mcp.spec.SoundnessProverTools;
import org.yawlfoundation.yawl.integration.mcp.spec.TemporalAnomalySpecification;
import org.yawlfoundation.yawl.integration.mcp.spec.TemporalPressureTools;
import org.yawlfoundation.yawl.integration.mcp.spec.WorkflowComplexitySpecification;
import org.yawlfoundation.yawl.integration.mcp.spec.WorkflowDiffSpecification;
import org.yawlfoundation.yawl.integration.mcp.spec.WorkflowGenomeSpecification;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlCompletionSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlFactoryToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlPromptSpecifications;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlToolSpecifications;
import org.yawlfoundation.yawl.integration.mcp.timeline.CaseTimelineSpecification;

/**
 * Model Context Protocol (MCP) Server for YAWL using the official MCP Java SDK v1 (1.0.0-RC1).
 *
 * Implements MCP 2025-11-25 specification with full capabilities over STDIO transport.
 *
 * Tools: Launch/cancel cases, get case status, list specifications, get/complete/checkout/checkin
 *   work items, get specification data/XML/schema, get running cases, upload/unload specifications,
 *   suspend/resume cases, skip work items (YawlToolSpecifications). Plus SPARQL CONSTRUCT
 *   coordination tools for Petri-net token routing at zero inference cost (ConstructCoordinationTools).
 *
 * Resources (3 static):
 *   - yawl://specifications - All loaded specifications
 *   - yawl://cases - All running cases
 *   - yawl://workitems - All live work items
 *
 * Resource Templates (4 parameterized):
 *   - yawl://cases/{caseId} - Specific case state and work items
 *   - yawl://cases/{caseId}/data - Specific case variable data
 *   - yawl://workitems/{workItemId} - Specific work item details
 *   - yawl://cases/{caseId}/mermaid - Live Mermaid flowchart of Petri-net token positions
 *
 * Prompts (4):
 *   - workflow_analysis - Analyze a workflow specification
 *   - task_completion_guide - Guide for completing a work item
 *   - case_troubleshooting - Diagnose issues with a workflow case
 *   - workflow_design_review - Review specification for best practices
 *
 * Completions (3):
 *   - workflow_analysis prompt: auto-complete spec identifiers
 *   - task_completion_guide prompt: auto-complete work item IDs
 *   - yawl://cases/{caseId} resource: auto-complete case IDs
 *
 * Logging: Structured MCP log notifications for tool execution, errors, and server events.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class YawlMcpServer {

    private static final String SERVER_NAME = "yawl-mcp-server";
    private static final String SERVER_VERSION = "6.0.0";

    private final String yawlEngineUrl;
    private final InterfaceB_EnvironmentBasedClient interfaceBClient;
    private final InterfaceA_EnvironmentBasedClient interfaceAClient;
    private final String yawlUsername;
    private final String yawlPassword;
    private final McpLoggingHandler loggingHandler;
    private McpSyncServer mcpServer;
    private String sessionHandle;

    /**
     * Construct a YAWL MCP Server with YAWL engine connection parameters.
     *
     * @param yawlEngineUrl base URL of YAWL engine (e.g. http://localhost:8080/yawl)
     * @param username YAWL admin username
     * @param password YAWL admin password
     */
    public YawlMcpServer(String yawlEngineUrl, String username, String password) {
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

        this.yawlEngineUrl = yawlEngineUrl;
        this.interfaceBClient = new InterfaceB_EnvironmentBasedClient(
                yawlEngineUrl + "/ib");
        this.interfaceAClient = new InterfaceA_EnvironmentBasedClient(
                yawlEngineUrl + "/ia");
        this.yawlUsername = username;
        this.yawlPassword = password;
        this.loggingHandler = new McpLoggingHandler();
    }

    /**
     * Build and start the MCP server using the official SDK v1 with STDIO transport.
     *
     * Connects to the YAWL engine, registers all MCP capabilities (tools, resources,
     * resource templates, prompts, completions, logging), and starts the server.
     * This method blocks until the server is shut down.
     *
     * @throws IOException if connection to the YAWL engine fails
     */
    public void start() throws IOException {
        connectToEngine();

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
        StdioServerTransportProvider transportProvider =
            new StdioServerTransportProvider(jsonMapper);

        // Core workflow tools
        var workflowTools = YawlToolSpecifications.createAll(
            interfaceBClient, interfaceAClient, sessionHandle);
        var allTools = new ArrayList<>(workflowTools);

        // Conversational workflow factory tools (NL-to-workflow)
        ConversationalWorkflowFactory workflowFactory = new ConversationalWorkflowFactory(
            org.yawlfoundation.yawl.integration.zai.SpecificationGenerator.create(),
            interfaceAClient,
            interfaceBClient,
            new org.yawlfoundation.yawl.integration.processmining.ProcessMiningFacade(
                yawlEngineUrl, yawlUsername, yawlPassword),
            sessionHandle
        );
        allTools.addAll(YawlFactoryToolSpecifications.createAll(workflowFactory));

        // CONSTRUCT coordination tools (Petri-net token routing, zero inference cost)
        var constructTools = ConstructCoordinationTools.createAll(interfaceBClient, sessionHandle);
        allTools.addAll(constructTools);

        // Formal Petri-net analysis tools
        allTools.add(LivenessOracleTools.create(interfaceBClient, sessionHandle));
        allTools.add(CounterfactualSimulatorTools.create(interfaceBClient, sessionHandle));
        allTools.add(DeadPathAnalyzerTools.create(interfaceBClient, sessionHandle));
        allTools.add(ComplexityBoundTools.create(interfaceBClient, sessionHandle));
        allTools.add(SoundnessProverTools.create(interfaceBClient, sessionHandle));
        allTools.add(TemporalPressureTools.create(interfaceBClient, sessionHandle));
        allTools.add(DataLineageTools.create(interfaceBClient, sessionHandle));
        allTools.add(CancellationAuditorTools.create(interfaceBClient, sessionHandle));
        allTools.add(CaseDivergenceTools.create(interfaceBClient, sessionHandle));
        int formalToolCount = 9;

        // Blue-ocean innovation tools (always loaded — no external service dependency)
        allTools.addAll(WorkflowGenomeSpecification.createAll(
            interfaceBClient, interfaceAClient, sessionHandle));
        allTools.add(TemporalAnomalySpecification.createTemporalAnomalySentinelTool(
            interfaceBClient, sessionHandle));
        allTools.addAll(CaseTimelineSpecification.createAll(
            interfaceBClient, sessionHandle));
        allTools.addAll(WorkflowDiffSpecification.createAll(
            interfaceBClient, interfaceAClient, sessionHandle));
        allTools.addAll(WorkflowComplexitySpecification.createAll(
            interfaceBClient, interfaceAClient, sessionHandle));

        // Ontology-derived tools from Rust/Oxigraph service (optional, graceful degradation)
        int ontologyToolCount = 0;
        try {
            List<io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification> ontologyTools =
                OntologyDrivenToolFactory.createAll(interfaceBClient, interfaceAClient, sessionHandle);
            allTools.addAll(ontologyTools);
            ontologyToolCount = ontologyTools.size();
        } catch (Exception e) {
            System.err.println("WARN [YawlMcpServer] Ontology service unavailable — "
                + "ontology-derived tools not loaded: " + e.getMessage());
        }

        int workflowToolCount = workflowTools.size();
        int constructToolCount = constructTools.size();

        mcpServer = McpServer.sync(transportProvider)
            .serverInfo(SERVER_NAME, SERVER_VERSION)
            .capabilities(YawlServerCapabilities.full())
            .instructions("""
                YAWL Workflow Engine MCP Server v6.0.0.

                Use tools to launch and manage workflow cases, query and upload specifications,
                checkout and complete work items. Resources provide read-only access to
                specifications, cases, and work items. Prompts guide workflow analysis,
                task completion, troubleshooting, and design review.

                CONSTRUCT coordination tools expose the Petri net token-marking model directly:
                routing decisions cost 0 inference tokens — answered by formal workflow
                semantics, not LLM inference. Tool schemas are SPARQL CONSTRUCT outputs
                derived from the workflow specification, not hand-authored.

                Capabilities: %d tools (%d workflow + %d CONSTRUCT coordination + %d formal Petri-net + %d ontology-derived),
                3 static resources, 4 resource templates, 4 prompts, 3 completions,
                logging (MCP 2025-11-25 compliant).

                Formal Petri-net tools (zero inference tokens):
                  yawl_prove_liveness            — BFS reachability: LIVE / AT_RISK / DEADLOCKED verdict
                  yawl_simulate_transition       — counterfactual token firing, zero side effects
                  yawl_analyze_dead_paths        — zombie path detection across all running cases
                  yawl_compute_structural_bounds — min/max completion steps + cyclomatic complexity
                  yawl_prove_soundness           — formal soundness: option-to-complete, proper-completion, no-dead-tasks
                  yawl_analyze_temporal_pressure — time-dimension heat map: expired timers, age outliers, urgency ranking
                  yawl_trace_data_lineage        — XQuery data flow graph: producers, consumers, orphans, dangling refs
                  yawl_audit_cancellation_regions — cancellation blast radius: mutual cancel, orphan cancel, live victims
                  yawl_analyze_case_divergence   — cross-case cohort analysis: divergence index, outlier cases, split attribution
                  yawl://cases/{caseId}/mermaid  — live Mermaid flowchart of token positions
                """.formatted(allTools.size(), workflowToolCount, constructToolCount, formalToolCount, ontologyToolCount))
            .tools(allTools)
            .resources(YawlResourceProvider.createAllResources(
                interfaceBClient, sessionHandle))
            .resourceTemplates(buildAllResourceTemplates(interfaceBClient, sessionHandle))
            .prompts(YawlPromptSpecifications.createAll(
                interfaceBClient, () -> sessionHandle))
            .completions(YawlCompletionSpecifications.createAll(
                interfaceBClient, sessionHandle))
            .build();

        loggingHandler.info(mcpServer, "YAWL MCP Server started");
        System.err.println("YAWL MCP Server v" + SERVER_VERSION + " started on STDIO transport");
        System.err.println("Capabilities: " + allTools.size() + " tools ("
            + workflowToolCount + " workflow + " + constructToolCount + " CONSTRUCT + "
            + formalToolCount + " formal Petri-net + " + ontologyToolCount + " ontology-derived), "
            + "3 resources, 4 resource templates, 4 prompts, 3 completions, logging");
    }

    private List<io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification>
            buildAllResourceTemplates(InterfaceB_EnvironmentBasedClient client, String session) {
        var templates = new ArrayList<>(
            YawlResourceProvider.createAllResourceTemplates(client, session));
        templates.add(MermaidStateResource.create(client, session));
        return templates;
    }

    /**
     * Stop the MCP server gracefully.
     */
    public void stop() {
        if (mcpServer != null) {
            loggingHandler.info(mcpServer, "YAWL MCP Server shutting down");
            mcpServer.closeGracefully();
            mcpServer = null;
        }
        disconnectFromEngine();
    }

    /**
     * Check if server has been built and is running.
     *
     * @return true if the MCP server is active
     */
    public boolean isRunning() {
        return mcpServer != null;
    }

    /**
     * Get the underlying MCP sync server instance.
     * Useful for sending log notifications from external code.
     *
     * @return the MCP sync server, or null if not started
     */
    public McpSyncServer getMcpServer() {
        return mcpServer;
    }

    /**
     * Get the logging handler for sending structured MCP log notifications.
     *
     * @return the logging handler
     */
    public McpLoggingHandler getLoggingHandler() {
        return loggingHandler;
    }

    // =========================================================================
    // YAWL Engine connection management
    // =========================================================================

    private void connectToEngine() throws IOException {
        sessionHandle = interfaceBClient.connect(yawlUsername, yawlPassword);
        if (sessionHandle == null || sessionHandle.contains("<failure>")) {
            throw new IOException(
                "Failed to connect to YAWL engine. " +
                "Verify the engine is running and credentials are correct. " +
                "Response: " + sessionHandle);
        }
        System.err.println("Connected to YAWL engine (session established)");
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
     * Entry point for running the YAWL MCP Server.
     *
     * Reads configuration from environment variables:
     *   YAWL_ENGINE_URL - YAWL engine base URL (required, e.g. http://localhost:8080/yawl)
     *   YAWL_USERNAME   - YAWL admin username (required)
     *   YAWL_PASSWORD   - YAWL admin password (required)
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
                "YAWL_PASSWORD environment variable is required. " +
                "See SECURITY.md for credential configuration procedures.");
        }

        System.err.println("Starting YAWL MCP Server v" + SERVER_VERSION);
        System.err.println("Engine URL: " + engineUrl);
        System.err.println("Transport: STDIO (official MCP SDK v1)");

        YawlMcpServer server = new YawlMcpServer(engineUrl, username, password);

        Runtime.getRuntime().addShutdownHook(
            Thread.ofVirtual().unstarted(() -> {
                System.err.println("Shutting down YAWL MCP Server...");
                server.stop();
            })
        );

        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start YAWL MCP Server: " + e.getMessage());
            throw new RuntimeException(
                "YAWL MCP Server startup failed. " +
                "Ensure the YAWL engine is running at " + engineUrl + " and credentials are valid.",
                e);
        }
    }
}
