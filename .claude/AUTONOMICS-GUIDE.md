# Autonomics Guide — Self-Healing Workflows

> Enable automatic failure recovery, health monitoring, and escalation.
> Let your workflows self-heal. ⚡

---

## What is Autonomics?

**Autonomics** = Systems that manage themselves with minimal human intervention.

For YAWL workflows, autonomics means:
- ✅ **Auto-retry** transient failures (network timeouts, temporary unavailability)
- ✅ **Auto-detect** stuck cases (no progress for 5+ minutes)
- ✅ **Auto-recover** deadlocked workflows (remove blockers, restart tasks)
- ✅ **Auto-escalate** critical issues (out-of-memory, security exceptions)
- ✅ **Dead letter queue** for cases needing human review

**Result**: Workflows complete without human intervention → Higher availability, lower MTTR.

---

## Quick Start: 5-Minute Setup

### Step 1: Create Autonomics Engine

```java
import org.yawlfoundation.yawl.resilience.autonomics.WorkflowAutonomicsEngine;
import org.yawlfoundation.yawl.stateless.engine.YStatelessEngine;
import java.time.Duration;

YStatelessEngine engine = YStatelessEngine.getInstance();
WorkflowAutonomicsEngine autonomics = new WorkflowAutonomicsEngine(engine);
```

### Step 2: Register Retry Policies

```java
// Retry connection errors 3 times with exponential backoff
autonomics.registerRetryPolicy(
    "ConnectionException",
    new WorkflowAutonomicsEngine.RetryPolicy(
        3,      // max 3 attempts
        100,    // initial backoff: 100ms
        2.0,    // exponential multiplier: 2x each retry
        true    // is transient? YES
    )
);

// Retry timeouts 2 times (faster recovery)
autonomics.registerRetryPolicy(
    "TimeoutException",
    new WorkflowAutonomicsEngine.RetryPolicy(2, 50, 2.0, true)
);

// DO NOT retry validation errors (permanent failure)
// Just escalate to dead letter queue
```

### Step 3: Start Health Monitoring

```java
// Check health every 30 seconds
autonomics.startHealthMonitoring(Duration.ofSeconds(30));

System.out.println("Autonomics engine started!");
```

### Step 4: Run Workflows (Autonomics Handles Rest)

```java
// Create and execute workflow normally
YIdentifier caseID = engine.createCase(spec);

// Autonomics automatically:
// - Retries failed tasks
// - Detects stuck cases
// - Escalates critical issues
// - Populates dead letter queue for failures
```

---

## Complete Example: E-Commerce Order Processing

```java
public class OrderProcessingService {

    private final WorkflowAutonomicsEngine autonomics;

    public OrderProcessingService(YStatelessEngine engine) {
        this.autonomics = new WorkflowAutonomicsEngine(engine);
        setupAutonomics();
    }

    private void setupAutonomics() {
        // Payment service might timeout temporarily
        autonomics.registerRetryPolicy(
            "PaymentServiceException",
            new WorkflowAutonomicsEngine.RetryPolicy(3, 500, 2.0, true)
        );

        // Inventory service might be temporarily unavailable
        autonomics.registerRetryPolicy(
            "InventoryUnavailableException",
            new WorkflowAutonomicsEngine.RetryPolicy(2, 200, 2.0, true)
        );

        // Shipping carrier might have intermittent API issues
        autonomics.registerRetryPolicy(
            "ShippingAPIException",
            new WorkflowAutonomicsEngine.RetryPolicy(3, 1000, 2.0, true)
        );

        // Start health checks every 60 seconds
        autonomics.startHealthMonitoring(Duration.ofSeconds(60));
    }

    public void processOrder(Order order) throws Exception {
        // Create workflow case
        YIdentifier caseID = autonomics.createOrderCase(order);

        // Autonomics now handles:
        // 1. Payment fails (network issue) → Auto-retry 3x
        // 2. Inventory check times out → Auto-retry 2x
        // 3. Shipping API down → Auto-retry 3x
        // 4. Case stuck > 5 min → Auto-escalate to dead letter
        // 5. Critical error (OOM) → Immediate escalation

        // Periodically check for failures requiring human attention
        monitorDeadLetterQueue();
    }

    private void monitorDeadLetterQueue() {
        WorkflowAutonomicsEngine.DeadLetterQueue dlq = autonomics.getDeadLetterQueue();

        while (dlq.size() > 0) {
            var stuck = dlq.poll();
            if (stuck.isPresent()) {
                WorkflowAutonomicsEngine.StuckCase stuckCase = stuck.get();
                System.err.println("Manual intervention needed: " + stuckCase);
                // → Send alert to operations team
                // → Create ticket for manual recovery
                // → Log for audit trail
            }
        }
    }

    public void printHealthReport() {
        WorkflowAutonomicsEngine.HealthReport health = autonomics.getHealthReport();
        System.out.println("Active cases: " + health.getActiveCases());
        System.out.println("Stuck cases: " + health.getStuckCases());
        System.out.println("Healthy: " + health.isHealthy());
    }
}
```

