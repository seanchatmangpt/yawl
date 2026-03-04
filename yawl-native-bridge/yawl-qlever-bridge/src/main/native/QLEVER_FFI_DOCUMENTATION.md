# QLever FFI Interface Documentation

## Overview

The `qlever_ffi.h` header provides a C-compatible interface for the QLever semantic search engine. This interface is designed to be consumed by Java's jextract tool to generate native bindings for seamless integration between Java and QLever's C++ implementation.

## Architecture

### Hourglass Façade Pattern

The FFI interface implements an hourglass pattern:
- **Wide at the top**: Comprehensive QLever functionality
- **Narrow at the middle**: Minimal FFI surface
- **Wide at the bottom**: Full C++ QLever engine

```
Java Application
       ↓ (jextract bindings)
QleverFfi.java (Java wrapper)
       ↓ (extern "C" calls)
qlever_ffi.h (C interface)
       ↓ (native implementation)
QLever C++ Engine
```

## Data Structures

### QleverStatus

The primary structure for error handling and status reporting:

```c
typedef struct {
    int32_t code;        // Status code (0=success, 1+=error)
    int32_t padding;     // 4-byte alignment
    const char* message; // Error message (NULL if success)
    void* data;          // Additional result data
} QleverStatus;
```

**Key Properties:**
- **Code**: Standardized error codes for programmatic handling
- **Message**: Human-readable error description
- **Data**: Optional result data handle
- **Alignment**: 8-byte alignment for 64-bit systems

### Handle Types

```c
typedef void* QleverEngineHandle;  // Engine instance handle
typedef void* QleverResultHandle;  // Query result handle
```

Handles are opaque pointers that implement RAII semantics. The caller owns the handles and is responsible for freeing them.

## Function Reference

### Engine Lifecycle

#### qlever_engine_create()

Creates a new QLever engine instance with the specified index.

```c
QleverStatus qlever_engine_create(
    const char* index_path,
    void** out_handle
);
```

**Parameters:**
- `index_path`: Path to QLever index directory
- `out_handle`: Output parameter for engine handle

**Returns:** QleverStatus with creation result

**Usage Example:**
```c
void* engine = NULL;
QleverStatus status = qlever_engine_create("/data/yawl-index", &engine);
if (status.code != QLEVER_STATUS_OK) {
    // Handle error
    qlever_status_free_message(&status);
    return;
}
```

#### qlever_engine_destroy()

Frees resources associated with an engine instance.

```c
QleverStatus qlever_engine_destroy(void* engine_handle);
```

**Parameters:**
- `engine_handle`: Engine handle to destroy

**Returns:** QleverStatus with destruction result

### Query Execution

#### qlever_engine_query()

Executes a SPARQL query against the QLever engine.

```c
QleverStatus qlever_engine_query(
    void* engine_handle,
    const char* sparql,
    void** out_result
);
```

**Parameters:**
- `engine_handle`: Valid engine handle
- `sparql`: SPARQL query string
- `out_result`: Output parameter for result handle

**Returns:** QleverStatus with query execution result

**SPARQL Query Examples:**
```sparql
-- Simple ASK query
ASK WHERE { ?s ?p ?o }

-- SELECT query with variables
SELECT ?subject ?predicate WHERE {
    ?subject ?predicate "example"
}

-- CONSTRUCT query
CONSTRUCT { ?s ?p ?o } WHERE {
    ?s ?p ?o
    FILTER(?p = rdf:type)
}
```

### Result Handling

#### qlever_result_get_data()

Retrieves JSON result data from a query result.

```c
QleverStatus qlever_result_get_data(
    void* result_handle,
    const char** out_data,
    size_t* out_len
);
```

**Parameters:**
- `result_handle`: Query result handle
- `out_data`: Output parameter for JSON string
- `out_len`: Output parameter for string length (optional)

**Returns:** QleverStatus with data retrieval result

**Memory Management:**
- Caller must free `*out_data` using `free()`
- `out_len` can be NULL if length is not needed

#### qlever_result_free()

Frees a query result handle and associated resources.

```c
QleverStatus qlever_result_free(void* result_handle);
```

**Parameters:**
- `result_handle`: Query result handle to free

**Returns:** QleverStatus with cleanup result

### Utility Functions

#### qlever_validate_query()

Validates a SPARQL query without executing it.

