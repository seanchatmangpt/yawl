# QLever Embedded FFI Reference

## Overview

QLever Embedded provides a C/C++ Foreign Function Interface (FFI) layer that enables seamless integration with Java through Project Panama FFM (Foreign Function Memory). This FFI layer uses the **Lippincott Pattern** for exception handling, providing a robust and type-safe bridge between Java and QLever's C++ engine.

## Architecture

### Key Design Principles

1. **Hourglass Pattern**: Thin C API interface wrapping QLever's C++ functionality
2. **Lippincott Pattern**: Centralized exception translation from C++ to C
3. **Memory Safety**: Clear ownership and lifetime semantics for all objects
4. **ABI Stability**: Struct packing and versioning for cross-compiler compatibility
5. **Streaming Interface**: Iterator pattern for large query results

### Layer Structure

```
Java Application (Panama FFM)
    ↓
qlever_ffi.h (C API)
    ↓
qlever_ffi.cpp (Lippincott Wrapper)
    ↓
QLever C++ Engine (Index, Parser, QueryPlanner)
    ↓
QLever C++ Runtime (STL, Exceptions)
```

## 1. FFI Header (`qlever_ffi.h`)

### Status Codes and Error Handling

```c
typedef struct {
    int32_t code;           // Error code: 0 = success, negative = error
    char message[512];      // Error message (null-terminated)
} QleverStatus;

#define QLEVER_STATUS_SUCCESS         0
#define QLEVER_STATUS_ERROR          -1
#define QLEVER_STATUS_INVALID_ARGUMENT -2
#define QLEVER_STATUS_RUNTIME_ERROR   -3
#define QLEVER_STATUS_BAD_ALLOC       -4
#define QLEVER_STATUS_UNKNOWN_ERROR  -99
#define QLEVER_STATUS_INDEX_NOT_LOADED -10
#define QLEVER_STATUS_PARSE_ERROR    -20
```

### Media Types

```c
typedef enum {
    QLEVER_MEDIA_JSON = 0,    // application/sparql-results+json
    QLEVER_MEDIA_TSV = 1,     // text/tab-separated-values
    QLEVER_MEDIA_CSV = 2,     // text/csv
    QLEVER_MEDIA_TURTLE = 3,  // text/turtle
    QLEVER_MEDIA_XML = 4      // application/sparql-results+xml
} QleverMediaType;
```

### Opaque Handles

```c
typedef struct QLeverIndex QLeverIndex;    // Index handle
typedef struct QLeverResult QLeverResult; // Result handle
```

### Function Declarations

#### Index Lifecycle

```c
// Create index from pre-built directory
QLeverIndex* qlever_index_create(const char* index_path, QleverStatus* status);

// Destroy index and resources
void qlever_index_destroy(QLeverIndex* index);

// Check if index is loaded
int qlever_index_is_loaded(const QLeverIndex* index);

// Get triple count
size_t qlever_index_triple_count(const QLeverIndex* index, QleverStatus* status);
```

#### Query Execution

```c
// Execute SPARQL query
QLeverResult* qlever_query_exec(
    QLeverIndex* index,
    const char* sparql_query,
    QleverMediaType media_type,
    QleverStatus* status
);
```

#### Result Iteration

```c
// Check if more results available
int qlever_result_has_next(const QLeverResult* result);

// Get next result line
const char* qlever_result_next(QLeverResult* result, QleverStatus* status);

// Destroy result
void qlever_result_destroy(QLeverResult* result);
```

#### Error Handling

```c
// Check if result has error
int qlever_result_has_error(const QLeverResult* result);

// Get error message
const char* qlever_result_error(const QLeverResult* result);

// Get HTTP status code
int qlever_result_status(const QLeverResult* result, QleverStatus* status);
```

#### Memory Management

```c
// Free status structure
void qlever_free_status(QleverStatus* status);

// Free string returned by QLever
void qlever_free_string(char* string);

// Free result (alias for destroy)
void qlever_result_free(QLeverResult* result);

// Free index (alias for destroy)
void qlever_index_free(QLeverIndex* index);
```

#### Utilities

