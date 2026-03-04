# API Reference

## Core Classes

### Tpot2Bridge

Main entry point for TPOT2 AutoML training.

```java
public final class Tpot2Bridge implements AutoCloseable
```

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `Tpot2Bridge()` | Creates bridge, extracts Python runner to temp dir |
| `Tpot2Bridge(Path tempDir, Path runnerScript)` | Package-private, for testing |

#### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `fit(TrainingDataset dataset, Tpot2Config config)` | `Tpot2Result` | Runs AutoML optimization |
| `close()` | `void` | Removes temp directory |

#### Example

```java
try (Tpot2Bridge bridge = new Tpot2Bridge()) {
    Tpot2Result result = bridge.fit(dataset, config);
}
```

---

### Tpot2Config

Immutable configuration record for training runs.

```java
public record Tpot2Config(
    Tpot2TaskType taskType,
    int generations,        // 1-100
    int populationSize,     // 2-500
    int maxTimeMins,        // 1-1440
    int cvFolds,            // 2-10
    String scoringMetric,   // nullable
    int nJobs,              // -1 = all CPUs
    String pythonExecutable
)
```

#### Factory Methods

| Method | Task Type | Scoring Metric |
|--------|-----------|----------------|
| `defaults(Tpot2TaskType)` | Any | null (TPOT2 default) |
| `forCaseOutcome()` | CASE_OUTCOME | `roc_auc` |
| `forRemainingTime()` | REMAINING_TIME | `neg_mean_absolute_error` |
| `forNextActivity()` | NEXT_ACTIVITY | `f1_macro` |
| `forAnomalyDetection()` | ANOMALY_DETECTION | `roc_auc` |

#### Validation

| Parameter | Range | Error |
|-----------|-------|-------|
| `generations` | 1-100 | `IllegalArgumentException` |
| `populationSize` | 2-500 | `IllegalArgumentException` |
| `maxTimeMins` | 1-1440 | `IllegalArgumentException` |
| `cvFolds` | 2-10 | `IllegalArgumentException` |
| `taskType` | non-null | `NullPointerException` |
| `pythonExecutable` | non-blank | `IllegalArgumentException` |

---

### Tpot2Result

Immutable result record from training.

```java
public record Tpot2Result(
    Tpot2TaskType taskType,
    double bestScore,
    String pipelineDescription,
    byte[] onnxModelBytes,
    long trainingTimeMs
)
```

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `taskType` | `Tpot2TaskType` | Task type that was optimized |
| `bestScore` | `double` | Cross-validated score |
| `pipelineDescription` | `String` | Human-readable sklearn pipeline |
| `onnxModelBytes` | `byte[]` | Serialized ONNX model |
| `trainingTimeMs` | `long` | Wall-clock training time |

---

### Tpot2TaskType

Enum for process mining task types.

```java
public enum Tpot2TaskType
```

#### Values

| Value | Type | Description |
|-------|------|-------------|
| `CASE_OUTCOME` | Classification | Binary: completed vs failed |
| `REMAINING_TIME` | Regression | Numeric: milliseconds until completion |
| `NEXT_ACTIVITY` | Classification | Multiclass: activity name |
| `ANOMALY_DETECTION` | Classification | Binary: normal vs anomaly |

#### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `isClassification()` | `boolean` | true for all except REMAINING_TIME |

---

### TrainingDataset

Immutable training data wrapper.

```java
public record TrainingDataset(
    List<String> featureNames,
    List<double[]> rows,
    List<String> labels,
    String specificationId,
    int caseCount
)
```

#### Validation

- All parameters must be non-null
- `rows.size()` must equal `labels.size()`

---

### Tpot2Exception

Checked exception for TPOT2 failures.

```java
public class Tpot2Exception extends Exception
```

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `Tpot2Exception(String message, String operation)` | Without cause |
| `Tpot2Exception(String message, String operation, Throwable cause)` | With cause |

#### Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getOperation()` | `String` | Operation that failed (e.g., "automl") |

---

## Exit Codes (Python Runner)

| Code | Meaning |
|------|---------|
| 0 | Success |
| 1 | Configuration or library error |
| 2 | Training failure (no pipeline found, ONNX export failed) |

## JSON Configuration Format

Passed to `tpot2_runner.py`:

```json
{
  "taskType": "CASE_OUTCOME",
  "generations": 5,
  "populationSize": 50,
  "maxTimeMins": 60,
  "cvFolds": 5,
  "nJobs": -1,
  "scoringMetric": "roc_auc"
}
```

## JSON Output Format

Returned on stdout:

```json
{
  "bestScore": 0.92,
  "pipelineDescription": "GradientBoostingClassifier(...)",
  "trainingTimeMs": 14325
}
```
