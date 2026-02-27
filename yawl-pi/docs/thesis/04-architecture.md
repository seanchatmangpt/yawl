# Chapter 4 — Architecture: The YAWL PI Stack

> *"Ten capabilities. 1023 emergent combinations. One JVM."*

---

## 4.1 The Ten Capability Packages

The YAWL Process Intelligence stack implements ten co-located capability packages
across three modules (`yawl-pi`, `yawl-graaljs`, `yawl-graalwasm`), each
independently valuable and exponentially more valuable in combination:

```
org.yawlfoundation.yawl.pi
│
├── bridge/           Object-Centric Event Data (OCED) import
│   ├── OcedBridge                  Pluggable import interface
│   ├── CsvOcedBridge               Comma-separated event log import
│   ├── JsonOcedBridge              JSON event log import
│   ├── XmlOcedBridge               XML event log import
│   ├── OcedBridgeFactory           Strategy selector
│   └── SchemaInferenceEngine       Auto-detects column types and semantics
│
├── predictive/       ONNX-based real-time prediction
│   ├── PredictiveModelRegistry     Thread-safe ONNX session cache; auto-loads *.onnx
│   ├── CaseOutcomePredictor        Wraps registry for typed outcome prediction
│   ├── BottleneckPredictor         Identifies predicted bottleneck tasks
│   ├── ProcessMiningTrainingDataExtractor  Live WorkflowEventStore → TrainingDataset
│   └── TrainingDataset             Typed feature matrix + label vector
│
├── prescriptive/     Constraint-based action recommendation
│   ├── PrescriptiveEngine          Evaluates constraints → selects action
│   ├── ActionRecommender           Higher-level orchestrator
│   ├── ProcessConstraintModel      Declarative constraint definitions
│   └── ProcessAction               sealed interface: Escalate | Reroute |
│                                   ReallocateResource | NoOp
│
├── optimization/     Mathematical process optimization
│   ├── ResourceOptimizer           Hungarian algorithm for assignment problems
│   ├── ProcessScheduler            Earliest-deadline-first case scheduling
│   ├── AlignmentOptimizer          Optimal trace–model alignment
│   └── AssignmentProblem/Solution  Typed input/output records
│
├── rag/              Natural language process reasoning
│   ├── NaturalLanguageQueryEngine  NL query → structured process knowledge
│   ├── ProcessKnowledgeBase        Indexed process facts and events
│   ├── ProcessContextRetriever     Semantic similarity retrieval
│   └── KnowledgeEntry              Typed knowledge record
│
├── automl/           TPOT2 AutoML for process mining tasks
│   ├── ProcessMiningAutoMl         Static facade: autoTrain{CaseOutcome,
│   │                               RemainingTime,NextActivity,AnomalyDetection}
│   ├── Tpot2Bridge                 Python subprocess management
│   ├── Tpot2Config                 Hyperparameter configuration record
│   ├── Tpot2Result                 Training result: accuracy + model path
│   └── Tpot2TaskType               Enum: CASE_OUTCOME | REMAINING_TIME |
│                                         NEXT_ACTIVITY | ANOMALY_DETECTION
│
├── adaptive/         Real-time adaptation via ObserverGateway      [new v6.0]
│   ├── PredictiveProcessObserver   ObserverGateway impl: 12 callbacks, all real
│   ├── PredictiveAdaptationRules   Rule factory: 7 methods + 4 vertical rule sets
│   └── EnterpriseAutoMlPatterns    Tutorial facade: 4 verticals in 1 line each
│
└── mcp/              MCP tool provider for AI agent integration
    ├── PIToolProvider              Exposes PI capabilities as MCP tools
    └── PISkill                    Individual tool skill definition

org.yawlfoundation.yawl.graaljs   (yawl-graaljs module)          [new v6.0]
├── JavaScriptExecutionEngine       Main API; thread-safe JS evaluation
├── JavaScriptContextPool           Pool of GraalJS interpreter contexts
├── JavaScriptExecutionContext      Single GraalJS context wrapper
├── JsTypeMarshaller                Java ↔ JavaScript type conversion
└── JavaScriptSandboxConfig         Security policy (STRICT/STANDARD/PERMISSIVE/forWasm)

org.yawlfoundation.yawl.graalwasm  (yawl-graalwasm module)        [new v6.0]
├── WasmExecutionEngine             General-purpose WASM execution
├── WasmModule                      Loaded + instantiated WASM module
├── WasmBinaryCache                 ConcurrentHashMap of parsed WASM sources
├── WasmSandboxConfig               WASI level and IO access policy
├── WasmException                   7 ErrorKind values
└── Rust4pmBridge                   OCEL2 process mining via @aarkue/process_mining_wasm
                                    (Rust-compiled WASM, wasm-bindgen, JS+WASM polyglot)
```

## 4.2 The Integration Spine

The integration spine shows how co-location creates the sub-millisecond adaptation
loop. Every arrow is a same-JVM method call — no serialization, no network.

