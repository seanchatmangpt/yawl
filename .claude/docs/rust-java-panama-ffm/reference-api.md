# Reference: rust4pm Panama FFM Bridge API

**Information-oriented reference for AI coding agents using rust4pm's Java-to-Rust FFM bridge.**

## Layer 1: Raw Bindings (rust4pm_h)

### Overview
`rust4pm_h` (package: `org.yawlfoundation.yawl.rust4pm.generated`) provides Panama Foreign Function and Memory (FFM) bindings to `librust4pm.so`. Do not use directly; call through `Rust4pmBridge` (Layer 2).

**Key behavior**:
- Class is `public final` with private constructor (static methods only)
- Library loading via system property `rust4pm.library.path` or default `./target/release/librust4pm.so`
- If library absent: all method calls throw `UnsupportedOperationException`
- When library present: static initializer runs layout assertion checks (CbC — correct-by-construction)

### System Property
| Property | Purpose | Default |
|----------|---------|---------|
| `rust4pm.library.path` | Full path to `librust4pm.so` | `./target/release/librust4pm.so` |

### Layout Assertions (Correct-by-Construction)
When library is loaded, static initializer calls `rust4pm_sizeof_*()` probes and asserts via `assertLayout()`:
- Verifies each hand-written `StructLayout.byteSize()` against actual Rust `sizeof()`
- Any divergence throws `AssertionError` at JVM startup (not silently during calls)
- Checked for all 8 struct types: `OcelLogHandle`, `ParseResult`, `OcelEventC`, `OcelEventsResult`, `OcelObjectC`, `OcelObjectsResult`, `DfgResultC`, `ConformanceResultC`

### Struct Layouts Reference

| Layout Name | Byte Size | Fields | Rust Equivalent | Notes |
|---|---|---|---|---|
| `OCEL_LOG_HANDLE_LAYOUT` | 8 | `ptr: ADDRESS` | `OcelLogHandle { ptr: *mut OcelLogInternal }` | 64-bit ADDRESS only |
| `PARSE_RESULT_LAYOUT` | 16 | `handle: OcelLogHandle(8)`, `error: ADDRESS(8)` | `ParseResult { handle: OcelLogHandle, error: *mut c_char }` | Nested struct layout |
| `OCEL_EVENT_C_LAYOUT` | 32 | `event_id: ADDRESS(8)`, `event_type: ADDRESS(8)`, `timestamp_ms: JAVA_LONG(8)`, `attr_count: JAVA_LONG(8)` | `OcelEventC { event_id: *const c_char, event_type: *const c_char, timestamp_ms: i64, attr_count: size_t }` | JAVA_LONG = i64; attr_count maps to size_t (u64) |
| `OCEL_EVENTS_RESULT_LAYOUT` | 24 | `events: ADDRESS(8)`, `count: JAVA_LONG(8)`, `error: ADDRESS(8)` | `OcelEventsResult { events: *const OcelEventC, count: size_t, error: *mut c_char }` | Pointer BORROWED from Rust; do not free separately |
| `OCEL_OBJECT_C_LAYOUT` | 16 | `object_id: ADDRESS(8)`, `object_type: ADDRESS(8)` | `OcelObjectC { object_id: *const c_char, object_type: *const c_char }` | Strings are borrowed C-strings from Rust |
| `OCEL_OBJECTS_RESULT_LAYOUT` | 24 | `objects: ADDRESS(8)`, `count: JAVA_LONG(8)`, `error: ADDRESS(8)` | `OcelObjectsResult { objects: *const OcelObjectC, count: size_t, error: *mut c_char }` | Pointer BORROWED from Rust |
| `DFG_RESULT_C_LAYOUT` | 16 | `json: ADDRESS(8)`, `error: ADDRESS(8)` | `DfgResultC { json: *mut c_char, error: *mut c_char }` | Both pointers are owned by Rust; must free via `rust4pm_dfg_free()` |
| `CONFORMANCE_RESULT_C_LAYOUT` | 24 | `fitness: JAVA_DOUBLE(8)`, `precision: JAVA_DOUBLE(8)`, `error: ADDRESS(8)` | `ConformanceResultC { fitness: f64, precision: f64, error: *mut c_char }` | Values in [0.0, 1.0] |

### VarHandles (Field Accessors)

