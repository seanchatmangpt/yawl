# Chapter 6 — Next Steps: Research Agenda 2026

> *"The most significant gap: the feedback loop is open.
> Models are trained on historical cases, but adaptation decisions
> are not yet fed back as training signal."*

---

## 6.1 The Closed Learning Loop (Q2 2026)

### Current State

`ProcessMiningAutoMl` trains models on historical case data extracted from
`WorkflowEventStore`. The trained ONNX models are loaded into `PredictiveModelRegistry`
and used by `PredictiveProcessObserver` to make adaptation decisions. But those
decisions are not yet fed back as training signal. The loop is open.

```
Current (open loop):
  Historical cases → train model → predict on new cases → [decisions discarded]
                                                           ↑
                                                      Gap here
```

### The Closed Loop

```
Target (closed loop):
  Historical cases → train model → predict on new cases
                                        ↓
                             Adaptation decision recorded
                                        ↓
                              Case outcome observed
                                        ↓
                   (decision, features, outcome) → labelled training example
                                        ↓
                          Incremental model retraining
                                        ↓
                           Updated model hot-swapped
                                        ↓
                   [loop continues with improving accuracy]
```

### Research Questions

**Causal attribution**: When `ESCALATE_TO_MANUAL` fires and the escalated case
resolves well, was the good outcome *caused* by the escalation or would it have
resolved well regardless? Naive feedback creates systematic bias — cases that are
escalated never have counterfactual outcomes in the data.

Proposed approach: **doubly robust estimation** using a propensity score model
trained on case features that predicts escalation probability, combined with
an outcome model. This allows unbiased outcome estimation even when escalation
is non-random.

**Sample efficiency**: TPOT2 requires minimum sample sizes for reliable model
selection. With milestone-triggered retraining (e.g., every 200 completed cases),
what is the minimum effective sample size before retraining improves vs. harms
model accuracy?

Proposed approach: Track out-of-sample prediction accuracy with rolling hold-out.
Only promote retrained model if accuracy improves by > δ (configurable threshold).

**Distribution shift detection**: The self-reference property means the model's own
predictions shift the case distribution, which shifts the training data, which
shifts the model. This can create feedback instabilities.

Proposed approach: Monitor Jensen-Shannon divergence between current training
feature distribution and the distribution used to train the currently deployed
model. Trigger retraining when divergence exceeds threshold; alert when divergence
accelerates.

### Implementation Sketch

```java
// New: AdaptationOutcomeRecorder in adaptive package
public interface AdaptationOutcomeRecorder {
    void recordDecision(String caseId, ProcessEvent event,
                        AdaptationResult decision, float[] features);
    void recordOutcome(String caseId, CaseOutcome outcome);
    TrainingDataset buildFeedbackDataset(String specId, int minSamples);
}

// New: ClosedLoopAutoMl in automl package
public final class ClosedLoopAutoMl {
    public Tpot2Result retrain(String taskName,
                               TrainingDataset feedbackDataset,
                               PredictiveModelRegistry registry,
                               Tpot2Config config,
                               Path modelDir) { ... }
}
```

---

## 6.2 Federated Process Intelligence (Q3 2026)

### The Problem

An insurance company runs 50 regional YAWL instances. Each instance learns from
its own cases. A fraud pattern that emerges in the Auckland region would take weeks
to appear in training data for the Sydney or London instances.

Centralizing raw event data is prohibited by GDPR (EU), Privacy Act (AU), and
similar regulations — case data cannot leave the jurisdiction where it was created.

### The Solution: Federated Learning

Federated learning aggregates model updates (gradient weights) without centralizing
raw data:

```
Regional Instance A (Auckland):     Regional Instance B (Sydney):
  Local cases → local training         Local cases → local training
  Local model delta (weights only)     Local model delta (weights only)
         │                                        │
         └──────────────┬─────────────────────────┘
                        ▼
               Central Aggregator
               (gradient averaging / FedAvg)
                        │
               Global model weights
                        │
         ┌──────────────┴─────────────────────────┐
         ▼                                        ▼
  Instance A updates local model          Instance B updates local model
```

No raw case data ever leaves the region. Only weight deltas are transmitted.
A fraud pattern learned in Auckland improves the Auckland model immediately and
improves all other regional models within one aggregation cycle.

### ONNX Integration

ONNX models support weight export and import natively. The federated aggregation
protocol operates on exported ONNX weight tensors:

```java
// New: FederatedModelAggregator in automl package
public final class FederatedModelAggregator {
    // FedAvg: weighted average of regional model weights
    public OnnxWeights aggregate(List<RegionalModelDelta> deltas) { ... }

    // Apply aggregated weights to local model (hot-swap in registry)
    public void applyToRegistry(OnnxWeights global, PredictiveModelRegistry registry,
                                 String taskName) { ... }
}
```

**Research questions**: (1) How many regional instances are needed before federated
averaging improves over per-instance training? (2) What aggregation frequency is
optimal (every 100 cases? every 1000?)? (3) How to handle heterogeneous process
distributions across regions?

---

## 6.3 RAG-Augmented Adaptation Explanation (Q3 2026)

### The Problem

`PredictiveProcessObserver` makes adaptation decisions that affect real cases.
Operators, auditors, and compliance officers ask: *"Why was this claim rejected?"*
*"Why was this patient escalated?"* *"What score triggered this SLA alert?"*