---

## Autonomics Behavior Reference

### 1. Auto-Retry for Transient Failures

**When**: Task throws exception marked as transient

**Flow**:
```
Task fails: ConnectionException
  ↓ (is transient? YES)
  ↓ (registered policy? YES)
  ↓ Wait 100ms (initial backoff)
  ↓ Retry attempt 1
    ├─ Success? → Continue
    └─ Fail? → Wait 200ms (backoff multiplied)
  ↓ Retry attempt 2
    ├─ Success? → Continue
    └─ Fail? → Wait 400ms
  ↓ Retry attempt 3
    ├─ Success? → Continue
    └─ Fail? → Escalate to dead letter
```

**Config**:
```java
// Short-lived temporary failures → Fast retries
new RetryPolicy(2, 50, 2.0, true)     // TimeoutException

// Longer-lived failures → Slower, more retries
new RetryPolicy(4, 500, 2.0, true)    // ShippingAPIException

// Permanent failures → Never register as transient
// Just escalate immediately
```

### 2. Health Monitoring for Stuck Cases

**When**: Case has no work items enabled for 5+ minutes

**Detection**:
```
Case A: Last progress 7 minutes ago
  ↓ Threshold exceeded (5 min)
  ↓ Investigate:
    ├─ Missing output flow? → Tokens stuck in condition
    ├─ Deadlock? → Task A waits for B, B waits for A
    ├─ Resource exhaustion? → Not enough threads/connections
    └─ Bug in custom code? → Task never completes
  ↓ Attempt auto-recovery (reset blockers)
  ↓ If fails → Escalate to dead letter queue
```

**Monitor Health**:
```java
autonomics.startHealthMonitoring(Duration.ofSeconds(30));

// Later, check status
HealthReport health = autonomics.getHealthReport();
if (!health.isHealthy()) {
    sendAlert("Stuck cases detected: " + health.getStuckCases());
}
```

### 3. Critical Escalation

**When**: Unrecoverable exception occurs

**Examples**:
```
OutOfMemoryError
  → Cannot retry (system broken)
  → Capture case state
  → Add to dead letter queue
  → Alert: CRITICAL

SecurityException (permission denied)
  → Cannot retry (permanent auth failure)
  → Escalate to security team
  → Block case from processing

ValidationException (bad data)
  → Cannot retry (user input invalid)
  → Notify user, ask for correction
  → Wait for manual intervention
```

### 4. Dead Letter Queue (Manual Intervention)

Cases that failed beyond automatic recovery:

```java
DeadLetterQueue dlq = autonomics.getDeadLetterQueue();

// Check what needs attention
for (StuckCase stuck : dlq.getAll()) {
    System.out.println(stuck.getCaseID() + ": " + stuck.getReason());
    System.out.println("Stuck for: " + stuck.getStuckDurationMs() + "ms");
}

// Options for manual recovery:
// 1. Fix root cause, then manually retry case
// 2. Skip problematic task, continue workflow
// 3. Cancel case, refund customer
// 4. Notify user, ask for more information
```

---

## Configuration Reference

### Retry Policy Options

```java
new WorkflowAutonomicsEngine.RetryPolicy(
    maxAttempts,        // How many times to retry (typically 2-4)
    initialBackoffMs,   // Milliseconds before first retry
    backoffMultiplier,  // Each retry waits N times longer
    isTransient         // True if error is temporary, false if permanent
)

// Examples:
new RetryPolicy(3, 100, 2.0, true)    // 100ms, 200ms, 400ms (exponential)
new RetryPolicy(2, 50, 2.0, true)     // Quick retries for timeouts
new RetryPolicy(4, 1000, 2.0, true)   // Slower retries for external APIs
```

