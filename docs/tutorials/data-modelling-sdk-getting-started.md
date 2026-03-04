# Data Modelling SDK Getting Started Tutorial

This tutorial guides you through the complete data-modelling-sdk v2.3.0 capabilities, from basic schema import to advanced LLM-powered schema refinement.

**What you'll learn:**
- Import schemas from 10+ formats (SQL, ODCS, OpenAPI, BPMN, DMN, Avro, etc.)
- Export schemas to multiple output formats
- Manage domains, workspaces, and relationships
- Create Architecture Decision Records (ADRs) and Knowledge Base articles
- Validate schemas and detect circular dependencies
- Use LLM integration for schema refinement

**Prerequisites:**
- YAWL v6.0.0 built and installed
- Java 25+ with `--enable-preview` and `--enable-native-access=ALL-UNNAMED`
- Basic familiarity with YAML/JSON

---

## 1. Your First Schema Import

### Step 1: Create the Bridge

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

// Create bridge with single context (sufficient for most use cases)
try (DataModellingBridge bridge = new DataModellingBridge()) {
    // All your operations here
}
```

The bridge is `AutoCloseable` — always use try-with-resources.

### Step 2: Import an ODCS Schema

```java
String odcsYaml = """
    apiVersion: v3.1.0
    kind: DataContract
    name: customers
    schema:
      fields:
        - name: customer_id
          type: bigint
          primary: true
        - name: email
          type: varchar(255)
          unique: true
        - name: created_at
          type: timestamp
          required: true
    """;

String workspaceJson = bridge.parseOdcsYaml(odcsYaml);
System.out.println("Imported workspace: " + workspaceJson);
```

### Step 3: Export Back to ODCS

```java
// Convert workspace back to ODCS YAML
String exportedYaml = bridge.exportOdcsYamlV2(workspaceJson);
System.out.println("Exported YAML:\n" + exportedYaml);
```

---

## 2. Import from SQL DDL

### PostgreSQL Example

```java
String postgresDdl = """
    CREATE TABLE orders (
        order_id SERIAL PRIMARY KEY,
        customer_id BIGINT NOT NULL REFERENCES customers(customer_id),
        order_date TIMESTAMP DEFAULT NOW(),
        total_amount DECIMAL(10,2)
    );

    CREATE INDEX idx_orders_customer ON orders(customer_id);
    """;

String workspace = bridge.importFromSql(postgresDdl, "postgres");
```

### Supported SQL Dialects

| Dialect | Identifier |
|---------|------------|
| PostgreSQL | `postgres` |
| MySQL | `mysql` |
| SQLite | `sqlite` |
| Databricks | `databricks` |
| Generic ANSI SQL | `generic` |

---

## 3. Import from OpenAPI

```java
String openapiYaml = """
    openapi: 3.1.0
    info:
      title: Pet Store API
      version: 1.0.0
    components:
      schemas:
        Pet:
          type: object
          required:
            - id
            - name
          properties:
            id:
              type: integer
              format: int64
            name:
              type: string
              maxLength: 100
            status:
              type: string
              enum: [available, pending, sold]
    """;

String domainId = "pet-store-domain";
String workspace = bridge.importOpenapiSpec(domainId, openapiYaml, "Pet Store API");
```

---

## 4. Domain and Workspace Management

### Create a Domain

```java
// Create a new business domain
String domain = bridge.createDomain("E-Commerce Platform");

// Parse domain to get ID
JsonObject domainObj = Json.parse(domain).asObject();
String domainId = domainObj.getString("domain_id", "");
```

### Create a Workspace

```java
String workspace = bridge.createWorkspace(
    "E-Commerce Data Platform",
    "data-team@company.com"
);

// Add domain to workspace
String updated = bridge.addDomainToWorkspace(
    workspace,
    domainId,
    "E-Commerce Platform"
);
```

### Manage Relationships

```java
// Define a relationship between tables
String relationshipJson = """
    {
      "source_table_id": "customers",
      "target_table_id": "orders",
      "relationship_type": "dataFlow",
      "cardinality": "one_to_many",
      "description": "Customers place orders"
    }
    """;

