# Explanation: JSON Wire Format and Three-Layer Design in FFM Bridges

**Purpose**: Understand the *why* behind YAWL's JSON-based FFM bridge architecture — why three layers instead of two, why JSON as a wire format instead of struct views, and how the capability completeness system works.

**Prerequisite reading**:
- A FFM bridge tutorial or how-to guide (not this document)
- `/home/user/yawl/.claude/docs/rust-java-panama-ffm/explanation-correct-by-construction.md` — covers layout invariants, sizeof probes, and lifetime management (this document assumes you understand those topics)

---

## Two Bridge Flavors in YAWL

YAWL has two distinct bridge patterns, each optimized for a different data access profile. Understanding when to use each prevents building bridges that work but are painful to maintain.

### The Struct-View Pattern (yawl-rust4pm)

In `yawl-rust4pm`, the Rust library returns array structs — specifically, arrays of `OcelEventC` and `OcelObjectC` records. The Java bridge exposes zero-copy views:

```java
// yawl-rust4pm/src/main/java/org/yawlfoundation/yawl/rust4pm/bridge/Rust4pmBridge.java
public OcelLogHandle parseOcel2Json(String json) throws ParseException {
    try (Arena call = Arena.ofConfined()) {
        MemorySegment jsonSeg = call.allocateFrom(json, StandardCharsets.UTF_8);
        MemorySegment result = rust4pm_h.rust4pm_parse_ocel2_json(call, jsonSeg, ...);

        MemorySegment errorPtr = (MemorySegment) rust4pm_h.PARSE_RESULT_ERROR.get(result, 0L);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(...).getString(...);
            rust4pm_h.rust4pm_error_free(errorPtr);
            throw new ParseException(msg);
        }

        MemorySegment handlePtr = (MemorySegment) rust4pm_h.PARSE_RESULT_HANDLE_PTR.get(result, 0L);
        return new OcelLogHandle(handlePtr, this);
    }
}
```

Then, in `OcelLogHandle`, calling `events()` returns:

```java
public OcelEventView events() throws ProcessMiningException {
    MemorySegment result = rust4pm_h.rust4pm_log_get_events(ownedArena, rawPtr);
    // ... extract count and pointer ...
    MemorySegment eventsSeg = eventsPtr.reinterpret(count * stride, ownedArena, null);
    return new OcelEventView(eventsSeg, (int) count);
}
```

The caller can now iterate events without copying:

```java
try (OcelEventView events = log.events()) {
    for (int i = 0; i < events.count(); i++) {
        System.out.println(events.eventId(i));  // Reads directly from Rust's memory
        System.out.println(events.objectId(i));
    }
}
```

**Cost**: Low latency, zero copies. One parse call on a 100k-event OCEL log takes ~50ms for the Rust parse; zero additional overhead in Java.

**Trade-off**: Layer 1 (`rust4pm_h.java`) contains ~300 lines of `StructLayout`, `VarHandle` declarations, and sizeof probes. Adding a new field to `OcelEventC` in Rust requires regenerating the entire accessor class. The interface is tightly coupled to the struct layout.

**When to use**: Process mining, high-frequency event iteration (>10k events), streaming analysis where copying is prohibitive.

### The JSON Wire Pattern (yawl-data-modelling)

In `yawl-data-modelling`, the Rust library returns JSON strings. The Java bridge decodes them to Java records:

```java
// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/bridge/DataModellingBridge.java
@MapsToCapability(PARSE_ODCS_YAML)
public String parseOdcsYaml(String yaml) {
    try (Arena call = Arena.ofConfined()) {
        return unwrapResult(data_modelling_ffi_h.dm_parse_odcs_yaml(call, cstrCall(call, yaml)));
    }
}

// yawl-data-modelling/src/main/java/org/yawlfoundation/yawl/datamodelling/api/DataModellingServiceImpl.java
@MapsToCapability(PARSE_ODCS_YAML)
@Override
public WorkspaceModel parseOdcsYaml(String yaml) {
    return decode(bridge.parseOdcsYaml(yaml), WorkspaceModel.class);
}
```

The caller receives a fully typed Java record:

```java
DataModellingService svc = DataModellingModule.create();
WorkspaceModel workspace = svc.parseOdcsYaml(yamlString);
System.out.println(workspace.name());
for (OdcsTable table : workspace.tables()) {
    System.out.println(table.name());
}
```

**Cost**: ~50µs per call for JSON deserialization. Negligible compared to user latency (a user clicking a button, waiting for a dialog) or I/O latency (database round trip).

