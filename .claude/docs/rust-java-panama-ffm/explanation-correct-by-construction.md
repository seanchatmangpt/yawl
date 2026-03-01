# Explanation: Correct-by-Construction in Rust→Java 25 Panama FFM Bridges

## Understanding Correct-by-Construction for FFM Bridges

Correct-by-construction (CbC) in Panama FFM bridges means encoding safety invariants into the type system and runtime semantics of Java itself, rather than relying on code review, documentation, or runtime checks to catch mistakes. When a CbC design is violated, the JVM throws a checked exception or prevents execution at startup — the error surface is impossible to miss.

This contrasts with traditional error discovery approaches:
- **Testing-after-fact**: Write code, hope tests catch data corruption or use-after-free bugs weeks later during integration testing
- **Code review discipline**: Assume developers remember which pointers are borrowed vs. owned, which arenas are still open, whether double-free is safe
- **Documentation-driven safety**: Javadoc says "don't call this after close()" and developers follow it

CbC moves these guarantees from human discipline to machine enforcement. In the Rust→Java Panama bridge pattern, this means the JVM itself rejects unsafe patterns at the point they occur, not after data corruption has propagated through the application.

### Why Silent Wrong-Data Reads Are Worse Than Crashes

In FFM interop, data corruption is often undetectable until deep in the call stack:

```
1. Rust struct has field F1 at offset 0, field F2 at offset 8
2. Java StructLayout incorrectly says F2 is at offset 4 (developer typo)
3. Code reads F2 from offset 4: retrieves garbage from middle of F1 value
4. Garbage propagates silently through business logic
5. Days later: batch processing report shows impossible numbers
6. Root cause unclear: is it bad input data, algorithm bug, or memory corruption?
```

Contrast with immediate failure:

```
1. Rust sizeof(OcelLogHandle) = 8 bytes
2. Java StructLayout.byteSize() = 16 bytes (typo: duplicated field)
3. JVM startup: rust4pm_h static initializer runs assertLayout()
4. AssertionError thrown immediately with exact mismatch: "Rust sizeof=8 but Java=16"
5. Developer sees exact error, regenerates layout from C header, commits fix
6. Root cause known, time-to-fix <5 minutes
```

The CbC design prevents the silent corruption case entirely by making layout mismatches impossible to ignore. This is consistent with the YAWL **Q invariant** (`real_impl ∨ throw UnsupportedOperationException`) — the system must be either fully correct or obviously broken, never subtly wrong.

---

## The Three Lifetime Invariants

All safety properties in a Panama FFM bridge reduce to three invariants that can be enforced by type system and runtime constraints rather than human memory.

### 1. Layout Invariant: `sizeof_Rust == byteSize_Java`

**Statement**: For every struct type C, the Java `StructLayout.byteSize()` must equal the Rust `sizeof(C)`.

**Where it's enforced**:

```java
// rust4pm_h.java static initializer
static {
    if (LIBRARY.isPresent()) {
        // ... load all MethodHandles ...

        // ── Correct-by-construction layout assertions ───────────────────
        // Verify every hand-written StructLayout byte size against the
        // actual Rust sizeof at JVM startup.
        assertLayout("OcelLogHandle",       OCEL_LOG_HANDLE_LAYOUT,       MH$sizeof_ocel_log_handle);
        assertLayout("ParseResult",         PARSE_RESULT_LAYOUT,          MH$sizeof_parse_result);
        assertLayout("OcelEventC",          OCEL_EVENT_C_LAYOUT,          MH$sizeof_ocel_event_c);
        // ... 5 more structs ...
    }
}

private static void assertLayout(String name, StructLayout layout, MethodHandle sizeofMH) {
    try {
        long rustSize = (long) sizeofMH.invokeExact();
        long javaSize = layout.byteSize();
        if (rustSize != javaSize) {
            throw new AssertionError(
                "Layout mismatch for " + name + ": Rust sizeof=" + rustSize +
                " but Java StructLayout.byteSize()=" + javaSize +
                ". Regenerate rust4pm_h.java from rust4pm.h.");
        }
    } catch (AssertionError e) {
        throw e;
    } catch (Throwable t) {
        throw new AssertionError("sizeof probe failed for " + name, t);
    }
}
```

