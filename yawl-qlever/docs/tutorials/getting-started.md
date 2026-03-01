# Getting Started with QLever Embedded SPARQL Engine

> **Diataxis Tutorial Guide**: This tutorial follows the *Learning* path, focusing on step-by-step implementation with practical examples.

## Introduction

The QLever Embedded SPARQL Engine provides ultra-fast SPARQL query execution inside your JVM process, eliminating network overhead and achieving sub-100µs query latency. This integration uses Java 25's Panama Foreign Function Interface (FFM) for direct communication with the native QLever engine.

## Prerequisites

- Java 25+ with preview features enabled
- Maven 3.8+
- Pre-built QLever index directory
- Native `libqlever_ffi` library (`.so`, `.dylib`, or `.dll`)

## Step 1: Adding QLever Dependency

First, add the QLever module to your YAWL project's dependencies:

### Maven Configuration

Add this to your `pom.xml`:

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-qlever</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

### Maven Compilation

Compile with Java 25 preview features:

```bash
mvn clean compile -Denable-preview -Denable-native-access=ALL-UNNAMED
```

## Step 2: Setting Up Native Library

The native library must be available in your `java.library.path`. There are several ways to achieve this:

### Option A: Place in Classpath

Create a `native/` directory in your project:

```
project/
├── src/
├── native/
│   ├── libqlever_ffi.so          # Linux
│   ├── libqlever_ffi.dylib       # macOS
│   └── qlever_ffi.dll           # Windows
└── pom.xml
```

### Option B: Environment Variable

```bash
export LD_LIBRARY_PATH=/path/to/native/libs:$LD_LIBRARY_PATH  # Linux
export DYLD_LIBRARY_PATH=/path/to/native/libs:$DYLD_LIBRARY_PATH  # macOS
export PATH=/path/to/native/libs:$PATH  # Windows
```

## Step 3: Creating Your First QLever Engine Instance

Let's create a simple application that queries a workflow dataset:

### Basic Usage Example

```java
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class QLeverQuickStart {

    public static void main(String[] args) {
        // Path to your pre-built QLever index
        Path indexPath = Paths.get("/var/lib/qlever/workflow-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            // Test basic functionality
            System.out.println("Engine type: " + engine.engineType());
            System.out.println("Triple count: " + engine.getTripleCount());
            System.out.println("Engine available: " + engine.isAvailable());

            // Execute a simple query (covered in next step)
            executeFirstQuery(engine);
        }
    }

    private static void executeFirstQuery(QLeverEmbeddedSparqlEngine engine) {
        try {
            // This will be implemented in the next step
            System.out.println("Ready for queries!");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

## Step 4: Executing Your First SPARQL Query

Now let's execute actual SPARQL queries. QLever Embedded supports multiple query types and output formats.

### SELECT Query Example

```java
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import java.nio.file.Path;

public class QueryExample {

    public static void main(String[] args) throws SparqlEngineException {
        Path indexPath = Path.of("/path/to/your/qlever-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {

            // Basic SELECT query
            String query = """
                PREFIX workflow: <http://yawl.io/workflow#>
                SELECT ?case ?status ?created
                WHERE {
                    ?case a workflow:Case ;
                          workflow:status ?status ;
                          workflow:created ?created .
                }
                ORDER BY DESC(?created)
                LIMIT 5
                """;

            // Execute query and get JSON results
            String jsonResult = engine.selectToJson(query);
            System.out.println("JSON Results:");
            System.out.println(jsonResult);

            // Get TSV format (useful for direct processing)
            String tsvResult = ((QLeverEmbeddedSparqlEngine) engine).selectToTsv(query);
            System.out.println("\nTSV Results:");
            System.out.println(tsvResult);
        }
    }
}
```

### CONSTRUCT Query Example

```java
public class ConstructQueryExample {

    public static void main(String[] args) throws SparqlEngineException {
        Path indexPath = Path.of("/path/to/your/qlever-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {

            String query = """
                PREFIX workflow: <http://yawl.io/workflow#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

                CONSTRUCT {
                    ?case workflow:status ?status .
                    ?case rdfs:label ?label
                }
                WHERE {
                    ?case a workflow:Case ;
                          workflow:status ?status ;
                          rdfs:label ?label .
                }
                LIMIT 10
                """;

            // Get TURTLE format
            String turtle = engine.constructToTurtle(query);
            System.out.println("TURTLE Results:");
            System.out.println(turtle);
        }
    }
}
```

### ASK Query Example

```java
public class AskQueryExample {

    public static void main(String[] args) throws SparqlEngineException {
        Path indexPath = Path.of("/path/to/your/qlever-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {

            String query = """
                PREFIX workflow: <http://yawl.io/workflow#>
                ASK WHERE {
                    ?case a workflow:Case ;
                          workflow:status "completed" .
                }
                """;

            // Execute ASK query
            boolean result = engine.ask(query);
            System.out.println("Cases with completed status exist: " + result);
        }
    }
}
```

## Step 5: Advanced Features

### Error Handling

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    try {
        String result = engine.selectToJson("INVALID QUERY");
    } catch (SparqlEngineException e) {
        System.err.println("Query failed: " + e.getMessage());
        System.err.println("Error code: " + e.getErrorCode());
    }
}
```

