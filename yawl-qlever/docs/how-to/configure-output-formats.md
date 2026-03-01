# How to Configure Output Formats

Learn how to configure and use different output formats for your query results.

## Problem

You need to execute SPARQL queries and get results in specific formats for different use cases.

## Solution

Use the `selectToMediaType()` method with the `QLeverMediaType` enum to get results in your desired format.

## Available Formats

| Format | MIME Type | Use Case |
|--------|-----------|----------|
| JSON | `application/sparql-results+json` | Programmatic processing, APIs |
| XML | `application/sparql-results+xml` | RDF tools, legacy systems |
| CSV | `text/csv` | Excel import, data analysis |
| TSV | `text/tab-separated-values` | Spreadsheet tools, command-line processing |
| Turtle | `text/turtle` | RDF data, semantic web applications |
| N-Triples | `application/n-triples` | Simple RDF serialization |
| RDF/XML | `application/rdf+xml` | Legacy RDF applications |

## Code Examples

### Using selectToMediaType Method

```java
import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverMediaType;
import java.nio.file.Path;

Path indexPath = Path.of("/var/lib/qlever/workflow-index");
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String query = """
        SELECT ?case ?status ?created
        WHERE {
            ?case workflow:status ?status;
                  workflow:created ?created.
        }
        LIMIT 10
        """;

    // Get results in different formats
    String json = engine.selectToMediaType(query, QLeverMediaType.JSON);
    String xml = engine.selectToMediaType(query, QLeverMediaType.XML);
    String csv = engine.selectToMediaType(query, QLeverMediaType.CSV);
    String tsv = engine.selectToMediaType(query, QLeverMediaType.TSV);
}
```

### Format-Specific Examples

#### JSON Format

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

    String json = engine.selectToMediaType(query, QLeverMediaType.JSON);
    // Output:
    // {
    //   "head": { "vars": ["case", "status", "priority"] },
    //   "results": {
    //     "bindings": [
    //       {
    //         "case": { "type": "uri", "value": "http://example.com/case/123" },
    //         "status": { "type": "literal", "value": "active" },
    //         "priority": { "type": "literal", "value": "5" }
    //       }
    //     ]
    //   }
    // }
}
```

#### CSV Format

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String query = """
        SELECT ?case ?status ?created
        WHERE {
            ?case workflow:status ?status;
                  workflow:created ?created.
        }
        LIMIT 3
        """;

    String csv = engine.selectToMediaType(query, QLeverMediaType.CSV);
    // Output:
    // case,status,created
    // "http://example.com/case/123","active","2024-01-15T10:30:00Z"
    // "http://example.com/case/124","pending","2024-01-15T11:45:00Z"
    // "http://example.com/case/125","completed","2024-01-15T09:15:00Z"
}
```

#### TSV Format

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String query = """
        SELECT ?case ?status
        WHERE {
            ?case workflow:status ?status.
        }
        LIMIT 3
        """;

    String tsv = engine.selectToMediaType(query, QLeverMediaType.TSV);
    // Output:
    // case	status
    // http://example.com/case/123	active
    // http://example.com/case/124	pending
    // http://example.com/case/125	completed
}
```

#### XML Format

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String query = """
        SELECT ?case ?status
        WHERE {
            ?case workflow:status ?status.
        }
        LIMIT 2
        """;

    String xml = engine.selectToMediaType(query, QLeverMediaType.XML);
    // Output:
    // <?xml version="1.0"?>
    // <sparql xmlns="http://www.w3.org/2005/sparql-results#">
    //   <head>
    //     <variable name="case"/>
    //     <variable name="status"/>
    //   </head>
    //   <results>
    //     <result>
    //       <binding name="case">
    //         <uri>http://example.com/case/123</uri>
    //       </binding>
    //       <binding name="status">
    //         <literal>active</literal>
    //       </binding>
    //     </result>
    //   </results>
    // </sparql>
}
```

#### RDF Formats

```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String constructQuery = """
        PREFIX workflow: <http://yawl.io/workflow#>
        CONSTRUCT {
            ?case workflow:status ?status.
        }
        WHERE {
            ?case workflow:status ?status.
        }
        LIMIT 5
        """;

    // Turtle format (default for CONSTRUCT)
    String turtle = engine.constructToTurtle(constructQuery);

    // N-Triples format
    String ntriples = engine.selectToMediaType(constructQuery, QLeverMediaType.N_TRIPLES);

    // RDF/XML format
    String rdfXml = engine.selectToMediaType(constructQuery, QLeverMediaType.RDF_XML);
}
```

## Choosing the Right Format

### For Programmatic Processing

```java
// Use JSON for APIs and programmatic consumption
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    String json = engine.selectToMediaType(query, QLeverMediaType.JSON);

    // Parse JSON using your preferred JSON library
    JSONObject jsonObject = new JSONObject(json);
    JSONArray bindings = jsonObject.getJSONObject("results").getJSONArray("bindings");

    for (int i = 0; i < bindings.length(); i++) {
        JSONObject binding = bindings.getJSONObject(i);
        String caseUri = binding.getJSONObject("case").getString("value");
        String status = binding.getJSONObject("status").getString("value");
        // Process data
    }
}
```

### For Data Analysis

```java
// Use CSV or TSV for data analysis
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // CSV for Excel compatibility
    String csv = engine.selectToMediaType(query, QLeverMediaType.CSV);
    processCSVForAnalysis(csv);

    // TSV for easier parsing
    String tsv = engine.selectToMediaType(query, QLeverMediaType.TSV);
    processTSVForAnalysis(tsv);
}
```

### For RDF Applications

