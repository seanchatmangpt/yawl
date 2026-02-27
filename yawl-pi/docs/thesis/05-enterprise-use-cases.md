# Chapter 5 — Enterprise Use Cases

> *"These capabilities simply cannot be replicated in distributed architectures.
> Not with better engineering — structurally cannot."*

---

Each use case below demonstrates a capability that is **only possible because the
ML inference engine and workflow engine share a JVM**. We note the specific
`ObserverGateway` callback responsible and the minimum latency in distributed
architecture for the equivalent effect.

---

## 5.1 Insurance Claims Triage

### The Problem

An insurer processes 50,000 claims per month. Approximately 3% are fraudulent.
Complex claims (requiring specialist adjusters) represent 18% of volume but consume
65% of adjuster time when misrouted to general queues.

**Batch reality**: Fraud scoring runs nightly on the previous day's claims. Complex
claims are identified weekly via actuarial review reports. A fraudulent claim
submitted on Monday morning may process through underwriting, approval, and initial
payment before the Wednesday night fraud batch identifies it. Complex claims sit in
the general queue for an average of 3.2 days before a supervisor notices and
reroutes.

### With Co-location

```
Claim submitted (case started)
  → announceCaseStarted fires [<1ms from submission]
  → case_outcome model: complexity score = 0.82, confidence = 0.91
  → confidence > 0.75: REROUTE_TO_SUBPROCESS
  → Case routes to specialist adjuster queue before first task enables
  → No general queue time. No supervisor intervention.

During processing (any task transition)
  → announceWorkItemStatusChange fires
  → anomaly_detection model: score = 0.87
  → score > 0.85: REJECT_IMMEDIATELY
  → Case cancelled before payment authorisation task executes
  → Fraudulent payment never reaches payment system.

Timer expiry on 'identity_verification' task
  → announceTimerExpiry fires [structural certainty, no model needed]
  → TIMER_EXPIRY_BREACH at EventSeverity.CRITICAL
  → SIU team notified immediately
  → No model inference required — timer expiry IS the evidence.
```

### Setup (One Line)

```java
PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
    .forInsuranceClaimsTriage(registry, result -> {
        if (result.adapted()) claimsService.execute(result.executedAction(), result.event());
    });
YEngine.getInstance().getAnnouncer().registerInterfaceBObserverGateway(observer);
```

### What Co-location Uniquely Enables

- **Synchronous rejection before first task**: In distributed architecture, the
  first task (identity verification, document collection) is already queued before
  any API call could return. The rejection fires in the `announceCaseStarted`
  callback, which runs before task enablement.
- **Timer expiry as hard evidence**: No model required. The structural event is the
  signal. Distributed architectures would need a polling service to detect timer
  expiry and then make an API call — minimum latency: 30 seconds.

### Models Required

| Model | Task type | Training source |
|---|---|---|
| `case_outcome` | `CASE_OUTCOME` | Historical claims with fraud/non-fraud label |
| `anomaly_detection` | `ANOMALY_DETECTION` | Claims event traces; anomalies = fraud cases |

---

## 5.2 Healthcare Case Management

### The Problem

A hospital emergency department runs 400 cases per day. Clinical pathways vary
widely; SLA compliance (4-hour emergency target) is 78% — below the 95% regulatory
requirement. High-risk patient journeys are identified at admission using a static
scoring tool that does not update as the case progresses.

**Batch reality**: The static tool runs once at admission. A patient who appears
low-risk at arrival but deteriorates within 2 hours receives no automated
re-stratification. SLA breaches are discovered in the morning audit, 12 hours after
the fact. No proactive intervention is possible.

### With Co-location

```
Patient admitted (case started)
  → announceCaseStarted fires
  → case_outcome model: high-risk score = 0.74
  → score > 0.70: ESCALATE_TO_MANUAL (senior clinician review)
  → Senior clinician assigned within minutes of admission

Every task transition (assessment → triage → treatment → discharge)
  → announceWorkItemStatusChange fires
  → remaining_time model: predicted wait = 42 minutes
  → 42 < 60 (warning threshold): NOTIFY_STAKEHOLDERS
  → Operations team alerted with 60-minute lead time

Task completion ('initial_assessment')
  → announceWorkItemStatusChange (newStatus = statusComplete)
  → next_activity model: confidence = 0.89
  → confidence > 0.80: INCREASE_PRIORITY
  → Clear care pathway → case boosted in queue

```

