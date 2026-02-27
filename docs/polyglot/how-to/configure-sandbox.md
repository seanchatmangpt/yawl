# How to configure sandbox restrictions for GraalPy, GraalJS, and GraalWasm

## Problem

You need to run Python, JavaScript, or WebAssembly code with different levels of trust. Untrusted code should have no access to files or network; trusted code may need file I/O.

## Solution

Use the sandbox configuration presets when building the engine. All three modules provide `Strict`, `Standard`, and `Permissive` configurations.

### Quick reference table

| Config | File I/O | Network | Use Case |
|--------|----------|---------|----------|
| `strict()` | No | No | Untrusted user-submitted scripts, math-only operations |
| `standard()` | Read-only | No | Trusted scripts that need to read workflow logs, configs |
| `permissive()` | Read/Write | Yes | Internal scripts with full system access (not recommended) |

### GraalPy sandbox configuration

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

// Strict: no I/O, no network
PythonExecutionEngine strictEngine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.strict())
    .build();

// Standard: read-only file access, no network
PythonExecutionEngine standardEngine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.standard())
    .build();

// Permissive: full access (use only for internal trusted code)
PythonExecutionEngine permissiveEngine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.permissive())
    .build();
```

### GraalJS sandbox configuration

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

// Strict: no I/O, no network
JavaScriptExecutionEngine strictEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.strict())
    .build();

// Standard: read-only file access, no network
JavaScriptExecutionEngine standardEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.standard())
    .build();

// Permissive: full access
JavaScriptExecutionEngine permissiveEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.permissive())
    .build();

// Special: JS + WASM for process_mining_wasm (Rust4pmBridge)
JavaScriptExecutionEngine jsWasmEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.forWasm())  // Enables JS + WASM interop
    .build();
```

### GraalWasm sandbox configuration

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;

// Pure WASM: isolated execution, no I/O, no network
WasmExecutionEngine pureWasmEngine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

// WASM + JavaScript: allows JS glue code (for wasm-bindgen modules)
WasmExecutionEngine jsWasmEngine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.withJs())
    .build();
```

### Real-world example: sandboxing untrusted user rules

```java
// User-submitted JavaScript routing rules
// Risk: attempts to read /etc/passwd or exfiltrate data
JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(2)
    .sandboxConfig(JavaScriptSandboxConfig.strict())  // Block all I/O
    .build();

// This will fail safely:
try {
    engine.eval("""
        const fs = require('fs');
        fs.readFileSync('/etc/passwd', 'utf8')
    """);
} catch (JavaScriptException e) {
    System.err.println("Blocked: " + e.getMessage());
    // Exception thrown; no file access occurred
}

// But this works:
String result = engine.evalToString("""
    Math.random() * 100
""");  // Safe: pure math, no I/O
```

### Real-world example: reading workflow logs with standard config

```java
// Internal Python script that needs to read workflow logs
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.standard())  // Read-only file access
    .build();

Map<String, Object> analysis = engine.evalToMap("""
    import json
    
    # Read-only access allowed
    with open('/var/log/yawl/workflow.log', 'r') as f:
        lines = f.readlines()
    
    analysis = {
        'total_lines': len(lines),
        'errors': sum(1 for line in lines if 'ERROR' in line),
        'avg_line_length': sum(len(l) for l in lines) / len(lines)
    }
    analysis
""");

System.out.println("Errors found: " + analysis.get("errors"));
```

### When to use `forWasm()` for JavaScript

Use `JavaScriptSandboxConfig.forWasm()` only when your JavaScript code needs to interoperate with WASM modules compiled by wasm-bindgen:

```java
import org.yawlfoundation.yawl.graalwasm.Rust4pmBridge;

// Rust4pmBridge uses wasm-bindgen, which requires JS+WASM context
JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.forWasm())
    .build();

// Now you can use Rust4pmBridge.processOcel2()
Rust4pmBridge bridge = new Rust4pmBridge(engine);
Map<String, Object> result = bridge.processOcel2(ocel2Json);
```

Do NOT use `forWasm()` for standard JavaScript rules; it has extra overhead.

## Tips

- **Default to strict**: Unless you have a specific reason to allow I/O, use `strict()`.
- **Document sandbox choice**: Add a comment explaining why your engine needs `standard()` or `permissive()`.
- **One engine per sandbox level**: Don't create multiple engines; reuse a single strict engine for untrusted code.
- **Test the boundary**: Write tests that confirm blocked operations actually fail (don't silently succeed).

