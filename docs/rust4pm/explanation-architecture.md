# YAWL rust4pm Panama FFM Bridge — Architecture Explanation

This document explains the architectural decisions behind the rust4pm process mining library integration with YAWL, with emphasis on *why* each design choice exists. We focus on understanding rather than usage; for usage guidance, see the TUTORIAL documents.

Rust4pm implements high-performance process mining algorithms (DFG discovery, conformance checking) in Rust and exposes them safely to Java through Panama FFM (Foreign Function & Memory). This explanation assumes you're familiar with Java 21+ and general systems programming concepts.

---

## 1. Why Panama FFM, Not WASM or GraalWASM or JNI?

Three approaches compete for integrating native code with Java. Each trades off differently.

### Comparison Table

| Characteristic | JNI | GraalWASM | Panama FFM (rust4pm) |
|---|---|---|---|
| **Call overhead per operation** | ~150 ns | ~1-10 µs | ~5 ns |
| **Zero-copy memory access** | No (copies via buffers) | No (copies via linear memory) | Yes (MemorySegment) |
| **Memory model** | JNI manual marshalling | WASM linear memory | Rust heap via MemorySegment |
| **Implementation complexity** | High (C boilerplate) | Medium (WASM glue) | Low (Linker + jextract) |
| **Debuggability** | Poor (separate VM) | Medium (WASM runtime) | Excellent (native symbols) |
| **Production-proven** | 25+ years | Emerging | Stable (Java 19+) |

### Why Panama FFM Wins for Process Mining

Process mining workloads are data-intensive. Consider a real-world OCEL2 log with 2 million events, 500 object types, and 50,000 total objects. A conformance check must:

1. Parse the log (1 operation)
2. Extract per-object traces (O(n) per object type, so ~25,000 operations)
3. Replay each trace against the Petri net (O(m·t) per trace, where m = model size, t = trace length)

With **JNI**, each operation—even a simple field read from a Rust struct—incurs the 150 ns JVM state save overhead. Over 2M events × multiple field reads, that's **300+ milliseconds of pure JVM friction**. Additionally, JNI requires copying data between Java heap and native memory, further compounding latency.

With **GraalWASM**, the WASM runtime itself introduces 1-10 µs per boundary crossing, plus the WASM process is isolated from the JVM—you can't directly share memory, so all data must cross the boundary via serialization.

With **Panama FFM**, the 5 ns overhead is negligible, and `MemorySegment` provides direct access to Rust-owned memory with zero copying. The same conformance check completes in milliseconds rather than seconds, with no intermediate copies of the event array.

This advantage compounds as data size grows. For typical YAWL deployments (millions of events), Panama FFM is the only approach that keeps conformance checking interactive.

---

## 2. The Three-Layer Pattern and Why Each Exists

Rust4pm is deliberately organized in three layers: **generated**, **bridge**, and **domain**. This separation is not accidental—each layer serves a specific purpose.

### Layer 1: Generated (rust4pm_h.java)

This layer is produced mechanically by `jextract`, which reads the C header `rust4pm.h` and generates Java code containing:

- `MemoryLayout` constants for struct fields (e.g., `OCEL_EVENT_C_EVENT_ID`, `OCEL_EVENT_C_TIMESTAMP_MS`)
- `MethodHandle` constants for C functions (e.g., `rust4pm_parse_ocel2_json`)
- Helper methods for pointer arithmetic

**Why is this separate?**

1. **Mechanical generation**: `jextract` produces this code directly from the C header. It's not hand-written—we don't maintain it. This isolation prevents manual mistakes from propagating.

2. **Compiler and linker errors**: If the C header changes, `jextract` regeneration will either fail (giving early feedback) or produce code that won't compile. This is better than subtle memory safety issues hidden in hand-written Layer 2 code.

3. **Type safety**: `jextract` generates type-safe MethodHandle constants. For example, `rust4pm_log_event_count` becomes a MethodHandle with a specific signature `(MemorySegment) → long`. The compiler verifies all calls match.

