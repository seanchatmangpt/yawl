# DataModelling API Reference

Complete reference for `org.yawlfoundation.yawl.datamodelling` module (YAWL 6.0.0-GA).

## DataModellingBridge

Primary API facade over the data-modelling-sdk WASM module.

### Constructor

```java
// Default configuration
try (DataModellingBridge bridge = new DataModellingBridge()) { }

// With custom JavaScript engine
try (DataModellingBridge bridge = new DataModellingBridge(jsEngine)) { }
```

**AutoCloseable:**
- Mandatory - cleans up JavaScript contexts
- Use try-with-resources pattern
- Prevents context leaks

---

## YAML Operations

### parseOdcsYaml

Parse ODCS YAML format into workspace JSON.

| Method | Returns | Throws |
|--------|---------|--------|
| `parseOdcsYaml(String yamlContent)` | `String` (workspace JSON) | `DataModellingException` |

**Supported Versions:**
- ODCS v2.x
- ODCS v3.1.0+

**Example:**
```java
String yaml = """
    apiVersion: v3.1.0
    kind: DataContract
    name: Customer
    schema:
      fields:
        - name: id
          type: uuid
        - name: email
          type: email
""";

String workspace = bridge.parseOdcsYaml(yaml);
```

### exportOdcsYamlV2 / exportOdcsYamlV3

Export workspace JSON to ODCS YAML format.

| Method | Returns | Throws |
|--------|---------|--------|
| `exportOdcsYamlV2(String workspaceJson)` | `String` | `DataModellingException` |
| `exportOdcsYamlV3(String workspaceJson)` | `String` | `DataModellingException` |

**Example:**
```java
String yaml = bridge.exportOdcsYamlV3(workspaceJson);
Files.writeString(Paths.get("output.yaml"), yaml);
```

---

## SQL Operations

### importFromSql

Import SQL DDL into workspace JSON.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromSql(String sqlDDL, String dialect)` | `String` (workspace JSON) | `DataModellingException` |

**Supported Dialects:**
- `"postgres"` - PostgreSQL
- `"mysql"` - MySQL
- `"sqlite"` - SQLite
- `"databricks"` - Databricks SQL

**Example:**
```java
String sql = """
    CREATE TABLE customers (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100) NOT NULL,
        email VARCHAR(255) UNIQUE
    );
    """;

String workspace = bridge.importFromSql(sql, "postgres");
```

### exportToSqlV2

Export workspace JSON to SQL DDL with dialect support.

| Method | Returns | Throws |
|--------|---------|--------|
| `exportToSqlV2(String workspaceJson, String dialect)` | `String` (SQL DDL) | `DataModellingException` |

**Example:**
```java
String sql = bridge.exportToSqlV2(workspaceJson, "mysql");
System.out.println(sql);
```

---

## Schema Format Operations

### importFromJsonSchema / exportToJsonSchema

JSON Schema import and export.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromJsonSchema(String jsonSchema)` | `String` | `DataModellingException` |
| `exportToJsonSchema(String workspaceJson)` | `String` | `DataModellingException` |

**Example:**
```java
String jsonSchema = """
    {
        "$schema": "http://json-schema.org/draft-07/schema#",
        "type": "object",
        "properties": {
            "name": {"type": "string"}
        }
    }
    """;

String workspace = bridge.importFromJsonSchema(jsonSchema);
String schemaJson = bridge.exportToJsonSchema(workspace);
```

### importFromProtobuf / exportToProtobuf

Protocol Buffers support.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromProtobuf(String protoSchema)` | `String` | `DataModellingException` |
| `exportToProtobuf(String workspaceJson)` | `String` | `DataModellingException` |

**Example:**
```java
String proto = """
    syntax = "proto3";
    message Customer {
        string id = 1;
        string email = 2;
    }
    """;

String workspace = bridge.importFromProtobuf(proto);
String protobuf = bridge.exportToProtobuf(workspace);
```

---

## BPMN and DMN Operations

### importFromBpmn / exportToBpmn

BPMN 2.0 process diagram operations.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromBpmn(String bpmnXml)` | `String` | `DataModellingException` |
| `exportToBpmn(String workspaceJson)` | `String` (BPMN XML) | `DataModellingException` |

**Example:**
```java
String bpmnXml = Files.readString(Paths.get("process.bpmn"));
String workspace = bridge.importFromBpmn(bpmnXml);

String convertedBpmn = bridge.exportToBpmn(workspace);
```

