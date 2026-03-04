# Maximizing Data Modelling SDK Usage

This guide explains strategies and patterns for getting the most value from data-modelling-sdk v2.3.0.

---

## Why This Guide Exists

The SDK provides 55+ capabilities across import, export, validation, conversion, inference, and mapping. This guide helps you understand:

1. **When to use each capability**
2. **How to combine capabilities for complex workflows**
3. **Architecture patterns that maximize value**
4. **Common pitfalls and how to avoid them**

---

## The Three-Layer Architecture

Understanding the layers helps you choose the right integration point.

```
┌─────────────────────────────────────────────────────────────┐
│                     Layer 3: Domain API                     │
│  Typed Java methods with zero FFM types in signatures    │
│  - DataModellingBridge.parseOdcsYaml()                │
│  - DataModellingBridge.importFromSql()                 │
│  - DataModellingBridge.exportOdcsYamlV2()              │
└─────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     Layer 2: FFI Transport                               │
│  callDm() helper for uniform char*→char* pattern                 │
│  - Memory allocation/deallocation                              │
│  - String marshalling between Java and native code             │
└─────────────────────┬─────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     Layer 1: Native FFI                                │
│  Panama FFM bindings to libdatamodelling.so/.dylib            │
│  - 55 dm_* functions via MethodHandles                         │
│  - dm_string_free() for memory management                    │
└─────────────────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────┐
│              data-modelling-sdk (Rust)                          │
│  - ODCS, ODPS, CADS, OpenAPI, BPMN, DMN importers            │
│  - Schema validation, conversion, inference                 │
│  - Single source of truth for all schema operations           │
└─────────────────────────────────────────────────────────────┘
```

### When to Use Each Layer

| Use Case | Layer | Example |
|----------|-------|---------|
| Application code | L3 (Domain API) | `bridge.parseOdcsYaml(yaml)` |
| Custom transport logic | L2 (FFI Transport) | Extending with custom serialization |
| Performance-critical paths | L1 (Native FFI) | Direct MethodHandle invocation |
| Adding new capabilities | Rust SDK | Fork data-modelling-sdk, add function |

---

## Usage Patterns

### Pattern 1: Schema Onboarding Pipeline

