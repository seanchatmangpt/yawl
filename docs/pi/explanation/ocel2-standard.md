# OCEL2 Standard — Object-Centric Event Logs

This document explains what OCEL2 v2.0 is, why it supersedes case-centric XES,
and how YAWL workflow events map to the OCEL2 object model.

---

## What is an event log?

A process mining event log records what happened during the execution of a business process.
Each entry (event) captures: what happened (activity), when (timestamp), and who/what was involved.

---

## The problem with case-centric XES

XES (eXtensible Event Stream) — the earlier IEEE standard — assumes each event belongs to
exactly one **case** (trace). This maps well onto simple linear processes, but breaks down for:

| Problem | Example |
|---|---|
| **Many-to-many object relationships** | An order event involves a customer, an order, and multiple order lines |
| **Convergence** | Multiple orders converge at a single payment event |
| **Divergence** | One purchase spawns multiple fulfilment cases |
| **Object lifecycle** | An item goes through picking, packing, and shipping — each a separate trace in XES |

Forcing these onto a single case ID requires artificial case identifiers that carry no
semantic meaning. Analysis based on these IDs produces distorted process models.

---

## OCEL2 v2.0 — object-centric events

OCEL2 decouples events from a single case. An event can be related to **many objects**
of **many types** simultaneously.

### Core concepts

| Concept | Description | Example |
|---|---|---|
| **Event** | A thing that happened | "Order confirmed at 2026-02-01T09:00Z" |
| **Object type** | A category of business entity | Order, Customer, Product, Employee |
| **Object** | An instance of an object type | Order #ORD-2026-001 |
| **Relationship** | Links an event to zero or more objects | Order confirmed → {ORD-2026-001, CUST-42, PROD-7} |
| **Attribute** | A property of an event or object | order.total = 249.99 |

### OCEL2 JSON structure

```json
{
  "objectTypes": [
    { "name": "Order",    "attributes": [{"name": "total", "type": "float"}] },
    { "name": "Customer", "attributes": [{"name": "tier",  "type": "string"}] }
  ],
  "eventTypes": [
    { "name": "order_confirmed", "attributes": [{"name": "channel", "type": "string"}] }
  ],
  "objects": [
    { "id": "ORD-001", "type": "Order",    "attributes": [{"time": "...", "name": "total", "value": "249.99"}] },
    { "id": "CUST-42", "type": "Customer", "attributes": [{"time": "...", "name": "tier",  "value": "gold"}] }
  ],
  "events": [
    {
      "id":          "evt-001",
      "type":        "order_confirmed",
      "time":        "2026-02-01T09:00:00Z",
      "attributes":  [{"name": "channel", "value": "web"}],
      "relationships": [
        {"objectId": "ORD-001", "qualifier": "creates"},
        {"objectId": "CUST-42", "qualifier": "involves"}
      ]
    }
  ]
}
```

---

## How YAWL events map to OCEL2

YAWL's `WorkflowEvent` is case-centric: each event has one `specId`, one `caseId`,
and one `workItemId`. The `OcedBridge` family converts these to OCEL2 by:

| YAWL field | OCEL2 mapping |
|---|---|
| `eventType` (CASE_STARTED, WORKITEM_COMPLETED, …) | event type name |
| `timestamp` | event time |
| `specId` | Specification object type instance |
| `caseId` | Case object (type: WorkflowCase) |
| `workItemId` | WorkItem object (type: WorkItem) |
| `payload` entries | event attributes |

The resulting OCEL2 document has three object types by default: Specification, WorkflowCase,
WorkItem. Each event relates to all three.

### Example mapping

```
WorkflowEvent {
  eventType = WORKITEM_COMPLETED,
  specId    = "loan-application/1.0",
  caseId    = "case-2026-0042",
  workItemId = "credit-check",
  timestamp  = 2026-02-01T10:15:00Z,
  payload    = { "decision": "approved" }
}
```

becomes:

```json
{
  "events": [{
    "id":    "evt-loan-cc-approved",
    "type":  "WORKITEM_COMPLETED",
    "time":  "2026-02-01T10:15:00Z",
    "attributes": [{"name": "decision", "value": "approved"}],
    "relationships": [
      {"objectId": "loan-application-1.0", "qualifier": "specification"},
      {"objectId": "case-2026-0042",       "qualifier": "case"},
      {"objectId": "credit-check",         "qualifier": "workItem"}
    ]
  }]
}
```

---

## SchemaInferenceEngine heuristics

When `OcedBridgeFactory.autoDetect()` or `inferSchema()` is called on raw CSV/JSON/XML
data (not from `WorkflowEventStore`), `SchemaInferenceEngine` attempts to identify
which columns map to which OCEL2 concepts using these heuristics in order:

| Priority | Heuristic | Target |
|---|---|---|
| 1 | Column name contains `case`, `instance`, `process_id` (case-insensitive) | caseIdColumn |
| 2 | Column name contains `activity`, `event`, `action`, `task` | activityColumn |
| 3 | Column parsed as ISO 8601 timestamp | timestampColumn |
| 4 | Low cardinality string column (< 50 distinct values) | objectTypeColumns |
| 5 | Remaining columns | attributeColumns |

If `ZaiService` is available and the heuristic result is ambiguous (score < 0.8),
`SchemaInferenceEngine` passes a sample to the LLM for semantic confirmation.

### Override

If inference is wrong, call `OcedBridge.inferSchema(rawData)` and examine the returned
`OcedSchema`, then construct a corrected `OcedSchema` manually and pass it to
`OcedBridge.convert(rawData, correctedSchema)`.

---

## OCEL2 v2.0 vs v1.0

YAWL uses v2.0. Key differences from v1.0:

| Feature | v1.0 | v2.0 |
|---|---|---|
| Object attributes | Static | Time-stamped (value history) |
| Event-object relationships | Unqualified | Qualified (qualifier string: "creates", "involves") |
| Object-object relationships | Not supported | Supported (O2O) |
| Schema versioning | Not supported | Required |

The `EventDataValidator` checks OCEL2 v2.0 compliance and reports violations
(missing required fields, type mismatches) vs warnings (non-standard qualifiers,
empty object lists).
