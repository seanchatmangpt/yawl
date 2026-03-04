# Reference: Tpot2Bridge

## Overview

Subprocess bridge for TPOT2 AutoML training. Extracts Python runner, serializes training data, launches TPOT2 subprocess, and returns ONNX model.

## Package

```java
package org.yawlfoundation.yawl.tpot2;
```

## Declaration

```java
public final class Tpot2Bridge implements AutoCloseable
```

## Constructors

### Tpot2Bridge()

```java
public Tpot2Bridge() throws Tpot2Exception
```

Creates bridge, extracts `tpot2_runner.py` from classpath to temp directory.

**Throws**:
- `Tpot2Exception` — If temp directory creation fails or runner script not found on classpath

### Tpot2Bridge(Path, Path)

```java
Tpot2Bridge(Path bridgeTempDir, Path runnerScript)
```

Package-private constructor for testing. Injects pre-created temp directory and runner script.

**Note**: Caller owns the directory; `close()` does not delete it.

## Methods

### fit(TrainingDataset, Tpot2Config)

```java
public Tpot2Result fit(TrainingDataset dataset, Tpot2Config config)
    throws Tpot2Exception
```

Runs TPOT2 AutoML on the training dataset and returns the best pipeline as ONNX.

**Parameters**:
- `dataset` — Training data with feature vectors and labels (required)
- `config` — TPOT2 run configuration (required)

**Returns**: `Tpot2Result` containing the best pipeline's ONNX bytes, score, and description

**Throws**:
- `Tpot2Exception` — If Python unavailable, tpot2/skl2onnx not installed, subprocess fails, or ONNX output missing
- `NullPointerException` — If dataset or config is null

**Thread Safety**: Each `fit()` call is independent; bridge may be used concurrently.

### close()

```java
@Override
public void close()
```

Removes temp directory created at construction. Safe to call multiple times.

**Note**: Only deletes if this instance owns the directory (created via default constructor).

## Lifecycle

```
┌─────────────────┐
│ new Tpot2Bridge │
└────────┬────────┘
         │ Extract tpot2_runner.py to /tmp/yawl-tpot2-XXX/
         ▼
┌─────────────────┐
│  fit(dataset,  │
│      config)   │◄───── Can call multiple times
└────────┬────────┘
         │ Creates per-run temp dir
         │ Runs Python subprocess
         │ Returns Tpot2Result
         │ Cleans up per-run temp dir
         ▼
┌─────────────────┐
│     close()     │
└────────┬────────┘
         │ Deletes /tmp/yawl-tpot2-XXX/
         ▼
┌─────────────────┐
│    (destroyed)  │
└─────────────────┘
```

## Usage Pattern

### Try-With-Resources (Recommended)

```java
try (Tpot2Bridge bridge = new Tpot2Bridge()) {
    Tpot2Result result = bridge.fit(dataset, config);
    // Use result...
}  // Temp directory automatically cleaned up
```

### Manual Lifecycle

```java
Tpot2Bridge bridge = new Tpot2Bridge();
try {
    Tpot2Result result = bridge.fit(dataset, config);
} finally {
    bridge.close();
}
```

## Error Handling

### Tpot2Exception Operations

| Operation | Cause |
|-----------|-------|
| `"automl"` | Training failure, Python issues, ONNX export failure |
| (constructor) | Temp directory creation failure |

### Error Detection Patterns

```java
try {
    Tpot2Result result = bridge.fit(dataset, config);
} catch (Tpot2Exception e) {
    String op = e.getOperation();

    if (e.getMessage().contains("Python executable not found")) {
        // Python not on PATH
    } else if (e.getMessage().contains("tpot2 not installed")) {
        // Run: pip install tpot2 skl2onnx
    } else if (e.getMessage().contains("ONNX output")) {
        // ONNX export failed
    } else if (e.getMessage().contains("timed out")) {
        // Increase maxTimeMins in config
    }
}
```

## See Also

- `Tpot2Config` — Configuration record
- `Tpot2Result` — Training result
- `TrainingDataset` — Training data wrapper
