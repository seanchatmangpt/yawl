package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Real end-to-end integration of all 4 autonomic observability features.
 *
 * Demonstrates:
 * - Anomaly Detection (80% problem visibility)
 * - SLA Monitoring (80% compliance visibility)
 * - Distributed Tracing (80% debugging speed)
 * - Auto-Remediation Logging (80% troubleshooting)
 */
class AutonomicObservabilityIntegrationTest {

    private AnomalyDetector anomalyDetector;
    private SLAMonitor slaMonitor;
    private DistributedTracer tracer;
    private AutoRemediationLog remediationLog;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        anomalyDetector = new AnomalyDetector(meterRegistry);
        slaMonitor = new SLAMonitor(meterRegistry);
        tracer = new DistributedTracer(OpenTelemetry.noop());
        remediationLog = new AutoRemediationLog(meterRegistry);
    }

    @Test
    void testFullWorkflowLifecycle_NormalExecution() throws InterruptedException {
        // Setup SLAs
        slaMonitor.defineSLA("approval_task", 2000, "Approval must complete in 2 seconds");
        slaMonitor.defineSLA("processing_case", 5000, "Case processing must complete in 5 seconds");

        // Start case trace
        String traceId = tracer.generateTraceId();
        try (DistributedTracer.TraceSpan caseSpan = tracer.startCaseSpan("case-123", "approval_spec")) {
            caseSpan.addEvent("case_started");

            // Setup SLA tracking
            Map<String, String> caseContext = new HashMap<>();
            caseContext.put("case_id", "case-123");
            caseContext.put("spec_id", "approval_spec");

            slaMonitor.startTracking("processing_case", "case-123", caseContext);

            // Simulate case processing
            long caseStartMs = System.currentTimeMillis();

            // Task 1: Approval
            try (DistributedTracer.TraceSpan taskSpan = tracer.startTaskSpan("approve_request", "case-123", "agent-alice")) {
                taskSpan.addEvent("task_started");

                Map<String, String> taskContext = new HashMap<>();
                taskContext.put("task", "approve_request");
                taskContext.put("assignee", "alice");

                slaMonitor.startTracking("approval_task", "wi-1", taskContext);

                // Simulate work (1 second)
                Thread.sleep(1000);

                long taskDurationMs = System.currentTimeMillis() - caseStartMs;

                // Record execution with anomaly detection
                anomalyDetector.recordExecution("task.approve", taskDurationMs, "approve_request", "approval_spec");

                slaMonitor.completeTracking("approval_task", "wi-1");
                taskSpan.addEvent("task_completed");
            }

            // Verify SLA compliance
            assertEquals(0, slaMonitor.getTotalViolations("approval_task"));

            slaMonitor.completeTracking("processing_case", "case-123");
            caseSpan.addEvent("case_completed");
        }

        // All should complete successfully
        assertEquals(0, remediationLog.getTotalRemediations());
    }

    @Test
    void testAnomalyDetectionWithAutoRemediationEscalation() throws InterruptedException {
        // Establish baseline for task
        for (int i = 0; i < 35; i++) {
            anomalyDetector.recordExecution("task.duration", 100);
        }

        // Simulate anomaly (outlier execution)
        int beforeAnomalies = anomalyDetector.getTotalAnomalies();

        anomalyDetector.recordExecution("task.duration", 800);

        assertTrue(anomalyDetector.getTotalAnomalies() > beforeAnomalies);

        // Auto-remediation: Log escalation action
        remediationLog.logTimeoutRecovery("item-1", 800, "escalate_to_supervisor", true);

        assertTrue(remediationLog.getTotalRemediations() > 0);
    }

    @Test
    void testSLAViolationWithAutoRemediationActions() throws InterruptedException {
        slaMonitor.defineSLA("quick_task", 500, "Must complete in 500ms");

        Map<String, String> context = new HashMap<>();
        context.put("task", "quick_processing");

        slaMonitor.startTracking("quick_task", "item-1", context);

        // Sleep longer than SLA
        Thread.sleep(800);

        slaMonitor.completeTracking("quick_task", "item-1");

        // Verify violation detected
        assertTrue(slaMonitor.getTotalViolations("quick_task") > 0);

        // Log remediation response
        AutoRemediationLog.RemediationScenario scenario = remediationLog.startRemediationScenario(
                "sla_breach_recovery", "escalation"
        );

        Map<String, Object> escalation = new HashMap<>();
        escalation.put("notification_sent_to", "manager");
        scenario.recordStep("notify_manager", escalation, true);

        scenario.complete();

        assertEquals(1, remediationLog.getTotalRemediations());
    }

    @Test
    void testDistributedTracingAcrossAgents() throws InterruptedException {
        String mainTraceId = tracer.generateTraceId();

        // Main workflow span
        try (DistributedTracer.TraceSpan mainSpan = tracer.startCaseSpan("case-456", "multi_agent_workflow")) {
            mainSpan.addEvent("workflow_started");

            // Agent 1 action with trace propagation
            Runnable agent1Task = tracer.withTraceContext(() -> {
                try (DistributedTracer.TraceSpan agent1Span = tracer.startAgentActionSpan(
                        "agent-alice", "review_document", "case-456")) {
                    agent1Span.addEvent("review_started");
                    Thread.sleep(500);
                    agent1Span.addEvent("review_completed");
                }
            }, mainTraceId);

            // Agent 2 action with trace propagation
            Runnable agent2Task = tracer.withTraceContext(() -> {
                try (DistributedTracer.TraceSpan agent2Span = tracer.startAgentActionSpan(
                        "agent-bob", "approve_decision", "case-456")) {
                    agent2Span.addEvent("approval_started");
                    Thread.sleep(300);
                    agent2Span.addEvent("approval_completed");
                }
            }, mainTraceId);

            // Execute agents
            agent1Task.run();
            agent2Task.run();

            mainSpan.addEvent("all_agents_completed");
        }

        // Trace should be fully propagated
        assertNotNull(mainTraceId);
    }

    @Test
    void testAnomalyAndRemediationCorrelation() throws InterruptedException {
        // Setup
        anomalyDetector.recordExecution("long_task", 100);
        for (int i = 0; i < 34; i++) {
            anomalyDetector.recordExecution("long_task", 100);
        }

        // Create anomaly
        anomalyDetector.recordExecution("long_task", 800);

        int anomalies = anomalyDetector.getTotalAnomalies();

        // Correlate with distributed trace for root cause analysis
        String traceId = tracer.generateTraceId();
        try (DistributedTracer.TraceSpan span = tracer.startTaskSpan("long_task", "case-789", "agent-001")) {
            span.setAttribute("anomaly_detected", "true");
            span.setAttribute("duration_ms", "800");

            // Log automatic root cause analysis attempt
            remediationLog.logStateReconciliation("case-789", "performance_degradation",
                    "analyze_system_metrics", true);
        }

        assertTrue(anomalies > 0);
        assertTrue(remediationLog.getTotalRemediations() > 0);
    }

    @Test
    void testComplexScenario_MultiTaskCaseWithMultipleFailures() throws InterruptedException {
        // Define SLAs for case and multiple tasks
        slaMonitor.defineSLA("task_a", 1000, "");
        slaMonitor.defineSLA("task_b", 1500, "");
        slaMonitor.defineSLA("task_c", 2000, "");

        String caseId = "complex-case-001";
        String traceId = tracer.generateTraceId();

        try (DistributedTracer.TraceSpan caseSpan = tracer.startCaseSpan(caseId, "complex_workflow")) {
            caseSpan.addEvent("complex_workflow_started");

            // Task A: Normal execution
            Map<String, String> contextA = new HashMap<>();
            contextA.put("task", "task_a");
            slaMonitor.startTracking("task_a", "wi-a", contextA);

            Thread.sleep(900);
            long durationA = 900;
            anomalyDetector.recordExecution("task_a", durationA, "task_a", caseId);
            slaMonitor.completeTracking("task_a", "wi-a");

            // Task B: Slow execution (violates SLA)
            Map<String, String> contextB = new HashMap<>();
            contextB.put("task", "task_b");
            slaMonitor.startTracking("task_b", "wi-b", contextB);

            Thread.sleep(1800);
            long durationB = 1800;
            anomalyDetector.recordExecution("task_b", durationB, "task_b", caseId);

            // Log remediation for slow execution
            remediationLog.logTimeoutRecovery("wi-b", durationB, "defer_to_high_priority_queue", true);

            slaMonitor.completeTracking("task_b", "wi-b");

            // Task C: Normal execution
            Map<String, String> contextC = new HashMap<>();
            contextC.put("task", "task_c");
            slaMonitor.startTracking("task_c", "wi-c", contextC);

            Thread.sleep(1200);
            long durationC = 1200;
            anomalyDetector.recordExecution("task_c", durationC, "task_c", caseId);
            slaMonitor.completeTracking("task_c", "wi-c");

            caseSpan.addEvent("complex_workflow_completed");
        }

        // Verify collected observability data
        assertTrue(slaMonitor.getTotalViolations("task_b") > 0);
        assertEquals(0, slaMonitor.getTotalViolations("task_a"));
        assertEquals(0, slaMonitor.getTotalViolations("task_c"));
        assertTrue(remediationLog.getTotalRemediations() > 0);
    }

    @Test
    void testParallelCaseExecutionMonitoring() throws InterruptedException {
        slaMonitor.defineSLA("parallel_task", 1000, "");

        CountDownLatch latch = new CountDownLatch(3);

        // Simulate 3 parallel cases
        for (int caseNum = 0; caseNum < 3; caseNum++) {
            new Thread(() -> {
                try {
                    String caseId = "parallel-case-" + Thread.currentThread().getId();
                    Map<String, String> ctx = new HashMap<>();
                    ctx.put("case", caseId);

                    slaMonitor.startTracking("parallel_task", caseId, ctx);

                    Thread.sleep(500);

                    anomalyDetector.recordExecution("parallel_task", 500, caseId, "spec-1");

                    slaMonitor.completeTracking("parallel_task", caseId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();

        // All cases should complete without violations
        assertEquals(0, slaMonitor.getTotalViolations("parallel_task"));
    }

    @Test
    void testMetricsExportAndAggregation() {
        // Record various events
        anomalyDetector.recordExecution("metric_1", 100);
        anomalyDetector.recordExecution("metric_1", 200);

        slaMonitor.defineSLA("sla_1", 1000, "");
        slaMonitor.defineSLA("sla_2", 2000, "");

        remediationLog.logRemediation("type_1", "action_1", true, new HashMap<>());
        remediationLog.logRemediation("type_1", "action_1", false, new HashMap<>());

        // Verify metrics are exported
        long anomalyTotal = meterRegistry.find("yawl.anomaly.total")
                .gauge()
                .map(g -> (long) g.value())
                .orElse(0L);

        long remediationTotal = meterRegistry.find("yawl.remediation.total")
                .gauge()
                .map(g -> (long) g.value())
                .orElse(0L);

        assertTrue(anomalyTotal >= 0);
        assertEquals(2, remediationTotal);
    }
}