**What this prevents**: When Rust code adds a field to `OcelEventC` (say, a new `event_flags: u8`), the Rust sizeof increases from 32 to 40 bytes. If Java's layout is not regenerated, the sizeof probe detects `Rust sizeof=40 but Java=32` at JVM startup. Without this check, Java code would read the wrong offsets and silently corrupt event data.

**Regeneration is automatic**: The layout is generated from `rust/rust4pm/rust4pm.h` using `bash scripts/jextract-generate.sh`. The sizeof probes are backward-compatible: they exist in the Rust library permanently (not behind feature flags), so even old Java binaries can verify the layout at runtime.

### 2. Lifetime Invariant: `use_after_close → IllegalStateException`

**Statement**: Accessing a `MemorySegment` after its owning `Arena` is closed must throw `IllegalStateException`, enforced by Panama's Arena reference counting. Developers cannot accidentally extend a segment's lifetime beyond the arena closure.

**Where it's enforced**:

```java
// OcelLogHandle.java
public final class OcelLogHandle implements AutoCloseable {
    private final MemorySegment rawPtr;
    private final Arena          ownedArena;   // per-handle; closed on close()

    OcelLogHandle(MemorySegment rawPtr, Rust4pmBridge bridge) {
        this.rawPtr     = rawPtr;
        this.ownedArena = Arena.ofShared();  // New arena per handle
        this.bridge     = bridge;
    }

    public OcelEventView events() throws ProcessMiningException {
        MemorySegment result = rust4pm_h.rust4pm_log_get_events(ownedArena, rawPtr);

        // ... extract events pointer and count from result ...
        long count = (long) rust4pm_h.OCEL_EVENTS_RESULT_COUNT.get(result, 0L);
        MemorySegment eventsPtr = (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_EVENTS.get(result, 0L);

        // Reinterpret: pointer into Rust's OcelLogInternal, scoped to ownedArena.
        // After close(), ownedArena.close() makes this segment invalid → IllegalStateException on access.
        MemorySegment eventsSeg = count == 0
            ? MemorySegment.NULL
            : eventsPtr.reinterpret(count * stride, ownedArena, null);  // Bind to ownedArena

        return new OcelEventView(eventsSeg, (int) count);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            rust4pm_h.rust4pm_log_free(rawPtr);
            ownedArena.close();  // Invalidates all derived segments
        }
    }
}
```

**Test verification**:

```java
// OcelLogHandleCorrectnessTest.java
@Test
void ocel_event_view_segment_invalid_after_owning_arena_closed() {
    Arena ownedArena = Arena.ofShared();
    MemorySegment testSeg = ownedArena.allocate(rust4pm_h.OCEL_EVENT_C_LAYOUT);

    OcelEventView view = new OcelEventView(testSeg, 1);

    // Pre-close: access works
    assertDoesNotThrow(() -> view.count());

    // Close the owning arena — simulates OcelLogHandle.close()
    ownedArena.close();

    // Post-close: Panama enforces segment validity — must throw
    assertThrows(IllegalStateException.class, () -> view.get(0),
        "Accessing OcelEventView after owning arena closed must throw IllegalStateException");
}
```

**What this prevents**: Without per-handle `ownedArena`, derived views (`OcelEventView`, `OcelObjectView`) would reference segments backed by a bridge-lifetime arena. If a view survived the handle's close, it would appear valid but reference freed Rust memory. Subsequent reads would access garbage or corrupted heap, with no exception. By binding each view to the handle's own arena, Panama enforces that the view is invalid the moment the handle closes — the JVM throws before the code can read freed memory.

**Tradeoff**: Each handle allocates its own `Arena.ofShared()`, adding 1 KB per handle. In production OCEL2 logs (10–100 concurrent handles), this is negligible. The safety guarantee is worth the cost.

### 3. Idempotency Invariant: `double_close → safe`

**Statement**: Calling `close()` twice must be safe (no crash, no double-free). This is enforced using `AtomicBoolean.compareAndSet()`, making the close guard idempotent.

**Where it's enforced**:

```java
// OcelLogHandle.java
private final AtomicBoolean closed = new AtomicBoolean(false);

@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        rust4pm_h.rust4pm_log_free(rawPtr);
        ownedArena.close();
    }
    // If closed is already true, compareAndSet returns false; no-op
}
```

**Test verification**:

