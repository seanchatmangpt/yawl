# Explanation: Process Intelligence Architecture

Deep dive into the design, principles, and trade-offs of the YAWL PI module.

---

## Philosophy: "No AI Without PI"

The YAWL PI module is built on a foundational principle from workflow process mining:

> **"No AI without PI"** — van der Aalst, 2025

This means you cannot intelligently improve workflows without:
1. Understanding patterns in historical case data (process mining)
2. Predicting outcomes before they happen (predictive analytics)
3. Recommending optimal actions in real-time (prescriptive analytics)
4. Learning from outcomes to improve future decisions (feedback loops)

PI is therefore the **foundation** upon which AI-driven workflow optimization rests.

---

## The Five Connections

YAWL PI implements five distinct AI capabilities:

### Connection 1: Predictive Intelligence

**Purpose:** Predict workflow outcomes (delay, cost, risk) before completion.

**Architecture:**

```
Case Data (features)
    ↓
Feature Engineering (normalize, scale, encode)
    ↓
ONNX Model Inference (pre-trained)
    ↓
Prediction + Confidence Score
    ↓
Action Trigger (if confidence > threshold)
```

**Why ONNX?**
- Runtime-agnostic: trained in Python, deployed in Java
- Vendor-neutral: models from TensorFlow, PyTorch, scikit-learn
- Fast: optimized inference, <100ms per prediction
- Portable: single .onnx file, no dependencies

**Trade-off:** Pre-training required. Models must be trained offline.

### Connection 2: Prescriptive Intelligence

**Purpose:** Recommend specific actions when predictions signal risk.

**Architecture:**

```
Prediction + Case Context
    ↓
Rule Engine (Drools)
    ↓
Candidate Actions (escalate, reroute, reallocate)
    ↓
Rank by Confidence (weighted scoring)
    ↓
Recommendation + Rationale
    ↓
Execute (auto) or Approve (manual)
```

**Why rules?**
- Transparent: rules are human-readable
- Auditable: explain why action was recommended
- Deterministic: no black-box randomness
- Controllable: business steers what can be automated

**Trade-off:** Rules must be authored. Cannot automatically discover optimal actions.

### Connection 3: Optimization

**Purpose:** Assign tasks to resources optimally (minimize cost, maximize fairness).

**Algorithm: Hungarian Algorithm**

```
Task-Resource Bipartite Graph
    ↓
Cost Matrix (distance/skill/availability)
    ↓
Hungarian Algorithm (O(n³))
    ↓
Optimal Matching
    ↓
Assignments (one task per resource, one resource per task)
```

**Why Hungarian?**
- Guaranteed optimal solution
- Polynomial time (tractable for 1000s of assignments)
- Proven algorithm (since 1955)

**Trade-off:** Optimal ≠ fair (may overload one resource). Requires constraints (capacity, skill).

### Connection 4: RAG (Retrieval-Augmented Generation)

**Purpose:** Answer natural language questions about process history.

**Architecture:**

```
User Question ("Which orders were delayed?")
    ↓
Vectorize Question (embeddings model)
    ↓
Search Vector DB (WEAVIATE) for similar events
    ↓
Rank by Similarity (threshold: 0.8)
    ↓
Context Window (top 5 documents)
    ↓
LLM Prompt with Context
    ↓
Answer + Citations
```

**Why RAG vs fine-tuning?**
- Data privacy: external LLM never sees your data
- Recency: answers always use latest case data
- Cost: cheaper than fine-tuning
- Interpretability: can trace back to source events

**Trade-off:** Quality depends on vector DB quality and embeddings model.

### Connection 5: Data Preparation

**Purpose:** Normalize event logs to OCEL2 (Object-Centric Event Log) standard.

**Why OCEL2?**
- Standard format: compatible with PM4Py, ProM, other tools
- Object-centric: tracks multiple entities (order, customer, item) simultaneously
- Event-sourced: immutable event log, perfect for replaying
- Queryable: XES format allows graph queries

---

## Design Principles

