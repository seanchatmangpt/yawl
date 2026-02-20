# Phase 5: Scoped Values & ThreadLocal Replacement (Java 25 Upgrade)

## Overview

Phase 5 replaces ad-hoc `ThreadLocal` usage with Java 25 `ScopedValue` across the YAWL engine and integration modules. ScopedValues are immutable, automatically inherited by forked virtual threads, and require no cleanup.

## Status: COMPLETE

All ScopedValue infrastructure is in place:
- `WorkflowContext` (Java 25 record) with `YEngine.WORKFLOW_CONTEXT` ScopedValue ✅
- `AgentContext` (Java 25 record) with `AgentContext.CURRENT` ScopedValue ✅
- Entry point binding in `YEngine.launchCase()` ✅
- Test coverage for propagation patterns ✅

## Key Components

### 1. WorkflowContext (Stateless Engine)

**File**: `/src/org/yawlfoundation/yawl/stateless/engine/WorkflowContext.java`

A Java 25 record carrying immutable case execution context:

```java
public record WorkflowContext(
    String caseID,
    String specID,
    int engineNbr,
    Instant startedAt
)
```

**Usage**:
```java
WorkflowContext ctx = WorkflowContext.of(caseID, specID, engineNbr);
ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
    // All code here and spawned virtual threads inherit ctx
    runner.start();
    return null;
});
```

### 2. AgentContext (Autonomous Agents)

**File**: `/src/org/yawlfoundation/yawl/integration/autonomous/AgentContext.java`

A Java 25 record for agent execution identity:

```java
public record AgentContext(
    String agentId,
    String agentName,
    String engineUrl,
    String sessionHandle
)
```

## Propagation Patterns

### Virtual Thread Automatic Inheritance

When a virtual thread is forked inside a `ScopedValue.callWhere()` scope, it automatically inherits the bound value:

```java
ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
    Thread vt = Thread.ofVirtual()
        .name("case-" + ctx.caseID())
        .start(() -> {
            // No explicit passing needed!
            WorkflowContext inherited = YEngine.WORKFLOW_CONTEXT.get();
        });
    vt.join();
    return null;
});
```

### StructuredTaskScope Pattern (Recommended)

The recommended Java 25 pattern for concurrent work:

```java
ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
    try (StructuredTaskScope.ShutdownOnFailure scope =
            new StructuredTaskScope.ShutdownOnFailure("case-tasks", Thread.ofVirtual().factory())) {
        // All subtasks inherit context automatically
        scope.fork(() -> processTask1());
        scope.fork(() -> processTask2());
        scope.join().throwIfFailed();
    }
    return null;
});
```

## Test Coverage

**File**: `/test/org/yawlfoundation/yawl/stateless/TestScopedValuePropagation.java`

Comprehensive test suite validates:
- ✅ WorkflowContext binding in current thread
- ✅ WorkflowContext propagation to spawned virtual threads
- ✅ Propagation through StructuredTaskScope
- ✅ AgentContext binding and propagation
- ✅ Multiple ScopedValue isolation
- ✅ Unbound access error handling
- ✅ Virtual thread name correlation with context

**Run tests**:
```bash
mvn test -Dtest=TestScopedValuePropagation -pl yawl-stateless
```

## Performance Implications

- **Memory**: ScopedValues are fixed-size; no cleanup overhead
- **CPU**: Context binding is O(1); no synchronization or locks
- **GC**: No pinning of threads; virtual threads can be GC'd freely
- **Virtual threads**: Context automatically inherited (no parameter passing overhead)

## Best Practices

### 1. Bind Early, Access Late
```java
// Good: Bind context at entry point
ScopedValue.callWhere(YEngine.WORKFLOW_CONTEXT, ctx, () -> {
    executeWorkflow();
    return null;
});
```

### 2. Use StructuredTaskScope for Concurrency
```java
try (StructuredTaskScope.ShutdownOnFailure scope = ...) {
    scope.fork(() -> task1()); // context inherited
    scope.fork(() -> task2()); // context inherited
    scope.join().throwIfFailed();
}
```

### 3. Name Virtual Threads for Observability
```java
Thread vt = Thread.ofVirtual()
    .name("case-" + YEngine.WORKFLOW_CONTEXT.get().caseID())
    .start(() -> { ... });
```

## References

- **Java 25 ScopedValue**: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html
- **StructuredTaskScope**: https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/concurrent/StructuredTaskScope.html
- **Virtual Threads**: https://openjdk.org/jeps/436
- **YAWL Rule File**: `/rules/java25/modern-java.md`

## Summary

Phase 5 completes the ThreadLocal → ScopedValue transition. Virtual threads spawned in case execution automatically inherit workflow context without explicit passing.
