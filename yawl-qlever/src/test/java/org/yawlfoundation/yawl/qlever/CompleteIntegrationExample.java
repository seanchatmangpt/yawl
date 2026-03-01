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
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.qlever.util.IntegrationHelper;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Complete integration example demonstrating the full workflow of using QLever with YAWL components.
 * This example shows the entire lifecycle from initialization to production deployment.
 */
public class CompleteIntegrationExample {

    private QLeverEmbedded qlever;
    private YAWLOntology ontology;
    private YAWLServiceGateway serviceGateway;
    private YWorkItemRepository workItemRepository;
    private CompleteWorkflowManager workflowManager;

    @BeforeEach
    void setUp() throws Exception {
        // Step 1: Initialize all components
        initializeComponents();

        // Step 2: Configure the system
        configureSystem();

        // Step 3: Load initial data
        loadInitialData();

        // Step 4: Start monitoring
        workflowManager.startMonitoring();
    }

    @Test
    void testCompleteWorkflow() throws Exception {
        // Step 5: Execute complete workflow example
        System.out.println("=== Starting Complete Integration Example ===");

        // 5.1 Create sample workflow
        YSpecification specification = createSampleWorkflow();
        assertNotNull(specification, "Should create sample workflow");

        // 5.2 Deploy workflow
        workflowManager.deployWorkflow(specification);
        System.out.println("Workflow deployed successfully");

        // 5.3 Execute workflow instances
        List<String> instanceIds = workflowManager.createWorkflowInstances(10);
        System.out.println("Created " + instanceIds.size() + " workflow instances");

        // 5.4 Monitor execution
        workflowManager.monitorExecution(instanceIds);
        System.out.println("Monitoring workflow execution");

        // 5.5 Analyze performance
        PerformanceReport report = workflowManager.generatePerformanceReport();
        System.out.println("Performance report generated: " + report.getTotalInstances() + " instances");

        // 5.6 Optimize based on findings
        workflowManager.optimizeWorkflows();
        System.out.println("Workflows optimized");

        // Step 6: Validate results
        assertNotNull(report);
        assertTrue(report.getTotalInstances() > 0);
        assertTrue(report.getCompletionRate() > 0.5);

        // Step 7: Cleanup
        workflowManager.cleanup();
        System.out.println("=== Integration Example Completed Successfully ===");
    }

    private void initializeComponents() {
        // Initialize QLever embedded engine
        qlever = new QLeverEmbedded();
        qlever.initialize();

        // Initialize YAWL components
        ontology = YAWLOntology.getInstance();
        serviceGateway = new YAWLServiceGateway();
        workItemRepository = new YWorkItemRepository();

        // Initialize workflow manager
        workflowManager = new CompleteWorkflowManager(qlever, serviceGateway, workItemRepository);
    }

    private void configureSystem() throws Exception {
        // Configure QLever for YAWL
        qlever.registerNamespace("yawl", "http://www.yawlfoundation.org/yawl");
        qlever.registerNamespace("bpmn", "http://www.omg.org/spec/BPMN/20100524/MODEL");
        qlever.registerNamespace("time", "http://www.w3.org/2006/time#");
        qlever.registerNamespace("perf", "http://example.org/performance#");
        qlever.registerNamespace("compliance", "http://example.org/compliance#");

        // Set up indexing for optimal performance
        IntegrationHelper.createIndexConfiguration(
            "http://www.yawlfoundation.org/yawl#hasWorkflow",
            "http://www.yawlfoundation.org/yawl#hasTask",
            "http://www.yawlfoundation.org/yawl#hasWorkItem",
            "http://example.org/performance#hasExecution",
            "http://example.org/performance#duration"
        );

        // Configure caching
        qlever.enableQueryCache(true);
        qlever.setCacheSize(1000);
    }

    private void loadInitialData() throws Exception {
        // Load workflow definitions
        IntegrationHelper.loadYawlData(qlever, "workflows.ttl", "Turtle");

        // Load performance metrics
        IntegrationHelper.loadYawlData(qlever, "performance-data.ttl", "Turtle");

        // Load compliance rules
        IntegrationHelper.loadYawlData(qlever, "compliance-rules.ttl", "Turtle");
    }