### Setup (One Line, Default Parameters)

```java
// SLA threshold 60 min, risk threshold 0.70 — sensible defaults for ED
PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
    .forHealthcareCaseManagement(registry, result -> edService.handle(result));
```

### What Co-location Uniquely Enables

- **Intra-episode risk re-stratification**: Static tools run once. Co-located ML
  re-stratifies at every task transition with the updated process context.
- **60-minute predictive SLA management**: A dashboard polling every 15 minutes
  can only react after a breach. Remaining-time prediction at every transition
  gives 60+ minutes of lead time.
- **Confident pathway priority boost**: Next-activity prediction identifies cases
  on a clear fast-path and boosts their queue priority — impossible without
  real-time access to current case state.

### Models Required

| Model | Task type | Training source |
|---|---|---|
| `case_outcome` | `CASE_OUTCOME` | Historical cases with outcome label |
| `remaining_time` | `REMAINING_TIME` | Historical cases with completion timestamps |
| `next_activity` | `NEXT_ACTIVITY` | Historical traces with transition sequences |

---

## 5.3 Financial Risk Monitoring

### The Problem

A bank's loan approval workflow processes 2,000 applications per day. The existing
rule-based fraud engine checks 12 static rules at intake. ML fraud scoring runs
nightly. Fraudulent applications that pass the static rules at intake are not
re-scored during the multi-step approval workflow (credit check → compliance review
→ underwriting → approval). They complete before the nightly score surfaces them.

**Batch reality**: Fraudulent loan applications that pass static intake checks
process through 4 workflow steps over 2 days before the nightly ML batch identifies
them. By that time, the loan may have been approved and funded.

### With Co-location

```
Application submitted (case started)
  → announceCaseStarted fires
  → case_outcome model: risk score = 0.68
  → score > 0.60 but < 0.75: NOTIFY_STAKEHOLDERS (risk ops team flagged)
  → Application continues but risk team monitoring

After 'credit_check' task completes
  → announceWorkItemStatusChange fires (statusComplete)
  → anomaly_detection model: score = 0.92 (new features after credit check)
  → score > 0.90: REJECT_IMMEDIATELY
  → Application cancelled before underwriting task is enabled
  → Credit check data exposed the fraud pattern the intake model missed

Application cancellation
  → announceCaseCancellation fires
  → anomaly_detection model confirms score = 0.92
  → PROCESS_ANOMALY_DETECTED at CRITICAL
  → Audit trail created with full feature vector

```

### What Co-location Uniquely Enables

- **Intra-workflow risk re-scoring**: The fraud pattern that was invisible at intake
  becomes visible after the credit check reveals inconsistencies. Only co-location
  allows re-scoring mid-workflow with the enriched feature set.
- **Pre-underwriting rejection**: In distributed architecture, by the time an API
  call returns with the updated score, the underwriting task is already queued.
  Co-located inference fires in the `announceWorkItemStatusChange` callback, before
  the next task is enabled.

### Models Required

| Model | Task type | Training source |
|---|---|---|
| `case_outcome` | `CASE_OUTCOME` | Historical applications with fraud label |
| `anomaly_detection` | `ANOMALY_DETECTION` | Application traces; anomalies = fraud cases |

---

## 5.4 Operations SLA Guardian

### The Problem

A logistics company's order fulfilment workflow has a 24-hour SLA. Operations
dashboards refresh every 15 minutes. SLA breaches are discovered post-hoc;
the average time between a case becoming at-risk and operator notification is
47 minutes (next dashboard refresh + review time).

**Batch reality**: The only mechanism for proactive SLA management is manual review
of the 15-minute dashboard. No predictive alerting. Breaches are managed reactively.

### With Co-location

