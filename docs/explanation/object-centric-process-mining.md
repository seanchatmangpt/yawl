# Object-Centric Process Mining

**Quadrant**: Explanation | **Audience**: Architects, Developers, Data Engineers

> **See also**: [Process Intelligence](process-intelligence.md) — how OCPM enables
> generative, predictive, and prescriptive AI in YAWL.
> [OCPM Integration How-To](../how-to/integration/ocpm-integration.md) — deploy
> and configure the Rust4PM + pm4py stack.

---

## Why Traditional Process Mining Falls Short

Classical process mining (XES / IEEE 5679-2016) assumes every event belongs to
exactly one **case**. Every order has one trace. Every patient has one trace.
This sounds natural until you encounter how organizations actually work:

| Challenge | Case-centric consequence |
|-----------|--------------------------|
| One order contains many line items | Must pick *one* as the case — you lose item-level visibility |
| Payments link to invoices *and* customers | Duplicated traces or lost cross-object dependencies |
| Resource A hands off to Resource B | Requires a separate social-network analysis step |
| Delays span department boundaries | Bottleneck is invisible inside a single-case view |

The result is **distorted models**, **inactionable insights**, and **post-mortem
analysis** rather than live diagnostics. Van der Aalst (2024) calls this the
"rigid case notion" problem.

---

## Object-Centric Event Data (OCED)

Object-Centric Process Mining (OCPM) lifts the single-case constraint. An event
can now reference **multiple objects of different types** simultaneously:

```
Event: "Item picked"
  ├── Object type: order    → order-42
  ├── Object type: item     → item-1337
  ├── Object type: resource → warehouse-clerk-07
  └── Object type: package  → pkg-891
```

This matches reality. The warehouse clerk didn't just pick "an order" — they
picked a specific item, placed it in a specific package, as part of a specific order.

OCED captures the full relational structure without lossy flattening.

---

## OCEL 2.0 — The Exchange Standard

**Object-Centric Event Log 2.0** (IEEE draft, arxiv:2403.01975) is the serialisation
format for OCED. It supports three physical formats:

| Format | Extension | Use Case |
|--------|-----------|----------|
| JSON | `.jsonocel` | REST APIs, human-readable |
| XML | `.xmlocel` | Legacy tooling, SAX streaming |
| SQLite | `.sqlite` | Large logs, SQL queries |

A minimal OCEL 2.0 JSON log:

```json
{
  "objectTypes": [
    {"name": "case",   "attributes": [{"name": "specId", "type": "string"}]},
    {"name": "task",   "attributes": [{"name": "taskName", "type": "string"}]},
    {"name": "resource","attributes": [{"name": "role", "type": "string"}]}
  ],
  "eventTypes": [
    {"name": "task-completed", "attributes": [{"name": "duration_ms", "type": "integer"}]}
  ],
  "objects": [
    {"id": "case-123", "type": "case",    "attributes": [{"time": "...", "name": "specId", "value": "OrderProcess"}]},
    {"id": "task-456", "type": "task",    "attributes": [{"time": "...", "name": "taskName", "value": "Approve"}]},
    {"id": "res-789",  "type": "resource","attributes": [{"time": "...", "name": "role", "value": "Manager"}]}
  ],
  "events": [
    {
      "id": "evt-001",
      "type": "task-completed",
      "time": "2026-02-25T10:15:00Z",
      "attributes": [{"name": "duration_ms", "value": 3200}],
      "relationships": [
        {"objectId": "case-123", "qualifier": "executes"},
        {"objectId": "task-456", "qualifier": "completes"},
        {"objectId": "res-789",  "qualifier": "performed-by"}
      ]
    }
  ]
}
```

Key advances over OCEL 1.0:
- **Object-to-object relationships** (e.g., package *contains* item)
- **Relationship qualifiers** (`"performed-by"`, `"executes"`, `"contains"`)
- **Evolving object attributes** — attribute value changes over time

---

## YAWL's Native OCEL 2.0 Fit

YAWL is unique among Business Process Management Suites: every `YWorkItem` already
natively references the four canonical OCEL 2.0 object types **without any
transformation**:

| OCEL 2.0 Object Type | YAWL Runtime Object | Relationship qualifier |
|----------------------|---------------------|------------------------|
| `case` | `YCase` (case ID) | `executes` |
| `task` | `YTask` (task definition) | `completes` |
| `specification` | `YSpecification` | `defined-by` |
| `resource` | Resourcing participant (role/user) | `performed-by` |

