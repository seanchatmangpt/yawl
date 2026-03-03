package org.yawlfoundation.yawl.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

/**
 * Example of how to integrate ReceiptChain with YAWL self-play workflows.
 * Demonstrates tracking deltas for workflow execution intelligence.
 */
public class YAWLIntegrationExample {

    private final ReceiptChain receiptChain;

    public YAWLIntegrationExample() {
        this.receiptChain = new ReceiptChain();
    }

    /**
     * Records a workflow execution delta
     */
    public void recordWorkflowExecution(String caseId, String workItemId, String taskId,
                                        String status, String... additionalData) {
        List<String> delta = new ArrayList<>();
        delta.add("workflow_event");
        delta.add("case_id:" + caseId);
        delta.add("workitem_id:" + workItemId);
        delta.add("task_id:" + taskId);
        delta.add("status:" + status);
        delta.add("timestamp:" + Instant.now());

        // Add additional data if provided
        if (additionalData != null) {
            for (String data : additionalData) {
                delta.add(data);
            }
        }

        receiptChain.appendReceipt(delta);
        System.out.println("Recorded workflow event: " + status + " for case " + caseId);
    }

    /**
     * Records an error in workflow execution
     */
    public void recordError(String caseId, String errorType, String errorDetails, String stackTraceHash) {
        List<String> delta = List.of(
            "error_event",
            "case_id:" + caseId,
            "error_type:" + errorType,
            "error_details:" + errorDetails,
            "stack_trace_hash:" + stackTraceHash,
            "timestamp:" + Instant.now().toString()
        );

        receiptChain.appendReceipt(delta);
        System.out.println("Recorded error: " + errorType + " in case " + caseId);
    }

    /**
     * Records a performance metric
     */
    public void recordPerformanceMetric(String metricName, long durationMs, String context) {
        List<String> delta = List.of(
            "performance_metric",
            "metric:" + metricName,
            "duration_ms:" + durationMs,
            "context:" + context,
            "timestamp:" + Instant.now().toString()
        );

        receiptChain.appendReceipt(delta);
        System.out.println("Recorded performance: " + metricName + " took " + durationMs + "ms");
    }

    /**
     * Validates the integrity of the receipt chain
     */
    public boolean validateIntegrity() {
        boolean isValid = receiptChain.validateChain();
        if (!isValid) {
            System.err.println("Receipt chain integrity validation failed!");
            return false;
        }
        return true;
    }

    /**
     * Gets the latest entry for debugging
     */
    public Receipt getLatestEntry() {
        return receiptChain.getHead();
    }

    /**
     * Demonstrates the integration with sample data
     */
    public static void main(String[] args) {
        System.out.println("=== YAWL Integration Example ===\n");

        YAWLIntegrationExample integration = new YAWLIntegrationExample();

        // Initialize the receipt chain with genesis
        List<String> genesisDelta = List.of("genesis", "initialized", "timestamp:" + Instant.now());
        integration.receiptChain.createGenesis(genesisDelta);

        // Simulate workflow events
        integration.recordWorkflowExecution(
            "CASE-123",
            "WI-001",
            "Task1",
            "created"
        );

        integration.recordWorkflowExecution(
            "CASE-123",
            "WI-001",
            "Task1",
            "completed",
            "execution_time:2500ms",
            "resources_used:cpu_25%,mem_100MB"
        );

        // Record a performance metric
        integration.recordPerformanceMetric("task_completion", 2500, "Task1");

        // Record an error
        integration.recordError(
            "CASE-124",
            "TIMEOUT",
            "Task did not complete within timeout period",
            "abc123hash"
        );

        // Validate integrity
        System.out.println("\n=== Chain Integrity Check ===");
        boolean valid = integration.validateIntegrity();
        System.out.println("Chain integrity: " + (valid ? "VALID" : "INVALID"));

        // Show the latest entry
        System.out.println("\n=== Latest Entry ===");
        Receipt latest = integration.getLatestEntry();
        if (latest != null) {
            System.out.println("Hash: " + latest.hash());
            System.out.println("Delta: " + latest.delta());
            System.out.println("Timestamp: " + latest.timestamp());
        }

        // Show chain summary
        System.out.println("\n=== Chain Summary ===");
        System.out.println(integration.receiptChain.getSummary());

        System.out.println("\n=== Integration Complete ===");
    }
}