```
Every task transition
  → announceWorkItemStatusChange fires
  → remaining_time model: predicted remaining = 95 minutes
  → 95 < 120 (warning threshold): NOTIFY_STAKEHOLDERS
  → Operations team receives alert: "Order C-882: ~95 min remaining"

  → [20 minutes later] remaining_time model: predicted = 68 minutes
  → 68 < 120: NOTIFY_STAKEHOLDERS (updated estimate)

  → [30 minutes later] remaining_time model: predicted = 24 minutes
  → 24 < 30 (critical threshold): ESCALATE_TO_MANUAL
  → Supervisor assigned immediately

Timer expiry on 'customs_clearance' task
  → announceTimerExpiry fires
  → TIMER_EXPIRY_BREACH at CRITICAL regardless of model state
  → Immediate escalation, SLA breach confirmed

```

### Setup (Two-Tier, Custom Thresholds)

```java
// Two-tier: warning at 120 min, critical at 30 min
PredictiveProcessObserver observer = EnterpriseAutoMlPatterns
    .forOperationsSlaGuardian(registry, result -> opsService.handle(result), 30, 120);
```

### What Co-location Uniquely Enables

- **Continuous remaining-time prediction**: Updated at every task transition, not
  every 15 minutes. The estimate improves as more case-specific evidence accumulates.
- **Pre-breach escalation**: The critical threshold fires when ~30 minutes remain —
  before the breach, not after.
- **Definite breach detection**: Timer expiry fires with structural certainty.
  No model inference, no false positive, no threshold tuning. The timer ran out.

### Models Required

| Model | Task type | Training source |
|---|---|---|
| `remaining_time` | `REMAINING_TIME` | Historical orders with completion timestamps |
| `anomaly_detection` | `ANOMALY_DETECTION` (optional) | Historical traces; anomalies = unusual routes |

---

## 5.5 Cross-Cutting Capabilities

Three capabilities appear across all four verticals and are worth highlighting
as general enterprise primitives:

### Deadlock as Perfect Anomaly

```java
@Override
public void announceDeadlock(Set<YAWLServiceReference> services,
                             YIdentifier id, Set<YTask> tasks) {
    // No model inference needed. Structural deadlock IS anomaly score = 1.0.
    eventSink.accept(new ProcessEvent(...,
        Map.of("caseId", id.toString(),
               "anomalyScore", 1.0,
               "deadlockedTasks", taskIds,
               "anomalyType", "STRUCTURAL_DEADLOCK"),
        EventSeverity.CRITICAL));
}
```

A structural deadlock is a perfect anomaly — score 1.0 by definition. No model
can improve on this. The callback is the evidence. This is a category of signal
that distributed architectures cannot generate in real-time.

### Self-Improving Feature Extraction

The `FeatureExtractor` interface in `PredictiveProcessObserver` is designed for
progressive enhancement:

```java
// Development: identity extractor (works everywhere, coarse accuracy)
PredictiveProcessObserver.IDENTITY_EXTRACTOR

// Production: domain extractor (calibrated to specific process variables)
(caseId, taskId, specId, ageMs) -> extractClaimFeatures(caseId, taskId, ageMs)

// Advanced: ProcessMiningTrainingDataExtractor-derived features
// (same features used in training, guaranteed consistency)
```

The same feature extraction code used in `ProcessMiningAutoMl.autoTrain*()` can
be reused at inference time — impossible in distributed architecture where training
runs in Python and inference is a Java REST call with a different feature schema.

### Shared Model Naming Convention

All four verticals use the same model naming convention (lowercase `Tpot2TaskType`):

```
case_outcome.onnx        → loaded as "case_outcome"
remaining_time.onnx      → loaded as "remaining_time"
next_activity.onnx       → loaded as "next_activity"
anomaly_detection.onnx   → loaded as "anomaly_detection"
```

Drop ONNX files into the model directory → `PredictiveModelRegistry` auto-loads →
`PredictiveProcessObserver.isAvailable()` checks → callbacks activate. Zero
configuration change.

---

*← [Chapter 4](04-architecture.md) · → [Chapter 6 — Next Steps](06-next-steps.md)*
