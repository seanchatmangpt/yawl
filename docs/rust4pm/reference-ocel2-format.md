# OCEL2 Format Reference

**Document Type**: Diataxis Reference
**Audience**: Process analysts, data engineers integrating with rust4pm
**Last Updated**: 2026-02-28

## Overview

The Object-Centric Event Log version 2 (OCEL2) is a JSON-based format for capturing event data in multi-object processes. rust4pm supports lenient parsing: unknown fields are silently ignored, and all top-level fields are optional.

**Minimal valid OCEL2 document**:
```json
{"events": []}
```

---

## Top-Level Structure

The OCEL2 JSON document contains four optional sections:

```json
{
  "objectTypes": [
    {"name": "order", "attributes": [...]},
    ...
  ],
  "eventTypes": [
    {"name": "place", "attributes": [...]},
    ...
  ],
  "objects": [
    {"id": "o1", "type": "order", "attributes": {...}},
    ...
  ],
  "events": [
    {"id": "e1", "type": "place", "time": "2024-01-15T10:30:00Z", ...},
    ...
  ]
}
```

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `objectTypes` | Array | No | Yes | Schema definitions for object types referenced in the log |
| `eventTypes` | Array | No | Yes | Schema definitions for event types in the log |
| `objects` | Array | No | Yes | Object instances involved in the process |
| `events` | Array | Yes | No | Event instances; only field that must exist (can be empty) |

---

## objectTypes Array

Defines the schema for object types in your process. Each entry describes what attributes can appear on objects of that type.

**Entry structure**:
```json
{
  "name": "order",
  "attributes": [
    {"name": "amount", "type": "float"},
    {"name": "customer_id", "type": "string"},
    {"name": "priority", "type": "string"}
  ]
}
```

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `name` | String | Yes | No | Unique identifier for the object type (e.g., `"order"`, `"item"`) |
| `attributes` | Array | No | Yes | List of attribute schema definitions for this type |

### Attribute Entry

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `name` | String | Yes | No | Attribute name (e.g., `"amount"`, `"status"`) |
| `type` | String | Yes | No | Data type hint: `"string"`, `"integer"`, `"float"`, `"boolean"`, `"datetime"` |

---

## eventTypes Array

Defines the schema for event types in your process. Each entry describes what attributes can appear on events of that type.

**Entry structure**:
```json
{
  "name": "place_order",
  "attributes": [
    {"name": "timestamp_received", "type": "datetime"},
    {"name": "processing_time_sec", "type": "integer"}
  ]
}
```

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `name` | String | Yes | No | Unique identifier for the event type (e.g., `"place"`, `"ship"`) |
| `attributes` | Array | No | Yes | List of attribute schema definitions for this event type |

### Attribute Entry

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `name` | String | Yes | No | Attribute name (e.g., `"cost"`, `"user_id"`) |
| `type` | String | Yes | No | Data type hint: `"string"`, `"integer"`, `"float"`, `"boolean"`, `"datetime"` |

---

## objects Array

Describes the actual object instances that participate in your process.

**Entry structure**:
```json
{
  "id": "order_2024_001",
  "type": "order",
  "attributes": {
    "amount": 1250.50,
    "customer_id": "cust_789",
    "priority": "high"
  }
}
```

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `id` | String | Yes | No | Unique identifier for this object instance |
| `type` | String | Yes | No | The object type (should match a `name` in `objectTypes`) |
| `attributes` | Object | No | Yes | Key-value pairs storing attribute data; keys should match attribute names defined in `objectTypes` |

### Attributes Field

The `attributes` object is a free-form key-value map. Values can be strings, numbers, booleans, or null.

---

## events Array

The core of OCEL2: a sequence of events that occurred during the process. Each event must have a unique ID and timestamp, and can relate to multiple objects.

**Entry structure**:
```json
{
  "id": "evt_12345",
  "type": "place",
  "time": "2024-01-15T10:30:00Z",
  "attributes": {
    "resource": "order_desk_1",
    "cost": 25.00
  },
  "relationships": [
    {
      "objectId": "order_2024_001",
      "qualifier": "primary"
    },
    {
      "objectId": "item_ABC123",
      "qualifier": "secondary"
    }
  ]
}
```

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `id` | String | Yes | No | Unique identifier for this event (must be unique across all events in the log) |
| `type` | String | Yes | No | The event type (should match a `name` in `eventTypes`) |
| `time` | String (ISO 8601) | Yes | No | When the event occurred (see time format section below) |
| `attributes` | Object | No | Yes | Key-value pairs storing event-specific data |
| `relationships` | Array | No | Yes | List of objects involved in this event |

