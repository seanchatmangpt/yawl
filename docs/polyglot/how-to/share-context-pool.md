# How to share a GraalJS or GraalPy context pool across multiple workflow tasks

## Problem

You're executing many workflow tasks that each need to evaluate JavaScript or Python code. Creating a new engine per task is expensive; you want to reuse a shared pool.

## Solution

Create the execution engine once in your application bootstrap, inject it as a singleton (via Spring `@Bean` or static factory), and use the same engine instance across all tasks.

### Spring Bean approach (recommended)

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

@Configuration
public class PolyglotConfiguration {

    @Bean(destroyMethod = "close")
    public JavaScriptExecutionEngine jsEngine() {
        return JavaScriptExecutionEngine.builder()
            .contextPoolSize(8)  // Tune based on task concurrency
            .sandboxConfig(JavaScriptSandboxConfig.standard())
            .build();
    }

    @Bean(destroyMethod = "close")
    public PythonExecutionEngine pythonEngine() {
        return PythonExecutionEngine.builder()
            .poolSize(4)  // Python Contexts heavier than JS; use fewer
            .sandboxConfig(PythonSandboxConfig.standard())
            .build();
    }
}
```

### Inject into task handlers

```java
import org.springframework.stereotype.Service;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;

@Service
public class LoanRoutingTask {

    private final JavaScriptExecutionEngine jsEngine;

    public LoanRoutingTask(JavaScriptExecutionEngine jsEngine) {
        this.jsEngine = jsEngine;
    }

    public void executeTask(WorkItem workItem) throws YStateException {
        // Load rules once (first request)
        if (!rulesLoaded) {
            jsEngine.evalScript(Path.of("classpath:workflows/loan-rules.js"));
            rulesLoaded = true;
        }

        // Evaluate rules for this case
        Map<String, Object> decision = jsEngine.evalToMap("""
            routeApplication({
                amount: %d,
                score: %d
            })
        """.formatted(
            (int) workItem.getDataVariable("amount"),
            (int) workItem.getDataVariable("creditScore")
        ));

        String nextTask = (String) decision.get("nextTask");
        workItem.setData("routing_decision", nextTask);
    }
}
```

### Static factory approach (no Spring)

```java
public class ScriptEngineFactory {
    private static JavaScriptExecutionEngine jsEngine;
    private static PythonExecutionEngine pyEngine;

    static {
        jsEngine = JavaScriptExecutionEngine.builder()
            .contextPoolSize(8)
            .sandboxConfig(JavaScriptSandboxConfig.standard())
            .build();

        pyEngine = PythonExecutionEngine.builder()
            .poolSize(4)
            .sandboxConfig(PythonSandboxConfig.standard())
            .build();
    }

    public static JavaScriptExecutionEngine getJsEngine() {
        return jsEngine;
    }

    public static PythonExecutionEngine getPyEngine() {
        return pyEngine;
    }

    public static void shutdown() {
        jsEngine.close();
        pyEngine.close();
    }
}
```

### Use in task handler (non-Spring)

```java
public class DataTransformTask implements YawlTask {
    
    @Override
    public void handleTask(WorkItem workItem) throws YStateException {
        JavaScriptExecutionEngine engine = ScriptEngineFactory.getJsEngine();
        
        String result = engine.evalToString("""
            JSON.stringify({
                case_id: '%s',
                processed: true,
                timestamp: new Date().toISOString()
            })
        """.formatted(workItem.getCaseID()));
        
        workItem.setData("transform_result", result);
    }
}
```

### Monitor pool health

```java
public class PoolMonitor {
    
    public void logPoolStats(JavaScriptExecutionEngine engine) {
        JavaScriptContextPool pool = engine.getContextPool();
        
        System.out.println("Borrowed contexts: " + pool.getNumBorrowed());
        System.out.println("Idle contexts: " + pool.getNumIdle());
        System.out.println("Max pool size: " + pool.getMaxPoolSize());
    }
}
```

### Preload shared rules into all contexts

Use `evalScriptInAllContexts()` to load utility functions or shared code once:

```java
@PostConstruct
public void initializeRules(JavaScriptExecutionEngine engine) {
    // Load utils into all pooled contexts (runs in parallel via virtual threads)
    engine.evalScriptInAllContexts(Path.of("classpath:rules/utils.js"));
    engine.evalScriptInAllContexts(Path.of("classpath:rules/common.js"));
    
    // All subsequent eval calls can use these utilities
}
```

### Tune pool size

```java
// For GraalJS (lightweight contexts)
// Rule of thumb: poolSize = 2 × (expected concurrent tasks)
JavaScriptExecutionEngine jsEngine = JavaScriptExecutionEngine.builder()
    .contextPoolSize(16)  // 16 contexts for 8 concurrent tasks
    .build();

// For GraalPy (heavier contexts, ~50-200MB each)
// Rule of thumb: poolSize = expected concurrent tasks
PythonExecutionEngine pyEngine = PythonExecutionEngine.builder()
    .poolSize(4)  // 4 contexts for 4 concurrent tasks
    .build();
```

### Handle pool exhaustion

If all contexts are borrowed and a new request arrives:

```java
// GraalJS pool behavior
JavaScriptExecutionEngine engine = ...;  // Pool size = 4

// Concurrent requests > 4 will wait for a context to return
// Default timeout: (implementation-dependent, typically 30 seconds)

// If you hit timeout:
try {
    String result = engine.evalToString("1 + 1");
} catch (JavaScriptException e) {
    if (e.getErrorKind() == ErrorKind.CONTEXT_ERROR) {
        System.err.println("Pool exhausted; increase contextPoolSize");
    }
}
```

### Graceful shutdown

```java
@PreDestroy
public void shutdown() {
    // Called automatically by Spring
    jsEngine.close();  // Closes all contexts, releases resources
    pyEngine.close();
}
```

## Tips

- **Pool size matters**: Start with `2 × task_concurrency` for JS, `1 × task_concurrency` for Python.
- **Preload once**: Call `evalScriptInAllContexts()` at startup to load shared code.
- **Monitor for exhaustion**: Log pool stats periodically; increase size if you see high "Borrowed" count.
- **Don't forget close()**: If not using Spring, manually call `engine.close()` on shutdown.
- **Per-task state**: Don't store state in the engine; evaluate fresh code per task or pass state explicitly.