4. **Encapsulation**: Callers never directly touch Layer 1. It's package-private to `org.yawlfoundation.yawl.rust4pm.generated`. This makes it clear: "You don't use this directly."

### Layer 2: Bridge (Rust4pmBridge, OcelLogHandle, OcelEventView)

This layer wraps Layer 1 and translates between the "native world" and the "Java world."

**Why not use Layer 1 directly?**

1. **Arena lifetime is complex**: Each Panama FFM call must pass an `Arena` to allocate temporary memory. The bridge centralizes this: it maintains `Arena.ofShared()` for the bridge's lifetime, and allocates `Arena.ofConfined()` for individual method calls. Callers don't need to understand Arena—they just call `bridge.parseOcel2Json(json)`.

2. **Error materialization**: Rust functions return errors as C strings (a pointer to UTF-8 bytes). Layer 2 reads these strings, frees the pointer, and throws a Java exception. This is error-prone when done ad-hoc; centralizing it ensures consistent error handling.

3. **World translation**: Layer 2 adapts between two different programming models:
   - **Rust world**: Ownership (boxes, references), lifetime annotations, error types
   - **Java world**: GC, checked exceptions, null-safety

   The bridge translates. Example: a Rust function returns `Result<OcelLogInternal, String>`. Layer 2 converts this to `OcelLogHandle | throws ParseException`.

4. **Resource management**: `OcelLogHandle` is a record that implements `AutoCloseable`. Its `close()` method calls `rust4pm_log_free(ptr)`, which tells Rust to drop the `Box<OcelLogInternal>`. This decouples the Java lifecycle from raw pointers—callers use try-with-resources, not manual `free()`.

### Layer 3: Domain (ProcessMiningEngine, ConformanceReport, OcelEvent)

This is the public API. Callers see only Java POJOs: `OcelEvent` records, `ConformanceReport` records, `DirectlyFollowsGraph` objects. No `MemorySegment`, no `Arena`, no Rust concerns.

**Why a third layer?**

1. **Abstraction**: Callers should never know Panama exists. If we switched to GraalVM native image (pure Java) tomorrow, the public API doesn't change.

2. **Backend flexibility**: The Layer 2 bridge could be swapped for a different implementation (WASM, pure Java via ND4J, etc.) without changing client code. The bridge is an implementation detail.

3. **Safety**: The public API is safe by default. Try-with-resources is enforced at the bridge level. Domain objects are immutable records. Callers can't misuse Panama.

### Architecture Diagram

```
┌───────────────────────────────────────────────────────┐
│  Layer 3: Domain (PUBLIC API)                         │
│  ConformanceReport, OcelEvent, ProcessMiningEngine    │
│  ↓ uses (type-safe, exception-safe)                   │
├───────────────────────────────────────────────────────┤
│  Layer 2: Bridge (IMPLEMENTATION)                     │
│  Rust4pmBridge, OcelLogHandle, OcelEventView          │
│  ↓ calls via MethodHandles (Arena-managed)            │
├───────────────────────────────────────────────────────┤
│  Layer 1: Generated (MECHANICAL)                      │
│  rust4pm_h.java (MemoryLayout, MethodHandle)          │
│  ↓ invokes via FFM (native calls)                     │
├───────────────────────────────────────────────────────┤
│  librust4pm.so (Rust native library)                  │
│  OcelLogInternal, conformance checker, DFG discovery  │
└───────────────────────────────────────────────────────┘
```

---

## 3. Arena Lifecycle and Memory Ownership

Panama FFM's `Arena` is a critical concept. Think of it as "the Rust allocator from Java's perspective."

In Rust, allocations are associated with an owner, and when the owner is dropped, the allocation is freed. In Java, memory allocation is usually transparent (the GC handles it). Panama FFM bridges these worlds via `Arena`.

### Two Types of Arena

**`Arena.ofShared()`** — The bridge lifetime arena
```
Rust4pmBridge bridge = new Rust4pmBridge();
// bridge.arena is Arena.ofShared()
// Lives as long as the bridge is open
try (OcelLogHandle log = bridge.parseOcel2Json(json)) {
    // ...
} // log is closed, log's memory is freed
bridge.close(); // arena is closed, any remaining Rust memory freed
```

