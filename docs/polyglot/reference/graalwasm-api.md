# GraalWasm API Reference

Complete reference for `org.yawlfoundation.yawl.graalwasm` module (YAWL 6.0.0-GA).

## WasmExecutionEngine

Main entry point for WebAssembly module execution.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `builder()` | `WasmExecutionEngine.Builder` | Static factory for creating engines | — |
| `loadModuleFromClasspath(String resourcePath, String moduleName)` | `WasmModule` | Load .wasm binary from classpath (e.g., "wasm/my.wasm") | `WasmException` |
| `loadModuleFromPath(Path wasmPath, String moduleName)` | `WasmModule` | Load .wasm binary from filesystem | `WasmException` |
| `getSandboxConfig()` | `WasmSandboxConfig` | Get current sandbox configuration | — |
| `close()` | `void` | Shutdown engine, clear binary cache, close all loaded modules | `IOException` |

**Builder Options**:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `sandboxConfig(WasmSandboxConfig)` | WasmSandboxConfig | `pureWasm()` | Sandbox restrictions (pureWasm or withJs) |

**Example**:
```java
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();
```

---

## WasmModule

Represents a loaded WebAssembly module. **AutoCloseable; must be closed.**

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `execute(String functionName, Object... args)` | `Value` | Call WASM-exported function by name | `WasmException` |
| `getContext()` | `org.graalvm.polyglot.Context` | Access raw GraalVM Context (advanced) | — |
| `close()` | `void` | Release underlying GraalVM Context; mandatory cleanup | `IOException` |

**CRITICAL**: WasmModule.close() MUST be called. Use try-with-resources:

```java
try (WasmModule module = engine.loadModuleFromClasspath("wasm/math.wasm", "math")) {
    Value result = module.execute("add", 5, 3);
    int sum = result.asInt();
}  // close() called automatically
```

**Example**:
```java
WasmModule module = engine.loadModuleFromClasspath("wasm/digest.wasm", "crypto");
try (module) {
    // sha256 is a WASM-exported function
    Value hash = module.execute("sha256", "hello world");
    byte[] hashBytes = hash.as(byte[].class);
}
```

---

## WasmBinaryCache

Caches compiled .wasm binaries to avoid repeated parsing.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `loadFromClasspath(String resourcePath)` | `Source` | Load .wasm from classpath and cache | `WasmException` |
| `loadFromPath(Path wasmPath)` | `Source` | Load .wasm from filesystem and cache | `WasmException` |
| `clear()` | `void` | Clear all cached binaries (do not call while modules are executing) | — |
| `size()` | `int` | Number of cached binary entries | — |

**Note**: Cache is managed automatically by WasmExecutionEngine. Usually no need to interact directly.

---

## WasmSandboxConfig

Predefined sandbox configurations for WASM execution isolation.

| Static Factory | Description | Use Case |
|---|---|---|
| `pureWasm()` | WASM-only, no JavaScript interop | Standalone WASM modules (math, crypto, compression) |
| `withJs()` | WASM + JavaScript (enables wasm-bindgen glue code) | wasm-bindgen modules like @aarkue/process_mining_wasm |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `isWasmOnly()` | boolean | True if pure WASM (no JS) |
| `supportsJavaScript()` | boolean | True if JS interop enabled |

**When to use `withJs()`**: Only for WASM modules compiled with wasm-bindgen, which generates JavaScript glue code for memory management, string encoding, etc. Examples: @aarkue/process_mining_wasm, any Rust crate using wasm-bindgen.

**Performance**: `pureWasm()` is slightly faster (~5% overhead reduction) due to no JavaScript context overhead.

---

## WasmException

Thrown on WASM load or execution errors.

| Property | Type | Description |
|----------|------|-------------|
| `message` | String | Error message |
| `errorKind` | `ErrorKind` | Category of error |
| `cause` | Throwable | Underlying exception (may be null) |

**ErrorKind Enum**:

| Kind | Meaning | Typical Recovery |
|------|---------|------------------|
| `MODULE_LOAD_ERROR` | Failed to load/parse .wasm binary (file not found, corrupt, invalid) | Check resource path, verify .wasm is valid |
| `EXECUTION_ERROR` | WASM code trapped or execution failed (divide by zero, call stack overflow) | Check WASM function signature and arguments |
| `CONTEXT_ERROR` | GraalVM Context creation failed (out of memory) | Reduce pool size, increase heap |
| `RUNTIME_NOT_AVAILABLE` | GraalVM WASM not on classpath | Verify Maven dependency |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `getErrorKind()` | `ErrorKind` | Get error category |
| `getMessage()` | `String` | Get error message |
| `getCause()` | `Throwable` | Get underlying exception (may be null) |

**Example**:
```java
try {
    WasmModule module = engine.loadModuleFromClasspath("wasm/bad.wasm", "bad");
} catch (WasmException e) {
    if (e.getErrorKind() == ErrorKind.MODULE_LOAD_ERROR) {
        System.err.println("File not found or invalid WASM binary");
    }
}
```

---

## Rust4pmBridge

Integration for @aarkue/process_mining_wasm (OCEL2 event log analysis).

**Location**: `org.yawlfoundation.yawl.graalwasm`

**Requires**: `JavaScriptSandboxConfig.forWasm()` in JavaScript engine

| Constructor | Description |
|---|---|
| `Rust4pmBridge(JavaScriptExecutionEngine engine)` | Create bridge with JavaScript engine supporting WASM |

| Method | Returns | Description | Throws |
|---|---|---|---|
| `processOcel2(String ocel2Json)` | `Map<String, Object>` | Process OCEL2 event log via rust4pm WASM module | `WasmException`, `JavaScriptException` |

**Architecture**: Rust4pmBridge uses both JavaScript (for wasm-bindgen glue) and WASM (for process mining logic). Requires `JavaScriptSandboxConfig.forWasm()`.

**Example**:
```java
JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.forWasm())
    .build();

Rust4pmBridge bridge = new Rust4pmBridge(jsEngine);

String ocel2Json = """
{
    "ocel:version": "2.0",
    "ocel:objects": [...],
    "ocel:events": [...]
}
""";

Map<String, Object> result = bridge.processOcel2(ocel2Json);
// result contains process metrics: conformance, fitness, etc.
```

---

## Value (GraalVM Polyglot)

Result of WASM function execution.

| Method | Returns | Description |
|---|---|---|
| `asInt()` | `int` | Convert to int |
| `asLong()` | `long` | Convert to long |
| `asDouble()` | `double` | Convert to double |
| `asString()` | `String` | Convert to String |
| `asBoolean()` | `boolean` | Convert to boolean |
| `as(Class<T> type)` | `T` | Convert to specific Java type |
| `isNull()` | `boolean` | Test for null |
| `isNumber()` | `boolean` | Test if numeric type |

**Example**:
```java
try (WasmModule module = engine.loadModuleFromClasspath("wasm/math.wasm", "math")) {
    Value result = module.execute("fibonacci", 10);
    int fib = result.asInt();  // 55
}
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalwasm</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

---

## Import Statements

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmBinaryCache;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;
import org.yawlfoundation.yawl.graalwasm.WasmException;
import org.yawlfoundation.yawl.graalwasm.Rust4pmBridge;
import org.graalvm.polyglot.Value;  // GraalVM Polyglot API
```

---

## Resource Location

WASM modules are typically placed in classpath:

```
src/main/resources/wasm/
├── math.wasm
├── crypto.wasm
└── process-mining.wasm
```

Maven pom.xml configuration:

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <includes>
                <include>wasm/**/*.wasm</include>
            </includes>
        </resource>
    </resources>
</build>
```

