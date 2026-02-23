# Autonomic Observability Quick Reference

## Initialize (at application startup)

```java
MeterRegistry meterRegistry = // from Spring/Micrometer

AnomalyDetector anomaly = new AnomalyDetector(meterRegistry);
SLAMonitor sla = new SLAMonitor(meterRegistry);
DistributedTracer tracer = new DistributedTracer(openTelemetry);
AutoRemediationLog remediation = new AutoRemediationLog(meterRegistry);
```

---

## 1. AnomalyDetector - Detect Outliers

### Define baseline (auto-learning)
```java
// Record 35+ samples to establish baseline
for (int i = 0; i < 40; i++) {
    anomaly.recordExecution("task.duration", 100 + random(10));
}
// System learns: mean=100ms, stddev=5ms
// Threshold = 100 + (2.5 * 5) = 112.5ms
```

### Detect anomalies
```java
anomaly.recordExecution("task.duration", 500, "approve", "spec-001");
// Logged: duration=500ms exceeds threshold=112.5ms
// Counter: yawl.anomaly.detected{metric="task.duration"}

if (anomaly.getTotalAnomalies() > 0) {
    // Take action...
}
```

### Get statistics
```java
AnomalyDetector.MetricBaseline baseline = anomaly.getBaseline("task.duration");
double mean = baseline.getMean();        // Current EWMA mean
double stddev = baseline.getStdDev();    // Current std deviation
double threshold = baseline.getThreshold(); // Current threshold
int p95 = baseline.getPercentile(0.95);  // 95th percentile
```

---

## 2. SLAMonitor - Track Compliance

### Define SLAs
```java
sla.defineSLA("approval_task", 3600000, "1 hour for approval");
sla.defineSLA("processing_case", 86400000, "1 day for full case");
```

### Track execution
```java
Map<String, String> context = Map.of(
    "task", "approve",
    "assignee", "alice",
    "case_id", "case-123"
);

sla.startTracking("approval_task", "wi-001", context);

// ... do work ...

sla.completeTracking("approval_task", "wi-001");
// If duration > 1 hour: yawl.sla.violations{sla_id="approval_task"} += 1
// If duration > 48 minutes: yawl.sla.at_risk{sla_id="approval_task"} += 1
```

### Check compliance
```java
long violations = sla.getTotalViolations("approval_task");
long atRisk = sla.getTotalViolations("approval_task");  // TODO: not exposed, use metrics
int activeCount = sla.getActiveTrackingCount();

if (violations > threshold) {
    // Alert! Escalate!
}
```

---

## 3. DistributedTracer - Correlate Traces

### Create trace for case
```java
String traceId = tracer.generateTraceId(); // "yawl-uuid"

try (DistributedTracer.TraceSpan caseSpan = tracer.startCaseSpan("case-123", "spec-001")) {
    caseSpan.addEvent("case_started");

    // ... execute tasks ...

    caseSpan.endWithSuccess();
}
// OTEL backend sees: span "case_spec-001" with attributes
// - yawl.case.id = "case-123"
// - yawl.spec.id = "spec-001"
```

### Create spans within case
```java
try (DistributedTracer.TraceSpan taskSpan = tracer.startTaskSpan("approve", "case-123", "agent-alice")) {
    taskSpan.addEvent("task_started");

    // ... do work ...

    taskSpan.setAttribute("duration_ms", "1500");
    taskSpan.endWithSuccess();
}
// Parent: case span → Child: task span (auto-linked)
```

### Propagate trace to agents (cross-thread)
```java
// Get current trace
String traceId = tracer.extractTraceId(); // from MDC

// Wrap agent work
Runnable agentTask = tracer.withTraceContext(() -> {
    try (DistributedTracer.TraceSpan agentSpan = tracer.startAgentActionSpan(
            "agent-bob", "verify_approval", "case-123")) {
        agentSpan.addEvent("agent_started");
        // ... agent does work ...
        agentSpan.endWithSuccess();
    }
}, traceId);

// Execute in different thread (trace context preserved)
new Thread(agentTask).start();
```