```c
QleverStatus qlever_validate_query(
    const char* sparql,
    int* out_valid
);
```

**Parameters:**
- `sparql`: SPARQL query to validate
- `out_valid`: Output parameter (true if valid)

**Returns:** QleverStatus with validation result

#### qlever_get_version()

Returns the QLever version string.

```c
const char* qlever_get_version(void);
```

**Returns:** Static version string (no need to free)

#### qlever_get_engine_info()

Returns detailed engine information as JSON.

```c
QleverStatus qlever_get_engine_info(
    const char** out_info,
    size_t* out_len
);
```

**Parameters:**
- `out_info`: Output parameter for JSON string
- `out_len`: Output parameter for string length

**Returns:** QleverStatus with info retrieval result

**Memory Management:**
- Caller must free `*out_info` using `free()`

### Status Handling

#### qlever_status_free_message()

Frees memory associated with a status message.

```c
QleverStatus qlever_status_free_message(const QleverStatus* status);
```

**Parameters:**
- `status`: Pointer to status struct

**Returns:** QleverStatus with cleanup result

#### qlever_status_string()

Converts status code to human-readable string.

```c
const char* qlever_status_string(int32_t code);
```

**Parameters:**
- `code`: Status code to convert

**Returns:** Static description string (no need to free)

## Memory Management

### Ownership Rules

1. **Handles**: Caller owns handles and must free them
2. **Strings**: Implementation allocates strings using `malloc()`, caller must free
3. **Status**: Caller owns status structs, must free messages
4. **Result Data**: Caller owns result JSON strings, must free

### Memory Allocation Pattern

```c
// All string allocations use malloc()
const char* message = strdup("error message");  // Implementation
const char* json = strdup("{...result...}");   // Implementation

// All deallocations use free()
free(message);  // Caller
free(json);     // Caller
```

### Safety Guidelines

1. **Always Check NULL**: Verify pointers before dereferencing
2. **Free After Use**: Call free functions when done with allocated memory
3. **Error Paths**: Ensure cleanup in all error scenarios
4. **Thread Safety**: All functions are reentrant and thread-safe

## Error Handling

### Status Codes

| Code | Constant | Description |
|------|----------|-------------|
| 0 | QLEVER_STATUS_OK | Success |
| 1 | QLEVER_STATUS_ERROR_PARSE | Query parsing failed |
| 2 | QLEVER_STATUS_ERROR_EXECUTION | Query execution failed |
| 3 | QLEVER_STATUS_ERROR_TIMEOUT | Query timed out |
| 4 | QLEVER_STATUS_ERROR_MEMORY | Out of memory |
| 5 | QLEVER_STATUS_ERROR_CONFIG | Configuration error |

### Error Message Format

- **UTF-8 Encoding**: All error messages are valid UTF-8
- **No Newlines**: Messages do not contain line breaks
- **Max Length**: 1024 characters including null terminator
- **Descriptive**: Include context about the failure

### Error Handling Pattern

```c
void* engine = NULL;
QleverStatus status = qlever_engine_create("/data/index", &engine);

if (status.code != QLEVER_STATUS_OK) {
    // Log error message
    fprintf(stderr, "Engine creation failed: %s\n", status.message);

    // Free message memory
    qlever_status_free_message(&status);
    return;
}

// Use engine...

// Cleanup
qlever_engine_destroy(engine);
```

## Integration with Java/jextract

### Java Binding Generation

The FFI interface is designed to work seamlessly with jextract:

```java
// Generated Java bindings will look like:
public class QleverFfi {
    public static class QleverStatus extends StructLayout {
        public ValueLayout.OfInt code();
        public ValueLayout.OfInt padding();
        public AddressLayout message();
        public AddressLayout data();
    }

    public static MemorySegment qlever_engine_create(
        MemorySegment index_path,
        MemorySegment out_handle
    );

    public static MemorySegment qlever_engine_query(
        MemorySegment engine_handle,
        MemorySegment sparql,
        MemorySegment out_result
    );
    // ... other functions
}
```

### Memory Layout Compatibility

The C struct layout matches Java's Panama Foreign Function Interface:

```c
// C struct
typedef struct {
    int32_t code;
    int32_t padding;
    const char* message;
    void* data;
} QleverStatus;

// Java equivalent (via jextract)
MemorySegment status = MemorySegment.allocateNative(QleverStatus.LAYOUT);
int code = status.get(QleverStatus.code());
String message = status.get(QleverStatus.message());
```