    private YSpecification createSampleWorkflow() {
        // Create a sample loan approval workflow
        YSpecification specification = new YSpecification();
        YNet net = specification.getRootNet();

        // Create tasks
        YTask task1 = new YAtomicTask("Task1");
        task1.setTaskName("Receive Application");
        task1.setTaskImplementation("http://example.org/processors#receiveApplication");

        YTask task2 = new YAtomicTask("Task2");
        task2.setTaskName("Verify Documents");
        task2.setTaskImplementation("http://example.org/processors#verifyDocuments");

        YTask task3 = new YAtomicTask("Task3");
        task3.setTaskName("Assess Risk");
        task3.setTaskImplementation("http://example.org/processors#assessRisk");

        YTask task4 = new YAtomicTask("Task4");
        task4.setTaskName("Make Decision");
        task4.setTaskImplementation("http://example.org/processors#makeDecision");

        // Create conditions
        YInputCondition start = new YInputCondition("C1");
        YOutputCondition end = new YOutputCondition("C2");

        // Add flows between tasks
        net.addFlow(start, task1);
        net.addFlow(task1, task2);
        net.addFlow(task2, task3);
        net.addFlow(task3, task4);
        net.addFlow(task4, end);

        // Store in QLever
        qlever.addTriple(workflowManager.getWorkflowUri(specification), "a", "yawl:Workflow");
        qlever.addTriple(workflowManager.getWorkflowUri(specification), "yawl:hasTask", task1.getID());
        qlever.addTriple(workflowManager.getWorkflowUri(specification), "yawl:hasTask", task2.getID());
        qlever.addTriple(workflowManager.getWorkflowUri(specification), "yawl:hasTask", task3.getID());
        qlever.addTriple(workflowManager.getWorkflowUri(specification), "yawl:hasTask", task4.getID());

        return specification;
    }

    /**
     * Complete workflow manager demonstrating integration patterns
     */
    class CompleteWorkflowManager {
        private final QLeverEmbedded qlever;
        private final YAWLServiceGateway serviceGateway;
        private final YWorkItemRepository workItemRepository;
        private final IntegrationHelper.BatchProcessor<String> batchProcessor;
        private final IntegrationHelper.Cache<String, PerformanceReport> cache;

        public CompleteWorkflowManager(QLeverEmbedded qlever,
                                     YAWLServiceGateway serviceGateway,
                                     YWorkItemRepository workItemRepository) {
            this.qlever = qlever;
            this.serviceGateway = serviceGateway;
            this.workItemRepository = workItemRepository;
            this.batchProcessor = new IntegrationHelper.BatchProcessor<>(new ArrayList<>(), 100);
            this.cache = new IntegrationHelper.Cache<>(300000); // 5 minutes cache
        }

        public void deployWorkflow(YSpecification specification) throws Exception {
            // Convert YAWL specification to RDF
            String rdf = convertSpecificationToRdf(specification);

            // Store in QLever
            qlever.addTriple(rdf);

            // Register workflow with YAWL engine
            serviceGateway.registerYAWLWorkflow(specification);

            // Set up monitoring
            setupWorkflowMonitoring(specification);
        }

        public List<String> createWorkflowInstances(int count) {
            List<String> instanceIds = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String instanceId = "instance-" + UUID.randomUUID().toString();
                instanceIds.add(instanceId);

                // Create workflow instance
                YWorkItem workItem = serviceGateway.createWorkItem(instanceId);
                workItemRepository.save(workItem);

                // Store instance information in QLever
                qlever.addTriple(instanceId, "a", "yawl:WorkflowInstance");
                qlever.addTriple(instanceId, "yawl:hasStatus", "ACTIVE");
                qlever.addTriple(instanceId, "yawl:createdTime", Instant.now().toString());
            }

            return instanceIds;
        }

