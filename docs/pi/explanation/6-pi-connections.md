# The Six PI Connections — Design Rationale

`ProcessIntelligenceFacade` exposes five connections directly.
A sixth (AutoML) operates independently. This document explains why each connection
exists as a separate engine rather than a monolithic AI module.

---

## Connection 1 — Predictive

**Package**: `predictive`
**Entry point**: `CaseOutcomePredictor.predict(caseId)`
**Returns**: `CaseOutcomePrediction` — completionProbability, riskScore, primaryRiskFactor

### What it does

Estimates the probability that a running case will complete successfully,
and produces a risk score [0.0–1.0] identifying the primary risk factor.

### Why it is separate

Case outcome prediction has two very different quality levels:
- **ONNX model**: high accuracy, trained on historical data, deterministic inference
- **DNA oracle fallback**: heuristic, available immediately, no training needed

Keeping prediction isolated allows the fallback to be transparent to callers.
Neither the prescriptive engine nor the MCP tools need to know which backend answered.

### Graceful degradation

```
predict(caseId)
    │
    ├─► PredictiveModelRegistry.isAvailable("case_outcome")?
    │       YES → infer(features) → CaseOutcomePrediction(fromOnnxModel=true)
    │       NO  → WorkflowDNAOracle.assessRisk(caseId)
    │                              → CaseOutcomePrediction(fromOnnxModel=false)
    └── always returns a prediction; never throws for missing model
```

---

## Connection 2 — Prescriptive

**Package**: `prescriptive`
**Entry point**: `PrescriptiveEngine.recommend(caseId, prediction)`
**Returns**: `List<ProcessAction>` — ranked, constraint-filtered, always non-empty

### What it does

Given a risk prediction, generates a ranked list of intervention candidates:
reroute, escalate, reallocate resources, or do nothing. Filters candidates
against workflow ordering constraints modelled in Apache Jena RDF.

### Why it is separate

Recommendation requires two concerns that do not belong in prediction:
1. **Domain knowledge** — which tasks are legally reachable from the current state
   (enforced by `ProcessConstraintModel` via SPARQL queries on a Jena RDF model)
2. **Scoring** — ranking candidates by expected improvement (`ActionRecommender`)

Separating prediction from recommendation allows each to evolve independently.
A richer constraint model (e.g., role-based permissions, SLA deadlines) can be
added to `ProcessConstraintModel` without touching prediction logic.

### Why the list always contains `NoOpAction`

`NoOpAction` is always included as the baseline: "this case is fine, no intervention
needed". Callers can check `actions.get(0) instanceof NoOpAction` to quickly
determine whether active intervention is warranted.

---

## Connection 3 — Optimization

**Package**: `optimization`
**Entry point**: `ResourceOptimizer.solve(problem)`, `ProcessScheduler.schedule(...)`, `AlignmentOptimizer.align(...)`
**Returns**: `AssignmentSolution`, `SchedulingResult`, `AlignmentResult`

### What it does

Three classical algorithms:

| Algorithm | Class | Problem | Complexity |
|---|---|---|---|
| Hungarian (Kuhn-Munkres) | `ResourceOptimizer` | Assign work items to resources minimizing cost | O(n³) time, O(n²) space |
| Shortest Processing Time | `ProcessScheduler` | Order tasks to minimize average completion time | O(n log n) |
| Levenshtein edit distance | `AlignmentOptimizer` | Measure trace deviation from reference model | O(m·n) |

### Why classical algorithms, not ML

For resource assignment and scheduling, classical algorithms give **provably optimal**
results. An ML approach would be approximate and harder to explain to auditors.
Alignment is a well-studied conformance checking problem with established semantics.

These three are grouped as "optimization" because they share no runtime dependencies
(no ONNX, no Jena, no event store) — stateless utilities safe to share across threads.

---

## Connection 4 — RAG (Retrieval-Augmented Generation)

**Package**: `rag`
**Entry point**: `NaturalLanguageQueryEngine.query(request)`
**Returns**: `NlQueryResponse` — answer, sourceFacts, groundedInKb, llmAvailable

### What it does

Answers natural language questions about process performance by:
1. Retrieving relevant facts from `ProcessKnowledgeBase` (keyword similarity)
2. Passing the facts and question to GLM-4 via `ZaiService`
3. Returning the grounded answer with citations

### Why RAG, not fine-tuning

Process mining facts change per deployment (different specs, different SLAs).
Fine-tuning a model per customer is expensive and requires retraining.
RAG separates the static language understanding (LLM) from the dynamic facts (KB),
making the system deployable without custom training.

### Why it degrades gracefully

`ZaiService` is declared optional in `PIFacadeConfig`. When unavailable:
- `llmAvailable = false` in the response
- The raw retrieved facts are returned as the answer
- `groundedInKb = true` confirms the facts came from real data

Operators can always get process facts even without an LLM endpoint.

---

## Connection 5 — Data Preparation (OCEL2)

**Package**: `bridge`
**Entry point**: `ProcessIntelligenceFacade.prepareEventData(rawData)` or `prepareEventData(rawData, format)`
**Returns**: OCEL2 v2.0 JSON string

### What it does

Converts raw event log data (CSV, JSON, XML) into standardized OCEL2 v2.0 format
that process mining tools consume. Auto-detects format or accepts an explicit one.
Uses `SchemaInferenceEngine` to identify semantic columns (case ID, timestamp, activity).

### Why OCEL2

OCEL2 (Object-Centric Event Log v2.0) is the IEEE standard for event logs that
handle multiple case notions (a payment linked to an order, a customer, and a product
simultaneously). Case-centric XES cannot represent these relationships.
YAWL workflows frequently involve multi-object processes.

See [OCEL2 standard](ocel2-standard.md) for a full primer.

---

## Connection 6 — AutoML (TPOT2)

**Package**: `automl`
**Entry point**: `ProcessMiningAutoMl.autoTrainCaseOutcome(specId, eventStore, registry, config)`
**Returns**: `Tpot2Result`

### What it does

Uses genetic algorithm-based pipeline search (TPOT2) to discover the best
sklearn pipeline for a process mining task, then exports it as an ONNX model
ready for Connection 1's `PredictiveModelRegistry`.

### Why AutoML instead of a fixed model

Different workflows have different signal distributions. A random forest may dominate
for insurance claims; gradient boosting for healthcare. Rather than hard-coding a
model family, TPOT2 searches the pipeline space automatically given historical data.

### Why it is not in the facade

AutoML training is a **batch operation** (minutes to hours), not a per-case request.
Including it in `ProcessIntelligenceFacade` would confuse the request/response
boundary. `ProcessMiningAutoMl` is called separately, typically during a training
window, and registers its output into `PredictiveModelRegistry` so Connection 1
can begin using the new model immediately.

---

## Why six separate engines, not one

A monolithic "AI service" would combine prediction + recommendation + optimization
+ RAG + data prep + training. This creates:

- **Coupling**: a bug in the Jena constraint evaluator would crash prediction
- **Testing difficulty**: unit tests must stub everything
- **Upgrade friction**: improving the ONNX backend requires touching RAG code
- **Deployment inflexibility**: a customer who only needs scheduling cannot opt out of ONNX

Each engine has **one reason to change**. The facade composes them without coupling them.
`ProcessIntelligenceFacade` delegates via interfaces — any engine can be swapped,
replaced with a no-op, or upgraded independently.