The current system has the data to answer these questions — the `ProcessEvent` payload
contains the feature vector and model output — but no explanation facility.

### The Solution: Explainability via RAG

`ProcessKnowledgeBase` and `NaturalLanguageQueryEngine` (already implemented in
`pi.rag`) can bridge this gap:

```
Adaptation decision emitted by PredictiveProcessObserver
  → ProcessEvent{caseId="C-447", eventType=SLA_BREACH_PREDICTED,
                  payload={remainingMinutes=23.4, taskId="customs-clearance", ...}}
  → ProcessKnowledgeBase.index(event, featureVector, modelOutput)

Operator queries via MCP:
  "Why was case C-447 escalated 2 hours ago?"
  → NaturalLanguageQueryEngine.query("case C-447 escalation 2 hours")
  → Retrieves indexed event
  → Generates explanation:
     "Case C-447 was escalated at 14:32 because the remaining-time model
      predicted 23 minutes remaining (critical threshold: 30 minutes).
      The prediction was based on: task 'customs_clearance' had been
      executing for 4.2 hours, 2 prior tasks had exceeded their median
      duration by 40%, and the case had already triggered one status
      exception. This combination historically predicts SLA breach with
      91% accuracy in similar cases."
```

**Research questions**: (1) How to generate faithful explanations (not hallucinated)
from ONNX model outputs that have no built-in explainability? (2) What level of
detail is appropriate for compliance vs. operational audiences? (3) How to handle
explanation requests for rejected cases where the "true" outcome is unobservable?

---

## 6.4 MCP-Mediated Process Intelligence (Q4 2026)

### Current State

`PIToolProvider` and `PISkill` expose PI capabilities as MCP tools. The current
implementation is a skeleton — the tool interface is defined but the full tool
suite is not yet implemented.

### Full MCP Tool Suite

The complete tool set required for AI agent process management:

| Tool | Signature | Description |
|---|---|---|
| `predict_case_outcome` | `(caseId, features[]) → score` | Point prediction for live case |
| `get_bottleneck_prediction` | `(specId) → taskId, confidence` | Current bottleneck |
| `trigger_retraining` | `(taskType, specId, minSamples)` | Initiate AutoML training |
| `query_process_knowledge` | `(nl_query) → answer` | NL query over process facts |
| `get_adaptation_history` | `(caseId, since) → List<AdaptationResult>` | Audit trail |
| `set_adaptation_rule` | `(rule_spec_json)` | Add rule to live engine |
| `get_model_performance` | `(taskType) → accuracy, drift_score` | Model health |
| `list_active_cases_at_risk` | `(threshold) → List<caseId>` | SLA risk list |

### Autonomous Process Intelligence Agent

With this tool suite, a Claude-powered agent can manage an intelligent process
deployment entirely through natural language:

```
User: "Our fraud rate increased 12% this week. Investigate and fix it."

Agent (Claude):
  1. query_process_knowledge("fraud rate increase last 7 days")
     → "23 cases flagged post-hoc as fraud; 19 passed anomaly_detection threshold"
  2. get_model_performance("anomaly_detection")
     → "accuracy: 0.82, drift_score: 0.34 (elevated)"
  3. trigger_retraining("anomaly_detection", "loan_approval", minSamples=500)
     → "Retraining initiated; ETA 8 minutes"
  4. [8 minutes later] get_model_performance("anomaly_detection")
     → "accuracy: 0.89, drift_score: 0.12 (normal)"
  5. set_adaptation_rule({eventType: PROCESS_ANOMALY_DETECTED,
                           threshold: 0.82,  // was 0.85
                           action: REJECT_IMMEDIATELY})
     → "Rule updated; new threshold active"

User: "Done. What changed?"

Agent: "Retraining on 500 recent cases improved fraud detection accuracy from
        0.82 to 0.89. Detection threshold tightened from 0.85 to 0.82, which
        would have caught 17 of the 19 fraud cases that passed last week.
        No legitimate cases fall below 0.82 in the new model."
```

---

## 6.5 Process Specification Auto-Generation (Q4 2026)

### The Vision

`ProcessMiningTrainingDataExtractor` can extract patterns from event data.
The next step: use those patterns to generate YAWL process specifications.

```
Input:  10,000 case traces in WorkflowEventStore
Output: A YAWL specification (.yawl + .ywl) that captures the discovered process

Validation: Replay specification against original traces
            Conformance score > 0.95 before deployment
```

This closes the process discovery loop:
1. Run arbitrary process (possibly via ad-hoc task assignments)
2. Record events in WorkflowEventStore
3. Discover process model from events
4. Generate YAWL specification
5. Deploy generated specification
6. Cases now run through governed, traceable workflow
7. New events feed back into step 2 — continuous refinement

**Research questions**: (1) Which discovery algorithm is most faithful for YAWL's
workflow patterns (AND-splits, OR-joins, cancellation regions)? (2) How to handle
infrequent traces that represent exceptional paths? (3) What YAWL-specific
constructs (worklets, deferred choice, multiple instances) require special discovery
logic?

---

*← [Chapter 5](05-enterprise-use-cases.md) · → [Chapter 7 — Vision 2030](07-vision-2030.md)*