**Trade-off**: Layer 1 is minimal — no struct accessors, just MethodHandles for raw C functions. Adding a field to the Rust schema requires adding a corresponding Java record field, but the registry and tests verify coverage automatically. The interface is loosely coupled to the struct layout.

**When to use**: Schema operations (import, export, validation), rich domain models with optional fields, APIs that change frequently, batch operations where latency is dominated by I/O not deserialization.

---

## Why JSON as Wire Format

The FFM struct-view approach works well when the data is simple: arrays of homogeneous records, few fields, stable layout. But as data models grow, the cost escalates:

**Scenario: Add nested structure to workspace**

```rust
// Before: simple fields
pub struct OdcsWorkspace {
    name: String,        // offset 0, size 24
    tables: Vec<Table>,  // offset 24, size 24
}

// After: add metadata object with versioning, schema hints, ownership
pub struct OdcsWorkspace {
    name: String,
    metadata: WorkspaceMetadata,  // NEW: nested struct
    tables: Vec<Table>,
}

pub struct WorkspaceMetadata {
    version: u32,
    schema_version: u32,
    owned_by: String,
    created_at: i64,
    // ... 10 more fields ...
}
```

With struct-view approach:
1. Generate `WorkspaceMetadata` layout in Java (5 fields × 2-3 lines = 10+ lines)
2. Generate `OdcsWorkspace` layout in Java (~15 lines)
3. Generate `VarHandle` for each field (~30 lines total)
4. Update sizeof probes (add 2 more probe MethodHandles)
5. Update CbC assertions in static initializer (add 5 checks)
6. Regenerate `OdcsWorkspaceAccessor`, `WorkspaceMetadataAccessor` classes
7. Update any code that reads the new fields (add `VarHandle` casts)
8. Result: ~200 additional lines in Layer 1

With JSON wire approach:
1. Add fields to Java record: `public record WorkspaceModel(..., String ownedBy, long createdAt, ...) {}`
2. Add to Rust: `serde_json::to_string(&workspace).unwrap()` (one line)
3. Jackson automatically deserializes the new fields
4. Done

The advantage compounds as the schema grows. By 50 fields (realistic for a rich domain model), struct-view becomes unmaintainable.

**Why JSON is safe for schema evolution**:

Jackson deserializes with `@JsonIgnoreProperties(ignoreUnknown = true)` by default on records. A new field in Rust doesn't break Java deserialization. Conversely, if Java adds an optional field that Rust doesn't fill in, Jackson handles the null gracefully. This loose coupling means the schema can evolve independently in each direction.

**The latency trade-off is acceptable** because data-modelling operations (parsing YAML, converting between formats, validating) are not in hot loops. Parsing a YAML file takes 100ms. The 50µs JSON deserialization is noise. In contrast, process mining iterates events in tight loops; 10k events × 50µs = 500ms of deserialization overhead (unacceptable).

---

## Why Three Layers, Not Two

Some engineers ask: "Why not collapse Layer 2 and Layer 3? Why not have ServiceImpl call the FFM code directly?"

Here's the case against it.

### Layer 2 + 3 Combined: Problems

Without Layer 2 (the bridge), ServiceImpl would manage Panama FFM directly:

```java
// ❌ DON'T DO THIS: ServiceImpl with Arena management
public final class DataModellingServiceImpl implements DataModellingService {

    // Where does the Arena live?
    // Option 1: shared Arena in field
    private final Arena arena = Arena.ofShared();

    @Override
    public WorkspaceModel parseOdcsYaml(String yaml) {
        // Allocate in the shared arena — but when does it get freed?
        MemorySegment yamlSeg = arena.allocateFrom(yaml, UTF_8);
        MemorySegment result = data_modelling_ffi_h.dm_parse_odcs_yaml(arena, yamlSeg);

        // Now unwrap — who owns the error/data pointers?
        // They must be freed, but arena.close() closes the whole arena,
        // invalidating yamlSeg. Freed too early.

        MemorySegment errorPtr = DmResult_h.error$get(result);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(...).getString(...);
            // If we free here, we close the shared arena? No.
            // We only want to free the individual pointer.
            // But arena.scope() doesn't exist — there's no way to free a single allocation.
        }

        WorkspaceModel model = decode(data$get(result), WorkspaceModel.class);
        // What if decode() throws? The result pointers are never freed.
        // We need try-finally, but that logic is scattered in every method.

        return model;
    }
}
```

