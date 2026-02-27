# Architecture — YAWL Process Intelligence

This document explains the internal structure of `yawl-pi`: what the packages are,
how they depend on each other, which classes are thread-safe, and where the external
TPOT2 subprocess boundary sits.

---

## Package map

```
org.yawlfoundation.yawl.pi
│
├── pi (root)          Facade + session + exception
│   ├── ProcessIntelligenceFacade   ← unified entry point
│   ├── PIFacadeConfig              ← dependency injection record
│   ├── PISession                   ← immutable session state
│   └── PIException                 ← checked exception
│
├── predictive         Case outcome prediction + training data
│   ├── CaseOutcomePredictor        ← predict(caseId) → CaseOutcomePrediction
│   ├── CaseOutcomePrediction       ← record: completionProbability, riskScore
│   ├── BottleneckPredictor         ← identifies slowest task
│   ├── PredictiveModelRegistry     ← ONNX Runtime session manager
│   ├── ProcessMiningTrainingDataExtractor ← event log → tabular features
│   └── TrainingDataset             ← record: featureNames, rows, labels
│
├── prescriptive       Action recommendations + constraint enforcement
│   ├── PrescriptiveEngine          ← recommend(caseId, prediction) → List<ProcessAction>
│   ├── ProcessAction               ← sealed interface (4 permitted types)
│   ├── RerouteAction               ← route case to alternate task
│   ├── EscalateAction              ← escalate to human
│   ├── ReallocateResourceAction    ← reassign work item
│   ├── NoOpAction                  ← no intervention needed
│   ├── ActionRecommender           ← scores + ranks candidates
│   └── ProcessConstraintModel      ← Jena RDF constraint filter
│
├── optimization       Classical algorithms
│   ├── ResourceOptimizer           ← Hungarian algorithm (O(n³))
│   ├── AssignmentProblem           ← record: workItemIds, resourceIds, costMatrix
│   ├── AssignmentSolution          ← record: assignment Map<workItem, resource>
│   ├── ProcessScheduler            ← Shortest Processing Time (SPT) scheduler
│   ├── SchedulingResult            ← record: orderedTaskIds, scheduledStartTimes
│   ├── AlignmentOptimizer          ← Levenshtein edit distance
│   └── AlignmentResult             ← record: distance, alignedSequences
│
├── bridge             Event log format conversion to OCEL2
│   ├── OcedBridge                  ← interface: inferSchema(), convert()
│   ├── OcedBridgeFactory           ← forFormat(str) + autoDetect(raw)
│   ├── CsvOcedBridge               ← CSV → OCEL2
│   ├── JsonOcedBridge              ← JSON → OCEL2
│   ├── XmlOcedBridge               ← XML → OCEL2
│   ├── OcedSchema                  ← record: column mappings
│   ├── SchemaInferenceEngine       ← AI-powered column tagging
│   └── EventDataValidator          ← OCEL2 v2.0 compliance check
│
├── rag                Natural language Q&A
│   ├── NaturalLanguageQueryEngine  ← query(NlQueryRequest) → NlQueryResponse
│   ├── ProcessKnowledgeBase        ← in-memory fact store
│   ├── KnowledgeEntry              ← record: factText, relevanceScore
│   ├── NlQueryRequest              ← record: question, specificationId, topK
│   └── NlQueryResponse             ← record: answer, sourceFacts, llmAvailable
│
├── automl             TPOT2 pipeline discovery
│   ├── ProcessMiningAutoMl         ← high-level AutoML coordinator
│   ├── Tpot2Bridge                 ← subprocess bridge to Python
│   ├── Tpot2Config                 ← record: taskType, generations, populationSize
│   ├── Tpot2TaskType               ← enum: CASE_OUTCOME, REMAINING_TIME, …
│   └── Tpot2Result                 ← record: onnxModelBytes, bestScore
│
├── adaptive           Co-located real-time rules
│   ├── PredictiveProcessObserver   ← ObserverGateway implementation
│   ├── PredictiveAdaptationRules   ← static factory for AdaptationRules
│   └── EnterpriseAutoMlPatterns    ← pre-built rule sets per industry
│
└── mcp                Agent integration
    ├── PIToolProvider              ← McpToolProvider (4 tools)
    └── PISkill                     ← A2A skill interface
```

---

## Dependency graph

```
External Systems                YAWL Engine
┌──────────────┐               ┌─────────────────────────────────┐
│  Python      │               │  YEngine / YNetRunner           │
│  (TPOT2      │               │  (announcement callbacks)       │
│   subprocess)│               └───────────────┬─────────────────┘
└──────┬───────┘                               │ ObserverGateway
       │ ProcessBuilder                        ▼
       ▼                        ┌─────────────────────────────────┐
  Tpot2Bridge ◄─── Tpot2Config  │  PredictiveProcessObserver      │
       │                        │  (adaptive package)             │
       ▼                        └────┬──────────────┬─────────────┘
  Tpot2Result                        │              │
       │                        ONNX inference   ProcessEvent emission
       ▼                             │              │
  ProcessMiningAutoMl                ▼              ▼
       │                   PredictiveModelRegistry   EventDrivenAdaptationEngine
       │                             ▲              (yawl-integration)
       │                             │
       └──────────► TrainingDataset ─┘

ProcessIntelligenceFacade  ←── PIFacadeConfig
    │  (ReentrantLock)
    ├──► CaseOutcomePredictor ──► WorkflowEventStore (yawl-integration)
    │         │                └► WorkflowDNAOracle (yawl-observatory)
    │         └──► PredictiveModelRegistry (ONNX Runtime)
    │
    ├──► PrescriptiveEngine ──► ActionRecommender
    │         │             └► ProcessConstraintModel (Apache Jena RDF)
    │         └──────────────► WorkflowDNAOracle
    │
    ├──► ResourceOptimizer (Hungarian Algorithm — no external deps)
    │
    ├──► NaturalLanguageQueryEngine ──► ProcessKnowledgeBase
    │         └──────────────────────► ZaiService (optional, GLM-4)
    │
    └──► OcedBridgeFactory ──► CsvOcedBridge / JsonOcedBridge / XmlOcedBridge
              └──────────────► SchemaInferenceEngine
```