```java
// OcelLogHandleCorrectnessTest.java
@Test
void close_is_idempotent_when_called_twice() {
    try (Rust4pmBridge bridge = new Rust4pmBridge()) {
        OcelLogHandle handle = new OcelLogHandle(MemorySegment.NULL, bridge);

        assertDoesNotThrow(handle::close);
        assertDoesNotThrow(handle::close, "Second close() must be idempotent");
    }
}
```

**What this prevents**: In exception paths and try-with-resources contexts, it's easy for a resource to be closed multiple times. Without idempotency, the second close might invoke `rust4pm_log_free` again, causing a double-free crash in Rust (undefined behavior). With `compareAndSet`, only the first close runs the cleanup; subsequent calls are guaranteed no-ops.

**Connection to H guards**: This pattern aligns with the HYPER_STANDARDS ban on `H_FALLBACK` (silent catch-and-fake). The idempotency invariant ensures that exception paths don't create cascading failures — the close is guaranteed safe, so exception handlers don't have to choose between ignoring the cleanup or risking a crash.

---

## Why Each Invariant Matters

### Layout Invariant Failure Scenario

Suppose a YAWL integration needs to parse a Petri net model from a Rust-based process mining library. The model includes event timestamps with nanosecond precision.

```rust
// Rust: rust4pm.h
struct OcelEventC {
    event_id: *const c_char,      // 8 bytes on 64-bit
    event_type: *const c_char,    // 8 bytes
    timestamp_ms: i64,            // 8 bytes
    timestamp_ns: i32,            // 4 bytes ← New field added
    attr_count: usize,            // 8 bytes
}
// Total: 36 bytes (was 32)
```

Without the layout invariant check, if Java's generated layout is not regenerated:

```java
// OLD java layout (before regeneration):
MemoryLayout.structLayout(
    ADDRESS.withName("event_id"),     // offset 0, size 8
    ADDRESS.withName("event_type"),   // offset 8, size 8
    JAVA_LONG.withName("timestamp_ms"),   // offset 16, size 8
    JAVA_LONG.withName("attr_count")      // offset 24, size 8 ← WRONG: should be 28
).withName("OcelEventC");
// byteSize() = 32 (incorrect)
```

**What happens**:

1. Rust writes 36-byte structs into the event array
2. Java reads with 32-byte stride, accessing wrong offsets for each subsequent event
3. For event 2, Java reads offsets that span the Rust padding space
4. `attr_count` is read from `timestamp_ns` field, getting 4-byte int zero-extended to 64-bit
5. Attribute count becomes 0 for all events, breaking downstream analyses
6. Error appears days later in event log processing, difficult to trace to root cause

**With the layout invariant**:

```java
// At JVM startup, static initializer runs:
long rustSize = MH$sizeof_ocel_event_c.invokeExact();  // Returns 36
long javaSize = OCEL_EVENT_C_LAYOUT.byteSize();        // Returns 32
if (rustSize != javaSize) {
    throw new AssertionError(
        "Layout mismatch for OcelEventC: Rust sizeof=36 but Java StructLayout.byteSize()=32. " +
        "Regenerate rust4pm_h.java from rust4pm.h.");
}
```

JVM startup fails immediately with an exact diagnostic message. Developer regenerates the layout in <1 minute. No data corruption possible.

### Lifetime Invariant Failure Scenario

Consider a YAWL ProcessMiningEngine that discovers DFGs concurrently using `StructuredTaskScope`:

```java
// ProcessMiningEngine.java (Layer 3 API)
public List<DirectlyFollowsGraph> discoverDfgsConcurrent(List<OcelLogHandle> logs)
        throws InterruptedException, ExecutionException {
    try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.<DirectlyFollowsGraph>awaitAllSuccessfulOrThrow())) {
        var tasks = logs.stream()
            .map(log -> scope.fork(() -> discoverDfg(log)))
            .toList();
        scope.join();
        return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
    } catch (Exception e) {
        // On error, scope.close() is called automatically
        // All log handles must be safe to close here
    }
}
```

Suppose the bridge uses a shared arena for all log data (incorrect design):

```java
// ANTI-PATTERN: Do not use this
private final Arena sharedBridgeArena = Arena.ofShared();

public OcelEventView events() {
    MemorySegment eventsPtr = rust4pm_h.rust4pm_log_get_events(sharedBridgeArena, rawPtr);
    // View is bound to sharedBridgeArena, not to the per-handle lifetime
    return new OcelEventView(eventsPtr, count);
}
```

