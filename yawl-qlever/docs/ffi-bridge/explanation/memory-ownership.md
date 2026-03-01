# Memory Ownership in the FFI Bridge

**Diátaxis quadrant: Explanation** — Concepts and reasoning, not steps.

---

## Two Heaps

A Java process that loads a native library has two allocators running
simultaneously:

| Heap | Managed by | Allocation | Deallocation |
|------|-----------|-----------|--------------|
| **JVM heap** | GC | `new` / array literals | Automatic (GC) |
| **Native heap** | `malloc`/`new` in C++ | `qlever_*_create()` | `qlever_*_destroy()` |

These heaps are **completely independent**. The GC knows nothing about native
allocations. A `MemorySegment` that refers to native memory is just an address
and a length — the GC can collect the Java `MemorySegment` object while the
native bytes live on. Conversely, the GC will never free native memory just
because no Java object references it.

This means: **every native allocation must be paired with an explicit free.**

---

## Ownership Rules in This Pattern

### Rule 1 — The C façade owns what it allocates

`qlever_index_create()` calls `new QLeverIndex()` in C++. The returned pointer
belongs to the C façade. Java receives the address, but Java does **not** own
the memory. Java must call `qlever_index_destroy()` to release it.

```
C++ heap:  [QLeverIndex object]
                 ↑
Java side: MemorySegment (address only — no size, no destructor)
```

### Rule 2 — Results are temporary

`qlever_query_exec()` allocates a `QLeverResult` and returns a pointer. Java
must drain all lines via `qlever_result_next()` and then call
`qlever_result_destroy()`. The `finally` block in `QLeverEmbeddedSparqlEngine`
guarantees this:

```java
MemorySegment result = null;
try {
    result = ffi.queryExec(...).result();
    // ... iterate
} finally {
    if (result != null) ffi.resultDestroy(result);
}
```

If `resultDestroy` is not called, the `std::vector<std::string>` inside
`QLeverResult` leaks until the process exits.

### Rule 3 — Arena owns call-scoped allocations

When Java passes a `String` argument to a C function (e.g., the SPARQL query
text), it must allocate a null-terminated UTF-8 byte array in native memory.
This allocation lives only for the duration of the call:

```java
try (Arena callArena = Arena.ofConfined()) {
    MemorySegment querySeg = callArena.allocateFrom(query, StandardCharsets.UTF_8);
    mhQueryExec.invokeExact(indexHandle, querySeg, ...);
}   // Arena.close() → free(querySeg.address())
```

`Arena.ofConfined()` is a try-with-resources scope. When the `try` block exits
(normally or exceptionally) the arena frees all segments allocated from it.
The C function must **not** retain a reference to `querySeg` after it returns —
and `extern "C"` functions by convention do not outlive their call stack.

### Rule 4 — Shared arena extends lifetime for returned C strings

`qlever_result_next()` returns a pointer into the `QLeverResult`'s internal
`std::vector<std::string>`. The returned pointer is **not separately allocated**
— it points inside the result object. To let Java safely read the string, the
binding reinterprets the pointer with the **shared arena** (lifetime of the
`QLeverFfiBindings` object):

```java
// linePtr points inside the QLeverResult's std::vector
MemorySegment lineSeg = linePtr.reinterpret(MAX_LINE_LENGTH, arena, null);
return lineSeg.getString(0, StandardCharsets.UTF_8);
// lineSeg is readable until arena.close() OR until qlever_result_destroy()
// whichever comes first.
```

> **Critical:** Do not call `qlever_result_destroy()` while any `MemorySegment`
> obtained from `qlever_result_next()` is still being read. In this codebase
> the pattern is safe because all lines are accumulated into a `StringBuilder`
> before `resultDestroy` is called.

---

## Arena Scopes Used in This Codebase

| Arena | Scope | Holds |
|-------|-------|-------|
| `Arena.ofConfined()` (per-call) | `try`-with-resources block | Argument strings |
| `Arena.ofShared()` (long-lived) | Lifetime of `QLeverFfiBindings` | Reinterpreted result string pointers |

The shared arena closes in `QLeverFfiBindings.close()`, which is called from
`QLeverEmbeddedSparqlEngine.close()`. Both implement `AutoCloseable`.

---

## What Happens When You Get It Wrong

| Mistake | Symptom |
|---------|---------|
| Forget `resultDestroy` | Native heap leak; process grows indefinitely under load |
| Call `resultDestroy` too early | Segfault reading a destroyed `QLeverResult` |
| Use `Arena.ofConfined()` for returned pointer | `IllegalStateException`: segment accessed after arena closed |
| Pass native pointer to GC'd `MemorySegment` | Segfault: GC moved Java object, address now invalid |
| Read beyond `reinterpret(MAX_LINE_LENGTH)` | `IndexOutOfBoundsException` from Panama bounds check |

Panama FFM enforces bounds on `MemorySegment` access — you get a Java exception
instead of a silent buffer overread. The `reinterpret(MAX_LINE_LENGTH)` call is
the safety valve: it asserts that the C string is at most `MAX_LINE_LENGTH`
bytes long.

---

## Lifecycle Diagram

```
QLeverFfiBindings()
    │ Arena.ofShared() created
    │
    ├── indexCreate(path)
    │     Arena.ofConfined() [path] → free on return
    │     returns MemorySegment (C++ heap, owned by caller)
    │
    ├── queryExec(index, query, mediaType)
    │     Arena.ofConfined() [query] → free on return
    │     returns MemorySegment (C++ heap, owned by caller)
    │
    ├── resultNext(result)               ← multiple calls
    │     reinterpret with shared arena
    │     Java String (JVM heap copy)
    │
    ├── resultDestroy(result)            ← must be called after all resultNext
    │     free(result) on C++ heap
    │
    ├── indexDestroy(index)              ← on engine close
    │     free(index) on C++ heap
    │
    └── close()
          Arena.ofShared().close()
```

---

## See Also

- [Tutorial: Bridge Your First C++ Library](../tutorial/bridge-first-cpp-library.md)
- [How-to: Manage Arena Memory](../how-to/manage-arena-memory.md)
- [Reference: Type Mapping](../reference/type-mapping.md)
