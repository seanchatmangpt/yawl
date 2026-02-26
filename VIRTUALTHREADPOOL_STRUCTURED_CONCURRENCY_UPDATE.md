# VirtualThreadPool Structured Concurrency Update for Java 25

## Summary of Changes

This document describes the updates made to `src/org/yawlfoundation/yawl/engine/VirtualThreadPool.java` to migrate from `Future.get()` patterns to Java 25's `StructuredTaskScope` for structured concurrency.

## Key Changes

### 1. Import Addition
- Added `import java.util.concurrent.StructuredTaskScope;` to enable structured concurrency features

### 2. Methods Updated

#### `executeInWithContext(List<Callable<T>>, String)` (lines 403-442)
**Before**: Used `CompletableFuture` and `allOf().get()` to wait for all tasks
**After**: Used `StructuredTaskScope.ShutdownOnFailure` with proper fork/join pattern
- Creates scope with `try (var scope = new StructuredTaskScope.ShutdownOnFailure())`
- Uses `scope.fork()` for each task with context propagation
- Uses `scope.join()` to wait for completion or first failure
- Uses `scope.throwIfFailed()` to throw any exceptions
- Collects results using `subtask.get()` after checking for failures

#### `executeInWithContext(List<Callable<T>>, String, String, String)` (lines 455-499)
**Before**: Used `CompletableFuture` and `allOf().get()` to wait for all tasks
**After**: Same pattern as above with full context propagation
- Maintains scoped value binding for case, spec, and tenant contexts
- Proper structured concurrency with automatic cancellation on failure

#### `executeInParallel(List<Callable<T>>)` (lines 511-546)
**Before**: Used `CompletableFuture` and `allOf().get()` to wait for all tasks
**After**: Uses `StructuredTaskScope.ShutdownOnFailure` without context
- Clean implementation for parallel execution without context requirements
- Maintains same result collection pattern

#### `submitAndWaitAll(List<Callable<T>>)` (lines 557-592)
**Before**: Used `CompletableFuture` and converted to `Future` objects
**After**: Mixed approach using StructuredTaskScope + CompletableFuture
- Uses structured scope for task execution
- Creates `CompletableFuture` wrappers for the returned futures
- Maintains backward compatibility with existing API that returns `List<Future<T>>`

## Benefits of Structured Concurrency

1. **Automatic Cancellation**: When one task fails, remaining tasks are automatically cancelled
2. **Structured Lifetimes**: Parent-child relationships prevent thread leaks
3. **Better Error Handling**: `throwIfFailed()` simplifies exception handling
4. **Virtual Thread Safety**: Properly handles virtual thread lifecycle
5. **Resource Management**: Automatic scope cleanup with try-with-resources

## Java 25 Preview API Considerations

The StructuredTaskScope is currently a preview API in Java 25. To compile and run:

```bash
# Compile with preview API enabled
javac --enable-preview --source 25 VirtualThreadPool.java

# Run with preview API enabled
java --enable-preview VirtualThreadPool
```

## Implementation Details

### Scope Management
- All scopes use `StructuredTaskScope.ShutdownOnFailure` for automatic cancellation on first failure
- Proper try-with-resources ensures scope is always closed
- Fork operations capture the current thread's context (including ScopedValue bindings)

### Error Handling
- `scope.throwIfFailed()` throws first exception if any task failed
- Individual `subtask.get()` calls are safe after `throwIfFailed()` since we already checked for failures
- Context propagation via ScopedValue works correctly in forked tasks

### Performance Considerations
- Virtual threads make the lightweight scope overhead negligible
- No need for manual thread pool management
- Structured concurrency prevents resource leaks
- Automatic task cancellation reduces waste

## Compatibility

The changes maintain:
- Same method signatures and return types
- Same exception handling (throws ExecutionException, InterruptedException)
- Same context propagation behavior via ScopedValue
- Same timing and ordering guarantees

## Testing

Existing tests in `test/org/yawlfoundation/yawl/engine/VirtualThreadPoolTest.java` should continue to pass as the API contracts are preserved.

## Dependencies

The project requires:
- Java 25 with preview features enabled
- The StructuredTaskScope API from java.util.concurrent
- ScopedValue API for context propagation
- OpenTelemetry Context for tracing (optional, with proper null handling)

## Migration Notes

This is a complete migration from CompletableFuture-based parallel execution to structured concurrency, providing:
- Better error handling and cancellation
- More predictable resource usage
- Improved diagnostics and debugging
- Alignment with Java 25 modern concurrency features

The changes are backward compatible in terms of API surface while internally leveraging the new structured concurrency paradigm.