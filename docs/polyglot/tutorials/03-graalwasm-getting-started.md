# Tutorial 3: Getting Started with GraalWasm

## Goal

Load and execute a compiled WebAssembly module from your YAWL workflow, running high-performance functions for analytics or data processing. By the end, you'll have a working `WasmExecutionEngine` that executes native WASM code directly from a Java task handler.

## Prerequisites

- **GraalVM JDK 24.1 or later** with WebAssembly language support
  ```bash
  sdk install java 24.1.0-graal
  gu install wasm
  ```
- **Maven** 3.8+
- **A compiled WebAssembly file** (.wasm binary). You can:
  - Compile from Rust using `wasm-pack` or `cargo build --target wasm32-unknown-unknown`
  - Compile from C/C++ using Emscripten
  - Use a pre-compiled binary (e.g., from npm or a Rust registry)
- **Familiarity** with Java, Maven, and basic WASM concepts

## Steps

### Step 1: Add Maven Dependency

Add yawl-graalwasm to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalwasm</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

Run Maven to download and cache the dependency:

```bash
mvn clean install -U
```

### Step 2: Bundle a WebAssembly File

Place your .wasm file in your project's resources directory:

```
src/main/resources/
└── wasm/
    └── analytics.wasm
```

For example, if you have a Rust project that exports a simple add function:

```rust
// src/lib.rs
#[no_mangle]
pub extern "C" fn add(a: i32, b: i32) -> i32 {
    a + b
}
```

Compile it to WASM:

```bash
cargo build --target wasm32-unknown-unknown --release
# The .wasm file is at: target/wasm32-unknown-unknown/release/your_crate.wasm
cp target/wasm32-unknown-unknown/release/your_crate.wasm src/main/resources/wasm/analytics.wasm
```

### Step 3: Create a WasmExecutionEngine

In your task handler class, instantiate the WASM engine using the builder pattern:

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;

public class AnalyticsHandler {

    public void processAnalytics(YWorkItem item) {
        // Create the engine with pure WASM sandbox
        WasmExecutionEngine engine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.pureWasm())
            .build();

        try {
            // Load and execute WASM module (see next steps)
        } finally {
            engine.close();  // Always close to release the GraalWasm context
        }
    }
}
```

The `pureWasm()` sandbox restricts the WASM module to pure computation without JavaScript interop.

### Step 4: Load a WASM Module from Classpath Resources

Load your bundled WASM file from the classpath:

```java
try {
    // Load the module from src/main/resources/wasm/analytics.wasm
    WasmModule module = engine.loadModuleFromClasspath(
        "wasm/analytics.wasm",
        "analytics"  // module name for reference
    );

    try {
        // Execute a WASM exported function (see next step)
    } finally {
        module.close();  // MUST close — releases GraalWasm context
    }

} finally {
    engine.close();
}
```

The module name is arbitrary and is used only for reference. The `loadModuleFromClasspath()` method searches the classpath for the resource.

### Step 5: Execute a WASM Exported Function

Call an exported function from your WASM module and get the result:

```java
try {
    WasmModule module = engine.loadModuleFromClasspath("wasm/analytics.wasm", "analytics");

    try {
        // Call the add function: add(3, 4) → 7
        Value result = module.execute("add", 3, 4);

        // Convert WASM Value to Java int
        int sum = result.asInt();
        System.out.println("Sum: " + sum);  // Output: Sum: 7

    } finally {
        module.close();
    }

} finally {
    engine.close();
}
```

The `execute(functionName, args...)` method calls a WASM exported function with the given arguments. The result is returned as a GraalVM `Value` object, which you convert to Java types using `asInt()`, `asDouble()`, `asBoolean()`, etc.

### Step 6: Handle Multiple WASM Function Calls

Execute several functions from the same module efficiently:

```java
try {
    WasmModule module = engine.loadModuleFromClasspath(
        "wasm/analytics.wasm",
        "analytics"
    );

    try {
        // Define some functions in the WASM module:
        // multiply(a: i32, b: i32) -> i32
        // divide(a: i32, b: i32) -> f64

        Value mulResult = module.execute("multiply", 6, 7);
        int product = mulResult.asInt();
        System.out.println("Product: " + product);  // Output: Product: 42

        Value divResult = module.execute("divide", 100, 3);
        double quotient = divResult.asDouble();
        System.out.println("Quotient: " + quotient);  // Output: Quotient: 33.333...

        // Update work item with results
        item.setDataVariable("product", String.valueOf(product));
        item.setDataVariable("quotient", String.valueOf(quotient));

    } finally {
        module.close();
    }

} finally {
    engine.close();
}
```

Each `execute()` call on the same module reuses the WASM context, keeping latency low.

### Step 7: Load a WASM Module from the Filesystem

For dynamic loading or larger WASM binaries, load from a filesystem path:

```java
try {
    // Load from /data/compute/matrix.wasm
    WasmModule module = engine.loadModuleFromPath(
        Paths.get("/data/compute/matrix.wasm"),
        "matrix"
    );

    try {
        Value result = module.execute("matrix_multiply",
            new int[]{1, 2, 3, 4},
            2  // 2x2 matrix dimensions
        );

        // Handle result (structure depends on WASM module)

    } finally {
        module.close();
    }

} finally {
    engine.close();
}
```

The `loadModuleFromPath(Path)` method loads WASM from an absolute filesystem path. This is useful for modules that are too large to bundle in the JAR or for dynamically generated modules.

### Step 8: Leverage Transparent Binary Caching

The WASM binary cache is transparent — repeat loads of the same .wasm file are instant:

```java
public class AnalyticsHandler {
    private WasmExecutionEngine engine;