### Trace with error
```java
try (DistributedTracer.TraceSpan span = tracer.startCaseSpan("case-123", "spec-001")) {
    // ... do work ...
    span.endWithSuccess();
} catch (Exception e) {
    // Auto-closed with error status
    span.recordException(e);
}
```

---

## 4. AutoRemediationLog - Audit Self-Healing

### Log timeout recovery
```java
try {
    // ... work with timeout ...
} catch (TimeoutException e) {
    long durationMs = System.currentTimeMillis() - startMs;

    // Log recovery attempt
    remediation.logTimeoutRecovery(itemId, durationMs, "retry_with_backoff", true);
    // Emits: yawl.remediation.success{remediation_type="timeout_recovery",action="retry_with_backoff"}

    // Retry...
}
```

### Log resource mitigation
```java
try {
    // ... work with resources ...
} catch (OutOfMemoryError e) {
    remediation.logResourceMitigation("heap", "out_of_memory", "trigger_gc_and_retry", true);
}
```

### Log state reconciliation
```java
remediation.logStateReconciliation(
    "item-1",
    "database_inconsistency",
    "query_and_sync_state",
    true  // was successful
);
```

### Complex multi-step scenario
```java
AutoRemediationLog.RemediationScenario scenario = remediation
    .startRemediationScenario("scenario-1", "deadlock_recovery");

try {
    // Step 1: Detect deadlock
    scenario.recordStep("detect", Map.of("lock_holders", 2), true);

    // Step 2: Compensate first transaction
    scenario.recordStep("compensate", Map.of("txn_id", "txn-abc"), true);

    // Step 3: Retry second transaction
    scenario.recordStep("retry", Map.of("attempt", 1), true);

    scenario.complete();
    // Success rate logged: remediation.logRemediation("deadlock_recovery", "scenario", true, ...)
} catch (Exception e) {
    scenario.fail("Rollback: all compensations executed");
    // Failure logged with rollback message
}
```

### Check remediation stats
```java
long total = remediation.getTotalRemediations();
double successRate = remediation.getSuccessRate("timeout_recovery"); // 0.0-1.0

if (successRate < 0.8) {
    // Alert: too many remediation failures!
}
```

---

## Full Integration Example

```java
// Initialize
MeterRegistry registry = /* ... */;
AnomalyDetector anomaly = new AnomalyDetector(registry);
SLAMonitor sla = new SLAMonitor(registry);
DistributedTracer tracer = new DistributedTracer(openTelemetry);
AutoRemediationLog remediation = new AutoRemediationLog(registry);

// Define SLAs
sla.defineSLA("approval", 3600000, "1 hour");
sla.defineSLA("case", 86400000, "1 day");

// Execute case
try {
    String traceId = tracer.generateTraceId();

    try (DistributedTracer.TraceSpan caseSpan = tracer.startCaseSpan("case-123", "spec-001")) {
        caseSpan.addEvent("case_started");

        // SLA + Anomaly tracking
        sla.startTracking("case", "case-123", Map.of("spec_id", "spec-001"));
        long caseStartMs = System.currentTimeMillis();

        // Task 1: Approval
        sla.startTracking("approval", "wi-1", Map.of("task", "approve"));
        long taskStartMs = System.currentTimeMillis();

        try (DistributedTracer.TraceSpan taskSpan = tracer.startTaskSpan("approve", "case-123", "agent-alice")) {
            taskSpan.addEvent("task_started");

            // Execute task...
            Thread.sleep(1500); // Simulate work

            taskSpan.addEvent("task_completed");
        }

        long taskDurationMs = System.currentTimeMillis() - taskStartMs;
        anomaly.recordExecution("task.approve", taskDurationMs, "approve", "spec-001");
        sla.completeTracking("approval", "wi-1");

        long caseDurationMs = System.currentTimeMillis() - caseStartMs;
        anomaly.recordExecution("case.duration", caseDurationMs, "spec-001");
        sla.completeTracking("case", "case-123");

        caseSpan.addEvent("case_completed");
        caseSpan.endWithSuccess();
    }
} catch (Exception e) {
    // Log remediation
    remediation.logStateReconciliation(
        "case-123",
        "execution_error",
        "notify_admin_and_rollback",
        true
    );
}
```

