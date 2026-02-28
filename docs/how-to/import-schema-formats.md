# How to Import Schema Formats with DataModellingBridge

## Problem

You have schema definitions in one of many standard formats — SQL DDL, Avro, JSON Schema, Protobuf, CADS, ODPS, BPMN, DMN, or OpenAPI — and you need to bring them into the YAWL data modelling workspace JSON format so that you can analyse, convert, or export them.

## Prerequisites

- `yawl-data-modelling` on your classpath (version 6.0.0-GA)
- GraalVM JDK 24.1+ at runtime
- Source content in at least one of the supported input formats

## Steps

### 1. Open the bridge

All import operations go through `DataModellingBridge`. Open it once and reuse it:

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

try (DataModellingBridge bridge = new DataModellingBridge()) {
    // All import operations inside this block
}
```

---

### 2. Import ODCS YAML

Open Data Contract Standard (ODCS) v3.1.0 is the primary format. Use `parseOdcsYaml()`:

```java
String odcsYaml = """
    apiVersion: v3.1.0
    kind: DataContract
    name: customers
    owner: analytics-team
    schema:
      fields:
        - name: id
          type: bigint
          required: true
        - name: email
          type: string
        - name: created_at
          type: timestamp
    """;

String workspace = bridge.parseOdcsYaml(odcsYaml);
```

For older ODCS v2.x documents with extended field preservation, use `parseOdcsYamlV2()`:

```java
String workspaceV2 = bridge.parseOdcsYamlV2(legacyOdcsYaml);
```

---

### 3. Import SQL DDL

Import CREATE TABLE statements from any of five supported SQL dialects:

```java
String sql = """
    CREATE TABLE orders (
        id         BIGINT        NOT NULL,
        customer_id BIGINT       NOT NULL,
        total      DECIMAL(10,2),
        status     VARCHAR(50),
        created_at TIMESTAMP,
        PRIMARY KEY (id)
    );
    """;

// PostgreSQL
String wsPostgres = bridge.importFromSql(sql, "postgres");

// MySQL
String wsMySQL = bridge.importFromSql(sql, "mysql");

// SQLite
String wsSqlite = bridge.importFromSql(
    "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT NOT NULL);",
    "sqlite");

// Databricks
String wsDatabricks = bridge.importFromSql(sql, "databricks");

// Generic (dialect-agnostic)
String wsGeneric = bridge.importFromSql(sql, "generic");
```

Valid dialect values: `"postgres"`, `"mysql"`, `"sqlite"`, `"databricks"`, `"generic"`.

---

### 4. Import Avro schema

```java
String avroSchema = """
    {
      "type": "record",
      "name": "Payment",
      "namespace": "com.example",
      "fields": [
        {"name": "paymentId", "type": "string"},
        {"name": "amount",    "type": "double"},
        {"name": "currency",  "type": "string"},
        {"name": "timestamp", "type": "long"}
      ]
    }
    """;

String workspace = bridge.importFromAvro(avroSchema);
```

---

### 5. Import JSON Schema

```java
String jsonSchema = """
    {
      "$schema": "https://json-schema.org/draft/2020-12/schema",
      "title": "Invoice",
      "type": "object",
      "properties": {
        "invoiceId": { "type": "string" },
        "amount":    { "type": "number" },
        "dueDate":   { "type": "string", "format": "date" }
      },
      "required": ["invoiceId", "amount"]
    }
    """;

String workspace = bridge.importFromJsonSchema(jsonSchema);
```

---

### 6. Import Protobuf

```java
String proto = """
    syntax = "proto3";
    package example;

    message Product {
      string product_id = 1;
      string name       = 2;
      double price      = 3;
      int32  stock      = 4;
    }
    """;

String workspace = bridge.importFromProtobuf(proto);
```

---

### 7. Import CADS (Compute Asset Description Specification)

```java
String cadsYaml = """
    kind: ComputeAsset
    name: user-analytics-pipeline
    version: "1.0"
    inputs:
      - name: events
        type: stream
        schema: com.example.UserEvent
    outputs:
      - name: metrics
        type: batch
        schema: com.example.UserMetrics
    """;