### Relationships Entry

Each entry in the `relationships` array links an event to one or more objects it involves:

| Field | Type | Required | Optional | Description |
|-------|------|----------|----------|-------------|
| `objectId` | String | Yes | No | The ID of an object involved in this event (should match an `id` in `objects`) |
| `qualifier` | String | No | Yes | Optional role or relationship type (e.g., `"primary"`, `"secondary"`, `"recipient"`) |

---

## Time Format

Events must include an ISO 8601 formatted timestamp with timezone information.

**Accepted formats**:
- UTC with `Z` suffix: `"2024-01-15T10:30:00Z"`
- With explicit offset: `"2024-01-15T10:30:00+02:00"`
- With minutes offset: `"2024-01-15T10:30:00-05:30"`

**Precision**: Timestamps can include milliseconds:
- `"2024-01-15T10:30:00.123Z"`
- `"2024-01-15T10:30:00.123456+00:00"`

Events without valid timestamps will be rejected by `rust4pm_parse_ocel2_json()`.

---

## Complete Working Example

A full order-to-cash process demonstrating all elements:

```json
{
  "objectTypes": [
    {
      "name": "order",
      "attributes": [
        {"name": "total_amount", "type": "float"},
        {"name": "customer_id", "type": "string"},
        {"name": "priority", "type": "string"}
      ]
    },
    {
      "name": "item",
      "attributes": [
        {"name": "sku", "type": "string"},
        {"name": "quantity", "type": "integer"},
        {"name": "unit_price", "type": "float"}
      ]
    },
    {
      "name": "shipment",
      "attributes": [
        {"name": "carrier", "type": "string"},
        {"name": "tracking_number", "type": "string"},
        {"name": "weight_kg", "type": "float"}
      ]
    }
  ],
  "eventTypes": [
    {
      "name": "place",
      "attributes": [
        {"name": "channel", "type": "string"},
        {"name": "operator", "type": "string"}
      ]
    },
    {
      "name": "pick",
      "attributes": [
        {"name": "warehouse_zone", "type": "string"},
        {"name": "picker_id", "type": "string"}
      ]
    },
    {
      "name": "pack",
      "attributes": [
        {"name": "packing_station", "type": "string"},
        {"name": "package_weight", "type": "float"}
      ]
    },
    {
      "name": "ship",
      "attributes": [
        {"name": "carrier_name", "type": "string"},
        {"name": "shipping_cost", "type": "float"}
      ]
    },
    {
      "name": "deliver",
      "attributes": [
        {"name": "delivery_address", "type": "string"},
        {"name": "recipient_name", "type": "string"}
      ]
    }
  ],
  "objects": [
    {
      "id": "order_2024_001",
      "type": "order",
      "attributes": {
        "total_amount": 1250.50,
        "customer_id": "cust_789",
        "priority": "high"
      }
    },
    {
      "id": "item_ABC123",
      "type": "item",
      "attributes": {
        "sku": "PROD-001",
        "quantity": 2,
        "unit_price": 625.25
      }
    },
    {
      "id": "shipment_SHP_2024_001",
      "type": "shipment",
      "attributes": {
        "carrier": "FedEx",
        "tracking_number": "794613852963",
        "weight_kg": 5.2
      }
    }
  ],
  "events": [
    {
      "id": "evt_001",
      "type": "place",
      "time": "2024-01-15T10:30:00Z",
      "attributes": {
        "channel": "web",
        "operator": "system"
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        }
      ]
    },
    {
      "id": "evt_002",
      "type": "pick",
      "time": "2024-01-15T11:15:00Z",
      "attributes": {
        "warehouse_zone": "A3",
        "picker_id": "picker_042"
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        },
        {
          "objectId": "item_ABC123",
          "qualifier": "secondary"
        }
      ]
    },
    {
      "id": "evt_003",
      "type": "pack",
      "time": "2024-01-15T12:00:00Z",
      "attributes": {
        "packing_station": "PS_2",
        "package_weight": 5.5
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        },
        {
          "objectId": "item_ABC123",
          "qualifier": "secondary"
        }
      ]
    },
    {
      "id": "evt_004",
      "type": "ship",
      "time": "2024-01-15T14:45:00Z",
      "attributes": {
        "carrier_name": "FedEx",
        "shipping_cost": 45.99
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        },
        {
          "objectId": "shipment_SHP_2024_001",
          "qualifier": "primary"
        }
      ]
    },
    {
      "id": "evt_005",
      "type": "deliver",
      "time": "2024-01-17T16:20:00Z",
      "attributes": {
        "delivery_address": "123 Oak Street, Springfield, IL 62701",
        "recipient_name": "Jane Doe"
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        },
        {
          "objectId": "shipment_SHP_2024_001",
          "qualifier": "primary"
        }
      ]
    },
    {
      "id": "evt_006",
      "type": "place",
      "time": "2024-01-17T18:00:00Z",
      "attributes": {
        "channel": "return",
        "operator": "system"
      },
      "relationships": [
        {
          "objectId": "order_2024_001",
          "qualifier": "primary"
        },
        {
          "objectId": "item_ABC123",
          "qualifier": "secondary"
        }
      ]
    }
  ]
}
```