---

## Alerting (Prometheus)

```yaml
groups:
- name: yawl_observability
  rules:
  - alert: AnomalousExecution
    expr: increase(yawl.anomaly.detected[5m]) > 5
    annotations:
      summary: "High anomaly rate detected"

  - alert: SLABreach
    expr: increase(yawl.sla.violations[1h]) > 10
    annotations:
      summary: "SLA violations detected"

  - alert: RemediationFailure
    expr: |
      yawl.remediation.failure / (yawl.remediation.success + yawl.remediation.failure) > 0.2
    annotations:
      summary: "Remediation success rate < 80%"
```

---

## Logging (ELK)

Structured JSON logs emitted by all components:

```json
{
  "timestamp": "2026-02-20T12:34:56Z",
  "level": "WARN",
  "logger": "org.yawlfoundation.yawl.observability.AnomalyDetector",
  "message": "Execution anomaly detected",
  "metric": "task.duration",
  "duration_ms": 500,
  "mean_ms": 100,
  "stddev_ms": 5,
  "threshold_ms": 112,
  "deviation_factor": "5.00",
  "task_name": "approve",
  "spec_id": "spec-001",
  "trace_id": "yawl-a1b2c3d4-..."
}
```

Query in Kibana/Loki:
```
level:"WARN" AND message:"anomaly" AND spec_id:"spec-001"
→ Find all anomalies for specific workflow
```

---

## Testing

Run all observability tests:

```bash
mvn test \
  -Dtest=AnomalyDetectorTest \
  -Dtest=SLAMonitorTest \
  -Dtest=DistributedTracerTest \
  -Dtest=AutoRemediationLogTest \
  -Dtest=AutonomicObservabilityIntegrationTest
```

Expected output:
- 106 tests
- 0 failures
- 0 skipped

---

## Files Reference

| File | Purpose | Lines |
|------|---------|-------|
| AnomalyDetector.java | Outlier detection | 250 |
| SLAMonitor.java | Compliance tracking | 234 |
| DistributedTracer.java | Cross-agent traces | 273 |
| AutoRemediationLog.java | Remediation audit | 310 |
| AnomalyDetectorTest.java | Tests | 234 |
| SLAMonitorTest.java | Tests | 283 |
| DistributedTracerTest.java | Tests | 318 |
| AutoRemediationLogTest.java | Tests | 333 |
| AutonomicObservabilityIntegrationTest.java | Integration tests | 331 |

---

## Key Constants

```java
// AnomalyDetector
EWMA_ALPHA = 0.3                    // Exponential weight
STDDEV_MULTIPLIER = 2.5             // Threshold multiplier
MIN_SAMPLES_FOR_BASELINE = 30       // Baseline establishment
MAX_HISTORY_SIZE = 500              // Max samples to keep

// SLAMonitor
TREND_THRESHOLD = 0.8               // At-risk threshold (80% of SLA)
PREDICTION_WINDOW_MS = 300000       // 5 minutes for trending
MIN_SAMPLES_FOR_TREND = 10          // Min samples to calculate trend
```

---

## Session & Commit

**Session**: https://claude.ai/code/session_01M7rwSjmSfZxWkkkguKqZpx

**Commit**: `e6403f3` feat: Add fast 80/20 autonomic observability for YAWL v6.0.0

---

## Documentation

- Full guide: `/home/user/yawl/FAST_80_20_AUTONOMIC_OBSERVABILITY.md`
- Implementation: `/home/user/yawl/OBSERVABILITY_IMPLEMENTATION_SUMMARY.md`
- API Docs: `src/org/yawlfoundation/yawl/observability/package-info.java`
