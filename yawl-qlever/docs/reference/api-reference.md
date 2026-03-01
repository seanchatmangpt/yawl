# QLever Embedded SPARQL Engine API Reference

## Overview

This document provides complete reference documentation for the QLever Embedded SPARQL Engine API, following the Diataxis framework for technical documentation.

### Diataxis Classification
- **Type**: API Reference
- **Purpose**: Technical documentation for developers
- **Scope**: Complete API specification for all public interfaces

## Table of Contents

1. [QLeverFfiBindings](#qleverffibindings)
   - [Constructor](#constructor)
   - [Query Execution Methods](#query-execution-methods)
   - [Configuration Methods](#configuration-methods)
   - [Resource Management](#resource-management-methods)
   - [Utility Methods](#utility-methods)

2. [QLeverEmbeddedSparqlEngine](#qleverembeddedsparqlengine)
   - [SparqlEngine Implementation](#sparqlengine-interface)
   - [Query Execution](#query-execution)
   - [Result Processing](#result-processing)

3. [QLeverStatus](#qleverstatus)
   - [Record Fields](#record-fields)
   - [Status Methods](#status-methods)

4. [QLeverMediaType](#qlevermediatype)
   - [Enum Values](#enum-values)
   - MediaType Methods](#mediatype-methods)

5. [QLeverFfiException](#qleverffiexception)
   - [Exception Hierarchy](#exception-hierarchy)
   - [Factory Methods](#factory-methods)
   - [Exception Handling](#exception-handling)

---

## QLeverFfiBindings

### Constructor

#### `new QLeverFfiBindings(javaLibraryPath: String)`
Creates a new instance of QLeverFfiBindings with the specified Java library path.

**Parameters:**
- `javaLibraryPath` (`String`): Path to the Java native library file (`.dll`, `.so`, or `.dylib`)

**Returns:** `QLeverFfiBindings`

**Exceptions:**
- `QLeverFfiException`: If the library cannot be loaded

**Example:**
```java
QLeverFfiBindings bindings = new QLeverFfiBindings("/path/to/libyawlqlever.so");
```

### Query Execution Methods

#### `executeQuery(engine: QLeverEmbeddedSparqlEngine, query: String, limit: int)`
Executes a SPARQL query on the specified engine.

**Parameters:**
- `engine` (`QLeverEmbeddedSparqlEngine`): The engine instance to execute the query on
- `query` (`String`): SPARQL query string to execute
- `limit` (`int`): Maximum number of results to return

**Returns:** `QLeverStatus` containing the execution status and result

**Exceptions:**
- `QLeverFfiException`: If query execution fails
- `IllegalArgumentException`: If query is null or empty

**Example:**
```java
QLeverStatus status = bindings.executeQuery(engine, "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", 100);
```

#### `executeQueryForResult(engine: QLeverEmbeddedSparqlEngine, query: String)`
Executes a SPARQL query and returns the result directly as a byte array.

**Parameters:**
- `engine` (`QLeverEmbeddedSparqlEngine`): The engine instance to execute the query on
- `query` (`String`): SPARQL query string to execute

**Returns:** `byte[]` containing the serialized query result

**Exceptions:**
- `QLeverFfiException`: If query execution fails
- `IllegalArgumentException`: If query is null or empty

**Example:**
```java
byte[] result = bindings.executeQueryForResult(engine, "ASK { ?s ?p ?o }");
```

### Configuration Methods

#### `setLogLevel(level: String)`
Sets the logging level for the QLever engine.

**Parameters:**
- `level` (`String`): Logging level ("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE")

**Returns:** `void`

**Exceptions:**
- `IllegalArgumentException`: If level is not recognized

**Example:**
```java
bindings.setLogLevel("DEBUG");
```

#### `setTempDirectory(path: String)`
Sets the temporary directory for QLever operations.

**Parameters:**
- `path` (`String`): Path to the temporary directory

**Returns:** `void`

**Exceptions:**
- `QLeverFfiException`: If directory cannot be created or accessed

**Example:**
```java
bindings.setTempDirectory("/tmp/yawl-qlever");
```

#### `enableNativeSQLLogging(enable: boolean)`
Enables or disables native SQL logging.

**Parameters:**
- `enable` (`boolean`): True to enable logging, false to disable

**Returns:** `void`

**Example:**
```java
bindings.enableNativeSQLLogging(true);
```

### Resource Management Methods

#### `initializeQLever()`
Initializes the QLever engine subsystem.

**Returns:** `QLeverStatus` containing initialization status

**Exceptions:**
- `QLeverFfiException`: If initialization fails

**Example:**
```java
QLeverStatus status = bindings.initializeQLever();
if (status.ok()) {
    System.out.println("QLever initialized successfully");
}
```

#### `shutdownQLever()`
Shuts down the QLever engine subsystem and releases resources.

**Returns:** `void`

**Example:**
```java
bindings.shutdownQLever();
```

#### `createEngine(config: QLeverEngineConfig)`
Creates a new QLever engine instance with the specified configuration.

**Parameters:**
- `config` (`QLeverEngineConfig`): Configuration for the new engine

**Returns:** `QLeverEmbeddedSparqlEngine` representing the new engine instance

**Exceptions:**
- `QLeverFfiException`: If engine creation fails
- `IllegalArgumentException`: If config is invalid

**Example:**
```java
QLeverEngineConfig config = new QLeverEngineConfig.Builder()
    .withDatabasePath("/path/to/database")
    .withMemoryLimit(1024L * 1024L * 1024L) // 1GB
    .build();
QLeverEmbeddedSparqlEngine engine = bindings.createEngine(config);
```

#### `destroyEngine(engine: QLeverEmbeddedSparqlEngine)`
Destroys an engine instance and releases associated resources.

**Parameters:**
- `engine` (`QLeverEmbeddedSparqlEngine`): The engine instance to destroy

**Returns:** `void`

**Example:**
```java
bindings.destroyEngine(engine);
```

### Utility Methods

#### `getVersion()`
Gets the QLever version information.

**Returns:** `String` containing version information

**Example:**
```java
String version = bindings.getVersion();
System.out.println("QLever version: " + version);
```

#### `getAvailableMediaTypes()`
Gets the list of supported media types for query results.

**Returns:** `List<QLeverMediaType>` containing supported media types

**Example:**
```java
List<QLeverMediaType> types = bindings.getAvailableMediaTypes();
for (QLeverMediaType type : types) {
    System.out.println("Supported: " + type.getMimeType());
}
```

#### `escapeString(input: String)`
Escapes special characters in a string for use in SPARQL queries.

**Parameters:**
- `input` (`String`): Input string to escape

**Returns:** `String` with escaped characters

**Example:**
```java
String escaped = bindings.escapeString("The value with \"quotes\" and \\backslashes");
```

---

## QLeverEmbeddedSparqlEngine

### SparqlEngine Interface

The `QLeverEmbeddedSparqlEngine` class implements the `SparqlEngine` interface, providing native SPARQL query execution capabilities.

#### Constructor (Internal)
Note: Engine instances should be created using `QLeverFfiBindings.createEngine()`.

### Query Execution

#### `executeQuery(query: String, limit: int)`
Executes a SPARQL query on this engine instance.

**Parameters:**
- `query` (`String`): SPARQL query string
- `limit` (`int`): Maximum number of results to return

**Returns:** `QLeverStatus` containing the execution status and result

**Implementation Details:**
- Delegates to `QLeverFfi.executeQuery()`
- Thread-safe for concurrent execution
- Supports all SPARQL 1.1 features

**Example:**
```java
QLeverStatus result = engine.executeQuery("SELECT ?s WHERE { ?s ?p ?o } LIMIT 100", 100);
if (result.ok()) {
    System.out.println("Query executed successfully");
    System.out.println("Result: " + new String(result.getData()));
} else {
    System.err.println("Query failed: " + result.getError());
}
```

#### `executeQueryForResult(query: String)`
Executes a SPARQL query and returns the serialized result.

**Parameters:**
- `query` (`String`): SPARQL query string

**Returns:** `byte[]` containing serialized query result

**Implementation Details:**
- Returns results in the default media type (application/sparql-results+json)
- Uses efficient direct data transfer
- Avoids intermediate data copies

**Example:**
```java
byte[] jsonResult = engine.executeQueryForResult("ASK { ?s ?p ?o }");
if (jsonResult != null) {
    String jsonString = new String(jsonResult, StandardCharsets.UTF_8);
    System.out.println("Query result: " + jsonString);
}
```

### Result Processing

#### `getEngineId()`
Gets a unique identifier for this engine instance.

**Returns:** `String` containing the engine ID

**Example:**
```java
String id = engine.getEngineId();
System.out.println("Engine ID: " + id);
```

#### `getStatus()`
Gets the current status of the engine.

**Returns:** `QLeverStatus` containing current engine status

**Example:**
```java
QLeverStatus status = engine.getStatus();
System.out.println("Engine status: " + status.getState());
```

#### `isHealthy()`
Checks if the engine is in a healthy state.

**Returns:** `boolean` indicating engine health status

**Implementation Details:**
- Returns true if engine is ready for queries
- Returns false if engine is shutting down or in error state
- Lightweight check without blocking

**Example:**
```java
if (engine.isHealthy()) {
    // Engine can accept queries
    executeCriticalQuery();
}
```

---

## QLeverStatus

### Record Fields

#### `state`
The current state of the QLever operation or engine.

**Type:** `QLeverState`

**Possible Values:**
- `QLeverState.IDLE`: Engine is ready for queries
- `QLeverState.QUERYING`: Engine is processing a query
- `QLeverState.LOADING`: Engine is loading data
- `QLeverState.SHUTTING_DOWN`: Engine is shutting down
- `QLeverState.ERROR`: Engine is in error state
- `QLeverState.DISPOSED`: Engine has been disposed

**Example:**
```java
if (status.getState() == QLeverState.QUERYING) {
    System.out.println("Query is in progress");
}
```

#### `data`
The result data from the operation, if any.

**Type:** `byte[]`

**Notes:**
- May be null for operations that don't return data
- Contains serialized result data (JSON, XML, etc.)
- Empty array for operations that succeed without data

**Example:**
```java
if (status.getData() != null && status.getData().length > 0) {
    String result = new String(status.getData(), StandardCharsets.UTF_8);
    System.out.println("Result data: " + result);
}
```

#### `error`
Error message if the operation failed, otherwise null.

**Type:** `String`

**Notes:**
- Contains detailed error information
- Null for successful operations
- May contain multiple lines of error output

**Example:**
```java
if (status.getError() != null) {
    System.err.println("Operation failed: " + status.getError());
}
```

### Status Methods

#### `ok()`
Checks if the operation completed successfully.

**Returns:** `boolean` indicating success

**Implementation:**
- Returns true if state is not ERROR and error is null
- Returns false for error states or when error is not null

**Example:**
```java
if (status.ok()) {
    System.out.println("Operation completed successfully");
} else {
    System.err.println("Operation failed");
}
```

#### `hasData()`
Checks if the status contains result data.

**Returns:** `boolean` indicating if data is available

**Implementation:**
- Returns true if data is not null and length > 0
- Returns false for null or empty data

**Example:**
```java
if (status.hasData()) {
    processData(status.getData());
}
```

#### `toString()`
Returns a string representation of the status.

**Returns:** `String` containing state, data size, and error information

**Example:**
```java
System.out.println("Status: " + status.toString());
```

---

## QLeverMediaType

### Enum Values

#### `JSON`
JSON format for SPARQL results.

**MIME Type:** `application/sparql-results+json`

**Features:**
- Compact and readable
- Easy to parse with JSON libraries
- Default format for most operations

**Example:**
```java
QLeverMediaType jsonType = QLeverMediaType.JSON;
System.out.println("MIME type: " + jsonType.getMimeType());
```

#### `XML`
XML format for SPARQL results.

**MIME Type:** `application/sparql-results+xml`

**Features:**
- W3C standard format
- Good for XML-based processing pipelines
- More verbose than JSON

**Example:**
```java
QLeverMediaType xmlType = QLeverMediaType.XML;
```

#### `CSV`
CSV format for SPARQL results.

**MIME Type:** `text/csv`

**Features:**
- Easy to import into spreadsheet applications
- Simple tabular format
- Minimal overhead

**Example:**
```java
QLeverMediaType csvType = QLeverMediaType.CSV;
```

#### `TSV`
TSV (Tab-Separated Values) format for SPARQL results.

**MIME Type:** `text/tab-separated-values`

**Features:**
- Tab instead of comma separation
- No quoting issues with embedded commas
- Good for simple data interchange

**Example:**
```java
QLeverMediaType tsvType = QLeverMediaType.TSV;
```

#### `BINARY`
Binary format for SPARQL results.

**MIME Type:** `application/x-qlever-results-binary`

**Features:**
- Most compact format
- Fast to serialize/deserialize
- Requires binary format parser

**Example:**
```java
QLeverMediaType binaryType = QLeverMediaType.BINARY;
```

### MediaType Methods

#### `getMimeType()`
Gets the MIME type string for this media type.

**Returns:** `String` containing the MIME type

**Example:**
```java
String mimeType = QLeverMediaType.JSON.getMimeType();
System.out.println("MIME type: " + mimeType); // "application/sparql-results+json"
```

#### `getExtension()`
Gets the file extension associated with this media type.

**Returns:** `String` containing the file extension

**Example:**
```java
String ext = QLeverMediaType.JSON.getExtension(); // "json"
```

#### `isBinary()`
Checks if this media type represents binary data.

**Returns:** `boolean` indicating if binary

**Example:**
```java
boolean isBinary = QLeverMediaType.BINARY.isBinary(); // true
boolean isText = QLeverMediaType.JSON.isBinary(); // false
```

#### `parseData(byte[] data)`
Parses data in this media type into a structured format.

**Parameters:**
- `data` (`byte[]`): Raw data to parse

**Returns:** `Object` containing parsed data (depends on media type)

**Exceptions:**
- `QLeverFfiException`: If parsing fails

**Example:**
```java
byte[] jsonData = engine.executeQueryForResult("SELECT ?s WHERE { ?s ?p ?o }");
JSONObject result = (JSONObject) QLeverMediaType.JSON.parseData(jsonData);
```

---

## QLeverFfiException

### Exception Hierarchy

All exceptions thrown by the QLever FFI bindings extend `QLeverFfiException`, which extends `RuntimeException`.

#### `QLeverFfiException`
Base exception class for QLever FFI errors.

**Common Causes:**
- Native library loading failures
- FFI method invocation errors
- Resource allocation failures

#### `QLeverEngineException`
Thrown for engine-specific errors.

**Common Causes:**
- Engine creation failures
- Invalid engine configuration
- Engine shutdown errors

#### `QLeverQueryException`
Thrown for query execution errors.

**Common Causes:**
- Invalid SPARQL syntax
- Query timeout exceeded
- Query execution engine errors

#### `QLeverResourceException`
Thrown for resource management errors.

**Common Causes:**
- Memory allocation failures
- File system access errors
- Resource cleanup failures

### Factory Methods

#### `fromErrorCode(int errorCode, String message)`
Creates an exception from an error code and message.

**Parameters:**
- `errorCode` (`int`): Native error code
- `message` (`String`): Error message

**Returns:** `QLeverFfiException` appropriate for the error code

**Example:**
```java
try {
    bindings.executeQuery(engine, "INVALID QUERY", 100);
} catch (QLeverFfiException e) {
    if (e.getErrorCode() == QLeverErrorCodes.SYNTAX_ERROR) {
        System.err.println("Syntax error in SPARQL query");
    }
}
```

#### `fromNativeError(String errorOutput)`
Creates an exception from native error output.

**Parameters:**
- `errorOutput` (`String`): Error message from native code

**Returns:** `QLeverFfiException` with the error message

**Example:**
```java
try {
    // Operation that might fail
} catch (QLeverFfiException e) {
    System.err.println("Native error: " + e.getMessage());
}
```

### Exception Handling

#### `getErrorCode()`
Gets the error code associated with this exception.

**Returns:** `int` containing the error code

**Example:**
```java
try {
    bindings.initializeQLever();
} catch (QLeverFfiException e) {
    int code = e.getErrorCode();
    if (code == QLeverErrorCodes.LIBRARY_NOT_FOUND) {
        System.err.println("QLever library not found");
    }
}
```

#### `getNativeMessage()`
Gets the raw native error message.

**Returns:** `String` containing the native error message

**Example:**
```java
try {
    // Operation
} catch (QLeverFfiException e) {
    System.err.println("Native message: " + e.getNativeMessage());
}
```

#### `isRecoverable()`
Checks if this error might be recoverable.

**Returns:** `boolean` indicating if error is recoverable

**Implementation:**
- Returns true for temporary errors (timeouts, temporary resource issues)
- Returns false for permanent errors (library not found, invalid configuration)

**Example:**
```java
try {
    bindings.executeQuery(engine, query, limit);
} catch (QLeverFfiException e) {
    if (e.isRecoverable()) {
        // Retry the operation
        bindings.executeQuery(engine, query, limit);
    } else {
        // Log and don't retry
        logError(e);
    }
}
```

---

## Error Codes

The QLever FFI bindings uses the following error codes:

| Code | Constant | Description |
|------|----------|-------------|
| 0 | QLeverErrorCodes.SUCCESS | Operation completed successfully |
| 1 | QLeverErrorCodes.LIBRARY_NOT_FOUND | Native library could not be loaded |
| 2 | QLeverErrorCodes.INITIALIZATION_FAILED | Engine initialization failed |
| 3 | QLeverErrorCodes.INVALID_QUERY | SPARQL query syntax error |
| 4 | QLeverErrorCodes.QUERY_TIMEOUT | Query exceeded time limit |
| 5 | QLeverErrorCodes.RESOURCE_NOT_FOUND | Required resource not found |
| 6 | QLeverErrorCodes.OUT_OF_MEMORY | Memory allocation failed |
| 7 | QLeverErrorCodes.INVALID_ARGUMENT | Invalid method argument |
| 8 | QLeverErrorCodes.ENGINE_NOT_INITIALIZED | Engine not properly initialized |
| 9 | QLeverErrorCodes.ENGINE_ALREADY_INITIALIZED | Engine already initialized |
| 10 | QLeverErrorCodes.ENGINE_DISPOSED | Engine has been disposed |
| 11 | QLeverErrorCodes.RESULT_TOO_LARGE | Query result exceeds size limit |
| 12 | QLeverErrorCodes.INVALID_CONFIGURATION | Engine configuration is invalid |
| 13 | QLeverErrorCodes.IO_EXCEPTION | File system access error |
| 14 | QLeverErrorCodes.THREAD_ERROR | Thread synchronization error |
| 15 | QLeverErrorCodes.UNKNOWN_ERROR | Unknown error occurred |

---

## Best Practices

### Resource Management
1. **Always call shutdown()** when done using bindings
2. **Destroy engine instances** when no longer needed
3. **Handle exceptions properly** to prevent resource leaks

### Query Execution
1. **Use appropriate result limits** to avoid memory issues
2. **Escape user input** when building queries dynamically
3. **Check status codes** after each operation

### Error Handling
1. **Distinguish between recoverable and permanent errors**
2. **Use error codes** to provide specific error handling
3. **Log native error messages** for debugging

### Performance Optimization
1. **Reuse engine instances** when possible
2. **Batch multiple queries** to minimize setup overhead
3. **Use binary result format** for large datasets

---

## Thread Safety

- **QLeverFfiBindings**: Thread-safe for concurrent operations
- **QLeverEmbeddedSparqlEngine**: Thread-safe for concurrent query execution
- **QLeverStatus**: Immutable, therefore thread-safe
- **QLeverMediaType**: Enum, thread-safe
- **QLeverFfiException**: Immutable, thread-safe

Note: Individual engine instances should not be shared between threads if configuration changes are made.