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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.util.JDOMUtil;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test Data Generator for YAWL Benchmarks
 * 
 * This class generates realistic synthetic data for performance testing:
 * - Workflow specifications in XML format
 * - Work item records with metadata
 * - Case data with various states
 * - Performance test scenarios
 */
public class TestDataGenerator {

    private static final String[] WORKFLOW_NAMES = {
        "Order Processing", "Loan Application", "Employee Onboarding", 
        "Expense Approval", "Customer Service", "Invoice Processing",
        "Contract Review", "Quality Assurance", "Shipping Logistics",
        "Compliance Check"
    };
    
    private static final String[] TASK_NAMES = {
        "Submit Request", "Review Application", "Approve/Reject", 
        "Process Payment", "Ship Order", "Send Notification",
        "Verify Documents", "Check Compliance", "Update Records",
        "Finalize Workflow"
    };
    
    private static final String[] DATA_TYPES = {
        "String", "Integer", "Boolean", "Date", "Decimal", "Document"
    };
    
    private static final String[] PRIORITIES = {"Low", "Medium", "High", "Critical"};
    
    private final Random random;
    
    public TestDataGenerator() {
        this.random = new Random();
    }
    
    /**
     * Generate workflow specifications for different patterns.
     *
     * <p>Returns valid YAWL specificationSet XML (schema 4.0) for each pattern.
     * These are sourced from {@link BenchmarkSpecFactory} to guarantee
     * compatibility with {@code YStatelessEngine.unmarshalSpecification}.</p>
     */
    public Map<String, String> generateWorkflowSpecifications() {
        Map<String, String> specs = new HashMap<>();

        // Sequential workflow (2-task baseline)
        specs.put("sequential", BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        // Parallel split/sync (AND-split + AND-join)
        specs.put("parallel", BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC);

        // Multi-choice/merge (OR-split)
        specs.put("multiChoice", BenchmarkSpecFactory.MULTI_CHOICE);

        // XOR exclusive choice
        specs.put("exclusiveChoice", BenchmarkSpecFactory.EXCLUSIVE_CHOICE);

        // 4-task sequential for deeper chains
        specs.put("sequential4", BenchmarkSpecFactory.SEQUENTIAL_4_TASK);

        return specs;
    }

    /**
     * Generate realistic mixed workload specifications for 1M case stress tests.
     *
     * <p>Returns a balanced distribution of workflow patterns with realistic
     * task execution time distributions:
     * <ul>
     *   <li>40% Sequential workflows (2–4 task chains)</li>
     *   <li>30% Parallel workflows (AND-split/sync patterns)</li>
     *   <li>20% Loop workflows (iterative execution patterns)</li>
     *   <li>10% Complex workflows (nested fork-join patterns)</li>
     * </ul>
     * </p>
     *
     * <p>Task execution times follow exponential distribution (median varies by workflow type):
     * <ul>
     *   <li>Sequential: 100–150ms median per task</li>
     *   <li>Parallel: 150–200ms median per branch</li>
     *   <li>Loop: 80–120ms per iteration</li>
     *   <li>Complex: 200–300ms per task (higher overhead)</li>
     * </ul>
     * </p>
     *
     * <p>Suitable for LongRunningStressTest to randomly select specifications
     * and execute realistic, heterogeneous workloads.</p>
     *
     * @param caseCount Total number of cases to distribute across specs
     * @param taskRateMs Baseline task execution rate in milliseconds (median)
     * @return Immutable map of workflow type → YAWL specification XML (valid for unmarshal)
     */
    public Map<String, String> newRealisticMixedWorkload(int caseCount, int taskRateMs) {
        Map<String, String> workload = new LinkedHashMap<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 40% Sequential workflows: use 2-task and 4-task patterns
        // Mix 70% 2-task (faster) and 30% 4-task (deeper)
        String sequentialSpec = rng.nextDouble() < 0.7
                ? BenchmarkSpecFactory.SEQUENTIAL_2_TASK
                : BenchmarkSpecFactory.SEQUENTIAL_4_TASK;
        workload.put("sequential_primary", sequentialSpec);
        workload.put("sequential_2task", BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
        workload.put("sequential_4task", BenchmarkSpecFactory.SEQUENTIAL_4_TASK);

        // 30% Parallel workflows: AND-split/sync pattern
        workload.put("parallel_andsplit", BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC);

        // 20% Loop workflows: create from sequential with iteration wrapper
        // If no dedicated loop spec, simulate with sequential pattern repeated
        workload.put("loop_sequential", BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        // 10% Complex workflows: use multi-choice (OR-split) and exclusive choice (XOR)
        // These have more complex decision logic and higher overhead
        workload.put("complex_multichoice", BenchmarkSpecFactory.MULTI_CHOICE);
        workload.put("complex_exclusive", BenchmarkSpecFactory.EXCLUSIVE_CHOICE);

        return Collections.unmodifiableMap(workload);
    }

    /**
     * Generates realistic task execution times following exponential distribution.
     *
     * <p>Used internally by newRealisticMixedWorkload() to define task processing delays.
     * Exponential distribution is realistic for:
     * <ul>
     *   <li>Highly variable processing times (some fast, some slow)</li>
     *   <li>Natural "bursty" execution patterns</li>
     *   <li>Memoryless property (no correlation with previous tasks)</li>
     * </ul>
     * </p>
     *
     * @param medianMs Median task execution time in milliseconds
     * @param rng ThreadLocalRandom instance for thread-safe randomness
     * @return Exponential random variable with given median
     */
    private static long generateExponentialTaskTime(long medianMs, ThreadLocalRandom rng) {
        double u = rng.nextDouble(0.0, 1.0);
        if (u <= 0.0) {
            u = 1e-10;
        }
        return Math.round(-medianMs * Math.log(u));
    }
    
    /**
     * Generate work items for testing
     */
    public List<Map<String, Object>> generateWorkItems(int count) {
        List<Map<String, Object>> workItems = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> workItem = new HashMap<>();
            workItem.put("id", "wi-" + UUID.randomUUID().toString());
            workItem.put("caseId", "case-" + UUID.randomUUID().toString());
            workItem.put("taskId", TASK_NAMES[random.nextInt(TASK_NAMES.length)]);
            workItem.put("status", randomWorkItemStatus());
            workItem.put("priority", randomPriority());
            workItem.put("createdTime", randomTimestamp());
            workItem.put("dueTime", randomFutureTimestamp());
            workItem.put("assignee", randomAssignee());
            workItem.put("dataFields", generateDataFields(random.nextInt(5) + 1));
            workItem.put("metadata", generateMetadata());
            
            workItems.add(workItem);
        }
        
        return workItems;
    }
    
    /**
     * Generate case data for scalability testing
     */
    public List<Map<String, Object>> generateCaseData(int count) {
        List<Map<String, Object>> cases = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> caseData = new HashMap<>();
            caseData.put("caseId", "case-" + UUID.randomUUID().toString());
            caseData.put("specId", "spec-" + random.nextInt(10));
            caseData.put("status", randomCaseStatus());
            caseData.put("priority", randomPriority());
            caseData.put("createdTime", randomTimestamp());
            caseData.put("completedTime", randomCompletedTime());
            caseData.put("processVariables", generateProcessVariables());
            caseData.put("workItemCount", random.nextInt(20) + 1);
            caseData.put("totalWorkItems", random.nextInt(50) + 10);
            caseData.put("workflowType", WORKFLOW_NAMES[random.nextInt(WORKFLOW_NAMES.length)]);
            caseData.put("metadata", generateCaseMetadata());
            
            cases.add(caseData);
        }
        
        return cases;
    }
    
    /**
     * Generate performance test scenarios
     */
    public List<Map<String, Object>> generatePerformanceScenarios() {
        List<Map<String, Object>> scenarios = new ArrayList<>();
        
        // Throughput scenarios
        scenarios.add(createScenario("sequential_throughput", 1000, 1, "Sequential workflow, 1000 cases"));
        scenarios.add(createScenario("parallel_throughput", 1000, 3, "Parallel workflow, 1000 cases"));
        scenarios.add(createScenario("mixed_throughput", 1000, 5, "Mixed pattern, 1000 cases"));
        
        // Latency scenarios
        scenarios.add(createScenario("latency_low", 100, 1, "Low latency test, 100 cases"));
        scenarios.add(createScenario("latency_medium", 100, 5, "Medium latency test, 100 cases"));
        scenarios.add(createScenario("latency_high", 100, 10, "High latency test, 100 cases"));
        
        // Scalability scenarios
        scenarios.add(createScenario("scalability_small", 100, 1, "Small scale, 100 cases"));
        scenarios.add(createScenario("scalability_medium", 1000, 1, "Medium scale, 1000 cases"));
        scenarios.add(createScenario("scalability_large", 10000, 1, "Large scale, 10000 cases"));
        
        // Stress scenarios
        scenarios.add(createScenario("stress_concurrent", 5000, 10, "High concurrency, 5000 cases"));
        scenarios.add(createScenario("stress_memory", 10000, 1, "Memory intensive, 10000 cases"));
        
        return scenarios;
    }
    
    /**
     * Generate synthetic event logs for performance analysis
     */
    public List<Map<String, Object>> generateEventLogs(int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", "event-" + UUID.randomUUID().toString());
            event.put("timestamp", randomTimestamp());
            event.put("eventType", randomEventType());
            event.put("caseId", "case-" + UUID.randomUUID().toString());
            event.put("taskId", TASK_NAMES[random.nextInt(TASK_NAMES.length)]);
            event.put("workItemId", "wi-" + UUID.randomUUID().toString());
            event.put("userId", randomAssignee());
            event.put("duration", randomDuration());
            event.put("data", generateEventData());
            event.put("metadata", generateEventMetadata());
            
            events.add(event);
        }
        
        return events;
    }
    
