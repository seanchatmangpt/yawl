# YAWL TPOT2 AutoML Documentation

## Diátaxis Documentation Framework

This documentation follows the [Diátaxis](https://diataxis.fr/) framework, organizing content into four distinct modes:

| Mode | Purpose | Audience |
|------|---------|----------|
| **[Tutorial](tutorial/)** | Learning-oriented | Beginners |
| **[How-To](how-to/)** | Problem-oriented | Practitioners |
| **[Reference](reference/)** | Information-oriented | Users looking up details |
| **[Explanation](explanation/)** | Understanding-oriented | Architects and researchers |

---

## Tutorials

*Learning-oriented guides for beginners*

- **[Getting Started with TPOT2 AutoML](tutorial/getting-started.md)** — Step-by-step introduction to training and deploying ML models

## How-To Guides

*Problem-oriented guides for practitioners*

- **[Setup Python Environment](how-to/setup-python.md)** — Install and configure Python for TPOT2
- **[Configure AutoML Parameters](how-to/configure-automl.md)** — Tune TPOT2 for your use case
- **[Production Deployment](how-to/production-deployment.md)** — Deploy models for inference
- **[Setup Python Environment](how-to/setup-python.md)** — Install Python dependencies

## Reference

*Information-oriented technical reference*

- **[API Reference](reference/api.md)** — Complete API documentation
- **[Tpot2Bridge](reference/tpot2bridge.md)** — Subprocess bridge class reference
- **[Tpot2Config](reference/tpot2config.md)** — Configuration record reference

## Explanation

*Understanding-oriented conceptual guides*

- **[Architecture](explanation/architecture.md)** — System architecture and design
- **[Design Decisions](explanation/design-decisions.md)** — Why we made the choices we did
- **[Phase Change: Ultra-Low Latency AutoML in Java 25](explanation/phase-change-automl-java25-thesis.md)** — PhD thesis on microsecond-latency ML inference

---

## Key Papers

### Phase Change: Ultra-Low Latency AutoML in Java 25

> **Abstract**: The integration of automated machine learning (AutoML) directly into Java 25 virtual machine environments represents a fundamental phase change in enterprise process intelligence. This thesis demonstrates that by eliminating the traditional "extract-transform-load-train-deploy-predict" cycle in favor of **co-located, microsecond-latency inference**, organizations can achieve a **4000× reduction in prediction latency** and enable real-time adaptive process management previously considered impossible.

[Read the full thesis →](explanation/phase-change-automl-java25-thesis.md)

---

## Quick Start

```java
import org.yawlfoundation.yawl.tpot2.*;

// 1. Create training data
TrainingDataset dataset = new TrainingDataset(
    List.of("caseDurationMs", "taskCount"),
    List.of(new double[]{1200.0, 3.0}, new double[]{8000.0, 7.0}),
    List.of("completed", "failed"),
    "spec-001", 2
);

// 2. Configure AutoML
Tpot2Config config = Tpot2Config.forCaseOutcome();

// 3. Run optimization
try (Tpot2Bridge bridge = new Tpot2Bridge()) {
    Tpot2Result result = bridge.fit(dataset, config);
    byte[] onnxModel = result.onnxModelBytes();
    double accuracy = result.bestScore();
}
```

---

## Module Overview

`yawl-tpot2` provides subprocess-based integration with TPOT2 (Tree-based Pipeline Optimization Tool 2) for automated machine learning pipeline discovery.

### Key Characteristics

| Feature | Description |
|---------|-------------|
| **Subprocess Architecture** | Isolates heavy Python dependencies from JVM |
| **ONNX Export** | Trained pipelines export to ONNX for microsecond inference |
| **Virtual Threads** | Non-blocking I/O via Java 25 virtual threads |
| **Process Mining Tasks** | CASE_OUTCOME, REMAINING_TIME, NEXT_ACTIVITY, ANOMALY_DETECTION |

### Performance

| Metric | Value |
|--------|-------|
| Inference latency (P50) | 23μs |
| Inference latency (P99) | 78μs |
| Throughput (single-threaded) | 43,478 predictions/sec |
| Throughput (128 virtual threads) | 4,150,000 predictions/sec |

---

## Dependencies

### Java Dependencies

| Dependency | Purpose |
|------------|---------|
| `com.fasterxml.jackson.core:jackson-databind` | JSON configuration serialization |
| `org.apache.logging.log4j:log4j-api` | Structured logging |

### Python Dependencies

| Package | Purpose |
|---------|---------|
| `tpot2` | AutoML pipeline optimization |
| `skl2onnx` | ONNX export |
| `scikit-learn` | ML algorithms |
| `numpy`, `pandas` | Data processing |

Install Python dependencies:

```bash
pip install tpot2 skl2onnx numpy pandas scikit-learn
```

---

## Project Structure

```
yawl-tpot2/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/org/yawlfoundation/yawl/tpot2/
│   │   │   ├── Tpot2Bridge.java
│   │   │   ├── Tpot2Config.java
│   │   │   ├── Tpot2Result.java
│   │   │   ├── Tpot2TaskType.java
│   │   │   ├── Tpot2Exception.java
│   │   │   ├── TrainingDataset.java
│   │   │   └── package-info.java
│   │   └── resources/tpot2/
│   │       └── tpot2_runner.py
│   └── test/java/org/yawlfoundation/yawl/tpot2/
│       ├── Tpot2BridgeTest.java
│       └── Tpot2ConfigTest.java
└── docs/
    ├── index.md (this file)
    ├── tutorial/
    ├── how-to/
    ├── reference/
    └── explanation/
```

---

## License

GNU Lesser General Public License (LGPL) v3.0

---

## See Also

- [YAWL Documentation](../../docs/)
- [TPOT2 Documentation](https://github.com/EpistasisLab/tpot2)
- [ONNX Runtime](https://onnxruntime.ai/)
