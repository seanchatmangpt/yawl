# Configuration Reference

## PIFacadeConfig

`org.yawlfoundation.yawl.pi.PIFacadeConfig`

Immutable dependency injection record for `ProcessIntelligenceFacade`.
Passed to engine constructors. All fields are set at construction time.

```java
public record PIFacadeConfig(
    WorkflowEventStore      eventStore,      // required
    WorkflowDNAOracle       dnaOracle,       // required
    PredictiveModelRegistry modelRegistry,   // required
    ZaiService              zaiService,      // optional — may be null
    Path                    modelDirectory   // required
)
```

The compact constructor validates required fields; throws `NullPointerException`
on null for `eventStore`, `dnaOracle`, `modelRegistry`, `modelDirectory`.

### Fields

| Field | Type | Required | Description |
|---|---|---|---|
| `eventStore` | `WorkflowEventStore` | Yes | JDBC-backed event store; used by `CaseOutcomePredictor` to read case events |
| `dnaOracle` | `WorkflowDNAOracle` | Yes | Fallback risk assessor when no ONNX model is registered; also used by `PrescriptiveEngine` for alternative path discovery |
| `modelRegistry` | `PredictiveModelRegistry` | Yes | In-memory ONNX model cache; auto-loads `.onnx` files from `modelDirectory` on construction |
| `zaiService` | `ZaiService` | No | GLM-4 LLM for RAG answer generation and schema inference; `null` means graceful fallback |
| `modelDirectory` | `Path` | Yes | Directory scanned by `PredictiveModelRegistry` for `.onnx` files; directory must exist |

### Construction example

```java
PIFacadeConfig config = new PIFacadeConfig(
    new WorkflowEventStore(dataSource),
    new WorkflowDNAOracle(new XesToYawlSpecGenerator(1)),
    new PredictiveModelRegistry(Path.of("models/")),
    null,                                             // no LLM
    Path.of("models/")
);
```

---

## Tpot2Config

`org.yawlfoundation.yawl.pi.automl.Tpot2Config`

Immutable configuration for a TPOT2 training run.
Use the factory methods for sensible defaults; override fields using the record constructor.

```java
public record Tpot2Config(
    Tpot2TaskType taskType,          // required
    int           generations,       // 1–100, default 5
    int           populationSize,    // 2–500, default 50
    int           maxTimeMins,       // 1–1440, default 60
    int           cvFolds,           // 2–10, default 5
    String        scoringMetric,     // nullable — TPOT2 chooses default
    int           nJobs,             // -1 = all CPUs, default -1
    String        pythonExecutable   // required, non-blank, default "python3"
)
```

The compact constructor validates all numeric ranges and `pythonExecutable`.

### Fields

| Field | Default | Valid range | Description |
|---|---|---|---|
| `taskType` | — | See below | Process mining task to optimise |
| `generations` | 5 | [1, 100] | Number of evolutionary generations; more generations = better model, longer training |
| `populationSize` | 50 | [2, 500] | Pipelines evaluated per generation; larger = wider search, slower |
| `maxTimeMins` | 60 | [1, 1440] | Hard wall-clock limit; TPOT2 stops at this regardless of generations |
| `cvFolds` | 5 | [2, 10] | Cross-validation folds for model scoring |
| `scoringMetric` | null | Any sklearn scorer | Override sklearn scoring (e.g. `"roc_auc"`, `"f1_macro"`, `"r2"`); null = TPOT2 default |
| `nJobs` | -1 | -1 or positive int | CPU parallelism; -1 = use all available cores |
| `pythonExecutable` | `"python3"` | Non-blank string | Python binary name or full path (e.g. `"/usr/bin/python3.11"`) |

### Factory methods

```java
Tpot2Config.defaults(Tpot2TaskType taskType)
// Returns: generations=5, populationSize=50, maxTimeMins=60, cvFolds=5,
//          scoringMetric=null, nJobs=-1, pythonExecutable="python3"
```

| Factory method | `taskType` | `scoringMetric` |
|---|---|---|
| `Tpot2Config.defaults(taskType)` | any | null (TPOT2 default) |
| `Tpot2Config.forCaseOutcome()` | `CASE_OUTCOME` | `"roc_auc"` |
| `Tpot2Config.forRemainingTime()` | `REMAINING_TIME` | `"neg_mean_absolute_error"` |
| `Tpot2Config.forNextActivity()` | `NEXT_ACTIVITY` | `"f1_macro"` |
| `Tpot2Config.forAnomalyDetection()` | `ANOMALY_DETECTION` | `"roc_auc"` |

### Fast config for development

Use `generations=2, populationSize=10, maxTimeMins=5` to verify the pipeline
end-to-end in under a minute:

```java
Tpot2Config fast = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    2, 10, 5, 2, "roc_auc", -1, "python3");
```

---

## Tpot2TaskType

`org.yawlfoundation.yawl.pi.automl.Tpot2TaskType`

Enum identifying the process mining prediction task to solve.

| Constant | ML problem | sklearn estimator family | Default scoring |
|---|---|---|---|
| `CASE_OUTCOME` | Binary classification | Classifiers | `roc_auc` |
| `REMAINING_TIME` | Regression | Regressors | `neg_mean_absolute_error` |
| `NEXT_ACTIVITY` | Multi-class classification | Classifiers | `f1_macro` |
| `ANOMALY_DETECTION` | Unsupervised / binary | IsolationForest | `roc_auc` |

```java
boolean isClassification()
// Returns true for CASE_OUTCOME, NEXT_ACTIVITY, ANOMALY_DETECTION.
// Returns false for REMAINING_TIME (regression).
```

### Input feature vectors by task

All four task types use the same five features extracted by
`ProcessMiningTrainingDataExtractor`:

| Index | Feature name | Unit |
|---|---|---|
| 0 | `caseDurationMs` | milliseconds |
| 1 | `taskCount` | count |
| 2 | `distinctWorkItems` | count |
| 3 | `hadCancellations` | 0.0 or 1.0 |
| 4 | `avgTaskWaitMs` | milliseconds |

Labels differ:
- `CASE_OUTCOME`: `"completed"` or `"failed"`
- `REMAINING_TIME`: remaining milliseconds (regression target)
- `NEXT_ACTIVITY`: task name string
- `ANOMALY_DETECTION`: `"normal"` or `"anomaly"`