String withRelationship = bridge.addRelationshipToWorkspace(updated, relationshipJson);
```

---

## 5. Decision Records (MADR)

### Create an Architecture Decision Record

```java
String decision = bridge.createDecision(
    1,                              // sequential number
    "Use PostgreSQL for Transactional Data",
    "We need a reliable relational database for ACID transactions",
    "PostgreSQL selected for its robustness, JSON support, and open-source license",
    "architecture-team@company.com"
);

// Export to Markdown (MADR format)
String markdown = bridge.exportDecisionToMarkdown(decision);
Files.writeString(Path.of("docs/decisions/0001-use-postgresql.md"), markdown);
```

### Create Decision Index

```java
String index = bridge.createDecisionIndex();
String withDecision = bridge.addDecisionToIndex(index, decision, "0001-use-postgresql.md");

// Export index to YAML
String indexYaml = bridge.exportDecisionIndexToYaml(withDecision);
```

---

## 6. Knowledge Base Management

### Create a Knowledge Article

```java
String article = bridge.createKnowledgeArticle(
    1,
    "How to Deploy YAWL on Kubernetes",
    "Step-by-step guide for deploying YAWL engine on K8s",
    """
    ## Prerequisites
    - Kubernetes cluster 1.28+
    - kubectl configured
    - Helm 3.x

    ## Steps
    1. Add the YAWL Helm repository
    2. Create namespace `yawl`
    3. Install with `helm install yawl yawl/yawl-engine`
    """,
    "platform-team@company.com"
);

// Search articles
String allArticles = "[" + article + "]";
String results = bridge.searchKnowledgeArticles(allArticles, "kubernetes deploy");
```

---

## 7. Sketch Management (Excalidraw)

```java
String sketch = bridge.createSketch(
    1,
    "System Architecture Diagram",
    "architecture",
    """
    {
      "type": "excalidraw",
      "version": 2,
      "elements": [
        {"type": "rectangle", "x": 100, "y": 100, "text": "YAWL Engine"},
        {"type": "rectangle", "x": 300, "y": 100, "text": "PostgreSQL"},
        {"type": "arrow", "points": [[180, 120], [280, 120]]}
      ]
    }
    """
);

// Export to YAML
String sketchYaml = bridge.exportSketchToYaml(sketch);
```

---

## 8. Validation

### Validate ODPS Schema

```java
String odpsYaml = """
    version: 1.0.0
    product:
      id: customer-orders
      name: Customer Orders Data Product
      owner: data-team@company.com
    """;

try {
    bridge.validateOdps(odpsYaml);
    System.out.println("ODPS schema is valid!");
} catch (DataModellingException e) {
    System.out.println("Validation failed: " + e.getMessage());
}
```

### Check for Circular Dependencies

```java
String relationships = """
    [
      {"source": "A", "target": "B"},
      {"source": "B", "target": "C"},
      {"source": "C", "target": "A"}
    ]
    """;

String hasCycle = bridge.checkCircularDependency(relationships, "A", "C");
if ("true".equals(hasCycle)) {
    System.out.println("Warning: Circular dependency detected!");
}
```

---

## 9. Advanced Querying

### Use the Query Builder

```java
// Create a query builder from workspace
var builder = bridge.queryBuilder(workspaceJson);

// Filter tables by owner
List<DataModellingTable> ownerTables = builder
    .filterTablesByOwner("data-team@company.com")
    .getTables();

// Filter by tag
List<DataModellingTable> piiTables = builder
    .filterTablesByTag("pii")
    .getTables();

// Filter by medallion layer
List<DataModellingTable> goldTables = builder
    .filterTablesByMedallionLayer("gold")
    .getTables();

// Get impact analysis
List<DataModellingTable> impacted = builder.getImpactAnalysis("customers-table-id");

