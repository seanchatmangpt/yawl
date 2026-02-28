# Product Requirements Document: GraalWASM + Rust Library Binding Pattern

**Version**: 1.0
**Date**: 2026-02-28
**Status**: DRAFT — First Principles Architecture
**Purpose**: Define a reusable pattern for wrapping Rust libraries in Java 25 via GraalWASM with zero-copy semantics

---

## Executive Summary

**Problem**: Java applications need high-performance, type-safe access to Rust libraries without JNI complexity or data marshalling overhead.

**Solution**: A systematic pattern for:
1. Exposing **complete Rust library APIs** via WASM bindings
2. Loading and calling those bindings from Java 25 via GraalVM's polyglot WASM runtime
3. Achieving **zero-copy semantics** for large data structures
4. Maintaining **type safety and idiomatic Java** at the boundary

**Pattern applies to**: Any Rust library that can be compiled to WASM (math, crypto, process mining, data compression, ML inference, etc.)

---

## 1. Problem Statement

### Current State of Rust-Java Interop

| Method | Pros | Cons |
|--------|------|------|
| **JNI** | Direct native calls | Complex build, platform-specific, unsafe memory, FFI boilerplate |
| **REST API** | Language agnostic | Network overhead, serialization tax, deployment complexity |
| **GRPC** | Typed, fast | Additional service, latency, deployment overhead |
| **WASM** | Sandboxed, cross-platform | Previously: no standard, unclear patterns |

### Why GraalWASM Changes Everything

1. **WASM as lingua franca** — Rust → WASM → Java (any JVM language)
2. **Sandbox isolation** — Untrusted code safe in WASM memory
3. **Zero OS dependency** — Same binary runs on Linux/Mac/Windows
4. **GraalVM native** — Direct polyglot integration, no translation layer
5. **Sub-microsecond calls** — For pointer operations (zero-copy)

### The Gap This PRD Fills

**Problem**: How do you systematically wrap a Rust library (e.g., Rust4PM: 30+ functions, multiple modules, complex data structures) in a way that:
- Exposes **100% of the Rust API** (not just a few hand-picked functions)
- Achieves **minimal latency** for hot paths
- Maintains **zero-copy semantics** for large data (event logs, matrices, graphs)
- Provides **idiomatic Java types** (records, sealed classes, exceptions)
- Is **reproducible** across different Rust libraries

---

## 2. Design Goals

### Primary Goals (Must Have)

1. **Complete API Coverage** — Every public Rust function exposed in WASM
2. **Zero-Copy Memory** — Large data structures passed by pointer, not copied
3. **Type Safety** — Java records, sealed classes, checked exceptions
4. **Latency** — <1µs for pointer operations, <100µs for data marshalling
5. **Reproducibility** — Pattern works for any Rust crate (math, crypto, process mining, etc.)

### Secondary Goals (Should Have)

6. **Concurrent Access** — Virtual threads with WASM context pooling
7. **Memory Isolation** — WASM sandbox prevents Rust bugs from crashing JVM
8. **Error Propagation** — Rust errors → Java exceptions
9. **Documentation** — Auto-generated API mapping (Rust docs ↔ Java wrapper)

### Non-Goals (Out of Scope)