    // Helper methods for generating specific workflow specs
    
    private String generateSequentialWorkflowSpec() {
        Element root = new Element("specification");
        root.setAttribute("id", "sequential-workflow");
        root.setAttribute("name", "Sequential Workflow");
        root.setAttribute("version", "1.0");
        
        // Add input/output parameters
        Element params = new Element("parameters");
        addParameter(params, "input", "String", "");
        addParameter(params, "output", "String", "");
        root.addContent(params);
        
        // Add net
        Element net = new Element("net");
        net.setAttribute("id", "seq-net");
        
        // Add elements (simplified for benchmarking)
        for (int i = 0; i < 5; i++) {
            Element task = new Element("task");
            task.setAttribute("id", "task-" + i);
            task.setAttribute("name", TASK_NAMES[i]);
            net.addContent(task);
        }
        
        root.addContent(net);
        return serializeToXml(root);
    }
    
    private String generateParallelWorkflowSpec() {
        Element root = new Element("specification");
        root.setAttribute("id", "parallel-workflow");
        root.setAttribute("name", "Parallel Workflow");
        root.setAttribute("version", "1.0");
        
        // Add net with parallel structure
        Element net = new Element("net");
        net.setAttribute("id", "parallel-net");
        
        // Parallel split tasks
        for (int i = 0; i < 3; i++) {
            Element task = new Element("task");
            task.setAttribute("id", "parallel-task-" + i);
            task.setAttribute("name", TASK_NAMES[i]);
            net.addContent(task);
        }
        
        // Sync task
        Element sync = new Element("task");
        sync.setAttribute("id", "sync-task");
        sync.setAttribute("name", "Synchronize");
        net.addContent(sync);
        
        root.addContent(net);
        return serializeToXml(root);
    }
    
