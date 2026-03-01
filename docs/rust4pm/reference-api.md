# rust4pm API Reference

Complete API documentation for the YAWL rust4pm Panama FFM bridge.

## Overview

The rust4pm bridge provides three layers of abstraction:

- **Layer 1** (rust4pm_h): Low-level C function bindings via Panama FFM
- **Layer 2**: Type-safe models, error handling, and memory management
- **Layer 3** (ProcessMiningEngine): High-level process mining operations

Start with Layer 3 for most use cases. Use Layer 2 for custom analysis workflows. Layer 1 is internal API.

---

## Layer 3: ProcessMiningEngine

High-level API for process mining and conformance checking.

### Class: `ProcessMiningEngine`

Package: `org.yawlfoundation.yawl.rust4pm.processmining`

Orchestrates process mining operations. Owns the Rust4pmBridge lifecycle.

#### Constructor

```java
ProcessMiningEngine(Rust4pmBridge bridge)
```

Creates engine with pre-initialized bridge.

#### Methods

##### parseOcel2Json

```java
OcelLogHandle parseOcel2Json(String json)
    throws ParseException
```

Parse OCEL2 JSON string into a log handle.

**Parameters:**
- `json` — Valid OCEL2 JSON (string)

**Returns:** `OcelLogHandle` — Handle to parsed log in Rust memory

**Throws:**
- `ParseException` — If JSON is invalid or incompatible with OCEL2 spec

**Memory:** Caller owns returned handle; must call `close()` when done.

**Example:**
```java
ProcessMiningEngine engine = new ProcessMiningEngine(new Rust4pmBridge());
OcelLogHandle log = engine.parseOcel2Json(jsonString);
try {
    int events = log.eventCount();
} finally {
    log.close();
}
```

##### discoverDfg

```java
DirectlyFollowsGraph discoverDfg(OcelLogHandle log)
    throws ProcessMiningException
```

Discover directly-follows graph from OCEL2 log.

**Parameters:**
- `log` — Parsed OCEL2 log handle (must be open)

**Returns:** `DirectlyFollowsGraph` — Discovered DFG with nodes and edges

**Throws:**
- `ProcessMiningException` — If discovery fails (corrupt log, etc.)

**Memory:** Graph data copied to Java heap; safe after `log` is closed.

**Example:**
```java
DirectlyFollowsGraph dfg = engine.discoverDfg(log);
System.out.println("Total transitions: " + dfg.totalTransitions());
dfg.findNode("Place1").ifPresent(node ->
    System.out.println("Node count: " + node.count())
);
```

##### checkConformance

```java
ConformanceReport checkConformance(OcelLogHandle log, String pnmlStr)
    throws ConformanceException
```

Check conformance of log against Petri net process model.

**Parameters:**
- `log` — Parsed OCEL2 log handle (must be open)
- `pnmlStr` — PNML XML of process model

**Returns:** `ConformanceReport` — Fitness, precision, and F1 score

**Throws:**
- `ConformanceException` — If alignment fails or PNML is invalid

**Memory:** Report data copied to Java heap; safe after `log` is closed.

**Example:**
```java
ConformanceReport report = engine.checkConformance(log, pnmlXml);
if (report.isPerfectFit()) {
    System.out.println("Log perfectly conforms to model");
} else {
    System.out.printf("Fitness: %.2f, Precision: %.2f, F1: %.2f%n",
        report.fitness(), report.precision(), report.f1Score());
}
```

##### parseAll

```java
List<OcelLogHandle> parseAll(List<String> jsonLogs)
    throws Exception
```

Parse multiple OCEL2 JSON strings into handles.

**Parameters:**
- `jsonLogs` — List of valid OCEL2 JSON strings

**Returns:** `List<OcelLogHandle>` — One handle per input log

**Throws:**
- `Exception` — If any log fails to parse

**Memory:** Caller owns all returned handles; must close each individually.

**Example:**
```java
List<OcelLogHandle> logs = engine.parseAll(Arrays.asList(json1, json2, json3));
try {
    for (OcelLogHandle log : logs) {
        System.out.println("Events: " + log.eventCount());
    }
} finally {
    logs.forEach(OcelLogHandle::close);
}
```

##### close

```java
void close()
```