    public AnalyticsHandler() {
        this.engine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.pureWasm())
            .build();
    }

    public void processAnalyticsTask1(YWorkItem item) throws WasmException {
        // First load: parses the WASM binary
        WasmModule module = engine.loadModuleFromClasspath(
            "wasm/analytics.wasm", "analytics"
        );

        try {
            Value result = module.execute("analyze_risk",
                Double.parseDouble(item.getDataVariable("amount")));
            item.setDataVariable("risk_score", result.asDouble() + "");
        } finally {
            module.close();
        }
    }

    public void processAnalyticsTask2(YWorkItem item) throws WasmException {
        // Second load: instant (uses cached parsed binary)
        WasmModule module = engine.loadModuleFromClasspath(
            "wasm/analytics.wasm", "analytics"
        );

        try {
            Value result = module.execute("predict_outcome",
                item.getDataVariable("case_type"));
            item.setDataVariable("prediction", result.toString());
        } finally {
            module.close();
        }
    }

    public void shutdown() {
        engine.close();
    }
}
```

The `WasmBinaryCache` caches the parsed `Source` object, so subsequent loads of the same .wasm file skip parsing and execute instantly. This is transparent to you.

### Step 9: Handle WASM Exceptions

Wrap engine and module calls in try/catch to handle WASM errors:

```java
try {
    WasmModule module = engine.loadModuleFromClasspath("wasm/analytics.wasm", "analytics");

    try {
        // This function might fail if the WASM module has a runtime error
        Value result = module.execute("risky_computation", 42);
        int value = result.asInt();
        item.setDataVariable("result", String.valueOf(value));

    } catch (WasmException e) {
        // Handle WASM-specific errors
        System.err.println("WASM execution error: " + e.getMessage());
        System.err.println("Error kind: " + e.getErrorKind());  // EXECUTION_ERROR, MODULE_LOAD_ERROR, etc.

        item.setDataVariable("result", "ERROR");
        item.setDataVariable("error_reason", e.getMessage());

    } finally {
        module.close();
    }

} catch (WasmException e) {
    // Handle module loading errors
    System.err.println("Failed to load WASM module: " + e.getMessage());
    item.setDataVariable("result", "MODULE_LOAD_FAILED");

} finally {
    engine.close();
}
```

`WasmException` can represent different error kinds:
- `MODULE_LOAD_ERROR` — Failed to parse or load the .wasm file
- `EXECUTION_ERROR` — Runtime error during WASM function execution
- `CONTEXT_ERROR` — GraalVM context error (rare)
- `RUNTIME_NOT_AVAILABLE` — WASM language not installed in GraalVM

### Step 10: Use Try-With-Resources for Automatic Cleanup

Simplify resource management by using try-with-resources (since `WasmModule` and `WasmExecutionEngine` are `AutoCloseable`):

```java
public void processAnalytics(YWorkItem item) {
    try (WasmExecutionEngine engine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.pureWasm())
            .build()) {

        try (WasmModule module = engine.loadModuleFromClasspath(
                "wasm/analytics.wasm", "analytics")) {

            Value result = module.execute("compute_score",
                Double.parseDouble(item.getDataVariable("amount")),
                Integer.parseInt(item.getDataVariable("caseAge"))
            );

            double score = result.asDouble();
            item.setDataVariable("analytics_score", String.valueOf(score));

        } catch (WasmException e) {
            System.err.println("Analytics failed: " + e.getMessage());
            item.setDataVariable("analytics_score", "0.0");
        }

    } catch (WasmException e) {
        System.err.println("Engine initialization failed: " + e.getMessage());
    }
}
```

The try-with-resources statement automatically calls `close()` on both the module and engine, even if an exception occurs.

### Step 11: Complete Example in a YAWL Task Handler

Here's a realistic example of a WASM-powered analytics task in a workflow:

```java
import org.yawl.engine.domain.YWorkItem;
import org.yawl.engine.interfce.WorkItemCompleteListener;
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmException;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;
import com.oracle.truffle.api.interop.InteropException;

