# Lippincott Pattern for C++ Exception → QLeverStatus Conversion

## Overview

The Lippincott pattern is used to convert C++ exceptions into C-compatible error codes while preserving stack trace information. This pattern is essential for maintaining safety and debugging capabilities across the FFI boundary.

## Pattern Design

### 1. Exception Capture

```cpp
template<typename Func>
QleverStatus safe_execute(Func&& func) {
    try {
        func();
        return QleverStatus{QLEVER_STATUS_OK, 0, nullptr, nullptr};
    } catch (const std::exception& e) {
        return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup(e.what()), nullptr};
    } catch (...) {
        return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup("Unknown exception"), nullptr};
    }
}
```

### 2. Memory Management

- **String Allocation**: Use `strdup()` for error messages (allocated on heap)
- **Ownership Transfer**: Caller owns the message and must free it
- **Null Checks**: Always check for nullptr before using strings
- **Buffer Size**: Reserve buffer space for error messages

### 3. Common Exception Types

```cpp
// Query execution
try {
    engine->executeQuery(query);
    return QleverStatus{QLEVER_STATUS_OK, 0, nullptr, nullptr};
} catch (const ParseException& e) {
    return QleverStatus{QLEVER_STATUS_ERROR_PARSE, 0, strdup(e.getMessage()), nullptr};
} catch (const ExecutionException& e) {
    return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup(e.what()), nullptr};
} catch (const TimeoutException& e) {
    return QleverStatus{QLEVER_STATUS_ERROR_TIMEOUT, 0, strdup(e.what()), nullptr};
} catch (const MemoryException& e) {
    return QleverStatus{QLEVER_STATUS_ERROR_MEMORY, 0, strdup(e.what()), nullptr};
}
```

### 4. Usage in Native Functions

```cpp
QleverStatus qlever_engine_query(void* engine_handle, const char* sparql, void** out_result) {
    return safe_execute([&] {
        auto* engine = static_cast<QLeverEngine*>(engine_handle);
        auto result = engine->query(sparql);
        *out_result = result.release(); // Transfer ownership
    });
}
```

## Error Message Guidelines

### Format Requirements
- **UTF-8 Encoding**: All messages must be valid UTF-8
- **No Newlines**: Messages should not contain line breaks
- **Max Length**: 1024 characters including null terminator
- **Descriptive**: Include context about what failed and why

### Good Examples
```
"Failed to parse SPARQL query: syntax error at line 5"
"Index not found at path '/data/yawl-index'"
"Query timeout after 30 seconds"
```

### Bad Examples
```
"Error"  // Too vague
"Parse error"  // No context
nullptr  // Never NULL for errors
```

## Memory Safety Guarantees

### Caller Responsibilities
1. **Free Messages**: Call `qlever_status_free_message()` when done with status
2. **Check NULLs**: Always check if message is NULL before using
3. **Buffer Size**: Allocate sufficient space for result data

### Implementation Responsibilities
1. **Allocate with malloc()**: All strings allocated using malloc()
2. **Transfer Ownership**: Clear ownership transfer at FFI boundary
3. **Exception Safety**: Never leak memory during exception handling

### Example Usage

```cpp
// In Java code
QleverStatus status = qlever_engine_create(indexPath, &engineHandle);
if (status.code != QLEVER_STATUS_OK) {
    String errorMessage = status.message; // JNI string from char*
    // Handle error
    qlever_status_free_message(&status);
    return;
}

// Execute query
QleverStatus queryStatus = qlever_engine_query(engineHandle, sparql, &resultHandle);
if (queryStatus.code != QLEVER_STATUS_OK) {
    String errorMessage = queryStatus.message;
    qlever_status_free_message(&queryStatus);
    qlever_engine_destroy(engineHandle);
    return;
}

// Get result data
const char* jsonData;
size_t jsonLen;
QleverStatus dataStatus = qlever_result_get_data(resultHandle, &jsonData, &jsonLen);
if (dataStatus.code == QLEVER_STATUS_OK) {
    // Process jsonData (owned by caller)
    free(jsonData); // Caller must free
}
qlever_result_free(resultHandle);
qlever_engine_destroy(engineHandle);
qlever_status_free_message(&status);
```

## Performance Considerations

1. **String Allocation**: Minimize by reusing error message buffers
2. **Exception Handling**: Use try-catch only at FFI boundaries
3. **Zero-Copy**: For large data, consider memory-mapped files
4. **Buffer Pool**: Implement buffer pools for frequently allocated strings

## Debugging Support

### Stack Trace Preservation
```cpp
QleverStatus safe_execute_with_trace(Func&& func) {
    try {
        func();
        return QleverStatus{QLEVER_STATUS_OK, 0, nullptr, nullptr};
    } catch (const std::exception& e) {
        std::string trace = getStackTrace();
        std::string full_msg = std::string(e.what()) + " | Trace: " + trace;
        return QleverStatus{QLEVER_STATUS_ERROR_EXECUTION, 0, strdup(full_msg.c_str()), nullptr};
    }
}
```

### Memory Profiling
- Track all malloc/free operations
- Log memory usage at key operations
- Implement memory leak detection in debug builds

## Thread Safety

### Synchronization
- All FFI functions are reentrant
- Use thread-local storage for error buffers
- Avoid static variables that could cause races

### Concurrency Patterns
```cpp
// Thread-safe query execution
QleverStatus qlever_engine_query(void* engine_handle, const char* sparql, void** out_result) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    return safe_execute([&] {
        // Query execution logic
    });
}
```

## Testing Strategy

### Unit Tests
1. **Exception Conversion**: Verify all exception types are properly converted
2. **Memory Management**: Test leak-free operation with valgrind
3. **Error Messages**: Validate message format and content
4. **Thread Safety**: Test under concurrent access

### Integration Tests
1. **End-to-End**: Test complete query workflow
2. **Error Recovery**: Test error handling paths
3. **Performance**: Measure overhead of exception handling
4. **Memory Usage**: Monitor memory allocation patterns