# Explanation: Data Modelling as a WASM Facade

**Why is data-modelling a thin Java layer?** The **data-modelling-sdk** (Rust) is the source of truth. Rather than reimplement all 70+ schema operations in Java, yawl-data-modelling wraps them with a typed facade, delegating computation to GraalVM.

---

## The Problem: Distributed Schema Logic

Before data-modelling, YAWL had schema operations scattered across multiple places:

- **Java**: Some validation logic
- **Database**: SQL constraints
- **Configuration files**: Type mappings
- **Scripts**: Data transformations

This fragmentation caused:

- **Inconsistency**: Same rule implemented differently in Java vs SQL
- **Maintenance burden**: Fix a bug in one place, miss others
- **Slow iteration**: Cross-language debugging
- **Platform lock-in**: Hard to use same logic in browser (JS) or Rust tools

---

## The Solution: Single Source of Truth in Rust/WASM

The **data-modelling-sdk** is a Rust library that provides all schema operations:

```
┌─────────────────────────────────────────────────────────┐
│        data-modelling-sdk (Rust, v2.3.0)               │
├─────────────────────────────────────────────────────────┤
│ • 70+ schema operations (import/export, validation)     │
│ • ODCS, OpenAPI, BPMN, DMN support                      │
│ • Compiled to WebAssembly (WASM)                        │
│ • No external dependencies                              │
│ • ~500KB binary                                         │
└─────────────────────────────────────────────────────────┘
         ↓ (WebAssembly boundary)
┌─────────────────────────────────────────────────────────┐
│   yawl-data-modelling (Java facade)                    │
├─────────────────────────────────────────────────────────┤
│ • TypedmethodSignatures                                │
│ • DataModellingBridge (entry point)                    │
│ • Resource pooling (GraalVM contexts)                  │
│ • Exception handling                                    │
└─────────────────────────────────────────────────────────┘
         ↓ (Java interface)
┌─────────────────────────────────────────────────────────┐
│        YAWL Applications                                 │
│        (DMN, ggen, Task Handlers)                       │
└─────────────────────────────────────────────────────────┘
```

---

## Architecture: Layered Design

### Layer 1: WASM Module (data-modelling-sdk)

Written in Rust, compiled to WebAssembly:

```rust
// data-modelling-sdk/src/lib.rs (simplified)

pub fn parse_odcs_to_workspace(yaml: &str) -> String {
    // Parse ODCS YAML
    let model = parse_yaml(yaml)?;

    // Validate model
    validate_model(&model)?;

    // Convert to workspace JSON
    let workspace = model_to_workspace(&model);
    serde_json::to_string(&workspace)
}

pub fn validate_workspace(json: &str) -> String {
    let workspace = serde_json::from_str(json)?;
    let mut errors = Vec::new();

    // Check referential integrity
    for table in &workspace.tables {
        for fk in &table.foreign_keys {
            if !workspace.has_table(&fk.references_table) {
                errors.push(format!(
                    "Foreign key references non-existent table: {}",
                    fk.references_table
                ));
            }
        }
    }

    // Check validation rules
    for rule in &workspace.rules {
        validate_rule(rule, &workspace, &mut errors);
    }

    ValidationResult {
        valid: errors.is_empty(),
        errors,
    }.to_json()
}

pub fn import_sql(ddl: &str) -> String {
    // Parse SQL DDL
    let schema = parse_sql_ddl(ddl)?;

    // Convert to workspace
    let workspace = schema_to_workspace(&schema);
    serde_json::to_string(&workspace)
}

// ... 66 more functions
```

When compiled to WASM:

```bash
$ wasm-pack build --target nodejs data-modelling-sdk
Generated: data_modelling_wasm_bg.wasm (~500 KB)
Generated: data_modelling_wasm.js (wrapper)
```

The WASM module exposes functions as **exports** that can be called from JavaScript or Java.

### Layer 2: Java Facade (yawl-data-modelling)