**What happens**:

1. Task 1 starts discovering DFG from log A, creates OcelEventView bound to sharedBridgeArena
2. Task 2 starts discovering DFG from log B, creates another OcelEventView bound to same arena
3. Task 1 completes and closes log A, triggering `ownedArena.close()` (different arena, no effect)
4. Task 2 tries to read an event: the shared arena is still open, so the read succeeds
5. But Rust has freed the log A memory region
6. View reads garbage from Rust's heap, triggering undefined behavior
7. No exception until data corruption occurs (or crashes unpredictably)

**With the lifetime invariant**:

```java
// CORRECT: Per-handle arena
OcelLogHandle(MemorySegment rawPtr, Rust4pmBridge bridge) {
    this.rawPtr     = rawPtr;
    this.ownedArena = Arena.ofShared();  // New arena per handle
    this.bridge     = bridge;
}

public OcelEventView events() {
    MemorySegment eventsPtr = rust4pm_h.rust4pm_log_get_events(ownedArena, rawPtr);
    // Reinterpret: bind to ownedArena, which is closed when close() is called
    MemorySegment eventsSeg = eventsPtr.reinterpret(count * stride, ownedArena, null);
    return new OcelEventView(eventsSeg, (int) count);
}

@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        rust4pm_h.rust4pm_log_free(rawPtr);
        ownedArena.close();  // Invalidates all views derived from this handle
    }
}
```

When log A closes, its `ownedArena.close()` invalidates the EventView. Task 2 attempts a read:

```
view.get(index)
  → eventsSeg.asSlice(...)
  → Panama checks segment's arena state
  → Arena is closed
  → throws IllegalStateException
```

Exception occurs at the exact point of use, not when memory corruption begins. Stack trace points to the specific view access. Root cause is obvious: "use-after-close."

### Idempotency Invariant Failure Scenario

A YAWL work item processor parses an OCEL2 log, processes events, and handles exceptions:

```java
try (Rust4pmBridge bridge = new Rust4pmBridge();
     OcelLogHandle log = bridge.parseOcel2Json(json)) {

    for (var event : log.events()) {
        processEvent(event);  // May throw ProcessMiningException
    }
} catch (Exception e) {
    logger.error("Processing failed", e);
}
// On exit (normal or exception): bridge.close(), then log.close()
```

If close is not idempotent, a cascade of errors can occur:

```java
// ANTI-PATTERN: Non-idempotent close
@Override
public void close() {
    rust4pm_h.rust4pm_log_free(rawPtr);  // Always called, even on second close
    ownedArena.close();
}
```

**What happens**:

1. Exception thrown in `processEvent`
2. Try-with-resources auto-closes resources in reverse order: log.close(), then bridge.close()
3. log.close() calls `rust4pm_log_free(rawPtr)` → valid, Rust deallocates OcelLogInternal
4. Exception propagates through resource close chain
5. Java runtime context cleanup calls close again on the log (in some exception paths)
6. log.close() calls `rust4pm_log_free(rawPtr)` again → double-free in Rust
7. Rust allocator detects corrupted heap metadata → crash with no message

**With the idempotency invariant**:

```java
private final AtomicBoolean closed = new AtomicBoolean(false);

@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        rust4pm_h.rust4pm_log_free(rawPtr);
        ownedArena.close();
    }
}
```

First close: `compareAndSet(false, true)` succeeds, cleanup runs.
Second close: `compareAndSet(false, true)` returns false (already true), no-op.
No crash, no undefined behavior. Guaranteed safe.

---

## The Three-Layer Architecture and Why It Exists

The bridge pattern separates concerns into three layers, each with a single responsibility for correctness.

### Layer 1: Generated Panama Bindings (`rust4pm_h.java`)

**Purpose**: Mechanical translation of C types and functions into Java FFM equivalents.

**What it owns**:
- `StructLayout` definitions for every C struct type
- `VarHandle` accessors for every field in every struct
- `MethodHandle` wrappers for every C function
- `SymbolLookup` library loading with fallback to `UnsupportedOperationException`
- Sizeof probe assertions at JVM startup (layout invariant enforcement)