The bridge's shared arena lasts for the bridge's entire lifetime. This is where long-lived allocations (the parsed log) live.

**`Arena.ofConfined()`** — Per-call arenas
```
public OcelLogHandle parseOcel2Json(String json) {
    try (Arena call = Arena.ofConfined()) {
        // call is confmed (single-threaded, fastest)
        // allocateFrom(json) lives in call's memory
        MemorySegment result = rust4pm_h.rust4pm_parse_ocel2_json(
            arena,  // ← bridge's shared arena (long-lived)
            jsonSeg // ← from call's confined arena (temporary)
        );
        // call closes here; jsonSeg is freed
        // result lives in bridge.arena
    }
    return new OcelLogHandle(result.ptr, this);
}
```

Confined arenas are single-threaded and faster. They're used for temporary allocations within a single method call.

### Memory Ownership Graph

```
Rust4pmBridge
    arena = Arena.ofShared()
    │
    └─ OcelLogHandle (wraps Rust Box<OcelLogInternal>)
            ptr → rust memory
            │
            └─ OcelEventView (borrows from OcelLogInternal)
                    events array (zero-copy)
                    │
                    └─ OcelEvent (record, ephemeral)
                            fields: String, Instant
                            │
                            └─ Allocated from strings in Rust
                                    (event_id, event_type C strings)
```

Key insight: **OcelEventView does not allocate**. It holds a `MemorySegment` that points into the Rust array. The array is freed only when the parent `OcelLogHandle` closes.

### Close Semantics

When you call `handle.close()`:

1. `OcelLogHandle.close()` → calls `bridge.freeLog(ptr)`
2. `bridge.freeLog(ptr)` → calls `rust4pm_h.rust4pm_log_free(ptr)`
3. `rust4pm_log_free` is a Panama FFM bridge that calls the Rust C function
4. The Rust function drops the `Box<OcelLogInternal>`, freeing all contained memory (the log, event arrays, C strings)

This is why the try-with-resources pattern is essential:

```java
try (OcelLogHandle log = bridge.parseOcel2Json(json)) {
    for (OcelEvent evt : log.events().stream()::iterator) {
        // use evt
    }
} // log.close() called automatically; Rust memory freed
```

---

## 4. Zero-Copy Semantics in Practice

"Zero-copy" is a buzzword, but in rust4pm it means something precise: **direct memory access without intermediate Java allocations**.

### What Zero-Copy Means Concretely

Consider three operations:

#### Example 1: `eventCount()`
```java
int count = log.eventCount();
```

What happens:
1. `OcelLogHandle.eventCount()` calls `rust4pm_h.rust4pm_log_event_count(ptr)`
2. This is a Panama FFM MethodHandle that calls the Rust function
3. Rust reads a `size_t` field from the struct and returns it
4. Java receives the long value

**Zero-copy**: No allocations on Java heap. No intermediate buffers. Just a single memory read from Rust and a return value.

Allocation count: **0**

#### Example 2: `events().get(n)`
```java
OcelEvent evt = log.events().get(42);
```

What happens:
1. `log.events()` calls `rust4pm_h.rust4pm_log_get_events(ptr)` → returns a struct with (pointer, count, error)
2. `OcelEventView` wraps this, pointing to the Rust array
3. `.get(42)` does pointer arithmetic: `segment.asSlice(42 * stride, stride)`
   - No allocation; just pointer math
4. `OcelEvent.fromSegment(slice)` reads two pointer fields and one long field from the struct
   - Returns a record with (eventId, eventType, timestamp, attrCount)

**Zero-copy array access**: The entire event array lives in Rust memory. We never copy it to Java. We just point into it.

**Where copying happens**: `fromSegment()` calls `idPtr.getString(0)`, which reads the C string and copies it into a Java `String`. This is **unavoidable** because Java strings are UTF-16 on the JVM, Rust strings are UTF-8, and we can't share the encoding.

Allocation count: **1** (the OcelEvent record is allocated on the Java heap, but not the underlying event data)

