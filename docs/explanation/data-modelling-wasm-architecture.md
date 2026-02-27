# The Data Modelling WASM Architecture

**Quadrant**: Explanation | **Concept**: GraalJS+WASM polyglot, wasm-bindgen ES modules, JSON pass-through design

This document explains why `yawl-data-modelling` is built the way it is. Understanding this helps you reason about performance, failure modes, and when (and when not) to extend the Java layer.

---

## The Core Decision: Don't Reimplement the SDK

The data-modelling-sdk (Rust, MIT, github.com/OffeneDatenmodellierung) provides 70+ schema operations — ODCS YAML parsing, SQL import for five dialects, Avro, JSON Schema, Protobuf, BPMN, DMN, and OpenAPI import-export, decision records, knowledge base, sketches, and validation.

The alternative to WASM is reimplementing all of this in Java. That is:

- **Months of work** to match the SDK's coverage and correctness
- **Permanent maintenance burden** as the Rust SDK evolves
- **Two diverging implementations** that will produce different results on edge cases
- **No benefit** — the SDK is already correct, well-tested, and licensed for reuse

The decision was therefore: expose the SDK's WASM exports as Java methods, pass strings through, and return strings. No logic in Java.

---

## What "WASM-first" Means

Every method in `DataModellingBridge` calls one WASM-exported function and returns its output unchanged:

```
Java caller
    → DataModellingBridge.parseOdcsYaml(yaml)
        → GraalJS eval("parse_odcs_yaml(arg0)")
            → data_modelling_wasm_bg.wasm exports::parse_odcs_yaml
                → Rust implementation
                    → JSON string result
        ← JavaScript string
    ← Java String (identical bytes)
```

The Java layer adds:
1. A connection pool (`JavaScriptContextPool`) to support concurrent callers
2. Safe argument passing that avoids JS injection (named bindings, not string interpolation)
3. Null-safety annotations and typed parameters so callers don't have to read the SDK docs
4. Exception wrapping into `DataModellingException` with error kind classification

The Java layer does **not** add:
- YAML parsing logic
- SQL dialect handling
- Circular dependency detection
- Name validation rules
- Any business logic whatsoever

---

## The GraalJS+WASM Polyglot Context

GraalVM's polyglot API allows a single `Context` to evaluate both JavaScript and WebAssembly. The SDK's compiled output uses **wasm-bindgen** — a Rust tool that generates a JavaScript/TypeScript glue layer over a WASM binary. The glue:

1. Defines ES module exports (functions that wrap WASM memory operations)
2. Handles UTF-8 string marshalling between JS heap and WASM linear memory
3. Sets up the `TextDecoder`/`TextEncoder` Web APIs for string conversion

In a browser environment, wasm-bindgen glue runs natively because browsers provide these Web APIs. In GraalJS, they must be polyfilled. `DataModellingBridge` installs minimal JS implementations of `TextDecoder`, `TextEncoder`, and `crypto.getRandomValues` before loading the glue module.

### Why ES module eval?

The wasm-bindgen glue is an **ES module** (it uses `import`/`export` syntax). GraalJS supports ES modules but requires a specific option to make `ctx.eval(esModule)` return the module namespace object (instead of `undefined`):

```java
builder.option("js.esm-eval-returns-exports", "true");
```

Without this, `moduleExports.getMember("initSync")` throws `UnsupportedOperation: Value.getMember(String) for 'undefined'`. This was a discovered bug fixed in `JavaScriptSandboxConfig.applyTo()`.

### Why host file I/O?

GraalJS resolves ES module URIs against the filesystem when loading from a file path. WASM loading requires reading the `.wasm` binary from disk via `WebAssembly.Module(bytes)`. Both require:

```java
builder.allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build());
```

The WASM binary is extracted from the classpath to a temp directory at bridge construction time, so the GraalJS context can reach it via a filesystem path.

---

## Initialisation Sequence

When `DataModellingBridge` is constructed, it performs this one-time setup per pool slot:

