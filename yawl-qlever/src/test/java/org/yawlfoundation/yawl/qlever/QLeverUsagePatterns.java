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

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLOntology;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.qlever.sparql.SparqlResult;
import org.yawlfoundation.yawl.qlever.queries.WorkflowPathFinder;
import org.yawlfoundation.yawl.qlever.queries.AnalyticsQuery;
import org.yawlfoundation.yawl.qlever.util.RDFUtil;

import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This class demonstrates practical usage patterns for QLever with YAWL workflows.
 * These patterns show common scenarios and best practices for integration.
 */
public class QLeverUsagePatterns {

    private QLeverEmbedded qlever;
    private YAWLOntology ontology;
    private YAWLServiceGateway serviceGateway;
    private YWorkItemRepository workItemRepository;

    @Test
    void testWorkflowDiscoveryPattern() throws Exception {
        // Pattern: Finding all available tasks in a workflow
        String query =
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "SELECT DISTINCT ?task ?taskName WHERE {\n" +
            "  ?task a yawl:Task ;\n" +
            "        yawl:taskName ?taskName ;\n" +
            "        yawl:hasStatus 'AVAILABLE' .\n" +
            "}";

        SparqlResult result = qlever.executeSparql(query);
        assertFalse(result.getResults().isEmpty(), "Should find available tasks");

        // Process results
        List<YTask> availableTasks = result.getResults().stream()
            .map(row -> createTaskFromResult(row))
            .collect(Collectors.toList());

        assertFalse(availableTasks.isEmpty(), "Should have available tasks");
    }

    private YTask createTaskFromResult(Map<String, String> row) {
        // Helper to create YTask from SPARQL result
        return new YAtomicTask(row.get("task"));
    }

    @Test
    void testPathAnalysisPattern() throws Exception {
        // Pattern: Finding all possible paths through a workflow
        WorkflowPathFinder pathFinder = new WorkflowPathFinder(qlever);

        List<List<String>> paths = pathFinder.findAllPaths(
            "http://example.org/workflow#Start",
            "http://example.org/workflow#End"
        );

        assertFalse(paths.isEmpty(), "Should find workflow paths");
        assertTrue(paths.size() > 0, "Should have at least one path");

        // Analyze path lengths
        int shortestPath = paths.stream()
            .mapToInt(List::size)
            .min()
            .orElse(0);

        System.out.println("Shortest path length: " + shortestPath);
    }

    @Test
    void testPerformanceMonitoringPattern() throws Exception {
        // Pattern: Monitoring workflow performance metrics
        AnalyticsQuery analytics = new AnalyticsQuery(qlever);

        // Get average execution time per task type
        Map<String, Double> executionTimes = analytics.getAverageExecutionTimes(
            Instant.now().minus(7, ChronoUnit.DAYS),
            Instant.now()
        );

        assertFalse(executionTimes.isEmpty(), "Should have execution time data");

        // Find slowest tasks
        Optional<Map.Entry<String, Double>> slowestTask = executionTimes.entrySet().stream()
            .max(Map.Entry.comparingByValue());

        assertTrue(slowestTask.isPresent(), "Should find slowest task");
        System.out.println("Slowest task: " + slowestTask.get().getKey() +
                         " with average time: " + slowestTask.get().getValue());
    }

    @Test
    void testResourceAllocationPattern() throws Exception {
        // Pattern: Optimizing resource allocation based on workload
        String query =
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX perf: <http://example.org/performance#>\n" +
            "SELECT ?resource ?currentLoad ?predictedLoad WHERE {\n" +
            "  ?resource perf:currentLoad ?currentLoad .\n" +
            "  ?resource perf:predictionModel ?model .\n" +
            "  # Simple prediction based on historical trends\n" +
            "  BIND(?currentLoad * 1.1 + 100 AS ?predictedLoad)\n" +
            "  FILTER(?predictedLoad > 80)  # Alert threshold\n" +
            "}";

        SparqlResult result = qlever.executeSparql(query);
        List<ResourceAlert> alerts = new ArrayList<>();

        for (Map<String, String> row : result.getResults()) {
            double predictedLoad = Double.parseDouble(row.get("predictedLoad"));
            alerts.add(new ResourceAlert(row.get("resource"), predictedLoad));
        }

        assertFalse(alerts.isEmpty(), "Should have resource allocation alerts");

        // Generate recommendations
        for (ResourceAlert alert : alerts) {
            String recommendation = generateResourceRecommendation(alert);
            System.out.println("Resource " + alert.getResourceId() +
                             ": " + recommendation);
        }
    }