Free all Rust memory and close the bridge.

Call this to prevent memory leaks. Best practice: use try-with-resources.

**Example:**
```java
try (ProcessMiningEngine engine = new ProcessMiningEngine(new Rust4pmBridge())) {
    OcelLogHandle log = engine.parseOcel2Json(json);
    // use log
} // engine and bridge auto-closed
```

---

## Layer 2: Bridge

Memory-safe interface to Rust runtime. Manages FFM Arena and native resources.

### Class: `Rust4pmBridge`

Package: `org.yawlfoundation.yawl.rust4pm.bridge`

Implements: `AutoCloseable`

#### Constructor

```java
Rust4pmBridge()
    throws ProcessMiningException
```

Initialize bridge and load Rust shared library.

**Throws:**
- `ProcessMiningException` — If .so not found or incompatible

**System Properties:**
- `rust4pm.library.path` — Override library location (default: classpath resource)

**Example:**
```java
System.setProperty("rust4pm.library.path", "/opt/rust4pm/librust4pm.so");
Rust4pmBridge bridge = new Rust4pmBridge();
```

#### Methods

##### parseOcel2Json

```java
OcelLogHandle parseOcel2Json(String json)
    throws ParseException
```

Parse OCEL2 JSON and return handle to Rust memory.

**Parameters:**
- `json` — Valid OCEL2 JSON string

**Returns:** `OcelLogHandle` — Borrowable reference to Rust-owned log data

**Throws:**
- `ParseException` — If JSON is invalid

**Example:**
```java
Rust4pmBridge bridge = new Rust4pmBridge();
OcelLogHandle log = bridge.parseOcel2Json(jsonString);
int eventCount = log.eventCount();
log.close();
bridge.close();
```

##### arena

```java
Arena arena()
```

Return the current FFM Arena for allocating native memory.

**Returns:** `Arena` — Active Arena for `MemorySegment` allocations

**Lifetime:** Valid until bridge is closed.

**Example:**
```java
Arena arena = bridge.arena();
MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG, 12345L);
```

##### close

```java
void close()
```

Free the FFM Arena and close Rust library handles.

Must be called to prevent resource leaks.

---

### Record: `OcelLogHandle`

Package: `org.yawlfoundation.yawl.rust4pm.bridge`

Implements: `AutoCloseable`

Immutable handle to a parsed OCEL2 log in Rust memory.

```java
public record OcelLogHandle(
    MemorySegment ptr,
    Rust4pmBridge bridge
) implements AutoCloseable
```

#### Methods

##### eventCount

```java
int eventCount()
```

Return number of events in the log.

**Returns:** Count of OCEL events

**Performance:** O(1), reads metadata; ~5ns

**Example:**
```java
OcelLogHandle log = bridge.parseOcel2Json(json);
int count = log.eventCount();  // Fast, O(1)
```

##### events

```java
OcelEventView events()
```

Return view of all events for iteration.

**Returns:** `OcelEventView` — Lazy view of event array

**Memory:** View holds reference to log; log must not be closed while view is active.

**Example:**
```java
OcelEventView events = log.events();
events.stream()
    .filter(e -> e.eventType().equals("Create"))
    .forEach(System.out::println);
```

##### close

```java
void close()
```

Free Rust memory for this log.

After closing, all views and events are invalid. Calling any method on closed handle results in `SEGV`.

**Example:**
```java
try (OcelLogHandle log = bridge.parseOcel2Json(json)) {
    // use log
} // auto-closed
```

---

### Record: `OcelEventView`

Package: `org.yawlfoundation.yawl.rust4pm.bridge`

Implements: `AutoCloseable`

Lazy, zero-copy view of OCEL events. Does not copy event data; accesses Rust memory directly.

```java
public record OcelEventView(
    MemorySegment segment,
    int count,
    MemorySegment resultSegment,
    Rust4pmBridge bridge
) implements AutoCloseable
```

#### Methods

##### get

```java
OcelEvent get(int index)
    throws IndexOutOfBoundsException
```

Get event by index.

**Parameters:**
- `index` — 0-based event index

**Returns:** `OcelEvent` — Event record populated from Rust memory

**Throws:**
- `IndexOutOfBoundsException` — If index >= count

**Performance:** O(1), ~100ns (includes Rust FFI call)

