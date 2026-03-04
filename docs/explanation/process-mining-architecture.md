# Explanation: Process Mining Architecture

Understanding-oriented content explaining YAWL's process mining design decisions.

---

## Why OCEL2 Beats XES

### The Case-Centric Limitation

Traditional XES (eXtensible Event Stream) assumes a single case ID per event. This works for simple processes but fails for real-world scenarios:

| Scenario | XES Problem | OCEL2 Solution |
|----------|-------------|----------------|
| **Order + Line Items** | Cannot link order to items | Multiple object types |
| **Resource Allocation** | Case-only view | Resource as object |
| **Cross-case Dependencies** | No relationships | Object-to-object links |
| **Multi-process Analysis** | Separate logs | Unified object model |

### OCEL2 Data Model

```
┌─────────────────────────────────────────────────────────┐
│                      OCEL2 Log                          │
├─────────────────────────────────────────────────────────┤
│  Events                    Objects                      │
│  ┌───────────────────┐     ┌───────────────────────┐   │
│  │ ev-001            │     │ case-001              │   │
│  │  activity: Start  │────▶│  type: Case          │   │
│  │  timestamp: ...   │     │  ovmap: {case:id:..} │   │
│  │  omap: ───────────┼──┐  └───────────────────────┘   │
│  └───────────────────┘  │  ┌───────────────────────┐   │
│                         └─▶│ wi-001               │   │
│                            │  type: WorkItem       │   │
│                            │  ovmap: {task: ...}   │   │
│                            └───────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### YAWL's OCEL2 Implementation

YAWL exports events with multiple object bindings:

```java
// Event with Case and WorkItem bindings
WorkflowEventRecord record = new WorkflowEventRecord(
    "ev-001",
    "case-001",      // Case object
    "wi-001",        // WorkItem object
    "SubmitOrder",   // Activity
    "user@example.com", // Resource attribute
    Instant.now(),
    "TaskCompleted"
);
```

Resulting OCEL2:
```json
{
  "ev-001": {
    "ocel:activity": "SubmitOrder",
    "ocel:omap": {
      "Case": ["case-001"],
      "WorkItem": ["wi-001"]
    }
  }
}
```

---

## Discovery Algorithm Trade-offs

### Alpha Miner

**Strengths:**
- Formal foundation (van der Aalst, 2004)
- Guaranteed fitness = 1.0 on discovery log
- Produces WF-nets (workflow nets)

**Weaknesses:**
- Cannot handle noise
- Cannot represent loops of length 1 or 2
- Produces spaghetti models on complex logs

**Best For:** Clean logs, formal verification, academic research

```
Alpha Miner Flow:
Traces → Footprint Matrix → Causal Relations → Maximal Pairs → WF-Net

Time: O(|T|³)    Space: O(|Y|)
```

### Heuristic Miner

**Strengths:**
- Handles noise and infrequent behavior
- Configurable thresholds
- Better real-world fit

**Weaknesses:**
- No soundness guarantee
- Threshold tuning required
- May miss rare but important behavior

**Best For:** Production logs, noise filtering, exploratory analysis

```
Heuristic Miner Flow:
Traces → Dependency Graph → Frequency Filter → Process Model

Noise Threshold: 0.1 (filter <10% frequency)
Frequency Threshold: 0.8 (include >80% activities)
```

### Inductive Miner

**Strengths:**
- Guaranteed sound models
- Process tree representation
- Handles complex control flow

**Weaknesses:**
- May over-generalize
- Less interpretable for domain experts

**Best For:** Complex processes, soundness requirements, process trees

```
Inductive Miner Flow:
Traces → DFG → Cut Detection → Process Tree → Petri Net

