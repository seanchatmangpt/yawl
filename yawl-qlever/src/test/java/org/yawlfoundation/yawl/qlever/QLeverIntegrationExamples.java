/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLOntology;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YFlow;
import org.yawlfoundation.yawl.elements.YRole;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.engine.YWorkItemCompletionMessage;
import org.yawlfoundation.yawl.qlever.sparql.SparqlResult;
import org.yawlfoundation.yawl.qlever.sparqlQLeverConfiguration;
import org.yawlfoundation.yawl.qlever.sparqlQLeverEngine;
import org.yawlfoundation.yawl.qlever.sparqlQLeverResult;
import org.yawlfoundation.yawl.qlever.queries.YAWLQueryTranslator;
import org.yawlfoundation.yawl.qlever.queries.WorkflowPathFinder;
import org.yawlfoundation.yawl.qlever.queries.AnalyticsQuery;
import org.yawlfoundation.yawl.qlever.util.RDFUtil;
import org.yawlfoundation.yawl.qlever.util.SparqlUtil;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration examples showing how to use QLever Embedded with YAWL components.
 * This class demonstrates practical patterns for integrating QLever with:
 * - AgentMarketplace
 * - Workflow Analytics
 * - Custom SPARQL Engine Configuration
 * - Combining with OxigraphSparqlEngine
 * - YAWL MCP/A2A Servers
 */
public class QLeverIntegrationExamples {

    private QLeverEmbedded qlever;
    private YAWLOntology ontology;
    private YAWLServiceGateway serviceGateway;
    private YWorkItemRepository workItemRepository;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize QLever embedded engine
        qlever = new QLeverEmbedded();
        qlever.initialize();

        // Load YAWL ontology
        ontology = YAWLOntology.getInstance();
        ontology.loadOntology("http://www.yawlfoundation.org/yawl");

        // Initialize YAWL components
        serviceGateway = new YAWLServiceGateway();
        workItemRepository = new YWorkItemRepository();

        // Register namespaces
        qlever.registerNamespace("yawl", "http://www.yawlfoundation.org/yawl");
        qlever.registerNamespace("bpmn", "http://www.omg.org/spec/BPMN/20100524/MODEL");
        qlever.registerNamespace("time", "http://www.w3.org/2006/time#");
        qlever.registerNamespace("perf", "http://example.org/performance#");