#### ParseResult VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `PARSE_RESULT_HANDLE_PTR` | `handle.ptr` | ADDRESS | MemorySegment | 0 bytes |
| `PARSE_RESULT_ERROR` | `error` | ADDRESS | MemorySegment | 8 bytes |

#### OcelEventC VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `OCEL_EVENT_C_EVENT_ID` | `event_id` | ADDRESS | MemorySegment | 0 bytes |
| `OCEL_EVENT_C_EVENT_TYPE` | `event_type` | ADDRESS | MemorySegment | 8 bytes |
| `OCEL_EVENT_C_TIMESTAMP_MS` | `timestamp_ms` | JAVA_LONG | long | 16 bytes |
| `OCEL_EVENT_C_ATTR_COUNT` | `attr_count` | JAVA_LONG | long | 24 bytes |

#### OcelEventsResult VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `OCEL_EVENTS_RESULT_EVENTS` | `events` | ADDRESS | MemorySegment | 0 bytes |
| `OCEL_EVENTS_RESULT_COUNT` | `count` | JAVA_LONG | long | 8 bytes |
| `OCEL_EVENTS_RESULT_ERROR` | `error` | ADDRESS | MemorySegment | 16 bytes |

#### OcelObjectC VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `OCEL_OBJECT_C_OBJECT_ID` | `object_id` | ADDRESS | MemorySegment | 0 bytes |
| `OCEL_OBJECT_C_OBJECT_TYPE` | `object_type` | ADDRESS | MemorySegment | 8 bytes |

#### OcelObjectsResult VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `OCEL_OBJECTS_RESULT_OBJECTS` | `objects` | ADDRESS | MemorySegment | 0 bytes |
| `OCEL_OBJECTS_RESULT_COUNT` | `count` | JAVA_LONG | long | 8 bytes |
| `OCEL_OBJECTS_RESULT_ERROR` | `error` | ADDRESS | MemorySegment | 16 bytes |

#### DfgResultC VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `DFG_RESULT_JSON` | `json` | ADDRESS | MemorySegment | 0 bytes |
| `DFG_RESULT_ERROR` | `error` | ADDRESS | MemorySegment | 8 bytes |

#### ConformanceResultC VarHandles
| VarHandle | Target Field | Type | Returns | Field Offset |
|---|---|---|---|---|
| `CONFORMANCE_FITNESS` | `fitness` | JAVA_DOUBLE | double | 0 bytes |
| `CONFORMANCE_PRECISION` | `precision` | JAVA_DOUBLE | double | 8 bytes |
| `CONFORMANCE_ERROR` | `error` | ADDRESS | MemorySegment | 16 bytes |

### C Function Bindings

#### Parsing
| Signature | C Name | Return Type | Arena | Critical | Rust Equivalent |
|---|---|---|---|---|---|
| `rust4pm_parse_ocel2_json(SegmentAllocator allocator, MemorySegment json, long jsonLen)` | `rust4pm_parse_ocel2_json` | MemorySegment (ParseResult layout) | `allocator` | No | `pub extern "C" fn rust4pm_parse_ocel2_json(json: *const c_char, json_len: size_t) -> ParseResult` |

#### Event Log Operations
| Signature | C Name | Return Type | Arena | Critical | Rust Equivalent |
|---|---|---|---|---|---|
| `rust4pm_log_event_count(MemorySegment handlePtr)` | `rust4pm_log_event_count` | `long` | none | **Yes** | `pub extern "C" fn rust4pm_log_event_count(handle: OcelLogHandle) -> size_t` |
| `rust4pm_log_get_events(SegmentAllocator allocator, MemorySegment handlePtr)` | `rust4pm_log_get_events` | MemorySegment (OcelEventsResult layout) | `allocator` | No | `pub extern "C" fn rust4pm_log_get_events(handle: OcelLogHandle) -> OcelEventsResult` |
| `rust4pm_log_object_count(MemorySegment handlePtr)` | `rust4pm_log_object_count` | `long` | none | **Yes** | `pub extern "C" fn rust4pm_log_object_count(handle: OcelLogHandle) -> size_t` |
| `rust4pm_log_get_objects(SegmentAllocator allocator, MemorySegment handlePtr)` | `rust4pm_log_get_objects` | MemorySegment (OcelObjectsResult layout) | `allocator` | No | `pub extern "C" fn rust4pm_log_get_objects(handle: OcelLogHandle) -> OcelObjectsResult` |

