# How to Handle Errors

Learn how to handle and debug common errors when working with QLever.

## Problem

You encounter errors when using the QLever Embedded SPARQL Engine and need to understand how to handle them properly.

## Solution

Understand the different exception types and their meanings, and implement proper error handling.

## Exception Types

### 1. SparqlEngineException
Thrown when there's a query execution error.

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
} catch (SparqlEngineException e) {
    System.err.println("Query execution failed: " + e.getMessage());
    // Handle syntax errors, semantic errors, or runtime errors
}
```

### 2. SparqlEngineUnavailableException
Thrown when the engine is not available.

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
} catch (SparqlEngineUnavailableException e) {
    System.err.println("Engine is unavailable: " + e.getMessage());
    // Engine was closed or disposed
}
```

### 3. QLeverFfiException
Thrown when there's a native library error.

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
} catch (QLeverFfiException e) {
    System.err.println("Native library error: " + e.getMessage());
    // Check native library installation
}
```

## Common Errors and Solutions

### 1. Index Loading Errors

**Error Message:**
```
Failed to load QLever index from: /var/lib/qlever/my-index
```

**Solution:**
```java
try {
    Path indexPath = Path.of("/var/lib/qlever/my-index");
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        // Use engine
    }
} catch (SparqlEngineException e) {
    if (e.getMessage().contains("Failed to load QLever index")) {
        System.err.println("Index path issue");
        // Check index directory
        checkIndexDirectory(indexPath);
    }
}

private void checkIndexDirectory(Path indexPath) {
    if (!Files.exists(indexPath)) {
        System.err.println("Index directory does not exist: " + indexPath);
    } else {
        System.err.println("Index directory exists, checking contents:");
        try {
            Files.list(indexPath).forEach(System.err::println);
        } catch (IOException e) {
            System.err.println("Cannot read directory: " + e.getMessage());
        }
    }
}
```

### 2. Query Syntax Errors

**Error Message:**
```
syntax error at line 1, column 15: Expected 'WHERE' or '{'
```

**Solution:**
```java
try {
    String query = "SELECT * WHERE { ?s ?p ?o }";  // Missing WHERE clause
    String result = engine.selectToJson(query);
} catch (SparqlEngineException e) {
    if (e.getMessage().contains("syntax error")) {
        System.err.println("SPARQL syntax error: " + e.getMessage());
        // Use a validator
        validateQuery(query);
    }
}

private void validateQuery(String query) {
    // Simple query validation
    if (!query.contains("WHERE") && !query.contains("CONSTRUCT")) {
        System.err.println("Query must contain WHERE or CONSTRUCT");
    }

    if (!query.contains("SELECT") && !query.contains("CONSTRUCT") &&
        !query.contains("ASK") && !query.contains("DESCRIBE")) {
        System.err.println("Query must be SELECT, CONSTRUCT, ASK, or DESCRIBE");
    }
}
```

### 3. Native Library Loading Errors

**Error Message:**
```
Failed to load native library 'qlever_ffi'
```

**Solution:**
```java
try {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        // Use engine
    }
} catch (QLeverFfiException e) {
    System.err.println("Native library error: " + e.getMessage());

    // Check library path
    checkNativeLibraryPath();

    // Verify library exists
    checkNativeLibraryExists();
}

private void checkNativeLibraryPath() {
    String libraryPath = System.getProperty("java.library.path");
    System.err.println("Java library path: " + libraryPath);

    String[] paths = libraryPath.split(File.pathSeparator);
    for (String path : paths) {
        System.err.println("Checking path: " + path);
        File dir = new File(path);
        if (dir.exists()) {
            String[] files = dir.list();
            if (files != null) {
                Arrays.stream(files)
                    .filter(f -> f.contains("qlever_ffi"))
                    .forEach(f -> System.err.println("Found: " + f));
            }
        }
    }
}
```

### 4. Memory Allocation Errors

**Error Message:**
```
java.lang.OutOfMemoryError: Cannot allocate memory
```

**Solution:**
```java
// Increase JVM heap
java -Xms2g -Xmx4g --enable-preview --enable-native-access=ALL-UNNAMED -jar app.jar

// Or in code
try {
    String largeQuery = "SELECT * WHERE { ?s ?p ?o } LIMIT 1000000";
    String result = engine.selectToJson(largeQuery);
} catch (OutOfMemoryError e) {
    System.err.println("Memory allocation failed");

    // Reduce query size
    String smallerQuery = "SELECT * WHERE { ?s ?p ?o } LIMIT 10000";
    String smallerResult = engine.selectToJson(smallerQuery);
}
```

### 5. Invalid Index Format

**Error Message:**
```
QLever index loaded but returned null handle
```

**Solution:**
```java
try {
    Path indexPath = Path.of("/var/lib/qlever/my-index");
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        // Check if engine is healthy
        if (engine.isAvailable()) {
            long tripleCount = engine.getTripleCount();
            System.out.println("Index contains " + tripleCount + " triples");
        } else {
            System.err.println("Engine is not available");
        }
    }
} catch (SparqlEngineException e) {
    if (e.getMessage().contains("null handle")) {
        System.err.println("Invalid index format");
        rebuildIndex(indexPath);
    }
}
```

## Error Handling Patterns

### 1. Comprehensive Error Handling

```java
public void executeSafely(Path indexPath, String query) {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        try {
            // Check availability first
            if (!engine.isAvailable()) {
                throw new SparqlEngineUnavailableException("Engine not available", indexPath.toString());
            }

            // Execute query
            String result = engine.selectToJson(query);

            // Process result
            processResult(result);

        } catch (SparqlEngineException e) {
            handleSparqlError(e);
        } catch (QLeverFfiException e) {
            handleFfiError(e);
        }
    } catch (SparqlEngineException e) {
        handleEngineCreationError(e);
    }
}

