# How to Manage Decision Records, Knowledge Base, and Sketches

## Problem

Your workflow project needs to document architecture decisions in MADR format, maintain a searchable knowledge base of how-to articles, and store system architecture sketches — all version-controlled alongside your code. `DataModellingBridge` provides WASM-backed operations for creating, exporting, indexing, and searching all three asset types without any external tooling.

## Prerequisites

- `yawl-data-modelling` on your classpath (version 6.0.0-GA)
- GraalVM JDK 24.1+ at runtime

## Steps

### Part A: Architecture Decision Records (MADR)

#### 1. Create a decision record

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

try (DataModellingBridge bridge = new DataModellingBridge()) {

    String decision = bridge.createDecision(
        1,                                          // sequential number
        "Use PostgreSQL for primary storage",        // title
        "The platform needs an ACID-compliant relational database. " +
        "Data grows at 5 GB/month. We evaluated PostgreSQL, MySQL, and CockroachDB.",
        "Adopt PostgreSQL 16 with connection pooling via PgBouncer. " +
        "Use WAL archival to S3 for point-in-time recovery.",
        "Platform Team"                             // author
    );

    System.out.println("Decision JSON: " + decision.substring(0, 80) + "...");
}
```

#### 2. Export a decision to YAML (for git storage)

```java
String yamlDecision = bridge.exportDecisionToYaml(decision);
System.out.println(yamlDecision);

// Write to disk
Files.writeString(Path.of("decisions/0001-use-postgresql.yaml"), yamlDecision);
```

#### 3. Export a decision to Markdown (for wikis and PR descriptions)

```java
String mdDecision = bridge.exportDecisionToMarkdown(decision);
System.out.println(mdDecision);
```

#### 4. Load an existing decision from YAML

```java
String savedYaml = Files.readString(Path.of("decisions/0001-use-postgresql.yaml"));
String reloaded = bridge.parseDecisionYaml(savedYaml);
```

#### 5. Build a decision index

A decision index tracks all ADRs in the project. Create an index, add decisions, then export:

```java
// Create an empty index
String index = bridge.createDecisionIndex();

// Create decisions
String adr1 = bridge.createDecision(1, "Use PostgreSQL", "Context...", "Decision...", "Team");
String adr2 = bridge.createDecision(2, "Use Kafka for events", "Context...", "Decision...", "Team");

// Add decisions to the index with their filenames
index = bridge.addDecisionToIndex(index, adr1, "0001-use-postgresql.yaml");
index = bridge.addDecisionToIndex(index, adr2, "0002-use-kafka-for-events.yaml");

// Export index to YAML
String indexYaml = bridge.exportDecisionIndexToYaml(index);
System.out.println(indexYaml);
Files.writeString(Path.of("decisions/index.yaml"), indexYaml);
```

#### 6. Load an existing decision index from YAML

```java
String savedIndex = Files.readString(Path.of("decisions/index.yaml"));
String parsedIndex = bridge.parseDecisionIndexYaml(savedIndex);
```

---

### Part B: Knowledge Base Articles

#### 7. Create a knowledge article

```java
String article = bridge.createKnowledgeArticle(
    1,                                             // sequential number
    "How to configure database connection pooling", // title
    "Step-by-step guide for PgBouncer setup",       // summary
    """
    # Database Connection Pooling

    ## Overview
    PgBouncer is a lightweight connection pooler for PostgreSQL.

    ## Installation
    ```bash
    apt install pgbouncer
    ```

    ## Configuration
    Edit `/etc/pgbouncer/pgbouncer.ini` with your database settings.
    """,
    "Infrastructure Team"                          // author
);
```

#### 8. Export an article to YAML and Markdown

```java
String articleYaml = bridge.exportKnowledgeToYaml(article);
String articleMd   = bridge.exportKnowledgeToMarkdown(article);

Files.writeString(Path.of("kb/0001-pgbouncer-setup.yaml"), articleYaml);
```

#### 9. Parse an existing article from YAML

```java
String savedYaml = Files.readString(Path.of("kb/0001-pgbouncer-setup.yaml"));
String parsed    = bridge.parseKnowledgeYaml(savedYaml);
```

#### 10. Build a knowledge index

```java
// Create articles
String article1 = bridge.createKnowledgeArticle(
    1, "PgBouncer Setup", "Connection pooling guide", "# Content...", "Team");
String article2 = bridge.createKnowledgeArticle(
    2, "Redis Caching", "Cache layer setup", "# Content...", "Team");

