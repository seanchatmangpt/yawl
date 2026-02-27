# How to evaluate JavaScript workflow rules from a YAWL task handler

## Problem

You need to define routing rules, task guards, or data transformations in JavaScript and execute them within a workflow task handler.

## Solution

Create a `JavaScriptExecutionEngine`, load your JavaScript rules file, invoke functions, and retrieve results as typed values.

### Basic example: workflow routing rule

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;
import java.nio.file.Path;
import java.util.Map;

// Initialize engine (singleton in your application)
JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(4)
    .sandboxConfig(JavaScriptSandboxConfig.standard())
    .build();

// Load rules.js once at startup
Path rulesFile = Path.of("/app/workflows/loan-rules.js");
engine.evalScript(rulesFile);

// In your task handler, invoke a routing rule
Map<String, Object> decision = engine.evalToMap("""
    routeApplication({
        amount: 5000,
        applicantScore: 750,
        employmentStatus: 'employed'
    })
""");

String nextTask = (String) decision.get("nextTask");      // "approve"
String reason = (String) decision.get("reason");          // "Eligible for fast-track"
```

### JavaScript rules file (rules.js)

```javascript
// Load in engine via engine.evalScript(Path.of("rules.js"))

function routeApplication(application) {
    const { amount, applicantScore, employmentStatus } = application;
    
    if (applicantScore >= 750 && amount <= 10000 && employmentStatus === 'employed') {
        return {
            nextTask: 'approve',
            reason: 'Eligible for fast-track approval'
        };
    }
    
    if (applicantScore >= 650) {
        return {
            nextTask: 'review',
            reason: 'Manual review required'
        };
    }
    
    return {
        nextTask: 'reject',
        reason: 'Score below minimum threshold'
    };
}
```

### Invoke a JavaScript function

```java
// Call JavaScript function via invokeJsFunction
Object result = engine.invokeJsFunction("routeApplication", 
    Map.of(
        "amount", 5000,
        "applicantScore", 750,
        "employmentStatus", "employed"
    )
);

Map<String, Object> decision = (Map<String, Object>) result;
```

### Evaluate inline and get typed results

```java
// String result
String status = engine.evalToString("""
    const now = new Date();
    now.toISOString()
""");

// Double result
double score = engine.evalToDouble("""
    Math.random() * 100
""");

// Map result (automatic JSON parsing)
Map<String, Object> data = engine.evalAsJson("""
    {
        "processId": "LP-2024-001",
        "status": "in_progress",
        "progress": 45.5
    }
""");

// List result
List<Object> steps = engine.evalToList("""
    ['intake', 'verification', 'approval', 'disbursement']
""");
```

### Handle JavaScript exceptions

```java
import org.yawlfoundation.yawl.graaljs.JavaScriptException;

try {
    engine.evalToDouble("undefined.property");
} catch (JavaScriptException e) {
    System.err.println("JS error: " + e.getMessage());
    System.err.println("Kind: " + e.getErrorKind());  // EXECUTION_ERROR
    // Recovery: log, notify, or use fallback routing
}
```

### Run a script in all pooled contexts (preload shared state)

```java
// Load utility functions into all engine contexts
Path utilsFile = Path.of("/app/workflows/utils.js");
engine.evalScriptInAllContexts(utilsFile);

// Now all subsequent evaluations can use these utilities
String result = engine.evalToString("formatDate(new Date())");
```

### Use async/await for complex orchestration

```javascript
// JavaScript file: async-processor.js

async function processWithDelay(caseName, delayMs) {
    await new Promise(resolve => setTimeout(resolve, delayMs));
    return {
        caseName: caseName,
        processedAt: new Date().toISOString(),
        status: 'completed'
    };
}
```

```java
// In Java task handler
Map<String, Object> result = engine.evalToMap("""
    // Note: GraalJS evaluations are synchronous from Java perspective
    // Use them for business logic, not I/O operations
    const caseData = {
        id: 'CASE-001',
        status: 'processed'
    };
    caseData
""");
```

### Close the engine on shutdown

```java
engine.close();
```

## Tips

- **Load rules once**: Call `evalScript(Path)` at application startup or after rules change. Use the same engine instance.
- **Pass Java objects**: Use `invokeJsFunction` with Map/List arguments. For complex Java objects, use `@HostAccess` binding (see "How to pass Java objects").
- **Async/await**: JavaScript `async/await` is fully supported, but evaluations block the calling Java thread. Use for business logic, not I/O.
- **Errors are fatal**: A JavaScript error throws `JavaScriptException`. Catch and decide whether to retry, use a default, or propagate.