## Thread Safety

### Concurrency Guarantees

1. **Reentrant Functions**: All FFI functions are reentrant
2. **No Shared State**: No global mutable state
3. **Thread-Local Storage**: Error buffers use thread-local storage
4. **Atomic Operations**: Handle operations are atomic

### Usage Pattern for Concurrent Access

```c
// Multiple threads can safely use different engine instances
void* engine1 = NULL;
void* engine2 = NULL;

// Thread 1
QleverStatus status1 = qlever_engine_create("/index1", &engine1);

// Thread 2
QleverStatus status2 = qlever_engine_create("/index2", &engine2);

// Both threads can safely execute queries concurrently
```

## Performance Considerations

### Optimization Strategies

1. **Handle Reuse**: Reuse engine handles for multiple queries
2. **Result Processing**: Process results immediately after retrieval
3. **Memory Pool**: Use memory pools for frequent allocations
4. **Buffer Size**: Reserve appropriate buffer sizes

### Best Practices

```c
// Good: Reuse engine handle for multiple queries
void* engine = NULL;
qlever_engine_create("/data/index", &engine);

// Execute multiple queries
for (const char* query : queries) {
    void* result = NULL;
    qlever_engine_query(engine, query, &result);
    // Process result
    qlever_result_free(result);
}

// Destroy when done
qlever_engine_destroy(engine);
```

## Debugging and Monitoring

### Debug Build Features

When compiled with `DEBUG=1`, the FFI interface provides:

1. **Verbose Logging**: Detailed operation logging
2. **Memory Tracking**: Memory allocation tracking
3. **Stack Traces**: Exception stack traces in error messages
4. **Performance Counters**: Operation timing and metrics

### Debug Usage

```bash
# Compile with debug symbols
gcc -DDEBUG=1 -g -O0 qlever_native.cpp -o qlever_native

# Run with debug output
./qlever_native 2> debug.log
```

### Common Issues and Solutions

1. **Memory Leaks**: Use valgrind to detect memory leaks
2. **Null Pointer**: Check all pointers before dereferencing
3. **Thread Races**: Use thread sanitizers for data race detection
4. **Encoding Issues**: Verify UTF-8 encoding of strings

## Example Usage

### Complete Query Workflow

```c
#include "qlever_ffi.h"
#include <stdio.h>
#include <stdlib.h>

void execute_sparql_query(const char* index_path, const char* sparql) {
    // 1. Create engine
    void* engine = NULL;
    QleverStatus status = qlever_engine_create(index_path, &engine);

    if (status.code != QLEVER_STATUS_OK) {
        fprintf(stderr, "Engine creation failed: %s\n", status.message);
        qlever_status_free_message(&status);
        return;
    }

    // 2. Validate query
    int is_valid = 0;
    status = qlever_validate_query(sparql, &is_valid);

    if (status.code != QLEVER_STATUS_OK || !is_valid) {
        fprintf(stderr, "Query validation failed: %s\n", status.message);
        qlever_status_free_message(&status);
        qlever_engine_destroy(engine);
        return;
    }

    // 3. Execute query
    void* result = NULL;
    status = qlever_engine_query(engine, sparql, &result);

    if (status.code != QLEVER_STATUS_OK) {
        fprintf(stderr, "Query execution failed: %s\n", status.message);
        qlever_status_free_message(&status);
        qlever_engine_destroy(engine);
        return;
    }

    // 4. Get result data
    const char* json_data = NULL;
    size_t json_len = 0;
    status = qlever_result_get_data(result, &json_data, &json_len);

    if (status.code == QLEVER_STATUS_OK) {
        printf("Query result (%zu bytes):\n%s\n", json_len, json_data);
        free((void*)json_data);  // Caller must free
    }

    // 5. Cleanup
    qlever_result_free(result);
    qlever_engine_destroy(engine);
    qlever_status_free_message(&status);
}

int main() {
    const char* index_path = "/data/yawl-index";
    const char* sparql = "SELECT ?s ?p WHERE { ?s ?p \"example\" }";

    execute_sparql_query(index_path, sparql);
    return 0;
}
```

This comprehensive FFI interface provides a robust, memory-safe, and efficient bridge between Java and QLever's C++ implementation while maintaining full compatibility with jextract's binding generation capabilities.