        public void monitorExecution(List<String> instanceIds) {
            // Use batch processing for monitoring
            batchProcessor.processItems(batch -> {
                for (String instanceId : batch) {
                    // Query workflow status
                    String statusQuery = IntegrationHelper.optimizeYawlQuery(
                        "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                        "SELECT ?status WHERE {\n" +
                        "  <" + instanceId + "> yawl:hasStatus ?status .\n" +
                        "}"
                    );

                    // Execute query
                    org.yawlfoundation.yawl.qlever.sparql.SparqlResult result = qlever.executeSparql(statusQuery);

                    // Process results
                    if (result.getResults() != null && !result.getResults().isEmpty()) {
                        String status = result.getResults().get(0).get("status");

                        // Log performance metrics
                        logPerformanceMetrics(instanceId, status);
                    }
                }
            });
        }

        public PerformanceReport generatePerformanceReport() {
            // Check cache first
            return cache.get("performance-report", key -> {
                String query = IntegrationHelper.createPerformanceQuery(
                    "http://example.org/workflow#LoanApproval",
                    Instant.now().minus(24, ChronoUnit.HOURS),
                    Instant.now()
                );

                org.yawlfoundation.yawl.qlever.sparql.SparqlResult result = qlever.executeSparql(query);

                return parsePerformanceReport(result);
            });
        }

        public void optimizeWorkflows() {
            // Find workflow bottlenecks
            String bottleneckQuery = IntegrationHelper.optimizeYawlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "SELECT ?task ?avgDuration WHERE {\n" +
                "  ?task perf:averageDuration ?avgDuration .\n" +
                "  FILTER(?avgDuration > 60000)  // Tasks taking > 1 minute\n" +
                "} ORDER BY DESC(?avgDuration) LIMIT 5"
            );

            org.yawlfoundation.yawl.qlever.sparql.SparqlResult result = qlever.executeSparql(bottleneckQuery);

            // Apply optimizations
            for (Map<String, String> row : result.getResults()) {
                String taskUri = row.get("task");
                double avgDuration = Double.parseDouble(row.get("avgDuration"));

                // Apply optimization
                optimizeTask(taskUri, avgDuration);
            }

            // Update workflow definitions
            updateWorkflowDefinitions();
        }

        public void startMonitoring() {
            // Start continuous monitoring thread
            new Thread(this::monitoringLoop).start();
        }

        private void monitoringLoop() {
            while (true) {
                try {
                    // Check for anomalies
                    checkForAnomalies();

                    // Update performance metrics
                    updatePerformanceMetrics();

                    // Sleep for monitoring interval
                    Thread.sleep(60000); // 1 minute
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        private void checkForAnomalies() {
            String anomalyQuery = IntegrationHelper.optimizeYamlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "SELECT ?instance ?anomaly WHERE {\n" +
                "  ?instance perf:executionTime ?time ;\n" +
                "            perf:completionRate ?rate .\n" +
                "  FILTER(?time > 3600000 && ?rate < 0.5)  // >1 hour, <50% completion\n" +
                "  BIND(CONCAT(?instance, ' needs attention') AS ?anomaly)\n" +
                "}"
            );

            org.yawlfoundation.yawl.qlever.sparql.SparqlResult result = qlever.executeSparql(anomalyQuery);

            for (Map<String, String> row : result.getResults()) {
                String instanceUri = row.get("instance");
                String anomaly = row.get("anomaly");

                // Send alert
                sendAlert(instanceUri, anomaly);
            }
        }

        private void updatePerformanceMetrics() {
            // Update real-time performance metrics
            String updateQuery = IntegrationHelper.optimizeYawlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "DELETE {\n" +
                "  ?task perf:currentLoad ?oldLoad .\n" +
                "}\n" +
                "INSERT {\n" +
                "  ?task perf:currentLoad ?newLoad .\n" +
                "}\n" +
                "WHERE {\n" +
                "  ?task perf:currentLoad ?oldLoad .\n" +
                "  BIND(?oldLoad + 1 AS ?newLoad)\n" +
                "}"
            );

            qlever.executeSparql(updateQuery);
        }

        private void setupWorkflowMonitoring(YSpecification specification) {
            // Set up monitoring rules
            String monitoringSetup = IntegrationHelper.optimizeYawlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "INSERT DATA {\n" +
                "  <" + getWorkflowUri(specification) + "> perf:monitoredBy 'QLever' ;\n" +
                "                           perf:monitoringLevel 'HIGH' .\n" +
                "}"
            );

            qlever.executeSparql(monitoringSetup);
        }