```
1. Create temp dir  →  extract data_modelling_wasm.js + data_modelling_wasm_bg.wasm
2. Build GraalJS context  →  js + wasm languages, STANDARD sandbox, IOAccess, esm-eval-returns-exports
3. Eval polyfills  →  TextDecoder, TextEncoder, crypto.getRandomValues
4. Eval glue module  →  load data_modelling_wasm.js as ES module, get namespace object
5. Get initSync  →  namespace.getMember("initSync")
6. Load WASM bytes  →  Files.readAllBytes(wasmPath)
7. WebAssembly.Module  →  instantiate from bytes in JS context
8. initSync(module)  →  Rust init code runs, WASM exports become callable
```

After step 8, each WASM function (`parse_odcs_yaml`, `import_from_sql`, etc.) is a JS global that the bridge calls by name.

---

## Argument Passing Without Injection

A naive implementation would do:

```java
// UNSAFE — string injection possible
ctx.eval("js", "parse_odcs_yaml('" + yaml + "')");
```

`DataModellingBridge.call()` uses named bindings instead:

```java
ctx.getBindings("js").putMember("__dm_arg0__", yaml);
ctx.eval("js", "parse_odcs_yaml(__dm_arg0__)");
// cleanup: removeMember("__dm_arg0__")
```

This prevents any YAML content from being interpreted as JavaScript. The `__dm_arg` prefix is distinctive enough to avoid collision with SDK globals.

---

## JSON Pass-Through Design

Every method returns a raw JSON string. This is deliberate:

- **Composability**: The output of one method is a valid input to the next. `parseOdcsYaml()` → `exportOdcsYamlV2()` is a valid round-trip without parsing.
- **No overhead**: Java code never deserialises intermediate results. If you call five methods in sequence, five JSON strings pass through without any object allocation on the Java heap.
- **SDK compatibility**: As the SDK evolves and adds new JSON fields, the Java layer transparently forwards them without a schema change.
- **Caller choice**: Callers who need typed access use their preferred JSON library (Jackson, Gson, `org.json`). The bridge doesn't impose one.

The trade-off is that callers must parse JSON to extract individual values. For most use cases this is one `mapper.readTree(result).path("tables").size()` call.

---

## Context Pool

`JavaScriptContextPool` (from `yawl-graaljs`) manages a pool of reusable `JavaScriptExecutionContext` instances. Each context holds a live GraalJS+WASM context with the WASM module already initialised.

The default pool size is 1. This means all calls are serialised through a single context — which is correct because GraalJS contexts are not thread-safe.

For concurrent workloads (e.g., a web service processing multiple requests simultaneously), construct with `new DataModellingBridge(N)` where N matches your expected concurrency. Each slot uses ~50 MB for WASM module initialisation.

---

## Failure Modes

| Failure | Cause | `ErrorKind` |
|---------|-------|-------------|
| Bridge constructor throws | `wasm/` resources missing from classpath | `MODULE_LOAD_ERROR` |
| Bridge constructor throws | GraalVM JDK not in use (Temurin) | `MODULE_LOAD_ERROR` |
| Method call throws | WASM function panic / FEEL parse error | `EXECUTION_ERROR` |
| Method returns `""` | SDK returned null | (no exception; returns empty string) |
| Method call hangs | Pool exhausted, all slots busy | (caller blocks in pool.execute()) |

Tests that must run on non-GraalVM JDKs (Temurin CI) should wrap bridge construction in try-catch and use `assumeTrue(bridge != null)` to skip WASM-dependent tests gracefully.

---

## Temp Directory Lifecycle

Resource extraction creates a temp directory under the system temp root:

```
/tmp/yawl-data-modelling-<random>/
├── data_modelling_wasm.js
└── data_modelling_wasm_bg.wasm
```

The directory is created at construction and deleted at `close()` via `Files.walk()` + `Files.delete()`. It is cleaned up best-effort — if the JVM is killed without `close()` being called, the OS cleans up on reboot or next cleanup run.

---

## What the Java Layer Is NOT

- It is not a schema registry
- It is not an ORM
- It is not a query planner
- It is not a streaming processor
- It does not cache results
- It does not validate inputs before passing them to WASM (the SDK validates)

If any of these capabilities are needed, build a service on top of `DataModellingBridge` rather than extending the bridge itself.
