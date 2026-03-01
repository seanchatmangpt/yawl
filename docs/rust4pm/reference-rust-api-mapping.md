# Rust ↔ Java API Mapping Reference

Maps the `process_mining` Rust crate (v0.5.2, [`docs.rs/process_mining`](https://docs.rs/process_mining/latest/process_mining/)) to the YAWL rust4pm Java bridge. All algorithms come from the same Rust implementation; this document explains the naming adaptations.

## Core Types

| Rust (`process_mining`) | Java (`org.yawlfoundation.yawl.rust4pm`) | Notes |
|------------------------|------------------------------------------|-------|
| `EventLog` | `OcelLogHandle` | Opaque handle to Rust-owned heap data via Panama FFM. Java never copies the event array. |
| `OCEL` | OCEL2 JSON string input | Object-centric event log, parsed by Rust from JSON. |
| `Event` | `OcelEvent` | Immutable record: `(eventId, eventType, timestamp, attrCount)`. |
| `Attribute` | `OcelValue` (sealed interface) | `StringValue`, `IntValue`, `FloatValue`, `Timestamp` permit types. |
| `DFG` | `DirectlyFollowsGraph` | Record: `(nodes: List<DfgNode>, edges: List<DfgEdge>)`. |
| `PetriNet` | `ProcessModel.PetriNet` | Sealed record with `places`, `transitions`, `pnmlJson`. |
| `ProcessTree` | `ProcessModel.ProcessTree` | Sealed record with `treeJson`. |
| `Trace` | _No direct equivalent_ | OCEL2 is object-centric; per-object traces are derived internally during conformance replay. |

## Discovery Algorithms

| Rust | Java method | Module mapping |
|------|-------------|---------------|
| `discovery::case_centric::dfg` | `ProcessMiningEngine.discoverDfg(OcelLogHandle)` | Discovers DFG from OCEL2 event relationships. Returns materialized `DirectlyFollowsGraph` record. |
| `discovery::case_centric::alphappp` | _Not exposed in this release_ | Alpha+++ discovery. Present in `rust/yawl-native/src/pm.rs` but not in rust4pm cdylib API. |
| `discovery::object_centric::oc_declare` | _Not exposed_ | OC-DECLARE constraint discovery. |

## Conformance Checking

| Rust | Java method | Notes |
|------|-------------|-------|
| `conformance::case_centric` (token-based replay) | `ProcessMiningEngine.checkConformance(OcelLogHandle, String pnml)` | Accepts PNML string. Returns `ConformanceReport` with `fitness` and `precision` fields. |
| `conformance::object_centric` | _Not exposed in this release_ | |

## Import / Export

| Rust | Java equivalent | Notes |
|------|-----------------|-------|
| `Importable` trait → `OCEL::from_json(str)` | `ProcessMiningEngine.parseOcel2Json(String)` | OCEL2 JSON format only. |
| `Importable` → OCEL XML | _Not supported_ | |
| `Importable` → OCEL SQLite / DuckDB | _Not supported_ | |
| `Exportable` trait → PNML | Input-only in Java (pass PNML string to `checkConformance`) | |
| `Exportable` → SVG/PNG (Graphviz) | _Not supported_ | |

## Module Organization Comparison

```
process_mining (Rust)          →   org.yawlfoundation.yawl.rust4pm (Java)
├── core/                      →   model/ (OcelEvent, DirectlyFollowsGraph, etc.)
├── discovery/                 →   ProcessMiningEngine.discoverDfg()
├── conformance/               →   ProcessMiningEngine.checkConformance()
├── analysis/                  →   (not exposed)
└── bindings/                  →   generated/rust4pm_h.java (Layer 1 FFM bindings)
                               →   bridge/ (Layer 2 Arena management)
                               →   processmining/ (Layer 3 domain API)
```

## Naming Conventions

| Pattern | Rust convention | Java adaptation |
|---------|-----------------|-----------------|
| Type names | `EventLog`, `DFG`, `OCEL` (screaming/mixed) | `OcelLogHandle`, `DirectlyFollowsGraph`, `OcelEvent` (CamelCase, descriptive) |
| Function names | `discover_dfg()`, `token_based_replay()` | `discoverDfg()`, `checkConformance()` (camelCase, intent-revealing) |
| Error handling | `Result<T, E>` with `?` propagation | Checked exceptions: `ParseException`, `ConformanceException` |
| Memory management | Rust ownership + borrow checker | `AutoCloseable` + `Arena`; explicit `close()` |
| Import | `Importable` trait, `OCEL::from_json()` | `parseOcel2Json(String)` on `ProcessMiningEngine` |
| Traits | `Importable`, `Exportable` | Interfaces: `AutoCloseable` only (sealed types used for model variants) |

## Feature Coverage Matrix

| Feature | Rust crate | Java bridge | Gap |
|---------|-----------|-------------|-----|
| OCEL2 JSON parsing | ✅ | ✅ | — |
| OCEL2 XML parsing | ✅ | ❌ | Not in cdylib |
| OCEL2 SQLite | ✅ | ❌ | Not in cdylib |
| OCEL2 DuckDB | ✅ | ❌ | Not in cdylib |
| XES (case-centric) parsing | ✅ | ❌ | Use yawl-native for XES |
| DFG discovery | ✅ | ✅ | — |
| Alpha+++ discovery | ✅ | ❌ | Not exported from cdylib |
| Token-based replay | ✅ | ✅ | — |
| Alignment-based conformance | ✅ | ❌ | Not in cdylib |
| OC-DECLARE constraints | ✅ | ❌ | Not in cdylib |
| PNML import | ✅ | ✅ (input only) | — |
| SVG/PNG visualization | ✅ | ❌ | Not in cdylib |
| Zero-copy event access | N/A (owned) | ✅ `OcelEventView.get(i)` | Java-only advantage |
| Parallel log parsing | ✅ | ✅ `parseAll()` | — |

## Algorithm Notes

### DFG Discovery
The Rust `discovery::case_centric::dfg` discovers a directly-follows graph by counting consecutive event pairs per case/trace. For OCEL2 (object-centric), events are grouped per object via the `relationships` array before applying the DFG algorithm. The Java `discoverDfg()` returns a materialized `DirectlyFollowsGraph` record with `DfgNode` and `DfgEdge` lists.

### Token-Based Replay (Conformance)
The Rust `conformance::case_centric` implements token-based replay as described in: van der Aalst, W.M.P. (2016). *Process Mining: Data Science in Action*. The algorithm fires transitions and counts missing/remaining tokens. Java's `checkConformance()` returns a `ConformanceReport` with `fitness` (0.0–1.0) and `precision` (0.0–1.0). Perfect conformance: `fitness == 1.0 && precision == 1.0`.

## References

- Rust crate source: [github.com/aarkue/rust4pm](https://github.com/aarkue/rust4pm)
- Rust API docs: [docs.rs/process_mining/latest/process_mining/](https://docs.rs/process_mining/latest/process_mining/)
- OCEL2 Standard: IEEE 1849.1
- Panama FFM: JEP 454
- Token-based replay: van der Aalst (2016), *Process Mining: Data Science in Action*
