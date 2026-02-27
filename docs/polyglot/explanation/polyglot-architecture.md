# Polyglot Runtime Architecture in YAWL

Understanding why and how YAWL uses GraalVM Polyglot for multi-language script execution.

## Why GraalVM Instead of Subprocess/IPC

### The Problem with External Processes

Older approaches (Java 8-11) used subprocess isolation:
- Start Python/Node process per script
- Use stdin/stdout/serialization for data exchange
- Process lifecycle overhead: ~50-200ms per invocation
- Memory overhead: ~20-50MB per Python process
- Complex error handling and timeout management

### The GraalVM Solution

GraalVM Polyglot provides in-process language execution:
- Python, JavaScript, WASM run in isolated contexts **within the JVM**
- Same process = no IPC serialization overhead
- Memory-efficient: Contexts share GraalVM runtime (~200MB once, ~10-50MB per additional context)
- Deterministic error handling (exceptions are thrown directly to Java code)
- Natural Java ↔ Script data exchange via Polyglot Values

### Performance Implications

| Approach | Cold Start | Warm Call | Memory/Instance | Best For |
|----------|-----------|-----------|---|---|
| Subprocess (Python) | ~50ms | ~20ms | 30MB | One-off scripts |
| GraalPy Context (warm) | ~500ms | <1ms | 50-200MB | High-frequency calls |
| GraalJS Context (warm) | ~100ms | <1ms | 5-20MB | Business rules engine |
| GraalWasm Module | ~5ms | <1ms | 1-5MB | Compute-heavy tasks |

**Takeaway**: GraalVM wins on amortized cost when the same engine/context is reused. This is YAWL's design: one engine per application, shared across all workflow tasks.

## The GraalVM Context Lifecycle

### What is a Context?

A `org.graalvm.polyglot.Context` is a **sandboxed execution environment** for a guest language (Python, JS, WASM). Think of it as a lightweight virtual machine:
- Private heap (garbage-collected independently)
- Isolated global scope (variables, function definitions)
- Sandbox restrictions (file I/O, network, system calls)
- Thread-local (not safe to share across threads)

### Why Contexts Are Expensive

Creating a new Context involves:
1. **Parsing language runtime** (~100-500ms for Python, ~20-100ms for JS)
2. **Initializing standard library** (loading modules like `os`, `json`, etc.)
3. **JIT compilation** (first invocation triggers JIT, subsequent calls are faster)
4. **Memory allocation** (dedicated heap per context)

**Cost**: ~100-500ms per Context, ~50-200MB per Python Context, ~5-20MB per JS Context.

### Why Pooling Solves This

Instead of:
```
Create Context → Evaluate → Close Context → (Repeat for next task)
```

YAWL does:
```
Create Pool of N Contexts → Reuse across tasks → Share same initialization
```

- First call to engine: Pay setup cost (1 context)
- Second call to engine: Reuse existing context (instant)
- N-th concurrent call: Borrow from pool (instant if available)

## The Context Pool Architecture

### GraalJS Pool (Example)

```
JavaScriptExecutionEngine
├── JavaScriptContextPool (size=8)
│   ├── JavaScriptExecutionContext[0] ─── Task 1 (borrowed)
│   ├── JavaScriptExecutionContext[1] ─── Task 2 (borrowed)
│   ├── JavaScriptExecutionContext[2] ─── (idle)
│   ├── JavaScriptExecutionContext[3] ─── (idle)
│   ├── JavaScriptExecutionContext[4] ─── (idle)
│   ├── JavaScriptExecutionContext[5] ─── (idle)
│   ├── JavaScriptExecutionContext[6] ─── (idle)
│   └── JavaScriptExecutionContext[7] ─── (idle)
└── Cache of loaded scripts (rules.js, utils.js, ...)
```