// Create and populate the index
String kbIndex = bridge.createKnowledgeIndex();
kbIndex = bridge.addArticleToKnowledgeIndex(kbIndex, article1, "0001-pgbouncer.yaml");
kbIndex = bridge.addArticleToKnowledgeIndex(kbIndex, article2, "0002-redis-caching.yaml");

// Export and save
String kbIndexYaml = bridge.exportKnowledgeIndexToYaml(kbIndex);
Files.writeString(Path.of("kb/index.yaml"), kbIndexYaml);
```

#### 11. Search knowledge articles

Search takes a JSON array of articles and a query string. Return value is a JSON array of matching articles:

```java
String article1 = bridge.createKnowledgeArticle(
    1, "Authentication Guide", "API auth setup", "# Auth with Bearer tokens", "Team");
String article2 = bridge.createKnowledgeArticle(
    2, "Deployment Runbook", "Production deploy", "# Deploy steps", "Team");

String articlesJson = "[" + article1 + "," + article2 + "]";

String matches = bridge.searchKnowledgeArticles(articlesJson, "authentication");
System.out.println("Search results: " + matches);
```

#### 12. Load an existing knowledge index from YAML

```java
String savedIndex = Files.readString(Path.of("kb/index.yaml"));
String kbIndex    = bridge.parseKnowledgeIndexYaml(savedIndex);
```

---

### Part C: Sketches (Architecture Diagrams)

Sketches store Excalidraw diagram data with metadata, indexed like decisions and KB articles.

#### 13. Create a sketch

```java
String excalidrawData = """
    {
      "type": "excalidraw",
      "version": 2,
      "elements": [
        {"type": "rectangle", "id": "box1", "x": 100, "y": 100,
         "width": 200, "height": 80, "label": {"text": "API Gateway"}},
        {"type": "arrow", "id": "arr1",
         "startBinding": {"elementId": "box1"},
         "endBinding":   {"elementId": "box2"}}
      ]
    }
    """;

String sketch = bridge.createSketch(
    1,                              // sequential number
    "System Architecture Overview", // title
    "architecture",                 // type
    excalidrawData                  // Excalidraw JSON
);
```

#### 14. Export and parse a sketch

```java
String sketchYaml = bridge.exportSketchToYaml(sketch);
Files.writeString(Path.of("sketches/0001-system-architecture.yaml"), sketchYaml);

// Re-load
String savedYaml = Files.readString(Path.of("sketches/0001-system-architecture.yaml"));
String parsed    = bridge.parseSketchYaml(savedYaml);
```

#### 15. Build a sketch index

```java
String sketch1 = bridge.createSketch(1, "System Architecture", "architecture", "{}");
String sketch2 = bridge.createSketch(2, "Data Flow Diagram", "dataflow", "{}");

String sketchIndex = bridge.createSketchIndex();
sketchIndex = bridge.addSketchToIndex(sketchIndex, sketch1, "0001-system-arch.yaml");
sketchIndex = bridge.addSketchToIndex(sketchIndex, sketch2, "0002-data-flow.yaml");

String indexYaml = bridge.exportSketchIndexToYaml(sketchIndex);
```

Wait — `exportSketchIndexToYaml` is not available. Use `parseSketchIndexYaml` to round-trip an existing YAML and `addSketchToIndex` to populate it. The index JSON can be held in memory or serialised using your own JSON library.

#### 16. Search sketches

```java
String sketches = "[" + sketch1 + "," + sketch2 + "]";
String results  = bridge.searchSketches(sketches, "architecture");
System.out.println("Found: " + results);
```

---

## Verification

Confirm round-trip fidelity by parsing what you export and checking the title is preserved:

```java
String decision = bridge.createDecision(1, "Use Redis", "Context", "Decision", "Team");
String yaml     = bridge.exportDecisionToYaml(decision);
String reparsed = bridge.parseDecisionYaml(yaml);

assert reparsed.contains("Use Redis") : "Decision title lost in round-trip";
```

---

## Troubleshooting

### `DataModellingException: WASM function 'create_decision' failed`

Check that `number` is greater than 0, and that `title`, `context`, `decision`, and `author` are all non-null non-empty strings.

### Index `addDecisionToIndex` silently returns unchanged index

The decision JSON may not be a valid decision object (e.g., it was produced by a different SDK version). Verify the decision was created by `createDecision()` or `parseDecisionYaml()` in the same bridge session.

### Search returns empty array when matches are expected

`searchKnowledgeArticles` performs full-text search on title and content. Ensure the `articlesJson` parameter is a valid JSON array (bracketed, comma-separated), not individual JSON strings concatenated.