### importFromDmn / exportToDmn

DMN 1.3 decision model operations.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromDmn(String dmnXml)` | `String` | `DataModellingException` |
| `exportToDmn(String workspaceJson)` | `String` (DMN XML) | `DataModellingException` |

**Example:**
```java
String dmnXml = """
    <definitions xmlns="http://www.omg.org/spec/DMN/20191111/MODEL/">
        <decision name="Eligibility" id="decision1">
            <!-- Decision content -->
        </decision>
    </definitions>
    """;

String workspace = bridge.importFromDmn(dmnXml);
String exportedDmn = bridge.exportToDmn(workspace);
```

---

## OpenAPI Operations

### importFromOpenAPIV2 / exportToOpenAPIV2

OpenAPI Specification v2/v3 support.

| Method | Returns | Throws |
|--------|---------|--------|
| `importFromOpenAPIV2(String openapiJson)` | `String` | `DataModellingException` |
| `exportToOpenAPIV2(String workspaceJson)` | `String` (OpenAPI JSON) | `DataModellingException` |

**Example:**
```java
String openapi = """
    {
        "openapi": "3.0.0",
        "info": {"title": "API"},
        "paths": {
            "/users": {
                "get": {
                    "responses": {
                        "200": {
                            "description": "OK"
                        }
                    }
                }
            }
        }
    }
    """;

