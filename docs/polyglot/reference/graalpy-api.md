# GraalPy API Reference

Complete reference for `org.yawlfoundation.yawl.graalpy` module (YAWL 6.0.0-GA).

## PythonExecutionEngine

Main entry point for Python script execution.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `builder()` | `PythonExecutionEngine.Builder` | Static factory for creating engines | — |
| `eval(String pythonSource)` | `Object` | Evaluate Python expression; returns GraalVM Value boxed as Object | `PythonException` |
| `evalToString(String pythonSource)` | `String` | Evaluate and return String result (calls `str()` on result) | `PythonException` |
| `evalToDouble(String pythonSource)` | `double` | Evaluate and return numeric result as double | `PythonException`, `ClassCastException` |
| `evalToLong(String pythonSource)` | `long` | Evaluate and return numeric result as long | `PythonException`, `ClassCastException` |
| `evalToMap(String pythonSource)` | `Map<String, Object>` | Evaluate to Python dict; auto-marshalled to Java Map (uses hasHashEntries()) | `PythonException`, `TypeMarshalException` |
| `evalToList(String pythonSource)` | `List<Object>` | Evaluate to Python list; auto-marshalled to Java List | `PythonException`, `TypeMarshalException` |
| `getContextPool()` | `PythonContextPool` | Access to underlying context pool for monitoring/configuration | — |
| `close()` | `void` | Shutdown engine, release all contexts, close pool | `IOException` |

**Builder Options**:

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `poolSize(int)` | int | 4 | Number of PythonExecutionContext instances in pool |
| `sandboxConfig(PythonSandboxConfig)` | PythonSandboxConfig | `standard()` | Sandbox restrictions (strict/standard/permissive) |
| `virtualEnvironment(PythonVirtualEnvironment)` | PythonVirtualEnvironment | null | Optional venv for pip-managed packages |
| `cacheByteCode(boolean)` | boolean | true | Cache compiled .pyc bytecode in memory |

**Example**:
```java
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.standard())
    .cacheByteCode(true)
    .build();
```

---

## PythonContextPool

Manages pool of reusable PythonExecutionContext instances.

| Method | Returns | Description |
|--------|---------|-------------|
| `getNumBorrowed()` | int | Number of contexts currently borrowed from pool |
| `getNumIdle()` | int | Number of idle contexts available to borrow |
| `getMaxPoolSize()` | int | Maximum size of the pool |
| `execute(Function<PythonExecutionContext, T> task)` | `T` | Borrow context, execute task function, return context |

**Note**: Contexts are borrowed/returned automatically by eval* methods.

---

## PythonBytecodeCache

Caches compiled Python bytecode (.pyc) to speed up repeated evaluations.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `loadFromClasspath(String resourcePath)` | `byte[]` | Load compiled bytecode from classpath resource | `PythonException` |
| `loadFromPath(Path pythonPath)` | `byte[]` | Compile Python file to bytecode, cache result | `PythonException` |
| `clear()` | `void` | Clear all cached bytecode | — |
| `size()` | `int` | Number of cached bytecode entries | — |

---

## PythonSandboxConfig

Predefined sandbox configurations controlling file I/O and network access.

| Static Factory | File I/O | Network | Use Case |
|---|---|---|---|
| `strict()` | Blocked | Blocked | Untrusted user scripts, math-only |
| `standard()` | Read-only | Blocked | Trusted scripts needing log access |
| `permissive()` | Read/Write | Allowed | Internal trusted code (not recommended) |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `allowFileRead()` | boolean | Whether read-only file access permitted |
| `allowFileWrite()` | boolean | Whether write access permitted |
| `allowNetwork()` | boolean | Whether network access permitted |

---

## PythonVirtualEnvironment

Optional virtual environment for managing pip-installed packages.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `PythonVirtualEnvironment(Path venvPath)` | — | Constructor; path to existing venv or new directory | — |
| `addPackage(String packageName)` | `void` | Install or ensure package via pip | `PythonException` |
| `getPath()` | `Path` | Get venv root directory | — |