```c
// Convert media type to MIME string
char* qlever_media_type_to_mime(QleverMediaType media_type, QleverStatus* status);

// Convert MIME type to media type
int32_t qlever_mime_to_media_type(const char* mime_type, QleverStatus* status);
```

## 2. Implementation Patterns (`qlever_ffi.cpp`)

### Lippincott Pattern Exception Handling

```cpp
// Centralized exception translation
static void translate_exception(const std::exception& e, QleverStatus* status) {
    if (!status) return;

    status->code = QLEVER_STATUS_ERROR;
    status->message[0] = '\0';

    try {
        if (dynamic_cast<const std::invalid_argument*>(&e)) {
            status->code = QLEVER_STATUS_INVALID_ARGUMENT;
            snprintf(status->message, sizeof(status->message), "Invalid argument: %s", e.what());
        } else if (dynamic_cast<const std::runtime_error*>(&e)) {
            status->code = QLEVER_STATUS_RUNTIME_ERROR;
            snprintf(status->message, sizeof(status->message), "Runtime error: %s", e.what());
        } else if (dynamic_cast<const std::bad_alloc*>(&e)) {
            status->code = QLEVER_STATUS_BAD_ALLOC;
            snprintf(status->message, sizeof(status->message), "Memory allocation failed: %s", e.what());
        } else {
            status->code = QLEVER_STATUS_ERROR;
            snprintf(status->message, sizeof(status->message), "Standard exception: %s", e.what());
        }
    } catch (...) {
        // Dynamic cast failed
        status->code = QLEVER_STATUS_ERROR;
        snprintf(status->message, sizeof(status->message), "Exception type unknown: %s", e.what());
    }
}
```

### Internal Structures

```cpp
struct QLeverIndex {
    std::unique_ptr<Index> index;
    std::string indexPath;
    bool loaded = false;
};

struct QLeverResult {
    std::string resultString;
    std::vector<std::string> lines;
    int64_t rowCount = 0;
    int statusCode = 0;
    size_t currentLine = 0;
};
```

### Query Execution Pattern

```cpp
QLeverResult* qlever_query_exec(
    QLeverIndex* index,
    const char* sparql_query,
    QleverMediaType media_type,
    QleverStatus* status
) {
    auto* result = new QLeverResult();

    try {
        // Parse SPARQL query
        SparqlParser parser(sparql_query);
        auto parsedQuery = parser.parse();

        // Create execution context
        QueryExecutionContext ctx(index->index.get());

        // Plan and execute query
        QueryPlanner planner(&ctx);
        auto executionTree = planner.createExecutionTree(parsedQuery);
        auto resultTable = executionTree.getResult();

        // Serialize result based on media type
        std::stringstream ss;
        switch (media_type) {
            case QLEVER_MEDIA_TSV:
                resultTable->writeTsv(ss, parsedQuery);
                break;
            // ... other cases
        }

        // Split into lines for streaming
        result->resultString = ss.str();
        std::istringstream lineStream(result->resultString);
        std::string line;
        while (std::getline(lineStream, line)) {
            result->lines.push_back(line);
        }

        result->statusCode = 200;  // OK

    } catch (const ParseException& e) {
        status->code = QLEVER_STATUS_PARSE_ERROR;
        snprintf(status->message, sizeof(status->message), "SPARQL parse error: %s", e.what());
        result->statusCode = 400;  // Bad Request
    } catch (const std::exception& e) {
        translate_exception(e, status);
        result->statusCode = 500;  // Internal Server Error
    } catch (...) {
        translate_unknown_exception(status);
        result->statusCode = 500;
    }

    return result;
}
```

## 3. Type Definitions (`qlever_ffi_types.h`)

### ABI-Stable Structures

```c
typedef struct {
    uint32_t code;          // Status code (bitmask)
    uint32_t message_id;     // Message ID for localization
    uint32_t _padding[2];   // ABI padding
} QleverStatus;

static_assert(sizeof(QleverStatus) == 16, "QleverStatus must be exactly 16 bytes");
```

### Query Parameters

