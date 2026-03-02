# ScopedValue YEngine Migration Guide

## Overview

This guide explains how to migrate from ThreadLocal-based YEngine context management to Java 25's ScopedValue-based implementation.

## Why Migrate to ScopedValue?

### Benefits of ScopedValue over ThreadLocal:

1. **Virtual Thread Compatibility**: ScopedValue works seamlessly with virtual threads, while ThreadLocal can cause memory leaks and poor performance
2. **Automatic Cleanup**: Values are automatically cleaned up when the scope exits
3. **Immutable Binding**: Values are immutable within a scope, preventing accidental modifications
4. **Structured Concurrency**: Better integration with structured concurrency features
5. **No Memory Leaks**: Unlike ThreadLocal, ScopedValue doesn't require explicit cleanup
6. **Better Performance**: Especially under high concurrency scenarios

## Migration Steps

### Step 1: Replace ThreadLocal with ScopedEngineContext

#### Before (ThreadLocal):
```java
// Old way using ThreadLocal
public class OldEngineManager {
    private static final ThreadLocal<YEngine> engineThreadLocal = new ThreadLocal<>();

    public static void setEngine(YEngine engine) {
        engineThreadLocal.set(engine);
    }

    public static YEngine getEngine() {
        YEngine engine = engineThreadLocal.get();
        if (engine == null) {
            throw new IllegalStateException("No engine bound to thread");
        }
        return engine;
    }

    public static void clearEngine() {
        engineThreadLocal.remove();
    }
}
```

#### After (ScopedValue):
```java
// New way using ScopedValue
public class ScopedEngineContext {
    private static final ScopedValue<YEngine> ENGINE = ScopedValue.newInstance();

    public static <T> T withEngine(YEngine engine, Supplier<T> action) {
        return ScopedValue.where(ENGINE, engine).call(action::get);
    }

    public static YEngine current() {
        try {
            return ENGINE.get();
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                "No YAWL bound in current scope. " +
                "Ensure execution is within a withEngine() scope.",
                e
            );
        }
    }

    public static boolean isEngineBound() {
        return ENGINE.isBound();
    }
}
```

### Step 2: Update Service Classes

#### Before:
```java
@Service
public class AgentEngineService {
    @Autowired
    private ThreadLocal<YEngine> engineThreadLocal;

    public void processWorkItem(WorkItem item) {
        YEngine engine = engineThreadLocal.get();
        // Process item...
    }
}
```

#### After:
```java
@Service
public class AgentEngineService {
    public void processWorkItem(WorkItem item) {
        return ScopedEngineContext.withEngine(getEngine(), () -> {
            // Process item with engine context
            processWithEngine(item);
            return null;
        });
    }

    private void processWithEngine(WorkItem item) {
        YEngine engine = ScopedEngineContext.current();
        // Process item...
    }
}
```

### Step 3: Update Controller Classes

#### Before:
```java
@RestController
public class AgentController {
    @Autowired
    private AgentEngineService engineService;

    @PostMapping("/workitems")
    public ResponseEntity<?> createWorkItem(@RequestBody WorkItemCreateDTO dto) {
        YEngine engine = engineService.getEngine();
        // Create work item...
    }
}
```

#### After:
```java
@RestController
public class AgentController {
    @Autowired
    private AgentEngineService engineService;

    @PostMapping("/workitems")
    public ResponseEntity<?> createWorkItem(@RequestBody WorkItemCreateDTO dto) {
        return engineService.createWorkItem(dto);
    }
}

@Service
public class AgentEngineService {
    public ResponseEntity<?> createWorkItem(WorkItemCreateDTO dto) {
        return ScopedEngineContext.withEngine(getEngine(), () -> {
            // Create work item with engine context
            return doCreateWorkItem(dto);
        });
    }
}
```

### Step 4: Update Task Executors

#### Before:
```java
public class WorkItemProcessor implements Runnable {
    @Override
    public void run() {
        YEngine engine = ThreadLocalYEngineManager.getEngine();
        // Process work item...
    }
}
```

#### After:
```java
public class WorkItemProcessor implements Runnable {
    private final YEngine engine;

    public WorkItemProcessor(YEngine engine) {
        this.engine = engine;
    }

    @Override
    public void run() {
        ScopedEngineContext.withEngine(engine, () -> {
            // Process work item...
            return null;
        });
    }
}
```

### Step 5: Update Exception Handling

#### Before:
```java
try {
    ThreadLocalYEngineManager.setEngine(engine);
    // Do work...
} finally {
    ThreadLocalYEngineManager.clearEngine();
}
```

#### After:
```java
// No need for manual cleanup - ScopedValue handles it automatically
ScopedEngineContext.withEngine(engine, () -> {
    // Do work...
    return null;
});
```

## Migration Patterns