    private String generateResourceRecommendation(ResourceAlert alert) {
        if (alert.getPredictedLoad() > 90) {
            return "Immediate scaling required";
        } else if (alert.getPredictedLoad() > 80) {
            return "Consider scaling up";
        } else {
            return "Load acceptable";
        }
    }

    @Test
    void testComplianceCheckingPattern() throws Exception {
        // Pattern: Checking workflow compliance with regulations
        String complianceQuery =
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX regulation: <http://example.org/regulations#>\n" +
            "SELECT ?task ?complianceIssue WHERE {\n" +
            "  ?task a yawl:Task ;\n" +
            "        yawl:taskName ?taskName .\n" +
            "  # Check for missing audit trails\n" +
            "  OPTIONAL { ?task regulation:hasAuditTrail ?audit }\n" +
            "  FILTER(!BOUND(?audit) && ?taskName != 'System Task')\n" +
            "  BIND(CONCAT(?taskName, ' missing audit trail') AS ?complianceIssue)\n" +
            "}";

        SparqlResult result = qlever.executeSparql(complianceQuery);
        List<ComplianceIssue> issues = result.getResults().stream()
            .map(row -> new ComplianceIssue(
                row.get("task"),
                row.get("complianceIssue")
            ))
            .collect(Collectors.toList());

        if (!issues.isEmpty()) {
            System.out.println("Found " + issues.size() + " compliance issues:");
            issues.forEach(issue ->
                System.out.println("- " + issue.getIssue()));
        }
    }

    @Test
    void testDynamicWorkflowOptimization() throws Exception {
        // Pattern: Dynamically optimizing workflow execution
        String optimizationQuery =
            "PREFIX yawl: <http://www.yawlfoundation.org/yawl#>\n" +
            "PREFIX perf: <http://example.org/performance#>\n" +
            "SELECT ?workflow ?optimization WHERE {\n" +
            "  ?workflow perf:hasExecution ?execution .\n" +
            "  ?execution perf:duration ?duration .\n" +
            "  ?execution perf:completionRate ?rate .\n" +
            "  # Identify optimization opportunities\n" +
            "  FILTER(?duration > 300000 && ?rate < 0.8)  # >5min, <80% completion\n" +
            "  BIND(CONCAT('Workflow ', ?workflow, ' needs optimization') AS ?optimization)\n" +
            "}";

        SparqlResult result = qlever.executeSparql(optimizationQuery);
        List<WorkflowOptimization> optimizations = result.getResults().stream()
            .map(row -> new WorkflowOptimization(
                row.get("workflow"),
                row.get("optimization")
            ))
            .collect(Collectors.toList());

        if (!optimizations.isEmpty()) {
            System.out.println("Optimization recommendations:");
            optimizations.forEach(opt ->
                System.out.println("- " + opt.getRecommendation()));
        }
    }

    @Test
    void testRealTimeMonitoringPattern() throws Exception {
        // Pattern: Real-time workflow event processing
        RealTimeMonitor monitor = new RealTimeMonitor(qlever);

        // Simulate receiving workflow events
        List<WorkflowEvent> events = Arrays.asList(
            new WorkflowEvent("workitem-created", "task-1", Instant.now()),
            new WorkflowEvent("task-completed", "task-1", Instant.now()),
            new WorkflowEvent("workitem-created", "task-2", Instant.now())
        );

        // Process events in real-time
        for (WorkflowEvent event : events) {
            monitor.processEvent(event);
        }

        // Check for anomalies
        List<Anomaly> anomalies = monitor.detectAnomalies();
        if (!anomalies.isEmpty()) {
            System.out.println("Detected anomalies:");
            anomalies.forEach(anomaly ->
                System.out.println("- " + anomaly.getDescription()));
        }
    }

    @Test
    void testHistoricalAnalysisPattern() throws Exception {
        // Pattern: Analyzing historical workflow data for trends
        AnalyticsQuery analytics = new AnalyticsQuery(qlever);

        // Analyze trends over time
        Map<String, List<Integer>> monthlyCounts = analytics.getMonthlyCompletionRates(
            Instant.now().minus(6, ChronoUnit.MONTHS),
            Instant.now()
        );

        assertFalse(monthlyCounts.isEmpty(), "Should have historical data");

        // Identify trends
        List<Trend> trends = identifyTrends(monthlyCounts);
        if (!trends.isEmpty()) {
            System.out.println("Identified trends:");
            trends.forEach(trend ->
                System.out.println("- " + trend.getDescription()));
        }
    }