**Example:**
```java
OcelEventView events = log.events();
OcelEvent first = events.get(0);
System.out.println(first.eventId());
```

##### stream

```java
Stream<OcelEvent> stream()
```

Return lazy Java stream of all events.

**Returns:** `Stream<OcelEvent>` — Lazy stream (non-terminal operation)

**Performance:** Terminal operations may issue many FFI calls (one per event)

**Example:**
```java
events.stream()
    .filter(e -> e.eventType().equals("Send"))
    .map(OcelEvent::timestamp)
    .collect(Collectors.toList());
```

##### close

```java
void close()
```

Release any allocated resources. View becomes invalid.

Safe to call multiple times (idempotent).

---

## Layer 2: Model

Type-safe data structures for process mining results.

### Record: `OcelEvent`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record OcelEvent(
    String eventId,
    String eventType,
    Instant timestamp,
    int attrCount
) implements Comparable<OcelEvent>
```

Represents a single OCEL event.

#### Static Methods

##### fromSegment

```java
static OcelEvent fromSegment(MemorySegment segment)
```

Construct OcelEvent from raw memory layout (internal use).

**Parameters:**
- `segment` — Valid memory region with OCEL event data

**Returns:** `OcelEvent` — Populated record

---

### Record: `OcelObject`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record OcelObject(
    String objectId,
    String objectType
)
```

Represents an OCEL object (entity).

---

### Record: `DirectlyFollowsGraph`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record DirectlyFollowsGraph(
    List<DfgNode> nodes,
    List<DfgEdge> edges
)
```

Process model discovered from event log.

#### Methods

##### findNode

```java
Optional<DfgNode> findNode(String id)
```

Find node by activity identifier.

**Parameters:**
- `id` — Node activity ID

**Returns:** `Optional<DfgNode>` — Node if found

**Performance:** O(n) linear search; consider building `Map` for repeated lookups

**Example:**
```java
dfg.findNode("Place1")
   .ifPresent(node -> System.out.println("Frequency: " + node.count()));
```

##### totalTransitions

```java
long totalTransitions()
```

Sum of all edge counts.

**Returns:** Total number of transitions in the log

**Performance:** O(m) where m = edges.size()

---

### Record: `DfgNode`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record DfgNode(
    String id,
    String label,
    long count
)
```

Activity node in DFG. Represents frequency of activity execution.

---