```c
typedef struct {
    uint64_t query_id;      // Unique query identifier
    uint32_t timeout_ms;    // Query timeout
    uint32_t max_results;   // Max results to return
    uint32_t offset;        // Result offset
    QleverMediaType output_format;
    uint32_t _padding[3];
} QleverQueryParams;

static_assert(sizeof(QleverQueryParams) == 32, "QleverQueryParams must be exactly 32 bytes");
```

### Extended Media Types

```c
typedef enum {
    QLEVER_MEDIA_TYPE_UNKNOWN = 0,
    QLEVER_MEDIA_TYPE_JSON = 1,
    QLEVER_MEDIA_TYPE_XML = 2,
    QLEVER_MEDIA_TYPE_TEXT = 3,
    QLEVER_MEDIA_TYPE_BINARY = 4,
    QLEVER_MEDIA_TYPE_HTML = 5,
    QLEVER_MEDIA_TYPE_CSV = 6,
    QLEVER_MEDIA_TYPE_TSV = 7,
    // SPARQL-specific types
    QLEVER_MEDIA_TYPE_SPARQL_JSON = 9,
    QLEVER_MEDIA_TYPE_SPARQL_XML = 10,
    QLEVER_MEDIA_TYPE_RDF_XML = 11,
    QLEVER_MEDIA_TYPE_RDF_TURTLE = 12,
    // Flags
    QLEVER_MEDIA_TYPE_FLAG_COMPRESSED = 0x1000,
    QLEVER_MEDIA_TYPE_FLAG_STREAMING = 0x2000
} QleverMediaType;
```

## 4. Build Instructions

### Prerequisites

- CMake 3.20+
- C++20 compatible compiler (GCC 11+, Clang 13+, MSVC 19.29+)
- QLever C++ library (as subdirectory or installed)
- JDK 17+ (for jextract integration)
- Build tools (make, ninja, or Visual Studio)

### Build Dependencies

```bash
# Ubuntu/Debian
sudo apt-get install build-essential cmake g++

# macOS (with Homebrew)
brew install cmake

# Windows (with vcpkg for dependencies)
vcpkg install qlever
```

### CMake Build Configuration

```bash
# Create build directory
mkdir build && cd build

# Configure with CMake
cmake .. \
    -DCMAKE_BUILD_TYPE=Release \
    -DBUILD_TESTS=ON \
    -DENABLE_FFI_TESTS=ON \
    -DBUILD_BENCHMARKS=OFF

# Build the library
cmake --build . --target qlever_ffi

# Install to Maven resources
cmake --build . --target install
```

### Multi-Platform Build

```bash
# Linux
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --target qlever_ffi

# macOS
cmake .. -DCMAKE_BUILD_TYPE=Release -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"
cmake --build . --target qlever_ffi

# Windows (x64)
cmake .. -A x64 -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release --target qlever_ffi
```

### jextract Integration

```bash
# Generate Java bindings
cmake --build . --target jextract_bindings

# This creates files in:
# ../generated-sources/org/yawlfoundation/yawl/qlever/ffi/
```

### Platform-Specific Considerations

#### Linux
```bash
# Build with GCC
cmake .. -DCMAKE_CXX_COMPILER=g++-11

# Build with Clang
cmake .. -DCMAKE_CXX_COMPILER=clang++-13
```

#### macOS
```bash
# Build for both Intel and Apple Silicon
cmake .. -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"

# Sign the library (optional)
codesign --force --sign - libqlever_ffi.dylib
```

#### Windows
```bash
# Build with MSVC
cmake .. -A x64 -DCMAKE_GENERATOR_PLATFORM=x64

# Ensure Visual Studio 2019 or later is installed
```

## 5. Memory Management Contract

### Ownership Rules

1. **QLeverIndex**: Created with `qlever_index_create()`, destroyed with `qlever_index_destroy()`
2. **QLeverResult**: Created by `qlever_query_exec()`, destroyed with `qlever_result_destroy()`
3. **QleverStatus**: Created by caller or by FFI functions, destroyed with `qlever_free_status()`
4. **Strings**: Created by FFI functions, destroyed with `qlever_free_string()`