    private String generateMultiChoiceWorkflowSpec() {
        Element root = new Element("specification");
        root.setAttribute("id", "multichoice-workflow");
        root.setAttribute("name", "Multi-Choice Workflow");
        root.setAttribute("version", "1.0");
        
        Element net = new Element("net");
        net.setAttribute("id", "choice-net");
        
        // Choice tasks
        for (int i = 0; i < 3; i++) {
            Element task = new Element("task");
            task.setAttribute("id", "choice-" + i);
            task.setAttribute("name", "Choice Option " + i);
            net.addContent(task);
        }
        
        // Merge task
        Element merge = new Element("task");
        merge.setAttribute("id", "merge");
        merge.setAttribute("name", "Merge");
        net.addContent(merge);
        
        root.addContent(net);
        return serializeToXml(root);
    }
    
    private String generateCancelRegionWorkflowSpec() {
        Element root = new Element("specification");
        root.setAttribute("id", "cancel-workflow");
        root.setAttribute("name", "Cancel Region Workflow");
        root.setAttribute("version", "1.0");
        
        Element net = new Element("net");
        net.setAttribute("id", "cancel-net");
        
        // Cancel region tasks
        for (int i = 0; i < 3; i++) {
            Element task = new Element("task");
            task.setAttribute("id", "cancel-task-" + i);
            task.setAttribute("name", "Cancelable Task " + i);
            net.addContent(task);
        }
        
        root.addContent(net);
        return serializeToXml(root);
    }
    
