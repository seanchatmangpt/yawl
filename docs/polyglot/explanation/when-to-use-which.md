# Choosing Between GraalPy, GraalJS, and GraalWasm

## The Decision in One Sentence

Use **GraalPy** when your team writes Python and needs the scientific ecosystem. Use **GraalJS** when you need JavaScript semantics, the fastest startup, or wasm-bindgen interop. Use **GraalWasm** when you need near-native performance from a compiled binary or are integrating the `Rust4pmBridge` for OCEL2 process mining.

## Decision Tree

```
What language is your script or library written in?
│
├── Python
│   └── → yawl-graalpy (PythonExecutionEngine)
│
├── JavaScript / TypeScript (transpiled)
│   └── → yawl-graaljs (JavaScriptExecutionEngine)
│
├── Rust / C / Go compiled to WASM
│   ├── Uses wasm-bindgen / wasm-pack?
│   │   └── → yawl-graalwasm via JavaScriptSandboxConfig.forWasm()
│   │         (or Rust4pmBridge if using @aarkue/process_mining_wasm)
│   └── Pure WASM exports (integers, floats)?
│       └── → yawl-graalwasm WasmExecutionEngine
│
└── OCEL2 / process mining data?
    └── → yawl-graalwasm Rust4pmBridge.processOcel2()
```

## Use GraalPy When

**Your logic is already Python.** If process mining scripts exist in Python — using PM4Py, pandas, numpy, or scipy — GraalPy executes them in-process without spawning a Python subprocess or serializing data over IPC.

**You need pip-managed packages.** `PythonVirtualEnvironment` wraps a real Python virtual environment. You can install packages via pip and import them from GraalPy scripts. No other YAWL polyglot module supports package managers.

**Script iteration speed matters more than cold-start latency.** Python scripts are interpreted; changes take effect immediately without recompilation. GraalPy has the highest JVM warmup cost (~100–500ms for the first evaluation) but no Ahead-Of-Time compilation step.

**Your team knows Python, not JavaScript.** Business analysts and data scientists often prefer Python. GraalPy lets them write workflow logic in the language they know, while Java engineers consume results through the typed `eval*` API.

## Use GraalJS When

**Routing rules are written by analysts who know JavaScript.** ES2022 syntax — arrow functions, destructuring, async/await, template literals — is fully supported. Rules can be loaded from `.js` files at runtime without restarting the workflow engine.

**You need the lowest per-call overhead.** GraalJS contexts initialise faster than GraalPy contexts and handle lightweight computations (string manipulation, number scoring, JSON transformation) with lower latency. For workflows executing thousands of rule evaluations per second, the difference is measurable.

**You need to share logic with a frontend team.** The same JavaScript module that runs in the browser can run in GraalJS — same business rules, validated in both environments.

**You need wasm-bindgen interop.** `JavaScriptSandboxConfig.forWasm()` creates a combined JS+WASM context. If you have a Rust library compiled with wasm-bindgen, GraalJS is the host — not GraalPy. GraalPy does not support WASM interop.

**You want async-compatible code.** GraalJS handles `Promise` and `async`/`await` in its execution model. GraalPy requires GraalVM's Python asyncio integration, which is more constrained.

## Use GraalWasm When

**You need near-native performance.** Rust compiled to WASM runs at ~80–95% of native Rust speed inside GraalWasm, far above what GraalPy or GraalJS achieve for compute-intensive algorithms. For FFT, matrix operations, or graph traversal on large datasets, WASM is the right choice.

**You are integrating `Rust4pmBridge`.** `Rust4pmBridge.processOcel2()` is the direct path to OCEL2 process mining: directly-follows graphs, variant analysis, activity statistics, all computed in Rust-speed WASM. This is the only module with this capability.

**You have a compiled binary from any language.** WASM is a compilation target for Rust, C, C++, Go, AssemblyScript, and others. Any module that compiles to WASM can be loaded by `WasmExecutionEngine`, regardless of the source language.

**You need strict memory isolation.** WASM modules run in a sandboxed linear memory space. By default, `WasmSandboxConfig.pureWasm()` prohibits file I/O, network access, and host function calls. This is the strictest isolation available across all three modules.

**You want deterministic execution.** A pure WASM module has no side effects unless you explicitly export them. Given the same inputs, it produces the same outputs. This property is useful for audit trails and reproducible process mining results.

## Performance Characteristics

| Characteristic | GraalPy | GraalJS | GraalWasm |
|----------------|---------|---------|-----------|
| **JVM warmup** | High (200–500ms) | Low (20–100ms) | Low (10–50ms per module) |
| **Context memory** | ~50–200MB per context | ~5–20MB per context | ~1–5MB per module |
| **Compute throughput** | Moderate (CPython parity) | Moderate (V8 parity) | High (near-native) |
| **String handling** | Native Python strings | Native JS strings | Via wasm-bindgen or byte arrays |
| **Pooling strategy** | Pool contexts (expensive to create) | Pool contexts (expensive to create) | Create per-call (cheap) |
| **Package ecosystem** | pip (full Python ecosystem) | npm not supported in-process | Compiled into WASM binary |

## Combining Modules in a Single Workflow

The three modules compose. A single YAWL workflow case can:

1. Use **GraalJS** to evaluate a routing rule (low latency, fast decision)
2. Pass the selected case variant to **GraalPy** for statistical analysis (Python ecosystem)
3. Feed the event log to **Rust4pmBridge** via **GraalWasm** for DFG computation (near-native speed)

Each engine is independent: a `PythonExecutionEngine` and a `JavaScriptExecutionEngine` can run concurrently, sharing no GraalVM state. Results flow between them as Java types (`String`, `Map<String, Object>`, `List<Object>`), not as polyglot `Value` objects.

## When to Start with GraalJS

If you are unsure which module to use, start with GraalJS. It has:
- The lowest overhead per call
- The most familiar language for most engineers
- Support for JSON in/out natively
- A clear upgrade path to GraalWasm if performance becomes a bottleneck

You can always add `PythonExecutionEngine` or `Rust4pmBridge` later for specific tasks without changing the rest of your workflow logic.
