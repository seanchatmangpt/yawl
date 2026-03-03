# Data Modelling SDK How-To Guide

Practical guides for accomplishing specific tasks with data-modelling-sdk v2.3.0.

---

## Schema Import

### How to Import PostgreSQL DDL

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    String ddl = Files.readString(Path.of("schema.sql"));
    String workspace = bridge.importFromSql(ddl, "postgres");

    // Verify import succeeded
    JsonObject ws = Json.parse(workspace).asObject();
    assert ws.get("tables") != null : "No tables imported";
}
```

**Supported dialects:** `postgres`, `mysql`, `sqlite`, `databricks`, `generic`

### How to Import Avro Schema

```java
String avroSchema = """
    {
      "type": "record",
      "name": "User",
      "fields": [
        {"name": "id", "type": "long"},
        {"name": "name", "type": "string"},
        {"name": "email", "type": ["null", "string"], "default": null}
      ]
    }
    """;

String workspace = bridge.importFromAvro(avroSchema);
```

### How to Import Protobuf Schema

```java
String proto = """
    syntax = "proto3";
    message Order {
      int64 order_id = 1;
      string customer_email = 2;
      repeated Item items = 3;
    }
    message Item {
      string sku = 1;
      int32 quantity = 2;
    }
    """;

String workspace = bridge.importFromProtobuf(proto);
```

### How to Import JSON Schema

```java
String jsonSchema = """
    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "type": "object",
      "properties": {
        "orderId": {"type": "integer"},
        "items": {"type": "array", "items": {"$ref": "#/$defs/Item"}}
      },
      "$defs": {
        "Item": {"type": "object", "properties": {"sku": {"type": "string"}}}
      }
    }
    """;

String workspace = bridge.importFromJsonSchema(jsonSchema);
```

### How to Import CADS (Compute Asset Description)

```java
String cadsYaml = """
    version: 1.0.0
    asset:
      id: spark-cluster-prod
      name: Production Spark Cluster
      infrastructure:
        type: databricks
        region: us-west-2
      compute:
        node_type: i3.xlarge
        num_workers: 10
    """;

String workspace = bridge.importFromCads(cadsYaml);
```

### How to Import ODPS (Open Data Product Standard)

```java
String odpsYaml = """
    version: 1.0.0
    product:
      id: customer-360
      name: Customer 360 Data Product
      owner: data-products@company.com
      description: Complete customer view for analytics
    inputPorts:
      - name: crm-data
        source: crm-system
    outputPorts:
      - name: customer-api
        type: rest
    """;

String workspace = bridge.importFromOdps(odpsYaml);
```

---

## Schema Export

### How to Export to ODCS YAML

```java
String odcsYaml = bridge.exportOdcsYamlV2(workspaceJson);

// Write to file
Files.writeString(Path.of("schemas/customers.odcs.yaml"), odcsYaml);
```

### How to Export to SQL DDL

```java
// Export workspace to PostgreSQL DDL
String ddl = bridge.exportToSql(workspaceJson, "postgres");

// Export to MySQL
String mysqlDdl = bridge.exportToSql(workspaceJson, "mysql");
```

### How to Export to Markdown Documentation

```java
// Export ODCS to Markdown
String markdown = bridge.exportOdcsYamlToMarkdown(odcsYaml);

// Export ODPS to Markdown
String productMarkdown = bridge.exportOdpsToMarkdown(odpsJson);
```

---

## Format Conversion

### How to Convert Any Format to ODCS

```java
// Auto-detect format
String odcs = bridge.convertToOdcs(inputContent, null);

// With explicit format hint
String odcs = bridge.convertToOdcs(content, "avro");
String odcs = bridge.convertToOdcs(content, "json_schema");
String odcs = bridge.convertToOdcs(content, "protobuf");
String odcs = bridge.convertToOdcs(content, "sql");
```

### How to Convert OpenAPI to ODCS

```java
String openapi = Files.readString(Path.of("api-spec.yaml"));

// Convert specific component
String tableJson = bridge.convertOpenapiToOdcs(openapi, "Pet", "pets");

// Analyze conversion feasibility
String analysis = bridge.analyzeOpenapiConversion(openapi, "Pet");
// Returns: {"feasible": true, "fieldMappings": [...], "issues": []}
```

### How to Convert BPMN to DMN

```java
String bpmnXml = Files.readString(Path.of("process.bpmn"));
String dmnResult = bridge.convertBpmnToDmn(bpmnXml);
```

---

## Domain Management

### How to Create a Domain

```java
String domain = bridge.createDomain("E-Commerce Platform");
JsonObject domainObj = Json.parse(domain).asObject();
String domainId = domainObj.getString("domain_id", "");
```

### How to Add a Domain to Workspace

```java
String updatedWorkspace = bridge.addDomainToWorkspace(
    workspaceJson,
    domainId,
    "E-Commerce Platform"
);
```

### How to Add Systems to a Domain

```java
String systemJson = """
    {
      "system_id": "crm-system",
      "name": "Salesforce CRM",
      "type": "SaaS",
      "owner": "sales-ops@company.com"
    }
    """;

