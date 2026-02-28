# Explanation: Unified Polyglot Execution Model

**Why three separate modules (GraalPy, GraalJS, GraalWASM)?** Each language has distinct use cases, performance characteristics, and library ecosystems. YAWL provides three unified modules that share context pooling, sandboxing, and type marshalling APIs.

---

## The Polyglot Vision

Modern workflow engines need to execute multiple languages in a single JVM without spawning subprocesses or making REST calls:

```
┌─────────────────────────────────────────┐
│  Workflow Engine (Java)                 │
├─────────────────────────────────────────┤
│  Task: Calculate Risk Score             │
│  → Execute JavaScript for routing logic │
│                                         │
│  Task: Analyze Customer Data            │
│  → Execute Python for ML predictions    │
│                                         │
│  Task: Audit Log Processing             │
│  → Execute WebAssembly for performance  │
└─────────────────────────────────────────┘
```

All three languages in **one process**, **zero network latency**, **shared memory**.

---

## GraalVM Polyglot Architecture

All three YAWL polyglot modules use **GraalVM's polyglot execution engine**:

```
┌───────────────────────────────────────────────────────────┐
│               GraalVM JDK 24.1+                           │
├───────────────────────────────────────────────────────────┤
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   GraalJS   │  │  GraalPy    │  │  GraalWASM  │     │
│  │ (JavaScript)│  │  (Python)   │  │  (WASM)     │     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘     │
│         │                 │                 │           │
│  ┌──────┴─────────────────┴─────────────────┴───────┐  │
│  │        GraalVM Polyglot Context                   │  │
│  │  (Shared memory, shared type system)             │  │
│  └────────────────────────────────────────────────┘  │
│         ↓                                              │
│  ┌──────────────────────────────────────────────┐    │
│  │  JVM (Java execution)                        │    │
│  └──────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────┘
```

Key insight: **A single GraalVM context can execute Python, JavaScript, and WASM simultaneously.**

---

## Module Comparison

### yawl-graaljs: JavaScript (ES2022)

**Best for**: Business rules, dynamic routing, real-time decisions

```java
JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(SandboxConfig.STRICT)
    .build();

// Evaluate JavaScript expression
double riskScore = jsEngine.evalToDouble("""
    function calculateRisk(amount, priority) {
        if (amount > 10000) return 0.8;
        if (priority == 'high') return 0.6;
        return 0.2;
    }
    calculateRisk(25000, 'high')
""");
// Result: 0.8
```

**Advantages:**
- Smallest footprint (~20MB)
- Fastest execution (<5ms per eval)
- Rich JavaScript library ecosystem (lodash, moment, etc.)
- Easy for frontend developers

**Disadvantages:**
- No scientific computing libraries
- Weaker type system
- Not ideal for heavy computation

### yawl-graalpy: Python 3.11+

**Best for**: Data analysis, ML predictions, scientific computing

```java
PythonExecutionEngine pyEngine = PythonExecutionEngine.builder()
    .poolSize(2)
    .pythonVirtualEnv(Path.of("/path/to/venv"))
    .build();

// Execute Python with numpy/pandas
String prediction = pyEngine.evalToString("""
    import numpy as np
    import pandas as pd

    def predict_churn(customer_features):
        # Load pre-trained model
        model = load_model('churn_model.pkl')
        # Predict
        return float(model.predict([customer_features])[0])

    result = predict_churn([1.2, 3.4, 5.6])
    str(result)
""");
```

**Advantages:**
- Rich data science libraries (numpy, pandas, scikit-learn)
- Strong typing with type hints
- Mature ML/AI ecosystem
- Bytecode caching for performance

**Disadvantages:**
- Larger footprint (~100MB with libraries)
- Slower startup (150-300ms)
- Requires virtual environment for pip packages

### yawl-graalwasm: WebAssembly

**Best for**: High-performance computing, process mining, compiled binaries

```java
WasmExecutionEngine wasmEngine = WasmExecutionEngine.builder()
    .modulePath(Path.of("analytics.wasm"))
    .sandboxConfig(WasmSandboxConfig.STRICT)
    .build();

// Call WASM function
int[] result = wasmEngine.invoke("analyze_log",
    new int[]{10, 20, 30, 40, 50}
);
// Result: WASM compute without GC pauses
```

**Advantages:**
- Compiled binaries (Rust, C, C++)
- Predictable performance (no GC)
- Binary caching
- Can run untrusted code safely

