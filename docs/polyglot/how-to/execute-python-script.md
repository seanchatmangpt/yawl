# How to execute a Python script and retrieve results in Java

## Problem

You need to evaluate Python code from a Java workflow task and retrieve typed results (string, number, map, or list).

## Solution

Create a `PythonExecutionEngine`, evaluate your Python expression, and retrieve the result using the appropriate typed method.

### Basic example: compute a process quality score

```java
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

// Initialize engine (do this once per application, reuse across tasks)
PythonExecutionEngine engine = PythonExecutionEngine.builder()
    .poolSize(4)
    .sandboxConfig(PythonSandboxConfig.standard())
    .build();

// Evaluate Python expression and get a double result
double qualityScore = engine.evalToDouble("""
    import json

    # Example: compute quality from case metrics
    metrics = {
        'total_cases': 100,
        'completed_cases': 95,
        'average_duration_days': 5
    }

    completion_rate = metrics['completed_cases'] / metrics['total_cases']
    quality_score = completion_rate * 100
    quality_score
""");

System.out.println("Quality score: " + qualityScore);  // 95.0
```

### Retrieve a map (dictionary) result

```java
// Evaluate Python and get a map of results
Map<String, Object> results = engine.evalToMap("""
    metrics = {
        'process_name': 'loan_application',
        'status': 'healthy',
        'throughput': 24.5
    }
    metrics
""");

String processName = (String) results.get("process_name");  // "loan_application"
double throughput = ((Number) results.get("throughput")).doubleValue();  // 24.5
```

### Retrieve a list result

```java
// Evaluate Python and get a list
List<Object> timestamps = engine.evalToList("""
    from datetime import datetime, timedelta

    now = datetime.now()
    timestamps = [
        now.isoformat(),
        (now + timedelta(hours=1)).isoformat(),
        (now + timedelta(hours=2)).isoformat()
    ]
    timestamps
""");

for (Object ts : timestamps) {
    System.out.println((String) ts);
}
```

### Handle Python exceptions

```java
import org.yawlfoundation.yawl.graalpy.PythonException;

try {
    double result = engine.evalToDouble("x / 0");  // ZeroDivisionError
} catch (PythonException e) {
    System.err.println("Python error: " + e.getMessage());
    System.err.println("Kind: " + e.getErrorKind());  // EXECUTION_ERROR
    // Typical recovery: use default value or rethrow as workflow exception
}
```

### Use typed evaluation for safety

Always use the specific `evalToX` method matching your expected type:

```java
// Prefer this
String name = engine.evalToString("'Alice'");

// Over generic eval (requires manual casting and type checking)
Object obj = engine.eval("'Alice'");
String name = (String) obj;
```

### Close the engine when done

```java
// In application shutdown or after workflow completion
engine.close();
```

## Tips

- **Reuse the engine**: Create it once at application startup and inject as a singleton (Spring `@Bean`).
- **Use standard sandbox for file access**: If your Python code reads workflow logs, use `PythonSandboxConfig.standard()`. Use `strict()` for untrusted scripts.
- **Access workflow data**: Pass Java objects via Context bindings (see "How to pass Java objects to GraalPy and GraalJS scripts").
- **Performance**: The first call to `engine.eval()` includes compilation overhead (~100-500ms). Subsequent calls are faster.

