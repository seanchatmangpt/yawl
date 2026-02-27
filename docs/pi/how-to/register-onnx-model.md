# How to Register an ONNX Model

This guide covers loading a pre-trained `.onnx` model into `PredictiveModelRegistry`
and using it for inference.

## Prerequisites

- A trained `.onnx` model file with 5-element float input and 2-element probability output
- `PredictiveModelRegistry` already constructed (see
  [configure the PI facade](configure-pi-facade.md))

---

## Auto-loading from directory

`PredictiveModelRegistry` scans the model directory at construction time and loads
every `.onnx` file it finds:

```java
Path modelDir = Path.of("/opt/yawl/models");
// Directory contents: case_outcome.onnx, anomaly_detection.onnx

PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir);
// Now loaded:
// registry.isAvailable("case_outcome")    → true
// registry.isAvailable("anomaly_detection") → true
```

The task name is the filename without the `.onnx` extension.

---

## Explicit registration

Register (or re-register) a model at runtime:

```java
registry.register("case_outcome", Path.of("/opt/models/loan_v2_case_outcome.onnx"));
```

If a model is already registered under `"case_outcome"`, it is **replaced**.
The old ONNX session is closed before the new one is loaded.

**Throws** `PIException("predictive")` if:
- The file does not exist
- The file is not a valid ONNX model
- ONNX Runtime fails to create a session

---

## Checking availability

```java
if (registry.isAvailable("case_outcome")) {
    // Use ONNX model
} else {
    // Fall back to DNA oracle
}
```

`CaseOutcomePredictor` performs this check automatically on every `predict()` call.
You only need to check manually when building custom inference paths.

---

## Running inference directly

```java
// Build 5-element feature vector [caseDurationMs, taskCount, distinctWorkItems,
//                                  hadCancellations, avgTaskWaitMs]
float[] features = { 15000.0f, 4.0f, 3.0f, 0.0f, 500.0f };

float[] probabilities = registry.infer("case_outcome", features);
float completionProb = probabilities[1];   // P(completed)
float riskScore      = 1.0f - completionProb;

System.out.printf("Completion: %.1f%%  Risk: %.1f%%%n",
    completionProb * 100, riskScore * 100);
```

`infer()` throws `PIException("predictive")` if:
- The model is not registered (`isAvailable()` returns false)
- The feature vector length does not match the model's expected input
- ONNX Runtime throws during inference

---

## Releasing resources

`PredictiveModelRegistry` implements `AutoCloseable`. Release ONNX sessions
when the application shuts down:

```java
try (PredictiveModelRegistry registry = new PredictiveModelRegistry(modelDir)) {
    // use registry
}
// All OrtSession objects and the shared OrtEnvironment are closed
```

In long-running servers, call `registry.close()` in a shutdown hook:

```java
Runtime.getRuntime().addShutdownHook(new Thread(registry::close));
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `PIException: model not found: case_outcome` | File missing or wrong name | Check `modelDir` contains `case_outcome.onnx` |
| `PIException: input size mismatch` | Model expects different feature count | Retrain with 5-element feature vector |
| `PIException: Failed to create OrtSession` | Corrupted or wrong-platform ONNX | Regenerate with `skl2onnx` on same platform |
| `isAvailable("case_outcome")` is false after registration | Exception during load was swallowed | Wrap `register()` in try-catch to see the cause |