---

## PNML Format for Conformance Checking

The `rust4pm_check_conformance()` function accepts Petri net definitions in PNML format (Petri Net Markup Language). Use PNML to specify the expected process model for validating event logs against.

### Minimal PNML Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<pnml xmlns="http://www.pnml.org/version-2009/grammar/pnml">
  <net id="net1" type="http://www.pnml.org/version-2009/grammar/pnmlcoremodel">
    <page id="p1">
      <!-- Places: states in the process -->
      <place id="start">
        <name><text>start</text></name>
        <initialMarking><text>1</text></initialMarking>
      </place>
      <place id="end">
        <name><text>end</text></name>
      </place>

      <!-- Transitions: activities/events -->
      <transition id="t_place">
        <name><text>place</text></name>
      </transition>
      <transition id="t_ship">
        <name><text>ship</text></name>
      </transition>

      <!-- Arcs: connections between places and transitions -->
      <arc id="a1" source="start" target="t_place"/>
      <arc id="a2" source="t_place" target="end"/>
      <arc id="a3" source="end" target="t_ship"/>
    </page>
  </net>
</pnml>
```

### Key Components

| Component | XML Element | Description | Required |
|-----------|-------------|-------------|----------|
| **Root** | `<pnml>` | Top-level container | Yes |
| **Net** | `<net id="..." type="...">` | The Petri net definition | Yes |
| **Page** | `<page id="...">` | Container for places, transitions, arcs | Yes |
| **Place** | `<place id="...">` | A state/condition in the process | Yes (at least 2) |
| **Transition** | `<transition id="...">` | An activity/event name | Yes (at least 1) |
| **Arc** | `<arc source="..." target="...">` | Connection from place to transition or vice versa | Yes (at least 1) |

### Place Definitions

Each place represents a state in the process:

```xml
<place id="p_after_pick">
  <name><text>after_pick</text></name>
  <initialMarking><text>0</text></initialMarking>
</place>
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier for the place |
| `<name><text>` | String | Human-readable name (optional) |
| `<initialMarking><text>` | Integer | Starting token count (0 or 1; omit for 0) |

### Transition Definitions

Transition labels **MUST exactly match** event type names in your OCEL2 log:

```xml
<transition id="t_place">
  <name><text>place</text></name>
</transition>
<transition id="t_pick">
  <name><text>pick</text></name>
</transition>
```

rust4pm maps the text inside `<name><text>...</text></name>` to the `"type"` field in OCEL2 events.

### Arc Definitions

Arcs connect places to transitions (or transitions to places):

```xml
<!-- Place to transition (input) -->
<arc id="arc1" source="start" target="t_place"/>

<!-- Transition to place (output) -->
<arc id="arc2" source="t_place" target="packing"/>
```

| Attribute | Type | Description |
|-----------|------|-------------|
| `id` | String | Unique arc identifier |
| `source` | String | ID of the source place or transition |
| `target` | String | ID of the target transition or place |

