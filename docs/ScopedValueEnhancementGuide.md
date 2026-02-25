# YAWL ScopedValue + OpenTelemetry Integration Guide

## Overview

This guide documents the enhanced observability features in YAWL that integrate ScopedValue context propagation with OpenTelemetry tracing and virtual thread monitoring. The implementation provides comprehensive observability capabilities for workflow execution with minimal overhead.

## Key Components

### 1. Enhanced YAWLTracing
- ScopedValue context propagation for virtual threads
- Automatic case ID inclusion in spans
- Virtual thread metrics collection
- Integration with Andon alert system

### 2. VirtualThreadPool Integration
- Context-aware task submission
- Virtual thread monitoring and metrics
- Performance optimization features
- Automatic cost tracking

### 3. ObservabilityIntegration
- Unified observability layer
- Context-aware monitoring
- Andon alert integration
- Performance optimization

### 4. Enhanced YNetRunner ScopedValue
- WORKFLOW_METADATA and OPERATION_CONTEXT scoped values
- CASE_CONTEXT for backward compatibility
- Automatic context inheritance
- Comprehensive validation

## Architecture

### Scoped Value Types

| ScopedValue | Type | Purpose |
|-------------|------|---------|
| `CASE_CONTEXT` | `String` | YAWL case identifier (existing) |
| `WORKFLOW_METADATA` | `WorkflowMetadata` | Specification and tenant information |
| `OPERATION_CONTEXT` | `OperationContext` | Operation tracking information |

### Data Structures

#### 1. `ExecutionContext`
Combines all scoped values for comprehensive context management:

```java
public record ExecutionContext(
        String caseID,
        WorkflowMetadata workflowMetadata,
        OperationContext operationContext
)
```

#### 2. `WorkflowMetadata`
Immutable specification and tenant metadata:

```java
public record WorkflowMetadata(
        String specId,
        String version,
        String tenant
)
```

#### 3. `OperationContext`
Immutable operation tracking information:

```java
public record OperationContext(
        String operationType,
        Instant startTime,
        String correlationId
)
```

## Usage Patterns

### Basic Context Binding

```java
// Create execution context
YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(
    "CASE-12345",
    "loanapproval:2.1",
    "acme-corp",
    "KICK",
    "corr-67890"
);

// Execute with context
String result = YNetRunner.executeWithContext(context, () -> {
    // Access contexts
    String caseId = YNetRunner.CASE_CONTEXT.get();
    String tenant = YNetRunner.WORKFLOW_METADATA.get().tenant();
    String operation = YNetRunner.OPERATION_CONTEXT.get().operationType();

    // Perform work
    return "operation-complete";
});
```

### Context Inheritance in Virtual Threads

```java
YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(
    "CASE-67890",
    "approval:1.5",
    "enterprise-corp",
    "CONTINUE",
    "corr-24680"
);

// Context automatically inherited by child threads
String result = YNetRunner.executeWithContext(context, () -> {
    // Child virtual thread
    Thread virtualThread = Thread.ofVirtual()
        .name("yawl-worker")
        .unstarted(() -> {
            // Context automatically available
            String caseId = YNetRunner.CASE_CONTEXT.get();
            String tenant = YNetRunner.WORKFLOW_METADATA.get().tenant();
            return processWork();
        });

    virtualThread.start();
    return virtualThread.join();
});
```

### Nested Context Propagation

```java
// Outer context
YNetRunner.ExecutionContext outerContext = YNetRunner.bindExecutionContext(
    "CASE-NESTED",
    "process:3.0",
    "tenant-outer",
    "KICK",
    "corr-outer"
);

// Execute with nested contexts
YNetRunner.executeWithContext(outerContext, () -> {
    // Inner context can override operation type
    YNetRunner.ExecutionContext innerContext =
        YNetRunner.bindExecutionContext(
            "CASE-NESTED",  // Same case
            "process:3.0",
            "tenant-outer",
            "TASK_COMPLETE",  // Different operation
            "corr-inner"
        );

    return YNetRunner.executeWithContext(innerContext, () -> {
        // Both contexts available
        return "nested-operation-complete";
    });
});
```

