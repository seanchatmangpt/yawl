# YAWL QLever Bridge

This module implements Layer 3 of the Three-Domain Native Bridge Pattern for QLever SPARQL query integration, providing a pure Java API with type-safe results and comprehensive error handling.

## Architecture Overview

The implementation follows a layered architecture:

### Layer 1: Native Library
- Location: External QLever C++ library
- Purpose: Core QLever engine functionality
- Pattern: Native FFI bindings

### Layer 2: Native Bridge
- Location: `src/main/java/org.yawlfoundation.yawl/bridge/qlever/native/`
- Purpose: Safe wrapper around native calls with error handling
- Pattern: FFI method handles with proper memory management

### Layer 3: Java Domain API
- Location: `src/main/java/org.yawlfoundation.yawl/bridge/qlever/engine/`
- Purpose: Pure Java API abstracting native details
- Pattern: QLeverEngine interface with typed query results

## Key Components

### QleverStatus
- Records the result of QLever operations
- Provides error codes and messages
- Converts to checked exceptions when needed

### NativeHandle<T>
- Generic typed handle for native resources
- Manages Arena lifecycle automatically
- Ensures proper resource cleanup

### QLeverEngine Interface
```java
try (QLeverEngine engine = QLeverEngine.create(indexPath)) {
    // ASK queries
    AskResult result = engine.ask("ASK { ?s ?p ?o }");

    // SELECT queries
    SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

    // CONSTRUCT queries
    ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
}
```

## Build Configuration

### Maven Requirements
- Java 25 with --enable-preview
- Panama FFI module: jdk.incubator.foreign
- JExtract for C bindings generation

### Build Commands
```bash
# Compile with Java 25 preview features
mvn compile -Denable-preview

# Run tests
mvn test -Denable-preview

# Generate bindings (requires jextract)
mvn generate-sources
```

## Usage Examples

### Basic Query Execution
```java
// Create engine
QLeverEngine engine = QLeverEngine.create("/path/to/qlever/index");

// Execute ASK query
AskResult askResult = engine.ask("ASK { ?s ?p ?o }");
if (askResult.isTrue()) {
    System.out.println("Query has results");
}

// Execute SELECT query
SelectResult selectResult = engine.select("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");
for (int i = 0; i < selectResult.getRowCount(); i++) {
    String subject = selectResult.getValue(i, "s");
    String predicate = selectResult.getValue(i, "p");
    System.out.println(subject + " " + predicate);
}

// Execute CONSTRUCT query
ConstructResult constructResult = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
System.out.println(constructResult.getTurtleResult());

// Always close
engine.close();
```

### Error Handling
```java
try {
    QLeverEngine engine = QLeverEngine.create(indexPath);
    AskResult result = engine.ask("ASK { ?s ?p ?o }");
} catch (QleverRuntimeException e) {
    System.err.println("Query failed: " + e.getUserFriendlyMessage());
    System.err.println("Technical details: " + e.getTechnicalDetails());
}
```

### Resource Management
```java
// Automatic resource management with try-with-resources
try (QLeverEngine engine = QLeverEngine.create(indexPath)) {
    // Execute queries
} // Engine automatically closed

// Manual resource management
QLeverEngine engine = QLeverEngine.create(indexPath);
try {
    // Execute queries
} finally {
    engine.close();
}
```

## Testing

### Running Tests
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=QLeverEngineTest

# Run tests with verbose output
mvn test -Dtest=QLeverEngineTest -q
```

### Test Coverage
- Unit tests for all core classes
- Error handling validation
- Resource management testing
- Integration tests for native operations

## Native Library Requirements

The implementation expects a native library named `libqlever_ffi.so` (Linux) or `qlever_ffi.dll` (Windows) with these C functions:

- `qlever_engine_create`
- `qlever_engine_query`
- `qlever_result_get_data`
- `qlever_engine_destroy`
- `qlever_result_destroy`
- `qlever_initialize`
- `qlever_shutdown`

## Performance Considerations

### Arena Management
- Use confined arenas for single-threaded operations
- Use shared arenas for multi-threaded scenarios
- Global arena for long-lived resources

### Error Handling
- Prefer QleverException for recoverable errors
- Use QleverRuntimeException for programming errors
- Always check result status before accessing data

### Memory Safety
- Never close NativeHandle manually when using arena auto-close
- Always validate native handle state before use
- Follow Panama memory management rules

## Known Limitations

1. **Native Library**: Requires external QLever library to be built and linked
2. **Index Dependency**: Requires properly formatted QLever index directory
3. **Memory Management**: Careful arena management required for performance

## Implementation Status

✅ **Completed:**
- QLeverEngine interface with all required methods
- AskResult, SelectResult, ConstructResult with proper types
- Triple class for RDF representation
- Thread-safe execution with ReentrantLock
- Comprehensive error handling
- Resource cleanup with AutoCloseable
- Query validation functionality
- JSON result parsing
- Turtle conversion for triples
- Complete test suite

⚠️ **Note:**
- Implementation requires native QLever library to be available
- JSON parsing assumes standard QLever output format
- All methods implement real functionality or throw clear exceptions

## License

This implementation follows YAWL's HYPER_STANDARDS - no mocks, stubs, or placeholders. Every method either implements real functionality or throws UnsupportedOperationException with clear guidance.

## References

- [Project Panama](https://openjdk.org/projects/panama/)
- [JExtract Documentation](https://jdk.java.net/panama/)
- [YAWL Workflow Engine](https://www.yawlfoundation.org/)
- [QLever Semantic Search Engine](https://github.com/ad-freiburg/qlever)