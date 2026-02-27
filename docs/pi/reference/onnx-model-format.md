# ONNX Model Format Reference

This reference covers how `PredictiveModelRegistry` loads and runs ONNX models,
the feature vector format expected by the runtime, naming conventions, and
how to verify a model is compatible.

---

## PredictiveModelRegistry

`org.yawlfoundation.yawl.pi.predictive.PredictiveModelRegistry`

Thread-safe in-memory registry of ONNX Runtime sessions.
Auto-loads `.onnx` files found in the model directory at construction time.

```java
// Construction — loads all .onnx files in the directory
PredictiveModelRegistry registry = new PredictiveModelRegistry(Path.of("models/"));

// Manual registration
registry.register("case_outcome", Path.of("models/loan_case_outcome.onnx"));

// Check availability
boolean ready = registry.isAvailable("case_outcome");   // true if loaded successfully

// Run inference
float[] features = { 12000.0f, 3.0f, 2.0f, 0.0f, 300.0f };
float[] output = registry.infer("case_outcome", features);
```

### Methods

| Method | Returns | Description |
|---|---|---|
| `register(taskName, modelPath)` | void | Load ONNX file and register under `taskName`. Throws `PIException("predictive")` if file not found or parse fails. Overwrites existing registration for same task name. |
| `isAvailable(taskName)` | boolean | Returns true if a model is registered and loaded for `taskName` |
| `infer(taskName, features)` | `float[]` | Run inference. Throws `PIException("predictive")` if model not available or inference fails |
| `close()` | void | Releases all ONNX Runtime sessions and the shared `OrtEnvironment` |

---

## Standard task names

These names are used by `CaseOutcomePredictor` and `ProcessMiningAutoMl`.
Use them exactly (case-sensitive):

| Name | Task type | Used by |
|---|---|---|
| `"case_outcome"` | `CASE_OUTCOME` | `CaseOutcomePredictor` |
| `"remaining_time"` | `REMAINING_TIME` | (available for custom integration) |
| `"next_activity"` | `NEXT_ACTIVITY` | (available for custom integration) |
| `"anomaly_detection"` | `ANOMALY_DETECTION` | `PredictiveProcessObserver` |

Custom task names are also valid for application-specific models.

---

## Input feature vector

`CaseOutcomePredictor` assembles a `float[5]` from case events:

| Index | Feature name | Unit | Source |
|---|---|---|---|
| 0 | `caseDurationMs` | ms (float) | `lastEvent.timestamp - firstEvent.timestamp` |
| 1 | `taskCount` | count (float) | number of `WORKITEM_STARTED` events |
| 2 | `distinctWorkItems` | count (float) | `distinct workItemId` values |
| 3 | `hadCancellations` | 0.0 or 1.0 | any `CASE_CANCELLED` event present |
| 4 | `avgTaskWaitMs` | ms (float) | mean time from `WORKITEM_ENABLED` to `WORKITEM_STARTED` |

The vector must have exactly 5 elements. Models trained with fewer features
(e.g., if your cases never use all event types) should be trained with the same
5-element vector, with 0.0 for unavailable features.

### Building the feature vector manually

```java
float[] features = {
    (float) caseDurationMs,          // elapsed ms
    (float) taskCount,               // work items started
    (float) distinctWorkItems,       // unique item IDs
    hasCancellations ? 1.0f : 0.0f,  // cancellation indicator
    (float) avgTaskWaitMs            // mean queue wait
};
```

---

## Output format

### Classification models (CASE_OUTCOME, NEXT_ACTIVITY, ANOMALY_DETECTION)

The model must output a probability array. `CaseOutcomePredictor` expects:

```
output[0] = P(failed / anomaly / non-primary class)
output[1] = P(completed / normal / primary class)
```

`completionProbability = output[1]`
`riskScore = 1.0 - output[1]`

If your model outputs only a single logit, wrap it in a sigmoid activation
before export with `skl2onnx`.

### Regression models (REMAINING_TIME)

```
output[0] = predicted remaining milliseconds
```

---

## Model file naming

Auto-discovery at registry construction: files matching `<taskName>.onnx`
in the model directory are loaded with `taskName` derived from the filename
(without `.onnx` extension).

Examples:
- `models/case_outcome.onnx` → registered as `"case_outcome"`
- `models/loan_case_outcome.onnx` → registered as `"loan_case_outcome"`

Use `register("case_outcome", path)` to explicitly override the name.

---

## Model file produced by ProcessMiningAutoMl

`ProcessMiningAutoMl.autoTrainCaseOutcome()` writes the trained ONNX model to:

```
<modelDirectory>/<specId>_case_outcome.onnx
```

Where `specId` is the specification identifier with `/` replaced by `_`.

Example: spec `"loan-application/1.0"` → `models/loan-application_1.0_case_outcome.onnx`

---

## Verifying a compatible model

To check that a model file is compatible before registration:

```bash
# Install onnxruntime
pip install onnxruntime

# Check input/output shape
python3 -c "
import onnxruntime as rt
s = rt.InferenceSession('models/case_outcome.onnx')
for i in s.get_inputs():
    print('Input:', i.name, i.shape, i.type)
for o in s.get_outputs():
    print('Output:', o.name, o.shape, o.type)
"
```

Expected output for a compatible case outcome model:
```
Input:  float_input [None, 5] tensor(float)
Output: probabilities [None, 2] tensor(float)
```
