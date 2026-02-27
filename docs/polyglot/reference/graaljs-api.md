# GraalJS API Reference

Complete reference for `org.yawlfoundation.yawl.graaljs` module (YAWL 6.0.0-GA).

## JavaScriptExecutionEngine

Main entry point for JavaScript script execution.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `builder()` | `JavaScriptExecutionEngine.Builder` | Static factory for creating engines | — |
| `eval(String javaScriptSource)` | `Object` | Evaluate JS expression; returns GraalVM Value boxed as Object | `JavaScriptException` |
| `evalToString(String source)` | `String` | Evaluate and return String result (calls `.toString()` on result) | `JavaScriptException` |
| `evalToDouble(String source)` | `double` | Evaluate and return numeric result as double | `JavaScriptException`, `ClassCastException` |
| `evalToLong(String source)` | `long` | Evaluate and return numeric result as long | `JavaScriptException`, `ClassCastException` |
| `evalToMap(String source)` | `Map<String, Object>` | Evaluate to JS object; auto-marshalled to Java Map (uses hasMembers()) | `JavaScriptException`, `TypeMarshalException` |
| `evalToList(String source)` | `List<Object>` | Evaluate to JS array; auto-marshalled to Java List | `JavaScriptException`, `TypeMarshalException` |
| `evalScript(Path scriptPath)` | `Object` | Load and execute .js file from filesystem (code available in context) | `JavaScriptException`, `IOException` |
| `evalScriptInAllContexts(Path scriptPath)` | `void` | Execute script in all pooled contexts in parallel (virtual threads) | `JavaScriptException`, `IOException` |
| `invokeJsFunction(String functionName, Object... args)` | `Object` | Call JavaScript function by name with Java arguments | `JavaScriptException` |
| `evalAs(String source, Class<T> type)` | `T` | Evaluate to specific Java type (reflection-based casting) | `JavaScriptException`, `ClassCastException` |
| `evalAsJson(String source)` | `Map<String, Object>` | Evaluate JSON string and parse to Map | `JavaScriptException` |
| `getContextPool()` | `JavaScriptContextPool` | Access underlying context pool | — |
| `close()` | `void` | Shutdown engine, release all contexts | `IOException` |

**Builder Options**:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `contextPoolSize(int)` | int | 4 | Number of JavaScriptExecutionContext instances |
| `sandboxed(boolean)` | boolean | false | Legacy; use `sandboxConfig()` instead |
| `sandboxConfig(JavaScriptSandboxConfig)` | JavaScriptSandboxConfig | `standard()` | Sandbox restrictions (overrides `sandboxed` param) |

**Example**:
```java
JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(8)
    .sandboxConfig(JavaScriptSandboxConfig.standard())
    .build();
```

---

## JavaScriptContextPool

Manages pool of reusable JavaScriptExecutionContext instances.

| Method | Returns | Description |
|--------|---------|-------------|
| `getNumBorrowed()` | int | Number of contexts currently borrowed |
| `getNumIdle()` | int | Number of idle contexts available |
| `getMaxPoolSize()` | int | Maximum pool size |
| `execute(Function<JavaScriptExecutionContext, T> task)` | `T` | Borrow context, execute task, return context |

**Note**: Contexts are borrowed/returned automatically by eval* methods.

---

## JavaScriptSandboxConfig

Predefined sandbox configurations controlling file I/O, network, and language interop.

| Static Factory | File I/O | Network | WASM | Use Case |
|---|---|---|---|---|
| `strict()` | Blocked | Blocked | No | Untrusted user scripts |
| `standard()` | Read-only | Blocked | No | Trusted scripts with log access |
| `permissive()` | Read/Write | Allowed | No | Internal code (not recommended) |
| `forWasm()` | Read-only | Blocked | **Yes** | wasm-bindgen modules (Rust4pmBridge) |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `allowFileRead()` | boolean | Whether read access permitted |
| `allowFileWrite()` | boolean | Whether write access permitted |
| `allowNetwork()` | boolean | Whether network access permitted |
| `allowWasm()` | boolean | Whether WASM interop enabled |