        private void logPerformanceMetrics(String instanceId, String status) {
            // Store performance metrics
            qlever.addTriple(instanceId, "perf:lastStatus", status);
            qlever.addTriple(instanceId, "perf:lastUpdate", Instant.now().toString());
        }

        private PerformanceReport parsePerformanceReport(org.yawlfoundation.yawl.qlever.sparql.SparqlResult result) {
            if (result.getResults() == null || result.getResults().isEmpty()) {
                return new PerformanceReport(0, 0.0);
            }

            Map<String, String> row = result.getResults().get(0);
            int totalInstances = Integer.parseInt(row.getOrDefault("count", "0"));
            double completionRate = Double.parseDouble(row.getOrDefault("completionRate", "0.0"));

            return new PerformanceReport(totalInstances, completionRate);
        }

        private void optimizeTask(String taskUri, double avgDuration) {
            // Apply task optimization
            String optimizationQuery = IntegrationHelper.optimizeYawlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "DELETE {\n" +
                "  <" + taskUri + "> perf:averageDuration ?oldDuration .\n" +
                "}\n" +
                "INSERT {\n" +
                "  <" + taskUri + "> perf:averageDuration ?newDuration ;\n" +
                "                    perf:optimizationApplied 'true' .\n" +
                "}\n" +
                "WHERE {\n" +
                "  <" + taskUri + "> perf:averageDuration ?oldDuration .\n" +
                "  BIND(?oldDuration * 0.8 AS ?newDuration)\n" +
                "}"
            );

            qlever.executeSparql(optimizationQuery);
        }

        private void updateWorkflowDefinitions() {
            // Update workflow definitions with optimizations
            String updateQuery = IntegrationHelper.optimizeYawlQuery(
                "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
                "PREFIX perf: <http://example.org/performance#>\n" +
                "INSERT DATA {\n" +
                "  [] perf:lastOptimized '" + Instant.now() + "' ;\n" +
                "          perf:optimizedBy 'QLever' .\n" +
                "}"
            );

            qlever.executeSparql(updateQuery);
        }

        private void sendAlert(String instanceUri, String anomaly) {
            // Send alert (mock implementation)
            System.out.println("ALERT: " + anomaly);
            System.out.println("Instance: " + instanceUri);

            // Store alert in QLever
            qlever.addTriple(instanceUri, "perf:hasAlert", anomaly);
        }

        public void cleanup() {
            // Clear cache
            cache.clear();

            // Stop monitoring
            // (In real implementation, would have proper shutdown)

            // Generate final report
            PerformanceReport finalReport = generatePerformanceReport();
            System.out.println("Final performance report: " + finalReport);
        }

        private String convertSpecificationToRdf(YSpecification specification) {
            // Convert YAWL specification to RDF (mock implementation)
            return "<" + getWorkflowUri(specification) + "> a yawl:Workflow .";
        }

        private String getWorkflowUri(YSpecification specification) {
            return "http://example.org/workflow#" + specification.getID();
        }
    }

    // Supporting classes
    static class PerformanceReport {
        private int totalInstances;
        private double completionRate;

        public PerformanceReport(int totalInstances, double completionRate) {
            this.totalInstances = totalInstances;
            this.completionRate = completionRate;
        }

        public int getTotalInstances() { return totalInstances; }
        public double getCompletionRate() { return completionRate; }

        @Override
        public String toString() {
            return String.format("PerformanceReport{instances=%d, rate=%.2f}", totalInstances, completionRate);
        }
    }
}