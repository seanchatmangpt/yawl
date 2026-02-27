# Co-located AutoML — Why Zero-ETL Prediction Matters

This document explains the architectural motivation behind co-locating ML inference
inside the YAWL engine process, and how `PredictiveProcessObserver` achieves this.

---

## The ETL lag problem

Traditional process mining analytics follow an Extract-Transform-Load pipeline:

```
YAWL Engine
    │ events written to DB
    ▼
ETL Job (runs hourly / nightly)
    │ extract → transform → load
    ▼
Data Warehouse / Process Mining Tool
    │ model trained or refreshed
    ▼
Prediction result
    │ shipped back to operations
    ▼
Human act on the prediction
```

By the time a prediction reaches an operator, the case it refers to may have
already completed — successfully or not. The insight is **stale**.

Typical lag in enterprise deployments: **minutes to hours**.
For time-sensitive cases (insurance claims, hospital admissions, financial transactions),
even a 10-minute lag means missed intervention windows.

---

## The co-located solution

`PredictiveProcessObserver` implements YAWL's `ObserverGateway` interface and is
registered directly with the engine. It runs **inside the same JVM** as `YEngine`.

```
YAWL Engine (JVM)
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  YEngine                                                    │
│    │  announceWorkItemStatusChange(item, oldStatus, ...)    │
│    │  announceCaseStarted(caseID, ...)                      │
│    │  announceCaseCancelled(caseID)                         │
│    │  announceCaseCompleted(caseID)                         │
│    │                                                        │
│    ▼  (ObserverGateway callback)                           │
│  PredictiveProcessObserver                                  │
│    │  ← receives event synchronously, zero network hops    │
│    │                                                        │
│    ├─► PredictiveModelRegistry.infer(features)             │
│    │       (ONNX Runtime — in-process, no HTTP)            │
│    │                                                        │
│    └─► EventDrivenAdaptationEngine.process(event)          │
│            (fires AdaptationRules synchronously)            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Latency**: The inference path from engine callback to rule firing is
**sub-millisecond** for typical ONNX models (small tabular classifiers).

---

## Observer lifecycle

`PredictiveProcessObserver` maps each YAWL engine announcement to a typed
`ProcessEvent`:

| YAWL announcement | Event type constant | Payload keys |
|---|---|---|
| `announceCaseStarted` | `CASE_OUTCOME_PREDICTION` | `caseId`, `outcomeScore`, `riskScore`, `confidence` |
| `announceWorkItemStatusChange` (ENABLED) | `NEXT_ACTIVITY_SUGGESTION` | `caseId`, `suggestedTask`, `confidence` |
| `announceWorkItemStatusChange` (FIRED) | `SLA_BREACH_PREDICTED` | `caseId`, `remainingMinutes` |
| `announceWorkItemStatusChange` (COMPLETED) | `CASE_OUTCOME_PREDICTION` | updated scores |
| `announceCaseCancelled` | `PROCESS_ANOMALY_DETECTED` | `caseId`, `anomalyScore` |
| Timer expiry | `TIMER_EXPIRY_BREACH` | `caseId`, `workItemId` |

---

## Non-blocking design

`PredictiveProcessObserver` is registered as an `ObserverGateway` — it is called
**on the engine thread**. To avoid blocking workflow execution:

1. **PIExceptions are caught and logged** — never propagated to the engine.
   An ONNX inference failure will not halt a case.
2. **Feature extraction is fast** — reads from in-memory event cache, not from DB.
3. **Rule actions are asynchronous** — `AdaptationAction` (ESCALATE_TO_MANUAL,
   NOTIFY_STAKEHOLDERS, etc.) submits work to an async queue, not blocking the callback.

```java
@Override
public void announceCaseStarted(YSpecificationID specID, String caseID, ...) {
    try {
        CaseOutcomePrediction p = predictor.predict(caseID);   // in-process ONNX
        adaptationEngine.process(ProcessEvent.caseOutcome(caseID, p));
    } catch (PIException e) {
        log.warn("Prediction failed for case {}: {}", caseID, e.getMessage());
        // engine callback returns normally — case is unaffected
    }
}
```

---

## AutoML fits the model that runs here

`ProcessMiningAutoMl` trains the ONNX model that `PredictiveProcessObserver` runs.
The full lifecycle:

```
Historical cases (WorkflowEventStore)
    │
    ▼  ProcessMiningTrainingDataExtractor.extractTabular()
TrainingDataset (feature matrix + labels)
    │
    ▼  Tpot2Bridge.fit()  [Python subprocess — TPOT2 genetic search]
Tpot2Result.onnxModelBytes
    │
    ▼  PredictiveModelRegistry.register("case_outcome", path)
ONNX model registered
    │
    ▼  PredictiveProcessObserver.announceCaseStarted()
                                    │
                                    ▼  PredictiveModelRegistry.infer()
                                CaseOutcomePrediction  (zero ETL)
```

The model trained on historical cases is the **same model** that fires on every
new case arrival. There is no separate serving infrastructure.

---

## When not to use co-location

Co-location makes sense when:
- Inference latency must be sub-millisecond
- You want to react to events in the same transaction as the engine
- You do not want to operate a separate ML serving cluster

Consider an external serving approach when:
- The model is very large (transformer, deep network) and impacts engine GC
- You need A/B testing with traffic splitting
- Compliance requires physical separation of AI and workflow systems

For YAWL's typical use cases (tabular classifiers on case features — 5 numeric inputs),
co-location is the right default.
