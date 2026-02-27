# YAWL GraalVM Polyglot Integration

## Overview

YAWL v6.0 introduces **GraalVM polyglot language execution**, enabling you to embed Python, JavaScript, and WebAssembly directly into your workflow logic without leaving Java. This document covers the three polyglot integration modules and helps you choose the right tool for your automation task.

### What is the Polyglot Stack?

The YAWL polyglot modules leverage **GraalVM's polyglot execution engine** to run non-Java code in-process, safely sandboxed, with minimal overhead. This enables:

- **Python data science**: Use NumPy, pandas, and scikit-learn libraries for case analytics
- **JavaScript business rules**: Implement workflow routing rules in a dynamic language
- **WebAssembly performance**: Run compiled Rust, C, or other WASM binaries for compute-intensive analytics
- **Process mining**: Analyze OCEL2 event logs using Rust-based algorithms via the Rust4pmBridge

All modules use **context pooling** (Apache Commons Pool2) to reuse interpreter instances, ensuring low latency in high-throughput workflow engines.

---

## Module Comparison Table

| Module | Language | Primary Package | Key Class | Primary Use Case | Runtime Requirement |
|--------|----------|-----------------|-----------|------------------|----------------------|
| **yawl-graalpy** | Python 3.11+ | `org.yawlfoundation.yawl.graalpy` | `PythonExecutionEngine` | Data analysis, process mining scripts, ML model evaluation | GraalVM JDK 24.1+ with Python support |
| **yawl-graaljs** | JavaScript (ES2022) | `org.yawlfoundation.yawl.graaljs` | `JavaScriptExecutionEngine` | Workflow routing rules, business logic, real-time decision making | GraalVM JDK 24.1+ with JavaScript support |
| **yawl-graalwasm** | WebAssembly (WASM/WASI) | `org.yawlfoundation.yawl.graalwasm` | `WasmExecutionEngine`, `Rust4pmBridge`, `DmnWasmBridge` | OCEL2 process mining, DMN decisions, high-performance analytics, custom WASM binaries | GraalVM JDK 24.1+ with WASM support |
| **yawl-dmn** | WASM + Java API | `org.yawlfoundation.yawl.dmn` | `DmnDecisionService` | DMN decision execution, schema validation, rule evaluation | GraalVM JDK 24.1+ with WASM support |
| **yawl-data-modelling** | WASM + JavaScript | `org.yawlfoundation.yawl.datamodelling` | `DataModellingBridge` | Schema operations, format conversion, decision records | GraalVM JDK 24.1+ with WASM support |

---

## When to Use Which Module

### Use yawl-graalpy if you need to:
- Integrate existing Python scripts without rewriting them in Java
- Use Python scientific libraries (NumPy, pandas, scikit-learn) for case analysis
- Implement machine learning predictions in workflow task handlers
- Process tabular data with Python's data manipulation libraries

### Use yawl-graaljs if you need to:
- Implement workflow business rules in a dynamic language
- Evaluate complex routing logic without recompiling Java
- Leverage JavaScript's functional programming capabilities
- Call JavaScript functions from Java for decision-making workflows

### Use yawl-graalwasm if you need to:
- Analyze OCEL2 process mining event logs using Rust4pmBridge
- Execute DMN decision models using DmnWasmBridge (bundled dmn_feel_engine.wasm)
- Run compiled WebAssembly binaries (from Rust, C, or C++) for performance-critical analytics
- Cache frequently-used WASM modules (binary cache is transparent)
- Execute untrusted code in a strict security sandbox

### Use yawl-dmn if you need to:
- Execute DMN 1.3 decision models with schema validation and COLLECT aggregation
- Validate input data against data models before decision evaluation
- Integrate business rules into workflow task handlers
- Aggregate results using SUM, MIN, MAX, or COUNT operations on multiple decision results

### Use yawl-data-modelling if you need to:
- Import/export schemas across 70+ formats (ODCS, SQL, BPMN, DMN, OpenAPI, etc.)
- Convert between data modeling formats (YAML ↔ JSON ↔ SQL ↔ OpenAPI)
- Create decision records and knowledge bases
- Validate schemas and manage domain organizations

---

## Decision Matrix

| Use Case | Recommended Module |
|----------|-------------------|
| Python data analysis, scikit-learn model evaluation | **yawl-graalpy** |
| JavaScript business rules engine, dynamic routing | **yawl-graaljs** |
| OCEL2 event log processing for process mining | **yawl-graalwasm** (Rust4pmBridge) |
| DMN 1.3 decision execution with schema validation | **yawl-dmn** |
| DMN evaluation with FEEL expression language | **yawl-dmn** (bundled dmn_feel_engine.wasm) |
| Data modeling format conversions (ODCS, SQL, BPMN, etc.) | **yawl-data-modelling** |
| Schema import/export across 70+ formats | **yawl-data-modelling** |
| Any custom WebAssembly binary (Rust, C, C++) | **yawl-graalwasm** (WasmExecutionEngine) |
| Bidirectional Java ↔ JS object marshalling | **yawl-graaljs** + JsTypeMarshaller |
| Python pip packages (numpy, pandas, requests) | **yawl-graalpy** + PythonVirtualEnvironment |
| High-throughput workflow decisions (1000s/sec) | **yawl-graaljs** (smallest overhead) |
| Isolated, safe execution of untrusted scripts | Any module with `SandboxConfig.strict()` |

---

## Architecture Highlights

### Context Pooling & Thread Safety