**Lifecycle**:
1. Task A requests engine
2. Engine borrows Context[0] from pool
3. Evaluate script in Context[0]
4. Return result
5. Engine returns Context[0] to pool (state may be polluted, but that's OK for stateless evaluation)

### Apache Commons Pool2 Integration

YAWL uses Apache Commons Pool2 for robust context pooling:
- **Borrow**: Block until context available (configurable timeout)
- **Return**: Context goes back to idle queue
- **Eviction**: Old/stale contexts are recycled
- **Factory**: Creates new contexts on demand

### Why GraalPy and GraalJS Have Different Pool Sizes

**GraalPy Pool**:
```java
PythonExecutionEngine.builder()
    .poolSize(4)  // Fewer contexts
    .build();
```
- Each Python Context: 50-200MB
- Slower to create (~500ms)
- Standard recommendation: `poolSize = expected_concurrent_tasks`

**GraalJS Pool**:
```java
JavaScriptExecutionEngine.builder()
    .contextPoolSize(8)  // More contexts (lighter weight)
    .build();
```
- Each JS Context: 5-20MB
- Faster to create (~100ms)
- Standard recommendation: `poolSize = 2 × expected_concurrent_tasks`

### Why GraalWasm Doesn't Use a Pool

```java
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

try (WasmModule module = engine.loadModuleFromClasspath("wasm/math.wasm", "m")) {
    // No pool; each load creates its own Context
}
```

Reason: WASM modules are **stateless** and **lightweight** (~1-5MB). Creating a new Context per invocation is acceptable. The binary cache (`WasmBinaryCache`) ensures parsing happens once.

## Thread Safety and Isolation

### GraalVM Contexts are NOT thread-safe

```java
// WRONG: Context is thread-local
JavaScriptExecutionContext ctx = pool.borrow();
// Thread A
executor.submit(() -> ctx.eval("x = 1"));  // ❌ Thread B might interfere
// Thread B
executor.submit(() -> ctx.eval("y = 2"));
```

### The Pool Solves This

```java
// CORRECT: Each thread borrows its own context
executor.submit(() -> {
    engine.evalToString("x = 1");  // Borrows Context[i] for Thread A
});
executor.submit(() -> {
    engine.evalToString("y = 2");  // Borrows Context[j] for Thread B
});
```

The pool ensures:
- Thread A and B never share a context
- Contexts are borrowed/returned atomically
- No two threads evaluate in the same context simultaneously

## Memory Model and Garbage Collection

### Heap Isolation

Each Context has its **own GC heap** separate from the Java heap:

```
Java Heap (JVM)                GraalVM Python Context
┌─────────────────┐            ┌──────────────────┐
│ String "hello"  │            │ Python str object│
│ Integer[] array │            │ dict/list objects│
│ YAWL objects    │            │ (Python GC)      │
└─────────────────┘            └──────────────────┘
```

### Polyglot Values

When Java calls Python and receives a result, the value is wrapped in a `org.graalvm.polyglot.Value`:

```java
Object pythonResult = engine.eval("{'name': 'Alice', 'age': 30}");
// Result is a Value wrapping a Python dict

Map<String, Object> javaMap = engine.evalToMap("...");
// TypeMarshaller converts dict → Map<String, Object> by reading values
// Original Python dict stays in Python Context's GC heap
```

The conversion is **lazy**: Polyglot Values don't copy data until you explicitly convert (e.g., `evalToMap`).

## Sandbox Restrictions

### Enforcement at Language Level

GraalVM doesn't use OS-level isolation (like containers); instead, it patches the language runtime:

```
HostAccess.RESTRICTED ← Java side (class methods visible to script)
HostAccess.ALL        ← No restrictions (dangerous)

Sandbox Config
├── strict()      ← Block all I/O, network
├── standard()    ← Read-only file I/O, no network
└── permissive()  ← Allow all (not recommended)
```

### How Strict Sandbox Works

```java
engine = JavaScriptExecutionEngine.builder()
    .sandboxConfig(JavaScriptSandboxConfig.strict())
    .build();

// This fails:
engine.eval("require('fs').readFileSync('/etc/passwd')");
// Error: require is not defined (or fs module unavailable)

// This works:
engine.evalToString("Math.random()");  // Pure computation, no I/O
```

Strict sandbox achieves this by **not loading** module libraries that could access the filesystem. The script sees a minimal environment with no `fs`, `http`, `os`, etc. modules.

## Integration Points

### YAWL Workflow Engine ↔ Polyglot Engines

```
┌─────────────────────────────────────────┐
│      YAWL Workflow Task Handler         │
│  (LoanRoutingTask, DataTransformTask)   │
└────────────┬────────────────────────────┘
             │ calls
             ↓
    ┌────────────────────────┐
    │ Polyglot Engine        │
    │ (Shared Singleton)     │
    │ ├─ GraalJS engine      │
    │ ├─ GraalPy engine      │
    │ └─ GraalWasm engine    │
    └────────────────────────┘
             │
             ├─→ eval("business rules")
             ├─→ invokeFunction("routeApplication", args)
             └─→ evalScript("classpath:rules.js")
```

### Spring Integration

```java
@Configuration
public class PolyglotConfig {
    @Bean
    JavaScriptExecutionEngine jsEngine() { ... }
    
    @Bean
    PythonExecutionEngine pyEngine() { ... }
}

@Service
public class LoanRoutingTask {
    @Autowired
    JavaScriptExecutionEngine jsEngine;  // Injected singleton
}
```

Spring manages the lifecycle: engine is created at application startup, shared by all tasks, closed on shutdown.

## Why This Design is Better Than Alternatives

| Design | Pros | Cons |
|--------|------|------|
| **GraalVM Polyglot (YAWL)** | Fast (no IPC), type-safe, reusable contexts | Contexts use JVM memory; startup cost once |
| **Subprocess (old)** | Isolation, can restart language | IPC overhead, process mgmt, 30MB per instance |
| **Interpreted (Nashorn)** | In-process, simple | No longer supported; JS engine outdated |
| **Graal Truffle AOT** | Compile to native binary | Complex build, not suitable for dynamic rules |

**Conclusion**: GraalVM Polyglot strikes the right balance for YAWL: in-process efficiency + language flexibility + robust isolation.