// Get data lineage
Map<String, Object> lineage = builder.getDataLineageReport("orders-table-id");
```

---

## 10. LLM Integration

### Configure LLM

```java
import org.yawlfoundation.yawl.datamodelling.llm.LlmConfig;

LlmConfig config = new LlmConfig();
config.setMode(LlmConfig.Mode.ONLINE);
config.setBaseUrl("http://localhost:11434");  // Ollama
config.setModel("llama3.2");
config.setTemperature(0.7);
config.setMaxTokens(2048);
config.setTimeoutSeconds(60);
```

### Check LLM Availability

```java
String available = bridge.checkLlmAvailability(config);
if ("true".equals(available)) {
    System.out.println("LLM service is ready");
}
```

### Refine Schema with LLM

```java
String[] samples = {
    "{\"customer_id\": 1, \"email\": \"user@example.com\"}",
    "{\"customer_id\": 2, \"email\": \"another@example.com\"}"
};

String[] objectives = {
    "Add appropriate field descriptions",
    "Detect PII fields",
    "Suggest indexes for common queries"
};

String refined = bridge.refineSchemaWithLlmOnline(
    odcsYaml,
    samples,
    objectives,
    "This schema stores customer PII data",
    config
);
```

### Match Fields Between Schemas

```java
String sourceSchema = bridge.parseOdcsYaml(sourceYaml);
String targetSchema = bridge.parseOdcsYaml(targetYaml);

String mappings = bridge.matchFieldsWithLlm(sourceSchema, targetSchema, config);
// Returns: {"fieldMappings": [{"source": "customer_id", "target": "client_id", "confidence": 0.95}]}
```

---

## 11. Format Conversion

### Convert Any Format to ODCS

```java
// Auto-detect format
String odcsWorkspace = bridge.convertToOdcs(avroSchema, null);

// Explicit format hint
String odcsFromJsonSchema = bridge.convertToOdcs(jsonSchemaContent, "json_schema");
String odcsFromSql = bridge.convertToOdcs(sqlDdl, "sql");
```

### Convert OpenAPI Component to ODCS

```java
String tableJson = bridge.convertOpenapiToOdcs(
    openapiYaml,
    "Pet",           // component name
    "pets"           // target table name (optional)
);
```

---

## 12. BPMN and DMN Integration

### Import BPMN Process

```java
String bpmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
      <bpmn:process id="OrderProcess" name="Order Processing">
        <bpmn:startEvent id="start"/>
        <bpmn:userTask id="approve" name="Approve Order"/>
        <bpmn:endEvent id="end"/>
      </bpmn:process>
    </bpmn:definitions>
    """;

String domain = bridge.importBpmnModel(domainId, bpmnXml, "Order Process");
```

### Import DMN Decision

```java
String dmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <dmn:definitions xmlns:dmn="https://www.omg.org/spec/DMN/20191111/MODEL/">
      <dmn:decision id="ApprovalDecision" name="Order Approval">
        <dmn:decisionTable>
          <dmn:input label="Amount"/>
          <dmn:output label="Approved"/>
        </dmn:decisionTable>
      </dmn:decision>
    </dmn:definitions>
    """;

String withDecision = bridge.importDmnModel(domainId, dmnXml, "Approval Rules");
```

---

## Summary

You've learned:
1. ✅ Import schemas from 10+ formats
2. ✅ Export to multiple output formats
3. ✅ Manage domains and workspaces
4. ✅ Create ADRs and knowledge articles
5. ✅ Validate schemas and detect issues
6. ✅ Use LLM for schema refinement
7. ✅ Integrate BPMN/DMN process models

**Next Steps:**
- [How-To: Import Schema Formats](../how-to/import-schema-formats.md)
- [How-To: Validate Data Schemas](../how-to/validate-data-schemas.md)
- [Reference: DataModellingBridge API](../reference/data-modelling-api.md)
- [Explanation: Data Modelling SDK Architecture](../explanation/data-modelling-sdk-facade.md)
