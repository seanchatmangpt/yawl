# How to share a GraalJS or GraalPy context pool across multiple workflow tasks

## Problem

Creating a new `PythonExecutionEngine` or `JavaScriptExecutionEngine` for each workflow task is expensive (contexts consume memory and startup time). You want to reuse a single engine and its context pool across multiple tasks.

## Solution

Create the engine once as an application singleton (or dependency-injected bean), and inject it into all tasks that need it.

## Using Spring dependency injection (recommended)

```java
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonSandboxConfig;

@Service
public class PolyglotEngineProvider {

    /**
     * Singleton JavaScript engine shared across all tasks.
     * Spring manages the lifecycle and ensures shutdown on app termination.
     */
    @Bean(destroyMethod = "close")
    public JavaScriptExecutionEngine javaScriptExecutionEngine() {
        return JavaScriptExecutionEngine.builder()
            .contextPoolSize(8)
            .sandboxConfig(JavaScriptSandboxConfig.standard())
            .build();
    }

    /**
     * Singleton Python engine shared across all tasks.
     */
    @Bean(destroyMethod = "close")
    public PythonExecutionEngine pythonExecutionEngine() {
        return PythonExecutionEngine.builder()
            .poolSize(8)
            .sandboxConfig(PythonSandboxConfig.standard())
            .build();
    }
}
```

Then inject into your task handlers:

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.yawlfoundation.yawl.core.AbstractYawlTask;
import org.yawlfoundation.yawl.core.WorkItem;

public class DataValidationTask extends AbstractYawlTask {
    private final JavaScriptExecutionEngine jsEngine;
    private final PythonExecutionEngine pythonEngine;

    @Autowired
    public DataValidationTask(
            JavaScriptExecutionEngine jsEngine,
            PythonExecutionEngine pythonEngine) {
        this.jsEngine = jsEngine;
        this.pythonEngine = pythonEngine;
    }

    @Override
    protected void executeTask(WorkItem item) {
        try {
            // Both engines share a single context pool per engine type
            // Requests are queued if no free contexts available
            Map<String, Object> validation = jsEngine.evalToMap(
                "({ valid: true, errors: [] })"
            );

            // Each engine has independent pool — can use both concurrently
            double score = pythonEngine.evalToDouble(
                "0.95"
            );

        } catch (Exception e) {
            failTask(item, "Validation error: " + e.getMessage());
        }
    }

    // No need to close — Spring manages lifecycle
}
```

## Using a static factory pattern (non-Spring)

If you don't use Spring, create a static holder for the shared engines:

```java
public class PolyglotEngineFactory {
    private static final JavaScriptExecutionEngine JS_ENGINE;
    private static final PythonExecutionEngine PY_ENGINE;

    static {
        // Initialize engines once when class is loaded
        JS_ENGINE = JavaScriptExecutionEngine.builder()
            .contextPoolSize(8)
            .sandboxConfig(JavaScriptSandboxConfig.standard())
            .build();

        PY_ENGINE = PythonExecutionEngine.builder()
            .poolSize(8)
            .sandboxConfig(PythonSandboxConfig.standard())
            .build();

        // Register shutdown hook to clean up resources
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            JS_ENGINE.close();
            PY_ENGINE.close();
        }));
    }

    public static JavaScriptExecutionEngine getJavaScriptEngine() {
        return JS_ENGINE;
    }

    public static PythonExecutionEngine getPythonEngine() {
        return PY_ENGINE;
    }
}
```

Usage in task handlers:

```java
public class MyWorkflowTask extends AbstractYawlTask {

    @Override
    protected void executeTask(WorkItem item) {
        JavaScriptExecutionEngine engine = PolyglotEngineFactory.getJavaScriptEngine();
        try {
            Object result = engine.eval("1 + 1");
            // Process result
        } catch (JavaScriptException e) {
            failTask(item, "JS error: " + e.getMessage());
        }
    }
}
```

## Monitoring the context pool

Access pool statistics to monitor usage:

```java
public class PoolMonitor {
    private final JavaScriptExecutionEngine engine;

    public PoolMonitor(JavaScriptExecutionEngine engine) {
        this.engine = engine;
    }

    public void printPoolStats() {
        JavaScriptContextPool pool = engine.getContextPool();

        System.out.println("=== Context Pool Statistics ===");
        System.out.println("Total contexts: " + pool.getPoolSize());
        System.out.println("Borrowed contexts: " + pool.getBorrowedCount());
        System.out.println("Idle contexts: " + pool.getIdleCount());
        System.out.println("Available: " + (pool.getPoolSize() - pool.getBorrowedCount()));
    }

    /**
     * Log pool stats every 10 seconds (useful for diagnosing pool exhaustion)
     */
    public void startMonitoring(ExecutorService executor) {
        executor.scheduleAtFixedRate(
            this::printPoolStats,
            10, 10, TimeUnit.SECONDS
        );
    }
}
```

## Preloading shared scripts

Load scripts once in all pooled contexts to reduce per-task overhead:

```java
@Service
public class RuleEngineService {
    private final JavaScriptExecutionEngine engine;