**Disadvantages:**
- Steeper build complexity (requires WASM toolchain)
- Less portable (WASM target not available for all languages)
- Smaller library ecosystem

---

## Unified API Design

All three modules share common patterns:

### 1. Builder Pattern

```java
// GraalJS
JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(SandboxConfig.STRICT)
    .build();

// GraalPy
PythonExecutionEngine pyEngine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.STANDARD)
    .build();

// GraalWASM
WasmExecutionEngine wasmEngine = WasmExecutionEngine.builder()
    .modulePath(Path.of("module.wasm"))
    .sandboxConfig(WasmSandboxConfig.STRICT)
    .build();
```

All builders support:
- `contextPoolSize()` / `poolSize()` → Thread-safe context pooling
- `sandboxConfig()` → Security level (STRICT, STANDARD, PERMISSIVE)
- `timeout()` → Execution timeout in milliseconds

### 2. Context Pooling (Apache Commons Pool2)

All three modules use identical pooling strategy:

```java
public abstract class ExecutionContextPool<T> {
    /**
     * Borrow a context from the pool (blocking)
     */
    public T borrowContext();

    /**
     * Return a context to the pool
     */
    public void returnContext(T context);

    /**
     * Get pool statistics
     */
    public PoolStats getStats();  // active, idle, waiters
}
```

This ensures:
- **Reusability**: Contexts are warmed up and reused
- **Thread-safety**: Pool handles concurrent access
- **Resource efficiency**: No context creation overhead per invocation

### 3. Type Marshalling

Each module automatically converts between Java and language types:

**JavaScript → Java**
```
JS Type      → Java Type
null         → null
undefined    → null
true/false   → Boolean
42           → Integer or Double
"hello"      → String
[1,2,3]      → List<Object>
{a:1, b:2}   → Map<String, Object>
Promise      → CompletableFuture (async)
```

**Python → Java**
```
Python Type  → Java Type
None         → null
True/False   → Boolean
42           → Integer
3.14         → Double
"hello"      → String
[1,2,3]      → List<Object>
{'a': 1}     → Map<String, Object>
numpy array  → double[] or int[]
```

**WASM → Java**
```
WASM Type    → Java Type
i32          → Integer
i64          → Long
f32          → Float
f64          → Double
(no arrays)  → Pass via memory or JSON
```

### 4. Sandboxing Configuration

All three modules support configurable trust boundaries:

```java
// STRICT: Maximum security
SandboxConfig strict = SandboxConfig.strict()
    .allowFileRead(false)
    .allowFileWrite(false)
    .allowNetwork(false)
    .allowShell(false)
    .timeout(Duration.ofSeconds(10));

// STANDARD: Balance security and functionality
SandboxConfig standard = SandboxConfig.standard()
    .allowFileRead(true)    // Read-only
    .allowFileWrite(false)
    .allowNetwork(false)
    .allowShell(false);

// PERMISSIVE: Development only
SandboxConfig permissive = SandboxConfig.permissive();
```

---

## Execution Flow: High-Level

### JavaScript Execution

```
┌──────────────────────────┐
│  JavaScriptExecutionEngine.evalToDouble(code)
└────────────┬─────────────┘
             ↓
┌──────────────────────────────────────────┐
│ Step 1: Borrow context from pool         │
│ (either new or reused)                   │
└─────────────────┬──────────────────────┘
                  ↓
┌──────────────────────────────────────────┐
│ Step 2: Create GraalVM Value from code   │
│ engine.eval(Source.create("js", code))   │
└─────────────────┬──────────────────────┘
                  ↓
┌──────────────────────────────────────────┐
│ Step 3: Marshal result to Java Double    │
│ result.asDouble()                        │
└─────────────────┬──────────────────────┘
                  ↓
┌──────────────────────────────────────────┐
│ Step 4: Return context to pool           │
│ pool.returnContext(context)              │
└─────────────────┬──────────────────────┘
                  ↓
┌──────────────────────────────────────────┐
│ Return: 0.8 (Java Double)                │
└──────────────────────────────────────────┘
```

All steps are synchronized and thread-safe.

---

## Decision Matrix: Which Module to Use