#### Mining Algorithms
| Signature | C Name | Return Type | Arena | Critical | Rust Equivalent |
|---|---|---|---|---|---|
| `rust4pm_discover_dfg(SegmentAllocator allocator, MemorySegment handlePtr)` | `rust4pm_discover_dfg` | MemorySegment (DfgResultC layout) | `allocator` | No | `pub extern "C" fn rust4pm_discover_dfg(handle: OcelLogHandle) -> DfgResultC` |
| `rust4pm_check_conformance(SegmentAllocator allocator, MemorySegment handlePtr, MemorySegment pnmlSeg, long pnmlLen)` | `rust4pm_check_conformance` | MemorySegment (ConformanceResultC layout) | `allocator` | No | `pub extern "C" fn rust4pm_check_conformance(handle: OcelLogHandle, petri_net_pnml: *const c_char, pnml_len: size_t) -> ConformanceResultC` |

#### Memory Cleanup
| Signature | C Name | Return Type | Arena | Critical | Rust Equivalent |
|---|---|---|---|---|---|
| `rust4pm_log_free(MemorySegment handlePtr)` | `rust4pm_log_free` | `void` | none | **Yes** | `pub extern "C" fn rust4pm_log_free(handle: OcelLogHandle)` |
| `rust4pm_dfg_free(MemorySegment resultSeg)` | `rust4pm_dfg_free` | `void` | none | No | `pub extern "C" fn rust4pm_dfg_free(result: DfgResultC)` |
| `rust4pm_error_free(MemorySegment errorPtr)` | `rust4pm_error_free` | `void` | none | **Yes** | `pub extern "C" fn rust4pm_error_free(error: *mut c_char)` |

#### Sizeof Probes (Static Initializer Only)
| Signature | C Name | Returns |
|---|---|---|
| `sizeof_ocel_log_handle()` | `rust4pm_sizeof_ocel_log_handle` | `long` |
| `sizeof_parse_result()` | `rust4pm_sizeof_parse_result` | `long` |
| `sizeof_ocel_event_c()` | `rust4pm_sizeof_ocel_event_c` | `long` |
| `sizeof_ocel_events_result()` | `rust4pm_sizeof_ocel_events_result` | `long` |
| `sizeof_ocel_object_c()` | `rust4pm_sizeof_ocel_object_c` | `long` |
| `sizeof_ocel_objects_result()` | `rust4pm_sizeof_ocel_objects_result` | `long` |
| `sizeof_dfg_result_c()` | `rust4pm_sizeof_dfg_result_c` | `long` |
| `sizeof_conformance_result_c()` | `rust4pm_sizeof_conformance_result_c` | `long` |

### Method Calling Conventions

**Arena usage**:
- Methods accepting `SegmentAllocator allocator` parameter allocate return structures (ParseResult, OcelEventsResult, etc.) in the provided arena
- Allocator must be valid for the duration of struct usage
- Typical pattern: `Arena.ofConfined()` for single-threaded call, scoped with try-with-resources

**Critical linker option**:
- Methods marked **critical** use `Linker.Option.critical(true)` to avoid context switch overhead
- Used for high-frequency calls (`_log_event_count`, `_log_object_count`) and cleanup (`_log_free`, `_error_free`)

**Error handling via pointers**:
- Result structures contain `error: *mut c_char` field (ADDRESS)
- If error pointer is not NULL, call `rust4pm_error_free()` to deallocate error string
- Never access error pointer after freeing

---

## Layer 2: Bridge

### Rust4pmBridge

**Package**: `org.yawlfoundation.yawl.rust4pm.bridge`

**Class design**: `public final class Rust4pmBridge implements AutoCloseable`

#### Lifecycle
- **Constructor**: `public Rust4pmBridge()` — creates internal shared arena (`Arena.ofShared()`)
- **Usage**: try-with-resources recommended
- **Close behavior**: closes internal arena on `close()`
- **Thread safety**: all methods are thread-safe; uses shared arena for concurrent string allocations

#### Methods

| Method | Signature | Exceptions | Behavior |
|---|---|---|---|
| `parseOcel2Json` | `public OcelLogHandle parseOcel2Json(String json) throws ParseException` | `ParseException`, `UnsupportedOperationException` | Parses OCEL2 JSON string (UTF-8) into native OcelLog handle; caller MUST close returned handle |
| `arena` | `Arena arena()` | none | Returns internal shared arena (package-private, used by ProcessMiningEngine) |
| `close` | `public void close()` | none | Closes internal arena; should be called after all derived handles are closed |