All three modules use **Apache Commons Pool2** to maintain a pool of pre-initialized execution contexts. This enables:
- **Thread-safe concurrent evaluation** without creating new interpreters per call
- **Warm-up of contexts** — load scripts once, reuse contexts across requests
- **Configurable pool sizes** — tune to your throughput needs
- **Automatic resource cleanup** via try-with-resources

### Sandboxing

Each module supports configurable security levels:
- **Strict sandbox**: No file I/O, no network access, no shell execution
- **Standard sandbox**: Read-only file access, no network
- **Permissive mode**: Full access (development and testing only)

### Type Marshalling

Automatic bridging between language types:
- **Python** ↔ **Java**: int, float, str, list, dict, None, bool
- **JavaScript** ↔ **Java**: number, string, object, array, null, Promise
- **WebAssembly**: Numeric I/O via Value interface, binary module caching

---

## Diataxis Documentation Structure

YAWL polyglot documentation is organized by learning style:

### Tutorials — Learning by Doing
- [**Getting Started with GraalPy**](tutorials/01-graalpy-getting-started.md) — Your first Python evaluation in YAWL
- [**Getting Started with GraalJS**](tutorials/02-graaljs-getting-started.md) — Your first JavaScript evaluation in YAWL
- [**Getting Started with GraalWasm**](tutorials/03-graalwasm-getting-started.md) — Your first WASM module execution
- [**Rust4pmBridge: OCEL2 Process Mining**](tutorials/04-rust4pm-ocel2.md) — Analyze event logs in real time
- [**DMN Decision Execution**](tutorials/05-dmn-decision-execution.md) — DMN 1.3 business rules in workflows
- [**DataModelling SDK Integration**](tutorials/06-data-modelling-sdk.md) — Schema operations with 70+ formats

### How-To Guides — Solving Real Problems
- How to load Python virtual environments with pip packages
- How to cache and reuse WASM modules
- How to marshal complex objects between Java and JavaScript
- How to set up strict sandboxing for untrusted scripts
- How to integrate polyglot execution into YAWL task handlers
- How to profile polyglot code for performance bottlenecks

### Reference — API & Configuration
- [GraalPy API Reference](../reference/polyglot-graalpy-api.md) — PythonExecutionEngine, PythonContextPool, PythonVirtualEnvironment
- [GraalJS API Reference](../reference/polyglot-graaljs-api.md) — JavaScriptExecutionEngine, JsTypeMarshaller, JavaScriptContextPool
- [GraalWasm API Reference](../reference/polyglot-graalwasm-api.md) — WasmExecutionEngine, WasmModule, Rust4pmBridge, WasmBinaryCache
- [DMN Integration Guide](../explanation/dmn-integration.md) — DmnWasmBridge, dmn_feel_engine.wasm integration
- [DataModelling API Reference](../reference/data-modelling-api.md) — DataModellingBridge methods and schema operations

### Explanation — Concepts & Design
- [Polyglot Execution Model](../explanation/polyglot-execution-model.md) — How GraalVM contexts work, threading, lifecycle
- [Type Marshalling Deep Dive](../explanation/type-marshalling.md) — Python dict vs JS object, array handling, null semantics
- [Sandboxing & Security](../explanation/polyglot-sandboxing.md) — Trust boundaries, capabilities, threat model
- [Performance Tuning](../explanation/polyglot-performance.md) — Context pooling, cache warmth, profiling strategies

---

## Maven Setup

All three modules are available from Maven Central under `org.yawlfoundation`:

```xml
<!-- GraalPy -->
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalpy</artifactId>
    <version>6.0.0-GA</version>
</dependency>

<!-- GraalJS -->
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graaljs</artifactId>
    <version>6.0.0-GA</version>
</dependency>

<!-- GraalWasm (includes Rust4pmBridge) -->
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalwasm</artifactId>
    <version>6.0.0-GA</version>
</dependency>

<!-- DMN Decision Engine -->
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-dmn</artifactId>
    <version>6.0.0-GA</version>
</dependency>

<!-- Data Modelling SDK Bridge -->
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-data-modelling</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

### Runtime Requirements

You must run YAWL on **GraalVM JDK 24.1 or later** with language support installed:

```bash
# Install GraalVM JDK 24.1+
sdk install java 24.1.0-graal

# Install language support
gu install python  # for yawl-graalpy
gu install js      # for yawl-graaljs
gu install wasm    # for yawl-graalwasm
```

---

## Quick Start Example

Here's a typical workflow task handler that evaluates a risk score using JavaScript, then processes the result in Python:

```java
// In a YAWL task completion handler
public void processWorkItem(YWorkItem item) {
    // Step 1: Evaluate risk score in JavaScript
    JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
        .sandboxed(true)
        .contextPoolSize(4)
        .build();

    double riskScore = jsEngine.evalToDouble(
        "calculateRisk(" + item.getSize() + ", " + item.getPriority() + ")"
    );

    // Step 2: Process result in Python
    PythonExecutionEngine pyEngine = PythonExecutionEngine.builder()
        .poolSize(4)
        .sandboxConfig(PythonSandboxConfig.standard())
        .build();

    String prediction = pyEngine.evalToString(
        "predict_outcome(" + riskScore + ", '" + item.getType() + "')"
    );

    item.setRiskLevel(riskScore);
    item.setOutcomePredictor(prediction);

    jsEngine.close();
    pyEngine.close();
}
```

---

## Next Steps

1. **New to polyglot execution?** Start with the tutorials for your language of choice.
2. **Need to solve a specific problem?** Check the How-To guides.
3. **Integrating into production?** Read Sandboxing & Security and Performance Tuning in Explanation.
4. **Looking for API details?** Consult the Reference section for your module.

Happy polyglot engineering!
