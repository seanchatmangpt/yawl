# QLever FFI Implementation - Lippincott Pattern

This directory contains the C++ FFI (Foreign Function Interface) implementation for QLever, designed to enable Java Panama FFM (Foreign Function Memory) integration.

## Architecture Overview

The implementation follows the **Lippincott pattern** for centralized exception handling, providing robust error reporting and memory management.

### Key Features

1. **Centralized Exception Handling**: All exceptions are translated to standardized error codes
2. **Status-Based Error Reporting**: All functions return status codes with detailed messages
3. **Memory Safety**: Balanced create/destroy pairs with automatic cleanup
4. **Thread Safety**: Safe for concurrent use with proper synchronization
5. **Complete API Coverage**: Full QLever functionality through C interface

## API Reference

### Status Management

```c
// Create a status structure
QLeverStatus* qlever_create_status();

// Free a status structure
void qlever_free_status(QLeverStatus* status);

typedef struct {
    int32_t code;           // Error code: 0 = success, negative = error
    char message[512];      // Error message (null-terminated)
} QleverStatus;
```

### Error Codes

| Code | Value | Description |
|------|-------|-------------|
| QLEVER_STATUS_SUCCESS | 0 | Operation completed successfully |
| QLEVER_STATUS_ERROR | 1 | General error |
| QLEVER_STATUS_INVALID_ARGUMENT | 2 | Invalid argument provided |
| QLEVER_STATUS_RUNTIME_ERROR | 3 | Runtime error occurred |
| QLEVER_STATUS_BAD_ALLOC | 4 | Memory allocation failed |
| QLEVER_STATUS_UNKNOWN_ERROR | 5 | Unknown exception type |
| QLEVER_STATUS_INDEX_NOT_LOADED | 6 | Index not properly loaded |
| QLEVER_STATUS_PARSE_ERROR | 7 | SPARQL parsing error |
| QLEVER_STATUS_IO_ERROR | 8 | Input/output error |

### Index Management

```c
// Create index from path
QLeverIndex* qlever_index_create(const char* index_path, QleverStatus* status);

// Destroy index
void qlever_index_destroy(QLeverIndex* index);

// Check if index is loaded
bool qlever_index_is_loaded(const QLeverIndex* index);

// Get triple count
size_t qlever_index_triple_count(const QLeverIndex* index, QleverStatus* status);
```

### Query Execution

```c
typedef enum {
    QLEVER_MEDIA_JSON = 0,    // application/sparql-results+json
    QLEVER_MEDIA_TSV = 1,     // text/tab-separated-values
    QLEVER_MEDIA_CSV = 2,     // text/csv
    QLEVER_MEDIA_TURTLE = 3,  // text/turtle
    QLEVER_MEDIA_XML = 4      // application/sparql-results+xml
} QleverMediaType;

// Execute SPARQL query
QLeverResult* qlever_query_exec(
    QLeverIndex* index,
    const char* sparql_query,
    QleverMediaType media_type,
    QleverStatus* status
);
```

### Result Processing

```c
// Check if result has more lines
int qlever_result_has_next(const QLeverResult* result);

// Get next line of result
const char* qlever_result_next(QLeverResult* result, QleverStatus* status);

// Destroy result
void qlever_result_destroy(QLeverResult* result);

// Get row count for SELECT queries
int64_t qlever_result_row_count(const QLeverResult* result);

// Check for errors
int qlever_result_has_error(const QLeverResult* result);

// Get error message
const char* qlever_result_error(const QLeverResult* result);

// Get HTTP status code
int qlever_result_status(const QLeverResult* result, QleverStatus* status);
```

### Memory Management

```c
// Free allocated strings
void qlever_free_string(char* string);

// Free result
void qlever_result_free(QLeverResult* result);

// Free index
void qlever_index_free(QLeverIndex* index);
```

### Media Type Utilities