#### Example 3: DFG discovery result
```java
DirectlyFollowsGraph dfg = engine.discoverDfg(log);
```

The Rust conformance checker returns JSON as a C string. This must be copied once (Rust UTF-8 → Java String). But the graph structure (nodes, edges) is parsed once in Java and kept in memory. No re-copying.

Allocation count: **1** (the JSON string), then GC-managed Java objects thereafter

### When Copying *Does* Occur (Necessary Evil)

Three cases force copying:

1. **Input JSON**: `bridge.parseOcel2Json(json)` calls `allocateFrom(json, StandardCharsets.UTF_8)`. Java strings are UTF-16; C strings are UTF-8. One copy is inevitable. There's no way around it—Java's String API requires it.

2. **String fields**: When you call `evt.eventId()` on an `OcelEvent`, you're reading a C string pointer from native memory. C strings are immutable, but Java `String` is a separate type. The Panama FFM library copies the bytes. Again, unavoidable.

3. **Complex results**: If the Rust function returns a JSON result (as rust4pm's DFG discovery does), the JSON is a single C string in Rust memory. It must be copied once to become a Java String. This is proportional to the JSON size, not the event count.

**The key insight**: We're copying strings (unavoidable), not the entire data structure. For a 2M-event log, we copy exactly:
- 1 JSON input (KB range)
- 2M event IDs and event types (collectively MB range, but only when actually accessed)
- 1 DFG result JSON (KB range)

This is vastly cheaper than JNI, which would copy the entire event array in and out, plus all intermediate results.

---

## 5. OCEL2 vs XES: Why Object-Centric Matters for Conformance

Process mining has two dominant data models: **XES** (classic) and **OCEL2** (contemporary).

### XES: Case-Centric

XES (eXtensible Event Stream) models a workflow as a sequence of **cases**, where each case is a trace of events. A conformance check answers: "What percentage of real cases comply with the expected process model?"

Example:
```
Trace 1: [submit → review → approve → pay]
Trace 2: [submit → review → reject]
```

One event belongs to exactly one case. Conformance is straightforward: extract the case trace, replay against the Petri net, measure fitness.

### OCEL2: Object-Centric

OCEL2 (Object-Centric Event Log) recognizes that events often involve *multiple objects simultaneously*. A "payment processed" event might touch an Order, an Invoice, and a Bank Account.

Example:
```
Event 1: [submitOrder for Order#123]
Event 2: [issueInvoice for Order#123, Invoice#456]
Event 3: [processPayment for Invoice#456, BankAccount#789]
```

For conformance checking, the same event belongs to *multiple* traces (one per object type):

**Order#123 trace**: [submitOrder → issueInvoice → ...]
**Invoice#456 trace**: [issueInvoice → processPayment → ...]
**BankAccount#789 trace**: [processPayment → ...]

### Why Object-Centric Matters

OCEL2 conformance requires **per-object trace extraction** before replay. For each object type, you extract all events touching that object, in temporal order, and replay that trace.

Rust4pm's conformance checker implements this correctly:
1. Parse OCEL2 log (1 operation)
2. Extract per-object traces (iterate events, group by object)
3. Replay each trace (deterministic against Petri net)
4. Aggregate fitness scores

If you tried to use case-centric XES conformance on OCEL2, you'd get nonsensical results because you're losing the object relationships.

**The architectural implication**: Rust4pm's data structures (`OcelEvent`, `OcelObject`) are OCEL2-aware. The bridge correctly exposes this to Java. The domain API (Layer 3) never pretends an event belongs to a single case.

---

## 6. Linker.Option.isTrivial() and Performance Impact

Panama FFM gives you fine-grained control over JVM behavior at the boundary. The `Linker.Option.isTrivial()` option is one of the most important tuning levers.

### The Problem It Solves

When the JVM crosses into native code via a normal FFM call, it must:
1. Save all Java thread state (frame pointer, return address, live registers)
2. Switch the OS into native mode
3. Execute the C function
4. Restore Java state
5. Resume bytecode execution

This state-save overhead is **~150 nanoseconds** per call. For simple operations like "read a field from a struct," this dominates.

### The Solution: isTrivial()

Trivial calls are functions that:
- Don't call back into Java
- Don't allocate memory
- Don't throw exceptions
- Don't access shared state

For trivial calls, the JVM can skip the state-save and use a fast path: ~5 nanoseconds.

### Rust4pm's Use of isTrivial()

`jextract` marks functions as trivial when it detects simple getters:

```java
// This is trivial (probably)
rust4pm_h.rust4pm_log_event_count(ptr);

// This is NOT trivial
rust4pm_h.rust4pm_parse_ocel2_json(arena, jsonSeg, len);
  // ↑ parsing can fail, can throw
```

Over 2M calls to `eventCount()` or `get(n)` pointer arithmetic, the 150ns → 5ns savings compounds to **290 milliseconds** on a single operation. This is why the zero-copy approach is practical.

### When NOT to Use isTrivial()

If a function can throw, allocate, or call back into Java, marking it trivial is a bug. The JVM assumes it doesn't need to save state, so an exception will corrupt the stack.

Rust4pm's parsing and conformance functions correctly avoid the trivial mark because they can fail.

---

## 7. Why Rust cdylib + lib Dual Target

The `Cargo.toml` specifies:
```toml
[lib]
crate-type = ["cdylib", "lib"]
```

This tells Rust to produce two outputs:

- **`cdylib`** (C dynamic library) → `librust4pm.so` (or `.dll` on Windows)
  - Linked by Java at runtime via Panama FFM
  - Exposes C ABI (the functions in `rust4pm.h`)

- **`lib`** (Rust library) → `librust4pm.rlib` (or intermediate artifact)
  - Not used directly; instead enables Rust unit tests
  - Can be tested via `cargo test --lib`

### Why Both?

You might think: "Why not just test the `cdylib`?" Because **you can't**. The `cdylib` target:
1. Exposes only C functions (with C calling convention)
2. Loses Rust type information (everything is lowered to C types)
3. Disables panic unwinding (Rust panics can't cross the C boundary)

So Rust's `#[test]` macro doesn't work with `cdylib`. You can't write:

```rust
#[test]
fn test_parsing() {
    let log = parse_ocel2_json(r#"{ "events": [...] }"#);
    assert_eq!(log.events.len(), 100);
}
```

This code works fine with the `lib` target, where Rust types are available. But with `cdylib`, the `parse_ocel2_json` function is `extern "C"`, takes a raw pointer, and returns an opaque handle.

### The Solution: Dual Target

By including both:
- **`cargo test --lib`** runs Rust unit tests against the Rust types and functions
- **`jextract && cargo build --release`** produces the native library for Java
- **Java tests** verify the Panama FFM integration

This gives 100% coverage: Rust logic tested in Rust, and Java integration tested in Java.

The alternative (testing only via Java) would require slower, more complex integration tests. With the dual target, you get fast unit tests in Rust and slower-but-necessary integration tests in Java.

---

## Summary

The rust4pm bridge demonstrates that **high-performance JVM-native integration is possible** with careful architectural design:

1. **Panama FFM** eliminates JNI and WASM overhead, enabling interactive performance for data-intensive workloads.

2. **Three layers** (generated, bridge, domain) separate concerns and make the system testable, maintainable, and backend-agnostic.

3. **Arena-based memory management** aligns Java's GC model with Rust's ownership model, preventing memory leaks and use-after-free bugs.

4. **Zero-copy semantics** are achieved by careful use of `MemorySegment` for large data structures, with copying only for unavoidable string conversions.

5. **OCEL2 support** enables conformance checking on contemporary object-centric logs, not just legacy case-centric data.

6. **Linker tuning** (isTrivial) makes the performance math work out: fast leaf calls, full state-save for complex operations.

7. **Rust's dual-target build** enables comprehensive testing at both the Rust unit test level and the Java integration level.

Together, these decisions make rust4pm a production-ready bridge between the JVM and native code, with safety, performance, and maintainability all addressed by design.