    private List<Trend> identifyTrends(Map<String, List<Integer>> monthlyData) {
        List<Trend> trends = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : monthlyData.entrySet()) {
            List<Integer> values = entry.getValue();
            if (values.size() >= 3) {
                // Simple trend detection
                boolean increasing = true;
                boolean decreasing = true;

                for (int i = 1; i < values.size(); i++) {
                    if (values.get(i) <= values.get(i-1)) increasing = false;
                    if (values.get(i) >= values.get(i-1)) decreasing = false;
                }

                if (increasing) {
                    trends.add(new Trend(entry.getKey(), "improving", "Completion rate increasing"));
                } else if (decreasing) {
                    trends.add(new Trend(entry.getKey(), "declining", "Completion rate decreasing"));
                }
            }
        }

        return trends;
    }

    // Supporting classes for usage patterns
    static class ResourceAlert {
        private String resourceId;
        private double predictedLoad;

        public ResourceAlert(String resourceId, double predictedLoad) {
            this.resourceId = resourceId;
            this.predictedLoad = predictedLoad;
        }

        public String getResourceId() { return resourceId; }
        public double getPredictedLoad() { return predictedLoad; }
    }

    static class ComplianceIssue {
        private String taskUri;
        private String issue;

        public ComplianceIssue(String taskUri, String issue) {
            this.taskUri = taskUri;
            this.issue = issue;
        }

        public String getTaskUri() { return taskUri; }
        public String getIssue() { return issue; }
    }

    static class WorkflowOptimization {
        private String workflowUri;
        private String recommendation;

        public WorkflowOptimization(String workflowUri, String recommendation) {
            this.workflowUri = workflowUri;
            this.recommendation = recommendation;
        }

        public String getWorkflowUri() { return workflowUri; }
        public String getRecommendation() { return recommendation; }
    }

    static class WorkflowEvent {
        private String type;
        private String taskUri;
        private Instant timestamp;

        public WorkflowEvent(String type, String taskUri, Instant timestamp) {
            this.type = type;
            this.taskUri = taskUri;
            this.timestamp = timestamp;
        }

        // Getters
        public String getType() { return type; }
        public String getTaskUri() { return taskUri; }
        public Instant getTimestamp() { return timestamp; }
    }

    static class Anomaly {
        private String description;
        private String severity;

        public Anomaly(String description, String severity) {
            this.description = description;
            this.severity = severity;
        }

        public String getDescription() { return description; }
        public String getSeverity() { return severity; }
    }

    static class Trend {
        private String subject;
        private String direction;
        private String description;

        public Trend(String subject, String direction, String description) {
            this.subject = subject;
            this.direction = direction;
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    // Real-time monitor implementation
    class RealTimeMonitor {
        private final QLeverEmbedded qlever;
        private List<WorkflowEvent> eventBuffer = new ArrayList<>();

        public RealTimeMonitor(QLeverEmbedded qlever) {
            this.qlever = qlever;
        }

        public void processEvent(WorkflowEvent event) {
            // Add event to buffer
            eventBuffer.add(event);

            // Convert to RDF triple and store
            qlever.addTriple(
                event.getTaskUri(),
                "http://example.org/events#" + event.getType(),
                event.getTimestamp().toString()
            );

            // Process in batches for efficiency
            if (eventBuffer.size() >= 10) {
                processBatch();
                eventBuffer.clear();
            }
        }

        private void processBatch() {
            // Analyze event patterns
            Map<String, Integer> eventCounts = new HashMap<>();
            for (WorkflowEvent event : eventBuffer) {
                eventCounts.merge(event.getType(), 1, Integer::sum);
            }

            // Detect anomalies
            detectAnomaliesInBatch(eventCounts);
        }

        public List<Anomaly> detectAnomalies() {
            List<Anomaly> anomalies = new ArrayList<>();

            // Check for rapid sequence of workitem creations
            long recentCreations = eventBuffer.stream()
                .filter(e -> e.getType().equals("workitem-created"))
                .count();

            if (recentCreations > 5) {
                anomalies.add(new Anomaly(
                    "High workitem creation rate detected",
                    "WARNING"
                ));
            }

            return anomalies;
        }

        private void detectAnomaliesInBatch(Map<String, Integer> eventCounts) {
            // Example: Detect task completion anomalies
            int completions = eventCounts.getOrDefault("task-completed", 0);
            int creations = eventCounts.getOrDefault("workitem-created", 0);

            if (completions > creations * 2) {
                // More completions than expected
                qlever.addTriple("anomaly-detected", "type", "high-completion-rate");
            } else if (creations > completions * 3) {
                // Too many pending workitems
                qlever.addTriple("anomaly-detected", "type", "workitem-backlog");
            }
        }
    }
}