# WebAssembly Binding Strategies: Why Rust4pmBridge Uses a JS Engine

## The Two Faces of WebAssembly in the JVM

WebAssembly (WASM) modules come in two fundamentally different shapes, and understanding the difference explains nearly every architectural decision in `yawl-graalwasm`.

**Pure WASM modules** export functions directly. Calling `add(3, 4)` from Java means writing three integers into WASM linear memory, invoking the export, and reading one integer back. GraalWasm handles this transparently. `WasmExecutionEngine` is all you need.

**wasm-bindgen modules** are different. `wasm-bindgen` is a Rust crate that generates a JavaScript glue layer alongside the WASM binary. This glue manages memory allocation (strings and complex objects cross the WASM boundary as pointers, not values), type coercion (JavaScript `string` ↔ Rust `&str`), and error propagation. The WASM binary alone is useless — it requires the generated JavaScript host to function.

`@aarkue/process_mining_wasm` — the npm package powering `Rust4pmBridge` — is compiled with wasm-bindgen. It cannot be loaded as a pure WASM module.

## Why `WasmExecutionEngine` Alone Cannot Run wasm-bindgen Modules

`WasmExecutionEngine` creates a GraalWasm context that speaks only WASM. When you call `loadModuleFromClasspath("wasm/compute.wasm", "compute")`, the context initialises the WASM memory model and maps exports. This works for any module whose WASM ABI is self-contained integers and floats.

Attempting to load a wasm-bindgen module directly into a pure WASM context fails at instantiation: the WASM binary declares JavaScript `import` bindings (e.g., `__wbindgen_string_new`, `__wbindgen_object_drop_ref`) that have no implementations in a WASM-only context. GraalWasm will throw a `WasmException` with kind `MODULE_LOAD_ERROR` because the required import table entries are missing.

## How `JavaScriptSandboxConfig.forWasm()` Solves This

`JavaScriptSandboxConfig.forWasm()` creates a combined GraalVM polyglot context that contains both a GraalJS interpreter and a GraalWasm interpreter sharing the same isolate. The JavaScript engine provides the host environment that wasm-bindgen expects:

```
GraalVM Isolate
├── GraalJS interpreter        (hosts wasm-bindgen glue — ESM module)
│   └── process_mining_wasm.js (generated glue: string encoding, memory mgmt)
└── GraalWasm interpreter      (executes native Rust logic at near-native speed)
    └── process_mining_wasm.wasm (Rust algorithms: DFG, variants, activity stats)
```

The JavaScript layer is thin — it exists only to satisfy the import bindings wasm-bindgen generated. All computationally intensive work runs in WASM.

## The ESM Loading Pattern

wasm-bindgen emits an ES Module (ESM). Loading it requires two GraalVM options:

```java
// Applied automatically inside JavaScriptSandboxConfig.forWasm()
context.option("js.esm-eval-returns-exports", "true");
source = Source.newBuilder("js", glueContent, "process_mining_wasm.js")
    .mimeType("application/javascript+module")
    .build();
```

Without `mimeType("application/javascript+module")`, GraalJS treats the file as a CommonJS script, which cannot use `import`/`export` syntax. Without `esm-eval-returns-exports=true`, the exports object is not accessible after evaluation.

## Trade-offs: Pure WASM vs. wasm-bindgen

| Concern | Pure WASM (`WasmExecutionEngine`) | wasm-bindgen (`Rust4pmBridge` / `forWasm`) |
|---------|-----------------------------------|---------------------------------------------|
| Classpath deps | `wasm` language only | `js` + `wasm` both required |
| Module startup | Fastest (WASM init only) | Slower (JS engine + WASM init) |
| String handling | Manual (byte arrays) | Automatic (JS encodes/decodes) |
| Complex types | Not supported | Supported (structs, Vecs, enums via JS) |
| Memory management | Manual (WASM linear memory) | Automatic (wasm-bindgen allocator) |
| Use when | Integer/float math, simple exports | Rust libraries using wasm-bindgen/wasm-pack |

## WASI: A Third Option

WASI (WebAssembly System Interface) is an alternative to wasm-bindgen for system-level WASM modules. Where wasm-bindgen generates a JavaScript host, WASI defines a standardised system call interface (file I/O, clocks, random) that a WASM host runtime implements directly.

GraalWasm has limited WASI support. For `yawl-graalwasm`'s primary use case — process mining analytics compiled from Rust — wasm-bindgen is the appropriate approach because `@aarkue/process_mining_wasm` was built for a browser/Node.js host, not a WASI host.

If you compile your own Rust to WASM targeting WASI (`--target wasm32-wasi`), `WasmExecutionEngine` with `WasmSandboxConfig.pureWasm()` is sufficient; no JavaScript engine is needed.

## Bundling Strategy for `Rust4pmBridge`

`Rust4pmBridge` bundles both the WASM binary and its JavaScript glue as classpath resources inside the `yawl-graalwasm` JAR:

```
yawl-graalwasm.jar
└── wasm/
    └── process_mining_wasm.js   (ESM glue — loads the WASM binary inline as base64)
```

The `.js` glue file includes the WASM binary inlined as a base64 data URL. This means a single resource path is sufficient; there is no separate `.wasm` file to bundle. `Rust4pmBridge.processOcel2()` loads this single resource and initialises the combined JS+WASM context.

## Choosing the Right Approach for Your WASM Module

```
Does your WASM module use wasm-bindgen?
├── YES (wasm-pack / cargo wasm-bindgen)
│   └── Use JavaScriptSandboxConfig.forWasm()
│       Need: both js + wasm language on classpath
│       Load: via JavaScript Source with mimeType="application/javascript+module"
│
├── NO (pure WASM exports — integers, floats, byte arrays)
│   └── Use WasmExecutionEngine with WasmSandboxConfig.pureWasm()
│       Need: wasm language only on classpath
│       Load: via WasmExecutionEngine.loadModuleFromClasspath()
│
└── WASI target (--target wasm32-wasi)
    └── Use WasmExecutionEngine (WASI preview1 imports available)
        Need: wasm language only on classpath
        Load: via WasmExecutionEngine.loadModuleFromClasspath()
```

The `Rust4pmBridge` API exists precisely to hide this complexity: callers do not need to understand ESM loading, wasm-bindgen ABI, or combined contexts. They call `Rust4pmBridge.processOcel2(json)` and receive results.