### Batch Query Execution

```java
public class BatchQueryExample {

    public static void main(String[] args) throws SparqlEngineException {
        Path indexPath = Path.of("/path/to/your/qlever-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {

            // Define multiple queries
            List<String> queries = List.of(
                "SELECT COUNT(*) { ?s ?p ?o }",
                "SELECT DISTINCT ?type WHERE { ?s a ?type } LIMIT 10",
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5"
            );

            // Execute queries sequentially
            for (String query : queries) {
                try {
                    if (query.startsWith("SELECT")) {
                        String result = engine.selectToJson(query);
                        System.out.println("SELECT result: " + result.length() + " chars");
                    } else if (query.startsWith("CONSTRUCT")) {
                        String result = engine.constructToTurtle(query);
                        System.out.println("CONSTRUCT result: " + result.length() + " chars");
                    }
                } catch (SparqlEngineException e) {
                    System.err.println("Query failed: " + e.getMessage());
                }
            }
        }
    }
}
```

### Performance Monitoring

```java
public class PerformanceExample {

    public static void main(String[] args) {
        Path indexPath = Path.of("/path/to/your/qlever-index");

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {

            String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 1000";

            // Warm up
            engine.selectToJson(query);

            // Benchmark
            long startTime = System.nanoTime();
            String result = engine.selectToJson(query);
            long duration = System.nanoTime() - startTime;

            double durationMs = duration / 1_000_000.0;
            System.out.printf("Query executed in %.3f ms%n", durationMs);
            System.out.println("Result size: " + result.length() + " characters");

        } catch (SparqlEngineException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}
```

## Step 6: Integration with YAWL Workflows

Here's how to integrate QLever Embedded into a YAWL service:

```java
import org.yawlfoundation.yawl.elements.YAWLService;
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;

public class YAWLQLeverService extends YAWLService {

    private QLeverEmbeddedSparqlEngine sparqlEngine;

    @Override
    public void start() {
        try {
            // Initialize QLever engine with workflow index
            Path indexPath = Path.of("/var/lib/qlever/workflow-index");
            sparqlEngine = new QLeverEmbeddedSparqlEngine(indexPath);

            System.out.println("QLever engine started with " +
                sparqlEngine.getTripleCount() + " triples");

        } catch (Exception e) {
            System.err.println("Failed to start QLever engine: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (sparqlEngine != null) {
            sparqlEngine.close();
            sparqlEngine = null;
        }
    }

    public String queryWorkflows(String query) throws SparqlEngineException {
        if (sparqlEngine == null || !sparqlEngine.isAvailable()) {
            throw new SparqlEngineException("QLever engine not available");
        }

        return sparqlEngine.selectToJson(query);
    }
}
```

## Step 7: Running with Maven

Create a simple Maven profile for running QLever applications:

```xml
<profiles>
    <profile>
        <id>qlever-demo</id>
        <activation>
            <property>
                <name>qlever</name>
                <value>true</value>
            </property>
        </activation>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>exec-maven-plugin</artifactId>
                    <version>3.1.0</version>
                    <configuration>
                        <mainClass>com.yourcompany.QLeverQuickStart</mainClass>
                        <arguments>
                            <argument>/path/to/qlever-index</argument>
                        </arguments>
                        <systemProperties>
                            <enable.native.access>ALL-UNNAMED</enable.native.access>
                        </systemProperties>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

Run with:

```bash
mvn exec:java -Dqlever=true -Denable.native.access=ALL-UNNAMED
```

## Common Issues and Solutions

### 1. Native Library Not Found

**Error**: `java.lang.UnsatisfiedLinkError: no qlever_ffi in java.library.path`

**Solution**: Ensure the native library is in your `java.library.path` or in the classpath under `native/`.

### 2. Invalid Index Path

**Error**: `QLeverFfiException: Invalid index directory`

**Solution**: Verify the index directory exists and contains the QLever index files.

### 3. Java Version Issues

**Error**: Preview features not enabled

**Solution**: Compile and run with `--enable-preview --enable-native-access=ALL-UNNAMED`

### 4. Memory Issues

**Solution**: Increase JVM heap size for large datasets:

```bash
java -Xmx4g --enable-preview --enable-native-access=ALL-UNNAMED -jar your-app.jar
```

## Best Practices

1. **Resource Management**: Always use try-with-resources to ensure proper cleanup
2. **Error Handling**: Catch `SparqlEngineException` for query-specific errors
3. **Index Management**: Keep indexes up-to-date and regularly optimize
4. **Query Optimization**: Use appropriate LIMIT clauses and indexing
5. **Logging**: Implement proper logging for debugging and monitoring

## Next Steps

- Explore the full API documentation in the JavaDoc
- Try more complex SPARQL queries and aggregation patterns
- Learn about QLever-specific features like provenance and as-of queries
- Implement caching strategies for frequently executed queries

## Further Reading

- [QLever Documentation](https://qlever.cs.uni-freiburg.de/)
- [SPARQL 1.1 Query Language](https://www.w3.org/TR/sparql11-query/)
- [YAWL Engine Documentation](http://www.yawlfoundation.org/documentation/)