String withSystem = bridge.addSystemToDomain(workspaceJson, domainId, systemJson);
```

### How to Add ODCS Nodes to a Domain

```java
String nodeJson = bridge.parseOdcsYaml(odcsYaml);
String withNode = bridge.addOdcsNodeToDomain(workspaceJson, domainId, nodeJson);
```

---

## Relationship Management

### How to Add a Relationship

```java
String relationship = """
    {
      "source_table_id": "customers",
      "target_table_id": "orders",
      "relationship_type": "dataFlow",
      "cardinality": "one_to_many",
      "description": "Customers place multiple orders"
    }
    """;

String withRelationship = bridge.addRelationshipToWorkspace(workspaceJson, relationship);
```

### How to Remove a Relationship

```java
String updated = bridge.removeRelationshipFromWorkspace(
    workspaceJson,
    "relationship-123"
);
```

### How to Query Table Relationships

```java
String relationships = bridge.queryTableRelationships(
    workspaceJson,
    "customers-table-id",
    "dataFlow"  // optional filter by type
);
```

---

## Validation

### How to Validate Table Names

```java
String result = bridge.validateTableName("customer_orders");
// Returns: {"valid": true} or {"valid": false, "error": "..."}

String invalid = bridge.validateTableName("123-invalid");
// Returns: {"valid": false, "error": "Table name cannot start with digit"}
```

### How to Validate Column Names

```java
String result = bridge.validateColumnName("customer_email");
```

### How to Validate Data Types

```java
String result = bridge.validateDataType("varchar(255)");
// Returns: {"valid": true, "normalizedType": "VARCHAR(255)"}
```

### How to Detect Circular Dependencies

```java
String hasCycle = bridge.hasCyclicDependencies(workspaceJson);
if ("true".equals(hasCycle)) {
    String cyclePath = bridge.detectCyclePath(workspaceJson);
    System.err.println("Cycle detected: " + cyclePath);
}
```

### How to Detect Naming Conflicts

```java
String conflicts = bridge.detectNamingConflicts(
    existingTablesJson,
    newTablesJson
);
// Returns: [{"table": "users", "conflict": "Duplicate name"}]
```

---

## Decision Records (ADRs)

### How to Create an ADR

```java
String decision = bridge.createDecision(
    42,  // ADR number
    "Use Event Sourcing for Order Service",
    "Order service needs complete audit trail and temporal queries",
    "Event sourcing with Kafka provides immutable event log and replay capability",
    "architecture@company.com"
);
```

### How to Export ADR to Markdown

```java
String markdown = bridge.exportDecisionToMarkdown(decision);
Files.writeString(Path.of("docs/decisions/0042-event-sourcing.md"), markdown);
```

### How to Create an ADR Index

```java
// Create empty index
String index = bridge.createDecisionIndex();

// Add decisions
index = bridge.addDecisionToIndex(index, decision1, "0042-event-sourcing.md");
index = bridge.addDecisionToIndex(index, decision2, "0043-cqrs-pattern.md");

// Export index
String indexYaml = bridge.exportDecisionIndexToYaml(index);
Files.writeString(Path.of("docs/decisions/index.yaml"), indexYaml);
```

---

## Knowledge Base

### How to Create a Knowledge Article

```java
String article = bridge.createKnowledgeArticle(
    15,
    "Troubleshooting Database Connection Pool Exhaustion",
    "How to diagnose and fix connection pool exhaustion in YAWL",
    """
    ## Symptoms
    - HTTP 503 errors under load
    - "Connection pool exhausted" in logs

    ## Diagnosis
    1. Check active connections: `SHOW STATUS LIKE 'Threads_connected'`
    2. Review pool metrics in Actuator: `/actuator/metrics/hikaricp.connections.active`

    ## Resolution
    1. Increase pool size in application.yml
    2. Add connection timeout handling
    """,
    "platform@company.com"
);
```

### How to Search Knowledge Articles

```java
String allArticles = "[" + article1 + "," + article2 + "]";
String results = bridge.searchKnowledgeArticles(allArticles, "connection pool");
```

---

## Sketch Management

### How to Create a Sketch

```java
String sketch = bridge.createSketch(
    1,
    "Data Platform Architecture",
    "architecture",
    excalidrawJson  // Excalidraw format JSON
);
```

### How to Export Sketch to YAML

```java
String yaml = bridge.exportSketchToYaml(sketch);
Files.writeString(Path.of("sketches/001-architecture.yaml"), yaml);
```

---

## LLM Integration

### How to Configure LLM for Offline Use (Ollama)

```java
LlmConfig config = new LlmConfig();
config.setMode(LlmConfig.Mode.ONLINE);
config.setBaseUrl("http://localhost:11434");
config.setModel("llama3.2");
config.setTemperature(0.7);
config.setMaxTokens(2048);
config.setTimeoutSeconds(120);