### Pattern 1: Simple Replacement
```java
// Old
YEngine engine = ThreadLocalYEngineManager.getEngine();
processWorkItem(engine);

// New
ScopedEngineContext.withEngine(getEngine(), () -> {
    processWorkItem();
    return null;
});
```

### Pattern 2: Nested Operations
```java
// Old
try {
    ThreadLocalYEngineManager.setEngine(engine1);
    // Do work 1
    ThreadLocalYEngineManager.setEngine(engine2);
    // Do work 2
} finally {
    ThreadLocalYEngineManager.clearEngine();
}

// New
ScopedEngineContext.withEngine(engine1, () -> {
    // Do work 1
    return ScopedEngineContext.withEngine(engine2, () -> {
        // Do work 2
        return null;
    });
});
```

### Pattern 3: Virtual Threads
```java
// Old (problematic with virtual threads)
Thread.ofVirtual().start(() -> {
    YEngine engine = ThreadLocalYEngineManager.getEngine();
    // Virtual thread doesn't inherit ThreadLocal values
});

// New (works perfectly with virtual threads)
Thread.ofVirtual().start(() -> {
    ScopedEngineContext.withEngine(engine, () -> {
        // Virtual thread inherits ScopedValue automatically
        return null;
    });
});
```

## Testing the Migration

### Test Scenarios

1. **Basic Functionality**: Verify engine context is properly bound and retrieved
2. **Nested Scopes**: Test that nested operations work correctly
3. **Error Handling**: Ensure proper exception handling and cleanup
4. **Concurrency**: Test with multiple threads and virtual threads
5. **Memory Usage**: Verify no memory leaks

### Example Test
```java
@Test
void testScopedValueMigration() {
    ScopedValueYEngine engine = new ScopedValueYEngine();

    // Test basic binding
    String result = ScopedEngineContext.withEngine(engine, () -> {
        assertSame(engine, ScopedEngineContext.current());
        return "success";
    });

    assertEquals("success", result);

    // Test nested binding
    ScopedEngineContext.withEngine(engine, () -> {
        String nestedResult = ScopedEngineContext.inNestedScope(() -> {
            assertSame(engine, ScopedEngineContext.current());
            return "nested";
        });
        assertEquals("nested", nestedResult);
        return null;
    });
}
```

## Performance Considerations

### Expected Performance Improvements

1. **Memory Usage**: Reduced memory footprint due to automatic cleanup
2. **Virtual Thread Performance**: Better scalability with virtual threads
3. **Concurrency**: Improved throughput in high-concurrency scenarios
4. **Garbage Collection**: Fewer GC cycles due to less object creation

### Benchmark Results

The provided `ScopedValueBenchmark` class can be used to compare performance:

```bash
# Run the benchmark
mvn clean install
java -jar target/benchmarks.jar

# Expected results should show:
# - Comparable or better performance for basic operations
# - Significantly better performance for virtual thread scenarios
# - Better memory usage patterns
```

## Troubleshooting

### Common Issues

1. **IllegalStateException**: No engine bound in current scope
   - Solution: Ensure all engine operations are within `withEngine()` scopes

2. **NullPointerException**: Engine is null
   - Solution: Verify engine is properly initialized before binding

3. **Memory Leaks**: Still seeing memory issues
   - Solution: Check for any remaining ThreadLocal usage

### Debug Tips

1. **Enable Debug Logging**: Add logging to track scope entry/exit
2. **Memory Profiling**: Use VisualVM or YourKit to monitor memory usage
3. **Thread Dumps**: Check for proper virtual thread usage

## Migration Checklist

- [ ] Replace all ThreadLocal usage with ScopedEngineContext
- [ ] Update service classes to use withEngine pattern
- [ ] Update controller and REST endpoint classes
- [ ] Update task executors and background processes
- [ ] Update exception handling patterns
- [ ] Run comprehensive test suite
- [ ] Run performance benchmarks
- [ ] Monitor memory usage in production
- [ ] Update documentation and examples

## Rollback Plan

If issues arise during migration:

1. **Feature Flag**: Wrap ScopedValue usage in a feature flag
2. **Gradual Rollback**: Migrate one module at a time
3. **Dual Implementation**: Run both implementations in parallel
4. **Monitoring**: Use metrics to compare performance

## Resources

- [Java 25 ScopedValue Documentation](https://docs.oracle.com/en/java/javase/25/core/structured-concurrency.html)
- [Virtual Threads Guide](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [YAWL Engine Documentation](https://yawl.sourceforge.net/)
- [Migration Test Suite](../src/test/java/org/yawlfoundation/yawl/engine/)

## Support

For migration issues or questions:
- Review the test classes in the package
- Check the migration examples above
- Run the benchmark to compare performance
- Consult the YAWL development team