String workspace = bridge.importFromOpenAPIV2(openapi);
String openapiSpec = bridge.exportToOpenAPIV2(workspace);
```

---

## Workspace Operations

### createWorkspace

Create a new workspace.

| Method | Returns | Throws |
|--------|---------|--------|
| `createWorkspace(String name, String description)` | `String` (workspace JSON) | `DataModellingException` |

**Example:**
```java
String workspace = bridge.createWorkspace(
    "ECommerce Platform",
    "Domain organization for online shopping"
);
```

### createDomain / createRelationship

Domain organization and relationships.

| Method | Returns | Throws |
|--------|---------|--------|
| `createDomain(String workspaceJson, String name, String description)` | `String` | `DataModellingException` |
| `createRelationship(String workspaceJson, String from, String to, String cardinalityFrom, String cardinalityTo)` | `String` | `DataModellingException` |

**Example:**
```java
String enrichedWorkspace = bridge.createRelationship(
    workspaceJson,
    "Customer", "Order", "1..*", "0..*"
);
```

### validateWorkspace

Validate workspace for integrity violations.

| Method | Returns | Throws |
|--------|---------|--------|
| `validateWorkspace(String workspaceJson)` | `List<String>` (violations) | `DataModellingException` |

**Example:**
```java
List<String> violations = bridge.validateWorkspace(workspaceJson);
if (violations.isEmpty()) {
    System.out.println("Workspace is valid!");
} else {
    violations.forEach(System.err::println);
}
```

---

## Decision Records (MADR)

### createDecisionRecord

Create MADR-compliant decision records.

| Method | Returns | Throws |
|--------|---------|--------|
| `createDecisionRecord(String title, String description, String category)` | `String` (decision JSON) | `DataModellingException` |

**Example:**
```java
String decision = bridge.createDecisionRecord(
    "Database Choice",
    "Selected PostgreSQL over MySQL for JSON support",
    "infra"
);
```

### parseDecisionRecord

Parse JSON decision records.

| Method | Returns | Throws |
|--------|---------|--------|
| `parseDecisionRecord(String decisionJson)` | `Map<String, Object>` | `DataModellingException` |

### exportDecisionToMarkdown / exportDecisionToYaml

Export decision records to different formats.

| Method | Returns | Throws |
|--------|---------|--------|
| `exportDecisionToMarkdown(String decisionJson)` | `String` (Markdown) | `DataModellingException` |
| `exportDecisionToYaml(String decisionJson)` | `String` (YAML) | `DataModellingException` |

**Example:**
```java
String markdown = bridge.exportDecisionToMarkdown(decision);
Files.writeString(Paths.get("decision.md"), markdown);
```

---

## Knowledge Base Operations

### createKnowledgeArticle

Create knowledge base articles.

| Method | Returns | Throws |
|--------|---------|--------|
| `createKnowledgeArticle(String title, String content, String category)` | `String` (article JSON) | `DataModellingException` |

**Example:**
```java
String article = bridge.createKnowledgeArticle(
    "Database Performance Tuning",
    "Add indexes for frequently queried columns",
    "tech"
);
```

### searchKnowledgeBase

Semantic search across knowledge base.

| Method | Returns | Throws |
|--------|---------|--------|
| `searchKnowledgeBase(String query)` | `List<String>` (matching article JSONs) | `DataModellingException` |

**Example:**
```java
List<String> articles = bridge.searchKnowledgeBase("database performance");
```

### exportKnowledgeToMarkdown / exportKnowledgeToYaml

Export entire knowledge base.

| Method | Returns | Throws |
|--------|---------|--------|
| `exportKnowledgeToMarkdown(String workspaceJson)` | `String` | `DataModellingException` |
| `exportKnowledgeToYaml(String workspaceJson)` | `String` | `DataModellingException` |

---

## Format Conversion

### universalOdcsConverter

Universal ODCS format converter.

| Method | Returns | Throws |
|--------|---------|--------|
| `universalOdcsConverter(String workspaceJson, String targetVersion)` | `String` | `DataModellingException` |

**Supported Target Versions:**
- `"v2.0"` - ODCS v2.0
- `"v3.0"` - ODCS v3.0
- `"v3.1"` - ODCS v3.1

**Example:**
```java
String converted = bridge.universalOdcsConverter(
    workspaceJson,
    "v3.1"
);
```

### openApiToOdcsConverter

OpenAPI to ODCS conversion.

| Method | Returns | Throws |
|--------|---------|--------|
| `openApiToOdcsConverter(String openapiJson)` | `String` (ODCS workspace) | `DataModellingException` |

---

## Utility Methods

### workspaceToJson

Get workspace metadata as JSON.

| Method | Returns | Throws |
|--------|---------|--------|
| `workspaceToJson(String workspaceJson)` | `String` | `DataModellingException` |

### articleToJson

Get article metadata.

| Method | Returns | Throws |
|--------|---------|--------|
| `articleToJson(String articleJson)` | `String` | `DataModellingException` |

---

## DataModellingException

Exception thrown for data modelling operations.

### Properties

| Property | Type | Description |
|----------|------|-------------|
| `message` | String | Error message |
| `errorCode` | String | Error code |
| `diagnostics` | String | Detailed diagnostics |

### Error Codes

| Code | Description | Recovery |
|------|-------------|----------|
| `INVALID_YAML` | Malformed YAML input | Validate YAML syntax |
| `SQL_GENERATION_ERROR` | Dialect-specific SQL issue | Check database compatibility |
| `INVALID_SCHEMA` | Schema validation failed | Review field definitions |
| `CONVERSION_ERROR` | Format conversion failed | Check supported formats |
| `INVALID_DECISION` | Decision record format error | Validate MADR structure |

### Example Handling

```java
try {
    String result = bridge.parseOdcsYaml(invalidYaml);
} catch (DataModellingException e) {
    System.err.println("Error: " + e.getMessage());
    System.err.println("Code: " + e.getErrorCode());

    if (e.hasDiagnostics()) {
        System.err.println("Details: " + e.getDiagnostics());
    }
}
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>org.yawlfoundation</groupId>
    <artifactId>yawl-data-modelling</artifactId>
    <version>6.0.0-GA</version>
</dependency>
```

## Import Statements

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;
import org.yawlfoundation.yawl.datamodelling.DataModellingException;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
```

---

## Resource Location

WASM modules are bundled with the JAR:

```
yawl-data-modelling.jar
├── META-INF/resources/wasm/
│   ├── data_modelling_wasm.js
│   └── data_modelling_wasm_bg.wasm
```

Maven configuration:

```xml
<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
            <includes>
                <include>wasm/**/*.wasm</include>
                <include>wasm/**/*.js</include>
            </includes>
        </resource>
    </resources>
</build>
```

## Performance Notes

- **First Call**: Slower due to WASM loading (~1-2 seconds)
- **Subsequent Calls**: Fast (~50-100ms per operation)
- **Memory Usage**: ~50MB base + schema size
- **Thread Safety**: Safe via JavaScriptContextPool

## WebAssembly SDK Details

- **Source**: https://github.com/OffeneDatenmodellierung/data-modelling-sdk
- **Version**: v2.3.0
- **License**: MIT
- **Languages**: Rust
- **Bindings**: wasm-bindgen
- **Size**: ~2MB WASM binary