**Example**:
```java
PythonVirtualEnvironment venv = new PythonVirtualEnvironment(Path.of("/tmp/yawl-venv"));
venv.addPackage("pm4py");
venv.addPackage("numpy");

PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .virtualEnvironment(venv)
    .build();
```

---

## TypeMarshaller

Internal utility for converting GraalVM Values to Java types. Used automatically by eval* methods.

| Method | Returns | Description |
|--------|---------|-------------|
| `toJava(Value value)` | `Object` | Convert Value to Java Object (smart casting) |
| `toString(Value value)` | `String` | Convert Value to String (calls str() in Python) |
| `toDouble(Value value)` | `double` | Convert Value to double (extracts numeric value) |
| `toLong(Value value)` | `long` | Convert Value to long |
| `toMap(Value value)` | `Map<String, Object>` | Convert Python dict to Map **using hasHashEntries()** |
| `toList(Value value)` | `List<Object>` | Convert Python list to List |

**Critical**: `toMap()` uses `hasHashEntries()` to detect Python dicts, NOT `hasMembers()`.

---

## PythonExecutionContext

Single GraalVM Context running Python interpreter. **Managed by pool; do not create directly.**

| Method | Returns | Description |
|--------|---------|-------------|
| `getContext()` | `org.graalvm.polyglot.Context` | Access raw GraalVM Context (advanced) |
| `eval(String pythonSource)` | `Value` | Low-level eval returning GraalVM Value |
| `close()` | `void` | **Do not call directly**; pool manages lifecycle |

---

## PythonInterfaceGenerator

Advanced feature: Generate Java interface from Python class.

| Method | Returns | Description | Throws |
|--------|---------|-------------|--------|
| `generateInterface(Value pythonClass, Class<?> javaInterface)` | `Object` | Create Java proxy wrapping Python object implementing interface | `PythonException` |

**Example**:
```java
// Python class MyTask with method run()
PythonExecutionEngine engine = ...;
Value pythonClass = engine.eval("class MyTask: def run(self): return 42");

Runnable proxy = (Runnable) PythonInterfaceGenerator.generateInterface(
    pythonClass, Runnable.class
);
proxy.run();  // Invokes Python run()
```

---

## PythonException

Thrown on Python evaluation errors or runtime failures.

| Property | Type | Description |
|----------|------|-------------|
| `message` | String | Error message from Python |
| `errorKind` | `ErrorKind` | Category of error |
| `cause` | Throwable | Underlying exception (often null) |

**ErrorKind Enum**:

| Kind | Meaning | Typical Recovery |
|------|---------|------------------|
| `EXECUTION_ERROR` | Python code raised exception (ZeroDivisionError, ValueError, etc.) | Catch, log, use default value |
| `CONTEXT_ERROR` | Context pool exhausted or context creation failed | Increase pool size |
| `RUNTIME_NOT_AVAILABLE` | GraalPy runtime not available on classpath | Verify Maven dependency |
| `TYPE_ERROR` | Result type does not match expected type (e.g., evalToDouble on non-numeric) | Check Python expression |

**Methods**:

| Method | Returns | Description |
|--------|---------|-------------|
| `getErrorKind()` | `ErrorKind` | Get error category |
| `getMessage()` | `String` | Get error message |
| `getCause()` | `Throwable` | Get underlying exception (may be null) |

**Example**:
```java
try {
    engine.evalToDouble("'not a number'");
} catch (PythonException e) {
    System.err.println("Kind: " + e.getErrorKind());  // TYPE_ERROR
}
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-graalpy</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

---

## Import Statements

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonContextPool;
import org.yawlfoundation.yawl.graalpy.PythonBytecodeCache;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;
import org.yawlfoundation.yawl.graalpy.PythonVirtualEnvironment;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionContext;
import org.yawlfoundation.yawl.graalpy.TypeMarshaller;
import org.yawlfoundation.yawl.graalpy.PythonInterfaceGenerator;
```