```
┌─────────────────────────────────────────────────────────────────────┐
│                         YEngine (stateful)                          │
│         cases · tasks · work items · state · event store            │
│                                                                     │
│   ┌───────────────────┐         ┌───────────────────────────────┐   │
│   │   YNetRunner      │         │      WorkflowEventStore       │   │
│   │  (case execution) │─events─▶│  (append-only event log)     │   │
│   └─────────┬─────────┘         └───────────────┬───────────────┘   │
│             │                                   │                   │
│             │ ObserverGateway callbacks          │ direct read       │
│             │ (synchronous, no serialization)    │ (same JVM)        │
└─────────────┼───────────────────────────────────┼───────────────────┘
              │                                   │
              ▼                                   ▼
┌─────────────────────────┐   ┌───────────────────────────────────────┐
│ PredictiveProcessObserver│   │  ProcessMiningTrainingDataExtractor   │
│  (adaptive package)      │   │       (predictive package)            │
│                          │   └───────────────────────────────────────┘
│  announceCaseStarted()   │                     │
│  announceFiredWorkItem() │                     │ TrainingDataset
│  announceTimerExpiry()   │                     ▼
│  announceDeadlock()      │   ┌───────────────────────────────────────┐
│  announceWorkItem        │   │         ProcessMiningAutoMl           │
│    StatusChange()        │   │       (automl package)                │
│  announceCaseCompletion()│   │  autoTrainCaseOutcome()               │
│  ...9 more callbacks     │   │  autoTrainRemainingTime()             │
└──────────┬───────────────┘   │  autoTrainNextActivity()             │
           │                   │  autoTrainAnomalyDetection()          │
           │ registry.infer()  └───────────────────────────────────────┘
           │ (50–500 μs ONNX)                  │
           ▼                                   │ ONNX model path
┌──────────────────────────┐                   ▼
│  PredictiveModelRegistry │◀──────────────────┘
│   (predictive package)   │   register(taskName, modelPath)
│                          │
│  ConcurrentHashMap       │
│  OrtSession × 4 models   │
└──────────┬───────────────┘
           │ float[] inference result
           ▼
┌──────────────────────────┐
│  ProcessEvent emission   │
│  (CASE_OUTCOME_PREDICTION│
│   SLA_BREACH_PREDICTED   │
│   NEXT_ACTIVITY_SUGGESTION│
│   PROCESS_ANOMALY_DETECTED│
│   TIMER_EXPIRY_BREACH)   │
└──────────┬───────────────┘
           │
           ▼
┌──────────────────────────┐
│ EventDrivenAdaptation    │
│      Engine              │
│  (integration.adaptation)│
│                          │
│  Rules sorted by priority│
│  First match → action    │
└──────────┬───────────────┘
           │ AdaptationResult
           ▼
┌──────────────────────────┐
│   Application Handler    │
│  (domain-specific logic) │
│                          │
│  REJECT_IMMEDIATELY      │
│  ESCALATE_TO_MANUAL      │
│  REROUTE_TO_SUBPROCESS   │
│  NOTIFY_STAKEHOLDERS     │
│  INCREASE_PRIORITY       │
│  PAUSE_CASE              │
│  CANCEL_CASE             │
│  DECREASE_PRIORITY       │
└──────────────────────────┘
```

The entire path from `ObserverGateway` callback to `AdaptationResult` runs
synchronously in the engine thread. No I/O. No serialization. No thread handoff.

## 4.3 The Twelve Callbacks

`PredictiveProcessObserver` implements every `ObserverGateway` method with real
predictive or signalling behaviour. This is a deliberate design choice: the H-Guards
rule (`real_impl ∨ throw`) forces every callback to do something meaningful.

| Callback | Behaviour | Model used |
|---|---|---|
| `announceCaseStarted` | Predict case outcome at intake | `case_outcome` |
| `announceFiredWorkItem` | Predict remaining time at earliest opportunity | `remaining_time` |
| `announceCancelledWorkItem` | Score cancellation as anomaly signal | `anomaly_detection` |
| `announceTimerExpiry` | Emit `TIMER_EXPIRY_BREACH` at CRITICAL (no model) | — (definite breach) |
| `announceWorkItemStatusChange` | Remaining-time + next-activity on completion | `remaining_time`, `next_activity` |
| `announceCaseCompletion` (broadcast) | Post-case anomaly detection | `anomaly_detection` |
| `announceCaseCompletion` (single) | Delegate to anomaly detection logic | `anomaly_detection` |
| `announceDeadlock` | Emit `PROCESS_ANOMALY_DETECTED` score=1.0 at CRITICAL | — (structural) |
| `announceCaseCancellation` | Score cancellation as anomaly signal | `anomaly_detection` |
| `announceCaseSuspended` | WARN log (stalled case requiring attention) | — |
| `announceCaseSuspending` | INFO log (entering suspension) | — |
| `announceCaseResumption` | INFO log (resuming from suspension) | — |
| `announceEngineInitialised` | INFO log with registered service count | — |
| `shutdown` | INFO log (clean teardown confirmation) | — |

