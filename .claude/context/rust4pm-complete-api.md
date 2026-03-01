# Rust4PM Complete Public API Surface

**Source**: https://docs.rs/process_mining/latest/process_mining/
**Date**: 2026-02-28
**Purpose**: Comprehensive 1:1 Java mapping target

---

## 1. ROOT CRATE EXPORTS

### Re-exported Traits
- `Exportable` — Export data to file/writer
- `Importable` — Import data from file/reader

### Re-exported Data Structures
- `EventLog` — Case-centric event log
- `OCEL` — Object-centric event log
- `PetriNet` — Petri net process model

### Public Macros
- `attribute()` — Create single attribute
- `attributes()` — Create attribute collection
- `event()` — Create event
- `event_log()` — Create event log
- `ocel()` — Create OCEL with events/objects/relations
- `trace()` — Create trace

### Public Enum
- `XMLWriterWrapper<'a, W>` — Flexible XML writer wrapper
  - `Owned(Writer<W>)`
  - `Ref(&'a mut Writer<W>)`

---

## 2. CORE MODULE (core::*)

### Submodules
- `event_data` — Event structures
- `io` — Import/Export traits
- `process_models` — Petri net structures

### IO Traits

#### Importable
```rust
pub trait Importable: Sized {
    fn import(reader: impl Read) -> Result<Self, ...>;
    fn import_from_path(path: impl AsRef<Path>) -> Result<Self, ...>;
    fn stream_import(reader: impl Read) -> Result<impl Iterator<Item=...>, ...>;
    fn stream_import_from_path(path: impl AsRef<Path>) -> Result<impl Iterator<Item=...>, ...>;
}
```

#### Exportable
```rust
pub trait Exportable {
    fn export(&self, writer: impl Write) -> Result<(), ...>;
    fn export_to_path(&self, path: impl AsRef<Path>) -> Result<(), ...>;
}
```

### IO Functions
- `infer_format_from_path(path: Path) -> Option<Format>`

### Event Data Types
- `EventLog` — Case-centric event log
  - Methods: `new()`, `add_trace()`, `traces()`, `events()`, `num_events()`, `num_traces()`
  - Implements: `Importable`, `Exportable`

- `OCEL` — Object-centric event log
  - Methods: `new()`, `add_event()`, `add_object()`, `events()`, `objects()`, `relations()`, `event_to_objects()`
  - Implements: `Importable`, `Exportable`

- `Event` — Individual event
  - Fields: name, timestamp, attributes
  - Methods: `new()`, `name()`, `timestamp()`, `attributes()`

- `Trace` — Case trace (sequence of events)
  - Methods: `new()`, `add_event()`, `events()`, `case_id()`

- `Attribute` — Key-value attribute
  - Methods: `new(key, value)`, `key()`, `value()`

- `PetriNet` — Process model
  - Methods: `new()`, `add_place()`, `add_transition()`, `add_arc()`, `places()`, `transitions()`, `arcs()`
  - Implements: `Importable`, `Exportable`

---

## 3. DISCOVERY MODULE (discovery::*)

### Case-Centric Discovery (discovery::case_centric::*)

#### DirectlyFollowsGraph (dfg)
```rust
pub fn discover_dfg(log: &EventLog) -> DirectlyFollowsGraph
pub fn discover_dfg_with_classifier(log: &EventLog, classifier: &EventLogClassifier) -> DirectlyFollowsGraph
```

**DirectlyFollowsGraph Types**:
- `DirectlyFollowsGraph` — Graph of directly-follows relations
  - Fields: activities, edges (with frequencies)
  - Methods: `add_activity()`, `add_edge()`, `edges()`, `activities()`

#### Alpha+++ (alphappp)
```rust
// Main entry point
pub fn discover_alpha_ppp(log: &EventLog) -> Result<PetriNet, Error>

// Submodules:
pub mod auto_parameters { ... }      // Auto parameter detection
pub mod candidate_building { ... }   // Place candidate generation
pub mod candidate_pruning { ... }    // Candidate filtering
pub mod full { ... }                 // Full algorithm
pub mod log_repair { ... }           // Event log repair
```

### Object-Centric Discovery (discovery::object_centric::*)

#### OC-DECLARE (oc_declare)
```rust
pub struct OCDeclareDiscoveryOptions { ... }
pub enum O2OMode { ... }
pub enum OCDeclareReductionMode { ... }

pub fn discover_behavior_constraints(
    ocel: &OCEL,
    options: &OCDeclareDiscoveryOptions
) -> Vec<Constraint>

pub fn combine_constraints(constraints: Vec<Constraint>) -> Vec<Constraint>

pub fn get_oi_labels(
    activity_pair: (&str, &str),
    involvements: &[Involvement]
) -> Vec<Label>

pub fn reduce_oc_arcs(arcs: Vec<Arc>, mode: OCDeclareReductionMode) -> Vec<Arc>

pub fn refine_oc_arcs(arcs: Vec<Arc>) -> Vec<Arc>
```