The Java layer is a **1:1 mapping** of WASM exports to typed methods:

```java
public class DataModellingBridge {
    private final WasmModule module;          // GraalVM WASM module
    private final WasmContextPool pool;       // Thread-safe context pooling

    public String parseOdcsToWorkspace(String odcsYaml) {
        try (var context = pool.borrowContext()) {
            // Call WASM export
            Value result = context.callFunction(
                "parse_odcs_to_workspace",
                odcsYaml
            );
            return result.asString();
        }
    }

    public String validateWorkspace(String workspace) {
        try (var context = pool.borrowContext()) {
            // Call WASM export
            Value result = context.callFunction(
                "validate_workspace",
                workspace
            );
            return result.asString();
        }
    }

    // ... 68 more methods, each calling a WASM export
}
```

**No logic is reimplemented**. Every method is a thin wrapper.

### Layer 3: YAWL Applications

Applications use the Java facade:

```java
// In a YAWL task handler
try (DataModellingBridge bridge = new DataModellingBridge()) {
    String workspace = bridge.parseOdcsToWorkspace(odcsYaml);
    String validation = bridge.validateWorkspace(workspace);
    // ...
}
```

---

## Key Design Principles

### 1. Single Responsibility

- **WASM**: All schema logic
- **Java facade**: Type safety + pooling + error handling
- **Applications**: Business logic using schema operations

### 2. Language-Agnostic Implementation

The same WASM module can be used from:

- **Java** (via yawl-data-modelling)
- **JavaScript** (browser via data_modelling_wasm.js)
- **Rust** (direct FFI)
- **Python** (via GraalVM polyglot)

### 3. Zero Reimplementation

All schema operations are defined once in Rust. No Java code duplicates logic.

### 4. Resource Pooling

WASM execution contexts are expensive. Pool and reuse them:

```java
public class WasmContextPool {
    private final BlockingQueue<WasmContext> pool;

    public WasmContext borrowContext() {
        return pool.take();  // Blocks if pool empty
    }

    public void returnContext(WasmContext context) {
        pool.put(context);   // Make available for reuse
    }
}
```

Typical pool size: 4-8 contexts for 10K operations/sec throughput.

---

## Data Flow: Example

### Scenario: Import SQL Schema into YAWL

```
Step 1: User provides SQL DDL
┌────────────────────────────────────┐
│ CREATE TABLE users (                │
│   id INT PRIMARY KEY,               │
│   name VARCHAR(255) NOT NULL        │
│ )                                   │
└────────────────────────────────────┘

                ↓

Step 2: Java facade calls WASM
┌────────────────────────────────────┐
│ bridge.importSql(sql)               │
│   → WasmContext.callFunction(       │
│       "import_sql", sql)            │
└────────────────────────────────────┘

                ↓

Step 3: WASM processes (Rust)
┌────────────────────────────────────┐
│ fn import_sql(ddl: &str) {          │
│   parse_sql_ddl(ddl)                │
│   schema_to_workspace(...)          │
│   serde_json::to_string(...)        │
│ }                                   │
└────────────────────────────────────┘

                ↓

Step 4: Workspace JSON returned to Java
┌────────────────────────────────────┐
│ {                                   │
│   "tables": [{                      │
│     "name": "users",                │
│     "columns": [                    │
│       {"name": "id", "type": "integer"} │
│     ]                               │
│   }]                                │
│ }                                   │
└────────────────────────────────────┘

                ↓

Step 5: Application uses workspace
┌────────────────────────────────────┐
│ String validation =                 │
│   bridge.validateWorkspace(w);      │
│ if (valid) {                        │
│   String yaml =                     │
│     bridge.exportToOdcs(w);         │
│ }                                   │
└────────────────────────────────────┘
```

---

## Performance Analysis

### Execution Overhead