#### Implementation Details
- `parseOcel2Json` uses `Arena.ofConfined()` for call-local allocations (JSON string)
- OcelLogHandle created with its own `Arena.ofShared()` for scoping derived segments
- If `LIBRARY` is empty (library not loaded), `rust4pm_h.requireLibrary()` throws `UnsupportedOperationException`

---

### OcelLogHandle

**Package**: `org.yawlfoundation.yawl.rust4pm.bridge`

**Class design**: `public final class OcelLogHandle implements AutoCloseable`

**Ownership model**:
- Wraps raw Rust pointer (`MemorySegment rawPtr` into `OcelLogInternal`)
- Valid only until `close()` is called
- Each handle owns a per-handle `Arena` (scoping derived view segments)

**Lifetime contract** (correct-by-construction):
- Events and objects views reinterpret borrowed Rust memory with handle's `ownedArena`
- When `close()` is called, `ownedArena.close()` invalidates all derived views
- Accessing view after handle close throws `IllegalStateException` (enforced by Panama)

**Idempotent close**: `close()` is safe to call multiple times (uses `AtomicBoolean` guard)

**Thread safety**: not thread-safe; do not share single handle across threads

#### Methods

| Method | Signature | Exceptions | Behavior | Copy? |
|---|---|---|---|---|
| `ptr` | `public MemorySegment ptr()` | none | Raw Rust pointer; used internally by ProcessMiningEngine | No |
| `eventCount` | `public int eventCount()` | none | Count of events in log (zero-copy, direct native call via critical linker option) | No |
| `objectCount` | `public int objectCount()` | none | Count of objects in log (zero-copy, direct native call via critical linker option) | No |
| `events` | `public OcelEventView events() throws ProcessMiningException` | `ProcessMiningException` | Zero-copy view of all events (borrowed from Rust, scoped to handle's arena) | No |
| `objects` | `public OcelObjectView objects() throws ProcessMiningException` | `ProcessMiningException` | Zero-copy view of all objects (borrowed from Rust, scoped to handle's arena) | No |
| `close` | `public void close()` | none | Idempotent; invokes `rust4pm_log_free()` then closes `ownedArena` | N/A |

#### Error Handling
- `events()` and `objects()` check returned error pointer; if not NULL, read error message, free pointer, throw `ProcessMiningException`

---

### OcelEventView

**Package**: `org.yawlfoundation.yawl.rust4pm.bridge`

**Class design**: `public final class OcelEventView implements AutoCloseable`

**Memory model**:
- Zero-copy view over Rust-owned `OcelEventC` array
- Backing memory BORROWED from parent `OcelLogHandle`'s `OcelLogInternal`
- Associated with handle's `ownedArena`; valid only while handle is open

**Lifetime contract**:
- Accessing after parent handle close throws `IllegalStateException` (Panama enforces)
- `close()` is logical only; does not free native memory (parent handle owns lifetime)

**Idempotent close**: safe to call multiple times

#### Methods

| Method | Signature | Exceptions | Behavior |
|---|---|---|---|
| `count` | `public int count()` | none | Number of events in view |
| `get` | `public OcelEvent get(int index)` | `IndexOutOfBoundsException`, `IllegalStateException` | Random access (O(1) offset arithmetic); materializes one `OcelEvent` from native memory via `OcelEvent.fromSegment()` |
| `stream` | `public Stream<OcelEvent> stream()` | `IllegalStateException` | Lazy stream; materializes events on-demand (no full-array copy); terminal operation checks arena liveness |
| `close` | `public void close()` | none | Logical close only; idempotent |

---

### OcelObjectView

**Package**: `org.yawlfoundation.yawl.rust4pm.bridge`

**Class design**: `public final class OcelObjectView implements AutoCloseable`

**Memory model**:
- Symmetric with `OcelEventView`
- Zero-copy view over Rust-owned `OcelObjectC` array
- BORROWED from parent `OcelLogHandle`; valid while handle is open

**Lifetime contract**: accessing after parent handle close throws `IllegalStateException`

**Idempotent close**: logical close only; idempotent

#### Methods

| Method | Signature | Exceptions | Behavior |
|---|---|---|---|
| `count` | `public int count()` | none | Number of objects in view |
| `get` | `public OcelObject get(int index)` | `IndexOutOfBoundsException`, `IllegalStateException` | Random access (O(1)); materializes one `OcelObject` from native memory via `OcelObject.fromSegment()` |
| `stream` | `public Stream<OcelObject> stream()` | `IllegalStateException` | Lazy stream; materializes objects on-demand; terminal operation checks arena liveness |
| `close` | `public void close()` | none | Logical close only; idempotent |

---

## Layer 2: Model Records

### OcelEvent

**Package**: `org.yawlfoundation.yawl.rust4pm.model`

**Type**: `public record OcelEvent(String eventId, String eventType, Instant timestamp, int attrCount)`

**Fields**:
- `eventId`: unique event identifier (from C-string)
- `eventType`: activity/event type name (from C-string)
- `timestamp`: event occurrence time (from `timestamp_ms` as milliseconds since epoch)
- `attrCount`: number of attributes (metadata count; attributes fetched on-demand if supported)

#### Construction
| Method | Signature | Behavior |
|---|---|---|
| `fromSegment` | `public static OcelEvent fromSegment(MemorySegment s)` | Reads one `OcelEventC` struct from native memory (zero-copy field reads); converts `timestamp_ms: i64` to `Instant.ofEpochMilli()`; reads C-strings with explicit `UTF-8` charset |

**Encoding guarantee**: C-strings are always UTF-8 from Rust; charset explicitly specified to match, avoiding platform-default inconsistency

---

### OcelObject

**Package**: `org.yawlfoundation.yawl.rust4pm.model`

**Type**: `public record OcelObject(String objectId, String objectType)`

**Fields**:
- `objectId`: unique object identifier (from C-string)
- `objectType`: object type name, e.g., "Order", "Item" (from C-string)

#### Construction
| Method | Signature | Behavior |
|---|---|---|
| `fromSegment` | `public static OcelObject fromSegment(MemorySegment s)` | Reads one `OcelObjectC` struct from native memory (zero-copy field reads); reads C-strings with explicit `UTF-8` charset |

---

## Layer 3: ProcessMiningEngine

**Package**: `org.yawlfoundation.yawl.processmining`

**Class design**: `public final class ProcessMiningEngine implements AutoCloseable`

**Design principle**: no Panama leaks — all public API is domain-oriented (no `MemorySegment`, `Arena`, `MethodHandle`)

**Lifecycle**: engine does not own bridge; bridge lifecycle must outlast engine

#### Constructor

| Method | Signature | Preconditions |
|---|---|---|
| Constructor | `public ProcessMiningEngine(Rust4pmBridge bridge)` | `bridge` must not be null (checked via `Objects.requireNonNull()`) |

#### Methods

| Method | Signature | Exceptions | Behavior |
|---|---|---|---|
| `parseOcel2Json` | `public OcelLogHandle parseOcel2Json(String json) throws ParseException` | `ParseException`, `UnsupportedOperationException` | Parses OCEL2 JSON (delegates to bridge) |
| `discoverDfg` | `public DirectlyFollowsGraph discoverDfg(OcelLogHandle log) throws ProcessMiningException` | `ProcessMiningException` | Discovers directly-follows graph from log; calls `rust4pm_discover_dfg()`, parses result JSON into `DirectlyFollowsGraph` |
| `checkConformance` | `public ConformanceReport checkConformance(OcelLogHandle log, String pnmlXml) throws ConformanceException` | `ConformanceException` | Token-based replay conformance check against Petri net in PNML XML format; returns `ConformanceReport` with `fitness`, `precision` ∈ [0.0, 1.0] |
| `parseAll` | `public List<OcelLogHandle> parseAll(List<String> jsonLogs) throws InterruptedException, ExecutionException` | `InterruptedException`, `ExecutionException` | Parallel parsing via `StructuredTaskScope` with virtual threads; `Joiner.awaitAllSuccessfulOrThrow()` cancels all tasks on first failure; returns list in input order |
| `parseXes` | `public OcelLogHandle parseXes(String xes) throws ParseException` | `UnsupportedOperationException` | **Not yet implemented**; throws `UnsupportedOperationException` with guidance to add `rust4pm_parse_xes()` to Rust lib |
| `discoverAlphaPpp` | `public PetriNet discoverAlphaPpp(OcelLogHandle log) throws ProcessMiningException` | `UnsupportedOperationException` | **Not yet implemented**; throws `UnsupportedOperationException` with guidance to add `rust4pm_discover_alpha_ppp()` to Rust lib |
| `computePerformanceStats` | `public PerformanceStats computePerformanceStats(OcelLogHandle log) throws ProcessMiningException` | `UnsupportedOperationException` | **Not yet implemented**; throws `UnsupportedOperationException` with guidance to add `rust4pm_compute_performance_stats()` to Rust lib |
| `close` | `public void close()` | none | No-op; bridge lifecycle managed by caller |

#### Concurrency Details
- **`parseAll` implementation**:
  - Uses `StructuredTaskScope.open()` with `Joiner.awaitAllSuccessfulOrThrow()`
  - Each JSON string parsed via `parseOcel2Json()` in a virtual thread
  - If any task fails: all in-flight tasks cancelled, first exception propagates
  - Empty input list returns immediately (no scope created)

#### Result Types (Domain Model)
- `DirectlyFollowsGraph`: contains `List<DfgNode>` and `List<DfgEdge>` (parsed from Rust JSON)
- `ConformanceReport`: contains `fitness: double`, `precision: double`, `eventCount: int`, and additional fields
- `PetriNet`, `PerformanceStats`: defined in model but methods not yet implemented

---

## Error Types

**Package**: `org.yawlfoundation.yawl.rust4pm.error`

### Exception Hierarchy
```
Throwable
├── Exception
│   └── RuntimeException
│       └── ProcessMiningException
│           ├── ParseException
│           └── ConformanceException
```

| Exception | Extends | Constructors | When Thrown |
|---|---|---|---|
| `ProcessMiningException` | `RuntimeException` | `ProcessMiningException(String msg)`, `ProcessMiningException(String msg, Throwable cause)` | General Rust operation failure (DFG discovery, events/objects queries, etc.) |
| `ParseException` | `ProcessMiningException` | Inherited | OCEL2 JSON parse error from Rust (`rust4pm_parse_ocel2_json` returns non-null error) |
| `ConformanceException` | `ProcessMiningException` | Inherited | Token-based replay conformance check fails from Rust (`rust4pm_check_conformance` returns non-null error) |

**Note**: All three are unchecked (extend `RuntimeException`); no throws clause needed in calling code (though may be caught)

---

## Struct Layout Reference (Adding New Bindings)

### ValueLayout Types and Rust Equivalents

| Java ValueLayout | Size (64-bit) | Rust Equivalent | Notes |
|---|---|---|---|
| `ADDRESS` | 8 bytes | `*const T`, `*mut T` | Pointer type; reinterpreted as `MemorySegment` in Panama |
| `JAVA_LONG` | 8 bytes | `i64`, `size_t` (on 64-bit) | Signed long; use for both `i64` and `size_t` (both u64 on 64-bit, but Panama doesn't distinguish) |
| `JAVA_DOUBLE` | 8 bytes | `f64` | IEEE 754 double precision |
| `JAVA_BYTE` | 1 byte | `i8` | Signed byte |

### Padding and Alignment Rules
- Struct fields laid out in declaration order (C `#[repr(C)]` semantics)
- No automatic padding inserted by Panama (developer must match hand-written layouts to Rust `#[repr(C)]` struct)
- For Rust struct padding: use `_padding: [u8; N]` field in Rust and corresponding `MemoryLayout.sequenceLayout(N, JAVA_BYTE)` in Java if needed

### 64-bit Address Size Assumption
- All pointers are 8 bytes (`ADDRESS` layout)
- `size_t` is 8 bytes (u64 on 64-bit systems)
- No support for 32-bit systems

### CbC Assertion Behavior
- When library loads, each struct layout is checked via `rust4pm_sizeof_*()` probe
- Example: `assertLayout("OcelEventC", OCEL_EVENT_C_LAYOUT, MH$sizeof_ocel_event_c)`
  1. Calls `MH$sizeof_ocel_event_c.invokeExact()` → calls `rust4pm_sizeof_ocel_event_c()` in Rust
  2. Gets Rust `sizeof(OcelEventC)`
  3. Compares against Java `OCEL_EVENT_C_LAYOUT.byteSize()`
  4. If mismatch: throws `AssertionError` with diagnostic message
  5. If exception during probe: wraps in `AssertionError` with "sizeof probe failed for ..." message

**Recovery**: If assertion fails, regenerate `rust4pm_h.java` from `rust4pm.h` via jextract (see script: `scripts/jextract-generate.sh`)

---

## Common Patterns

### Basic Parsing and Iteration
```java
try (var bridge = new Rust4pmBridge();
     var engine = new ProcessMiningEngine(bridge)) {
    try (OcelLogHandle log = engine.parseOcel2Json(json)) {
        try (OcelEventView events = log.events()) {
            events.stream()
                .forEach(e -> System.out.println(e.eventId()));
        }
    }
}
```

**Invariants**:
- Bridge must outlast engine
- Handle must outlast view
- Views are logical closes only (native memory freed by handle close)

### Error Handling
```java
try (OcelLogHandle log = engine.parseOcel2Json(json)) {
    // ...
} catch (ParseException e) {
    // JSON parse failed; e.getMessage() contains Rust error
    System.err.println("Parse error: " + e.getMessage());
}
```

### Parallel Parsing
```java
List<OcelLogHandle> logs = engine.parseAll(
    List.of(json1, json2, json3)
);
// All parsed concurrently via virtual threads
// If any fails: ExecutionException wraps first failure
```

---

## Library Loading and Troubleshooting

### Successful Load
- System property `rust4pm.library.path` is set, or
- `./target/release/librust4pm.so` exists (found automatically)
- Static initializer runs layout assertions; if all pass, library is ready
- Calling any method proceeds normally

### Failed Load (Library Absent)
- `rust4pm.library.path` not set and default `./target/release/librust4pm.so` not found
- Static initializer catches exception silently; `LIBRARY` field is `Optional.empty()`
- First call to any public method calls `rust4pm_h.requireLibrary()` → throws `UnsupportedOperationException` with guidance:
  ```
  rust4pm native library not found.
  Build:  bash scripts/build-rust4pm.sh
  Set:    -Drust4pm.library.path=/path/to/librust4pm.so
  Or put: librust4pm.so at ./target/release/librust4pm.so
  ```

### Layout Assertion Failure
- If `Rust sizeof != Java StructLayout.byteSize()`, static initializer throws `AssertionError`:
  ```
  Layout mismatch for OcelEventC: Rust sizeof=40 but Java StructLayout.byteSize()=32.
  Regenerate rust4pm_h.java from rust4pm.h.
  ```
- **Fix**: Regenerate via `bash scripts/jextract-generate.sh`

---

## Memory Ownership and Cleanup

### Ownership Model

| Struct | Owned By | Cleanup | When Valid |
|---|---|---|---|
| `OcelLogHandle` (Java wrapper) | `Rust4pmBridge` caller | `close()` (idempotent) | Until wrapper closed |
| Rust `OcelLogInternal` (via raw ptr) | Rust library | Freed by `rust4pm_log_free()` | While Java handle exists |
| Event array (borrowed) | Rust `OcelLogInternal` | Freed with log | While handle is open |
| Object array (borrowed) | Rust `OcelLogInternal` | Freed with log | While handle is open |
| Error strings | Rust library | Freed by `rust4pm_error_free()` | Only until freed |
| DFG result JSON | Rust library | Freed by `rust4pm_dfg_free()` | Only until freed |

**Critical rule**: BORROWED pointers (events, objects) must not be freed separately; they are owned by the Rust `OcelLogInternal` and freed when the log is closed.

---

## Version Information

- **JDK**: Java 19+ (Panama FFM preview in 19-21, final in Java 22+)
- **Build system**: Maven (see `pom.xml`)
- **Rust target**: `librust4pm.so` (Linux x86_64)
- **Generated bindings**: `rust4pm_h.java` — auto-generated via jextract; do not edit by hand

---

## Regenerating Bindings

**When to regenerate**:
- Rust C header `rust4pm.h` changes (new functions, struct fields)
- Java version upgraded significantly
- Panama FFM API changes

**How to regenerate**:
```bash
bash scripts/jextract-generate.sh
```

**Output**:
- Overwrites `src/main/java/org/yawlfoundation/yawl/rust4pm/generated/rust4pm_h.java`
- Preserves public API; all struct layouts and VarHandles updated
- Must verify against layout assertions at JVM startup