The three structural callbacks (timer expiry, deadlock, suspended) produce definitive
signals without model inference — the structural event is itself the evidence.

## 4.4 The Adaptation Rule Algebra

`AdaptationCondition` provides a composable algebra for building rule conditions:

```java
// Primitives
AdaptationCondition.eventType(String type)           // eventType == type
AdaptationCondition.payloadAbove(String key, double) // payload[key] > threshold
AdaptationCondition.payloadBelow(String key, double) // payload[key] < threshold
AdaptationCondition.payloadEquals(String key, Object)// payload[key] == target
AdaptationCondition.severityAtLeast(EventSeverity)   // severity >= min

// Combinators
AdaptationCondition.and(a, b)  // a && b (short-circuit)
AdaptationCondition.or(a, b)   // a || b (short-circuit)
```

`AdaptationRule` binds a condition to an action with a priority:

```java
// Insurance fraud rule: anomaly > 0.85 → reject immediately
new AdaptationRule(
    "rule-fraud-85",
    "High-confidence fraud rejection",
    AdaptationCondition.and(
        AdaptationCondition.eventType(PROCESS_ANOMALY_DETECTED),
        AdaptationCondition.payloadAbove("anomalyScore", 0.85)
    ),
    AdaptationAction.REJECT_IMMEDIATELY,
    10,   // priority (lower = higher precedence)
    "Reject cases where anomaly_detection score exceeds 0.85"
);
```

`EventDrivenAdaptationEngine` is immutable and thread-safe. Rules are evaluated
in priority order; first match wins; all matched rules are included in the result
for audit purposes.

## 4.5 The Diátaxis Mapping

The `adaptive` package is structured according to the Diátaxis documentation
framework, which maps to four roles users need:

| Diátaxis role | Class | Purpose |
|---|---|---|
| **Tutorial** (learning by doing) | `EnterpriseAutoMlPatterns` | One-line factory per vertical |
| **How-to** (solving a specific problem) | `PredictiveAdaptationRules` | Rule factory for each enterprise pattern |
| **Reference** (technical description) | `PredictiveProcessObserver` | Full callback semantics documented |
| **Explanation** (conceptual understanding) | `package-info.java` | Co-location thesis, comparison table |

This mapping ensures that a developer at any experience level — from "I just want
insurance triage to work" to "I need to understand exactly what fires on
`announceDeadlock`" — has a documented entry point.

## 4.6 The OCEL2 Grounding — Answering "No AI Without PI!"

Van der Aalst's 2025 paper *"No AI Without PI!"* argues that **Process Intelligence
is the grounding layer for all enterprise AI**: without access to actual process
execution data (event logs), AI systems produce recommendations that are
ungrounded in operational reality.

YAWL v6.0 answers this argument with a concrete implementation:

```
OCEL2 XML event log (external)
    ↓
Rust4pmBridge.parseOcel2XmlToJsonString(ocel2Xml)
    ↓  (Rust-compiled WASM via GraalWasm polyglot — no Python, no network)
JSON representation of OCEL2 object-centric event log
    ↓
OcedBridge (bridge package) — import into WorkflowEventStore
    ↓
ProcessMiningTrainingDataExtractor — extract features from live event store
    ↓
ProcessMiningAutoMl.autoTrainCaseOutcome(...) — TPOT2 discovers optimal pipeline
    ↓
PredictiveModelRegistry — ONNX model deployed in-process
    ↓
PredictiveProcessObserver — predictions fire in ObserverGateway callbacks (<1ms)
```

**What this eliminates**: In PM4Py, ocpa, or Celonis, OCEL2 parsing requires a
Python runtime or an external platform API call. The round-trip is measured in
seconds. `Rust4pmBridge` parses OCEL2 natively in the JVM via a Rust-compiled
WebAssembly module — the call completes in the same thread, in the same JVM,
with no serialization boundary.

**The van der Aalst 5-connection diagram** maps to YAWL v6.0 as follows:

| Van der Aalst connection | YAWL v6.0 implementation |
|---|---|
| Event data → process models | `Rust4pmBridge` + `OcedBridge` → `WorkflowEventStore` |
| Process models → predictions | `ProcessMiningAutoMl` → ONNX via TPOT2 |
| Predictions → decisions | `PredictiveProcessObserver` → `EventDrivenAdaptationEngine` |
| Decisions → actions | `AdaptationAction`: 8 engine-level interventions |
| Actions → event data (loop) | `YNetRunner` → `WorkflowEventStore` → closed loop |

The co-location thesis is van der Aalst's PI argument taken to its logical
conclusion: not just that PI grounds AI, but that **co-location of PI and
execution eliminates every latency barrier** between data, model, and action.

---

*← [Chapter 3](03-theoretical-foundation.md) · → [Chapter 5 — Use Cases](05-enterprise-use-cases.md)*