---

## Key data types crossing package boundaries

| Type | Produced by | Consumed by |
|---|---|---|
| `CaseOutcomePrediction` | `CaseOutcomePredictor` | `PrescriptiveEngine`, `ProcessIntelligenceFacade`, `PIToolProvider`, `PredictiveProcessObserver` |
| `ProcessAction` (sealed) | `PrescriptiveEngine` | `EventDrivenAdaptationEngine`, MCP tools, A2A skills |
| `TrainingDataset` | `ProcessMiningTrainingDataExtractor` | `ProcessMiningAutoMl`, `Tpot2Bridge` |
| `OcedSchema` | `OcedBridge.inferSchema()` | `OcedBridge.convert()`, `EventDataValidator` |
| `KnowledgeEntry` | `ProcessKnowledgeBase.ingest()` | `NaturalLanguageQueryEngine.query()` |
| `NlQueryResponse` | `NaturalLanguageQueryEngine` | `ProcessIntelligenceFacade.ask()`, MCP tools |
| `Tpot2Result` | `Tpot2Bridge` | `ProcessMiningAutoMl` |
| `AssignmentSolution` | `ResourceOptimizer.solve()` | `ProcessIntelligenceFacade.optimizeResources()` |

---

## Thread-safety model

| Class | Lock | Notes |
|---|---|---|
| `ProcessIntelligenceFacade` | `ReentrantLock` | All five public methods; non-reentrant by intent |
| `ProcessKnowledgeBase` | `ReentrantReadWriteLock` | Multiple concurrent readers; exclusive writer |
| `PredictiveProcessObserver` | `ReentrantReadWriteLock` | State map protected from concurrent engine events |
| `PredictiveModelRegistry` | `ConcurrentHashMap` + `OrtEnvironment` | Thread-safe model map; ONNX sessions are thread-safe |
| `ProcessMiningTrainingDataExtractor` | `ConcurrentHashMap` + virtual threads | Per-case processing runs in parallel via `newVirtualThreadPerTaskExecutor()` |
| `ResourceOptimizer` | Stateless | New matrix copy per call — safe to share |
| `ProcessScheduler` | Stateless | New list copy per call — safe to share |
| `AlignmentOptimizer` | Stateless | DP matrix allocated per call |

---

## External process boundary: TPOT2

`Tpot2Bridge` is the only class that crosses the JVM boundary.
All other classes run within the JVM.

```
JVM
┌─────────────────────────────────────────────────────────────────────┐
│                                                                     │
│  ProcessMiningAutoMl                                                │
│       │                                                             │
│       ▼                                                             │
│  Tpot2Bridge.fit(dataset, config)                                   │
│       │                                                             │
│       │  ProcessBuilder                                             │
│       │  ──────────────────────────────────────────────────────►   │
│       │  python3 tpot2_runner.py --data x.csv --output y.onnx      │
│       │                                           (subprocess)     │
│       │  ◄──────────────────────────────────────────────────────   │
│       │  stdout: { bestScore, pipelineDescription, trainingTimeMs } │
│       │  file:   y.onnx                                             │
│       │                                                             │
│       └──► Tpot2Result(onnxModelBytes, bestScore, …)               │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

- If the Python process exits non-zero, `Tpot2Bridge` throws `PIException("automl")`.
- If the `python3` executable is not on PATH, `PIException` carries the `IOException`.
- The bridge extracts `tpot2_runner.py` to a temp directory on first use and cleans up on `close()`.

---

## Design patterns

| Pattern | Where used |
|---|---|
| **Facade** | `ProcessIntelligenceFacade` unifies 5 engines |
| **Sealed interface + record** | `ProcessAction` enables exhaustive pattern matching without null checks |
| **Strategy** | `OcedBridge` interface — 3 format-specific implementations |
| **Factory (static)** | `OcedBridgeFactory`, `PredictiveAdaptationRules`, `EnterpriseAutoMlPatterns` |
| **Observer** | `PredictiveProcessObserver` implements YAWL's `ObserverGateway` |
| **Graceful degradation** | `CaseOutcomePredictor` falls back from ONNX → DNA oracle; `NaturalLanguageQueryEngine` falls back from LLM → raw facts |
| **Subprocess bridge** | `Tpot2Bridge` — subprocess chosen over GraalPy because TPOT2 requires heavy native Python deps that GraalPy cannot host |
