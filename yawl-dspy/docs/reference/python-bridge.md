# Python Bridge Reference

## PythonDspyBridge

The main interface for executing DSPy programs from Java.

### Constructor

```java
public PythonDspyBridge()
```

Creates a new bridge with default GraalPy context.

### Methods

#### loadProgram

```java
public DspyProgram loadProgram(
    String name,
    String pythonPath,
    String className
) throws PythonException
```

Loads a DSPy program from a Python file.

| Parameter | Type | Description |
|------------|------|-------------|
| `name` | String | Program identifier |
| `pythonPath` | String | Path to Python file |
| `className` | String | DSPy module class name |

**Returns:** `DspyProgram` - The loaded program

**Throws:** `PythonException` - If loading fails

#### execute

```java
public DspyExecutionResult execute(
    String programName,
    Map<String, Object> inputs
) throws DspyProgramNotFoundException, PythonException
```

Executes a loaded program with inputs.

#### shutdown

```java
public void shutdown()
```

Gracefully shuts down the Python context.

## DspyProgram

Represents a loaded DSPy program.

### Methods

```java
public String name()
public String className()
public DspyExecutionResult execute(Map<String, Object> inputs)
public DspyExecutionMetrics lastMetrics()
```

## DspyExecutionResult

Result of DSPy program execution.

### Fields

```java
public Map<String, Object> output()
public DspyExecutionMetrics metrics()
public boolean success()
public Optional<String> error()
```

## DspyExecutionMetrics

Performance metrics for execution.

### Fields

```java
public long executionTimeMs()
public long compilationTimeMs()
public int inputTokens()
public int outputTokens()
public boolean cacheHit()
public double qualityScore()
```

## PythonException

Exception for Python execution errors.

### Constructors

```java
public PythonException(String message, ErrorKind kind)
public PythonException(String message, ErrorKind kind, Throwable cause)
```

### ErrorKind Values

| Kind | Description |
|------|-------------|
| `SYNTAX_ERROR` | Python syntax error |
| `RUNTIME_ERROR` | Python runtime error |
| `IMPORT_ERROR` | Module import failed |
| `TYPE_ERROR` | Type conversion error |
| `TIMEOUT` | Execution timeout |
