# How-To: Configure AutoML Parameters

## Problem

You need to tune TPOT2 parameters for your specific use case.

## Solution

### Quick Start: Factory Methods

Use task-specific factory methods for sensible defaults:

```java
// Case outcome (binary classification)
Tpot2Config config = Tpot2Config.forCaseOutcome();
// → scoringMetric="roc_auc"

// Remaining time (regression)
Tpot2Config config = Tpot2Config.forRemainingTime();
// → scoringMetric="neg_mean_absolute_error"

// Next activity (multiclass)
Tpot2Config config = Tpot2Config.forNextActivity();
// → scoringMetric="f1_macro"

// Anomaly detection (binary)
Tpot2Config config = Tpot2Config.forAnomalyDetection();
// → scoringMetric="roc_auc"
```

### Custom Configuration

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,  // taskType
    10,                           // generations (1-100)
    100,                          // populationSize (2-500)
    30,                           // maxTimeMins (1-1440)
    5,                            // cvFolds (2-10)
    "f1",                         // scoringMetric (nullable)
    -1,                           // nJobs (-1 = all CPUs)
    "python3"                     // pythonExecutable
);
```

### Parameter Guidelines

| Parameter | Low Value | High Value | Trade-off |
|-----------|-----------|------------|-----------|
| `generations` | 1-5 (fast) | 50-100 (thorough) | Time vs. pipeline quality |
| `populationSize` | 20-50 (fast) | 200-500 (diverse) | Memory vs. search space |
| `maxTimeMins` | 5-15 (quick) | 120-480 (comprehensive) | Deadline vs. optimization |
| `cvFolds` | 2-3 (fast) | 10 (robust) | Speed vs. variance reduction |

### Common Patterns

#### Quick Iteration (Development)

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    2,    // Few generations
    20,   // Small population
    5,    // 5 minutes max
    3,    // 3-fold CV
    null, // Default scoring
    -1, "python3"
);
```

#### Production Quality

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,
    20,    // More generations
    100,   // Larger population
    120,   // 2 hours max
    5,     // 5-fold CV
    "roc_auc",
    -1, "python3"
);
```

#### Regression Task

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.REMAINING_TIME,
    10, 50, 60, 5,
    "neg_mean_squared_error",  // Regression metric
    -1, "python3"
);
```

### Scoring Metrics Reference

| Task Type | Recommended Metrics |
|-----------|---------------------|
| CASE_OUTCOME | `roc_auc`, `f1`, `accuracy`, `precision`, `recall` |
| REMAINING_TIME | `neg_mean_absolute_error`, `neg_mean_squared_error`, `r2` |
| NEXT_ACTIVITY | `f1_macro`, `f1_weighted`, `accuracy` |
| ANOMALY_DETECTION | `roc_auc`, `f1`, `precision`, `recall` |

## Validation Errors

### "generations must be 1-100"

```java
// Wrong
new Tpot2Config(..., 0, ...);   // Too low
new Tpot2Config(..., 101, ...); // Too high

// Correct
new Tpot2Config(..., 1, ...);   // Minimum
new Tpot2Config(..., 100, ...); // Maximum
```

### "maxTimeMins must be 1-1440"

Maximum is 1440 minutes (24 hours). For longer runs, use multiple training sessions with warm-start (advanced).