### Memory Lifecycle

```c
// Index lifecycle
QLeverIndex* index = qlever_index_create("/path/to/index", &status);
if (index) {
    // Use index
    qlever_index_destroy(index);
}

// Query and result lifecycle
QLeverResult* result = qlever_query_exec(index, "SELECT * WHERE { ?s ?p ?o }", QLEVER_MEDIA_JSON, &status);
while (qlever_result_has_next(result)) {
    const char* line = qlever_result_next(result, &status);
    // Process line (string only valid until next call)
    if (line) {
        // Copy string if needed beyond next call
    }
}
qlever_result_destroy(result);

// Status management
QleverStatus* status = qlever_create_status();
// ... use status ...
qlever_free_status(status);
```

### String Handling

```c
// Strings returned by FFI are temporary and valid until:
// 1. Next call to the same function
// 2. Result/index is destroyed
// 3. Function is called again on the same result

const char* mime = qlever_media_type_to_mime(QLEVER_MEDIA_JSON, &status);
if (mime) {
    // Copy immediately if string needs to persist
    char* mime_copy = strdup(mime);
    // Use copy...
    free(mime_copy);  // Note: use qlever_free_string() if using FFI allocator
    qlever_free_string((char*)mime);  // Free original
}
```

## 6. Integration Examples

### Basic Query Execution

```java
// Java code using Panama FFM
MemorySegment library = Linker.nativeLinker().defaultLookup().find("qlever_ffi").orElseThrow();

// Function pointers
FunctionDescriptor indexCreateDesc = FunctionDescriptor.of(
    Addressable.class, ValueLayout.JAVA_STRING, ValueLayout.ADDRESS
);
MemorySegment indexCreateFunc = library.lookup("qlever_index_create").get().address();

// Create index
QleverStatus status = new QleverStatus();
MemorySegment statusSegment = MemorySegment.allocateFrom(ValueLayout.JAVA_INT, 0);
MemorySegment index = (MemorySegment) MethodHandle.invokeExact(
    indexCreateFunc,
    MemorySegment.ofAddressString("/path/to/index"),
    statusSegment
);
```

### Error Handling Pattern

```c
// In C callback
QleverStatus* status = qlever_create_status();
QLeverResult* result = qlever_query_exec(index, query, media_type, status);

if (status->code != 0) {
    printf("Error: %s\n", status->message);
    qlever_free_status(status);
    // Handle error
}

// Process result
while (qlever_result_has_next(result)) {
    const char* line = qlever_result_next(result, status);
    if (line) {
        printf("%s\n", line);
    }
}

// Cleanup
qlever_result_destroy(result);
qlever_free_status(status);
```

### Thread Safety Considerations

```c
// Thread-safe usage pattern (multiple threads, same index)
void* thread_func(void* arg) {
    ThreadContext* ctx = (ThreadContext*)arg;
    QleverStatus status = {0};

    // Each thread creates its own result
    QLeverResult* result = qlever_query_exec(
        ctx->index,  // Shared index (read-only)
        ctx->query,  // Thread-local query
        QLEVER_MEDIA_JSON,
        &status
    );

    // Each thread has its own result iterator
    while (qlever_result_has_next(result)) {
        const char* line = qlever_result_next(result, &status);
        // Process line
    }

    qlever_result_destroy(result);
    return NULL;
}
```

## 7. Performance Considerations

### Memory Allocation

- Use arena allocators for large result sets
- Pre-allocate result buffers when possible
- Minimize string copies in hot paths
- Consider memory pools for high-throughput scenarios

### Query Optimization

- Batch multiple queries when possible
- Cache frequently used queries
- Use appropriate media types (TSV for large datasets, JSON for complex results)
- Implement query timeouts for long-running operations

### Profiling Integration

```cpp
// Performance monitoring hooks can be added to:
// - translate_exception() for exception handling costs
// - qlever_query_exec() for query timing
// - qlever_result_next() for iteration performance

// Example: Query timing
struct QueryMetrics {
    uint64_t start_time;
    uint64_t parse_time;
    uint64_t execute_time;
    uint64_t serialize_time;
};
```

