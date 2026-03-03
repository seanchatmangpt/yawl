# QLever JExtract Layer 1 Implementation

This package contains the jextract-generated Layer 1 bindings for QLever FFI operations.

## Overview

The jextract layer provides low-level foreign function interface bindings to the native QLever C++ library using Project Panama FFI. All methods follow modern Java conventions and use Arena-scoped memory management.

## Architecture

### Core Components

1. **QleverFfi** - Main FFI interface with MethodHandle declarations
2. **QleverLayouts** - Memory layouts for native C++ structures
3. **QleverStatus** - Status/result wrapper with proper error handling
4. **QleverEngineHandle** - Opaque handle for engine instances
5. **QleverResultHandle** - Opaque handle for query results

## Function Signatures

### Engine Operations

```java
// Create engine
public static void createEngine(String configPath, QleverStatus status, MemorySegment result);

// Execute query
public static void executeQuery(MemorySegment engineHandle, MemorySegment query, QleverStatus status, MemorySegment result);

// Destroy engine
public static void destroyEngine(QleverEngineHandle engineHandle);
```

### Result Operations

```java
// Get result data
public static void getResultData(MemorySegment resultHandle, MemorySegment dataPtr, MemorySegment lenPtr, QleverStatus status);

// Free result
public static void freeResult(QleverResultHandle resultHandle);
```

## Memory Management

All operations use `Arena.ofConfined()` for automatic memory safety:

1. **Input strings** - Allocated as UTF-8 strings in confined arenas
2. **Output buffers** - Pre-allocated by callers for result storage
3. **Status structures** - Passed as parameters for error reporting
4. **Automatic cleanup** - Memory is freed when arenas are closed

## Error Handling

The implementation throws `UnsupportedOperationException` for:
- Missing or unavailable native library
- FFI call failures
- Invalid memory access patterns

This ensures no mock/stub implementations are present, requiring real native code to function.

## Usage Pattern

```java
// Create status structure
QleverStatus status = QleverStatus.success();

// Allocate output handle
MemorySegment engineHandle = arena.allocate(ADDRESS);

// Create engine
QleverFfi.createEngine(configPath, status, engineHandle);

if (status.isError()) {
    throw new UnsupportedOperationException("Engine creation failed: " + status.message());
}
```

## Integration with QLeverEngineImpl

The jextract layer is designed to be used by `QLeverEngineImpl` which provides the high-level API. The implementation follows the pattern established in `QLeverEngineImpl` for consistency.

## Status Codes

- `OK = 0` - Success
- `ERROR_PARSE = 1` - Query parsing error
- `ERROR_EXECUTION = 2` - Query execution error
- `ERROR_TIMEOUT = 3` - Query timeout
- `ERROR_MEMORY = 4` - Memory allocation error
- `ERROR_CONFIG = 5` - Configuration error

## Memory Layouts

The `QleverLayouts` class provides MemoryLayout definitions for native C++ structures:
- `QLEVER_STATUS` - Status structure with code, message, and data pointers
- `QLEVER_QUERY` - Query structure with query string and length
- `QLEVER_RESULT` - Result structure with data pointer and size

All layouts include proper alignment and padding for compatibility with the native library.