**What it does NOT own**:
- Arena lifecycle management (no Arena creation; receives allocators as parameters)
- Error string materialization (raw `MemorySegment` returned)
- Type conversion (C types only: pointers, integers, floats)

**Why it's regenerated**: The layout is generated from `rust/rust4pm/rust4pm.h` using jextract (when available) or hand-written to match C semantics exactly. Regeneration is idempotent: running jextract twice on the same header produces identical code. The sizeof probes survive regeneration and verify correctness at runtime.

**Example**:

```java
// Layer 1: Generated
public static MemorySegment rust4pm_log_get_events(
        SegmentAllocator allocator, MemorySegment handlePtr) {
    requireLibrary();
    try {
        return (MemorySegment) MH$log_get_events.invokeExact(allocator, handlePtr);
    } catch (Throwable t) { throw new AssertionError("rust4pm_log_get_events failed", t); }
}

public static final VarHandle OCEL_EVENTS_RESULT_EVENTS =
    OCEL_EVENTS_RESULT_LAYOUT.varHandle(groupElement("events"));

public static final VarHandle OCEL_EVENTS_RESULT_COUNT =
    OCEL_EVENTS_RESULT_LAYOUT.varHandle(groupElement("count"));
```

### Layer 2: Bridge (`Rust4pmBridge`, `OcelLogHandle`, views)

**Purpose**: Manage Arena lifetimes, enforce invariants, materialize errors, and provide safe handle types.

**What it owns**:
- `Rust4pmBridge.arena` (thread-safe, shared, lives as long as bridge)
- `OcelLogHandle.ownedArena` (per-handle, closes when handle closes)
- Per-call `Arena.ofConfined()` for transient allocations
- Error string materialization: read C error pointer, convert to `String`, throw checked exception
- Idempotency guard: `AtomicBoolean closed` on handles
- Zero-copy views: `OcelEventView`, `OcelObjectView` reinterpret borrowed memory into views

