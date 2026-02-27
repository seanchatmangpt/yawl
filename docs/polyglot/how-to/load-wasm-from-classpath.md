# How to bundle and load a .wasm binary from the classpath

## Problem

You have a compiled WebAssembly (.wasm) module that you want to include with your YAWL application and load at runtime without managing external file paths.

## Solution

Place the .wasm file in `src/main/resources/wasm/`, configure Maven to include it, then load it via `WasmExecutionEngine.loadModuleFromClasspath()`.

### Step 1: Organize your WASM module

Create the directory structure:
```
src/main/resources/
└── wasm/
    └── my-module.wasm
```

Copy your compiled .wasm binary into this directory.

### Step 2: Update pom.xml

Ensure the Maven Resource Plugin includes .wasm files:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-my-wasm-task</artifactId>
    <version>6.0.0-GA</version>

    <dependencies>
        <dependency>
            <groupId>org.yawlfoundation</groupId>
            <artifactId>yawl-graalwasm</artifactId>
            <version>6.0.0-GA</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <configuration>
                    <encoding>UTF-8</encoding>
                    <!-- .wasm files are binary, not text -->
                </configuration>
            </plugin>
        </plugins>

        <resources>
            <resource>
                <directory>src/main/resources</directory>
                <includes>
                    <include>**/*.wasm</include>
                    <include>**/*.js</include>
                    <include>**/*.properties</include>
                </includes>
            </resource>
        </resources>
    </build>
</project>
```

### Step 3: Load and execute the module

```java
import org.yawlfoundation.yawl.graalwasm.WasmExecutionEngine;
import org.yawlfoundation.yawl.graalwasm.WasmModule;
import org.yawlfoundation.yawl.graalwasm.WasmSandboxConfig;
import org.yawlfoundation.yawl.graalwasm.WasmException;
import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.graalvm.polyglot.Value;

// Create engine with pure WASM sandbox
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

try {
    // Load from classpath: src/main/resources/wasm/my-module.wasm
    WasmModule module = engine.loadModuleFromClasspath(
        "wasm/my-module.wasm",
        "myModule"
    );

    try {
        // Execute WASM function: add(int, int) -> int
        Value result = module.execute("add", 10, 20);
        int sum = result.asInt();
        System.out.println("Result: " + sum);  // Output: Result: 30

    } finally {
        // CRITICAL: WasmModule must be closed to release the Context
        module.close();
    }

} catch (WasmException e) {
    System.err.println("WASM execution failed: " + e.getMessage());
    // Inspect e.getErrorKind() for specific error types
} finally {
    engine.close();
}
```

### For wasm-bindgen modules (like Rust4pmBridge)

If your .wasm module was compiled with `wasm-bindgen` (which generates JavaScript glue code), use the combined JS+WASM sandbox:

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.withJs())
    .build();

try {
    WasmModule module = engine.loadModuleFromClasspath(
        "wasm/process_mining_wasm.js",  // Note: .js file, which loads .wasm internally
        "processMiningModule"
    );

    try {
        // Execute wasm-bindgen function
        Value result = module.execute("analyzeTrace", jsonString);
        // Process result...
    } finally {
        module.close();
    }

} catch (WasmException e) {
    // Handle error...
}
```

## Common patterns

### Loading multiple WASM modules from the same engine

```java
WasmExecutionEngine engine = WasmExecutionEngine.builder()
    .sandboxConfig(WasmSandboxConfig.pureWasm())
    .build();

try {
    WasmModule mathModule = engine.loadModuleFromClasspath(
        "wasm/math-functions.wasm", "mathModule");
    WasmModule stringModule = engine.loadModuleFromClasspath(
        "wasm/string-functions.wasm", "stringModule");

    try {
        Value math1 = mathModule.execute("multiply", 3, 7);
        Value str1 = stringModule.execute("reverse", "hello");

        System.out.println(math1.asInt());      // 21
        System.out.println(str1.asString());    // "olleh"
    } finally {
        mathModule.close();
        stringModule.close();
    }
} finally {
    engine.close();
}
```

### Error handling for missing resources

```java
try {
    WasmModule module = engine.loadModuleFromClasspath(
        "wasm/nonexistent.wasm", "missing");
} catch (WasmException e) {
    if (e.getErrorKind() == WasmException.ErrorKind.MODULE_LOAD_ERROR) {
        System.err.println("WASM file not found in classpath: " + e.getMessage());
        // Fall back to alternate implementation
    }
}
```

### Loading from filesystem instead of classpath

```java
try {
    // If your .wasm is outside the classpath (e.g., /tmp/my-module.wasm)
    WasmModule module = engine.loadModuleFromPath(
        Paths.get("/tmp/my-module.wasm"),
        "tmpModule"
    );

    try {
        Value result = module.execute("someFunction");
    } finally {
        module.close();
    }
} catch (WasmException e) {
    System.err.println("Failed to load WASM from path: " + e.getMessage());
}
```

## Notes

- **Module names**: The module name (e.g., `"myModule"`) is used for debugging and context tracking; it doesn't need to match the filename.
- **Binary vs text**: .wasm files are binary; Maven Resource Plugin handles them correctly by default.
- **Context lifecycle**: Each `WasmModule` holds a GraalVM Context. **You must call `close()` to avoid leaking memory.** Use try-with-resources or explicit try-finally.
- **Pure WASM**: Use `pureWasm()` sandbox for modules with only WASM exports; use `withJs()` for wasm-bindgen modules.
- **Caching**: The binary cache is managed by `WasmBinaryCache` internally; repeated loads of the same module name use cached binaries.

## Related guides

- [WebAssembly binding strategies](../explanation/wasm-binding-strategies.md)
- [Configure sandbox restrictions](./configure-sandbox.md)
- [Choosing between GraalPy, GraalJS, and GraalWasm](../explanation/when-to-use-which.md)
