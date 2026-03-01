# How-to Guides for QLever Embedded SPARQL Engine

This section provides practical, task-oriented guides for using the QLever Embedded SPARQL Engine. Each guide focuses on solving a specific problem and provides direct steps with code examples.

## Available Guides

### 1. [Execute SELECT Queries](./execute-select-queries.md)
Learn how to execute SPARQL SELECT queries and get results in different formats (JSON, TSV, CSV, XML).

### 2. [Execute CONSTRUCT Queries](./execute-construct-queries.md)
Learn how to execute CONSTRUCT queries to retrieve RDF data in Turtle format.

### 3. [Handle Errors](./handle-errors.md)
Learn how to handle and debug common errors when working with QLever.

### 4. [Configure Output Formats](./configure-output-formats.md)
Learn how to configure and use different output formats for your query results.

## Quick Start

```java
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import java.nio.file.Path;

// Create engine with pre-built index
Path indexPath = Path.of("/var/lib/qlever/workflow-index");
try (SparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Execute queries
    String result = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT { ?case workflow:status ?status }
        WHERE { ?case workflow:status ?status }
        LIMIT 100
        """);
    System.out.println(result);
}
```

## Prerequisites

- Java 25 with `--enable-preview --enable-native-access=ALL-UNNAMED`
- Pre-built QLever index directory
- Native library in `java.library.path`

## Running Examples

All examples assume you have:

1. A pre-built QLever index
2. The native library loaded
3. Proper JVM arguments

```bash
java --enable-preview --enable-native-access=ALL-UNNAMED \
     -Djava.library.path=/path/to/native/libs \
     -Dqlever.test.index=/path/to/test/index \
     -jar your-app.jar
```

## Need Help?

- Check [Error Handling](./handle-errors.md) for common issues
- Refer to the main [README.md](../../README.md) for setup instructions
- Review the [QLever Documentation](https://github.com/ad-freiburg/qlever)