```java
// Use RDF formats for semantic web applications
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Turtle for most RDF applications
    String turtle = engine.constructToTurtle(constructQuery);
    loadRDFGraph(turtle);

    // N-Triples for simple, line-based processing
    String ntriples = engine.selectToMediaType(constructQuery, QLeverMediaType.N_TRIPLES);
    processNTriples(ntriples);
}
```

## Format Conversion Utilities

### Convert Between Formats

```java
import org.yawlfoundation.yawl.qlever.QLeverMediaType;

public class FormatConverter {
    public String convertFormat(String input, String inputFormat, String outputFormat) {
        // Parse input based on format
        switch (inputFormat.toLowerCase()) {
            case "json":
                return convertFromJSON(input, outputFormat);
            case "csv":
                return convertFromCSV(input, outputFormat);
            case "tsv":
                return convertFromTSV(input, outputFormat);
            default:
                throw new IllegalArgumentException("Unsupported input format: " + inputFormat);
        }
    }

    private String convertFromJSON(String json, String outputFormat) {
        // Parse JSON and convert to desired format
        // Implementation depends on your needs
        return json; // Simplified
    }
}
```

### Detect Format from File

```java
public String detectAndReadFormat(Path filePath) throws IOException {
    String content = Files.readString(filePath);
    String filename = filePath.getFileName().toString().toLowerCase();

    if (filename.endsWith(".json")) {
        return content;
    } else if (filename.endsWith(".csv")) {
        return processCSV(content);
    } else if (filename.endsWith(".ttl")) {
        return processTurtle(content);
    }

    throw new IllegalArgumentException("Unsupported file format");
}
```

## Best Practices

### 1. Choose Appropriate Format for Your Use Case

```java
// ✅ Good - format matches use case
public void exportToExcel(Path indexPath, String query) {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        String csv = engine.selectToMediaType(query, QLeverMediaType.CSV);
        saveToCSV(csv, "workflow-report.csv");
    }
}

// ❌ Bad - wrong format for use case
// public void exportToExcel(Path indexPath, String query) {
//     String json = engine.selectToMediaType(query, QLeverMediaType.JSON);
//     // Hard to parse for Excel
// }
```

### 2. Handle Large Results Efficiently

```java
// Process large results in chunks
public void processLargeResult(Path indexPath, String query, QLeverMediaType format) {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        String result = engine.selectToMediaType(query, format);

        // Process based on format
        switch (format) {
            case CSV:
                processCSVInChunks(result);
                break;
            case JSON:
                processJSONInChunks(result);
                break;
            default:
                processLargeString(result);
        }
    }
}
```

### 3. Validate Output Format

```java
public void validateOutputFormat(String result, QLeverMediaType expectedFormat) {
    if (result == null || result.isEmpty()) {
        throw new IllegalArgumentException("Empty result");
    }

    switch (expectedFormat) {
        case JSON:
            if (!result.trim().startsWith("{")) {
                throw new IllegalArgumentException("Invalid JSON format");
            }
            break;
        case CSV:
            if (!result.contains(",")) {
                throw new IllegalArgumentException("Invalid CSV format");
            }
            break;
        case TSV:
            if (!result.contains("\t")) {
                throw new IllegalArgumentException("Invalid TSV format");
            }
            break;
    }
}
```

## Performance Considerations

### 1. Format Impact on Performance

```java
// Benchmark different formats
public void benchmarkFormats(Path indexPath, String query) {
    Map<QLeverMediaType, Long> timings = new EnumMap<>(QLeverMediaType.class);

    for (QLeverMediaType format : QLeverMediaType.values()) {
        long startTime = System.nanoTime();
        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            engine.selectToMediaType(query, format);
        }
        long duration = System.nanoTime() - startTime;
        timings.put(format, duration);
    }

    // Log results
    timings.forEach((format, time) -> {
        System.out.println(format + ": " + (time / 1_000_000.0) + "ms");
    });
}
```

### 2. Memory Usage by Format

```java
// Compare memory usage
public void compareMemoryUsage(Path indexPath, String query) {
    Runtime runtime = Runtime.getRuntime();

    for (QLeverMediaType format : Arrays.asList(QLeverMediaType.JSON, QLeverMediaType.CSV, QLeverMediaType.TSV)) {
        long before = runtime.totalMemory() - runtime.freeMemory();

        try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
            String result = engine.selectToMediaType(query, format);
        }

        long after = runtime.totalMemory() - runtime.freeMemory();
        System.out.println(format + ": " + (after - before) + " bytes used");
    }
}
```

## Common Patterns

### 1. Adaptive Format Selection

```java
public String getResultsInPreferredFormat(Path indexPath, String query, String preferredFormat) {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        QLeverMediaType format = determineBestFormat(preferredFormat);
        return engine.selectToMediaType(query, format);
    }
}

private QLeverMediaType determineBestFormat(String preferred) {
    try {
        return QLeverMediaType.valueOf(preferred.toUpperCase());
    } catch (IllegalArgumentException e) {
        // Fallback to JSON
        return QLeverMediaType.JSON;
    }
}
```

### 2. Multi-format Export

```java
public void exportToMultipleFormats(Path indexPath, String query) {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        Map<String, String> results = new HashMap<>();

        results.put("json", engine.selectToMediaType(query, QLeverMediaType.JSON));
        results.put("csv", engine.selectToMediaType(query, QLeverMediaType.CSV));
        results.put("tsv", engine.selectToMediaType(query, QLeverMediaType.TSV));
        results.put("xml", engine.selectToMediaType(query, QLeverMediaType.XML));

        // Save all formats
        results.forEach((format, content) -> {
            try {
                Files.writeString(Path.of("report." + format.toLowerCase()), content);
            } catch (IOException e) {
                System.err.println("Failed to save " + format + ": " + e.getMessage());
            }
        });
    }
}
```