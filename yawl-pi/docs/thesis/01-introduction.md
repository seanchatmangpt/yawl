# Chapter 1 — The Invisible Tax of Distributed Intelligence

> *"By the time a routing decision reflects learned behaviour, the process that
> generated the training data is already a different process."*

---

## 1.1 Two Systems, One Enterprise

Every enterprise today operates two separate systems with an invisible tax between them.

**System A — Workflow Execution** routes tasks, enforces business rules, manages case
lifecycle, and records every event as a side effect. The major implementations are
BPMN engines: Camunda, Flowable, jBPM, Bizagi. They are fast, reliable, and
generate extraordinarily rich event data. They are not designed to learn from it.

**System B — Process Intelligence** analyses that event data to produce insights.
Process mining tools (Celonis, UiPath Process Mining, ProM) discover what processes
are actually running. ML platforms (SageMaker, Azure ML, MLflow) train models to
predict outcomes, detect anomalies, and recommend actions. They are not designed to
act on their findings in real-time.

The tax between them is called **ETL**.

```
System A                    Tax                     System B
─────────                ─────────                ─────────
Workflow Engine   →  Extract  →  Transform  →  Load  →  ML Platform
  (events)           (nightly)   (pipeline)   (warehouse)  (weekly train)
                                                              ↓
                   ←  Import  ←  Deploy  ←  REST call  ←  Model
                     (batch)    (DevOps)    (per case)    (stale)
```

By the time a routing decision reflects learned behaviour, the learned behaviour
is stale — typically one week old in the best-run enterprise deployments. The model
was trained on last week's cases. This week's cases are different.

## 1.2 The Tax Is Structural, Not Engineering

This is not a problem that better engineering can solve. It is a consequence of
physics and distributed systems theory.

Any system call that crosses a process boundary requires:

1. **Serialisation** of the request (nonzero time, proportional to data size)
2. **Network transmission** (bounded below by the speed of light)
3. **Deserialisation** at the receiving end (symmetric to step 1)
4. **Queue time** (context switch, thread scheduler quantum)

The fastest achievable cross-process call in a co-located datacenter is approximately
0.1–1 milliseconds. The fastest achievable cross-cloud call is 5–50 milliseconds.
These numbers are physical constants, not engineering variables.

In contrast, a function call within the same JVM completes in nanoseconds to
microseconds. An ONNX Runtime inference call — the ML inference primitive in YAWL
v6.0's `PredictiveModelRegistry` — runs in 50–500 microseconds for tabular models.

The ratio is not a performance difference. It is a **category difference**:

| Architecture | Inference latency | Adaptation granularity |
|---|---|---|
| Distributed (REST API) | 1–50 ms | Per case, per workflow |
| Co-located (same JVM) | 50–500 μs | Per task, per token, per callback |

At 50μs latency, inference is cheap enough to run at every `ObserverGateway`
callback — every case start, every task transition, every timer expiry, every
deadlock. At 50ms latency, it is too expensive to run more than once per case.
This difference in granularity changes what adaptive behaviour is possible.

We prove this formally in [Chapter 3](03-theoretical-foundation.md) as the
**ETL Barrier Theorem**.

## 1.3 What Co-location Changes

YAWL v6.0 dissolves the boundary. The `PredictiveModelRegistry` runs ONNX Runtime
inside the same JVM as `YEngine`. The `ObserverGateway` callbacks fire synchronously
during task execution. The `ProcessMiningTrainingDataExtractor` reads from the live
`WorkflowEventStore` without an ETL hop. `ProcessMiningAutoMl` trains new models on
any workflow milestone — no scheduler, no batch job, no data engineering team.

The `PredictiveProcessObserver` implements every `ObserverGateway` callback with
real predictive or signalling behaviour:

```java
// Called by YEngine when a new case starts.
// Runs ONNX inference. Returns before the case routes.
@Override
public void announceCaseStarted(Set<YAWLServiceReference> services,
                                YSpecificationID specID, YIdentifier caseID,
                                String launchingService, boolean delayed) {
    float[] features = featureExtractor.extract(caseID.toString(), "", specID.getIdentifier(), 0L);
    float[] result = registry.infer(MODEL_CASE_OUTCOME, features);  // ~200μs
    eventSink.accept(buildCaseOutcomeEvent(caseID, specID, result));
}
```

Between the engine calling `announceCaseStarted` and the first task being enabled,
the system has already predicted the case's likely outcome and fired an adaptation
rule. No HTTP call. No serialisation. No ETL.

## 1.4 The Thesis

This thesis makes a single claim and defends it from four angles:

> **Co-location is not an optimization. It is a prerequisite for a new class of
> system — one that is self-optimizing, self-explaining, and architecturally
> inaccessible to any distributed implementation of the same components.**

The four defences:

1. **Formal** (Chapter 3): The ETL Barrier Theorem proves a structural minimum lag
   floor. The Combinatoric Value Law shows that N co-located capabilities produce
   exponentially more emergent value than N distributed capabilities.

2. **Market** (Chapter 2): Blue Ocean Analysis shows the IPOS creates an uncontested
   market space by making competition in existing categories structurally irrelevant.

3. **Architectural** (Chapter 4): The YAWL PI stack demonstrates that 8 co-located
   capabilities generate 255 emergent combinations unavailable in distributed
   deployment.

4. **Empirical** (Chapter 5): Four enterprise use cases show concrete capabilities
   that simply cannot be replicated in distributed architectures: timer-expiry-as-
   definite-breach, deadlock-as-perfect-anomaly, and synchronous rejection before
   first task execution.

---

*← [Abstract](00-abstract.md) · → [Chapter 2 — Blue Ocean](02-blue-ocean.md)*
