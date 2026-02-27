# How to configure sandbox restrictions for GraalPy, GraalJS, and GraalWasm

## Problem

You need to execute untrusted or user-provided scripts safely, with explicit control over what file system and network access they have.

## Solution

Use the sandbox configuration classes provided by each polyglot module. Each offers three preset levels: `strict()`, `standard()`, and `permissive()`.

## GraalPy Sandbox Configuration

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

// Strict: No I/O, no network, no system calls
PythonExecutionEngine strictEngine = PythonExecutionEngine.builder()
    .sandboxConfig(PythonSandboxConfig.strict())
    .build();

// Standard: Read-only file access, no network
PythonExecutionEngine standardEngine = PythonExecutionEngine.builder()
    .sandboxConfig(PythonSandboxConfig.standard())
    .build();

// Permissive: Full file and network access (use only for trusted code)
PythonExecutionEngine permissiveEngine = PythonExecutionEngine.builder()
    .sandboxConfig(PythonSandboxConfig.permissive())
    .build();
```

### GraalPy Sandbox Behavior

| Config | File Read | File Write | Network | System Calls | Use Case |
|--------|-----------|-----------|---------|--------------|----------|
| **Strict** | ✗ | ✗ | ✗ | ✗ | Untrusted user scripts; pure computation |
| **Standard** | ✓ (read-only) | ✗ | ✗ | ✗ | Loading data files; no output; no side effects |
| **Permissive** | ✓ | ✓ | ✓ | ✓ | Internal trusted Python modules; ML libraries |

### Example: Strict Python sandbox for user-uploaded rules

```java
String userProvidedScript = """
    def calculate_score(data):
        total = sum(data.values())
        return total / len(data)

    result = calculate_score({'metric1': 10, 'metric2': 20})
    """;

try {
    PythonExecutionEngine engine = PythonExecutionEngine.builder()
        .poolSize(2)
        .sandboxConfig(PythonSandboxConfig.strict())
        .build();

    double score = engine.evalToDouble(userProvidedScript);
    System.out.println("Score: " + score);

} catch (PythonException e) {
    // User script attempted file access or network call
    System.err.println("Sandboxed execution violation: " + e.getMessage());
}
```

## GraalJS Sandbox Configuration

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

// Strict: No I/O, no network
JavaScriptExecutionEngine strictEngine = JavaScriptExecutionEngine.builder()
    .sandboxConfig(JavaScriptSandboxConfig.strict())
    .build();

// Standard: Limited file access (read-only), no network
JavaScriptExecutionEngine standardEngine = JavaScriptExecutionEngine.builder()
    .sandboxConfig(JavaScriptSandboxConfig.standard())
    .build();

// Permissive: Full access
JavaScriptExecutionEngine permissiveEngine = JavaScriptExecutionEngine.builder()
    .sandboxConfig(JavaScriptSandboxConfig.permissive())
    .build();

// Special: JS + WASM combined context (for wasm-bindgen modules)
JavaScriptExecutionEngine wasmEngine = JavaScriptExecutionEngine.builder()
    .sandboxConfig(JavaScriptSandboxConfig.forWasm())
    .build();
```

### GraalJS Sandbox Behavior

| Config | File Read | File Write | Network | Import Modules | Use Case |
|--------|-----------|-----------|---------|-----------------|----------|
| **Strict** | ✗ | ✗ | ✗ | ✗ | Untrusted user rules; expression evaluation |
| **Standard** | ✓ (read-only) | ✗ | ✗ | ✗ | Loading JSON config files; no side effects |
| **Permissive** | ✓ | ✓ | ✓ | ✓ | Internal rule engines; external APIs |
| **forWasm()** | ✓ | ✗ | ✗ | ✓ | WASM modules (process_mining_wasm); JS+WASM interop |

### Example: Standard JS sandbox for loading external rules

```java
String ruleScript = """
    function checkApproval(amount) {
        if (amount > 5000) {
            return { approved: false, reason: 'Over limit' };
        }
        return { approved: true, reason: 'Within budget' };
    }
    """;

try {
    JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
        .contextPoolSize(4)
        .sandboxConfig(JavaScriptSandboxConfig.standard())
        .build();

    Map<String, Object> decision = engine.evalToMap(ruleScript);
    // Script cannot write files, make network calls, or import modules

} catch (JavaScriptException e) {
    System.err.println("Execution failed: " + e.getMessage());
}
```