### Validation and Cleanup

```java
try {
    // Bind and execute in scope
    YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(...);

    ScopedValue.where(YNetRunner.CASE_CONTEXT, context.caseID(),
        ScopedValue.where(YNetRunner.WORKFLOW_METADATA, context.workflowMetadata(),
            ScopedValue.where(YNetRunner.OPERATION_CONTEXT, context.operationContext(),
                () -> {
                    // Validate contexts
                    YNetRunner.validateExecutionContext();

                    // Perform work
                    return "success";
                }
            )
        )
    );

    // Context automatically cleaned up after scope exits
} catch (Exception e) {
    // Handle errors
}
```

## Features

### 1. Automatic Context Propagation

- **Virtual Thread Inheritance**: Child virtual threads automatically inherit all scoped values
- **StructuredTaskScope Integration**: Works with Java 25's structured concurrency
- **No ThreadLocal Leaks**: Automatic cleanup when scope exits

### 2. Comprehensive Context Management

- **Workflow Metadata**: Specification ID, version, and tenant information
- **Operation Context**: Operation type, start time, and correlation ID
- **Case Context**: Existing YAWL case identifier (backward compatible)

### 3. Validation and Safety

- **Runtime Validation**: `validateExecutionContext()` checks all contexts are present
- **Immutability**: All records are immutable and thread-safe
- **Null Safety**: Comprehensive null and blank value validation

### 4. Flexible Creation Patterns

#### Workflow Metadata Creation

```java
// With explicit version
YNetRunner.WorkflowMetadata metadata1 =
    new YNetRunner.WorkflowMetadata("loanapproval", "2.1", "acme-corp");

// Auto-extract version from spec ID
YNetRunner.WorkflowMetadata metadata2 =
    YNetRunner.WorkflowMetadata.fromSpecId("loanapproval:2.1", "acme-corp");

// Auto-assign default version
YNetRunner.WorkflowMetadata metadata3 =
    YNetRunner.WorkflowMetadata.fromSpecId("simple", "acme-corp");
```

#### Operation Context Creation

```java
// With current time
YNetRunner.OperationContext op1 =
    YNetRunner.OperationContext.of("KICK", "corr-123");

// With specific time
Instant customTime = Instant.parse("2024-01-01T00:00:00Z");
YNetRunner.OperationContext op2 =
    new YNetRunner.OperationContext("CONTINUE", customTime, "corr-456");

// Update operation type
YNetRunner.OperationContext op3 = op2.withOperationType("COMPLETE");
```

## Best Practices

### 1. Context Binding

Always use `bindExecutionContext()` to create contexts consistently:

```java
// Good
YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(
    caseID, specId, tenant, operationType, correlationId
);

// Avoid - manual context creation is error-prone
YNetRunner.ExecutionContext context = new ExecutionContext(
    caseID,
    new WorkflowMetadata(specId, version, tenant),
    new OperationContext(operationType, Instant.now(), correlationId)
);
```

### 2. Scope Management

Use ScopedValue.where() for proper scope management:

```java
// Good
ScopedValue.where(CASE_CONTEXT, caseID,
    ScopedValue.where(WORKFLOW_METADATA, metadata,
        ScopedValue.where(OPERATION_CONTEXT, operation,
            () -> performWork()
        )
    )
);

// Avoid - can lead to context leaks
CASE_CONTEXT.bind(caseID);
try {
    performWork();
} finally {
    CASE_CONTEXT.reset();
}
```

### 3. Virtual Thread Usage

Use virtual threads for I/O-bound operations to maximize concurrency:

```java
// Good for I/O operations
String result = YNetRunner.executeWithContext(context, () -> {
    return YNetRunner.executeInChildThread(() -> {
        // Network/database operations
        return fetchDataFromRemote();
    });
});

// Use for CPU-bound operations only when necessary
Thread virtualThread = Thread.ofVirtual()
    .name("cpu-task")
    .start(() -> performComputation());
```

