# OCED Schema Reference

## OcedSchema

`org.yawlfoundation.yawl.pi.bridge.OcedSchema`

Immutable record that maps raw data columns to OCEL2 semantic roles.
Returned by `OcedBridge.inferSchema()` and passed to `OcedBridge.convert()`.

```java
public record OcedSchema(
    String       schemaId,              // unique identifier for this schema
    String       caseIdColumn,          // column name → OCEL2 Case object ID
    String       activityColumn,        // column name → event type name
    String       timestampColumn,       // column name → event time
    List<String> objectTypeColumns,     // column names → additional object types
    List<String> attributeColumns,      // column names → event attributes
    String       inferredFormat,        // "csv", "json", or "xml"
    boolean      aiInferred,            // true if LLM confirmed the inference
    Instant      inferredAt             // when inference ran
)
```

The compact constructor validates that `schemaId`, `caseIdColumn`, `activityColumn`,
`timestampColumn`, and `inferredFormat` are non-null and non-blank.

### Fields

| Field | Required | Description |
|---|---|---|
| `schemaId` | Yes | Unique string identifying this schema mapping; used for caching |
| `caseIdColumn` | Yes | Name of the column that identifies the workflow case (e.g., `"case_id"`) |
| `activityColumn` | Yes | Name of the column that identifies the activity / event type (e.g., `"activity"`) |
| `timestampColumn` | Yes | Name of the column that holds the event timestamp in ISO 8601 format (e.g., `"timestamp"`) |
| `objectTypeColumns` | No | Additional columns whose values become OCEL2 object type instances (e.g., `["department", "product_id"]`) |
| `attributeColumns` | No | Columns whose values become event attributes (e.g., `["amount", "status"]`) |
| `inferredFormat` | Yes | Format the schema was inferred from: `"csv"`, `"json"`, or `"xml"` |
| `aiInferred` | No | `true` if `SchemaInferenceEngine` used LLM confirmation; `false` for heuristic-only |
| `inferredAt` | Yes | Timestamp of inference; used for cache validity |

### Manual construction

When auto-inference is wrong, construct `OcedSchema` directly:

```java
OcedSchema schema = new OcedSchema(
    "my-loan-schema",
    "CASE_ID",              // caseIdColumn
    "ACTIVITY",             // activityColumn
    "TIMESTAMP",            // timestampColumn
    List.of("DEPARTMENT"),  // objectTypeColumns
    List.of("AMOUNT", "STATUS"), // attributeColumns
    "csv",
    false,
    Instant.now()
);

OcedBridge bridge = OcedBridgeFactory.forFormat("csv");
String ocel2Json = bridge.convert(rawCsvData, schema);
```

---

## OcedBridge interface

`org.yawlfoundation.yawl.pi.bridge.OcedBridge`

```java
public interface OcedBridge {
    OcedSchema inferSchema(String rawSample) throws PIException;
    String     convert(String rawData, OcedSchema schema) throws PIException;
    String     formatName();    // "csv", "json", or "xml"
}
```

### Implementations

| Class | `formatName()` |
|---|---|
| `CsvOcedBridge` | `"csv"` |
| `JsonOcedBridge` | `"json"` |
| `XmlOcedBridge` | `"xml"` |

---

## OcedBridgeFactory

`org.yawlfoundation.yawl.pi.bridge.OcedBridgeFactory`

```java
// Explicit format
OcedBridge bridge = OcedBridgeFactory.forFormat("csv");   // case-insensitive

// Auto-detect from content
OcedBridge bridge = OcedBridgeFactory.autoDetect(rawData);
```

`autoDetect` detection order:
1. XML: content starts with `<` after trimming
2. JSON: content starts with `[` or `{` after trimming
3. Default: CSV

---

## ValidationReport

`org.yawlfoundation.yawl.pi.bridge.ValidationReport`

```java
public record ValidationReport(
    boolean      isValid,
    List<String> violations,   // FAIL conditions — conversion will produce invalid OCEL2
    List<String> warnings      // WARN conditions — conversion proceeds but may lose fidelity
)
```

### Typical violations

| Violation | Meaning |
|---|---|
| `"Missing required field: ocel:version"` | OCEL2 top-level version field absent |
| `"Event without timestamp: evt-042"` | Event record has null or unparseable timestamp |
| `"Object type referenced but not declared: Customer"` | Object used in relationships but not in `objectTypes` list |

### Typical warnings

| Warning | Meaning |
|---|---|
| `"Non-standard qualifier: 'processes'"` | Qualifier not in the OCEL2 standard set; still valid |
| `"Empty object list for type: Product"` | No instances of Product object type in the log |
| `"Timestamp precision truncated to milliseconds"` | Source had nanosecond precision |

---

## OCEL2 v2.0 output structure

The `convert()` method produces a JSON string conforming to OCEL2 v2.0:

```json
{
  "ocel:version": "2.0",
  "ocel:ordering": "timestamp",
  "objectTypes": [
    {
      "name": "WorkflowCase",
      "attributes": []
    },
    {
      "name": "WorkItem",
      "attributes": []
    }
  ],
  "eventTypes": [
    {
      "name": "CASE_STARTED",
      "attributes": []
    }
  ],
  "objects": [
    {
      "id": "case-001",
      "type": "WorkflowCase",
      "attributes": []
    }
  ],
  "events": [
    {
      "id": "evt-uuid-here",
      "type": "CASE_STARTED",
      "time": "2026-02-01T09:00:00Z",
      "attributes": [],
      "relationships": [
        {
          "objectId": "case-001",
          "qualifier": "case"
        }
      ]
    }
  ]
}
```
