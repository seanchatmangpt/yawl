# How to Ingest an Event Log

This guide shows how to convert a CSV, JSON, or XML event log into OCEL2 v2.0 JSON
using the `OcedBridge` family or the `ProcessIntelligenceFacade.prepareEventData()` shortcut.

---

## Using the facade (simplest path)

If you already have a `ProcessIntelligenceFacade`, use `prepareEventData()`:

```java
// Auto-detect format
String ocel2Json = facade.prepareEventData(rawData);

// Explicit format
String ocel2Json = facade.prepareEventData(rawData, "csv");
```

Both methods throw `PIException("dataprep")` on failure.

---

## Using OcedBridgeFactory directly

### Auto-detect format

```java
import org.yawlfoundation.yawl.pi.bridge.*;

String rawData = "case_id,activity,timestamp,amount\n"
               + "case-001,loan_created,2026-02-01T09:00:00Z,5000\n"
               + "case-001,credit_check,2026-02-01T09:30:00Z,5000\n";

OcedBridge bridge = OcedBridgeFactory.autoDetect(rawData);  // detects CSV
OcedSchema schema  = bridge.inferSchema(rawData);
String ocel2Json   = bridge.convert(rawData, schema);
```

### Explicit format

```java
OcedBridge bridge = OcedBridgeFactory.forFormat("csv");    // "json" or "xml" also valid
```

---

## Schema inference

`inferSchema()` returns an `OcedSchema` record. Inspect it before converting:

```java
OcedSchema schema = bridge.inferSchema(rawData);

System.out.println("Case ID column: "   + schema.caseIdColumn());
System.out.println("Activity column: "  + schema.activityColumn());
System.out.println("Timestamp column: " + schema.timestampColumn());
System.out.println("Object types: "     + schema.objectTypeColumns());
System.out.println("Attributes: "       + schema.attributeColumns());
System.out.println("AI confirmed: "     + schema.aiInferred());
```

---

## Correcting a wrong schema

If the auto-inferred schema maps the wrong columns, construct `OcedSchema` manually:

```java
OcedSchema corrected = new OcedSchema(
    "loan-schema-v1",       // schemaId
    "PROC_INST_KEY",        // caseIdColumn — actual column name in your CSV
    "ACTIVITY_TYPE",        // activityColumn
    "EVENT_TIME",           // timestampColumn
    List.of("DEPT_CODE"),   // objectTypeColumns
    List.of("AMOUNT", "OFFICER_ID"),  // attributeColumns
    "csv",                  // inferredFormat
    false,                  // aiInferred
    Instant.now()
);

String ocel2Json = bridge.convert(rawData, corrected);
```

---

## Validating the output

```java
import org.yawlfoundation.yawl.pi.bridge.EventDataValidator;
import org.yawlfoundation.yawl.pi.bridge.ValidationReport;

EventDataValidator validator = new EventDataValidator();
ValidationReport report = validator.validate(ocel2Json);

if (!report.isValid()) {
    System.err.println("Violations: " + report.violations());
} else if (!report.warnings().isEmpty()) {
    System.out.println("Warnings: " + report.warnings());
}
```

---

## CSV format expectations

The CSV bridge expects:
- **Header row** on the first line
- **Comma delimiter** (configurable via `OcedSchema` in future versions)
- **ISO 8601 timestamps** in the timestamp column (e.g., `2026-02-01T09:00:00Z`)
- **No quoting required** for simple values; standard CSV quoting supported

Example:
```
case_id,activity,timestamp,resource
case-001,create_order,2026-02-01T09:00:00Z,alice
case-001,approve_order,2026-02-01T09:15:00Z,bob
case-002,create_order,2026-02-01T10:00:00Z,charlie
```

---

## JSON format expectations

The JSON bridge expects a **top-level array** of event objects:

```json
[
  {
    "case_id": "case-001",
    "activity": "create_order",
    "timestamp": "2026-02-01T09:00:00Z",
    "amount": 249.99
  },
  {
    "case_id": "case-001",
    "activity": "approve_order",
    "timestamp": "2026-02-01T09:15:00Z",
    "amount": 249.99
  }
]
```

---

## XML format expectations

The XML bridge expects a **root element** wrapping individual **event elements**:

```xml
<events>
  <event>
    <case_id>case-001</case_id>
    <activity>create_order</activity>
    <timestamp>2026-02-01T09:00:00Z</timestamp>
    <amount>249.99</amount>
  </event>
  <event>
    <case_id>case-001</case_id>
    <activity>approve_order</activity>
    <timestamp>2026-02-01T09:15:00Z</timestamp>
    <amount>249.99</amount>
  </event>
</events>
```

Element tag names become column names for schema inference.