| Operation | Time | Bottleneck |
|-----------|------|-----------|
| Context borrow | <1ms | Queue lookup |
| WASM invocation | 1-5ms | GraalVM polyglot bridge |
| Rust computation | varies | Algorithm (usually <10ms) |
| **Total per operation** | **2-15ms** | WASM bridge |

### Throughput

- **Sequential**: ~100 operations/sec
- **With pooling (4 contexts)**: ~400 operations/sec
- **With pooling (8 contexts)**: ~800 operations/sec

### Memory

- **Per context**: ~50MB (WASM module + JIT)
- **8 contexts**: ~400MB baseline
- **Workspace JSON**: typically 10-100KB

---

## Comparison: Facade vs Reimplementation

| Aspect | Facade (current) | Full Java Reimpl |
|--------|------------------|------------------|
| **Lines of code** | ~2000 (facade only) | ~50,000 (full logic) |
| **Maintenance burden** | Low (sync with Rust) | High (parallel logic) |
| **Testing** | Test via WASM | Test full Java impl |
| **Performance** | 2-15ms/op (WASM) | <1ms/op (JVM) |
| **Bug consistency** | Single source of truth | May diverge |
| **Deployment** | 1 WASM binary | Large JAR |
| **Browser support** | Yes (JS wrapper) | No (Java-only) |

---

## Integration Patterns

### Pattern 1: Validation Middleware

```java
public class DataValidationMiddleware {
    private final DataModellingBridge bridge;

    public void validateWorkItem(YWorkItem item) {
        String data = item.getDataVariable("customer");
        String workspace = bridge.getDomain("sales");
        String result = bridge.validateData(data, workspace, "customers");

        if (!isValid(result)) {
            item.setErrorData(extractErrors(result));
            // Route to error handler
        }
    }
}
```

### Pattern 2: DMN + Schema Validation

```java
// Combine data model validation with DMN decisions
String workspace = bridge.parseOdcsToWorkspace(odcsYaml);
String dmn = bridge.exportToDmn(workspace);

DmnDecisionService svc = new DmnDecisionService(
    DataModel.fromOdcs(odcsYaml)
);
// Now all DMN inputs are schema-validated
```

### Pattern 3: API Contract Generation

```java
// Generate OpenAPI from data model
String workspace = bridge.parseOdcsToWorkspace(odcsYaml);
String openApi = bridge.exportToOpenApi(workspace);

// Serve as part of REST API
app.get("/api/spec", (req, res) -> res.send(openApi));
```

---

## When WASM Boundary is Worth It

**Worth the 2-15ms overhead:**

- Complex schema logic (>100 lines)
- Multi-format support (ODCS + OpenAPI + BPMN)
- Need for single source of truth across languages
- Browser compatibility

**Not worth it:**

- Simple type validation (<10 lines)
- Java-only use case with <1ms latency requirement
- Lightweight schemas (small workspace)

For most YAWL workflows, the WASM overhead is negligible (2-15ms << task execution time, which is usually 100ms+).

---

## Future Evolution

### Potential Optimizations

1. **Binary protocol**: Replace JSON with Protocol Buffers for less serialization overhead
2. **Streaming**: For large schemas, stream workspace JSON instead of buffering
3. **Compilation**: Ahead-of-time compile WASM to native code (Wasmtime)
4. **Caching**: Memoize frequently-accessed workspaces

### Expansion

The same pattern could be applied to other domains:

- **Process mining** (Rust4pmBridge)
- **DMN evaluation** (dmn_feel_engine.wasm)
- **Compliance checking** (audit rules engine)

---

## Next Steps

- [Tutorial: Data Modelling Getting Started](../tutorials/11-data-modelling-bridge.md)
- [Reference: DataModellingBridge API](../reference/data-modelling-api.md)
- [How-To: Validate Data Models](../how-to/data-modelling-schema-validation.md)
- [SDK Source: data-modelling-sdk](https://github.com/OffeneDatenmodellierung/data-modelling-sdk)