        // Load sample workflow data
        loadSampleWorkflowData();
    }

    /**
     * Example 1: AgentMarketplace Integration
     * Demonstrates using QLever for agent discovery and matching
     */
    @Test
    void testAgentMarketplaceIntegration() throws Exception {
        AgentMarketplaceIntegration agentIntegration = new AgentMarketplaceIntegration();

        // Create test service requests
        List<ServiceRequest> requests = createTestServiceRequests();

        // Test agent discovery
        List<Agent> matchingAgents = agentIntegration.findMatchingAgents(requests.get(0));
        assertFalse(matchingAgents.isEmpty(), "Should find matching agents");

        // Test workload analytics
        AgentWorkloadAnalytics analytics = agentIntegration.getWorkloadAnalytics();
        assertTrue(analytics.getTotalTasks() > 0, "Should have workload data");
    }

    class AgentMarketplaceIntegration {

        private final QLeverEmbedded qlever;
        private final List<Agent> agents = new ArrayList<>();

        public AgentMarketplaceIntegration() {
            this.qlever = QLeverIntegrationExamples.this.qlever;
            initializeAgents();
        }

        private void initializeAgents() {
            // Initialize sample agents with QLever data
            agents.add(new Agent("agent-1", "LoanProcessor", Arrays.asList("loan-approval", "risk-assessment")));
            agents.add(new Agent("agent-2", "DocumentReviewer", Arrays.asList("document-verification", "compliance-check")));
            agents.add(new Agent("agent-3", "CustomerService", Arrays.asList("customer-query", "issue-resolution")));

            // Store agents as RDF triples
            for (Agent agent : agents) {
                qlever.addTriple(agent.getUri(), "a", "amp:Agent");
                qlever.addTriple(agent.getUri(), "amp:hasName", agent.getName());
                qlever.addTriple(agent.getUri(), "amp:availability", "AVAILABLE");

                for (String skill : agent.getSkills()) {
                    qlever.addTriple(agent.getUri(), "amp:hasCapability", skill);
                }
            }
        }

        public List<Agent> findMatchingAgents(ServiceRequest request) {
            String query = String.format(
                "PREFIX amp: <http://example.org/agent-marketplace#>\n" +
                "SELECT ?agent ?score WHERE {\n" +
                "  ?agent a amp:Agent ;\n" +
                "         amp:hasCapability ?capability ;\n" +
                "         amp:availability 'AVAILABLE' .\n" +
                "  ?capability amp:matchesService '%s' .\n" +
                "  BIND(100 - COUNT(?capability) * 10 AS ?score)\n" +
                "} ORDER BY DESC(?score) LIMIT 10",
                request.getType()
            );

            SparqlResult result = qlever.executeSparql(query);
            List<Agent> matches = new ArrayList<>();

            for (Map<String, String> row : result.getResults()) {
                String agentUri = row.get("agent");
                Agent agent = agents.stream()
                    .filter(a -> a.getUri().equals(agentUri))
                    .findFirst()
                    .orElse(null);
                if (agent != null) {
                    matches.add(agent);
                }
            }

            return matches;
        }

        public AgentWorkloadAnalytics getWorkloadAnalytics() {
            String query =
                "PREFIX amp: <http://example.org/agent-marketplace#>\n" +
                "SELECT ?agent (COUNT(?task) as ?taskCount) WHERE {\n" +
                "  ?task amp:assignedTo ?agent ;\n" +
                "        amp:status ?status .\n" +
                "  FILTER(?status != 'COMPLETED')\n" +
                "} GROUP BY ?agent";

            SparqlResult result = qlever.executeSparql(query);
            Map<String, Integer> workloadMap = new HashMap<>();

            for (Map<String, String> row : result.getResults()) {
                String agentUri = row.get("agent");
                int taskCount = Integer.parseInt(row.get("taskCount"));
                workloadMap.put(agentUri, taskCount);
            }

            return new AgentWorkloadAnalytics(workloadMap);
        }
    }

    // Supporting classes for AgentMarketplace
    static class Agent {
        private String uri;
        private String name;
        private List<String> skills;

        public Agent(String uri, String name, List<String> skills) {
            this.uri = uri;
            this.name = name;
            this.skills = skills;
        }

        // Getters
        public String getUri() { return uri; }
        public String getName() { return name; }
        public List<String> getSkills() { return skills; }
    }

    static class ServiceRequest {
        private String uri;
        private String type;

        public ServiceRequest(String uri, String type) {
            this.uri = uri;
            this.type = type;
        }

        public String getRequestUri() { return uri; }
        public String getType() { return type; }
    }

    static class AgentWorkloadAnalytics {
        private Map<String, Integer> workloadMap;
        private int totalTasks;

        public AgentWorkloadAnalytics(Map<String, Integer> workloadMap) {
            this.workloadMap = workloadMap;
            this.totalTasks = workloadMap.values().stream().mapToInt(Integer::intValue).sum();
        }

        public int getTotalTasks() { return totalTasks; }
        public Map<String, Integer> getWorkloadMap() { return workloadMap; }
    }

    /**
     * Example 2: Workflow Analytics Integration
     * Demonstrates performance analytics and bottleneck detection
     */
    @Test
    void testWorkflowAnalyticsIntegration() throws Exception {
        WorkflowAnalyticsIntegration analytics = new WorkflowAnalyticsIntegration();

        // Initialize analytics
        analytics.initializeAnalytics();

        // Test performance report generation
        WorkflowPerformanceReport report = analytics.generatePerformanceReport(
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now()
        );

        assertNotNull(report);
        assertTrue(report.getTotalInstances() >= 0);

        // Test bottleneck detection
        List<Bottleneck> bottlenecks = analytics.identifyBottlenecks();
        assertNotNull(bottlenecks);
    }

    class WorkflowAnalyticsIntegration {

        private final QLeverEmbedded qlever;

        public WorkflowAnalyticsIntegration() {
            this.qlever = QLeverIntegrationExamples.this.qlever;
        }

        public void initializeAnalytics() {
            // Sample workflow execution data
            qlever.loadRdfData("workflow-executions.ttl", "Turtle");
            qlever.loadRdfData("performance-metrics.ttl", "Turtle");
        }

        public WorkflowPerformanceReport generatePerformanceReport(Instant start, Instant end) {
            String query = String.format(
                "PREFIX perf: <http://example.org/performance#>\n" +
                "SELECT \n" +
                "  (COUNT(?workflow) as ?totalInstances),\n" +
                "  (AVG(?duration) as ?avgDuration)\n" +
                "WHERE {\n" +
                "  ?workflow perf:hasExecution ?execution .\n" +
                "  ?execution perf:startTime ?startTime ;\n" +
                "              perf:endTime ?endTime .\n" +
                "  BIND(TIME(?endTime) - TIME(?startTime) AS ?duration)\n" +
                "  FILTER(?startTime >= '%s'^^xsd:dateTime &&\n" +
                "         ?endTime <= '%s'^^xsd:dateTime)\n" +
                "}",
                start, end
            );

            SparqlResult result = qlever.executeSparql(query);
            if (!result.getResults().isEmpty()) {
                Map<String, String> row = result.getResults().get(0);
                int totalInstances = Integer.parseInt(row.get("totalInstances"));
                double avgDuration = Double.parseDouble(row.get("avgDuration"));

                return new WorkflowPerformanceReport(totalInstances, avgDuration);
            }
            return new WorkflowPerformanceReport(0, 0);
        }

        public List<Bottleneck> identifyBottlenecks() {
            String query =
                "PREFIX perf: <http://example.org/performance#>\n" +
                "SELECT ?task (AVG(?waitTime) as ?avgWait) WHERE {\n" +
                "  ?task perf:waitTime ?waitTime .\n" +
                "  FILTER(?waitTime > 30000)\n" +
                "} GROUP BY ?task\n" +
                "ORDER BY DESC(?avgWait) LIMIT 10";

            SparqlResult result = qlever.executeSparql(query);
            List<Bottleneck> bottlenecks = new ArrayList<>();

            for (Map<String, String> row : result.getResults()) {
                String taskUri = row.get("task");
                double avgWait = Double.parseDouble(row.get("avgWait"));
                bottlenecks.add(new Bottleneck(taskUri, avgWait));
            }

            return bottlenecks;
        }
    }

    // Supporting classes for Analytics
    static class WorkflowPerformanceReport {
        private int totalInstances;
        private double avgDuration;

        public WorkflowPerformanceReport(int totalInstances, double avgDuration) {
            this.totalInstances = totalInstances;
            this.avgDuration = avgDuration;
        }

        public int getTotalInstances() { return totalInstances; }
        public double getAvgDuration() { return avgDuration; }
    }

    static class Bottleneck {
        private String taskUri;
        private double avgWaitTime;

        public Bottleneck(String taskUri, double avgWaitTime) {
            this.taskUri = taskUri;
            this.avgWaitTime = avgWaitTime;
        }

        public String getTaskUri() { return taskUri; }
        public double getAvgWaitTime() { return avgWaitTime; }
    }

    /**
     * Example 3: Custom SPARQL Engine Configuration
     * Demonstrates optimizing QLever for YAWL-specific workloads
     */
    @Test
    void testCustomSPARQLConfiguration() throws Exception {
        QLeverCustomConfiguration config = new QLeverCustomConfiguration();

        // Test optimized engine creation
        QLeverEmbedded optimizedEngine = config.createOptimizedEngine();
        assertNotNull(optimizedEngine);

        // Test indexing configuration
        config.configureYAWLSpecificIndexing();

        // Test caching configuration
        config.configureQueryCache();
    }

    class QLeverCustomConfiguration {

        public QLeverEmbedded createOptimizedEngine() {
            QLeverEmbedded engine = new QLeverEmbedded();

            // Configure for YAWL-specific patterns
            engine.addIndexPattern("http://example.org/yawl#");
            engine.setQueryTimeout(5000);

            // Register custom vocabulary
            Vocabulary yawlVocabulary = new Vocabulary();
            yawlVocabulary.addPrefix("yawl", "http://www.yawlfoundation.org/yawl");
            yawlVocabulary.addPrefix("bpmn", "http://www.omg.org/spec/BPMN/20100524/MODEL");
            engine.setVocabulary(yawlVocabulary);

            return engine;
        }

        public void configureYAWLSpecificIndexing() {
            QLeverEmbedded engine = new QLeverEmbedded();

            // Create indexes for YAWL-specific patterns
            engine.createIndex("workflow-definition",
                "http://www.yawlfoundation.org/yawl#hasWorkflowDefinition");
            engine.createIndex("workitem-state",
                "http://www.yawlfoundation.org/yawl#hasWorkItemStatus");
            engine.createIndex("time-patterns",
                "http://www.w3.org/2006/time#");

            engine.setAutoIndexMaintenance(true);
        }

        public void configureQueryCache() {
            QLeverEngineConfig config = new QLeverEngineConfig();
            config.setCacheSize(1000);
            config.setCacheTTL(300000);
            config.enableQueryOptimization(true);

            QLeverEmbedded engine = new QLeverEmbedded(config);

            // Register frequently executed queries
            engine.registerCachedQuery("workflow-stats",
                "SELECT (COUNT(?wf) as ?count) WHERE { ?wf a yawl:Workflow }");
            engine.registerCachedQuery("active-tasks",
                "SELECT ?task WHERE { ?task yawl:status 'ACTIVE' }");
        }
    }

    // Supporting classes for configuration
    static class Vocabulary {
        private Map<String, String> prefixes = new HashMap<>();

        public void addPrefix(String prefix, String uri) {
            prefixes.put(prefix, uri);
        }

        public Map<String, String> getPrefixes() { return prefixes; }
    }

    static class QLeverEngineConfig {
        private int cacheSize;
        private long cacheTTL;
        private boolean queryOptimization;

        public void setCacheSize(int size) { cacheSize = size; }
        public void setCacheTTL(long ttl) { cacheTTL = ttl; }
        public void enableQueryOptimization(boolean enable) { queryOptimization = enable; }
    }

    /**
     * Example 4: Combining with OxigraphSparqlEngine
     * Demonstrates hybrid architecture leveraging both engines
     */
    @Test
    void testHybridEngineIntegration() throws Exception {
        HybridSparqlEngine hybrid = new HybridSparqlEngine();

        // Test query routing
        String simpleQuery = "SELECT * WHERE { ?s ?p ?o }";
        QueryResult result1 = hybrid.executeOptimizedQuery(simpleQuery);
        assertNotNull(result1);

        String complexQuery = "SELECT (COUNT(?wf) as ?count) WHERE { ?wf a yawl:Workflow } GROUP BY ?wf";
        QueryResult result2 = hybrid.executeOptimizedQuery(complexQuery);
        assertNotNull(result2);
    }

    class HybridSparqlEngine {

        private final QLeverEmbedded qleverEngine;
        private final OxigraphSparqlEngine oxigraphEngine;

        public HybridSparqlEngine() {
            // Initialize QLever for complex analytics
            this.qleverEngine = new QLeverEmbedded();
            qleverEngine.loadRdfData("large-analytics-dataset.ttl");

            // Initialize Oxigraph for transactional queries
            this.oxigraphEngine = new OxigraphSparqlEngine();
            oxigraphEngine.loadRdfData("workflow-definitions.ttl");
        }

        public QueryResult executeOptimizedQuery(String sparqlQuery) {
            if (isComplexAnalyticalQuery(sparqlQuery)) {
                return qleverEngine.executeSparql(sparqlQuery);
            } else {
                return oxigraphEngine.executeSparql(sparqlQuery);
            }
        }

        private boolean isComplexAnalyticalQuery(String query) {
            return query.toLowerCase().contains("group by") ||
                   query.toLowerCase().contains("union") ||
                   query.toLowerCase().contains("aggregate") ||
                   query.toLowerCase().contains("count(");
        }

        public QueryResult executeFederatedQuery(String query) {
            if (query.contains("FROM <analytics-dataset>")) {
                return qleverEngine.executeSparql(query);
            } else if (query.contains("FROM <workflow-definitions>")) {
                return oxigraphEngine.executeSparql(query);
            } else {
                return executeCrossEngineQuery(query);
            }
        }

        private QueryResult executeCrossEngineQuery(String query) {
            // Simplified implementation
            // In practice, you'd need proper query rewriting and result merging
            return qleverEngine.executeSparql(query);
        }
    }

    /**
     * Example 5: YAWL MCP/A2A Server Integration
     * Demonstrates intelligent query capabilities through MCP/A2A interfaces
     */
    @Test
    void testYawlMcpIntegration() throws Exception {
        YawlMcpQleverIntegration mcpIntegration = new YawlMcpQleverIntegration();

        // Initialize MCP server
        mcpIntegration.initializeMcpServer();

        // Test YAWL query execution
        String yawlQuery = "SELECT ?task WHERE { ?task a yawl:Task }";
        QueryResult result = mcpIntegration.executeYawlQuery(yawlQuery);
        assertNotNull(result);

        // Test analytics service
        AnalyticsReport analytics = mcpIntegration.getWorkflowAnalytics(
            Instant.now().minus(1, ChronoUnit.DAYS).toString(),
            Instant.now().toString()
        );
        assertNotNull(analytics);
    }

    class YawlMcpQleverIntegration {

        private final YawlMcpServer mcpServer;
        private final QLeverEmbedded qlever;

        public YawlMcpQleverIntegration() {
            this.mcpServer = new YawlMcpServer();
            this.qlever = QLeverIntegrationExamples.this.qlever;

            initializeMcpServer();
        }

        private void initializeMcpServer() {
            // Load YAWL ontology
            qlever.loadRdfData("yawl-ontology.ttl", "Turtle");

            // Register MCP tools
            mcpServer.registerTool("yawl-query", this::executeYawlQuery);
            mcpServer.registerTool("workflow-analytics", this::getWorkflowAnalytics);
            mcpServer.registerTool("case-insights", this::getCaseInsights);
        }

        public QueryResult executeYawlQuery(String query) {
            String optimizedQuery = optimizeYawlQuery(query);
            return qlever.executeSparql(optimizedQuery);
        }

        private String optimizeYawlQuery(String query) {
            // Add YAWL-specific optimizations
            if (query.contains("SELECT * WHERE")) {
                return query.replace("SELECT * WHERE",
                    "SELECT DISTINCT ?task ?name ?status WHERE");
            }
            return query;
        }

        public AnalyticsReport getWorkflowAnalytics(String startDate, String endDate) {
            String query = String.format(
                "PREFIX perf: <http://example.org/performance#>\n" +
                "SELECT (COUNT(?workflow) as ?count) WHERE {\n" +
                "  ?workflow perf:executionDate ?date .\n" +
                "  FILTER(?date >= '%s'^^xsd:dateTime &&\n" +
                "         ?date <= '%s'^^xsd:dateTime)\n" +
                "}",
                startDate, endDate
            );

            SparqlResult result = qlever.executeSparql(query);
            // Convert to AnalyticsReport
            return new AnalyticsReport(result);
        }

        public CaseInsights getCaseInsights(String caseId) {
            String insightsQuery =
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "SELECT ?task ?status WHERE {\n" +
                "  ?case yawl:hasCase ?caseId ;\n" +
                "         yawl:hasTask ?task .\n" +
                "  ?task yawl:status ?status .\n" +
                "}";

            SparqlResult result = qlever.executeSparql(insightsQuery);
            return new CaseInsights(caseId, result);
        }
    }

    // Supporting classes for MCP integration
    static class AnalyticsReport {
        private final SparqlResult result;

        public AnalyticsReport(SparqlResult result) {
            this.result = result;
        }

        public int getTotalWorkflows() {
            if (!result.getResults().isEmpty()) {
                return Integer.parseInt(result.getResults().get(0).get("count"));
            }
            return 0;
        }
    }

    static class CaseInsights {
        private String caseId;
        private SparqlResult result;

        public CaseInsights(String caseId, SparqlResult result) {
            this.caseId = caseId;
            this.result = result;
        }

        public List<Map<String, String>> getTasks() {
            return result.getResults();
        }
    }

    // Helper methods
    private void loadSampleWorkflowData() throws Exception {
        // Sample workflow definitions in Turtle format
        String workflowData =
            "@prefix yawl: <http://www.yawlfoundation.org/yawl#> .\n" +
            "@prefix time: <http://www.w3.org/2006/time#> .\n" +
            "@prefix perf: <http://example.org/performance#> .\n\n" +
            "workflow-1 a yawl:Workflow ;\n" +
            "           yawl:hasTask task-1, task-2, task-3 ;\n" +
            "           yawl:hasCondition condition-1, condition-2 .\n\n" +
            "task-1 a yawl:AtomicTask ;\n" +
            "       yawl:taskName 'Process Request' ;\n" +
            "       yawl:taskImplementation 'http://example.org/processors#processRequest' ;\n" +
            "       yawl:hasStatus 'AVAILABLE' .\n\n" +
            "task-2 a yawl:AtomicTask ;\n" +
            "       yawl:taskName 'Review Application' ;\n" +
            "       yawl:taskImplementation 'http://example.org/reviewers#review' ;\n" +
            "       yawl:hasStatus 'AVAILABLE' .\n\n" +
            "task-3 a yawl:AtomicTask ;\n" +
            "       yawl:taskName 'Final Decision' ;\n" +
            "       yawl:taskImplementation 'http://example.org/deciders#decide' ;\n" +
            "       yawl:hasStatus 'AVAILABLE' .\n\n" +
            "condition-1 a yawl:InputCondition ;\n" +
            "             yawl:conditionName 'Start Condition' .\n\n" +
            "condition-2 a yawl:OutputCondition ;\n" +
            "             yawl:conditionName 'End Condition' .\n\n" +
            "# Performance data\n" +
            "execution-1 perf:hasWorkflow workflow-1 ;\n" +
            "           perf:startTime '2026-02-20T10:00:00'^^xsd:dateTime ;\n" +
            "           perf:endTime '2026-02-20T10:30:00'^^xsd:dateTime .\n\n" +
            "task-1 perf:hasExecution execution-1 ;\n" +
            "      perf:waitTime 5000 ;\n" +
            "      perf:executeTime 45000 .\n\n" +
            "task-2 perf:hasExecution execution-1 ;\n" +
            "      perf:waitTime 3000 ;\n" +
            "      perf:executeTime 120000 .\n\n" +
            "task-3 perf:hasExecution execution-1 ;\n" +
            "      perf:waitTime 2000 ;\n" +
            "      perf:executeTime 15000 .";

        // Write to temporary file
        File tempFile = File.createTempFile("workflow-data", ".ttl");
        try (Writer writer = new FileWriter(tempFile)) {
            writer.write(workflowData);
        }

        // Load into QLever
        qlever.loadRdfData(tempFile.getAbsolutePath(), "Turtle");

        // Clean up
        tempFile.delete();
    }

    private List<ServiceRequest> createTestServiceRequests() {
        return Arrays.asList(
            new ServiceRequest("request-1", "loan-approval"),
            new ServiceRequest("request-2", "document-verification"),
            new ServiceRequest("request-3", "customer-query")
        );
    }

    // Mock classes for testing
    static class YawlMcpServer {
        private Map<String, QueryFunction> tools = new HashMap<>();

        public void registerTool(String name, QueryFunction function) {
            tools.put(name, function);
        }

        public QueryFunction getTool(String name) {
            return tools.get(name);
        }
    }

    @FunctionalInterface
    interface QueryFunction {
        QueryResult apply(String query);
    }

    static class QueryResult {
        private List<Map<String, String>> results;
        private String error;

        public QueryResult(List<Map<String, String>> results) {
            this.results = results;
        }

        public QueryResult(String error) {
            this.error = error;
        }

        public List<Map<String, String>> getResults() {
            return results;
        }

        public String getError() {
            return error;
        }
    }

    static class OxigraphSparqlEngine {
        public QueryResult executeSparql(String query) {
            // Mock implementation
            List<Map<String, String>> results = new ArrayList<>();
            Map<String, String> row = new HashMap<>();
            row.put("s", "example:subject");
            row.put("p", "example:predicate");
            row.put("o", "example:object");
            results.add(row);
            return new QueryResult(results);
        }

        public void loadRdfData(String filePath, String format) {
            // Mock implementation
        }
    }
}