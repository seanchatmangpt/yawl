# How to Execute CONSTRUCT Queries

Learn how to execute SPARQL CONSTRUCT queries to retrieve RDF data in Turtle format.

## Problem

You need to execute SPARQL CONSTRUCT queries against a QLever index to retrieve RDF data in Turtle format.

## Solution

Use the `constructToTurtle()` method to execute CONSTRUCT queries and get results in Turtle format.

## Code Examples

### Basic CONSTRUCT Query

```java
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import java.nio.file.Path;

Path indexPath = Path.of("/var/lib/qlever/workflow-index");
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Execute CONSTRUCT query - returns Turtle
    String turtleResult = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:status ?status;
                  workflow:created ?created.
        }
        WHERE {
            ?case workflow:status ?status;
                  workflow:created ?created.
            FILTER(?status = "active")
        }
        LIMIT 100
        """);

    System.out.println(turtleResult);
    // Output:
    // @prefix workflow: <http://yawl.io/workflow#> .
    //
    // <http://example.com/case/123> workflow:status "active" ;
    //                                workflow:created "2024-01-15T10:30:00Z" .
    //
    // <http://example.com/case/124> workflow:status "active" ;
    //                                workflow:created "2024-01-15T11:45:00Z" .
}
```

### Building RDF Graphs

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Construct all workflow-related triples
    String fullGraph = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?subject ?predicate ?object.
        }
        WHERE {
            ?subject ?predicate ?object.
            FILTER(STRAFTER(STR(?predicate), "workflow:") != "")
        }
        """);

    // Construct specific pattern with filters
    String filteredGraph = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
        CONSTRUCT {
            ?case workflow:status ?status;
                  workflow:priority ?priority.
        }
        WHERE {
            ?case workflow:status ?status;
                  workflow:priority ?priority.
            FILTER(?priority >= 8 && DATETIME(?created) >= "2024-01-01"^^xsd:dateTime)
        }
        """);
}
```

### Using DESCRIBE Pattern

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // DESCRIBE queries work with CONSTRUCT
    String describeResult = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT WHERE {
            ?case a workflow:Case;
                  workflow:status ?status.
            ?case ?p ?o.
        }
        LIMIT 50
        """);

    // Construct based on SELECT pattern
    String selectBased = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?s ?p ?o.
        }
        WHERE {
            {
                SELECT ?s ?p ?o
                WHERE {
                    ?s ?p ?o.
                    FILTER(STRAFTER(STR(?s), "http://example.com/case/") != "")
                }
                LIMIT 1000
            }
        }
        """);
}
```

### Complex CONSTRUCT Patterns

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Nested patterns with OPTIONAL
    String optionalConstruct = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:status ?status;
                  workflow:assignedTo ?assignedTo.
        }
        WHERE {
            ?case workflow:status ?status.
            OPTIONAL {
                ?case workflow:assignedTo ?assignedTo.
            }
        }
        LIMIT 200
        """);

    // Using BIND and VALUES
    String complexConstruct = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:status ?status.
        }
        WHERE {
            VALUES ?status { "active" "pending" }
            ?case workflow:status ?status;
                  workflow:priority ?priority.
            FILTER(?priority > 5)
        }
        """);
}
```

## Best Practices

### 1. Always Use try-with-resources

```java
// ✅ Good - ensures proper cleanup
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 100");
}

// ❌ Bad - may cause resource leaks
QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath);
String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 100");
```

### 2. Use LIMIT with CONSTRUCT

```java
// ✅ Good - limits result size
String result = engine.constructToTurtle("""
    PREFIX workflow: <http://yawl.io/workflow#>
    CONSTRUCT WHERE { ?s ?p ?o }
    LIMIT 5000
    """);

// ❌ Bad - may return huge RDF graph
// String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o }");
```

### 3. Filter Early

```java
// ✅ Good - filters at query time
String filtered = engine.constructToTurtle("""
    PREFIX workflow: <http://yawl.io/workflow#>
    CONSTRUCT {
        ?case workflow:status ?status.
    }
    WHERE {
        ?case workflow:status ?status.
        FILTER(?status = "active")
    }
    LIMIT 100
    """);

// ❌ Bad - filters after getting huge results
// String huge = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o }");
// String filtered = filterResults(huge);
```

### 4. Check Engine Availability

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    if (engine.isAvailable()) {
        String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 100");
    } else {
        System.err.println("Engine is not available");
    }
}
```

## Common Patterns

### 1. Extract Specific Properties

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Extract only case-status relationships
    String statusOnly = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:status ?status.
        }
        WHERE {
            ?case workflow:status ?status.
        }
        LIMIT 1000
        """);

    // Extract with custom prefix handling
    String withPrefixes = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        PREFIX case: <http://example.com/case/>
        CONSTRUCT {
            ?case workflow:status ?status;
                  workflow:created ?created.
        }
        WHERE {
            ?case workflow:status ?status;
                  workflow:created ?created.
        }
        LIMIT 500
        """);
}
```

### 2. Transform Data Structure

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Reconstruct triples with new properties
    String reconstructed = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:isActive ?boolStatus.
        }
        WHERE {
            ?case workflow:status ?status.
            BIND(IF(?status = "active", "true", "false") AS ?boolStatus)
        }
        LIMIT 100
        """);

    // Create derived triples
    String derived = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
        CONSTRUCT {
            ?case workflow:hasHighPriority ?highPriority.
        }
        WHERE {
            ?case workflow:priority ?priority.
            BIND(?priority >= 8 AS ?highPriority)
        }
        LIMIT 200
        """);
}
```

### 3. Handle Large Results

```java
// For very large CONSTRUCT results, process in chunks
int offset = 0;
int pageSize = 1000;
List<String> allTurtle = new ArrayList<>();

while (true) {
    String query = String.format("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT WHERE {
            ?s ?p ?o.
        }
        LIMIT %d OFFSET %d
        """, pageSize, offset);

    String chunk = engine.constructToTurtle(query);
    if (chunk.isEmpty() || chunk.trim().equals("@prefix ")) {
        break;
    }

    allTurtle.add(chunk);
    offset += pageSize;
}

// Combine all chunks
String fullTurtle = String.join("\n", allTurtle);
```

## Performance Tips

1. **Use LIMIT** - Always limit CONSTRUCT results
2. **Filter early** - Use FILTER clauses to reduce triples
3. **Be specific** - Construct only needed triples
4. **Reuse engine** - Don't create new engines for each query
5. **Consider sharding** - Split large CONSTRUCT queries

## Troubleshooting

### Empty Results

```java
try {
    String result = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 10");
    if (result.isEmpty()) {
        System.out.println("Query returned empty result");
    }
} catch (SparqlEngineException e) {
    System.err.println("Query failed: " + e.getMessage());
}
```

### Memory Issues with Large Results

```java
// Process results incrementally
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String result = engine.constructToTurtle("""
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT WHERE { ?s ?p ?o }
        LIMIT 10000
        """);

    // Process in memory-efficient way
    processTurtleIncrementally(result);
}
```

### Turtle Format Issues

```java
// Ensure proper Turtle formatting
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String turtle = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 5");

    // Check if it's valid Turtle
    if (!turtle.trim().startsWith("@prefix")) {
        System.err.println("Invalid Turtle format");
    } else {
        // Process valid Turtle
        System.out.println("Valid Turtle received");
    }
}
```