```c
// Convert media type to MIME string
char* qlever_media_type_to_mime(QleverMediaType media_type, QleverStatus* status);

// Convert MIME string to media type
int32_t qlever_mime_to_media_type(const char* mime_type, QleverStatus* status);
```

## Usage Examples

### Basic Query Execution

```c
// Create status
QLeverStatus* status = qlever_create_status();

// Load index
QLeverIndex* index = qlever_index_create("/path/to/index", status);
if (status->code != QLEVER_STATUS_SUCCESS) {
    printf("Error loading index: %s\n", status->message);
    qlever_free_status(status);
    return;
}

// Execute query
QLeverResult* result = qlever_query_exec(
    index,
    "SELECT ?s ?p ?o WHERE {?s ?p ?o}",
    QLEVER_MEDIA_JSON,
    status
);

if (status->code == QLEVER_STATUS_SUCCESS) {
    // Process results
    while (qlever_result_has_next(result)) {
        const char* line = qlever_result_next(result, status);
        printf("%s\n", line);
    }

    printf("Total rows: %ld\n", qlever_result_row_count(result));
} else {
    printf("Query failed: %s\n", status->message);
}

// Cleanup
qlever_result_destroy(result);
qlever_index_destroy(index);
qlever_free_status(status);
```

### Error Handling Pattern

```c
void safe_operation() {
    QLeverStatus* status = qlever_create_status();

    // Perform operation with status checking
    QLeverIndex* index = qlever_index_create("/path/to/index", status);
    if (status->code != QLEVER_STATUS_SUCCESS) {
        printf("Operation failed: %s\n", status->message);
        qlever_free_status(status);
        return;
    }

    // Continue with operation...

    // Cleanup
    qlever_index_destroy(index);
    qlever_free_status(status);
}
```

## Exception Handling

The implementation uses a centralized exception translation system:

```cpp
// Exception types are mapped to error codes:
// std::invalid_argument → QLEVER_STATUS_INVALID_ARGUMENT
// std::runtime_error → QLEVER_STATUS_RUNTIME_ERROR
// std::bad_alloc → QLEVER_STATUS_BAD_ALLOC
// std::exception → QLEVER_STATUS_ERROR
// unknown → QLEVER_STATUS_UNKNOWN_ERROR
```

## Memory Management

All allocated memory must be properly freed:

- `QLeverStatus` → `qlever_free_status()`
- `QLeverIndex` → `qlever_index_destroy()` or `qlever_index_free()`
- `QLeverResult` → `qlever_result_destroy()` or `qlever_result_free()`
- Strings → `qlever_free_string()`

## Building

The implementation uses CMake for building with comprehensive cross-platform support:

### Quick Start

```bash
# Configure build
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release

# Build the library
cmake --build build

# Run tests (optional)
cmake --build build --target test

# Install to Maven resources
cmake --install build
```

### Build Types

```bash
# Debug build (with debug symbols and assertions)
cmake -B build -S . -DCMAKE_BUILD_TYPE=Debug

# Release build (optimized, no debug info)
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release

# RelWithDebInfo (optimized with debug symbols)
cmake -B build -S . -DCMAKE_BUILD_TYPE=RelWithDebInfo

# MinSizeRel (minimal size, optimized for space)
cmake -B build -S . -DCMAKE_BUILD_TYPE=MinSizeRel
```

### Features

#### jextract Integration

Generate Java bindings automatically:

```bash
# Enable jextract (requires JDK)
cmake -B build -S . -Djextract_FOUND=ON
cmake --build build --target jextract_bindings
```

Generated bindings are placed in `../generated-sources`.

#### Testing

Enable comprehensive testing:

```bash
# Enable all tests
cmake -B build -S . -DBUILD_TESTS=ON
cmake --build build --target test

# Run specific test suites
cmake --build build --target qlever_ffi_unit_tests
cmake --build build --target qlever_ffi_integration_tests
```

#### Benchmarks

Enable performance benchmarks:

