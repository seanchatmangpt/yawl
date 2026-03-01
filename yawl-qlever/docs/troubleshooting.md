# QLever Embedded Troubleshooting Guide

This guide covers common issues and solutions when integrating QLever Embedded with Java applications using Panama FFM.

## Table of Contents
1. [UnsatisfiedLinkError - Library Not Found](#unsatisfiedlinkerror---library-not-found)
2. [Index Loading Failures](#index-loading-failures)
3. [SPARQL Parse Errors](#sparql-parse-errors)
4. [Memory Allocation Errors](#memory-allocation-errors)
5. [Result Iteration Issues](#result-iteration-issues)
6. [Thread Safety Problems](#thread-safety-problems)
7. [Performance Issues](#performance-issues)
8. [Platform-Specific Issues](#platform-specific-issues)

---

## 1. UnsatisfiedLinkError - Library Not Found

### Error Message
```
java.lang.UnsatisfiedLinkError: no qleverjni in java.library.path
    at java.base/jdk.internal.loader.NativeLibraries.load(NativeLibraries.java:200)
    at java.base/jdk.internal.loader.NativeLibraries$NativeLibraryImpl.open(NativeLibraries.java:386)
```

### Root Cause
- Native library not found in the system library path
- Incorrect architecture mismatch (x86 vs ARM64)
- Library not built for the current platform
- Library path not set properly

### Solution Steps

#### Step 1: Verify Native Library Availability
```bash
# Check if libraries exist in the expected locations
find . -name "*qleverjni*" -type f
find . -name "*.so" -type f  # Linux
find . -name "*.dylib" -type f  # macOS
find . -name "*.dll" -type f  # Windows
```

#### Step 2: Set Library Path
**Linux:**
```bash
export LD_LIBRARY_PATH=$PWD/src/main/native/build:$LD_LIBRARY_PATH
java -Djava.library.path=$PWD/src/main/native/build YourApplication
```

**macOS:**
```bash
export DYLD_LIBRARY_PATH=$PWD/src/main/native/build:$DYLD_LIBRARY_PATH
java -Djava.library.path=$PWD/src/main/native/build YourApplication
```

**Windows:**
```cmd
set PATH=%CD%\src\main\native\build;%PATH%
java -Djava.library.path="%CD%\src\main\native\build" YourApplication
```

#### Step 3: Verify Architecture
```bash
# Check system architecture
uname -m  # Linux
arch  # macOS

# Check library architecture
file libqleverjni.so  # Linux
file libqleverjni.dylib  # macOS
```

### Prevention Tips
- Include build script in your project
- Use Maven/Gradle native dependency management
- Create platform-specific build profiles
- Add library path validation in startup code

```java
// Library path validation example
public class LibraryLoader {
    static {
        try {
            System.loadLibrary("qleverjni");
            System.out.println("QLever native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            String libPath = System.getProperty("java.library.path");
            throw new RuntimeException(
                "Failed to load QLever library. Check library path: " + libPath, e);
        }
    }
}
```

---

## 2. Index Loading Failures

### Error Message
```
QLever Error: Index not loaded (code: -10)
QLever Error: Cannot open index directory: /path/to/index
QLever Error: Missing required index files
```

### Root Cause
- Index directory doesn't exist
- Missing required index files (.index.pbm, .index.pso, etc.)
- Permission issues accessing index files
- Corrupted index files

### Solution Steps

#### Step 1: Verify Index Directory Structure
```bash
# Check if index directory exists
ls -la /path/to/index/

# Required files should include:
# - .index.pbm
# - .index.pso
# - .index.pos
# - .index.patterns
# - .index.prefixes
```

#### Step 2: Validate Index Files
```bash
# Check file permissions
ls -la /path/to/index/.index.*

# Check file integrity
file /path/to/index/.index.pbm
hexdump -C /path/to/index/.index.pbm | head
```

#### Step 3: Check Index Creation Process
```bash
# Ensure index was built with correct QLever version
./qlever build -i /path/to/index /path/to/data.ttl

# Verify index configuration
cat /path/to/index/.index.meta.json
```

### Prevention Tips
- Create index validation utility
- Use checksums for index files
- Implement index health checks
- Store index metadata for version tracking

```java
// Index validation example
public class IndexValidator {
    public static boolean validateIndex(String indexPath) {
        File indexDir = new File(indexPath);
        if (!indexDir.exists()) {
            throw new IllegalArgumentException("Index directory not found: " + indexPath);
        }

        String[] requiredFiles = {
            ".index.pbm", ".index.pso", ".index.pos",
            ".index.patterns", ".index.prefixes"
        };

        for (String file : requiredFiles) {
            File f = new File(indexDir, file);
            if (!f.exists() || !f.canRead()) {
                return false;
            }
        }
        return true;
    }
}
```

---

## 3. SPARQL Parse Errors

### Error Message
```
QLever Error: Parse error (code: -20)
QLever Error: SPARQL syntax error at line 5, column 12: Expected '}' but found 'SELECT'
QLever Error: Unknown predicate 'http://example.org/undefined'
```

### Root Cause
- Invalid SPARQL syntax
- Undefined predicates/variables
- Missing namespace prefixes
- Unsupported SPARQL features

### Solution Steps

#### Step 1: Validate SPARQL Query
```bash
# Use QLever CLI to validate queries
echo "SELECT * WHERE { ?s ?p ?o }" | ./qlever query -i /path/to/index

# Use online SPARQL validator
# https://queryvalidator.w3.org/
```

#### Step 2: Check Prefix Definitions
```sparql
-- Good practice: Define all prefixes
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX ex: <http://example.org/>

SELECT ?s ?p ?o
WHERE {
    ?s ?p ?o .
    FILTER(?p = ex:definedProperty)
}
```

#### Step 3: Verify Schema Compatibility
```bash
# Check available predicates
./qlever query -i /path/to/index "SELECT DISTINCT ?p WHERE { ?s ?p ?o } LIMIT 100"

# Check triple patterns
./qlever query -i /path/to/index "SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }"
```

### Prevention Tips
- Implement SPARQL query validation layer
- Maintain schema documentation
- Use parameterized queries to prevent injection
- Create query builder utilities

```java
// SPARQL validation example
public class SparqlValidator {
    public static void validateQuery(String query, Index index) {
        // Basic syntax validation
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        // Check for balanced brackets
        int openBrackets = countOccurrences(query, '{');
        int closeBrackets = countOccurrences(query, '}');
        if (openBrackets != closeBrackets) {
            throw new IllegalArgumentException("Unbalanced braces in query");
        }

        // Execute with LIMIT 1 to validate syntax
        String testQuery = query + " LIMIT 1";
        QleverResult result = qlever_query_exec(index, testQuery,
            QLEVER_MEDIA_JSON, status);

        if (status.code != QLEVER_STATUS_SUCCESS) {
            throw new SparqlParseException(status.message);
        }
    }
}
```

---

## 4. Memory Allocation Errors

### Error Message
```
QLever Error: Memory allocation failed (code: -4)
java.lang.OutOfMemoryError: Java heap space
java.lang.OutOfMemoryError: Metaspace
```

### Root Cause
- Insufficient heap memory
- Large result sets not streamed
- Memory leaks in native code
- JVM memory configuration issues

### Solution Steps

#### Step 1: Increase JVM Memory
```bash
# For small to medium datasets
java -Xms512m -Xmx2g -XX:+UseG1GC YourApplication

# For large datasets
java -Xms2g -Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 YourApplication

# For native memory issues
java -XX:MaxDirectMemorySize=4g YourApplication
```

#### Step 2: Implement Result Streaming
```java
// Process results in batches instead of loading all at once
QLeverResult result = qlever_query_exec(index, query, mediaType, status);
if (status.code != QLEVER_STATUS_SUCCESS) {
    throw new RuntimeException(status.message);
}

try {
    while (qlever_result_has_next(result, status)) {
        String line = qlever_result_next(result, status);
        if (status.code != QLEVER_STATUS_SUCCESS) {
            throw new RuntimeException(status.message);
        }

        // Process one line at a time
        processResultLine(line);

        // Check for memory pressure
        if (Runtime.getRuntime().freeMemory() < 100 * 1024 * 1024) {
            System.gc();
        }
    }
} finally {
    qlever_result_destroy(result);
}
```

#### Step 3: Monitor Memory Usage
```java
// Memory monitoring example
public class MemoryMonitor {
    public static void checkMemoryPressure() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        double usagePercent = (double) usedMemory / maxMemory * 100;

        if (usagePercent > 80) {
            System.gc();
            log.warning("High memory usage: " + usagePercent + "%");
        }
    }
}
```

### Prevention Tips
- Use streaming for large results
- Implement result batch processing
- Add memory monitoring
- Configure appropriate JVM settings
- Use off-heap memory for large datasets

---

## 5. Result Iteration Issues

### Error Message
```
QLever Error: Invalid result handle
QLever Error: Result iteration out of bounds
QLever Error: Result already consumed
```

### Root Cause
- Result handle used after destruction
- Iteration without checking hasNext()
- Concurrent modification of result
- Improper resource cleanup

### Solution Steps

#### Step 1: Proper Resource Management
```java
// Use try-with-resources pattern
public List<String> executeQuery(String query, QLeverIndex index) {
    QleverResult result = null;
    try {
        result = qlever_query_exec(index, query, QLEVER_MEDIA_JSON, status);
        if (status.code != QLEVER_STATUS_SUCCESS) {
            throw new RuntimeException(status.message);
        }

        List<String> results = new ArrayList<>();
        while (qlever_result_has_next(result, status)) {
            String line = qlever_result_next(result, status);
            if (status.code != QLEVER_STATUS_SUCCESS) {
                throw new RuntimeException(status.message);
            }
            results.add(line);
        }
        return results;
    } finally {
        if (result != null) {
            qlever_result_destroy(result);
        }
    }
}
```

#### Step 2: Thread-Safe Result Processing
```java
// Each thread should have its own result handle
public class QueryExecutor {
    public void executeConcurrentQueries(List<String> queries, QLeverIndex index) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (String query : queries) {
            executor.submit(() -> {
                QleverResult result = null;
                try {
                    result = qlever_query_exec(index, query,
                        QLEVER_MEDIA_JSON, status);
                    processResultSafely(result);
                } finally {
                    if (result != null) {
                        qlever_result_destroy(result);
                    }
                }
            });
        }
    }
}
```

### Prevention Tips
- Implement AutoCloseable for result handles
- Use try-with-resources consistently
- Document single-consumer requirement
- Add result validation checks

```java
// AutoCloseable implementation
public class QLeverResult implements AutoCloseable {
    private final QLeverResult resultHandle;
    private boolean closed = false;

    public QLeverResult(QLeverResult resultHandle) {
        this.resultHandle = resultHandle;
    }

    public String next() throws QLeverException {
        if (closed) {
            throw new IllegalStateException("Result already closed");
        }
        // ... implementation
    }

    @Override
    public void close() {
        if (!closed) {
            qlever_result_destroy(resultHandle);
            closed = true;
        }
    }
}
```

---

## 6. Thread Safety Problems

### Error Message
```
QLever Error: Concurrent modification detected
QLever Error: Index accessed from multiple threads without proper synchronization
java.util.ConcurrentModificationException
```

### Root Cause
- Shared index handle without proper synchronization
- Concurrent result iteration
- Race conditions in status reporting
- Non-atomic operations

### Solution Steps

#### Step 1: Index Sharing Strategy
```java
// Thread-safe index wrapper
public class ThreadSafeIndex {
    private final QLeverIndex index;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public ThreadSafeIndex(QLeverIndex index) {
        this.index = index;
    }

    public QLeverResult executeQuery(String query, QleverMediaType mediaType) {
        lock.readLock().lock();
        try {
            QleverStatus status = new QleverStatus();
            QLeverResult result = qlever_query_exec(index, query, mediaType, status);
            if (status.code != QLEVER_STATUS_SUCCESS) {
                throw new QLeverException(status.message);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
```

#### Step 2: Virtual Thread Best Practices
```java
// Virtual thread pool for query execution
public class QueryService {
    private final ExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor();

    public CompletableFuture<List<String>> executeAsync(String query,
        QLeverIndex index) {
        return CompletableFuture.supplyAsync(() -> {
            QLeverResult result = null;
            try {
                result = qlever_query_exec(index, query,
                    QLEVER_MEDIA_JSON, new QleverStatus());
                // Process results
                return processResults(result);
            } finally {
                if (result != null) {
                    qlever_result_destroy(result);
                }
            }
        }, executor);
    }
}
```

#### Step 3: Status Handling
```java
// Thread-safe status handling
public class StatusReporter {
    private final AtomicReference<QleverStatus> currentStatus =
        new AtomicReference<>();

    public QleverStatus executeWithStatus(Runnable operation) {
        QleverStatus status = new QleverStatus();
        currentStatus.set(status);

        try {
            operation.run();
            return status;
        } finally {
            currentStatus.set(null);
        }
    }
}
```

### Prevention Tips
- Use appropriate locking strategies
- Prefer virtual threads for I/O-bound operations
- Avoid shared mutable state
- Implement proper synchronization primitives

---

## 7. Performance Issues

### Error Message
```
QLever Error: Query timeout
QLever Error: Slow query execution
High CPU usage detected
Long GC pauses observed
```

### Root Cause
- Inefficient SPARQL queries
- Missing query optimizations
- Excessive object allocations
- Poor JVM configuration

### Solution Steps

#### Step 1: Query Optimization
```sparql
-- Bad: Select all columns without filtering
SELECT * WHERE { ?s ?p ?o }

-- Good: Specific selects with filters
SELECT ?s ?p WHERE {
    ?s a ex:Person ;
       ex:name ?name ;
       ex:age ?age .
    FILTER(?age > 18)
    OPTIONAL { ?s ex:email ?p }
}

-- Use LIMIT for testing
SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 1000
```

#### Step 2: Performance Monitoring
```java
// Query timing wrapper
public class TimedQueryExecutor {
    public QLeverResult executeQuery(String query, QLeverIndex index,
        QleverMediaType mediaType) {
        long startTime = System.nanoTime();

        QleverStatus status = new QleverStatus();
        QLeverResult result = qlever_query_exec(index, query, mediaType, status);

        long duration = System.nanoTime() - startTime;
        double durationMs = duration / 1_000_000.0;

        if (durationMs > 1000) { // Log slow queries
            log.warning("Slow query detected: " + durationMs + "ms");
            log.warning("Query: " + query);
        }

        return result;
    }
}
```

#### Step 3: JVM Tuning
```bash
# G1GC for balanced performance
java -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
     -XX:InitiatingHeapOccupancyPercent=35 \
     -Xms2g -Xmx8g

# ParallelGC for high throughput
java -XX:+UseParallelGC -XX:ParallelGCThreads=4 \
     -XX:MaxGCPauseMillis=100 -XX:+UseAdaptiveSizePolicy \
     -Xms2g -Xmx8g
```

### Prevention Tips
- Implement query performance monitoring
- Use query timeouts
- Optimize JVM settings for workload
- Consider off-heap memory for large datasets
- Implement connection pooling for repeated queries

---

## 8. Platform-Specific Issues

### Linux-Specific Issues

#### Error Message
```
error while loading shared libraries: libqleverjni.so: cannot open shared object file
Segmentation fault (core dumped)
```

#### Solutions
```bash
# Set correct library path
export LD_LIBRARY_PATH=$(pwd)/src/main/native/build:$LD_LIBRARY_PATH

# Check for missing dependencies
ldd libqleverjni.so

# Generate core dumps for debugging
ulimit -c unlimited
./your-application
gdb -core core.12345 your-application
```

### macOS-Specific Issues

#### Error Message
```
dyld: Library not loaded: @rpath/libqleverjni.dylib
Library not loaded: /usr/local/lib/libqleverjni.dylib
```

#### Solutions
```bash
# Check library architecture
file libqleverjni.dylib
# Should show: Mach-O 64-bit dynamically linked shared library x86_64

# For Apple Silicon, use Rosetta if needed
arch -x86_64 java -Djava.library.path=... YourApplication

# Set DYLD_LIBRARY_PATH
export DYLD_LIBRARY_PATH=$(pwd)/src/main/native/build:$DYLD_LIBRARY_PATH
```

### Windows-Specific Issues

#### Error Message
```
The specified module could not be found
Unhandled exception at 0x00007FF... in java.exe
DLL load failed while loading qleverjni.dll
```

#### Solutions
```cmd
# Add DLL to PATH
set PATH=C:\path\to\build;%PATH%

# Use dependency Walker to check dependencies
depends.exe qleverjni.dll

# Check for architecture mismatch
file qleverjni.dll
# Should show: PE32+ executable (DLL) (x86-64) 64-bit
```

### Cross-Platform Solutions

#### Build Script
```bash
#!/bin/bash
# cross-platform-build.sh

PLATFORM=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)

case "$PLATFORM" in
    linux)
        CMAKE_ARGS="-DCMAKE_BUILD_TYPE=Release"
        ;;
    darwin)
        CMAKE_ARGS="-DCMAKE_BUILD_TYPE=Release -DCMAKE_OSX_ARCHITECTURES=$ARCH"
        ;;
    mingw*|msys*|cygwin*)
        CMAKE_ARGS="-DCMAKE_BUILD_TYPE=Release -A x64"
        ;;
    *)
        echo "Unsupported platform: $PLATFORM"
        exit 1
        ;;
esac

mkdir -p build
cd build
cmake .. $CMAKE_ARGS
make -j$(nproc)
```

#### Platform Detection in Java
```java
public class PlatformUtils {
    public static boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static String getNativeLibraryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "qleverjni.dll";
        } else if (os.contains("mac")) {
            return "libqleverjni.dylib";
        } else {
            return "libqleverjni.so";
        }
    }
}
```

---

## Additional Resources

### Debugging Tools
- **GDB/LLDB**: Native code debugging
- **JDB**: Java debugging
- **VisualVM**: JVM monitoring
- **YourKit/Java Flight Recorder**: Profiling

### Logging Configuration
```java
// Enable detailed logging
public class QLeverConfig {
    static {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        System.setProperty("qlever.debug", "true");
    }
}
```

### Performance Checklist
- [ ] Verify library architecture matches JVM
- [ ] Check memory settings are adequate
- [ ] Monitor query execution times
- [ ] Verify thread safety of concurrent access
- [ ] Check for file descriptor leaks
- [ ] Monitor garbage collection behavior

### Getting Help
1. Check the QLever documentation
2. Review this troubleshooting guide
3. Search existing issues
4. Create a new issue with:
   - Platform and architecture
   - Error messages with full stack traces
   - Minimal reproducible example
   - Expected vs actual behavior