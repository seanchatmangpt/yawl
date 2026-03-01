# QLever Embedded SPARQL Engine - Quick Reference

## Quick Commands

### Create Engine
```java
try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
    // Use engine
}
```

### Execute Queries
```java
// SELECT queries (JSON)
String json = engine.selectToJson("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 100");

// SELECT queries (other formats)
String csv = engine.selectToCsv("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");
String tsv = engine.selectToTsv("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");
String xml = engine.selectToXml("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");

// CONSTRUCT queries (Turtle)
String turtle = engine.constructToTurtle("CONSTRUCT WHERE { ?s ?p ?o } LIMIT 50");

// Generic method
String result = engine.selectToMediaType(query, QLeverMediaType.JSON);
```

## Common Output Formats

| Format | Method | Use Case |
|--------|--------|----------|
| JSON | `selectToJson()` | APIs, programmatic processing |
| CSV | `selectToCsv()` | Excel, data analysis |
| TSV | `selectToTsv()` | Spreadsheets, command-line |
| XML | `selectToXml()` | RDF tools, legacy systems |
| Turtle | `constructToTurtle()` | RDF data, semantic web |

## Error Handling

```java
try {
    try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(indexPath)) {
        String result = engine.selectToJson("SELECT * WHERE { ?s ?p ?o }");
        return result;
    }
} catch (SparqlEngineException e) {
    // Query execution errors
} catch (SparqlEngineUnavailableException e) {
    // Engine not available
} catch (QLeverFfiException e) {
    // Native library errors
}
```

## Available Media Types

```java
QLeverMediaType.values() returns:
- JSON
- XML
- CSV
- TSV
- TURTLE
- N_TRIPLES
- RDF_XML
```

## JVM Arguments

```bash
java --enable-preview --enable-native-access=ALL-UNNAMED \
     -Djava.library.path=/path/to/libs \
     -jar your-app.jar
```

## Check Availability

```java
if (engine.isAvailable()) {
    // Engine is ready
} else {
    // Engine is closed
}

// Get engine info
String type = engine.engineType();
Path index = engine.getIndexPath();
long triples = engine.getTripleCount();
```