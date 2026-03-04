# How-To: Production Deployment

## Problem

You have trained an AutoML model and need to deploy it for real-time inference in production.

## Solution

Use the ONNX runtime for microsecond-latency predictions directly in the JVM.

## Prerequisites

- Trained ONNX model (from `Tpot2Result.onnxModelBytes()`)
- `onnxruntime` dependency

## Step 1: Add ONNX Runtime Dependency

```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.19.2</version>
</dependency>
```

## Step 2: Create Inference Engine

```java
import ai.onnxruntime.*;

public class CaseOutcomePredictor implements AutoCloseable {
    private final OrtEnvironment env;
    private final OrtSession session;

    public CaseOutcomePredictor(Path onnxModelPath) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.session = env.createSession(onnxModelPath.toString());
    }

    public Prediction predict(float[] features) throws OrtException {
        // Create input tensor
        long[] shape = {1, features.length};
        OnnxTensor input = OnnxTensor.createTensor(env, new float[][]{features});

        // Run inference
        OrtSession.Result result = session.run(Map.of("float_input", input));

        // Extract output
        float[][] output = (float[][]) result.get(0).getValue();
        float probability = output[0][0];

        return new Prediction(probability, probability > 0.5f ? "failed" : "completed");
    }

    @Override
    public void close() throws OrtException {
        session.close();
        env.close();
    }

    public record Prediction(float probability, String label) {}
}
```

## Step 3: Use in Production

```java
// Initialize once at application startup
CaseOutcomePredictor predictor = new CaseOutcomePredictor(Path.of("/models/case_outcome.onnx"));

// Use for each prediction (microsecond latency)
float[] features = extractFeaturesFromCase(activeCase);
Prediction pred = predictor.predict(features);

if (pred.label().equals("failed") && pred.probability() > 0.8f) {
    triggerEscalation(activeCase);
}
```

## Step 4: Model Registry Pattern

```java
public final class PredictiveModelRegistry {
    private final Path modelDir;
    private final Map<String, CaseOutcomePredictor> models = new ConcurrentHashMap<>();

    public PredictiveModelRegistry(Path modelDir) {
        this.modelDir = modelDir;
    }

    public void register(String taskName, Path onnxPath) throws OrtException {
        models.put(taskName, new CaseOutcomePredictor(onnxPath));
    }

    public boolean isAvailable(String taskName) {
        return models.containsKey(taskName);
    }

    public float[] infer(String taskName, float[] features) throws PIException {
        CaseOutcomePredictor predictor = models.get(taskName);
        if (predictor == null) {
            throw new PIException("Model not loaded: " + taskName, "predictive");
        }
        try {
            Prediction pred = predictor.predict(features);
            return new float[]{pred.probability()};
        } catch (OrtException e) {
            throw new PIException("Inference failed", "predictive", e);
        }
    }
}
```

## Troubleshooting

### Python Not Found

```
Tpot2Exception: Python executable not found: 'python3'
```

**Solution**: Ensure Python 3.9+ is on PATH, or set `pythonExecutable` in config:

```java
Tpot2Config config = new Tpot2Config(
    Tpot2TaskType.CASE_OUTCOME, 5, 50, 60, 5, "roc_auc", -1, "/usr/bin/python3.11"
);
```

### tpot2 Not Installed

```
Tpot2Exception: tpot2 Python library is not installed
```

**Solution**: Install Python dependencies:

```bash
pip install tpot2 skl2onnx
```

### ONNX Export Failed

```
Tpot2Exception: ONNX export failed
```

**Solution**: Ensure `skl2onnx` is installed and compatible with your sklearn version:

```bash
pip install --upgrade skl2onnx scikit-learn
```

## Related

- **[How-To: Retrain Models](retrain-models.md)**
- **[Reference: Configuration Options](../reference/configuration.md)**