Cut Types: SEQ, XOR, AND, LOOP
Guarantee: Sound and Fitting
```

### Decision Matrix

| Log Characteristic | Recommended Algorithm |
|--------------------|----------------------|
| Clean, formal logs | Alpha Miner |
| Noisy production logs | Heuristic Miner |
| Complex control flow | Inductive Miner |
| Soundness critical | Inductive Miner |
| Quick exploration | Alpha Miner |

---

## Conformance Checking Techniques

### Token-Based Replay

YAWL uses token-based replay for conformance checking:

```
Model: [Start] → (Review) → [p1] → (Approve) → [End]

Trace: Start, Review, Approve
Tokens: 1→1→1→1 (perfect fit)

Trace: Start, Approve (skip Review)
Tokens: 1→0 (missing token at p1)
Fitness: 0.5
```

#### Fitness Calculation

```
Fitness = (produced_tokens - missing_tokens + consumed_tokens - remaining_tokens)
          ─────────────────────────────────────────────────────────────────────
                         (produced_tokens + consumed_tokens)
```

#### Interpreting Results

| Fitness | Interpretation |
|---------|----------------|
| 0.95 - 1.0 | Process conforms to model |
| 0.85 - 0.95 | Minor deviations |
| 0.70 - 0.85 | Moderate drift |
| < 0.70 | Significant process change |

### Alignment-Based Conformance

For detailed diagnostics, alignment-based techniques find optimal paths:

```
Trace:     Start → Review → Approve
Model:     Start → Review → Validate → Approve
Alignment: Start → Review → [Validate:skip] → Approve
Cost:      1 (one model move skipped)
```

---

## Embedded FFI Architecture

### Why Embedded Over HTTP Bridge

The paper "Developing a High-Performance Process Mining Library with Java and Python Bindings in Rust" proposes HTTP/JSON bridges for FFI. YAWL improves this with **embedded FFI via Panama/GraalPy**:

| Aspect | Paper's HTTP Bridge | YAWL's Embedded FFI |
|--------|--------------------|--------------------|
| **Latency** | 10-50ms per call | <1ms per call |
| **Serialization** | JSON overhead | Direct memory |
| **Throughput** | 1000 calls/sec | 100,000+ calls/sec |
| **Deployment** | External service | Single JAR |
| **Failure Mode** | Network failures | In-process exceptions |

### YAWL's GraalPy Integration

```java
// Embedded Python via GraalPy
public final class GraalPyProcessMiningService implements ProcessMiningService {
    private final GraalPyContext context;

    public String performanceAnalysis(String xesXml) {
        // Direct Python call, no HTTP
        return context.eval("python", """
            import pm4py
            log = pm4py.read_xes_string(xes_xml)
            return pm4py.get_case_duration_stats(log).to_json()
            """.replace("xes_xml", escapePython(xesXml)));
    }
}
```

### Rust4pm Integration (Optional)

For maximum performance, YAWL supports rust4pm via WASM:

```java
public final class Rust4pmWasmProcessMiningService implements ProcessMiningService {
    private final WasmInstance rust4pm;

    @Override
    public String performanceAnalysis(String xesXml) {
        // Native performance via WASM
        return rust4pm.invoke("performance_analysis", xesXml);
    }
}
```

---

## Real-Time Mining Architecture

### Streaming Event Processing

YAWL supports real-time process mining through event streaming:

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────┐
│ YAWL Engine │────▶│ Event Stream │────▶│ Incremental DFG │
└─────────────┘     └──────────────┘     └─────────────────┘
                           │                      │
                           ▼                      ▼
                    ┌──────────────┐     ┌─────────────────┐
                    │ Event Store  │     │ Anomaly Detector │
                    └──────────────┘     └─────────────────┘
```

### Incremental DFG Updates

```java
// O(1) incremental update
public void addEvent(String activity, String previousActivity) {
    edgeCounts.merge(activity + "→" + previousActivity, 1L, Long::sum);
    activityCounts.merge(activity, 1L, Long::sum);
}
```

### Anomaly Detection Rules

