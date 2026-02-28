# Phase 1 - Type-Safe Model Layer API Reference

Quick reference for teammates integrating Phase 1 converters into Phases 2-5.

---

## Converter Methods Summary

### WorkspaceConverter

```java
// Deserialize
DataModellingWorkspace ws = WorkspaceConverter.fromJson(json);

// Serialize
String json = WorkspaceConverter.toJson(ws);

// Create new builder
DataModellingWorkspace.Builder builder = WorkspaceConverter.newBuilder();
```

### TableConverter

```java
DataModellingTable table = TableConverter.fromJson(json);
String json = TableConverter.toJson(table);
DataModellingTable.Builder builder = TableConverter.newBuilder();
```

### ColumnConverter

```java
DataModellingColumn col = ColumnConverter.fromJson(json);
String json = ColumnConverter.toJson(col);
DataModellingColumn.Builder builder = ColumnConverter.newBuilder();
```

### RelationshipConverter

```java
DataModellingRelationship rel = RelationshipConverter.fromJson(json);
String json = RelationshipConverter.toJson(rel);
DataModellingRelationship.Builder builder = RelationshipConverter.newBuilder();
```

### DomainConverter

```java
DataModellingDomain domain = DomainConverter.fromJson(json);
String json = DomainConverter.toJson(domain);
DataModellingDomain.Builder builder = DomainConverter.newBuilder();
```

### DecisionConverter

```java
DataModellingDecision decision = DecisionConverter.fromJson(json);
String json = DecisionConverter.toJson(decision);
DataModellingDecision.Builder builder = DecisionConverter.newBuilder();
```

### ArticleConverter

```java
DataModellingArticle article = ArticleConverter.fromJson(json);
String json = ArticleConverter.toJson(article);
DataModellingArticle.Builder builder = ArticleConverter.newBuilder();
```

### SketchConverter

```java
DataModellingSketch sketch = SketchConverter.fromJson(json);
String json = SketchConverter.toJson(sketch);
DataModellingSketch.Builder builder = SketchConverter.newBuilder();
```

### JsonObjectMapper (Raw Access)

```java
// Get singleton instance
ObjectMapper mapper = JsonObjectMapper.getInstance();

// Parse to any type
MyClass obj = JsonObjectMapper.parseJson(json, MyClass.class);

// Serialize from any type
String json = JsonObjectMapper.toJson(anyObject);
```

---

## Builder Pattern Usage

All models support fluent builders:

```java
DataModellingTable table = DataModellingTable.builder()
    .id("t-1")
    .name("customers")
    .businessName("Customer Master Data")
    .owner("analytics-team")
    .infrastructureType("postgresql")
    .addColumn(DataModellingColumn.builder()
        .name("customer_id")
        .dataType("bigint")
        .primaryKey(true)
        .nullable(false)
        .build())
    .addColumn(DataModellingColumn.builder()
        .name("email")
        .dataType("string")
        .nullable(false)
        .build())
    .build();
```

---

## Key Getter Methods

### DataModellingWorkspace

```java
String id = ws.getId();
String name = ws.getName();
List<DataModellingTable> tables = ws.getTables();
List<DataModellingRelationship> rels = ws.getRelationships();
List<DataModellingDomain> domains = ws.getDomains();
List<DataModellingDecision> decisions = ws.getDecisions();
List<DataModellingArticle> articles = ws.getArticles();
List<DataModellingSketch> sketches = ws.getSketches();

// Helpers
DataModellingTable table = ws.getTableByName("customers");
DataModellingTable table = ws.getTableById("t-1");
DataModellingDomain domain = ws.getDomainById("d-1");
int tableCount = ws.getTableCount();
```

### DataModellingTable

```java
String id = table.getId();
String name = table.getName();
String owner = table.getOwner();
String infrastructureType = table.getInfrastructureType();
List<DataModellingColumn> columns = table.getColumns();
String medallionLayer = table.getMedallionLayer(); // bronze, silver, gold
String scd = table.getScd(); // Slowly Changing Dimension type

// Helpers
DataModellingColumn col = table.getColumnByName("customer_id");
List<DataModellingColumn> pks = table.getPrimaryKeyColumns();
```

### DataModellingColumn

```java
String id = col.getId();
String name = col.getName();
String dataType = col.getDataType();
Boolean isPrimaryKey = col.getPrimaryKey();
Boolean isNullable = col.getNullable();
Integer pkPosition = col.getPrimaryKeyPosition();
String classification = col.getClassification();
Boolean isCritical = col.getCriticalDataElement();
List<DataModellingColumn> nested = col.getProperties(); // For OBJECT types
DataModellingColumn itemType = col.getItems(); // For ARRAY types
List<String> enumValues = col.getEnumValues();
List<String> examples = col.getExamples();
```

### DataModellingRelationship

```java
String id = rel.getId();
String label = rel.getLabel();
String sourceTableId = rel.getSourceTableId();
String targetTableId = rel.getTargetTableId();
String sourceCardinality = rel.getSourceCardinality(); // zeroOrOne, exactlyOne, zeroOrMany, oneOrMany
String targetCardinality = rel.getTargetCardinality();
String flowDirection = rel.getFlowDirection(); // sourceToTarget, targetToSource, bidirectional
String relationshipType = rel.getRelationshipType(); // dataFlow, dependency, foreignKey, etl
String owner = rel.getOwner();
String infrastructureType = rel.getInfrastructureType();
```

### DataModellingDomain

```java
String id = domain.getId();
String name = domain.getName();
String owner = domain.getOwner();
List<String> systems = domain.getSystems();
List<DataModellingDomainAsset> assets = domain.getAssets();
```

