# GIL Handling for Long-Running TPOT2 Operations

## Overview

This document describes the Global Interpreter Lock (GIL) handling implementation for long-running TPOT2 optimization operations in the YAWL ML Bridge.

## Problem Statement

TPOT2 optimization is a computationally intensive genetic programming algorithm that can run for minutes to hours. The original implementation had several GIL-related issues:

1. **Blocking GIL**: Python code runs while holding the GIL, blocking other Python operations
2. **No timeout handling**: Operations could run indefinitely
3. **No progress monitoring**: No way to track optimization progress
4. **Thread safety concerns**: Concurrent access to Python objects

## Solution Architecture

### 1. Background Thread Execution

TPOT2 operations now run in separate background threads using `std::thread::spawn()`:

```rust
// Start background thread for TPOT2 optimization
let thread_operation_id = operation_id.clone();
thread::spawn(move || {
    let result = run_tpot2_optimization(...);
    // Handle result
});
```

### 2. GIL Management

- **GIL Acquisition**: Python operations still use `Python::with_gil()`, but in background threads
- **GIL Release**: The GIL is automatically released when the thread completes
- **Thread Safety**: Each thread has its own Python interpreter context

### 3. Operation Tracking

Active operations are tracked in a global `RwLock<HashMap>`:

```rust
static ACTIVE_OPERATIONS: RwLock<std::collections::HashMap<String, OperationHandle>> = 
    RwLock::new(std::collections::HashMap::new());
```

### 4. Timeout Handling

Operations use Unix signals for timeout enforcement:

```python
def timeout_handler(signum, frame):
    raise TimeoutException("Optimization timed out")

signal.signal(signal.SIGALRM, timeout_handler)
signal.alarm(max_time_mins * 60)
```

## API Changes

### New Functions Added

1. **`tpot2_monitor_operation(OperationId)`**
   - Check operation progress and status
   - Returns elapsed time, timeout remaining, and completion percentage

2. **`tpot2_cancel_operation(OperationId)`**
   - Cancel running operations
   - Remove from active operations tracking

3. **`tpot2_get_active_operations()`**
   - List all active operations with their status

### Modified Functions

1. **`tpot2_optimize()`**
   - Now returns an operation ID instead of running synchronously
   - Creates background thread for optimization

## Thread Safety Considerations

### Data Structures

- **`RwLock<HashMap>`**: Thread-safe access to active operations
- **`OperationHandle`**: Contains thread-safe fields (timestamp, duration)
- **`crossbeam_channel`**: For thread-safe communication

### Concurrent Access Patterns

1. **Single Writer**: Only one thread can modify active operations at a time
2. **Multiple Readers**: Multiple threads can read operation status concurrently
3. **Atomic Cleanup**: Timed-out operations are cleaned up automatically

### Memory Management

- **Operation Handles**: Automatically removed when complete
- **Channel Senders**: Dropped when thread completes
- **Python Objects**: Properly managed by PyO3 in their respective threads

## Performance Considerations

### GIL Impact

- **Background Threads**: Python operations don't block the main thread
- **Parallel Execution**: Multiple Python operations can run concurrently
- **Memory Overhead**: Each thread has its own Python interpreter context

### Resource Usage

- **Thread Pool**: Each TPOT2 operation uses one thread
- **Memory**: Additional memory for Python context in each thread
- **CPU**: Properly distributed across available cores

## Error Handling

### Timeout Errors

- Signal-based timeout prevents indefinite hangs
- Graceful cleanup of resources
- Error response with elapsed time

### Python Exceptions

- Caught and converted to Rust errors
- Maintained error information
- Proper resource cleanup

### Channel Errors

- Dropped senders when thread completes
- No messages lost during cancellation

## Best Practices

### For Users

1. **Monitor Operations**: Use `tpot2_monitor_operation()` to track progress
2. **Set Timeouts**: Always specify reasonable timeout values
3. **Clean Up**: Cancel completed operations when no longer needed
4. **Check Status**: Verify operation status before accessing results

### For Developers

1. **Test Concurrency**: Verify multiple operations run simultaneously
2. **Monitor Memory**: Watch for memory leaks with long-running tests
3. **Stress Test**: Run many concurrent operations to find bottlenecks
4. **Profile Performance**: Use system profilers to identify issues

## Migration Guide

### From Old API

```erlang
% Old - blocking call
Result = yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson),

% New - async call
{ok, OperationId} = yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson),
% ... do other work ...
{ok, Result} = yawl_ml_bridge:tpot2_get_result(OperationId)
```

### To New API

```erlang
% Start optimization
{ok, OperationId} = yawl_ml_bridge:tpot2_optimize(XJson, YJson, ConfigJson),

% Monitor progress
case yawl_ml_bridge:tpot2_monitor_operation(OperationId) of
    {ok, Status} -> 
        io:format("Progress: ~.1f%~n", [maps:get(progress, Status)]);
    {error, Reason} ->
        io:format("Error: ~p~n", [Reason])
end,

% Get final result (when complete)
case yawl_ml_bridge:tpot2_get_result(OperationId) of
    {ok, Result} -> 
        % Process result
    {error, Reason} ->
        % Handle error
end
```

## Future Enhancements

1. **Progress Callbacks**: Support for real-time progress updates via callbacks
2. **Thread Pool**: Manage a pool of threads instead of creating one per operation
3. **Priority Scheduling**: Implement priority for different operation types
4. **Resource Limits**: Cap concurrent operations to prevent resource exhaustion

## Testing

### Unit Tests

- Test operation creation and tracking
- Test timeout behavior
- Test concurrent operations
- Test error conditions

### Integration Tests

- Test with actual TPOT2 operations
- Test with large datasets
- Test with multiple concurrent operations
- Test system under stress

### Performance Tests

- Measure throughput of concurrent operations
- Measure memory usage patterns
- Measure CPU utilization
- Measure impact on other operations