No other BPMS can make this claim. SAP, ServiceNow, Camunda — all require an ETL
step to reconstruct object linkages from relational tables. YAWL emits OCEL 2.0
natively because its data model *is* object-centric at the engine level.

### XES vs OCEL 2.0 — Side by Side

| Dimension | XES (case-centric) | OCEL 2.0 (object-centric) |
|-----------|-------------------|--------------------------|
| Case model | One trace per case | Events linked to N objects |
| Resource view | Attribute on event | First-class object type |
| Cross-process | Requires separate analysis | Built into event structure |
| Format | XML only | JSON / XML / SQLite |
| Discovery | Directly-Follows Graph | Object-Centric DFG (OC-DFG) |
| Conformance | Token replay (per trace) | Object-centric token replay |
| Tooling | ProM, pm4py | pm4py ≥ 2.7, Rust4PM, Celonis |

---

## YAWL's Export Pipeline

```
YEngine runtime
    │
    ▼
EventLogExporter          ──► XES XML (.xes)       → pm4py, ProM, Celonis
    │
    ├── Ocel2Exporter     ──► OCEL 2.0 JSON         → pm4py ≥ 2.7, Rust4PM
    │
    └── Rust4PmClient     ──► POST /export-ocel2    → Rust4PM service (10-40× faster)
```

The `ProcessMiningFacade` orchestrates all three paths:

```java
// Java 25 — immutable record result from the full pipeline
ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, user, pass);
ProcessMiningFacade.ProcessMiningReport report = facade.analyze(specId, net, true);

String xesXml     = report.xesXml();     // IEEE 5679-2016 XES
String ocelJson   = report.ocelJson();   // OCEL 2.0 JSON
int    traceCount = report.traceCount(); // cases analysed
```

The `report` is a **Java 25 record** — immutable, naturally serializable, thread-safe.

---

## OCPM Discovery with pm4py

Once you have an OCEL 2.0 export from YAWL, standard pm4py calls discover
object-centric process models:

```python
import pm4py

ocel = pm4py.read_ocel2("yawl-export.jsonocel")   # or read_ocel2_xml / read_ocel2_sqlite

# Object-Centric Directly-Follows Graph
ocdfg = pm4py.discover_ocdfg(ocel)
pm4py.view_ocdfg(ocdfg, annotation="frequency")

# Object-Centric Petri Net (van der Aalst & Berti, 2020)
ocpn = pm4py.discover_oc_petri_net(ocel)
pm4py.view_ocpn(ocpn)

# Object interaction graph — which object types interact?
obj_graph = pm4py.ocel.discover_objects_graph(ocel, graph_type="object_interaction")
```

For **large logs** (> 100K events), use `rustxes` for 10-40× faster import:

```python
import rustxes   # pip install rustxes
ocel = rustxes.read_ocel2_json("yawl-export.jsonocel")
```

---

## What OCPM Reveals That XES Cannot

Running OCPM on a YAWL order-processing workflow reveals insights invisible in XES:

1. **Convergence**: A package is assembled from items belonging to different orders —
   causing unexpected waiting time. XES shows "package delay"; OCPM shows *why*.

2. **Divergence**: One approval task spawns five parallel item-processing tasks —
   the approval appears as a bottleneck in XES; OCPM shows it is actually the
   downstream fan-out that is under-resourced.

3. **Object lifecycle compliance**: OCEL 2.0 tracks that an `item` object must
   follow the sequence `received → inspected → approved → shipped`. Violations
   (items shipped before inspection) are conformance failures at the object level,
   not the case level.

---

## Further Reading

- [Process Intelligence](process-intelligence.md) — how OCPM enables generative,
  predictive, and prescriptive AI
- [OCPM Integration How-To](../how-to/integration/ocpm-integration.md) — deploy
  Rust4PM + pm4py, export OCEL 2.0 from YAWL
- [Process Mining Enhancement Plan](process-mining.md) — roadmap for advanced
  discovery algorithms and streaming export
- Van der Aalst, W.M.P. (2024). "No AI Without PI!" — _ICPM Keynote_
- OCEL 2.0 specification: arxiv.org/abs/2403.01975
- Rust4PM (BPM 2024): ceur-ws.org/Vol-3758/paper-16.pdf