**When to use `forWasm()`**: Only for scripts that call WASM modules compiled with wasm-bindgen (like @aarkue/process_mining_wasm). Adds ~10% overhead due to extra language context setup.

---

## JsTypeMarshaller

Internal utility for converting GraalVM Values to Java types. Used automatically by eval* methods.

| Method | Returns | Description |
|--------|---------|-------------|
| `toJava(Value value)` | `Object` | Convert Value to Java Object |
| `toString(Value value)` | `String` | Convert Value to String (calls `.toString()` in JS) |
| `toDouble(Value value)` | `double` | Extract numeric value as double |
| `toLong(Value value)` | `long` | Extract numeric value as long |
| `toMap(Value value)` | `Map<String, Object>` | Convert JS object to Map **using hasMembers()** |
| `toList(Value value)` | `List<Object>` | Convert JS array to List |
| `as(Value value, Class<T> type)` | `T` | Cast to specific Java type |
| `parseJsonString(String json)` | `Map<String, Object>` | Parse JSON string to Map |

**Critical**: `toMap()` uses `hasMembers()` to detect JS objects, NOT `hasHashEntries()` (that's Python).

---

## JavaScriptExecutionContext

Single GraalVM Context running JavaScript interpreter. **Managed by pool; do not create directly.**

| Method | Returns | Description |
|--------|---------|-------------|
| `getContext()` | `org.graalvm.polyglot.Context` | Access raw GraalVM Context (advanced) |
| `eval(String jsSource)` | `Value` | Low-level eval returning GraalVM Value |
| `close()` | `void` | **Do not call directly**; pool manages lifecycle |

---

## JavaScriptException

Thrown on JavaScript evaluation errors or runtime failures.

| Property | Type | Description |
|----------|------|-------------|
| `message` | String | Error message from JavaScript |
| `errorKind` | `ErrorKind` | Category of error |
| `cause` | Throwable | Underlying exception (often null) |

**ErrorKind Enum**:

| Kind | Meaning | Typical Recovery |
|------|---------|------------------|
| `EXECUTION_ERROR` | JavaScript code raised exception (TypeError, ReferenceError, etc.) | Catch, log, use fallback |
| `CONTEXT_ERROR` | Context pool exhausted or context creation failed | Increase contextPoolSize |
| `RUNTIME_NOT_AVAILABLE` | GraalVM JS runtime not on classpath | Verify Maven dependency |
| `TYPE_ERROR` | Result type mismatch (e.g., evalToDouble on non-numeric) | Check JS expression |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `getErrorKind()` | `ErrorKind` | Get error category |
| `getMessage()` | `String` | Get error message |
| `getCause()` | `Throwable` | Get underlying exception (may be null) |

**Example**:
```java
try {
    engine.evalToDouble("undefined.x");
} catch (JavaScriptException e) {
    System.err.println("Kind: " + e.getErrorKind());  // EXECUTION_ERROR
}
```

---

## Rust4pmBridge (Process Mining WASM Integration)

Special integration for @aarkue/process_mining_wasm (compiled WASM + wasm-bindgen glue).

**Location**: Requires `JavaScriptSandboxConfig.forWasm()`

| Static Method | Returns | Description | Throws |
|---|---|---|---|
| `processOcel2(JavaScriptExecutionEngine engine, String ocel2Json)` | `Map<String, Object>` | Process OCEL2 event log via @aarkue/process_mining_wasm | `JavaScriptException`, `WasmException` |

**Example**:
```java
JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.forWasm())
    .build();

Rust4pmBridge bridge = new Rust4pmBridge(engine);
Map<String, Object> result = bridge.processOcel2(ocel2Json);
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graaljs</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

---

## Import Statements

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptContextPool;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;
import org.yawlfoundation.yawl.graaljs.JsTypeMarshaller;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionContext;
import org.yawlfoundation.yawl.graaljs.JavaScriptException;
import org.yawlfoundation.yawl.graalwasm.Rust4pmBridge;
```