// Check availability
if ("true".equals(bridge.checkLlmAvailability(config))) {
    // LLM is ready
}
```

### How to Refine Schema with LLM

```java
String[] samples = {
    "{\"id\": 1, \"name\": \"Alice\", \"email\": \"alice@example.com\"}",
    "{\"id\": 2, \"name\": \"Bob\", \"email\": \"bob@example.com\"}"
};

String[] objectives = {
    "Add meaningful field descriptions",
    "Identify PII fields",
    "Suggest appropriate indexes"
};

String refined = bridge.refineSchemaWithLlmOnline(
    schemaJson,
    samples,
    objectives,
    "Customer data for e-commerce platform",
    config
);
```

### How to Enrich Documentation with LLM

```java
String enriched = bridge.enrichDocumentationWithLlm(schemaJson, config);
// Returns schema with AI-generated descriptions for tables and columns
```

### How to Detect Patterns with LLM

```java
String patterns = bridge.detectPatternsWithLlm(schemaJson, config);
// Returns: {"patterns": [{"field": "email", "type": "PII", "confidence": 0.95}]}
```

---

## Advanced Querying

### How to Filter Tables by Owner

```java
String filtered = bridge.filterTablesByOwner(workspaceJson, "data-team@company.com");
```

### How to Filter Tables by Tag

```java
String piiTables = bridge.filterTablesByTag(workspaceJson, "pii");
String goldTables = bridge.filterTablesByTag(workspaceJson, "gold-layer");
```

### How to Filter by Medallion Layer

```java
String bronze = bridge.filterTablesByMedallionLayer(workspaceJson, "bronze");
String silver = bridge.filterTablesByMedallionLayer(workspaceJson, "silver");
String gold = bridge.filterTablesByMedallionLayer(workspaceJson, "gold");
```

### How to Get Impact Analysis

```java
String impacted = bridge.getImpactAnalysis(workspaceJson, "customers-table-id");
// Returns list of tables that would be affected by changes to customers table
```

### How to Get Data Lineage Report

```java
String lineage = bridge.getDataLineageReport(workspaceJson, "orders-table-id");
// Returns: {"upstream": [...], "downstream": [...], "table": {...}}
```

---

## Error Handling

### How to Handle DataModellingException

```java
try {
    String workspace = bridge.parseOdcsYaml(invalidYaml);
} catch (DataModellingException e) {
    switch (e.getErrorKind()) {
        case MODULE_LOAD_ERROR ->
            System.err.println("WASM module failed to load: " + e.getMessage());
        case EXECUTION_ERROR ->
            System.err.println("Schema parsing failed: " + e.getMessage());
        default ->
            System.err.println("Unknown error: " + e.getMessage());
    }
}
```

### How to Check Bridge Availability

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    // Bridge is ready
} catch (DataModellingException e) {
    if (e.getErrorKind() == DataModellingException.ErrorKind.MODULE_LOAD_ERROR) {
        // WASM resources not found - ensure yawl-data-modelling JAR is on classpath
    }
}
```

---

## Performance Tips

### How to Use Context Pooling

```java
// For high-throughput scenarios, increase pool size
try (DataModellingBridge bridge = new DataModellingBridge(4)) {
    // 4 concurrent JS/WASM contexts available
    // Suitable for 100+ operations per second
}
```

### How to Batch Operations

```java
// Bad: Create new bridge for each operation
for (String schema : schemas) {
    try (DataModellingBridge bridge = new DataModellingBridge()) {
        bridge.parseOdcsYaml(schema);  // Expensive!
    }
}

// Good: Reuse bridge
try (DataModellingBridge bridge = new DataModellingBridge()) {
    for (String schema : schemas) {
        bridge.parseOdcsYaml(schema);  // Reuses context
    }
}
```

---

## Next Steps

- [Reference: DataModellingBridge API](../reference/data-modelling-api.md)
- [Explanation: Data Modelling SDK Architecture](../explanation/data-modelling-sdk-facade.md)
- [Tutorial: Getting Started](../tutorials/data-modelling-sdk-getting-started.md)