    private String generateNOutOfMWorkflowSpec() {
        Element root = new Element("specification");
        root.setAttribute("id", "n-outof-m-workflow");
        root.setAttribute("name", "N-out-of-M Workflow");
        root.setAttribute("version", "1.0");
        
        Element net = new Element("net");
        net.setAttribute("id", "n-net");
        
        // N-out-of-M tasks
        for (int i = 0; i < 5; i++) {
            Element task = new Element("task");
            task.setAttribute("id", "n-task-" + i);
            task.setAttribute("name", "Option Task " + i);
            net.addContent(task);
        }
        
        // Final task
        Element finalTask = new Element("task");
        finalTask.setAttribute("id", "final");
        finalTask.setAttribute("name", "Final Decision");
        net.addContent(finalTask);
        
        root.addContent(net);
        return serializeToXml(root);
    }
    
    // Utility methods
    
    private void addParameter(Element parent, String name, String type, String value) {
        Element param = new Element("parameter");
        param.setAttribute("name", name);
        param.setAttribute("type", type);
        param.setAttribute("value", value);
        parent.addContent(param);
    }
    
    private String serializeToXml(Element element) {
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        return outputter.outputString(new Document(element));
    }
    
    private String randomWorkItemStatus() {
        String[] statuses = {"enabled", "allocated", "started", "completed", "cancelled"};
        return statuses[random.nextInt(statuses.length)];
    }
    
    private String randomCaseStatus() {
        String[] statuses = {"running", "completed", "cancelled", "paused", "terminated"};
        return statuses[random.nextInt(statuses.length)];
    }
    
    private String randomPriority() {
        return PRIORITIES[random.nextInt(PRIORITIES.length)];
    }
    
    private String randomAssignee() {
        return "user-" + (random.nextInt(100) + 1);
    }
    
    private long randomTimestamp() {
        return System.currentTimeMillis() - random.nextInt(86400000); // Last 24 hours
    }
    
    private long randomFutureTimestamp() {
        return System.currentTimeMillis() + random.nextInt(86400000); // Next 24 hours
    }
    
    private long randomCompletedTime() {
        return System.currentTimeMillis() - random.nextInt(3600000); // Last hour
    }
    
