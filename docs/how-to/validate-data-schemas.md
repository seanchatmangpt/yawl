# How to Validate Data Schema Quality

## Problem

Before importing or publishing a schema, you want to catch naming violations, type errors, circular foreign-key dependencies, and naming conflicts between existing and new tables — all without deploying the schema to a database or running a linter separately.

`DataModellingBridge` exposes the data-modelling-sdk's validation functions as typed Java methods.

## Prerequisites

- `yawl-data-modelling` on your classpath (version 6.0.0-GA)
- GraalVM JDK 24.1+ at runtime
- Workspace JSON or individual names/types to validate

## Steps

### 1. Validate table names

Table names must follow snake_case ODCS conventions. Use `validateTableName()` to check:

```java
import org.yawlfoundation.yawl.datamodelling.DataModellingBridge;

try (DataModellingBridge bridge = new DataModellingBridge()) {

    // Valid snake_case
    String ok = bridge.validateTableName("customer_orders");
    System.out.println("Valid name result: " + ok);

    // Invalid (camelCase)
    String bad = bridge.validateTableName("CustomerOrders");
    System.out.println("Invalid name result: " + bad);
}
```

The return value is a JSON string. Parse it with your JSON library to extract the `valid` boolean and any `errors` array:

```json
{"valid": true, "name": "customer_orders", "errors": []}
{"valid": false, "name": "CustomerOrders", "errors": ["Table names must use snake_case"]}
```

---

### 2. Validate column names

Column names follow the same snake_case convention. Reserved SQL keywords are also checked:

```java
// Valid
String colOk  = bridge.validateColumnName("created_at");

// Reserved keyword
String colBad = bridge.validateColumnName("order");
```

---

### 3. Validate data types

Check whether a type string is a recognised ODCS type:

```java
// Standard ODCS types
bridge.validateDataType("bigint");     // valid
bridge.validateDataType("varchar");    // valid
bridge.validateDataType("timestamp");  // valid
bridge.validateDataType("uuid");       // valid
bridge.validateDataType("jsonb");      // valid

// Non-standard
String result = bridge.validateDataType("MYTYPE");
System.out.println(result); // {"valid": false, "errors": ["Unknown type: MYTYPE"]}
```

---

### 4. Validate ODPS YAML

Validate an Open Data Product Standard YAML document against the ODPS JSON Schema. This method throws `DataModellingException` if the document is invalid:

```java
String odpsYaml = """
    product:
      name: analytics-events
      version: "1.0"
      outputs:
        - dataContract: events-v2
          format: parquet
    """;

try {
    bridge.validateOdps(odpsYaml);
    System.out.println("ODPS document is valid");
} catch (DataModellingException e) {
    System.err.println("ODPS validation failed: " + e.getMessage());
}
```

Unlike the other validate methods, `validateOdps()` returns `void` and throws on failure rather than returning a result JSON. This is intentional — ODPS validation is a schema-compliance gate, not a soft check.

---

### 5. Check for circular dependencies

Before adding a foreign key relationship between two tables, check that it would not create a cycle in the relationship graph:

```java
// Existing relationships JSON array
String existingRelationships = """
    [
      {"id": "rel-1", "sourceTableId": "orders", "targetTableId": "customers"},
      {"id": "rel-2", "sourceTableId": "order_items", "targetTableId": "orders"}
    ]
    """;

// Would adding orders → order_items create a cycle?
String circular = bridge.checkCircularDependency(
    existingRelationships,
    "orders",        // sourceTableId
    "order_items"    // targetTableId
);

System.out.println("Is circular: " + circular);
// Returns: "true" or "false" as a JSON string
```

Integrate this into your relationship-addition logic:

```java
if ("true".equals(bridge.checkCircularDependency(relationships, srcId, tgtId))) {
    throw new IllegalArgumentException(
        "Adding relationship " + srcId + " → " + tgtId +
        " would create a circular dependency");
}
// Safe to add
workspace = bridge.addRelationshipToWorkspace(workspace, newRelationshipJson);
```

---

### 6. Detect naming conflicts between existing and new tables

Before merging a new set of tables into an existing workspace, check for name collisions:

```java
String existingTables = """
    [
      {"id": "t1", "name": "customers"},
      {"id": "t2", "name": "orders"}
    ]
    """;

String newTables = """
    [
      {"id": "t3", "name": "customers"},
      {"id": "t4", "name": "products"}
    ]
    """;

String conflicts = bridge.detectNamingConflicts(existingTables, newTables);
System.out.println("Conflicts: " + conflicts);
// Returns JSON array of conflict descriptions
// e.g. [{"name": "customers", "conflict": "duplicate table name"}]
```

Use this before importing a new schema into a workspace that already has tables:

```java
// Import candidate tables
String candidateWorkspace = bridge.importFromSql(newSql, "postgres");
// ... extract tables from candidateWorkspace with JSON library ...

String conflicts = bridge.detectNamingConflicts(existingTablesJson, newTablesJson);
if (!conflicts.equals("[]")) {
    System.err.println("Import blocked: naming conflicts detected: " + conflicts);
} else {
    // Safe to merge
}
```

---

### 7. Build a validation pipeline

Combine the above checks into a single validation pass before committing a schema:

```java
public record ValidationResult(boolean valid, List<String> errors) {}

public ValidationResult validateSchema(DataModellingBridge bridge,
                                        String tableName,
                                        List<String> columnNames,
                                        List<String> dataTypes) {
    List<String> errors = new ArrayList<>();

    // Validate table name
    String tableResult = bridge.validateTableName(tableName);
    // parse tableResult JSON and check "valid"

    // Validate all column names
    for (String col : columnNames) {
        String colResult = bridge.validateColumnName(col);
        // parse colResult and collect errors
    }

    // Validate all data types
    for (String type : dataTypes) {
        String typeResult = bridge.validateDataType(type);
        // parse typeResult and collect errors
    }

    return new ValidationResult(errors.isEmpty(), errors);
}
```

---

## Verification

Run a known-bad name and confirm the validation catches it:

```java
String result = bridge.validateTableName("MyTable");
assert result.contains("\"valid\":false") || result.contains("\"valid\": false") :
    "Expected invalid result for CamelCase name";
```

---

## Troubleshooting

### `validateOdps()` throws with message "schema validation"

The ODPS YAML is missing required top-level fields. ODPS requires at minimum `product.name`, `product.version`, and `product.outputs`. Add the missing fields or use the data-modelling-sdk documentation for the full ODPS schema.

### `checkCircularDependency` always returns `"false"` even with obvious cycles

Ensure the `relationshipsJson` parameter contains JSON objects with `sourceTableId` and `targetTableId` fields. If the field names differ (e.g., `from` and `to`), the SDK will not detect the cycle.

### `detectNamingConflicts` returns `"[]"` when conflicts exist

The `existingTablesJson` and `newTablesJson` parameters must be valid JSON arrays of objects with a `name` field. Pass the raw table objects from workspace JSON, not the workspace JSON itself.