### 1. Separation of Concerns

Each connection is independent and can be disabled:

```yaml
yawl:
  pi:
    predictive: enabled    # Predict outcomes
    prescriptive: enabled  # Recommend actions
    optimization: disabled # Don't optimize assignments
    rag: disabled          # Don't use NLQ
    bridge: enabled        # Always normalize data
```

### 2. Pluggable Backends

Swap implementations without changing code:

```java
PIFacadeConfig config = PIFacadeConfig.builder()
    .predictiveBackend(PredictiveBackend.ONNX)
    // Or: .predictiveBackend(PredictiveBackend.REST)  // Call remote ML service
    .ragBackend(RagBackend.WEAVIATE)
    // Or: .ragBackend(RagBackend.PINECONE)
    .build();
```

### 3. Graceful Degradation

If a connection fails, the system continues (doesn't crash):

```java
try {
    prediction = pi.predictCaseOutcome(caseId, data);
} catch (PIException e) {
    // Log failure
    logger.warn("Prediction failed, continuing without prediction", e);
    // Return null confidence (system understands this)
    prediction = CaseOutcomePrediction.UNKNOWN;
}
```

### 4. Observable by Default

Every operation is traced, metered, and logged:

```
2026-02-28 10:05:42 [mcp-1] [TRACE] pi.predictive.case_outcome_predictor
  caseId=42 duration=87ms confidence=0.92 outcome=delayed
```

---

## Data Flow

### Typical Workflow with PI

```
1. Case Enabled
   ↓
2. Engine publishes WorkItemEnabledEvent
   ↓
3. PI listener receives event
   ├─→ Extract case features (amount, priority, actor, ...)
   ├─→ Call Predictive: "Will this delay?"
   └─→ If delay risk > 70%, call Prescriptive: "What to do?"
   ├─→ Rule engine recommends [Escalate, Reallocate, ExtendDeadline]
   └─→ Log recommendation for human review (or auto-execute)
   ↓
4. Case completes
   ├─→ Actual outcome recorded (delayed/on-time)
   └─→ Stored for future model retraining
```

### Model Training Loop

```
Historical Cases (1000s)
    ↓
Extract Features (delay, cost, risk, ...)
    ↓
Split into Train/Test (80/20)
    ↓
AutoML (TPOT2) finds best model
    ↓
Evaluate on holdout test set
    ├─→ Accuracy > 85%? ✓ Deploy
    └─→ Accuracy < 85%? ✗ Retrain with more data
    ↓
Export as ONNX
    ↓
Register in model registry
    ↓
PI loads model on next startup
```

---

## Performance Characteristics

### Latency

| Operation | Typical Latency | P99 Latency | Constraint |
|-----------|-----------------|-------------|-----------|
| ONNX prediction | 50-100ms | 200ms | Model size |
| Rule evaluation | 10-50ms | 100ms | Rule count |
| Hungarian algorithm | 100-500ms | 1000ms | Task count |
| Vector search | 20-100ms | 300ms | Vector DB |
| Feature extraction | 10-30ms | 50ms | Feature count |

### Throughput

With reasonable hardware (8-core CPU, 16GB RAM):
- Predictions: **1000/sec** (single-threaded)
- Batch predictions: **10,000/sec** (8 workers)
- Rules: **5000/sec**
- Optimizations: **100/sec** (Hungarian algorithm)

### Memory

- ONNX model: **50-500MB** (depends on model size)
- Prediction cache (Redis): **100MB** for 10,000 entries
- Vector embeddings: **200MB** for 50,000 case vectors

---

## Caching Strategy

### Prediction Cache

Cache similar cases to avoid redundant predictions:

```
Request: predictCaseOutcome(caseId, features)
    ↓
Hash(features) → cache key
    ↓
Cache Hit? → return cached prediction (TTL: 60 min)
Cache Miss? → compute, cache result, return
```

**Benefit:** 70-80% hit rate typical for repetitive workflows.

### Vector Cache

Pre-computed embeddings for event logs:

```
Event: "order placed on 2026-02-28"
    ↓
Embeddings Model: "order placed on [timestamp]" → [0.12, -0.45, 0.67, ...]
    ↓
Cache in Redis with TTL 24h
    ↓
RAG searches use cached embeddings
```

---

## Failure Modes & Resilience

### Failure 1: Model Not Found

**Symptom:** Prediction returns UNKNOWN confidence

**Cause:** ONNX file missing or corrupted

**Recovery:** System continues without predictions (graceful degradation)

**Solution:** Pre-load models on startup, monitor registry

### Failure 2: Rule Engine Crashes

**Symptom:** No recommendations generated

**Cause:** Malformed rule, infinite loop

**Recovery:** Prescriptive disabled, prescriptive.enabled=false

**Solution:** Unit test all rules before deployment

### Failure 3: Vector DB Unavailable

**Symptom:** RAG queries timeout

**Cause:** WEAVIATE unreachable or full

**Recovery:** RAG disabled, rag.enabled=false, fallback to BM25

**Solution:** Multi-region vector DB, connection pooling

### Failure 4: Prediction Takes Too Long

**Symptom:** Case waits for PI decision

**Cause:** Model too large, GPU unavailable

**Recovery:** Timeout (default 5 sec), proceed without prediction

**Solution:** Quantize model (float32 → int8), use model inference server

---

## Trade-offs

### Accuracy vs Latency

| Strategy | Accuracy | Latency | Use Case |
|----------|----------|---------|----------|
| Simple LR | 75% | 10ms | Fast decisions |
| Neural Net | 88% | 100ms | Normal operations |
| Ensemble | 92% | 500ms | Critical decisions |

### Automation vs Human Control

| Strategy | Speed | Auditability | Risk |
|----------|-------|--------------|------|
| Auto-execute | Fast | Low | High |
| Recommend + Log | Slower | High | Medium |
| Manual approval | Slowest | High | Low |

Recommendation: Start with "recommend + log", escalate to "auto-execute" after proving accuracy.

### Cost vs Coverage

| Strategy | Cost | Coverage | Scalability |
|----------|------|----------|-------------|
| Process Mining Tool | High $$ | 100% | Limited |
| ML Ops (in-house) | High $$ | 95% | High |
| SaaS ML | Low $ | 50% | Medium |

Recommendation: Start with SaaS (low cost), migrate to in-house ML Ops as volume grows.

---

## Integration Patterns

### Pattern 1: Reactive (Event-Driven)

```
Case fires task
    ↓
Engine publishes event
    ↓
PI subscriber listens
    ↓
PI makes prediction
    ↓
PI recommends action
    ↓
Action logged (human reviews)
```

**Pros:** Real-time, responsive
**Cons:** Latency-sensitive (must complete in <5 sec)

### Pattern 2: Batch (Nightly)

```
1 AM: Cron job fires
    ↓
Analyze all cases from yesterday
    ↓
PI predicts tomorrow's delays
    ↓
Pre-allocate resources
    ↓
Send escalations to managers
```

**Pros:** No latency pressure, can do complex analysis
**Cons:** Not real-time, decisions based on yesterday's data

### Pattern 3: Hybrid

```
Real-time: Quick predictions (LR model, 10ms)
    ↓
If confidence < 0.8: Batch deeper analysis (Neural Net, 100ms)
    ↓
If still uncertain: Send to human (expert review)
```

**Pros:** Best of both worlds
**Cons:** Complexity

---

## Future Enhancements

1. **Continuous Learning** — Retrain models daily without manual intervention
2. **Federated Learning** — Train across multiple YAWL instances privately
3. **Causal Inference** — Explain "why" action improved (not just "what")
4. **Digital Twin** — Simulation before execution
5. **Explainable AI (XAI)** — Transparent model predictions

---

## See Also

- **Reference:** `docs/reference/pi-api.md`
- **How-To:** `docs/how-to/pi-configuration.md`
- **Tutorial:** `docs/tutorials/pi-getting-started.md`