## GraalWasm Sandbox Configuration

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;

// Pure WASM: Strict by default; no I/O, no network
WasmExecutionEngine pureEngine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

// WASM + JS: Allows JS glue code (wasm-bindgen modules)
WasmExecutionEngine wasmJsEngine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.withJs())
    .build();
```

### GraalWasm Sandbox Behavior

| Config | File I/O | Network | WASM Exports | JS Interop | Use Case |
|--------|----------|---------|--------------|------------|----------|
| **pureWasm()** | ✗ | ✗ | ✓ | ✗ | Compiled Rust/C/Go functions; math; crypto |
| **withJs()** | ✗ (JS restricted) | ✗ | ✓ | ✓ | wasm-bindgen modules; JS+WASM composition |

### Example: Pure WASM for cryptographic operations

```java
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

try {
    WasmModule cryptoModule = engine.loadModuleFromClasspath(
        "wasm/crypto-hash.wasm",
        "cryptoModule"
    );

    try {
        // WASM module cannot perform I/O or network calls
        // Strict isolation ensures side-effect-free computation
        Value hash = cryptoModule.execute("sha256", "input-data");
        System.out.println("Hash: " + hash.asString());
    } finally {
        cryptoModule.close();
    }
} catch (WasmException e) {
    System.err.println("Crypto failed: " + e.getMessage());
}
```

## Sandbox comparison table

Quick reference for choosing the right sandbox:

| Scenario | GraalPy | GraalJS | GraalWasm |
|----------|---------|---------|-----------|
| **Untrusted user-provided rule** | `strict()` | `strict()` | `pureWasm()` |
| **Load external config files** | `standard()` | `standard()` | N/A |
| **Internal trusted script** | `permissive()` | `permissive()` | N/A |
| **wasm-bindgen module** | N/A | `forWasm()` | `withJs()` |
| **Process mining analysis** | N/A | N/A | `withJs()` (via Rust4pmBridge) |
| **Pure WASM computation** | N/A | N/A | `pureWasm()` |

## Complete example: Multi-engine sandbox configuration

```java
public class WorkflowScriptEngine {
    private final PythonExecutionEngine pythonEngine;
    private final JavaScriptExecutionEngine jsEngine;
    private final WasmExecutionEngine wasmEngine;

    public WorkflowScriptEngine() {
        // Python: untrusted user calculations
        this.pythonEngine = PythonExecutionEngine.builder()
            .poolSize(4)
            .sandboxConfig(PythonSandboxConfig.strict())
            .build();

        // JavaScript: business rules (needs read-only file access for configs)
        this.jsEngine = JavaScriptExecutionEngine.builder()
            .contextPoolSize(4)
            .sandboxConfig(JavaScriptSandboxConfig.standard())
            .build();

        // WASM: process mining analysis with JS glue code
        this.wasmEngine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.withJs())
            .build();
    }

    public double runUserCalculation(String pythonCode) throws Exception {
        try {
            return pythonEngine.evalToDouble(pythonCode);
        } catch (PythonException e) {
            throw new Exception("Calculation failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> evaluateBusinessRule(String jsCode) throws Exception {
        try {
            return jsEngine.evalToMap(jsCode);
        } catch (JavaScriptException e) {
            throw new Exception("Rule evaluation failed: " + e.getMessage(), e);
        }
    }

    public void cleanup() {
        pythonEngine.close();
        jsEngine.close();
        wasmEngine.close();
    }
}
```

## Notes

- **Defense in depth**: Sandbox restrictions are one layer; also validate script syntax and structure before execution.
- **Standard vs Permissive trade-off**: Use `standard()` for production untrusted code; only use `permissive()` when you control the script source.
- **forWasm()**: Special configuration for JavaScript that needs to interact with WASM modules. Not recommended for untrusted code.
- **Module loading**: WASM modules loaded with `withJs()` still cannot access the file system directly (JS glue is sandboxed).

## Related guides

- [Execute a Python script](./execute-python-script.md)
- [Execute JavaScript workflow rules](./execute-js-in-workflow-task.md)
- [Load WASM from classpath](./load-wasm-from-classpath.md)