- Building a specific Rust4PM wrapper (that's implementation, not pattern)
- Modifying Rust source code (user provides .wasm binary)
- Handling JavaScript interop (pure WASM only)
- Supporting older Java versions (<25)

---

## 3. Architecture Overview

### High-Level Flow

```
Rust Library (any crate)
    ↓
[wasm-bindgen macros]
    ↓
Cargo build --target wasm32-unknown-unknown
    ↓
libfoo.wasm (compiled binary)
    ↓
[Java 25 Binding Generator] ← PRD defines this
    ↓
GraalWASM Wrapper (idiomatic Java)
    ↓
Application Code (Java 25)
```

### Runtime Architecture

```
┌─────────────────────────────────────────┐
│  Application Code (Java)                │
│  ├── Rust4pmEngine.parseOcel2Json(...)  │
│  └── ProcessMiningService.discoverDfg() │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  Typed Wrapper Layer (records/records)  │
│  ├── Rust4pmOcelParser                  │
│  ├── OcelLog (record)                   │
│  └── OcelEvent (record)                 │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  GraalWASM Bridge (WasmModule.execute)  │
│  ├── Value conversion                   │
│  ├── Error handling                     │
│  └── Pointer management                 │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  GraalVM Polyglot Engine                │
│  ├── WasmExecutionEngine                │
│  ├── WasmModule                         │
│  └── Context pool (virtual threads)     │
└──────────────┬──────────────────────────┘
               │
┌──────────────▼──────────────────────────┐
│  WASM Binary (.wasm file)               │
│  └── Rust library compiled to WASM      │
└─────────────────────────────────────────┘
```

---

## 4. Core Concepts

### 4.1 WASM Binding Tiers

**Tier 1: Raw WASM Functions**
Direct mapping of WASM-exported functions to Java methods.
```java
// Raw: corresponds to WASM export directly
Value result = module.execute("wasm_parse_ocel2_json", jsonBytes);
```

**Tier 2: Typed Bridge**
Convert GraalVM Values to Java objects with minimal overhead.
```java
// Bridge: Value → OcelLog record
OcelLog log = parser.parseOcel2Json(jsonString);
```

**Tier 3: High-Level API**
Domain-specific methods hiding WASM details entirely.
```java
// Application: business logic using domain objects
double conformance = engine.checkConformance(log, petriNet);
```

### 4.2 Memory Models

#### Model 1: Auto-Cleanup (Copy)
```
Java Memory          WASM Memory
─────────────────────────────────
                     [Raw binary]
                          ↓
                     [Parsed Data]
                          ↓
OcelLog (record) ←── [Copy to Java]
                          ↓
                     [WASM frees]
```
**Latency**: ~100µs (for OCEL2 logs <1MB)
**Use case**: One-shot parsing, small datasets
**Tradeoff**: GC pressure from copies

#### Model 2: Zero-Copy (Pointer)
```
Java Memory          WASM Memory
─────────────────────────────────
                     [Raw binary]
                          ↓
                     [Parsed Data]
                          ↓
OcelPointer ←─────── [Keep in WASM]
   ├── addr (i64)        │
   ├── size (i64)        │
   └── cleanup()         ↓
                    [Manual free]
```
**Latency**: <1µs (just pointer arithmetic)
**Use case**: Large datasets, streaming, repeated access
**Tradeoff**: Manual memory management

#### Model 3: Hybrid (Pointer + Lazy Materialization)
```
OcelPointer (lazy)
   ├── read(offset, length) → byte[]    [On-demand copy]
   ├── iterate() → Stream<OcelEvent>    [Streaming, no copy]
   └── materialize() → OcelLog          [Full copy]
```
**Latency**: 0µs to 100µs (depends on what you do)
**Use case**: Progressive processing, large logs
**Tradeoff**: API complexity

### 4.3 Type Mapping Rules

**Rust → WASM → GraalVM Value → Java**

| Rust Type | WASM Export | GraalVM Value | Java Type |
|-----------|-------------|---------------|-----------|
| `i32` | 32-bit signed int | `Value.asInt()` | `int` |
| `i64` | 64-bit signed int | `Value.asLong()` | `long` |
| `f64` | 64-bit float | `Value.asDouble()` | `double` |
| `bool` | 1 (true) or 0 (false) | `Value.asBoolean()` | `boolean` |
| `&[u8]` | pointer + length | Separate args | `byte[]` |
| `String` | pointer + length | Separate args | `String` |
| `struct Foo { ... }` | pointer | `Value.asInt()` | `OcelPointer` or `FooRecord` |
| `Result<T, E>` | (return_value, error_flag) | Two returns | `T` or exception |

---

## 5. Implementation Pattern

### 5.1 Three-Layer Architecture

#### Layer 1: WasmBindingFacade
**Location**: `org.yawlfoundation.yawl.graalwasm.Rust4pmWasmBridge`
**Purpose**: Low-level WASM function calls
**Responsibility**: Value conversion, pointer management

```java
public class Rust4pmWasmBridge {
    private final WasmModule module;

    // Raw WASM exports (1:1 mapping)
    public Value wasmParseOcel2Json(byte[] jsonData) throws WasmException {
        return module.execute("wasm_parse_ocel2_json", jsonData);
    }

    // Helper: convert byte[] to WASM pointer
    protected long toWasmPointer(byte[] data) { ... }

    // Helper: read from WASM memory by pointer
    protected byte[] fromWasmPointer(long ptr, int len) { ... }
}
```

#### Layer 2: TypedBridge
**Location**: `org.yawlfoundation.yawl.integration.processmining.rust4pm.Rust4pmOcelParser`
**Purpose**: Value → Java records
**Responsibility**: JSON/XML parsing, record construction, error handling

```java
public class Rust4pmOcelParser {
    private final Rust4pmWasmBridge bridge;

    // Typed API (1:1 mapping of Rust public API)
    public OcelLog parseOcel2Json(String jsonString) throws ProcessMiningException {
        byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        Value result = bridge.wasmParseOcel2Json(jsonBytes);
        return OcelLog.fromWasmValue(result);
    }

    public OcelLog parseOcel2Xml(String xmlString) throws ProcessMiningException { ... }
}
```

#### Layer 3: HighLevelAPI
**Location**: `org.yawlfoundation.yawl.integration.processmining.ProcessMiningEngine`
**Purpose**: Domain-specific operations
**Responsibility**: Workflow orchestration, business logic

```java
public class ProcessMiningEngine {
    private final Rust4pmOcelParser parser;

    // High-level operations
    public ConformanceResult checkConformance(OcelLog log, PetriNet model) {
        // Delegates to Rust4PM discovery/conformance functions
    }

    public ProcessModel discoverModel(OcelLog log) {
        // Delegates to Rust4PM discovery functions
    }
}
```

### 5.2 Record Design Pattern

**Goal**: Type-safe, immutable representation of Rust data structures.

```java
// Root record (sealed to restrict implementations)
public sealed record OcelLog(
    String version,
    List<OcelEvent> events,
    List<OcelObject> objects,
    List<OcelRelation> relations,
    Map<String, String> metadata
) permits OcelLogImpl { }

// Event record
public record OcelEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    Map<String, OcelValue> attributes
) implements Comparable<OcelEvent> { }

// Type-safe attribute values
public sealed interface OcelValue permits
    OcelValue.StringValue,
    OcelValue.NumberValue,
    OcelValue.BooleanValue {

    record StringValue(String value) implements OcelValue {}
    record NumberValue(double value) implements OcelValue {}
    record BooleanValue(boolean value) implements OcelValue {}
}

// Pointer-based record for zero-copy access
public record OcelPointer(
    long address,
    long sizeBytes,
    WasmModule module
) implements AutoCloseable {
    public int getEventCount() throws WasmException {
        return (int) module.execute("wasm_get_ocel_num_events_from_pointer", address)
            .asLong();
    }

    public void close() throws WasmException {
        module.execute("wasm_destroy_ocel_pointer", address);
    }
}
```

### 5.3 Error Handling Pattern

**Goal**: Convert Rust errors to Java exceptions while preserving error context.

```java
public sealed abstract class ProcessMiningException extends Exception
    permits WasmExecutionException, JsonParseException, XmlParseException {

    protected ProcessMiningException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ProcessMiningException fromWasmError(WasmException e) {
        return switch(e.getErrorKind()) {
            case EXECUTION_ERROR -> new WasmExecutionException(
                "WASM runtime error: " + e.getMessage(), e);
            case MODULE_LOAD_ERROR -> new WasmExecutionException(
                "Failed to load Rust4PM WASM module", e);
            case CONTEXT_ERROR -> new WasmExecutionException(
                "GraalVM context error (out of memory?)", e);
            default -> new ProcessMiningException(
                "Unknown WASM error: " + e.getMessage(), e) {};
        };
    }
}

public final class WasmExecutionException extends ProcessMiningException { }
public final class JsonParseException extends ProcessMiningException { }
public final class XmlParseException extends ProcessMiningException { }
```

### 5.4 Concurrency Pattern (Virtual Threads)

**Goal**: Handle concurrent WASM calls without context exhaustion.

```java
public class WasmContextPool {
    private final ExecutorService executor;
    private final WasmExecutionEngine engine;
    private final Semaphore contextLimit;

    public WasmContextPool(int poolSize) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.contextLimit = new Semaphore(poolSize);
        this.engine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.pureWasm())
            .build();
    }

    public <T> T executeAsync(Function<WasmModule, T> task) throws Exception {
        contextLimit.acquire();
        try {
            return executor.submit(() -> {
                try (WasmModule module = engine.loadModuleFromClasspath(
                        "wasm/process_mining_wasm_bg.wasm", "rust4pm")) {
                    return task.apply(module);
                }
            }).get();
        } finally {
            contextLimit.release();
        }
    }
}

// Usage
OcelLog log1 = contextPool.executeAsync(module ->
    bridge.parseOcel2Json(jsonData1));
OcelLog log2 = contextPool.executeAsync(module ->
    bridge.parseOcel2Json(jsonData2));
```

---

## 6. API Design Principles

### 6.1 1:1 Mapping Rule
**Every public Rust function MUST have a corresponding Java method.**

```rust
// Rust
pub fn discover_dfg(log: &EventLog) -> DirectlyFollowsGraph { ... }
pub fn discover_alpha_ppp(log: &EventLog) -> Result<PetriNet, Error> { ... }
```

```java
// Java (exact method names, parameters in order)
public DirectlyFollowsGraph discoverDfg(EventLog log) throws ProcessMiningException { ... }
public PetriNet discoverAlphaPpp(EventLog log) throws ProcessMiningException { ... }
```

### 6.2 Type Fidelity Rule
**Preserve Rust semantics in Java types.**

```rust
// Rust: generic type with multiple variants
pub enum ProcessModel {
    PetriNet(PetriNet),
    ProcessTree(ProcessTree),
    DeclarativeModel(DeclareModel),
}
```

```java
// Java: sealed interface (type-safe)
public sealed interface ProcessModel permits
    PetriNetModel, ProcessTreeModel, DeclareModel {
    // Common methods
}
```

### 6.3 Error Propagation Rule
**Rust Result<T, E> → Java checked exception.**

```rust
// Rust
pub fn validate_petri_net(pn: &PetriNet) -> Result<(), ValidationError> { ... }
```

```java
// Java: throws exception on Err, returns value on Ok
public void validatePetriNet(PetriNetModel pn) throws ValidationException { ... }
```

---

## 7. WASM Binding Generation Strategy

### 7.1 Automated WASM Binding Process

**Input**: Rust source code
**Output**: `.wasm` binary with complete API exposed

```
src/lib.rs (Rust library)
    ↓
[Add #[wasm_bindgen] to every public function]
    ↓
Cargo.toml
    [dependencies]
    wasm-bindgen = "0.2"

    [lib]
    crate-type = ["cdylib"]
    ↓
cargo build --target wasm32-unknown-unknown --release
    ↓
target/wasm32-unknown-unknown/release/libfoo.wasm
    ↓
[Copy to Java resources]
src/main/resources/wasm/libfoo.wasm
```

### 7.2 Function Export Checklist

For each public Rust function:
- [ ] Add `#[wasm_bindgen]` attribute
- [ ] Primitive types only in signature (i32, f64, bool, String, &[u8])
- [ ] Complex types → use pointers or JSON serialization
- [ ] Result<T, E> → return T, use custom error handling
- [ ] Test function is callable from WASM

### 7.3 Example: Rust4PM Complete Binding

```rust
// src/lib.rs (excerpt)
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
pub fn wasm_parse_ocel2_json(json_data: &[u8]) -> Vec<u8> {
    let log = parse_ocel2_json_internal(json_data)?;
    serde_json::to_vec(&log).unwrap()
}

#[wasm_bindgen]
pub fn wasm_discover_dfg(log_json: &str) -> String {
    let log = serde_json::from_str(log_json)?;
    let dfg = discover_dfg(&log);
    serde_json::to_string(&dfg).unwrap()
}

#[wasm_bindgen]
pub fn wasm_discover_alpha_ppp(log_json: &str) -> String {
    let log = serde_json::from_str(log_json)?;
    let pn = discover_alpha_ppp(&log)?;
    serde_json::to_string(&pn).unwrap()
}

// ... 30+ more functions with #[wasm_bindgen]
```

---

## 8. Performance Requirements

### 8.1 Latency Targets

| Operation | Target | Measurement |
|-----------|--------|-------------|
| Pointer operation (zero-copy) | <1µs | Address arithmetic |
| Function call overhead | <10µs | WASM → Java bridge |
| Small data marshalling | <100µs | Uint8Array → String |
| Large data copy | <1ms | 1MB OCEL log |
| Record construction | <100µs | Build OcelLog record |

### 8.2 Throughput Targets

| Scenario | Target | Notes |
|----------|--------|-------|
| Parse OCEL2 JSON (100KB) | >10K logs/sec | Single thread |
| Concurrent parsing (10 threads) | >100K logs/sec | Virtual thread pool |
| Discovery (small log) | <100ms | DFG on <1000 events |
| Conformance check | <500ms | Token-based replay |

### 8.3 Memory Targets

| Scenario | Target | Notes |
|----------|--------|-------|
| Engine startup | <100MB | Includes WASM binary cache |
| Per context | <50MB | WASM linear memory |
| Record overhead | <10% | vs raw WASM data |

---

## 9. Example: Rust4PM Application

### Use Case: Parse OCEL2, Check Conformance, Report Metrics

```java
// Java 25 application code
public class CaseAnalytics {
    private final Rust4pmEngine engine = Rust4pmEngine.getInstance();

    public ConformanceReport analyzeCase(String ocel2Json, PetriNet model)
            throws ProcessMiningException {

        // Parse OCEL2 (zero-copy for large logs)
        try (OcelPointer logPtr = engine.parseOcel2JsonKeepInWasm(ocel2Json)) {

            // Check conformance
            double fitness = engine.checkConformance(logPtr, model);

            // Discover model if needed
            if (fitness < 0.8) {
                OcelLog fullLog = logPtr.materialize();  // Lazy: only copy if needed
                ProcessModel discovered = engine.discoverModel(fullLog);

                return new ConformanceReport(fitness, discovered, fullLog.eventCount());
            }

            return new ConformanceReport(fitness, model, logPtr.getEventCount());
        }
    }
}
```

**Performance**: ~50ms for 10K-event log (with conformance checking)

---

## 10. Testing Strategy

### 10.1 Unit Tests

**Layer 1 (WasmBridge)**: Test raw WASM function calls
```java
@Test
void testWasmParseOcel2Json() throws WasmException {
    Value result = bridge.wasmParseOcel2Json(validJsonBytes);
    assertThat(result).isNotNull();
}
```

**Layer 2 (TypedBridge)**: Test type conversion
```java
@Test
void testParseOcel2JsonReturnsValidRecord() throws ProcessMiningException {
    OcelLog log = parser.parseOcel2Json(validJsonString);
    assertThat(log.events()).hasSize(1000);
}
```

**Layer 3 (API)**: Test business logic
```java
@Test
void testConformanceReturnsValidScore() throws ProcessMiningException {
    double score = engine.checkConformance(log, model);
    assertThat(score).isBetween(0.0, 1.0);
}
```

### 10.2 Performance Tests

```java
@Test
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public void benchmarkZeroCopyPointerAccess() {
    // <1µs expected
}

@Test
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public void benchmarkAutocleanupParsing() {
    // <100µs expected for typical logs
}
```

### 10.3 Concurrency Tests

```java
@Test
void testConcurrentParsingWith10VirtualThreads() throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    List<Future<OcelLog>> futures = new ArrayList<>();

    for (int i = 0; i < 100; i++) {
        futures.add(executor.submit(() ->
            parser.parseOcel2Json(testData)));
    }

    for (Future<OcelLog> f : futures) {
        assertThat(f.get()).isNotNull();
    }
}
```

---

## 11. Delivery Artifacts

### 11.1 Source Code

```
src/main/java/org/yawlfoundation/yawl/
├── graalwasm/
│   ├── WasmContextPool.java              [Context pool, virtual threads]
│   └── Rust4pmWasmBridge.java            [Layer 1: Raw WASM]
│
└── integration/processmining/rust4pm/
    ├── Rust4pmEngine.java                [Singleton, engine lifecycle]
    ├── Rust4pmOcelParser.java            [Layer 2: Typed bridge]
    ├── model/
    │   ├── OcelLog.java
    │   ├── OcelEvent.java
    │   ├── OcelObject.java
    │   ├── OcelValue.java
    │   └── OcelPointer.java
    ├── error/
    │   ├── ProcessMiningException.java
    │   ├── WasmExecutionException.java
    │   └── JsonParseException.java
    └── package-info.java                 [Comprehensive usage guide]
```

### 11.2 WASM Binary

```
src/main/resources/wasm/
└── process_mining_wasm_bg.wasm          [Rust4PM compiled to WASM]
```

### 11.3 Documentation

- `graalwasm-rust-binding-pattern.md` — This PRD
- `Rust4pmEngine-usage-guide.md` — API reference with examples
- Inline JavaDoc with cross-references to Rust docs
- Performance benchmark report

### 11.4 Tests

```
test/java/org/yawlfoundation/yawl/integration/processmining/rust4pm/
├── Rust4pmWasmBridgeTest.java           [Layer 1 unit tests]
├── Rust4pmOcelParserTest.java           [Layer 2 unit tests]
├── Rust4pmEngineTest.java               [Layer 3 integration tests]
├── Rust4pmConcurrencyTest.java          [Virtual thread tests]
└── Rust4pmBenchmark.java                [JMH benchmarks]
```

---

## 12. Success Criteria

- [ ] **API Coverage**: 100% of Rust4PM public functions exposed in Java
- [ ] **Type Safety**: All Rust types mapped to Java sealed/records with no casting
- [ ] **Performance**: <1µs pointer ops, <100µs marshalling, >10K logs/sec throughput
- [ ] **Concurrency**: 10+ virtual threads without context leaks
- [ ] **Documentation**: Every Java method links to corresponding Rust docs
- [ ] **Testing**: >95% code coverage, no flaky tests
- [ ] **Error Handling**: All Rust errors propagate as Java exceptions
- [ ] **Memory Safety**: Zero WASM memory leaks under stress test

---

## 13. Open Questions

1. **WASM Binary Source**: Where does the `.wasm` file come from? (npm, custom build, fork?)
2. **Rust4PM Version**: Which version of Rust4PM? (latest on crates.io or custom fork?)
3. **Serialization Format**: JSON/XML for complex types, or raw pointer-based access?
4. **Memory Limits**: Max WASM linear memory per context? (default 1GB)
5. **Multi-Module Support**: Single .wasm file or multiple modules (one per feature)?
6. **Java 25 Features**: Use preview features (virtual threads are final in Java 25)?

---

## 14. Timeline & Next Steps

**Phase 1: Design & Validation** (Current)
- [x] Write PRD
- [ ] Review with stakeholders
- [ ] Clarify open questions

**Phase 2: Implementation** (Next context window)
- [ ] Build Rust4pmWasmBridge (Layer 1)
- [ ] Build Rust4pmOcelParser (Layer 2)
- [ ] Create OcelLog/OcelEvent records
- [ ] Implement error handling

**Phase 3: Optimization & Testing** (Following context window)
- [ ] Concurrency tests with virtual threads
- [ ] Performance benchmarks (JMH)
- [ ] Memory leak detection (JFR)
- [ ] Documentation & examples

**Phase 4: Integration** (Final)
- [ ] Wire into YAWL process mining stack
- [ ] End-to-end testing
- [ ] Production validation

---

## Appendix: Glossary

| Term | Definition |
|------|-----------|
| **WASM** | WebAssembly — portable binary instruction format |
| **wasm-bindgen** | Rust tool for generating WASM-JavaScript bindings |
| **GraalVM** | Polyglot VM running Java, JavaScript, Python, WASM, etc. |
| **Polyglot** | Multiple languages running in same runtime |
| **Value** | GraalVM's type-agnostic result object |
| **Zero-copy** | Data accessed by pointer without copying to Java heap |
| **Auto-cleanup** | WASM memory automatically freed after function call |
| **Sealed class** | Java 15+ type restricting implementations (type-safe enum) |
| **Record** | Java 14+ immutable data class with generated boilerplate |
| **Virtual Thread** | Java 19+ lightweight thread (Project Loom) |

---

**End of PRD**
