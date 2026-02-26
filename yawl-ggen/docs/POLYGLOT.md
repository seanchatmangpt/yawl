# YAWL ggen v6.0.0-GA Polyglot Integration

**Status**: GA-Ready | **GraalPy** | **February 2026**

---

## Table of Contents

1. [Overview](#1-overview)
2. [PowlPythonBridge](#2-powlpythonbridge)
3. [PythonExecutionEngine](#3-pythonexecutionengine)
4. [POWL Generator Python Module](#4-powl-generator-python-module)
5. [Usage Examples](#5-usage-examples)
6. [Error Handling](#6-error-handling)
7. [Performance](#7-performance)

---

## 1. Overview

YAWL ggen supports polyglot execution via GraalPy, enabling Python library integration for process mining and POWL generation. This allows use of the `pm4py` and `powl` Python libraries from Java.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Application                          │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              PowlPythonBridge                         │   │
│  │  ┌────────────────────────────────────────────────┐   │   │
│  │  │ • generatePowlJson(description) → JSON string  │   │   │
│  │  │ • mineFromXes(xesContent) → JSON string        │   │   │
│  │  │ • generate(description) → PowlModel            │   │   │
│  │  │ • mineFromLog(xesContent) → PowlModel          │   │   │
│  │  └────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           PythonExecutionEngine (yawl-graalpy)        │   │
│  │  ┌────────────────────────────────────────────────┐   │   │
│  │  │ • Context pool (4 GraalPy contexts)            │   │   │
│  │  │ • Sandboxed execution                          │   │   │
│  │  │ • evalToString() for string results            │   │   │
│  │  │ • evalToObject() for polyglot objects          │   │   │
│  │  └────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                          │                                   │
│                          ▼                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              powl_generator.py                        │   │
│  │  ┌────────────────────────────────────────────────┐   │   │
│  │  │ • generate_powl_json(description) → str        │   │   │
│  │  │ • mine_from_xes(xes_content) → str             │   │   │
│  │  │ • Uses: pm4py, powl library                    │   │   │
│  │  └────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Requirements

| Requirement | Version | Notes |
|-------------|---------|-------|
| GraalVM JDK | 24.1+ | Required for polyglot |
| Python | 3.10+ | GraalPy compatible |
| pm4py | 2.7+ | Process mining library |
| powl | 1.0+ | POWL model library |

### Fallback Behavior

On standard JDK (non-GraalVM), all polyglot methods throw `PythonException` with `RUNTIME_NOT_AVAILABLE`. Applications should catch this and fall back to `OllamaCandidateSampler`.

---

## 2. PowlPythonBridge

### Interface

```java
public class PowlPythonBridge implements PowlGenerator, AutoCloseable {

    /**
     * Constructs a PowlPythonBridge with a sandboxed PythonExecutionEngine
     * with a pool of 4 GraalPy contexts. Thread-safe; reuse across calls.
     */
    public PowlPythonBridge() {
        this(PythonExecutionEngine.builder()
                .sandboxed(true)
                .contextPoolSize(4)
                .build());
    }

    // ─── Raw JSON layer (PowlGenerator interface) ─────────────

    /**
     * Returns raw JSON string from Python generate_powl_json() function.
     * @param description natural language process description
     * @return JSON string conforming to POWL wire format
     */
    @Override
    public String generatePowlJson(String description);

    /**
     * Returns raw JSON string from Python mine_from_xes() function.
     * @param xesContent XES event log XML content
     * @return JSON string conforming to POWL wire format
     */
    @Override
    public String mineFromXes(String xesContent);

    // ─── Parsed layer ─────────────────────────────────────────

    /**
     * Generates a POWL model from natural language description.
     * @return PowlModel parsed from JSON
     */
    public PowlModel generate(String processDescription)
        throws PowlParseException;

    /**
     * Mines a POWL model from an XES event log.
     * @return PowlModel discovered from the log
     */
    public PowlModel mineFromLog(String xesContent)
        throws PowlParseException;

    @Override
    public void close() {
        engine.close();
    }
}
```

### Two-Layer API

| Layer | Method | Return Type | Use Case |
|-------|--------|-------------|----------|
| **Raw** | `generatePowlJson()` | `String` (JSON) | Forwarding to other services |
| **Raw** | `mineFromXes()` | `String` (JSON) | Testing, debugging |
| **Parsed** | `generate()` | `PowlModel` | Direct use in Java |
| **Parsed** | `mineFromLog()` | `PowlModel` | Process mining workflows |

---

## 3. PythonExecutionEngine

The `PythonExecutionEngine` from `yawl-graalpy` provides the Java-Python integration layer.

### Configuration

```java
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .sandboxed(true)           // Enable sandbox for security
    .contextPoolSize(4)        // Pool of 4 GraalPy contexts
    .allowFileSystem(false)    // Disable file system access
    .allowNetwork(false)       // Disable network access
    .maxMemoryMB(256)          // Memory limit per context
    .timeoutMs(30000)          // Execution timeout
    .build();
```

### Thread Safety

- **Context Pool**: Multiple GraalPy contexts for concurrent access
- **Thread-Safe**: Multiple threads can call `evalToString()` simultaneously
- **Reuse**: Engine should be reused across calls (expensive to create)

### Sandbox Mode

When sandboxed is enabled:

| Restriction | Enabled |
|-------------|---------|
| File system access | Blocked |
| Network access | Blocked |
| Native JNI calls | Blocked |
| System properties | Read-only |
| Environment variables | Read-only |

---

## 4. POWL Generator Python Module

### powl_generator.py

Located at `src/main/resources/polyglot/powl_generator.py`:

```python
import json
from pm4py.objects.log.importer.xes import importer as xes_importer
from powl import POWL

def generate_powl_json(description: str) -> str:
    """
    Generate a POWL model from a natural language description.

    Args:
        description: Natural language process description

    Returns:
        JSON string conforming to POWL wire format
    """
    # Use pm4py/powl to generate model
    # ... implementation ...

    model_dict = {
        "id": f"powl-{hash(description)}",
        "root": serialize_node(root),
        "generatedAt": datetime.now().isoformat()
    }
    return json.dumps(model_dict)

def mine_from_xes(xes_content: str) -> str:
    """
    Mine a POWL model from an XES event log using inductive miner.

    Args:
        xes_content: XES event log XML content

    Returns:
        JSON string conforming to POWL wire format
    """
    # Parse XES content
    log = xes_importer.deserialize(xes_content)

    # Apply inductive miner
    # ... implementation ...

    return json.dumps(model_dict)

def serialize_node(node) -> dict:
    """Serialize a POWL node to JSON-compatible dict."""
    if isinstance(node, Activity):
        return {
            "type": "activity",
            "id": node.id,
            "label": node.label
        }
    elif isinstance(node, Operator):
        return {
            "type": "operator",
            "id": node.id,
            "operator": node.operator.name,
            "children": [serialize_node(c) for c in node.children]
        }
```

### POWL Wire Format

```json
{
  "id": "powl-abc123",
  "root": {
    "type": "operator",
    "id": "op-1",
    "operator": "SEQ",
    "children": [
      {
        "type": "activity",
        "id": "act-1",
        "label": "Submit Application"
      },
      {
        "type": "operator",
        "id": "op-2",
        "operator": "XOR",
        "children": [
          {"type": "activity", "id": "act-2", "label": "Approve"},
          {"type": "activity", "id": "act-3", "label": "Reject"}
        ]
      }
    ]
  },
  "generatedAt": "2026-02-26T14:30:00Z"
}
```

---

## 5. Usage Examples

### Basic Generation

```java
try (PowlPythonBridge bridge = new PowlPythonBridge()) {
    PowlModel model = bridge.generate("Order processing workflow");

    // Convert to YAWL
    YawlSpecification yawl = new PowlToYawlConverter().convert(model);

} catch (PythonException e) {
    if (e.getErrorKind() == PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE) {
        // Fall back to OllamaCandidateSampler
        return fallbackToOllama(description);
    }
    throw e;
}
```

### Process Mining from XES

```java
try (PowlPythonBridge bridge = new PowlPythonBridge()) {
    // Load XES event log
    String xesContent = Files.readString(Path.of("event_log.xes"));

    // Mine process model
    PowlModel discoveredModel = bridge.mineFromLog(xesContent);

    // Validate and convert
    ValidationReport report = new PowlValidator().validate(discoveredModel);
    if (report.valid()) {
        YawlSpecification yawl = new PowlToYawlConverter().convert(discoveredModel);
    }

} catch (PythonException | PowlParseException e) {
    // Handle error
}
```

### Integration with GRPO

```java
// Create polyglot sampler
PowlPythonBridge bridge = new PowlPythonBridge();

// Custom sampler that uses Python bridge
CandidateSampler sampler = (description, k) -> {
    List<PowlModel> candidates = new ArrayList<>();
    for (int i = 0; i < k; i++) {
        candidates.add(bridge.generate(description));
    }
    return candidates;
};

// Use in GRPO optimizer
GrpoOptimizer optimizer = new GrpoOptimizer(
    sampler,
    new CompositeRewardFunction(new FootprintScorer(), new LlmJudgeScorer(), 0.5, 0.5),
    RlConfig.defaults()
);

PowlModel best = optimizer.optimize("Invoice approval workflow");
```

### Raw JSON for API Forwarding

```java
// Get raw JSON to forward to another service
try (PowlPythonBridge bridge = new PowlPythonBridge()) {
    String json = bridge.generatePowlJson("Process description");

    // Forward to external service
    httpClient.send(HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/process"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build());
}
```

---

## 6. Error Handling

### PythonException Types

```java
public enum ErrorKind {
    RUNTIME_NOT_AVAILABLE,  // GraalVM not present
    RUNTIME_ERROR,          // Python runtime error
    TIMEOUT,                // Execution exceeded timeout
    MEMORY_LIMIT,           // Exceeded memory limit
    SANDBOX_VIOLATION,      // Attempted restricted operation
    SYNTAX_ERROR            // Python syntax error
}
```

### Exception Handling Pattern

```java
try {
    PowlModel model = bridge.generate(description);

} catch (PythonException e) {
    switch (e.getErrorKind()) {
        case RUNTIME_NOT_AVAILABLE:
            log.warn("GraalPy not available, falling back to Ollama");
            return fallbackToOllama(description);

        case TIMEOUT:
            log.error("Python execution timed out");
            throw new GenerationTimeoutException(e);

        case MEMORY_LIMIT:
            log.error("Python execution exceeded memory limit");
            throw new GenerationMemoryException(e);

        case SYNTAX_ERROR:
            log.error("Python syntax error: {}", e.getMessage());
            throw new GenerationSyntaxException(e);

        default:
            throw e;
    }
} catch (PowlParseException e) {
    log.error("Failed to parse POWL JSON: {}", e.getMessage());
    throw new ModelParseException(e);
}
```

### Graceful Degradation

```java
public class HybridGenerator implements PowlGenerator {
    private final PowlPythonBridge pythonBridge;
    private final OllamaCandidateSampler ollamaSampler;
    private boolean pythonAvailable = true;

    @Override
    public String generatePowlJson(String description) {
        if (pythonAvailable) {
            try {
                return pythonBridge.generatePowlJson(description);
            } catch (PythonException e) {
                if (e.getErrorKind() == ErrorKind.RUNTIME_NOT_AVAILABLE) {
                    pythonAvailable = false;
                    log.warn("GraalPy unavailable, switching to Ollama");
                } else {
                    throw e;
                }
            }
        }

        // Fallback to Ollama
        return ollamaSampler.sample(description, 1).get(0).toJson();
    }
}
```

---

## 7. Performance

### Latency Comparison

| Method | Latency | Notes |
|--------|---------|-------|
| `generatePowlJson()` | ~50-100ms | Python execution only |
| `generate()` | ~50-150ms | Includes JSON parsing |
| `mineFromXes()` | ~200-500ms | Depends on log size |

### Context Pool Benefits

| Configuration | Concurrent Requests | Memory |
|---------------|---------------------|--------|
| Pool size 1 | Sequential | ~100MB |
| Pool size 4 | 4 parallel | ~400MB |
| Pool size 8 | 8 parallel | ~800MB |

### Memory Footprint

| Component | Memory | Notes |
|-----------|--------|-------|
| GraalPy context | ~100MB | Per context |
| Python heap | ~50MB | Shared across contexts |
| POWL model (parsed) | ~2KB | Per model |

### Best Practices

1. **Reuse the bridge**: Create once, use many times
2. **Use try-with-resources**: Ensures proper cleanup
3. **Handle RUNTIME_NOT_AVAILABLE**: Always have fallback
4. **Size context pool appropriately**: Match concurrent load
5. **Enable sandbox**: Security in production

---

## Related Documentation

- [Architecture Overview](./ARCHITECTURE.md)
- [RL Engine Documentation](./RL_ENGINE.md)
- [API Reference](./API_REFERENCE.md)

---

*Last Updated: February 26, 2026*
*Version: YAWL ggen v6.0.0-GA*