**Problems**:
1. Arena lifetime management is spread across 40+ methods (one per capability)
2. If a decode throws, pointers leak
3. Testing business logic (the decode/encode) requires the native library to be loaded (can't test the service layer alone)
4. Adding a new capability means copying the same error-handling boilerplate

### Layer 2 Isolation: Solution

Layer 2 (Bridge) concentrates all Arena and pointer management in one place:

```java
// ✅ DO THIS: Bridge isolates FFM mechanics
public final class DataModellingBridge implements AutoCloseable {
    private final Arena arena = Arena.ofShared();  // One place to manage lifetime

    @MapsToCapability(PARSE_ODCS_YAML)
    public String parseOdcsYaml(String yaml) {
        try (Arena call = Arena.ofConfined()) {  // Per-call allocation
            return unwrapResult(data_modelling_ffi_h.dm_parse_odcs_yaml(call, cstrCall(call, yaml)));
        }
    }

    private String unwrapResult(MemorySegment result) {
        // Error handling in one place, reused by all methods
        MemorySegment errorPtr = DmResult_h.error$get(result);
        if (!MemorySegment.NULL.equals(errorPtr)) {
            String msg = errorPtr.reinterpret(...).getString(...);
            data_modelling_ffi_h.dm_string_free(errorPtr);
            throw new DataModellingException(msg);
        }
        MemorySegment dataPtr = DmResult_h.data$get(result);
        try {
            return dataPtr.reinterpret(...).getString(...);
        } finally {
            data_modelling_ffi_h.dm_string_free(dataPtr);  // Always freed
        }
    }

    @Override
    public void close() {
        arena.close();  // One place, lifetime bounded by try-with-resources
    }
}

// Now Layer 3 is pure domain logic
public final class DataModellingServiceImpl implements DataModellingService {
    private final DataModellingBridge bridge;

    @MapsToCapability(PARSE_ODCS_YAML)
    @Override
    public WorkspaceModel parseOdcsYaml(String yaml) {
        return decode(bridge.parseOdcsYaml(yaml), WorkspaceModel.class);
        // No Arena, no MemorySegment, no error handling — just decode JSON
    }

    @Override
    public void close() {
        bridge.close();  // Delegate, don't manage
    }
}
```

**Benefits**:
1. Error handling logic (`unwrapResult`, `unwrapVoidResult`) is centralized and correct
2. Arena lifetime is guaranteed — each method uses `Arena.ofConfined()` per call, freeing after the call returns
3. Layer 3 is testable without the native library (mock the Bridge, test deserialization)
4. Adding a new capability requires only two annotations and one line of decode logic

This is the **separation of concerns** principle: Layer 2 owns "How do I talk to Rust safely?" and Layer 3 owns "What does the user want to do?"

---

## Why Capability Enum Completeness

YAWL enforces that every Rust function you intend to expose has an entry in the Capability enum, and that every enum value is mapped to exactly one method in both Bridge and ServiceImpl. This seems like overkill — why not just trust the code?

The answer: hidden enum values are discovered weeks later by users.

### Scenario: The Forgotten Method

A new schema operation is added to Rust: `dm_export_to_parquet`. The engineer adds:
1. MethodHandle in Layer 1 ✅
2. Bridge method with @MapsToCapability ✅
3. Forgets the ServiceImpl method ❌

The code compiles. Tests pass (maybe they don't cover all capabilities). A user tries to export to Parquet... and discovers the method doesn't exist in the public API. Now it's a production incident.

With the Capability enum:
1. Add `EXPORT_TO_PARQUET` to the enum
2. JVM startup runs `DataModellingCapabilityRegistry.assertComplete()`
3. Registry scans all `@MapsToCapability` annotations
4. Finds that `EXPORT_TO_PARQUET` is missing from ServiceImpl
5. Throws `CapabilityRegistryException` before any code runs
6. The incident never happens

The registry makes the gap impossible to ignore — the JVM won't start.

### Why Dual Annotation (@MapsToCapability on both Bridge and ServiceImpl)

Some ask: "Can't we just annotate ServiceImpl and infer the Bridge coverage?"

The answer is: not safely. A ServiceImpl method could be calling the wrong Bridge method (copy-paste error), or calling multiple Bridge methods incorrectly. Annotating both forces declarative coverage:

- `@MapsToCapability(PARSE_ODCS_YAML)` on `DataModellingBridge.parseOdcsYaml()` means "Layer 2 has this"
- `@MapsToCapability(PARSE_ODCS_YAML)` on `DataModellingServiceImpl.parseOdcsYaml()` means "Layer 3 exposes this"

If one is missing, the registry catches it. If the Bridge method calls the wrong FFM function, the annotation will be mismatched with the actual code — the annotation is the contract, the code must honor it.

---

## Why assumeTrue Instead of @Disabled

FFM tests require the native library to be loaded. Without it, VarHandle field reads return garbage, MethodHandles throw LinkageError, and tests fail.

Two approaches:

**Approach 1: @Disabled annotation**

```java
@Disabled("Requires native library")
@Test
void bridge_parses_odcs_yaml() {
    // ...
}
```

**Problem**: The test is permanently invisible. If a new developer later builds the Rust library, they won't know to enable tests. CI never runs them. They stay disabled forever.

**Approach 2: assumeTrue (conditional skip)**

```java
@BeforeAll
static void skipIfNoLibrary() {
    assumeTrue(data_modelling_ffi_h.LIBRARY.isPresent(), "Native library not loaded");
}

@Test
void bridge_parses_odcs_yaml() {
    // ...
}
```

**Behavior**:
- Developer machine with Rust build: library loads, `LIBRARY.isPresent() == true`, all tests run ✅
- CI without Rust build: library absent, `LIBRARY.isPresent() == false`, tests skipped with info message ✅
- Later developer adds Rust build to CI: tests automatically run, no code change needed ✅

`assumeTrue` makes capability tests "latent but real" — they're waiting to run, not permanently bypassed.

---

## Why Jackson for JSON

The alternative would be manual parsing:

```java
// ❌ Manual parsing
public static WorkspaceModel parseWorkspace(String json) {
    // Parse JSON manually, extract name, description, tables array
    // Handle missing fields gracefully
    // Handle null values
    // For each table, extract columns...
    // ~100 lines of fragile code
    return new WorkspaceModel(...);
}
```

Jackson handles all this automatically:

```java
// ✅ Jackson
public static WorkspaceModel parseWorkspace(String json) {
    return MAPPER.readValue(json, WorkspaceModel.class);
}
```

**Why Jackson specifically**:
1. It's already a YAWL dependency (used in workflow engine)
2. Excellent Java 25 record support (auto-detects all record fields)
3. `@JsonIgnoreProperties(ignoreUnknown = true)` enables forward compatibility
4. `@JsonInclude(Include.NON_NULL)` handles optional fields
5. Consistent error messages ("JSON decode failed for WorkspaceModel: missing required field 'name'")

The `Json.encode/decode` helpers wrap it in one place, so if we ever need to switch serialization frameworks, the change is localized to one file.

---

## The Complete Picture: How Pieces Connect

A single narrative that ties everything together:

> A new Rust schema operation appears: `dm_export_to_parquet`. The engineer adds `EXPORT_TO_PARQUET` to the `Capability` enum and updates `TOTAL` to 43. They add a MethodHandle in `data_modelling_ffi_h.java` (Layer 1) and a probe for sizeof. They write a bridge method `exportToParquet(String json, String outputPath)` in `DataModellingBridge` with `@MapsToCapability(EXPORT_TO_PARQUET)`. They implement the service method in `DataModellingServiceImpl` with `@MapsToCapability(EXPORT_TO_PARQUET)`, encoding the input record and decoding the output. They write a test in `@CapabilityTest(EXPORT_TO_PARQUET)`. On next JVM startup, `DataModellingModule.create()` calls `DataModellingCapabilityRegistry.assertComplete()`. The registry scans all 43 enum values and finds all three annotations (Bridge, ServiceImpl, Test). The CbC probes verify the struct layouts match Rust. The system starts. On a developer machine with the Rust library, the test runs and verifies the round-trip. In CI without the library, the test skips silently. The capability is provably real — it cannot silently disappear.

This is the YAWL guarantee: **correct by construction or provably broken, never subtly wrong**.

---

## See Also

- **Reference**: `/home/user/yawl/.claude/docs/rust-java-panama-ffm/reference-map-and-wrap-module-anatomy.md` — Exact file structure, naming conventions, and mandatory code elements
- **Correct-by-Construction**: `/home/user/yawl/.claude/docs/rust-java-panama-ffm/explanation-correct-by-construction.md` — Layout invariants, lifetime management, CbC verification patterns
- **Struct-View Pattern**: `yawl-rust4pm` source code — Zero-copy OCEL parsing for process mining (comparison)