### Health Check Interval

```java
// Check every 30 seconds (fast detection)
autonomics.startHealthMonitoring(Duration.ofSeconds(30));

// Check every 5 minutes (less overhead)
autonomics.startHealthMonitoring(Duration.ofMinutes(5));

// Tradeoff:
// - Shorter → Faster stuck detection (good for SLA)
// - Longer → Less CPU usage (good for efficiency)
// Recommend: 30-60 seconds for production
```

---

## Monitoring and Metrics

### Health Reports

```java
HealthReport health = autonomics.getHealthReport();

health.getActiveCases()  // → 1,234 (workflows running)
health.getStuckCases()   // → 3 (cases with no progress)
health.isHealthy()       // → false (action needed)

// Alert if stuck > 0
if (!health.isHealthy()) {
    ops.alert("Workflow health degraded: " + health.getStuckCases() + " stuck");
}
```

### OpenTelemetry Metrics

Autonomics exports metrics (with OpenTelemetry integration):

```
yawl_autonomics_retries_total{reason="ConnectionException"}
yawl_autonomics_stuck_cases_total
yawl_autonomics_dlq_size
yawl_autonomics_recovery_success_total
yawl_autonomics_recovery_failure_total
```

---

## Best Practices

### ✅ DO

```java
// ✅ Register policies for known transient errors
autonomics.registerRetryPolicy(
    "ConnectionException",
    new RetryPolicy(3, 100, 2.0, true)
);

// ✅ Use exponential backoff to prevent thundering herd
new RetryPolicy(3, 100, 2.0, true)  // 100, 200, 400ms

// ✅ Monitor dead letter queue regularly
scheduler.scheduleAtFixedRate(
    autonomics::checkDeadLetterQueue,
    Duration.ofMinutes(1)
);

// ✅ Alert on health degradation
if (!health.isHealthy()) {
    alertOps("Workflow health issue detected");
}
```

### ❌ DON'T

```java
// ❌ Don't retry permanent failures (validation, auth)
// autonomics.registerRetryPolicy("ValidationException", ...); ← NO!

// ❌ Don't use instant retries (they will fail again)
new RetryPolicy(3, 0, 1.0, true)  ← NO! Add backoff

// ❌ Don't ignore dead letter queue
// Schedule regular reviews of stuck cases

// ❌ Don't set impossible stuck thresholds
// 5 minutes = reasonable, 30 seconds = too aggressive
```

---

## Troubleshooting

| Problem | Cause | Solution |
|---------|-------|----------|
| "Cases stuck constantly" | Threshold too short or real deadlock | Increase threshold to 10 min, investigate case logic |
| "Too many retries failing" | Underlying issue not transient | Mark as permanent, escalate immediately |
| "Dead letter queue growing" | Systemic issue (service down?) | Investigate root cause, fix infrastructure |
| "Autonomics not detecting stucks" | Health monitoring not started | Call `startHealthMonitoring()` |
| "Cases not retrying" | Policy not registered | Check exception type name matches |

---

## Production Checklist

- [ ] Registered retry policies for all expected transient errors
- [ ] Health monitoring started with 30-60s interval
- [ ] Dead letter queue monitored every 1-5 minutes
- [ ] Alerts configured for stuck cases (health.isHealthy() == false)
- [ ] Escalation procedures documented (who handles DLQ cases?)
- [ ] Test failover: manually kill external service, verify retries work
- [ ] Monitor metrics: retries, stuck cases, DLQ depth
- [ ] Review logs: look for patterns (same failure type repeatedly?)
- [ ] Capacity: autonomics uses threads, verify thread pool sized correctly
- [ ] Graceful shutdown: call autonomics.shutdown() on application stop

---

## Next Steps

1. **Implement custom recovery** (extend HealthMonitor for domain-specific logic)
2. **Integrate with observability** (link to OpenTelemetry metrics)
3. **Build operations dashboard** (dead letter queue SLA dashboard)
4. **Setup PagerDuty** (alerts on critical escalations)
5. **Document runbooks** (how to manually recover stuck cases)

---

**Your workflows now self-heal!** 🚀⚡

See also:
- `QUICKSTART.md` — Basic workflow execution
- `CONCEPTS.md` — Workflow semantics
- `.claude/rules/engine/` — Workflow patterns and rules

Last updated: 2026-02-20