String workspace = bridge.importFromCads(cadsYaml);
```

---

### 8. Import ODPS (Open Data Product Standard)

```java
String odpsYaml = """
    product:
      name: customer-360
      version: "2.1"
      domain: marketing
      outputs:
        - dataContract: customers-v3
          format: parquet
          refreshCadence: daily
    """;

String workspace = bridge.importFromOdps(odpsYaml);
```

---

### 9. Import a BPMN process model into a domain

BPMN models are attached to a named domain. Provide the domain ID, BPMN XML content, and an optional model name:

```java
String bpmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                 id="def1" targetNamespace="http://example.com">
      <process id="orderProcess" isExecutable="true">
        <startEvent id="start"/>
        <userTask id="reviewTask" name="Review Order"/>
        <endEvent id="end"/>
        <sequenceFlow id="sf1" sourceRef="start" targetRef="reviewTask"/>
        <sequenceFlow id="sf2" sourceRef="reviewTask" targetRef="end"/>
      </process>
    </definitions>
    """;

String domainId = "domain-001";
String domainJson = bridge.importBpmnModel(domainId, bpmnXml, "OrderProcess");
```

---

### 10. Import a DMN decision model into a domain

```java
String dmnXml = """
    <?xml version="1.0" encoding="UTF-8"?>
    <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                 id="def1" name="LoanDecisions"
                 namespace="http://example.com">
      <decision id="EligibilityCheck" name="Eligibility Check">
        <decisionTable id="dt1" hitPolicy="UNIQUE">
          <input id="in1" label="age">
            <inputExpression typeRef="integer"><text>age</text></inputExpression>
          </input>
          <output id="out1" label="eligible" name="eligible" typeRef="boolean"/>
          <rule id="r1">
            <inputEntry><text>&gt;= 18</text></inputEntry>
            <outputEntry><text>true</text></outputEntry>
          </rule>
        </decisionTable>
      </decision>
    </definitions>
    """;

String domainJson = bridge.importDmnModel("domain-001", dmnXml, "LoanDecisions");
```

---

### 11. Import an OpenAPI specification into a domain

```java
String openApiSpec = """
    openapi: "3.1.1"
    info:
      title: Payment API
      version: "1.0"
    components:
      schemas:
        Payment:
          type: object
          properties:
            paymentId:
              type: string
            amount:
              type: number
    """;

String domainJson = bridge.importOpenapiSpec("domain-001", openApiSpec, "PaymentAPI");
```

---

### 12. Use the universal converter

When you have content but are unsure which specific import method to use, `convertToOdcs()` auto-detects the format:

```java
// Auto-detect format
String workspace = bridge.convertToOdcs(anyContent, null);

// Hint the format explicitly
String workspace2 = bridge.convertToOdcs(sqlContent, "sql");
String workspace3 = bridge.convertToOdcs(avroContent, "avro");
```

---

## Verification

Every import method returns a workspace JSON string. Verify import success:

```java
String ws = bridge.importFromSql(sql, "postgres");
assert ws != null && !ws.isBlank() : "Import returned empty";
assert ws.contains("orders") : "Expected table name in workspace";
```

Check for the target format using your JSON library:

```java
import com.fasterxml.jackson.databind.ObjectMapper;
ObjectMapper mapper = new ObjectMapper();
var root = mapper.readTree(ws);
System.out.println("Tables: " + root.path("tables").size());
```

---

## Troubleshooting

### `DataModellingException: WASM function 'import_from_sql' failed`

The SQL syntax is not recognised by the dialect parser. Check:
- The dialect string is exactly one of: `postgres`, `mysql`, `sqlite`, `databricks`, `generic`
- The DDL uses standard `CREATE TABLE` syntax
- Multi-statement SQL (CREATE TABLE + INSERT) is separated correctly

### Bridge constructor throws on non-GraalVM JDK

`DataModellingBridge` requires GraalVM JDK 24.1+ with WebAssembly support. On Temurin or OpenJDK, construction will throw. Switch the runtime JDK:

```bash
export JAVA_HOME=/path/to/graalvm-jdk-24
java -version  # should say GraalVM
```

### Import returns empty JSON `{}`

The input content is structurally valid but has no schema elements the SDK can extract. For ODCS YAML, ensure `schema.fields` is present and non-empty. For SQL, ensure the CREATE TABLE has at least one column definition.