### 4. Error Handling

Always validate context before use:

```java
try {
    YNetRunner.validateExecutionContext();
    // Safe to use contexts
    String caseId = YNetRunner.CASE_CONTEXT.get();
    // ...
} catch (IllegalStateException e) {
    // Handle missing context
    logger.error("Context not available: " + e.getMessage());
    throw new WorkflowException("Missing execution context", e);
}
```

## Performance Considerations

### 1. Memory Usage

- Scoped values are lightweight references
- No heap allocation per thread (unlike ThreadLocal)
- Automatic garbage collection when scope exits

### 2. Context Access Speed

- Direct field access for immutable values
- No synchronization required
- Virtual thread context switching is efficient

### 3. Concurrency

- Read-only access from multiple threads is safe
- Context binding is thread-local to the scope
- No contention during context access

## Testing

### Unit Tests

The implementation includes comprehensive unit tests in `YNetRunnerScopedValueTest.java`:

```java
@Test
void testContextInheritance() throws Exception {
    YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(
        "CASE-123", "spec:1.0", "tenant", "KICK", "corr"
    );

    String result = YNetRunner.executeWithContext(context, () -> {
        // Verify contexts are inherited
        assertEquals("CASE-123", YNetRunner.CASE_CONTEXT.get());
        assertEquals("tenant", YNetRunner.WORKFLOW_METADATA.get().tenant());
        return "success";
    });

    assertEquals("success", result);
}
```

### Integration Tests

Test context propagation across multiple virtual threads and nested scopes.

### Stress Tests

Test high-concurrency scenarios with thousands of virtual threads sharing contexts.

## Migration Guide

### Existing Code

Existing code using `CASE_CONTEXT` continues to work unchanged:

```java
// This still works (backward compatible)
ScopedValue.where(YNetRunner.CASE_CONTEXT, caseID, () -> {
    String currentCase = YNetRunner.CASE_CONTEXT.get();
    // ...
});
```

### New Features

To use enhanced features, update code to use new context management:

```java
// Before (ThreadLocal pattern)
ThreadLocal<String> currentCase = new ThreadLocal<>();

// After (ScopedValue pattern)
YNetRunner.ExecutionContext context = YNetRunner.bindExecutionContext(
    caseID, specId, tenant, operationType, correlationId
);
YNetRunner.executeWithContext(context, () -> {
    // Access contexts via ScopedValue
    String currentCase = YNetRunner.CASE_CONTEXT.get();
    String tenant = YNetRunner.WORKFLOW_METADATA.get().tenant();
    // ...
});
```

## Troubleshooting

### Common Issues

1. **IllegalStateException: CASE_CONTEXT is missing**
   - Ensure context is properly bound before use
   - Check that ScopedValue.callWhere() is used correctly

2. **Context not inherited by child threads**
   - Use `executeWithContext()` or `executeInChildThread()`
   - Ensure parent thread has context bound

3. **NullPointerException on context access**
   - Validate context with `validateExecutionContext()` before access
   - Check all context components are non-null

### Debug Tips

1. Use logging to track context binding:
```java
logger.info("Binding context: case={}, tenant={}, operation={}",
    context.caseID(),
    context.workflowMetadata().tenant(),
    context.operationContext().operationType());
```

2. Verify scope exit:
```java
try {
    // Work with context
} finally {
    logger.debug("Context scope exited");
}
```

## Future Enhancements

1. **Context Propagation Events**: Add listeners for context changes
2. **Context Caching**: Cache frequently used context values
3. **Context Analytics**: Track context usage patterns
4. **Context Serialization**: Support for context persistence

## Conclusion

The enhanced ScopedValue implementation provides a robust, thread-safe mechanism for context propagation in YAWL workflow execution. It eliminates ThreadLocal leaks, enables automatic context inheritance, and provides comprehensive context management while maintaining backward compatibility with existing code.

The implementation follows Java 25 best practices and is designed for high-performance, concurrent workflow execution with minimal overhead.