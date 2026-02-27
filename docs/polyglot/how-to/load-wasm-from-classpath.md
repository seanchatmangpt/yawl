# How to bundle and load a .wasm binary from the classpath

## Problem

You have a compiled WebAssembly module (`.wasm` file) and need to load it from your application's classpath.

## Solution

Place the `.wasm` file in `src/main/resources/wasm/`, declare it in `pom.xml`, load via `loadModuleFromClasspath`, and execute functions using try-with-resources to ensure cleanup.

### Project structure

```
src/main/resources/wasm/
├── my-module.wasm              # Your compiled WASM binary
└── process-mining.wasm         # Example: rust4pm WASM module
```

### Add resource to pom.xml

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

### Load and execute WASM module

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;
import org.graalvm.polyglot.Value;

// Initialize engine once per application
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

// Load module from classpath (throws WasmException if not found)
WasmModule module = engine.loadModuleFromClasspath(
    "wasm/my-module.wasm",
    "myModule"
);

// Use try-with-resources to ensure module is closed
try (module) {
    // Execute a function exported from the WASM module
    Value result = module.execute("add", 5, 3);
    int sum = result.asInt();  // 8
    System.out.println("5 + 3 = " + sum);
} catch (Exception e) {
    System.err.println("WASM execution failed: " + e.getMessage());
}
```

### Example: Compute with multiple return values

```java
WasmModule module = engine.loadModuleFromClasspath(
    "wasm/stats-module.wasm",
    "statsModule"
);

try (module) {
    // WASM function returns struct/tuple
    Value stats = module.execute("computeStats", new int[]{1, 2, 3, 4, 5});
    
    // Access fields (depends on WASM module export signature)
    int mean = stats.getMember("mean").asInt();
    int sum = stats.getMember("sum").asInt();
    
    System.out.println("Mean: " + mean + ", Sum: " + sum);
}
```

### Load from file path (if not in classpath)

```java
// For development or external WASM files
WasmModule module = engine.loadModuleFromPath(
    Path.of("/opt/wasm/custom-processor.wasm"),
    "processor"
);

try (module) {
    Value output = module.execute("process", inputData);
    // ...
}
```

### CRITICAL: Always close the module

Forgetting to close a `WasmModule` leaks the underlying GraalVM Context:

```java
// WRONG: Memory leak
WasmModule module = engine.loadModuleFromClasspath("wasm/my.wasm", "m");
module.execute("fn", 1, 2);
// Module not closed!

// CORRECT: Automatic cleanup
try (WasmModule module = engine.loadModuleFromClasspath("wasm/my.wasm", "m")) {
    module.execute("fn", 1, 2);
}  // AutoCloseable.close() called automatically
```

### Handle WASM exceptions

```java
import org.yawlfoundation.yawl.graalwasm.WasmException;

try (WasmModule module = engine.loadModuleFromClasspath("wasm/math.wasm", "math")) {
    // WASM may trap (execution error)
    Value result = module.execute("divide", 10, 0);
} catch (WasmException e) {
    System.err.println("WASM error: " + e.getMessage());
    System.err.println("Kind: " + e.getErrorKind());  // MODULE_LOAD_ERROR, EXECUTION_ERROR, etc.
}
```

### Cache binary across multiple loads

The engine maintains a `WasmBinaryCache`. Subsequent loads of the same `.wasm` file are instant:

```java
// First load: parses and caches binary (fast)
WasmModule m1 = engine.loadModuleFromClasspath("wasm/common.wasm", "mod1");
try (m1) { m1.execute("work"); }

// Second load: instant, uses cached binary
WasmModule m2 = engine.loadModuleFromClasspath("wasm/common.wasm", "mod2");
try (m2) { m2.execute("work"); }
```

Clear the cache if needed:

```java
engine.close();  // Clears binary cache and closes engine
```

## Tips

- **Keep modules small**: Each module takes 1-10 MB per instance.
- **Reuse engine**: Create once and keep alive for multiple module loads.
- **Try-with-resources is mandatory**: Use `try (WasmModule m = ...)` for automatic cleanup.
- **Classpath vs. filesystem**: Use classpath for bundled modules (simpler packaging). Use filesystem for dynamically loaded modules.