    @Autowired
    public RuleEngineService(JavaScriptExecutionEngine engine) {
        this.engine = engine;
    }

    @PostConstruct
    public void loadSharedRules() {
        try {
            // Load rules.js into ALL contexts in the pool
            // This runs in parallel using virtual threads
            engine.evalScriptInAllContexts(
                Paths.get("classpath:business-rules.js")
            );
            System.out.println("Business rules loaded into all contexts");
        } catch (JavaScriptException e) {
            throw new RuntimeException("Failed to load shared rules", e);
        }
    }

    /**
     * Invoke a pre-loaded rule function
     * No need to load rules again — they're already in the context
     */
    public Map<String, Object> evaluateApproval(String submitter, double amount) {
        try {
            return engine.evalToMap(
                String.format(
                    "approveRequest('%s', %f)",
                    submitter, amount
                )
            );
        } catch (JavaScriptException e) {
            throw new RuntimeException("Rule evaluation failed", e);
        }
    }
}
```

## Thread-safe concurrent usage

Both `PythonExecutionEngine` and `JavaScriptExecutionEngine` are thread-safe. Multiple workflow tasks can safely call methods concurrently:

```java
@Service
public class ConcurrentRuleEngine {
    private final JavaScriptExecutionEngine engine;

    @Autowired
    public ConcurrentRuleEngine(JavaScriptExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * Safe to call from multiple threads simultaneously
     * Each thread gets a context from the pool
     */
    public Map<String, Object> evaluateInParallel(List<String> caseIds) {
        return caseIds.parallelStream()
            .collect(Collectors.toMap(
                caseId -> caseId,
                caseId -> {
                    try {
                        return engine.evalToMap(
                            "checkStatus('" + caseId + "')"
                        );
                    } catch (JavaScriptException e) {
                        throw new RuntimeException(e);
                    }
                }
            ));
    }
}
```

## Pool sizing recommendations

| Scenario | Pool Size | Rationale |
|----------|-----------|-----------|
| **Low concurrency** (< 5 concurrent tasks) | 2-4 | Minimal memory overhead |
| **Medium concurrency** (5-20 concurrent tasks) | 8 | One context per expected concurrent task |
| **High concurrency** (20+ concurrent tasks) | 16-32 | Account for contention; contexts are reused |
| **Production** | num_cpus * 2 | Good default for most servers |

## Complete example: Shared engines in a YAWL service

```java
@Service
public class WorkflowIntegrationService {
    private final JavaScriptExecutionEngine jsEngine;
    private final PythonExecutionEngine pythonEngine;
    private static final Logger LOGGER = LoggerFactory.getLogger(
        WorkflowIntegrationService.class
    );

    @Autowired
    public WorkflowIntegrationService(
            JavaScriptExecutionEngine jsEngine,
            PythonExecutionEngine pythonEngine) {
        this.jsEngine = jsEngine;
        this.pythonEngine = pythonEngine;
    }

    /**
     * Initialize shared scripts in all contexts once on startup
     */
    @PostConstruct
    public void initialize() {
        try {
            jsEngine.evalScriptInAllContexts(
                Paths.get("classpath:workflows/rules.js")
            );
            LOGGER.info("Workflow rules loaded into all JavaScript contexts");
        } catch (JavaScriptException e) {
            throw new RuntimeException("Failed to initialize workflow rules", e);
        }
    }

    /**
     * Evaluate a business rule using the shared JS engine
     * Called from multiple task handlers — guaranteed thread-safe
     */
    public String routeWorkflow(WorkItem item) throws Exception {
        try {
            Object result = jsEngine.invokeJsFunction(
                "determineRoute",
                item.getCaseId(),
                item.getDataValue("amount")
            );
            return (String) result;
        } catch (JavaScriptException e) {
            LOGGER.error("Routing failed for case {}", item.getCaseId(), e);
            throw new Exception("Workflow routing error", e);
        }
    }

    /**
     * Analyze case data using Python — uses shared pool
     */
    public Map<String, Object> analyzeCase(String caseId) throws Exception {
        try {
            return pythonEngine.evalToMap(
                String.format(
                    """
                    import json
                    result = {
                        'case_id': '%s',
                        'complexity': 'high',
                        'recommended_action': 'escalate'
                    }
                    json.dumps(result)
                    """,
                    caseId
                )
            );
        } catch (PythonException e) {
            LOGGER.error("Analysis failed for case {}", caseId, e);
            throw new Exception("Case analysis error", e);
        }
    }
}
```

## Notes

- **Singleton pattern**: Create engines once at application startup; reuse across all tasks.
- **Pool size**: Set pool size to match expected concurrent tasks; too small causes queuing, too large wastes memory.
- **Spring shutdown**: The `destroyMethod = "close"` bean property ensures proper cleanup on application termination.
- **Thread safety**: Both engines are thread-safe; pool handles context borrowing and return automatically.
- **Script preloading**: Use `evalScriptInAllContexts()` to load shared rule files once instead of on every task.

## Related guides

- [Execute a Python script](./execute-python-script.md)
- [Execute JavaScript workflow rules](./execute-js-in-workflow-task.md)
- [Pass Java objects to scripts](./pass-java-objects-to-script.md)