**Scenario:** You receive schemas from multiple teams in different formats and need to normalize them.

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Team A    │     │   Team B    │     │   Team C    │
│  PostgreSQL │     │   Avro     │     │  JSON Schema │
└─────┬──────┘     └─────┬──────┘     └─────┬──────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────────┐
│                  DataModellingBridge                          │
│  importFromSql() | importFromAvro() | importFromJsonSchema() │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                  Unified Workspace JSON                           │
│  All schemas in common format for downstream processing        │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    // Collect schemas from different sources
    String pgSchema = bridge.importFromSql(pgDdl, "postgres");
    String avroSchema = bridge.importFromAvro(avroJson);
    String jsonSchema = bridge.importFromJsonSchema(jsonSchemaStr);

    // All are now in common workspace format
    // Can be combined, compared, validated
}
```

### Pattern 2: Contract-First Development

**Scenario:** Define data contracts before implementation exists.

```
1. Write ODCS YAML contract
2. Validate contract
3. Generate SQL DDL
4. Share with DBAs
5. Implement when ready
```

**Implementation:**

```java
// 1. Define contract
String contract = """
    apiVersion: v3.1.0
    kind: DataContract
    name: orders
    schema:
      fields:
        - name: order_id
          type: bigint
          primary: true
        - name: customer_id
          type: bigint
          required: true
    quality:
      type: sql
      queries:
        - "SELECT COUNT(*) FROM orders WHERE created_at > NOW() - interval '1 day'"
    "" ;

// 2. Import and validate
try (DataModellingBridge bridge = new DataModellingBridge()) {
    String workspace = bridge.parseOdcsYaml(contract);
    bridge.validateOdps(workspace);

    // 3. Generate SQL for implementation
    String sql = bridge.exportToSql(workspace, "postgres");
    System.out.println("Generated DDL:\n" + sql);
}
```

### Pattern 3: Schema Evolution Tracking

**Scenario:** Track how schemas change over time using decision records.

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    // Create ADR for schema change
    String decision = bridge.createDecision(
        5,  // ADR-005
        "Add PII classification to customer schema",
        "GDPR compliance requires tracking PII fields",
        "Added pii_classification tag to email, phone, address fields; created data_governance domain",
        "data-governance@company.com"
    );

    // Export to MADR format
    String markdown = bridge.exportDecisionToMarkdown(decision);
    Files.writeString(Path.of("docs/decisions/0005-pii-classification.md"), markdown);

    // Maintain decision index
    String index = bridge.createDecisionIndex();
    String updated = bridge.addDecisionToIndex(index, decision, "0005-pii-classification.md");
    String indexYaml = bridge.exportDecisionIndexToYaml(updated);
}
```

### Pattern 4: Multi-Format Publishing

**Scenario:** Single source schema published to multiple formats for different consumers.

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    String workspace = bridge.parseOdcsYaml(odcsYaml);

    // For data engineers: SQL
    String sql = bridge.exportToSql(workspace, "postgres");
    Files.writeString(Path.of("dist/sql/schema.sql"), sql);

    // For API developers: OpenAPI
    String openapi = bridge.convertToOdcs(workspace, null);
    // (Additional conversion to OpenAPI format)

    // For analysts: Markdown documentation
    String markdown = bridge.exportOdcsYamlToMarkdown(odcsYaml);
    Files.writeString(Path.of("docs/schemas/customers.md"), markdown);
}
```

### Pattern 5: LLM-Assisted Schema Design

**Scenario:** Use LLM to refine and document schemas automatically.

```java
try (DataModellingBridge bridge = new DataModellingBridge()) {
    // Configure LLM
    LlmConfig config = new LlmConfig();
    config.setMode(LlmConfig.Mode.ONLINE);
    config.setBaseUrl("http://localhost:11434");
    config.setModel("llama3.2");
    config.setTemperature(0.3);  // Low temp for consistency

    // Sample data for context
    String[] samples = {
        "{\"id\": 1, \"email\": \"user@example.com\", \"created\": \"2024-01-15\"}"
    };

    // Objectives for refinement
    String[] objectives = {
        "Add field descriptions",
        "Detect and tag PII fields",
        "Suggest appropriate indexes"
    };

    // Refine schema
    String refined = bridge.refineSchemaWithLlmOnline(
        rawSchema, samples, objectives, "E-commerce customer data", config
    );

    // Enrich documentation
    String documented = bridge.enrichDocumentationWithLlm(refined, config);
}
```

---

## Integration Patterns

### Pattern: Workflow Task Integration

```java
public class SchemaValidationHandler implements YParameterizedItemHandler {

    private final DataModellingBridge bridge;

    public SchemaValidationHandler() {
        this.bridge = new DataModellingBridge(2);  // Pool size 2
    }

    @Override
    public void execute(YParameterizedItem item) {
        String schemaYaml = item.getDataVariable("schema");

        try {
            // Import schema
            String workspace = bridge.parseOdcsYaml(schemaYaml);

            // Validate
            bridge.validateOdps(workspace);

            // Check for circular dependencies
            String hasCycle = bridge.hasCyclicDependencies(workspace);
            if ("true".equals(hasCycle)) {
                item.setDynamicData("validationStatus", "FAILED: Circular dependency");
                return;
            }

            // Success
            item.setDynamicData("validationStatus", "VALID");
            item.setDynamicData("workspace", workspace);

        } catch (DataModellingException e) {
            item.setDynamicData("validationStatus", "ERROR: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        bridge.close();
    }
}
```

### Pattern: Event-Driven Schema Registry

```java
public class SchemaRegistryService {

    private final DataModellingBridge bridge;
    private final SchemaStore store;

    public void onSchemaSubmitted(SchemaSubmittedEvent event) {
        try {
            // Normalize to workspace format
            String workspace = bridge.convertToOdcs(
                event.getContent(),
                event.getFormatHint()
            );

            // Create domain if needed
            if (event.getDomainName() != null) {
                String domain = bridge.createDomain(event.getDomainName());
                // Link workspace to domain
            }

            // Store normalized schema
            store.save(event.getSchemaId(), workspace);

            // Publish to subscribers
            eventBus.publish(new SchemaRegisteredEvent(event.getSchemaId(), workspace));

        } catch (DataModellingException e) {
            eventBus.publish(new SchemaRejectedEvent(event.getSchemaId(), e.getMessage()));
        }
    }
}
```

---

## Performance Optimization

### Context Pooling Strategy

| Pool Size | Use Case | Expected Throughput |
|-----------|----------|---------------------|
| 1 | Development, low-volume | ~50 ops/sec |
| 2-4 | Production, moderate load | ~200 ops/sec |
| 8+ | High-throughput, parallel processing | ~500+ ops/sec |

### Memory Considerations

| Resource | Size | Notes |
|----------|------|-------|
| WASM binary | ~500KB | Loaded once per context |
| Per-context overhead | ~50MB | JS engine + WASM JIT |
| Workspace JSON | 10-500KB | Depends on schema complexity |
| Recommended heap | 512MB+ | For production use |

### Batch Processing Pattern

```java
// BAD: One bridge per item
for (String schema : schemas) {
    try (DataModellingBridge b = new DataModellingBridge()) {
        processSchema(b, schema);
    }
}

// GOOD: Shared bridge, batch processing
try (DataModellingBridge bridge = new DataModellingBridge(4)) {
    schemas.parallelStream().forEach(schema -> processSchema(bridge, schema));
}
```

---

## Common Pitfalls

### Pitfall 1: Not Closing the Bridge

```java
// BAD: Resource leak
DataModellingBridge bridge = new DataModellingBridge();
bridge.parseOdcsYaml(yaml);
// Forgot to close - temp files accumulate!

// GOOD: Try-with-resources
try (DataModellingBridge bridge = new DataModellingBridge()) {
    bridge.parseOdcsYaml(yaml);
}
```

### Pitfall 2: Ignoring Validation Errors

```java
// BAD: Silent failure
String workspace = bridge.parseOdcsYaml(invalidYaml);
// workspace may be empty or malformed

// GOOD: Validate explicitly
String workspace = bridge.parseOdcsYaml(yaml);
try {
    bridge.validateOdps(workspace);
} catch (DataModellingException e) {
    logger.error("Schema validation failed: {}", e.getMessage());
    return;
}
```

### Pitfall 3: JSON Parsing Errors

```java
// BAD: Assume valid JSON
String workspace = bridge.parseOdcsYaml(yaml);
JsonObject obj = Json.parse(workspace).asObject();  // May throw

// GOOD: Defensive parsing
String workspace = bridge.parseOdcsYaml(yaml);
if (workspace == null || workspace.isEmpty()) {
    throw new IllegalStateException("Empty workspace returned");
}
JsonObject obj = Json.parse(workspace).asObject();
```

---

## Best Practices Summary

1. **Always use try-with-resources** for bridge lifecycle
2. **Pool contexts appropriately** based on throughput needs
3. **Validate after import** to catch errors early
4. **Use typed methods** (L3) over raw FFI calls
5. **Handle DataModellingException** with appropriate error recovery
6. **Batch operations** when processing multiple schemas
7. **Store normalized format** (workspace JSON) for downstream processing
8. **Maintain decision records** for schema evolution tracking
9. **Use LLM for documentation** but validate outputs
10. **Test with real schemas** not synthetic data

---

## Next Steps

- [Tutorial: Getting Started](../tutorials/data-modelling-sdk-getting-started.md)
- [How-To: Common Tasks](../how-to/data-modelling-sdk-how-to.md)
- [Reference: API Reference](../reference/data-modelling-sdk-reference.md)
