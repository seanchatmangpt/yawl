# DataModellingBridge API Reference

**Module**: `yawl-data-modelling` | **Class**: `org.yawlfoundation.yawl.datamodelling.DataModellingBridge`

`DataModellingBridge` is a thin Java facade over the data-modelling-sdk v2.3.0 WebAssembly module. Every method delegates to a WASM export and returns raw JSON. Methods return workspace JSON strings that can be passed to subsequent operations without parsing.

---

## Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `WASM_RESOURCE` | `"wasm/data_modelling_wasm_bg.wasm"` | Classpath resource: compiled Rust WASM binary |
| `GLUE_RESOURCE` | `"wasm/data_modelling_wasm.js"` | Classpath resource: wasm-bindgen ES module glue |

---

## Constructors

### `DataModellingBridge()`

Opens the bridge with a context pool size of 1. Suitable for sequential, single-threaded use.

```java
DataModellingBridge bridge = new DataModellingBridge();
```

**Throws**: `DataModellingException(MODULE_LOAD_ERROR)` if WASM resources are missing from the classpath.

### `DataModellingBridge(int poolSize)`

Opens the bridge with a custom JS+WASM context pool size for concurrent callers.

```java
DataModellingBridge bridge = new DataModellingBridge(4);
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `poolSize` | `int` | Number of concurrent JS+WASM contexts. Must be ≥ 1. |

**Throws**: `IllegalArgumentException` if `poolSize < 1`.

---

## Schema Import

All import methods return a workspace JSON string. Inputs must not be null.

### `parseOdcsYaml(String yaml)`

Parses ODCS YAML (v3.1.0 or v2.x) into workspace JSON.

```java
String workspace = bridge.parseOdcsYaml(odcsYaml);
```

### `parseOdcsYamlV2(String yaml)`

Alternate ODCS v2 parser with extended field preservation. Use when `parseOdcsYaml` drops fields.

### `importFromSql(String sql, String dialect)`

Imports SQL CREATE TABLE statements into workspace JSON.

| Parameter | Type | Valid values |
|-----------|------|-------------|
| `sql` | `String` | SQL DDL content |
| `dialect` | `String` | `"postgres"`, `"mysql"`, `"sqlite"`, `"databricks"`, `"generic"` |

### `importFromAvro(String avroContent)`

Imports an Avro JSON schema into workspace JSON.

### `importFromJsonSchema(String jsonSchemaContent)`

Imports a JSON Schema (draft 2019-09 or 2020-12) into workspace JSON.

### `importFromProtobuf(String protobufContent)`

Imports a `.proto` Protobuf schema definition into workspace JSON.

### `importFromCads(String yamlContent)`

Imports a CADS (Compute Asset Description Specification) YAML document.

### `importFromOdps(String yamlContent)`

Imports an ODPS (Open Data Product Standard) YAML document.

### `importBpmnModel(String domainId, String xmlContent, @Nullable String modelName)`

Imports a BPMN 2.0 XML process model into a named domain.

| Parameter | Type | Description |
|-----------|------|-------------|
| `domainId` | `String` | Target domain ID |
| `xmlContent` | `String` | BPMN 2.0 XML content |
| `modelName` | `String?` | Optional model name; pass `null` to use the BPMN `name` attribute |

**Returns**: Domain JSON string (not workspace JSON).

### `importDmnModel(String domainId, String xmlContent, @Nullable String modelName)`

Imports a DMN 1.3 XML decision model into a named domain.

| Parameter | Type | Description |
|-----------|------|-------------|
| `domainId` | `String` | Target domain ID |
| `xmlContent` | `String` | DMN 1.3 XML content |
| `modelName` | `String?` | Optional model name; may be null |

**Returns**: Domain JSON string.

### `importOpenapiSpec(String domainId, String content, @Nullable String apiName)`

Imports an OpenAPI 3.1.1 specification (YAML or JSON) into a named domain.

---

## Schema Export

All export methods accept a workspace or domain JSON string as input.

### `exportOdcsYamlV2(String contractJson)`

Exports workspace JSON to ODCS YAML v3.1.0.

```java
String yaml = bridge.exportOdcsYamlV2(workspaceJson);
```

### `exportToSql(String workspaceJson, String dialect)`

Exports workspace JSON to SQL DDL statements. Dialect values same as `importFromSql`.

### `exportBpmnModel(String xmlContent)`

Normalises and re-serialises BPMN 2.0 XML.

### `exportDmnModel(String xmlContent)`

Normalises and re-serialises DMN 1.3 XML.

### `exportOdcsYamlToMarkdown(String odcsYaml)`

Renders ODCS YAML as a Markdown data dictionary.

### `exportOdpsToMarkdown(String productJson)`

Renders an ODPS product JSON as Markdown documentation.

---

## Format Conversion

### `convertToOdcs(String input, @Nullable String format)`

Universal converter: converts any supported format to ODCS v3.1.0.

| Parameter | Type | Description |
|-----------|------|-------------|
| `input` | `String` | Source content in any supported format |
| `format` | `String?` | Format hint: `"sql"`, `"avro"`, `"json_schema"`, `"protobuf"`, `"cads"`, `"odps"`, `"bpmn"`, `"dmn"`, `"openapi"`. Pass `null` for auto-detection. |

### `convertOpenapiToOdcs(String openapiContent, String componentName, @Nullable String tableName)`

Converts a specific OpenAPI schema component to an ODCS table definition.

| Parameter | Type | Description |
|-----------|------|-------------|
| `openapiContent` | `String` | Full OpenAPI specification |
| `componentName` | `String` | The `components.schemas` key to convert |
| `tableName` | `String?` | Optional target table name; defaults to `componentName` |

### `analyzeOpenapiConversion(String openapiContent, String componentName)`

Returns a feasibility analysis JSON showing which OpenAPI fields map cleanly to ODCS and which require manual intervention.

### `migrateDataflowToDomain(String dataflowYaml, @Nullable String domainName)`

Migrates a legacy DataFlow YAML file to the current Domain schema format.

---

## Workspace Operations

### `createWorkspace(String name, String ownerId)`

Creates a new empty workspace.

```java
String workspace = bridge.createWorkspace("data-platform", "team-001");
```

### `parseWorkspaceYaml(String yamlContent)`

Parses workspace YAML into workspace JSON.

### `addRelationshipToWorkspace(String workspaceJson, String relationshipJson)`

Adds a relationship object to a workspace. Returns the updated workspace JSON.

### `removeRelationshipFromWorkspace(String workspaceJson, String relationshipId)`

Removes a relationship by ID from a workspace. Returns the updated workspace JSON.

---

## Domain Operations

### `createDomain(String name)`

Creates a new business domain object.

```java
String domain = bridge.createDomain("sales");
```

### `addDomainToWorkspace(String workspaceJson, String domainId, String domainName)`

Attaches a domain reference to a workspace. Returns the updated workspace JSON.

### `removeDomainFromWorkspace(String workspaceJson, String domainId)`

Removes a domain reference from a workspace by ID. Returns the updated workspace JSON.

### `addSystemToDomain(String workspaceJson, String domainId, String systemJson)`

Adds a system node to a domain within a workspace. Returns the updated workspace JSON.

### `addOdcsNodeToDomain(String workspaceJson, String domainId, String nodeJson)`

Adds an ODCS table node (parsed from ODCS YAML or SQL import) to a domain. Returns the updated workspace JSON.

### `addCadsNodeToDomain(String workspaceJson, String domainId, String nodeJson)`

Adds a CADS compute asset node to a domain. Returns the updated workspace JSON.

---

## Decision Records (MADR)

### `createDecision(int number, String title, String context, String decision, String author)`

Creates an MADR-compliant Architecture Decision Record.

| Parameter | Type | Description |
|-----------|------|-------------|
| `number` | `int` | Sequential ADR number (must be > 0) |
| `title` | `String` | Decision title |
| `context` | `String` | Problem context and forces |
| `decision` | `String` | Chosen option |
| `author` | `String` | Author name or team |

**Returns**: Decision JSON string.

### `createDecisionIndex()`

Creates an empty decision index JSON.

### `parseDecisionYaml(String yamlContent)`

Parses a saved decision YAML file back to decision JSON.

### `exportDecisionToYaml(String decisionJson)`

Exports a decision to MADR YAML format, suitable for git storage.

### `exportDecisionToMarkdown(String decisionJson)`

Exports a decision to MADR Markdown format.

### `parseDecisionIndexYaml(String yamlContent)`

Parses a saved decision index YAML file back to index JSON.

### `exportDecisionIndexToYaml(String indexJson)`

Exports a decision index to YAML format.

### `addDecisionToIndex(String indexJson, String decisionJson, String filename)`

Adds a decision entry to an index. Returns the updated index JSON.

| Parameter | Type | Description |
|-----------|------|-------------|
| `indexJson` | `String` | Current decision index JSON |
| `decisionJson` | `String` | Decision JSON to add |
| `filename` | `String` | YAML filename for the decision (e.g., `"0001-use-postgres.yaml"`) |

---

## Knowledge Base

### `createKnowledgeArticle(int number, String title, String summary, String content, String author)`

Creates a KB article.

| Parameter | Type | Description |
|-----------|------|-------------|
| `number` | `int` | Sequential article number (must be > 0) |
| `title` | `String` | Article title |
| `summary` | `String` | One-line summary |
| `content` | `String` | Full article body in Markdown |
| `author` | `String` | Author name or team |

**Returns**: Article JSON string.

### `createKnowledgeIndex()`

Creates an empty knowledge index JSON.

### `parseKnowledgeYaml(String yamlContent)`

Parses a saved knowledge article YAML back to article JSON.

### `exportKnowledgeToYaml(String articleJson)`

Exports an article to YAML format.

### `exportKnowledgeToMarkdown(String articleJson)`

Exports an article to Markdown format.

### `parseKnowledgeIndexYaml(String yamlContent)`

Parses a knowledge index YAML back to index JSON.

### `exportKnowledgeIndexToYaml(String indexJson)`

Exports a knowledge index to YAML format.

### `addArticleToKnowledgeIndex(String indexJson, String articleJson, String filename)`

Adds an article entry to a knowledge index. Returns the updated index JSON.

### `searchKnowledgeArticles(String articlesJson, String query)`

Full-text searches a JSON array of articles by title and content. Returns a JSON array of matching articles.

| Parameter | Type | Description |
|-----------|------|-------------|
| `articlesJson` | `String` | JSON array: `[article1, article2, ...]` |
| `query` | `String` | Search query string |

---

## Sketches

### `createSketch(int number, String title, String sketchType, String excalidrawData)`

Creates a sketch with Excalidraw data.

| Parameter | Type | Description |
|-----------|------|-------------|
| `number` | `int` | Sequential sketch number (must be > 0) |
| `title` | `String` | Sketch title |
| `sketchType` | `String` | Type tag (e.g., `"architecture"`, `"dataflow"`, `"workflow"`) |
| `excalidrawData` | `String` | Excalidraw JSON data |

**Returns**: Sketch JSON string.

### `createSketchIndex()`

Creates an empty sketch index JSON.

### `parseSketchYaml(String yamlContent)`

Parses a saved sketch YAML back to sketch JSON.

### `exportSketchToYaml(String sketchJson)`

Exports a sketch to YAML format.

### `parseSketchIndexYaml(String yamlContent)`

Parses a sketch index YAML back to index JSON.

### `addSketchToIndex(String indexJson, String sketchJson, String filename)`

Adds a sketch entry to a sketch index. Returns the updated index JSON.

### `searchSketches(String sketchesJson, String query)`

Searches sketches by title, description, or type tags.

| Parameter | Type | Description |
|-----------|------|-------------|
| `sketchesJson` | `String` | JSON array of sketch objects |
| `query` | `String` | Search query string |

---

## Validation

### `validateOdps(String yamlContent)`

Validates ODPS YAML against the ODPS JSON Schema. **Returns void**.

**Throws**: `DataModellingException(EXECUTION_ERROR)` if validation fails. The exception message contains the schema violation details.

### `validateTableName(String name)`

Validates a table name against ODCS naming conventions.

**Returns**: JSON string with `valid` (boolean) and `errors` (array of strings).

### `validateColumnName(String name)`

Validates a column name against ODCS naming conventions.

**Returns**: JSON string with `valid` (boolean) and `errors` (array of strings).

### `validateDataType(String dataType)`

Validates a data type string against the ODCS type registry.

**Returns**: JSON string with `valid` (boolean) and `errors` (array of strings).

### `checkCircularDependency(String relationshipsJson, String sourceTableId, String targetTableId)`

Checks whether adding a directed edge would create a cycle in the relationship graph.

| Parameter | Type | Description |
|-----------|------|-------------|
| `relationshipsJson` | `String` | JSON array of existing relationship objects (each with `sourceTableId` and `targetTableId` fields) |
| `sourceTableId` | `String` | Proposed source table |
| `targetTableId` | `String` | Proposed target table |

**Returns**: `"true"` or `"false"` as a JSON string.

### `detectNamingConflicts(String existingTablesJson, String newTablesJson)`

Detects name collisions between existing tables and a new set of tables.

| Parameter | Type | Description |
|-----------|------|-------------|
| `existingTablesJson` | `String` | JSON array of existing table objects (each with `name` field) |
| `newTablesJson` | `String` | JSON array of new table objects to check |

**Returns**: JSON array of conflict description objects (empty array `[]` if no conflicts).

---

## Lifecycle

### `close()`

Closes the bridge, releasing all JS+WASM contexts and the temp directory containing extracted resources. Idempotent.

```java
bridge.close();      // explicit
// or:
try (var bridge = new DataModellingBridge()) { ... }  // auto-close
```

---

## Exceptions

### `DataModellingException`

Thrown when any operation fails. Contains an `ErrorKind` enum:

| ErrorKind | When |
|-----------|------|
| `MODULE_LOAD_ERROR` | WASM binary or JS glue not found, or temp-dir extraction failed |
| `EXECUTION_ERROR` | WASM function trapped, FEEL parse error, or validation failure |

```java
try {
    String result = bridge.parseOdcsYaml(badYaml);
} catch (DataModellingException e) {
    switch (e.getErrorKind()) {
        case MODULE_LOAD_ERROR -> log.error("WASM not on classpath");
        case EXECUTION_ERROR   -> log.error("WASM execution failed: {}", e.getMessage());
    }
}
```

---

## Thread Safety

`DataModellingBridge` is thread-safe. The underlying `JavaScriptContextPool` serialises access to the shared GraalJS contexts. Increase `poolSize` to allow concurrent evaluations.

---

## Runtime Requirements

| Requirement | Minimum |
|-------------|---------|
| JDK | GraalVM JDK 24.1+ |
| GraalVM languages | `js` (GraalJS) + `wasm` (GraalWASM) |
| Memory | ~50 MB per pool slot at startup (WASM module initialisation) |