**What it does NOT own**:
- Business logic (Layer 3 owns this)
- Conversion from C types to Java domain types (that's on callers or Layer 3)

**Why it exists**: Layer 1 cannot manage lifetimes because it has no context. Layer 3 cannot manage lifetimes because it needs a safe API. Layer 2 is the boundary where safety is enforced: it owns the Arena lifecycle and ensures all segments are bound to appropriate arenas. The Q invariant (`real_impl ∨ throw`) is satisfied by Layer 2: it provides real implementations of arena management, not stubs or mocks.

**Example**:

```java
// Layer 2: Bridge
public final class OcelLogHandle implements AutoCloseable {
    private final MemorySegment rawPtr;
    private final Arena          ownedArena;   // Per-handle arena
    private final AtomicBoolean  closed = new AtomicBoolean(false);

    public OcelEventView events() throws ProcessMiningException {
        MemorySegment result = rust4pm_h.rust4pm_log_get_events(ownedArena, rawPtr);

        MemorySegment errPtr = (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_ERROR.get(result, 0L);
        if (!MemorySegment.NULL.equals(errPtr)) {
            String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
            rust4pm_h.rust4pm_error_free(errPtr);
            throw new ProcessMiningException("events() failed: " + msg);
        }

        long count = (long) rust4pm_h.OCEL_EVENTS_RESULT_COUNT.get(result, 0L);
        MemorySegment eventsPtr = (MemorySegment) rust4pm_h.OCEL_EVENTS_RESULT_EVENTS.get(result, 0L);

        MemorySegment eventsSeg = count == 0
            ? MemorySegment.NULL
            : eventsPtr.reinterpret(count * stride, ownedArena, null);

        return new OcelEventView(eventsSeg, (int) count);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            rust4pm_h.rust4pm_log_free(rawPtr);
            ownedArena.close();
        }
    }
}
```

### Layer 3: Domain API (`ProcessMiningEngine`, models)

**Purpose**: Provide a high-level API for YAWL integration that has no Panama types in its surface.

**What it owns**:
- Domain model types: `OcelEvent`, `OcelObject`, `DirectlyFollowsGraph`, `ConformanceReport`
- Business logic: DFG discovery, conformance checking, event stream processing
- Error handling: checked exceptions for domain-level failures

**What it does NOT own**:
- Arena lifetime management (Layer 2 owns this)
- Struct layout definitions (Layer 1 owns this)
- Any Panama types (MemorySegment, Arena, MethodHandle, VarHandle)

**Why no Panama types leak**: Callers (YAWL engine, orchestration services) should not be concerned with FFM details. They interact with Java objects and records. The bridge is an implementation detail. This allows Layer 3 to be swapped without recompiling Layer 3 code — e.g., if a future version of Rust PM exposes a different API, the bridge layer adapts while the domain API remains stable.

**The Q invariant in Layer 3**: Methods that are not yet implemented throw `UnsupportedOperationException` with actionable guidance, not return null or fake data:

```java
public OcelLogHandle parseXes(String xes) throws ParseException {
    throw new UnsupportedOperationException(
        "parseXes requires rust4pm_parse_xes() C function.\n" +
        "Add to lib.rs:\n" +
        "  #[no_mangle]\n" +
        "  pub extern \"C\" fn rust4pm_parse_xes(\n" +
        "      xes: *const c_char,\n" +
        "      len: usize\n" +
        "  ) -> ParseResult\n" +
        "See: rust/rust4pm/src/lib.rs"
    );
}
```

**Example**:

```java
// Layer 3: Domain API (zero Panama types)
public final class ProcessMiningEngine implements AutoCloseable {
    public DirectlyFollowsGraph discoverDfg(OcelLogHandle log) throws ProcessMiningException {
        // Internal: uses Layer 2 to call Rust via Layer 1
        // Public: returns only domain types (DirectlyFollowsGraph, OcelNode, etc.)
    }

    public ConformanceReport checkConformance(OcelLogHandle log, String pnmlXml)
            throws ConformanceException {
        // Internal: uses Layer 2 bridge
        // Public: returns only domain types (ConformanceReport, double metrics)
    }

    public List<OcelLogHandle> parseAll(List<String> jsonLogs)
            throws InterruptedException, ExecutionException {
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<OcelLogHandle>awaitAllSuccessfulOrThrow())) {
            var tasks = jsonLogs.stream()
                .map(json -> scope.fork(() -> parseOcel2Json(json)))
                .toList();
            scope.join();
            return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        }
    }
}
```

---

## Connection to the Q Invariant

The Q invariant states: **`real_impl ∨ throw UnsupportedOperationException`** — no middle ground.

The Rust→Java bridge design enforces this through the three-layer architecture:

### Layer 1: Mechanical Translation Has No Choice

Layer 1 (generated code) cannot choose to return fake data or stub behavior. It either:
- Calls the native function via MethodHandle and returns the result, or
- Throws UnsupportedOperationException if the library is not loaded

```java
static void requireLibrary() {
    if (LIBRARY.isEmpty()) {
        throw new UnsupportedOperationException(
            "rust4pm native library not found.\n" +
            "Build:  bash scripts/build-rust4pm.sh\n" +
            "Set:    -D" + LIB_PATH_PROP + "=/path/to/librust4pm.so\n" +
            "Or put: librust4pm.so at ./target/release/librust4pm.so");
    }
}

public static long rust4pm_log_event_count(MemorySegment handlePtr) {
    requireLibrary();
    try {
        return (long) MH$log_event_count.invokeExact(handlePtr);
    } catch (Throwable t) { throw new AssertionError("rust4pm_log_event_count failed", t); }
}
```

There is no conditional fallback, no `return 0` if the call fails, no retries with degraded results. The call succeeds or the JVM terminates with a visible error.

### Layer 2: Arena Management Is Real

Layer 2 owns the actual Arena lifecycle. It does not simulate resource management or use try-finally tricks to work around missing library support. The idempotency invariant ensures that close operations are always safe, never degraded:

```java
@Override
public void close() {
    if (closed.compareAndSet(false, true)) {
        rust4pm_h.rust4pm_log_free(rawPtr);  // Real cleanup
        ownedArena.close();                   // Real Arena lifetime enforcement
    }
}
```

### Layer 3: Business Logic Is Complete

Layer 3 methods that are not yet implemented throw `UnsupportedOperationException`, not return null, empty lists, or degraded results:

```java
public OcelLogHandle parseXes(String xes) throws ParseException {
    throw new UnsupportedOperationException(
        "parseXes requires rust4pm_parse_xes() C function.\n" + ...
    );
}

public PetriNet discoverAlphaPpp(OcelLogHandle log) throws ProcessMiningException {
    throw new UnsupportedOperationException(
        "discoverAlphaPpp requires rust4pm_discover_alpha_ppp() C function.\n" + ...
    );
}
```

Callers know exactly what is implemented and what is not. They cannot accidentally use a half-baked feature because the error is obvious at the call site.

### Why Sequential Fallback is Forbidden for StructuredTaskScope

In `ProcessMiningEngine.parseAll()`, multiple logs are parsed concurrently:

```java
public List<OcelLogHandle> parseAll(List<String> jsonLogs)
        throws InterruptedException, ExecutionException {
    try (var scope = StructuredTaskScope.open(
            StructuredTaskScope.Joiner.<OcelLogHandle>awaitAllSuccessfulOrThrow())) {
        var tasks = jsonLogs.stream()
            .map(json -> scope.fork(() -> parseOcel2Json(json)))
            .toList();
        scope.join();
        return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
    }
}
```

If one parse fails, the scope cancels all remaining tasks. Retrying sequentially (fallback to single-threaded parsing) would:

1. Hide the original failure from the caller
2. Make latency unpredictable (fallback might take 10× longer)
3. Mask resource exhaustion (if concurrency was the issue, sequential fallback won't help)

Instead, the scope fails fast. If concurrency is a problem, the caller can implement retry logic with exponential backoff, but they control it — the bridge does not silently degrade.

### DFG-as-JSON: Acceptable Temporary Tradeoff

One place where the design temporarily uses JSON as an intermediate format is DFG discovery:

```java
public DirectlyFollowsGraph discoverDfg(OcelLogHandle log) throws ProcessMiningException {
    try (Arena call = Arena.ofConfined()) {
        MemorySegment result = rust4pm_h.rust4pm_discover_dfg(call, log.ptr());

        // Error handling...

        MemorySegment jsonPtr = (MemorySegment) rust4pm_h.DFG_RESULT_JSON.get(result, 0L);
        String dfgJson = jsonPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
        rust4pm_h.rust4pm_dfg_free(result);
        return parseDfgJson(dfgJson);  // JSON → DfgNode/DfgEdge records
    }
}
```

Why is this acceptable?

1. **It's an intermediate format**, not a permanent design. Rust returns JSON because the Rust type system is richer than FFM can express. If a future version of rust4pm exports a binary struct format (e.g., using Bincode or Cap'n Proto), the JSON parsing can be removed without changing the Java API.

2. **It's not a fallback**. If Rust DFG computation fails, it returns an error string, and the exception is thrown. The JSON path is only taken on success.

3. **The contract is real**. The `DfgNode` and `DfgEdge` records are genuine domain types, not fake data. JSON is only the wire format.

4. **It satisfies Q at the boundary**: Inside `parseDfgJson`, if JSON parsing fails (malformed JSON from Rust), a `ProcessMiningException` is thrown, not an empty `DirectlyFollowsGraph`. The Q invariant (real or throw) is maintained.

If JSON were used as a fallback (e.g., "if DFG discovery fails, return an empty graph serialized as JSON"), that would violate the Q invariant. But that's not the design — JSON is merely the serialization format chosen by Rust, not a degradation strategy.

---

## Summary

Correct-by-construction in Rust→Java Panama FFM bridges means:

1. **Layout invariant**: Sizeof probes at JVM startup detect struct layout mismatches immediately, preventing silent data corruption.

2. **Lifetime invariant**: Per-handle Arenas enforce that borrowed Rust memory is invalid after handle closure, preventing use-after-free by throwing `IllegalStateException` at the point of access.

3. **Idempotency invariant**: AtomicBoolean guards ensure double-close is always safe, preventing crashes in exception paths and try-with-resources contexts.

4. **Three-layer architecture**: Generation (Layer 1) is mechanical, bridges (Layer 2) are real, and domain logic (Layer 3) has no FFM details in its API.

5. **Q invariant enforcement**: All functions either do real work or throw `UnsupportedOperationException`; no silent fallbacks, no degraded modes, no fake data.

The result: FFM interop that is impossible to misuse. Errors are detected at JVM startup (layout), at the point of use (lifetime), or in exception paths (idempotency), never deep in production processing as silent corruption.
