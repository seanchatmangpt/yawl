# Facade API Reference

## ProcessIntelligenceFacade

`org.yawlfoundation.yawl.pi.ProcessIntelligenceFacade`

Unified entry point for all five PI connections. Thread-safe — a single instance
may be shared across threads. All methods acquire an internal `ReentrantLock`
for the duration of the call.

### Constructor

```java
public ProcessIntelligenceFacade(
    CaseOutcomePredictor predictor,
    PrescriptiveEngine   prescriptive,
    ResourceOptimizer    optimizer,
    NaturalLanguageQueryEngine nlEngine)
```

All four parameters are required. Throws `IllegalArgumentException` if any is null.

---

### Methods

#### `predictOutcome`

```java
public CaseOutcomePrediction predictOutcome(String caseId) throws PIException
```

Predicts the outcome of a running workflow case.

| Parameter | Required | Description |
|---|---|---|
| `caseId` | Yes | Workflow case identifier |

**Returns** `CaseOutcomePrediction` — never null.

**Throws**
- `PIException("predictive")` — if the underlying predictor fails
- `IllegalArgumentException` — if `caseId` is null or empty

Uses ONNX model if available in `PredictiveModelRegistry`; falls back to
`WorkflowDNAOracle` heuristics. The `fromOnnxModel` field on the result
indicates which backend was used.

---

#### `recommendActions`

```java
public List<ProcessAction> recommendActions(
    String caseId,
    CaseOutcomePrediction prediction) throws PIException
```

Recommends ranked interventions to improve a case's predicted outcome.

| Parameter | Required | Description |
|---|---|---|
| `caseId` | Yes | Workflow case identifier |
| `prediction` | Yes | Result of a prior `predictOutcome()` call |

**Returns** `List<ProcessAction>` — ranked highest-improvement first.
Always non-empty (minimum: `[NoOpAction]`).

**Throws**
- `PIException("prescriptive")` — if recommendation generation fails
- `IllegalArgumentException` — if either parameter is null or `caseId` is empty

See [Process actions reference](process-actions.md) for action types and pattern-matching.

---

#### `optimizeResources`

```java
public AssignmentSolution optimizeResources(AssignmentProblem problem) throws PIException
```

Solves a resource assignment problem using the Hungarian algorithm.

| Parameter | Required | Description |
|---|---|---|
| `problem` | Yes | Assignment problem: work items, resources, cost matrix |

**Returns** `AssignmentSolution` — optimal assignment with total cost.

**Throws**
- `PIException("optimization")` — if the algorithm fails (e.g., non-square padded matrix)
- `IllegalArgumentException` — if `problem` is null

The algorithm runs in O(n³) time. For n ≤ 100 this is typically < 10 ms.

---

#### `ask`

```java
public NlQueryResponse ask(NlQueryRequest request) throws PIException
```

Answers a natural language question using RAG (retrieval-augmented generation).

| Parameter | Required | Description |
|---|---|---|
| `request` | Yes | `NlQueryRequest` — question, optional specId scoping, topK |

**Returns** `NlQueryResponse` — answer, source facts, LLM availability flag.

**Throws**
- `PIException("rag")` — if query processing fails
- `IllegalArgumentException` — if `request` is null

When `ZaiService` is unavailable, the answer is the concatenated raw facts
from `ProcessKnowledgeBase` and `llmAvailable` is `false`.

---

#### `prepareEventData(rawData)`

```java
public String prepareEventData(String rawData) throws PIException
```

Auto-detects format (CSV / JSON / XML) and converts to OCEL2 v2.0 JSON.

| Parameter | Required | Description |
|---|---|---|
| `rawData` | Yes | Raw event log content as a string |

**Returns** OCEL2 v2.0 JSON string.

**Throws**
- `PIException("dataprep")` — if format detection or conversion fails
- `IllegalArgumentException` — if `rawData` is null or empty

---

#### `prepareEventData(rawData, format)`

```java
public String prepareEventData(String rawData, String format) throws PIException
```

Converts to OCEL2 using an explicit format name.

| Parameter | Required | Description |
|---|---|---|
| `rawData` | Yes | Raw event log content |
| `format` | Yes | `"csv"`, `"json"`, or `"xml"` (case-insensitive) |

**Returns** OCEL2 v2.0 JSON string.

**Throws**
- `PIException("dataprep")` — if conversion fails or format is unknown
- `IllegalArgumentException` — if either parameter is null or empty

---

## PISession

`org.yawlfoundation.yawl.pi.PISession`

Immutable session state for a PI analysis run.

```java
public record PISession(
    String  sessionId,          // UUID string
    String  specificationId,    // workflow specification identifier
    Instant createdAt,          // when the session was created
    Instant lastAnalyzedAt      // when the last analysis was performed (nullable)
)
```

### Factory method

```java
public static PISession start(String specificationId)
```

Creates a new session with a generated UUID and `createdAt = Instant.now()`.
`lastAnalyzedAt` is null until an analysis method is called.

---

## PIException

`org.yawlfoundation.yawl.pi.PIException`

Checked exception thrown by all PI operations.

### Constructors

```java
public PIException(String message, String connection)
public PIException(String message, String connection, Throwable cause)
```

### Method

```java
public String getConnection()
```

Returns the connection identifier that failed. Valid values:

| Value | Origin |
|---|---|
| `"predictive"` | `CaseOutcomePredictor` / `BottleneckPredictor` |
| `"prescriptive"` | `PrescriptiveEngine` |
| `"optimization"` | `ResourceOptimizer` / `ProcessScheduler` / `AlignmentOptimizer` |
| `"rag"` | `NaturalLanguageQueryEngine` |
| `"dataprep"` | `OcedBridge` family |
| `"automl"` | `Tpot2Bridge` / `ProcessMiningAutoMl` |

### Handling pattern

```java
try {
    CaseOutcomePrediction p = facade.predictOutcome(caseId);
} catch (PIException e) {
    switch (e.getConnection()) {
        case "predictive" -> log.error("Prediction failed: {}", e.getMessage());
        case "rag"        -> log.warn("RAG unavailable: {}", e.getMessage());
        default           -> throw new RuntimeException("Unexpected PI failure", e);
    }
}
```
