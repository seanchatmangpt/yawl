# YEngine ThreadLocal to ScopedValue Migration Guide

## Overview

This guide explains how to migrate YEngine from ThreadLocal to ScopedValue for improved thread-safety and virtual thread support.

## What Changed

### Before (ThreadLocal)
```java
// ThreadLocal context with manual management
YEngine.setTenantContext(tenantContext);
try {
    // Business logic
    executeCaseOperation();
} finally {
    YEngine.clearTenantContext();
}
```

### After (ScopedValue)
```java
// ScopedValue with automatic inheritance
YEngine.executeWithTenant(tenantContext, () -> {
    // Business logic - context automatically inherited by virtual threads
    executeCaseOperation();
});
```

## Key Benefits

1. **Virtual Thread Support**: ScopedValue automatically propagates to virtual threads
2. **Automatic Cleanup**: No manual context clearing required
3. **Type Safety**: Compile-time checking of context usage
4. **Structured Concurrency**: Better integration with structured task scopes
5. **Performance**: No thread-local overhead on virtual threads

## Migration Steps

### Step 1: Update Import Statements
Add ScopedValue import to your Java files:
```java
import java.util.concurrent.Callable;
import java.util.concurrent.ScopedValue;
```

### Step 2: Replace ThreadLocal Usage

#### Method 1: Simple Execution (Recommended)
```java
// OLD
YEngine.setTenantContext(tenantContext);
try {
    businessLogic();
} finally {
    YEngine.clearTenantContext();
}

// NEW
YEngine.executeWithTenant(tenantContext, () -> {
    businessLogic();
});
```

#### Method 2: Parallel Execution
```java
// OLD - Required manual context management in each thread
for (int i = 0; i < threadCount; i++) {
    new Thread(() -> {
        YEngine.setTenantContext(tenantContext);
        try {
            task.execute();
        } finally {
            YEngine.clearTenantContext();
        }
    }).start();
}

// NEW - Automatic inheritance
YEngine.executeParallel(tenantContext, tasks);
```

#### Method 3: Callable with Return Value
```java
// OLD
YEngine.setTenantContext(tenantContext);
try {
    String result = businessLogic();
    return result;
} finally {
    YEngine.clearTenantContext();
}

// NEW
return YEngine.executeWithTenant(tenantContext, () -> {
    return businessLogic();
});
```

### Step 3: Remove ThreadLocal References
1. Remove `ThreadLocal<TenantContext>` fields
2. Remove `setTenantContext()`, `getTenantContext()`, `clearTenantContext()` calls
3. Replace with ScopedValue-based methods

### Step 4: Test Migration
Run the provided tests to verify the migration:
```bash
./verify_scoped_value_inheritance.sh
```

## New API Methods

### YEngine ScopedValue Methods

```java
// Execute with tenant context (Callable)
public static <T> T executeWithTenant(TenantContext tenantContext, Callable<T> task)

// Execute with tenant context (Runnable)
public static void executeWithTenant(TenantContext tenantContext, Runnable task)

// Execute with tenant context (convenience)
public static void runWithTenant(TenantContext tenantContext, Runnable task)

// Parallel execution with context inheritance
public static <T> T[] executeParallel(TenantContext tenantContext, Callable<T>[] tasks)
```

### ScopedTenantContext Utility Methods

```java
// Get current context
public static TenantContext getTenantContext()

// Check if context exists
public static boolean hasTenantContext()

// Get context with validation
public static TenantContext requireTenantContext()

// Execute with context
public static <T> T runWithTenant(TenantContext tenantContext, Callable<T> task)
```

## Testing

### Provided Test Suite

1. **ScopedTenantContextTest**: Basic ScopedValue functionality
2. **VirtualThreadTenantInheritanceTest**: Virtual thread inheritance
3. **YEngineMigrationTest**: Migration compatibility
4. **TenantContextPerformanceTest**: Performance comparison

### Key Test Scenarios

- Virtual thread inheritance
- Context isolation between scopes
- Concurrent access
- Nested scopes
- Performance benchmarking
- Backward compatibility

## Best Practices

### 1. Always Use ScopedValue for New Code
```java
// Good - ScopedValue with automatic cleanup
YEngine.executeWithTenant(tenantContext, () -> {
    // Business logic
});

// Bad - Manual ThreadLocal management
YEngine.setTenantContext(tenantContext);
try {
    // Business logic
} finally {
    YEngine.clearTenantContext();
}
```

### 2. Prefer Immutable Context Objects
```java
public record TenantContext(String tenantId, Set<String> authorizedCases) {
    // Immutable, thread-safe
}
```

### 3. Handle Null Contexts Gracefully
```java
YEngine.executeWithTenant(tenantContext, () -> {
    if (ScopedTenantContext.hasTenantContext()) {
        // Use context
    } else {
        // Handle no context
    }
});
```

### 4. Use Structured Concurrency for Parallel Tasks
```java
YEngine.executeWithTenant(tenantContext, () -> {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        // Tasks automatically inherit context
        scope.fork(() -> task1());
        scope.fork(() -> task2());
        scope.join();
    }
});
```

## Migration Checklist

- [ ] Replace all ThreadLocal calls with ScopedValue
- [ ] Update all public API methods
- [ ] Remove ThreadLocal field declarations
- [ ] Add ScopedValue imports
- [ ] Run migration tests
- [ ] Run performance tests
- [ ] Update documentation
- [ ] Deprecate old ThreadLocal methods

## Troubleshooting

### Common Issues

1. **NullPointerException**: Check if context is null before using
   ```java
   // Fix
   if (ScopedTenantContext.hasTenantContext()) {
       // Use context
   }
   ```

2. **Context Not Inherited**: Ensure using ScopedValue.runWhere()
   ```java
   // Fix
   ScopedValue.runWhere(SCOPED_VALUE, context, task);
   ```

3. **Memory Leaks**: ScopedValue automatically cleans up

### Performance Considerations

- ScopedValue has no performance overhead on virtual threads
- For platform threads, performance is comparable to ThreadLocal
- Context switching is automatic and efficient

## Virtual Thread Benefits

### Automatic Inheritance
```java
YEngine.executeWithTenant(tenantContext, () -> {
    // Virtual threads inherit parent context automatically
    Thread.ofVirtual().start(() -> {
        Context context = ScopedTenantContext.getTenantContext();
        // Context available without passing explicitly
    });
});
```

### Structured Concurrency Support
```java
YEngine.executeWithTenant(tenantContext, () -> {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        // All child tasks inherit tenant context
        scope.fork(() -> processCase(case1));
        scope.fork(() -> processCase(case2));
        scope.join();
    }
});
```

## Conclusion

The migration from ThreadLocal to ScopedValue provides:
- Better thread-safety
- Virtual thread support
- Automatic cleanup
- Cleaner code
- Improved performance

The provided test suite ensures backward compatibility while enabling modern Java features.