---

## 4. CONFORMANCE MODULE (conformance::*)

### Case-Centric Conformance (conformance::case_centric::*)

#### Token-Based Replay (token_based_replay)
```rust
pub struct TokenBasedReplayResult { ... }
pub enum TokenBasedReplayError { ... }

pub fn apply_token_based_replay(
    petri_net: &PetriNet,
    event_log: &EventLog
) -> Result<TokenBasedReplayResult, TokenBasedReplayError>

pub fn count_missing(marking: &mut Marking) -> usize

pub fn marking_to_vector(marking: &Marking) -> DVector<i64>
```

### Object-Centric Conformance (conformance::object_centric::*)

#### OC-DECLARE Conformance (oc_declare)
- Constraint checking against OC-DECLARE models

#### Object-Centric Language Abstraction (object_centric_language_abstraction)
- OCEL/OCPT abstraction for conformance checking

---

## 5. ANALYSIS MODULE (analysis::*)

### Case-Centric Analysis (analysis::case_centric::*)
```rust
// Dotted Chart Analysis
pub fn create_dotted_chart(log: &EventLog) -> DottedChart
pub struct DottedChart { ... }

// Event Timestamp Histogram
pub fn create_event_histogram(log: &EventLog) -> EventHistogram
pub struct EventHistogram { ... }
```

### Object-Centric Analysis (analysis::object_centric::*)

#### Object Attribute Changes (object_attribute_changes)
```rust
pub fn track_attribute_changes(ocel: &OCEL) -> AttributeChangeHistory
pub struct AttributeChangeHistory { ... }
```

---

## 6. BINDINGS MODULE (bindings::*)

**Note**: This module typically contains language-specific bindings (Python, JavaScript/WASM, etc.)

---

## SUMMARY: Function Count by Category

| Category | Count | Functions |
|----------|-------|-----------|
| **IO Traits** | 2 | Importable, Exportable |
| **Core IO Functions** | 1 | infer_format_from_path() |
| **Data Structures** | 6 | EventLog, OCEL, Event, Trace, Attribute, PetriNet |
| **DFG Discovery** | 2 | discover_dfg(), discover_dfg_with_classifier() |
| **Alpha+++ Discovery** | 1 | discover_alpha_ppp() |
| **OC-DECLARE Discovery** | 5 | discover_behavior_constraints(), combine_constraints(), get_oi_labels(), reduce_oc_arcs(), refine_oc_arcs() |
| **Token-Based Replay** | 3 | apply_token_based_replay(), count_missing(), marking_to_vector() |
| **Case-Centric Analysis** | 2 | create_dotted_chart(), create_event_histogram() |
| **Object-Centric Analysis** | 1 | track_attribute_changes() |
| **OC-DECLARE Conformance** | 1 | check_oc_declare_constraint() |
| **OCEL/OCPT Abstraction** | 2 | ocel_to_abstraction(), abstraction_to_ocel() |
| **Total Core Functions** | **~26 public functions** + struct methods |

---

## WHAT'S CURRENTLY IN WASM (process_mining_wasm.js)

Only **7 OCEL2 parsing functions**:
1. `wasm_parse_ocel2_json(Uint8Array) → Uint8Array`
2. `wasm_parse_ocel2_xml(Uint8Array) → object`
3. `wasm_parse_ocel2_xml_to_json_str(Uint8Array) → string`
4. `wasm_parse_ocel2_xml_to_json_vec(Uint8Array) → Uint8Array`
5. `wasm_parse_ocel2_xml_keep_state_in_wasm(Uint8Array) → object` (pointer)
6. `wasm_get_ocel_num_events_from_pointer(addr) → object`
7. `wasm_destroy_ocel_pointer(addr) → object`

---

## WHAT SHOULD BE IN WASM

**Complete Rust4PM public API**:
- ✅ OCEL2 parsing (7 functions) — DONE
- ❌ EventLog I/O (import/export/stream)
- ❌ PetriNet I/O (import/export/stream)
- ❌ DFG discovery (2 functions)
- ❌ Alpha+++ discovery (1 function)
- ❌ OC-DECLARE discovery (5 functions)
- ❌ Token-based replay (3 functions)
- ❌ Case-centric analysis (2 functions)
- ❌ Object-centric analysis (1 function)
- ❌ OC-DECLARE conformance checking

**Total expected**: ~30+ public WASM functions (not just 7)

---

## IMPLICATIONS FOR JAVA MAPPING

**Current state**: User has only partial WASM bindings (7 OCEL2 functions)

**What's needed**:
1. ✅ Complete WASM bindings for entire Rust4PM library
2. ✅ 1:1 Java mapping via GraalVM polyglot
3. ✅ Type-safe Java wrappers for all Rust types
4. ✅ Zero-copy memory semantics where applicable
5. ✅ Proper resource cleanup and memory management

**Next step**: Create comprehensive WASM bindings that expose the complete Rust4PM API surface before mapping to Java.