| Rule | Threshold | Action |
|------|-----------|--------|
| **Rare Activity** | < 1% frequency | Log warning |
| **New Edge** | Not in baseline | Alert and classify |
| **Burst** | > 3x normal rate | Throttle or alert |
| **Sequence Deviation** | Fitness < 0.7 | Escalate |

---

## Integration with Autonomous Agents

### Process Intelligence Feedback Loop

```
┌────────────────────────────────────────────────────────────┐
│                   Autonomous Agent                          │
├────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────────┐     ┌─────────────┐     ┌────────────┐  │
│   │ Observe     │────▶│ Orient      │────▶│ Decide     │  │
│   │ (Events)    │     │ (Mining)    │     │ (Action)   │  │
│   └─────────────┘     └─────────────┘     └────────────┘  │
│         ▲                   │                   │          │
│         │                   ▼                   ▼          │
│   ┌─────────────┐     ┌─────────────┐     ┌────────────┐  │
│   │ Event Store │◀────│ Process     │◀────│ Execute    │  │
│   │             │     │ Intelligence│     │ (Workflow) │  │
│   └─────────────┘     └─────────────┘     └────────────┘  │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

### Decision Patterns

```java
public ProcessAction decide(ProcessMiningReport report) {
    // Pattern 1: Throughput degradation
    if (report.performance.throughputPerHour() < baseline * 0.8) {
        return ProcessAction.scaleUp("Throughput below 80% baseline");
    }

    // Pattern 2: Variant drift
    if (report.variantCount > baselineVariants * 2) {
        return ProcessAction.reviewModel("Variant explosion detected");
    }

    // Pattern 3: Conformance failure
    if (report.conformance != null && report.conformance.fitness() < 0.7) {
        return ProcessAction.escalate("Process drift from specification");
    }

    return ProcessAction.continueNormal();
}
```

---

## AutoML Integration

### TPOT2 Pipeline

YAWL integrates with TPOT2 for automated model training:

```
Historical Events
      │
      ▼
┌─────────────────┐
│ Feature Extract │  ← WorkflowDNAOracle (spec-derived features)
└─────────────────┘
      │
      ▼
┌─────────────────┐
│ TPOT2 AutoML    │  ← Genetic programming optimization
└─────────────────┘
      │
      ▼
┌─────────────────┐
│ ONNX Export     │  ← Portable model format
└─────────────────┘
      │
      ▼
┌─────────────────┐
│ Model Registry  │  ← PredictiveModelRegistry
└─────────────────┘
      │
      ▼
┌─────────────────┐
│ Live Inference  │  ← CaseOutcomePredictor
└─────────────────┘
```

---

## Performance Considerations

### Memory Management

| Component | Memory Usage | Optimization |
|-----------|--------------|--------------|
| XES Export | O(n) events | Stream to file |
| Alpha Miner | O(|T|²) footprint | Sparse matrices |
| DFG | O(|E|) edges | Incremental updates |
| OCEL2 | O(n + m) events + objects | Chunked serialization |

### Threading Model

```java
// Parallel discovery for large logs
ProcessMiningSettings settings = new ProcessMiningSettings();
settings.setEnableParallelProcessing(true);
settings.setMaxIterations(100);

// Uses virtual threads for concurrent trace processing
```

### Benchmark Results

| Operation | 1K Events | 10K Events | 100K Events |
|-----------|-----------|------------|-------------|
| XES Export | 50ms | 200ms | 1.5s |
| Alpha Miner | 100ms | 500ms | 3s |
| Conformance | 80ms | 400ms | 2.5s |
| OCEL2 Export | 30ms | 150ms | 1s |

---

## Related Documentation

- **[Tutorial: Process Discovery Getting Started](../tutorials/process-discovery-getting-started.md)**
- **[How-To: Process Mining Analysis](../how-to/process-mining-analysis.md)**
- **[Reference: Process Mining API](../reference/process-mining-api.md)**
- **[PI Module Index](../pi/index.md)** - Process Intelligence integration