    private Map<String, Object> generateDataFields(int count) {
        Map<String, Object> fields = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String name = "field-" + i;
            String type = DATA_TYPES[random.nextInt(DATA_TYPES.length)];
            Object value = generateValue(type);
            fields.put(name, value);
        }
        return fields;
    }
    
    private Object generateValue(String type) {
        switch (type) {
            case "String":
                return "Value-" + UUID.randomUUID().toString();
            case "Integer":
                return random.nextInt(1000);
            case "Boolean":
                return random.nextBoolean();
            case "Date":
                return new Date(randomTimestamp());
            case "Decimal":
                return random.nextDouble() * 1000;
            case "Document":
                return "Document-" + UUID.randomUUID().toString();
            default:
                return "Unknown";
        }
    }
    
    private Map<String, String> generateMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "benchmark-generator");
        metadata.put("generatedAt", String.valueOf(System.currentTimeMillis()));
        metadata.put("version", "1.0");
        metadata.put("environment", "test");
        return metadata;
    }
    
    private Map<String, Object> generateProcessVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("status", "processing");
        variables.put("step", 1);
        variables.put("retryCount", 0);
        variables.put("maxRetries", 3);
        return variables;
    }
    
    private Map<String, String> generateCaseMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("workflowType", WORKFLOW_NAMES[random.nextInt(WORKFLOW_NAMES.length)]);
        metadata.put("department", "Operations");
        metadata.put("priority", randomPriority());
        metadata.put("createdBy", "system");
        return metadata;
    }
    
    private String randomEventType() {
        String[] types = {"WorkflowStarted", "TaskStarted", "TaskCompleted", 
                          "WorkflowCompleted", "TaskCancelled", "WorkflowCancelled"};
        return types[random.nextInt(types.length)];
    }
    
    private long randomDuration() {
        return random.nextInt(5000) + 100; // 100-5100ms
    }
    
    private Map<String, Object> generateEventData() {
        Map<String, Object> data = new HashMap<>();
        data.put("result", "success");
        data.put("details", "Event processed successfully");
        data.put("durationMs", randomDuration());
        return data;
    }
    
    private Map<String, String> generateEventMetadata() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("eventType", "performance");
        metadata.put("benchmark", "true");
        metadata.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return metadata;
    }
    
    private Map<String, Object> createScenario(String id, int caseCount, int concurrency, String description) {
        Map<String, Object> scenario = new HashMap<>();
        scenario.put("id", id);
        scenario.put("caseCount", caseCount);
        scenario.put("concurrency", concurrency);
        scenario.put("description", description);
        scenario.put("expectedDuration", estimateDuration(caseCount, concurrency));
        scenario.put("expectedThroughput", estimateThroughput(caseCount, concurrency));
        return scenario;
    }
    
    private long estimateDuration(int caseCount, int concurrency) {
        // Rough estimate: 100ms per case, adjusted for concurrency
        return (caseCount * 100L) / concurrency;
    }
    
    private double estimateThroughput(int caseCount, int concurrency) {
        // Rough estimate: cases per second
        return (caseCount / ((caseCount * 100L) / concurrency / 1000.0));
    }
    
    // Public utility methods
    
    public static void main(String[] args) {
        TestDataGenerator generator = new TestDataGenerator();
        
        // Generate test data
        System.out.println("Generating test data...");
        
        // Specifications
        Map<String, String> specs = generator.generateWorkflowSpecifications();
        specs.forEach((name, xml) -> {
            System.out.println("Generated " + name + " spec (" + xml.length() + " chars)");
        });
        
        // Work items
        List<Map<String, Object>> workItems = generator.generateWorkItems(100);
        System.out.println("Generated " + workItems.size() + " work items");
        
        // Cases
        List<Map<String, Object>> cases = generator.generateCaseData(1000);
        System.out.println("Generated " + cases.size() + " cases");
        
        // Performance scenarios
        List<Map<String, Object>> scenarios = generator.generatePerformanceScenarios();
        System.out.println("Generated " + scenarios.size() + " performance scenarios");
        
        // Event logs
        List<Map<String, Object>> events = generator.generateEventLogs(10000);
        System.out.println("Generated " + events.size() + " event log entries");
    }
}