private void handleSparqlError(SparqlEngineException e) {
    if (e.getMessage().contains("syntax error")) {
        System.err.println("Syntax error in query");
    } else if (e.getMessage().contains("Failed to load QLever index")) {
        System.err.println("Index loading failed");
    } else {
        System.err.println("Query execution error: " + e.getMessage());
    }
}
```

### 2. Retry Logic

```java
public String executeWithRetry(Path indexPath, String query, int maxRetries) {
    int retryCount = 0;
    SparqlEngineException lastError = null;

    while (retryCount < maxRetries) {
        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            return engine.selectToJson(query);
        } catch (SparqlEngineException e) {
            lastError = e;
            retryCount++;
            System.err.println("Attempt " + retryCount + " failed: " + e.getMessage());

            // Wait before retry
            try {
                Thread.sleep(1000 * retryCount);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Retry interrupted", ie);
            }
        }
    }

    throw new RuntimeException("Failed after " + maxRetries + " attempts", lastError);
}
```

### 3. Error Logging

```java
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

private static final Logger logger = LogManager.getLogger(QLeverEmbeddedSparqlEngine.class);

public String executeWithLogging(Path indexPath, String query) {
    try {
        logger.info("Executing query: " + query);
        long startTime = System.currentTimeMillis();

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            String result = engine.selectToJson(query);

            long duration = System.currentTimeMillis() - startTime;
            logger.info("Query executed successfully in " + duration + "ms");
            return result;
        }
    } catch (SparqlEngineException e) {
        logger.error("Query execution failed: " + e.getMessage(), e);
        throw e;
    } catch (Exception e) {
        logger.error("Unexpected error: " + e.getMessage(), e);
        throw e;
    }
}
```

### 4. Graceful Degradation

```java
public List<String> executeMultipleQueries(Path indexPath, List<String> queries) {
    List<String> results = new ArrayList<>();
    List<String> failedQueries = new ArrayList<>();

    for (String query : queries) {
        try {
            try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
                String result = engine.selectToJson(query);
                results.add(result);
            }
        } catch (SparqlEngineException e) {
            logger.warn("Query failed: " + query + " - " + e.getMessage());
            failedQueries.add(query);
            // Continue with other queries
        }
    }

    // Log summary
    logger.info("Successfully executed " + results.size() + "/" + queries.size() + " queries");
    if (!failedQueries.isEmpty()) {
        logger.warn("Failed queries: " + failedQueries);
    }

    return results;
}
```

## Debug Mode

### Enable Debug Logging

```java
// Enable debug logging
System.setProperty("qlever.debug", "true");
System.setProperty("qlever.log.level", "debug");

// Set log level
Logger logger = LogManager.getLogger(QLeverEmbeddedSparqlEngine.class);
logger.setLevel(Level.DEBUG);
```

### Debug Query Execution

```java
public String executeWithDebugInfo(Path indexPath, String query) {
    System.err.println("=== DEBUG INFO ===");
    System.err.println("Query: " + query);
    System.err.println("Index path: " + indexPath);

    try {
        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            System.err.println("Engine type: " + engine.engineType());
            System.err.println("Engine available: " + engine.isAvailable());

            String result = engine.selectToJson(query);
            System.err.println("Result length: " + result.length());
            return result;
        }
    } catch (Exception e) {
        System.err.println("Error type: " + e.getClass().getSimpleName());
        System.err.println("Error message: " + e.getMessage());
        throw e;
    }
}
```

## Performance Issues

### 1. Slow Queries

```java
// Monitor query performance
public String executeWithTiming(Path indexPath, String query) {
    long startTime = System.nanoTime();

    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        String result = engine.selectToJson(query);

        long duration = System.nanoTime() - startTime;
        double durationMs = duration / 1_000_000.0;

        if (durationMs > 1000) {
            logger.warn("Slow query detected: " + durationMs + "ms");
        }

        return result;
    }
}
```

### 2. Memory Usage

```java
// Monitor memory usage
Runtime runtime = Runtime.getRuntime();
long usedMemory = runtime.totalMemory() - runtime.freeMemory();
long maxMemory = runtime.maxMemory();

if (usedMemory > maxMemory * 0.8) {
    logger.warn("High memory usage: " + (usedMemory / 1024 / 1024) + "MB used");
}
```