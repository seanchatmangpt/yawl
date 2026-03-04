# Reference: Tpot2Config

## Overview

Immutable configuration record for TPOT2 AutoML training runs.

## Package

```java
package org.yawlfoundation.yawl.tpot2;
```

## Declaration

```java
public record Tpot2Config(
    Tpot2TaskType taskType,
    int generations,
    int populationSize,
    int maxTimeMins,
    int cvFolds,
    String scoringMetric,
    int nJobs,
    String pythonExecutable
)
```

## Parameters

### taskType

**Type**: `Tpot2TaskType` (required)

Process mining use case to optimize.

| Value | Description | sklearn Estimator |
|-------|-------------|-------------------|
| `CASE_OUTCOME` | Binary: completed vs. failed | `TPOTClassifier` |
| `REMAINING_TIME` | Regression: time until completion | `TPOTRegressor` |
| `NEXT_ACTIVITY` | Multiclass: next activity label | `TPOTClassifier` |
| `ANOMALY_DETECTION` | Binary: normal vs. anomaly | `TPOTClassifier` |

### generations

**Type**: `int` (range: 1-100, default: 5)

Number of evolutionary generations. More generations = better pipelines but longer training.

### populationSize

**Type**: `int` (range: 2-500, default: 50)

Number of pipeline candidates per generation. Larger populations explore more combinations.

### maxTimeMins

**Type**: `int` (range: 1-1440, default: 60)

Maximum wall-clock time in minutes. TPOT2 stops early if this limit is reached.

### cvFolds

**Type**: `int` (range: 2-10, default: 5)

Cross-validation folds for pipeline evaluation. More folds = more robust estimates but slower.

### scoringMetric

**Type**: `String` (nullable, default: null)

sklearn scoring string. If null, TPOT2 uses the default for the estimator type.

Common values:
- Classification: `"roc_auc"`, `"f1"`, `"f1_macro"`, `"accuracy"`
- Regression: `"neg_mean_absolute_error"`, `"neg_mean_squared_error"`, `"r2"`

### nJobs

**Type**: `int` (default: -1)

Parallelism degree for sklearn operations:
- `-1`: Use all available CPUs
- `1`: Single-threaded
- `N`: Use N CPUs

### pythonExecutable

**Type**: `String` (required, default: "python3")

Python binary name or path. Must have `tpot2` and `skl2onnx` installed.

## Factory Methods

### defaults(Tpot2TaskType)

```java
public static Tpot2Config defaults(Tpot2TaskType taskType)
```

Returns default configuration for the given task type.

**Defaults**: generations=5, populationSize=50, maxTimeMins=60, cvFolds=5, scoringMetric=null, nJobs=-1, pythonExecutable="python3"

### forCaseOutcome()

```java
public static Tpot2Config forCaseOutcome()
```

Configuration optimized for case outcome prediction. Sets `scoringMetric="roc_auc"`.

### forRemainingTime()

```java
public static Tpot2Config forRemainingTime()
```

Configuration optimized for remaining time prediction. Sets `scoringMetric="neg_mean_absolute_error"`.

### forNextActivity()

```java
public static Tpot2Config forNextActivity()
```

Configuration optimized for next activity prediction. Sets `scoringMetric="f1_macro"`.

### forAnomalyDetection()

```java
public static Tpot2Config forAnomalyDetection()
```

Configuration optimized for anomaly detection. Sets `scoringMetric="roc_auc"`.

## Exceptions

### NullPointerException

Thrown if `taskType` is null.

### IllegalArgumentException

Thrown if:
- `generations` not in [1, 100]
- `populationSize` not in [2, 500]
- `maxTimeMins` not in [1, 1440]
- `cvFolds` not in [2, 10]
- `pythonExecutable` is null or blank

## Example

```java
// Using factory method
Tpot2Config config = Tpot2Config.forCaseOutcome();

// Custom configuration
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    10,    // generations
    100,   // populationSize
    30,    // maxTimeMins
    5,     // cvFolds
    "f1",  // scoringMetric
    -1,    // nJobs
    "/opt/venv/bin/python3"  // pythonExecutable
);
```

## See Also

- `Tpot2Bridge` — Uses this config for training
- `Tpot2Result` — Training result
- `Tpot2TaskType` — Task type enum