public class CaseAnalyticsHandler implements WorkItemCompleteListener {
    private WasmExecutionEngine engine;

    public CaseAnalyticsHandler() {
        this.engine = WasmExecutionEngine.builder()
            .sandboxConfig(WasmSandboxConfig.pureWasm())
            .build();
    }

    @Override
    public void workItemCompleted(YWorkItem item) {
        try (WasmModule analyticsModule = engine.loadModuleFromClasspath(
                "wasm/case_analytics.wasm", "case_analytics")) {

            // Extract case properties
            double caseAmount = Double.parseDouble(
                item.getDataVariable("amount")
            );
            int caseAgeDays = Integer.parseInt(
                item.getDataVariable("caseAge")
            );
            String caseType = item.getDataVariable("type");

            // Call WASM function to compute risk score
            Value riskResult = analyticsModule.execute(
                "calculate_risk_score",
                caseAmount,
                caseAgeDays,
                caseType.hashCode()  // WASM functions typically work with numbers
            );
            double riskScore = riskResult.asDouble();

            // Call another WASM function to predict processing time
            Value timeResult = analyticsModule.execute(
                "predict_processing_days",
                caseAmount,
                riskScore
            );
            int predictedDays = timeResult.asInt();

            // Update work item with analytics results
            item.setDataVariable("wasm_risk_score", String.valueOf(riskScore));
            item.setDataVariable("predicted_processing_days", String.valueOf(predictedDays));

            // Determine next action based on analytics
            String nextAction = riskScore > 0.7 ? "ESCALATE" : "STANDARD_PROCESSING";
            item.setDataVariable("recommended_action", nextAction);

            System.out.println("Case " + item.getId()
                + ": Risk=" + String.format("%.2f", riskScore)
                + ", Predicted Days=" + predictedDays
                + ", Action=" + nextAction);

        } catch (WasmException e) {
            System.err.println("Analytics failed for case " + item.getId()
                + ": " + e.getMessage());
            item.setDataVariable("wasm_risk_score", "0.5");  // Default safe value
            item.setDataVariable("recommended_action", "MANUAL_REVIEW");

        } finally {
            // Engine is closed in shutdown() method
        }
    }

    public void shutdown() {
        if (engine != null) {
            engine.close();
        }
    }
}
```

Assuming the WASM module (`src/main/resources/wasm/case_analytics.wasm`) exports:
- `calculate_risk_score(amount: f64, age_days: i32, case_type_hash: i32) -> f64`
- `predict_processing_days(amount: f64, risk_score: f64) -> i32`

### Step 12: Integrate with WASM+JavaScript Context (Optional)

For modules that require JavaScript interop (e.g., wasm-bindgen generated code), use `WasmSandboxConfig.withJs()`:

```java
// For WASM modules compiled with wasm-bindgen that require JavaScript
try (WasmExecutionEngine engine = WasmExecutionEngine.builder()
        .sandboxConfig(WasmSandboxConfig.withJs())  // Enable JS+WASM polyglot
        .build()) {

    try (WasmModule module = engine.loadModuleFromClasspath(
            "wasm/bindgen_module.wasm", "bindgen_module")) {

        // Call WASM functions that may use JavaScript interop internally
        Value result = module.execute("process_data", "input_data");
        // Process result...

    }
}
```

This allows WASM modules that use `wasm-bindgen` or similar frameworks to work properly.

---

## What You Built

You've created a **WASM execution pipeline** for high-performance analytics in your YAWL workflow. Your `CaseAnalyticsHandler` can now:

1. Load compiled WebAssembly modules from classpath or filesystem
2. Call WASM exported functions with Java arguments
3. Retrieve WASM results and convert them to Java types
4. Handle WASM errors and exceptions gracefully
5. Benefit from transparent binary caching for repeat loads
6. Automatically clean up WASM contexts via try-with-resources

WASM's native compilation enables performance 10–100× faster than pure Java for compute-intensive tasks like matrix operations, cryptography, or machine learning inference.

**Next steps:**
- Read [Rust4pmBridge: OCEL2 Process Mining](04-rust4pm-ocel2.md) to analyze event logs with Rust-based algorithms
- Explore How-To guides for packaging WASM modules and integrating with CI/CD
- Consult the [GraalWasm API Reference](../../reference/polyglot-graalwasm-api.md) for advanced memory layouts and interop
