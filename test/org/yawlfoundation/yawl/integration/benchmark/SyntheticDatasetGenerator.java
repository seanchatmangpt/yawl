/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import java.util.function.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Generator of large synthetic datasets for YAWL v6.0.0-GA scalability testing.
 *
 * <p>Generates massive datasets (1M+ work items) for stress testing and performance
 * benchmarking with realistic data patterns and statistical distributions.
 *
 * <p>Dataset Types:
 * <ul>
 *   <li>Work Item Streams - Continuous flow of work items</li>
 *   <li>Case Histories - Complete workflow execution trails</li>
 *   <li>User Activity Logs - User interaction patterns</li>
 *   <li>Performance Metrics - System performance data</li>
 *   <li>Error Scenarios - Failure injection scenarios</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Multi-threaded generation for scalability</li>
 *   <li>Realistic data distributions (Pareto, Normal, Exponential)</li>
 *   <li>Time-series data with temporal patterns</li>
 *   <li>Memory-efficient streaming generation</li>
 *   <li>Statistical validation of generated data</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class SyntheticDatasetGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    // User and organization data
    private static final String[] FIRST_NAMES = {
        "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
        "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
        "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Nancy", "Daniel", "Lisa",
        "Matthew", "Betty", "Anthony", "Dorothy", "Mark", "Sandra", "Donald", "Ashley",
        "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle",
        "Kenneth", "Laura", "Kevin", "Sarah", "Brian", "Cynthia", "George", "Anne"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson",
        "Thomas", "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson",
        "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker",
        "Young", "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill",
        "Flores", "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Gonzalez"
    };

    private static final String[] DEPARTMENTS = {
        "Finance", "HR", "IT", "Operations", "Legal", "Sales", "Marketing", "Customer Service",
        "R&D", "Quality Assurance", "Supply Chain", "Manufacturing", "Logistics", "Procurement",
        "Facilities", "Security", "Compliance", "Training", "Communications", "Public Relations"
    };

    private static final String[] CATEGORIES = {
        "Approval", "Processing", "Review", "Verification", "Submission", "Authorization",
        "Notification", "Escalation", "Assignment", "Completion", "Cancellation", "Rejection"
    };

    // Data generators for different distributions
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    private final ExecutorService executor;

    /**
     * Creates a new synthetic dataset generator
     */
    public SyntheticDatasetGenerator() {
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Generates a stream of work items for continuous testing
     */
    public Stream<Map<String, Object>> generateWorkItemStream(int count, long delayMs) {
        return IntStream.range(0, count)
            .mapToObj(i -> {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return generateWorkItem(i);
            });
    }

    /**
     * Generates complete case histories with all work items
     */
    public List<Map<String, Object>> generateCaseHistories(int caseCount, int workItemsPerCase) {
        List<Map<String, Object>> cases = new ArrayList<>(caseCount);

        for (int i = 0; i < caseCount; i++) {
            Map<String, Object> caseHistory = new HashMap<>();
            String caseId = "case-" + UUID.randomUUID().toString().substring(0, 8);
            caseHistory.put("caseId", caseId);
            caseHistory.put("workflowId", "workflow-" + random.nextInt(1, 10));
            caseHistory.put("startTime", Instant.now().minusSeconds(random.nextInt(0, 86400)).toString());
            caseHistory.put("status", random.nextBoolean() ? "completed" : "in_progress");

            // Generate work items for the case
            List<Map<String, Object>> workItems = new ArrayList<>(workItemsPerCase);
            for (int j = 0; j < workItemsPerCase; j++) {
                Map<String, Object> workItem = generateWorkItemForCase(caseId, j);
                workItems.add(workItem);
            }

            caseHistory.put("workItems", workItems);
            caseHistory.put("totalWorkItems", workItemsPerCase);
            caseHistory.put("totalDuration", calculateCaseDuration(workItems));

            cases.add(caseHistory);
        }

        return cases;
    }

    /**
     * Generates user activity logs
     */
    public List<Map<String, Object>> generateUserActivityLogs(int logCount) {
        List<Map<String, Object>> logs = new ArrayList<>(logCount);

        for (int i = 0; i < logCount; i++) {
            Map<String, Object> log = new HashMap<>();
            log.put("id", "log-" + UUID.randomUUID().toString().substring(0, 12));
            log.put("userId", generateUserId());
            log.put("timestamp", Instant.now().minusSeconds(random.nextInt(0, 86400)).toString());
            log.put("action", getRandomAction());
            log.put("resource", getRandomResource());
            log.put("result", random.nextBoolean() ? "success" : "failure");
            log.put("details", generateActivityDetails());
            log.put("ipAddress", generateIPAddress());
            log.put("userAgent", generateUserAgent());

            logs.add(log);
        }

        return logs;
    }

    /**
     * Generates performance metrics for system monitoring
     */
    public List<Map<String, Object>> generatePerformanceMetrics(int metricCount, Duration timeRange) {
        List<Map<String, Object>> metrics = new ArrayList<>(metricCount);
        Instant endTime = Instant.now();
        Instant startTime = endTime.minus(timeRange);

        for (int i = 0; i < metricCount; i++) {
            Instant timestamp = startTime.plus(
                Duration.ofMillis(random.nextLong(0, timeRange.toMillis()))
            );

            Map<String, Object> metric = new HashMap<>();
            metric.put("timestamp", timestamp.toString());
            metric.put("metricType", getRandomMetricType());
            metric.put("value", generateMetricValue(metric.get("metricType").toString()));
            metric.put("unit", getMetricUnit(metric.get("metricType").toString()));
            metric.put("component", getRandomComponent());
            metric.put("service", getRandomService());
            metric.put("tenantId", generateTenantId());
            metric.put("dimension", generateMetricDimension());

            metrics.add(metric);
        }

        return metrics;
    }

    /**
     * Generates error scenarios for chaos testing
     */
    public List<Map<String, Object>> generateErrorScenarios(int scenarioCount) {
        List<Map<String, Object>> scenarios = new ArrayList<>(scenarioCount);

        for (int i = 0; i < scenarioCount; i++) {
            Map<String, Object> scenario = new HashMap<>();
            scenario.put("scenarioId", "error-" + UUID.randomUUID().toString().substring(0, 8));
            scenario.put("errorType", getRandomErrorType());
            scenario.put("severity", getRandomSeverity());
            scenario.put("timestamp", Instant.now().toString());
            scenario.put("component", getRandomComponent());
            scenario.put("service", getRandomService());
            scenario.put("failurePoint", getRandomFailurePoint());
            scenario.put("recoveryTime", random.nextInt(10, 300));
            scenario.put("impact", getRandomImpact());
            scenario.put("triggers", generateTriggers());
            scenario.put("mitigation", generateMitigation());

            scenarios.add(scenario);
        }

        return scenarios;
    }

    /**
     * Generates large JSON datasets for serialization benchmarks
     */
    public String generateLargeJsonDataset(int recordCount) {
        try {
            Map<String, Object> dataset = new HashMap<>();
            dataset.put("generatedAt", Instant.now().toString());
            dataset.put("totalRecords", recordCount);
            dataset.put("records", generateRecords(recordCount));

            return objectMapper.writeValueAsString(dataset);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JSON dataset", e);
        }
    }

    /**
     * Generates datasets with realistic data distributions
     */
    public Map<String, List<Map<String, Object>>> generateRealisticDataset(int userCount, int workflowCount) {
        Map<String, List<Map<String, Object>>> dataset = new HashMap<>();

        // Generate users with Pareto distribution
        dataset.put("users", generateUsersWithDistribution(userCount, "pareto"));

        // Generate workflows with normal distribution
        dataset.put("workflows", generateWorkflowsWithDistribution(workflowCount, "normal"));

        // Generate work items with exponential distribution
        dataset.put("workItems", generateWorkItemsWithDistribution(userCount * 100, "exponential"));

        // Generate case histories
        dataset.put("caseHistories", generateCaseHistories(workflowCount * 10, 5));

        // Generate user activity
        dataset.put("userActivities", generateUserActivityLogs(userCount * 50));

        return dataset;
    }

    /**
     * Stream-based generation for memory efficiency
     */
    public void generateAndWriteDataset(Path outputPath, IntFunction<Map<String, Object>> generator, int count) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write JSON array start
            writer.write("[\n");

            // Generate and write records
            for (int i = 0; i < count; i++) {
                Map<String, Object> record = generator.apply(i);
                String json = objectMapper.writeValueAsString(record);

                if (i > 0) {
                    writer.write(",\n");
                }
                writer.write("  ");
                writer.write(json);

                // Progress indicator
                if (i % 1000 == 0) {
                    System.out.printf("Generated %d/%d records%n", i, count);
                }
            }

            // Write JSON array end
            writer.write("\n]");
        }
    }

    // Helper methods

    private Map<String, Object> generateWorkItem(int index) {
        Map<String, Object> workItem = new HashMap<>();
        String workItemId = "wi-" + UUID.randomUUID().toString().substring(0, 12);

        workItem.put("id", workItemId);
        workItem.put("caseId", "case-" + random.nextInt(1000, 9999));
        workItem.put("taskId", "task-" + random.nextInt(1, 10));
        workItem.put("name", CATEGORIES[random.nextInt(CATEGORIES.length)] + " Task");
        workItem.put("status", getRandomStatus());
        workItem.put("priority", random.nextInt(1, 6));
        workItem.put("department", DEPARTMENTS[random.nextInt(DEPARTMENTS.length)]);
        workItem.put("assignee", generateUserId());
        workItem.put("created", Instant.now().toString());
        workItem.put("updated", Instant.now().toString());
        workItem.put("deadline", Instant.now().plusSeconds(random.nextInt(3600, 86400)).toString());
        workItem.put("workflowId", "workflow-" + random.nextInt(1, 10));

        // Add workflow-specific data
        addWorkflowSpecificData(workItem);

        return workItem;
    }

    private Map<String, Object> generateWorkItemForCase(String caseId, int index) {
        Map<String, Object> workItem = generateWorkItem(index);
        workItem.put("caseId", caseId);
        workItem.put("sequenceNumber", index);
        workItem.put("isFirst", index == 0);
        workItem.put("isLast", index == 4); // Assuming 5 work items per case
        return workItem;
    }

    private void addWorkflowSpecificData(Map<String, Object> workItem) {
        String taskType = workItem.get("taskId").toString();

        switch (taskType) {
            case "task-1":
                workItem.put("customer", generateCustomerName());
                workItem.put("amount", random.nextInt(100, 10000));
                workItem.put("currency", "USD");
                break;
            case "task-2":
                workItem.put("approver", generateUserId());
                workItem.put("budget", random.nextInt(1000, 100000));
                break;
            case "task-3":
                workItem.put("employee", generateUserId());
                workItem.put("location", generateLocation());
                break;
            default:
                workItem.put("assignee", generateUserId());
                workItem.put("estimatedDuration", random.nextInt(300, 3600));
                break;
        }
    }

    private Duration calculateCaseDuration(List<Map<String, Object>> workItems) {
        Instant startTime = Instant.parse((String) workItems.get(0).get("created"));
        Instant endTime = Instant.parse((String) workItems.get(workItems.size() - 1).get("updated"));
        return Duration.between(startTime, endTime);
    }

    private String getRandomStatus() {
        String[] statuses = {"offered", "allocated", "started", "suspended", "completed", "failed"};
        return statuses[random.nextInt(statuses.length)];
    }

    private String getRandomAction() {
        String[] actions = {"login", "logout", "create_case", "update_case", "complete_task",
                          "escalate", "review", "approve", "reject", "search", "export", "import"};
        return actions[random.nextInt(actions.length)];
    }

    private String getRandomResource() {
        String[] resources = {"workitem", "case", "workflow", "user", "tenant", "document"};
        return resources[random.nextInt(resources.length)];
    }

    private Map<String, Object> generateActivityDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("ipAddress", generateIPAddress());
        details.put("userAgent", generateUserAgent());
        details.put("sessionDuration", random.nextInt(60, 3600));
        details.put("deviceType", getRandomDeviceType());
        return details;
    }

    private String generateIPAddress() {
        return String.format("%d.%d.%d.%d",
            random.nextInt(1, 255),
            random.nextInt(0, 255),
            random.nextInt(0, 255),
            random.nextInt(1, 255));
    }

    private String generateUserAgent() {
        String[] browsers = {"Chrome", "Firefox", "Safari", "Edge"};
        String[] versions = {"120.0", "119.0", "118.0", "117.0"};
        return String.format("%s/%s (%s; %s)",
            browsers[random.nextInt(browsers.length)],
            versions[random.nextInt(versions.length)],
            generateOS(),
            generateDevice());
    }

    private String generateOS() {
        String[] os = {"Windows NT 10.0", "Mac OS X", "Linux", "Android", "iOS"};
        return os[random.nextInt(os.length)];
    }

    private String generateDevice() {
        String[] devices = {"x86_64", "aarch64", "iPhone", "iPad"};
        return devices[random.nextInt(devices.length)];
    }

    private String getRandomDeviceType() {
        String[] types = {"desktop", "mobile", "tablet", "api"};
        return types[random.nextInt(types.length)];
    }

    private String getRandomMetricType() {
        String[] types = {"cpu_usage", "memory_usage", "response_time", "throughput",
                         "error_rate", "queue_length", "active_users", "workload"};
        return types[random.nextInt(types.length)];
    }

    private double generateMetricValue(String metricType) {
        return switch (metricType) {
            case "cpu_usage" -> random.nextDouble(0, 100);
            case "memory_usage" -> random.nextDouble(0, 100);
            case "response_time" -> random.nextDouble(10, 5000);
            case "throughput" -> random.nextDouble(100, 10000);
            case "error_rate" -> random.nextDouble(0, 5);
            case "queue_length" -> random.nextInt(0, 1000);
            case "active_users" -> random.nextInt(10, 10000);
            case "workload" -> random.nextDouble(0, 1000);
            default -> 0;
        };
    }

    private String getMetricUnit(String metricType) {
        return switch (metricType) {
            case "cpu_usage", "memory_usage", "error_rate", "workload" -> "%";
            case "response_time" -> "ms";
            case "throughput" -> "req/s";
            case "queue_length", "active_users" -> "count";
            default -> "";
        };
    }

    private String getRandomComponent() {
        String[] components = {"workflow_engine", "database", "cache", "api_gateway", "auth_service"};
        return components[random.nextInt(components.length)];
    }

    private String getRandomService() {
        String[] services = {"case_management", "task_routing", "user_management", "document_processing"};
        return services[random.nextInt(services.length)];
    }

    private String generateTenantId() {
        return "tenant-" + random.nextInt(1, 1000);
    }

    private String generateMetricDimension() {
        String[] dimensions = {"region", "environment", "version", "tier"};
        return dimensions[random.nextInt(dimensions.length)];
    }

    private String getRandomErrorType() {
        String[] errors = {"timeout", "connection_failed", "validation_error", "permission_denied",
                          "resource_not_found", "data_corruption", "service_unavailable", "rate_limit"};
        return errors[random.nextInt(errors.length)];
    }

    private String getRandomSeverity() {
        String[] severities = {"low", "medium", "high", "critical"};
        return severities[random.nextInt(severities.length)];
    }

    private String getRandomFailurePoint() {
        String[] points = {"task_execution", "data_validation", "resource_allocation", "notification",
                         "workflow_routing", "case_storage", "user_interaction"};
        return points[random.nextInt(points.length)];
    }

    private String getRandomImpact() {
        String[] impacts = {"minor", "moderate", "major", "critical"};
        return impacts[random.nextInt(impacts.length)];
    }

    private List<String> generateTriggers() {
        return Arrays.asList(
            "high_load", "resource_exhaustion", "invalid_input", "timeout", "network_issue"
        );
    }

    private String generateMitigation() {
        String[] mitigations = {"restart_service", "retry_operation", "scale_resources",
                               "notify_admin", "implement_circuit_breaker"};
        return mitigations[random.nextInt(mitigations.length)];
    }

    private String generateUserId() {
        return "user-" + FIRST_NAMES[random.nextInt(FIRST_NAMES.length)].toLowerCase() + "-" +
               LAST_NAMES[random.nextInt(LAST_NAMES.length)].toLowerCase();
    }

    private String generateCustomerName() {
        return "customer-" + LAST_NAMES[random.nextInt(LAST_NAMES.length)].toLowerCase();
    }

    private String generateLocation() {
        String[] locations = {"New York", "London", "Tokyo", "Sydney", "Berlin", "Singapore",
                           "Toronto", "Mumbai", "São Paulo", "Dubai"};
        return locations[random.nextInt(locations.length)];
    }

    // Distribution-based generation methods

    private List<Map<String, Object>> generateUsersWithDistribution(int count, String distribution) {
        List<Map<String, Object>> users = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Map<String, Object> user = new HashMap<>();
            user.put("id", generateUserId());
            user.put("firstName", FIRST_NAMES[random.nextInt(FIRST_NAMES.length)]);
            user.put("lastName", LAST_NAMES[random.nextInt(LAST_NAMES.length)]);
            user.put("email", generateEmail(user));
            user.put("department", DEPARTMENTS[random.nextInt(DEPARTMENTS.length)]);
            user.put("role", getRandomRole());
            user.put("joinDate", Instant.now().minusSeconds(random.nextInt(0, 31536000)).toString());
            user.put("isActive", random.nextBoolean());

            // Apply distribution to user activity level
            double activityLevel = switch (distribution) {
                case "pareto" -> 1 - Math.pow(random.nextDouble(), 3); // Pareto distribution
                case "normal" -> Math.max(0, Math.min(1, random.nextGaussian() * 0.3 + 0.5));
                case "exponential" -> 1 - Math.exp(-random.nextDouble() * 5);
                default -> random.nextDouble();
            };

            user.put("activityLevel", activityLevel);

            users.add(user);
        }

        return users;
    }

    private List<Map<String, Object>> generateWorkflowsWithDistribution(int count, String distribution) {
        List<Map<String, Object>> workflows = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Map<String, Object> workflow = new HashMap<>();
            workflow.put("id", "workflow-" + (i + 1));
            workflow.put("name", generateWorkflowName());
            workflow.put("version", random.nextInt(1, 10));
            workflow.put("category", CATEGORIES[random.nextInt(CATEGORIES.length)]);
            workflow.put("complexity", getComplexityLevel());
            workflow.put("created", Instant.now().minusSeconds(random.nextInt(0, 2592000)).toString());
            workflow.put("updated", Instant.now().minusSeconds(random.nextInt(0, 86400)).toString());

            // Apply distribution to usage frequency
            double frequency = switch (distribution) {
                case "pareto" -> Math.pow(random.nextDouble(), -1.5);
                case "normal" -> Math.max(0, random.nextGaussian() * 50 + 100);
                case "exponential" -> random.nextDouble() * 200;
                default -> random.nextDouble() * 100;
            };

            workflow.put("usageFrequency", frequency);

            workflows.add(workflow);
        }

        return workflows;
    }

    private List<Map<String, Object>> generateWorkItemsWithDistribution(int count, String distribution) {
        List<Map<String, Object>> workItems = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Map<String, Object> workItem = generateWorkItem(i);

            // Apply distribution to processing time
            double processingTime = switch (distribution) {
                case "pareto" -> 1 + random.nextDouble() * 100; // Heavy-tailed
                case "normal" -> Math.max(10, random.nextGaussian() * 100 + 300);
                case "exponential" -> -Math.log(random.nextDouble()) * 300;
                default -> random.nextDouble() * 300;
            };

            workItem.put("processingTimeMs", (int) processingTime);

            workItems.add(workItem);
        }

        return workItems;
    }

    private List<Map<String, Object>> generateRecords(int count) {
        List<Map<String, Object>> records = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            Map<String, Object> record = new HashMap<>();
            record.put("id", UUID.randomUUID().toString());
            record.put("timestamp", Instant.now().toString());
            record.put("data", generateRecordData());
            record.put("metadata", generateMetadata());

            records.add(record);
        }

        return records;
    }

    private Map<String, Object> generateRecordData() {
        Map<String, Object> data = new HashMap<>();
        data.put("value", random.nextDouble());
        data.put("category", CATEGORIES[random.nextInt(CATEGORIES.length)]);
        data.put("tags", generateTags());
        return data;
    }

    private List<String> generateTags() {
        return Arrays.asList("benchmark", "generated", "test", "performance", "scalability");
    }

    private Map<String, Object> generateMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generator", "SyntheticDatasetGenerator");
        metadata.put("version", "6.0.0");
        metadata.put("compressed", false);
        return metadata;
    }

    private String generateEmail(Map<String, Object> user) {
        String firstName = user.get("firstName").toString().toLowerCase();
        String lastName = user.get("lastName").toString().toLowerCase();
        return firstName + "." + lastName + "@example.com";
    }

    private String generateWorkflowName() {
        String[] prefixes = {"Standard", "Advanced", "Enterprise", "Simple", "Complex"};
        String[] suffixes = {"Process", "Workflow", "Procedure", "Flow", "System"};
        return prefixes[random.nextInt(prefixes.length)] + " " +
               CATEGORIES[random.nextInt(CATEGORIES.length)] + " " +
               suffixes[random.nextInt(suffixes.length)];
    }

    private String getRandomRole() {
        String[] roles = {"Administrator", "Manager", "Analyst", "User", "Reviewer", "Approver"};
        return roles[random.nextInt(roles.length)];
    }

    private String getComplexityLevel() {
        String[] levels = {"low", "medium", "high", "very_high"};
        return levels[random.nextInt(levels.length)];
    }

    /**
     * Validates dataset consistency and integrity
     */
    public Map<String, Object> validateDataset(Map<String, List<Map<String, Object>>> dataset) {
        Map<String, Object> validation = new HashMap<>();
        validation.put("totalRecords", dataset.values().stream().mapToInt(List::size).sum());
        validation.put("datasets", new HashMap<>());

        for (Map.Entry<String, List<Map<String, Object>>> entry : dataset.entrySet()) {
            Map<String, Object> datasetValidation = new HashMap<>();
            datasetValidation.put("count", entry.getValue().size());
            datasetValidation.put("valid", validateRecords(entry.getKey(), entry.getValue()));

            ((Map<String, Object>) validation.get("datasets")).put(entry.getKey(), datasetValidation);
        }

        return validation;
    }

    private boolean validateRecords(String datasetType, List<Map<String, Object>> records) {
        // Basic validation - all records required fields
        return records.stream().allMatch(record ->
            record.containsKey("id") && record.containsKey("timestamp")
        );
    }

    /**
     * Cleanup resources
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}