### Record: `DfgEdge`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record DfgEdge(
    String source,
    String target,
    long count
)
```

Transition between activities. Represents directly-follows frequency.

---

### Record: `ConformanceReport`

Package: `org.yawlfoundation.yawl.rust4pm.model`

```java
public record ConformanceReport(
    double fitness,
    double precision,
    int eventCount,
    ProcessModel model
)
```

Results of conformance checking between log and process model.

#### Methods

##### f1Score

```java
double f1Score()
```

Harmonic mean of fitness and precision.

**Formula:** `2 * (fitness * precision) / (fitness + precision)`

**Range:** [0.0, 1.0]

**Example:**
```java
ConformanceReport report = engine.checkConformance(log, pnml);
double f1 = report.f1Score();
System.out.printf("F1 Score: %.3f%n", f1);
```

##### isPerfectFit

```java
boolean isPerfectFit()
```

Return true if fitness and precision both equal 1.0.

**Example:**
```java
if (report.isPerfectFit()) {
    System.out.println("Log perfectly conforms to model");
}
```

---

### Sealed Interface: `ProcessModel`

Package: `org.yawlfoundation.yawl.rust4pm.model`

Base class for process model representations.

#### Permissible Subclasses

##### PetriNet

```java
record PetriNet(
    List<Place> places,
    List<Transition> transitions,
    String pnmlJson
) implements ProcessModel
```

Petri net process model.

- `places` — Petri net places
- `transitions` — Petri net transitions
- `pnmlJson` — Original PNML as JSON

##### ProcessTree

```java
record ProcessTree(
    String treeJson
) implements ProcessModel
```

Process tree model (imperative process model).

- `treeJson` — Process tree structure as JSON

##### Declare

```java
record Declare(
    List<String> constraints
) implements ProcessModel
```

Declare model (declarative process model).

- `constraints` — Declare constraint rules

---

### Sealed Interface: `OcelValue`

Package: `org.yawlfoundation.yawl.rust4pm.model`

Typed OCEL event attribute value.

#### Permissible Subclasses

##### StringValue

```java
record StringValue(String value) implements OcelValue
```

String attribute.

##### IntValue

```java
record IntValue(long value) implements OcelValue
```

Integer (64-bit) attribute.

##### FloatValue

```java
record FloatValue(double value) implements OcelValue
```

Floating-point (64-bit) attribute.

##### Timestamp

```java
record Timestamp(Instant value) implements OcelValue
```

Timestamp attribute.

---

## Layer 2: Errors

Exception hierarchy for error handling.

### Class: `ProcessMiningException`

Package: `org.yawlfoundation.yawl.rust4pm.error`

Base checked exception for all rust4pm errors.

```java
public class ProcessMiningException extends Exception
```

#### Constructors

```java
ProcessMiningException(String message)
ProcessMiningException(String message, Throwable cause)
```

#### Subclasses

##### ParseException

```java
public class ParseException extends ProcessMiningException
```

Raised when OCEL2 JSON parsing fails (invalid schema, syntax error).

**Example:**
```java
try {
    log = engine.parseOcel2Json(malformedJson);
} catch (ParseException e) {
    System.err.println("Invalid OCEL2: " + e.getMessage());
}
```

##### ConformanceException

```java
public class ConformanceException extends ProcessMiningException
```

Raised when conformance checking fails (alignment computation error, invalid model).

**Example:**
```java
try {
    report = engine.checkConformance(log, pnml);
} catch (ConformanceException e) {
    System.err.println("Conformance check failed: " + e.getMessage());
}
```

---

## Layer 1: rust4pm_h

Low-level FFM bindings to Rust C ABI. Internal API — do not use directly.

### Package

`org.yawlfoundation.yawl.rust4pm.generated`

### Symbol Lookup

```java
public static final Optional<SymbolLookup> LIBRARY
```

Loaded Rust shared library. Empty if .so not found or incompatible.

---

## C ABI: rust4pm.h

Rust cdylib exported C interface.

### Library Name

- Linux: `librust4pm.so`
- macOS: `librust4pm.dylib`
- Windows: `rust4pm.dll`

### Exported Functions

#### log_parse_ocel2_json

```c
OcelLogHandle* log_parse_ocel2_json(const char* json_str)
```

Parse OCEL2 JSON string.

**Parameters:**
- `json_str` — UTF-8 null-terminated OCEL2 JSON

**Returns:** Pointer to `OcelLogHandle` struct in Rust memory, or NULL on error

**Ownership:** Returned pointer owned by Rust; caller must call `log_free()` to release

**Example:**
```java
String json = "{...}"; // OCEL2 JSON
MemorySegment ptr = logParseOcel2Json(json, bridge);
// ... use ptr ...
logFree(ptr, bridge);
```

#### log_free

```c
void log_free(OcelLogHandle* handle)
```

Free OCEL2 log handle.

**Parameters:**
- `handle` — Pointer from `log_parse_ocel2_json()`

**Ownership:** After this call, handle is invalid; caller must not dereference

**Safety:** Idempotent; safe to call multiple times (though wasteful)

#### log_event_count

```c
int32_t log_event_count(OcelLogHandle* handle)
```

Return number of events in log.

**Parameters:**
- `handle` — Valid `OcelLogHandle*` from `log_parse_ocel2_json()`

**Returns:** Event count (>= 0)

**Performance:** O(1), ~5ns

#### log_events_ptr

```c
const OcelEvent* log_events_ptr(OcelLogHandle* handle)
```

Return pointer to event array.

**Parameters:**
- `handle` — Valid handle

**Returns:** Pointer to array of `OcelEvent` structs

**Lifetime:** Valid for lifetime of handle

**Example:**
```c
OcelEvent* events = log_events_ptr(handle);
for (int i = 0; i < log_event_count(handle); i++) {
    printf("%s\n", events[i].event_id);
}
```

#### discover_dfg

```c
DirectlyFollowsGraphHandle* discover_dfg(OcelLogHandle* log)
```

Discover directly-follows graph from log.

**Parameters:**
- `log` — Valid log handle

**Returns:** DFG handle, or NULL on error

**Ownership:** Returned handle owned by Rust; caller must call `dfg_free()`

#### dfg_free

```c
void dfg_free(DirectlyFollowsGraphHandle* handle)
```

Free DFG handle.

#### dfg_nodes

```c
const DfgNode* dfg_nodes(DirectlyFollowsGraphHandle* handle, int32_t* out_count)
```

Return array of DFG nodes.

**Parameters:**
- `handle` — Valid DFG handle
- `out_count` — Output pointer; filled with node count

**Returns:** Pointer to `DfgNode` array

#### dfg_edges

```c
const DfgEdge* dfg_edges(DirectlyFollowsGraphHandle* handle, int32_t* out_count)
```

Return array of DFG edges.

**Parameters:**
- `handle` — Valid DFG handle
- `out_count` — Output pointer; filled with edge count

**Returns:** Pointer to `DfgEdge` array

#### check_conformance

```c
ConformanceReportHandle* check_conformance(
    OcelLogHandle* log,
    const char* pnml_str
)
```

Check log conformance against Petri net model.

**Parameters:**
- `log` — Valid log handle
- `pnml_str` — PNML XML model (UTF-8 null-terminated)

**Returns:** Report handle, or NULL on error

**Ownership:** Caller must call `report_free()` to release

#### report_free

```c
void report_free(ConformanceReportHandle* handle)
```

Free conformance report handle.

#### report_fitness

```c
double report_fitness(ConformanceReportHandle* handle)
```

Return fitness metric [0.0, 1.0].

#### report_precision

```c
double report_precision(ConformanceReportHandle* handle)
```

Return precision metric [0.0, 1.0].

#### report_event_count

```c
int32_t report_event_count(ConformanceReportHandle* handle)
```

Return number of events evaluated.

---

## System Properties

### `rust4pm.library.path`

Override default library location.

**Type:** String (filesystem path)

**Default:** ClassLoader resource (bundled .so)

**Example:**
```java
System.setProperty("rust4pm.library.path", "/opt/libs/librust4pm.so");
```

---

## Memory Safety Guarantees

All public API enforces memory safety:

- **Zero-copy segment access** — Events accessed via `MemorySegment.ofAddress()` without copy
- **Lifetime tracking** — `OcelLogHandle` holds `Rust4pmBridge` reference to prevent premature closure
- **AutoCloseable** — All resource classes implement try-with-resources
- **No dangling pointers** — Private FFM layer prevents invalid dereferences

---

## Performance Characteristics

| Operation | Time | Space |
|-----------|------|-------|
| `bridge.parseOcel2Json()` | O(n) where n = JSON size | O(m) where m = events |
| `log.eventCount()` | ~5ns | O(1) |
| `log.events().get(i)` | ~100ns | O(1) heap |
| `engine.discoverDfg()` | O(n log n) | O(n+m) graph |
| `engine.checkConformance()` | O(n*m) alignment | O(n) |

---

## Error Recovery

### ParseException

Indicates invalid OCEL2 JSON. Inspect message for details. Fix JSON and retry.

```java
try {
    log = engine.parseOcel2Json(json);
} catch (ParseException e) {
    System.err.println("Parse error: " + e.getMessage());
    // Fix JSON format or schema
}
```

### ConformanceException

Indicates alignment failure. PNML may be invalid or incompatible with log.

```java
try {
    report = engine.checkConformance(log, pnml);
} catch (ConformanceException e) {
    System.err.println("Alignment failed: " + e.getMessage());
    // Validate PNML format or check model compatibility
}
```

### ProcessMiningException

Catch-all for bridge initialization, library load failures.

```java
try {
    bridge = new Rust4pmBridge();
} catch (ProcessMiningException e) {
    System.err.println("Bridge init failed: " + e.getMessage());
    // Check LD_LIBRARY_PATH or rust4pm.library.path system property
}
```

---

## Thread Safety

**Bridge**: NOT thread-safe. Create one per thread or synchronize access.

**ProcessMiningEngine**: NOT thread-safe. Use one per analysis task or synchronize.

**OcelLogHandle, OcelEventView**: NOT thread-safe after construction.

**Records (OcelEvent, DfgNode, etc.)**: Thread-safe (immutable).