### Complete PNML Example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<pnml xmlns="http://www.pnml.org/version-2009/grammar/pnml">
  <net id="order_to_cash" type="http://www.pnml.org/version-2009/grammar/pnmlcoremodel">
    <page id="p1">
      <place id="p_start">
        <name><text>Order Received</text></name>
        <initialMarking><text>1</text></initialMarking>
      </place>
      <place id="p_after_place">
        <name><text>Order Placed</text></name>
      </place>
      <place id="p_after_pick">
        <name><text>Items Picked</text></name>
      </place>
      <place id="p_after_pack">
        <name><text>Items Packed</text></name>
      </place>
      <place id="p_after_ship">
        <name><text>Shipment Sent</text></name>
      </place>
      <place id="p_end">
        <name><text>Delivered</text></name>
      </place>

      <transition id="t_place">
        <name><text>place</text></name>
      </transition>
      <transition id="t_pick">
        <name><text>pick</text></name>
      </transition>
      <transition id="t_pack">
        <name><text>pack</text></name>
      </transition>
      <transition id="t_ship">
        <name><text>ship</text></name>
      </transition>
      <transition id="t_deliver">
        <name><text>deliver</text></name>
      </transition>

      <arc id="a1" source="p_start" target="t_place"/>
      <arc id="a2" source="t_place" target="p_after_place"/>
      <arc id="a3" source="p_after_place" target="t_pick"/>
      <arc id="a4" source="t_pick" target="p_after_pick"/>
      <arc id="a5" source="p_after_pick" target="t_pack"/>
      <arc id="a6" source="t_pack" target="p_after_pack"/>
      <arc id="a7" source="p_after_pack" target="t_ship"/>
      <arc id="a8" source="t_ship" target="p_after_ship"/>
      <arc id="a9" source="p_after_ship" target="t_deliver"/>
      <arc id="a10" source="t_deliver" target="p_end"/>
    </page>
  </net>
</pnml>
```

---

## Error Messages from rust4pm_parse_ocel2_json()

When parsing an invalid OCEL2 JSON document, rust4pm returns specific error messages:

### Missing or Invalid ID

**Condition**: An event or object lacks a required `id` field
**Error**:
```
ParseError: Event at index 2 missing required field 'id'
```

**Resolution**: Ensure every entry in `events` and `objects` has a unique, non-empty `id` field.

### Invalid Timestamp Format

**Condition**: Event `time` field is not a valid ISO 8601 datetime with timezone
**Error**:
```
ParseError: Event 'evt_001' has invalid timestamp format: '2024-01-15 10:30:00'
  Expected: ISO 8601 with timezone (e.g., '2024-01-15T10:30:00Z')
```

**Resolution**: Use ISO 8601 format with timezone: `"2024-01-15T10:30:00Z"` or `"2024-01-15T10:30:00+02:00"`

### Duplicate Event IDs

**Condition**: Two or more events share the same `id`
**Error**:
```
ParseError: Duplicate event ID 'evt_042' found at indices 3 and 7
```

**Resolution**: Ensure all event IDs are unique across the entire `events` array.

### Malformed JSON

**Condition**: The document is not valid JSON
**Error**:
```
ParseError: Invalid JSON syntax at line 12, column 5: unexpected character '}'
```

**Resolution**: Validate JSON syntax using a JSON linter or online validator.

### Missing Required Top-Level Field

**Condition**: Although `events` is the only technically required field, omitting essential structure can cause issues
**Error**:
```
ParseWarning: No 'events' array found; treating as empty log
```

**Resolution**: Always include an `events` array, even if empty: `{"events": []}`

---

## Lenient Parsing Rules

rust4pm applies these rules when processing OCEL2 documents:

1. **Unknown fields are ignored**: Extra fields in objects, events, or type definitions do not cause errors.
2. **Null attributes are allowed**: Attribute values can be `null`.
3. **Type hints are not enforced**: A string attribute can contain numeric values; type hints are schema documentation only.
4. **Missing optional fields are ignored**: Omitting `objectTypes`, `eventTypes`, or `objects` is valid.
5. **Relationship qualifiers are optional**: An object can be related without a `qualifier` field.
6. **Empty arrays are valid**: `{"events": []}` is a valid log.

**Example of lenient parsing**:
```json
{
  "events": [
    {
      "id": "evt_1",
      "type": "process",
      "time": "2024-01-15T10:30:00Z",
      "custom_field": "ignored by parser",
      "attributes": {
        "extra_data": 12345
      },
      "relationships": [
        {"objectId": "obj_1"}
      ]
    }
  ],
  "unknown_section": "also ignored"
}
```

This document parses successfully; unknown fields are silently discarded.

---

## Integration Checklist

Before passing OCEL2 data to rust4pm functions:

- [ ] All events have unique `id` fields
- [ ] All events have ISO 8601 timestamps with timezone
- [ ] All transition names in PNML match event types in OCEL2
- [ ] Event type names do not have leading/trailing whitespace
- [ ] Object IDs referenced in relationships exist in the `objects` array
- [ ] JSON is valid (check with `jq` or a JSON validator)
- [ ] Timestamps are in chronological order (recommended, not required)

---

## See Also

- **YAWL Process Definition**: See `docs/rust4pm/yawl-integration.md`
- **rust4pm API Reference**: See `docs/rust4pm/api-reference.md`
- **Conformance Checking**: See `docs/rust4pm/conformance-checking.md`
- **PNML Standard**: http://www.pnml.org/