### DataModellingDecision

```java
String id = decision.getId();
String title = decision.getTitle();
String status = decision.getStatus(); // accepted, rejected, deprecated, superseded
String context = decision.getContext();
String decision = decision.getDecision();
String consequences = decision.getConsequences();
List<DataModellingDecisionOption> options = decision.getOptions();
List<String> authors = decision.getAuthors();
List<String> reviewers = decision.getReviewers();
List<String> affectedIds = decision.getAffects();
```

### DataModellingArticle

```java
String id = article.getId();
String title = article.getTitle();
String category = article.getCategory();
String content = article.getContent(); // Markdown
String author = article.getAuthor();
List<String> tags = article.getTags();
String status = article.getStatus(); // draft, published, archived
List<String> relatedTables = article.getRelatedTables();
```

### DataModellingSketch

```java
String id = sketch.getId();
String title = sketch.getTitle();
String format = sketch.getFormat(); // excalidraw, drawio, etc.
String content = sketch.getContent(); // JSON serialized sketch
String thumbnail = sketch.getThumbnail(); // Base64 PNG
String creator = sketch.getCreator();
List<String> relatedTables = sketch.getRelatedTables();
List<String> relatedDomains = sketch.getRelatedDomains();
Integer width = sketch.getWidth();
Integer height = sketch.getHeight();
```

---

## Error Handling

All converters throw `DataModellingException`:

```java
try {
    DataModellingWorkspace ws = WorkspaceConverter.fromJson(json);
} catch (DataModellingException e) {
    if (e.getKind() == DataModellingException.ErrorKind.JSON_PARSE_ERROR) {
        // Handle parsing error (malformed JSON, wrong type, etc.)
        System.err.println("Invalid JSON: " + e.getMessage());
    } else if (e.getKind() == DataModellingException.ErrorKind.JSON_SERIALIZE_ERROR) {
        // Handle serialization error
        System.err.println("Cannot serialize: " + e.getMessage());
    }
}
```

---

## Typical Phase-to-Phase Flow

### Phase 1 (Complete)
```java
// Bridge returns JSON string
String workspaceJson = bridge.parseOdcsYaml(yamlContent);

// Phase 1: Convert to types
DataModellingWorkspace ws = WorkspaceConverter.fromJson(workspaceJson);
```

### Phase 2 (Pipeline) - Use Phase 1 Converters

```java
// Phase 2: Work with typed objects
String inferredJson = bridge.inferSchemaFromJson(data);
DataModellingWorkspace inferred = WorkspaceConverter.fromJson(inferredJson);

// Process workspace through pipeline
DataModellingWorkspace mapped = pipelineExecutor.executeMapping(inferred);

// Convert back for export
String output = WorkspaceConverter.toJson(mapped);
```

### Phase 3 (LLM) - Use Phase 1 Converters

```java
// Phase 3: LLM refinement
String original = WorkspaceConverter.toJson(ws);
String prompt = createRefinementPrompt(original);
String refined = llmClient.refine(prompt);

DataModellingWorkspace refinedWs = WorkspaceConverter.fromJson(refined);
```

### Phase 4 (Database Sync) - Use Phase 1 Converters

```java
// Phase 4: Database operations
for (DataModellingTable table : ws.getTables()) {
    String dbType = table.getInfrastructureType();
    String physicalName = table.getPhysicalName();

    // Sync to database
    databaseSync.syncTable(table, dbType);
}
```

---

## Important Notes for Integration

1. **Thread Safety**: All converters are thread-safe (Jackson mapper is thread-safe)
2. **Performance**: Jackson is fast (~1-10ms per conversion depending on size)
3. **Memory**: No memory leaks (Jackson handles cleanup properly)
4. **Null Handling**: Models use `@JsonInclude(NON_NULL)` so nulls don't appear in JSON
5. **Round-trip**: JSON → Model → JSON preserves all data

---

## Testing in Phase 2+

```java
// Example test setup
DataModellingWorkspace original = WorkspaceConverter.fromJson(
    bridge.parseOdcsYaml(testYaml)
);

// Do some processing
original.getTables().get(0).setOwner("new-owner");

// Verify by round-trip
String json = WorkspaceConverter.toJson(original);
DataModellingWorkspace restored = WorkspaceConverter.fromJson(json);
assertEquals("new-owner", restored.getTables().get(0).getOwner());
```

---

## Where to Find Code

**Converter Classes**:
- `/src/org/yawlfoundation/yawl/datamodelling/converters/*.java`

**Model Classes**:
- `/src/org/yawlfoundation/yawl/datamodelling/models/*.java`

**Tests**:
- `/src/test/java/org/yawlfoundation/yawl/datamodelling/converters/*.java`

**Documentation**:
- `/src/org/yawlfoundation/yawl/datamodelling/converters/package-info.java`

---

## Troubleshooting

**Q: JSON_PARSE_ERROR with valid JSON?**
A: Check dataType mismatch. Model expects specific types (String, Integer, Boolean, List, Object). Use JsonObjectMapper.getInstance() to see actual Jackson error.

**Q: Getting null values after conversion?**
A: Jackson skips null fields in JSON by design. Check original JSON. Use `.isNull()` or check with `Objects.nonNull()`.

**Q: Model builder requires field X but I don't have it?**
A: Check `.build()` error message. Only `name`, `title`, etc. are required. ID auto-generates.

**Q: Need to add custom field?**
A: Models support `customProperties` list for arbitrary key-value pairs. Add to list as Object (Map or POJO).

---

**For detailed API docs, see each converter class JavaDoc and model class JavaDoc.**
