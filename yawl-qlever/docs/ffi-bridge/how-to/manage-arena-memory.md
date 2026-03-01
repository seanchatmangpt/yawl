# How-to: Manage Arena Memory in Panama FFM

**Diátaxis quadrant: How-to** — Task-oriented. You need correct Arena usage patterns.

---

## Goal

Allocate native memory, pass it to C functions, and ensure it is always freed
without leaks or use-after-free errors.

---

## Arena Cheat Sheet

| Arena type | Scope | Thread-safe | Use for |
|-----------|-------|-------------|---------|
| `Arena.ofConfined()` | try-with-resources block | No (single thread) | Per-call argument strings, structs |
| `Arena.ofShared()` | Object lifetime (closed manually) | Yes | Long-lived handles, reinterpreted pointers |
| `Arena.global()` | Process lifetime | Yes | Static/constant strings (rare) |
| `Arena.ofAuto()` | GC (non-deterministic) | Yes | Experimental; avoid in prod |

**Default rule:** `Arena.ofConfined()` for arguments, `Arena.ofShared()` for bindings object.

---

## Pattern 1 — Per-Call String Argument

Use `Arena.ofConfined()` when you need to pass a Java `String` to a C function
and the C function does not retain the pointer after returning.

```java
public void doSomething(String query) {
    try (Arena call = Arena.ofConfined()) {
        MemorySegment querySeg = call.allocateFrom(query, StandardCharsets.UTF_8);
        // querySeg is valid inside this try block
        mhDoSomething.invokeExact(handle, querySeg);
    }
    // call.close() automatically frees querySeg
}
```

Never use `Arena.ofConfined()` if the C code stores the pointer in a struct
for later use — the memory is freed when the arena closes.

---

## Pattern 2 — Per-Call Status Struct

```java
public long getValue() {
    try (Arena call = Arena.ofConfined()) {
        MemorySegment status = call.allocate(STATUS_LAYOUT);
        status.fill((byte) 0);
        long value = (long) mhGetValue.invokeExact(handle, status);
        checkStatus(status);
        return value;
    } catch (CounterFfiException e) {
        throw e;
    } catch (Throwable t) {
        throw new CounterFfiException("getValue failed", t);
    }
}
```

Both `querySeg` and `status` are freed when the `try` block exits, regardless
of whether the call succeeded or threw.

---

## Pattern 3 — Reinterpreting a Returned C String Pointer

When a C function returns a `const char*` that points **inside** a longer-lived
native object (e.g., a result row inside a query result struct), use the
**shared arena** to reinterpret:

```java
// mhResultNext returns a MemorySegment with size = 0 (unknown)
// We reinterpret with a safe maximum length
public String resultNext(MemorySegment result) {
    try {
        MemorySegment ptr = (MemorySegment) mhResultNext.invokeExact(result);
        if (ptr == null || ptr.equals(MemorySegment.NULL)) return null;

        // Reinterpret: arena guarantees the segment is readable until
        // either arena.close() or qlever_result_destroy(), whichever comes first.
        MemorySegment lineSeg = ptr.reinterpret(MAX_LINE_BYTES, sharedArena, null);
        return lineSeg.getString(0, StandardCharsets.UTF_8);
    } catch (Throwable t) {
        throw new MyFfiException("resultNext failed", t);
    }
}
```

The third argument to `reinterpret(size, arena, cleanup)` is a cleanup action
run when the arena closes. Pass `null` unless you need custom cleanup.

---

## Pattern 4 — Passing Multiple Arguments

When a function takes several arguments that need native memory:

```java
public void configure(String host, int port, String database) {
    try (Arena call = Arena.ofConfined()) {
        MemorySegment hostSeg = call.allocateFrom(host, StandardCharsets.UTF_8);
        MemorySegment dbSeg   = call.allocateFrom(database, StandardCharsets.UTF_8);
        MemorySegment status  = call.allocate(STATUS_LAYOUT);
        status.fill((byte) 0);

        mhConfigure.invokeExact(handle, hostSeg, port, dbSeg, status);
        checkStatus(status);
    } catch (MyFfiException e) {
        throw e;
    } catch (Throwable t) {
        throw new MyFfiException("configure failed", t);
    }
}
```

All allocations from a single `Arena.ofConfined()` are freed together when
the arena closes.

---

## Pattern 5 — Long-Lived Native Handle

When Java holds a native handle (opaque pointer returned by `*_create`),
store the handle as a `MemorySegment` field and close it explicitly:

```java
public final class MyEngine implements AutoCloseable {
    private final MyFfiBindings ffi;
    private final MemorySegment handle;    // owns: must call ffi.destroy(handle)

    public MyEngine() {
        this.ffi    = new MyFfiBindings(); // opens sharedArena
        this.handle = ffi.create();        // call_arena frees argument; handle lives on
    }

    @Override
    public void close() {
        ffi.destroy(handle);   // frees C++ object on native heap
        ffi.close();           // closes sharedArena
    }
}
```

Use try-with-resources at call sites:

```java
try (MyEngine engine = new MyEngine()) {
    // use engine
}  // destroy + close called even on exception
```

---

## Pattern 6 — Collecting Results Before Freeing

When draining a C iterator, collect all data into JVM types before calling
`*_destroy`:

```java
public List<String> collectResults(MemorySegment result) {
    List<String> lines = new ArrayList<>();
    try {
        while (ffi.resultHasNext(result)) {
            String line = ffi.resultNext(result);  // reads native memory
            if (line != null) lines.add(line);     // copies to JVM heap
        }
    } finally {
        ffi.resultDestroy(result);  // safe: all lines already copied to JVM
    }
    return lines;
}
```

After `resultDestroy`, any `MemorySegment` reinterpreted from that result's
data is invalid. Always copy to Java `String` before destroying.

---

## Common Mistakes and Fixes

| Mistake | What Happens | Fix |
|---------|-------------|-----|
| `Arena.ofConfined()` for a pointer the C code retains | Use-after-free: Java frees; C++ reads | Use `Arena.ofShared()` or document the C contract clearly |
| `Arena.ofShared()` for a per-call argument | Memory never freed until `close()` | Use `Arena.ofConfined()` |
| Missing `status.fill((byte) 0)` | Stale garbage in status fields | Always zero-fill before passing to C |
| Reading `MemorySegment` after owning arena closed | `IllegalStateException` | Close arena only after all reads are done |
| Calling `resultNext` after `resultDestroy` | Segfault | Drain fully before destroying |
| Returning `MemorySegment` from `ofConfined()` block | Segment is invalid after return | Copy content to Java type before returning |

---

## Debugging Memory Issues

Enable Panama bounds checking:

```bash
java --enable-preview \
     -Djdk.internal.foreign.check.bounds=true \
     ...
```

Track arena state with verbose logging (add temporarily during debugging):

```java
Arena call = Arena.ofConfined();
System.out.println("Arena open: " + call.isAlive());
// ... use arena ...
call.close();
System.out.println("Arena open: " + call.isAlive()); // should print: false
```

---

## See Also

- [Explanation: Memory Ownership](../explanation/memory-ownership.md)
- [How-to: Map C++ Structs to Java MemoryLayout](map-cpp-structs.md)
- [Reference: Type Mapping](../reference/type-mapping.md)
