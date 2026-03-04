# Tutorial: Getting Started with TPOT2 AutoML

## Learning Objectives

By the end of this tutorial, you will:
1. Understand the TPOT2 integration architecture
2. Create and configure training datasets
3. Run your first AutoML pipeline optimization
4. Deploy the resulting ONNX model for inference

## Prerequisites

- Java 25 with preview features enabled
- Maven 3.9+
- Python 3.9+ with TPOT2 installed

## Step 1: Install Python Dependencies

```bash
pip install tpot2 skl2onnx numpy pandas scikit-learn
```

Verify installation:

```bash
python3 -c "import tpot2; print('TPOT2 version:', tpot2.__version__)"
```

## Step 2: Add Module Dependency

In your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-tpot2</artifactId>
</dependency>
```

## Step 3: Prepare Training Data

The `TrainingDataset` record wraps tabular data for AutoML:

```java
import org.yawlfoundation.yawl.tpot2.*;

// Define feature columns
List<String> features = List.of(
    "caseDurationMs",    // Case duration in milliseconds
    "taskCount",         // Number of tasks in case
    "distinctWorkItems", // Unique work item count
    "hadCancellations",  // Binary: 0 or 1
    "avgTaskWaitMs"      // Average wait time
);

// Create training rows (one per case)
List<double[]> rows = List.of(
    new double[]{1200.0, 3.0, 2.0, 0.0, 400.0},  // Case 1
    new double[]{8000.0, 7.0, 5.0, 1.0, 1100.0}, // Case 2
    new double[]{500.0, 2.0, 1.0, 0.0, 250.0},   // Case 3
    new double[]{15000.0, 10.0, 8.0, 1.0, 3000.0} // Case 4
);

// Labels: "completed" or "failed" for CASE_OUTCOME
List<String> labels = List.of("completed", "failed", "completed", "failed");

TrainingDataset dataset = new TrainingDataset(
    features, rows, labels, "order-processing-spec", 4
);
```

## Step 4: Configure AutoML

Use factory methods for sensible defaults:

```java
// For case outcome prediction (binary classification)
Tpot2Config config = Tpot2Config.forCaseOutcome();

// For remaining time prediction (regression)
Tpot2Config config = Tpot2Config.forRemainingTime();

// For next activity prediction (multiclass)
Tpot2Config config = Tpot2Config.forNextActivity();

// For anomaly detection (binary classification)
Tpot2Config config = Tpot2Config.forAnomalyDetection();
```

Or customize:

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME,  // Task type
    10,                           // Generations (1-100)
    50,                           // Population size (2-500)
    30,                           // Max time in minutes (1-1440)
    5,                            // CV folds (2-10)
    "roc_auc",                    // Scoring metric
    -1,                           // nJobs (-1 = all CPUs)
    "python3"                     // Python executable
);
```

## Step 5: Run Optimization

```java
try (Tpot2Bridge bridge = new Tpot2Bridge()) {
    Tpot2Result result = bridge.fit(dataset, config);

    System.out.println("Best score: " + result.bestScore());
    System.out.println("Pipeline: " + result.pipelineDescription());
    System.out.println("Training time: " + result.trainingTimeMs() + "ms");

    // ONNX model bytes for deployment
    byte[] onnxModel = result.onnxModelBytes();
    Files.write(Path.of("model.onnx"), onnxModel);
}
```

## Step 6: Understanding the Output

| Field | Description |
|-------|-------------|
| `bestScore` | Cross-validated score (metric depends on task type) |
| `pipelineDescription` | Human-readable sklearn pipeline |
| `onnxModelBytes` | Serialized ONNX model for inference |
| `trainingTimeMs` | Wall-clock training time |

## What You Learned

- How to structure training data for AutoML
- Configuration options for different task types
- Running TPOT2 optimization from Java
- Accessing results for deployment

## Next Steps

- **[How-To: Production Deployment](../how-to/production-deployment.md)**
- **[Reference: API Documentation](../reference/api.md)**
- **[Explanation: Architecture](../explanation/architecture.md)**
