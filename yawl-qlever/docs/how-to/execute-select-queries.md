# How to Execute SELECT Queries

Learn how to execute SPARQL SELECT queries and get results in different formats.

## Problem

You need to execute SPARQL SELECT queries against a QLever index and get the results in a specific format.

## Solution

Use the `selectToJson()`, `selectToTsv()`, `selectToCsv()`, and `selectToXml()` methods to execute SELECT queries and get formatted results.

## Code Examples

### Basic SELECT Query (JSON Format)

```java
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import java.nio.file.Path;

Path indexPath = Path.of("/var/lib/qlever/workflow-index");
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Execute SELECT query - returns JSON
    String jsonResult = engine.selectToJson("""
        SELECT ?case ?status ?created
        WHERE {
            ?case workflow:status ?status;
                  workflow:created ?created.
            FILTER(?status = "active")
        }
        LIMIT 10
        """);

    System.out.println(jsonResult);
    // Output:
    // {
    //   "head": { "vars": ["case", "status", "created"] },
    //   "results": {
    //     "bindings": [
    //       { "case": { "type": "uri", "value": "http://example.com/case/123" },
    //         "status": { "type": "literal", "value": "active" },
    //         "created": { "type": "literal", "value": "2024-01-15T10:30:00Z" } }
    //     ]
    //   }
    // }
}
```

### Multiple Formats

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String query = """
        SELECT ?case ?status ?priority
        WHERE {
            ?case workflow:status ?status;
                  workflow:priority ?priority.
        }
        LIMIT 5
        """;

    // Get results in different formats
    String json = engine.selectToJson(query);
    String tsv = engine.selectToTsv(query);
    String csv = engine.selectToCsv(query);
    String xml = engine.selectToXml(query);

    // Each format is optimized for different use cases:
    // - JSON: Programmatic processing
    // - TSV: Easy parsing, spreadsheet compatibility
    // - CSV: Excel import
    // - XML: RDF tool compatibility
}
```

### Using Variables in Queries

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Query with FILTER conditions
    String highPriorityCases = engine.selectToJson("""
        SELECT ?case ?status ?priority
        WHERE {
            ?case workflow:status ?status;
                  workflow:priority ?priority.
            FILTER(?priority >= 8 && ?status = "active")
        }
        ORDER BY DESC(?priority)
        LIMIT 20
        """);

    // Query with OPTIONAL patterns
    String optionalResults = engine.selectToJson("""
        SELECT ?case ?status ?assignedTo
        WHERE {
            ?case workflow:status ?status.
            OPTIONAL { ?case workflow:assignedTo ?assignedTo. }
        }
        LIMIT 10
        """);
}
```

### Aggregation Queries

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Count cases by status
    String statusCounts = engine.selectToJson("""
        SELECT ?status (COUNT(?case) AS ?count)
        WHERE {
            ?case workflow:status ?status.
        }
        GROUP BY ?status
        ORDER BY DESC(?count)
        """);

    // Find average priority
    String avgPriority = engine.selectToJson("""
        SELECT (AVG(?priority) AS ?avgPriority)
        WHERE {
            ?case workflow:priority ?priority.
        }
        """);

    // Complex aggregation with HAVING
    String filteredCounts = engine.selectToJson("""
        SELECT ?status (COUNT(?case) AS ?count)
        WHERE {
            ?case workflow:status ?status.
        }
        GROUP BY ?status
        HAVING(?count > 5)
        ORDER BY DESC(?count)
        """);
}
```

## Best Practices

### 1. Always Use try-with-resources

```java
// ✅ Good - ensures proper cleanup
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o } LIMIT 100");
}

// ❌ Bad - may cause resource leaks
QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath);
String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o } LIMIT 100");
```

### 2. Check Engine Availability

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    if (engine.isAvailable()) {
        String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
    } else {
        System.err.println("Engine is not available");
    }
}
```

### 3. Handle Exceptions

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    try {
        String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
        System.out.println(result);
    } catch (org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException e) {
        System.err.println("Query error: " + e.getMessage());
    }
}
```

### 4. Use LIMIT for Large Results

```java
// ✅ Good - limits result size
String result = engine.selectToJson("""
    SELECT ?case ?status
    WHERE { ?case workflow:status ?status }
    LIMIT 1000
    """);

// ❌ Bad - may return huge results
// String result = engine.selectToJson("SELECT ?case ?status WHERE { ?case workflow:status ?status }");
```

## Common Patterns

### 1. Pagination

```java
int offset = 0;
int pageSize = 100;
List<String> allResults = new ArrayList<>();

while (true) {
    String query = String.format("""
        SELECT ?case ?status
        WHERE { ?case workflow:status ?status }
        LIMIT %d OFFSET %d
        """, pageSize, offset);

    String page = engine.selectToJson(query);
    if (page.isEmpty() || page.contains("\"bindings\": []")) {
        break;
    }

    allResults.add(page);
    offset += pageSize;
}
```

### 2. Batch Processing

```java
List<String> queries = Arrays.asList(
    "SELECT ?case WHERE { ?case workflow:status 'active' }",
    "SELECT ?case WHERE { ?case workflow:status 'completed' }",
    "SELECT ?case WHERE { ?case workflow:status 'pending' }"
);

for (String query : queries) {
    try {
        String result = engine.selectToJson(query);
        processResults(result);
    } catch (SparqlEngineException e) {
        System.err.println("Failed to execute query: " + query);
    }
}
```

## Performance Tips

1. **Use appropriate LIMITs** - Avoid loading huge result sets
2. **Filter early** - Use FILTER clauses to reduce data
3. **Index properties** - Ensure your QLever index is optimized
4. **Reuse engine instance** - Don't create new engines for each query

## Troubleshooting

### Empty Results

```java
// Check if query syntax is correct
try {
    String result = engine.selectToJson("SELECT ?s WHERE { ?s ?p ?o }");
    System.out.println("Query returned: " + result);
} catch (SparqlEngineException e) {
    System.err.println("Query failed: " + e.getMessage());
}
```

### Memory Issues

```java
// For large queries, process results incrementally
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.selectToJson("SELECT ?case WHERE { ?case workflow:status 'active' } LIMIT 5000");
    // Process results in chunks
    processLargeResult(result);
}
```