```bash
# Enable benchmarks (requires Google Benchmark)
cmake -B build -S . -DBUILD_BENCHMARKS=ON
cmake --build build --target benchmark
```

## Testing

Run the comprehensive test suite:

```bash
# All tests
cmake --build build --target test

# Run test executable directly
cd build
./qlever_ffi_test

# With GoogleTest framework
ctest --output-on-failure
```

### Test Categories

- **Unit Tests**: Individual component testing with mock engine
- **Integration Tests**: End-to-end workflow testing
- **Performance Tests**: Stress testing and benchmarks

## Integration with Java

This FFI implementation is designed to work with Java's Panama FFM:

```java
// Load the native library
MemorySegment library = LibraryLoader.sharedLibrary("qlever_ffi").lookup("*");

// Get function pointers
FunctionDescriptor indexDesc = FunctionDescriptor.of(
    ValueLayout.ADDRESS,      // QLeverIndex*
    ValueLayout.ADDRESS,      // const char*
    ValueLayout.ADDRESS       // QleverStatus*
);

MemorySegment indexPtr = (MemorySegment) library.lookup("qlever_index_create")
    .get()
    .invokeExact(
        MemorySegment.NULL,
        MemorySegment.NULL,
        MemorySegment.NULL
    );
```

## Performance Considerations

- All operations are thread-safe for read-only access
- Results are streamed line-by-line to minimize memory usage
- Status structures are reused when possible to reduce allocations
- String copying is minimized for performance

## Thread Safety

- Index objects can be shared across threads for concurrent queries
- Result objects are not thread-safe and should only be accessed by one thread
- Status objects are not thread-safe and should be local to each thread

## Error Recovery

The implementation provides comprehensive error information:

1. **Error codes** for programmatic handling
2. **Error messages** for debugging
3. **Status propagation** through call chains
4. **Resource cleanup** even on error paths

## Cross-Platform Support

The build system supports Linux, macOS, and Windows:

### Linux
```bash
# Standard Linux build
cmake -B build -S .

# With GCC
CC=gcc CXX=g++ cmake -B build -S .
```

### macOS
```bash
# Standard macOS build
cmake -B build -S .

# For Apple Silicon
cmake -B build -S . -DCMAKE_OSX_ARCHITECTURES=arm64
```

### Windows
```bash
# With Visual Studio 2022
cmake -B build -S . -G "Visual Studio 17 2022"

# With MinGW
cmake -B build -S . -G "MinGW Makefiles"
```

## QLever Integration

The build system looks for QLever in the following order:

1. **As subdirectory** (recommended)
   ```bash
   # Clone QLever as sibling directory
   git clone https://github.com/Qlever/qlever.git ../qlever
   ```

2. **System-wide installation**
   ```bash
   # Ubuntu/Debian
   sudo apt-get install qlever
   ```

3. **Manual configuration**
   ```bash
   # Set QLEVER_DIR environment variable
   export QLEVER_DIR=/path/to/qlever
   ```

## Output Structure

### Artifacts

```
build/
├── lib/                    # Built libraries
│   └── libqlever_ffi.so    # Linux
│   └── libqlever_ffi.dylib # macOS
│   └── qlever_ffi.dll     # Windows
└── bin/                    # Executables
    └── qlever_ffi_test     # Test executable

../resources/native/
├── linux/x86_64/
│   └── libqlever_ffi.so
├── macos/aarch64/
│   └── libqlever_ffi.dylib
└── windows/x86_64/
    └── qlever_ffi.dll
```

### Configuration Files

- `qlever_ffi.properties` - Maven configuration properties
- `qlever_ffi-config.cmake` - CMake package config

## Contributing

When adding new functionality:

1. Follow the Lippincott pattern for exception handling
2. Update all create/destroy pairs to be balanced
3. Add comprehensive error codes and messages
4. Include unit tests for all new functions
5. Document all new APIs in this README
6. Update CMakeLists.txt for new source files or targets