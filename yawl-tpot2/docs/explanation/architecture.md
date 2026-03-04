# Architecture: Why Subprocess, Not GraalPy

## Design Decision

TPOT2 integration uses **subprocess execution** via `ProcessBuilder`, not embedded Python via GraalPy. This is intentional.

## Constraints

### TPOT2 Dependency Chain

```
tpot2 → sklearn → numpy → BLAS/LAPACK → native libraries
       ↓
    deap (genetic algorithms)
       ↓
    sklearn pipeline optimization
       ↓
    skl2onnx → ONNX export
```

This chain requires:
- 500+ MB of Python packages
- Native BLAS/LAPACK libraries
- Full NumPy C extensions
- Scikit-learn compiled extensions

### GraalPy Limitations

| Limitation | Impact |
|------------|--------|
| Sandboxed execution | Cannot load native BLAS |
| Memory isolation | 500MB+ overhead per context |
| Compatibility layer | Not all sklearn extensions work |
| Version constraints | GraalPy lags CPython releases |

### Subprocess Advantages

| Advantage | Value |
|-----------|-------|
| Full CPython compatibility | All sklearn/tpot2 features work |
| Process isolation | Crash doesn't affect JVM |
| Memory isolation | Python GC independent of Java GC |
| Any JDK | Works on Temurin, GraalVM, OpenJ9 |
| Native BLAS | Full NumPy performance |

## Communication Protocol

```
┌─────────────┐                      ┌─────────────────┐
│   JVM       │                      │    Python       │
│ Tpot2Bridge │                      │ tpot2_runner.py │
└──────┬──────┘                      └────────┬────────┘
       │                                      │
       │  1. Write training_data.csv          │
       │─────────────────────────────────────►│
       │                                      │
       │  2. Write tpot2_config.json          │
       │─────────────────────────────────────►│
       │                                      │
       │  3. ProcessBuilder.start()           │
       │─────────────────────────────────────►│
       │                                      │
       │                           4. Load CSV│
       │                           5. TPOT.fit│
       │                           6. ONNX export
       │                                      │
       │  7. JSON metrics on stdout           │
       │◄─────────────────────────────────────│
       │                                      │
       │  8. Read best_pipeline.onnx          │
       │◄─────────────────────────────────────│
       │                                      │
```

## Virtual Thread Integration

Java 25 virtual threads prevent subprocess I/O from blocking carrier threads:

```java
Thread stdoutThread = Thread.ofVirtual().name("tpot2-stdout").start(() ->
    captureStream(process.getInputStream(), stdout, false));

Thread stderrThread = Thread.ofVirtual().name("tpot2-stderr").start(() ->
    captureStream(process.getErrorStream(), stderr, true));
```

Benefits:
- **Non-blocking**: TPOT2 can run for hours without blocking platform threads
- **Scalable**: Multiple concurrent training runs
- **Efficient**: ~1KB per virtual thread vs ~1MB per platform thread

## Resource Management

### Temp Directory Lifecycle

```
Tpot2Bridge()
    │
    └──► bridgeTempDir = /tmp/yawl-tpot2-XXX/
             │
             ├── tpot2_runner.py (extracted from JAR)
             │
             └── run-YYY/  (created per fit() call)
                  ├── training_data.csv
                  ├── tpot2_config.json
                  └── best_pipeline.onnx
```

### Cleanup Guarantees

1. **Per-run cleanup**: `run-YYY/` deleted after each `fit()` call
2. **Bridge cleanup**: `bridgeTempDir` deleted on `close()`
3. **Best-effort**: Cleanup failures logged but don't throw

## Error Handling Strategy

| Error Type | Detection | Exception |
|------------|-----------|-----------|
| Python not found | `IOException: error=2` | `Tpot2Exception("automl")` |
| tpot2 not installed | stderr contains "No module" | `Tpot2Exception("automl")` |
| Training failure | exit code 2 | `Tpot2Exception("automl")` |
| ONNX export failure | missing output file | `Tpot2Exception("automl")` |
| Timeout | `waitFor()` returns false | `Tpot2Exception("automl")` |

## Alternatives Considered

### 1. GraalPy Embedded

**Rejected**: Native library incompatibility with sklearn.

### 2. Jython

**Rejected**: Python 2.7 only, no sklearn support.

### 3. REST API to Python Service

**Rejected**:
- Additional deployment complexity
- Network latency for large datasets
- State management overhead

### 4. JNI to Native Library

**Rejected**:
- No native TPOT2 equivalent
- Would require full sklearn port

## Future Considerations

### ONNX Runtime Training

If ONNX Runtime adds training support, we could eliminate Python entirely:

```java
// Hypothetical future API
OnnxTrainingSession session = env.createTrainingSession(config);
OnnxTrainedModel model = session.train(dataset);
```

**Timeline**: ONNX Runtime training is experimental; monitor for GA.

### GraalPy Native Extensions

GraalPy is improving native extension support. Revisit when:
- NumPy/BLAS compatibility reaches 100%
- Memory overhead decreases
- Version lag reduces to <6 months