| Use Case | Module | Reason |
|----------|--------|--------|
| Calculate risk scores | GraalJS | Fast, dynamic, no dependencies |
| Route based on business rules | GraalJS | Easy to update rules without recompile |
| Analyze customer data with pandas | GraalPy | Rich data science libs |
| Run ML model predictions | GraalPy | numpy/sklearn/tensorflow support |
| OCEL2 process mining | GraalWASM | Rust4pmBridge pre-built |
| Custom numeric computation | GraalWASM | Rust compiled for speed |
| Complex workflow conditionals | GraalJS | Lower latency |
| Report generation with charts | GraalPy | matplotlib/seaborn support |
| Real-time anomaly detection | GraalWASM | No GC pauses |

---

## Performance Comparison

### Latency Per Invocation

| Operation | GraalJS | GraalPy | GraalWASM |
|-----------|---------|---------|-----------|
| Simple calculation | 1-3ms | 10-20ms | 2-5ms |
| With dependencies | 3-5ms | 30-50ms | 5-10ms |
| Pool borrow overhead | <1ms | <1ms | <1ms |
| **Total (first call)** | **2-8ms** | **11-70ms** | **3-15ms** |
| **Total (subsequent)** | **1-5ms** | **10-60ms** | **2-10ms** |

### Memory Usage

| Module | Per Context | 4 Contexts | Notes |
|--------|------------|-----------|-------|
| GraalJS | 15-20MB | 60-80MB | Lightweight |
| GraalPy | 80-120MB | 320-480MB | With libraries |
| GraalWASM | 10-15MB | 40-60MB | Module-dependent |

### Throughput (High Concurrency)

| Scenario | GraalJS | GraalPy | GraalWASM |
|----------|---------|---------|-----------|
| 4 threads, simple ops | ~1000/sec | ~150/sec | ~800/sec |
| 8 threads, simple ops | ~2000/sec | ~300/sec | ~1600/sec |
| With I/O | Similar | Similar | Similar |

---

## Polyglot Debugging

### Enabled Runtime Metrics

```java
// Get execution statistics
var jsStats = jsEngine.getPoolStats();
System.out.println("GraalJS active contexts: " + jsStats.getActiveCount());
System.out.println("GraalJS idle contexts: " + jsStats.getIdleCount());
System.out.println("GraalJS waiter threads: " + jsStats.getWaiterCount());

// Get per-invocation timing
long startNs = System.nanoTime();
double result = jsEngine.evalToDouble(code);
long elapsedNs = System.nanoTime() - startNs;
System.out.println("Execution time: " + (elapsedNs / 1_000_000.0) + " ms");
```

### Enable GraalVM Tracing

```bash
# Trace all polyglot context operations
java -Dtruffle.TruffleInstrument.Tracer.TraceReturn=true \
     -Dtruffle.TruffleInstrument.Tracer.TraceInlining=true \
     MyWorkflowApp
```

---

## Best Practices

### 1. Choose Right Tool for Right Job

- **Rules**: JavaScript
- **Analytics**: Python
- **Performance**: WASM

### 2. Reuse Contexts

Don't create new engine per invocation:

```java
// Good: Singleton engine
private static final JavaScriptExecutionEngine JS_ENGINE =
    JavaScriptExecutionEngine.builder().poolSize(4).build();

public double evalRule(String code) {
    return JS_ENGINE.evalToDouble(code);
}

// Bad: New engine per invocation
public double evalRuleBad(String code) {
    var engine = JavaScriptExecutionEngine.builder().build();  // Expensive!
    return engine.evalToDouble(code);
}
```

### 3. Cache Compiled Code

```java
// Good: Pre-compile and reuse
private static final Value COMPILED_FN = JS_ENGINE.eval("""
    function riskScore(amount, priority) {
        ...
    }
""");

public double evaluate(int amount, String priority) {
    return COMPILED_FN.execute(amount, priority).asDouble();
}
```

### 4. Configure Pool Size for Workload

```java
// Rule of thumb: pool_size = (expected_throughput_per_sec / 100) + 2
// For 500 ops/sec: pool_size = (500 / 100) + 2 = 7

JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(7)  // Tune based on load test
    .build();
```

---

## Next Steps

- [Tutorial: GraalJS Getting Started](../tutorials/02-graaljs-getting-started.md)
- [Tutorial: GraalPy Getting Started](../tutorials/01-graalpy-getting-started.md)
- [Tutorial: GraalWASM Getting Started](../tutorials/03-graalwasm-getting-started.md)
- [How-To: Configure Sandbox](../how-to/configure-sandbox.md)