## 8. Debugging and Troubleshooting

### Common Issues

1. **Memory Leaks**: Use valgrind (Linux) or Instruments (macOS) to verify all allocations are freed
2. **ABIs Mismatches**: Check struct sizes with `static_assert`
3. **Exception Handling**: Verify exception translation in logs
4. **Thread Safety**: Use thread-local storage for per-thread data

### Debug Builds

```bash
# Debug build with ASan
cmake .. -DCMAKE_BUILD_TYPE=Debug -DCMAKE_CXX_FLAGS="-fsanitize=address"

# Debug build with symbols
cmake .. -DCMAKE_BUILD_TYPE=Debug -DCMAKE_CXX_FLAGS="-g3 -O0"
```

### Logging Integration

```cpp
// Add logging to FFI layer
#include <spdlog/spdlog.h>

// In FFI functions
spdlog::info("Executing query: {}", sparql_query);
spdlog::debug("Result has {} lines", result->lines.size());
spdlog::error("Query failed: {}", status->message);
```

## 9. Version Compatibility

### Versioning Strategy

- Semantic versioning (MAJOR.MINOR.PATCH)
- ABI stability guarantees for minor versions
- Breaking changes only in major versions
- Backward compatibility for patch versions

### Version Checking

```c
// Version information in qlever_ffi_types.h
typedef struct {
    uint32_t major;
    uint32_t minor;
    uint32_t patch;
    uint32_t build;
    uint32_t _padding[4];
} QleverVersion;

// Version compatibility check
int qlever_check_version(int major, int minor) {
    const QleverVersion* current = qlever_get_version();
    if (current->major != major) return -1;  // Major version mismatch
    if (current->minor < minor) return -1;   // Required minor version not met
    return 0;  // Compatible
}
```

## 10. Testing Framework

### Unit Tests

```cpp
// Using GoogleTest
TEST_F(FFITest, IndexCreation) {
    QleverStatus status = {0};
    QLeverIndex* index = qlever_index_create("/tmp/test_index", &status);

    ASSERT_NE(index, nullptr);
    ASSERT_EQ(status.code, QLEVER_STATUS_SUCCESS);

    qlever_index_destroy(index);
}

TEST_F(FFITest, QueryExecution) {
    // Setup index
    QLeverResult* result = qlever_query_exec(index, "SELECT * WHERE { ?s ?p ?o }", QLEVER_MEDIA_JSON, &status);

    ASSERT_NE(result, nullptr);
    ASSERT_EQ(status.code, QLEVER_STATUS_SUCCESS);

    while (qlever_result_has_next(result)) {
        const char* line = qlever_result_next(result, &status);
        ASSERT_NE(line, nullptr);
    }

    qlever_result_destroy(result);
}
```

### Integration Tests

```cpp
// End-to-end test
TEST_F(FFIIntegrationTest, FullWorkflow) {
    // 1. Create index
    // 2. Execute queries
    // 3. Verify results
    // 4. Cleanup resources
}
```

### Performance Benchmarks

```cpp
// Benchmark query execution
BENCHMARK(QueryBenchmark)->Range(8, 8 << 10)->Unit(benchmark::kMillisecond);

BENCHMARK(QueryBenchmark, Args({"/large/index", "complex.sparql"}))->Unit(benchmark::kMillisecond);
```

## 11. Migration Guide

### From v0.x to v1.0

Breaking changes:
- `qlever_free_string()` now takes `char*` instead of `const char*`
- Status structure size increased to 16 bytes
- Media type enum values changed

Migration steps:
1. Update status allocation: `QleverStatus* status = (QleverStatus*)malloc(16);`
2. Change string freeing: `qlever_free_string(str_copy);`
3. Update media type constants

### From v1.0 to v1.1

New features:
- Extended media types with flags
- Query timeout support
- Result streaming API

Migration path:
- Add new media type constants
- Implement timeout handling
- Migrate to streaming API for large results

---

This documentation provides a comprehensive reference for the QLever Embedded FFI layer, covering all aspects of the C/C++ interface, implementation patterns, build system, and integration guidance.