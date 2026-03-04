# QLever Native FFI Interface

This directory contains the native C++ FFI interface for QLever that provides a bridge between Java and the QLever semantic search engine through the jextract tool.

## Overview

The QLever Native FFI Interface implements the Hourglass pattern with:

- **Wide top**: Comprehensive QLever functionality
- **Narrow middle**: Minimal FFI surface through `qlever_ffi.h`
- **Wide bottom**: Full C++ QLever engine capabilities

## File Structure

```
src/main/native/
├── qlever_ffi.h              # Main FFI header (C interface)
├── qlever_native.cpp         # Main implementation
├── test_main.cpp            # Test implementation
├── CMakeLists.txt           # Build configuration
├── build.sh                # Build script
├── qlever-native.pc.in      # pkg-config template
├── README.md               # This file
├── QLEVER_FFI_DOCUMENTATION.md  # Comprehensive documentation
├── LIPPINCOTT_PATTERN.md   # Exception handling pattern
└── NATIVE_IMPLEMENTATION_GUIDE.md  # Implementation guide
```

## Quick Start

### Prerequisites

- CMake 3.10 or higher
- C++17 compatible compiler
- QLever C++ library and headers
- jextract (for Java bindings)

### Build the Native Library

```bash
# Build with default settings
./build.sh

# Build release version
./build.sh --release

# Clean and rebuild
./build.sh --clean

# Build static library
./build.sh --static

# Build and install to system
./build.sh --install
```

### Run Tests

```bash
# Build and run tests
./build.sh --tests

# Build, test, and install
./build.sh --tests --install
```

### Using pkg-config

```bash
# Get compiler flags
pkg-config --cflags qlever-native

# Get linker flags
pkg-config --libs qlever-native

# Compile a test program
gcc test_main.cpp pkg-config --cflags --libs qlever-native -o test_main
```

## C Interface Usage

The FFI interface provides the following functions:

### Engine Lifecycle

```c
// Create engine
void* engine = NULL;
QleverStatus status = qlever_engine_create("/data/yawl-index", &engine);

// Execute query
void* result = NULL;
status = qlever_engine_query(engine, "SELECT ?s ?p WHERE { ?s ?p ?o }", &result);

// Get result data
const char* json_data;
size_t json_len;
status = qlever_result_get_data(result, &json_data, &json_len);

// Cleanup
qlever_result_free(result);
qlever_engine_destroy(engine);
```

### Error Handling

```c
QleverStatus status = qlever_engine_create("/path/to/index", &engine);

if (status.code != QLEVER_STATUS_OK) {
    fprintf(stderr, "Error: %s\n", status.message);
    qlever_status_free_message(&status);
    return;
}
```

### Memory Management

```c
// Result data must be freed by caller
const char* json_data;
status = qlever_result_get_data(result, &json_data, NULL);
if (status.code == QLEVER_STATUS_OK) {
    // Use json_data
    free((void*)json_data);  // Caller must free!
}
```

## Java Integration

### jextract Configuration

The FFI interface is designed to work seamlessly with jextract:

```bash
# Generate Java bindings
jextract \
    --output-dir ../target/classes \
    --header-class-name org.yawlfoundation.yawl.bridge.qlever.jextract.QleverFfi \
    --source-code-path . \
    qlever_ffi.h
```

### Usage in Java

```java
// Use the generated bindings
import org.yawlfoundation.yawl.bridge.qlever.jextract.*;

public class QLeverExample {
    public static void main(String[] args) {
        MemorySegment engineHandle = MemorySegment.allocateNative(ADDRESS);
        QleverStatus status = QleverFfi.qlever_engine_create(
            MemorySegment.allocateFromUTF8("/data/index"),
            engineHandle
        );

        if (status.code() != QLEVER_STATUS_OK) {
            System.err.println("Error: " + status.message());
            return;
        }

        // Execute query
        MemorySegment resultHandle = MemorySegment.allocateNative(ADDRESS);
        status = QleverFfi.qlever_engine_query(
            engineHandle,
            MemorySegment.allocateFromUTF8("SELECT ?s ?p WHERE { ?s ?p ?o }"),
            resultHandle
        );

        // Process results...
    }
}
```

## Error Codes

| Code | Constant | Description |
|------|----------|-------------|
| 0 | QLEVER_STATUS_OK | Success |
| 1 | QLEVER_STATUS_ERROR_PARSE | Query parsing failed |
| 2 | QLEVER_STATUS_ERROR_EXECUTION | Query execution failed |
| 3 | QLEVER_STATUS_ERROR_TIMEOUT | Query timed out |
| 4 | QLEVER_STATUS_ERROR_MEMORY | Out of memory |
| 5 | QLEVER_STATUS_ERROR_CONFIG | Configuration error |

## Thread Safety

All FFI functions are reentrant and thread-safe:

- No global mutable state
- Thread-local storage for error messages
- Atomic handle operations
- Proper mutex locking for internal operations

## Performance Considerations

1. **Handle Reuse**: Reuse engine handles for multiple queries
2. **Result Processing**: Process results immediately after retrieval
3. **Memory Pool**: Use memory pools for frequent allocations
4. **Buffer Size**: Reserve appropriate buffer sizes

## Debugging

### Debug Build

```bash
# Build with debug symbols
./build.sh --debug

# Run with memory leak detection
valgrind --leak-check=full ./test_main
```

### Logging

Enable debug logging by defining `DEBUG=1`:

```bash
DEBUG=1 ./build.sh --debug
```

## Troubleshooting

### Common Issues

1. **Undefined References**
   ```bash
   # Link with QLever libraries
   pkg-config --libs qlever-native
   ```

2. **Header Not Found**
   ```bash
   # Include path
   pkg-config --cflags qlever-native
   ```

3. **Memory Leaks**
   ```bash
   # Use valgrind
   valgrind --leak-check=full ./your_program
   ```

### Build Errors

1. **CMake Error**: Missing dependencies
   ```bash
   # Install dependencies
   sudo apt-get install cmake build-essential libqliver-dev
   ```

2. **Compiler Error**: C++17 required
   ```bash
   # Check compiler version
   g++ --version
   # CMake will use C++17 by default
   ```

## Testing

### Unit Tests

```bash
# Run specific tests
./test_main --gtest_filter="*Engine*"

# Run with verbose output
./test_main --gtest_verbose

# Run memory tests
valgrind --leak-check=full ./test_main
```

### Integration Tests

1. Create a test QLever index
2. Run the test suite
3. Verify query results

## Contributing

1. Follow the Lippincott pattern for exception handling
2. Ensure all functions are thread-safe
3. Add tests for new functionality
4. Document new functions in the header
5. Update documentation as needed

## License

This project is part of the YAWL workflow engine and follows the same license terms.